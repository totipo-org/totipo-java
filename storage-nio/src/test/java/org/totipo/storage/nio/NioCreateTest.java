package org.totipo.storage.nio;

import org.totipo.spi.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class NioCreateTest {
    @TempDir Path root;
    private static final byte[] BYTES = {3, 4, 5}; // Provider is deliberately protocol-agnostic.
    private NioTotipoStore open(NioVaultStorage.Operations vaults) throws IOException {
        return NioTotipoStore.open(root, new NioObjectStorage.Operations(p -> {}), vaults, new NioTotipoStore.ScanOperations());
    }
    private void noTemps() throws IOException {
        try (var paths = Files.walk(root)) { assertFalse(paths.anyMatch(p -> p.getFileName().toString().startsWith(".totipo-"))); }
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void completeStageCreateAndExactExistingNeverReplace(boolean coordinated) throws Exception {
        var installer = coordinated ? NioCanonicalInstaller.COORDINATED_MOVE : NioCanonicalInstaller.HARD_LINK;
        var events = new ArrayList<String>();
        var vaults = new NioVaultStorage.Operations(p -> events.add("root"), installer) {
            @Override void syncStage(FileChannel channel) throws IOException { events.add("force"); super.syncStage(channel); }
            @Override void installIfAbsent(Path target, Path stage) throws IOException {
                assertArrayEquals(BYTES, Files.readAllBytes(stage)); assertEquals(List.of("force"), events);
                super.installIfAbsent(target, stage); events.add("install");
            }
        };
        try (var store = open(vaults)) {
            assertInstanceOf(VaultCreate.Created.class, store.createVault(BYTES));
            assertEquals(coordinated ? List.of("force", "install", "force", "root") : List.of("force", "install", "root"), events);
            assertInstanceOf(VaultCreate.AlreadyPresent.class, store.createVault(BYTES));
            assertInstanceOf(VaultCreate.AlreadyPresent.class, store.createVault(new byte[]{9}));
            assertArrayEquals(BYTES, ((BoundedRead.Present) store.readVault(3)).bytes());
        }
        noTemps();
    }
    @ParameterizedTest @ValueSource(strings = {"directory", "symlink", "regular"})
    void existingWrongKindOrDifferentVaultBlocksWithoutStaging(String kind) throws Exception {
        Path canonical = root.resolve("vault");
        if (kind.equals("directory")) Files.createDirectory(canonical);
        else if (kind.equals("symlink")) Files.createSymbolicLink(canonical, root.resolve("missing"));
        else Files.write(canonical, new byte[]{9});
        try (var store = open(new NioVaultStorage.Operations(p -> fail("No persistence")) {
            @Override Path temporary(Path path) { return fail("No staging"); }
        })) { assertInstanceOf(VaultCreate.AlreadyPresent.class, store.createVault(BYTES)); }
        if (kind.equals("regular")) assertArrayEquals(new byte[]{9}, Files.readAllBytes(canonical));
        else assertEquals(kind.equals("symlink"), Files.isSymbolicLink(canonical));
        noTemps();
    }
    @ParameterizedTest @ValueSource(strings = {"temporary", "write", "stage-force", "install", "root-force"})
    void failureKnowledgeAndCleanupAreHonest(String point) throws Exception {
        var vaults = new NioVaultStorage.Operations(p -> { if (point.equals("root-force")) throw new IOException("force"); }) {
            @Override Path temporary(Path path) throws IOException { if (point.equals("temporary")) throw new IOException("temp"); return super.temporary(path); }
            @Override int write(FileChannel channel, ByteBuffer bytes) throws IOException { if (point.equals("write")) throw new IOException("write"); return super.write(channel, bytes); }
            @Override void syncStage(FileChannel channel) throws IOException { if (point.equals("stage-force")) throw new IOException("force"); super.syncStage(channel); }
            @Override void installIfAbsent(Path target, Path temp) throws IOException { if (point.equals("install")) throw new IOException("ambiguous"); super.installIfAbsent(target, temp); }
        };
        try (var store = open(vaults)) {
            var result = store.createVault(BYTES);
            if (Set.of("temporary", "write", "stage-force").contains(point)) assertInstanceOf(VaultCreate.Failed.class, result);
            else assertInstanceOf(VaultCreate.Uncertain.class, result);
        }
        assertEquals(point.equals("root-force"), Files.exists(root.resolve("vault"))); noTemps();
    }
    @Test void independentSharedCreatorsHaveExactlyOneNoReplaceWinner() throws Exception {
        var barrier = new CyclicBarrier(2);
        var ops = new NioVaultStorage.Operations(p -> {}) {
            @Override void link(Path target, Path stage) throws IOException {
                try { barrier.await(10, TimeUnit.SECONDS); } catch (Exception e) { throw new IOException(e); }
                super.link(target, stage);
            }
        };
        var pool = Executors.newFixedThreadPool(2);
        try (var first = open(ops); var second = open(ops)) {
            var a = pool.submit(() -> first.createVault(BYTES));
            var b = pool.submit(() -> second.createVault(new byte[]{9}));
            var results = List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS));
            assertEquals(1, results.stream().filter(VaultCreate.Created.class::isInstance).count());
            assertEquals(1, results.stream().filter(VaultCreate.AlreadyPresent.class::isInstance).count());
            assertArrayEquals(results.get(0) instanceof VaultCreate.Created ? BYTES : new byte[]{9}, Files.readAllBytes(root.resolve("vault")));
        } finally { pool.shutdownNow(); }
        noTemps();
    }
    @Test void ordinaryOpenRequiresHardLinksAndDoesNotFallBack() throws Exception {
        try (var store = open(new NioVaultStorage.Operations(p -> {}) {
            @Override void link(Path target, Path temp) { throw new UnsupportedOperationException("Denied links"); }
        })) { assertInstanceOf(VaultCreate.Uncertain.class, store.createVault(BYTES)); }
        assertFalse(Files.exists(root.resolve("vault"))); noTemps();
    }
    @Test void coordinatedFactoryCreatesAndPublishesWithImmutableVault() throws Exception {
        try (var store = NioStoreComposition.coordinatedDelegate(root, new NioDurability())) {
            assertInstanceOf(VaultCreate.Created.class, store.createVault(BYTES));
            assertInstanceOf(ObjectWrite.Written.class, store.publishObject(new ObjectName("opaque"), BYTES));
            assertInstanceOf(ObjectWrite.AlreadyPresentExact.class, store.publishObject(new ObjectName("opaque"), BYTES));
            assertArrayEquals(BYTES, Files.readAllBytes(root.resolve("vault")));
        }
        assertTrue(Arrays.stream(NioTotipoStore.class.getMethods()).noneMatch(m -> m.getName().equals("openPrivate")));
        noTemps();
    }
    @Test void postInstallCollisionExceptionCannotClaimNoMutation() throws Exception {
        try (var store = open(new NioVaultStorage.Operations(p -> { throw new FileAlreadyExistsException("injected force failure"); }))) {
            assertInstanceOf(VaultCreate.Uncertain.class, store.createVault(BYTES));
        }
        assertArrayEquals(BYTES, Files.readAllBytes(root.resolve("vault"))); noTemps();
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void coordinatedCopyMoveForcesActualCanonicalAndPartialFailureStaysUncertain(boolean partial) throws Exception {
        var events = new ArrayList<String>();
        var ops = new NioVaultStorage.Operations(p -> events.add("root"), NioCanonicalInstaller.COORDINATED_MOVE) {
            @Override void link(Path target, Path stage) { fail("Coordinated path must not link"); }
            @Override int write(FileChannel channel, ByteBuffer bytes) throws IOException {
                int limit = bytes.limit(); bytes.limit(Math.min(limit, bytes.position() + 1));
                try { return super.write(channel, bytes); } finally { bytes.limit(limit); }
            }
            @Override void installIfAbsent(Path target, Path stage) throws IOException {
                assertArrayEquals(BYTES, Files.readAllBytes(stage));
                if (partial) { Files.write(target, new byte[]{3}); throw new IOException("Partial provider move"); }
                Files.copy(stage, target); Files.delete(stage); events.add("copy");
            }
            @Override void syncInstalled(Path target) throws IOException {
                assertEquals(List.of("copy"), events); assertArrayEquals(BYTES, Files.readAllBytes(target));
                super.syncInstalled(target); events.add("canonical-force");
            }
        };
        try (var store = open(ops)) {
            if (partial) assertInstanceOf(VaultCreate.Uncertain.class, store.createVault(BYTES));
            else assertInstanceOf(VaultCreate.Created.class, store.createVault(BYTES));
        }
        assertArrayEquals(partial ? new byte[]{3} : BYTES, Files.readAllBytes(root.resolve("vault")));
        assertEquals(partial ? List.of() : List.of("copy", "canonical-force", "root"), events); noTemps();
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void targetAppearingAtInstallIsNeverOverwrittenAndAliasIsNeverCanonical(boolean alias) throws Exception {
        var ops = new NioVaultStorage.Operations(p -> {}) {
            @Override void link(Path target, Path stage) throws IOException {
                Files.write(root.resolve(alias ? "VAULT" : "vault"), new byte[]{9});
                if (alias) throw new FileAlreadyExistsException("Model alias collision");
                super.link(target, stage);
            }
        };
        try (var store = open(ops)) {
            if (alias) assertEquals(new VaultCreate.Failed(StoreFailure.UNSAFE_NAMESPACE), store.createVault(BYTES));
            else assertInstanceOf(VaultCreate.AlreadyPresent.class, store.createVault(BYTES));
        }
        assertArrayEquals(new byte[]{9}, Files.readAllBytes(root.resolve(alias ? "VAULT" : "vault"))); noTemps();
    }

}

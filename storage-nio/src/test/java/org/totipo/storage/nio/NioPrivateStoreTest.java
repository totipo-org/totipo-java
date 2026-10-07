package org.totipo.storage.nio;

import org.totipo.spi.*;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** Private deployment semantics on a local JVM filesystem, not platform qualification. */
class NioPrivateStoreTest {
    @TempDir Path root;
    private static final ObjectName NAME = new ObjectName("opaque");
    private static final byte[] BYTES = {1, 7, 3};
    private static final byte[] OTHER = {9, 8};
    private NioObjectStorage.Operations objects() {
        return new NioObjectStorage.Operations(p -> {}, NioCanonicalInstaller.PRIVATE_MOVE);
    }
    private NioVaultStorage.Operations vaults() {
        return new NioVaultStorage.Operations(p -> {}, NioCanonicalInstaller.PRIVATE_MOVE);
    }
    private NioTotipoStore open(NioObjectStorage.Operations objects, NioVaultStorage.Operations vaults) throws IOException {
        return NioTotipoStore.open(root, objects, vaults, new NioTotipoStore.ScanOperations());
    }
    private PreparedVault prepare(TotipoStore store) {
        return assertInstanceOf(VaultPrepare.Prepared.class, store.prepareVault(BYTES)).vault();
    }
    private Path target() { return root.resolve("objects-v1").resolve(NAME.value()); }
    private void noTemps() throws IOException {
        try (var entries = Files.walk(root)) {
            assertFalse(entries.anyMatch(p -> p.getFileName().toString().startsWith(".totipo-")));
        }
    }

    @Test void publicPrivateFactoryInstallsAndReopensWithDefaultDurability() throws Exception {
        try (var store = NioTotipoStore.openPrivate(root); var stage = prepare(store)) {
            assertArrayEquals(BYTES, assertInstanceOf(BoundedRead.Present.class, stage.readBack(3)).bytes());
            assertInstanceOf(VaultInstall.Installed.class, stage.installCanonicalIfAbsent());
            assertInstanceOf(ObjectWrite.Written.class, store.publishObject(NAME, BYTES));
            assertInstanceOf(ObjectWrite.Written.class, store.publishObject(new ObjectName("second"), OTHER));
            assertInstanceOf(ObjectWrite.AlreadyPresentExact.class, store.publishObject(NAME, BYTES));
            assertInstanceOf(ObjectWrite.ExistingDifferent.class, store.publishObject(NAME, OTHER));
        }
        try (var store = NioTotipoStore.openPrivate(root)) {
            assertArrayEquals(BYTES, assertInstanceOf(BoundedRead.Present.class, store.readVault(3)).bytes());
            assertArrayEquals(BYTES, assertInstanceOf(BoundedRead.Present.class, store.readObject(NAME, 3)).bytes());
        }
        noTemps();
    }

    @Test void privateFactoryOpeningIsReadOnly() throws Exception {
        try (var store = NioTotipoStore.openPrivate(root, p -> fail("Opening must not persist"))) {
            assertInstanceOf(BoundedRead.Absent.class, store.readVault(3));
        }
        try (var entries = Files.list(root)) { assertEquals(0, entries.count()); }
    }

    @Test void sharedModeStillDependsOnHardLinksAndNeverFallsBack() throws Exception {
        var calls = new ArrayList<String>();
        var objects = new NioObjectStorage.Operations(p -> {}) {
            @Override void link(Path target, Path temp) throws IOException {
                calls.add("object-link"); throw new AccessDeniedException(target.toString());
            }
        };
        var vaults = new NioVaultStorage.Operations(p -> {}) {
            @Override void link(Path target, Path temp) throws IOException {
                calls.add("vault-link"); throw new AccessDeniedException(target.toString());
            }
        };
        try (var store = open(objects, vaults); var stage = prepare(store)) {
            assertInstanceOf(ObjectWrite.Uncertain.class, store.publishObject(NAME, BYTES));
            assertInstanceOf(VaultInstall.Uncertain.class, stage.installCanonicalIfAbsent());
        }
        assertEquals(List.of("object-link", "vault-link"), calls);
        assertFalse(Files.exists(target())); assertFalse(Files.exists(root.resolve("vault"))); noTemps();
    }

    @Test void privateModeDoesNotCallHardLinkSeams() throws Exception {
        var objects = new NioObjectStorage.Operations(p -> {}, NioCanonicalInstaller.PRIVATE_MOVE) {
            @Override void link(Path target, Path temp) { fail("Private mode must not hard link"); }
        };
        var vaults = new NioVaultStorage.Operations(p -> {}, NioCanonicalInstaller.PRIVATE_MOVE) {
            @Override void link(Path target, Path temp) { fail("Private mode must not hard link"); }
        };
        try (var store = open(objects, vaults); var stage = prepare(store)) {
            assertInstanceOf(ObjectWrite.Written.class, store.publishObject(NAME, BYTES));
            assertInstanceOf(VaultInstall.Installed.class, stage.installCanonicalIfAbsent());
        }
        noTemps();
    }

    @ParameterizedTest @ValueSource(strings = {"different", "directory", "symlink"})
    void existingVaultIsPreservedIncludingWrongKinds(String kind) throws Exception {
        Path target = root.resolve("vault");
        if (kind.equals("directory")) Files.createDirectory(target);
        else if (kind.equals("symlink")) Files.createSymbolicLink(target, Files.write(root.resolve("outside"), OTHER));
        else Files.write(target, OTHER);
        try (var store = NioTotipoStore.openPrivate(root); var stage = prepare(store)) {
            assertInstanceOf(VaultInstall.AlreadyPresent.class, stage.installCanonicalIfAbsent());
            assertThrows(IllegalStateException.class, stage::installCanonicalIfAbsent);
        }
        if (kind.equals("directory")) assertTrue(Files.isDirectory(target));
        else if (kind.equals("symlink")) assertTrue(Files.isSymbolicLink(target));
        else assertArrayEquals(OTHER, Files.readAllBytes(target));
        noTemps();
    }

    @Test void targetAppearingAfterOuterAbsenceCheckIsNotReplaced() throws Exception {
        var objects = new NioObjectStorage.Operations(p -> {}, NioCanonicalInstaller.PRIVATE_MOVE) {
            @Override void installIfAbsent(Path target, Path temp) throws IOException {
                Files.write(target, OTHER, StandardOpenOption.CREATE_NEW);
                super.installIfAbsent(target, temp);
            }
        };
        var vaults = new NioVaultStorage.Operations(p -> {}, NioCanonicalInstaller.PRIVATE_MOVE) {
            @Override void installIfAbsent(Path target, Path temp) throws IOException {
                Files.write(target, OTHER, StandardOpenOption.CREATE_NEW);
                super.installIfAbsent(target, temp);
            }
        };
        try (var store = open(objects, vaults); var stage = prepare(store)) {
            assertInstanceOf(ObjectWrite.ExistingDifferent.class, store.publishObject(NAME, BYTES));
            assertInstanceOf(VaultInstall.AlreadyPresent.class, stage.installCanonicalIfAbsent());
        }
        assertArrayEquals(OTHER, Files.readAllBytes(target()));
        assertArrayEquals(OTHER, Files.readAllBytes(root.resolve("vault"))); noTemps();
    }

    @Test void ordinaryMoveRejectsExistingTargetWithoutReplacementOptions() throws Exception {
        // Exercises Files.move itself, including when a target appears after a caller's absence check.
        Path stage = Files.write(root.resolve("stage"), BYTES);
        Path target = Files.write(root.resolve("canonical"), OTHER);
        assertThrows(FileAlreadyExistsException.class, () -> Files.move(stage, target));
        assertArrayEquals(OTHER, Files.readAllBytes(target)); assertArrayEquals(BYTES, Files.readAllBytes(stage));
    }

    @ParameterizedTest @ValueSource(strings = {"directory", "symlink"})
    void unsuitableObjectTargetRemainsUntouched(String kind) throws Exception {
        Files.createDirectory(root.resolve("objects-v1"));
        if (kind.equals("directory")) Files.createDirectory(target());
        else Files.createSymbolicLink(target(), Files.write(root.resolve("outside"), OTHER));
        try (var store = NioTotipoStore.openPrivate(root)) {
            assertEquals(new ObjectWrite.Failed(StoreFailure.UNSAFE_NAMESPACE), store.publishObject(NAME, BYTES));
        }
        if (kind.equals("directory")) assertTrue(Files.isDirectory(target()));
        else { assertTrue(Files.isSymbolicLink(target())); assertArrayEquals(OTHER, Files.readAllBytes(root.resolve("outside"))); }
        noTemps();
    }

    @Test void copyBasedMoveForcesCanonicalAfterClosingOriginalStage() throws Exception {
        var events = new ArrayList<String>();
        var objects = new NioObjectStorage.Operations(p -> {}, NioCanonicalInstaller.PRIVATE_MOVE) {
            FileChannel stage;
            @Override void force(FileChannel channel, String point) throws IOException {
                if (point.equals("stage-sync")) stage = channel;
                if (point.equals("post-link-sync")) {
                    assertNotSame(stage, channel); assertFalse(stage.isOpen()); events.add("canonical-force");
                }
                super.force(channel, point);
            }
            @Override void installIfAbsent(Path target, Path temp) throws IOException {
                assertFalse(stage.isOpen());
                Files.copy(temp, target); Files.delete(temp); // Model a provider's move implementation.
                events.add("install");
            }
            @Override void sync(Path directory, String point) throws IOException {
                if (point.equals("directory-sync")) events.add("namespace-force");
                super.sync(directory, point);
            }
        };
        try (var store = open(objects, vaults())) {
            assertInstanceOf(ObjectWrite.Written.class, store.publishObject(NAME, BYTES));
        }
        assertEquals(List.of("install", "canonical-force", "namespace-force"), events);
        assertArrayEquals(BYTES, Files.readAllBytes(target())); noTemps();
    }

    @Test void partialProviderMoveFailureRemainsUncertainWithoutCanonicalCleanup() throws Exception {
        var objects = new NioObjectStorage.Operations(p -> {}, NioCanonicalInstaller.PRIVATE_MOVE) {
            @Override void installIfAbsent(Path target, Path temp) throws IOException {
                Files.write(target, new byte[]{1}, StandardOpenOption.CREATE_NEW);
                throw new IOException("partial provider move");
            }
        };
        var vaults = new NioVaultStorage.Operations(p -> {}, NioCanonicalInstaller.PRIVATE_MOVE) {
            @Override void installIfAbsent(Path target, Path temp) throws IOException {
                Files.write(target, new byte[]{1}, StandardOpenOption.CREATE_NEW);
                throw new IOException("partial provider move");
            }
        };
        try (var store = open(objects, vaults); var stage = prepare(store)) {
            assertInstanceOf(ObjectWrite.Uncertain.class, store.publishObject(NAME, BYTES));
            assertInstanceOf(VaultInstall.Uncertain.class, stage.installCanonicalIfAbsent());
            assertInstanceOf(ObjectWrite.ExistingDifferent.class, store.publishObject(NAME, BYTES));
        }
        assertArrayEquals(new byte[]{1}, Files.readAllBytes(target()));
        assertArrayEquals(new byte[]{1}, Files.readAllBytes(root.resolve("vault"))); noTemps();
    }

    @Test void injectedMoveCollisionUsesExactWinnerAndRejectsAlias() throws Exception {
        var objects = new NioObjectStorage.Operations(p -> {}, NioCanonicalInstaller.PRIVATE_MOVE) {
            @Override void installIfAbsent(Path target, Path temp) throws IOException {
                Files.write(target, BYTES, StandardOpenOption.CREATE_NEW);
                throw new FileAlreadyExistsException(target.toString());
            }
        };
        var vaults = new NioVaultStorage.Operations(p -> {}, NioCanonicalInstaller.PRIVATE_MOVE) {
            @Override void installIfAbsent(Path target, Path temp) throws IOException {
                Files.write(root.resolve("VAULT"), OTHER);
                throw new FileAlreadyExistsException(target.toString());
            }
        };
        try (var store = open(objects, vaults); var stage = prepare(store)) {
            assertInstanceOf(ObjectWrite.AlreadyPresentExact.class, store.publishObject(NAME, BYTES));
            assertEquals(new VaultInstall.Failed(StoreFailure.UNSAFE_NAMESPACE), stage.installCanonicalIfAbsent());
        }
        assertArrayEquals(OTHER, Files.readAllBytes(root.resolve("VAULT"))); noTemps();
    }

    @ParameterizedTest @ValueSource(strings = {"root-sync", "stage-sync", "before-link", "after-link", "post-link-sync", "directory-sync"})
    void objectFailureBoundaryAndRecoveryRemainConservative(String point) throws Exception {
        var objects = new NioObjectStorage.Operations(p -> {}, NioCanonicalInstaller.PRIVATE_MOVE) {
            boolean fail = true;
            @Override void at(String at) throws IOException {
                if (fail && point.equals(at)) { fail = false; throw new IOException("injected"); }
            }
        };
        boolean before = Set.of("root-sync", "stage-sync", "before-link").contains(point);
        try (var store = open(objects, vaults())) {
            if (before) assertInstanceOf(ObjectWrite.Failed.class, store.publishObject(NAME, BYTES));
            else assertInstanceOf(ObjectWrite.Uncertain.class, store.publishObject(NAME, BYTES));
            assertEquals(!before, Files.exists(target()));
            noTemps();
            if (before) assertInstanceOf(ObjectWrite.Written.class, store.publishObject(NAME, BYTES));
            else assertInstanceOf(ObjectWrite.AlreadyPresentExact.class, store.publishObject(NAME, BYTES));
        }
    }

    @ParameterizedTest @ValueSource(strings = {"existing-force", "existing-directory-sync", "existing-root-sync", "existing-confirm"})
    void exactRetryStillRequiresExistingAcknowledgement(String point) throws Exception {
        Files.write(Files.createDirectory(root.resolve("objects-v1")).resolve(NAME.value()), BYTES);
        var objects = new NioObjectStorage.Operations(p -> {}, NioCanonicalInstaller.PRIVATE_MOVE) {
            @Override void at(String at) throws IOException { if (point.equals(at)) throw new IOException("injected"); }
        };
        try (var store = open(objects, vaults())) {
            assertInstanceOf(ObjectWrite.Uncertain.class, store.publishObject(NAME, BYTES));
        }
        assertArrayEquals(BYTES, Files.readAllBytes(target()));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void moveInvocationErrorsAreUncertainEvenIfInjectedBeforeEffect(boolean afterEffect) throws Exception {
        var objects = new NioObjectStorage.Operations(p -> {}, NioCanonicalInstaller.PRIVATE_MOVE) {
            @Override void installIfAbsent(Path target, Path temp) throws IOException {
                if (afterEffect) super.installIfAbsent(target, temp);
                throw new IOException("move acknowledgement unavailable");
            }
        };
        var vaults = new NioVaultStorage.Operations(p -> {}, NioCanonicalInstaller.PRIVATE_MOVE) {
            @Override void installIfAbsent(Path target, Path temp) throws IOException {
                if (afterEffect) super.installIfAbsent(target, temp);
                throw new IOException("move acknowledgement unavailable");
            }
        };
        try (var store = open(objects, vaults); var stage = prepare(store)) {
            assertInstanceOf(ObjectWrite.Uncertain.class, store.publishObject(NAME, BYTES));
            assertInstanceOf(VaultInstall.Uncertain.class, stage.installCanonicalIfAbsent());
        }
        assertEquals(afterEffect, Files.exists(target()));
        assertEquals(afterEffect, Files.exists(root.resolve("vault"))); noTemps();
    }

    @ParameterizedTest @ValueSource(strings = {"prepare", "canonical-force", "root-force"})
    void vaultForceFailureClassification(String point) throws Exception {
        var vaults = new NioVaultStorage.Operations(p -> {}, NioCanonicalInstaller.PRIVATE_MOVE) {
            int forces;
            @Override void syncStage(FileChannel channel) throws IOException {
                if ((point.equals("prepare") && forces == 0) || (point.equals("canonical-force") && forces == 1))
                    throw new IOException("force failed");
                forces++; super.syncStage(channel);
            }
            @Override void syncDirectory(Path directory) throws IOException {
                if (point.equals("root-force")) throw new IOException("directory force failed");
                super.syncDirectory(directory);
            }
        };
        try (var store = open(objects(), vaults)) {
            if (point.equals("prepare")) assertInstanceOf(VaultPrepare.Failed.class, store.prepareVault(BYTES));
            else try (var stage = prepare(store)) {
                assertInstanceOf(VaultInstall.Uncertain.class, stage.installCanonicalIfAbsent());
            }
        }
        assertEquals(!point.equals("prepare"), Files.exists(root.resolve("vault"))); noTemps();
    }

    @Test void vaultMissingRootPreconditionIsDefiniteFailure() throws Exception {
        try (var store = NioTotipoStore.openPrivate(root); var stage = prepare(store)) {
            Path moved = root.resolveSibling(root.getFileName() + "-moved"); Files.move(root, moved);
            try { assertInstanceOf(VaultInstall.Failed.class, stage.installCanonicalIfAbsent()); }
            finally { Files.move(moved, root); }
        }
        noTemps();
    }

    @Test void initialInstallUsesActualStageAndReplacementRetainsFallback() throws Exception {
        var vaults = new NioVaultStorage.Operations(p -> {}, NioCanonicalInstaller.PRIVATE_MOVE) {
            @Override void atomicMove(Path temp, Path target) throws IOException {
                throw new AtomicMoveNotSupportedException(temp.toString(), target.toString(), "injected");
            }
        };
        try (var store = open(objects(), vaults)) {
            try (var stage = prepare(store)) {
                Path actual;
                try (var entries = Files.list(root)) { actual = entries.findFirst().orElseThrow(); }
                Files.write(actual, OTHER);
                assertArrayEquals(OTHER, assertInstanceOf(BoundedRead.Present.class, stage.readBack(2)).bytes());
                assertInstanceOf(VaultInstall.Installed.class, stage.installCanonicalIfAbsent());
                assertArrayEquals(OTHER, Files.readAllBytes(root.resolve("vault")));
            }
            try (var stage = prepare(store)) {
                assertInstanceOf(VaultReplace.Replaced.class, stage.replaceCanonical());
                assertArrayEquals(BYTES, Files.readAllBytes(root.resolve("vault")));
            }
        }
        noTemps();
    }
}

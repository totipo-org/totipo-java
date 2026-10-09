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
        return new NioObjectStorage.Operations(p -> {}, NioCanonicalInstaller.COORDINATED_MOVE);
    }
    private NioVaultStorage.Operations vaults() {
        return new NioVaultStorage.Operations(p -> {}, NioCanonicalInstaller.COORDINATED_MOVE);
    }
    private NioTotipoStore open(NioObjectStorage.Operations objects, NioVaultStorage.Operations vaults) throws IOException {
        return NioTotipoStore.open(root, objects, vaults, new NioTotipoStore.ScanOperations());
    }

    private Path target() { return root.resolve("objects-v1").resolve(NAME.value()); }
    private void noTemps() throws IOException {
        try (var entries = Files.walk(root)) {
            assertFalse(entries.anyMatch(p -> p.getFileName().toString().startsWith(".totipo-")));
        }
    }

    @Test void privateFactoryOpeningIsReadOnly() throws Exception {
        try (var store = NioStoreComposition.coordinatedDelegate(root, p -> fail("Opening must not persist"))) {
            assertInstanceOf(BoundedRead.Absent.class, store.readVault(3));
        }
        try (var entries = Files.list(root)) { assertEquals(0, entries.count()); }
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
        try (var store = NioStoreComposition.coordinatedDelegate(root, new NioDurability())) {
            assertEquals(new ObjectWrite.Failed(StoreFailure.UNSAFE_NAMESPACE), store.publishObject(NAME, BYTES));
        }
        if (kind.equals("directory")) assertTrue(Files.isDirectory(target()));
        else { assertTrue(Files.isSymbolicLink(target())); assertArrayEquals(OTHER, Files.readAllBytes(root.resolve("outside"))); }
        noTemps();
    }

    @Test void copyBasedMoveForcesCanonicalAfterClosingOriginalStage() throws Exception {
        var events = new ArrayList<String>();
        var objects = new NioObjectStorage.Operations(p -> {}, NioCanonicalInstaller.COORDINATED_MOVE) {
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

    @ParameterizedTest @ValueSource(strings = {"root-sync", "stage-sync", "before-link", "after-link", "post-link-sync", "directory-sync"})
    void objectFailureBoundaryAndRecoveryRemainConservative(String point) throws Exception {
        var objects = new NioObjectStorage.Operations(p -> {}, NioCanonicalInstaller.COORDINATED_MOVE) {
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
        var objects = new NioObjectStorage.Operations(p -> {}, NioCanonicalInstaller.COORDINATED_MOVE) {
            @Override void at(String at) throws IOException { if (point.equals(at)) throw new IOException("injected"); }
        };
        try (var store = open(objects, vaults())) {
            assertInstanceOf(ObjectWrite.Uncertain.class, store.publishObject(NAME, BYTES));
        }
        assertArrayEquals(BYTES, Files.readAllBytes(target()));
    }

}

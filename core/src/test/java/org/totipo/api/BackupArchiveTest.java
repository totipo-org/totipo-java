package org.totipo.api;

import org.totipo.*;
import org.totipo.backup.*;
import org.totipo.spi.*;
import org.totipo.testing.MemoryVault;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.totipo.backup.BackupException.Reason.*;

@Timeout(30)
class BackupArchiveTest {
    @TempDir Path root;
    private static byte[] bootstrap;
    @BeforeAll static void template() {
        var source = new MemoryVault();
        try (var session = source.create()) { bootstrap = source.bootstrap.clone(); assertNotNull(session.vaultId()); }
    }
    private static MemoryVault source() { var source = new MemoryVault(); source.bootstrap = bootstrap.clone(); return source; }
    private static final class Store implements TotipoStore {
        final MemoryVault source;
        Runnable onScan = () -> { }, onRead = () -> { };
        boolean incomplete, unsupported, readUnavailable;
        int vaultReads, reads;
        Store(MemoryVault source) { this.source = source; }
        @Override public BoundedRead readVault(int expected) {
            vaultReads++;
            return new BoundedRead.Present(source.bootstrap);
        }
        @Override public ObjectScan scanObjects() { return scanObjects(8192); }
        @Override public ObjectScan scanObjects(int maximum) {
            if (unsupported) return TotipoStore.super.scanObjects(maximum);
            var entries = source.objects.keySet().stream().sorted().map(name ->
                    new ObjectEntry(new ObjectName(name), EntryKind.UNKNOWN, OptionalLong.empty())).toList();
            onScan.run();
            if (entries.size() > maximum) return new ObjectScan.Incomplete(entries.subList(0, maximum), StoreFailure.CAPACITY);
            return incomplete ? new ObjectScan.Incomplete(entries, StoreFailure.UNAVAILABLE) : new ObjectScan.Complete(entries);
        }
        @Override public BoundedRead readObject(ObjectName name, int expected) {
            reads++; onRead.run();
            var bytes = source.objects.get(name.value());
            if (readUnavailable) return new BoundedRead.Unavailable(StoreFailure.UNAVAILABLE);
            if (bytes == null) return new BoundedRead.Absent();
            if (bytes.length < expected) return new BoundedRead.Undersized(bytes.length);
            if (bytes.length > expected) return new BoundedRead.Oversized();
            return new BoundedRead.Present(bytes);
        }
        @Override public ObjectWrite publishObject(ObjectName name, byte[] bytes) { throw new AssertionError("Capture mutated source"); }
        @Override public VaultCreate createVault(byte[] bytes) { throw new AssertionError("Capture created vault"); }
        @Override public void close() { }
    }
    private static VaultSession open(Store store) {
        var session = assertInstanceOf(OpenResult.Opened.class, Totipo.open(store, "password".toCharArray())).session();
        PublicApiTest.finished(session); store.vaultReads = 0; store.reads = 0; return session;
    }
    private static TokenHead create(VaultSession session, String issuer) {
        SaveResult.Saved result;
        try (var secret = NewSecret.copyOf(new byte[]{1, 2, 3}); var editor = session.state().createToken()) {
            result = PublicApiTest.saved(editor.secret(secret).issuer(issuer).save());
        }
        return PublicApiTest.token(session, result.tokenId()).heads().get(0);
    }
    private static byte[] write(BackupCapture capture) throws IOException {
        var output = new ByteArrayOutputStream(); capture.writeTo(output); return output.toByteArray();
    }
    private static Map<String, byte[]> entries(byte[] zip) throws IOException {
        var entries = new LinkedHashMap<String, byte[]>();
        try (var in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) entries.put(entry.getName(), in.readAllBytes());
        }
        return entries;
    }
    @Test void emptyNioCaptureAndDetachedLifetime() throws Exception {
        Files.write(root.resolve("vault"), bootstrap);
        BackupCapture capture;
        try (var session = assertInstanceOf(OpenResult.Opened.class,
                org.totipo.storage.nio.NioTotipo.open(root, "password".toCharArray())).session()) {
            capture = session.captureBackup(BackupLimits.DEFAULT);
            assertEquals(0, capture.summary().objects());
        }
        try (capture) {
            byte[] zip = write(capture);
            assertArrayEquals(zip, write(capture));
            var inspection = BackupArchives.inspect(new ByteArrayInputStream(zip), BackupLimits.DEFAULT);
            assertEquals(capture.summary().vaultId(), inspection.vaultId());
            assertFalse(inspection.sourceAuthenticated()); assertTrue(capture.summary().sourceAuthenticated());
            assertEquals(capture.summary().archiveSha256(), inspection.archiveSha256());
            assertEquals(List.of("totipo-backup.txt", "vault"), new ArrayList<>(entries(zip).keySet()));
            assertArrayEquals(bootstrap, entries(zip).get("vault"));
        }
        capture.close(); assertThrows(IllegalStateException.class, () -> write(capture));
        try (var children = Files.list(root)) { assertEquals(1, children.count()); }
    }
    @Test void historyConflictsTombstonesMetadataAndMissingAncestryAreByteExact() throws Exception {
        var source = source();
        TokenHead ancestor;
        try (var author = source.open()) {
            ancestor = create(author, "ancestor");
            try (var update = author.state().update(ancestor)) {
                PublicApiTest.saved(update.issuer("branch one").metadata(new ClientMetadata(Optional.of("desktop"), Optional.of(0L))).save());
            }
            try (var update = author.state().update(ancestor)) {
                PublicApiTest.saved(update.issuer("branch two").status(TokenStatus.TOMBSTONED)
                        .metadata(new ClientMetadata(Optional.of("android"), Optional.of(-1L))).save());
            }
            assertEquals(3, source.objects.size());
        }
        var store = new Store(source);
        try (var session = open(store); var capture = session.captureBackup(BackupLimits.DEFAULT)) {
            assertEquals(3, capture.summary().objects()); assertEquals(0, capture.summary().unresolvedReferences());
            var entries = entries(write(capture));
            source.objects.forEach((name, bytes) -> assertArrayEquals(bytes, entries.get("objects-v1/" + name)));
            assertEquals(5, entries.size());
            source.objects.remove(ancestor.revision().hex());
            try (var limited = session.captureBackup(BackupLimits.DEFAULT)) {
                assertEquals(2, limited.summary().objects()); assertEquals(2, limited.summary().unresolvedReferences());
                assertEquals(2, BackupArchives.inspect(new ByteArrayInputStream(write(limited)), BackupLimits.DEFAULT).unresolvedReferences());
            }
            // Earlier frozen set survives source disappearance and newer state emissions.
            source.objects.clear(); session.requestRefresh();
            assertEquals(5, entries(write(capture)).size());
        }
    }
    @Test void externalPublicationAfterInventoryIsNotSilentlyAddedOrInvalidating() throws Exception {
        var source = source();
        try (var author = source.open()) { create(author, "first"); create(author, "second"); }
        String late = source.objects.keySet().iterator().next(); byte[] lateBytes = source.objects.remove(late);
        var store = new Store(source);
        try (var session = open(store)) {
            var executor = Executors.newSingleThreadExecutor();
            try {
                store.onScan = () -> {
                    try { executor.submit(() -> source.objects.put(late, lateBytes)).get(5, TimeUnit.SECONDS); }
                    catch (Exception e) { throw new AssertionError(e); }
                };
                try (var capture = session.captureBackup(BackupLimits.DEFAULT)) {
                    assertEquals(1, capture.summary().objects());
                    assertFalse(entries(write(capture)).containsKey("objects-v1/" + late));
                }
                try (var next = session.captureBackup(BackupLimits.DEFAULT)) { assertEquals(2, next.summary().objects()); }
            } finally { executor.shutdownNow(); }
        }
    }
    @Test void evidenceFailuresCapacityAndCancellationReturnNoCapture() throws Exception {
        var source = source();
        try (var author = source.open()) { create(author, "test"); }
        var store = new Store(source);
        try (var session = open(store)) {
            store.incomplete = true; failure(SOURCE_UNAVAILABLE, () -> session.captureBackup(BackupLimits.DEFAULT)); store.incomplete = false;
            store.unsupported = true; failure(UNSUPPORTED, () -> session.captureBackup(BackupLimits.DEFAULT)); store.unsupported = false;
            failure(CAPACITY, () -> session.captureBackup(new BackupLimits(0, 8192, 6 * 1024 * 1024)));
            failure(CAPACITY, () -> session.captureBackup(new BackupLimits(4096, 8192, 512)));
            source.objects.put("junk", new byte[1]);
            failure(CAPACITY, () -> session.captureBackup(new BackupLimits(4096, 1, 6 * 1024 * 1024)));
            source.objects.remove("junk");
            String name = source.objects.keySet().iterator().next(); byte[] exact = source.objects.get(name);
            store.readUnavailable = true; failure(SOURCE_UNAVAILABLE, () -> session.captureBackup(BackupLimits.DEFAULT)); store.readUnavailable = false;
            source.objects.put(name, new byte[1023]); failure(SOURCE_UNAVAILABLE, () -> session.captureBackup(BackupLimits.DEFAULT));
            source.objects.put(name, new byte[1025]); failure(SOURCE_UNAVAILABLE, () -> session.captureBackup(BackupLimits.DEFAULT));
            source.objects.put(name, new byte[1024]); failure(INVALID_OBJECT, () -> session.captureBackup(BackupLimits.DEFAULT));
            source.objects.put(name, exact);
            store.onRead = () -> source.objects.remove(name); failure(SOURCE_UNAVAILABLE, () -> session.captureBackup(BackupLimits.DEFAULT));
            store.onRead = () -> { }; source.objects.put(name, exact);
            Thread.currentThread().interrupt();
            try { failure(CANCELLED, () -> session.captureBackup(BackupLimits.DEFAULT)); }
            finally { assertTrue(Thread.interrupted()); }
            try (var good = session.captureBackup(BackupLimits.DEFAULT)) { assertEquals(1, good.summary().objects()); }
        }
    }
    @Test void vaultSubstitutionAndSessionClosureReject() throws Exception {
        var source = source(); var store = new Store(source);
        var opened = open(store);
        try (var session = opened) {
            byte[] original = source.bootstrap;
            store.onScan = () -> { source.bootstrap = original.clone(); source.bootstrap[86] ^= 1; };
            failure(VAULT_CHANGED, () -> session.captureBackup(BackupLimits.DEFAULT));
            source.bootstrap = original; store.onScan = () -> { };
        }
        assertThrows(SessionClosedException.class, () -> opened.captureBackup(BackupLimits.DEFAULT));
    }
    @Test void borrowedStreamsFailuresZeroReadsAndCancellation() throws Exception {
        try (var session = open(new Store(source())); var capture = session.captureBackup(BackupLimits.DEFAULT)) {
            byte[] expected = write(capture);
            var output = new ByteArrayOutputStream() {
                @Override public void close() { fail("Borrowed output closed"); }
                @Override public void flush() { fail("Borrowed output flushed"); }
            };
            capture.writeTo(output); assertArrayEquals(expected, output.toByteArray());
            assertThrows(IOException.class, () -> capture.writeTo(new OutputStream() {
                @Override public void write(int b) throws IOException { throw new IOException("broken output"); }
            }));
            for (int limit : List.of(100, expected.length - 15)) {
                assertThrows(IOException.class, () -> capture.writeTo(new OutputStream() {
                    int written;
                    @Override public void write(int b) throws IOException {
                        if (written++ == limit) throw new IOException("midstream/finish failure");
                    }
                    @Override public void close() { fail("Failed borrowed output closed"); }
                }));
            }
            assertArrayEquals(expected, write(capture));
            var input = new ByteArrayInputStream(expected) {
                boolean zero;
                @Override public synchronized int read(byte[] b, int off, int len) {
                    zero = !zero; return zero ? 0 : super.read(b, off, Math.min(7, len));
                }
                @Override public void close() { fail("Borrowed input closed"); }
            };
            assertEquals(capture.summary().archiveSha256(), BackupArchives.inspect(input, BackupLimits.DEFAULT).archiveSha256());
            assertThrows(IOException.class, () -> BackupArchives.inspect(new InputStream() {
                @Override public int read() throws IOException { throw new IOException("broken input"); }
            }, BackupLimits.DEFAULT));
            Thread.currentThread().interrupt();
            try {
                failure(CANCELLED, () -> capture.writeTo(output));
                failure(CANCELLED, () -> BackupArchives.inspect(new ByteArrayInputStream(expected), BackupLimits.DEFAULT));
            } finally { Thread.interrupted(); }
            assertArrayEquals(expected, write(capture));
        }
    }
    @Test void captureHoldsProviderGateButDetachedWriteDoesNotHoldSessionOpen() throws Exception {
        var source = source();
        try (var author = source.open()) { create(author, "gate"); }
        var store = new Store(source); var session = open(store);
        var executor = Executors.newFixedThreadPool(2);
        var entered = new CountDownLatch(1); var proceed = new CountDownLatch(1);
        try {
            store.onRead = () -> {
                entered.countDown();
                try { assertTrue(proceed.await(10, TimeUnit.SECONDS)); }
                catch (InterruptedException e) { throw new AssertionError(e); }
            };
            var capturing = executor.submit(() -> session.captureBackup(BackupLimits.DEFAULT));
            assertTrue(entered.await(10, TimeUnit.SECONDS));
            var closing = executor.submit(session::close);
            // The root cannot be wiped while the admitted capture uses it.
            assertThrows(TimeoutException.class, () -> closing.get(100, TimeUnit.MILLISECONDS));
            proceed.countDown();
            try (var capture = capturing.get(10, TimeUnit.SECONDS)) {
                closing.get(10, TimeUnit.SECONDS);
                assertEquals(1, BackupArchives.inspect(new ByteArrayInputStream(write(capture)), BackupLimits.DEFAULT).objects());
            }
        } finally { proceed.countDown(); executor.shutdownNow(); session.close(); }
    }
    @Test void nioCaptureRejectsUnreadableSupportedKindsAndIgnoresUnrelatedNames() throws Exception {
        Files.write(root.resolve("vault"), bootstrap);
        var directory = Files.createDirectory(root.resolve("objects-v1"));
        Files.write(directory.resolve("unrelated"), new byte[]{1});
        String canonical = "a".repeat(64);
        Files.createSymbolicLink(directory.resolve(canonical), root.resolve("vault"));
        try (var session = assertInstanceOf(OpenResult.Opened.class,
                org.totipo.storage.nio.NioTotipo.open(root, "password".toCharArray())).session()) {
            failure(SOURCE_UNAVAILABLE, () -> session.captureBackup(BackupLimits.DEFAULT));
            Files.delete(directory.resolve(canonical)); Files.createDirectory(directory.resolve(canonical));
            failure(SOURCE_UNAVAILABLE, () -> session.captureBackup(BackupLimits.DEFAULT));
            Files.delete(directory.resolve(canonical));
            try (var capture = session.captureBackup(BackupLimits.DEFAULT)) { assertEquals(0, capture.summary().objects()); }
        }
    }
    @FunctionalInterface private interface IOAction { void run() throws Exception; }
    private static void failure(BackupException.Reason reason, IOAction action) {
        assertEquals(reason, assertThrows(BackupException.class, action::run).reason());
    }
}

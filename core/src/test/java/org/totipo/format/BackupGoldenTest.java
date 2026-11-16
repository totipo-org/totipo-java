package org.totipo.format;

import org.totipo.*;
import org.totipo.backup.*;
import org.totipo.spi.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

class BackupGoldenTest {
    private static byte[] fixture(String name) throws IOException {
        try (var input = BackupGoldenTest.class.getResourceAsStream("/backup/profile-1/" + name + ".zip")) {
            return Objects.requireNonNull(input).readAllBytes();
        }
    }
    private static Map<String, byte[]> entries(byte[] bytes) throws IOException {
        var entries = new TreeMap<String, byte[]>();
        try (var input = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry; while ((entry = input.getNextEntry()) != null) entries.put(entry.getName(), input.readAllBytes());
        }
        return entries;
    }
    private static final class Store implements TotipoStore {
        final byte[] vault; final Map<String, byte[]> objects = new TreeMap<>();
        Store(byte[] fixture) throws IOException {
            var entries = entries(fixture); vault = entries.get("vault");
            entries.forEach((name, bytes) -> { if (name.startsWith("objects-v1/")) objects.put(name.substring(11), bytes); });
        }
        @Override public BoundedRead readVault(int expected) { return new BoundedRead.Present(vault); }
        @Override public BoundedRead readObject(ObjectName name, int expected) { return new BoundedRead.Present(objects.get(name.value())); }
        @Override public ObjectScan scanObjects() { return scanObjects(8192); }
        @Override public ObjectScan scanObjects(int bound) {
            var entries = objects.keySet().stream().map(name -> new ObjectEntry(new ObjectName(name), EntryKind.REGULAR, OptionalLong.of(1024))).toList();
            return entries.size() <= bound ? new ObjectScan.Complete(entries)
                    : new ObjectScan.Incomplete(entries.subList(0, bound), StoreFailure.CAPACITY);
        }
        @Override public ObjectWrite publishObject(ObjectName name, byte[] bytes) { throw new AssertionError("No writes"); }
        @Override public VaultCreate createVault(byte[] bytes) { throw new AssertionError("No create"); }
        @Override public void close() { }
    }
    @ParameterizedTest @ValueSource(strings = {"empty", "history", "missing-parent"})
    void sharedGoldenIsAuthenticatedAndWriterMatchesEveryByte(String name) throws Exception {
        byte[] golden = fixture(name); var store = new Store(golden);
        assertEquals(Map.of("empty", "603eb0dd84f67678bab82c78ee684dc22592b5e4de37f24d16f5694c382fa425",
                "history", "369b77c872a32f2474d867e0653e39b66befdfc6cbd1244da486505ba8bca95b",
                "missing-parent", "6991ee0b764409728a10ee6612d6eced56207dbefa58a35611ea3d6112c25e58").get(name), BackupZip.hash(golden));
        try (var session = assertInstanceOf(OpenResult.Opened.class, Totipo.open(store, "fixture password".toCharArray())).session();
             var capture = session.captureBackup(BackupLimits.DEFAULT)) {
            var output = new ByteArrayOutputStream(); capture.writeTo(output);
            assertArrayEquals(golden, output.toByteArray());
            var summary = BackupArchives.inspect(new ByteArrayInputStream(golden), BackupLimits.DEFAULT);
            assertEquals(capture.summary().archiveSha256(), summary.archiveSha256());
            assertEquals(name.equals("missing-parent") ? 1 : 0, summary.unresolvedReferences());
            assertEquals("8bef25c2a2b81a0691b4f719eabed9c001a1778af415ddccab380eba9b408351", summary.vaultId().hex());
            for (var object : store.objects.entrySet()) {
                assertInstanceOf(ObjectCandidateValidation.Valid.class,
                        session.validateObject(new RevisionId(object.getKey()), object.getValue()));
            }
        }
    }
    @Test void wrongTokenParentIsAnExplicitGapNotAnInvalidChild() throws Exception {
        var store = new Store(fixture("history"));
        byte[] root = new byte[32]; Arrays.fill(root, (byte) 0x11);
        ObjectId parent = ObjectId.fromFilename(store.objects.keySet().iterator().next());
        var secret = new SecurityBytes(new byte[]{1, 2, 3}, 3);
        try {
            var value = new TokenValue(1, "wrong-token parent", "", new TokenValue.Credential(1, 6, 30, secret));
            var token = new TokenObject(new TokenId(new byte[32]), List.of(parent), value, new TokenMetadata(Optional.empty(), Optional.empty()));
            var envelope = V1EnvelopeWriter.seal(root, TokenWriter.write(token));
            store.objects.put(envelope.id().filename(), envelope.bytes());
            try (var session = assertInstanceOf(OpenResult.Opened.class, Totipo.open(store, "fixture password".toCharArray())).session();
                 var capture = session.captureBackup(BackupLimits.DEFAULT)) {
                assertEquals(4, capture.summary().objects()); assertEquals(1, capture.summary().unresolvedReferences());
            }
        } finally { secret.clear(); Arrays.fill(root, (byte) 0); }
    }
    @Test void canonicalWriterMatchesGoldenBytesInEveryAvailableTimeZone() throws Exception {
        var previous = TimeZone.getDefault();
        try {
            for (String name : List.of("empty", "history", "missing-parent")) {
                byte[] golden = fixture(name); var store = new Store(golden);
                byte[] spool = new byte[store.objects.size() * 1024]; int offset = 0;
                for (byte[] object : store.objects.values()) {
                    System.arraycopy(object, 0, spool, offset, object.length); offset += object.length;
                }
                for (String zone : TimeZone.getAvailableIDs()) {
                    TimeZone.setDefault(TimeZone.getTimeZone(zone));
                    try (var capture = new BackupZip.Frozen(store.vault.clone(), spool.clone(),
                            new ArrayList<>(store.objects.keySet()), name.equals("missing-parent") ? 1 : 0, BackupLimits.DEFAULT)) {
                        var output = new ByteArrayOutputStream(); capture.writeTo(output);
                        assertArrayEquals(golden, output.toByteArray(), name + " in " + zone);
                        assertEquals(BackupZip.hash(golden), capture.summary().archiveSha256());
                    }
                }
            }
        } finally { TimeZone.setDefault(previous); }
    }
    @Test void maximumCapturePreservesMoreThan512ObjectsAndExactBoundary() throws Exception {
        var store = new Store(fixture("empty"));
        byte[] root = new byte[32]; Arrays.fill(root, (byte) 0x11);
        var secret = new SecurityBytes(new byte[]{1, 2, 3}, 3);
        try {
            var value = new TokenValue(1, "capacity", "", new TokenValue.Credential(1, 6, 30, secret));
            // A chain models actual immutable history; no heads-only shortcut can pass.
            var parents = List.<ObjectId>of();
            for (int n = 0; n < 4096; n++) {
                var token = new TokenObject(new TokenId(new byte[32]), parents, value,
                        new TokenMetadata(Optional.of("capacity"), Optional.of(new UInt64(n))));
                var envelope = V1EnvelopeWriter.seal(root, TokenWriter.write(token));
                store.objects.put(envelope.id().filename(), envelope.bytes()); parents = List.of(envelope.id());
            }
            try (var session = assertInstanceOf(OpenResult.Opened.class, Totipo.open(store, "fixture password".toCharArray())).session();
                 var capture = session.captureBackup(BackupLimits.DEFAULT)) {
                assertEquals(4096, capture.summary().objects()); assertEquals(0, capture.summary().unresolvedReferences());
                var output = new ByteArrayOutputStream(); capture.writeTo(output); byte[] zip = output.toByteArray();
                var summary = BackupArchives.inspect(new ByteArrayInputStream(zip), BackupLimits.DEFAULT);
                assertEquals(4096, summary.objects()); assertEquals(capture.summary().archiveSha256(), summary.archiveSha256());
                assertEquals(5_120_525, zip.length);
                assertEquals(BackupException.Reason.CAPACITY, assertThrows(BackupException.class,
                        () -> session.captureBackup(new BackupLimits(4095, 8192, 6 * 1024 * 1024))).reason());
                var extra = V1EnvelopeWriter.seal(root, TokenWriter.write(new TokenObject(new TokenId(new byte[32]),
                        parents, value, new TokenMetadata(Optional.of("capacity"), Optional.of(new UInt64(4096))))));
                store.objects.put(extra.id().filename(), extra.bytes());
                assertEquals(BackupException.Reason.CAPACITY, assertThrows(BackupException.class,
                        () -> session.captureBackup(BackupLimits.DEFAULT)).reason());
            }
        } finally { secret.clear(); Arrays.fill(root, (byte) 0); }
    }
}

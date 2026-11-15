package org.totipo.format;

import org.totipo.backup.*;
import org.junit.jupiter.api.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

class NewLocationRestoreTest {
    static byte[] fixture(String name) throws IOException {
        try (var in = NewLocationRestoreTest.class.getResourceAsStream("/backup/profile-1/" + name + ".zip")) {
            return Objects.requireNonNull(in).readAllBytes();
        }
    }
    static BackupRestore prepare(byte[] bytes, char[] password) throws IOException {
        return BackupArchives.prepareRestore(new ByteArrayInputStream(bytes), BackupLimits.DEFAULT, password);
    }
    @Test void detachedPlansAuthenticateAllGoldenArchivesAndRetainNoCallerPassword() throws Exception {
        for (String name : List.of("empty", "history", "missing-parent")) {
            char[] password = "fixture password".toCharArray();
            byte[] source = fixture(name);
            var plan = prepare(source, password);
            assertArrayEquals("fixture password".toCharArray(), password);
            Arrays.fill(password, 'x'); Arrays.fill(source, (byte) 0);
            assertTrue(plan.summary().sourceAuthenticated());
            assertEquals(name.equals("empty") ? 0 : name.equals("history") ? 3 : 1, plan.summary().objects());
            assertEquals(name.equals("missing-parent") ? 1 : 0, plan.summary().unresolvedReferences());
            plan.close(); plan.close();
            assertThrows(IllegalStateException.class, plan::summary);
            assertThrows(IllegalStateException.class, plan::retry);
        }
    }
    @Test void wrongPasswordInvalidEncodingAndMalformedArchiveReturnNoPlan() throws Exception {
        assertEquals(BackupException.Reason.AUTHENTICATION_FAILED,
                assertThrows(BackupException.class, () -> prepare(fixture("history"), "wrong".toCharArray())).reason());
        assertThrows(IllegalArgumentException.class, () -> prepare(fixture("empty"), new char[]{'\uD800'}));
        byte[] corrupt = fixture("history"); corrupt[0] = 0;
        assertEquals(BackupException.Reason.MALFORMED,
                assertThrows(BackupException.class, () -> prepare(corrupt, new char[0])).reason());
    }
    @Test void completeStructureAndEveryAuthenticatedObjectAreRequired() throws Exception {
        var entries = new TreeMap<String, byte[]>();
        try (var zip = new ZipInputStream(new ByteArrayInputStream(fixture("history")))) {
            ZipEntry e; while ((e = zip.getNextEntry()) != null) entries.put(e.getName(), zip.readAllBytes());
        }
        byte[] vault = entries.get("vault"), root = new byte[32]; Arrays.fill(root, (byte) 0x11);
        var objects = new TreeMap<String, byte[]>();
        entries.forEach((k, v) -> { if (k.startsWith("objects-v1/")) objects.put(k.substring(11), v); });
        var badGrammar = V1EnvelopeWriter.seal(root, new byte[]{0});
        byte[] corrupted = objects.firstEntry().getValue().clone(); corrupted[1023] ^= 1;
        for (var invalid : List.of(Map.of(badGrammar.id().filename(), badGrammar.bytes()),
                Map.of("0".repeat(64), objects.firstEntry().getValue()),
                Map.of(objects.firstKey(), corrupted))) {
            var all = new TreeMap<>(objects); all.putAll(invalid);
            byte[] spool = new byte[1024 * all.size()]; int offset = 0;
            for (byte[] bytes : all.values()) { System.arraycopy(bytes, 0, spool, offset, 1024); offset += 1024; }
            var out = new ByteArrayOutputStream();
            try (var frozen = new BackupZip.Frozen(vault.clone(), spool, List.copyOf(all.keySet()), 0, BackupLimits.DEFAULT)) {
                frozen.writeTo(out);
            }
            assertEquals(BackupException.Reason.INVALID_OBJECT, assertThrows(BackupException.class,
                    () -> prepare(out.toByteArray(), "fixture password".toCharArray())).reason());
        }
        Arrays.fill(root, (byte) 0);
    }
    @Test void borrowedShortZeroReadStreamCapacityAndInterruption() throws Exception {
        byte[] bytes = fixture("history");
        var input = new ByteArrayInputStream(bytes) {
            boolean zero;
            @Override public synchronized int read(byte[] b, int off, int len) {
                zero = !zero; return zero ? 0 : super.read(b, off, Math.min(5, len));
            }
            @Override public void close() { fail("Borrowed stream closed"); }
        };
        try (var plan = BackupArchives.prepareRestore(input, BackupLimits.DEFAULT, "fixture password".toCharArray())) {
            assertEquals(3, plan.summary().objects());
        }
        assertEquals(BackupException.Reason.CAPACITY, assertThrows(BackupException.class,
                () -> BackupArchives.prepareRestore(new ByteArrayInputStream(bytes), new BackupLimits(0, 8192, 6 * 1024 * 1024), new char[0])).reason());
        Thread.currentThread().interrupt();
        try { assertEquals(BackupException.Reason.CANCELLED, assertThrows(BackupException.class,
                () -> prepare(bytes, new char[0])).reason()); }
        finally { Thread.interrupted(); }
    }
}

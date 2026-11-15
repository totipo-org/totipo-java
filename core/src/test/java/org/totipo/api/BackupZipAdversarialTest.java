package org.totipo.api;

import org.totipo.backup.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

class BackupZipAdversarialTest {
    static byte[] fixture(String name) throws IOException {
        try (var input = BackupZipAdversarialTest.class.getResourceAsStream("/backup/profile-1/" + name + ".zip")) {
            return Objects.requireNonNull(input).readAllBytes();
        }
    }
    private static BackupSummary inspect(byte[] bytes) throws IOException {
        return BackupArchives.inspect(new ByteArrayInputStream(bytes), BackupLimits.DEFAULT);
    }
    private static int u16(byte[] bytes, int p) { return (bytes[p] & 255) | (bytes[p + 1] & 255) << 8; }
    private static int u32(byte[] bytes, int p) { return u16(bytes, p) | u16(bytes, p + 2) << 16; }
    private static void put16(byte[] bytes, int p, int v) { bytes[p] = (byte) v; bytes[p + 1] = (byte) (v >>> 8); }
    private static void put32(byte[] bytes, int p, int v) { put16(bytes, p, v); put16(bytes, p + 2, v >>> 16); }
    private static List<Integer> central(byte[] bytes) {
        int p = u32(bytes, bytes.length - 6); var positions = new ArrayList<Integer>();
        while (u32(bytes, p) == 0x02014b50) { positions.add(p); p += 46 + u16(bytes, p + 28); }
        return positions;
    }
    private static int local(byte[] bytes, int central) { return u32(bytes, central + 42); }
    private static void rejected(byte[] bytes) { assertThrows(BackupException.class, () -> inspect(bytes)); }
    @Test void everyTruncationAndTrailingByteRejected() throws Exception {
        byte[] valid = fixture("history");
        assertEquals(3, inspect(valid).objects());
        // Small ingress budget keeps the exhaustive truncation campaign allocation bounded.
        var limits = new BackupLimits(3, 8, 5000);
        for (int n = 0; n < valid.length; n++) {
            int size = n;
            assertThrows(BackupException.class, () -> BackupArchives.inspect(new ByteArrayInputStream(valid, 0, size), limits), "length " + n);
        }
        rejected(Arrays.copyOf(valid, valid.length + 1));
        var prefixed = new byte[valid.length + 1]; System.arraycopy(valid, 0, prefixed, 1, valid.length); rejected(prefixed);
        var concatenated = Arrays.copyOf(valid, 2 * valid.length); System.arraycopy(valid, 0, concatenated, valid.length, valid.length); rejected(concatenated);
    }
    @Test void centralAndLocalAmbiguitiesAreRejected() throws Exception {
        byte[] valid = fixture("history"); var positions = central(valid);
        // Individual metadata disagreement, unsupported features, ZIP64 sentinel, disks, special attributes.
        for (int offset : List.of(0, 4, 6, 8, 10, 12, 14, 16, 20, 24, 28, 30, 32, 34, 36, 38, 42, 46)) {
            byte[] bad = valid.clone(); bad[positions.get(0) + offset] ^= 1; rejected(bad);
        }
        for (int offset : List.of(0, 4, 6, 8, 10, 12, 14, 18, 22, 26, 28, 30)) {
            byte[] bad = valid.clone(); bad[offset] ^= 1; rejected(bad);
        }
        for (int offset : List.of(0, 4, 6, 8, 10, 12, 16, 20)) {
            byte[] bad = valid.clone(); bad[bad.length - 22 + offset] ^= 1; rejected(bad);
        }
        for (int bits : List.of(1, 8, 64, 0xffff)) {
            byte[] bad = valid.clone(); int c = positions.get(0);
            put16(bad, 6, bits); put16(bad, c + 8, bits); rejected(bad);
        }
        byte[] zip64 = valid.clone(); put16(zip64, 4, 45); put16(zip64, positions.get(0) + 6, 45); rejected(zip64);
        byte[] link = valid.clone(); put16(link, positions.get(0) + 4, 0x314); put32(link, positions.get(0) + 38, 0120777 << 16); rejected(link);
        byte[] repeatedOffset = valid.clone(); put32(repeatedOffset, positions.get(1) + 42, 0); rejected(repeatedOffset);
        byte[] orphan = valid.clone(); put16(orphan, orphan.length - 12, 2); put16(orphan, orphan.length - 14, 2); rejected(orphan);
        byte[] crc = valid.clone(); int data = local(valid, positions.get(2)) + 30 + 75; crc[data + 17] ^= 1; rejected(crc);
        // Equal CRC mutation in both headers still must match the real payload.
        byte[] crcLie = valid.clone(); crcLie[14] ^= 1; crcLie[positions.get(0) + 16] ^= 1; rejected(crcLie);
    }
    @ParameterizedTest @ValueSource(strings = {
        "../", "/absolute", "objects-v1/../x", "objects-v1/UPPER", "objects-v1\\escape", "vault/", "VAULT", "unknown", "objects-v2/", "\u0000bad"
    })
    void unsafeOrUnknownNamesAreRejected(String name) throws Exception {
        byte[] valid = fixture("history"); int c = central(valid).get(2), l = local(valid, c);
        byte[] nameBytes = (name + "x".repeat(75)).substring(0, 75).getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(nameBytes, 0, valid, l + 30, 75); System.arraycopy(nameBytes, 0, valid, c + 46, 75); rejected(valid);
    }
    @Test void duplicateAndUnsortedNamesRejectedEvenWithDistinctValidPayloads() throws Exception {
        byte[] valid = fixture("history"); var positions = central(valid); int c1 = positions.get(2), c2 = positions.get(3);
        byte[] duplicate = valid.clone();
        System.arraycopy(valid, c1 + 46, duplicate, c2 + 46, 75);
        System.arraycopy(valid, c1 + 46, duplicate, local(valid, c2) + 30, 75); rejected(duplicate);
        byte[] unsorted = valid.clone();
        System.arraycopy(valid, c2 + 46, unsorted, c1 + 46, 75);
        System.arraycopy(valid, c2 + 46, unsorted, local(valid, c1) + 30, 75);
        System.arraycopy(valid, c1 + 46, unsorted, c2 + 46, 75);
        System.arraycopy(valid, c1 + 46, unsorted, local(valid, c2) + 30, 75); rejected(unsorted);
    }
    private static byte[] repack(byte[] original, java.util.function.UnaryOperator<byte[]> manifestChange, boolean deflate) throws IOException {
        var output = new ByteArrayOutputStream();
        try (var input = new ZipInputStream(new ByteArrayInputStream(original)); var zip = new ZipOutputStream(output)) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                byte[] data = input.readAllBytes(); if (entry.getName().equals("totipo-backup.txt")) data = manifestChange.apply(data);
                var fresh = new ZipEntry(entry.getName()); fresh.setTimeLocal(java.time.LocalDateTime.of(1980, 1, 2, 0, 0));
                if (!deflate) {
                    fresh.setMethod(ZipEntry.STORED); fresh.setSize(data.length); var crc = new CRC32(); crc.update(data); fresh.setCrc(crc.getValue());
                }
                zip.putNextEntry(fresh); zip.write(data); zip.closeEntry();
            }
        }
        return output.toByteArray();
    }
    @ParameterizedTest @ValueSource(strings = {
        "totipo-backup 1|totipo-backup 2", "protocol totipo-v1|protocol totipo-v2",
        "object-count 3|object-count 03", "object-count 3|object-count 2",
        "unresolved-references 0|unresolved-references 13", "unresolved-references 0|unresolved-references -1",
        "unresolved-references 0|unresolved-references 9999999999999999999999",
        "authentication checked|authentication unchecked", "enumeration complete|enumeration incomplete",
        "consistency observed-set|consistency coordinated-local", "8bef25c2|0bef25c2", "\n|\r\n"
    })
    void malformedOrUnsupportedManifestIsRejected(String replacement) throws Exception {
        String[] parts = replacement.split("\\|", -1);
        rejected(repack(fixture("history"), bytes -> new String(bytes, StandardCharsets.US_ASCII)
                .replace(parts[0], parts[1]).getBytes(StandardCharsets.US_ASCII), false));
    }
    @Test void deflateDescriptorsAndCapacityAreRejected() throws Exception {
        byte[] valid = fixture("history"); rejected(repack(valid, b -> b, true));
        assertEquals(3, BackupArchives.inspect(new ByteArrayInputStream(valid), new BackupLimits(3, 8, valid.length)).objects());
        assertEquals(BackupException.Reason.CAPACITY, assertThrows(BackupException.class,
                () -> BackupArchives.inspect(new ByteArrayInputStream(valid), new BackupLimits(3, 8, valid.length - 1))).reason());
        assertEquals(BackupException.Reason.CAPACITY, assertThrows(BackupException.class,
                () -> BackupArchives.inspect(new ByteArrayInputStream(valid), new BackupLimits(2, 8, valid.length))).reason());
        assertThrows(IllegalArgumentException.class, () -> new BackupLimits(4097, 8192, 6 * 1024 * 1024));
    }
    @Test void arbitrarySmallInputsNeverEscapeAsUncheckedParserExceptions() throws Exception {
        var random = new Random(19); var limits = new BackupLimits(4, 8, 512);
        for (int n = 0; n < 1000; n++) {
            byte[] bytes = new byte[random.nextInt(512)]; random.nextBytes(bytes);
            assertThrows(BackupException.class, () -> BackupArchives.inspect(new ByteArrayInputStream(bytes), limits));
        }
    }
    @Test void structuralInspectionNeverClaimsObjectAuthentication() throws Exception {
        byte[] bytes = fixture("history"); int c = central(bytes).get(2), l = local(bytes, c);
        int start = l + 30 + 75;
        bytes[start] ^= 1;
        var crc = new CRC32(); crc.update(bytes, start, 1024);
        put32(bytes, l + 14, (int) crc.getValue()); put32(bytes, c + 16, (int) crc.getValue());
        assertFalse(inspect(bytes).sourceAuthenticated());
    }
}

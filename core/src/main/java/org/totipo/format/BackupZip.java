package org.totipo.format;

import org.totipo.*;
import org.totipo.backup.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.zip.*;
import static org.totipo.backup.BackupException.Reason.*;

/** Internal two-module/API bridge. Narrow ZIP profile verification, not a general ZIP parser. */
public final class BackupZip {
    private BackupZip() { }
    static final String MANIFEST = "totipo-backup.txt";
    static void cancelled() throws BackupException {
        if (Thread.currentThread().isInterrupted()) throw new BackupException(CANCELLED);
    }
    static MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    static String hash(byte[] bytes) { return HexFormat.of().formatHex(digest().digest(bytes)); }
    static byte[] manifest(VaultId id, int count, int gaps) {
        return ("totipo-backup 1\nprotocol totipo-v1\nvault-id " + id.hex()
                + "\nconsistency observed-set\nenumeration complete\nauthentication checked\nobject-count "
                + count + "\nunresolved-references " + gaps + "\n").getBytes(StandardCharsets.US_ASCII);
    }
    static final class Frozen implements BackupCapture {
        private byte[] vault, spool;
        private final List<String> names;
        private final BackupSummary summary;
        private final byte[] manifest;
        Frozen(byte[] vault, byte[] spool, List<String> names, int gaps, BackupLimits limits) throws IOException {
            this.vault = vault; this.spool = spool; this.names = List.copyOf(names);
            var id = Totipo.vaultId(vault);
            manifest = manifest(id, names.size(), gaps);
            // Exact classic STORED size: local + central headers, names twice, data, EOCD.
            long size = 22 + 76 + 2L * MANIFEST.length() + manifest.length + 76 + 10 + 87
                    + names.size() * (76L + 2 * 75 + 1024);
            if (size > limits.archiveBytes()) throw new BackupException(CAPACITY);
            var digest = digest();
            write(new DigestOutputStream(OutputStream.nullOutputStream(), digest));
            summary = new BackupSummary(id, names.size(), gaps, true, HexFormat.of().formatHex(digest.digest()));
        }
        private void active() { if (spool == null) throw new IllegalStateException("Capture closed"); }
        @Override public synchronized BackupSummary summary() { active(); return summary; }
        @Override public synchronized void writeTo(OutputStream output) throws IOException {
            active(); write(Objects.requireNonNull(output));
        }
        private void write(OutputStream output) throws IOException {
            // Closing releases the JDK Deflater but must not close/flush the borrowed stream.
            try (var zip = new ZipOutputStream(new FilterOutputStream(output) {
                @Override public void write(byte[] b, int off, int len) throws IOException { out.write(b, off, len); }
                @Override public void close() { }
            }, StandardCharsets.UTF_8)) {
                entry(zip, MANIFEST, manifest, 0, manifest.length);
                entry(zip, "vault", vault, 0, vault.length);
                for (int i = 0; i < names.size(); i++) entry(zip, "objects-v1/" + names.get(i), spool, i * 1024, 1024);
                zip.finish();
            }
        }
        @Override public synchronized void close() {
            if (spool == null) return;
            Arrays.fill(spool, (byte) 0); Arrays.fill(vault, (byte) 0); spool = null; vault = null;
        }
    }
    private static void entry(ZipOutputStream zip, String name, byte[] data, int offset, int length) throws IOException {
        cancelled();
        var entry = new ZipEntry(name);
        entry.setMethod(ZipEntry.STORED); entry.setSize(length); entry.setCompressedSize(length);
        var crc = new CRC32(); crc.update(data, offset, length); entry.setCrc(crc.getValue());
        entry.setTimeLocal(LocalDateTime.of(1980, 1, 2, 0, 0));
        zip.putNextEntry(entry); zip.write(data, offset, length); zip.closeEntry();
    }

    /** Internal bridge; applications use BackupArchives.inspect. */
    public static BackupSummary inspect(InputStream input, BackupLimits limits) throws IOException {
        try (var archive = read(input, limits)) { return archive.summary; }
    }
    /** Owned ingress; constructed only after complete profile verification. */
    static final class Archive implements AutoCloseable {
        private byte[] bytes;
        final BackupSummary summary;
        private final List<Record> records;
        Archive(byte[] bytes, BackupSummary summary, List<Record> records) {
            this.bytes = bytes; this.summary = summary; this.records = List.copyOf(records);
        }
        byte[] payload(int index) {
            var record = records.get(index);
            int offset = record.offset + 30 + record.name.length();
            return Arrays.copyOfRange(bytes, offset, offset + record.size);
        }
        String name(int index) { return records.get(index + 2).name.substring(11); }
        @Override public void close() { if (bytes != null) { Arrays.fill(bytes, (byte) 0); bytes = null; } }
    }
    static Archive read(InputStream input, BackupLimits limits) throws IOException {
        Objects.requireNonNull(input); Objects.requireNonNull(limits);
        // Fixed allocation prevents ByteArrayOutputStream geometric growth/copies on hostile ingress.
        byte[] bytes = new byte[limits.archiveBytes()];
        boolean transferred = false;
        try {
            int used = 0;
            while (used < bytes.length) {
                cancelled();
                int n = input.read(bytes, used, bytes.length - used);
                if (n < 0) break;
                if (n == 0) { int one = input.read(); if (one < 0) break; bytes[used++] = (byte) one; }
                else used += n;
            }
            cancelled();
            if (used == bytes.length && input.read() != -1) throw new BackupException(CAPACITY);
            var records = new ArrayList<Record>();
            var summary = verify(bytes, used, limits, records);
            var archive = new Archive(bytes, summary, records);
            transferred = true;
            return archive;
        } finally { if (!transferred) Arrays.fill(bytes, (byte) 0); }
    }
    private record Record(String name, int offset, int size, long crc, int flags, int time, int date) { }
    private static int u16(byte[] b, int p) throws BackupException {
        range(b.length, p, 2); return (b[p] & 255) | (b[p + 1] & 255) << 8;
    }
    private static long u32(byte[] b, int p) throws BackupException {
        return Integer.toUnsignedLong(u16(b, p) | u16(b, p + 2) << 16);
    }
    private static void range(int end, int p, int length) throws BackupException {
        if (p < 0 || length < 0 || p > end - length) throw new BackupException(MALFORMED);
    }
    private static void require(boolean value) throws BackupException { if (!value) throw new BackupException(MALFORMED); }
    private static void supported(boolean value) throws BackupException { if (!value) throw new BackupException(UNSUPPORTED); }
    private static String ascii(byte[] b, int p, int n) throws BackupException {
        range(b.length, p, n);
        for (int i = p; i < p + n; i++) require(b[i] >= 32 && b[i] <= 126);
        return new String(b, p, n, StandardCharsets.US_ASCII);
    }
    private static BackupSummary verify(byte[] b, int end, BackupLimits limits, List<Record> verified) throws IOException {
        require(end >= 22);
        int eocd = end - 22;
        require(u32(b, eocd) == 0x06054b50L);
        supported(u16(b, eocd + 4) == 0 && u16(b, eocd + 6) == 0 && u16(b, eocd + 20) == 0);
        int count = u16(b, eocd + 10);
        require(count == u16(b, eocd + 8) && count >= 2);
        if (count > limits.objects() + 2) throw new BackupException(CAPACITY);
        long central = u32(b, eocd + 16), centralSize = u32(b, eocd + 12);
        require(central <= eocd && centralSize == eocd - central);
        var records = new ArrayList<Record>(count);
        int p = 0;
        String previous = "";
        for (int i = 0; i < count; i++) {
            cancelled(); range((int) central, p, 30); require(u32(b, p) == 0x04034b50L);
            supported(u16(b, p + 4) == 10 && (u16(b, p + 6) & ~0x800) == 0 && u16(b, p + 8) == 0);
            int n = u16(b, p + 26);
            supported(u16(b, p + 28) == 0);
            require(n <= 75); range((int) central, p + 30, n);
            String name = ascii(b, p + 30, n);
            if (i == 0) require(name.equals(MANIFEST));
            else if (i == 1) require(name.equals("vault"));
            else { require(name.matches("objects-v1/[0-9a-f]{64}") && name.compareTo(previous) > 0); previous = name; }
            long size = u32(b, p + 22);
            require(size == u32(b, p + 18));
            require(i == 0 ? size <= 512 : size == (i == 1 ? 87 : 1024));
            range((int) central, p + 30 + n, (int) size);
            records.add(new Record(name, p, (int) size, u32(b, p + 14), u16(b, p + 6), u16(b, p + 10), u16(b, p + 12)));
            p += 30 + n + (int) size;
        }
        require(p == central);
        for (var record : records) {
            cancelled(); range(eocd, p, 46); require(u32(b, p) == 0x02014b50L);
            // No host-dependent filesystem attributes: profile uses DOS host and zero attributes.
            supported(u16(b, p + 4) == 10 && u16(b, p + 6) == 10 && u16(b, p + 30) == 0
                    && u16(b, p + 32) == 0 && u16(b, p + 34) == 0 && u16(b, p + 36) == 0 && u32(b, p + 38) == 0);
            require(u16(b, p + 8) == record.flags && u16(b, p + 10) == 0
                    && u16(b, p + 12) == record.time && u16(b, p + 14) == record.date
                    && u32(b, p + 16) == record.crc && u32(b, p + 20) == record.size
                    && u32(b, p + 24) == record.size && u32(b, p + 42) == record.offset);
            int n = u16(b, p + 28); range(eocd, p + 46, n);
            require(ascii(b, p + 46, n).equals(record.name)); p += 46 + n;
        }
        require(p == eocd);
        // JDK reads payloads and independently checks each CRC. Restricted layout above is necessary:
        // ZipInputStream alone deliberately does not validate the central directory or trailing data.
        byte[] vault = null, manifest = null;
        try (var zip = new ZipInputStream(new ByteArrayInputStream(b, 0, end), StandardCharsets.UTF_8)) {
            for (int i = 0; i < records.size(); i++) {
                cancelled(); var entry = zip.getNextEntry(); var record = records.get(i);
                require(entry != null && entry.getName().equals(record.name));
                byte[] data = zip.readNBytes(record.size + 1); require(data.length == record.size);
                zip.closeEntry(); if (i == 0) manifest = data; else if (i == 1) vault = data;
            }
            require(zip.getNextEntry() == null);
        } catch (ZipException invalid) { throw new BackupException(MALFORMED); }
        VaultId id;
        try { id = Totipo.vaultId(vault); } catch (IllegalArgumentException invalid) { throw new BackupException(MALFORMED); }
        String text = new String(manifest, StandardCharsets.US_ASCII);
        String prefix = new String(manifest(id, count - 2, 0), StandardCharsets.US_ASCII);
        String key = "unresolved-references ";
        String fixed = prefix.substring(0, prefix.indexOf(key)) + key;
        require(text.startsWith(fixed) && text.endsWith("\n"));
        String number = text.substring(fixed.length(), text.length() - 1);
        require(number.matches("0|[1-9][0-9]{0,4}"));
        int gaps = Integer.parseInt(number); require(gaps <= (count - 2) * 4);
        var digest = digest(); digest.update(b, 0, end);
        verified.addAll(records);
        return new BackupSummary(id, count - 2, gaps, false, HexFormat.of().formatHex(digest.digest()));
    }
}

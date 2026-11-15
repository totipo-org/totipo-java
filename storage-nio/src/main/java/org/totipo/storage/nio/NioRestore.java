package org.totipo.storage.nio;

import org.totipo.spi.*;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** New-child restore allocation and explicit recovery. No existing entry is accepted by reserve.
 * The caller chooses a real existing parent and one new direct child. Failures after directory
 * creation can leave an incomplete allocation; never delete/reuse it automatically. Recovery
 * requires this provider's exact durable intent marker and the same frozen archive digest.
 * Markers are non-secret local evidence, not authentication against same-privilege attackers.
 */
public final class NioRestore {
    private static final String MARKER = ".totipo-restore-v1";
    private NioRestore() { }
    public static RestoreReservation reserve(Path parent, String child, String archiveSha256) throws IOException {
        return reserve(parent, child, archiveSha256, new NioDurability());
    }
    public static RestoreReservation reserve(Path parent, String child, String archiveSha256,
                                              StorageDurability durability) throws IOException {
        return reserve(parent, child, archiveSha256, durability, false);
    }
    static RestoreReservation reserve(Path parent, String child, String archiveSha256,
                                      StorageDurability durability, boolean coordinated) throws IOException {
        Objects.requireNonNull(durability);
        Path root = child(parent, child);
        byte[] marker = marker(archiveSha256);
        Files.createDirectory(root); // Atomic exclusive directory creation; NEVER an existing empty root.
        if (NioFiles.findExactDirectChild(root.getParent(), child).isEmpty())
            throw new IOException("EXACT_ALLOCATION_NAME_UNAVAILABLE");
        // Noncanonical allocation evidence. A torn marker is quarantined, never repaired implicitly.
        Files.write(root.resolve(MARKER), marker, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        return open(root, archiveSha256, durability, coordinated);
    }
    public static RestoreReservation recover(Path root, String archiveSha256) throws IOException {
        return recover(root, archiveSha256, new NioDurability());
    }
    public static RestoreReservation recover(Path root, String archiveSha256, StorageDurability durability) throws IOException {
        return open(root.toAbsolutePath(), archiveSha256, durability, false);
    }
    private static Path child(Path parent, String child) throws IOException {
        Objects.requireNonNull(child);
        Path absolute = parent.toAbsolutePath(); NioFiles.directory(absolute);
        if (!NioReads.directName(absolute, child)) throw new IllegalArgumentException("Direct child required");
        return absolute.resolve(child);
    }
    private static byte[] marker(String digest) {
        if (digest == null || !digest.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Archive SHA-256 required");
        return ("totipo-restore 1\n" + digest + "\n").getBytes(StandardCharsets.US_ASCII);
    }
    static RestoreReservation open(Path root, String digest, StorageDurability durability, boolean coordinated) throws IOException {
        TotipoStore store = coordinated ? NioStoreComposition.coordinatedDelegate(root, durability) : NioTotipoStore.open(root, durability);
        return wrap(root, digest, durability, store);
    }
    // Package seam permits real-NIO injected publication faults without replacing storage semantics.
    static RestoreReservation wrap(Path root, String digest, StorageDurability durability, TotipoStore store) throws IOException {
        try {
            var reservation = new Reservation(root, digest, durability, store);
            reservation.check();
            return reservation;
        } catch (IOException | RuntimeException failure) { store.close(); throw failure; }
    }
    private static final class Reservation implements RestoreReservation {
        private final Path root;
        private final String digest;
        private final byte[] intent;
        private final StorageDurability durability;
        private final TotipoStore store;
        private boolean closed;
        Reservation(Path root, String digest, StorageDurability durability, TotipoStore store) {
            this.root = root; this.digest = digest; this.intent = marker(digest);
            this.durability = Objects.requireNonNull(durability); this.store = store;
        }
        private void active() { if (closed) throw new IllegalStateException("Reservation closed"); }
        void check() throws IOException {
            active(); NioFiles.directory(root);
            Path path = NioFiles.findExactDirectChild(root, MARKER).orElseThrow(() -> new IOException("RESTORE_INTENT_ABSENT"));
            if (!Arrays.equals(intent, NioFiles.read(path, intent.length + 1))) throw new IOException("RESTORE_INTENT_MISMATCH");
        }
        private void force(Path file) throws IOException {
            NioFiles.regular(file);
            try (var channel = FileChannel.open(file, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) { channel.force(true); }
        }
        @Override public String archiveSha256() { active(); return digest; }
        @Override public boolean acknowledgeLocation() {
            try {
                check(); force(root.resolve(MARKER)); durability.syncDirectory(root);
                durability.syncDirectory(root.getParent()); check(); return true;
            } catch (IOException | RuntimeException failure) { return false; }
        }
        @Override public ObjectWrite installVault(byte[] bytes) {
            active(); byte[] owned = bytes.clone();
            boolean entered = false;
            try {
                check();
                var read = store.readVault(owned.length);
                if (read instanceof BoundedRead.Absent) {
                    entered = true;
                    var created = store.createVault(owned);
                    if (created instanceof VaultCreate.Created) {
                        if (!exact(owned)) return new ObjectWrite.Uncertain(StoreFailure.UNAVAILABLE);
                        return new ObjectWrite.Written();
                    }
                    if (created instanceof VaultCreate.Failed failed) return new ObjectWrite.Failed(failed.reason());
                    if (created instanceof VaultCreate.Uncertain uncertain) return new ObjectWrite.Uncertain(uncertain.reason());
                    // A concurrent exact winner still needs a positive durability acknowledgement.
                }
                var existing = store.readVault(owned.length);
                if (existing instanceof BoundedRead.Unavailable || existing instanceof BoundedRead.Absent)
                    return new ObjectWrite.Failed(StoreFailure.UNAVAILABLE);
                if (!(existing instanceof BoundedRead.Present p) || !Arrays.equals(p.bytes(), owned))
                    return new ObjectWrite.ExistingDifferent();
                entered = true;
                force(root.resolve("vault")); durability.syncDirectory(root); durability.syncDirectory(root.getParent());
                if (!exact(owned)) return new ObjectWrite.Uncertain(StoreFailure.UNAVAILABLE);
                return new ObjectWrite.AlreadyPresentExact();
            } catch (IOException | RuntimeException failure) {
                return entered ? new ObjectWrite.Uncertain(StoreFailure.UNAVAILABLE) : new ObjectWrite.Failed(StoreFailure.UNAVAILABLE);
            } finally { Arrays.fill(owned, (byte) 0); }
        }
        private boolean exact(byte[] bytes) {
            return store.readVault(bytes.length) instanceof BoundedRead.Present p && Arrays.equals(p.bytes(), bytes);
        }
        @Override public BoundedRead readVault(int n) { active(); return store.readVault(n); }
        @Override public ObjectScan scanObjects() { active(); return store.scanObjects(); }
        @Override public ObjectScan scanObjects(int n) { active(); return store.scanObjects(n); }
        @Override public BoundedRead readObject(ObjectName name, int n) { active(); return store.readObject(name, n); }
        @Override public ObjectWrite publishObject(ObjectName name, byte[] bytes) { active(); return store.publishObject(name, bytes); }
        @Override public VaultCreate createVault(byte[] bytes) { throw new UnsupportedOperationException("Use reserved installVault"); }
        @Override public void close() { if (!closed) { closed = true; store.close(); } }
    }
}

package org.totipo.storage.nio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.Arrays;

/** Shared opaque staging engine. Protocol sizes and lifecycle policy belong to core. */
final class NioVaultStorage {
    private final Path root;
    private final Operations operations;
    boolean mutationEntered;
    private boolean closed;
    static NioVaultStorage open(Path root, Operations operations) throws IOException {
        return new NioVaultStorage(NioFiles.root(root), operations);
    }
    private NioVaultStorage(Path root, Operations operations) { this.root = root; this.operations = operations; }
    static class Operations {
        private final StorageDurability durability;
        private final NioCanonicalInstaller installer;
        Operations(StorageDurability durability) { this(durability, NioCanonicalInstaller.HARD_LINK); }
        Operations(StorageDurability durability, NioCanonicalInstaller installer) {
            this.durability = java.util.Objects.requireNonNull(durability);
            this.installer = java.util.Objects.requireNonNull(installer);
        }
        Path temporary(Path root) throws IOException { return NioFiles.temporary(root, ".totipo-vault-"); }
        int write(FileChannel channel, ByteBuffer bytes) throws IOException { return channel.write(bytes); }
        void syncStage(FileChannel channel) throws IOException { channel.force(true); }
        void link(Path target, Path temp) throws IOException { Files.createLink(target, temp); }
        void installIfAbsent(Path target, Path temp) throws IOException {
            if (installer == NioCanonicalInstaller.HARD_LINK) link(target, temp);
            else installer.installIfAbsent(target, temp);
        }
        void checkAbsent(Path target) throws IOException { installer.checkAbsent(target); }
        void syncInstalled(Path target) throws IOException {
            if (installer == NioCanonicalInstaller.COORDINATED_MOVE) {
                NioFiles.regular(target);
                try (var channel = FileChannel.open(target, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
                    syncStage(channel);
                }
            }
        }
        void syncDirectory(Path root) throws IOException { durability.syncDirectory(root); }
    }
    void create(byte[] candidate) throws IOException {
        mutationEntered = false;
        if (closed) throw new IOException("STORE_CLOSED");
        byte[] owned = candidate.clone();
        Path temp = null;
        try {
            temp = operations.temporary(root);
            try (var channel = FileChannel.open(temp, StandardOpenOption.WRITE)) {
                NioFiles.write(channel, owned, operations::write); operations.syncStage(channel);
            }
            Path canonical = root.resolve("vault");
            operations.checkAbsent(canonical);
            mutationEntered = true;
            try { operations.installIfAbsent(canonical, temp); }
            catch (FileAlreadyExistsException exists) { mutationEntered = false; throw exists; }
            operations.syncInstalled(canonical);
            operations.syncDirectory(root);
        } finally {
            if (temp != null) NioFiles.cleanup(temp);
            Arrays.fill(owned, (byte) 0);
        }
    }
    void close() { closed = true; }
}

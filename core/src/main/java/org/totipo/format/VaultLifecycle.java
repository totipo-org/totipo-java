package org.totipo.format;

import org.totipo.*;
import org.totipo.spi.*;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Objects;

/** Implementation bridge for Totipo; all protocol interpretation remains in core. */
public final class VaultLifecycle {
    private final VaultBootstrapWriter writer;
    private final VaultUnlocker unlocker;
    private final EntropySource entropy;

    private VaultLifecycle() { this(new VaultBootstrapWriter(), new VaultUnlocker(), new EntropySource.Jdk()); }
    VaultLifecycle(VaultBootstrapWriter writer, VaultUnlocker unlocker, EntropySource entropy) {
        this.writer = Objects.requireNonNull(writer);
        this.unlocker = Objects.requireNonNull(unlocker);
        this.entropy = Objects.requireNonNull(entropy);
    }

    /** Pure structural recognition. No authentication, KDF, freshness or origin claim. */
    public static VaultId vaultId(byte[] representation) {
        Objects.requireNonNull(representation);
        if (representation.length != VaultBootstrap.RECORD_BYTES) throw new IllegalArgumentException("Invalid canonical v1 VAULT length");
        byte[] snapshot = representation.clone();
        if (VaultBootstrap.parse(snapshot) == null) throw new IllegalArgumentException("Invalid canonical v1 VAULT representation");
        return new VaultId(CryptoSupport.sha256(snapshot));
    }

    /** Takes store ownership on every outcome. */
    public static OpenResult openSession(TotipoStore store, char[] password) {
        Objects.requireNonNull(store);
        boolean transferred = false;
        byte[] encoded = null, canonical = null;
        try {
            encoded = password(password);
            var read = store.readVault(VaultBootstrap.RECORD_BYTES);
            if (read instanceof BoundedRead.Absent) return new OpenResult.Absent();
            if (read instanceof BoundedRead.Undersized || read instanceof BoundedRead.Oversized)
                return new OpenResult.InvalidVault();
            if (!(read instanceof BoundedRead.Present present)) return new OpenResult.Unavailable();
            canonical = present.bytes();
            try (var result = new VaultUnlocker().unlock(canonical, encoded)) {
                if (result.status() == VaultUnlockResult.Status.UNLOCKED) {
                    byte[] root = result.root();
                    try {
                        var session = new ApplicationSession(root, vaultId(canonical), store);
                        transferred = true;
                        return new OpenResult.Opened(session);
                    } finally { wipe(root); }
                }
                return result.status() == VaultUnlockResult.Status.AUTHENTICATION_FAILED
                        ? new OpenResult.AuthenticationFailed() : new OpenResult.InvalidVault();
            }
        } catch (SecurityException | UnsupportedOperationException unavailable) {
            return new OpenResult.Unavailable();
        } finally { wipe(encoded); wipe(canonical); if (!transferred) cleanup(store); }
    }

    /** Takes store ownership on every outcome. */
    public static CreateVaultResult createSession(TotipoStore store, char[] password) {
        return new VaultLifecycle().create(store, password);
    }

    CreateVaultResult create(TotipoStore store, char[] password) {
        Objects.requireNonNull(store);
        byte[] encoded = null, root = null, salt = null, nonce = null, candidate = null, canonical = null;
        boolean attempted = false, transferred = false;
        try {
            encoded = password(password);
            var existing = store.readVault(VaultBootstrap.RECORD_BYTES);
            if (!(existing instanceof BoundedRead.Absent)) {
                return existing instanceof BoundedRead.Unavailable ? new CreateVaultResult.Failed()
                        : new CreateVaultResult.AlreadyExists();
            }
            // Even incomplete observation can positively reveal unauthenticated contextual evidence.
            var observation = store.scanObjects();
            if (observation.entries().stream().anyMatch(e -> e.name().value().matches("[0-9a-f]{64}")))
                return new CreateVaultResult.Failed(CreateVaultResult.FailureReason.OBJECT_DATA_OBSERVED);
            root = new byte[32]; salt = new byte[16]; nonce = new byte[12];
            entropy.fill(root); entropy.fill(salt); entropy.fill(nonce);
            candidate = writer.encode(encoded, root, salt, nonce);
            if (!valid(candidate, encoded, root)) return new CreateVaultResult.Failed();
            attempted = true;
            var result = store.createVault(candidate.clone());
            if (result instanceof VaultCreate.AlreadyPresent) return new CreateVaultResult.AlreadyExists();
            if (result instanceof VaultCreate.Failed) return new CreateVaultResult.Failed();
            if (!(result instanceof VaultCreate.Created)) return new CreateVaultResult.Uncertain();
            // A persistence acknowledgement alone is insufficient. Freshly read actual canonical bytes.
            var read = store.readVault(VaultBootstrap.RECORD_BYTES);
            if (!(read instanceof BoundedRead.Present present)) return new CreateVaultResult.Uncertain();
            canonical = present.bytes();
            if (canonical.length != VaultBootstrap.RECORD_BYTES || !Arrays.equals(candidate, canonical)
                    || !valid(canonical, encoded, root)) return new CreateVaultResult.Uncertain();
            var session = new ApplicationSession(root, vaultId(canonical), store);
            transferred = true;
            return new CreateVaultResult.Created(session);
        } catch (SecurityException | UnsupportedOperationException failure) {
            return attempted ? new CreateVaultResult.Uncertain() : new CreateVaultResult.Failed();
        } catch (RuntimeException failure) {
            if (attempted) return new CreateVaultResult.Uncertain();
            throw failure;
        } finally {
            wipe(encoded); wipe(root); wipe(salt); wipe(nonce); wipe(candidate); wipe(canonical);
            if (!transferred) cleanup(store);
        }
    }

    private boolean valid(byte[] bytes, byte[] password, byte[] root) {
        try (var result = unlocker.unlock(bytes, password)) {
            if (result.status() != VaultUnlockResult.Status.UNLOCKED) return false;
            byte[] recovered = result.root();
            try { return MessageDigest.isEqual(root, recovered); } finally { wipe(recovered); }
        }
    }
    private static byte[] password(char[] password) {
        byte[] encoded = PasswordBytes.encode(Objects.requireNonNull(password));
        if (encoded == null) throw new IllegalArgumentException("Invalid password input");
        return encoded;
    }
    static void wipe(byte[] bytes) { if (bytes != null) Arrays.fill(bytes, (byte) 0); }
    static void cleanup(AutoCloseable resource) {
        if (resource != null) try { resource.close(); } catch (Exception ignored) { /* Best effort cleanup. */ }
    }
}

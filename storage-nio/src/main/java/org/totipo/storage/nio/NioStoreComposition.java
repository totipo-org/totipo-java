package org.totipo.storage.nio;

import org.totipo.spi.TotipoStore;
import java.io.IOException;
import java.nio.file.Path;

/** Experimental provider-composition boundary for an externally coordinated owner.
 * Ordinary applications use NioTotipo or NioTotipoStore.open.
 * The owner MUST serialize every store call, session, writer, bridge mutation and handle
 * across the whole root for the delegate's entire lifetime. Direct uncoordinated use violates
 * this contract. Opening cannot verify or enforce this policy. Move publication supplies no
 * atomic no-replace exclusion against independent concurrent writers.
 * No automatic fallback from ordinary shared hard-link publication exists. */
public final class NioStoreComposition {
    private NioStoreComposition() { }
    /** Fresh allocation for the same whole-root externally coordinated owner contract.
     * Wrap the returned reservation before core use. SAF is never this canonical destination.
     */
    public static org.totipo.spi.RestoreReservation reserveRestore(Path parent, String child,
            String archiveSha256, StorageDurability durability) throws IOException {
        return NioRestore.reserve(parent, child, archiveSha256, durability, true);
    }
    /** Explicitly recover this provider's retained allocation intent for the same frozen ZIP. */
    public static org.totipo.spi.RestoreReservation recoverRestore(Path root,
            String archiveSha256, StorageDurability durability) throws IOException {
        return NioRestore.open(root.toAbsolutePath(), archiveSha256, durability, true);
    }
    /** Obtain one persistent move-based delegate to wrap before exposing it to core or bridges. */
    public static TotipoStore coordinatedDelegate(Path root, StorageDurability durability) throws IOException {
        return NioTotipoStore.open(root,
                new NioObjectStorage.Operations(durability, NioCanonicalInstaller.COORDINATED_MOVE),
                new NioVaultStorage.Operations(durability, NioCanonicalInstaller.COORDINATED_MOVE),
                new NioTotipoStore.ScanOperations());
    }
}

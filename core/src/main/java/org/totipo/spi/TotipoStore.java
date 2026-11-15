package org.totipo.spi;

/** Experimental provider SPI. Understands root/vault/objects-v1 and opaque storage only.
 * Construction is read-only. Calls are synchronous; inputs remain caller-owned.
 * Writes neither mutate nor retain caller arrays without copying.
 * Passing this store to Totipo.open/create transfers ownership on every outcome.
 * Calls are serialized by core; providers need not support concurrent use of one handle.
 * Close is idempotent and never removes canonical state. */
public interface TotipoStore extends AutoCloseable {
    BoundedRead readVault(int expectedBytes);
    ObjectScan scanObjects();
    /** Opt-in bounded enumeration. Counts every direct child, including unrelated names.
     * Reaching the bound before EOF returns Incomplete; never silently truncates Complete.
     * The default does not invoke the potentially unbounded legacy scan.
     * @param maximumEntries positive retained-observation and enumeration budget
     * @return bounded observations with the ordinary scan semantics
     */
    default ObjectScan scanObjects(int maximumEntries) {
        if (maximumEntries < 1) throw new IllegalArgumentException("Positive scan bound required");
        return new ObjectScan.Incomplete(java.util.List.of(), StoreFailure.UNSUPPORTED);
    }
    BoundedRead readObject(ObjectName name, int expectedBytes);
    ObjectWrite publishObject(ObjectName name, byte[] bytes);
    /** Publish complete opaque bytes only if canonical vault is absent. Never replace. */
    VaultCreate createVault(byte[] bytes);
    @Override void close();
}

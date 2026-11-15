package org.totipo.spi;

/** Experimental provider-owned allocation for one frozen archive. The initial allocation MUST
 * exclusively create a previously absent location, refusing every existing entry. Explicit
 * recovery must prove the original allocation/intent, never infer freshness from emptiness.
 * The owner excludes competing workflows until close; honest shared immutable writers remain safe.
 * No method removes canonical data. Close releases resources, not allocation evidence.
 * Implementations retain their durable recovery evidence for application-managed retirement.
 */
public interface RestoreReservation extends TotipoStore {
    /** Frozen archive digest bound to the allocation; recognition, not authentication. */
    String archiveSha256();
    /** Create complete bootstrap if absent, or acknowledge exact existing bytes WITHOUT rewriting.
     * Written/AlreadyPresentExact require file and containing-location durability, and fresh exact
     * confirmation. Different/unsuitable entries remain untouched. Uncertain includes lost barriers.
     */
    ObjectWrite installVault(byte[] bytes);
    /** Acknowledge allocation and its parent namespace persistence. False is not completion. */
    boolean acknowledgeLocation();
}

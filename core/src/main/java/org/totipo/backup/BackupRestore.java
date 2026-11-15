package org.totipo.backup;

import org.totipo.spi.RestoreReservation;

/** Owned immutable bounded ciphertext plan. Retains no password, root or session.
 * Close wipes evidence, waits for admitted work, and closes its destination without rollback.
 * All work is synchronous and belongs off application UI threads. Caller owns stream cancellation.
 */
public interface BackupRestore extends AutoCloseable {
    BackupSummary summary();
    /** An admitted call takes exclusive ownership of one reservation, including on rejection.
     * May be called once; programmer errors (closed plan or second install) do not transfer it.
     * The reservation must be bound to summary().archiveSha256(). Never registers/opens a vault.
     */
    RestoreResult install(RestoreReservation destination);
    /** Explicit exact replay on the same allocation. After shutdown, authenticate the same ZIP
     * again and supply an explicitly recovered reservation to a new plan instead.
     */
    RestoreResult retry();
    /** Permanently requests cancellation without waiting for entered provider work.
     * Further attempts require a new plan and explicitly recovered reservation.
     */
    void cancel();
    @Override void close();
}

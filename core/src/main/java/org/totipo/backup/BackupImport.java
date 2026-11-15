package org.totipo.backup;

/** Session-owned, authenticated immutable encrypted evidence. Only session preparation creates
 * library handles; descriptive summaries and candidate-validation values confer no authority.
 * Apply/summary/close are serialized through the session gate. Close releases bounded ciphertext
 * memory, waits for an active apply, and never rolls back storage. Session close also closes this
 * handle. Callers must close it. A newer state does not invalidate it.
 */
public interface BackupImport extends AutoCloseable {
    /** Authenticated archive-only diagnostics, recomputed without trusting manifest history claims.
     * @return immutable summary; no freshness, origin or destination-observation claim
     */
    BackupSummary summary();
    /** Preflights all targets again, then publishes exact frozen representations in ID order through
     * the owning session's provider. Exact-existing objects receive provider durability acknowledgement.
     * Stops on failure/cancellation without rollback. Repeat explicitly to replay the same evidence;
     * uncertainty survives failures until that object is positively acknowledged. May block; never call
     * under an external bridge lock. No input stream, new assertion or semantic merge gate is used.
     * Interruption/closing is checked between objects; blocked provider calls must retire normally.
     * Completion can win a late cancellation. No refresh is requested: after return, arrange ordinary
     * observation outside external coordination; requestRefresh returning is not scan completion.
     * @return cumulative persistence knowledge for this handle, separate from state observation
     * @throws org.totipo.SessionClosedException if the session is already closing at entry
     * @throws IllegalStateException if this handle is closed
     */
    ImportResult apply();
    @Override void close();
}

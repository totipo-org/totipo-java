package org.totipo;

import java.util.concurrent.Flow;
/** Live owner of vault secrets and storage. See API_DESIGN.md for normative contracts
 * and the <a href="https://github.com/totipo-org/totipo-java/blob/main/API_DESIGN.md#operation-classes-and-state-snapshot-semantics">operation/state-snapshot model</a>.
 * State emissions do not generically invalidate earlier same-session work.
 * @see VaultState
 */
public interface VaultSession extends AutoCloseable {
    VaultId vaultId();
    /** Immediate, I/O-free read of the latest emitted state. */
    VaultState state();
    /** Ordered replay-latest publisher with independent coalescing backpressure.
     * Invokes onSubscribe synchronously on the thread calling subscribe.
     * Subsequent state and terminal callbacks are delivered asynchronously through
     * the common-pool drain and serialized per subscription.
     */
    Flow.Publisher<VaultState> states();
    /** Non-blocking request for another local observation; requests may coalesce.
     * The observation reads the configured store without mutating semantic vault history.
     * Subsequent emissions do not invalidate older snapshots or same-session references.
     * Requires an open session.
     * @see #states()
     */
    void requestRefresh();
    /** Synchronously validates an immutable objects-v1 candidate against this open authenticated vault.
     * Success establishes exact physical length (1024 bytes), envelope authentication,
     * keyed OBJECT_ID match, padding/framing validity and current semantic TOKEN validity.
     * It does not establish current-head status, graph completeness, freshness, persistence,
     * provider/store origin, remote synchronization or contradiction with another candidate.
     * Performs no import, store access or state change, password re-entry or KDF; exports no root key.
     * The caller must not modify input during the call. Input is neither mutated nor retained;
     * successful results defensively own the exact validated ciphertext snapshot and their
     * representation accessors return copies. Results retain no session/root reference and may
     * outlive the session. They are descriptive values; only the successful call establishes validation.
     * Enters the normal session serialization/lifecycle gate and rejects work after closing starts.
     * The default preserves compatibility for external session implementations.
     * @param objectId supplied canonical OBJECT_ID
     * @param representation externally obtained immutable object bytes
     * @return Invalid for candidate validation failure, or Valid with the exact ciphertext snapshot
     * @throws NullPointerException if either argument is null in an open library session
     * @throws SessionClosedException if this library's session is closing or closed
     * @throws UnsupportedOperationException if an external implementation lacks this capability
     */
    default ObjectCandidateValidation validateObject(RevisionId objectId, byte[] representation) {
        throw new UnsupportedOperationException("Candidate validation unavailable");
    }
    /** Rejects entrants, waits for mutating certainty, wipes secrets and completes subscribers. May block.
     * Never waits for subscriber callbacks; safe to call from onNext. A prior fatal termination retains its error. */
    @Override void close();
}

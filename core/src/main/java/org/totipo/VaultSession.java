package org.totipo;

import java.util.concurrent.Flow;
/** Live owner of vault secrets and storage. See API_DESIGN.md for normative contracts
 * and the <a href="https://github.com/totipo-org/totipo-java/blob/main/API_DESIGN.md#operation-classes-and-state-snapshot-semantics">operation/state-snapshot model</a>.
 * State emissions do not generically invalidate earlier same-session work.
 * @see VaultState
 */
public interface VaultSession extends AutoCloseable {
    /** Captures all supported locally observed encrypted history under this session's provider gate.
     * Requires an open authenticated session; reads bounded enumeration and fresh exact bytes,
     * validates each object and rechecks the immutable vault binding. Incomplete enumeration,
     * unreadable/invalid candidates or capacity exhaustion fail without a capture. Missing or
     * wrong-token ancestry alone is permitted with explicit diagnostics. Unrelated names are ignored.
     * Concurrent external publications may or may not be included: this is a finite observed set,
     * not an atomic snapshot, synchronization barrier or global completeness claim. State emissions
     * do not invalidate it. No canonical writes, assertions, refresh or TOTP computation occur.
     * Close waits on the gate; interruption is checked between provider calls, not inside blocked I/O.
     * Returned ciphertext ownership transfers to the caller and survives session close.
     * @param limits bounded memory/enumeration/archive budgets
     * @return owned frozen capture; caller must close
     * @throws java.io.IOException capture evidence, capacity or cancellation failure
     * @throws SessionClosedException if closing or closed
     */
    org.totipo.backup.BackupCapture captureBackup(org.totipo.backup.BackupLimits limits)
            throws java.io.IOException;
    VaultId vaultId();
    /** Immediate, I/O-free read of the latest emitted state.
     * Library-created 0.2.0 sessions install a distinct state reference per completed observation pass.
     * See API_DESIGN.md, Observation-pass ordering and composition, for ordering and barrier assumptions. */
    VaultState state();
    /** Ordered replay-latest publisher with independent coalescing backpressure.
     * Invokes onSubscribe synchronously on the thread calling subscribe.
     * Subsequent state and terminal callbacks are delivered asynchronously through
     * the common-pool drain and serialized per subscription.
     */
    Flow.Publisher<VaultState> states();
    /** Non-blocking request for another local observation; requests may coalesce.
     * Return acknowledges no observation start/completion and correlates no state delivery to this request.
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
     * @param objectId supplied canonical OBJECT_ID
     * @param representation externally obtained immutable object bytes
     * @return Invalid for candidate validation failure, or Valid with the exact ciphertext snapshot
     * @throws NullPointerException if either argument is null in an open library session
     * @throws SessionClosedException if this library's session is closing or closed
     */
    ObjectCandidateValidation validateObject(RevisionId objectId, byte[] representation);
    /** Rejects entrants, waits for mutating certainty, wipes secrets and completes subscribers. May block.
     * Never waits for subscriber callbacks; safe to call from onNext. A prior fatal termination retains its error. */
    @Override void close();
}

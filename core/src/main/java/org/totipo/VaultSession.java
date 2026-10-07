package org.totipo;

import java.util.concurrent.Flow;
/** Live owner of vault secrets and storage. See API_DESIGN.md for normative contracts. */
public interface VaultSession extends AutoCloseable {
    VaultFingerprint fingerprint();
    /** Immediate, I/O-free read of the latest emitted state. */
    VaultState state();
    /** Ordered replay-latest publisher with independent coalescing backpressure. */
    Flow.Publisher<VaultState> states();
    /** Non-blocking request for another local observation; requests may coalesce. */
    void requestRefresh();
    /** May block for KDF and configured-store I/O. Uncertainty requires re-observation/reopen.
     * Rewrap retains the same root with fresh salt/nonce. It does not revoke old wrappers,
     * rotate the root or provide recovery from root compromise. */
    PasswordChangeResult changePassword(char[] currentPassword, char[] newPassword);
    /** Synchronously validates an immutable objects-v1 candidate without store access or state changes.
     * Only an exact 1024-byte representation can be valid. Input is caller-owned and must
     * not be modified during the call; successful results own a defensive ciphertext copy.
     * Serialized with session operations; rejects work after closing starts.
     * The default preserves compatibility for external session implementations.
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

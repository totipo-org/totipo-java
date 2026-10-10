package org.totipo;

import java.time.Duration;
/** Fluent field editing shared by the three distinct builders. Builders are thread-confined. */
public interface TokenEditor<T extends TokenEditor<T>> extends AutoCloseable {
    /** Changes logical lifecycle state. Tombstoning retains the secret and immutable history;
     * it does not securely erase credentials or historical/provider copies. */
    T status(TokenStatus value);
    T issuer(String value);
    T account(String value);
    T algorithm(TotpAlgorithm value);
    T digits(int value);
    T period(Duration value);
    T secret(NewSecret value);
    T metadata(ClientMetadata value);
    /** May block for observation, local crypto and configured-store I/O; keep off the UI thread.
     * Normal merge save checks newer evidence and may return additional conflict;
     * create/update saves do not perform that merge-only check. No silent rebase occurs.
     * See the <a href="https://github.com/totipo-org/totipo-java/blob/main/API_DESIGN.md#operation-classes-and-state-snapshot-semantics">operation/state-snapshot model</a>.
     * @see SaveResult.AdditionalConflict
     * @see PartialResolution#save()
     * @see PublicationRetry#retryPublication()
     */
    SaveResult save();
    @Override void close();
}

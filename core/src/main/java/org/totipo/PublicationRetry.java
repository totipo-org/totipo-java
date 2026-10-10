package org.totipo;

/** Independent exact-publication capability. Each attempt transfers capability to its result. */
public interface PublicationRetry extends AutoCloseable {
    /** May block for configured-store I/O; republishes exact frozen bytes and identities.
     * Never reobserves, rebases semantics or downgrades uncertainty because newer states exist.
     * Requires an open owning session; each attempt transfers capability to its result.
     * @see TokenEditor#save()
     */
    RetryResult retryPublication();
    @Override void close();
}

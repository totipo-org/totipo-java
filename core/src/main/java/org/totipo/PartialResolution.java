package org.totipo;

/** Frozen original merge resolution, independent of its builder. */
public interface PartialResolution extends AutoCloseable {
    /** May block for configured-store I/O. Publishes the frozen original resolution;
     * does not repeat the semantic new-information gate or rebase onto newer states.
     * Requires an open owning session.
     * @see SaveResult.AdditionalConflict
     * @see PublicationRetry#retryPublication()
     */
    PartialSaveResult save();
    @Override void close();
}

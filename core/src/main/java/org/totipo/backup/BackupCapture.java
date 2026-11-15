package org.totipo.backup;

import java.io.IOException;
import java.io.OutputStream;

/** Owned frozen ciphertext, independent of subsequent source changes and session closure.
 * Library captures retain only bounded private memory, no keys, plaintext, files or provider.
 * Write and close are serialized; close releases/wipes the spool, is idempotent, and waits for
 * an active write. Operations after close throw IllegalStateException. Callers must close.
 * Implementations supplied by applications establish no validation authority.
 */
public interface BackupCapture extends AutoCloseable {
    BackupSummary summary();
    /** Writes deterministic profile-1 ZIP, finishing without closing or flushing the borrowed
     * stream. May block. A failure may leave arbitrary partial output; discard it. Return only
     * acknowledges stream writes/ZIP finish, never provider close, readback or durability.
     * Interruption is checked between records; blocked stream calls require caller cancellation.
     * A frozen capture can be retried with a new output after failure.
     * @param output borrowed destination
     * @throws IOException stream failure, cancellation or capacity failure
     */
    void writeTo(OutputStream output) throws IOException;
    @Override void close();
}

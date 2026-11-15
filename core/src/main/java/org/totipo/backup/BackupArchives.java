package org.totipo.backup;

import java.io.IOException;
import java.io.InputStream;
import org.totipo.format.BackupZip;

/** Shared portable ZIP inspection and detached authenticated restore preparation. */
public final class BackupArchives {
    private BackupArchives() { }
    /** Freezes/verifies the complete bounded ZIP, authenticates its vault with the original
     * password and validates every object. Borrows input through EOF without closing it.
     * Password characters remain caller-owned; do not mutate them during the call. Owned
     * encodings and derived secrets are wiped before return. No storage access occurs.
     * Invalid password encoding is a programmer error. Caller must close the detached plan.
     * @throws IOException archive, authentication, validation, capacity or cancellation failure
     */
    public static BackupRestore prepareRestore(InputStream input, BackupLimits limits, char[] password) throws IOException {
        return org.totipo.format.NewLocationRestore.prepare(input, limits, password);
    }
    /** Consumes from the current position through EOF, without closing the borrowed input.
     * Uses bounded private memory; retains no payload after return. Rejects ambiguous ZIP layout,
     * unsupported features, malformed entries and budgets, including trailing bytes. Checks CRCs
     * and structural vault identity, which are not cryptographic authentication. Manifest history
     * diagnostics are untrusted producer assertions. SHA-256 of the entire ZIP may separately be
     * computed by a caller while streaming; it supplies no freshness proof.
     * Interruption is checked between reads/records. A blocked input needs caller cancellation.
     * @param input borrowed untrusted stream
     * @param limits per-operation budgets
     * @return descriptive structural inspection; sourceAuthenticated is always false
     * @throws IOException malformed/unsupported/capacity/cancelled archive or input failure
     */
    public static BackupSummary inspect(InputStream input, BackupLimits limits) throws IOException {
        return BackupZip.inspect(input, limits);
    }
}

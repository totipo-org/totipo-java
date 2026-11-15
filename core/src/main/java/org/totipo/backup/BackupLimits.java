package org.totipo.backup;

/** Per-operation budgets, bounded by the initial implementation's memory envelope.
 * Budgets never select a successful prefix of history. */
public record BackupLimits(int objects, int observedEntries, int archiveBytes) {
    /** Initial supported ceiling; applications may choose smaller budgets. */
    public static final BackupLimits DEFAULT = new BackupLimits(4096, 8192, 6 * 1024 * 1024);
    public BackupLimits {
        if (objects < 0 || objects > 4096 || observedEntries < 1 || observedEntries > 8192
                || archiveBytes < 512 || archiveBytes > 6 * 1024 * 1024)
            throw new IllegalArgumentException("Unsupported backup budgets");
    }
}

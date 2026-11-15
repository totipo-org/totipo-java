package org.totipo.backup;

import org.totipo.VaultId;

/** Descriptive data only; never import/restore authority. Even authentication claims in a
 * structurally inspected manifest are untrusted producer assertions. No remote freshness,
 * simultaneous snapshot, global completeness or archive-origin authentication is established.
 * @param vaultId structural identity of the exact encrypted vault
 * @param objects number of encrypted immutable objects
 * @param unresolvedReferences missing or wrong-token parent edges reported by the producer
 * @param sourceAuthenticated true for a library capture or authenticated import plan; false for structural inspection
 * @param archiveSha256 lowercase SHA-256 of the complete ZIP, an integrity identifier only
 */
public record BackupSummary(VaultId vaultId, int objects, int unresolvedReferences, boolean sourceAuthenticated,
                            String archiveSha256) { }

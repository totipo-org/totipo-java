package org.totipo.backup;

/** Cumulative unique-object knowledge, not an atomic transaction or perpetual retention promise.
 * Counts partition the plan. Installed counts positive Written acknowledgements; retained counts
 * exact acknowledgements without a known Written on this handle. Uncertain objects may be present.
 * An uncertain object becomes acknowledged only through positive provider publication acknowledgement,
 * never from a read/equality check. Results remain readable after handle/session close.
 * @param outcome completion or incomplete persistence knowledge
 * @param installed acknowledged new objects on this handle
 * @param retained acknowledged exact-existing objects without known installation on this handle
 * @param uncertain objects with publication entered but no subsequent positive acknowledgement
 * @param remaining objects with neither acknowledgement nor uncertainty
 * @param reason stopping reason, or NONE on completion
 */
public record ImportResult(Outcome outcome, int installed, int retained, int uncertain, int remaining, Reason reason) {
    /** COMPLETE and NO_OP require a full acknowledged replay and final exact VAULT recheck.
     * PARTIAL preserves some positive acknowledgements; UNCERTAIN takes precedence over PARTIAL.
     * FAILED means no object has been acknowledged or possibly published by this handle.
     */
    public enum Outcome { COMPLETE, NO_OP, PARTIAL, UNCERTAIN, FAILED }
    /** Coarse, non-secret stop classification. */
    public enum Reason { NONE, CANCELLED, SESSION_CLOSING, VAULT_CHANGED, TARGET_CONFLICT, TARGET_UNAVAILABLE, STORAGE }
}

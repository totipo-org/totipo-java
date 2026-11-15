package org.totipo.backup;

/** Immutable cumulative receipt, independent of application registration or observation.
 * Object counts use ImportResult's unique-object acknowledgement semantics. Acknowledged bootstrap
 * plus incomplete objects is PARTIAL even for zero acknowledged objects. Uncertainty takes priority.
 * Interrupted/closed jobs preserve evidence; no result authorizes cleanup or blind fresh retry.
 * UNCERTAIN also covers unacknowledged location/parent persistence. REJECTED describes canonical
 * installation only: the reserved directory/intent may remain even with no canonical installation.
 * @param outcome overall completion/persistence knowledge
 * @param bootstrap bootstrap acknowledgement knowledge
 * @param locationAcknowledged allocation/parent persistence acknowledgement on this attempt
 * @param objects cumulative object replay receipt
 * @param inventory bounded sorted per-object knowledge, retained independently of plan closure
 */
public record RestoreResult(Outcome outcome, Bootstrap bootstrap, boolean locationAcknowledged,
                            ImportResult objects, java.util.List<ObjectReceipt> inventory) {
    public RestoreResult { inventory = java.util.List.copyOf(inventory); }
    public enum Outcome { REJECTED, COMPLETE, PARTIAL, UNCERTAIN }
    /** NOT_INSTALLED means no possible/acknowledged installation by this plan; it does not
     * establish absence in an explicitly recovered allocation after another process stopped.
     */
    public enum Bootstrap { NOT_INSTALLED, ACKNOWLEDGED, UNCERTAIN }
    public enum Knowledge { REMAINING, INSTALLED, RETAINED, UNCERTAIN }
    /** Non-secret acknowledgement knowledge, not a claim of perpetual presence. */
    public record ObjectReceipt(org.totipo.RevisionId id, Knowledge knowledge) { }
}

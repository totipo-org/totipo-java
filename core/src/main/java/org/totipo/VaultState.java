package org.totipo;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
/**
 * Immutable valid observation, not a lease on currentness. Later same-session emissions
 * do not invalidate this state or its references; operation-specific freshness rules still apply.
 * Descriptive reads remain valid after session closure. Secret-backed operations require
 * the owning session to be open; references from another session are not accepted.
 * See the <a href="https://github.com/totipo-org/totipo-java/blob/main/API_DESIGN.md#operation-classes-and-state-snapshot-semantics">operation/state-snapshot model</a>.
 * @see VaultSession
 */
public interface VaultState {
    ObservationProgress observation();
    List<TokenState> tokens();
    Optional<TokenState> token(TokenId id);
    List<VaultDiagnostic> diagnostics();
    CreateToken createToken();
    /** Uses all equal current heads in this state, or the alternative's captured heads when absent.
     * Captures that basis without consulting later session states; the builder is thread-confined. */
    UpdateToken update(TokenAlternative alternative);
    /** Uses exactly this one head; historical same-session references are accepted.
     * The captured basis is not silently rebased; the builder is thread-confined. */
    UpdateToken update(TokenHead head);
    /** Selects this state's complete current token frontier; later states do not rebase the builder.
     * Normal save performs the merge freshness check.
     * @see TokenEditor#save()
     */
    MergeToken merge(TokenId tokenId);
    /** Selects exactly the captured heads of the supplied same-session, same-token alternatives.
     * Normal save checks freshness against the receiving state's captured frontier.
     * @see TokenEditor#save()
     */
    MergeToken merge(Collection<TokenAlternative> alternatives);
    /** Local projection from a valid same-session alternative and session-owned secret material.
     * Performs no configured-store I/O or provider freshness check; neither this state nor
     * the alternative must remain current. Requires an open session. Applications own
     * late-result presentation relevance; see the <a href="https://github.com/totipo-org/totipo-java/blob/main/API_DESIGN.md#operation-classes-and-state-snapshot-semantics">operation/state-snapshot model</a>.
     * @see TotpCode
     */
    TotpCode generateTotp(TokenAlternative alternative, Instant time);
}

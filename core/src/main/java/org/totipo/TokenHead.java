package org.totipo;

import java.util.List;

/** Session-scoped identity: equal exactly for the same session, token and revision.
 * Equality/hash codes are stable across states and after close; metadata and ancestry are not identity. */
public interface TokenHead {
    TokenId tokenId();
    RevisionId revision();
    /** Exact metadata of this revision, including on captured heads after later observations or close. */
    ClientMetadata metadata();
    /**
     * Resolved, validated immediate causal parents in this immutable observation.
     * Historical parents expose their own exact metadata and parents. The list is
     * immutable, thread-safe and readable after refresh or session closure, with no
     * storage I/O. Roots and revisions with no available parents return an empty list.
     * Missing, invalid, unauthenticated or wrong-token parents are never fabricated.
     * Consult {@link TokenState#unresolvedReferences()}, {@link VaultState#observation()}
     * and {@link VaultState#diagnostics()} from the corresponding state for known gaps.
     * Empty diagnostics cannot prove that unavailable revisions never existed.
     * Traversal should track visited revisions defensively against cycles.
     * @return the available immediate parents, in canonical revision order
     */
    List<TokenHead> parents();
}

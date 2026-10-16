package org.totipo;

import java.util.List;
public interface TokenState {
    TokenId id();
    List<TokenAlternative> alternatives();
    List<TokenHead> heads();
    /** Known unresolved edges across all validated revisions of this token in this
     * observation, including historical children reachable through {@link TokenHead#parents()}.
     * Does not establish complete history or the nonexistence of unavailable revisions.
     * @return immutable known child/parent gaps
     */
    List<UnresolvedReference> unresolvedReferences();
    TokenCompetition competingValues();
    boolean hasConflict();
}


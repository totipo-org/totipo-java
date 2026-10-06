package org.totipo;

import java.util.List;
public interface MergeToken extends TokenEditor<MergeToken> {
    /**
     * Initializes every semantic field from one Alternative in this builder's captured
     * merge basis: issuer, account, status (including TOMBSTONED), secret, algorithm,
     * digits and period. This selects a semantic Alternative, not a Head; multiple
     * Heads may support one Alternative and their count implies no preference.
     * The Alternative's captured heads must belong to this basis. No state is recaptured.
     * Existing secrets are transferred internally through this builder's secret choices
     * and are never returned. Transfer is atomic; later explicit field setters may
     * deliberately modify the result. Per-head metadata is not a semantic field.
     * Publication occurs only on {@link #save()}, through the usual merge path, and
     * may still return {@link SaveResult.AdditionalConflict} or
     * {@link SaveResult.PublicationUncertain}.
     *
     * @param alternative the complete captured semantic value to keep
     * @return this builder
     * @throws IllegalArgumentException if the Alternative does not belong to the captured basis
     * @throws NullPointerException if the Alternative is null
     */
    MergeToken keep(TokenAlternative alternative);
    TokenCompetition competingValues();
    List<MergeSecretChoice> secretChoices();
    MergeToken secret(MergeSecretChoice choice);
    List<String> unresolvedFields();
}

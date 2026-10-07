package org.totipo.format;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;

/** Bounded validation over the platform discovery boundary; diagnostics never gate use. */
final class TokenStoreReader {
    private TokenStoreReader() {}

    static TokenStoreObservation read(DiscoverySource source, byte[] root) {
        Objects.requireNonNull(source);
        Objects.requireNonNull(root);
        var tokens = new ArrayList<ValidatedToken>();
        var candidates = new ArrayList<TokenStoreObservation.CandidateDiagnostic>();
        var snapshots = new ArrayList<DiscoverySource.SnapshotIssue>();
        try (var snapshot = source.snapshot()) {
            if (snapshot.issue() != DiscoverySource.SnapshotIssue.NONE) snapshots.add(snapshot.issue());
            for (var candidate : snapshot.candidates()) {
                try (var channel = candidate.opener().open()) {
                    byte[] bytes = BoundedObjectRead.read(channel);
                    var validation = validate(candidate.id(), bytes, root);
                    if (validation.token() != null) tokens.add(validation.token());
                    else candidates.add(new TokenStoreObservation.CandidateDiagnostic(candidate.id(), validation.reason()));
                } catch (IOException unavailable) {
                    candidates.add(new TokenStoreObservation.CandidateDiagnostic(candidate.id(),
                            TokenStoreObservation.Reason.UNAVAILABLE));
                }
            }
        } catch (IOException unavailable) {
            snapshots.add(DiscoverySource.SnapshotIssue.ENUMERATION_UNAVAILABLE);
        }
        return new TokenStoreObservation(tokens, candidates, snapshots);
    }

    /** Same independent validation path for store observations and session candidates.
     * A successful caller owns the parsed secret and must transfer or clear it. */
    record Validation(ValidatedToken token, TokenStoreObservation.Reason reason) { }

    static Validation validate(ObjectId id, byte[] bytes, byte[] root) {
        var envelope = EnvelopeReader.open(id.filename(), bytes, root);
        try {
            if (envelope.status() != EnvelopeReader.Status.AUTHENTICATED_SEMANTIC)
                return new Validation(null, TokenStoreObservation.Reason.INVALID_STORAGE);
            byte[] semantic = envelope.semanticBytes();
            try {
                return new Validation(new ValidatedToken(id, TokenReader.read(semantic)), null);
            } catch (IllegalArgumentException invalid) {
                return new Validation(null, TokenStoreObservation.Reason.INVALID_TOKEN);
            } finally {
                Arrays.fill(semantic, (byte) 0);
            }
        } finally {
            envelope.clear();
        }
    }
}

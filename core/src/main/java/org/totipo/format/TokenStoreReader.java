package org.totipo.format;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;

/** Bounded validation over the platform discovery boundary; diagnostics never gate use. */
final class TokenStoreReader {
    private TokenStoreReader() {}

    static TokenStoreObservation read(org.totipo.spi.TotipoStore store, byte[] root) {
        Objects.requireNonNull(store); Objects.requireNonNull(root);
        var tokens = new ArrayList<ValidatedToken>();
        var candidates = new ArrayList<TokenStoreObservation.CandidateDiagnostic>();
        var snapshots = new ArrayList<TokenStoreObservation.SnapshotIssue>();
        var scan = store.scanObjects();
        if (scan instanceof org.totipo.spi.ObjectScan.Incomplete incomplete)
            snapshots.add(incomplete.reason() == org.totipo.spi.StoreFailure.UNSAFE_NAMESPACE
                    ? TokenStoreObservation.SnapshotIssue.UNSAFE_NAMESPACE
                    : TokenStoreObservation.SnapshotIssue.ENUMERATION_UNAVAILABLE);
        for (var entry : scan.entries()) {
            ObjectId id;
            try { id = ObjectId.fromFilename(entry.name().value()); }
            catch (IllegalArgumentException ignored) { continue; }
            var read = store.readObject(entry.name(), EnvelopeReader.OBJECT_BYTES);
            if (read instanceof org.totipo.spi.BoundedRead.Present present) {
                var validation = validate(id, present.bytes(), root);
                if (validation.token() != null) tokens.add(validation.token());
                else candidates.add(new TokenStoreObservation.CandidateDiagnostic(id, validation.reason()));
            } else {
                var reason = read instanceof org.totipo.spi.BoundedRead.Undersized
                        || read instanceof org.totipo.spi.BoundedRead.Oversized
                        ? TokenStoreObservation.Reason.INVALID_STORAGE : TokenStoreObservation.Reason.UNAVAILABLE;
                candidates.add(new TokenStoreObservation.CandidateDiagnostic(id, reason));
            }
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

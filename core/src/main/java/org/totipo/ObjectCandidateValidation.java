package org.totipo;

import java.util.Arrays;
import java.util.Objects;

/** Descriptive result of synchronous immutable object validation by an open authenticated session.
 * Success establishes exact physical length, envelope authentication, keyed OBJECT_ID match,
 * padding/framing validity and current semantic TOKEN validity under that vault root.
 * It establishes neither current-head status, graph completeness, freshness, persistence,
 * provider/store origin, remote synchronization, import nor contradiction with another candidate.
 * Values are descriptive and publicly constructible; only a session call establishes validation.
 * No plaintext, TOKEN value, parent list, key or parser diagnostics are returned. */
public sealed interface ObjectCandidateValidation {
    /** Candidate validation failure, including wrong vault/root, AEAD failure, keyed ID mismatch,
     * malformed framing, padding failure or invalid current TOKEN grammar. No candidate bytes
     * or finer crypto/parser failure diagnostics are retained. */
    record Invalid() implements ObjectCandidateValidation { }

    /** Supplied canonical object ID and exact validated 1024-byte opaque ciphertext snapshot.
     * Construction defensively copies the representation and access returns defensive copies.
     * No plaintext TOKEN, secret, issuer/account, parent model, metadata projection, root,
     * fingerprint, key or validation diagnostics are retained. toString redacts ciphertext.
     * This publicly constructible value is descriptive; only a successful session call establishes validation.
     * Retains no session or secret and remains
     * readable after session close. Compare only results validated against the same vault root:
     * equal IDs with unequal representations indicate an integrity contradiction.
     * Canonical v1 encryption is deterministic, including zero padding, so exact ciphertext
     * equality also compares complete canonical plaintext without disclosing it. */
    record Valid(RevisionId objectId, byte[] representation) implements ObjectCandidateValidation {
        public Valid {
            Objects.requireNonNull(objectId); Objects.requireNonNull(representation);
            if (representation.length != 1024) throw new IllegalArgumentException("Object representation length");
            representation = representation.clone();
        }
        /** Returns an independently owned ciphertext copy. */
        @Override public byte[] representation() { return representation.clone(); }
        @Override public boolean equals(Object other) {
            return other instanceof Valid v && objectId.equals(v.objectId)
                    && Arrays.equals(representation, v.representation);
        }
        @Override public int hashCode() { return 31 * objectId.hashCode() + Arrays.hashCode(representation); }
        @Override public String toString() { return "Valid[objectId=" + objectId + ", representation=redacted]"; }
    }
}

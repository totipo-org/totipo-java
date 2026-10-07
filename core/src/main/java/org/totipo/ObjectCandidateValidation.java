package org.totipo;

import java.util.Arrays;
import java.util.Objects;

/** Independent current-family validation, not a graph fact or an import capability.
 * Values are descriptive and publicly constructible; only a session call establishes validation.
 * No plaintext, TOKEN value, parent list, key or parser diagnostics are returned. */
public sealed interface ObjectCandidateValidation {
    /** Malformed, unauthenticated, wrong keyed identity or invalid current TOKEN grammar. */
    record Invalid() implements ObjectCandidateValidation { }

    /** Exact validated identity and ciphertext. Retains no session or secret and remains
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

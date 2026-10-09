package org.totipo;

import java.util.Objects;
public sealed interface CreateVaultResult {
    record Created(VaultSession session) implements CreateVaultResult {
        public Created { Objects.requireNonNull(session); }
    }
    record AlreadyExists() implements CreateVaultResult { }
    enum FailureReason { STORAGE, OBJECT_DATA_OBSERVED }
    record Failed(FailureReason reason) implements CreateVaultResult {
        public Failed { Objects.requireNonNull(reason); }
        public Failed() { this(FailureReason.STORAGE); }
    }
    record Uncertain() implements CreateVaultResult { }
}


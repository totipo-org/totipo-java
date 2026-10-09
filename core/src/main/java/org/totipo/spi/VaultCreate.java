package org.totipo.spi;

import java.util.Objects;

/** Opaque create-only VAULT publication knowledge. Core must freshly verify canonical bytes.
 * Failed and AlreadyPresent positively establish no canonical mutation by this call.
 * Uncertain permits possible publication; never remove or repair canonical state. */
public sealed interface VaultCreate {
    record Created() implements VaultCreate { }
    record AlreadyPresent() implements VaultCreate { }
    record Failed(StoreFailure reason) implements VaultCreate {
        public Failed { Objects.requireNonNull(reason); }
    }
    record Uncertain(StoreFailure reason) implements VaultCreate {
        public Uncertain { Objects.requireNonNull(reason); }
    }
}

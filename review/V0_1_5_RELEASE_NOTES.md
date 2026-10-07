## v0.1.5

### Highlights

Adds `VaultSession.validateObject(RevisionId, byte[])` for externally obtained
immutable object representations using an already-open authenticated vault.
Validation requires no password re-entry/KDF or root-key export and performs no
import, store mutation or state update.

Results are `ObjectCandidateValidation.Invalid` or `Valid`: the latter defensively
owns the exact validated 1024-byte ciphertext and supplied canonical object ID,
without exposing plaintext TOKENs, parent IDs, metadata or session/root material.
Malformed, wrong-root and unauthenticated candidates collapse to Invalid, including
keyed identity, framing, padding and semantic TOKEN failures.

### Compatibility and scope

This is a source/binary compatible additive API: one default session method and
an entirely new result hierarchy. Existing external `VaultSession` implementations
retain compatibility and inherit `UnsupportedOperationException` when validation
is unsupported. This does not expand the project's long-term API stability promise.

No SPI change, storage-NIO behavioral change, protocol/spec/wire/crypto/vector
change or external dependency change. Both `org.totipo:totipo-core` and
`org.totipo:totipo-storage-nio` advance together to 0.1.5. Java 17 remains the
production target; BC remains runtime-only through core at 1.86. Totipo Vault
Format v1/r18 remains pinned to `4623a7e1718e23504903096c92332597057bd8f0`.

No Android/SAF support claim, reconciliation/import API or VAULT candidate
validation is supplied. A single call determines neither contradiction against
another candidate, current state, graph completeness, freshness nor remote sync.
Existing qualification limits and application responsibilities remain in README,
API_DESIGN and SPI_DESIGN.

### Validation

The preparation report records local qualification and the human Nix checkpoint.
The protected release workflow must revalidate the reviewed source, normal/offline
suites, all 90 portable cases, Java 17 bytecode, artifact inventories and both
consumer modes before publication. Exact remote bytes and fresh Central consumers
remain required before the source tag and GitHub release.

See [the 0.1.5 preparation report](V0_1_5_RELEASE_PREPARATION_REPORT.md) and
[the candidate API review](OBJECT_CANDIDATE_VALIDATION_API_REPORT.md).

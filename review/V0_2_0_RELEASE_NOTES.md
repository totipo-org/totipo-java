## v0.2.0

### Highlights

Targets exact Totipo Vault Format v1/r19 at
`cdb4e91be1c6d3704874b2b92457ffe7be5e9084`. VAULT is immutable and create-once;
in-place password change is removed. `VaultId` recognizes the exact canonical
VAULT instead of a root-derived fingerprint, including structural pre-unlock
recognition through `Totipo.vaultId(byte[])` without password authentication/KDF.

Core consumes a direct cohesive `TotipoStore` provider. Obsolete storage adapters,
legacy discovery/publication interfaces and public VAULT staging/replacement SPI
are removed. Ordinary NIO shared-store mode retains strong hard-link no-replace
publication. Creation vetoes observed orphan object-candidate names and freshly
revalidates canonical VAULT after create-only publication.

The experimental `NioStoreComposition.coordinatedDelegate(...)` replaces
`NioTotipoStore.openPrivate(...)` for externally serialized provider composition.
Its owner must serialize the whole root across store calls, sessions, writers and
bridge mutations. Java qualification does not establish that external discipline.

### Compatibility and scope

0.2.0 is intentionally source/binary incompatible with the 0.1.x experimental API.
The minor pre-1.0 bump reflects deliberate public API removal and breaking SPI
simplification, with no compatibility shims. Both Maven modules advance together.
TOKEN/state/TOTP/crypto formats remain unchanged; Java 17 production bytecode and
core's sole runtime dependency, BC 1.86, remain unchanged. No new production
dependency or FFM/native boundary is introduced.

Migration, Android sync and application reconciliation are deferred. Previous
physical Android qualification of the underlying move strategy does not qualify
the new Java API. Existing operation-scoped conformance, provider and application
responsibilities in README, API_DESIGN and SPI_DESIGN remain.

### Java consumer migration

- Remove `VaultSession.changePassword(...)`, `PasswordChangeResult`, and
  `VaultFingerprint`/`session.fingerprint()`. Credential/root changes require a
  different vault; migration is an application responsibility.
- Use `VaultId`, `VaultSession.vaultId()` and `Totipo.vaultId(byte[])`. Structural
  recognition alone proves neither authenticity, freshness nor intended origin.
- Implement cohesive `TotipoStore`, including `createVault(byte[])` returning
  `VaultCreate`. Replace `VaultPrepare`, `PreparedVault`, `VaultInstall`,
  `VaultReplace`, `VaultBootstrapStorage`, `VaultBootstrapReplacementStorage`,
  `DiscoverySource` and `V1ObjectPublicationStore` integrations. Remove reliance
  on `ApplicationVaults`, `StoreAdapter` and NIO legacy provider wrappers.
  `format.ObjectId` is now package-private; application IDs use `RevisionId`.
- Handle `CreateVaultResult.Failed.reason()` / `FailureReason`, particularly
  `OBJECT_DATA_OBSERVED`. Do not blindly retry initialization over existing objects.
- Ordinary consumers retain `NioTotipo` and `NioTotipoStore.open(...)`. Externally
  coordinated providers replace `openPrivate(...)` with
  `NioStoreComposition.coordinatedDelegate(...)` under its serialization contract.
  The direct-core implementation bridge is `format.VaultLifecycle`; obsolete
  bridge overloads are removed.

The preparation report links complete `javap -protected -s` inventories and the
accounted delta against canonical published 0.1.5, including nested result types
and descriptors.

### Validation

Local qualification and the separate human Nix checkpoint are recorded in
[the preparation report](V0_2_0_RELEASE_PREPARATION_REPORT.md). Release-source CI
and the protected workflow must independently validate the exact committed
source, normal/forced-offline builds, 92/92 required r19 cases, 99 snapshot records,
Java 17 bytecode, ten unsigned artifact hashes and module/POM consumer modes.
Exact remote bytes and fresh Central-only consumers remain publication gates.
These notes describe preparation and do not claim publication.

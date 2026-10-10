# Totipo Java

Portable Java libraries targeting Totipo Vault Format v1, revision **r19**.
The exact committed specification and 92-case language-neutral corpus are pinned
in [SPEC_PIN.md](SPEC_PIN.md).

Normal filesystem clients use `NioTotipo.open(path, password)` or
`NioTotipo.create(path, password)`, then `VaultSession`, immutable `VaultState`,
create/update/merge builders and session-backed TOTP. The directory must already
exist. Opening, creation, saves and close may block; inspect their explicit results.
See [API_DESIGN.md](API_DESIGN.md) and [SPI_DESIGN.md](SPI_DESIGN.md).

The provider architecture is:

```text
Totipo / VaultSession
    -> TotipoStore
    -> NioTotipoStore
```

The cohesive SPI is explicitly experimental and unfrozen. Providers interpret
storage layout, entry observations, bounded bytes, opaque create-only publication,
durability acknowledgement and lifetime. Protocol interpretation stays in core.

VAULT is immutable and create-once. Credential, bootstrap policy or root changes
require a different vault. Migration is an application workflow outside this
milestone. Creation validates intended bytes locally, publishes without overwrite,
then freshly rereads canonical `vault`, requiring exact 87-byte equality, structural
canonicality, credential authentication and recovery of the generated root before
returning Created. Failed verification after possible publication is Uncertain;
canonical data is never repaired.

Before generating secrets, ordinary creation observes `objects-v1`. Any observed
exact 64-character lowercase hexadecimal direct-child name vetoes creation with
`Failed(OBJECT_DATA_OBSERVED)`, including incomplete scans. Names are unauthenticated
contextual evidence of possible existing Totipo data. The library preserves them
and requires recovery, reconfiguration or a new-location workflow. No globally
complete enumeration is required.

`session.vaultId()` returns immutable, non-secret `VaultId`: SHA-256 of the exact
canonical VAULT representation. `Totipo.vaultId(byte[])` also derives it before
unlock, requiring the exact length, magic and bootstrap version. It runs no KDF or
authentication and claims neither authenticity nor freshness. The ordinary text
representation is lowercase hexadecimal. TOKEN publication and exact retries leave
VAULT byte-identical.

The implementation executes **92/92 portable cases without skips**. Bootstrap,
object crypto, TOKEN encoding, metadata, graph/fold, authorship, publication,
storage, TOTP and immutable VAULT workflows are covered. Bootstrap encoding,
Argon2id, AES-GCM root wrapping, HKDF, keyed object addressing, object encryption,
TOKEN grammar and TOTP remain byte-compatible. See
[the simplification report](review/V1_R19_REPIN_SIMPLIFICATION_REPORT.md).

Conformance is scoped by supported operation (§20): core protocol foundation and
audited facade TOKEN projections/metadata, plus qualified NIO/core storage
observation, create-only publication and durability-result handling. This is not
blanket application certification. Interactive applications supply empty-password
confirmation, truthful presentation, conflict disclosure and safe rendering of
untrusted text. Tombstones retain secrets/history; deletion is not secure erasure.
There is no full API stability promise, independent interoperability demonstration,
security audit or new desktop/Android readiness claim.

The two production modules are:

- `totipo-core`: portable Java 17 protocol/application API, crypto, TOKEN/state,
  authorship/folds, TOTP, immutable VAULT creation/opening and the storage SPI.
- `totipo-storage-nio`: portable Java 17 layout-only filesystem provider and the
  normal NIO facade, exposing core transitively. Directory durability is injectable.

Core uses Bouncy Castle **1.86** only for lightweight Argon2id. All other crypto,
including SHA-256, uses JDK providers. Jackson and JUnit are test-only. Both modules
compile with `--release 17`; checks inspect every production class (major 61,
minor 0). The build uses JDK 25 and pinned Gradle 9.8.0, strict dependency verification
and locks. No native access/FFM or Android dependency is required.

Normal repository qualification on **x86_64-linux** is exactly:

```sh
nix flake check path:.
```

This is the authoritative CI and reproducible qualification environment. It uses
JDK 25, Gradle 9.8.0 and Python 3 from the committed Nix lockfile, with a fixed
Gradle dependency cache. It runs the full Java tests, r19 accounting/integrity,
build/Javadocs/sources, publication verification, independent module and POM-only
consumers, and Python release guardrails. Qualification runs offline after filling
an isolated Gradle home from the fixed local replay cache; Gradle locks and strict
verification remain enabled. No signing, upload, tagging or release action runs.
No second ordinary Nix build is needed. `nix build` optionally materializes the
same derivation's ten verified unsigned Maven-shaped artifacts under
`result/publication/`, plus inventory and small qualification summaries.

`package-deps.json` materializes the already locked/verified dependencies using
nixpkgs `gradle.fetchDeps`. Only when dependency inputs change, a human runs:

```sh
nix run path:.#update-package-deps
```

Review every generated coordinate/version, artifact URL and SRI hash against the
Gradle locks, verification metadata and canonical artifacts, including build-plugin
transitives and parent/BOM metadata. Reject unexpected modules, dynamic versions
and snapshots. Local Totipo consumer artifacts must come from staging, never from
this download cache. Cache regeneration is separate from qualification and never
runs in CI. Keep `flake.lock` pinned; updating dependencies does not authorize a
Nix input/toolchain update. Agents must not run Nix.

Direct Gradle commands remain supported for development with JDK 25, in the
existing development shell (`nix develop` or `direnv allow`) or on the host:

```sh
./gradlew clean test
./gradlew build
./gradlew dependencies
./gradlew --offline --no-daemon --no-build-cache --rerun-tasks clean test
```

The provider integration suite assumes a case-sensitive host with symlinks,
hard links, directory channels accepting force, POSIX permissions and `mkfifo`.
The normative corpus must execute without skips even if optional provider fixtures
use assumptions. NIO qualification remains limited to the local case-sensitive
Linux filesystem tested here. Windows, macOS, Android, case-insensitive providers
and physical crash/power-loss behavior are not qualified by Java tests.

`NioDurability` requests directory-channel force through pure Java NIO. Successful
return means the provider accepted the required operation; Java SE does not promise
universal directory-fsync behavior. Unsupported capability fails rather than being
treated as durable. `StorageDurability` remains injectable.

Scans expose all observed direct children of exact `objects-v1`, with no-follow
kind and optional length observations. Core selects canonical names and freshly
reads candidates. Bytes are hostile; bounded reads, authentication and exact grammar
validation are mandatory. Diagnostics preserve independently valid observations,
without certifying completeness or freshness. Exact spelling matters: alternate-case
siblings cannot supply canonical data and alias collisions fail conservatively.

Ordinary `NioTotipoStore.open`, `NioTotipo.open` and `NioTotipo.create` use exclusive
hard-link installation and retain no-replace exclusion for independent honest shared
writers. They never automatically fall back to ordinary moves.

The separate experimental `NioStoreComposition.coordinatedDelegate(root, durability)`
factory retains complete-stage ordinary-move publication for an external coordinated
owner, such as a future Android CoordinatedPrivateStore. That owner must wrap one
persistent delegate and serialize **every** store call, session, writer, bridge
mutation and handle across the whole root. Direct use without this policy violates
the contract. Core coordinates only one session. Move publication forces the actual
canonical file and its namespace, retaining conservative uncertainty, but provides
no atomic exclusion against independent writers. The ordinary facade remains the
appropriate entry point for directly synchronized/shared desktop stores.

Session synchronization retains separate provider and local secret/lifecycle locks.
Provider observations, validation, saves, retries and cleanup remain serialized;
setters and TOTP do not wait on provider I/O under the local lock. Publication plans
and retries retain frozen ciphertext and acknowledge only the configured local store,
without claiming remote propagation. TOKEN/state/authorship/merge behavior is retained.

The vendored snapshot is test-only and excluded from production JARs. Java derives
behavior from the exact normative spec/corpus, without importing upstream implementation
code. Historical review reports remain unchanged. Build-cache/dependency inputs need
no regeneration when dependencies and build configuration are unchanged.

## Maven consumption

The Maven coordinates for published Java implementation version **0.1.5** are
shown below.
Maven Central is the binary distribution channel; the manually dispatched
[Release workflow](.github/workflows/release.yml) validates, signs, publishes and
verifies each release before creating its source tag and GitHub release.
Applications consuming that version use Maven Central and:

```kotlin
implementation("org.totipo:totipo-storage-nio:0.1.5")
```

`totipo-storage-nio` is the normal filesystem/NIO entry point and provider. It
exposes `totipo-core` transitively, including `VaultSession` and `VaultState`.
Use `NioTotipo` and the high-level application API; the SPI guidance above remains
unchanged. For portable protocol/application API and core implementation without
an NIO provider:

```kotlin
implementation("org.totipo:totipo-core:0.1.5")
```

Core brings Bouncy Castle 1.86 at runtime for Argon2id, without exposing BC as a
public compile dependency. Both artifacts require Java 17; tests, test fixtures,
and the specification snapshot are excluded from publications.

<!-- prepared-release:start -->
Java 0.2.0 is released. See RELEASE_CHECKLIST.md.
<!-- prepared-release:end -->

0.2.0 is intentionally source/binary incompatible with the 0.1.x experimental API.
Independent
session object-candidate validation is retained, without import or root export.
Specification revisions do not mechanically dictate Java semantic versions.
See [the release checklist](RELEASE_CHECKLIST.md) for exact provenance,
credential-free validation and the protected release-environment approval gate.
The [J1.1 namespace migration report](review/J1_1_NAMESPACE_MIGRATION_REPORT.md)
records the namespace migration and supersedes J1 coordinates. The v0.1.1
preparation validation is recorded in the
[release-preparation report](review/V0_1_1_RELEASE_PREPARATION_REPORT.md), with
[release notes](review/V0_1_1_RELEASE_NOTES.md).
Publication checks additionally require Python 3.9+ (standard library only).

# v1/r19 repin and simplification report

Java implementation and agent-run validation are complete. Human Nix validation
is **PASS**, user-reported successful `nix flake check`; the milestone is complete.
All work remains unstaged/uncommitted. No publication or remote CI was run.

## 1. Starting state

Clean `main`, HEAD `7d3461e297f60711c8ec4a202d0f6dc68c0e81bc`, VERSION `0.1.5`.
Modules: totipo-core and totipo-storage-nio; experimental unfrozen SPI; v1/r18
at specification commit `4623a7e1718e23504903096c92332597057bd8f0`.
Exact starting public/protected compiled type/member/descriptors are in
[r19/PUBLIC_API_BEFORE.txt](r19/PUBLIC_API_BEFORE.txt).

## 2. Baseline validation

Before editing, `./gradlew tasks --all`, `./gradlew clean test`, `./gradlew build`,
`./gradlew dependencies`, both module runtimeClasspath reports, snapshot hashes,
and `git diff --check` passed. The README's exact strict command
`./gradlew --offline --no-daemon --no-build-cache --rerun-tasks clean test`
also passed. Core: 382 tests; NIO: 146; total 528. Zero failures/errors/skips.
Integrity: 97/97 hash records, 3 snapshot tests, 1 profile test; 90/90 cases executed.
Java 17 major 61/minor 0 in both modules; build JDK 25. Runtime: BC 1.86 in core;
NIO adds only core. No baseline failure was bypassed.

## 3. Exact r19 pin and hashes

Authoritative committed source: `cdb4e91be1c6d3704874b2b92457ffe7be5e9084`
from https://github.com/totipo-org/totipo-spec. Its normative header identifies
Totipo Vault Format v1/r19. All five reviewed hashes matched before import:

| Artifact | SHA-256 |
| --- | --- |
| spec/totipo-vault-format-v1.md | 8bb76b890086eb4bf89271edd5861e02f833f3ffa11a83b5865080ed4e4228cb |
| vectors/manifest.json | 3953dbc315b4dcb3d0d31dd399bf82a15cb38c49fde2e2b93ea81c5cfe0ec714 |
| requirements/v1-pre-rc.json | 7242fc557a7ca56452c934bedc5e7dc2835248f5cd36fa220f05588dc967ec80 |
| vectors/case.schema.json | 99805d442f13872cd4febe9ac8ae4f36fd25607580bc1e457ff5ae2efe199e23 |
| vectors/manifest.schema.json | f265771f904be57d19602dd6da9a572c16b3f80fdd166c5177721aaa3f9a91ec |

Imported exact upstream bytes only, without modifying upstream material.
99/99 SNAPSHOT.sha256 records cover 92 manifest cases and seven supporting files.
SPEC_PIN, profile integrity, loaders/consumers and current documentation target r19.

## 4. 90 → 92 corpus reconciliation

Five retired IDs disappear: bootstrap rewrap and four VAULT replacement cases.
Seven appear: bootstrap known-answer-extra and VAULT_ID mutation; VAULT different
existing, failed revalidation, wrong type, object publication unchanged and exact
object retry unchanged. Counts: bootstrap 5, crypto 5, encoding 30, fold 6,
graph 13, metadata 7, size 1, storage 13, TOTP 3, VAULT 9 = 92.
All required IDs execute before being recorded in the manifest-derived executed set.
[r19/CORPUS_DELTA.txt](r19/CORPUS_DELTA.txt) records exact case-level differences.

## 5. Architecture inventory

[r19/ARCHITECTURE_BEFORE.txt](r19/ARCHITECTURE_BEFORE.txt) records every matching
production/test source and responsibilities searched before editing.

| Types/concepts | Decision | Responsibility/disposition |
| --- | --- | --- |
| VaultSession | SIMPLIFY | Remove password change and root recognition; retain TOKEN/state API and locks. |
| PasswordChangeResult, VaultFingerprint, root fingerprint helpers | DELETE | Retired mutable VAULT/root recognition. |
| VaultBootstrapReplacementStorage, VaultReplace | DELETE | Replacement only. |
| VaultPrepare, PreparedVault | DELETE | Public staging has no security property beyond local validation plus canonical post-publication verification. |
| VaultInstall | REPLACE | Minimal create-only outcome. |
| VaultBootstrapStorage | DELETE | Superseded by cohesive TotipoStore. |
| VaultLifecycle | REPLACE | One direct-store core construction helper, without replacement paths. |
| ApplicationVaults | DELETE | Duplicate lifecycle orchestration and superseded multi-interface overloads. |
| StoreAdapter | DELETE | Move observation/publication responsibilities to direct TotipoStore consumers. |
| DiscoverySource, V1ObjectPublicationStore | DELETE | Superseded provider contracts; move semantic tests to SPI fixtures. |
| TokenStoreReader, TokenPublisher, TokenStoreObservation | SIMPLIFY | Consume layout SPI directly; retain protocol semantics in core. |
| NioDiscoverySource, NioV1ObjectPublicationStore, NioApplicationPublication, NioVaultBootstrapStorage | DELETE | Legacy wrappers/adapters. |
| NioVaultStorage | SIMPLIFY | Internal complete-stage create-only publication and cleanup. |
| NioObjectStorage | SIMPLIFY | Retain immutable publication/exact retry; remove legacy-only branches. |
| NioCanonicalInstaller | KEEP | Strong shared hard links and externally coordinated ordinary moves. |
| StorageDurability | KEEP | Injectable containing-directory persistence capability. |
| TotipoStore | SIMPLIFY | Primary cohesive storage boundary with createVault. |
| NioTotipoStore | SIMPLIFY | Remove staging/replacement and normal openPrivate factories. |
| NioTotipo | KEEP | Normal application facade stays shared-safe. |
| replacement staging, BASE/CURRENT, replacement fault seams/tests | DELETE | No current r19 semantic responsibility. |
| creation orphan observation | REPLACE | Core pre-entropy veto for observed plausible names, including incomplete scans. |
| bootstrap post-publication reread | REPLACE | Fresh exact-byte, structural, credential and generated-root verification before success. |

No current non-test module requires the legacy interfaces. No protocol logic needs
to move into NIO. The existing split provider/local lock model can remain intact.

Every matching pre-edit production/test source also has a final KEEP/SIMPLIFY/
REPLACE/DELETE classification in
[r19/ARCHITECTURE_DISPOSITION.txt](r19/ARCHITECTURE_DISPOSITION.txt).
No escalation condition was found.

## 6. Removed password-change API

Deleted VaultSession.changePassword and PasswordChangeResult, without deprecation,
aliases or a package-private alternative. Both duplicate lifecycle implementations
and all authentication/stale/replacement orchestration are removed.

## 7. Removed replacement SPI

Deleted VaultReplace, replacement bootstrap interface, staging replacement methods,
replacement result states, NIO atomic/fallback replacement paths and fault seams.
No dormant replacement implementation remains.

## 8. Vault staging disposition

Deleted VaultPrepare/PreparedVault/VaultInstall. TotipoStore.createVault returns
VaultCreate: Created, AlreadyPresent, Failed(reason), Uncertain(reason).
The provider stages complete bytes internally in one synchronous operation, with
cleanup in finally. No outstanding staging registry/consumption lifecycle remains.
Local intended validation plus fresh canonical verification reproduces the required
security property under the existing storage trust model; public staging adds none.

## 9. TotipoStore direct-provider architecture

Totipo and VaultSession use direct-store core logic. TokenStoreReader scans and
bounded-reads TotipoStore; session publication and TokenPublisher publish opaque
bytes directly. Storage/providers retain no token/root/password/identity semantics.
The public format.VaultLifecycle static bridge is necessary for the two-package
core layout and has only direct-store open/create and structural identity entry
points. It implements current lifecycle responsibilities rather than legacy adapters.

## 10. StoreAdapter disposition

Deleted entirely. Its name selection, read taxonomy and acknowledgement interpretation
now live at their direct core consumers. No reconstructed invalid-length streams remain. ObjectId is internalized now that
NIO no longer needs a protocol-aware identity type.

## 11. ApplicationVaults disposition

Deleted entirely, including all multi-interface and adapter-building overloads.
Its current ownership/password handling is consolidated in VaultLifecycle.
It is not retained as a test bridge.

## 12. Legacy storage-interface disposition

Deleted DiscoverySource, V1ObjectPublicationStore, VaultBootstrapStorage and
VaultBootstrapReplacementStorage plus their NIO wrappers. BoundedObjectRead is
also removed because bounded content reading belongs to the provider now.
Semantic fixtures use TotipoStore; no compatibility shim is supplied.

## 13. VaultId API

Added immutable VaultId: 32 defensively owned bytes, copy-returning bytes(), lowercase
hex(), value equality and stable Arrays.hashCode. toString deliberately prints
VaultId[hex]. Session.vaultId replaces root recognition; it remains descriptive after
close. The root-derived crypto helper and unlock-result recognition methods are gone.

## 14. Pre-unlock VaultId derivation

Totipo.vaultId(byte[]) validates exact 87-byte length and the existing magic/bootstrap
version parser, then uses JDK SHA-256 on the exact snapshot. No password, KDF or root
access. It establishes structural representation identity without authenticity,
freshness, origin or intention. All r19 known answers and one-bit mutation answers pass.

## 15. Initial creation semantics

Only positive canonical absence permits initialization. Any exact existing entry,
including wrong kind/different bytes, blocks unchanged. A definite concurrent winner
is AlreadyExists; definite no-mutation provider failure is Failed(STORAGE); possible
mutation without acknowledgement is Uncertain. No automatic repair or retry occurs.

## 16. Post-create canonical revalidation

After durability acknowledgement core freshly requests canonical vault (87 bytes,
provider reads at most 88). It requires exact bytes, structural bootstrap, credential
authentication and generated-root equality before constructing a session. Failed
reread/size/kind/equality/authentication or unchecked post-call failure is Uncertain.
Tests count three KDF operations (encode, local check, canonical check), two reads,
and one create attempt; injected canonical authentication failure is not success.

## 17. Orphan-candidate veto

Core observes objects-v1 before root/salt/nonce generation and KDF. An observed
64-character lowercase hexadecimal name vetoes even in incomplete scans and for
stale/unknown kind observations. Failed.reason() = OBJECT_DATA_OBSERVED explains
possible existing Totipo object data. No object validity/root claim, deletion or
unseen candidate is invented. Nonmatching observed names do not veto.

## 18. NIO create-only simplification

NioVaultStorage is now one internal complete-stage create operation, without
replacement flags, public read-back, stage handles or stage registries. Both publication
categories reuse NioFiles write/temp/cleanup helpers, the internal installer and
StorageDurability. Distinct fixed-name VAULT and object namespace/exact-retry sequencing
remain explicit. The object's legacy read-only acknowledgement branch is removed;
the pre-existing TotipoStore durability acknowledgement is unchanged.

## 19. Shared NIO publication semantics

Ordinary NioTotipoStore.open and NioTotipo convenience factories still choose hard
links only, without automatic fallback. Two independent honest shared creators
have exactly one create winner; the loser cannot overwrite it. Exact spelling,
wrong-kind hazards, alias collision and conservative uncertainty are retained.
A post-install FileAlreadyExistsException from a durability seam is explicitly
Uncertain, rather than falsely declaring an existing target/no-mutation result.

## 20. Removal of openPrivate

Both public NioTotipoStore.openPrivate overloads are deleted; no private flag,
application publication-mode enum, renamed exclusive facade or fallback replaces them.

## 21. Android move mechanism and integration boundary

The exact new API is:
`org.totipo.storage.nio.NioStoreComposition.coordinatedDelegate(Path, StorageDurability)`
returning TotipoStore. It is explicitly experimental provider composition, with one
factory and no normal facade mode switch. Android must obtain one persistent delegate,
wrap it in CoordinatedPrivateStore, and serialize every call/session/writer/bridge/
handle across the whole root. Direct uncoordinated use violates its contract.

storage-nio retains complete staging, ordinary Files.move without options, pre-move
absence check, actual canonical file force and directory persistence. Copy-based and
partial-move fault simulations preserve uncertainty and canonical residue. No raw NIO
publication logic moved to core; no Java test claims Android physical qualification.

## 22. Session synchronization simplification

Removed password-change gate participation and duplicate bootstrap/publication owners.
One store is closed exactly once. Provider gate and local secret/lifecycle lock retain
their existing order and responsibilities: observations, validation, saves, retries,
cleanup/close versus local values, setters, TOTP and lifecycle. Existing close/write/
retry/merge observation races remain exercised; unrelated locking was not redesigned.

## 23. Object-publication invariance

Written, AlreadyPresentExact, ExistingDifferent, Failed and Uncertain remain intact.
Exact retries still force file, objects directory and root, then confirm exact bytes.
Frozen application uncertainty remains monotonic. Ordinary publication/exact retry
cases prove VAULT byte identity, without implementing cross-vault copying.

## 24. TOKEN/state/authorship invariance

VaultState, TokenState/Alternative/Head/Descriptor, create/update/merge/editors,
partial resolution, retries, SaveResult, refresh/state streams, object validation,
TOTP, graph/fold, metadata and tombstones retain their application contracts.
Their production implementations are unchanged except direct storage calls and
retired VAULT ownership/API fields. Relevant application/semantic tests remain.

## 25. Crypto/vector byte invariance

All common bootstrap password/salt/nonce/wrap-key/header/87-byte record fields
are unchanged. All crypto, encoding, fold, graph, metadata, size and TOTP case files
are byte-identical. The extra known answer is a standalone bootstrap fixture.
Argon2id, AES-GCM wrapping, HKDF hierarchy, keyed object addressing, object encryption,
TOKEN encoding and TOTP source algorithms did not change. Existing root/key/ciphertext/
tag and object/TOTP known-answer tests still pass. No crypto dependency is added.

## 26. Exact public API before/after

[r19/PUBLIC_API_BEFORE.txt](r19/PUBLIC_API_BEFORE.txt) and
[r19/PUBLIC_API_AFTER.txt](r19/PUBLIC_API_AFTER.txt) contain full javap -protected -s
inventories per module, including nested public types and descriptors.
Compiled public type totals: core 105 → 85; NIO 7 → 5.

Removed: password-change/result, fingerprint/value, replacement/bootstrap/staging SPI,
legacy provider interfaces/overloads, ApplicationVaults, NIO wrappers and normal
openPrivate overloads. Added: VaultId, session.vaultId, Totipo.vaultId, VaultCreate,
TotipoStore.createVault, focused Failed.reason/FailureReason and NioStoreComposition.
VaultLifecycle is the direct-core public implementation bridge. Token/state APIs
and ordinary NIO open/create descriptors remain stable. No compatibility shims. The legacy-adapter export format.ObjectId is now package-private;
the application RevisionId API and addressing algorithm are unchanged.

## 27. Deleted production files

- `core/src/main/java/org/totipo/PasswordChangeResult.java`
- `core/src/main/java/org/totipo/VaultFingerprint.java`
- `core/src/main/java/org/totipo/format/ApplicationVaults.java`
- `core/src/main/java/org/totipo/format/BoundedObjectRead.java`
- `core/src/main/java/org/totipo/format/DiscoverySource.java`
- `core/src/main/java/org/totipo/format/StoreAdapter.java`
- `core/src/main/java/org/totipo/format/V1ObjectPublicationStore.java`
- `core/src/main/java/org/totipo/format/VaultBootstrapReplacementStorage.java`
- `core/src/main/java/org/totipo/format/VaultBootstrapStorage.java`
- `core/src/main/java/org/totipo/spi/PreparedVault.java`
- `core/src/main/java/org/totipo/spi/VaultInstall.java`
- `core/src/main/java/org/totipo/spi/VaultPrepare.java`
- `core/src/main/java/org/totipo/spi/VaultReplace.java`
- `storage-nio/src/main/java/org/totipo/storage/nio/NioApplicationPublication.java`
- `storage-nio/src/main/java/org/totipo/storage/nio/NioDiscoverySource.java`
- `storage-nio/src/main/java/org/totipo/storage/nio/NioV1ObjectPublicationStore.java`
- `storage-nio/src/main/java/org/totipo/storage/nio/NioVaultBootstrapStorage.java`

## 28. Deleted obsolete tests and fixtures

- `core/src/test/java/org/totipo/api/PrivateNioWorkflowTest.java`
- `core/src/test/java/org/totipo/conformance/R18ProfileIntegrityTest.java`
- `core/src/test/java/org/totipo/format/DiscoverySourceTest.java`
- `core/src/test/java/org/totipo/format/FakeV1ObjectPublicationStore.java`
- `core/src/test/java/org/totipo/format/TokenStoreReaderTest.java`
- `core/src/test/java/org/totipo/format/V1ObjectPublicationStoreTest.java`
- `core/src/test/java/org/totipo/format/VaultLifecycleTest.java`
- `core/src/test/java/org/totipo/format/VaultNioWorkflowTest.java`
- `core/src/test/java/org/totipo/format/VaultTestStore.java`
- `storage-nio/src/test/java/org/totipo/format/NioDiscoveryTest.java`
- `storage-nio/src/test/java/org/totipo/storage/nio/DurabilityContractTest.java`
- `storage-nio/src/test/java/org/totipo/storage/nio/NioApplicationPublicationTest.java`
- `storage-nio/src/test/java/org/totipo/storage/nio/NioPublicationTest.java`
- `storage-nio/src/test/java/org/totipo/storage/nio/NioVaultStorageTest.java`
- `storage-nio/src/testFixtures/java/org/totipo/storage/nio/ReplacementStorageFaults.java`
- `storage-nio/src/testFixtures/java/org/totipo/storage/nio/VaultStorageFaults.java`

Mixed PublicApiTest/SpiApplicationTest/NioSpiTest/NioPrivateStoreTest also remove
password-change, replacement, stale-comparison and public-stage-specific methods.
Their relevant object/state tests use direct SPI fixtures.
[r19/DELETED_TEST_METHODS.txt](r19/DELETED_TEST_METHODS.txt) lists removed/renamed
method declarations; the Git diff supplies their complete reviewable changes. Interface-specific
lifetime/stream/legacy read-only publication tests disappear; provider bounded-read,
exact-name, durability/recovery, race and immutable creation semantics are exercised
through the cohesive SPI suites. Removed r18 cases are replaced only by exact r19
manifest contents, never by invented translations of retired workflows.

## 29. Added/updated r19 tests and removal audit

Added VaultIdTest, VaultCreationTest, ImmutableNioWorkflowTest, TokenObservationTest,
NioCreateTest, R19ProfileIntegrityTest and TestStore. Updated bootstrap/vault/storage
consumers, manifest execution accounting, MemoryVault, PublicationTestStore,
application/SPI tests, TOKEN NIO workflows and object fault seams.
Core 320 + NIO 80 = 400 tests with no failures/errors/skips; all 92 cases execute.
The reduction from 528 reflects removal of obsolete interfaces/lifecycle tests,
not skipped conformance cases. New fault evidence includes dishonest canonical bytes,
post-create unavailability, no-entropy veto, concurrent shared creators, move-copy
canonical force, partial move uncertainty and post-mutation collision exceptions.

[r19/REMOVAL_AUDIT.txt](r19/REMOVAL_AUDIT.txt) classifies surviving retired-term
occurrences. Current production/docs have none. Survivors are historical review
reports, before/after architecture/API evidence, authoritative spec history and
intentional test assertions that the APIs are absent. Current r18 references are
only links to unchanged historical metadata evidence or upstream revision history.

## 30. Dependencies and supply chain

No new production dependency. Core runtime is bcprov-jdk18on 1.86; NIO exposes
core transitively. SHA-256 uses JDK. Gradle build files, VERSION, dependency catalog,
locks, verification metadata, wrapper and Nix inputs are unchanged. No dependency
cache regeneration, Android/filesystem/synchronization/migration library is needed.

## 31. Build/classfile/JAR validation

Normal clean tests/build and final forced offline build passed:
`./gradlew --offline --no-daemon --no-build-cache --rerun-tasks clean test build`.
Module runtime dependency reports passed. Exact 99 snapshot hashes, 92 executed
cases, Java 17 major 61/minor 0 checks, Javadocs and six JAR content checks passed.
All six production/sources/Javadoc JAR hashes match normal versus forced offline;
[r19/JAR_SHA256.txt](r19/JAR_SHA256.txt) records them. No spec/test resources or
retired classes are in production JARs. No FFM/native boundary change.

`python3 -B -m unittest discover -s publishing -p 'test_*.py'`: 21 passed.
`python3 -B publishing/release.py tests`: 400 tests, no failures/errors/skips,
3/3 snapshot + 1/1 profile; exact executed-set 92/92 assertion passed.
The updated consumer smoke compiled against built Java 17 JARs and ran with BC,
opening shared/coordinated delegates read-only. Release provenance/tool integrity
filters now reference r19. No release action was invoked.

Publication-dependent verifyPublication/consumerSmoke tasks and the full release
command containing them were not invoked, because the user prohibited publication.
The README's exact strict baseline suite and final nonpublication checks passed;
no claim is made that the Maven staging/POM consumer gates ran in this milestone.
Build logs/XML and normal hash evidence are available under /tmp/totipo-r19-*.

## 32. Recommended next Java version

VERSION stays 0.1.5. Existing additive releases use 0.1.x patch bumps; this deliberately
breaking pre-1.0 simplification calls for a minor bump, recommended **0.2.0**, subject
to a separate release review. No version, tag or Maven release was selected/published.

## 33. Desktop follow-up

No totipo-desktop changes. Repin to the eventual reviewed Java release; remove Change
Password UI/workflow, replace any fingerprint references with VaultId, and adapt
Failed.reason for observed object data. Ordinary NIO remains suitable for directly
synchronized/shared desktop stores. Migration is not implemented here.

## 34. Android follow-up

No totipo-android changes. Replace openPrivate with the new NioStoreComposition
factory inside CoordinatedPrivateStore. Keep one persistent delegate and a coordinated
session+bridge domain owning whole-root serialization. Prefer local canonical store
terminology to local replica. Use public pre-unlock VaultId derivation for immutable
local/provider VAULT comparison; gate M3A/M3B object sync on matching VAULT. Remove
password-change coordination/test assumptions. Preserve the already-qualified
Android move mechanism; Java tests do not qualify Android.

## 35. Human Nix

The exact existing human check is `nix flake check`, as recorded in the 0.1.5
preparation report. Existing environment entry is `nix develop` or `direnv allow`.
No Nix command was run by the agent. The user reported `nix flake check` successful.
Human validation is **PASS**, based on that report; no command log was supplied.
No new Nix check or dependency-cache regeneration was needed.

## 36. Remote CI

Not triggered. The current release workflow's integrity test selector was updated
for R19; release.py provenance/spec-pin hash and its tests now target the exact
snapshot. No remote identity queries, releases, publication, tags or pushes occurred.

## 37. Final Git state

HEAD/branch remain the starting values. VERSION is unchanged. All modifications,
deletions and new files remain unstaged/uncommitted; `git diff --cached --stat` is
empty and `git diff --check` passes. Existing historical reports are byte-unchanged.
Only totipo-java was edited. The milestone is complete with user-reported Nix success.

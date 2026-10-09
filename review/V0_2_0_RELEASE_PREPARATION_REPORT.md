# Totipo Java 0.2.0 release preparation

## 1. Starting state

Work was confined to totipo-java. Before editing, the requested Git/VERSION/pin
commands established clean `main`, an empty index, VERSION `0.1.5`, and the exact
v1/r19 pin. No implementation work remained uncommitted. Nothing was staged,
committed, tagged, signed, uploaded, pushed or dispatched. No Nix command was run
by the agent. Unsigned local Maven staging was used for qualification only.

## 2. Implementation HEAD

`857b71c666cfeb4a34c109267341916b19370f46`, “Simplify for immutable VAULT and r19”.
HEAD and branch remain unchanged. All implementation changes precede this diff.
The committed [simplification report](V1_R19_REPIN_SIMPLIFICATION_REPORT.md) is
historical evidence and was not rewritten.

## 3. Baseline validation

Before release edits:

```sh
./gradlew clean test
./gradlew build
python3 -B publishing/release.py tests
python3 -B -m unittest discover -s publishing -p 'test_*.py'
git diff --check
```

All passed: **400 Java tests**, zero failures/errors/skips; exact **92/92** portable
cases through the executed-set assertion; snapshot **3/3**, profile **1/1**;
**21 Python tests** before adding preparation coverage. Java 17 checks passed in
both modules. The declared runtime graph was core → BC 1.86 and NIO → core → BC
1.86; generated metadata and independent consumers subsequently verified it.
The baseline snapshot validated **99/99** records. Existing Javadoc missing-comment
warnings did not fail the build. The wrapper downloaded its pinned Gradle 9.8.0.

## 4. Release identity

VERSION is now **0.2.0**. Coordinates are `org.totipo:totipo-core:0.2.0` and
`org.totipo:totipo-storage-nio:0.2.0`. The checklist, workflow dispatch example,
consumer self-locks and marked README/API prepared-status blocks agree.
0.2.0 is prepared locally and not yet released. README dependency examples use
confirmed published **0.1.5**, with its availability separated from current API
architecture and prepared status.

[Occurrence classification](v0_2_0/OCCURRENCE_CLASSIFICATION.txt) records all **342**
matching original committed lines containing 0.1.5, 0.1.4, r18 or r19, including
hidden workflow files. Current metadata advances; published examples were manually
updated after Central confirmation; historical reports/notes, spec history,
normative snapshot, source comments and test fixtures remain unchanged. Classification
was based on the original implementation HEAD, not on replacement-generated text.

## 5. Exact r19 identity and hashes

Totipo Vault Format **v1/r19**, upstream commit
`cdb4e91be1c6d3704874b2b92457ffe7be5e9084`, **92 required cases**.

| Normative artifact | SHA-256 |
| --- | --- |
| spec | `8bb76b890086eb4bf89271edd5861e02f833f3ffa11a83b5865080ed4e4228cb` |
| manifest | `3953dbc315b4dcb3d0d31dd399bf82a15cb38c49fde2e2b93ea81c5cfe0ec714` |
| requirements | `7242fc557a7ca56452c934bedc5e7dc2835248f5cd36fa220f05588dc967ec80` |
| case schema | `99805d442f13872cd4febe9ac8ae4f36fd25607580bc1e457ff5ae2efe199e23` |
| manifest schema | `f265771f904be57d19602dd6da9a572c16b3f80fdd166c5177721aaa3f9a91ec` |

SPEC_PIN.md SHA-256 stays
`736f9489852dcf2d0f22fb13f7da9fcd2dca4de18f8d735fbb34aad6162dedd0`;
SNAPSHOT.sha256 stays
`8b8a6660923a425bc68d9bcd3843bd4cc35db642c494c4812f510c0fba7f0905`.
The exact inventory and every covered file validate before and after preparation.
No snapshot or protocol bytes changed.

## 6. Why a minor pre-1.0 bump

0.2.0 is intentionally source/binary incompatible with the 0.1.x experimental API.
Public removals and the breaking experimental provider SPI warrant a minor pre-1.0
bump rather than another additive patch release. Specification revisions alone do
not determine Java semantic versions. No compatibility with 0.1.5 is claimed.

## 7. Included implementation changes

The committed r19 simplification supplies immutable/create-once VAULT, removal of
in-place password change, exact-VAULT `VaultId` recognition including pre-unlock
structural derivation, direct cohesive `TotipoStore`, removal of obsolete adapters,
legacy interfaces and public staging/replacement SPI, orphan-candidate creation
veto, and fresh canonical VAULT revalidation after creation.

Normal NIO retains hard-link no-replace publication. Experimental
`NioStoreComposition.coordinatedDelegate(...)` replaces `openPrivate(...)` for
externally serialized composition. TOKEN/state/TOTP/crypto formats and the
production dependency boundary remain unchanged. This preparation changes no
production semantics or implementation Java.

## 8. Deferred work and scope

No migration implementation, Android sync, Android wrapper, application reconciliation,
new provider qualification, independent interoperability claim or security audit.
Consumer smoke does not prove external serialization. Previous Android move-strategy
qualification does not qualify this Java API physically on Android. Existing
operation-scoped conformance limits remain in README/API_DESIGN/SPI_DESIGN.
SPI_DESIGN already accurately describes r19 and needed no release-identity edit.

## 9. Breaking API comparison against published 0.1.5

Read-only canonical Central binary/source/POM/module downloads were saved under
`/tmp/totipo-0.2.0-release/`. Binary input hashes:

| Canonical artifact | SHA-256 |
| --- | --- |
| core 0.1.5 JAR | `e99609e59db1d9f52f80c446060e63ce7dde17ad69252fa87d397d0cb1577f81` |
| NIO 0.1.5 JAR | `9e559ec75fb09af068f876751f32d696c50c40988a16d28419dfa05d1d1c4dae` |
| core 0.1.5 sources | `d797c0854db469e8288b787dcbeb3dd3eb8e83062bea5c42b0013082b2cccdc4` |
| NIO 0.1.5 sources | `bee2a5c9f941360b9c499f1518e3b2201e1e94405a34a8d2709033419e2ed67f` |

Fresh `javap -protected -s` inventories were generated for all public types,
including nested types, from published 0.1.5 and prepared 0.2.0 JARs. Comparing by
type, declaration and descriptor (ignoring invocation order/blank lines), they
exactly match committed [before](r19/PUBLIC_API_BEFORE.txt) and
[after](r19/PUBLIC_API_AFTER.txt) inventories. Therefore the entire public delta
is the already-reviewed r19 delta. See the durable
[complete delta](v0_2_0/PUBLIC_API_DELTA.diff) and
[type accounting](v0_2_0/PUBLIC_API_COMPARISON.txt).
Public type totals: core **105 → 85**, NIO **7 → 5**; **31** removed exported types,
**9** added exported types, **5** changed surviving types. No unexpected changes.

| Delta group | Accounting |
| --- | --- |
| PasswordChangeResult, VaultFingerprint, VaultSession | Remove password-change/result and fingerprint; replace recognition with vaultId. |
| VaultId, Totipo | Defensive 32-byte value API; add Totipo.vaultId(byte[]); existing open/create descriptors retained. |
| CreateVaultResult.Failed / FailureReason | Add reason constructor/accessor and STORAGE/OBJECT_DATA_OBSERVED; retain no-argument constructor. |
| ApplicationVaults and legacy discovery/publication/bootstrap interfaces, including nested types | Removed with obsolete adapters; direct-core VaultLifecycle exposes vaultId/openSession/createSession. |
| format.ObjectId | Export removed by becoming package-private; application RevisionId remains unchanged. |
| PreparedVault, VaultPrepare, VaultInstall, VaultReplace and nested result types | Removed staging/replacement contract; VaultCreate plus AlreadyPresent/Created/Failed/Uncertain replaces create publication outcomes. |
| TotipoStore and NioTotipoStore | prepareVault replaced by createVault; NIO openPrivate overloads removed; ordinary open overloads retained. |
| NioDiscoverySource, NioV1ObjectPublicationStore, NioVaultBootstrapStorage | Removed legacy provider wrappers. |
| NioStoreComposition | Add coordinatedDelegate(Path, StorageDurability), returning cohesive TotipoStore. |

StoreAdapter and NioApplicationPublication were internal classes, removed from
source/artifacts rather than exported API removals. Every member of every removed
nested type is covered by its contract retirement, including generated record/enum
members. Token/state APIs and ordinary NioTotipo open/create descriptors stay equal.
No compatibility harness was used to turn intended removals into failures.

## 10. Release-preparation script design

The existing `publishing/release.py` owns preparation; no second release framework
was introduced. Existing responsibilities were inventoried first: dispatch inputs,
identity/pin/notes guards, Central retry state, ten-artifact inventory/comparison,
signature/remote verification, full/focused test accounting, secret inspection and
post-verification tag/release actions. Existing publication helpers remain separate.

`prepare <version> [--dry-run | --check]` reuses shared release-version validation
and exact note-path/heading selection. Preparation applies a stricter canonical
stable-semver policy (no qualifiers/leading zeroes) with numeric advancement.
Known-file planning validates all layouts before any write. Source mutation runs
no Gradle builds, fuzzing or qualification suite. No persistent preparation state
or parallel version/note parser was added. The template is an in-helper contract
using established Highlights / Compatibility and scope / Validation sections.

## 11. Prepare-command safety boundary

Preparation reads local Git branch/status only. It never stages, commits, tags,
pushes, signs, publishes, uploads, calls mutation APIs, dispatches Actions, reads
credentials or changes upstream repositories. Initial mutation/dry-run requires
clean `main`, a well-formed current VERSION, strictly newer target and exact r19
pin/inventory/file integrity. Missing, duplicate, ambiguous or unexpected metadata
layouts fail before writes. Historical and published-example roles are preserved.

Checks use local files and Git reads; no Maven credentials or GPG are needed.
Existing network/publication helpers are not on preparation's call path.
Tests make forbidden network, signing, publication, process and Git operations fail
immediately if reached.

## 12. Files automatically updated by prepare

The clean-baseline preparation planner edited exactly:

- VERSION
- RELEASE_CHECKLIST.md
- .github/workflows/release.yml (version input example)
- publishing/consumer-smoke/gradle.lockfile (two exact self-version entries)
- README.md (prepared status)
- API_DESIGN.md (prepared status)
- review/V0_2_0_RELEASE_NOTES.md (new version-selected skeleton)

To enforce the initial clean-tree requirement while developing this new command,
the helper's initial implementation was loaded alongside the existing release.py
from a temporary evidence file and applied to the still-clean baseline; only then
were the helper and tests installed/edited in the checkout. No Git cleanliness
bypass was added. The installed final command was validated with isolated clean
fixtures, including future preparation through the new markers, and actual
`prepare 0.2.0 --check` on this diff.

Manual review edits finished notes/checklist, updated published README examples
to confirmed Central 0.1.5, clarified intentional incompatibility, expanded the
independent smoke and added helper/tests/evidence/report. CI and release preflight
now discover all Python guardrail tests; release preflight also checks prepared
source metadata. Protected publication permissions/gates are preserved.

## 13. Dry-run, check and tests

Dry-run on the clean starting source validated all preconditions, listed the seven
planned paths and wrote nothing; Git status was checked before actual preparation.
Mutating prepare rejects an equal/older target, explicitly directing prepared users
to `--check`. Repeatable `prepare 0.2.0 --check` validates the reviewed notes,
metadata and exact spec integrity without writes; it permits the review diff.
Generated TODO notes must be reviewed before that check passes.

**34 Python tests passed**: 21 established guardrails plus 13 new preparation tests.
Coverage includes valid advancement, exact changed paths/locks, historical and
published examples, dry-run immutability, repeated check, equal/downgrade/malformed
inputs, unexpected current VERSION, dirty tree, branch, corrupt real pin/snapshot,
missing/ambiguous metadata, exact notes/version/template contract, future marker
reuse, inconsistent prepared locks and CLI routing. All fixtures are temporary;
no actual credentials, Git mutations, network mutation or publication are used.
Existing HTTP-error mock tests emit Python ResourceWarnings; their assertions pass.

## 14. Release notes

[0.2.0 notes](V0_2_0_RELEASE_NOTES.md) follow the established format and add a concise
Java migration section. They describe exact r19 identity, creation safeguards,
recognition and provider simplification, expected breaking APIs, unchanged formats,
dependencies and Java target, external serialization responsibilities and deferred
migration/Android sync. They make no release-completion or physical Android claim.

## 15. Publication metadata

`verifyPublication` and direct `publishing/verify-publication.py` passed for both
unsigned local publications. All GAV/POM/module/file identities use **0.2.0**.
Core's only POM dependency is BC **1.86 runtime**; NIO's only POM dependency is core
**exactly 0.2.0 compile**, exposing core transitively. Module API/runtime variants
and JVM **17** metadata agree. No extra dependency, mixed self-version, local path,
test fixture, Android dependency, review/spec resource or unexpected staged file.
The verifier remains the single structural/content/inventory authority.

## 16. Independent consumer smoke

Both local staging modes passed: Gradle module metadata and `-PpomOnly`. The separate
Gradle build resolves published-shaped JARs, with strict locks/verification and no
Maven Local, composite/source fallback. Compile coordinates are exactly core/NIO
0.2.0; runtime adds only BC 1.86.

The smoke loads runtime APIs/BC, opens ordinary shared NioTotipoStore and the
coordinated delegate read-only, calls ordinary NioTotipo.open on an empty root,
checks that opening does not mutate it, exercises structural pre-unlock VaultId
recognition and value/ownership behavior, and compiles session.vaultId and cohesive
TotipoStore.createVault/VaultCreate boundaries. The synthetic structural VAULT is
not authenticated. Coordinated construction proves no external serialization or
Android qualification. Retired API calls are absent.

## 17. Normal qualification

```sh
./gradlew clean test build verifyPublication consumerSmoke
python3 -B publishing/release.py tests
python3 -B -m unittest discover -s publishing -p 'test_*.py'
python3 -B publishing/verify-publication.py
```

PASS: **400** Java tests, zero failures/errors/skips; exact **92/92** case assertion;
3/3 snapshot and 1/1 profile; Java 17, builds/Javadocs, strict dependencies,
publication and independent module consumer. The final normal run included the
expanded current-API smoke. Full suite execution is also established by the forced
run below. Logs/evidence are under `/tmp/totipo-0.2.0-release/`.

## 18. Forced offline qualification

After preserving normal inventory outside build outputs:

```sh
./gradlew --offline --no-daemon --no-build-cache --rerun-tasks \
  clean test build verifyPublication consumerSmoke
python3 -B publishing/release.py tests
./gradlew -p publishing/consumer-smoke --offline -PpomOnly clean check
```

PASS: **30/30 tasks executed** in the full root build, **400** Java tests, zero
failures/errors/skips, exact 92/92 cases, Java/publication and module consumer gates.
POM-only independent build: **5/5 tasks executed**, boundary/runtime checks passed.
The forced run needed no network/dependency resolution bypass.

## 19. Reproducibility and exact hashes

Normal inventory was preserved at
`/tmp/totipo-0.2.0-release/normal-publication-sha256.json` before `clean`.
The established `release.py compare` function was invoked with exact
REQUESTED_VERSION `0.2.0`, REQUESTED_COMMIT/DISPATCH_SHA equal to implementation HEAD,
DISPATCH_REF `refs/heads/main`, and EXPECTED_INVENTORY containing that normal JSON.
**10/10 rebuilt artifact hashes match preflight**. Identity/input validation was
not weakened; inventory comparison uses the existing local helper, not remote
identity/tag validation. Exact inventory paths/hashes are durably recorded in
[PUBLICATION_SHA256.json](v0_2_0/PUBLICATION_SHA256.json).

| Unsigned artifact | SHA-256 |
| --- | --- |
| `totipo-core-0.2.0-javadoc.jar` | `863f8b31f9a66461cf60ec56785f112eaa6e327985c0c1b4bc5ef3a5edbb795c` |
| `totipo-core-0.2.0-sources.jar` | `e2661770e0e59ca733959b4931689a9253988352605087c390bfc3a045dd9b6c` |
| `totipo-core-0.2.0.jar` | `4f1fb4bb1ab5f0a78c0f2d9a1ed3146413c94f631e7f9b95477968a66968520f` |
| `totipo-core-0.2.0.module` | `e344cca2fe0297cb06f74acba63800fb85c69a24bc146f1165143a232984338e` |
| `totipo-core-0.2.0.pom` | `1a6bbd5b4d82c079cb621d66c89b446c09e2df33efe5e0e89dd5abfd35d7c136` |
| `totipo-storage-nio-0.2.0-javadoc.jar` | `ef1b8cf0a278eb64e0cb9073e627debeba298f0fd400378fadfdbf8cd9c7ac7c` |
| `totipo-storage-nio-0.2.0-sources.jar` | `bb690c98837f938b1cce75cdfc06b44b47b066c4cb937a4ec461f2dc82f5b04b` |
| `totipo-storage-nio-0.2.0.jar` | `776068249e689e94136748fb8ffd86c837e0af8bbb6cec8ae5e785c4eca4ba93` |
| `totipo-storage-nio-0.2.0.module` | `33a431575523bb545b57876b09fd04255e04b60b2c64541d72b2cda5f05c7afa` |
| `totipo-storage-nio-0.2.0.pom` | `4acf1725ac7bb5cd7a4299f729d5eceb82aa3ea524dca7c9d5a9b56616db87b2` |

Repository-index timestamps and signatures are excluded from this established
deterministic scope. No signatures were generated. The local JDK was OpenJDK
25.0.4.1; equality is for these actual normal/offline builds, not an all-JDK claim.

## 20. Snapshot, profile and corpus integrity

Full normal/offline `release.py tests`: exact 92/92 required-case execution, none
deferred/skipped. Focused final command:

```sh
./gradlew :core:test \
  --tests org.totipo.conformance.SpecSnapshotIntegrityTest \
  --tests org.totipo.conformance.R19ProfileIntegrityTest
python3 -B publishing/release.py focused-tests
(cd core/src/test/resources/totipo-spec/v1-pre-rc && sha256sum -c SNAPSHOT.sha256)
```

PASS: four focused tests, zero failures/errors/skips; snapshot 3/3 and r19 profile
1/1. **99/99 checksum records** pass. No stale R18 selector was retained.
The focused final run replaces core's full-suite XML with focused evidence, as in
the existing release flow; the full 400-test assertions were checked before it.
`prepare --check` independently revalidated all exact snapshot bytes afterward.

## 21. Public API and artifact inspection

All six production/source/Javadoc JARs were inspected. Binary contents exactly
match production class outputs, source contents exactly match production sources,
and Javadocs exactly match generated docs plus the established manifest. No test
classes/resources, vendored snapshot, review material or retired replacement,
password-change/fingerprint, adapter/legacy-provider classes appear.
Core has **153** production classes and NIO **19**; all are non-preview Java 17.
VaultId/Totipo.vaultId and NioStoreComposition are present; ordinary NIO open remains,
openPrivate is absent from public javap inventories. Internal ObjectId remains
package-private, as explicitly accounted by the simplification. No accidental
FFM/native boundary change: production sources and their API inventory equal the
committed r19 implementation, and bytecode checks remain exact.

## 22. Dependencies and supply chain

No production dependency or build plugin change. Core alone declares runtime BC
1.86; NIO has exact core 0.2.0 and no additional external dependency. Existing
wrapper checksum, strict locks/verification and external BC hash template remain.
Only consumer self-version lock entries changed. No credentials/signature files
or extra publication outputs were introduced. Canonical Central reads and public
GitHub CI reads were the only remote inspection; no remote mutation occurred.

## 23. Java and classfile boundary

Pinned Gradle **9.8.0**, JDK **25** toolchain, production release target **17**.
Every production class has magic `CAFEBABE`, minor **0**, major **61**; both published
module JVM attributes are **17**. Compilation warnings-as-errors remain enabled.
The independent consumer compiles to release 17 and loads the staged API/runtime
boundary. Android API/runtime/native qualification is unchanged and not inferred.

## 24. Human Nix

**PASS — user-reported for this preparation:** “flake check passed”. The agent
requested the exact current checkpoint, `nix flake check`, after all local agent
gates passed and ran no Nix command. No command log was supplied. The flake and
0.1.5/r19 release history support this established gate alone; the older 0.1.4
extra Nix-shell Gradle run is not introduced as a new 0.2.0 requirement.

## 25. Remote CI evidence

Read-only public GitHub evidence for implementation HEAD:
[CI run 37968836476](https://github.com/totipo-org/totipo-java/actions/runs/37968836476),
head `857b71c666cfeb4a34c109267341916b19370f46`, completed **success**.
This is implementation-commit CI, not release-preparation CI. This diff remains
uncommitted; release-source CI has not run and must pass after reviewed commit.
No workflows were dispatched.

Canonical Central metadata reported latest/release **0.1.5** for both modules;
read-only 0.2.0 POM queries returned **404/404**. This is point-in-time evidence,
not authorization to publish or a substitute for the protected workflow's fresh
absence/provenance/remote-byte checks. No Git fetch/push/tag operation was needed.

## 26. Release readiness

**READY FOR RELEASE-SOURCE COMMIT/CI**, subject to final human review of this
unstaged preparation diff. All local agent gates and the user-reported Nix gate
pass. Publication remains a separate explicitly authorized action after reviewed
source commit and successful release-source CI. This milestone has performed no
release/publication. Checklist gates separate source preparation, local
qualification, human Nix, commit/review, release-source CI and protected publication.

## 27. Final Git state

Branch remains `main`; HEAD remains
`857b71c666cfeb4a34c109267341916b19370f46`. All preparation edits are unstaged and
uncommitted; the index is empty. Final `git diff --check` passes. Production Java,
implementation tests, Gradle production configuration, spec pin/snapshot and all
historical reports/notes are byte-unchanged. The Java edit is confined to the
independent publication consumer smoke. No generated credentials/signatures,
release tag, commit, push, upload, publication or remote mutation occurred.

Final status/stat/HEAD evidence is retained in
[v0_2_0/FINAL_GIT_STATE.txt](v0_2_0/FINAL_GIT_STATE.txt). New report/evidence/notes and
helper tests accompany the known metadata/helper/smoke/workflow diff.

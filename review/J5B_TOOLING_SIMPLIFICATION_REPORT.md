# J5B — Final tooling simplification

Status: **host qualification PASS; human Nix PASS (owner reported); activation DISABLED**.
All implementation changes remain uncommitted. No live release operation ran.
Evidence is outside source membership at `/tmp/totipo-j5b-evidence/`.

## 1. Baseline, authority and classifications

Started on clean `main`, HEAD `bfa539e070bb6f89acf0960dd33d3d9b434cbbe1`.
Initial `git status --short` was empty. J5A was committed; no report-only baseline
exception was needed. J5A and historical J4C/J4B/J4S reports were consulted along
with AGENTS.md and actual source/callers. Historical reports are unchanged.
Work stayed in this repository, with disposable generated fixtures, no sibling
repository, worktree or sub-agent. Nothing was committed, pushed or tagged.

Classifications: RELEASE_TOOLING; DEPENDENCY_BUILD (Nix test/fileset wiring and
consumer task removal); PUBLICATION_METADATA (acceptance/signing integration);
CONSUMER_BOUNDARY; DOCS_JAVADOC (process prose). No production library/API,
protocol/corpus, version or release-source preparation change.

| Input | Current value |
| --- | --- |
| VERSION | `0.2.0` plus LF |
| Specification | v1/r19, `cdb4e91be1c6d3704874b2b92457ffe7be5e9084` |
| Profile hash | `7242fc557a7ca56452c934bedc5e7dc2835248f5cd36fa220f05588dc967ec80` |
| Snapshot / portable corpus | 99 files / 92 cases, unchanged |
| JDK / production bytecode | OpenJDK 25.0.4.1+1 / Java 17 (61.0) |
| Gradle / Vanniktech | 9.8.0 / 0.37.0 |
| Jackson / BC / JUnit | 2.21.7 / 1.86 / 6.1.3 |
| Publishing transport | Existing OkHttp 5.1.0 |
| Nix installer | 2.35.2, SHA-256 `9adda97297d9e8ab360df95c729eabff4f4f93d6db091953c3a68f29e3fb130c` |
| Root nixpkgs input | `nixpkgs_3`, revision `39ad350a0602fa0a58a544344e3e9187526ea45c` |
| checkout action | `3d3c42e5aac5ba805825da76410c181273ba90b1` |
| install-nix action | `13d8dd58da0234aa297dedd986986ccb8e7f3e24` |

No Nix executable was invoked to inspect versions. Installed JDK/store tools and
committed declarations supplied these observations. Both activation guards were
false initially and remain exactly unchanged (section 8).

## 2. Owner decisions and resulting architecture

Applied Option A, with the owner's provider-enforced external-action pin policy,
nonzero XML critical-suite accounting, signing-before-reservation, and continued
Java ownership of substantive validation. No Option B filesystem, serialization,
finalizer, listener or shell/API rewrite was introduced. LOC is descriptive.

Before: wrapper hash checks plus unreachable declaration parser; local action
registry/conventions; repeated semantic publication acceptance in artifact commands;
repeated corpus hashing/focused XML; repeated canonical Git fetches/config allowlist;
settings repository self-check; tag reservation before signing.

After: exact wrapper identities; GitHub-enforced action pinning; semantic acceptance
once per produced staging, fresh manifest/hash checks at use; full-suite accounting
and real integrity/conformance tests; one canonical acquisition per fresh job;
actual consumer resolution; signing and acceptance before first-time reservation.
Actions orders jobs and propagates failures. Git supplies source identity/ancestry.
Gradle builds/signs/publishes. Nix fixes tools/dependencies. Java still validates
source, packages, signing inputs, remote bytes, create-only tags and exact releases.
Repository tests are regressions, not independent authorization of mutable source.

## 3. Complete component disposition

Names below are relative to `tooling/src/main/java/org/totipo/tooling/`, except
wrapper. This accounts for all 24 original standalone production Java classes.

| Disposition | Components and reason |
| --- | --- |
| KEEP | Main, StrictJson: CLI routing and bounded JSON |
| KEEP | publication/PublicationVerifier, PublicationManifest, PublicationRepository, PublicationCli, FilesSupport, Json: semantic acceptance, schema 1, freshness/copy, bounds and existing filesystem/serialization contracts |
| KEEP | release/AcceptedArtifacts, EvidenceFiles: all ten identities, actual unsigned signing inputs, bounded detached evidence |
| KEEP | release/ReleaseInputs, RemoteReleaseState: bounded input identity, absence veto and API records |
| KEEP | release/RemoteArtifactVerifier, FinalizationProcesses: exact Central bytes, bounded HTTP/processes, narrow credential-safe GitHub adapter |
| SIMPLIFY | wrapper/WrapperVerifier: whole-file identity only |
| SIMPLIFY | release/ReleaseInspectionCli: accepted-manifest reuse, single accounting command; preserve exact VERSION and require job-local acceptance path |
| SIMPLIFY | release/SourceIdentity, SourceFiles: retain exact VERSION/spec pin and selected completed version notes; remove prose/lock-layout/corpus checks |
| SIMPLIFY | release/GitQueries: retain sanitized HEAD/clean/ancestry; remove duplicate fetch and closed config allowlist |
| SIMPLIFY | release/HttpQueries: remove unused GitHub GET endpoint branch/helper; canonical Central behavior unchanged |
| SIMPLIFY | release/TestResults: full accounting only; nonzero critical-suite execution |
| SIMPLIFY | release/ReleaseFinalizer: adapt notes API only; create-or-validate behavior preserved |
| REMOVE | release/WorkflowPins: provider owns external pin policy |
| REMOVE | release/AcceptedInventory: redundant semantic-verification wrapper |

Whole production-class deletions: **WorkflowPins.java, AcceptedInventory.java**.
Whole test deletion: **WorkflowPinsTest.java**. No production library class changed.

Permanent test inventory is now nine general standalone runners: StrictJsonTest,
PublicationTest, ReleaseTest, AuthorizationTest, RemoteVerificationTest,
FinalizationTest, FinalizationProcessTest, TestResultsTest, WorkflowBoundaryTest.
FakeFinalizationCli remains their bounded process fixture. WrapperVerifierTest is
separate and JDK-only. All existing independent publication/remote/process tests
remain. ReleaseTest adds real disposable Git identity/ancestry negatives.

Removed wrapper parser/reflection/compatibility fixtures, workflow registry and
setup-count/fileset/template conventions, focused accounting fixtures and exact
critical row-count expectations. Flexible nonzero counts are positively tested.
WorkflowBoundaryTest keeps mutation guards, permissions/secret isolation, one-shot
order/freeze wiring, actual precheckout input rejection, actual bootstrap shell
failure propagation and the revised signing-step failure paths. It does not
implement a general workflow simulator or parser.

## 4. Wrapper and action identity

The JDK-only verifier hashes the complete committed wrapper JAR and properties,
rejecting modified/missing/unreadable inputs. CLI success/failure/usage statuses
remain 0/1/2. Representative wrong-file, missing-file, directory/read error and
invalid-argument probes pass. Native distribution checksum and Nix Gradle inputs
are unchanged. This authenticates wrapper files, **not arbitrary cached Gradle
distribution bytes**. No downloaded executable or replacement action was added.

Unchanged SHA-256 contracts:

- JAR: `238e777fcddd7e34f9708186085def2abd6e08e658505b38718d79d74c21abd5`.
- Properties: `e81b90975868be2513ca00d6a5a01303a406a5cb01a306210b887e983e4b17ae`.

WorkflowPins, its CLI route, workflow calls and Nix runner are gone. Existing full
external action SHAs and repository-local setup references are unchanged. The
owner reports GitHub's native full-commit-SHA requirement configured; no settings
were queried or modified. This is a provider assumption, not a local test PASS.
The separate external installer URL/hash and fail-closed acquisition remain.

## 5. Semantic acceptance and artifact freshness

Caller audit found AcceptedInventory.verify used only by ReleaseInspectionCli's
inventory/compare/signed/remote branches. Its compare helper had no production
caller. Each actual workflow path already runs `clean verifyPublication` or the
full gate before those operations: preflight normal/offline builds, independent
publisher reconstruction and independent verification reconstruction. Gradle's
verifyPublication executes PublicationCli.verify on newly produced staging;
manifest/consumer evidence is invalidated before acceptance and written only on
success. Nix uses that same task and checks before copying. No producer boundary
lost its initial independent semantic verifier.

Downstream operations use this job's `build/publication-manifest.json`, exact
source VERSION, schema-1 bounds/duplicates/ten canonical identities and current
artifact hashes plus the unchanged whole-staging fingerprint. Cross-job inventory
is an expectation; signing compares it against independently accepted current
staging. Remote verification reconstructs its own staging without caller inventory.
An arbitrary alternate acceptance-file path is rejected by the release CLI.
The trusted job-local manifest records prior acceptance; parsing it alone does
not prove acceptance or current freshness. Reviewed workflow order and protected
source establish its provenance, not an invented token/schema.

No semantic verification remains in downstream inventory/compare/signed/remote.
Missing acceptance evidence, modified/missing artifacts, index/sidecar changes,
wrong version, supplied alternate manifest, missing/extra members and substituted
signing inputs remain rejected. Independent POM/module/archive/source/Javadoc
semantics, Java 17 checks and all ten roles/coordinates remain unchanged.

## 6. Accounting, source readiness and Git ownership

Full tests still execute all 92 exact expected/executed portable cases and the
snapshot/profile tests, including alteration negatives and required pinned content.
TestResults retains bounded hardened XML parsing, module XML presence, critical
suite discovery, nonzero successful snapshot/profile tests, the conformance
execution assertion, duplicate/attribute consistency and zero failures/errors/skips.
No fixed total or 3/1 method-count ratchet is required. Full tests observed today
still contain 3 snapshot and 1 profile tests; those are observations, not policy.

Removed focused-test-results, mandatory focused integrity rerun and repeated shell
snapshot checks from Nix/release qualification. Preserve/account full XML before
intentional diagnostic focused tests or clean; no focused run is a release gate.
AGENTS.md, README, RELEASE_CHECKLIST and API_DESIGN now describe the resulting
single accounting boundary, accepted-manifest use and signing order. Final human
Nix and included-input freezing remain mandatory. Release reproducibility remains
separate from routine qualification.

Source validation retains exact VERSION/LF, reviewed source SHA/HEAD, cleanliness,
job-acquired main ancestry, exact specification pin and selected version notes.
Notes must identify the requested version in the title, have a nonempty body, and reject TODO/TBD/FIXME placeholders. No required Markdown section names,
README/API status text or exact consumer-lock configuration spelling remain.
Actual locked consumer resolution still checks self-coordinates/version/graph.
Full corpus integrity belongs to the actual qualified tests, not every release
identity/finalization call.

Every fresh workflow job still fetches canonical HTTPS main and proves ancestry
before executing requested source tooling. Java performs no second network fetch;
it rechecks HEAD/clean and ancestry against that job's acquired dedicated ref.
Missing ref, wrong HEAD, dirty checkout, stale main not reaching source and unrelated
history are permanently tested using real Git. Harmless local `color.ui` is allowed.
Git's sanitized environment, no replacements/hooks/helpers and bounded execution
remain. These Java operations assume the documented bootstrap occurred in this
fresh job; a cached ref from another job is not an acquisition substitute.

## 7. Small build/Nix/HTTP cleanup

Removed consumer verifyRepositorySelection and its check dependency. Actual exclusive
staging/Central-only repository declarations, both metadata modes, exact resolved
graph, compile/runtime smoke, strict verification and locks are unchanged.

Nix selects the exact canonical Jackson cache coordinate/JAR hash directly; version
and hash are unchanged. No downloader, pin file or dependency update was added.
Removed Forbidden.java synthetic compilation/diagnostic matching. Actual independent
javac still uses explicit sourcepaths and only pinned Jackson externally; production
and Gradle classes are absent. Test compilation adds only compiled tooling classes.
The wrapper remains JDK-only. The tooling fixture union drops corpus and self-fileset
inputs now unused; seven Git-known fixture files suffice and pass source-dependent
runners. Existing locked gitMinimal, already in release-runtime, is supplied to
ReleaseTest for its real Git regressions; no acquisition pin/cache changed.

Unused HttpQueries GitHub GET policy branches are removed. The actual GitHub adapter
remains in FinalizationProcesses. Central no-redirect, bounded complete responses,
statuses, streaming hashes, monotonic budgets and fail-closed errors are untouched.

## 8. Signing-before-reservation and operational isolation

The disabled publisher now performs:

1. Credential-free exact-source bootstrap and independent semantic reconstruction.
2. Preflight ten-hash comparison, eligibility and existing-tag absence checks.
3. Required secret presence checks, then only the two existing Gradle Sign tasks.
4. Java source/preflight/signing-input and detached-output evidence acceptance.
5. Set centralUpload freeze; final source/eligibility check with accepted outputs
   unchanged; first-time create-only annotated tag reservation and exact readback.
6. Fresh source/tag authentication and accepted artifact/signature evidence check.
7. One Gradle `publishAndReleaseToMavenCentral` invocation, using frozen outputs.
8. Independent Central verification, both remote consumers and separate finalization.

No Gradle task graph implementation change was needed: existing onlyIf guards freeze
JavaCompile/Javadoc/archive/POM/module/Sign producers. Existing shared
verifyCentralSigningInputs depends on both frozen Sign tasks and runs once before
either local Central staging publisher. The plugin and automatic-publication task
are unchanged. Freeze is execution configuration, not a portable upload authorization.
Actions' successful first-time reserve in this step supplies that prerequisite.

Actual publishing shell fixtures show missing credentials, signing failure and
signing-input rejection stop before reserve; reservation/tag authentication failure
stops before upload; success reaches upload exactly once after signed acceptance,
reserve, tag authentication and final signed check. Existing Java tests prove atomic
create-only races, existing-tag veto even with Central 404, ambiguous creation veto,
rerun rejection, exact tag readback and conflicting/ambiguous finalization behavior.
No matching existing tag authorizes upload; uncertainty never authorizes recovery.

Disposable real signing qualification uses an exported Git-known source tree and
a new RSA fixture key generated with the Gradle distribution's existing BC libraries.
Private key bytes stay in a pipe/process environment and memory, never a key file,
source, artifact or log. Single-use Gradle processes terminate and the temporary
process environment drops the key; no disk key material needs erasure. This is
not production signer/trust qualification or a claim of managed-memory overwriting.
The independent tooling compiler still has only Jackson, not these signing libraries.

Actual successful signing and Java acceptance proceed to the existing disposable
fake GitHub API's first-time reservation. Missing/invalid signing keys and a changed
actual POM signing input reject. A fixture-only init script skips all plugin Central
network-registration task classes (Prepare/Enable); it exercises the selected real
publication graph with `--rerun-tasks`, including actual local staging publishers,
without live upload credentials or network registration. All producer/Sign tasks
are SKIPPED; the shared check and two local publishers execute. All 378 captured
producer/signature files retain exact hashes. Removing a detached signature makes
the shared check fail before either local publisher. The init script is external
test evidence, never production code or source membership.

Local/remote signature evidence retains its existing bounded marker/unsigned-input
identity contract, not independent cryptographic verification or signer trust.
Missing, malformed, duplicate or substituted unsigned signing evidence rejects.
No new signing algorithm/trust infrastructure or signature evidence schema exists.

Verification declares no Maven/signing secrets, release environment, inheritance,
reservation or upload path. It reconstructs independently and can be explicitly
rerun only to verify/finalize. Internal plugin transport retries remain accepted
within one authorized invocation; no exactly-once HTTP claim or upload recovery.

Both activation guards remain **exactly**:

```yaml
# release.yml / publish-central
if: ${{ false && github.run_attempt == 1 }}
# verify-release.yml / finalize
if: ${{ false }}
```

No live tagging, signing with production credentials, Central upload, GitHub Release
creation, dispatch or settings mutation ran. No successful-path manual step was added.

## 9. Qualification evidence and invocation accounting

Baseline `./gradlew test`: PASS, 28s, 13 actionable tasks (2 executed, 11 up-to-date).
Both module tests were up-to-date; preserved XML accounts 405, not fresh execution.
Original independently compiled WorkflowBoundaryTest also passed before edits.

Final normal command:

```sh
TOTIPO_PUBLICATION_TOOL=/tmp/totipo-j5b-evidence/tool ./gradlew --no-daemon --no-build-cache --no-configuration-cache clean test build javadoc verifyPublication consumerSmoke
```

PASS, 1m, 30 outer tasks executed; nested module consumer 10s, 4 tasks executed.
The removed self-check explains four rather than five consumer tasks. Full accounting
immediately afterward accepts 405 tests, zero failures/errors/skips, 92-case exact-set
execution and nonzero snapshot/profile suites. XML and manifest are preserved in
`normal/`. Java 17 bytecode and independent semantic verification pass.

The separate host offline pass is justified by changed consumer build behavior and
publication acceptance/signing integration. It also supplies requested release
normal/offline reproducibility, not another human hermetic gate:

```sh
TOTIPO_PUBLICATION_TOOL=/tmp/totipo-j5b-evidence/tool ./gradlew --offline --no-daemon --no-build-cache --no-configuration-cache --rerun-tasks clean test build javadoc verifyPublication consumerSmoke
./gradlew -p publishing/consumer-smoke --offline -PpomOnly clean check
```

Offline full PASS, 1m4s, 30 outer tasks executed; nested module consumer 5s, 4
executed. Immediate accounting again accepts 405, zero failures/errors/skips and
92 cases; `offline/` preserves XML/manifest. POM-only PASS, 5s, 4 tasks executed.
Normal/offline/disposable reconstruction hashes agree on **10/10/10** artifacts;
Python independently reread current bytes as an additional evidence check, while
Java release compare and publication check also pass. No Python is repository
release tooling or an acceptance authority. Exact hashes follow in section 10.

Standalone production/test compilation uses direct JDK25 `--release 25 -encoding
UTF-8 -Xlint:all -Werror -proc:none`, explicit sourcepaths and authenticated Jackson
JAR SHA-256 `8c5623b98f32d5f7e1287ff61ed50e08ab7b11381e991fff89327faabba42261`.
Final runners pass: StrictJson 43 assertions, Publication 137, Release 46,
Authorization 144, XML 29/26 fixtures; remote, finalization/process, workflow and
JDK-only wrapper runners PASS. Final source-dependent runners also pass on the
exact seven-file Nix fixture union. No new TestListener/schema/framework was added.

| Invocation category | Count / outcome |
| --- | --- |
| Broad baseline | 1 PASS, cached module test results explicitly accounted |
| Original standalone compilation | 1 main/test pair; original workflow boundary PASS |
| Implementation standalone compilation | 4 main/test pairs PASS; final pair authoritative |
| Focused implementation runners | Workflow, Release, XML: 3 PASS; wrapper: 1 PASS |
| Full remaining standalone sets | 4 sets / 9 runners each PASS; last set authoritative |
| Final narrow-fixture source runners | 6 PASS (initial and final sets) (Release, Finalization, WorkflowBoundary) |
| Wrapper javac | 1 JDK-only invocation compiling main/test; empty external classpath |
| Wrapper test / direct verifier / Main smoke | 5 / 4 / 4 PASS |
| Focused Gradle library tests | 0 |
| Final normal full gate / separate host offline | 1 / 1 PASS |
| Full-suite XML accounting | 3 PASS (baseline, normal, offline) |
| Full-gate semantic acceptance / module consumer | 2 / 2 PASS; nested tasks, not extra full suites |
| Independent POM-only consumer | 1 PASS |
| Normal/offline ten-artifact pair | 1 PASS |
| Disposable reconstruction | 2 PASS; renewed after release-helper changes; final export authoritative |
| Real disposable signing | 2 PASS; final renewed fixture authoritative |
| Missing key / invalid key Gradle negatives | 2 / 1 expected failures |
| Network-disabled frozen publication graphs | 2 PASS; 2 missing-signature expected failures |
| Actual signing-input substitution | 1 expected Java rejection in final real-signing fixture |
| Real signing → disposable API reservation | 2 PASS, including final source-readiness reacceptance; no live GitHub mutation |
| Launcher unknown / extra argument / removed focused command | 3 expected status rejections (2 / 2 / 1) |
| Agent Nix / human J5B Nix / cache updater | 0 / 1 owner-reported PASS / 0 |
| Live upload/tag/release/dispatch/settings/commit/push | 0 each |

Iteration release-helper refinements (notes flexibility/title identity and nonempty body, exact artifact-command
VERSION/manifest boundary, real Git negatives) were followed by renewed standalone
compilation/runners and disposable signing. The last title/body refinement was
followed by the final full standalone set, three narrow-fixture runners and
reacceptance of real disposable signed outputs before fake-API reservation. They did not change library/artifact
producer inputs, so no extra full library gate was needed. The first standalone
sets/signing fixture are superseded by the final versions; no unexpected test/gate
failure was hidden. Expected negative fixtures are rejections, not failing gates.
The original focused integrity gate, exact method counts, workflow-pins command
and forbidden-import probe are superseded by approved policy, not unexecuted
mandatory checks. Live Central consumers/API/hosted configuration remain untested.

## 10. Artifact evidence

Ten canonical normal/offline/final-export hashes, independently reread:

```text
org/totipo/totipo-core/0.2.0/totipo-core-0.2.0.jar	1b628743548c376c2ae85ece2c2f9be43fc7364e13fc0b4d4d25e3d49ee6d864
org/totipo/totipo-core/0.2.0/totipo-core-0.2.0-sources.jar	eee068cd5a700f66cab212ccaa728c7d1f04c981d31648c1e4e90e826d1e8b93
org/totipo/totipo-core/0.2.0/totipo-core-0.2.0-javadoc.jar	aa0960585fc5bd0c4c5615b754f7d259d9499776e133ca1f8298250af8dcf8df
org/totipo/totipo-core/0.2.0/totipo-core-0.2.0.pom	1a6bbd5b4d82c079cb621d66c89b446c09e2df33efe5e0e89dd5abfd35d7c136
org/totipo/totipo-core/0.2.0/totipo-core-0.2.0.module	28fcefec7f89ca69b651beb86afb25d07af5933da2f29e8fd5bdb5776b9d227c
org/totipo/totipo-storage-nio/0.2.0/totipo-storage-nio-0.2.0.jar	776068249e689e94136748fb8ffd86c837e0af8bbb6cec8ae5e785c4eca4ba93
org/totipo/totipo-storage-nio/0.2.0/totipo-storage-nio-0.2.0-sources.jar	bb690c98837f938b1cce75cdfc06b44b47b066c4cb937a4ec461f2dc82f5b04b
org/totipo/totipo-storage-nio/0.2.0/totipo-storage-nio-0.2.0-javadoc.jar	ef1b8cf0a278eb64e0cb9073e627debeba298f0fd400378fadfdbf8cd9c7ac7c
org/totipo/totipo-storage-nio/0.2.0/totipo-storage-nio-0.2.0.pom	4acf1725ac7bb5cd7a4299f729d5eceb82aa3ea524dca7c9d5a9b56616db87b2
org/totipo/totipo-storage-nio/0.2.0/totipo-storage-nio-0.2.0.module	33a431575523bb545b57876b09fd04255e04b60b2c64541d72b2cda5f05c7afa
```

Every staging validates its own fingerprint; ephemeral indexes are not compared
across builds. Full-staging fingerprint removal remains deferred. Separate fresh
publisher/verification reconstruction remains in production workflow source.

## 11. Actual maintenance reduction and deliberately relaxed conventions

Physical LOC includes blanks/comments, measured over Git-known source against HEAD:

| Scope | Before files / LOC | After files / LOC | Net LOC |
| --- | ---: | ---: | ---: |
| Standalone production Java, including wrapper | 24 / 2182 | 22 / 1965 | -217 |
| Standalone test/helper Java, including wrapper | 12 / 1601 | 11 / 1326 | -275 |
| Gradle/Nix/workflow/action configuration | 19 / 1214 | 19 / 1148 | -66 |

Total standalone Java reduction: **492 lines**; configuration: **66 lines**.
New signing-order and real Git regressions intentionally offset some test deletions.
Library production/tests/fixtures, consumer Java and corpus are unchanged.
Report prose is excluded from implementation LOC, and no J5A estimate is a threshold.

Deliberately relaxed: historical wrapper text diagnostics; duplicated action/version
registry, exact workflow layout/setup counts/self-fileset assertions; exact integrity
method counts/focused mode; rigid notes sections/status prose/lock-line spelling;
closed local Git config keys; duplicate corpus/semantic reexecution. Required real
boundaries, freshness, conformance, consumers and distribution integrity remain.

## 12. Freeze, dependency accounting and final Git evidence

No dependency version, publishing plugin, Gradle lock, verification metadata,
package-deps.json, flake.lock, VERSION, spec pin or library/corpus bytes changed.
No cache updater or dependency review checkpoint is needed. Existing pinned Git
is reused for standalone test fixtures; fixed dependency acquisition is unchanged.
Unchanged protected input SHA-256:

- flake.lock: `44728fcfd8529462cb415b186ee4ce3400343dd911d325b6a965bd386b9f7749`.
- package-deps.json: `ee87ee929d064232e30b0bb91c8aaa4a249ae5f0fad7d85180a9833709f6a879`.
- Gradle verification: `383e4bc19e0b17b635408740f9ebf1681842ae8203a102baab4c831d9ec1b16a`.

Inspected qualification.nix's unchanged source filter, flake inputs, tooling/wrapper
filesets. AGENTS/README/RELEASE_CHECKLIST/API_DESIGN and review are excluded from
library qualification; tooling Java/workflow fixtures and Nix definitions still
determine checks. Removed paths are absent; this report is the sole new path and
is intent-to-add only. No build/cache/evidence/signing output/secret is Git-known.
Source membership, file modes and hashes are frozen externally in
`frozen-inputs.json`; all included executable inputs are frozen before handoff.
Any later included-input edit invalidates that boundary and a human result.
Only confirmed-excluded report prose may be finalized afterward.

Human J5B Nix status: **PASS, owner reported on 2026-10-13**. The owner confirmed
the final routine command passed:

```sh
nix flake check
```

No second Nix build is requested. Prior milestone Nix results are not J5B evidence.
Final branch/HEAD remain the baseline. `git diff --check` and
`git diff --cached --quiet` pass; staged content and cached path inventory are empty.
Full final Git status, diff inventory and frozen-input digest are appended below.

## 13. Deferred findings and activation prerequisites

Retained SecureDirectoryStream/atomic directory handling, copier, JSON serialization,
whole-staging fingerprint, standalone Java finalizer and Nix finalization environment.
No Nix-store cache, new API abstraction, crypto trust infrastructure, dependency
upgrade, protocol change or release state machine. No new concrete blocker was found
in these deferred areas. The existing wrapper cache/signature-marker/live-provider
limits described above remain; this milestone does not claim stronger guarantees.

Remaining activation prerequisites: ordinary hosted CI for final committed source,
separate owner activation authorization, confirmation of effective native action
pin/main/tag/environment protections and hosted signer/secret configuration. Real
protected-token creation/immutability, production signing identity, Central delivery
and hosted finalization are not demonstrated by disposable local fixtures. Neither
activation guard is enabled. Stop after J5B; no release rehearsal/activation began.

Frozen non-review Git-known inputs: **321** regular files, modes and SHA-256.
Inventory SHA-256: `0efb7bb5b176b4855edce95d13053feb3284f203974399446774570111a80e56`.

`git status --short --untracked-files=all`:

```text
 M .github/workflows/ci.yml
 M .github/workflows/release.yml
 M .github/workflows/verify-release.yml
 M AGENTS.md
 M API_DESIGN.md
 M README.md
 M RELEASE_CHECKLIST.md
 M nix/tooling.nix
 M publishing/consumer-smoke/build.gradle.kts
 M publishing/consumer-smoke/settings.gradle.kts
 M qualification.nix
 A review/J5B_TOOLING_SIMPLIFICATION_REPORT.md
 D tooling/src/main/java/org/totipo/tooling/release/AcceptedInventory.java
 M tooling/src/main/java/org/totipo/tooling/release/GitQueries.java
 M tooling/src/main/java/org/totipo/tooling/release/HttpQueries.java
 M tooling/src/main/java/org/totipo/tooling/release/ReleaseFinalizer.java
 M tooling/src/main/java/org/totipo/tooling/release/ReleaseInspectionCli.java
 M tooling/src/main/java/org/totipo/tooling/release/SourceFiles.java
 M tooling/src/main/java/org/totipo/tooling/release/SourceIdentity.java
 M tooling/src/main/java/org/totipo/tooling/release/TestResults.java
 D tooling/src/main/java/org/totipo/tooling/release/WorkflowPins.java
 M tooling/src/test/java/org/totipo/tooling/release/AuthorizationTest.java
 M tooling/src/test/java/org/totipo/tooling/release/ReleaseTest.java
 M tooling/src/test/java/org/totipo/tooling/release/TestResultsTest.java
 M tooling/src/test/java/org/totipo/tooling/release/WorkflowBoundaryTest.java
 D tooling/src/test/java/org/totipo/tooling/release/WorkflowPinsTest.java
 M tooling/wrapper/src/main/java/org/totipo/tooling/wrapper/WrapperVerifier.java
 M tooling/wrapper/src/test/java/org/totipo/tooling/wrapper/WrapperVerifierTest.java
```

`git diff --name-only`:

```text
.github/workflows/ci.yml
.github/workflows/release.yml
.github/workflows/verify-release.yml
AGENTS.md
API_DESIGN.md
README.md
RELEASE_CHECKLIST.md
nix/tooling.nix
publishing/consumer-smoke/build.gradle.kts
publishing/consumer-smoke/settings.gradle.kts
qualification.nix
review/J5B_TOOLING_SIMPLIFICATION_REPORT.md
tooling/src/main/java/org/totipo/tooling/release/AcceptedInventory.java
tooling/src/main/java/org/totipo/tooling/release/GitQueries.java
tooling/src/main/java/org/totipo/tooling/release/HttpQueries.java
tooling/src/main/java/org/totipo/tooling/release/ReleaseFinalizer.java
tooling/src/main/java/org/totipo/tooling/release/ReleaseInspectionCli.java
tooling/src/main/java/org/totipo/tooling/release/SourceFiles.java
tooling/src/main/java/org/totipo/tooling/release/SourceIdentity.java
tooling/src/main/java/org/totipo/tooling/release/TestResults.java
tooling/src/main/java/org/totipo/tooling/release/WorkflowPins.java
tooling/src/test/java/org/totipo/tooling/release/AuthorizationTest.java
tooling/src/test/java/org/totipo/tooling/release/ReleaseTest.java
tooling/src/test/java/org/totipo/tooling/release/TestResultsTest.java
tooling/src/test/java/org/totipo/tooling/release/WorkflowBoundaryTest.java
tooling/src/test/java/org/totipo/tooling/release/WorkflowPinsTest.java
tooling/wrapper/src/main/java/org/totipo/tooling/wrapper/WrapperVerifier.java
tooling/wrapper/src/test/java/org/totipo/tooling/wrapper/WrapperVerifierTest.java
```

`git diff --cached --name-only`: empty. No staged file contents.

## 14. Completed human qualification

The owner reported `nix flake check passed` on 2026-10-13. This records one
owner-run J5B PASS; no full log or derivation identifier was supplied. Agent Nix
invocations remain zero. After that report, all 321 frozen non-review input hashes,
modes and exact Git-known membership matched the handoff inventory. Branch/HEAD,
full Git status and diff-path inventory also matched; no staged content exists.
Both disabled mutation guards remain unchanged.

Only this confirmed-excluded review report was updated to record the result.
No Java rebuild, renewed Nix gate or second build is required. Host qualification
and the human gate are complete; all changes remain uncommitted for owner review.
J5B stops here. Release activation and live release operations remain unauthorized.

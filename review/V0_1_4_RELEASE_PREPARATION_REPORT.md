# Totipo Java 0.1.4 release preparation

## 1. Starting state

Preparation date: 2026-10-07. Branch: `main`. Exact starting HEAD and implementation
commit being released: `11f2480d4e165d301aaaa89247af8c37a5ecf11e`
(`add openPrivate in case we know that the location is truly app exclusive`).
Before any edit, `git branch --show-current`, `git rev-parse HEAD` and
`git status --short` confirmed that branch/SHA and an empty, clean worktree.
Committed HEAD contains both private factories, the installer, private NIO/facade
tests and the portability report; this report does not reuse the investigation's
previous uncommitted-state claims. Starting VERSION was `0.1.3`.

Inspected VERSION, README, SPEC_PIN, API_DESIGN, SPI_DESIGN, root/module/consumer
Gradle files, publication metadata/verifier, dependency locks/catalog/verification
metadata, both workflows, Python release guardrails, flake.nix, prior 0.1.1/0.1.3
preparation/notes and release-workflow reports, and the committed portability report.
There is no separate changelog: version-specific reviewed notes under `review/`
are the established release format. Public GitHub read-only evidence confirms
v0.1.1 at `dfd76a9a9cff261fdb214ac85457bddb06f1d777` (2026-10-03) and v0.1.3 at
`e2326aca5f5aac661d74925a30fcc57cd91014e8` (2026-10-06); no v0.1.2 tag or GitHub
release exists in those listings. The historical 0.1.3 notes explain the 0.1.2
final-job failure. Local tags initially contained only v0.1.0; no tags were fetched
or created. Historical release bodies were inspected without changing remote state.

## 2. Release identity

VERSION is the single source of truth and now contains `0.1.4`.
Coordinates: `org.totipo:totipo-core:0.1.4` and
`org.totipo:totipo-storage-nio:0.1.4`. No group, artifact or module rename.
Protocol remains Totipo Vault Format **v1/r18**, exact spec commit
`4623a7e1718e23504903096c92332597057bd8f0`.

**No spec change.** SPEC_PIN, all snapshot/corpus bytes, requirements profile,
schemas, manifest, checksums and expected outcomes are byte-identical to starting
HEAD. Normative spec SHA-256:
`8a357e75f3ddd92efa954fde2ffc9af33f40a2c2396d6afbf1d5de5f00bc4f8a`;
profile SHA-256:
`4c7954cd2b59aa0afbbe3c22080cadddf72135d884b186a671266c29d58418df`.
No upstream HEAD comparison or repin is needed.

Reference classification from `git grep -n '0\.1\.3'` before and after editing:

| Occurrence | Classification/action |
| --- | --- |
| VERSION; RELEASE_CHECKLIST version, coordinates, tag/notes path and dispatch example; release.yml input example | Current release metadata: advance to 0.1.4. |
| consumer-smoke/gradle.lockfile, two Totipo entries | Current self-resolution versions: advance to 0.1.4 only. |
| README pending-0.1.3 paragraph | Stale release status: replace with locally prepared, unreleased 0.1.4. |
| README consumption examples, formerly 0.1.1 | Dependency documentation: use published 0.1.3, explicitly separate from unreleased 0.1.4. These retained 0.1.3 references are intentional. |
| V0_1_3_RELEASE_NOTES, V0_1_3_RELEASE_PREPARATION_REPORT, NIO_PRIVATE_LOCAL_PORTABILITY_REPORT | Historical/release provenance: preserve every occurrence. |
| publishing/test_release.py, notes-selection/missing-notes/mock-tag tests | Inspected test fixtures: preserve 0.1.3. They test version selection and historical mocked actions, not current release metadata. |
| This report and the new release notes | Intentional baseline/provenance references. |

No blind global replacement. README/API/SPI/Javadocs now explicitly include all
writers and bridge operations in root-wide serialization; shared behavior and
no-fallback wording remain clear. The overload inherits the primary factory's
complete deployment contract. No behavioral production code change during preparation.

## 3. Included change

Only the committed private-local NIO enhancement: explicit
`NioTotipoStore.openPrivate(Path)` and
`NioTotipoStore.openPrivate(Path, StorageDurability)` for exclusively
application-controlled local replicas. Complete forced staging precedes ordinary
move installation without replacement options for immutable objects and initial
VAULT creation; canonical file/directory acknowledgement and conservative
uncertainty remain explicit. The application excludes unrelated ordinary writers
and direct synchronization software and serializes every writer, handle/session
and bridge operation for the root. Opening does not establish or enforce exclusivity.

Existing open factories, NioTotipo convenience entry points and legacy adapters
retain shared/default hard-link publication without automatic fallback. Directly
synchronized/shared roots should continue using shared mode unless separately
qualified. VAULT replacement retains its existing behavior.

## 4. Excluded/deferred work

- No Android qualification claim. Supplied M1C evidence rejected 0.1.3 shared/default
  mode on one API-37 app-private filesystem because hard-link installation was denied.
  New private mode has JVM/fault evidence but no physical Android retest. Android
  0.1.4 requalification is separate; no Android crash safety, API-37 runtime or
  physical power-loss claim follows.
- No exact-existing acknowledgement change: r18's read-only exact-existing allowance
  and the stronger current Java SPI/NIO acknowledgement contract remain distinct.
- No protocol/spec/wire/crypto/vector change or expanded conformance claim.
- No provider/SAF bridge implementation, external dependency upgrade or unrelated fix.

## 5. API compatibility

No formal binary-compatibility Gradle tool/task is configured. Actual released
0.1.3 core/NIO JARs were downloaded from canonical Maven Central into temporary
local evidence storage; no repository files or remote state were changed.
`javap -protected -s` inventories of public types compare declarations, headers
and JVM descriptors: core **102 -> 102** public types, unchanged; NIO **7 -> 7**,
with exactly the two added public static factories. No removed public type/member
or changed existing descriptor/header. Core class entries and bytes are all
identical to released 0.1.3, including sealed hierarchies; NIO has no sealed public
hierarchy change. The only new binary class is the internal NioCanonicalInstaller.
Changed NIO class bytes correspond to the committed installation engines/provider.

The original published consumer and a shared-opening smoke client were compiled
with `javac --release 17` against 0.1.3 JARs, then run with only 0.1.4 Totipo JARs
plus BC at runtime: PASS, including actual shared NioTotipoStore.open and
NioTotipo.open calls. The current consumer recompiles against 0.1.4. This supports
source/binary compatibility for the additive delta, rather than asserting a new
long-term stability promise for the experimental SPI.

## 6. Publication metadata

Repository verifyPublication checks exactly ten unsigned artifacts: binary,
source and Javadoc JARs, POM and Gradle module metadata for each of two modules.
Core's sole POM dependency remains BC `bcprov-jdk18on:1.86` at **runtime**;
NIO's sole POM dependency is core **exactly 0.1.4** at **compile**. Module metadata
has matching 0.1.4 components and core requires-version in NIO API/runtime variants;
BC appears only in core runtime. Documentation variants add no dependencies.
Production classfiles are Java 17 major 61/minor 0 and module attributes specify 17.

Structural allowlists compare binaries to production classes, sources to production
Java and Javadocs to generated documents. Staging contains only the two version-0.1.4
GAVs and expected artifact/checksum/index files. No Android code, test classes/resources,
snapshot, review report, local state, fixture capability, mixed-version Totipo JAR,
extra runtime dependency or local path leakage. NIO binary/source/Javadoc artifacts
contain the new implementation and both public factories.

Build JVM remains the installed JDK 25.0.4.1; wrapper remains Gradle 9.8.0. Wrapper
JAR SHA-256 matches CI:
`238e777fcddd7e34f9708186085def2abd6e08e658505b38718d79d74c21abd5`.
Byte comparison against HEAD passes for **108** tracked spec/corpus/dependency/toolchain
files. Wrapper, version catalog, root/module locks, verification metadata/template,
Gradle build configuration, flake.nix/lock and external versions remain unchanged.
Only the two smoke self-dependency lock versions advance. Generated consumer hash
metadata remains ignored, produced by the established publication verifier.

## 7. Tests/qualification

Uses committed release.yml credential-free commands, not identity/upload/tag commands.
Local logs, full JUnit XML copies, API inventories and baseline artifacts are in
`/tmp/totipo-0.1.4-release/`, outside publication inputs.

| Command/check | Result |
| --- | --- |
| `python3 -B -m unittest discover -s publishing -p test_release.py` | PASS: 21 tests; network/signing/Git mutations mocked. Existing HTTPError fixture ResourceWarnings only. |
| `reviewed_notes('0.1.4')` via publishing/release.py helper | PASS: version-specific file and exact `## v0.1.4` heading. |
| `./gradlew clean test build verifyPublication consumerSmoke` | PASS: initial full clean run 2m9s, 30/30 executed; final normal packaging repeat 3s, 23 executed/7 cached after Javadoc wrap cleanup. |
| `python3 -B publishing/release.py tests` after each full build | PASS: 524 tests (378 core + 146 NIO), zero failures/errors/skips; exact-set 90/90 corpus, snapshot 3/3, profile 1/1. Full XML preserved before focused filtering. |
| `./gradlew --offline --no-daemon --no-build-cache --rerun-tasks clean test build verifyPublication consumerSmoke` | PASS: 1m48s, all 30 tasks executed; same 524 tests and exact 90/90 corpus. |
| `publishing/release.py compare` helper with final normal EXPECTED_INVENTORY | PASS: 10/10 hashes identical. Initial helper invocation lacked required DISPATCH_REF; corrected with validated local identity environment, no remote action. |
| `./gradlew -p publishing/consumer-smoke --offline -PpomOnly clean check` | PASS: 5 tasks executed, compile/runtime boundaries and shared/private factory calls. |
| `./gradlew :core:test --tests org.totipo.conformance.SpecSnapshotIntegrityTest --tests org.totipo.conformance.R18ProfileIntegrityTest` | PASS: focused four tests. |
| `python3 -B publishing/release.py focused-tests` | PASS: snapshot 3/3, profile 1/1; zero failures/errors/skips. |
| `(cd core/src/test/resources/totipo-spec/v1-pre-rc && sha256sum -c SNAPSHOT.sha256)` | PASS: 97/97 records. |
| `javap -protected -s` released/current public API inventory; `javac --release 17` 0.1.3 consumer then `java` with 0.1.4 artifacts | PASS: additive factories only; existing consumers compile/load/open correctly. Temporary script and exact classpaths retained outside checkout. |
| verifyPublication + module Java17 checks + explicit generated POM/module inspection | PASS: 176 core / 27 NIO production classfiles; scopes, artifact inventories, versions and absence of local paths correct. |
| `git diff --check`; untracked notes/report trailing-whitespace checks | PASS. |


Normal final packaging was repeated after a Javadoc line-wrap cleanup; its clean
build reused seven cached tasks. The initial normal clean full build executed all
30 tasks; the final forced offline pass executes all tasks without build cache.
The comparison uses the final normal inventory for the final documentation bytes.
Javadocs retain existing missing-comment/tag warnings; no doclint/build failure.
Compiler uses existing -Xlint:all/-Werror. No new duplicate test suite is introduced;
the consumer smoke is extended only to exercise the published public API.

## 8. Reproducibility

**10/10 final normal/forced-offline artifact paths and SHA-256 hashes match.**
The repository release helper compares the verified inventories; the normal
inventory was copied before the offline clean. Same-environment evidence only,
with installed JDK 25.0.4.1 and Gradle 9.8.0. The forced build uses no build cache
and reruns all tasks. Six JARs, two POMs and two .module files are the complete
reproducibility scope; repository-index timestamps, checksums of that index and
signatures are excluded by existing convention. After the human Nix pass, all ten
resulting hashes also match this saved final normal inventory. No distribution/signing archive
is part of credential-free staging; no wider deterministic-files claim.

| Artifact | SHA-256 |
| --- | --- |
| `totipo-core-0.1.4-javadoc.jar` | `7ad2e70d741d2884181b3de2e8d20846ce64d68ff327dce7cd8bfc943a809eb3` |
| `totipo-core-0.1.4-sources.jar` | `f18bc8e9c006c9ee29da93874be791022ba60fbc151c91ceb63c32a8e0597984` |
| `totipo-core-0.1.4.jar` | `1e3db6ca15273941549dfa821b9f7dc9a00c6ad81e7e66642a1f09c114eeee19` |
| `totipo-core-0.1.4.module` | `6dc1052306ed1e25dd06c7484a0378e7e3f19c74607f5bc5b7ab3be54802c564` |
| `totipo-core-0.1.4.pom` | `498abaca7446d84fbb0da3bd92246424fe2eabe839ff5c02c1f5d7b2b7e54bc8` |
| `totipo-storage-nio-0.1.4-javadoc.jar` | `d6720ee7f82a3e2f6d4caa67338fa610b2ec5805090be58a406674f97810c991` |
| `totipo-storage-nio-0.1.4-sources.jar` | `bee2a5c9f941360b9c499f1518e3b2201e1e94405a34a8d2709033419e2ed67f` |
| `totipo-storage-nio-0.1.4.jar` | `9e559ec75fb09af068f876751f32d696c50c40988a16d28419dfa05d1d1c4dae` |
| `totipo-storage-nio-0.1.4.module` | `2d9b9e6bf66e21a7258eeeecc85550f11dc4071896e847c2d57d7319f3c2af24` |
| `totipo-storage-nio-0.1.4.pom` | `f954ae9643d0a33b3e2ee8c0024a62e4b5fc555c6c233795a986eb6c12a48b4c` |


## 9. Consumer smoke

Both repository-supported boundaries use the independent consumer build and local
unsigned staging: Gradle metadata and `-PpomOnly` (POM-only Maven-style resolution,
not a separate mvn invocation). They compile and execute existing shared
`NioTotipoStore.open(Path)` and both new private factories against published JARs,
scan the empty root, verify opening leaves it empty and load the public facade
and runtime-only BC. Compile dependencies are exactly core/NIO 0.1.4; runtime
adds only BC 1.86. No Maven-local/composite/source fallback or Android test.
The additional 0.1.3-compiled runtime smoke supports existing binary consumption.

## 10. Human Nix validation

**PASS — user-reported for this 0.1.4 preparation, followed by agent inspection.**
The user reported “all passed” for both requested commands and supplied
`git status --short`. This is new release-preparation evidence; the earlier
implementation-milestone Nix evidence remains historical.
The agent executed no Nix command and does not substitute installed-JDK validation
for this required human checkpoint. flake.nix exposes a development shell and
formatter, with no default package or check derivation. The user-reported commands
run from the repository root were:

```sh
nix flake check
nix develop --command ./gradlew \
  --offline \
  --no-daemon \
  --no-build-cache \
  --rerun-tasks \
  clean test build verifyPublication consumerSmoke
```

Both commands passed according to the user; their terminal output/exit codes were
not independently captured by the agent. After that report, the agent independently
ran `python3 -B publishing/release.py tests`: **524 tests**, zero failures/errors/skips,
**90/90** portable cases, snapshot **3/3**, profile **1/1**.
`python3 -B publishing/verify-publication.py` also passes for the resulting staging:
0.1.4 core/NIO metadata, dependency scopes, Java 17 and artifact contents.
All ten resulting SHA-256 hashes match the saved final normal inventory.

Branch/HEAD remain main / `11f2480d4e165d301aaaa89247af8c37a5ecf11e`.
Independent status exactly matches the supplied file list; whitespace checks pass
and the index is empty. Spec/corpus/dependency/toolchain bytes still match starting
HEAD. No additional tracked/untracked build state was generated. Human Nix
validation is complete; no Nix command was executed by the agent.

## 11. Remote CI

| Evidence boundary | Status |
| --- | --- |
| Implementation-commit remote CI | **PASS**, GitHub API reports run 37658504886 completed/success at exact HEAD 11f2480d4e165d301aaaa89247af8c37a5ecf11e on 2026-10-07. [Authoritative run](https://github.com/totipo-org/totipo-java/actions/runs/37658504886). |
| Release-preparation local validation | See measured non-Nix results above. |
| Human Nix validation | **PASS**, user-reported for both requested commands; resulting tests, staging hashes and Git state independently inspected. |
| Release-preparation remote CI | **Not run**: changes remain uncommitted/unpushed; no workflow dispatched. |

Read-only GitHub tags/releases/Actions GET requests were the only remote inspection.
No remote release or publication action was attempted.

## 12. Release notes draft

Version-specific reviewed draft follows prior Highlights / Compatibility and scope /
Validation format: [V0_1_4_RELEASE_NOTES.md](V0_1_4_RELEASE_NOTES.md).
Proposed GitHub/Maven summary:

> Adds a private/exclusive-local NIO storage mode intended for separately controlled
> local replicas, including filesystems where hard-link installation is unavailable.
> Private mode uses complete staged representations and ordinary moves without
> replacement options under explicit application-controlled exclusivity and
> root-wide serialization of all writers, handles and bridge operations. Shared/default
> NIO mode retains hard-link publication; VAULT replacement remains unchanged.
> Source/binary compatibility is preserved. No protocol/spec/wire/crypto/vector or
> external dependency change; no Android qualification is claimed.

The protected workflow will append the eventual reviewed release source SHA;
starting implementation HEAD is not represented as an uncommitted release-source SHA.

## 13. Release readiness

**READY ONCE IMPLEMENTATION CI IS GREEN** — that condition is satisfied by the
authoritative implementation-commit CI result recorded above. Local non-Nix
qualification and the user-reported human Nix checkpoint both pass, including
subsequent agent inspection. Release preparation is complete for local review.
Human review, final release validation and eventual release-source CI remain
operator gates; release-preparation remote CI has not run. This worktree is not released.

## 14. Final Git state

```text
$ git status --short
 M .github/workflows/release.yml
 M API_DESIGN.md
 M README.md
 M RELEASE_CHECKLIST.md
 M SPI_DESIGN.md
 M VERSION
 M publishing/consumer-smoke/gradle.lockfile
 M publishing/consumer-smoke/src/main/java/ConsumerSmoke.java
 M storage-nio/src/main/java/org/totipo/storage/nio/NioTotipoStore.java
?? review/V0_1_4_RELEASE_NOTES.md
?? review/V0_1_4_RELEASE_PREPARATION_REPORT.md

$ git diff --stat
 .github/workflows/release.yml                      |  2 +-
 API_DESIGN.md                                      |  7 +++--
 README.md                                          | 30 +++++++++++++---------
 RELEASE_CHECKLIST.md                               | 18 ++++++-------
 SPI_DESIGN.md                                      |  9 ++++---
 VERSION                                            |  2 +-
 publishing/consumer-smoke/gradle.lockfile          |  4 +--
 .../src/main/java/ConsumerSmoke.java               | 25 ++++++++++++++++--
 .../org/totipo/storage/nio/NioTotipoStore.java     |  9 ++++---
 9 files changed, 70 insertions(+), 36 deletions(-)

$ git diff --check
(empty; exit 0)

$ git diff --cached --stat
(empty; exit 0)
```


Nothing staged, committed, tagged, released, signed, remotely published/uploaded or
pushed by the agent. No remote state modified and no Nix command executed. Local
unsigned staging is validation only. HEAD remains the exact starting implementation
commit. Git diff statistics exclude the two untracked new notes/report files. The final
Git state above was rechecked after the human Nix result and report finalization.

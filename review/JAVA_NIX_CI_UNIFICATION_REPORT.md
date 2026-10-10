# Java Nix / CI unification

Status: **complete for local qualification**. Human-generated dependency cache
reviewed; human `nix flake check path:.` passed. No fresh Nix execution claim:
valid store outputs may have been reused. All content remains unstaged and
uncommitted; the human marked new paths intent-to-add. Remote CI NOT RUN.

## 1. Starting state / exact HEAD

Started on clean `main` at `d6310c177ae930df188fd4f5798622c935698b2e`.
`VERSION` is `0.2.0`; `SPEC_PIN.md` identifies Vault Format v1/r19, upstream
`cdb4e91be1c6d3704874b2b92457ffe7be5e9084`, 92 cases and 99 snapshot files.
The operator clarified that this is the released source, and current prepared-status
documentation is stale. No local tag is required. Read-only confirmation found:

- Both canonical Central `org/totipo/totipo-{core,storage-nio}/0.2.0/` POMs exist.
- GitHub `v0.2.0` annotated tag object is
  `d24e3d0ae71ea7fe318261519a9b5d08657a0e03`; peeled commit equals starting HEAD.
- GitHub release is published (2026-10-09), neither draft nor prerelease.
- Released `SPEC_PIN.md` at that commit is byte-identical to the checkout.

## 2. Baseline qualification

Before edits, ran the existing normal and forced offline suites:

```sh
./gradlew clean test build verifyPublication consumerSmoke
./gradlew --offline --no-daemon --no-build-cache --rerun-tasks clean test build verifyPublication consumerSmoke
python3 -B publishing/release.py tests
python3 -B -m unittest discover -s publishing -p 'test_*.py'
python3 -B publishing/verify-publication.py
./gradlew -p publishing/consumer-smoke --offline -PpomOnly clean check
./gradlew :core:test --tests org.totipo.conformance.SpecSnapshotIntegrityTest --tests org.totipo.conformance.R19ProfileIntegrityTest
python3 -B publishing/release.py focused-tests
(cd core/src/test/resources/totipo-spec/v1-pre-rc && sha256sum -c SNAPSHOT.sha256)
git diff --check
```

Both full runs passed: **400 JUnit tests**, zero failures/errors/skips; **92/92**
portable cases, exact-set execution assertion; snapshot **3/3**, profile **1/1**.
Focused accounting passed 4 tests. Snapshot hashes passed **99/99**. Python
guardrails passed **36 tests**. Publication verifier accepted exactly **10 unsigned
artifacts**. `release.py compare` confirmed **10/10 equal normal/offline hashes**
using an inventory preserved outside build outputs. Worktree remained clean.

An accounting command was initially invoked while the first Gradle suite was
still running and reported missing XML. It was rerun after Gradle completed;
the passing full accounting above is from the completed suite, before focused
tests. No baseline failure was waived.

Build/test JDK: OpenJDK 25.0.4.1+1; wrapper Gradle 9.8.0; Python 3.14.7.
Production target: `--release 17`, class major **61**, minor **0**.
Staging identity: `build/repository/org/totipo/totipo-{core,storage-nio}/0.2.0/`.
Core runtime graph: `org.bouncycastle:bcprov-jdk18on:1.86`.
NIO runtime graph: `org.totipo:totipo-core:0.2.0` → BC 1.86.
Independent consumer compile graph: NIO 0.2.0 + core 0.2.0; runtime adds only BC.
Wrapper JAR SHA-256:
`238e777fcddd7e34f9708186085def2abd6e08e658505b38718d79d74c21abd5`.

## 3. Current CI / release gate inventory

“CI” below describes the starting ordinary workflow; release-specific commands
are inspected in the protected workflow and checklist. Remote reads without
secrets are still excluded from normal qualification when they depend on mutable
release state. Bootstrap is dependency-maintenance tooling, not qualification.

| Check | Owner | Current CI? | Local release prep? | Safe for Nix check? | Requires remote state? | Requires secrets? |
| --- | --- | --- | --- | --- | --- | --- |
| Compile, tests, Javadocs, sources, bytecode, strict locks/verification (`test build`) | Gradle | Yes | Yes | Yes, fixed cache | Acquisition only before fixed cache | No |
| Local publication staging + `verifyPublication` | Gradle / publication verifier | Yes | Yes | Yes | No | No |
| Independent module consumer (`consumerSmoke`) | Standalone Gradle build | Yes | Yes | Yes, offline | No | No |
| Independent POM-only consumer | Standalone Gradle build | Yes | Yes | Yes, offline | No | No |
| Full XML / exact r19 executed-set accounting (`release.py tests`) | Release helper | No; release preflight | Yes | Yes | No | No |
| Python `unittest discover` guardrails | Python | Yes | Yes | Yes; mutations mocked | No | No |
| Standalone `verify-publication.py` | Python | Through Gradle | Yes | Yes, preserved independent invocation | No | No |
| Focused snapshot/profile tests + `focused-tests` | Gradle / release helper | No; release preflight | Yes | Yes, after full accounting | No | No |
| `sha256sum -c SNAPSHOT.sha256` | Snapshot inventory | No; release preflight | Yes | Yes | No | No |
| Wrapper JAR/distribution identity | Reviewed wrapper inputs | JAR only | Yes | Yes; strengthened to all three identities | No | No |
| Normal/offline artifact inventory/hash comparison (`compare`) | Release helper | No; release workflow | Yes | Keep separate release evidence | No | No |
| `prepare --check`, version/notes/source layouts | Release helper | No; release preflight | Yes | File-only; unit tests exercise it | No | No |
| `inputs`, `inventory` | Release helper | No; release workflow | Yes | Local release context; verifier/accounting own normal evidence | No | No |
| `git diff --check`, clean checkout | Git | No; release preflight | Yes | Source-tree hygiene outside filtered derivation | No | No |
| `prepare` / `prepare --dry-run` | Release helper | No | Yes | No: source preparation, not qualification | No | No |
| `identity`, `state`, `upload-ready`, attempt history | Release helper | No; release workflow | Authorized release | No | Yes | GitHub token where used |
| `secrets`, signing tasks, `signed` | Release helper / Gradle | No; protected release | Authorized release | No | Not necessarily | Yes |
| `publishAndReleaseToMavenCentral` | Gradle | No; protected release | Authorized release | No | Yes | Yes |
| `remote`, fresh Central-only module/POM consumers | Release helper / Gradle | No; release workflow | Post-publication | No | Yes | No Central credentials |
| `tag-release` | Release helper / Git / GitHub | No; final release job | Authorized release | No | Yes | GitHub write token |
| `bootstrap-m0.sh`, wrapper/locks/verification generation | Bootstrap tooling | No | No; maintenance | No: mutates reproducibility inputs | Yes | No |

## 4. Current flake baseline

Starting flake described a development environment, with nixpkgs, flake-utils,
llm-agents and jailed-agents inputs, nixpkgs-fmt and a default shell containing
JDK 25 headless, Gradle 9 and Python 3. No checks or packages existed.
Numtide substituter/key already existed. The root nixpkgs pin is
`39ad350a0602fa0a58a544344e3e9187526ea45c` (root input `nixpkgs_3` in the lock).
Its Gradle implementation is 9.8.0 and supports `fetchDeps`; no input upgrade needed.

## 5. Desktop / Android pattern review

Sibling repositories were absent locally. Inspected committed upstream files
read-only, without creating or editing sibling checkouts:

- Desktop `f960aa0dec45d887b04156b66a0b22f30247a404`: flake, package derivation,
  dependency manifest and CI. Same derivation for check/default package,
  `gradle.fetchDeps`, updater passthru, selected source trees, SHA-pinned actions,
  explicit flake-config acceptance, independent wrapper check.
- Android `3493126a06aa6eeccb905b8f1bc87fc95b2f6717`: flake, package derivation
  and manifest. x86_64-linux scope, selected sources, `fetchDeps`, updater,
  fail-closed ungenerated cache detection, pinned Gradle assertion.
- Inspected Gradle setup/fetch/update/init scripts and mitm-cache fetch/setup at
  Java's exact nixpkgs pin. Replay uses only fixed fetched files. Its Gradle hook
  adds proxy flags during replay, and `--offline` without a replay proxy.

## 6. Java derivation design decision

`qualification.nix` defines one library qualification derivation. It neither
installs nor launches an application. Gradle remains build and Maven-artifact
authority. The source filter includes production/test sources, snapshot, all
Gradle locks/verification/catalog inputs, publication tooling and Nix helpers;
excludes Git state, build outputs, Gradle homes, editor state and Python caches.

## 7. checks.java design

`checks.x86_64-linux.java` refers to the qualification derivation. First populate
an isolated Gradle home from nixpkgs' fixed replay cache by resolving all existing
resolvable project/buildscript configurations with `nix/dependencies.gradle`.
Strict verification and locks apply to this acquisition too. Then append
`--offline` for the full build and every later Gradle command. A missing cache
entry fails; there is no internet fallback inside the Nix sandbox.

This cache population is not a second Java build or test run. It is needed because
`fetchDeps` supplies fixed HTTP replay files rather than a prepopulated Gradle home.
Only the explicitly invoked updater records remote dependency acquisition.

## 8. packages.default disposition

Expose the **same derivation** as `packages.x86_64-linux.default` because its
output is useful: exactly the reviewed unsigned Maven-shaped ten-artifact tree.
`nix build` is optional materialization, never a second normal gate.

## 9. Fixed Gradle dependency-cache design

`package-deps.json` is consumed by the pinned `gradle.fetchDeps` mechanism.
The updater executes the same build task set after resolving all configurations;
tests, production, Javadocs and plugin resolution are covered. Both consumer
modes need only staged Totipo artifacts and the already cached BC 1.86 inputs.
No Maven Local, source/project substitution, composite build, verification
exception or dependency-lock change is introduced.

## 10. package-deps generation / review

**ACCEPTED:** the human ran the updater; the generated manifest is present and
was exhaustively reviewed. SHA-256:
`23f772ac20c4b38745d382e817fb889fa7dc6d7b0d8ae79c6eae985324f2ee2e`.

- **92 hashed artifacts**, **39 exact coordinates**, only `jar`, `pom`, `module`.
- URLs are exclusively `https://plugins.gradle.org/m2/` and
  `https://repo.maven.apache.org/maven2/`. Every compressed URL was expanded and
  reviewed. Every SRI decodes to a 32-byte SHA-256. No redirects or mutable Maven
  version-list metadata are recorded. The only non-artifact fields are the
  standard nixpkgs comment and format version 1.
- **92/92** hashes match downloaded canonical artifact bytes. Plugin transitives
  were compared directly with canonical Maven Central too; the plugin marker POM
  was checked at its canonical Gradle Plugin Portal URL.
- **68/92** hashes also match existing committed Gradle verification entries.
  All **24** remaining entries are POMs accompanying module metadata; canonical
  hashes match and the fixed cache pins them. Existing strict verification and
  its selected-artifact hashes are unchanged, with no exception added.
- All external modules/versions from core, NIO and consumer locks are present.
  No cached `org.totipo` artifact: both consumer modes use local staging.
  BC is exactly 1.86; Jackson core/BOM 2.18.2; selected JUnit 6.1.3.
- No snapshots, dynamic versions, unexpected selected versions or unrelated
  modules. Offline `buildEnvironment` and core `testRuntimeClasspath` reports
  confirm the existing graph. Kotlin stdlib/reflect 2.4.10, Kotlin jdk7/jdk8
  adapters 1.8.21, Moshi/Retrofit/OkHttp/Okio and SLF4J belong to the unchanged
  Vanniktech 0.37.0 publication plugin, not production libraries.
- Expected metadata-only earlier versions are explained: Moshi requests Okio
  3.7.0, resolved to 3.15.0 (no Okio 3.7.0 JAR cached); Jackson's parent chain
  (`jackson-base` → `jackson-bom` → `jackson-parent` → `oss-parent:61`) imports
  JUnit BOM 5.10.2. That BOM has no runtime JAR and does not change selected
  JUnit 6.1.3. Both metadata identities already existed in verification metadata.

Complete coordinate/file inventory follows. `P` is the Gradle Plugin Portal base
above; `C` is canonical Maven Central. URLs expand from group dots to slashes as
`BASE/group/artifact/version/artifact-version.EXT`. Exact SRI identities remain
in the reviewed manifest identified by the SHA-256 above.

| Repository | Coordinate | Artifact extensions | Existing verification matches |
| --- | --- | --- | --- |
| C | `com.fasterxml:oss-parent:61` | pom | 1/1 |
| C | `com.fasterxml.jackson:jackson-base:2.18.2` | pom | 1/1 |
| C | `com.fasterxml.jackson:jackson-bom:2.18.2` | pom | 1/1 |
| C | `com.fasterxml.jackson:jackson-parent:2.18.1` | pom | 1/1 |
| C | `com.fasterxml.jackson.core:jackson-core:2.18.2` | jar, module, pom | 2/3 |
| P | `com.squareup.moshi:moshi:1.15.2` | jar, module, pom | 2/3 |
| P | `com.squareup.moshi:moshi-kotlin:1.15.2` | jar, module, pom | 2/3 |
| P | `com.squareup.okhttp3:okhttp:5.1.0` | module, pom | 1/2 |
| P | `com.squareup.okhttp3:okhttp-jvm:5.1.0` | jar, module, pom | 2/3 |
| P | `com.squareup.okio:okio:3.15.0` | module, pom | 1/2 |
| P | `com.squareup.okio:okio:3.7.0` | module, pom | 1/2 |
| P | `com.squareup.okio:okio-jvm:3.15.0` | jar, module, pom | 2/3 |
| P | `com.squareup.retrofit2:converter-moshi:3.0.0` | jar, module, pom | 2/3 |
| P | `com.squareup.retrofit2:converter-scalars:3.0.0` | jar, module, pom | 2/3 |
| P | `com.squareup.retrofit2:retrofit:3.0.0` | jar, module, pom | 2/3 |
| P | `com.vanniktech:central-portal:0.37.0` | jar, module, pom | 2/3 |
| P | `com.vanniktech:gradle-maven-publish-plugin:0.37.0` | jar, module, pom | 2/3 |
| P | `com.vanniktech.maven.publish:com.vanniktech.maven.publish.gradle.plugin:0.37.0` | pom | 1/1 |
| C | `org.apiguardian:apiguardian-api:1.1.2` | jar, module, pom | 2/3 |
| C | `org.bouncycastle:bcprov-jdk18on:1.86` | jar, pom | 2/2 |
| P | `org.jetbrains:annotations:13.0` | jar, pom | 2/2 |
| P | `org.jetbrains.kotlin:kotlin-reflect:2.4.10` | jar, pom | 2/2 |
| P | `org.jetbrains.kotlin:kotlin-stdlib:2.4.10` | jar, module, pom | 2/3 |
| P | `org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.8.21` | jar, pom | 2/2 |
| P | `org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.8.21` | jar, pom | 2/2 |
| C | `org.jspecify:jspecify:1.0.0` | jar, module, pom | 2/3 |
| C | `org.junit:junit-bom:5.10.2` | module, pom | 2/2 |
| C | `org.junit:junit-bom:6.1.3` | module, pom | 2/2 |
| C | `org.junit.jupiter:junit-jupiter:6.1.3` | jar, module, pom | 2/3 |
| C | `org.junit.jupiter:junit-jupiter-api:6.1.3` | jar, module, pom | 2/3 |
| C | `org.junit.jupiter:junit-jupiter-engine:6.1.3` | jar, module, pom | 2/3 |
| C | `org.junit.jupiter:junit-jupiter-params:6.1.3` | jar, module, pom | 2/3 |
| C | `org.junit.platform:junit-platform-commons:6.1.3` | jar, module, pom | 2/3 |
| C | `org.junit.platform:junit-platform-engine:6.1.3` | jar, module, pom | 2/3 |
| C | `org.junit.platform:junit-platform-launcher:6.1.3` | jar, module, pom | 2/3 |
| C | `org.opentest4j:opentest4j:1.3.0` | jar, module, pom | 2/3 |
| P | `org.slf4j:slf4j-api:2.0.18` | jar, pom | 2/2 |
| P | `org.slf4j:slf4j-bom:2.0.18` | pom | 1/1 |
| P | `org.slf4j:slf4j-parent:2.0.18` | pom | 1/1 |

## 11. Toolchain

Qualification uses pinned JDK 25 headless, Gradle 9.8.0 (explicit assertion), and
Python 3. `JAVA_HOME` selects that JDK; automatic toolchain downloads remain off.
`LC_ALL=C.UTF-8` matches Linux filename assumptions. Java output remains release 17.

## 12. Gradle qualification inside Nix

Build task set: `clean test build verifyPublication consumerSmoke`, with offline,
no-daemon, no-build-cache, rerun-tasks, no-configuration-cache and strict dependency
verification. nixpkgs' Gradle setup supplies no-daemon; our flags and preBuild
supply the other constraints. The download updater alone records acquisition.

## 13. Consumer smoke

`consumerSmoke` remains a separate Gradle build. An optional
`consumerGradleExecutable` property selects Nix-provided Gradle inside the
derivation; default remains `./gradlew`. The consumer explicitly uses offline,
no-daemon, no-build-cache, rerun-tasks and strict verification. Nix separately
runs POM-only `clean check`. Existing exclusive local Totipo staging and BC-only
Central repository declarations, module-coordinate checks, compile/runtime graphs,
class-loading assertions and generated verified staging hashes remain intact.

## 14. Publication verifier

The exact existing `publishing/verify-publication.py` is unchanged. It runs through
Gradle and independently again in the check phase. It verifies six JARs, two POMs,
two module metadata files, contents/scopes/Java 17, checksums and absence of
unexpected unsigned staging files. Consumer verification metadata is generated
only after structural/content validation; external BC hashes stay pinned.

## 15. release.py accounting

Run `release.py tests` immediately after the complete Gradle suite; capture its
summary before focused tests can overwrite core XML. It fails on missing explicit
integrity counts, failures/errors/skips or missing exact-set corpus assertion.

## 16. Release guardrail unit tests

Run all `publishing/test_*.py` via standard-library unittest. Existing remote,
Git and signing mutations stay mocked. Two added tests cover released-status
write-free checks / future preparation and ambiguous duplicate rejection.
`release.py` recognizes the exact released-status block while retaining all
existing preparation/version/notes/identity/publication boundaries. No new remote
or release action is added. Post-edit guardrails currently pass **38 tests**.

## 17. Snapshot / profile / r19 integrity

Full-suite accounting precedes a separate focused snapshot/profile Gradle run,
then `release.py focused-tests`, then all `SNAPSHOT.sha256` records. All existing
independent release-preflight integrity evidence is represented in the check.
Snapshot/corpus/profile bytes and production tests are unchanged.

## 18. Wrapper-integrity check

`checks.wrapper` runs `nix/verify-wrapper.py` without executing the wrapper.
It checks exact reviewed JAR SHA-256, exact distribution URL and exact distribution
SHA-256, rejecting duplicate identity properties. URL is Gradle 9.8.0 bin ZIP;
distribution SHA-256 is
`bafd5ce9cfaea0fbccfdc8439a1ac42fbd4cd9c89dc9a988228d8a2639a58e6c`.
The independent lightweight check has the same x86_64-linux scope; no Darwin claim.

## 19. Derivation output

`qualified`, `publication/` (only the verifier's ten artifacts),
`publication-sha256.json`, `test-summary.txt`, `dependency-summary.txt`.
No signatures, credentials, Gradle home or transient logs. Timestamped repository
indexes and their checksums are excluded. No timestamp is added to summaries.

## 20. CI migration

Authoritative ordinary Linux CI becomes checkout, install Nix, and exactly
`nix flake check --print-build-logs path:.`. No setup-java/setup-gradle, direct
Gradle/Python qualification, extra build or forced rebuild remains in that workflow.

## 21. Supplemental platform CI disposition

Starting ordinary CI had one ubuntu-latest job and no matrix or portability jobs.
No Windows/macOS/other platform evidence was deleted. Authoritative derivation
scope is x86_64-linux; dev shells retain their existing default systems.

## 22. GitHub Actions immutable pins

Read-only `git ls-remote` confirmed each release tag resolves to the pinned commit.

| Action | Intended tag | Commit SHA | Disposition |
| --- | --- | --- | --- |
| actions/checkout | v7.0.1 | `3d3c42e5aac5ba805825da76410c181273ba90b1` | Ordinary CI pinned; already pinned in release |
| cachix/install-nix-action | v31.11.1 | `13d8dd58da0234aa297dedd986986ccb8e7f3e24` | Reuse desktop convention |
| actions/setup-java | v6.0.1 | `de7274f081f381c8f8158605e0321c36c376e2e6` | Removed from ordinary CI; unchanged protected release |

## 23. Cache / flake-config policy

Installer explicitly sets `accept-flake-config = true`. Existing
`https://cache.numtide.com` and
`niks3.numtide.com-1:DTx8wZduET09hRmMtKdQDxNNthLQETkc/yaX7M4qK0g=`
remain unchanged. No new cache or disabled signature requirement. CI keeps
`permissions: contents: read`, `persist-credentials: false`, and no release secrets.

## 24. Protected release-workflow disposition

`.github/workflows/release.yml` is unchanged. It couples exact-source/current-main
identity, absence checks, normal/offline artifact hashes passed between jobs,
protected environment, signing/upload and independent remote verification, then
tag/release mutation. Replacing portions now would expand release scope and risk
the artifact handoff. Its existing direct Java preflight remains deliberate
release-specific evidence; no credentials enter ordinary flake checks.

## 25. README / checklist / contributor guidance

README and checklist name `nix flake check path:.` as the single routine source
gate. README preserves direct Gradle development commands, explains optional
artifact materialization, pinned cache provenance/update/review and platform scope.
New AGENTS.md states agents must not run Nix and must not request a second ordinary
build. README, API_DESIGN and checklist now say Java 0.2.0 is released. Historical
release preparation reports are unchanged; existing historical Maven examples
remain unchanged.

## 26. Release-reproducibility separation

Checklist retains the explicit normal/offline ten-artifact inventory comparison
for release preparation. A successful Nix check, optional materialization or
deliberate rebuild never substitutes for that comparison. Routine source
qualification no longer separately repeats all Gradle/Python/focused commands.

## 27. flake.lock invariance

Starting committed lock SHA-256:
`44728fcfd8529462cb415b186ee4ce3400343dd911d325b6a965bd386b9f7749`.
Use this HEAD's lock; no historical lock comparison. The agent ran no Nix
command and no flake input update; the human ran the cache updater and final check; lock bytes remain unchanged. Checkpoint recheck confirms the identical SHA-256 and no diff from HEAD.

## 28. Java / version / spec / dependency invariance

No edits to VERSION, SPEC_PIN, snapshot/corpus, production Java, public APIs,
protocol, dependency versions, Gradle locks, verification metadata, wrapper or
BC 1.86. Root build edit only selects consumer executable and enforces consumer
execution flags. New manifest materializes existing dependencies; it does not
authorize dependency changes. Checkpoint byte-invariance audit passes for all these paths, including the
protected release workflow and consumer verification template.

## 29. Non-Nix validation after edits

Passed after edits:

- Normal `./gradlew clean test build verifyPublication consumerSmoke` and full
  accounting: **400 tests**, zero failures/errors/skips, exact **92/92** corpus,
  **3/3 snapshot + 1/1 profile**.
- Forced offline/no-daemon/no-build-cache/rerun-tasks full suite and accounting:
  same **400** and integrity counts; all 30 actionable root tasks executed.
- Python guardrails: **38 tests passed** (36 existing + 2 released-status guards).
- Standalone publication verifier: **10 unsigned artifacts**, expected runtime
  graphs and Java 17 classfiles. Normal/offline comparison: **10/10 equal**;
  artifact hashes also equal the pre-edit normal inventory.
- Independent POM-only consumer passed. Explicit consumer executable override to
  installed Nix-provided `gradle` passed with offline strict verification and no
  Nix invocation; ordinary wrapper invocation also passed.
- Dependency-population init script resolved all configurations successfully using
  the existing host offline Gradle cache. This verifies Gradle/task compatibility,
  not Nix sandbox realization; the subsequently generated manifest is reviewed
  separately in section 10.
- Focused integrity: **4 tests**, no failures/errors/skips; snapshot hashes **99/99**.
- Current released `release.py prepare 0.2.0 --check` passed without writes.
- Wrapper identities passed; altered JAR, changed URL and duplicate identity were
  rejected. Static CI audit passed; `git diff --check` passed.

Existing nonfatal Javadoc missing-comment warnings and Python HTTPError fixture
ResourceWarnings were present. No warning was used to waive a failed check.

## 30. Human cache-generation result

**DONE:** human reported running `nix run path:.#update-package-deps`. The
8252-byte generated manifest is present, its content review passed as detailed in
section 10, and protected source/lock/verification inputs remain unchanged.
The updater terminal transcript was not supplied; cache generation is established
by the resulting manifest and its reviewed bytes, not inferred test execution.

## 31. Human one-command flake-check result

**PASS:** the human reports successful `nix flake check path:.` and supplied the
complete visible terminal output:

```text
> nix flake check path:.
warning: The check omitted these incompatible systems: aarch64-darwin, aarch64-linux, x86_64-darwin
Use '--all-systems' to check all.
```

This is normal quiet successful qualification on x86_64-linux. The incompatible
system warning is consistent with the intentionally limited platform scope; no
all-systems check is requested. Reviewing the current flake establishes that
`checks.x86_64-linux.java` is the qualification derivation and
`checks.x86_64-linux.wrapper` checks all three reviewed wrapper identities.
Successful ordinary flake-check realization therefore covers both checks, either
by building or by valid reuse. The definition includes the full offline Gradle
suite, independent module/POM consumers, Python guardrails, complete accounting
before focused tests and snapshot integrity. No claim is made that this quiet
invocation freshly executed those commands; output store paths/logs were not
independently inspected in the agent environment. Structural review plus the
human-reported successful realization/reuse is the agreed gate evidence.
The agent has not run Nix. No second build/rebuild/development-shell gate requested.

The human also tried plain `nix flake check`; it failed at evaluation because
`qualification.nix` was untracked. This was Git-source filtering, not a Java
qualification failure. Explicit `path:.` includes new working-tree inputs. A
Git-source flake includes only Git-known paths; the human subsequently used
intent-to-add so new paths are visible without staging contents. The agent
performed no index mutation. The authoritative human command remains exactly
`nix flake check path:.`.

## 32. Remote CI status

**Remote CI NOT RUN.** No push or workflow dispatch. Exact committed workflow
run must be reviewed separately after human review/commit/push.

## 33. Spec follow-up

Spec should get its own meaningful `checks` exposing structural, Go, conformance,
race/fuzz qualification under `nix flake check`. No totipo-spec changes or design
work is included here.

## 34. Final Git state

At completion: branch `main`, HEAD unchanged at
`d6310c177ae930df188fd4f5798622c935698b2e`; no staged content. The human
subsequently marked new paths intent-to-add to investigate Git-source invocation;
the agent performed no index mutation. Modified existing files:
`.github/workflows/ci.yml`, `API_DESIGN.md`, `README.md`, `RELEASE_CHECKLIST.md`,
`build.gradle.kts`, `flake.nix`, `publishing/release.py`, `publishing/test_prepare.py`.
New files (now human-marked intent-to-add): `AGENTS.md`, `qualification.nix`, `package-deps.json`,
`nix/dependencies.gradle`, `nix/verify-wrapper.py`, and this report.
All edits remain unstaged/uncommitted. No tag, signing,
release, remote publication, workflow dispatch or push has been performed.
Cache review and human one-command qualification evidence review are complete.
Remote CI is the explicitly deferred post-commit/push follow-up.

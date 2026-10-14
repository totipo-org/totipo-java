# J6 — Nix-first build and minimal publication verification

Status: **implementation, host qualification and final owner Nix gate PASS;
release activation DISABLED**. J6 is complete; the owner reported the corrected
`nix flake check` passed on 2026-10-14.
All changes remain uncommitted. Evidence is external at `/tmp/totipo-j6/`.
No live release operation was performed.

## 1. Starting authority and classification

Started from clean, committed `main`, full HEAD
`ae872f5be84ff0e19a823307f4e3d9936883ea51`.
Initial `git status --short` and cached diff were empty.
HEAD includes J5B (`0cd0631`), the timestamp fixture correction (`1ef3786`),
and the workflow-host fixture correction (`ae872f5`).

Read AGENTS.md completely; consulted the requested J5A, J5B, J4B and J4C reports
and inspected actual build, workflow, publication, signing, consumer and Nix code.
The J6 instruction explicitly supersedes the historical decisions to retain
wrapper authentication and per-entry archive validation. Historical reports are unchanged.
Work stayed in totipo-java; no sibling repository, agent worktree or sub-agent
was inspected or used. Disposable source exports are generated test fixtures,
not additional repositories. No repository commit, tag, push, dispatch, settings
change, Central publication or GitHub Release was authorized or performed.

Classifications: **DEPENDENCY_BUILD, RELEASE_TOOLING, PUBLICATION_METADATA,
CONSUMER_BOUNDARY, DOCS_JAVADOC** (Markdown process prose).
No library runtime/API, source Javadoc, corpus, dependency-version or release-version
change. The requested release-style reproducibility and disposable signing checks
are warranted by the execution/acceptance boundary change; this is not preparation
of a real release version.

| Identity | Before and after |
| --- | --- |
| VERSION | `0.2.0` plus LF |
| Specification | v1/r19, `cdb4e91be1c6d3704874b2b92457ffe7be5e9084` |
| Requirements profile SHA-256 | `7242fc557a7ca56452c934bedc5e7dc2835248f5cd36fa220f05588dc967ec80` |
| Snapshot / portable cases | 99 imported files / 92 cases |
| Gradle | 9.8.0, revision `a927be5e08efe79e0b87ada06c762dde6bb9f8b8` |
| JDK | OpenJDK 25.0.4.1+1 (Nix); production target Java 17 / class version 61 |
| Publishing plugin / BC / Jackson / JUnit | 0.37.0 / 1.86 / 2.21.7 / 6.1.3, unchanged |
| Nix bootstrap declaration | 2.35.2, unchanged; no local Nix executable/version query was run |
| Root nixpkgs | locked `nixpkgs_3`, revision `39ad350a0602fa0a58a544344e3e9187526ea45c` |

No Nix executable was found on the session PATH. Nix's current hosted bootstrap
identity is a source declaration, not a locally observed running version.
Agents must not run Nix.

## 2. Original acquisition and execution inventory

Before J6, qualification.nix already used
`pkgs.gradle_9.override { java = pkgs.jdk25_headless; }`, asserting 9.8.0.
The devShell/jailed shell used `gradle_9` and JDK 25, but the release-runtime
contained the JDK, tooling, wrapper verifier, Git, curl and CA bundle, without Gradle.
Release execution therefore acquired a second Gradle distribution through the wrapper.

Original operational wrapper paths:

- Root `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar` and properties.
- build.gradle.kts's pinned Wrapper task and default nested consumer executable.
- CI wrapper identity step; release preflight and publisher wrapper steps/commands;
  verify-release reconstruction, both remote consumers and finalizer wrapper steps.
- Standalone WrapperVerifier/WrapperVerifierTest and nix/wrapper-verifier.nix,
  including its launcher, separate flake check, fixture selection and runtime inclusion.
- AGENTS.md, README.md, RELEASE_CHECKLIST.md and three dependency-lock maintenance comments.
- WorkflowBoundaryTest's publishing-shell fake and finalizer no-build assertion.

The original properties selected the 9.8.0 binary distribution with SHA-256
`bafd5ce9cfaea0fbccfdc8439a1ac42fbd4cd9c89dc9a988228d8a2639a58e6c`.
The small wrapper verifier authenticated the complete JAR/properties; it did not
authenticate arbitrary cached distribution bytes. Historical report references
remain intentionally historical.

The original publication caller was Gradle verifyPublication → prebuilt
PublicationCli.verify → PublicationVerifier.verify(root, repository, version).
Release inventory/compare/signed/remote already reused job-local accepted manifests;
they did not repeat semantic verification. Nix checked/copies accepted staging.
This caller/authority model remains; verify no longer receives the unused source root.

## 3. Supported Nix-first model and migrated callers

One flake let-binding selects JDK 25 and Gradle 9.8.0 with that JDK override.
Qualification, devShell, jailed shell and release-runtime reuse these packages.
The devShell explicitly supplies JAVA_HOME and TOTIPO_GRADLE, and exposes java/gradle.
Supported local operation requires direnv or nix develop, then ordinary
`gradle test`, `gradle build`, `gradle verifyPublication`, etc.
There is no supported non-Nix host Gradle/JDK fallback or separate distribution acquisition.

| Caller | Final executable boundary |
| --- | --- |
| Root/local tests, build, Javadocs, publication and maintenance | devShell `gradle` |
| Nested local module consumer | explicit consumerGradleExecutable property, otherwise TOTIPO_GRADLE; devShell PATH `gradle` when neither is supplied |
| Nix qualification/root and POM consumer | existing pinned nativeBuildInputs Gradle; nested consumer retains explicit store-path property |
| CI | Nix qualification; credential-free runtime exports its explicit TOTIPO_GRADLE path |
| Release normal/offline preflight and POM consumer | quoted `"$TOTIPO_GRADLE"` from release-runtime |
| Publisher reconstruction, Sign tasks and single Central invocation | same explicit runtime path |
| Verification reconstruction and both fresh-home remote consumers | same explicit runtime path even when GRADLE_USER_HOME changes |
| Disposable host qualification/signing | materialized pinned store executable; no host downloader/fallback |
| AGENTS, README, checklist and lock maintenance comments | local devShell `gradle` instructions |

All existing command-specific no-daemon, no-build-cache, no-configuration-cache,
offline, rerun-tasks and strict-verification options retain their purpose.
No Gradle installation action, runner PATH assumption or new launcher framework was added.
The Gradle package's own store-internal launcher happens to be named gradlew;
this is Nix package implementation, not repository wrapper acquisition/execution.

Host tests used:
`/nix/store/jma178bc8ijyv2xbb6c142ajylyhvcnc-gradle-9.8.0/bin/gradle`
with JAVA_HOME
`/nix/store/7gph19iic118dil9na8zx0nxp316fx1x-openjdk-headless-25.0.4.1+1/lib/openjdk`.
Gradle --version observed 9.8.0 and launcher/daemon JVM 25.0.4.1+1.
The package launcher defaults to another Nix JDK 25 package when JAVA_HOME is absent;
our host runs used the explicit reviewed headless JDK. The new shared overridden
flake/runtime declaration still requires owner Nix evaluation, not a claimed host build.

Deleted all seven obsolete files listed above: both wrapper scripts, JAR,
properties, WrapperVerifier.java, WrapperVerifierTest.java, nix/wrapper-verifier.nix.
Removed Wrapper import/configuration, verifier launcher/check/runtime wiring and
all five workflow identity steps. Retained Gradle's ordinary built-in functionality.
No custom Gradle identity verifier replaces Nix.

qualification.nix's remaining source filter needs no replacement: its gradle tree
still contains required catalog/strict metadata, while deleted wrapper files are absent.
The separate wrapper fileset/check is deleted. nix/tooling.nix's independent Java
compilation, nine runners and narrow workflow fixtures remain intact.

## 4. Minimal publication acceptance and explicit trade-off

The checker establishes: **the expected Maven artifacts, essential publication
metadata, and their complete bytes**. Gradle is the trusted archive producer.

Retained contract:

- Exactly org.totipo:totipo-core and org.totipo:totipo-storage-nio at the requested version.
- Five canonical artifact roles each: binary, sources, Javadoc, POM and module metadata;
  ten complete SHA-256 identities in the unchanged bounded schema-1 manifest.
- Missing/extra publication paths reject; optional standard checksum sidecars and
  local Maven indexes are checked and included in the existing staging fingerprint.
- POM coordinates, nonempty descriptive/license/developer/SCM metadata, exactly
  the expected direct dependency and scope; optional=false/type=jar are accepted
  explicit defaults. Optional dependencies, classifiers and exclusions cannot
  silently alter the intended graph.
- Module coordinates and supported metadata schema; actual usage/category/docstype
  identifies API/runtime/source/Javadoc roles, with unique names/roles and correct
  artifact links, sizes and complete-file checksums. Variant name spelling,
  exact component-attributes equality and broad project-path string rejection are gone.
- Core exposes BC 1.86 only at runtime; NIO exposes core in API/runtime metadata
  and compile scope in the POM. Extra fixture/development dependencies, capabilities,
  constraints and variant redirection reject.
- Java 17 remains the Gradle compilation target, with existing all-production-class
  bytecode checks attached to test/check, plus published target metadata.
- Actual isolated consumers retain strict verification, locks, graph checks,
  compile/runtime smoke and both module-metadata/POM-only modes.

Removed compareArchive, archive, dual ZIP readers, per-entry size/CRC/name/layout
checks, class-header checks within archives, exact archive manifest bytes, and
class/source/Javadoc tree/member byte comparisons. Removed ZIP fixture writers,
mutators, archive-only expected output trees and all archive convention negatives.
Publication fixtures now deliberately use opaque non-ZIP bytes, demonstrating
that this checker does not inspect archive members. No replacement ZIP parser,
jar-list pipeline or extraction framework was introduced.

POM exact title/license/developer/SCM value pins became presence checks, while
coordinates/dependencies remain exact. Required XML fields reject duplicates,
nested malformed values and unsafe XML; insignificant surrounding value whitespace
is accepted. Equivalent variant names/descriptive metadata and unrelated JSON
extension text are positively covered.

FilesSupport, Json, PublicationRepository and PublicationManifest still serve
bounded reads, schema-1 serialization, fingerprints, evidence freshness and safe
copy operations. They are not archive-only helpers and remain otherwise unchanged.
PublicationCli drops only the obsolete source-root argument to the verifier.
No filesystem/JSON rewrite, dependency or replacement manifest format was introduced.

Lost guarantees are deliberate: no independent proof that every compiled class,
source or generated Javadoc file is included, no independent archive entry/CRC/
dual-view/path convention validation, and no exact packaging-manifest assertion.
A reproducible but consistently mispackaged archive can escape if it satisfies
metadata and consumer checks. This residual risk is explicitly accepted.
Whole-artifact hashing establishes exact-byte identity, not semantic packaging correctness.

## 5. Identity, signing and release authorization

Every newly produced staging repository still passes verifyPublication before
inventory/comparison/signing/remote use. Stale evidence is invalidated before
acceptance; manifest publication remains the final successful evidence write.
Use-boundary checks reread the accepted ten bytes and staging fingerprint.

The manifest is trusted job-local acceptance output. Arbitrary supplied hash
lists are expectations, not authority: the release CLI rejects an alternate
acceptance-file path, verifies canonical schema/identities and authenticates
current bytes. Permanent substitution, wrong-hash and changed-byte tests remain.
This does not claim that a hostile writer able to replace both the trusted
job-local manifest and repository is defeated; workflow/source provenance supplies
that boundary, as before J6.

Independent builds compare all ten hashes without normalization. Actual unsigned
signing-task inputs must match acceptance; late shared checking precedes both
local Central staging publishers. Producer/Sign onlyIf guards freeze accepted bytes.
RemoteArtifactVerifier still compares complete Central downloads to independently
reconstructed hashes, without unpacking. All ten detached signatures require
bounded acquisition and armor markers only: no cryptographic signer-trust claim.

Sequence remains qualification → acceptance → signing → signed-input evidence →
producer/Sign freeze → first-time annotated tag reservation → fresh tag/artifact
binding → one authorized Gradle Central invocation → independent verification →
create-or-validate GitHub Release. Signing failure still stops before reservation.
Existing-tag veto, rerun rejection, ambiguous-reservation failure, internal plugin
HTTP retry policy and verification-only recovery are unchanged.

Exact guards remain:

```yaml
# release.yml / publish-central
if: ${{ false && github.run_attempt == 1 }}
# verify-release.yml / finalize
if: ${{ false }}
```

Workflow changes only remove wrapper verification, export runtime Gradle and
replace executable call sites. Secret introduction remains after credential-free
runtime materialization and reconstruction. No Maven/signing secrets enter Nix.
The verify workflow still has no publishing/signing secrets, release environment,
tag reservation or upload capability. Existing source/action pins, permissions,
shared Nix token cleanup and protection assumptions remain.
No activation route was enabled.

## 6. Host qualification, compatibility and execution accounting

Baseline compilation used direct JDK 25 with --release 25, UTF-8, -Xlint:all,
-Werror, -proc:none and explicit sourcepaths. The only external tooling classpath
entry was pinned Jackson Core 2.21.7, canonical SHA-256
`8c5623b98f32d5f7e1287ff61ed50e08ab7b11381e991fff89327faabba42261`,
matching package-deps.json. Gradle never compiled independent tooling.

Before removal, the wrapper baseline downloaded its already-reviewed distribution,
ran test + relevant verifyPublication, and accepted existing staging.
Stage A then used materialized Nix Gradle for clean reconstruction/acceptance and
the real nested consumer before wrapper deletion. Ten hashes matched exactly.

Stable normal:
`gradle test build javadoc verifyPublication consumerSmoke`, then immediate
full accounting/manifest check and independent offline POM-only clean check.
PASS: 405 tests, zero failures/errors/skips; 92/92 exact-set execution assertion;
snapshot 3, profile 1. Production Java 17 checks pass. XML is preserved externally.

Independent forced-offline reconstruction used Git-known current source exported
to /tmp/totipo-j6/reconstruction, with no checkout build outputs, generated consumer
metadata, manifest or configuration caches. A distinct external Gradle home
received only acquired modules-2 dependency cache files, excluding locks.
The full no-daemon/no-build-cache/no-configuration-cache/rerun-tasks offline gate
passed, with the same 405/92/snapshot/profile accounting and ten hashes.
This supplies independent filesystem/cache-context and offline-acquisition evidence,
not Nix fixed-cache/sandbox proof. XML/manifest were preserved before signing probes.
Existing Javadoc warnings remain nonfatal; no test was skipped.
Both actual consumer modes also reject a removed core binary JAR in disposable
staging, using distinct fresh homes with no Totipo acquisition cache. Both failures
report the missing totipo-core-0.2.0.jar; original accepted bytes were restored.

All nine permanent standalone runners pass: StrictJsonTest, PublicationTest,
ReleaseTest, AuthorizationTest, RemoteVerificationTest, FinalizationTest,
FinalizationProcessTest, TestResultsTest and WorkflowBoundaryTest; Main smoke passes.
They retain inventory/metadata/malformed file, wrong scopes, accepted-byte changes,
manifest substitution, signing mismatch, Central mismatch, tag/finalization and
workflow authorization negatives. The actual workflow shell fixture still tests
signing-before-reservation failure propagation using the runtime Gradle variable.

| Invocation category | Actual count / result |
| --- | --- |
| Broad wrapper baseline with focused publication addition | 1 PASS; 29s, 26 outer tasks: 7 executed / 19 up-to-date; module tests reused existing XML |
| Baseline standalone main/test compilation | 1 pair PASS |
| Focused baseline PublicationTest | 1 PASS, 137 assertions |
| Stage A focused Gradle reconstruction + module consumer | 1 PASS; 29s, 21 outer tasks executed; nested 10s / 4 executed |
| Stage A ten-artifact compatibility comparison | 1 PASS |
| Stage B standalone compilation attempts | 2 pairs attempted: first main PASS/test compile FAIL, corrected main/test PASS |
| Focused Stage B PublicationTest | 1 executed PASS, 127 assertions; earlier class-not-found attempt did not execute tests |
| Full final standalone suite | 1 set, 9 runners PASS; Main smoke 1 PASS |
| Launcher unknown / empty-extra-argument probes | 2 expected status-2 rejections |
| Stable final normal Java gate | 1 PASS; 42s, 27 outer tasks: 15 executed / 12 up-to-date; tests actually executed |
| Separate host full offline gate | 1 PASS; independent export, 75s, 30 outer tasks: 27 executed / 3 up-to-date clean tasks |
| Full-suite XML accounting | 3 PASS: cached baseline, actual normal and actual offline; preserved externally |
| Real Gradle publication acceptances | 4 PASS: baseline, Stage A, normal, offline; not four extra test-suite invocations |
| Nested module consumers | 3 PASS: Stage A, normal, offline; normal 4 executed, offline 3 executed / 1 up-to-date clean |
| Independent POM-only consumer | 1 PASS; 4s, 4 tasks executed |
| Disposable actual missing-artifact consumer probes | 2 expected resolution failures: metadata and POM-only, distinct fresh Gradle homes |
| Normal/offline ten-artifact comparison | 1 PASS, all ten exact hashes |
| Standalone real staging manifest checks | 3 PASS: initial existing staging, final normal, independent offline |
| Disposable Sign-task invocations | 3: missing-key and invalid-key expected failures; real fixture-key signing PASS |
| Real signed acceptance / POM substitution | 1 PASS / 1 expected Java rejection |
| Frozen local publisher invocations | 4: two fixture-only failures, corrected PASS, missing-signature expected failure |
| Agent Nix / J6 human Nix / cache updater | 0 / 2: initial evaluation failed, corrected gate owner-reported PASS / 0 |
| Live remote consumers, hosted CI, Central/GitHub mutation, real-credential signing | 0 each |

The first Stage B test compilation accidentally removed the shared fixture cleanup
helper with ZIP helpers; javac reported five missing remove(Path) calls. Restoring
that still-used cleanup fixed compilation before any final gate.
The two frozen fixture failures were external Groovy script mistakes: an unqualified
taskGraph allTasks lookup, then a script getClass()/this lookup that failed to skip
PrepareMavenCentralPublishingTask. The latter stopped at missing credentials;
no network registration/upload happened. Correcting task receivers fixed the
focused check. All failed logs remain outside build outputs; no included source
changed and no broad gate was invalidated by these disposable script corrections.

Disposable real signing generated one RSA fixture key in memory using the pinned
Gradle distribution's existing BC 1.85 signing libraries. Key bytes passed only
through process capture/environment, never a source/key file or log; single-use
Gradle processes ended and the parent dropped the key. These signing libraries
are Gradle's existing runtime, not new tooling/library dependencies or production
BC-version changes. The disposable generator emitted a deprecated-API compile
note; repository tooling compilation remained warning-clean under -Werror.

Missing and invalid keys fail before successful signing. Valid signing executed
both existing Sign tasks; Java accepted all ten actual signing inputs. Appending
one newline to the actual POM signing input rejected; original bytes were restored.
The fixture selected only two local file Maven publishers, asserted file repository
URLs, prohibited uploader/deployment tasks and skipped network registration tasks.
No Central aggregate upload task was selected.

The corrected frozen invocation force-ran the graph: all JavaCompile/Javadoc/
archive/POM/module/Sign producers SKIPPED; shared verifyCentralSigningInputs and
two local publishers executed. All **477** captured producer/signature files
retained identical hashes. Missing detached signature then failed the shared check
before either local publisher. This is real Gradle task/freeze evidence, not a
live publication or production signer qualification.

## 7. Artifact identities and unchanged acquisition inputs

All ten identities match the preexisting accepted staging, freshly checked
wrapper baseline, Stage A Nix Gradle reconstruction, stable normal build and
independent offline export. **No artifact difference requires explanation.**
Ephemeral Maven-index-inclusive repository fingerprints differ and each validates
locally; they are not cross-build identity.

| Artifact | SHA-256 (all builds) |
| --- | --- |
| `org/totipo/totipo-core/0.2.0/totipo-core-0.2.0.jar` | `1b628743548c376c2ae85ece2c2f9be43fc7364e13fc0b4d4d25e3d49ee6d864` |
| `org/totipo/totipo-core/0.2.0/totipo-core-0.2.0-sources.jar` | `eee068cd5a700f66cab212ccaa728c7d1f04c981d31648c1e4e90e826d1e8b93` |
| `org/totipo/totipo-core/0.2.0/totipo-core-0.2.0-javadoc.jar` | `aa0960585fc5bd0c4c5615b754f7d259d9499776e133ca1f8298250af8dcf8df` |
| `org/totipo/totipo-core/0.2.0/totipo-core-0.2.0.pom` | `1a6bbd5b4d82c079cb621d66c89b446c09e2df33efe5e0e89dd5abfd35d7c136` |
| `org/totipo/totipo-core/0.2.0/totipo-core-0.2.0.module` | `28fcefec7f89ca69b651beb86afb25d07af5933da2f29e8fd5bdb5776b9d227c` |
| `org/totipo/totipo-storage-nio/0.2.0/totipo-storage-nio-0.2.0.jar` | `776068249e689e94136748fb8ffd86c837e0af8bbb6cec8ae5e785c4eca4ba93` |
| `org/totipo/totipo-storage-nio/0.2.0/totipo-storage-nio-0.2.0-sources.jar` | `bb690c98837f938b1cce75cdfc06b44b47b066c4cb937a4ec461f2dc82f5b04b` |
| `org/totipo/totipo-storage-nio/0.2.0/totipo-storage-nio-0.2.0-javadoc.jar` | `ef1b8cf0a278eb64e0cb9073e627debeba298f0fd400378fadfdbf8cd9c7ac7c` |
| `org/totipo/totipo-storage-nio/0.2.0/totipo-storage-nio-0.2.0.pom` | `4acf1725ac7bb5cd7a4299f729d5eceb82aa3ea524dca7c9d5a9b56616db87b2` |
| `org/totipo/totipo-storage-nio/0.2.0/totipo-storage-nio-0.2.0.module` | `33a431575523bb545b57876b09fd04255e04b60b2c64541d72b2cda5f05c7afa` |

No coordinate/version, dependency acquisition, strict verification or fixed-cache
change. Three Gradle lockfiles change only maintenance-command comments; all lock
records remain unchanged. No cache regeneration or owner cache checkpoint is
required. flake.lock, package-deps.json, catalog, verification metadata, settings
lock, consumer settings/build, all library sources/tests and r19 corpus are unchanged.

Unchanged SHA-256:

- `flake.lock`: `44728fcfd8529462cb415b186ee4ce3400343dd911d325b6a965bd386b9f7749`.
- `package-deps.json`: `ee87ee929d064232e30b0bb91c8aaa4a249ae5f0fad7d85180a9833709f6a879`.
- `gradle/verification-metadata.xml`: `383e4bc19e0b17b635408740f9ebf1681842ae8203a102baab4c831d9ec1b16a`.

## 8. Maintained source reduction

Physical Git-tracked source lines, including blanks/comments, compared to starting
HEAD. Historical reports, generated outputs, external evidence and binary bytes
are excluded. Reductions describe maintenance surface, not acceptance criteria.

| Category | Before files / lines | After files / lines | Net lines |
| --- | ---: | ---: | ---: |
| Standalone production Java | 22 / 1965 | 21 / 1847 | -118 |
| Standalone test Java | 11 / 1340 | 10 / 1228 | -112 |
| Library production Java | 94 / 3725 | 94 / 3725 | 0 |
| Library tests/fixtures Java | 50 / 6160 | 50 / 6160 | 0 |
| Operational build/configuration text | 13 / 1341 | 9 / 902 | -439 |

The operational-text group is .github, nix, flake.nix, qualification.nix, root
build.gradle.kts, both wrapper scripts and wrapper properties. Other retained
module/consumer configuration has zero net line change. The wrapper JAR deletion
is additional binary removal, not counted as source lines. Java reduction is
118 production + 112 test lines; PublicationVerifier alone goes 254 → 196 lines,
PublicationTest 352 → 278. Wrapper production/test classes are deleted entirely.
Documentation/report lines are not included in savings.

## 9. Git-aware source freeze and owner gate

The final implementation inputs are frozen. Inspected qualification.nix's explicit
tree/file allowlist and flake.nix/tooling.nix acquisition/fileset wiring:
core/storage source Javadocs are included; AGENTS.md, README.md,
RELEASE_CHECKLIST.md, API_DESIGN.md and review/ are excluded. Tooling Java and
its actual workflow/action/build/settings fixtures remain included independently.
Nix definitions, locked inputs and fixed cache continue to determine qualification.
No required protocol, consumer or tooling coverage was removed with wrapper wiring.

Conservative non-review inventory:
**314 existing regular Git-known files and 7 deleted-path markers**,
recording modes/SHA-256 in /tmp/totipo-j6/frozen-inputs.txt.
Inventory SHA-256: `5c4b5ec7c68a1ba44dbbedfe894a7d4e787edbf8a9e373e4a252b252ef1fbdf6`.
This external handoff inventory is evidence, not a new permanent trust mechanism.
The report was made Git-known with intent-to-add only before this freeze.
No staged file contents: `git diff --cached --quiet` passes and
`git diff --cached --name-only` is empty. No generated output, cache, source
export, temporary key or external evidence entered Git membership.
All tracked deletions remain unstaged.

Current branch/HEAD remains main /
`ae872f5be84ff0e19a823307f4e3d9936883ea51`.
Complete `git status --short`:

```text
 M .github/workflows/ci.yml
 M .github/workflows/release.yml
 M .github/workflows/verify-release.yml
 M AGENTS.md
 M API_DESIGN.md
 M README.md
 M RELEASE_CHECKLIST.md
 M build.gradle.kts
 M core/gradle.lockfile
 M flake.nix
 D gradle/wrapper/gradle-wrapper.jar
 D gradle/wrapper/gradle-wrapper.properties
 D gradlew
 D gradlew.bat
 D nix/wrapper-verifier.nix
 M publishing/consumer-smoke/gradle.lockfile
 A review/J6_NIX_FIRST_MINIMAL_PUBLICATION_REPORT.md
 M storage-nio/gradle.lockfile
 M tooling/src/main/java/org/totipo/tooling/publication/PublicationCli.java
 M tooling/src/main/java/org/totipo/tooling/publication/PublicationVerifier.java
 M tooling/src/test/java/org/totipo/tooling/publication/PublicationTest.java
 M tooling/src/test/java/org/totipo/tooling/release/WorkflowBoundaryTest.java
 D tooling/wrapper/src/main/java/org/totipo/tooling/wrapper/WrapperVerifier.java
 D tooling/wrapper/src/test/java/org/totipo/tooling/wrapper/WrapperVerifierTest.java
```

Complete `git diff --name-only`:

```text
.github/workflows/ci.yml
.github/workflows/release.yml
.github/workflows/verify-release.yml
AGENTS.md
API_DESIGN.md
README.md
RELEASE_CHECKLIST.md
build.gradle.kts
core/gradle.lockfile
flake.nix
gradle/wrapper/gradle-wrapper.jar
gradle/wrapper/gradle-wrapper.properties
gradlew
gradlew.bat
nix/wrapper-verifier.nix
publishing/consumer-smoke/gradle.lockfile
review/J6_NIX_FIRST_MINIMAL_PUBLICATION_REPORT.md
storage-nio/gradle.lockfile
tooling/src/main/java/org/totipo/tooling/publication/PublicationCli.java
tooling/src/main/java/org/totipo/tooling/publication/PublicationVerifier.java
tooling/src/test/java/org/totipo/tooling/publication/PublicationTest.java
tooling/src/test/java/org/totipo/tooling/release/WorkflowBoundaryTest.java
tooling/wrapper/src/main/java/org/totipo/tooling/wrapper/WrapperVerifier.java
tooling/wrapper/src/test/java/org/totipo/tooling/wrapper/WrapperVerifierTest.java
```

`git diff --cached --name-only`: empty.
Final `git diff --check` passes after report finalization.
Intent-to-add remains for owner review/commit. No source changes are planned after
this freeze; only this excluded report may record the owner result.

The final human-owned routine gate is exactly:

```sh
nix flake check
```

**J6 human Nix runs: 2 — initial evaluation failed; corrected gate PASS,
owner reported on 2026-10-14.** No full log or derivation identifier was supplied.
Agents ran no Nix. See the platform correction and completed handoff below.
This gate must establish actual Git-aware fixed-cache/offline qualification and
Nix wiring. No second nix build, cache update or live release test is requested.
The already materialized pinned tools establish host compatibility; actual
construction of the revised devShell/release-runtime and hosted Actions execution
are not demonstrated by host/static evidence. Any later included-input edit
invalidates the freeze and any owner result, requiring the affected revalidation.

## 10. Remaining first-live-release limitations

Hosted GitHub self-reference/bootstrap, installer/daemon configuration, actual
runtime materialization on hosted runners, effective branch/tag/environment
protections and native action-pin policy remain provider/owner qualification
boundaries. No hosted CI/workflow was dispatched by the agent.
Real signer identity/key handling, Central delivery, remote consumer availability,
protected first-time tag creation/immutability and GitHub Release mutation have
not been exercised live. Local fake transports/regressions do not prove those
provider behaviors. Detached signatures still establish presence only.
At-most-one authorized Gradle invocation is not exactly-once HTTP delivery;
internal plugin retries remain accepted and no upload recovery is introduced.

No unrelated architecture, toolchain, dependency, security mechanism or release
activation change was made. Stop after J6; completion is recorded in section 12 following the
corrected owner Nix gate.

## 11. Owner Nix evaluation failure and platform correction

The owner reported one failed `nix flake check` evaluation: the locked nixpkgs
26.11 rejects x86_64-darwin. flake-utils.eachDefaultSystem enumerated that removed
platform, and J6's new outer Gradle-version assertion forced the nixpkgs import
while constructing those outputs. This failure is not a Java/test/cache or
Gradle-version incompatibility.

Replaced eachDefaultSystem with an explicit eachSystem list of x86_64-linux,
aarch64-linux and aarch64-darwin. Intel macOS is no longer advertised; the other
previous development-shell platforms remain. Qualification/packages/apps still
target x86_64-linux, with all existing checks and the 9.8.0 assertion intact.
No nixpkgs downgrade, lock/input change, distribution source or host fallback
was introduced.

The included flake.nix edit supersedes the earlier source freeze. The non-review
inventory above is renewed; the prior inventory is preserved externally as
frozen-inputs-before-platform-fix.txt. All other frozen file hashes remain
unchanged. Git status/diff membership and no-staged-content invariants remain
unchanged. Static diff/whitespace and frozen-input checks pass.
No Java, publication, consumer or standalone source changed, so their successful
host gates remain applicable; no additional Gradle/test invocation was made.

Renewed owner gate remains exactly `nix flake check`. The reported attempt
failed before qualification and is not counted as a pass. Agent Nix invocations
remain zero. J6 stays pending until the corrected frozen tree passes that gate.

## 12. Completed owner qualification and handoff

On 2026-10-14 the owner reported: `nix flake check passed`.
This records the corrected tree's owner-reported PASS, following the initial
unsupported-platform evaluation failure. Total J6 human Nix invocations: two,
one failed evaluation and one successful gate. No full log or derivation
identifier was supplied; no additional hosted-runtime or live-release result is
inferred. Agent Nix and cache-updater invocations remain zero.

Before recording this result, all 314 frozen non-review regular file hashes/modes
and seven deleted-path markers were rechecked against the renewed inventory.
Branch/HEAD, exact Git status and diff membership remain unchanged.
The cached diff is empty and no contents are staged. The review report remains
intent-to-add only. Only this excluded report was finalized; no included input
changed after the owner gate, so no Java rebuild or Nix rerun is required.

J6 implementation and qualification are complete. All changes remain uncommitted
for owner review. Both literal release mutation guards remain disabled; no live
release, activation or subsequent milestone work was performed.

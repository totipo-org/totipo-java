# J0 — Jackson Core 2.21.7 upgrade report

Status: **PASS — J0 complete**. Agent host checks pass; the owner reported the final Nix gate passed.
Classification: **DEPENDENCY_BUILD** only. All changes remain uncommitted.
Evidence directory (local temporary evidence, outside Git source membership):
`/tmp/totipo-j0-f1D95r/`. No sibling repositories or isolated worktrees accessed.

## Starting checkout and policy

**PASS** — read `AGENTS.md` before changes. Started on clean `main` at
`724ea4cf4854a35d19976d6879bb0711457f68cb`; `git status --short` emitted nothing.
Starting branch/status/HEAD: `baseline-git.txt`. Every tracked regular file's
SHA-256 and mode was recorded in `baseline-files.json` before editing.
Gradle wrapper and qualification require 9.8.0; wrapper distribution SHA-256 is
`bafd5ce9cfaea0fbccfdc8439a1ac42fbd4cd9c89dc9a988228d8a2639a58e6c`.
Nix input revisions remain pinned by unchanged `flake.lock`. Host Java is
OpenJDK 25.0.4.1+1. No toolchain changes or agent Nix invocations.

## Baseline tests and resolved dependencies

**PASS** — one `./gradlew test` invocation, 27s; 13 actionable tasks:
2 executed, 11 up-to-date (both test tasks up-to-date). Do not interpret this as
fresh execution of every test. `python3 -B publishing/release.py tests` accounted
for 405 JUnit tests, zero failures/errors/skips, 92/92 portable cases,
snapshot 3/3 and profile 1/1. Logs: `baseline-test.log`,
`baseline-accounting.log`; complete XML preserved in `baseline-xml/` before
focused tests replaced core XML.

**PASS** — baseline resolved graphs captured by
`./gradlew :core:dependencies :storage-nio:dependencies` in
`baseline-dependencies.log`. Core test compile/runtime selected Jackson Core
2.18.2 and Jackson BOM 2.18.2. Core production compile/runtime selected only
BC 1.86. Storage production compile uses local core; runtime adds BC 1.86.
Storage tests/fixtures had no Jackson. Unchanged test dependency versions:
JUnit 6.1.3, OpenTest4J 1.3.0, API Guardian 1.1.2, JSpecify 1.0.0.

## Release identity, compatibility and security review

**PASS** — exact stable `com.fasterxml.jackson.core:jackson-core:2.21.7`
identified through [Maven Central](https://central.sonatype.com/artifact/com.fasterxml.jackson.core/jackson-core/2.21.7),
[upstream 2.21 LTS notes](https://github.com/FasterXML/jackson/wiki/Jackson-Release-2.21)
and [2.21.7 notes](https://github.com/FasterXML/jackson/wiki/Jackson-Release-2.21.7).
Release date: 2026-09-21; module metadata declares status `release`.
No candidate, dynamic version or snapshot selected.

Canonical JAR, POM, module, checksum sidecar and detached signature were obtained
via authenticated HTTPS from `repo.maven.apache.org/maven2/` and retained under
`canonical/`. Independently fetched JAR bytes from `repo1.maven.org` are identical
(these are two hostnames of Central, not independent publishers). The upstream
`jackson-core-2.21.7` GitHub tag POM was separately obtained as `tag-pom.xml`.
Published module size (594782), SHA-256 and checksum sidecar match the actual JAR.
Repository acceptance here follows its existing canonical-artifact/checksum
review standard. HTTPS authenticates the endpoints; checksum pinning establishes
byte identity relative to the reviewed pin, not publisher trustworthiness.
**NOT RUN** — detached PGP signature verification: no GPG verifier available;
downloading the signature and upstream KEYS does not constitute signature
verification. No signing-key trust or reproducible upstream build is claimed.

Published POM parent chain: jackson-base 2.21.7 → jackson-bom 2.21.7 →
jackson-parent **2.21** → com.fasterxml:oss-parent **75**. Jackson base imports
JUnit BOM **5.14.1**, whose property comes from oss-parent 75. This explains
additional metadata acquisition; no JUnit 5 JAR is introduced and Totipo's
resolved JUnit stays 6.1.3. Gradle module variants depend only on Jackson BOM,
with no additional external JAR dependencies. Published shaded parser internals
are part of Jackson's JAR, not a new Totipo dependency.

Base Jackson classfiles are major 52 (Java 8); multi-release entries are majors
53/55/61/65. This is compatible with host Java 25; Totipo still targets Java 17.
The actual focused tests below executed successfully on the pinned host JDK.

Security review: [upstream advisory index](https://github.com/FasterXML/jackson-core/security/advisories)
and release notes inspected. 2.21.7 fixes
[CVE-2026-89407](https://github.com/FasterXML/jackson-core/security/advisories/GHSA-p6pp-m3f8-5c89)
(numeric recognition ReDoS) and
[CVE-2026-89425](https://github.com/FasterXML/jackson-core/security/advisories/GHSA-7hhh-6rmp-j9qf)
(DataInput error-token memory growth). Earlier fixes address name-length,
number-length, document-length and nesting limits. 2.21.7 also fixes UTF-32
surrogate boundary handling and generator escape buffering. The upstream change
list for unreleased 2.21.8 includes further surrogate/parser fixes; this report
does not claim all possible parser issues are eliminated. No security-relevant
compatibility change appeared in the existing focused contracts. Jackson remains
limited to trusted conformance test input; this does not qualify standalone tooling.

## Catalog, locks and strict checksum review

**PASS** — sole catalog edit: `jackson = "2.18.2"` → `jackson = "2.21.7"`.
Alias and `testImplementation(libs.jackson.core)` declaration unchanged.
Used repository maintenance procedure once:

```sh
./gradlew :core:dependencies :storage-nio:dependencies --write-locks --write-verification-metadata sha256
```

**PASS** — generated core lock changes only Jackson Core/BOM to 2.21.7 on test
compile/runtime. Storage and settings locks unchanged. No manual generated-lock
substitution. Log: `update-dependencies.log`.

**PASS** — reviewed all newly accepted records against separately fetched canonical
bytes; audit: `checksum-audit.txt`. Removed obsolete Jackson 2.18.x verification
records, retained unrelated checksums exactly, including oss-parent 61 where
previously reviewed. Added the independently reviewed Core POM pin explicitly
(Gradle generated JAR/module pins). Strict verification and verify-metadata=true
remain enabled. Metadata-only JUnit 5.14.1 records are required by the published
parent import, not an unrelated test framework change.

Canonical SHA-256 pins (full Maven paths derive from the coordinates above):

| Artifact | SHA-256 |
| --- | --- |
| `jackson-core-2.21.7.jar` | `8c5623b98f32d5f7e1287ff61ed50e08ab7b11381e991fff89327faabba42261` |
| `jackson-core-2.21.7.module` | `1584e2d46b3d57454a335dc7c6016282836e5e1f58b8fa14f287b098e37c596d` |
| `jackson-core-2.21.7.pom` | `70233c57a076fadb2f3af1ca25647dc65116d8c135df6c3cb79c95a2560eafce` |
| `jackson-base-2.21.7.pom` | `acf1265a240d27f58fb38fd8b6b2a57f5036bf6f8474e08da2eb626f29367f4c` |
| `jackson-bom-2.21.7.pom` | `ecf645e79390beae614e8f83e42bd3e91a166831c37a3e783f37e67e41939495` |
| `jackson-parent-2.21.pom` | `3851df627faeb46887b956104630b74a8bbb5f7de22e974cf15982e20e0373de` |
| `oss-parent-75.pom` | `fcbbf1c18c9047e6917d384f2064e21b42a58fc85fb0523a9e2e015eff7c619d` |
| `junit-bom-5.14.1.pom` | `01b01dfa366550b40ac5760548a7d728b6109d17c451e83864d1e5e0ce862c94` |
| `junit-bom-5.14.1.module` | `278acb11ccc9998694224386f96fb4941a22edb42cb446c92e0f1f33014b6b48` |

## Focused compatibility and resulting graph

**PASS** — one focused Gradle invocation:

```sh
./gradlew :core:test --tests org.totipo.conformance.VectorCaseLoaderTest --tests org.totipo.conformance.SpecSnapshotIntegrityTest --tests org.totipo.conformance.R19ProfileIntegrityTest --dependency-verification=strict
```

Seven tests executed: loader 3, snapshot 3, r19 profile 1; zero failures/errors/skips.
5s, 10 actionable tasks, 3 executed and 7 up-to-date. XML: `focused-xml/`;
log: `focused-tests.log`. Loader exercises duplicate rejection, exact unsigned
integers, type rejection, string/hex interpretation, malformed/trailing JSON and
floating-number rejection. Profile test loads the entire fixture set and checks
manifest/profile coverage. Snapshot tests preserve pinned content integrity.
No parser, test, fixture or expected-outcome edits were required.

**PASS** — normal strict graph resolution after checksum review:
`./gradlew :core:dependencies :storage-nio:dependencies --dependency-verification=strict`;
`after-dependencies.log`. Core test compile and runtime resolve solely Jackson Core
2.21.7 and BOM 2.21.7. Production graphs and storage graphs are unchanged.
No new runtime dependency, extra JAR, dynamic version or snapshot.

## Human cache checkpoint and complete cache delta

**PASS — human-reported** `nix run .#update-package-deps`: owner reported
BUILD SUCCESSFUL in 1m; 30 actionable tasks, 28 executed and 2 up-to-date;
gradleUpdateScript completed in 1m23s. One human cache-update invocation reported.
This is cache generation evidence, separate from final Nix qualification.

**PASS — agent audit** `cache-audit.txt`: exhaustively downloaded all **92**
cache artifacts from their exact recorded URLs and checked coordinate/version,
canonical bytes and decoded SHA-256 SRI. All **83 retained** records are identical
to baseline. **9 old records removed, 9 new records added**:

| Removed | Added | Artifact kinds |
| --- | --- | --- |
| com.fasterxml:oss-parent:61 | com.fasterxml:oss-parent:75 | POM |
| com.fasterxml.jackson:jackson-base:2.18.2 | com.fasterxml.jackson:jackson-base:2.21.7 | POM |
| com.fasterxml.jackson:jackson-bom:2.18.2 | com.fasterxml.jackson:jackson-bom:2.21.7 | POM |
| com.fasterxml.jackson:jackson-parent:2.18.1 | com.fasterxml.jackson:jackson-parent:2.21 | POM |
| com.fasterxml.jackson.core:jackson-core:2.18.2 | com.fasterxml.jackson.core:jackson-core:2.21.7 | JAR, module, POM |
| org.junit:junit-bom:5.10.2 | org.junit:junit-bom:5.14.1 | module, POM |

All replacements use `https://repo.maven.apache.org/maven2` and match the
reviewed strict verification pins above. Every retained plugin/transitive/parent
record was independently checked too. Existing POM records superseded by Gradle
module metadata lack separate verification pins; these retained POMs match both
baseline SRI and fresh canonical bytes, and no exemptions were added. Initial
audit incorrectly assumed every historical POM had a verification record; that
temporary script failed, then was corrected to account explicitly for these
unchanged POMs. No cache mismatch or qualification failure occurred.

Cache coordinates reconcile with unchanged production/plugin locks and updated
core test locks. Parent/import-only metadata is not represented as JAR dependencies
in Gradle locks. No dynamic/snapshot coordinates, additional JARs or remote local
Totipo artifacts. Fixed-cache architecture and Nix files unchanged. Frozen tracked
inputs were checked unchanged except the authorized package-deps update.

Catalog, core lock and verification metadata are frozen for this checkpoint.
Freeze hashes/modes for all existing tracked regular files:
`cache-checkpoint-freeze.json`; baseline is `baseline-files.json`.

- `gradle/libs.versions.toml`: `97ddf10133f5152dcdd0acc5e6486369b4ec3f6ab85c1e6280e2ad30d242410b`, mode `0o644`.

- `core/gradle.lockfile`: `300646d8a824a5417129ab27fba3dfd9845d54c32f5102e2e20f2e51fbb58e92`, mode `0o644`.

- `gradle/verification-metadata.xml`: `383e4bc19e0b17b635408740f9ebf1681842ae8203a102baab4c831d9ec1b16a`, mode `0o644`.

## Publication boundary and final qualification

**PASS** — source declarations and resolved production graphs unchanged.
Final `verifyPublication` accepted both libraries: core POM contains only BC
1.86 at runtime, storage POM only core 0.2.0 at compile scope. Gradle module
metadata dependency sets match those production boundaries. Verifier compares
exact production classes/sources and Java 17 major/minor 61/0, preventing bundled
test/tooling/Jackson classes. Additional inspection (`production-isolation.txt`)
confirms no Jackson production POM/module declarations or JAR classes. Its first
ad-hoc assertion counted the ordinary `org/` directory entry as a file; corrected
to inspect files, then passed. Existing publication verifier passed throughout.

**PASS** — stable normal Java gate, one invocation:
`./gradlew test build javadoc verifyPublication consumerSmoke`.
42s, 27 outer actionable tasks: 9 executed, 18 up-to-date; core test executed,
storage test up-to-date. Nested module-metadata consumer: 13s, 5 tasks executed;
this is one consumer execution, not another full Java suite.
Logs: `final-normal.log`, `final-normal-accounting.log`.
Immediately accounted for **405 tests**, zero failures/errors/skips,
**92/92 portable cases**, snapshot 3/3, profile 1/1. Full XML and accepted manifest
saved in `normal-evidence/` before the independent offline pass.

**PASS** — existing Python guardrails:
`python3 -B -m unittest discover -s publishing -p 'test_*.py'`, **51 tests**, OK.
Log: `python-guardrails.log`. The unit tests' internal synthetic artifact comparison
is part of existing guardrails; no release artifact reproducibility procedure ran.

**PASS** — `python3 -B publishing/publication.py check`, 10/10 accepted artifact
fresh-byte checks; `normal-manifest-check.log`.
**PASS** — `./gradlew -p publishing/consumer-smoke --offline -PpomOnly clean check`,
5s, 5 tasks executed; `normal-pom-consumer.log`.
Both independent consumers prove compile coordinates are only core/storage 0.2.0;
runtime adds only BC 1.86. Repository-selection checks confirm local staging for
Totipo, no source substitution; no Jackson or test-fixture leakage.

**PASS** — one independent host forced-offline validation justified
by dependency acquisition/cache changes:

```sh
./gradlew --offline --no-daemon --no-build-cache --rerun-tasks --dependency-verification=strict test build javadoc verifyPublication consumerSmoke
```

Log: `host-offline.log`, **58s, 27/27 outer tasks executed**, both module test
tasks executed. Nested module-metadata consumer: 7s, 5/5 tasks executed.
This checks host cached resolution without network or task/build-cache reuse;
it does not replace sandboxed fixed-dependency Nix qualification. Immediately
accounted for **405 tests**, zero failures/errors/skips, **92/92 portable cases**,
snapshot 3/3 and profile 1/1 (`host-offline-accounting.log`). Full XML and accepted
manifest preserved in `offline-evidence/`. Normal evidence was preserved first.
Additional `python3 -B publishing/publication.py check` invocation after offline
staging again passes (`offline-manifest-check.log`); this verifies the current
staging bytes after regeneration, not a repeat semantic gate or reproducibility
comparison. No release ten-artifact comparison.

**PASS — human-reported** — owner reported `nix flake check passed` after the
cache audit, host gates and final input freeze. One final human Nix invocation
reported; no duration, task counts or detailed log supplied. The agent did not
execute Nix. This result qualifies J0; no prior milestone gate is reused.
**PASS — agent verification** — all 278 frozen input hashes and modes match after
the owner's report, and branch/HEAD remain unchanged. Inspected qualification
source filter: core/src, storage-nio/src, gradle, publishing, nix plus enumerated
build/lock/version/pin/license files. Review reports and AGENTS/README/checklists
are excluded. Flake inputs, package-deps and wrapper checks also participate.
**PASS** — final freeze recorded all **278** Git-known qualification source and
flake/cache/lock/wrapper input hashes and modes in
`/tmp/totipo-j0-f1D95r/final-qualification-inputs.json`.
Inventory SHA-256: `b53e3c585ec7799984113995e5ac8069e2b380f3541df7473769e12854cde7d0`.
Changed dependency-input hashes are recorded above; final package-deps SHA-256 is
`ee87ee929d064232e30b0bb91c8aaa4a249ae5f0fad7d85180a9833709f6a879`, mode 0644.
Frozen contents were compared successfully before recording the owner-reported Nix result.
No included input may be edited after freeze.
Only the excluded report may be finalized after the successful human gate.

## Protected invariants and invocation accounting

**PASS** — all 196 protected tracked files under core/src/main,
storage-nio/src/main, core/src/test/resources/totipo-spec plus SPEC_PIN.md and
VERSION retain baseline hashes and modes. Protocol v1/r19, public API, crypto,
production dependency declarations, release controller, verifier, signing policies,
Python safeguards, Nix definitions/toolchains and historical reports unchanged.

| Invocation category | Final count and result |
| --- | --- |
| Broad baseline Gradle test | 1 PASS; test tasks up-to-date |
| Baseline full XML accounting | 1 PASS |
| Dependency graph/maintenance Gradle | 3 PASS: before, generated update, strict after |
| Focused Gradle tests | 1 PASS, 7 tests executed |
| Focused Python tests | 0 NOT RUN (no helper changes) |
| Final normal full Java gate | 1 PASS |
| Final full XML accounting | 2 PASS: normal and independent offline |
| Full Python guardrails | 1 PASS, 51 tests |
| Publication/consumer/POM-only passes | verifyPublication 2 PASS, module consumer 2 PASS (normal/offline nested); POM-only 1 PASS; manifest fresh-byte check 2 PASS |
| Separate host forced-offline passes | 1 PASS; 27/27 tasks executed; justified above |
| Human cache-update invocations | 1 reported PASS |
| Human final Nix invocations | 1 human-reported PASS; frozen inputs independently unchanged |
| Agent Nix invocations | 0 |
| Release reproducibility/sign/publish/tag/push/commit | 0 NOT RUN, outside scope |

No failed Gradle/test attempts or qualification invalidations. A temporary
checksum-audit script initially failed to handle the XML namespace; corrected and
rerun successfully before accepting the checksums. No qualification coverage was
weakened. GPG/OpenSSL/PGPy were unavailable; no signature-verification success is
claimed. No remote CI result is claimed.

## Changed-file inventory and current Git state

Intended changed files: core/gradle.lockfile, gradle/libs.versions.toml,
gradle/verification-metadata.xml, package-deps.json and this report.
No other implementation changes intended. Report uses intent-to-add solely for
source membership; actual staged contents remain empty. Final Git evidence follows after the successful human gate.

**PASS** — final protected hash/mode comparison and whole-checkout inventory:
only the four intended existing dependency files changed and only this report
is newly Git-known. No included input edits after final host qualification.
`git diff --check` and `git diff --cached --quiet` pass. Report intent-to-add
remains; staged-content inventory is empty. No incompatibility was observed;
PGP verification remains explicitly NOT RUN as discussed above. Final Nix is
PASS (human-reported); release actions, J1 implementation and remote CI are NOT RUN.
Only this explicitly excluded report was finalized after the human gate, so the
frozen qualification inputs and successful gate remain valid. Stop after J0.

`git status --short`:

```text
 M core/gradle.lockfile
 M gradle/libs.versions.toml
 M gradle/verification-metadata.xml
 M package-deps.json
 A review/J0_JACKSON_2_21_7_UPGRADE_REPORT.md
```

`git diff --name-only`:

```text
core/gradle.lockfile
gradle/libs.versions.toml
gradle/verification-metadata.xml
package-deps.json
review/J0_JACKSON_2_21_7_UPGRADE_REPORT.md
```

`git diff --cached --name-only`: empty.

`git diff --stat` (including this report's final evidence):

```text
 core/gradle.lockfile                       |   4 +-
 gradle/libs.versions.toml                  |   2 +-
 gradle/verification-metadata.xml           |  44 ++--
 package-deps.json                          |  30 +--
 review/J0_JACKSON_2_21_7_UPGRADE_REPORT.md | 340 +++++++++++++++++++++++++++++
 5 files changed, 388 insertions(+), 32 deletions(-)
```

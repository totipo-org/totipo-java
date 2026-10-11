# Java support-tooling consolidation Wave 2

## 1. Starting state

2026-10-11: clean `main`, HEAD `41f07d0e63f847d1e084b746d75660a922ddcb50`,
tracking origin/main; initial `git status --short` empty. The audit was committed
in `b3bdfa4`; Wave 1 was committed in starting HEAD. VERSION remains `0.2.0`.
SPEC_PIN.md remains v1/r19, upstream
`cdb4e91be1c6d3704874b2b92457ffe7be5e9084`, profile SHA-256
`7242fc557a7ca56452c934bedc5e7dc2835248f5cd36fa220f05588dc967ec80`,
92 cases / 99 upstream files. Neither pin nor version was advanced.

Read AGENTS.md, README.md, RELEASE_CHECKLIST.md, the committed audit and Wave 1
report, root/module build scripts, verifier, release controller/tests,
qualification.nix, flake.nix, both workflows, wrapper checker and consumer build.
No signing, upload, publication, workflow dispatch, tag, staging, commit or push.
The agent did not run Nix.

## 2. Classification

`RELEASE_TOOLING`, `DEPENDENCY_BUILD` (shared Gradle task implementation),
`PUBLICATION_METADATA` (acceptance/evidence plumbing), `CONSUMER_BOUNDARY`
(accepted-hash handoff; consumer implementation unchanged). Also described by the
request's BUILD_DEPENDENCY / TEST_OR_QUALIFICATION_TOOLING categories.
No Java runtime, API, source Javadoc, dependency coordinate, lock, verification
policy, fixed cache, wrapper/toolchain identity or spec change. No dependency
acquisition input changed, so no human cache regeneration checkpoint applies.
This is not release-source preparation and does not authorize a release.

## 3. Old responsibility/call graph

```text
Gradle verifyPublication -> both staging publishers -> verify-publication.py
  -> exact filenames / semantic acceptance / path-to-hash JSON / consumer XML
consumerSmoke -> same verifyPublication task -> isolated module consumer
host final recipe / Nix checkPhase -> direct verify-publication.py again
release inventory -> separate regex/count/SHA schema -> compare/sign/remote
Nix install -> embedded JSON copier -> ten unsigned staged files
workflow -> normal JSON copy -> offline compare -> JSON job output
         -> approved worker rebuild -> signing-input check -> upload
         -> verification worker rebuild -> hosted bytes -> fresh consumers
three release workers -> repeated inline wrapper JAR SHA literal
core + storage-nio -> duplicated loose-class header task implementation
```

The explicit `verifyPublication` and `consumerSmoke` dependency in one Gradle
invocation already execute the verifier task once. That dependency was not
removed. All four release verifier executions observe distinct producer origins:
normal, clean forced-offline, publishing-worker rebuild, verification-worker
rebuild. None was removed.

## 4. Manifest authority before/after

Before: semantic verifier emitted a ten-entry path/hash object; release.py
reimplemented admissible path/suffix/count/hash rules; Nix interpreted the object
in embedded copying code. YAML transported it without semantic parsing.

After: `publication.py` owns canonical artifact identities and strict manifest
parsing. Only `verify-publication.py` calls its acceptance constructor in
production, after the complete staged repository passes all existing semantic
checks. Downstream candidates never define expected filenames. Release uses the
same validated representation for comparison, signing-input identity and remote
paths. Nix delegates copying to that implementation and checks destination bytes.
YAML transports the complete accepted manifest as its existing small job output.
The old generated `publication-sha256.json` is superseded by
`publication-manifest.json`; historical reports were not rewritten.

## 5. Shared schema/model

Schema 1 has exact top-level fields `schema`, `version`, `repositorySha256`,
`artifacts`. Each of exactly ten records has exact fields `module`, `group`,
`artifact`, `version`, `role`, `suffix`, `path`, `sha256`.

Canonical policy generates both modules' binary, sources, Javadoc, POM and Gradle
module metadata names from the requested version. Every record must match its
canonical identity/path/role and contain a lowercase 64-hex SHA-256. Duplicate
JSON fields, duplicate paths, unknown fields, wrong schema/version, omissions
and extras fail. Manifest ordering is not authority; comparisons use path/hash
maps after validating both manifests.

`repositorySha256` fingerprints the complete staged file-path/hash map, including
repository indexes and checksum sidecars. Evidence reuse freshly hashes artifacts
and this complete tree, rejecting later extra files and sidecar/index changes.
This binds reuse to unchanged staging; it is not independent semantic acceptance
or a cryptographic attestation. The fingerprint is deliberately excluded from
cross-build artifact equality because indexes contain timestamps.

Signing maps validated artifact roles to the plugin's actual POM/module input
names; it still reads actual unsigned sign-task inputs and signatures. Remote
verification still downloads and hashes actual hosted bytes. Copying checks the
source staging, independently hashes destination bytes and rejects destination
extras. Source and copied artifacts are not conflated.

## 6. Redundant invocations removed

Two executable recipes replaced their unchanged-byte direct semantic verifier
rerun with `python3 -B publishing/publication.py check`:

- AGENTS.md stable host gate, after Gradle acceptance and Python guardrails.
- qualification.nix checkPhase, after Gradle acceptance and Python guardrails.

Each recipe now performs one semantic acceptance instead of two, with a fresh
unchanged-staging check in the later position. The Nix install transformation
also uses the manifest copier, rather than removing its fresh-byte boundary.
No release rebuild verifier was removed. The development old/new differential
verifier invocations recorded below were deliberate comparison observations.

## 7. Independent checks retained

- Both loose-class Java17 checks and packaged class/header/byte equality.
- Static POM/module shape, actual module-metadata consumer resolution and separate
  POM-only resolution with metadata redirection disabled.
- Strict Gradle verification/locks and separate Nix fixed acquisition/replay.
- Accepted staging, actual signing inputs, independently rebuilt outputs and
  hosted Central bytes; each hashes its own observed bytes.
- Hosted byte verification and fresh Central-only consumers with separate homes.
- Nix qualification and release normal/offline equality.
- Approval/source/tag/release/absence/retry checks at their existing boundaries.
- Full XML accounting before focused overwrites, focused integrity and all
  snapshot hashes in authoritative Nix qualification.

The consumer project/options, CI action-pin guard placement, protected environment,
secrets, job permissions, signing/upload/tag authority and propagation policy are
unchanged. Actual Central/signing boundaries were tested with credential-free
fixtures, not exercised against a release service.

## 8. Bytecode/wrapper disposition

Moved the duplicate bytecode task into existing root `subprojects` conventions.
Both modules retain their explicitly named `verifyJava17Bytecode` task, depending
on `compileJava`, consuming every production class directory and wired to both
`test` and `check`. No buildSrc/plugin infrastructure was added. Packaged checks
remain in the semantic publication verifier.

All three release inline wrapper-JAR hashes now invoke the existing standard-library
`nix/verify-wrapper.py` before any wrapper execution on that worker. It executes
no wrapper and needs no wrapper-produced code. It checks JAR, distribution URL
and distribution SHA-256. The independent flake wrapper check is untouched.
The checker implementation and reviewed identity constants are unchanged.

## 9. Tests added/changed

Added nine manifest behavior tests covering roundtrip/copy, missing/extra/wrong
count, duplicate paths, wrong module/group/artifact/version/path/role/suffix,
malformed SHA, changed/missing bytes, cross-module and cross-role hash substitution,
extra staging files, changed indexes, schema/field/duplicate-JSON rejection,
release comparison of validated evidence and fresh local-byte reads.

Moved the old mocked inventory-mismatch test into real-manifest release comparison
coverage. Extended signing coverage to reject ambiguous duplicate signing outputs,
retaining missing-signature and actual unsigned-input mismatch checks. Existing
remote mismatch/bounded propagation, duplicate/ambiguous upload, source identity,
notes, tag/release safety and preparation negatives remain.
Final discovery: 51 tests pass (20 preparation, 22 release/workflow, nine manifest).
Existing Python 3.14 ResourceWarnings for mocked HTTPError cleanup persist;
there were no Python failures or errors.

## 10. Artifact differential

Saved old accepted bytes and hashes before editing under
`/tmp/totipo-wave2-41f07d0/`. The old verifier accepted this starting publication.
Focused new-verifier, Gradle-publication and final normal checks produced exactly
the same ten paths/hashes. Clean normal and forced-offline results are recorded
below. Exact POM/module byte equality additionally proves their structure and
scopes are unchanged. No semantic artifact output change was needed.

| Exact repository-relative path | SHA-256 (unchanged) |
| --- | --- |
| `org/totipo/totipo-core/0.2.0/totipo-core-0.2.0-javadoc.jar` | `aa0960585fc5bd0c4c5615b754f7d259d9499776e133ca1f8298250af8dcf8df` |
| `org/totipo/totipo-core/0.2.0/totipo-core-0.2.0-sources.jar` | `eee068cd5a700f66cab212ccaa728c7d1f04c981d31648c1e4e90e826d1e8b93` |
| `org/totipo/totipo-core/0.2.0/totipo-core-0.2.0.jar` | `1b628743548c376c2ae85ece2c2f9be43fc7364e13fc0b4d4d25e3d49ee6d864` |
| `org/totipo/totipo-core/0.2.0/totipo-core-0.2.0.module` | `28fcefec7f89ca69b651beb86afb25d07af5933da2f29e8fd5bdb5776b9d227c` |
| `org/totipo/totipo-core/0.2.0/totipo-core-0.2.0.pom` | `1a6bbd5b4d82c079cb621d66c89b446c09e2df33efe5e0e89dd5abfd35d7c136` |
| `org/totipo/totipo-storage-nio/0.2.0/totipo-storage-nio-0.2.0-javadoc.jar` | `ef1b8cf0a278eb64e0cb9073e627debeba298f0fd400378fadfdbf8cd9c7ac7c` |
| `org/totipo/totipo-storage-nio/0.2.0/totipo-storage-nio-0.2.0-sources.jar` | `bb690c98837f938b1cce75cdfc06b44b47b066c4cb937a4ec461f2dc82f5b04b` |
| `org/totipo/totipo-storage-nio/0.2.0/totipo-storage-nio-0.2.0.jar` | `776068249e689e94136748fb8ffd86c837e0af8bbb6cec8ae5e785c4eca4ba93` |
| `org/totipo/totipo-storage-nio/0.2.0/totipo-storage-nio-0.2.0.module` | `33a431575523bb545b57876b09fd04255e04b60b2c64541d72b2cda5f05c7afa` |
| `org/totipo/totipo-storage-nio/0.2.0/totipo-storage-nio-0.2.0.pom` | `4acf1725ac7bb5cd7a4299f729d5eceb82aa3ea524dca7c9d5a9b56616db87b2` |

## 11. Consumer results

Both modes before and after resolve exactly:

```text
compile: org.totipo:totipo-core:0.2.0
         org.totipo:totipo-storage-nio:0.2.0
runtime: same two modules + org.bouncycastle:bcprov-jdk18on:1.86
```

External client compilation, runtime loading and repository-selection assertions
pass. Local Totipo remains exclusive staging and BC remains runtime-only.
Before-change module and POM offline attempts initially failed because host BC
1.86 was not cached. Preserved logs, filled that entry through a normal strictly
verified consumer run, then repeated the POM offline check successfully. No
lock/checksum exception or consumer isolation change was introduced.
Stable normal-gate module and POM consumers pass; each clean release-pair build
also runs the existing isolated forced-offline module consumer. Fresh Central-only
consumers remain wired in the workflow and were not run against a release here.

## 12. Normal/offline release evidence

PASS: ran the checklist's normal `./gradlew clean test build verifyPublication
consumerSmoke`, immediately accounted full XML and saved the manifest/XML outside
build outputs; then ran `./gradlew --offline --no-daemon --no-build-cache
--rerun-tasks clean test build verifyPublication consumerSmoke` and immediately
accounted its XML. Each root invocation reports 30 actionable tasks, all executed;
each nested module consumer reports five executed. Both account 405 JUnit tests,
zero failures/errors/skips, 92/92 portable cases and 3/3 + 1/1 integrity.
The release comparison parser confirms 10/10 normal/offline artifact hashes equal.
All ten also equal the old pre-change publication; POM/module bytes and consumer
graphs match exactly. Normal and offline JSON, XML and logs are preserved under
`/tmp/totipo-wave2-41f07d0/`.

This pair is specifically justified by the changed accepted-manifest format,
release comparison/handoff and shared build convention. It checks clean rebuild
and offline/rerun influences independently of incremental normal qualification.
It is separate from Nix and from any authority to publish. No additional host
forced-offline full suite was added beyond this pair.

## 13. LOC/call-count reduction

Counts use physical lines and the audit's approximate nonblank, non-comment
method; docstrings/braces still count. Counts cover the code/workflow files below, excluding this report and process
prose. `.gitignore` separately adds one configuration line for generated
`__pycache__/` directories created by shared imports in existing non-`-B` callers.
This prevents generated caches from failing release clean-tree checks; it does
not ignore Python source or alter publication artifacts.

| File | Physical before / after | Approx LOC before / after |
| --- | ---: | ---: |
| `.github/workflows/release.yml` | 238 / 238 | 225 / 225 |
| `build.gradle.kts` | 125 / 150 | 111 / 133 |
| `core/build.gradle.kts` | 46 / 22 | 38 / 17 |
| `storage-nio/build.gradle.kts` | 54 / 30 | 45 / 24 |
| `publishing/release.py` | 461 / 465 | 385 / 386 |
| `publishing/verify-publication.py` | 133 / 130 | 121 / 117 |
| `qualification.nix` | 104 / 95 | 97 / 88 |
| `publishing/publication.py` | 0 / 133 | 0 / 98 |
| `publishing/test_release.py` | 284 / 285 | 247 / 249 |
| `publishing/test_publication.py` | 0 / 120 | 0 / 107 |

Shared bytecode conventions remove 20 approximate implementation lines overall;
the embedded Nix copier removes nine; semantic verifier removes four. Release
controller adds one while delegating schema ownership. The new strict manifest
model adds 98 approximate implementation lines: total production support surface
therefore **increases by 66**, rather than achieving a net LOC reduction. Tests
increase by 109 approximate lines. This is the measured cost of explicit accepted
evidence/schema validation, complete-staging invalidation and checked copy reuse;
no language migration or new dependency was used to chase a LOC target.

Semantic verifier executions per host/Nix qualification recipe: **2 -> 1**.
Release rebuild verifier executions: **4 -> 4** across its independent origins.
Inline wrapper identity literal policies in YAML: **3 -> 0**, with three calls to
the existing checker. Loose-class implementations: **2 -> 1**, retaining two tasks.

## 14. Qualification accounting

| Invocation category | Count / result |
| --- | --- |
| Broad baseline Java | 0; focused publication/release baseline instead |
| Focused Python suites | 4: baseline release 23/23; manifest 9/9; release 22/22 twice |
| Focused Gradle Java tests | 0 |
| Focused Gradle publication | 1 successful `verifyPublication` |
| Direct semantic verifier | 2: old baseline and new differential, both successful |
| Stable full Java gate | 1 successful; test tasks up-to-date, see fresh pair below |
| Full Python guardrails | 1 successful, 51/51 |
| Full Java XML accounting | 3: final host, clean normal, clean offline; all successful |
| Additional release-pair broad Gradle invocations | 2 successful; normal and forced offline |
| Total Gradle semantic-verifier task executions | 4: focused, final host, normal, offline |
| Total semantic-verifier process invocations | 6: four Gradle + two direct differential |
| Module consumer invocations | 5: two pre-change attempts (one failed), final host, normal, offline |
| POM-only consumer invocations | 3: two pre-change attempts (one failed), final host |
| Standalone accepted-manifest check / copy CLI | 1 / 1 successful |
| Separate host forced-offline full suite | 1; offline half of justified release pair only |
| Direct wrapper checker | 1 successful; three workflow calls updated, not executed |
| Human Nix reported / agent Nix | 1 PASS reported / 0; agent execution prohibited |
| Actual signing/Central/tag/release actions | 0 |

Host tools: existing JDK 25.0.4.1+1, Gradle 9.8.0, Python 3.14.7.
The small required pre-edit baseline was release/workflow Python (23 tests), not
a broad Java run. Also ran the old verifier and pre-change consumer differential.
Focused post-edit suites: manifest 9/9 once; release/workflow 22/22 twice (before
and after switching signing to role records). No focused Java test overwrote XML.

Stable host sequence uses the revised repository recipe:

```sh
./gradlew test build javadoc verifyPublication consumerSmoke
python3 -B publishing/release.py tests
python3 -B -m unittest discover -s publishing -p 'test_*.py'
python3 -B publishing/publication.py check
./gradlew -p publishing/consumer-smoke --offline -PpomOnly clean check
```

Full accounting reports 405 JUnit tests, zero failures/errors/skips, 92/92
portable cases, snapshot 3/3 and profile 1/1. Final normal Gradle invocation
reports 27 actionable tasks: eight executed, 19 up-to-date; the nested consumer
reports five executed. Up-to-date tests are not represented as a fresh execution;
the clean normal/offline pair separately supplies fresh full-suite execution.
Full accounting and both modules' XML were preserved outside build outputs before
each later clean. There were no failed full Java or Python qualification attempts.
The two failed initial consumer attempts and their justified retries are above.
No included source was edited after the stable Java/Python gates.

## 15. Human Nix status and freeze

**PASS reported by the human** after the frozen-input handoff for the command
below. The agent did not run Nix or independently inspect its logs. All 278
recorded frozen inputs were checked against the saved SHA-256 inventory and remain
unchanged. Only this excluded report was edited after the reported PASS.

The human qualification command was:

```sh
nix flake check path:.
```

Inspected qualification.nix's explicit file/tree source filter and flake.nix's
inputs/check outputs. Gradle files and all publishing Python are included;
qualification.nix itself determines the derivation. AGENTS.md, README.md,
RELEASE_CHECKLIST.md, review/ and .github are excluded from that projection;
workflow pins are separately checked on the full checkout by the Python guard.
The root `.gitignore` is also excluded; its generated-cache rule was added after
host qualification and does not invalidate artifact or Nix source evidence.
Flake lock/inputs, package-deps, wrapper identities and all dependency locks remain
unchanged. No cache regeneration or second Nix build is requested.
Frozen-input inventory saved outside build outputs as
`/tmp/totipo-wave2-41f07d0/frozen-input-sha256.json` (278 files;
SHA-256 of canonical sorted JSON: `366a83457435c1d02c36354138dea5c4ba0930b35c6673a7da06153e269b6bec`).
Static whitespace checks passed for tracked changes and this new report.

Only this excluded report may change to record the human result; later included
input changes invalidate qualification. The human-reported Nix PASS is recorded separately from host evidence.

## 16. Final Git state

Branch remains `main`; HEAD remains
`41f07d0e63f847d1e084b746d75660a922ddcb50`. Index unchanged/empty of changes.
Eleven tracked files modified, two new Python files and this report untracked:

```text
 M .github/workflows/release.yml
 M .gitignore
 M AGENTS.md
 M RELEASE_CHECKLIST.md
 M build.gradle.kts
 M core/build.gradle.kts
 M publishing/release.py
 M publishing/test_release.py
 M publishing/verify-publication.py
 M qualification.nix
 M storage-nio/build.gradle.kts
?? publishing/publication.py
?? publishing/test_publication.py
?? review/JAVA_SUPPORT_TOOLING_WAVE2_REPORT.md
```

All changes are unstaged and uncommitted. No Java production/test files, public
API, spec/corpus, VERSION, dependency locks/checksums/cache, toolchain pins or
consumer implementation changed. No stage/commit/push/tag/publication occurred.
The generated manifest/consumer verification metadata remain build outputs.

## 17. Remaining migration candidates

The semantic verifier remains Python, with existing POM/module/JAR/source/doc
acceptance checks. The release controller remains stdlib Python; preparation,
XML accounting, source/retry/signing/remote/tag boundaries remain deliberate.
The non-executing wrapper checker remains appropriate before wrapper trust.
Further local/workflow recipe sharing or a Java support-language migration needs
its own scope/bootstrapping decision. No Wave 3 work was started.

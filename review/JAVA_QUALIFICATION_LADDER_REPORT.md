# Java qualification ladder report

## Starting state and scope

Starting HEAD: `ce00f0c6db8fb1c81322a7b9e02132d276f63a33`.
The checkout started on clean `main`, tracking `origin/main`, with no staged,
unstaged or untracked files.

Classification recorded before validation: `DOCS_JAVADOC`. Final classification
remains `DOCS_JAVADOC`: only process prose in AGENTS.md, README.md,
RELEASE_CHECKLIST.md and this new report changes. No Java source/Javadoc, tests,
API contract, runtime, build, workflow, publication helper, dependency or Nix
input changes. This is not `RELEASE_TOOLING` or `RELEASE_PREPARATION` work.

Inspected AGENTS.md, API_DESIGN.md (including operation/state-snapshot semantics),
README.md, RELEASE_CHECKLIST.md, qualification.nix, flake.nix, publishing helpers
and independent consumer configuration, CI and protected release workflows.
Also inspected Gradle task wiring and Nix dependency/wrapper helpers to distinguish
nested tasks and qualification inputs. Historical review reports remain unchanged.

## Existing expensive evidence layers

| Layer | Current gate / evidence |
| --- | --- |
| Unit/API/provider tests | `./gradlew test`; focused `:core:test` and `:storage-nio:test` selections. |
| Build and documentation | `build`, Javadoc and source JARs; Java 17 production classfile checks. |
| Publication | `verifyPublication` stages/verifies the ten unsigned artifacts, POM/module scopes, exact contents and consumer verification metadata. |
| Independent consumers | `consumerSmoke` depends on publication verification and runs a standalone forced-offline module consumer; separate `-PpomOnly clean check` exercises Maven POM resolution. |
| Python guardrails | `release.py tests` accounts for full XML, Python unittest discovery exercises release failure paths, and `verify-publication.py` verifies staged bytes. |
| Fixed/offline qualification | CI uses `nix flake check --print-build-logs path:.`; the human routine command is exactly `nix flake check path:.`. |
| Release reproducibility | Protected release preflight retains full normal/forced-offline builds and exact ten-artifact hash comparison. |

qualification.nix currently uses `clean test build verifyPublication consumerSmoke`
with strict verification, rerun/no-cache options and an isolated fixed replay
cache, then runs full accounting, Python guardrails, publication verification,
POM-only consumption, focused snapshot/profile integrity and all snapshot hashes.
Full accounting deliberately precedes focused tests that replace core XML.
flake.nix separately checks the reviewed wrapper identities. Neither definition
nor workflow was edited.

## Durable ladder and inner loop

AGENTS.md now requires classification before validation, with all nine categories:
`DOCS_JAVADOC`, `CORE_RUNTIME`, `STORAGE_NIO`, `PUBLIC_API`,
`PUBLICATION_METADATA`, `CONSUMER_BOUNDARY`, `RELEASE_TOOLING`,
`DEPENDENCY_BUILD` and `RELEASE_PREPARATION`. Multiple applicable categories must
be recorded. README and the release checklist link to the durable policy.

Ordinary Java/API baseline is one `./gradlew test` invocation, without clean,
rerun, offline or publication/consumer expansion. Publication and release-tooling
work adds only the relevant focused baseline. Pure docs/Javadocs use
`./gradlew javadoc`, with focused contract tests only when appropriate.

Iteration selects affected module/API tests, `verifyPublication`, `consumerSmoke`
or specific Python tests. Broad failures are reproduced narrowly, evidence is
preserved outside build outputs, and the affected broad gate is rerun after the
reproduction is stable. Full XML is accounted for before focused tests overwrite
it. Existing nested consumer options remain intact.

The explicit invariant is preserved: never trade final qualification coverage
for faster iteration; move expensive checks to stable boundaries instead.

## Stable final normal Java gate

Runtime/API/publication-affecting milestones use once when stable:

```sh
./gradlew test build javadoc verifyPublication consumerSmoke
python3 -B publishing/release.py tests
python3 -B -m unittest discover -s publishing -p 'test_*.py'
python3 -B publishing/verify-publication.py
./gradlew -p publishing/consumer-smoke --offline -PpomOnly clean check
```

Full test accounting precedes focused tests. The separate POM-only consumer
remains mandatory because module-metadata consumption does not replace it.
Release-helper-only changes use focused Python tests and final full guardrails;
Java/publication effects additionally select the full Java gate.

Source-Javadoc changes require Javadoc and the final normal Java gate once to
qualify packaged sources/docs through existing publication checks. Pure excluded
Markdown process/report edits do not require the full Java gate. This milestone
uses that Markdown-only profile; no executable contract changed and no focused
contract test was needed.

## Nix/offline relationship and release separation

The authoritative routine offline suite already runs inside fixed-dependency Nix
qualification. A second host forced-offline full suite is not automatic. Separate
host offline evidence requires a specific dependency-acquisition, Gradle/build,
publication-resolution, consumer-resolution or fixed-cache change and a recorded
independent purpose.

Before requesting human Nix, freeze all included source and qualification inputs.
The exact human routine command is `nix flake check path:.`. There is no second
build request; materialization of the same unsigned output is not a new gate.
Any subsequent included-input edit invalidates the result.

Normal/offline ten-artifact comparison remains separately mandatory for explicit
release preparation. Routine code/docs milestones do not run it.
`nix build --rebuild` is not a routine milestone command. Protected dispatch,
environment approval, signing/publication, remote-byte/consumer verification and
source tagging remain separate. No release operation was initiated here.

## Invalidation matrix

| Change after qualification | Required action |
| --- | --- |
| Java executable/test/build | Rerun affected final Java gate; included input invalidates final Nix. |
| Javadoc only | Rerun Javadoc and apply source-Javadoc full-gate policy; included source invalidates Nix. |
| Python release helper | Relevant Python tests, stable full guardrails and final Nix if included. |
| qualification.nix / Nix helper / package-deps | Final Nix invalidated; affected build/resolution checks apply. |
| Excluded review report only | No Java rebuild or Nix rerun. |
| Release-checklist prose | No artifact reproducibility rerun unless release operation changed; inspect inclusion for Nix. |

A Markdown edit does not automatically invalidate compiled artifacts. Inclusion
must be checked against both the derivation filter and flake inputs.

## Dependency-update policy

Dependency changes require focused Gradle resolution/update work, then the human
`nix run path:.#update-package-deps` checkpoint. Review every generated coordinate,
version, URL and SRI hash against locks, strict verification and canonical bytes,
including plugins/transitives and parent/BOM metadata. Reject unexpected modules,
dynamic versions and snapshots; Totipo consumers resolve local artifacts from
staging. Next run the final Java gate, freeze qualification definitions/helpers,
cache and included source, and request final human Nix. Unrelated edits do not
regenerate the cache or authorize flake/toolchain updates.

## Execution counts and validation

Count invocations separately from task executions, noting nested publication and
consumer passes and up-to-date tasks. Record failures, invalidations and justified
reruns. The normal Java target is one small broad baseline, one final full Java
gate, zero separate host offline full runs unless justified, and one final human
Nix. The excluded Markdown profile explains the zero full-suite counts here.

| Category | This milestone |
| --- | --- |
| Baseline broad Java tests | 0; docs-only profile. |
| Docs baseline / Javadoc | 1: `./gradlew javadoc`, passed; four actionable tasks all up to date, including both module Javadocs. No regeneration claimed. |
| Focused Gradle tests | 0; no executable contract changes. |
| Focused Python tests | 0; helper code unchanged. Read-only metadata probes below are static validation, not unit-test runs. |
| Final full Java gate | 0; only excluded Markdown changed. |
| Publication / module consumer / POM-only passes | 0 each; publication behavior and artifacts unchanged. |
| Full Python guardrails / full XML accounting | 0 each; no fresh test-suite claim. |
| Separate host forced-offline full passes | 0; no independent resolution/build evidence needed. |
| Human Nix / agent Nix | 0 / 0. |
| Release comparison / release actions | 0 / 0. |

Validation performed:

- `git diff --check` passed, including the final report.
- Local Markdown link targets/heading anchors and paired code fences passed for
  all four changed documents. The operation/state-snapshot guidance in AGENTS.md
  was compared with starting HEAD and is byte-for-byte preserved.
- The read-only `release.preparation_plan(current, current)` probe rejected an
  existing API_DESIGN.md line: `This is the application scheduling model for the
  existing 0.2.0 API. The detailed`. The helper treats this as an ambiguous version
  occurrence. The identical rejection was reproduced using starting-HEAD file
  contents in a temporary directory; this is a pre-existing limitation, not a
  regression caused by the ladder.
- The edited documentation metadata layouts passed the same read-only plan check
  with only that pre-existing API line normalized in memory. No file was changed
  by this probe. Unmodified release preparation is not claimed to pass, and its
  existing incompatibility was left outside this documentation/process scope.

## Preserved security/correctness evidence

The ladder retains strict dependency verification/locks, isolated independent
consumers without Maven-local/composite/source substitution, Java 17 bytecode,
pinned r19 integrity, exact-set 92/92 portable-case accounting without skips,
3/3 snapshot and 1/1 profile tests, and 99 snapshot hashes. Publication checks and
release-only reproducibility/remote verification retain their roles. Existing
operation/state-snapshot validity, presentation relevance and operation-specific
freshness guidance remains intact. These are preserved requirements, not claims
of newly executed full qualification in this prose-only milestone.

## Nix disposition and final Git state

qualification.nix includes `core/src`, `storage-nio/src`, `gradle`, `publishing`,
`nix` and its explicit version/spec/license/build/lock files. Source Javadocs are
included. AGENTS.md, README.md, RELEASE_CHECKLIST.md, API_DESIGN.md and `review/`
are excluded. flake.nix, flake.lock, qualification.nix, package-deps and wrapper
inputs are unchanged. Therefore this diff does not invalidate the qualification
derivation and no final human Nix run is required or requested for this milestone.
No existing Nix success is asserted.

Final HEAD remains `ce00f0c6db8fb1c81322a7b9e02132d276f63a33` on `main`.
AGENTS.md, README.md and RELEASE_CHECKLIST.md are modified and unstaged;
review/JAVA_QUALIFICATION_LADDER_REPORT.md is new and untracked. Nothing is staged
or committed; no historical report, production/test/build/dependency/Nix/workflow
file changed.

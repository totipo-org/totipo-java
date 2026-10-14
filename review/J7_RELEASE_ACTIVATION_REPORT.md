# J7 — Release workflow activation

Status: **J7 correction host qualification and renewed human Nix PASS (owner reported)**.
All changes remain uncommitted. No live release operation is authorized or performed.

The original J7 evidence below is preserved historically. Its WorkflowBoundaryTest
inventory, source freeze and owner-reported Nix PASS are superseded by the correction
section at the end. Active release configuration and capacity prerequisites are unchanged.

## Baseline and scope

Started on clean `main` at `aeb99ff7380623c44965608e4c6bdf6dcd283e85`.
Initial `git status --short` was empty. The committed J6 report is present;
the owner reports committed J6 hosted CI green and GitHub protections configured.
Read AGENTS.md completely, J6, J5B, J4B, J4S and the release checklist, and
inspected current workflows, Java release owners, Gradle and Nix definitions.
Historical implementation decisions are superseded by current source where applicable.

Classifications: RELEASE_TOOLING; DOCS_JAVADOC (Markdown process prose only).
This is activation, not RELEASE_PREPARATION. No dependency/build, library/API,
artifact-producing code, publication metadata, signing algorithm or corpus change.

VERSION remains `0.2.0` plus LF. Specification remains v1/r19 at
`cdb4e91be1c6d3704874b2b92457ffe7be5e9084`, profile SHA-256
`7242fc557a7ca56452c934bedc5e7dc2835248f5cd36fa220f05588dc967ec80`,
99 snapshot files and 92 cases. This existing version is not a test release candidate.

## Source activation and preserved boundaries

Only the two job conditions and their explanatory comments change in workflows:

| Job | Before | After |
| --- | --- | --- |
| Release / publish-central | `${{ false && github.run_attempt == 1 }}` | `${{ github.run_attempt == 1 }}` |
| Verify Release / finalize | `${{ false }}` | `${{ success() }}` |

Release still has only `workflow_dispatch` with required version and exact commit
inputs. Its preflight authenticates main dispatch/SHA equality, exact checkout,
VERSION, reviewed notes, specification, fresh canonical main ancestry and absent
tag/release/Central POMs. Central 404 is a veto check, not publication authority.
Preflight retains exact-source Nix plus normal/offline release qualification,
ten-artifact comparison and both local consumers. `publish-central` needs preflight;
`call-verification` needs both preflight and publication. Verify Release retains
manual dispatch and reusable workflow_call. `finalize` needs successful `verify`,
which checks Central and both independent remote consumer modes.

Signing-before-reservation remains: qualification → unsigned acceptance →
preflight/publisher ten-hash comparison → required credential presence → both real
Gradle Sign tasks → Java signing-input/signature evidence acceptance → producer/Sign
freeze → fresh source/eligibility → first-time annotated tag creation → exact tag
authentication → fresh signed binding → one Gradle Central invocation.
Missing credentials, failed signing or rejected signed evidence cannot consume
a tag. Failed/ambiguous reservation or tag authentication cannot reach upload.

Version concurrency retains cancellation disabled. Both shell attempt checks and
Java reservation reject reruns. Every existing tag rejects ordinary publication,
including a matching tag and a fresh attempt-1 dispatch. SourceIdentity.reserve
uses create-only POST /git/refs, requires HTTP 201 and exact annotated object/
commit/annotation readback; ambiguous creation stops. No update/delete or recovery
route exists. Gradle owns `publishAndReleaseToMavenCentral` with Vanniktech 0.37.0.
At most one authorized Gradle publication invocation per version is claimed;
accepted internal plugin HTTP retries do not imply exactly-once delivery.

Verification independently reconstructs ten unsigned artifacts, verifies complete
Central hashes and all ten detached signature presence contracts, then executes
module-metadata and POM-only consumers in distinct fresh external Gradle homes.
It needs no earlier upload success, artifact, inventory, receipt, publishing/signing
secret or release environment. It cannot upload or reserve/create a tag.
Finalization derives notes/provenance from authenticated source/tag: absent release
is created once and read back; exact existing release is accepted without mutation;
conflicting release is rejected without repair/overwrite. Finalization cannot tag
or invoke Gradle. Existing Java API implementation remains authoritative.

## Permissions, provider assumptions and Nix-first execution

Workflow defaults and preflight/verify are contents:read. Only publisher,
reusable-call permission ceiling and finalizer declare contents:write. Only the
publisher uses environment `release`; only its final runtime step references
Maven/signing secrets. No secrets:inherit or publishing secrets in verification.
Runtime materialization/reconstruction precedes secrets and scrubs credentials
from Nix. No extra approval step or GitHub settings mutation was added.

Owner-confirmed prerequisites, not locally proven guarantees: protected reviewed
main with CI; protected v* updates/deletions without publisher bypass; permitted
first-time tag creation by the workflow token; immutable full-SHA external-action
policy; read-only default Actions permission; main-only release environment;
environment-only Maven/signing secrets without broader fallback. Privileged
effective settings and credential scope were not available for local inspection.
Historical J4S public observations are not current settings evidence.

Shared setup-nix action, exact-source locked flake acquisition and runtime exports
are unchanged. Observed host JDK is OpenJDK 25.0.4.1+1 (Nix); observed Gradle is
9.8.0, revision `a927be5e08efe79e0b87ada06c762dde6bb9f8b8`, using that JDK.
Production target stays Java 17. Nix bootstrap declares 2.35.2 with installer
SHA-256 `9adda97297d9e8ab360df95c729eabff4f4f93d6db091953c3a68f29e3fb130c`.
No Nix executable is on session PATH; no Nix identity command was run.
Host Gradle is `/nix/store/8rx2iqx4xapirr6w0s2q69ycb76yzshr-gradle-9.8.0/bin/gradle`;
JAVA_HOME is `/nix/store/7gph19iic118dil9na8zx0nxp316fx1x-openjdk-headless-25.0.4.1+1/lib/openjdk`.
Release/CI still export explicit TOTIPO_GRADLE from the pinned release-runtime;
no wrapper, second distribution, host fallback or credential-bearing derivation.
External action references remain checkout
`3d3c42e5aac5ba805825da76410c181273ba90b1` and install-nix
`13d8dd58da0234aa297dedd986986ccb8e7f3e24`; no pin scanner/registry was added.

## Permanent regressions and documentation (original J7 inventory, superseded)

WorkflowBoundaryTest now requires the active first-attempt and success-only
conditions, manual release trigger, both verifier entry points and uncancelled
version concurrency. Existing signing/reservation/upload order, freeze wiring,
single invocation, consumer and secret isolation regressions remain. Negative
source fixtures reject unconditional publication, push trigger, cancellation and
unconditional finalization. Actual publishing shell fixtures additionally execute
attempt 2 (zero operations) and upload failure (one invocation, no retry).
No workflow simulator, parser, action-pin registry or native policy reimplementation.
Other permanent input/source/artifact/signing/tag/finalization negatives are unchanged.

Checklist, README and AGENTS disabled-state descriptions now reflect activation
after reviewed commit, manual version/SHA authorization and the separate capacity
gate. Checklist retains verification-only recovery and effective settings review.
No historical report is edited.

## Qualification and source freeze (original J7 evidence, superseded boundary)

Evidence is external at `/tmp/totipo-j7/`. Direct JDK25 main/test compilation uses
--release 25, UTF-8, -Xlint:all -Werror -proc:none, explicit sourcepaths and only
Jackson Core 2.21.7 externally. Its canonical JAR was read from Maven Central,
SHA-256 `8c5623b98f32d5f7e1287ff61ed50e08ab7b11381e991fff89327faabba42261`;
no dependency input changes. Gradle does not compile the independent tooling.

Initial unchanged WorkflowBoundaryTest attempt failed because /bin/bash is absent;
using the installed Nix-store Bash passed. No source correction was needed.
Updated focused WorkflowBoundaryTest passed after activation.
An external evidence-script setup attempt assumed python3, which is absent here;
it exited 127 before creating scripts or running checks. The subsequent attempted
script invocation also exited 127 because that script had not been created.
Plain installed-shell setup corrected this without repository changes. These are
setup failures, not executed Gradle or standalone-suite failures; no new Python
dependency or repository release tooling was introduced.

Final direct compilation and all nine permanent runners listed in nix/tooling.nix
pass: StrictJsonTest (43 assertions), PublicationTest (127), ReleaseTest (46),
AuthorizationTest (144), RemoteVerificationTest, FinalizationTest,
FinalizationProcessTest, TestResultsTest (29 assertions / 26 accounting fixtures),
WorkflowBoundaryTest; Main smoke also passes. Source-dependent final runners use
the same narrow read-only fixture union as Nix (spec, workflows, setup action,
root build and consumer settings). Existing fake transports test source/tag absence,
create-only race/ambiguity, rerun denial, artifact and signing-input substitutions,
remote mismatch/timeouts and exact/conflicting/ambiguous release finalization.

Stable normal Java gate PASS:
`gradle test build javadoc verifyPublication consumerSmoke`, immediate standalone
`release test-results`, `publication check`, then
`gradle -p publishing/consumer-smoke --offline -PpomOnly clean check`.
Normal outer Gradle: 34s, 27 actionable tasks, 8 executed / 19 up-to-date.
Both library test tasks and Javadoc tasks are up-to-date: results are reused,
not claimed freshly executed. Full accounting accepts 405 tests, zero failures/
errors/skips, snapshot 3, profile 1 and the 92/92 exact-set conformance assertion.
Java 17 bytecode checks execute; unsigned local publication accepts ten artifacts,
and the independent manifest check reauthenticates current staging bytes.
Nested module consumer PASS: 10s, four tasks executed; POM-only PASS: 5s, four
tasks executed. Full XML and manifest are preserved in `/tmp/totipo-j7/normal/`.

No artifact-producing code, Gradle/Nix build inputs, publication metadata or
resolution/cache inputs change. Therefore release-only normal/offline artifact
reproducibility, separate host offline full suite, fresh disposable real signing
and actual frozen Gradle producer probes are omitted under the scoped policy.
J6's reported real signing/freeze/ten-hash evidence remains historical evidence
for unchanged implementations, not new J7 execution or a live-release guarantee.
J7 reruns permanent signing-input negatives and actual workflow shell failure/order
fixtures; it reviews unchanged Gradle freeze wiring. The existing full release
preflight retains its normal/offline procedure for any later genuine release.

| Invocation category | J7 count / outcome |
| --- | --- |
| Broad baseline Gradle / focused Gradle tests | 0 / 0; release-tooling baseline uses the relevant standalone runner |
| Baseline standalone main/test compilation | 1 pair PASS |
| Baseline WorkflowBoundaryTest | 2 attempts: absent /bin/bash FAIL; installed Bash PASS |
| Stable standalone main/test compilation | 1 pair PASS; total javac invocations 4 |
| Focused updated WorkflowBoundaryTest | 1 PASS |
| Full final standalone suite | 1 set, nine runners PASS; Main smoke 1 PASS |
| Final normal full Java gate | 1 PASS; cached library XML explicitly accounted |
| Gradle publication acceptance / nested module consumer | 1 / 1 PASS, dependencies in the same full gate |
| Full XML accounting / independent manifest check | 1 / 1 PASS |
| Independent local POM-only consumer | 1 PASS; its required offline mode is not a separate full host offline suite |
| Separate host full offline / release reproducibility pair | 0 / 0; producing and acquisition inputs unchanged |
| New real disposable signing / real frozen publisher probes | 0 / 0; focused permanent fixtures rerun, implementations unchanged |
| Agent Nix / human J7 Nix / cache updater | 0 / 1 owner-reported PASS / 0 |
| Live Central consumers, production signing, tag/upload/release/dispatch/settings mutation | 0 each |

No failed final gate, hidden rerun or included-source invalidation occurred.
Up-to-date tasks and nested consumers are task executions within their listed
invocations, not additional full suites. Synthetic signing/API fixtures do not
count as real signing, Central upload or GitHub mutation.

Inspected qualification.nix's source filter and flake.nix inputs plus tooling.nix's
independent Java/workflow filesets. AGENTS.md, README.md, RELEASE_CHECKLIST.md and
review/ are excluded; changed workflows and tooling Java are included in the
standalone tooling check. Nix helpers, lock and cache still determine qualification.
All included inputs are frozen. Conservative external inventory covers 314
non-review Git-known regular files with modes/SHA-256 (including excluded process
Markdown), `/tmp/totipo-j7/frozen-inputs.txt`, SHA-256
`735f68b2ccd28540a2b88c0d09622478ea994cc853ba10c6071878f349e63ede`.
Post-host recheck matches every entry. Only this confirmed-excluded report was
finalized afterward. Later included-input edits invalidate qualification and any
future human Nix result.

The sole new file, this report, is Git-known through intent-to-add only, established
before stable qualification/freeze. No actual contents are staged;
`git diff --cached --quiet` passes and cached path inventory is empty. No cache,
generated build output, external fixture/evidence or secret entered Git membership.

Dependency/lock/cache accounting: exact Git comparison with starting HEAD confirms
VERSION, SPEC_PIN, library/tests/corpus, module/build/consumer inputs, Gradle locks,
catalog/strict verification, all standalone production Java, Nix helpers, flake
and package-deps unchanged. No new dependency, cache update or toolchain refresh.
Unchanged SHA-256: flake.lock
`44728fcfd8529462cb415b186ee4ce3400343dd911d325b6a965bd386b9f7749`;
package-deps.json `ee87ee929d064232e30b0bb91c8aaa4a249ae5f0fad7d85180a9833709f6a879`;
Gradle verification `383e4bc19e0b17b635408740f9ebf1681842ae8203a102baab4c831d9ec1b16a`.

Human J7 Nix: **PASS, owner reported on 2026-10-14** for `nix flake check`.
One human J7 invocation is reported; no full log or derivation identifier was
supplied. Agent Nix invocations remain zero. No second build or cache checkpoint
is required. This J7 result is separate from the earlier J6 owner Nix/CI evidence.
No live credentials, Central capacity, real tag mutation or release mutation is
tested or inferred from local qualification.

## Central capacity and owner handoff

Before manually dispatching Release, the owner must confirm that the organization's
Maven Central usage and applicable enforcement status permit the intended publication,
or that Sonatype has granted an appropriate adjustment or exemption.
The owner reports capacity exhausted or near exhaustion. Owner-visible authority:
[Usage Center](https://central.sonatype.com/publishing/usage); official
[publishing-limits documentation](https://central.sonatype.org/publish/maven-central-publishing-limits/)
was reviewed read-only. Organization-specific capacity/enforcement was not inspected.
If blocked, wait for confirmed capacity or contact central-support@sonatype.com.
Never infer eligibility from a calendar-month change. Quota rejection occurs after
immutable version reservation and cannot authorize automatic re-upload.
No quota client, polling, disposable upload, override, alternative namespace/account
or retry was introduced or used.

Remaining owner sequence: review/commit J7; require hosted CI;
confirm effective protections and secret scope; confirm Central capacity; only then
explicitly authorize a genuine future version's manual release. Do not dispatch
Release to test activation or repeat it for a verification timeout. Investigate
uncertain publication manually; Verify Release repeats only distribution
verification and exact GitHub finalization.

Live protected-token behavior, production signer/key handling, actual Central
delivery/internal HTTP retries, remote consumers and GitHub finalization remain
unverified. J7 demonstrates source-level activation, not successful live publication.
No repository commit, push, real version tag, production signing, Central upload, GitHub
Release, workflow dispatch or settings mutation occurred. Work stayed exclusively
in totipo-java; no sibling/worktree inspection or sub-agents. Stop after J7.

## Final Git state and complete diff inventory (original J7 handoff)

Branch/HEAD remain `main` / `aeb99ff7380623c44965608e4c6bdf6dcd283e85`.
Both workflow files compare exactly with baseline after applying only the stated
condition/comment replacements; no other workflow line changed. Diff review and
`git diff --check` pass. Complete `git status --short --untracked-files=all`:

```text
 M .github/workflows/release.yml
 M .github/workflows/verify-release.yml
 M AGENTS.md
 M README.md
 M RELEASE_CHECKLIST.md
 A review/J7_RELEASE_ACTIVATION_REPORT.md
 M tooling/src/test/java/org/totipo/tooling/release/WorkflowBoundaryTest.java
```

Complete `git diff --name-only`:

```text
.github/workflows/release.yml
.github/workflows/verify-release.yml
AGENTS.md
README.md
RELEASE_CHECKLIST.md
review/J7_RELEASE_ACTIVATION_REPORT.md
tooling/src/test/java/org/totipo/tooling/release/WorkflowBoundaryTest.java
```

`git diff --cached --name-only`: empty. Intent-to-add remains for owner review/commit.
No other tracked or nonignored untracked paths changed. Source activation and
qualification are complete for owner review; no live publication success is claimed.

## Completed human qualification (original J7 result, superseded)

The owner reported `nix flake check passed` on 2026-10-14. Before recording this
result, all 314 frozen non-review file hashes and modes matched the original
inventory. Branch/HEAD, complete Git status and diff membership also remain as
recorded above. `git diff --cached --quiet` and `git diff --check` pass; no actual
contents are staged, and the report remains intent-to-add only.

Only this confirmed-excluded report was updated after the human gate. No included
input changed, so no Java rebuild or renewed Nix gate is required. All J7 changes
remain uncommitted. Hosted CI on the eventual J7 commit and owner confirmation of
effective provider settings/credentials and Central capacity remain prerequisites
for a separately authorized real release. No release operation was performed.
J7 stops here.

## J7 correction — remove redundant workflow boundary testing

Correction baseline: `main`, HEAD `aeb99ff7380623c44965608e4c6bdf6dcd283e85`,
exactly the original seven J7 status/diff paths above, no unrelated changes and
no staged contents. The report remains intent-to-add only. AGENTS.md was read
completely; its clean-main rule is superseded for this expressly authorized
correction to the existing uncommitted J7 tree. Original host and owner-reported
Nix results were recorded before editing; one focused baseline ReleaseTest also
passed (46 assertions). External evidence: `/tmp/totipo-j7-correction/`.
Classifications: RELEASE_TOOLING; DEPENDENCY_BUILD (Nix test/fixture wiring only);
DOCS_JAVADOC (report prose). No acquisition/cache, library or release behavior change.

WorkflowBoundaryTest is **deleted entirely**, with no replacement. Audit of all
assertion groups and their disposition:

| Former responsibility | Classification / disposition |
| --- | --- |
| Job extraction/names, trigger/condition/needs spellings, permissions, environment, reusable calls and concurrency text | PROVIDER; source review and GitHub own these declarations and their execution |
| Fixed compare/sign/reserve/tag/upload order, secret introduction order, invocation counts and verifier/finalizer command absence | PROVIDER/source structure; removed, operational source reviewed unchanged |
| Producer freeze/dependency/error-message strings and Central consumer settings text | DUPLICATE/OBSOLETE; strings do not execute Gradle, actual qualification covers signing/consumption |
| Consumer command ordering, fresh-home names/counts, strict-option spelling, Git/bootstrap command strings and interpolation conventions | PROVIDER/OBSOLETE source conventions; removed |
| Negative edited-YAML fixtures and appended secret inheritance | PROVIDER; removed with validate/job/count helpers |
| Installer URL/hash text pins and expected step-name extraction | OBSOLETE local convention; reviewed bootstrap remains unchanged |
| Preinstalled Nix, download/checksum failures, failed version/config queries, empty failed query and nonempty effective token shell probes | BEHAVIORAL; unique failure-path probes, removed rather than porting YAML extraction/fakes |
| Fake signing/reservation/tag/upload shell failures, attempt-2 rejection and no upload retry | BEHAVIORAL orchestration probes, partly DUPLICATE of Java reservation/signing-input tests; removed rather than retaining a shell coordinator fixture |
| Precheckout version/output-injection shell cases | BEHAVIORAL, partly DUPLICATE of ReleaseTest input grammar/SHA/main identity; exact shell output emission is left to review/hosted execution |

Deleted all class-local require/job/count/validate/validation/signingOrder/actionRun/
shell helpers, YAML/shell extractors, command fakes and fixture mutations. No other
test calls those helpers. No check is relocated and no production shell code,
workflow scanner, parser, pin registry, simulator or runner abstraction is added.
No focused Bash workflow test is retained. Bootstrap negative paths and shell
failure propagation are meaningful but cannot be preserved here without retaining
source extraction and orchestration fakes. Hosted CI exercises the normal bootstrap;
it does not prove every removed negative case. These failures, including stopping
after failed signing and before reservation, remain explicit source-review/hosted
execution limitations, not claims of equivalent remaining automated coverage.

All other permanent Java tests are byte-identical to HEAD: ReleaseTest covers
malformed version/SHA, initial main/SHA binding, exact VERSION/pin/notes, dirty or
wrong source and missing/stale/unrelated Git references; AuthorizationTest covers
ten-artifact acceptance, manifest substitution/freshness and missing/malformed/
substituted signing inputs/signatures. FinalizationTest covers existing-tag veto,
first-attempt/create-only reservation, races/ambiguity/readback and exact/conflicting/
ambiguous GitHub Release creation. PublicationTest retains essential Maven metadata/
inventory negatives; RemoteVerificationTest retains wrong remote bytes and failed/
bounded acquisition. FinalizationProcessTest retains credential-safe bounded process
execution and API method restrictions. StrictJsonTest and TestResultsTest remain.
Failed real Gradle signing/freeze qualification recorded in J6 is historical evidence
for unchanged behavior; no fresh real signing or production credential test is claimed.

Trust GitHub for triggers, needs/success propagation, permissions, environment access,
action resolution/pin enforcement, concurrency and protected references. Source
review plus actual hosted execution own workflow configuration. Java regressions
test Totipo-specific contracts and supply no independent release authorization.

### Runner cleanup and actual reduction

nix/tooling.nix removes only the deleted runner and its unused fixture inputs.
releaseIntegritySource now contains **SPEC_PIN.md only**, which is the sole external
source file read by ReleaseTest and FinalizationTest. Their remaining fixtures are
generated by the tests. Removed workflow directory, setup action, root build and
consumer settings selections reduce the read-only fixture projection from seven
files to one. Independent JDK25 compilation, Jackson pin, Git executable, all eight
remaining runners, Main smoke and launcher argument checks stay wired into Nix.
CI commands, qualification.nix and flake.nix remain unchanged. Active-caller scan
finds no WorkflowBoundaryTest reference outside historical reports. AGENTS.md and
other active documentation contain no removed runner command, so need no correction.

Compared with the correction's uncommitted starting tree: production Java 0 lines
changed; test Java **one file / 183 physical lines deleted**; Nix configuration
99 → 92 lines (**7 net lines removed**, 2 added / 9 deleted). Total implementation
reduction: **190 lines**, excluding report prose. Relative to committed J6, the
deleted test had 174 lines; overall J7 test/config reduction is 181 lines. No other
production/test file is modified to compensate.

All workflow/action bytes match the correction baseline, including CI. Publisher
`${{ github.run_attempt == 1 }}` and finalizer `${{ success() }}` remain enabled.
Manual dispatch/version/SHA, preflight, signing before reservation, one-shot Gradle
publication, independent verification/consumers, finalization and Nix-first runtime
are unchanged. No actual operational flaw was found or repaired in this correction.
Publication capacity and provider-setting prerequisites above still apply.

### Correction qualification and renewed freeze

Fresh independent main/test compilation PASS: pinned Nix JDK25, --release 25,
UTF-8, -Xlint:all -Werror -proc:none, explicit sourcepaths and only the previously
authenticated Jackson Core 2.21.7 JAR externally. Fresh output directories exclude
stale deleted-test classes. All **eight** remaining Nix-listed runners pass:
StrictJsonTest 43, PublicationTest 127, ReleaseTest 46, AuthorizationTest 144,
RemoteVerificationTest, FinalizationTest, FinalizationProcessTest, TestResultsTest
29 assertions / 26 fixtures. Main smoke passes. Host runner names exactly match
Nix's eight-runner inventory; both source-dependent runners pass against the
one-file SPEC_PIN fixture. Actual fixture/source membership was inspected, not
replaced with a permanent validator. Direct publication check using the freshly
compiled production tooling accepts the existing ten-artifact staging manifest.

| Correction invocation category | Count / outcome |
| --- | --- |
| Focused baseline standalone ReleaseTest | 1 PASS using original independently compiled classes |
| Independent main/test javac | 1 pair PASS (2 compiler invocations) |
| Full final remaining standalone suite | 1 set / 8 runners PASS; includes focused input/tag/finalization and publication/signing tests |
| Main smoke / direct current-staging manifest check | 1 / 1 PASS |
| Retained focused Bash workflow tests | 0; none retained |
| Broad/focused Gradle / new full Java gate / new consumers | 0 / 0 / 0 / 0 |
| Separate host offline / release reproducibility / real signing/freeze probes | 0 / 0 / 0 |
| Agent Nix / renewed human Nix / dependency-cache updater | 0 / 1 owner-reported PASS / 0 |
| Live release/verification dispatch, repository commit/push/tag, production signing, upload/release/settings mutation | 0 each |

No correction compile/test failures, retries or hidden invalidations. Under AGENTS'
release-helper scoped policy, the affected final gate is the full standalone suite
and renewed Nix. This correction changes neither executable/library/publication
behavior nor Gradle producer/resolution inputs, so no new normal Java/publication/
consumer, host offline or release-only reproducibility pass is required. The original
normal gate's 405-test/92-case accounting and successful consumers remain applicable
host evidence; they are reused, not counted as correction executions. Nix still
qualifies the actual full library/publication/consumer boundary. No dependency,
lock, fixed cache, VERSION, specification, production library or release owner changed.

The original owner Nix PASS and 314-file freeze are **superseded** by included test
deletion and Nix wiring edits. The renewed `nix flake check` is **PASS, owner reported
on 2026-10-14**. One correction human invocation is reported; no full log or
derivation identifier was supplied. Total J7 human gates: two reported passes,
the original superseded gate and this renewed correction gate. No second Nix build
or dependency-cache checkpoint is required; agents have run no Nix.

Inspected qualification.nix, flake.nix and revised tooling.nix projections: report/
AGENTS/README/checklist remain excluded. Frozen conservative inventory covers 313
non-review Git-known regular files plus the deleted-test marker, with modes/SHA-256:
`/tmp/totipo-j7-correction/frozen-inputs.txt`, digest
`ed390e849c18e6fbd74d8b5d256994d5f431878c6ae0e8cb1e6dd31231ef8e0f`.
No included input will be edited after this freeze; later edits invalidate it.
Only this existing excluded report is finalized afterward. Intent-to-add membership
remains; no new file/report, generated output, cache, temporary fixture or secret
entered source membership. `git diff --cached --quiet` and `git diff --check` pass.

Final branch/HEAD remain main / `aeb99ff7380623c44965608e4c6bdf6dcd283e85`.
Complete correction-final `git status --short --untracked-files=all`:

```text
 M .github/workflows/release.yml
 M .github/workflows/verify-release.yml
 M AGENTS.md
 M README.md
 M RELEASE_CHECKLIST.md
 M nix/tooling.nix
 A review/J7_RELEASE_ACTIVATION_REPORT.md
 D tooling/src/test/java/org/totipo/tooling/release/WorkflowBoundaryTest.java
```

Complete `git diff --name-only`:

```text
.github/workflows/release.yml
.github/workflows/verify-release.yml
AGENTS.md
README.md
RELEASE_CHECKLIST.md
nix/tooling.nix
review/J7_RELEASE_ACTIVATION_REPORT.md
tooling/src/test/java/org/totipo/tooling/release/WorkflowBoundaryTest.java
```

`git diff --cached --name-only`: empty. The eight intended overall J7 paths are
uncommitted; only the deletion, nix/tooling.nix and this report changed during the
correction. No live operation occurred. Correction host and human qualification
are complete; all changes remain uncommitted for owner review. Stop after J7.

### Renewed human qualification completed

The owner reported `nix flake check passed` on 2026-10-14 for the corrected tree.
Before recording this result, all 313 frozen non-review file hashes/modes and the
deleted-test marker matched the correction inventory. Workflow/action hashes,
branch/HEAD, complete Git status and diff membership are unchanged. Both
`git diff --cached --quiet` and `git diff --check` pass; no contents are staged.
The report remains intent-to-add only.

Only this confirmed-excluded report was updated after the renewed gate. No included
input changed, so no Java rebuild or additional Nix gate is required. Hosted CI
after the owner's eventual J7 commit, effective provider settings/credential
confirmation and Central publishing capacity remain prerequisites for a separately
authorized real release. No release dispatch, tag, production signing, upload,
GitHub Release, repository commit/push or settings mutation was performed.

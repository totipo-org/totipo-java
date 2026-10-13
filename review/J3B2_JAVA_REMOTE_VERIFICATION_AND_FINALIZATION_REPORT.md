# J3B.2 — Java remote verification, finalization and controlled preparation

Status: **PASS — J3B.2 implementation, host qualification and fresh
human-reported Nix qualification.** Python remains authoritative. No live release execution was
qualified. Changes remain uncommitted. Stop after J3B.2; J4 is not started.

Evidence is outside Git membership at `/tmp/totipo-j3b2-evidence/`. PASS below
means agent-observed host evidence unless explicitly attributed to the owner.

## 1. Starting checkout and prerequisite

**PASS.** Work exclusively used `/home/niki/Sources/totipo-java`, clean `main`,
starting HEAD `10260cd21e8fd8efbdd1096e01ffab7c1c6bbd4e` (`Add release tooling
for java`). Starting `git status --short` was empty. J3B.1 implementation and
`review/J3B1_JAVA_RELEASE_AUTHORIZATION_AND_SIGNING_REPORT.md` are committed.
Read that report and the J3A/J2B reports. Owner-reported J3B.1 CI success is
prerequisite evidence only; none of its qualification is reused.

Read AGENTS.md completely before editing; complete Python release controller,
release/preparation/publication tests, publication model, protected workflow,
release checklist, nix/tooling.nix, qualification.nix and flake.nix. Inspected
J3A/J3B.1 source/plan/identity/HTTP/Git/policy/artifact/evidence implementations,
J2B verifier/manifest/repository/CLI/filesystem implementation and their fixtures.
Read API_DESIGN's operation/state-snapshot guidance; application operation
semantics are unchanged. No sibling checkout, worktree or sub-agent was used.
`baseline.json` records all initially tracked regular-file hashes and modes.

## 2. Classifications and preparation-policy interpretation

**PASS — RELEASE_TOOLING and DEPENDENCY_BUILD.** The latter covers standalone
Java compilation/Nix test wiring; dependency versions/acquisition semantics are
unchanged. No dependency cache update is required.

AGENTS.md defines RELEASE_PREPARATION as **“Explicit release-source preparation”**
and separately requires reproducibility for that activity. This milestone
implements and tests preparation on disposable fixtures; it does not prepare the
real source checkout or any release-source commit. Therefore it is not classified
RELEASE_PREPARATION. The separate release-only normal/offline ten-artifact gate
is **NOT RUN**, without deleting or weakening that gate. This matches J3A's
policy distinction. Routine full Java/publication/consumer qualification remains
required and is retained.

## 3. Authoritative Python contract

**PASS — unchanged oracle inspected.** `verify_remote()` freshly checks staging
and compares all ten artifact hashes to EXPECTED_INVENTORY before polling. Each
canonical artifact is downloaded and SHA-256 compared; 404 is pending. Only after
a matching artifact does it request the corresponding `.asc`, requiring both
ASCII armor markers. A missing signature retains the artifact in the pending
set, so its artifact is downloaded again next poll. Completed pairs are removed.
The default deadline is 2,400 seconds; delays are 10, 20, 40, 60, 60… seconds,
clamped to remaining time. A hash mismatch is permanent. Signature presence is
not OpenPGP cryptographic verification or signer-trust certification.

`tag_release()` revalidates identity allowing existing tags, requires established
v0.1.0 annotated/unsigned style, constructs notes plus provenance, and compares
all existing release fields. Body comparison normalizes CRLF and strips trailing
whitespace. A conflicting release is never overwritten. New tags are annotated,
unsigned and target the reviewed commit. Existing correct tags are reused. Push
is non-force; GitHub creation uses `--verify-tag`, exact title and a notes file.
Python configures the bot persistently and redundantly pushes an already valid
remote tag when its release is absent; Java's controlled differences follow.

`prepare()` requires canonical increasing stable versions, exact source integrity,
clean main and validated metadata layouts before writing its owned paths. It
normalizes text-mode newlines, preserves historical prose and existing reviewed
notes, and creates the exact notes skeleton when absent. Same-version prepared
checking is read-only and separately requires completed notes.

## 4. J3B.2 versus J4 and protected authority ordering

**PASS.** No Main/ReleaseInspectionCli or protected workflow change. No Java
operational `remote`, `tag-release`, `prepare-apply`, upload or signing CLI exists.
Seven operational command probes reject with status 1 and preserve a synthetic
GITHUB_OUTPUT sentinel (`cli-probes.json`).

Preflight identity/Central state/release reproducibility/accepted inventory;
post-approval identity/state/rebuild/signing/immediate upload-ready recheck;
Gradle upload; exact remote verification; independent Central-only module and
POM consumers; source revalidation; tag/release finalization remain separate,
in their existing order. Implementing an API does not authorize bypassing any
workflow gate. No independent consumer is modified or executed inside the remote
verifier.

J4 owns workflow activation, live credential/observer integration, transport,
fresh live authorization immediately before upload, accounting replacement,
Python/oracle removal and end-to-end/cross-platform qualification.

## 5. Remote architecture and accepted evidence

**PASS.** RemoteArtifactVerifier takes explicit manifest/staging/version/preflight
manifest inputs and injected Transport, monotonic Clock and Sleeper boundaries.
It calls unchanged J3B.1 AcceptedArtifacts.fresh and unchanged J2B parsing and
checkStaging. All ten canonical identities and the complete current repository
fingerprint must validate before any HTTP. The preflight manifest is independently
parsed and its ten path/hash pairs must equal the freshly checked current pairs.
Repository fingerprints are deliberately not compared between builds; a positive
test varies the expected fingerprint without varying artifact hashes.

An immutable Verified receipt has a private constructor reached only after
complete verification. It cannot be built from unchecked hash lists or a boolean.
Transport is a trusted injected acquisition boundary: its digest represents a
complete bounded response. The concrete implementation is the existing J3A
HttpQueries, extended with a separate canonical-artifact acquisition method.
There is no new HTTP dependency, JSON parser or publication verifier.

## 6. Ten artifacts and ten corresponding signatures

**PASS.** Requests use only accepted Maven-relative paths beneath
`https://repo.maven.apache.org/maven2/`, followed by the identical path plus `.asc`.
The runtime adapter independently restricts scheme/host/port/userinfo/query/
fragment/encoded paths and canonical core/storage version/suffix identities.
Search APIs, arbitrary hosts and redirects are rejected. Every completed pair
requires exact SHA-256 plus both signature markers. No 404 means success.
Verified evidence always contains ten canonical path/digest pairs.

## 7. Response bounds and complete-byte acquisition

**PASS.** Binary/source/Javadoc/POM/module responses have J2B's accepted-file
maximum of **67,108,864 bytes (64 MiB)**. Signatures have the existing J3B.1
**65,536-byte** bound. Current accepted artifact sizes are recorded in
`shadow/artifact-sizes.json`: the largest is the core Javadoc JAR at **4,644,129
bytes**, followed by storage Javadoc at **4,207,479 bytes**. The bound covers all
current reviewed artifacts without raising J2B's limit.

DigestBody incrementally consumes ByteBuffers into SHA-256 without buffering
artifact bodies. Only signatures retain bounded bytes. Content-Length is checked
for early overflow and complete-body consistency, but byte counting remains
independent of absent, understated or misleading metadata. Oversized chunks
cancel the subscription; truncation, transport errors and deadlines fail. No
truncated data becomes successful verification. Request completion, including
body acquisition, is bounded by the smaller of 20 seconds and overall remaining
time; failure cancels the body and asynchronous request.

## 8. Clock, deadline, backoff and deterministic requests

**PASS.** Nano-time elapsed subtraction handles wraparound; backwards clock
observations reject. The overall deadline starts once after local evidence
acceptance and never resets on partial progress. Time is checked before and after
requests and before sleeping. Sleeps never exceed remaining time. A request that
finishes after the overall deadline cannot establish success. Known mismatched
artifact bytes retain PERMANENT_MISMATCH even when acquisition ends at/after the
deadline; a dedicated permanent and differential case covers that precedence.

Pending paths retain manifest order, matching Python. Permanent tests assert
exact request/sleep sequences: immediate availability uses the twenty alternating
artifact/signature URLs with no sleep; first-artifact delay uses 19 initial
requests then its two requests after 10 seconds; signature delay repeats the pair
after 10 seconds; staggered appearance asserts every URL and 10/20/40/60 sleeps.
A missing signature with a 25-second budget sleeps 10 then 15 seconds and stops
before another request. Exported differential traces include every request/sleep
and timeout pending count (`j3b2-parity-frozen/results.json`). No polling test
sleeps or falls back to live HTTP.

## 9. Pending versus permanent/unknown outcomes

**PASS.** VerificationException distinguishes PROPAGATION_TIMEOUT,
PERMANENT_MISMATCH, INVALID_SIGNATURE and ACQUISITION_FAILED, retaining the pending
pair count with static diagnostics. Only 404 is propagation absence. Hash mismatch
is not retried. Invalid armor rejects. 401/403/429/redirect/5xx, connection/TLS
failure, resource overflow and incomplete acquisition fail closed. Unknown/error
responses never become 404. No receipt is returned on incomplete verification.

## 10. Independently constructed release body

**PASS.** Expected.read requires exact spec pin, version and selected reviewed
notes. Its constructor is private. It constructs the Python body independently:

```text
<reviewed notes with Python-compatible trailing whitespace removed>

### Release provenance

- Java source commit: `<reviewed 40-character lowercase SHA>`
- Totipo v1/r19 spec commit: `cdb4e91be1c6d3704874b2b92457ffe7be5e9084`
- Portable corpus: 92/92 executed, none deferred.
```

It emits UTF-8/LF with a final newline. SourceFiles preserves Python universal
newline treatment. Unicode trailing-whitespace cases, CRLF release comparison,
exact notes bytes and provenance all agree with the independent oracle. Existing
GitHub body contents never supply expected provenance or spec/corpus identity.

## 11. Annotated source tags

**PASS.** Read-only inspection of the actual established v0.1.0 shows object type
`tag`, target `1865fd36a05d2153425aa288dc7b17ac80c71a69`, no signature marker and
annotation `totipo-java 0.1.0\n`. No actual checkout tag/configuration was changed.

Local/remote inspected tags require separate object SHA, peeled commit, object
type and exact unsigned annotation. Wrong commits, lightweight tags, missing
peeled/object evidence, unexpected annotations and conflicting local/remote
objects block. New tags are `v<version>` at the reviewed commit with
`totipo-java <version>` annotation and explicit signing disabled. No tag movement,
force update, deletion or force push exists. Runtime push uses the inspected
annotated **object SHA** as the source of its non-force refspec, preventing a
concurrent local tag-reference change from substituting another object.

## 12. Finalization state machine and prerequisite boundary

**PASS.** ReleaseFinalizer separates Expected, Prerequisites, Observed, Decision
and Adapter. Prerequisites require the private-constructor remote receipt plus
two separate successful Central consumer evidence records with distinct
CENTRAL_MODULE_METADATA/CENTRAL_POM modes, the same reviewed version/commit and
the exact ten verified hashes. Fresh observed HEAD, remote main and clean status
are required at each decision, as is the established tag convention.

ConsumerEvidence is an explicit trusted workflow-adapter receipt; its existence
cannot prove a real Gradle invocation. J4 must create it only after the actual
independent Central-only checks succeed, in the required order. J3B.2 supplies
only deterministic evidence. No `verified=true` shortcut substitutes for consumers
or source identity, and the verifier never executes Gradle.

| Observed consistent state | Next action |
| --- | --- |
| No local/remote tag, no release | CREATE_ANNOTATED_TAG |
| Local annotated tag, remote absent | PUSH_EXISTING_TAG |
| Inspected remote tag, release absent | CREATE_GITHUB_RELEASE |
| Exact inspected remote tag and exact release | COMPLETE |
| Incomplete/contradictory evidence | BLOCKED |

## 13. Idempotence, postconditions and interrupted execution

**PASS.** Execution obtains a new observation before planning and after each
successful mutation. A zero exit is insufficient: post-observations must establish
progress or exact completion. No-op commands stop rather than loop. All operations
have a small fixed action budget. Uncertain mutation outcomes stop immediately;
a new invocation must reobserve. API failures during re-observation stop too.

Tests cover tag creation, existing local tag push, existing remote tag without
release, exact completed release with no mutation, all tag/release conflicts,
zero-exit no-op, push success/release failure, and failure before/after possible
tag/push/release success. Later attempts reuse the inspected valid objects.
Complete evidence causes no second push or release creation. Mutation ordering
and exact call counts are asserted. No inconsistent release is overwritten.

## 14. Credentials and subprocess isolation

**PASS.** FinalizationProcesses is a narrow runtime adapter with explicit trusted
absolute Git/gh executables, root, temporary parent, repository and deadline. It
is unreachable from the installed CLI. Observer is separately injected: complete
remote object inspection/source freshness remains that boundary's responsibility.
No arbitrary Git argument or arbitrary live origin API is exposed.

ProcessBuilder executes fixed argument lists without a shell. Environment is
cleared, global/system Git config and replace objects are disabled, hooks,
fsmonitor/untracked cache, credential helpers/askpass and signing are disabled;
HTTP redirects are disabled. Local config is inspected and only a small safe
standard-init/origin allowlist is accepted. Alias/include/helper/transport
configuration cannot silently alter behavior. Reviewed bot name/email are
per-invocation options, never persisted.

Git authentication uses only an ephemeral environment header scoped to the exact
reviewed `https://github.com/<owner>/<repo>.git` URL. No token/header enters argv,
remote URLs, persisted config, logs, derivations or test files. Tests use the
literal synthetic value `synthetic-token`; the fake validates its exact scope and
absence from argv. gh receives GH_TOKEN in its private runtime environment, exact
`--verify-tag --title --notes-file` arguments and a private notes directory/file.

Each process has a positive <=60-second runtime budget (tests use smaller values),
1 MiB stdout and 1 MiB stderr limits, concurrent pipe draining, process/descendant
termination and stream/worker cleanup. Provider/process diagnostics and causes
are discarded. Notes are removed on success/failure/timeout/overflow/malformed
output. Real Git operations only use disposable repositories/local bare remotes;
all gh operations use a strict Java fake executable. No GitHub release was created.

## 15. Controlled preparation application

**PASS.** PreparationApplication.review wraps the unchanged J3A planner; it does
not reimplement metadata rewriting. ReviewedPlan privately captures explicit
normalized root, starting commit, immutable output plan and exact original owned
file/notes bytes. Apply requires that same root/commit, source integrity, clean
main, increasing canonical version, unchanged bytes and an independently
recomputed equal J3A plan before any write.

All destinations are validated and their parents securely anchored before the
first edit. Every component rejects symlinks/path escape/nonregular inputs.
Existing bytes are checked again immediately before replacement. Existing files
use create-new private temporary files and SecureDirectoryStream moves with modes
preserved. Notes use CREATE_NEW. Only J3A's six metadata paths and optional exact
version notes path are written. Existing reviewed notes remain unchanged.

The operation is **not globally atomic**. A failed intermediate write may leave
previous successful edits; errors say so, never report success, and never perform
unsafe automatic rollback. Temporary partial replacements are cleaned. A final
read checks every output byte. Exclusive/frozen source ownership is still needed:
Java offers no atomic compare-and-replace across independent files against an
adversarial writer in the final check/rename window.

## 16. Preparation byte equivalence

**PASS.** Python applies its unchanged prepare() to one disposable source copy;
Java reviews/applies J3A's plan to an independent identical copy. Complete file
byte inventories agree for released baseline, already-prepared baseline, existing
reviewed notes, new notes skeleton, CRLF metadata, historical prose and normal
marker layouts. Unowned files and historical notes are included in inventory
comparison. After completing skeleton TODOs identically, both results pass
Python's existing prepared-source check. Java permanent tests separately exercise
J3A's prepared-source check. Repeated application rejects without edits.

## 17. Python/Java differential outcomes

**PASS — final J3B.2:** **74 comparisons, 6 measured acceptance tightenings, zero
unexplained acceptance or safety-disposition drift**. Exact response bytes,
request/sleep traces, pending timeout counts, mutation ordering/retries, release
body bytes and complete preparation inventories are compared. Detailed cases and
traces: `j3b2-parity-frozen/results.json`.

Preserved historical differential suites also pass: J2B **90 cases/11 documented
tightenings**, J3A **169/18**, J3B.1 **173/12** (`standalone-final.log`). Python was
never changed to obtain agreement. Migration-only additions are
`tooling/completion-parity.py` and Java test-only `CompletionFixture`; mark both
for J4 removal along with the earlier oracle harnesses. Permanent runners require
neither Python nor Gradle implementation classes.

## 18. Intentional safety tightenings

Measured Python acceptance / Java rejection:

1. Acquisition completes after the overall deadline: Java cannot succeed late.
2. Oversized detached signature: bounded acquisition rejects it.
3. Symlinked owned source: Java rejects before application.
4. Conflicting annotated local/remote tag objects: same commit alone is insufficient.
5. Missing peeled remote-tag evidence: no inferred annotated-tag success.
6. Unexpected source-tag annotation: exact reviewed annotation is required.

Other explicit defensive constraints: independent response byte/deadline limits;
truncation/Content-Length consistency; complete remote tag object inspection;
unsigned exact annotation; strict existing J3A JSON field/type/duplicate checks;
monotonic clock enforcement and no request after deadline; secure filesystem and
source-byte revalidation; strict process config/environment/output/runtime bounds;
uncertain mutation stop/reobserve; object-bound push; private temporary cleanup.
Java omits Python's redundant push of an already valid remote tag. The oracle
records that intentional action omission separately from acceptance differences.
No new cryptographic signing/trust requirement or automatic signing is introduced.

## 19. Offline fixtures and negative coverage

**PASS.** RemoteVerificationTest covers immediate, delayed artifact/signature,
staggered propagation, missing signature/deadline, acquisition expiry, mismatch
after 404/progress/at deadline, malformed signature, partial-progress network
failure, all prohibited statuses, signature overflow, 64 MiB artifact-stream
overflow, absent/misleading Content-Length, short/truncated body, cancellation,
nonmonotonic time, unchecked manifests, cross-job fingerprint distinction and
changed staging. Exact sequences are asserted; unknown fake URLs fail.

FinalizationTest covers every required tag/release identity conflict, incomplete
observations, missing/wrong consumer evidence, wrong reviewed source/main/dirty
status, exact/CRLF release, interrupted operations, retries, API failures and
successful-zero-exit without postconditions. FinalizationProcessTest covers
actual annotated bot tag creation/inspection/local-only push, lightweight/missing
inspection, immutable existing tags, object-bound authenticated fake push,
Git/gh failure, timeout/overflow/invalid UTF-8, config rejection, static diagnostics
and private temporary-file cleanup.

PreparationApplicationTest covers canonical-version ordering, dirty/detached/
wrong branch, corrupt pin/snapshot, metadata ambiguity, lock configuration,
missing/nonregular/symlinked paths and parents, path escape/root mismatch, notes
appearance before apply and at CREATE_NEW, changing source bytes, symlink change
during application, repeated application and partial temporary-file write failure.
No tests edit the actual checkout. Read-only fixture inputs and relative-classpath
fake launchers pass, matching sandbox conditions.

## 20. Independent compilation and dependencies

**PASS.** Direct JDK **25.0.4.1+1** javac uses `--release 25 -encoding UTF-8
-Xlint:all -Werror -proc:none` with explicit sourcepaths. Main external classpath
contains only Jackson Core **2.21.7**; tests add only standalone main classes.
The existing canonical fixed-input JAR was acquired for host compilation and
SHA-256 authenticated as
`8c5623b98f32d5f7e1287ff61ed50e08ab7b11381e991fff89327faabba42261`.
This dependency acquisition is separate from release-service testing; no live
Totipo/Central/GitHub release inspection occurred.

No new Java dependency/module/parser, production/Gradle implementation class,
Databind or test framework. Forbidden production/Gradle imports fail under the
restricted classpath. Wrapper compilation remains JDK-only. Runtime tests use
explicit executables and empty PATH/CLASSPATH where applicable.

## 21. Nix tooling integration and selected sources

**PASS — wiring audit and human-reported execution.** nix/tooling.nix retains the
Java-only tooling/src fileset, restricted javac, pinned JDK/Jackson construction,
all previous runners, forbidden imports, smoke/launcher checks and integrity
fixture fileset. It adds the four permanent J3B.2 runners and explicit
`${gitMinimal}/bin/git` for disposable Git tests, plus the existing stdenv shell
for fake launchers. Git is a fixture executable, not a Java compilation dependency.
No real gh executable/authentication enters the derivation.

The existing Java fileset selects all new main/test Java sources, including test
helpers. Host Python migration scripts and review reports are not selected.
The spec fixture selection stays exactly SPEC_PIN plus the reviewed snapshot.
No additional Gradle build, network access or production-derivation semantic
change. qualification.nix and flake.nix are byte-identical; checks.java's J2B
shadow, full/focused accounting, snapshot hashes and both independent consumers
remain active, as does checks.wrapper. package-deps/flake.lock/locks/verification
metadata pins are unchanged. No cache updater or toolchain change.

## 22. Existing J1–J3B.1 preservation

**PASS.** Parser: **43 assertions**; publication: **132 assertions /64 semantic
fixtures**; release inspection: **218 assertions**; authorization: **228
assertions**. Original wrapper suite: **199 fixtures (1 accepted/198 rejected)**,
**190 raw-declaration tests**, direct API/CLI and four extra CLI probes. Canonical
Java and Python wrapper checks pass. Full current spec snapshot hashes are
**99/99 PASS**. New permanent tests and fake subprocesses pass offline.

## 23. Python and protected workflow invariance

**PASS.** All tracked publishing/ files, including release.py, test_release.py,
test_prepare.py, publication.py, test_publication.py and verify-publication.py,
retain exact baseline bytes/modes. release.yml is unchanged. Existing action
permissions, environments, concurrency, approvals, signing and ordering stay
Python-authoritative. No Java call is activated in any protected job. No Nix
installation or live credential integration is added to release jobs.

## 24. Host qualification and invocation accounting

**PASS — focused baseline:** release tests **22**, preparation tests **20**, one
invocation each. No broad Gradle baseline; relevant release-tooling baselines
selected. Full Python guardrails: **51 PASS**, one invocation. Existing mocked
HTTPError cleanup ResourceWarnings are non-failing.

First full normal gate: **37 seconds**, **27 outer tasks (8 executed/19 up-to-date)**;
nested isolated module consumer **10 seconds, 5 executed**. Immediate accounting:
**405 JUnit tests, zero failures/errors/skips; 92/92 portable cases; snapshot 3/3;
profile 1/1**. Full XML preserved as `full-xml/`. POM-only consumer **5 seconds,
5 executed**. Both production test tasks/Javadocs were UP-TO-DATE; this is valid
accounting of retained complete XML, not a claim of freshly executed tests.

A final security audit bound the push refspec to the inspected annotated object
SHA rather than a mutable local tag ref. That included-input edit invalidated the
first Java qualification; the process suite and complete normal boundary were
rerun. Earlier XML/logs are retained. Frozen results follow below.

| Invocation category | Count / outcome |
| --- | --- |
| Broad Gradle baseline / focused production Gradle tests | 0 / 0 |
| Focused Python baselines / other focused Python unit tests | 2 PASS / 0 |
| Standalone main / test javac | 7 / 8 PASS; restricted classpaths |
| RemoteVerificationTest | 5 total: 4 PASS, 1 early stale-class ordering failure |
| FinalizationTest | 3 PASS |
| PreparationApplicationTest | 4 PASS |
| FinalizationProcessTest | 5 PASS |
| J1 / J2B / J3A / J3B.1 permanent runners | 1 / 1 / 2 / 1 PASS |
| Bootstrap smoke / forbidden import compile | 1 PASS / 1 expected rejection |
| Wrapper main / test compilation; full runner; Java/Python canonical checks | 1 / 1; 1; 1 / 1 PASS |
| J2B / J3A / J3B.1 differential | 1 / 1 / 1 PASS |
| J3B.2 differential | 5 attempts: 2 harness failures, then PASS 73, 73, 74 |
| Full normal Java gate / immediate full accounting | 2 / 2; final audit invalidation justified |
| Authoritative verifyPublication / isolated module consumer | 2 / 2, nested in normal gates |
| Full Python guardrails | 1 PASS, 51 tests |
| Python fresh manifest check / independent POM consumer | 2 / 2 |
| Real Java shadow verify / staging check / copy | Frozen totals below |
| Independent Python Java-copy check | 1 PASS, exact ten hashes and file inventory |
| Operational CLI rejection probes / snapshot hash inventory | 7 PASS / 1 PASS (99 records) |
| Separate host forced-offline full suite | 0 NOT RUN; acquisition/resolution/build semantics unchanged |
| Agent Nix / dependency-cache updater | 0 / 0 NOT RUN |
| Human J3B.2 Nix | 1 owner-reported PASS; zero agent invocations |
| Release reproducibility / all real release operations | 0 NOT RUN |

The early remote-test failure used main classes compiled before a request-order
change; recompilation resolved it without weakening expectations. The first
migration failure exposed Python's redundant remote-tag push; the second needed
the missing-peeled-tag safety tightening explicitly recorded. Both harness
adjustments retain the unchanged Python oracle. No unexpected Java compilation,
permanent safety behavior or normal-gate failure occurred. Expected negative
fixtures are successful tests. No focused run overwrote full-suite XML.

Counts distinguish invocations from up-to-date/nested task execution. Each process
runner uses one actual fixture tag creation/local push and one fake credential
push, plus deterministic negative calls; these are not real source-tag mutations
or network pushes. No real GitHub CLI release execution occurs.

## 25. Human Nix qualification

**PASS — owner-reported fresh J3B.2 qualification.** The owner reported
“nix flake check passed” after the included-input freeze. One human invocation
is reported; no detailed log, duration or task/cache counts were supplied and
none are inferred. Agent Nix invocations remain zero. No J3B.1 result is reused.
The reported command is exactly:

```sh
nix flake check
```

**PASS — post-gate audit.** All **339** frozen non-review hashes/modes still
match, all **216** protected files retain baseline bytes/modes, and branch,
HEAD, fourteen-file change inventory and intent-to-add membership are unchanged.
`git diff --cached --quiet` and `git diff --check` pass. Evidence:
`/tmp/totipo-j3b2-evidence/post-human-audit.json`.
Only this confirmed-excluded review report was finalized afterward; no included
input changed or invalidated qualification. No redundant build or dependency-cache
updater was requested. J3B.2's qualification requirement is satisfied.

## 26. Protected byte/mode invariance

**PASS.** All **216** requested protected tracked files retain exact starting
SHA-256 and filesystem modes (`protected-audit.json`): production sources, full
spec snapshot/corpus, SPEC_PIN, VERSION, gradle/, publishing/, package-deps,
flake.lock and release.yml. All other initially tracked files also match except
nix/tooling.nix and HttpQueries.java. Protocol v1/r19, 92 cases, Java17 production
bytecode, JDK25 tooling, Jackson2.21.7, BC1.86, wrapper pins, ten unsigned artifacts,
manifest/fingerprint formats, independent consumer/signing contracts are retained.
No version bump, repin or new Maven artifact.

## 27. Complete intended changed-file inventory

**PASS — 14 files:** two modified existing files; four new implementation files;
four permanent runners; two test helpers; one migration oracle; one report.

- `nix/tooling.nix`
- `tooling/src/main/java/org/totipo/tooling/release/HttpQueries.java`
- `tooling/src/main/java/org/totipo/tooling/release/RemoteArtifactVerifier.java`
- `tooling/src/main/java/org/totipo/tooling/release/ReleaseFinalizer.java`
- `tooling/src/main/java/org/totipo/tooling/release/FinalizationProcesses.java`
- `tooling/src/main/java/org/totipo/tooling/release/PreparationApplication.java`
- `tooling/src/test/java/org/totipo/tooling/release/RemoteVerificationTest.java`
- `tooling/src/test/java/org/totipo/tooling/release/FinalizationTest.java`
- `tooling/src/test/java/org/totipo/tooling/release/FinalizationProcessTest.java`
- `tooling/src/test/java/org/totipo/tooling/release/PreparationApplicationTest.java`
- `tooling/src/test/java/org/totipo/tooling/release/FakeFinalizationCli.java`
- `tooling/src/test/java/org/totipo/tooling/release/CompletionFixture.java`
- `tooling/completion-parity.py`
- `review/J3B2_JAVA_REMOTE_VERIFICATION_AND_FINALIZATION_REPORT.md`

All new intended files receive intent-to-add only. No actual contents are staged.
No generated outputs, cache, temporary evidence or secret is added to source
membership. Freeze hashes/modes and exact final Git state follow below.

## 28. Residual limitations and J4 requirements

**NOT RUN — live release execution.** No signing, upload, real tag/config mutation,
remote push, GitHub release, real-checkout preparation, commit, protected dispatch
or publication was performed. Actual Git mutation tests are fixture-only.

J4 must supply genuine fresh source/main/inspected remote tag/release observations,
real successful Central-only consumer receipts and runtime credentials, preserving
all preceding/following workflow gates. Injected receipts/observers are trusted
boundaries, not proof of live execution. Remote tag presence alone is insufficient;
unavailable full object/peeled/annotation evidence must stop. No automatic fetch,
unsafe tag reconciliation or insufficient-evidence recovery is claimed.

Filesystem application requires SecureDirectoryStream/POSIX support and exclusive
source ownership, cannot atomically transact multiple files, and can leave partial
edits on failure. Runtime mutation executables and temporary parents are trusted;
process cleanup covers observable child trees, not hostile daemonizing executables.
Credential Strings are runtime-only immutable Java values. Strict resource/config/
annotation limits can stop an operation; they never grant release authority.
No independent OpenPGP signature validity/trust is claimed.

## 29. Frozen final qualification and Git state

Final stable normal results, shadow evidence, freeze SHA-256/modes, Git inventories
and staged-content proof are recorded below. Fresh human Nix qualification is
owner-reported PASS, with matching post-gate inputs. All changes stay uncommitted.

**PASS — frozen second normal gate:** **6 seconds**, **27 outer tasks: 8
executed/19 up-to-date**. Nested isolated module consumer: **5 seconds, 5 tasks
executed**. Immediate accounting again confirms **405 tests**, zero
failures/errors/skips, **92/92 cases**, snapshot **3/3**, profile **1/1**.
Both production test tasks and Javadocs remain UP-TO-DATE; no fresh execution is
inferred. Evidence: `frozen-normal.log`, `frozen-accounting.log`, `frozen-xml/`.
Fresh Python manifest check passes ten artifacts; independent POM-only consumer
passes in **882ms, 5 executed** (`frozen-manifest.log`,
`frozen-pom-consumer.log`). No focused production tests replaced the preserved XML.

**PASS — frozen real-artifact shadow:** complete Java semantic verification and
fresh staging check pass; manifest and consumer XML are byte-identical to current
Python evidence (`shadow-frozen.log`, `shadow-frozen/`). Final real shadow totals:
**2 verify / 2 check / 1 copy**, all PASS; one independent Python check confirms
the copied exact ten hashes and file set. First/second ordinary host-gate artifact
hashes agree; this is not release-only normal/offline reproducibility evidence.

**PASS — final source review/membership:** complete implementation/test/harness
and Nix diff reviewed; no unrelated/protected file changes. All twelve new intended
paths, including this report, have intent-to-add membership only. `git diff
--cached --quiet` and `git diff --check` pass. No file contents are staged and no
secret/cache/build output/temporary evidence is Git-known. Branch remains `main`,
HEAD remains `10260cd21e8fd8efbdd1096e01ffab7c1c6bbd4e`.

**PASS — input freeze:** **339** Git-known non-review regular files, a
conservative superset of qualification inputs, have frozen SHA-256/modes in
`/tmp/totipo-j3b2-evidence/frozen-inputs.json`. Inventory SHA-256:
`5aab9c6dcd1dd9bb764caa8c2af2944884e7f875b6f521806f19fa3238f79dc2`.
All included source/tests/Nix/lock/cache/wrapper inputs are now frozen. Only this
confirmed-excluded report may be finalized after the human gate.

Changed non-review hashes/modes:

| Path | SHA-256 | Mode |
| --- | --- | --- |
| `nix/tooling.nix` | `b9b56dfaa59dc52dc402758e047460c4fcbc7ca7daf416ef889566d9727fced3` | `0o644` |
| `tooling/completion-parity.py` | `7545ef17890b3a43611d64ef94a3e232eb2eb66b497ab4d764f66da95b2e58fd` | `0o644` |
| `tooling/src/main/java/org/totipo/tooling/release/FinalizationProcesses.java` | `eb5b38f030e55d925879b68da0cff6b1d39642b1794877c964bb9a24df35d760` | `0o644` |
| `tooling/src/main/java/org/totipo/tooling/release/HttpQueries.java` | `665e73570c6a55efcfb076b44e1597e8d3c8894f50439115b7920d0c95cde6fc` | `0o644` |
| `tooling/src/main/java/org/totipo/tooling/release/PreparationApplication.java` | `5c9d0ea33d1b9a6bbd0ad8da2bd9866c1c0133d6e2481a339359bfb1f7ac3bb4` | `0o644` |
| `tooling/src/main/java/org/totipo/tooling/release/ReleaseFinalizer.java` | `c958229cacadcb4795b01e162513903309ccee76dfbe113ec7e341cad46c36d1` | `0o644` |
| `tooling/src/main/java/org/totipo/tooling/release/RemoteArtifactVerifier.java` | `4d69c0b0246669ce79056e9eb291db5753ed6240560587a3ff503c1c68406aab` | `0o644` |
| `tooling/src/test/java/org/totipo/tooling/release/CompletionFixture.java` | `472cd469282a0e2d84c20e142fa81311f490d7b6c965b62225f99280009e4b96` | `0o644` |
| `tooling/src/test/java/org/totipo/tooling/release/FakeFinalizationCli.java` | `56cd89bd2d99f42cd2cbda8e2ed58ae4c71284ddfad80605c769ed591d3fbfe8` | `0o644` |
| `tooling/src/test/java/org/totipo/tooling/release/FinalizationProcessTest.java` | `e6d29ad2c91e6b44933a2d0b41db0398ad273c7613c3e52a80bc5fa0270d0dcd` | `0o644` |
| `tooling/src/test/java/org/totipo/tooling/release/FinalizationTest.java` | `c51df0c300ccbe183b942b02be316a7c3c5e837084baad49af6dbabd6010e7e4` | `0o644` |
| `tooling/src/test/java/org/totipo/tooling/release/PreparationApplicationTest.java` | `f0b990349d291355398b333f9eb2b113297a46e09157d73605b5b24cc7ee2e28` | `0o644` |
| `tooling/src/test/java/org/totipo/tooling/release/RemoteVerificationTest.java` | `0b6e54cf4325daa40dd9d983abb03cd973239dbb0d712fed9af6e85a42d8cdee` | `0o644` |

`git status --short`:

```text
 M nix/tooling.nix
 A review/J3B2_JAVA_REMOTE_VERIFICATION_AND_FINALIZATION_REPORT.md
 A tooling/completion-parity.py
 A tooling/src/main/java/org/totipo/tooling/release/FinalizationProcesses.java
 M tooling/src/main/java/org/totipo/tooling/release/HttpQueries.java
 A tooling/src/main/java/org/totipo/tooling/release/PreparationApplication.java
 A tooling/src/main/java/org/totipo/tooling/release/ReleaseFinalizer.java
 A tooling/src/main/java/org/totipo/tooling/release/RemoteArtifactVerifier.java
 A tooling/src/test/java/org/totipo/tooling/release/CompletionFixture.java
 A tooling/src/test/java/org/totipo/tooling/release/FakeFinalizationCli.java
 A tooling/src/test/java/org/totipo/tooling/release/FinalizationProcessTest.java
 A tooling/src/test/java/org/totipo/tooling/release/FinalizationTest.java
 A tooling/src/test/java/org/totipo/tooling/release/PreparationApplicationTest.java
 A tooling/src/test/java/org/totipo/tooling/release/RemoteVerificationTest.java
```

`git diff --name-only`:

```text
nix/tooling.nix
review/J3B2_JAVA_REMOTE_VERIFICATION_AND_FINALIZATION_REPORT.md
tooling/completion-parity.py
tooling/src/main/java/org/totipo/tooling/release/FinalizationProcesses.java
tooling/src/main/java/org/totipo/tooling/release/HttpQueries.java
tooling/src/main/java/org/totipo/tooling/release/PreparationApplication.java
tooling/src/main/java/org/totipo/tooling/release/ReleaseFinalizer.java
tooling/src/main/java/org/totipo/tooling/release/RemoteArtifactVerifier.java
tooling/src/test/java/org/totipo/tooling/release/CompletionFixture.java
tooling/src/test/java/org/totipo/tooling/release/FakeFinalizationCli.java
tooling/src/test/java/org/totipo/tooling/release/FinalizationProcessTest.java
tooling/src/test/java/org/totipo/tooling/release/FinalizationTest.java
tooling/src/test/java/org/totipo/tooling/release/PreparationApplicationTest.java
tooling/src/test/java/org/totipo/tooling/release/RemoteVerificationTest.java
```

`git diff --cached --name-only`: empty.

**PASS — J3B.2 complete: implementation, host qualification and fresh
human-reported `nix flake check`, with matching post-gate frozen inputs. PENDING —
none for J3B.2. NOT RUN — all live release operations.** No live release execution
is qualified. Only this excluded report changed after the human gate; included
inputs remain frozen. All changes remain uncommitted for owner review. Python
remains authoritative and J4 remains unstarted. Stop after J3B.2.

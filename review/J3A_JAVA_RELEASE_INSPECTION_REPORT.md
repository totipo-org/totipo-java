# J3A — Java release preparation, identity and remote-state inspection

Status: **PASS — J3A implementation, host qualification, freeze and human Nix qualification.**
Python remains authoritative. No live release operation is qualified. Changes remain
uncommitted. Stop after J3A; no J3B/J4 implementation or cutover is authorized.
Temporary evidence: `/tmp/totipo-j3a-evidence/`, outside Git source membership.

## 1. Starting checkout and prerequisite evidence

Started on clean `main`, HEAD `31917dddd10acb27db4439046481a0ce742c0995`
(`Rewrite publication in java`). `git status --short` was empty. J2B implementation,
tests and `review/J2B_JAVA_PUBLICATION_VERIFICATION_REPORT.md` are committed at
this HEAD. Read AGENTS.md completely before editing and the J0, J1, J2A, J2A.1
and J2B reports. Their historical uncommitted statements describe their own
handoffs; current Git state establishes commitment. J2B CI success is the
owner-reported prerequisite identified in the request; no remote CI query or
J3A qualification is inferred from it.

Work exclusively used this checkout. No sibling repository, isolated worktree,
sub-agent, agent Nix invocation, commit, tag, push, signing or upload.
`baseline.json` records starting SHA-256 and filesystem modes for tracked files.

## 2. Classifications and qualification selection

`RELEASE_TOOLING`: release inspection/planning and independent migration tests.
`DEPENDENCY_BUILD`: standalone Java/Nix check wiring, without dependency changes.
Not `RELEASE_PREPARATION`: no actual release-source preparation on the real
checkout. No runtime/API, protocol, source-Javadoc or publication-schema change.
The focused baseline selected Python preparation and release tests (20 and 22).
The normal full Java gate is retained because standalone/Nix inputs feed the
established publication shadow boundary. No broad Gradle baseline was added.

## 3. Complete current Python release command inventory

The committed `main()` exposes exactly **13** commands:

| Command | Current implementation / J3A ownership |
| --- | --- |
| `inputs` | Operator version/commit/dispatch validation; independently implemented. |
| `identity` | Source/version/clean/pin/notes, fetch/main, tags and release absence; read-only checks ported, fetch excluded. |
| `state` | Central observations plus history, live publish/verify/error policy, writes GITHUB_OUTPUT; Java implements facts only. |
| `inventory` | Fresh accepted-publication manifest and workflow output; existing J2B model retained, not ported here. |
| `compare` | Fresh ten-artifact hashes against expected accepted inventory; existing Python authority/J2B evidence retained. |
| `signed` | Detached signing outputs bound to accepted unsigned inputs; deferred J3B. |
| `remote` | Exact artifact/signature polling and post-publication verification; deferred J3B. |
| `tests` | Full JUnit/corpus/snapshot/profile accounting; existing Python retained. |
| `focused-tests` | Focused snapshot/profile accounting; existing Python retained. |
| `secrets` | Environment/key packet inspection; deferred J3B. |
| `upload-ready` | Live upload authorization recheck; deferred J3B. |
| `tag-release` | Idempotent finalization, tag configuration/creation/push and GitHub release creation; deferred J3B. |
| `prepare` | Applying/dry-run/check source preparation; Java implements planning/dry-run/check only. |

Read all of release.py, test_release.py, test_prepare.py, publication.py,
test_publication.py, release.yml, RELEASE_CHECKLIST.md, AGENTS.md, tooling.nix,
qualification.nix and flake.nix; inspected all J2B Java implementation and tests.
Python files and protected workflow are unchanged.

## 4. Responsibility boundary

J3A owns typed, read-only input/source/preparation/identity observations, release
record decoding, complete bounded prior-attempt history, canonical POM presence
and descriptive remote classifications. It never returns permission to upload.

J3B retains live authorization, retry/recovery policy integration, upload-ready,
signing verification, keys/secrets, propagation polling, post-publication checks,
source-tag mutation, GitHub release creation and finalization. J4 workflow/Python
cutover is deferred. Python alone remains operational in protected release jobs.

## 5. Standalone architecture

Nine cohesive release implementation files under `org.totipo.tooling.release`:
`ReleaseInputs`, `SourceFiles`, `PreparationPlan`, `GitQueries`, `SourceIdentity`,
`RemoteReleaseState`, `HttpQueries`, `RecordedServices`, `ReleaseInspectionCli`.
Records/enums model inputs, immutable plans, tags, releases, jobs, steps, pages,
coordinate presence, prior-upload evidence and descriptive classifications.
Git and HTTP are small injected interfaces; SourceFiles takes an explicit root;
environment input is an injected map. Exceptions represent unknown/error evidence;
no failure is converted into ABSENT or NO_RECORDED_UPLOAD.

## 6. Compilation and dependency boundary

Direct JDK 25.0.4.1+1 `javac --release 25 -encoding UTF-8 -Xlint:all -Werror
-proc:none`, explicit main/test sourcepaths. Main classpath contains only the
existing Jackson Core 2.21.7 JAR; tests add only standalone main classes.
Canonical host JAR SHA-256 independently checked:
`8c5623b98f32d5f7e1287ff61ed50e08ab7b11381e991fff89327faabba42261`.
The existing fixed-output Nix pin/bootstrap remains unchanged. No new dependency,
Databind, production Totipo classes, Gradle classes or test framework.
Negative compilation rejects both production and Gradle imports. Parser,
publication and release tests run with empty PATH/CLASSPATH using explicit Java.
Commands and output: `host-tooling.sh`, `host-tooling.log`.

## 7. Versions and operator input

General input grammar exactly follows Python's ASCII numeric triple and optional
alphanumeric qualifier components. SNAPSHOT suffixes reject case-insensitively.
Commits require exactly 40 lowercase hex digits; dispatch requires main and exact
SHA equality. Missing variables, newline/shell expressions, malformed versions,
uppercase/short commits and mismatched dispatch fail. Preparation separately
requires canonical stable components without leading zeros or qualifiers and
uses BigInteger comparison, never bounded machine-integer version comparison.

## 8. Reviewed notes

Only `review/V<version-with-dots-as-underscores>_RELEASE_NOTES.md` is selected.
Identity requires the exact `## v<version>\n` opening, matching Python.
Preparation additionally requires Highlights, Compatibility and scope, Validation
headings; prepared checking also rejects TODO:. Initial planning may retain an
existing valid notes file with TODOs, or include Python's exact skeleton in its
plan. No notes file was created on the real checkout; no fallback version exists.

## 9. Independent spec and snapshot identity

Expected pins are independent reviewed Java constants matching committed Python:

- Spec commit: `cdb4e91be1c6d3704874b2b92457ffe7be5e9084`.
- SPEC_PIN SHA-256: `736f9489852dcf2d0f22fb13f7da9fcd2dca4de18f8d735fbb34aad6162dedd0`.
- SNAPSHOT inventory SHA-256: `8b8a6660923a425bc68d9bcd3843bd4cc35db642c494c4812f510c0fba7f0905`.

Preparation verifies exact inventory bytes, exactly 99 records and every
referenced fixture checksum. Missing/changed/malformed inventory or records fail;
exact inventory pin rejects unexpected required records. Extra unreferenced files
are not part of Python's source_integrity contract. Identity alone retains
Python's exact spec-only prerequisite; prepared checking supplies full integrity.
No pin update, snapshot regeneration or corpus edit. Source reads reject unsafe
relative paths, symlinks and nonregular files; bounds are 2 MiB text, 64 KiB
pin/inventory, 16 MiB per fixture and 128 MiB total snapshot content.

## 10. Preparation plan and byte equivalence

The immutable plan owns exactly VERSION; checklist title/GAVs/tag/notes path/input
version/status; workflow version example; two exact consumer self-lock entries;
README/API_DESIGN status blocks; optionally the target notes skeleton. Public
construction rejects incomplete/unowned path sets. Immutable strings encode exact
UTF-8 replacement bytes; returned arrays cannot mutate the plan. No apply method.

Initial planning requires clean main, strictly newer stable target, full source
integrity, exact one-slot matches and exact lock configuration ownership. Same
version prepared checking performs no Git query and accepts a detached reviewed
checkout. Released status is retained on same-version check and becomes prepared
for a newer plan. Historical API/prose/examples are preserved. Python text-mode
newline translation is reproduced, including CRLF inputs and LF replacement bytes.

Independent Python applying behavior and test-only Java-plan application operate
on separate disposable copies for released, prepared, existing-notes and CRLF
layouts. Every resulting file byte and complete unchanged-path inventory agrees.
Neither comparison edits the live source checkout. Tests also cover equal/
downgrade/qualifier, dirty/detached/wrong branch, missing/duplicate/stale metadata,
wrong lock identity/configurations, notes and pin/corpus corruption.

## 11. Git identity and tag observations

Preserved ordering: HEAD, VERSION, clean including untracked, exact pin, reviewed
notes, fresh remote-main evidence, remote/local tags, release record. No fetch.
REMOTE_MAIN is an explicit injected observation, never a cached tracking ref.
The CLI labels its evidence recorded and cannot claim a fresh live remote check.
An eventual live integration must establish fresh origin/main before using facts.

Initial inspection rejects all existing tags/releases. Read-only existing-tag
inspection checks exact peeled/local commit identity and never moves a tag.
Local object type distinguishes annotation; remote tag-object and peeled commit
are separately retained. Duplicate/conflicting/unexpected refs and orphan peeled
refs reject. Lightweight and annotated fixtures, wrong HEAD/main/tag, unavailable
main evidence and Git failures are covered. Existing-tag inspection also observes
the release record; this conservative extra read is not finalization policy.

Local ProcessBuilder has a closed read-query vocabulary, 20-second deadline and
1 MiB output bound. Remote queries fail instead of fetching. Optional Git locks,
fsmonitor, untracked cache and hooks are disabled; Git environment redirection and
global configuration are not inherited; no configuration is modified. Tests use strict
fakes; no live repository tags/configuration/refs are mutated.

## 12. GitHub release records

404 means absent only at the version-tag release endpoint; 200 requires a complete
bounded object with exact tag, name/body string fields and draft/prerelease
booleans. Initial identity rejects presence. Wrong/missing fields, malformed JSON
and conflicting identities fail. No create/update endpoint or credential handling.
No final release-body/provenance authorization or tag finalization is implemented.

## 13. Prior-attempt history and pagination

Validated repo/run/attempt inputs construct fixed same-run earlier-attempt URLs.
Every observed job must name the exact reviewed HEAD. Optional API run_id,
run_attempt and job ID fields must be exact positive integers consistent with
query identity; duplicate job IDs/names and upload-step identities reject.
Required steps have explicit name, nullable started_at and conclusion fields.
A started, non-skipped exact upload step means POSSIBLE_UPLOAD even for failure,
cancellation, timeout or null conclusion. Skipped/not-started steps are distinct.

All required pages and attempts are examined, even after upload evidence is found.
Full 100-job pages require a subsequent page. Missing jobs/steps/upload step,
empty earlier history, malformed/contradictory records, later-page errors and
budget exhaustion fail. Optional total_count must agree across pages and with
observed totals. No early stop can establish clean history. Limits: at most 100
current attempt and 100 pages per earlier attempt; bounds never return absence.

## 14. Canonical Central observations

Only actual POM URLs beneath `https://repo.maven.apache.org/maven2/org/totipo/`
for totipo-core and totipo-storage-nio at the requested version. No search API.
Both absent, both present and both partial states are independently retained.
Prior history remains a separate typed observation. POSSIBLE_UPLOAD_NOT_VISIBLE
expressly preserves the two-404 propagation ambiguity; visible coordinates are
never called a fresh publication opportunity. No Java publish/verify policy enum.
Python's live dispositions are reconstructed only inside the differential test.

## 15. Fail-closed ambiguity

HTTP errors, incomplete history, wrong identities, malformed files and resource
budget exhaustion throw errors and do not produce successful observations.
Possible prior upload plus two 404s remains possible prior upload. Present
coordinates without same-run provenance remain present, never upload permission.
No upload-ready API, retry decision, GITHUB_OUTPUT write or release gate is exposed.

## 16. JSON and HTTP bounds

The existing StrictJson/Jackson Core infrastructure decodes the owning schema;
there is no alternate JSON parser or object mapper. J1 and J2B profiles unchanged.
GitHub profile: 4,194,304 bytes, depth 32, string 262,144, member name 128,
numeric length 128, 500,000 tokens. Complete parsing, strict duplicate detection,
UTF-8 without BOM/NUL, exact integer tokens, and unknown-field traversal force
all constraints, including lazy strings. No truncating parse or skipChildren.

Optional anonymous HttpClient adapter: approved HTTPS hosts/paths only, no userinfo,
fragments/encoded path substitutions or arbitrary endpoints; 20-second connect,
request and whole-body deadline; redirects NEVER. BodySubscriber cancels and fails
on acquisition overflow (GitHub 4 MiB, Central POM 64 KiB). Only 200/404 are
accepted; 401/403/429/5xx, redirects, timeout and transport failure are errors.
No token parameter, credential/header logging or response-body diagnostics.
Jackson errors are replaced with static endpoint-type diagnostics to avoid
leaking malformed response tokens. The adapter was never invoked against live
release services during qualification. Transport/subscriber tests need no network.

## 17. Deterministic test boundaries

ReleaseTest is JDK/Jackson-only and offline. Git enum fakes and URI-map HTTP fakes
reject every unknown request. RecordedServices reads explicit Git-query files and
SHA-256-of-URI keyed status/body files; missing fixtures have no fallback process
or network route. Filesystem corruption and plan application occur only in fresh
temporary directories. Nix read-only fixtures are copied and made writable only
in those disposable directories. Environment maps and source roots are explicit.
ReleaseFixture is a test-only oracle bridge, excluded from installed main classes.

## 18. Differential results

Host migration runner: tooling/release-parity.py. It imports unchanged Python,
simulates its fetch/HTTP calls with strict fixtures and invokes independently
compiled Java tests. It does not alter Python or use release credentials/network.
Final frozen run: **169 cases; 18 intentional tightenings; zero
unexplained acceptance/safety drift** (`parity-review.log`, `parity-review/results.json`).
Successful paginated history/release outcomes are compared as typed values, not
only accepted/rejected status; combined state dispositions also agree.
Four complete resulting-fixture byte/inventory comparisons pass. Operator/version,
preparation/check/notes/integrity, local/remote identities/tags, releases/history,
100-job/multiple pages and later failures, coordinate combinations/prior uploads,
unexpected HTTP and unavailable transport are compared. Permanent Java tests
add each resource bound, acquisition cancellation, pagination-budget exhaustion,
unknown fake requests and credential-free diagnostics.

## 19. Intentional tightenings

Measured Python ACCEPT / Java REJECT cases are listed in the final results JSON:
orphan peeled/duplicate tag refs; missing/mismatched/duplicate release JSON fields;
empty/missing publishing history; missing steps/upload step/step fields; wrong
optional run/attempt identity; duplicate jobs/steps/JSON; later-page failure after
upload evidence; contradictory/incomplete total_count. These prevent uncertainty
from becoming clean history and do not introduce any upload authorization.
Additional defensive bounds/types, unknown conclusion rejection, strict JSON,
symlink/path restrictions, duplicate IDs and complete later-attempt inspection
are conservative constraints. No Java acceptance broadening is intended.

## 20. CLI and side effects

`totipo-java-tool release` supports only:

```text
inputs
prepare-dry-run <root> <version>
prepare-check <root> <version>
inspect-identity <root> <recorded-evidence-directory>
inspect-state <recorded-evidence-directory>
```

Unknown commands, empty/malformed arguments and all mutation/authorization names
fail. Inspection prints that publication is not authorized. Remote CLI commands
are explicitly offline evidence inspection. Prepare commands only read source;
there is no prepare-apply command. No GITHUB_OUTPUT write. Sixteen actual Main
CLI probes pass with unchanged disposable source bytes and GITHUB_OUTPUT sentinel.

## 21. Nix integration and input selection

Only nix/tooling.nix and Main.java are modified existing files. The original
Java-only tooling/src fileset automatically includes all new Java implementation
and test sources. checks.tooling now executes ReleaseTest. Its separate narrowly
selected integrity fixture fileset contains SPEC_PIN.md and exactly the existing
v1-pre-rc snapshot tree, as read-only inputs. It does not copy production classes,
Gradle outputs, root documentation, workflows, reports, caches or credentials.
Synthetic owned-metadata fixtures stay in the Java test source. No new Gradle
build, package-deps regeneration, dependency or Nix network access.

Inspected unchanged qualification.nix source filter and flake.nix inputs.
Existing Java, wrapper, J1/parser and publication checks remain active. review/
is excluded from all derivations; tooling/release-parity.py is host-only.
Included inputs are frozen after complete diff/source-membership review, as
recorded below.

## 22. J2B preservation

No J2B class, schema, parser profile, tests, publication verification or consumer
logic changed. Existing tests pass: J1 43 assertions; J2B 132 assertions and 64
exported fixtures; J2B independent parity 90 cases, 11 documented tightenings,
zero unexplained drift. Real staging accepted by Python is independently
verified by Java; complete manifest and consumer XML compare byte-for-byte.
Java fresh staging check/copy pass and independent Python checks ten copied
artifacts and exact file inventory. No accepted-publication schema is forked.

## 23. Python/workflow authority

All publishing/, release.yml, qualification.nix and flake.nix bytes/modes remain
unchanged. Protected permissions, approvals, signing, secret boundaries,
retry/recovery and release controller invocations remain Python-owned. No live
GitHub/Maven Central inspection was performed. Downloading the already reviewed
Jackson JAR for host compilation is dependency acquisition, not release inspection.
No trial publication, signing, tag creation/push, GitHub release or finalization.

## 24. Host validation and execution accounting

Focused baseline: preparation 20 PASS and release 22 PASS. First normal Java gate:
37s, 27 outer tasks, 8 executed/19 up-to-date; module consumer 10s, five executed.
Immediate accounting: 405 tests, zero failures/errors/skips, 92/92 cases,
snapshot 3/3 and profile 1/1. XML and accepted manifest preserved outside build.
Full Python guardrails 51 PASS (existing synthetic HTTPError cleanup warnings
are non-failing); fresh manifest check ten artifacts PASS; POM consumer 4s,
five tasks executed. Real Java shadow verification/check/copy PASS.

Final audit found Jackson diagnostic text could expose response tokens. Sanitized
release/jobs parsing diagnostics and added two regression assertions. Also
rejected null coordinate/history observations in the public state record, with
two additional assertions, so missing facts cannot classify as absence. That
included-input edit invalidated the first final gate; the affected normal
boundary and standalone release/parity checks were rerun successfully, as
recorded below. No validation coverage was removed; no unexpected failing test attempt.
Full Python guardrails need no repeat because Python files did not change.
Final invocation counts and frozen results are recorded below.

## 25. Human qualification

**PASS — human-reported J3A `nix flake check`. Agent Nix invocations: zero.**
The owner reported: “nix flake check passed”. One fresh human invocation is
reported; logs, duration and task counts were not supplied and are not inferred.
After this report, the agent independently rechecked all 318 frozen file hashes
and modes, starting HEAD, main branch and absence of staged file contents: PASS.
Evidence: `post-human-freeze-audit.json`. J2B qualification was not reused.
No redundant build or cache update was requested. Only this excluded review
report was finalized afterward; included qualification inputs remain frozen.

## 26. Protected invariance

Audit compares baseline SHA-256 and filesystem modes for core/src/main/,
storage-nio/src/main/, core/src/test/resources/totipo-spec/, SPEC_PIN.md, VERSION,
gradle/, package-deps.json, flake.lock, publishing/ and release.yml. Final results
are appended below. Also inspect all other existing tracked files: only the two
explicitly intended existing files may differ. Java 17 production bytecode,
JDK25 tooling, Jackson2.21.7, BC1.86, r19, all92 portable cases and ten unsigned
artifacts remain under existing qualification checks. No new release notes.

## 27. Changed-file inventory and freeze

Final intended inventory, Git-aware intent-to-add membership, empty staged-content
proof, hashes/modes and exact Git status are recorded below. All modifications
remain uncommitted; intended new files have intent-to-add membership only.

## 28. Remaining J3B/J4 work and limits

Live authorization and recovery remain unimplemented, including upload-ready,
accepted signing-output checks, secrets, propagation and finalization. Recorded
identity evidence is not live freshness proof; eventual integration must obtain
fresh read-only remote-main/tag observations without trusting cached refs.
Anonymous HTTP permission/rate-limit errors fail; private-repository/token
support would require separate review and is not silently enabled. Resource
budgets and strict incomplete-history rejection can stop an inspection; they
never authorize retry. Filesystem inspection assumes a frozen checkout during
observation, not an atomic snapshot against concurrent filesystem rewrites.
Python remains authoritative; no live release execution safety claim follows.

## Frozen host results, invocation accounting and final handoff

**PASS — agent-executed final qualification.** Second full normal gate: **6s**,
**27 outer tasks: 8 executed, 19 up-to-date**; nested module consumer **5s**,
**5/5 tasks executed**. Both production test tasks were up-to-date: full XML was
accounted for, without claiming every test freshly executed. Immediate accounting:
**405 tests**, zero failures/errors/skips, **92/92** portable cases, snapshot
**3/3**, profile **1/1**. Preserved XML: `frozen-xml/`; accepted manifest:
`frozen-publication-manifest.json`. Logs: `frozen-normal.log`,
`frozen-accounting.log`. No focused production tests overwrote this evidence.

**PASS — fresh acceptance/consumers/shadow.** Python manifest fresh-byte check
passes ten artifacts; independent POM-only consumer **1s, 5/5 tasks executed**.
Final Java verification independently accepts the same real staging; complete
manifest and consumer XML bytes match Python; Java fresh staging check passes.
Logs: `frozen-manifest-check.log`, `frozen-pom-consumer.log`, `frozen-shadow.log`.
The earlier normal gate's Java copy was independently checked by Python for all
ten exact hashes and file paths. First/final normal-gate ten-artifact hashes
match (`ordinary-artifact-invariance.json`); this is ordinary invariance evidence,
not the separate release normal/offline reproducibility procedure.

**PASS — standalone final release runner:** **218 assertions**, including static
response diagnostics and rejection of null observations, under empty PATH and
CLASSPATH with read-only fixture inputs (`release-frozen.log`). Earlier read-only
fixture test also passed; original fixtures retain their read-only modes.
Final parity: **169 cases, 18 documented tightenings, zero unexplained drift**.
Final host-only oracle review strengthened successful history/release value
comparison; this excluded Python runner change affects neither Java artifacts
nor Nix source projections and was validated by a fresh differential invocation.

| Invocation category | Count / result |
| --- | --- |
| Broad baseline Gradle test suite | 0; focused release-tooling baseline selected |
| Focused Python baseline tests | 2 PASS: preparation 20, release 22 |
| Focused production Gradle tests / other focused Python unit runs | 0 / 0 |
| Direct main / test javac | 7 / 6 PASS, restricted classpaths |
| New standalone release runner | 7 PASS: 189, 212, 212, 214, 214, 216, 218 assertions |
| J1 parser / smoke / forbidden-import compilation | 1 / 1 / 1 PASS; forbidden compilation fails as expected |
| Existing J2B publication runner / differential | 1 / 1 PASS: 132 assertions, 64 semantic fixtures / 90 comparisons |
| J3A differential suites | 6 PASS: 165, 167, 169, 169, 169, 169 cases |
| Actual Main CLI probes | 16 PASS: four accepted, twelve expected rejection statuses |
| Full normal Java gates / immediate full accounting | 2 / 2 PASS; final audit invalidated the first gate |
| Authoritative verifyPublication / module consumer | 2 / 2 PASS, nested in full normal gates |
| Full Python guardrails | 1 PASS: 51 tests; unchanged authoritative Python |
| Python fresh manifest check / independent POM-only consumer | 2 / 2 PASS |
| Real Java shadow verify / staging check / copy | 2 / 2 / 1 PASS; two byte-evidence comparisons |
| Independent Python check of Java copy | 1 PASS, ten hashes and exact file inventory |
| Separate host forced-offline full suite | 0 NOT RUN; acquisition/build/resolution/cache behavior unchanged |
| Dependency-cache updater / agent Nix | 0 / 0 NOT RUN, neither authorized |
| Human J3A Nix | 1 human-reported PASS; zero agent invocations |
| Release reproducibility, live service inspection, signing, upload, tag, release, commit, push | 0 NOT RUN |

Counts describe invocations, not repeated task execution. Nested consumers are
not extra full Java suites. Existing consumers retain their isolated forced-offline
options. Initial pinned Gradle distribution acquisition occurred in the normal
gate; it did not alter locks/cache source inputs. No failed test/build attempt,
no dependency-cache regeneration and no weakened checks. The final-audit
security edits explain the one full-gate rerun; earlier host evidence is retained.

**Measured conservative differences (Python ACCEPT / Java REJECT):**

```text
identity:tag:peeled-only:allow
identity:tag:duplicate:allow
release-record:missing
release-record:wrong-version
release-record:duplicate
history:empty
history:missing-job
history:missing-steps
history:missing-upload
history:missing-step-fields
history:wrong-run-id
history:wrong-attempt
history:duplicate-jobs
history:duplicate-steps
history:upload-then-later-page-failure
history:duplicate-json
history:incomplete-total-count
history:contradictory-total-count
```

**PASS — protected invariance:** all **216** requested protected tracked
files retain exact starting SHA-256 and filesystem modes. All other existing
tracked files likewise match except nix/tooling.nix and Main.java. No protected
production/protocol/publication bytes, workflow, lock, pin, VERSION, publishing
helper, historical report or release note changed. Audit: `protected-audit.json`.

**PASS — input freeze:** **318** Git-known non-review regular files,
a conservative superset of all qualification inputs, are recorded with SHA-256
and modes in `/tmp/totipo-j3a-evidence/frozen-inputs.json`.
Inventory SHA-256:
`c3cb8fdfa0b398f6bf676a4e85a56b0a49c5ad32ef89bf000cf479da2ba6b538`.
No included input may change after this freeze without invalidating qualification.
Only this confirmed-excluded review report may be finalized afterward.

Changed non-review input hashes/modes:

| File | SHA-256 | Mode |
| --- | --- | --- |
| `nix/tooling.nix` | `fc6aae4e0b046c0f60393c91d471df33f7ceff2a7c6cf391a6d2d480e4b7a65f` | `0o644` |
| `tooling/release-parity.py` | `dc98d79b208401426b34f7400952f60b1ccd0b16770c616be7cdc8f183e79096` | `0o644` |
| `tooling/src/main/java/org/totipo/tooling/Main.java` | `22390e0218dabef6f62dee2e14d301f6cf44333d454472bdb6ae095dfad90cd6` | `0o644` |
| `tooling/src/main/java/org/totipo/tooling/release/GitQueries.java` | `719342a5fef5f5e0d7bd77821b1bc4fb2861defc5ffe90482e5418d5d0650e5c` | `0o644` |
| `tooling/src/main/java/org/totipo/tooling/release/HttpQueries.java` | `24c93470144a6ac534ddb2abf93306004971c9ace8481e52ea46e58e65248231` | `0o644` |
| `tooling/src/main/java/org/totipo/tooling/release/PreparationPlan.java` | `e7b868672a72e5c1d658577dfd1aa31086d68914c97496f0af097ddf9780737b` | `0o644` |
| `tooling/src/main/java/org/totipo/tooling/release/RecordedServices.java` | `056182cc896a8d7f367fbf8dd20434027726296ef8aa1661eb4ddf3067849b0d` | `0o644` |
| `tooling/src/main/java/org/totipo/tooling/release/ReleaseInputs.java` | `d6337f06622767051d964d7f61cc421609eb7cc770be1906f6ecf08c1533cbbe` | `0o644` |
| `tooling/src/main/java/org/totipo/tooling/release/ReleaseInspectionCli.java` | `fff11f67a9ff6da4080708a43de57c3fc6f7b6763471345a0ec4f4ecd6d8e9bc` | `0o644` |
| `tooling/src/main/java/org/totipo/tooling/release/RemoteReleaseState.java` | `e1624af00380d02098ed355a9eeceda595778d2e0d653e256225ada031291114` | `0o644` |
| `tooling/src/main/java/org/totipo/tooling/release/SourceFiles.java` | `6987b475341fa72a61c8666d4466ffcbbc2bb86653a1872bfad0de2f2cea3510` | `0o644` |
| `tooling/src/main/java/org/totipo/tooling/release/SourceIdentity.java` | `59b0d71b2c15dd47baf833ae423a3b802e062b218804eacfe465f40a22a974f7` | `0o644` |
| `tooling/src/test/java/org/totipo/tooling/release/ReleaseFixture.java` | `f78f7601152d2b7df72bce2f883f07c02cfcc142cdcf636bb6e78aa639905a7c` | `0o644` |
| `tooling/src/test/java/org/totipo/tooling/release/ReleaseTest.java` | `918e8e0e99dc9be8e710e7789ee87dc4b45b5c139882486182a4eb9818b3a154` | `0o644` |

**PASS — complete diff/source membership review.** Exactly **15** intended files:
two modified existing files and thirteen new files (nine implementation files,
two Java test files, one host differential runner, one review report). All new
files are intent-to-add only. No caches, generated outputs, temporary evidence or
secrets are Git-known. `git diff --cached --quiet` and `git diff --check` pass.
Branch remains main and HEAD remains the recorded starting revision.

`git status --short`:

```text
 M nix/tooling.nix
 A review/J3A_JAVA_RELEASE_INSPECTION_REPORT.md
 A tooling/release-parity.py
 M tooling/src/main/java/org/totipo/tooling/Main.java
 A tooling/src/main/java/org/totipo/tooling/release/GitQueries.java
 A tooling/src/main/java/org/totipo/tooling/release/HttpQueries.java
 A tooling/src/main/java/org/totipo/tooling/release/PreparationPlan.java
 A tooling/src/main/java/org/totipo/tooling/release/RecordedServices.java
 A tooling/src/main/java/org/totipo/tooling/release/ReleaseInputs.java
 A tooling/src/main/java/org/totipo/tooling/release/ReleaseInspectionCli.java
 A tooling/src/main/java/org/totipo/tooling/release/RemoteReleaseState.java
 A tooling/src/main/java/org/totipo/tooling/release/SourceFiles.java
 A tooling/src/main/java/org/totipo/tooling/release/SourceIdentity.java
 A tooling/src/test/java/org/totipo/tooling/release/ReleaseFixture.java
 A tooling/src/test/java/org/totipo/tooling/release/ReleaseTest.java
```

`git diff --name-only`:

```text
nix/tooling.nix
review/J3A_JAVA_RELEASE_INSPECTION_REPORT.md
tooling/release-parity.py
tooling/src/main/java/org/totipo/tooling/Main.java
tooling/src/main/java/org/totipo/tooling/release/GitQueries.java
tooling/src/main/java/org/totipo/tooling/release/HttpQueries.java
tooling/src/main/java/org/totipo/tooling/release/PreparationPlan.java
tooling/src/main/java/org/totipo/tooling/release/RecordedServices.java
tooling/src/main/java/org/totipo/tooling/release/ReleaseInputs.java
tooling/src/main/java/org/totipo/tooling/release/ReleaseInspectionCli.java
tooling/src/main/java/org/totipo/tooling/release/RemoteReleaseState.java
tooling/src/main/java/org/totipo/tooling/release/SourceFiles.java
tooling/src/main/java/org/totipo/tooling/release/SourceIdentity.java
tooling/src/test/java/org/totipo/tooling/release/ReleaseFixture.java
tooling/src/test/java/org/totipo/tooling/release/ReleaseTest.java
```

`git diff --cached --name-only`: empty.

**PASS — human J3A qualification.** The owner reported “nix flake check passed”.
The agent independently rechecked all 318 frozen SHA-256 hashes and filesystem
modes, unchanged main/HEAD, Git source membership and empty staged file contents.
The human gate includes existing Java/publication/consumer/wrapper checks and
new permanent J3A tests through checks.tooling. No agent Nix invocation occurred.
Only this excluded report was finalized after the gate; no included input changed.
All 15 intended changed paths remain uncommitted for review.
**FAIL — none observed. PENDING — none for J3A. NOT RUN — all live release operations.**
J3A is complete. Stop at J3A; J3B/J4 remain deferred.

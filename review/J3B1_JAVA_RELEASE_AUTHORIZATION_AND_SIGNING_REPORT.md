# J3B.1 — Java release decision, signing evidence and secret preflight

Status: **PASS — J3B.1 implementation, complete host qualification and fresh
human Nix qualification.** Python remains the protected workflow's
release authority. No live release authorization or real signing is qualified.
All changes remain uncommitted for owner review. Stop at J3B.1.

Evidence directory: `/tmp/totipo-j3b1-evidence/`, outside Git membership.

## 1. Starting state and classifications

Started exclusively in `/home/niki/Sources/totipo-java` on clean `main`.
Starting `git status --short`: empty. Starting HEAD:
`5bc55c8a26b5d0af0ba8a243454dacd46ef0cb86` (`Rewrite release in java`).
J3A implementation and report are committed at this HEAD. Owner-confirmed J3A
CI success is a prerequisite only; no J3A qualification is reused.

Classifications: **RELEASE_TOOLING**, **DEPENDENCY_BUILD** (direct standalone
compilation and Nix test wiring). No dependency update or source-release
preparation; not RELEASE_PREPARATION. No production, public library API, protocol,
Gradle resolution, publication metadata or consumer semantics change.

Read AGENTS.md in full; J1, J2A/J2A.1, J2B and J3A reports; complete Python release,
release/preparation tests, publication model/verifier; protected workflow and
release checklist; qualification.nix, nix/tooling.nix and flake.nix. Inspected
J3A release classes and J2B verifier/model/CLI/secure filesystem implementation
and their fixture infrastructure. Reviewed API_DESIGN operation/snapshot guidance;
no application operation semantics change. No sibling repository, isolated
worktree, sub-agent or agent Nix invocation.

## 2. Full command migration matrix and responsibility inventory

Python exposes exactly **13** commands. This matrix remains a J4 removal checklist;
Java API availability does not mean workflow migration or live authority.

| Python command | Authority/responsibility | Java status after J3B.1 | Remaining owner |
| --- | --- | --- | --- |
| `inputs` | Read-only operator version/commit/dispatch checks | J3A independent implementation | J4 workflow cutover |
| `identity` | Source/pin/notes/clean/reviewed SHA; current main/tags/release observations; Python fetch | J3A read-only independent observations; no fetch | J4 fresh live integration |
| `state` | Central/history observations, pure policy, writes `mode` to GITHUB_OUTPUT | J3A observations + J3B.1 pure typed dispositions; no operational output | J4 protected sequencing/output |
| `inventory` | Accepted unsigned-artifact evidence, fresh staging, manifest workflow transport | J2B schema + J3B.1 fresh ten-path/hash API | J4 transport integration |
| `compare` | Fresh unsigned hashes versus EXPECTED_INVENTORY | J3B.1 API reuses J2B parse/checkStaging | J4 integration |
| `signed` | Read-only sign-task input/armor evidence | J3B.1 bounded secure filesystem API | J4 integration; Gradle owns signing |
| `remote` | Propagation polling, exact remote bytes/signature evidence after compare | Unimplemented | J3B.2 implementation, J4 cutover |
| `tests` | Full XML, corpus and integrity accounting | Python retained | J4 must migrate accounting before removal |
| `focused-tests` | Focused snapshot/profile XML accounting | Python retained | J4 must migrate accounting before removal |
| `secrets` | Required credentials and read-only private-key packet inspection | J3B.1 injected environment/inspection API and runtime GPG adapter | J4 runtime-only integration |
| `upload-ready` | Immediate live recheck; refuses unless state is publish | No operational Java command; boundary modeled only | J4 reviewed live integration |
| `tag-release` | Idempotent tag/release checks, Git configuration, annotated tag creation/push, gh release creation | Unimplemented | J3B.2 implementation, J4 cutover |
| `prepare` | Source planning/check and actual application | J3A plan/dry-run/check only | J3B.2 apply, J4 cutover |

Read-only observations, pure decisions, accepted artifact evidence, signing-task
input evidence and credential/key inspection remain separate authorities. Actual
signing/upload are Gradle operations outside this controller command inventory.
`tag-release` is credential-bearing and irreversible. No credential-bearing Java
release operation is implemented or authorized.

## 3. Protected callers and ordering

The unchanged workflow validates dispatch before checkout, then checks exact
reviewed identity and preparation, calls `state`, verifies the wrapper, runs
Python guardrails, performs the release-only normal/offline artifact comparison,
accounts full XML before focused integrity, and emits `inventory`.
After release-environment approval, publish-central checks required secrets,
checks out the reviewed commit, calls `secrets`, revalidates `identity`, calls
`state` again, verifies the wrapper, rebuilds and calls `compare`. In publish
mode it invokes Gradle signing and then `signed`. The upload step immediately
repeats `identity` and `upload-ready` before the existing Gradle Central task.
Verify-central rebuilds, calls `remote` (which compares inventory first), then
uses fresh Central-only module/POM consumers. Only afterward does the final job
call `tag-release`. These call sites and all approval/secret/permission/concurrency
boundaries remain Python-owned and byte-identical.

## 4. Pure state machine and exact disposition table

`PublicationPolicy` interprets the existing immutable J3A `RemoteReleaseState`.
It returns `Decision(Disposition, Reason)`, never a broad safeToPublish boolean.
Construction rejects inconsistent disposition/reason pairs. Invalid/incomplete
observations fail in J3A; the boundary assessment returns BLOCKED with
OBSERVATION_UNAVAILABLE, without response contents or credential diagnostics.
Direct interpretation rejects absent evidence rather than inventing a state.

| Core POM | Storage POM | Complete same-run upload history | Disposition / reason |
| --- | --- | --- | --- |
| absent | absent | no recorded upload | CANDIDATE_PUBLISH / ABSENT_WITHOUT_PRIOR_UPLOAD |
| present | absent | no recorded upload | BLOCKED / EXISTING_WITHOUT_SAME_RUN_UPLOAD |
| absent | present | no recorded upload | BLOCKED / EXISTING_WITHOUT_SAME_RUN_UPLOAD |
| present | present | no recorded upload | BLOCKED / EXISTING_WITHOUT_SAME_RUN_UPLOAD |
| absent | absent | possible upload | BLOCKED / PROPAGATION_UNCERTAIN |
| present | absent | possible upload | VERIFY_EXISTING / SAME_RUN_UPLOAD_VISIBLE |
| absent | present | possible upload | VERIFY_EXISTING / SAME_RUN_UPLOAD_VISIBLE |
| present | present | possible upload | VERIFY_EXISTING / SAME_RUN_UPLOAD_VISIBLE |
| any | any | incomplete, contradictory, malformed or unavailable | BLOCKED / OBSERVATION_UNAVAILABLE |
| API/transport error | any | any | BLOCKED / OBSERVATION_UNAVAILABLE |

VERIFY_EXISTING means skip every upload and require later complete verification;
it is not successful remote verification. CANDIDATE_PUBLISH describes only one
completed observation. No timestamp, fixture, decision or preflight result is a
reusable authorization token. The report and tests establish local policy only.

## 5. Prior upload, propagation ambiguity and observation reuse

J3A remains the only Central/GitHub parser and owner of upload-step identity.
No parser, upload-step name or required job-name constant is duplicated in main
code. Failed, cancelled, timed-out or unconcluded started upload steps can have
uploaded bytes. Two current 404s after a possible upload never authorize retry.
Presence without same-run evidence blocks; missing steps/jobs cannot prove a
clean history. Every required page and earlier attempt must be complete even
when an earlier record already establishes a possible upload. J3A's stricter
identity, duplicate and total-count checks are preserved unchanged.

`Boundary` distinguishes INITIAL, AFTER_APPROVAL and IMMEDIATELY_BEFORE_UPLOAD.
`assess` invokes its observer each time and does not accept a previous Decision.
Permanent tests count observer calls and demonstrate initial candidates followed
by core presence, storage presence, newly visible prior-upload evidence, API
failure, and complete continued absence. Final outcomes block a new upload or
remain a candidate only. No upload occurs. Tests use injected observations,
not cached `inspect-state` results.

Future J4 must obtain fresh live GitHub/Central observations and reviewed source
identity at each workflow boundary, especially immediately before upload. This
API cannot prove freshness of a caller-supplied observer. A caller returning a
cached fixture cannot gain authority here. There is no globally atomic GitHub/
Central transaction; best-effort sequencing and honest ambiguity remain required.

## 6. No-live-authorization API and CLI guarantees

No Main or ReleaseInspectionCli change. Existing recorded-evidence inspection
continues to say that inspection does not authorize publication. No Java
`upload-ready`, `publish`, `sign`, `tag-release`, `release-create` or live
`inspect-policy` command is added. New methods cannot invoke Gradle, mutate
Central/GitHub, create a tag/release or write `mode=publish` to GITHUB_OUTPUT.
Only the separately supplied GPG executable can be spawned by new main code,
with fixed read-only inspection arguments. Test-only fixture bridges are not
installed in tooling main classes and have no live network fallback.

## 7. J2B acceptance and fresh inventory comparison

`AcceptedArtifacts.fresh` bounded-reads the accepted manifest, validates its
version with J3A and all ten canonical identities with unchanged J2B
PublicationManifest.parse, then calls PublicationRepository.checkStaging.
The ten actual artifacts and complete repository fingerprint are reread.
Neither internally consistent hashes nor a caller-created mutable release state
bypass fresh staging verification. The existing manifest schema/fingerprint
encoding and Python-authoritative manifest remain unchanged.

Inventory exposes exactly ten immutable canonical path/SHA-256 pairs. Compare
parses EXPECTED_INVENTORY using the same J2B schema and compares those pairs to
a freshly checked current staging. Preflight's repository fingerprint is not
compared across rebuilds because indexes can contain timestamps; current local
staging must still match its own accepted fingerprint. A permanent positive
case checks that distinction. Wrong identities, version, role, path, missing/
extra/duplicate records, malformed/trailing/duplicate-key JSON, changed hashes,
all ten validly formatted but incorrect hashes, altered staging/index/sidecar
and internally consistent false manifest hashes reject.

Test-only AcceptedFixture calls the existing J2B PublicationTest.fixture and
independent PublicationVerifier; it does not implement another verifier. The
new release layer does not modify J2B main classes or existing tests.

## 8. Detached signature evidence and unsigned byte binding

For every accepted artifact the exact Python mapping is preserved: `pom` maps
to `pom-default.xml`, `module` to `module.json`, other roles to the canonical
artifact filename. Search is confined to that artifact's module build tree.
Exactly one matching `.asc` is required; no fallback filename or module exists.
The adjacent unsigned task input is independently read and SHA-256 hashed,
then compared to the accepted unsigned artifact. Detached evidence must contain
both ASCII armor markers, exactly as Python requires. Staging is checked again
after all ten inputs are inspected.

This proves reviewed-input byte identity and marker presence only. It does not
prove detached OpenPGP cryptographic validity, signer identity/trust, correct
secret-key usage or Central acceptance. No new cryptographic signature verifier
or signing-policy change is introduced. Gradle owns actual signing.

EvidenceFiles uses SecureDirectoryStream for every ancestor and recursive child,
NOFOLLOW for attributes/channels, and regular-file checks. Unexpected symlinks,
including unrelated entries and ancestor links, and nonregular entries fail.
Limits: 64 MiB per unsigned input, 64 KiB per signature, 20,000 entries per tree
scan and depth 32. A matching signature directory is explicitly rejected.
Scanning continues after the first candidate so later ambiguity cannot pass.
Filesystem observation assumes a frozen build tree, not an atomic snapshot
against concurrent directory renames or byte rewrites.

Permanent synthetic tests cover all ten inputs, each missing/duplicate signature,
wrong input bytes, wrong module, POM/module filename mapping, empty/truncated
markers, unsigned/signature directories, symlink escape, changed staging,
ambiguous paths, sparse oversized input, oversized signatures, depth and entry
limits. No real signing task or key is used.

## 9. Environment secrets and packet preflight

SecretPreflight takes an explicit environment map and injected PacketInspector.
Nonempty mavenCentralUsername, mavenCentralPassword and signingInMemoryKey
properties under ORG_GRADLE_PROJECT_ are required. Empty strings reject;
whitespace retains Python's nonempty-string behavior. A private-key packet is
required. Presence of `protected` or `protect algo:` requires a nonempty
signingInMemoryKeyPassword. Key ID remains optional and is not newly required.
Inspection evidence does not prove signing will succeed or key/password
compatibility; Gradle's actual signing task remains authoritative.

Missing variables are diagnosed by property name only. Inspector exceptions,
causes and packet dumps are replaced by static messages. Tests inject synthetic
environment strings and packet metadata: missing/empty required properties,
unprotected/protected key, required/optional password, unreadable key, unavailable
inspector, malformed/unexpected metadata, bounded output and safe diagnostics.
No private-key packet bytes or real credentials are stored in Git fixtures.

## 10. GPG runtime process isolation

GpgPacketInspector requires an explicit absolute runtime executable, temporary
parent and positive deadline no greater than one minute. It invokes no shell:
`--no-options --batch --no-autostart --homedir <temporary-home>
--pinentry-mode error --list-packets`. Key material is stdin only; never argv,
private-key files or import commands. The isolated temporary home is removed
on success and failure. Child environment is cleared except LC_ALL=C, so release
credentials, GPG config and loader overrides are not inherited. No user's
keyring is imported into or used for this inspection.

Three concurrent virtual-thread pipe tasks drain bounded stdout/stderr and write
stdin. The same monotonic deadline bounds all futures and process completion,
including blocked stdin. Nonzero exit fails deterministically. Failure destroys
the process/descendants, closes pipes, shuts down workers, wipes acquired key
bytes and deletes temporary material. Byte/output bounds are 1 MiB each; immutable
Java caller Strings cannot be wiped and remain the runtime caller's responsibility.
Diagnostics discard causes/suppressed provider or process messages.

Packet inspection is GPG text-framing/marker interpretation, not a new OpenPGP
parser. Strict UTF-8, control-character rejection, packet/comment/indented framing
and the exact secret-key header reject malformed/unframed output; universal
newlines and Unicode user IDs are accepted. Protection markers preserve Python.
The explicit executable and trusted temporary parent are runtime trust inputs.
No real GPG/private-key execution was used for qualification.

Process tests use a temporary fixed launcher and Java FakeGpg. The fake asserts
fixed arguments and exact stdin bytes; exercises success/protected, nonzero exit,
timeout, blocked stdin, stdout/stderr overflow and malformed extra output; writes
only a synthetic cleanup sentinel. Every invocation verifies home cleanup and
static diagnostics. Nix supplies its existing stdenv shell solely to the fake
launcher, avoiding a sandbox dependency on `/bin/sh`. Main code never constructs
a shell command. Real credential inspection must execute outside derivations;
no derivation argument, store input or fixture contains release credentials.

## 11. Differential validation and conservative differences

Unchanged publishing/release.py and publication.py remain the historical oracles.
The host-only authorization-parity.py gives both implementations identical
recorded Central/history responses, disposable staging/signature trees and
synthetic environment/packet results. Java uses the existing J3A RecordedServices
and parsers. Policy output values are compared, not merely exit success. Inventory
compares exact canonical paths and hashes. Rejections compare safety dispositions.

Focused completed suites: **157 cases / 11 tightenings**, then **173 cases /
12 tightenings**, zero unexplained drift. Final frozen standalone pipeline reruns
the 173-case suite against final classes; its result is recorded below. Existing
J3A and J2B differential suites are also retained and rerun.

Measured tightenings include J3A missing upload/empty history, wrong run identity,
duplicate JSON, incomplete total_count, later-page failure after an upload;
secure signing-input symlink rejection; GPG nonzero exit despite a secret marker,
unframed additional output and output overflow. Some malformed histories already
block Python because present coordinates lack same-run evidence; those cases
agree in disposition rather than counting a tightening. Complete measured case
names are appended from results.json.

The first differential invocation failed because patching release.subprocess.run
also patched the Java subprocess runner (shared Python module object). Fixed the
host harness by retaining its original subprocess function before mocking GPG.
No Java safety defect or Python implementation was changed to resolve it. This
failed attempt is accounted separately; expected fixture rejections are tests,
not unexpected execution failures. Python's GPG function historically ignores
returncode if its marker exists and has no deadline/output bounds; Java's explicit
nonzero rejection and bounded behavior implement the requested conservative
preflight contract. No unexplained Java acceptance broadening is intended.

J4 removal inventory includes authorization-parity.py, AuthorizationFixture,
ReleaseFixture/release-parity.py, publication-parity.py and their Python oracle
responsibilities. Permanent AuthorizationTest runs independently of Python.
AcceptedFixture/FakeGpg remain deterministic Java test infrastructure.

## 12. Standalone classpath and Nix integration

JDK 25.0.4.1+1 direct javac, --release 25, UTF-8, -Xlint:all -Werror,
-proc:none, explicit sourcepaths. Main external classpath remains only reviewed
Jackson Core 2.21.7. Canonical host acquisition authenticated SHA-256:
`8c5623b98f32d5f7e1287ff61ed50e08ab7b11381e991fff89327faabba42261`.
No GPG parser/library, production, Gradle, Databind, JUnit or other dependency.
The existing negative production/Gradle imports and JDK-only wrapper compilation
remain active. No pins, locks, cache or toolchain update; no updater checkpoint.

Only pre-existing changed file: nix/tooling.nix. It updates the local derivation
version and adds AuthorizationTest with the existing stdenv shell test argument.
Its unchanged Java-only tooling/src fileset includes every new main/test source,
including the fake process and reused J2B fixture bridge. No fixture export,
temporary evidence, key material, caches or build outputs enter that fileset.
Existing parser/smoke, J2B, J3A, forbidden-import and launcher checks remain.
checks.java's real-artifact Java/Python shadow, consumers, full/focused accounting
and all snapshot hashes remain unchanged, as do checks.wrapper and flake inputs.

Inspected qualification.nix and flake.nix source selection: review/ is excluded;
standalone Java is selected separately; nix/ is also selected by production
qualification. All included inputs were frozen before the human gate. No new
network dependency or credential input. Host-only Python oracle is not executed
inside the Java derivation. Qualification fixtures are offline and deterministic.

## 13. Host qualification and invocation accounting

Focused baseline before implementation: Python test_release.py **22 PASS** and
existing test_publication.py **9 PASS**, one invocation each. No broad Gradle
baseline. Existing synthetic HTTPError ResourceWarnings are non-failing.

Direct standalone compile/test invocations: five main javac, five test javac,
all PASS. AuthorizationTest assertion counts by invocation: **216, 216, 224,
226, 228**. Expanded coverage explains each iteration. The stable regression
pipeline runs the final suite once with empty PATH/CLASSPATH alongside the
existing independent checks. No production focused Gradle test was run.

Final full normal gate, XML accounting/preservation, full Python guardrails,
fresh manifest check, POM-only consumer, real-artifact Java shadow verification/
check/copy and final standalone results are recorded after completion below.
No release-only reproducibility comparison and no separate host forced-offline
full suite: Gradle acquisition/build/resolution and fixed cache semantics did
not change. Required isolated module/POM consumers retain their offline options.
Normal wrapper distribution acquisition is allowed host build acquisition, not
live release inspection or dependency pin/cache modification.

## 14. Human qualification

**PASS — human-reported fresh J3B.1 `nix flake check`.** The owner reported:
“nix flake check passed”. One human J3B.1 invocation is reported. Detailed logs,
duration and task/cache counts were not supplied and are not inferred. Agent
Nix invocations remain zero; J3A qualification is not reused. No redundant
nix build or package-deps updater was requested.

**PASS — agent post-gate audit:** all 328 frozen non-review SHA-256 hashes and
filesystem modes still match. Branch main, starting HEAD, complete changed-file
membership/status and absence of staged file content are unchanged. Evidence:
`/tmp/totipo-j3b1-evidence/post-human-audit.json`. Only this confirmed-excluded
report was finalized after the gate; no included input changed or invalidated
qualification. J3B.1 is complete; J3B.2/J4 remain deferred.

## 15. Protected invariance, complete Git state and freeze

Baseline hashes/modes: baseline.json. Final audit and freeze inventories are
appended below. Protected production sources, spec snapshot/corpus, SPEC_PIN,
VERSION, gradle/, publishing/, package-deps, flake.lock and release.yml must all
retain exact bytes/modes. Java 17 library bytecode, JDK25 tooling, Jackson2.21.7,
BC1.86, wrapper pins, r19, 92 cases, ten unsigned artifacts and independent
consumer semantics remain under existing gates. No Python source/test or J2B/J3A
class modification. No workflow permissions/environment/approval/concurrency or
release-source preparation change. No real release authorization/signing claim.

All intended new files, including this report, receive intent-to-add only.
Actual staged file content must be empty. No generated output, cache, temporary
evidence, secret or ignored file is intent-added. Full changed inventory, hashes,
modes, status, diff names and cached names follow after final review.

## 16. Deferred commands, risks and required next ownership

J3B.2 remains unstarted: actual source preparation application, propagation
polling, exact remote-byte/signature verification, tag creation/push, GitHub
release creation and idempotent finalization. J4 remains unstarted: protected
workflow replacement, runtime credential integration, fresh live upload boundary,
Python/oracle removal and cross-platform cutover qualification. Full/focused
accounting must have an independent reviewed replacement before Python deletion.
No new Java operational upload-ready/sign/publish/tag/release command exists.

Residual constraints: secure filesystem providers are mandatory; immutable
caller secret Strings remain in process memory; GPG executable/temporary parent
must be reviewed runtime inputs; bounded resources and unexpected metadata fail
closed. Fresh boundary invocation does not prove that an injected observer is
live, and no atomic remote transaction exists. Real signing remains untested and
Gradle-authoritative. Marker evidence establishes neither crypto validity nor
signer trust. Nix qualification is owner-reported PASS; no real credential or
signing execution follows from that result.

**NOT RUN:** real signing/key inspection, live Central/GitHub release checks,
upload, propagation, tag/configuration mutation, push, GitHub release creation,
commit, protected workflow dispatch, release-only reproducibility or cache update.

## 17. Completed final host evidence

**PASS — agent-executed.** One stable full normal gate:
`./gradlew test build javadoc verifyPublication consumerSmoke`, **43s**, 27 outer
actionable tasks, **8 executed / 19 up-to-date**. Nested isolated module consumer:
**12s, 5/5 tasks executed**. Both module test tasks and Javadocs were UP-TO-DATE;
this accounts current full XML and does not claim fresh execution of all 405 tests.
The required command was run without clean or rerun-tasks. Immediate
`python3 -B publishing/release.py tests`: **405 tests**, zero failures/errors/skips,
**92/92 portable cases**, snapshot **3/3**, profile **1/1**. Full XML copied to
full-xml/ and accepted manifest preserved before any further consumer work.
No focused production run overwrote full-suite evidence.

**PASS — separate Python guardrails:** **51 tests**, no failures/skips on the
full checkout. Existing mocked HTTPError ResourceWarnings remain non-failing.
**PASS — Python fresh manifest check:** 10/10. **PASS — independent POM-only
consumer:** 5s, **5/5 tasks executed**, with existing offline options.
**PASS — Java J2B real-artifact shadow:** independent semantic verification,
complete manifest/consumer XML byte parity with Python, fresh staging check,
ten-artifact copy, then independent Python rehash and exact copied inventory.
Python-authoritative manifest was never overwritten by Java.

**PASS — stable standalone regression pipeline with empty PATH/CLASSPATH:**
J1 **43 assertions**; J2B **132 assertions / 64 exported fixtures**; J3A
**218 assertions**; J3B.1 **228 assertions**; bootstrap smoke. Forbidden imports
of both production and Gradle fail as expected under the restricted classpath.
JDK-only wrapper verification: **199 fixtures (1 accept, 198 reject)**, direct
API and CLI; **190** original raw-declaration tests; four additional CLI probes
and invalid-path assertion; independent digest/pin consistency and
Properties.load escape proofs. Separate canonical Java and Python wrapper
verification both pass, preserving exact JAR/properties identities.

**PASS — frozen differential suites:** J2B **90 cases / 11 tightenings**,
J3A **169 / 18**, J3B.1 **173 / 12**; zero unexplained acceptance/safety drift.
**PASS — 12 actual Main probes:** all operational/unported command names reject
with status 1, GITHUB_OUTPUT sentinel unchanged. No live service operation occurs.
All fake process fixtures operate offline with synthetic inputs.

Logs: final-normal.log, final-accounting.log, python-full.log, manifest-check.log,
pom-consumer.log, shadow.log, final-pipeline.log, regressions.log, forbidden.log,
cli-probes.json. Fixtures/results: publication-fixtures/, j3a-parity/,
authorization-parity/. This evidence is external temporary review material,
not qualification source membership.

## 18. Final invocation accounting and failed attempts

| Category | Invocations / result |
| --- | --- |
| Broad Gradle baseline | 0; focused release-tooling baseline selected |
| Focused Python baseline | 2 PASS: release 22, publication 9 |
| Other focused Python unit / focused production Gradle runs | 0 / 0 |
| Standalone positive main / test javac | 5 / 5 PASS |
| New authorization suite | 6 PASS: 216, 216, 224, 226, 228, 228 assertions |
| Existing J1/parser, J2B/publication, J3A/release runners | 1 each PASS: 43, 132, 218 assertions |
| Smoke / expected forbidden-import compile | 1 / 1 PASS; compile rejects as expected |
| Wrapper main / test javac | 1 / 1 PASS, JDK-only |
| Wrapper full runner / canonical Java / canonical Python | 1 / 1 / 1 PASS |
| J2B / J3A differential | 1 / 1 PASS, 90 / 169 cases |
| J3B.1 differential attempts | 4: first FAIL (harness mocking), then PASS 157, 173, 173 |
| Actual operational CLI rejection probes | 12 PASS, expected status 1 |
| Final full normal Java gate / immediate accounting | 1 / 1 PASS |
| verifyPublication / module consumer | 1 / 1 PASS, nested in final gate |
| Full Python guardrails | 1 PASS, 51 tests |
| Fresh Python manifest / POM consumer | 1 / 1 PASS |
| Real Java shadow verify / check / copy | 1 / 1 / 1 PASS |
| Independent Python Java-copy check | 1 PASS, ten hashes and exact set |
| Separate host forced-offline full suite | 0 NOT RUN; no changed acquisition/resolution semantics |
| Human cache updater / agent Nix / human J3B.1 Nix reported | 0 / 0 / 1 human-reported PASS |
| Release reproducibility, real key inspection/signing/upload/tag/release/commit/push | 0 NOT RUN |

Counts are invocations, not executions of up-to-date tasks or extra full suites
for nested consumers. Fake process cases are eight per authorization runner
(two positive, six expected process failures); all cleanup/diagnostic assertions
pass. Expected negative fixture failures are successful tests. The one failed
migration harness attempt is documented in section 11; no Java/Python oracle
weakening was used. A local search for the pinned JAR found no host Gradle-cache
copy (rg status 2), so canonical pinned bytes were acquired and authenticated.
No unexpected compile/unit/build failures or final-gate invalidation occurred.
All included-source edits preceded the stable final gate.

## 19. Protected audit, source membership and frozen hashes

**PASS — all 216 requested protected tracked files** retain exact baseline
SHA-256 and filesystem modes. Every other pre-existing tracked file also matches
except the intended nix/tooling.nix. Audit: protected-audit.json. Production/spec,
Python, dependencies/cache/locks, wrapper and protected workflow remain unchanged.
No remote CI result is claimed for this uncommitted milestone.

**PASS — freeze:** 328 Git-known non-review regular files, a conservative superset
of all included qualification inputs, are recorded with SHA-256 and filesystem
modes in `/tmp/totipo-j3b1-evidence/frozen-inputs.json`.
Inventory SHA-256:
`6c9410985e5aea9064e52a9cd712fcc5c180a2bd7f01449cb7751577ef290d4c`.
No included file may change after this freeze without invalidating qualification.
Only this confirmed-excluded report was finalized afterward. The owner reported
the fresh human gate **PASS**; all 328 frozen input hashes/modes still match.

Changed non-review inputs and host-oracle hashes/modes:

| Path | SHA-256 | Mode |
| --- | --- | --- |
| `nix/tooling.nix` | `d4ce8cfae9e237ac887e57fe3ce3c215441d15013b1d59e00999e927c7f3bb28` | `0644` |
| `tooling/authorization-parity.py` | `efb7279fce27a4bc5306c63be08c275058176acca55077083c84ec07404b7111` | `0644` |
| `tooling/src/main/java/org/totipo/tooling/release/AcceptedArtifacts.java` | `24b18d37236a0612a5770cc6fb6b08abc8b02d71755bb730430ae427d92b200b` | `0644` |
| `tooling/src/main/java/org/totipo/tooling/release/EvidenceFiles.java` | `819d74a2a3df2aaaf72c60436e6642790d76fc72121753504b1f352e411434c2` | `0644` |
| `tooling/src/main/java/org/totipo/tooling/release/GpgPacketInspector.java` | `bb62fd4cb1bca5f96b26d3d4bb166337d4c3659e413f8bf90060933c3073f465` | `0644` |
| `tooling/src/main/java/org/totipo/tooling/release/PublicationPolicy.java` | `5b27e1ae1beab2d732d3d60d1116bb59ff56f0f1846b35f83f9d30ab7c512bcf` | `0644` |
| `tooling/src/main/java/org/totipo/tooling/release/SecretPreflight.java` | `e0f0e81dc41d96fb4ced6b546aa623d376fec4d56a47655f896c5cfe24b87c18` | `0644` |
| `tooling/src/test/java/org/totipo/tooling/publication/AcceptedFixture.java` | `0c5b9ebd3e6cd2d5e63221a5a53b112de5a0e5b872363591a9db7cedd8d2d584` | `0644` |
| `tooling/src/test/java/org/totipo/tooling/release/AuthorizationFixture.java` | `af4a0309eff8c44349fc7342217d09a4f4c987d50d36241ce8f172d3873896f9` | `0644` |
| `tooling/src/test/java/org/totipo/tooling/release/AuthorizationTest.java` | `da456fe9917b46a6aeb498aa1605dfd38adf7eb5b6a6c7946737ef50ec8fe81d` | `0644` |
| `tooling/src/test/java/org/totipo/tooling/release/FakeGpg.java` | `2a8716df35c0ce8e99da23ae432fb23198afcc3383c17c25264d51d4726eca1b` | `0644` |

## 20. Measured conservative differential cases

Python nonblocked/accepted and Java blocked/rejected, deliberately:

```text
policy:200:200:later-page-error
policy:200:404:later-page-error
policy:404:200:later-page-error
policy:404:404:missing-upload
policy:404:404:wrong-run
policy:404:404:empty-history
policy:404:404:incomplete-total
policy:404:404:duplicate-json
signed:symlink-tightening
secrets:gpg-failure
secrets:unexpected-output
secrets:oversized
```

## 21. Complete final Git inventory

Exactly **12** intended paths: one modified existing Nix file, five new main
Java classes, four new test Java classes, one host-only oracle and this report.
New paths have intent-to-add membership only; actual staged content is empty.
No temporary evidence, fixture exports, generated output, secrets or ignored
files are Git-known. Branch remains main; HEAD remains the starting revision.

`git status --short`:

```text
 M nix/tooling.nix
 A review/J3B1_JAVA_RELEASE_AUTHORIZATION_AND_SIGNING_REPORT.md
 A tooling/authorization-parity.py
 A tooling/src/main/java/org/totipo/tooling/release/AcceptedArtifacts.java
 A tooling/src/main/java/org/totipo/tooling/release/EvidenceFiles.java
 A tooling/src/main/java/org/totipo/tooling/release/GpgPacketInspector.java
 A tooling/src/main/java/org/totipo/tooling/release/PublicationPolicy.java
 A tooling/src/main/java/org/totipo/tooling/release/SecretPreflight.java
 A tooling/src/test/java/org/totipo/tooling/publication/AcceptedFixture.java
 A tooling/src/test/java/org/totipo/tooling/release/AuthorizationFixture.java
 A tooling/src/test/java/org/totipo/tooling/release/AuthorizationTest.java
 A tooling/src/test/java/org/totipo/tooling/release/FakeGpg.java
```

`git diff --name-only` (complete changed inventory):

```text
nix/tooling.nix
review/J3B1_JAVA_RELEASE_AUTHORIZATION_AND_SIGNING_REPORT.md
tooling/authorization-parity.py
tooling/src/main/java/org/totipo/tooling/release/AcceptedArtifacts.java
tooling/src/main/java/org/totipo/tooling/release/EvidenceFiles.java
tooling/src/main/java/org/totipo/tooling/release/GpgPacketInspector.java
tooling/src/main/java/org/totipo/tooling/release/PublicationPolicy.java
tooling/src/main/java/org/totipo/tooling/release/SecretPreflight.java
tooling/src/test/java/org/totipo/tooling/publication/AcceptedFixture.java
tooling/src/test/java/org/totipo/tooling/release/AuthorizationFixture.java
tooling/src/test/java/org/totipo/tooling/release/AuthorizationTest.java
tooling/src/test/java/org/totipo/tooling/release/FakeGpg.java
```

`git diff --cached --name-only`: empty. `git diff --cached --quiet` and
`git diff --check`: PASS. Review report mode: 0644. Non-report diff summary:
**11 files, 877 insertions, 1 deletion**; this report is the twelfth new file.
Report-only finalization changes its line count without changing included inputs.
Machine Git inventories are also preserved in git-status.txt, diff-names.txt,
cached-names.txt and diff-stat.txt; frozen-inputs.json is authoritative for bytes.

**PASS — host implementation/qualification and human-reported J3B.1
`nix flake check`, with matching post-gate frozen inputs. PENDING — none for
J3B.1. NOT RUN — every real release operation.** No claim that
real release authorization, private-key compatibility or signing was qualified.
Stop after J3B.1; J3B.2 and J4 remain deferred. All changes remain uncommitted.

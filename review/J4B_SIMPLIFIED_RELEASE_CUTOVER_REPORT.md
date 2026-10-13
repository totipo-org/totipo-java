# J4B — Simplified release cutover and Python elimination

**Status: host qualification PASS; human Nix PASS (owner reported); live mutations DISABLED.**
All work is uncommitted. This milestone implements the design and qualifies local
boundaries; it does not activate or execute a live release. Evidence is under
`/tmp/totipo-j4b-evidence/`, outside Git source membership.

## 1. Starting checkout and authority

Clean `main`, full HEAD `6df11d9efa0b47a125bd9723c6ccec68c95f5ac6`;
initial `git status --short` was empty. That commit contains the approved J4S
report (`git log -1 -- review/J4S_RELEASE_PROCESS_SIMPLIFICATION_REPORT.md`).
Read AGENTS.md completely before edits, J4S/J4A and the relevant J2B/J3A/J3B.1/
J3B.2 contracts, actual implementations/tests, workflows, Gradle, Nix and API
operation/state-snapshot guidance. Historical reports are unchanged.
Only this checkout and disposable exports of its source were used. No siblings,
unrelated worktrees or sub-agents were used.

Classifications: RELEASE_TOOLING, DEPENDENCY_BUILD (integration/runtime wiring),
PUBLICATION_METADATA, CONSUMER_BOUNDARY and DOCS_JAVADOC (Markdown process prose).
Not CORE_RUNTIME, STORAGE_NIO, PUBLIC_API or RELEASE_PREPARATION: no production
library/API, version, pin, release notes or release-source preparation change.
The explicitly requested release-process reproducibility proof still applies.

## 2. Recorded versions and protected inputs

- VERSION: exact `0.2.0` plus LF.
- Totipo v1/r19 commit: `cdb4e91be1c6d3704874b2b92457ffe7be5e9084`.
- Requirements profile SHA-256:
  `7242fc557a7ca56452c934bedc5e7dc2835248f5cd36fa220f05588dc967ec80`.
- 99 pinned snapshot records; 92 portable cases.
- Host/pinned tooling JDK: OpenJDK 25.0.4.1+1; host executable under
  `/nix/store/7gph19iic118dil9na8zx0nxp316fx1x-openjdk-headless-25.0.4.1+1`.
  Existing store executables were used directly; no Nix command was run.
- Gradle 9.8.0; Vanniktech 0.37.0; OkHttp 5.1.0; Jackson Core 2.21.7;
  Bouncy Castle 1.86; JUnit 6.1.3. Production output remains Java 17 (61.0).
- Jackson JAR SHA-256 authenticated before independent compilation:
  `8c5623b98f32d5f7e1287ff61ed50e08ab7b11381e991fff89327faabba42261`.
- Unchanged nixpkgs revision `a799d3e3886da994fa307f817a6bc705ae538eeb`;
  flake-utils `11707dc2f618dd54ca8739b309ec4fc024de578b`;
  llm-agents `4bb57cff45b5554a02dba0cf7a8c7f4f010a864d`;
  jailed-agents `82b9d99d454bd752ae028e8a4bf0c90daf1c23ef`.

Protected byte audit uses `git diff --exit-code HEAD --` on VERSION, SPEC_PIN.md,
core/storage sources/resources, module builds/locks, gradle/, settings files,
package-deps.json, flake.lock and the entire consumer build. All remain unchanged.

## 3. Approved publication policy

At most one authorized Gradle Maven Central publication invocation per version.
Successful exact-source preflight and first-time protected annotated tag reservation
are prerequisites. Existing tags always block ordinary publication, including a
new dispatch at attempt 1 with both Central POMs absent. Reruns, failure, cancellation,
timeout, ambiguity and 404 observations never authorize another invocation.
No upload recovery, exceptional override, custom uploader or deployment controller
was added. The internal `centralUpload` Gradle property freezes and rechecks accepted
inputs; it cannot bypass the workflow's first-time reservation or attempt guards.

Vanniktech 0.37.0 / OkHttp 5.1.0 internal HTTP retries remain accepted within the
single invocation. This does not prove exactly-once HTTP transmission, exactly-once
Sonatype delivery, harmless retransmission or idempotent deployment. Uncertain or
duplicate deployment outcomes require human investigation, without automatic upload.

## 4. Old and new architecture

Old: Python-authoritative release controller, moving-main equality, historical
attempt/job parsing, publish/verify recovery policies, Java coordinator simulations,
preparation transactions, consumer receipts and tag push/resume finalization.

New: Actions owns sequencing. Focused standalone Java commands validate source,
artifacts, tags, remote bytes and exact release records. The durable tag moves
before signing/upload. Two workflows replace the controller; no third controller,
release database, persistent release-evidence schema or migration authority remains.

## 5. Workflows and successful execution sequence

`release.yml` has only manual dispatch with required string `version`/`commit`:

1. `preflight` (`contents:read`): bounded validation before checkout; initial
   dispatch SHA equality; exact-source checkout/cleanliness and fresh canonical
   reachability; reviewed bootstrap/wrapper/workflow checks; source/notes/locks/
   complete snapshot and tag/release/Central absence checks; exact-source Nix gate;
   normal and forced-offline full Java gates and immediate accounting; compare ten
   accepted hashes; independent local POM consumer; focused integrity/accounting;
   emit the existing compact schema-1 inventory.
2. `publish-central` (`contents:write`, main-only `release` environment): disabled
   by J4B's source guard. After activation it independently rebuilds/accepts staging
   and compares all ten hashes. Runtime-only secret presence checks precede tag
   reservation. In one shell step: reject reruns, reserve/read back a new tag,
   sign, freshly reauthenticate source/tag, validate all ten signing inputs, freeze
   producer/signing tasks, invoke Gradle publication once. There is no retry branch.
3. `call-verification` needs successful preflight and publication, uses exactly
   `./.github/workflows/verify-release.yml`, supplies only explicit version/commit,
   and grants the `contents:write` reusable-workflow ceiling. No secrets are inherited.

`verify-release.yml` supports both workflow_call and workflow_dispatch:

1. `verify` (`contents:read`): validate inputs, check out the original source,
   authenticate fresh reachability and existing annotated tag, bootstrap pinned
   tools, reconstruct and semantically accept unsigned staging, verify Central's
   ten artifact/signature pairs, execute the two actual remote consumers.
2. `finalize` needs successful `verify`, has `contents:write` and no release
   environment or Maven/signing credentials. It is disabled by J4B's second guard.
   After activation it reauthenticates source/tag and creates or validates the
   exact GitHub Release.

Ordinary success propagation controls continuation. No `always()`,
continue-on-error, historical-status parsing, automated dispatch or route back to
publication exists. A skipped/failed publishing job does not call verification.

## 6. Maintainer procedures

After separate activation: prepare/review version, both consumer self-lock
coordinates, status metadata and completed version-specific notes; qualify and
commit source; confirm ordinary CI; Actions → Release on main → enter exact version
and reviewed SHA → start. Successful publication, verification and GitHub finalization
are automatic. No manual tag, Portal promotion or release creation is required.

Recovery: Actions → Verify Release on main → enter the original version/SHA → start.
Never rerun publication merely to retry verification. Before tag creation, correct
source/environment and review source corrections. Once reserved, the version cannot
be ordinarily republished even if signing/upload never starts. Investigate uncertain
upload manually. Explicitly repeat verification after timeout/consumer/GitHub failure.
Conflicting tag/bytes/release always stop without repair. RELEASE_CHECKLIST.md keeps
this everyday procedure separate from engineering details.

## 7. One-shot reservation, races and reruns

`SourceIdentity.reserve` rejects any attempt other than exact `1`, requires absent
ref/release, creates an annotated tag object with the reviewed commit and bot identity,
then POSTs `/git/refs` with `refs/tags/v<version>`. Only HTTP 201 with the exact ref/
object response plus an authenticated ref/tag-object readback returns successfully.
The annotation is exactly `totipo-java <version>\n`; the target is directly a commit,
not a nested/lightweight tag. All SHA fields are lowercase forty-character identities.

The API adapter uses pinned curl, `--disable`, no redirects and `--retry 0`, bounded
stdin/stdout/stderr and deadlines. There is no PATCH/DELETE/ref update/tag push/repair.
A lost/ambiguous create response throws immediately; no readback can convert it into
publication authorization. Unreachable tag objects are not repaired or cleaned up.

Reservation, signing, acceptance and publication share one job and one shell step;
no output from another job/run is an authorization receipt. Bash failure propagation
stops the remaining commands. Version concurrency is `release-publish-<version>`,
cancellation disabled. Verification uses `release-verify-<version>`, avoiding a
reusable-call deadlock. Job attempt checks supplement the permanent existing-tag veto.

Focused fake-API tests cover first creation, matching existing tags after Central
404, new attempt-1 dispatches, reruns, reserved-without-upload, simultaneous creators
(one winner), ambiguous creation, bad statuses, failed readback and pre-reservation
failure. Actual workflow tests require a single publication command after reservation,
no alternate publication route and successful publication as the verifier dependency.
These tests qualify invocation authorization, not transport exactly-once semantics.

## 8. Source identity and main advancement

Shell validation bounds versions to 128 characters, rejects SNAPSHOT, requires exact
lowercase 40-character SHA/main dispatch, and emits validated scalar outputs before
checkout uses them. Inputs enter shell only through quoted environment variables.
Only initial publication decision requires dispatch SHA equality.

Java requires exact raw VERSION/LF, matching unique README/API status blocks, both
exact consumer self-lock entries, complete reviewed notes/sections/no TODO, exact
SPEC_PIN and all 99 snapshot hashes. Git checks exact HEAD and a clean checkout,
allows only reviewed local config, scrubs inherited environment/config/hooks/helpers/
replacement objects, fetches canonical HTTPS main into a dedicated local tracking
ref and requires `merge-base --is-ancestor` success. It never relies on cached main.
The pinned CA path is explicitly retained in otherwise scrubbed Git/curl environments.

Initial/eligible checks reject every tag/release and require two actual Central POM
404s; acquisition/error/presence vetoes release. After initial decision, source must
remain reachable, rather than equal an advancing main tip. Verification deliberately
does not apply the initial dispatch equality check. Existing tags are authenticated
through the exact ref and annotated object, annotation and direct reviewed commit.

## 9. Gradle publication and signing boundary

The existing plugin/configuration and exact task remain:

```sh
./gradlew --no-daemon --no-build-cache --no-configuration-cache \
  -PcentralRelease=true publishAndReleaseToMavenCentral
```

No split upload/promotion, HTTP interceptor or plugin patch. Ordinary configuration
still has only unsigned local staging; generic publish with Central mode rejects.
A real ordinary `tasks --all` inventory exposes no Central/signing tasks.

The publishing job verifies staged bytes independently and compares preflight's ten
path/hash pairs, then invokes both existing Sign tasks. `release signed` semantically
reauthenticates source/staging, compares preflight, and verifies every actual adjacent
unsigned signing-task input plus detached markers. In upload mode producer and Sign
tasks have a false onlyIf guard, even when scheduled or forced to rerun. They cannot
rebuild/substitute inputs after acceptance; missing/stale inputs fail verification.

One shared Gradle `verifyCentralSigningInputs` Exec task depends on both Sign tasks
(which remain frozen during upload) and precedes both Maven Central staging publishers.
It rechecks all ten original inputs once before either publisher copies signatures.
Checking separately after the first module copied signatures would falsely detect
those copies as duplicate signing evidence; the shared dependency avoids that issue.
Gradle then owns its existing local bundle assembly, upload, validation and automatic
Central release. The freeze property is passed through the step environment, without
changing the required publication command. No real publication task was executed here.

## 10. Independent ten-artifact reconstruction

Final proof uses clean normal and forced-offline full builds, independent semantic
acceptance and local fingerprint checks, then canonical ten path/hash comparison.
A separate disposable export of the frozen Git-known source uses a distinct external
GRADLE_USER_HOME populated only with existing dependency/wrapper acquisition caches,
not this checkout's build outputs, test XML, Kotlin script caches or publication
manifest. Strict verification remains active. Clean unsigned staging independently
reconstructs all ten expected hashes, including Javadocs, in a different filesystem
path. The ignored consumer-metadata directory starts absent and is created securely.
No earlier Actions artifact, remote byte, release body or caller manifest supplies
those expected hashes. The export is a generated fixture, not another worktree/repo.

Each accepted repository validates its own index/sidecar-inclusive fingerprint.
Different ephemeral repository fingerprints are never compared across builds. No
artifact bytes are normalized, ignored or dropped. Final artifact table and actual
results are appended after the frozen executions complete.

## 11. Central remote verifier

`RemoteArtifactVerifier`, its bounded streaming HttpQueries transport and accepted
inventory/fingerprint model survive. All ten remote bytes must exactly hash to fresh
independently reconstructed local expectations; all ten adjacent `.asc` files require
complete bounded acquisition and both ASCII armor markers. This is evidence of
signature-file presence, not independent OpenPGP trust/key/cryptographic verification.
No trust subsystem was added.

Preserved: 40-minute overall deadline, maximum 20-second requests, monotonic elapsed
budget, 10/20/40/60-second bounded backoff, only 404 pending, and fail-closed hash,
marker, status, network, truncation, size and timeout handling. Polling never grants
permission to upload. The permanent remote/acquisition negative suite is retained.

## 12. Independent consumers and verification isolation

Actions executes actual commands with two fresh distinct external homes:

```sh
./gradlew -p publishing/consumer-smoke --no-daemon --no-build-cache \
  --no-configuration-cache --dependency-verification=strict -PremoteCentral clean check
./gradlew -p publishing/consumer-smoke --no-daemon --no-build-cache \
  --no-configuration-cache --dependency-verification=strict -PremoteCentral -PpomOnly clean check
```

The unchanged consumer settings/build/locks/template preserve canonical Central-only
remote resolution, no local fallback/composite/source substitution, exact NIO/core
compile graph and Bouncy Castle 1.86 runtime graph, strict verification and compile/run
checks. Local module/POM modes pass as separate host evidence; live remote consumers
were not run. Actual workflow regression checks require both commands/homes/modes,
strict options, order, read permissions and successful verify-job dependency before
finalization. The Java consumer executor/receipt abstraction is removed.

Verify Release declares no secrets, signing mode, Central upload task, release
environment, original-run artifact download or expected caller inventory. Its
unsigned local staging task cannot expose a Central uploader in ordinary Gradle mode.
The standalone Java classpath contains no Gradle uploader/controller or production
classes. A separate write-only finalization job receives no publishing capabilities.

## 13. Narrow GitHub finalization

`ReleaseFinalizer.finish` authenticates the existing annotated tag, independently
constructs reviewed notes/provenance, queries exact release-by-tag, and either
validates an exact existing release without mutation or creates an absent release
once. It validates creation response, reauthenticates tag and validates fresh readback.
Require exact tag/title, published/non-prerelease state and reviewed body under the
established CRLF/trailing-whitespace normalization. Do not use target_commitish as
source proof. No tag creation/push/update/deletion, release update/delete or asset
overwrite exists in this operation. Conflicts/ambiguous creation stop; a later explicit
verification run may observe and accept the exact existing release.

Tests cover absent/exact/conflicting releases, missing/lightweight/wrong-object/
wrong-annotation/wrong-commit tags, ambiguous creation and later exact observation,
postcondition failures, process deadlines/overflow and sanitized secret diagnostics.

## 14. Permissions, environments and credentials

Defaults are contents:read. Explicit job permissions are preflight read; publishing
write; reusable-call write ceiling; verify read; finalize write. Only publishing
uses `environment: release`. Its secrets appear in one runtime step after all Nix/
bootstrap/build work. The verifier/caller/finalizer declare/inherit no Maven/signing
secrets. No actions-write, packages-write, OIDC or repo-wide credential fallback
configuration was added. Required secret presence is checked without logging values;
Gradle owns actual key/password validation. Optional empty signing properties are unset.

HTTP mutation credentials enter curl stdin/config only, not argv, URLs, files or
captured diagnostics. Child environments are cleared; outputs are bounded and errors
static. Source/tag Github reads use only runtime operation-scoped tokens. Configuration
scope/organization fallback and effective environment protection require owner access;
workflow source cannot prove where a secret was configured.

## 15. Reviewed Nix bootstrap and pin evidence

Revalidated inert upstream text from the exact install-nix-action commit
`13d8dd58da0234aa297dedd986986ccb8e7f3e24` (v31.11.1):
[action metadata](https://raw.githubusercontent.com/cachix/install-nix-action/13d8dd58da0234aa297dedd986986ccb8e7f3e24/action.yml),
[installation script](https://raw.githubusercontent.com/cachix/install-nix-action/13d8dd58da0234aa297dedd986986ccb8e7f3e24/install-nix.sh).
Downloaded raw-text SHA-256 values:
`46d90d7cd0453aefd350f837a57f669199227668564b10ad58a05bbf862a3bf0`
(action.yml),
`836671507d9b4ea84252f968bd0622c5c2fa72f5deea93f5764f1c60821062f6`
(install-nix.sh).
The [official Nix 2.35.2 installer](https://releases.nixos.org/nix/nix-2.35.2/install)
again hashes to `9adda97297d9e8ab360df95c729eabff4f4f93d6db091953c3a68f29e3fb130c`
and embeds x86_64-linux tarball hash
`0c3960a9792331a22081c3c7a5d8465db9b17c50b3acdf18587fa4c6f2cb1158`.
The tarball/installer were not executed by the agent.

Both release workflows and CI use short explicit setup blocks: reject preinstalled
Nix, checksum the versioned installer, supply its local file URL to the pinned action,
require installed Nix identity, remove token declarations from user/daemon config,
restart daemon and require effective empty access-tokens before flake evaluation.
The named-setting output contract for the empty-token assertion was independently
confirmed in [pinned Nix 2.35.2 source](https://raw.githubusercontent.com/NixOS/nix/2.35.2/src/nix/config.cc):
the specific setting prints its value, rather than a key/value record. No Nix
command was executed for that inspection. The pinned composite internally supplies github.token; this behavior is explicitly
handled rather than assumed absent. Build environments scrub tokens/Gradle secret
properties and use locked Git-aware commands with no lock writes/updates.

`packages.tooling` aliases the existing standalone launcher. `release-runtime` joins
that launcher, independent wrapper verifier, pinned JDK/git/curl/CA utilities from
unchanged nixpkgs. Runtime JAVA_HOME/launcher/Git/curl/CA paths derive from that output.
No Gradle-dependent Java compilation, new JSON library or second downloader/compiler
framework exists. Hosted installation/daemon behavior remains unexecuted live evidence;
structural/pin checks and host builds do not pretend to be an Actions installation.

## 16. Java retained, simplified and removed

Retain/simplify fifteen release owners: ReleaseInputs, SourceFiles, SourceIdentity,
GitQueries, RemoteReleaseState, HttpQueries, ReleaseInspectionCli, AcceptedInventory,
AcceptedArtifacts, EvidenceFiles, TestResults, RemoteArtifactVerifier, WorkflowPins,
ReleaseFinalizer and FinalizationProcesses. Retain StrictJson and all six independent
publication owners; PublicationCli gains narrow approved Gradle output destinations
and secure creation of missing evidence directories. Retain the independent wrapper
verifier. Main exposes focused commands, with sanitized diagnostics.

Completely remove ten production classes: FreshRemoteMain, PublicationPolicy,
ReleaseWorkflowCoordinator, RecordedServices, GitHubOutputs, CentralConsumers,
PreparationPlan, PreparationApplication, SecretPreflight and GpgPacketInspector.
No equivalent coordinator/recovery/preparation state machine was recreated.

## 17. Python and migration removals

Four operational files removed (754 lines): publishing/release.py,
publishing/publication.py, publishing/verify-publication.py, nix/verify-wrapper.py.
Three Python test files removed (717 lines): publishing/test_release.py,
publishing/test_prepare.py, publishing/test_publication.py.
Six parity scripts removed (1,161 lines): tooling/publication-parity.py,
release-parity.py, authorization-parity.py, completion-parity.py, accounting-parity.py
and workflow-output-parity.py.
Five Java oracle bridges removed (212 lines): publication/AcceptedFixture and
release/ReleaseFixture, AuthorizationFixture, CompletionFixture, WorkflowOutputFixture.
The tiny accepted semantic fixture factory moves into permanent PublicationTest;
no independent verifier implementation or acceptance authority is duplicated.
Optional publication/accounting migration exports and cross-language expectation
fields are removed. Python-source wrapper pin comparisons become independent Java
constant/hash assertions. Historical reports remain unchanged.

## 18. Deleted tests and preserved negative coverage

Remove ReleaseWorkflowIntegrationTest, CentralConsumersTest,
PreparationApplicationTest, FakeGpg and obsolete WorkflowEvidenceTest (533 lines).
Their coordinator/history/recovery, preparation mutation and packet-parser behaviors
are intentionally gone. Port shared cleanup to retained AuthorizationTest; keep
independent semantic fixtures in PublicationTest.

Permanent suites now test focused source/input/version/metadata/notes/pin checks;
strict JSON/bounded canonical endpoints; create-only tag races/ambiguity; exact release
conflicts; artifact inventory/path/hash/fingerprint substitution; all ten signing
inputs; symlink/nonregular/size/depth/count limits; full/focused XML accounting;
remote bytes/signature acquisition/deadlines; actual process redaction/stdin credentials;
actual workflow pins, activation, permissions, one-invocation order, consumer/home
isolation and failure propagation. The workflow test executes the real precheckout
shell against injected invalid inputs, preserving exact output-injection rejection.
Publication tests also exercise a fresh missing consumer-metadata directory and
reject unapproved in-checkout evidence outputs. No new coordinator simulation exists.

Final standalone results: StrictJson 43 assertions; PublicationTest 137; source/input
40; signing/inventory 141; TestResults 29 with 26 accounting fixtures; tag/finalization,
remote/acquisition, process isolation, action pins and actual workflow suites PASS.
Wrapper: 199 fixtures (1 accepted/198 rejected), 190 raw-declaration cases, API/CLI
and additional argument/path probes PASS. Independent production/Gradle import
rejection is checked separately. All final qualification compilation uses --release 25, UTF-8,
-Xlint:all -Werror -proc:none and explicit independent classpaths/sourcepaths.

## 19. Complete active Python scan

No extant repository Python source, active interpreter command/import/caller or
Python source-data dependency remains. Scanned hidden workflows, Nix, Gradle,
Java main/tests, development-shell packages, CLI wiring and active documentation.
`rg --files --hidden -g '*.py'` returns no source paths. Git-known deleted paths
still appear in `git ls-files` until owner commit; extant-file filtering confirms
zero remaining Python files. A full tracked text occurrence inventory is preserved
outside the checkout.

Remaining nonhistorical mentions are the explicit removal/transitive-infrastructure
notes in README/AGENTS, provenance comments about former tests, and preserved
serialization/Unicode-whitespace compatibility semantics (`pythonStrip`,
`pythonWhitespace`, Python-compatible JSON/rstrip). These invoke no interpreter.
Unchanged source-filter exclusions for cache/pyc files are not runtime dependencies.
Third-party installer/runner/agent infrastructure may use Python internally; this
milestone removes repository release tooling, not every transitive executable.

## 20. Documentation and owner settings

README, RELEASE_CHECKLIST, AGENTS and API_DESIGN describe the intended
post-activation one-dispatch experience, version reservation before upload,
verification-only recovery, exact source reachability, accepted plugin retries,
Java qualification replacements and inert activation boundary. Obsolete Python
commands, automated preparation and same-run upload recovery/manual fallback are
removed. Routine qualification and separate release reproducibility remain distinct.

Owner-operated configuration is detailed in RELEASE_CHECKLIST.md. Require reviewed
main changes, successful CI's build job (CI / build), no force-push/deletion and the
chosen no-bypass policy. Configure an active `v*` tag ruleset preventing updates and
deletion without publishing-actor bypass, while allowing the workflow token's
first-time creation. Confirm effective rules permit creation but deny later mutation
for that same actor; if they cannot, stop before activation. Restrict the release
environment to protected main; keep signing/Central secrets environment-only, with
no repository/organization fallback. Respect effective organization protections
without adding an otherwise unnecessary approval step. Keep default tokens read-only
and the explicit job permission boundaries above. No repository settings were changed.
Public J4S observations are not a privileged audit of effective settings or secrets.

## 21. Exact disabled activation guards

`release.yml` → `jobs.publish-central.if` is literal:
`${{ false && github.run_attempt == 1 }}`.
`verify-release.yml` → `jobs.finalize.if` is literal `${{ false }}`.
All real tag creation, signing and Central upload commands are inside the first
job; all GitHub Release creation commands are inside the second. No repository
variable/environment toggle can enable them. Missing configuration cannot enable
mutation. Preflight/standalone verification remain read-only. Actual workflow tests
reject enabled/removed guards, alternate needs/paths and inherited publishing secrets.

A separately authorized source activation change must replace the first guard with
`${{ github.run_attempt == 1 }}` and the second with `${{ success() }}`, update the
activation assertions and qualify that exact change after owner settings review.
Preserve attempt checks, first-time create-only reservation, source/reachability,
signing-input, credentials, strict verification and consumer dependencies. Merging
J4B alone activates none of these mutations.

## 22. Host qualification and execution accounting

Frozen final results/invocation totals are appended below. Count invocations separately
from executed/up-to-date tasks, synthetic fixture calls and nested consumer tasks.
Full XML/accounting is preserved immediately before clean/focused runs can overwrite it.
The full normal/offline boundaries include complete tests, build/Javadocs, independent
publication verification, module consumer, Java 17 output and strict dependencies.
Independent POM consumer and focused snapshot/profile/all-99-hash checks are additional.

Failures/reruns are explicit. Initial host publication failed because the temporary
launcher used unavailable /usr/bin/env; fixing its external shebang yielded a pass.
The first fresh exported checkout exposed missing ignored consumer-metadata parents;
PublicationCli now creates them with anchored NOFOLLOW operations, with a permanent
regression. A default focused build exposed script capture in the new onlyIf guard;
local captured values and configuration-time Exec arguments fix serialization.
Default publication configuration-cache store and reuse both pass. An extra root
clean publication build ran with the reconstruction home while correcting a working-
directory selection; it is counted separately, not claimed as exported reconstruction.
One earlier tool startup failed before Gradle because its not-yet-created working
directory was selected; it is not a build invocation. Failed logs/XML/configuration-
cache report and exported staging were preserved outside build outputs.

An early passing normal/offline pair was invalidated by those included integration
fixes. A renewed passing pair was followed by final source/tag freshness and scrubbed
CA-path refinements; the final frozen pair is the handoff authority. No earlier pass
substitutes for it. The shared signing-check refinement precedes the renewed/frozen
pairs. A deliberate missing-signature Gradle check fails before any uploader/signing
action, as an expected negative test; producer freeze probes skip all scheduled
producers and preserve their exact hashes. No gate was weakened to obtain a pass.

## 23. Human-owned Nix and dependency/cache accounting

Agent Nix invocations: **zero**. Human J4B Nix results supplied: **three failures
followed by one PASS**, as reported by the owner. See sections31–34.
The completed human gate is:

```sh
nix flake check
```

Do not add nix build as a second qualification gate. No dependency versions,
verification metadata, Gradle locks, flake.lock or package-deps bytes changed; no
cache updater or lock/toolchain refresh is required or was run. Runtime utilities
come from existing locked nixpkgs. The package-deps human cache-update checkpoint
is required only if dependency inputs genuinely change later.

Unchanged hashes: flake.lock
`44728fcfd8529462cb415b186ee4ce3400343dd911d325b6a965bd386b9f7749`;
package-deps.json `ee87ee929d064232e30b0bb91c8aaa4a249ae5f0fad7d85180a9833709f6a879`;
Gradle verification metadata
`383e4bc19e0b17b635408740f9ebf1681842ae8203a102baab4c831d9ec1b16a`.
No unperformed human check is reported as passed.

## 24. Git-aware source freeze

Every intended new file (verify workflow, permanent workflow-boundary test, this
report) is intent-to-add only. `git diff --cached --quiet` passes; no contents are
staged. Deleted paths are accounted for and no cache/generated output/secret/evidence
is included. qualification.nix's existing source filter is unchanged. It selects
core/storage/gradle/publishing/nix plus its explicit build/version/pin files and
excludes review/ and process Markdown. tooling.nix independently selects Java and
read-only spec/workflow/build/settings fixtures; wrapper-verifier.nix selects its
Java and exact wrapper inputs. flake.nix/cache/lock/helper files also determine inputs.
Workflow changes now affect the tooling guardrail fixture; freeze them too.

Conservative frozen inventory: 323 Git-known regular non-review files, including
excluded process Markdown as a superset, recorded in external final-frozen-inputs.json.
Inventory SHA-256:
`d99868f61af1169a7e28c3534a5f97e9ccb07a5f417969e6d1567b454f516979`.
The earlier inventory was superseded only for RemoteReleaseState and ReleaseTest:
a final strict-JSON audit found that unknown numeric values were represented as
strings, which could let an all-numeric SHA field appear string-typed. Numeric
values now remain Number values; a permanent numeric-SHA rejection regression
passes. The full standalone guardrail gate was rerun. This release-tool-only
correction changes no library, Gradle or independent publication-verifier input,
so the completed artifact/test/consumer gates remain valid under the scoped
release-helper revalidation policy. No human Nix result existed at that point.
The subsequent human failure required the locale-only nix/tooling.nix correction
in section31; the inventory above includes that correction and supersedes the
pre-locale inventory `13fdf581448892badc5a9fcb401bd40df3dd1c585227f13eec03906085371688`.
The subsequent TMPDIR launcher correction in section32 supersedes the locale-only
inventory `6d14a02ab194bec1347c909c3640216e80319e6cf09fc2fdad73c5b703e5cf96`.
The install-copy TMPDIR correction in section33 subsequently supersedes inventory
`7f0faa937b7bf790b9180cee41cba92c47ca3cd52cb35f3d1f1c161c6389ae3f`.
Both checkout and exported source now match every final frozen hash/mode.
Only this confirmed-excluded report may be finalized after that freeze. Final
post-check rehash matches every file/mode (section34). Any included edit after the completed
human Nix gate invalidates it and requires renewed qualification.

## 25. Remaining unverified live behavior

Not run: hosted pinned Nix installation/daemon bootstrap, protected GitHub token
creation/immutability enforcement, live tag authentication/mutation, real signing,
Central upload/plugin retry behavior, live Central byte/signature/consumer execution,
GitHub Release mutation or operational activation. Effective privileged settings and
secret scope remain owner prerequisites. Local fixtures and structural checks do
not prove live service availability/authorization. Human Nix passed as reported
by the owner (section34); this does not qualify live release behavior.

## 26. Actual source/file/line accounting

Actual deletion counts, final source line totals and diff totals are appended below.
Whole-file removals are ten Java production classes, ten Java test/helper/oracle
files and thirteen Python files: 33 paths. The removed whole-file bodies total
4,075 lines (698 production Java; 754 operational Python; 717 Python tests; 1,161
parity scripts; 212 Java bridges; 533 obsolete Java suites/helpers). Retained-file
simplification and new focused workflow/test code are counted separately in Git's
net totals. The J4S 4,000–4,600 estimate is not an acceptance threshold.

## 27. Final Git state and complete inventory

Starting branch/HEAD remain unchanged. Final status/diff inventory, staged-content
proof and post-freeze checks are appended below. No commit, tag, push, real signing,
Central publication invocation, GitHub Release creation, live dispatch or settings
change occurred. New source/report membership is intent-to-add only.

## 28. Deviations and disposition

No architectural deviation: two workflows, Gradle-owned automatic publication,
create-only version reservation, credential-free independent verification, narrow
release finalization, Python/coordinator removal and source-controlled inert guards.
Implementation refinements are the secure fresh-directory integration, configuration-
cache-compatible captures, one shared pre-copy signing-input check, explicit pinned
CA propagation and post-sign source/tag reauthentication. Required host reruns and
extra independent reconstruction are accounted rather than hidden. There is no known
remaining host blocker. Frozen host checks and the owner-reported human Nix gate
are complete. Stop after implementation/qualification;
activation and a real release require separate authorization.

## 29. Authoritative frozen host results and invocation totals

**PASS — frozen normal:** 53 seconds; 30 outer tasks executed, with nested isolated
module consumer 4 seconds/5 executed. **PASS — frozen forced offline:** 62 seconds;
30 outer tasks executed, nested module consumer 5 seconds/5 executed. Each full
suite actually executes 405 tests, zero failures/errors/skips, snapshot3/3,
profile1/1 and explicit 92-case exact-set execution. Immediate full accounting and
complete core/storage XML are preserved in the frozen-normal/offline evidence
folders before focused output replacement. Normal/offline/export hashes match
10/10/10. Local POM consumer: 876ms,5 tasks executed. Fresh exported reconstruction:
15 seconds,20 tasks executed; distinct external Gradle home, no original build output.
The earlier first execution in that fresh home had 17 producers executed and three
clean tasks up-to-date because there was nothing to clean; it is not called a cached
producer build.

**PASS — focused accounting and snapshot:** final focused command succeeds in
776ms:1 task executed,1 from cache,8 up-to-date. Its four selected tests are restored
from Gradle's matching cache, not claimed freshly executed. Fresh full suites above
already execute all snapshot/profile tests. Focused XML/accounting accepts4 tests,
zero failure/error/skip, snapshot3/3 and profile1/1. All99 snapshot files are freshly
SHA-256 checked. No focused XML is substituted for full-suite evidence.

**PASS — independent tool boundaries:** final direct JDK25 compilation/permanent
runners; real-checkout action/workflow tests; wrapper's final explicit empty-classpath
compilation and complete runner; Main smoke; six unknown/invalid/removed-command
exit probes; forbidden production/Gradle imports rejected. Real manifest checks and
Java copy pass; independent destination rehash proves exactly ten canonical copied
paths/hashes, without indexes/signatures. No source or staging substitution is ignored.

| Invocation category | Actual count and outcome |
| --- | --- |
| Broad baseline `./gradlew test` | 1 PASS;28s,13 tasks:2 executed/11 up-to-date; both module tests up-to-date; existing full XML independently accounted405 |
| Pre-cutover full Python baseline | 1 PASS,51 tests; focused Python invocations0; post-cutover Python invocations0 |
| Standalone main/test javac | 11/11 PASS:one initial compile pair plus ten full independent iteration/final pipelines |
| Full standalone permanent-runner pipelines | 10 PASS; includes the locale and TMPDIR follow-ups in sections31–32; synthetic fixture calls are not real release invocations |
| Wrapper javac / full fixture runner | 11/11 PASS:ten pipeline iterations plus explicit empty-classpath wrapper qualification (unchanged wrapper inputs) |
| Focused production Gradle test selections | 3:initial tests executed but build FAIL storing configuration cache; retry PASS/up-to-date; final PASS/from-cache as described |
| Focused root publication producer invocations | 5:initial launcher FAIL; corrected PASS; extra root clean build PASS; default configuration-cache store PASS; reuse PASS |
| Full normal Java gates | 3 PASS:72s,57s,53s; earlier two superseded by included fixes/refinements; final53s authoritative |
| Separate host forced-offline full gates | 3 PASS:58s,59s,62s; preserve corresponding immediate accounting; final62s authoritative |
| Release normal/offline comparison pairs | 3 PASS; first two superseded; final ten-hash pair authoritative |
| Independent exported producer invocations | 4:missing generated-directory FAIL, corrected PASS, fresh-home PASS, frozen-source PASS; final source/input hashes match the checkout |
| Total real Gradle verifyPublication attempts | 15:6 nested full gates +5 focused root +4 exports;13 PASS/2 FAIL; these are unsigned local publication checks |
| Local module consumer invocations | 6 PASS, nested in six full gates; each nested build5 tasks executed, not another full Java suite |
| Independent local POM-only consumer | 2 PASS:6s and876ms; final one authoritative |
| Gradle freeze/binding probes | 2 expected successes:producers skipped with hashes unchanged; missing signing evidence rejected, both Sign actions skipped and no uploader selected |
| Ordinary task inventory | 1 PASS;no Central/signing task exposed in ordinary mode |
| Real standalone publication manifest checks / copy | 9/2 PASS;copies independently rehashed and exact ten-file inventories checked; install-boundary follow-up also has two expected cross-filesystem failures (section33) |
| Final snapshot checksum pass / focused accounting | 1/1 PASS;99 files and4 selected XML tests |
| Full-suite accounting | 7 PASS:baseline +six full suites; full XML preserved before overwrite |
| Host forbidden-import compiler probe | 1 expected rejection;diagnostic temporary helper compile1, not qualification code |
| Agent Nix / supplied human J4B Nix / cache updater | 0/3 failed then 1 PASS/0;owner reported final human gate PASS |
| Live Central consumers / remote release verification | 0/0;fake acquisition suites and workflow commands checked, no live PASS inferred |
| Real signing / Central publication / tag mutation / GitHub Release / live dispatch / settings mutation / commit / push | 0 each |

These counts describe invocations rather than fresh execution of up-to-date tasks.
The shared missing-signature check selected two Sign dependencies but both were
SKIPPED; it executed no signing action. Only existing pinned Gradle acquisition
caches were reused/copied; no fixed cache/lock was regenerated. Main CLI negative
probes fail before mutation adapters can be reached.

Final canonical path/hash pairs (identical normal, offline and independent export):

```text
org/totipo/totipo-core/0.2.0/totipo-core-0.2.0.jar	1b628743548c376c2ae85ece2c2f9be43fc7364e13fc0b4d4d25e3d49ee6d864
org/totipo/totipo-core/0.2.0/totipo-core-0.2.0-sources.jar	eee068cd5a700f66cab212ccaa728c7d1f04c981d31648c1e4e90e826d1e8b93
org/totipo/totipo-core/0.2.0/totipo-core-0.2.0-javadoc.jar	aa0960585fc5bd0c4c5615b754f7d259d9499776e133ca1f8298250af8dcf8df
org/totipo/totipo-core/0.2.0/totipo-core-0.2.0.pom	1a6bbd5b4d82c079cb621d66c89b446c09e2df33efe5e0e89dd5abfd35d7c136
org/totipo/totipo-core/0.2.0/totipo-core-0.2.0.module	28fcefec7f89ca69b651beb86afb25d07af5933da2f29e8fd5bdb5776b9d227c
org/totipo/totipo-storage-nio/0.2.0/totipo-storage-nio-0.2.0.jar	776068249e689e94136748fb8ffd86c837e0af8bbb6cec8ae5e785c4eca4ba93
org/totipo/totipo-storage-nio/0.2.0/totipo-storage-nio-0.2.0-sources.jar	bb690c98837f938b1cce75cdfc06b44b47b066c4cb937a4ec461f2dc82f5b04b
org/totipo/totipo-storage-nio/0.2.0/totipo-storage-nio-0.2.0-javadoc.jar	ef1b8cf0a278eb64e0cb9073e627debeba298f0fd400378fadfdbf8cd9c7ac7c
org/totipo/totipo-storage-nio/0.2.0/totipo-storage-nio-0.2.0.pom	4acf1725ac7bb5cd7a4299f729d5eceb82aa3ea524dca7c9d5a9b56616db87b2
org/totipo/totipo-storage-nio/0.2.0/totipo-storage-nio-0.2.0.module	33a431575523bb545b57876b09fd04255e04b60b2c64541d72b2cda5f05c7afa
```

Distinct local repository fingerprints (each locally accepted, never compared as
cross-build identity): normal
`47d7c29cc0123ace4643126bfe12a528947e5f1a9c30b4f66bc3dd8d537fa5b1`,
offline `bd43a4b73983299f60b878c57f3bb36f7e8071703c6bad7bfc0075dd3d28918b`,
export `516d8eea7a0412d86e3b616bb3234daebf04e2917a7b416c6160f970dbf12270`.

## 30. Measured final source reduction and Git evidence

| Java source scope | Before files/lines | After files/lines |
| --- | --- | --- |
| Release production | 25/2112 | 15/1169 |
| Release tests | 17/1795 | 9/795 |
| All main tooling Java | 33/2965 | 23/2028 |
| All test tooling Java | 20/2235 | 11/1228 |

Release rows are subsets of all-tooling rows, not additive. Wrapper verifier
production is unchanged; its tests remove the source-data oracle dependency.
Non-report Git diff: **68 paths, 1,371 insertions, 5,811 deletions; net 4,440 lines
removed**. This includes workflows/Nix/Gradle/process documentation as well as code;
it does not pretend every removed line is production code. This excluded report's
new prose is deliberately separated from that implementation count. There are
33 whole-file deletions,33 existing-file modifications and3 new intent-to-add files
including this report:69 intended paths total. No unintended/untracked path exists.

Final branch/HEAD: main at `6df11d9efa0b47a125bd9723c6ccec68c95f5ac6`.
`git diff --check` and `git diff --cached --quiet`:PASS.
All323 frozen file hashes/modes match, including the separately exported source.
Protected bytes remain unchanged. No staged content, generated/cache/secret input,
release mutation or Git commit/push is present.

Exact `git status --short --untracked-files=all`:

```text
 M .github/workflows/ci.yml
 M .github/workflows/release.yml
 A .github/workflows/verify-release.yml
 M AGENTS.md
 M API_DESIGN.md
 M README.md
 M RELEASE_CHECKLIST.md
 M build.gradle.kts
 M flake.nix
 M nix/tooling.nix
 D nix/verify-wrapper.py
 M nix/wrapper-verifier.nix
 D publishing/publication.py
 D publishing/release.py
 D publishing/test_prepare.py
 D publishing/test_publication.py
 D publishing/test_release.py
 D publishing/verify-publication.py
 M qualification.nix
 A review/J4B_SIMPLIFIED_RELEASE_CUTOVER_REPORT.md
 D tooling/accounting-parity.py
 D tooling/authorization-parity.py
 D tooling/completion-parity.py
 D tooling/publication-parity.py
 D tooling/release-parity.py
 M tooling/src/main/java/org/totipo/tooling/Main.java
 M tooling/src/main/java/org/totipo/tooling/publication/PublicationCli.java
 D tooling/src/main/java/org/totipo/tooling/release/CentralConsumers.java
 M tooling/src/main/java/org/totipo/tooling/release/FinalizationProcesses.java
 D tooling/src/main/java/org/totipo/tooling/release/FreshRemoteMain.java
 D tooling/src/main/java/org/totipo/tooling/release/GitHubOutputs.java
 M tooling/src/main/java/org/totipo/tooling/release/GitQueries.java
 D tooling/src/main/java/org/totipo/tooling/release/GpgPacketInspector.java
 M tooling/src/main/java/org/totipo/tooling/release/HttpQueries.java
 D tooling/src/main/java/org/totipo/tooling/release/PreparationApplication.java
 D tooling/src/main/java/org/totipo/tooling/release/PreparationPlan.java
 D tooling/src/main/java/org/totipo/tooling/release/PublicationPolicy.java
 D tooling/src/main/java/org/totipo/tooling/release/RecordedServices.java
 M tooling/src/main/java/org/totipo/tooling/release/ReleaseFinalizer.java
 M tooling/src/main/java/org/totipo/tooling/release/ReleaseInputs.java
 M tooling/src/main/java/org/totipo/tooling/release/ReleaseInspectionCli.java
 D tooling/src/main/java/org/totipo/tooling/release/ReleaseWorkflowCoordinator.java
 M tooling/src/main/java/org/totipo/tooling/release/RemoteReleaseState.java
 D tooling/src/main/java/org/totipo/tooling/release/SecretPreflight.java
 M tooling/src/main/java/org/totipo/tooling/release/SourceFiles.java
 M tooling/src/main/java/org/totipo/tooling/release/SourceIdentity.java
 M tooling/src/main/java/org/totipo/tooling/release/WorkflowPins.java
 D tooling/src/test/java/org/totipo/tooling/publication/AcceptedFixture.java
 M tooling/src/test/java/org/totipo/tooling/publication/PublicationTest.java
 D tooling/src/test/java/org/totipo/tooling/release/AuthorizationFixture.java
 M tooling/src/test/java/org/totipo/tooling/release/AuthorizationTest.java
 D tooling/src/test/java/org/totipo/tooling/release/CentralConsumersTest.java
 D tooling/src/test/java/org/totipo/tooling/release/CompletionFixture.java
 M tooling/src/test/java/org/totipo/tooling/release/FakeFinalizationCli.java
 D tooling/src/test/java/org/totipo/tooling/release/FakeGpg.java
 M tooling/src/test/java/org/totipo/tooling/release/FinalizationProcessTest.java
 M tooling/src/test/java/org/totipo/tooling/release/FinalizationTest.java
 D tooling/src/test/java/org/totipo/tooling/release/PreparationApplicationTest.java
 D tooling/src/test/java/org/totipo/tooling/release/ReleaseFixture.java
 M tooling/src/test/java/org/totipo/tooling/release/ReleaseTest.java
 D tooling/src/test/java/org/totipo/tooling/release/ReleaseWorkflowIntegrationTest.java
 M tooling/src/test/java/org/totipo/tooling/release/RemoteVerificationTest.java
 M tooling/src/test/java/org/totipo/tooling/release/TestResultsTest.java
 A tooling/src/test/java/org/totipo/tooling/release/WorkflowBoundaryTest.java
 D tooling/src/test/java/org/totipo/tooling/release/WorkflowEvidenceTest.java
 D tooling/src/test/java/org/totipo/tooling/release/WorkflowOutputFixture.java
 M tooling/src/test/java/org/totipo/tooling/release/WorkflowPinsTest.java
 D tooling/workflow-output-parity.py
 M tooling/wrapper/src/test/java/org/totipo/tooling/wrapper/WrapperVerifierTest.java
```

Exact `git diff --name-only`:

```text
.github/workflows/ci.yml
.github/workflows/release.yml
.github/workflows/verify-release.yml
AGENTS.md
API_DESIGN.md
README.md
RELEASE_CHECKLIST.md
build.gradle.kts
flake.nix
nix/tooling.nix
nix/verify-wrapper.py
nix/wrapper-verifier.nix
publishing/publication.py
publishing/release.py
publishing/test_prepare.py
publishing/test_publication.py
publishing/test_release.py
publishing/verify-publication.py
qualification.nix
review/J4B_SIMPLIFIED_RELEASE_CUTOVER_REPORT.md
tooling/accounting-parity.py
tooling/authorization-parity.py
tooling/completion-parity.py
tooling/publication-parity.py
tooling/release-parity.py
tooling/src/main/java/org/totipo/tooling/Main.java
tooling/src/main/java/org/totipo/tooling/publication/PublicationCli.java
tooling/src/main/java/org/totipo/tooling/release/CentralConsumers.java
tooling/src/main/java/org/totipo/tooling/release/FinalizationProcesses.java
tooling/src/main/java/org/totipo/tooling/release/FreshRemoteMain.java
tooling/src/main/java/org/totipo/tooling/release/GitHubOutputs.java
tooling/src/main/java/org/totipo/tooling/release/GitQueries.java
tooling/src/main/java/org/totipo/tooling/release/GpgPacketInspector.java
tooling/src/main/java/org/totipo/tooling/release/HttpQueries.java
tooling/src/main/java/org/totipo/tooling/release/PreparationApplication.java
tooling/src/main/java/org/totipo/tooling/release/PreparationPlan.java
tooling/src/main/java/org/totipo/tooling/release/PublicationPolicy.java
tooling/src/main/java/org/totipo/tooling/release/RecordedServices.java
tooling/src/main/java/org/totipo/tooling/release/ReleaseFinalizer.java
tooling/src/main/java/org/totipo/tooling/release/ReleaseInputs.java
tooling/src/main/java/org/totipo/tooling/release/ReleaseInspectionCli.java
tooling/src/main/java/org/totipo/tooling/release/ReleaseWorkflowCoordinator.java
tooling/src/main/java/org/totipo/tooling/release/RemoteReleaseState.java
tooling/src/main/java/org/totipo/tooling/release/SecretPreflight.java
tooling/src/main/java/org/totipo/tooling/release/SourceFiles.java
tooling/src/main/java/org/totipo/tooling/release/SourceIdentity.java
tooling/src/main/java/org/totipo/tooling/release/WorkflowPins.java
tooling/src/test/java/org/totipo/tooling/publication/AcceptedFixture.java
tooling/src/test/java/org/totipo/tooling/publication/PublicationTest.java
tooling/src/test/java/org/totipo/tooling/release/AuthorizationFixture.java
tooling/src/test/java/org/totipo/tooling/release/AuthorizationTest.java
tooling/src/test/java/org/totipo/tooling/release/CentralConsumersTest.java
tooling/src/test/java/org/totipo/tooling/release/CompletionFixture.java
tooling/src/test/java/org/totipo/tooling/release/FakeFinalizationCli.java
tooling/src/test/java/org/totipo/tooling/release/FakeGpg.java
tooling/src/test/java/org/totipo/tooling/release/FinalizationProcessTest.java
tooling/src/test/java/org/totipo/tooling/release/FinalizationTest.java
tooling/src/test/java/org/totipo/tooling/release/PreparationApplicationTest.java
tooling/src/test/java/org/totipo/tooling/release/ReleaseFixture.java
tooling/src/test/java/org/totipo/tooling/release/ReleaseTest.java
tooling/src/test/java/org/totipo/tooling/release/ReleaseWorkflowIntegrationTest.java
tooling/src/test/java/org/totipo/tooling/release/RemoteVerificationTest.java
tooling/src/test/java/org/totipo/tooling/release/TestResultsTest.java
tooling/src/test/java/org/totipo/tooling/release/WorkflowBoundaryTest.java
tooling/src/test/java/org/totipo/tooling/release/WorkflowEvidenceTest.java
tooling/src/test/java/org/totipo/tooling/release/WorkflowOutputFixture.java
tooling/src/test/java/org/totipo/tooling/release/WorkflowPinsTest.java
tooling/workflow-output-parity.py
tooling/wrapper/src/test/java/org/totipo/tooling/wrapper/WrapperVerifierTest.java
```

`git diff --cached --name-only`:empty. Intent-to-add entries remain for owner review.
Only this excluded report was finalized after freeze. The final human-owned command
is **`nix flake check`**; three failed human runs were followed by the owner-reported
successful run on the corrected frozen source (sections31–34).
All implementation changes remain uncommitted and live mutation guards remain false.

## 31. Human Nix failure and UTF-8 locale correction

The owner reported a failed `nix flake check`: PublicationTest.jsonCompatibility
could not resolve `a/é`, throwing InvalidPathException at line345. Direct execution
with the pinned JDK25 under `LC_ALL=C` reproduced the exact exception. The default
C locale makes the JVM's native filesystem-path encoding unable to represent the
Unicode fixture, despite UTF-8 source compilation. The fixture intentionally tests
Unicode and control-character filenames and remains unchanged.

nix/tooling.nix's standalone classes derivation now explicitly sets
`LC_ALL = "C.UTF-8"`, matching qualification.nix. No dependency, lock, cache, Java
source, artifact expectation or activation guard changed. This is one additional
source line. No agent Nix command was executed.

Host follow-up: one focused PublicationTest invocation under C failed as expected
(external c-locale-publication.log); one complete standalone main/test compilation
and permanent-runner pipeline under C.UTF-8 passed (utf8-locale-tooling.log), including
all137 publication assertions and199 wrapper fixtures. Counts in section29 include
the additional passing pipeline. Full Gradle/artifact/consumer qualification was
not repeated: the locale correction only changes the standalone tooling derivation's
execution environment, and all affected tooling runners passed with that environment.

The previous freeze is superseded. All323 checkout and exported-source file hashes
match the renewed section24 inventory; only nix/tooling.nix differs from the prior
inventory. The exported artifact build predates this locale-only change; its library
and artifact-producing inputs remain identical. The report is excluded. Git status
and changed-path inventories in section30 remain unchanged; staged contents remain
empty and git diff --check passes. The owner must rerun the single human gate,
`nix flake check`, on this corrected source. Human qualification remains incomplete;
no successful Nix result is inferred from the host reproduction.

## 32. Publication launcher temporary-directory boundary

The owner reported a second failed human gate: Gradle verifyPublication's standalone
launcher exited1. The supplied excerpt contains no underlying verifier diagnostic,
so its precise cause is not confirmed. No passing human gate is claimed.

Inspection exposed a concrete sandbox boundary bug: FilesSupport.directories safely
creates missing evidence directories by moving a private temporary directory through
anchored directory handles. Java defaults java.io.tmpdir to /tmp, ignoring Nix's
TMPDIR. If /tmp and the build checkout occupy distinct mounts, that secure move
fails with AtomicMoveNotSupportedException / Invalid cross-device link. This was
reproduced with /dev/shm as the temporary filesystem and an absent evidence parent
on /tmp, against the independently built ten-artifact staging repository. The failure
occurs before semantic publication verification. No secure-filesystem check was relaxed.

The Nix launcher now explicitly passes `-Djava.io.tmpdir="${TMPDIR:-/tmp}"`, preserving
one quoted argument and the usual fallback outside Nix. Nix's build-local TMPDIR can
therefore supply the temporary directory on the build filesystem. This does not
change Java classes, dependency inputs, the uploader, or mutation activation guards.

Host evidence outside the checkout: cross-filesystem-failure.log records one expected
failure; cross-filesystem-corrected.log records one successful direct CLI verification
with a build-local temporary directory. An expanded equivalent of the updated launcher
then passed one additional real ten-artifact verification and one manifest check,
using a TMPDIR and evidence paths containing spaces. One complete standalone
compilation/permanent-runner pipeline passed again (tmpdir-tooling.log;137 publication
assertions,199 wrapper fixtures). These two direct verifications are additional
standalone invocations, not Gradle publication attempts; the manifest check and
permanent pipeline are included in section29's updated counts. No Gradle full suite
was repeated for this launcher-only environment correction; prior artifact-producing
inputs and artifact evidence are unchanged. Agent Nix invocations remain zero.

The renewed section24 freeze includes this launcher correction. All323 non-review
file hashes match both checkout and exported source; only nix/tooling.nix differs
from the preceding freeze. Git status/path inventory is unchanged, no contents are
staged, and git diff --check passes. The human `nix flake check` remains pending.
If it still fails, the verifier's task output is needed to distinguish this corrected
filesystem boundary from another rejection; this report does not infer the missing
exception from the Gradle exit code.

## 33. Install output filesystem correction

The third reported human failure reached the snapshot checksum pass and installPhase,
then printed `Publication operation failed (details suppressed)`. The install copy
also creates artifact subdirectories through secure atomic directory moves. The
previous launcher fix uses the build's TMPDIR, but the Nix store output can occupy
a different filesystem from that build directory. Thus the previous correction
covered workspace evidence generation but missed the install destination.

Reproduction used the accepted independent ten-artifact repository on /tmp and an
empty output/publication directory on /dev/shm. Copy with TMPDIR on /tmp failed;
one additional direct diagnostic invocation exposed AtomicMoveNotSupportedException
at FilesSupport.directories, PublicationRepository.copy line43: Invalid cross-device
link. These are two expected failing invocations, one through the sanitized launcher
and one through an external diagnostic harness; no repository diagnostics were weakened.
Using the output root as TMPDIR passed the same copy. All ten copied files were
independently SHA-256 checked against the accepted manifest, and the output contained
exactly ten files. Evidence: install-cross-filesystem-failure.log,
install-cross-filesystem-diagnostic.log, install-cross-filesystem-corrected.log and
install-copy-hashes.log, all outside the checkout.

qualification.nix now scopes `TMPDIR="$out"` to its publication-copy invocation.
The existing launcher translates that value to java.io.tmpdir. Temporary directories
and artifact directories therefore share the output filesystem. Secure handles,
NOFOLLOW behavior, empty-destination checks, exact inventory and source/destination
hash revalidation remain unchanged; no cross-filesystem copy fallback was introduced.
The private temporary directories are moved or removed by the existing operation.
No dependency, lock, cache, Java source, artifact producer or activation guard changed.
The source filter is unchanged. No additional full Gradle or compilation pipeline was
run for this install-only environment correction; the focused real copy and independent
hash check exercise the affected operation. Human Nix remains required.

The section24 inventory is renewed for qualification.nix only relative to the prior
freeze. All323 checkout/export file hashes match it. Git status and path inventories
are unchanged; git diff --cached --quiet and git diff --check pass. The owner must
rerun `nix flake check` on this frozen source. Agent Nix invocations remain zero;
human results are three failures and no successful result. The supplied sanitized
output does not expose its exception, so the cross-filesystem diagnosis is supported
by the direct reproduction rather than an underlying exception from the human log.

## 34. Final human qualification handoff

The owner subsequently reported: `nix flake check passed`. This is the fourth
reported human J4B qualification attempt: three failures followed by one successful
gate. No full log or derivation identifier was supplied for the passing run; the
PASS is recorded as owner-reported evidence, not an agent execution. Pending-gate
statements in the chronological follow-up sections31–33 describe their earlier
handoffs and are superseded by this result. No second build or qualification gate
is requested. Agent Nix invocations and cache updater invocations remain zero.

After the report of success, all323 frozen non-review file hashes and modes were
rechecked against the section24 inventory and matched. Git status and diff-path
inventories exactly match the install-fix handoff. `git diff --cached --quiet` and
`git diff --check` pass. Branch remains main at
`6df11d9efa0b47a125bd9723c6ccec68c95f5ac6`. Only this confirmed-excluded report was
updated after the successful gate; no qualified source input changed.

Final source remains69 changed paths, including the three intent-to-add paths,
33 deletions and33 modifications listed in section30; no staged file contents.
External evidence: nix-pass-final-git-status.txt and nix-pass-final-diff-paths.txt.
Both literal-false mutation guards remain present: release.yml publish-central
and verify-release.yml finalize. No commit, tag, push, real signing, Central upload,
GitHub Release, live dispatch or repository-settings change occurred. J4B is ready
for owner review, with operational activation and effective GitHub settings still
requiring separate owner authorization and configuration as documented above.

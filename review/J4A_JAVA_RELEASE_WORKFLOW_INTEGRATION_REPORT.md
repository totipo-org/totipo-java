# J4A — Java release workflow integration and test accounting

Status: **PASS — J4A complete, including human-reported Nix qualification.**
Agent host results and the frozen inventory are recorded below. Python and the protected release workflow remain
authoritative. This is offline integration, not protected-workflow execution or
release-source preparation. All changes remain uncommitted.

Evidence directory: `/tmp/totipo-j4a-evidence/`, outside Git source membership.
No credentials or live release-service observations were used. PASS means
agent-observed unless expressly attributed to the human.

## 1. Starting checkout and classifications

**PASS.** Clean `main`; empty starting `git status --short`; starting HEAD
`0f8b92b40ba4ebbb75b5e131e19fb695cf7e5b7b` (`Remote verification in java`).
J3B.2 implementation/report are committed. Read AGENTS.md before editing, the
J2B/J3A/J3B.1/J3B.2 reports, current controller/publication implementations and
tests, workflow, checklist and Nix definitions. Inspected existing standalone
APIs and fixtures. The checked-out source is authoritative. Owner-reported
J3B.2 CI is prerequisite evidence only, not J4A qualification.

Classifications: **RELEASE_TOOLING**, **DEPENDENCY_BUILD** (standalone Nix wiring).
No dependency acquisition/resolution policy changes. **NOT RELEASE_PREPARATION**:
no real source preparation, VERSION/notes changes or release-source commit.
AGENTS.md defines that classification as explicit release-source preparation;
disposable simulations do not prepare this repository. Release reproducibility
remains mandatory for actual preparation, separately from routine qualification.
Work stayed in this checkout; no sibling sources, worktrees, agents or Nix runs.
API_DESIGN operation/snapshot guidance was consulted; library operations are unchanged.

## 2. Complete controller-command migration map

All commands are implemented by the unchanged `publishing/release.py`; function
names below identify its exact policy owner. Environment names refer to runtime
inputs, never derivation inputs. Common reviewed identity is REQUESTED_VERSION,
REQUESTED_COMMIT, DISPATCH_REF and DISPATCH_SHA. Common source boundaries are
VERSION, SPEC_PIN.md, version-selected reviewed notes and an explicit checkout.
The eventual job graph remains responsible for approval and permissions.

| Command / Python implementation | Java pieces and required adapter | Environment / observable output | Side effects / negative evidence | J4B activation checklist |
| --- | --- | --- | --- | --- |
| `inputs` / `inputs`, `validate_version` | `ReleaseInputs.from`; `GitHubOutputs.inputs` | Common identity; Python command returns identity without Actions output; existing precheckout shell emits version/commit; Java adapter emits those two exact names | Read-only except explicit temporary output append in tests; malformed/SNAPSHOT/newline/uppercase/short SHA/ref/SHA mismatch reject before output | Retain shell before checkout; activate Java input validation only after trusted checkout where useful |
| `identity` / `identity`, `reviewed_notes`, `release_record` | `SourceIdentity`, `SourceFiles`, `GitQueries`, `RemoteReleaseState.GitHub`; `FreshObservers` at each boundary; `FreshRemoteMain` fixed-command design | Common identity, GITHUB_REPOSITORY, runtime read token where necessary; identity diagnostic | Python fetches tags/main; Java currently injects fresh remote queries; no installed live remote adapter. Wrong HEAD/main/dirty tree/pin/notes/tag/release reject | Wire explicit hardened remote-main query and exact remote tag-object inspection; never trust cached origin/main; preserve no tag/release on initial identity |
| `state` / `central_state`, `prior_upload` | `RemoteReleaseState.inspect`, `PublicationPolicy`, `GitHubOutputs.candidate` | Common identity; GITHUB_REPOSITORY/RUN_ID/RUN_ATTEMPT; `mode=publish|verify` | HTTP reads, output append; unknown history/API failure blocks; failed upload may establish possible prior upload; two 404s after possible upload block | Activate fresh J3A transport and output adapter in original jobs; mode is branch transport, not upload authority |
| `inventory` / `accepted_manifest`, `inventory`, `main` | `AcceptedInventory.verify` invokes J2B `PublicationVerifier`; `PublicationManifest.compact`; output adapter | Common identity/version; accepted manifest/staging/source/build bytes; `inventory=<compact schema-1 JSON>` | Read-only semantic verification; explicit output append only after success; exact version/ten canonical identities/hashes/fingerprint | Preserve EXPECTED_INVENTORY; retain verifier acceptance before output; CLI currently prints read-only inventory rather than consulting ambient GITHUB_OUTPUT |
| `compare` / `compare_inventory` | `AcceptedInventory.compare`, `AcceptedArtifacts.compare`; coordinator independently semantically verifies rebuilt staging first | EXPECTED_INVENTORY and reviewed version; ten-hash equality diagnostic | Fresh local artifact/fingerprint reads; mismatched artifact/version/schema/path/index/sidecar reject; cross-build fingerprint equality deliberately unnecessary | Wire existing rebuild step to safe compare; keep each build bound to its own fingerprint |
| `signed` / `signed` | `AcceptedArtifacts.signed`, `EvidenceFiles`; trusted Gradle sign execution boundary | Accepted staging/manifest, ten sign-task inputs and adjacent .asc files; ten-input evidence | Reads only; wrong input/missing/duplicate/failed signature markers block. Markers are not cryptographic validation | Keep existing Gradle signing tasks; activate binding check afterward; no Java signer |
| `remote` / `verify_remote` | `RemoteArtifactVerifier`, `HttpQueries.artifacts`, coordinator verification job | EXPECTED_INVENTORY/version; fresh staging; remote private-constructor receipt, ten matching hashes/signature-marker pairs | Bounded Central reads/polling; timeout/hash/signature/status/acquisition failures reject. No installed live remote CLI | Wire exact remote verification after rebuild and before both Central consumers; recorded inspection must never claim live completion |
| `tests` / `test_results(False)` | `TestResults.account(root,false)`; safe `release test-results <root>` | Core and storage TEST-*.xml; total count, snapshot 3/3, profile 1/1, explicit 92-case assertion | Read-only; missing/malformed/duplicate/failed/skipped/incomplete evidence rejects | Replace all full-accounting call sites immediately after full suites, preserving XML before focused runs |
| `focused-tests` / `test_results(True)` | `TestResults.account(root,true)`; safe `release focused-test-results <root>` | Core XML only; total, snapshot 3/3/profile 1/1; no full-corpus obligation | Read-only; missing integrity evidence rejects; unrelated storage/corpus evidence unnecessary | Replace focused accounting separately; retain snapshot hash checks |
| `secrets` / `secrets` | `SecretPreflight`, `GpgPacketInspector`; approved-job injected environment and inspector | ORG_GRADLE_PROJECT_mavenCentralUsername/mavenCentralPassword/signingInMemoryKey; encrypted key also needs signingInMemoryKeyPassword; key ID remains optional | Read-only GPG packet inspection, isolated runtime process/stdin; sanitized diagnostics; real key/signing NOT RUN | Activate only after environment approval, outside Nix builds; Gradle remains signing authority |
| `upload-ready` / `main` plus `central_state` | Coordinator's immediately adjacent BEFORE_UPLOAD identity/state boundary | Fresh reviewed source/main/tag/release/Central/history; no reusable token/output authorization | J4A records fake existing Gradle upload only after fresh candidate; changed/unknown/possible-upload state blocks | No operational J4A CLI. J4B must wire fresh check adjacent to original authorized Gradle task without carrying a preflight token |
| `tag-release` / `tag_release` | `ReleaseFinalizer.Expected/plan/execute`, `FinalizationProcesses`; coordinator finalization after remote+two consumers | Runtime write-scoped GitHub token/repository; reviewed identity/notes, exact tag objects and release record | J4A fake/disposable mutations only; uncertain outcome stops/reobserves; conflicting tag/body or failed consumer blocks later mutation | Activate only final job with contents:write; preserve annotated unsigned convention/non-force push/exact body/idempotence; authenticate successful upstream job evidence |
| `prepare` / `prepare`, `preparation_plan`, `source_integrity` | `PreparationPlan`, `PreparationApplication`; safe dry-run/check CLI already present | Explicit root/target, current version/metadata/spec snapshot/notes, local Git identity | Check/dry-run read-only; apply only disposable fixtures here; owned slots/modes validated, partial failures explicit; no release helpers | Separate explicit source-preparation interface/authority in J4B; no prepare-apply route in J4A launcher |

No controller command has been removed. No installed Java upload, signing,
tagging, release creation or preparation-apply command exists.

## 3. Python outside the controller: removal inventory

**PASS — source and live-command audit.** Each row distinguishes active paths from
migration-only comparisons. J4B must remove callers and interpreter requirements
only after activating and qualifying the named replacement. Historical reports
are evidence, not executable dependencies, and must not be rewritten.

| Source / live dependency | Classification and exact responsibility | Replacement / regression evidence / J4B removal condition |
| --- | --- | --- |
| `nix/verify-wrapper.py` | Active wrapper integrity checker; README and three protected-job invocations | JDK-only `WrapperVerifier`; existing direct/API/CLI/raw-declaration tests plus fresh J4A canonical Java/Python checks. Keep exact identities and rejection policy |
| `nix/wrapper-verifier.nix` | Active **source-data** dependency: Java wrapper tests read the Python file to compare pins; does not execute Python | J4B remove third Python-source argument and replace cross-language identity comparison with independently pinned Java/Nix fixture assertions; retain all raw-declaration/API/CLI negative tests |
| `publishing/verify-publication.py` | Active independent semantic Maven verifier; invoked by root Gradle `verifyPublication` (`commandLine("python3",...)`) | J2B `PublicationCli verify` / `PublicationVerifier`; synthetic negative suite, 90-case differential and fresh real-artifact manifest/consumer XML byte comparison. Wire prebuilt standalone launcher into Gradle in J4B, preserving independent compilation |
| `publishing/publication.py` | Active schema/parser/fingerprint/hash/copy model; imported by release/verifier/tests and CLI check/copy | J2B `PublicationManifest`, `PublicationRepository`, `PublicationCli check/copy`, J3B.1 accepted-artifact APIs, J4A inventory transport. Tests include copy rehash/exact file set, fingerprint and schema mutations |
| `publishing/release.py` | Active 13-command controller and accounting; authoritative workflow implementation | Complete mapping above; phase APIs remain unactivated. Remove only after all original job call sites are replaced and protected activation qualified |
| `publishing/test_release.py` | Active release guardrail tests, plus WorkflowPinTests in ordinary CI | Existing permanent J3A/J3B.1/J3B.2 and J4A tests; new `WorkflowPins`/`WorkflowPinsTest` specifically preserve pin validation. Safe `release workflow-pins <root>` checks real workflows. Output runner compares pin positives/negatives to unchanged Python |
| `publishing/test_prepare.py` | Active isolated source-preparation guardrails, 20 tests | `ReleaseTest`, `PreparationApplicationTest`, unchanged J3A/J3B.2 preparation differential, full resulting-fixture inventories; delete Python only with equivalent Java guardrail runner retained |
| `publishing/test_publication.py` | Active accepted-manifest/copy/staging guardrails, 9 tests | `PublicationTest`, `AuthorizationTest`, `WorkflowEvidenceTest`; schema/substitution/fresh-byte/copy/index/sidecar tests retained |
| `tooling/publication-parity.py` | Migration-only oracle: unchanged Python semantic verification, manifests, copy and negative fixtures | Remove host runner after J4B activation; permanent `PublicationTest` stays. Remove oracle-only fixture-export plumbing only after preserving self-contained negative tests |
| `tooling/release-parity.py` + `ReleaseFixture` | Migration-only J3A inputs/preparation/Git/GitHub/history/Central oracle and test bridge | Remove host oracle/bridge in J4B; `ReleaseTest` and independently written fixtures stay |
| `tooling/authorization-parity.py` + `AuthorizationFixture` | Migration-only policy/inventory/signing/secrets oracle/bridge | Remove host oracle/bridge; `AuthorizationTest`, `AcceptedFixture` and fake GPG process tests remain |
| `tooling/completion-parity.py` + `CompletionFixture` | Migration-only remote/finalization/body/preparation oracle/bridge | Remove host oracle/bridge; remote/finalization/process/preparation permanent tests and strict fake executables remain |
| New `tooling/accounting-parity.py` | Migration-only J4A identical-XML accounting oracle | Remove host runner; retain self-contained `TestResultsTest` fixtures/assertions, removing optional export only if desired |
| New `tooling/workflow-output-parity.py` + `WorkflowOutputFixture` | Migration-only exact Actions outputs, compare/state/inputs and action pins | Remove oracle/bridge; retain `WorkflowEvidenceTest`, `WorkflowPinsTest`, coordinator/consumer/accounting permanent tests |
| `tooling/wrapper/.../WrapperVerifierTest` Python-source pin comparisons | Active test-only data dependency, not runtime interpreter dependency | See wrapper Nix row; retain Python-compatible whitespace/property behavior as compatibility semantics, without requiring Python source |
| `PublicationTest` optional Python expectation/export fields | Migration-only metadata within a permanent runner | Remove only oracle export fields; permanent independently expected acceptance fixtures must continue without Python |
| Wrapper `pythonWhitespace`, publication `pythonStrip`, finalizer Python-compatible rstrip/JSON | Historical compatibility semantics, **not Python invocation** | Keep exact established behavior; renaming is optional and must not weaken whitespace/serialization parity |
| `review/**/*.md`, historical inventories/release reports | Historical review evidence | Leave unchanged; do not mistake historical command examples for active callers |
| Upstream pinned install-nix action's `python3` CPU-count helper | Unrelated transitive bootstrap tooling (daemon installer branch), outside this source tree | Document separately from Java release runtime. Do not promise removal of every Python executable on a GitHub runner; pin/review bootstrap dependencies independently |

## 4. Exhaustive active call-site checklist for J4B

- Root `build.gradle.kts`: `verifyPublication` executes
  `python3 publishing/verify-publication.py`. Replace with prebuilt independent
  Java launcher; preserve Gradle task ordering and generated verification XML.
- `qualification.nix`: function argument `python3`, nativeBuildInputs, full
  release `tests`, full publishing unittest discovery, publication `check`,
  focused `focused-tests`, installation `publication copy`. Replace each with
  its named Java equivalent/permanent Java guardrails; retain full-before-focused
  accounting, 99 snapshot hashes, J2B shadow semantics and both consumers.
  After cutover, Python/Java shadow comparison becomes historical evidence;
  independent Java semantic verification must remain mandatory, not disappear.
- `nix/wrapper-verifier.nix`: Python checker source path is supplied to wrapper
  tests. Remove that data dependency with explicit independent pin assertions.
- `flake.nix`: remove `python3` from **both** default development-shell packages
  and jailed agent extraPkgs only after all repository Python callers are gone.
  Flake inputs and unrelated transitive tooling are distinct from a direct
  Python interpreter requirement.
- `.github/workflows/ci.yml`: replace publishing unittest invocation restricted
  to `WorkflowPinTests` with `release workflow-pins` or equivalent independent
  pre-Nix pin guard. It must continue checking all workflow files on the full
  checkout; Nix's production source projection intentionally omits `.github`.
- `.github/workflows/release.yml`, **preflight**: identity, prepare --check,
  state; wrapper checker; full publishing unittest discovery; two full `tests`
  invocations (normal and forced offline), compare, focused-tests, inventory.
  Replace every call; retain both release-only builds and compare, POM consumer,
  snapshot sha256sum, clean-source checks, approval and sequencing.
- Same workflow, **publish-central**: secrets; post-approval identity/state;
  wrapper; rebuild compare; signed; immediate identity/upload-ready. Preserve
  both original trusted Gradle signing and publication commands, optional-secret
  unsetting, daemon/configuration-cache restrictions and step name used by history.
- Same workflow, **verify-central**: inputs/exact HEAD/VERSION; wrapper;
  fresh rebuild then remote; two fresh Central consumer homes/modes. No Maven or
  signing secret is needed here. Retain check execution and final clean tree.
- Same workflow, **tag-and-github-release**: replace tag-release only after
  verified successful upstream job evidence can be trusted at runtime; retain
  contents:write only here, source revalidation, exact tags/releases and retries.
- `README.md`: operational wrapper-check command and every current Python
  requirement/qualification statement (Nix development environment, guardrails,
  migration status, full accounting and Python3.9+ publication prerequisite).
- `RELEASE_CHECKLIST.md`: prepare dry-run/apply/check commands, normal/offline
  full accounting/compare recipe, publication schema/helper ownership and current
  publication check command. Replace active examples, preserve distinct gates.
- `AGENTS.md`: focused Python example, full normal gate's release accounting,
  unittest guardrails and publication check, plus Python-helper invalidation
  language. Update through the J4B policy review; do not silently drop coverage.
- Remove all six migration-only `.py` runners listed above, their oracle-only
  Java bridges, and Python modules/tests only after checking import/caller closure.
  Keep fake Java process tools, permanent runners and independently defined
  expectations. Re-scan all tracked hidden workflows, build/Nix/docs/imports,
  test source-data references and development shells before deleting interpreter
  dependencies. No source filter, lock/cache, strict verification or consumer
  isolation relaxation is an acceptable substitute.

The machine occurrence inventory is `python-occurrences.txt`; the tables above
classify operational command chains, import/data dependencies and historical or
transitive mentions, rather than relying on a `.py` file count. No Python was deleted.

## 5. Java integration architecture

**PASS.** `ReleaseWorkflowCoordinator` composes existing owners. Immutable `Job`
identifies one explicit root/reviewed version/commit/repository/run/attempt.
`PreflightEvidence` has a private constructor and holds accepted inventory and
normal/offline/focused accounting. `PublicationBranch` distinguishes simulated
upload completion from verify-existing. `CentralConsumers.Verified` is private-
constructed only after the exact two consumer execution boundaries succeed.
Finalization receives its prerequisites through that receipt.

Four separate methods model jobs: preflight, approved, verify, finalize. There
is no monolithic release command or installed mutating coordinator adapter.
FreshObservers and Execution are **trusted injected boundaries**, not proofs
that a live action happened. No complete J4A public operational CLI exists.
J3A owns observations, J2B semantic acceptance, J3B.1 policy/secrets/signing
identity, J3B.2 remote verification/finalization, and J4A evidence flow/order.

## 6. Full/focused accounting and XML security

**PASS.** `TestResults` scans TEST-*.xml in core and storage for full accounting,
and core only for focused accounting. Full requires at least one XML per module.
Suite identities must be nonblank and unique. Exact nonnegative decimal tests,
failures, errors and skipped attributes are mandatory; all outcomes must be zero.
Direct testcase count must match tests; nested failure/error/skipped evidence
cannot contradict successful suite attributes. Counts are summed from reports;
405 is not a permanent invariant.

Mandatory suites: SpecSnapshotIntegrityTest exactly 3; R19ProfileIntegrityTest
exactly 1. Full additionally requires Phase2ConformanceTest with a direct testcase
whose name starts with `everyImplementedCaseExecutesWithoutSkipping`, exactly
the Python assertion contract. No independent hardcoded corpus oracle replaces it.
Focused does not require storage or that assertion. Accounting existing XML does
not prove tests ran freshly; Gradle up-to-date reports are explicitly distinguished.

JDK DOM secure processing; DOCTYPE, external general/parameter entities and external
DTD loading prohibited; ACCESS_EXTERNAL_DTD/SCHEMA empty; XInclude/entity expansion
not enabled; resolver throws; parse diagnostics are static. No network/entity
fallback. SecureDirectoryStream/NOFOLLOW regular-file reads anchor every ancestor.
Limits: 4 MiB/file, 4,096 selected XML files, 64 MiB total selected bytes. Required
attributes cannot silently default; duplicate suite identities cannot overwrite.

## 7. Accounting tests and differential outcomes

**PASS.** Permanent disposable fixtures cover full/focused success; missing XML,
malformed XML, absent suite name/count, malformed/signed integer count, duplicate
identity, failure/error/skip, absent/wrong snapshot and profile counts, absent
conformance assertion, focused without corpus/storage, wrong root, DTD/external
entity attack, oversized input, empty directory, incomplete module set, missing
outcome attributes, testcase/count mismatch and contradictory child failure.
The runner also rejects the selected-file-count bound. No Gradle/Python required.

Host unchanged Python oracle runs on **identical bytes** exported by the Java
runner: **26 cases, 8 measured tightenings, zero unexplained drift**. Successful
total/integrity/assertion evidence is compared, not just exit status. Failure
categories are compared, with documented earlier missing-set rejection and
resource-limit classification distinct from XML syntax failure.

Measured Python ACCEPT / Java REJECT: duplicate suite; missing errors attribute;
testcase count mismatch; embedded child failure despite zero attributes; signed
`+3` count; harmless DTD; unexpected XML root; missing storage module results.
Resource bounds/secure filesystem restrictions are additional explicit defensive
constraints. An empty core directory with remaining storage XML is Python
INTEGRITY failure and Java MISSING failure: both reject, Java stops earlier.
First host oracle attempt failed on that classification mismatch; the harness
now records it explicitly. Python was unchanged.

## 8. Accepted inventory and cross-job outputs

**PASS.** `AcceptedInventory.verify` requires source VERSION and invokes J2B's
full independent semantic verifier. It cannot be constructed from caller hashes.
Its transport uses the existing schema 1/version/ten canonical artifacts/hashes/
repositorySha256 and artifact order. `PublicationManifest.compact` removes only
JSON whitespace outside quoted strings from the existing sorted Python-compatible
serializer. No schema or fingerprint representation changed.

Later rebuilds receive parsed EXPECTED_INVENTORY bytes, independently semantically
verify current staging, then compare against the current local accepted manifest
and fresh fingerprint. Exactly ten hashes must agree. Cross-build fingerprint
comparison is intentionally absent; each current build must match its own index/
sidecar-inclusive accepted fingerprint. Changed indexes/sidecars invalidate local
acceptance even when ten primary artifact hashes remain unchanged.

`GitHubOutputs` has fixed typed methods, no arbitrary-name API. It validates all
values before append and accepts only an explicit absolute existing regular
runner file, anchored NOFOLLOW. Missing/unusable/symlink paths fail. Version/commit,
mode and compact inventory are single-line values; controls/newlines reject.
BLOCKED has no successful mode. Output success proves only transport, not action
execution or authority. No real GITHUB_OUTPUT was written.

Permanent tests exercise byte-exact output names/order, blocked and injected
values leaving the file unchanged, invalid paths, fresh inventory checks and
cross-build fingerprint distinction. The integration harness serializes and
parses version, commit and inventory before creating later job contexts.
Host differential: **28 output/interface/pin cases**, exact successful output
bytes and failure-before-output behavior, zero unexplained drift. Includes all
eight publish/verify/block state combinations and rebuilt inventory equality.

## 9. Normative workflow phase order

**PASS.** Preflight: fresh source identity; prepared-source check; Central/history;
wrapper; guardrails; normal qualification/accounting/semantic acceptance; offline
qualification/accounting/rebuild comparison; independent local POM consumer;
focused accounting/snapshot hashes; accepted inventory. Approval remains external.
Approved publication: required secret/key inspection; new source/state observation;
wrapper; rebuild/semantic verification/compare; publish branch sign execution and
accepted-input binding; new immediate identity/state; existing Gradle upload fake.
Verify-existing skips sign/upload. Verification: exact reviewed HEAD/VERSION;
wrapper; rebuild/compare; all ten remote artifact/signature pairs; module then POM
Central consumers. Finalization: fresh identity, then J3B.2 exact state/mutations
and post-observations. No phase receives an earlier decision as upload authority.

Preflight's normal/offline builds are **simulated** here. Actual routine host
qualification does not execute release-only reproducibility. Existing release.yml
still performs that separate real release gate when appropriately authorized.

## 10. Fresh identity, state, signing and unsigned bytes

**PASS.** Distinct PREFLIGHT, AFTER_APPROVAL, BEFORE_UPLOAD and BEFORE_FINALIZATION
observer acquisitions invoke the existing SourceIdentity checks anew. New Git/
HTTP observations are required; initial candidates are never resumable tokens.
`FreshRemoteMain` designs one exact HTTPS ls-remote main query, with explicit
protected argument/environment vocabulary and no cached-ref/fetch fallback.
Strict fixtures reject cached refs, missing/duplicate/ambiguous results. Its
runtime executor must inherit J3B.2 config/process protections; no live executor
was activated and real origin was never fetched.

J3A's complete history pagination, later-page failure rejection, exact same-run/
commit identity, started failed upload evidence and Central status semantics are
retained. Missing history is not clean history. Two 404s after possible upload
block; visible coordinates without same-run provenance block. Immediate changed
state stops rather than reusing the post-approval candidate. Secret packet checks
do not prove signing success; fake successful signing still requires all ten
actual adjacent unsigned inputs to match accepted staging hashes and signature
markers. A failed signing invocation cannot reach upload.

## 11. Independent Central consumers and finalization

**PASS.** `CentralConsumers.run` requires reviewed VERSION/remote version, then
allocates two distinct absolute normalized empty private homes outside the
checkout. It invokes exactly:

```text
./gradlew -p publishing/consumer-smoke --no-daemon --no-build-cache -PremoteCentral clean check
./gradlew -p publishing/consumer-smoke --no-daemon --no-build-cache -PremoteCentral -PpomOnly clean check
```

Each invocation receives only its own GRADLE_USER_HOME and the existing no-daemon/
no-configuration-cache GRADLE_OPTS map. The reviewed existing consumer build owns
Central-only repository selection, exact coordinates and source-substitution
rejection. No second publication verifier was added. The trusted executor must
execute the command, return an actual completed exit status and clean its private
home; no operational executor is installed in J4A. Only zero exit of both modes,
unchanged VERSION and successful cleanup produce the private aggregate receipt.
Null/nonzero/exception/one-mode/wrong-version/nonempty/local-home cases reject.

The coordinator does not accept caller-constructed J3B.2 ConsumerEvidence as its
finalization entry input. Its trusted aggregate is created only by the execution
boundary. Existing lower-level J3B.2 test records remain available to its own
policy tests. `ReleaseFinalizer` continues owning exact body/tag policies and
uncertain-mutation handling. No failed consumer reaches finalization.

The existing Actions graph transports verification success through job sequencing,
not a portable Java object. In this offline model the private successful receipt
crosses explicit job contexts; version/commit/inventory are serialized. J4B must
bind process-to-process completion evidence to the authenticated successful
verify job of this reviewed run/attempt/commit, preserving exact executed consumer
steps. Deserializing caller booleans, recorded fixtures or EXPECTED_INVENTORY
alone must never mint that receipt or grant finalization authority.

## 12. Complete offline integration scenarios and call counts

**PASS — 28 named scenarios**, with an additional same-run partial-visible
recovery subscenario after the initial delayed-upload block:

```text
publish; verify-existing; blocked-approval; blocked-upload;
failed-upload-delayed; partial-central; changed-artifact; changed-index;
changed-sidecar; wrong-signing-input; failed-signing; incomplete-history;
later-page-failure; http-timeout; rate-limit; remote-hash;
remote-signature-missing; remote-signature-invalid; module-fails; pom-fails;
remote-tag; release-fails; uncertain-push; complete; wrong-body;
wrong-source; invalid-output; interrupted
```

Four separate disposable checkout/build contexts are created. Strict expected
step queues and Git-query/HTTP-response queues have no fallback. Successful jobs
must exhaust all registered requests; failures stop at the expected prefix.
All artifact/signature HTTP requests are explicitly registered and exact ordered
URLs are asserted. Every command/mutation is counted, not inferred from state.

New-publication successful path: preflight six execution steps; approved wrapper,
rebuild, sign, immediate new observation, **one fake Gradle upload**; verification
wrapper/rebuild, **20 exact artifact/signature requests**, **two consumers**;
finalization `observe, tag, observe, push, observe, release, observe` (**3 mutations**).
Verify-existing and partial-visible paths: **zero signs/uploads**, still 20 remote
requests/two consumers and required finalization. Known pre-upload blocks and
interruptions have **zero uploads**. Remote/consumer failures can follow an earlier
simulated upload but have **zero subsequent finalization mutations**; the harness
does not pretend it could know later propagation/consumer results before upload.

Existing correct remote tag: `observe, release, observe`, one mutation. Exact
existing tag/release: one observation, **zero mutations**. Wrong body: blocked
with one observation, **zero mutations**. Release failure: tag/push succeed,
release fails; retry reobserves then only release/observe. Uncertain push after
possible success: stop immediately; retry reobserves remote tag then only creates
release. No duplicate push or tag creation is inferred. Interrupted signing
cannot resume its earlier candidate; a new failed identity blocks with zero upload.

## 13. Differential evidence and preserved independent checks

**PASS.** New accounting 26/8 tightenings; output/interface/pins 28/zero unexplained
drift. Existing source-level implementations were retained. Fresh J4A host runs
also exercise J2B 90/11 tightenings, J3A 169/18, J3B.1 173/12 and J3B.2 74/6.
These cover input grammar, state decisions, manifest/hash equality, signing,
remote polling, finalization/release bodies and controlled preparation byte
inventories. No historical qualification result substitutes for J4A evidence.
Permanent Java tests remain independent of Python after removing optional
migration exports/bridges. All unchanged Python guardrails still run.

**PASS — independent classpath.** Direct JDK25 javac --release25, UTF-8,
-Xlint:all -Werror -proc:none, explicit sourcepaths; Jackson Core2.21.7 only
external main dependency. Authenticated existing JAR SHA-256:
`8c5623b98f32d5f7e1287ff61ed50e08ab7b11381e991fff89327faabba42261`.
No Gradle/production classes, Databind/new JSON/HTTP/Git library or test framework.
Production and Gradle import negative compilation fails as expected. Separate
wrapper compilation is JDK-only. No package-deps/lock/toolchain change.

## 14. Nix integration and launcher boundary

**PASS — wiring review and human-reported qualification.** Existing Java-only tooling/src
fileset automatically selects every new Java file. checks.tooling adds permanent
TestResultsTest, WorkflowEvidenceTest, CentralConsumersTest, WorkflowPinsTest and
ReleaseWorkflowIntegrationTest, using the existing read-only spec fixture source.
All original parser/publication/release/authorization/remote/finalization/process/
preparation runners, forbidden-import and launcher checks remain. No Python
oracle is executed in the tooling derivation. No network, token, key, Gradle
publication or live remote Git is required by the new runner.

Existing wrapper, checks.java, Python guards/accounting and J2B real-artifact
shadow checks are unchanged. qualification.nix source filter and flake inputs
were inspected; review/ and host-only tooling/*.py are excluded; tooling Java is
selected independently. nix/tooling.nix is also included by production qualification.
No second Java/Nix bootstrap, new dependency or fixed cache regeneration.

Safe added commands in the existing `release` namespace: test-results,
focused-test-results, inventory, compare and workflow-pins, with explicit paths/
arguments. They are read-only and do not consult ambient GITHUB_OUTPUT. Unknown/
invalid combinations and all dangerous command probes return nonzero and leave
a temporary output sentinel unchanged. Main cannot reach coordinator mutations,
FinalizationProcesses or PreparationApplication.apply.

## 15. Concrete J4B protected-job bootstrap design

**REVIEWED DESIGN; NOT EXECUTED.** Preserve all four jobs, needs/approval,
permissions, concurrency, retry step identity and existing trusted Gradle signing/
publication. No workflow change is made in J4A. Proposed J4B sequence per job:

1. Keep canonical shell dispatch validation before checkout; checkout exact
   reviewed commit with the existing SHA-pinned checkout action and
   persist-credentials:false. Before executing source-controlled tooling, use
   fixed shell/Git checks for dispatch/ref/SHA, exact HEAD/VERSION, clean tree and
   fresh remote main. Never execute a candidate ref before checking its identity.
2. Install a **known pinned Nix**, not an assumed runner binary. Reuse the already
   reviewed install-nix action SHA
   `13d8dd58da0234aa297dedd986986ccb8e7f3e24` (v31.11.1). Review/commit an explicit
   installer checksum before J4B activation. J4A inspected the official Nix2.35.2
   installer as inert text: SHA-256
   `9adda97297d9e8ab360df95c729eabff4f4f93d6db091953c3a68f29e3fb130c`;
   its x86_64-linux tarball pin is
   `0c3960a9792331a22081c3c7a5d8465db9b17c50b3acdf18587fa4c6f2cb1158`.
   Download that version-specific installer to RUNNER_TEMP, verify its reviewed
   checksum, supply a local file install_url to the pinned action, verify installed
   version and reject an unexpected preexisting installation. Do not execute an
   unpinned installer or introduce a second Java/Jackson bootstrap. The installer
   was **not executed** in J4A.
3. Bootstrap caveats from reviewed upstream source: the pinned action can reuse
   any preinstalled Nix, uses a Python CPU-count helper for daemon installation,
   and defaults to configuring the GitHub token in Nix access-tokens. J4B must
   explicitly prevent silent preinstalled reuse and remove token configuration
   (including daemon effective configuration) **before any Nix build**. Do not log
   secret config values. Public pinned flake acquisition/build must work without
   GitHub/Maven/signing credentials. An alternative reviewed installation approach
   is a bootstrap design change needing its own review, not an automatic fallback.
4. Expose `packages.tooling = tooling.launcher` as a J4B alias to the **same**
   existing derivation, not another compiler/bootstrap. Build `.#tooling` from
   this exact Git-aware checkout with no lock writes, no path: source override,
   no source mutation, no secrets in environment/config/derivation parameters.
   Capture its output path and execute its launcher at runtime outside derivations.
   JDK25/Jackson identity comes from unchanged flake.lock/cache/fixed-output pins;
   each job uses the same reviewed checkout and launcher derivation identity.
5. Build tooling before credential-bearing runtime steps. Keep Nix subprocess
   environments scrubbed, including inherited GH_TOKEN/GITHUB_TOKEN and
   ORG_GRADLE_PROJECT_* variables, and remove configured Nix access-tokens.
   Secret/key inspection and finalization adapters receive only operation-specific
   runtime credentials after the required boundary. Keys stay in memory/stdin,
   never files/store/derivation inputs. The original Gradle commands receive their
   existing runtime secret properties; Java never publishes Central itself.
6. J4B must activate narrowly scoped read-only remote-main/tag inspection and
   fresh GitHub/Central transport, then exact output writing. Full remote annotated
   tag inspection needs the exact fetched object and annotation; ls-remote presence
   alone is insufficient. Any object acquisition should use a narrowly scoped
   protected disposable inspection repository/ref, not move source tags or rely
   on stale tracking refs. Complete identity must be revalidated at each boundary.
7. Preserve actual command execution adapters: exact Gradle arguments, isolated
   environments, bounded completion and cleanup. Authenticate verification-job
   completion for finalization using this run/attempt/reviewed commit and exact
   successful consumer steps/job graph. Do not deserialize success booleans from
   an arbitrary file or accept a caller-supplied mode as authority. Environment
   approval remains GitHub-owned, not a Java flag.
8. Bootstrap/build/input/observer failure stops the job; no host-Java, downloaded
   Jackson, Python operational fallback or publish retry is inferred. Same-run
   reruns obtain new observations: visible prior-upload coordinates take verify-
   existing; prior possible upload with two 404s remains blocked until propagation.
   Successful publishing stays complete when only verification/finalization failed.

The current protected workflow installs JDK25 but **does not install Nix**. Its
secret-check step precedes checkout, and its key inspection precedes identity.
Keep the cheap precheckout required-secret shell check; place secret-free tooling
bootstrap after trusted checkout/identity shell checks and before key inspection.
Finalization also needs bootstrap before Java runtime. These are explicit J4B
ordering adaptations; no Nix/credentials-inside-derivations requirement exists.
Rate limits or unavailable public inputs must block safely. External bootstrap
Python is transitive action tooling, separate from repository controller removal.

Sources inspected for the bootstrap review:
[pinned action metadata](https://raw.githubusercontent.com/cachix/install-nix-action/13d8dd58da0234aa297dedd986986ccb8e7f3e24/action.yml),
[pinned installation script](https://raw.githubusercontent.com/cachix/install-nix-action/13d8dd58da0234aa297dedd986986ccb8e7f3e24/install-nix.sh),
[official versioned installer](https://releases.nixos.org/nix/nix-2.35.2/install).
The hashes above describe observed review text, not an executed installation.

## 16. Host qualification, real artifacts and accounting

Final results and invocation counts are appended after the stable host gate.
Full-suite XML/accounting is always preserved before focused Gradle changes it.
No Gradle up-to-date result is described as fresh test execution. Actual source-
release preparation, normal/offline release reproducibility and remote Central-
only consumers are NOT RUN. Ordinary local module/POM consumers remain required.

## 17. Human qualification, invariants, freeze and Git state

**PASS — human-reported `nix flake check`. Agent Nix invocations: zero.** No prior
milestone Nix/CI result is reused. No redundant nix build or dependency-cache
update is requested. Final changed inventory, protected hashes/modes, source
membership, empty staged-content proof and frozen inputs are appended below.
Only this confirmed-excluded report may be finalized after the human gate.

## 18. Residual risks and completion limits

**NOT RUN:** live protected workflow, actual signing/key validation, Central
upload/remote inspection/remote consumers, real tag/push/release or source
preparation. The strict simulation qualifies evidence flow and fail-closed
ordering, not live service availability, signing compatibility, authenticated
runtime completion transport or a fully atomic distributed release.

Injected observer/executor/transport implementations are trusted boundaries.
Private receipts do not prove a live process ran unless J4B supplies the reviewed
actual execution adapters. No atomic transaction spans GitHub/Central/source
filesystems; observations can race later external changes. Secure filesystem
providers/POSIX behavior and reviewed explicit executables remain runtime
requirements. Signature markers retain the existing presence policy, not
cryptographic validity or signer trust. Limits may safely reject unusual inputs.

J4B must activate/runtime-qualify the mapped operations, bootstrap and credential
boundaries, preserve every original gate, authenticate cross-process successful
verification evidence, remove Python caller/import/data dependencies, and update
current docs/dev shells. Historical reports remain untouched. None of that
activation is authorized here. Stop after J4A; no release action is authorized.

## 19. Frozen host results and invalidation accounting

**PASS — stable final normal Java gate:** 42s, 27 outer actionable tasks,
**9 executed / 18 up-to-date**. Nested isolated module consumer: **5s, 5 executed**.
Core tests actually reran after the focused selection; storage tests and Javadocs
were up-to-date. Immediate Python and Java full accounting both accept **405**
reported tests, zero failures/errors/skips, snapshot3/3, profile1/1 and explicit
92-case execution assertion. Full XML and accepted manifest are preserved in
`frozen-full-xml/` and `frozen-publication-manifest.json` before any replacement.

**PASS — Python guardrails:** 51 tests, no failures/skips on the full checkout.
Existing synthetic HTTPError cleanup ResourceWarnings are non-failing.
**PASS — fresh Python manifest check:** ten artifacts.
**PASS — independent POM consumer:** 930ms, five tasks executed, existing offline
options unchanged. No separate forced-offline full host suite was run.

**PASS — final real-artifact J2B shadow:** independent Java semantic verification,
fresh staging check and exact ten-file copy. Complete manifest and generated
consumer metadata compare byte-for-byte with Python. Independent Python rehashes
the Java copy and checks exact path membership. Evidence: `shadow-frozen/`,
`shadow-frozen.log`, `final-host.log`; Python-authoritative outputs untouched.

**PASS — real focused accounting:** one explicit focused core Gradle invocation
ran snapshot/profile tests; Python and Java both accepted **4** tests, snapshot3/3
and profile1/1, without requiring corpus/storage. Evidence:
`focused-gradle.log`, `focused-accounting-python.log`, `focused-accounting-java.log`.
The earlier full XML had already been preserved. The final normal gate restored
full current XML afterward. No further focused run overwrote it.

**PASS — final standalone pipeline:** J1 43 assertions; J2B132 assertions and64
semantic fixtures; J3A218; J3B.1 228; all J3B.2 runners; new accounting29 assertions/
26 fixtures, evidence outputs, nine consumer execution/isolation cases, action-pin
negatives and28 named end-to-end scenarios. Explicit Java with empty PATH and
CLASSPATH is used for permanent runners. Fresh real workflow-pin CLI check passes.
JDK-only wrapper199 fixtures (one accept/198reject),190 raw-declaration tests,
additional API/CLI probes and canonical Java/Python wrapper verification pass.
Both forbidden production/Gradle imports reject under the restricted classpath.

**Invalidation recorded:** first normal gate passed (38s,27 tasks:8 executed/
19up-to-date; nested module consumer10s/five executed), followed by full accounting,
51 Python guards, POM consumer and real Java shadow. The removal audit then found
workflow action-pin validation needed a permanent Java replacement and strengthened
serialized version/commit handoff assertions. Those included Java/Nix edits
invalidated the first final gate. Standalone checks and the complete normal
boundary were rerun after the refinements; no qualification coverage was dropped.
All included-input edits preceded the final gate and freeze.

**Unexpected failures:** first accounting differential attempt failed in its host
classification comparison for empty core XML with remaining storage results.
Both implementations rejected; Java MISSING versus Python INTEGRITY is now
explicitly accounted. No Python or Java policy was weakened. An initial log-tail
inspection used an unsupported local flag placement; only the diagnostic read
failed. No compilation/permanent runner/Gradle test/consumer/shadow failure occurred.
Expected negative-fixture rejections are successful tests.

| Invocation category | Count / outcome |
| --- | --- |
| Broad production baseline | 0; focused release-tooling baseline selected |
| Focused Python baselines | 3 PASS: release22, preparation20, publication9 |
| Other focused Python unit invocations | 0 |
| Focused production Gradle | 1 PASS, explicit snapshot/profile selection |
| Standalone main / test javac | 5 / 5 PASS; restricted classpaths |
| Accounting permanent runner (including oracle exports) | 6 PASS;29 assertions and26 exported fixtures per export run |
| Accounting host oracle attempts | 4: first classification FAIL, then3 PASS with26 cases/8 tightenings |
| WorkflowEvidenceTest / CentralConsumersTest / integration runner | 3 / 3 / 4 PASS; integration28 named scenarios plus delayed-recovery subscenario |
| WorkflowPinsTest / real full-checkout pin CLI | 1 / 1 PASS |
| New output/interface host oracle | 2 PASS:21 cases initially,28 after pin guard addition |
| Existing J1/J2B/J3A/J3B.1 and four J3B.2 permanent runners | 2 each PASS, retained across final compilation refinement |
| Smoke / forbidden import compilation | 2 / 2 PASS; forbidden compile rejects as expected |
| Existing J2B / J3A / J3B.1 / J3B.2 host differential | 2 each PASS:90/169/173/74 cases; final compile/refinement pipeline retained full interface regressions |
| JDK-only wrapper main/test javac; full runner; canonical Java/Python | 1/1;1;1/1 PASS |
| Installed dangerous/unknown/invalid-argument rejection probes | 2 sets of12 PASS,24 probes total; temporary output sentinel unchanged |
| Full normal Java gates / immediate full Python+Java accounting | 2 / 2 pairs PASS; first gate invalidated as described |
| Authoritative verifyPublication / isolated module consumer | 2 / 2 PASS, nested in the two normal gates |
| Full Python guardrails | 2 PASS,51 each |
| Python fresh manifest / independent POM consumer | 2 / 2 PASS |
| Real Java shadow verify / check / copy; independent Python copy check | 2/2/2;2 PASS |
| Real focused Python / Java accounting | 1 / 1 PASS,4 tests |
| Separate forced-offline full host suite | 0 NOT RUN; tooling wiring changed, not Gradle acquisition/resolution/cache semantics |
| Agent Nix / cache updater | 0 / 0 NOT RUN |
| Human J4A Nix | 1 owner-reported PASS; duplicate confirmation is not counted as another invocation |
| Release reproducibility / real signing/upload/tag/release/preparation/commit/push/dispatch | 0 NOT RUN |

Counts are invocations rather than fresh executions of up-to-date tasks. Nested
consumer checks are not extra full Java suites. Migration fixtures use fake
boundaries/disposable roots; real-artifact shadow counts are separated from them.

## 20. Protected byte/mode audit and exact input freeze

**PASS — all216 protected tracked files retain their starting SHA-256 and modes**:
core/storage production sources, all spec snapshot/corpus bytes, SPEC_PIN, VERSION,
gradle/, publishing/, package-deps, flake.lock and protected release.yml. Every
other initially tracked file also matches except the three explicitly modified
files listed below. Java17 production bytecode, r19/92cases, BC1.86, wrapper pins,
Jackson2.21.7, ten unsigned artifacts and strict consumer/verification boundaries
remain under the existing gates. No workflow, pin, dependency or version change.

**PASS — frozen inputs:**354 Git-known non-review regular files, a conservative
superset of derivation inputs, are recorded with SHA-256 and filesystem modes in
`/tmp/totipo-j4a-evidence/frozen-inputs.json`. Inventory SHA-256:
`d8faf432e0b1fde69b6559768b4dca498af30f8df7a85d6647c91f69a6852662`.
No included input may change without invalidating qualification. Host oracles
are conservatively frozen too, although excluded from derivations. Only this
excluded report is finalized after freezing. Protected audit: `protected-audit.json`.

## 21. Complete changed-file inventory, hashes/modes and Git state

**PASS —19 intended files:**3 modified existing files,7 new main Java files,
5 new permanent runners,1 host-only Java bridge,2 host-only Python oracles and
this report. All16 new files have intent-to-add only. No actual contents are
staged. No secrets, temporary fixtures/evidence, caches or build outputs are
Git-known. All new source/report modes are0644. No commit/tag/push occurred.

Non-report changed-file hashes and filesystem modes:

| Path | SHA-256 | Mode |
| --- | --- | --- |
| `nix/tooling.nix` | `716594ee9e04d13f0254d8c12c10f752087caad1f658cd9604720c0d9cfcb4d6` | `0644` |
| `tooling/accounting-parity.py` | `068cd05d6d727f3fbe7b178a592774af81aeedd165f713ca3e77ba7f247290bf` | `0644` |
| `tooling/src/main/java/org/totipo/tooling/publication/PublicationManifest.java` | `6b9656f75066c44761747073b85752d6918af8197ff4e7aadeb22e1b204d5155` | `0644` |
| `tooling/src/main/java/org/totipo/tooling/release/AcceptedInventory.java` | `b07c387c8927b6c43cc9dc427ba50021af168f3eb7119ae3304ddcd88935f505` | `0644` |
| `tooling/src/main/java/org/totipo/tooling/release/CentralConsumers.java` | `f3ac95d961374069a3c0d5e6c93f16445939dc17e99d5c6914f66ec7773f04c3` | `0644` |
| `tooling/src/main/java/org/totipo/tooling/release/FreshRemoteMain.java` | `612482f9208b1a9e3a51127419109741b54c5a0948aaf4423345ca5d1a702958` | `0644` |
| `tooling/src/main/java/org/totipo/tooling/release/GitHubOutputs.java` | `c95df34efb12d8c36471946b185a7a9746d62c76669f271f1e2906c194f5b7eb` | `0644` |
| `tooling/src/main/java/org/totipo/tooling/release/ReleaseInspectionCli.java` | `a786e387bc187586f3a86bad5af3ecde360253fd31121a8f111fa74ccd449048` | `0644` |
| `tooling/src/main/java/org/totipo/tooling/release/ReleaseWorkflowCoordinator.java` | `bbdda7790ea56a6bc447e5e606bd28d0cd2c1559c18ba667577aab86344335fb` | `0644` |
| `tooling/src/main/java/org/totipo/tooling/release/TestResults.java` | `0ff4c3cbb9ab8f04fafb88e33054525c1b91761e5000158657d4b1fa59b45091` | `0644` |
| `tooling/src/main/java/org/totipo/tooling/release/WorkflowPins.java` | `54f4e796dfe4a2c63d06a2a7d5c190928c13f79b51af81eb01332aadab352333` | `0644` |
| `tooling/src/test/java/org/totipo/tooling/release/CentralConsumersTest.java` | `de10d320fa57960c91ba1e72ee7dcbf3b4228e94f691b843ea5bcc0fcf5975ca` | `0644` |
| `tooling/src/test/java/org/totipo/tooling/release/ReleaseWorkflowIntegrationTest.java` | `2dcf5b6b6b34e95a5ce8def2d1127d014e341e60aa0f9e6e22169f8b68d5b44e` | `0644` |
| `tooling/src/test/java/org/totipo/tooling/release/TestResultsTest.java` | `88ce794167d136190c6402c2bd1c2d79e4ee8c093b601ba4aa63a0d4df28172c` | `0644` |
| `tooling/src/test/java/org/totipo/tooling/release/WorkflowEvidenceTest.java` | `f055548f0e0cb274e90b02e0f953646d76c78e1a2a829760c419a2546e7ead6e` | `0644` |
| `tooling/src/test/java/org/totipo/tooling/release/WorkflowOutputFixture.java` | `070c11776e7ea25ad17512f4ff7ae69693e07e1a0176193149c99c22d6ff0a83` | `0644` |
| `tooling/src/test/java/org/totipo/tooling/release/WorkflowPinsTest.java` | `43d9eeadcbe5e9488c353961260a9ab024f34b8503698fc22bd74ec9d73a9948` | `0644` |
| `tooling/workflow-output-parity.py` | `ea7515d4935b8b5873d735fde8953a669f0a0f77943b33a79cedbc93f4533735` | `0644` |

Exact `git status --short`:

```text
 M nix/tooling.nix
 A review/J4A_JAVA_RELEASE_WORKFLOW_INTEGRATION_REPORT.md
 A tooling/accounting-parity.py
 M tooling/src/main/java/org/totipo/tooling/publication/PublicationManifest.java
 A tooling/src/main/java/org/totipo/tooling/release/AcceptedInventory.java
 A tooling/src/main/java/org/totipo/tooling/release/CentralConsumers.java
 A tooling/src/main/java/org/totipo/tooling/release/FreshRemoteMain.java
 A tooling/src/main/java/org/totipo/tooling/release/GitHubOutputs.java
 M tooling/src/main/java/org/totipo/tooling/release/ReleaseInspectionCli.java
 A tooling/src/main/java/org/totipo/tooling/release/ReleaseWorkflowCoordinator.java
 A tooling/src/main/java/org/totipo/tooling/release/TestResults.java
 A tooling/src/main/java/org/totipo/tooling/release/WorkflowPins.java
 A tooling/src/test/java/org/totipo/tooling/release/CentralConsumersTest.java
 A tooling/src/test/java/org/totipo/tooling/release/ReleaseWorkflowIntegrationTest.java
 A tooling/src/test/java/org/totipo/tooling/release/TestResultsTest.java
 A tooling/src/test/java/org/totipo/tooling/release/WorkflowEvidenceTest.java
 A tooling/src/test/java/org/totipo/tooling/release/WorkflowOutputFixture.java
 A tooling/src/test/java/org/totipo/tooling/release/WorkflowPinsTest.java
 A tooling/workflow-output-parity.py
```

Exact `git diff --name-only`:

```text
nix/tooling.nix
review/J4A_JAVA_RELEASE_WORKFLOW_INTEGRATION_REPORT.md
tooling/accounting-parity.py
tooling/src/main/java/org/totipo/tooling/publication/PublicationManifest.java
tooling/src/main/java/org/totipo/tooling/release/AcceptedInventory.java
tooling/src/main/java/org/totipo/tooling/release/CentralConsumers.java
tooling/src/main/java/org/totipo/tooling/release/FreshRemoteMain.java
tooling/src/main/java/org/totipo/tooling/release/GitHubOutputs.java
tooling/src/main/java/org/totipo/tooling/release/ReleaseInspectionCli.java
tooling/src/main/java/org/totipo/tooling/release/ReleaseWorkflowCoordinator.java
tooling/src/main/java/org/totipo/tooling/release/TestResults.java
tooling/src/main/java/org/totipo/tooling/release/WorkflowPins.java
tooling/src/test/java/org/totipo/tooling/release/CentralConsumersTest.java
tooling/src/test/java/org/totipo/tooling/release/ReleaseWorkflowIntegrationTest.java
tooling/src/test/java/org/totipo/tooling/release/TestResultsTest.java
tooling/src/test/java/org/totipo/tooling/release/WorkflowEvidenceTest.java
tooling/src/test/java/org/totipo/tooling/release/WorkflowOutputFixture.java
tooling/src/test/java/org/totipo/tooling/release/WorkflowPinsTest.java
tooling/workflow-output-parity.py
```

`git diff --cached --name-only`: empty. `git diff --cached --quiet` and
`git diff --check`: PASS. Branch main and HEAD remain the recorded starting values.
Non-report diff:18 files,1,146 insertions,1 deletion. The nineteenth path is this
excluded report; its prose line count changes during finalization. Machine Git
inventories are preserved beside the frozen inventory.

## 22. Final qualification status

**PASS — agent implementation, offline regressions/differential evidence, full
host qualification, protected invariance, source membership and input freeze.**
**PASS — fresh human J4A `nix flake check` and post-gate freeze audit.**
The owner reported “nix flake check passed”. The repeated confirmation is counted
as one reported qualification, not evidence of two invocations. Detailed Nix logs,
duration and cache/task counts were not supplied and are not inferred. J3B.2
qualification was not reused. No redundant build/cache updater was requested.

**NOT RUN — all live release operations and protected-workflow activation.**
Python and release.yml remain authoritative and unchanged. All19 changes remain
uncommitted for owner review. J4B activation/removal/runtime integration stays
separate. Only excluded report edits may follow the human gate without rerunning it.


## 23. Human qualification and post-gate audit

**PASS — owner-reported J4A human gate:** `nix flake check`.
**PASS — agent post-gate audit:** all354 frozen non-review SHA-256 hashes and
filesystem modes match; all216 protected files retain baseline bytes/modes.
Branch main, starting HEAD,19-path change inventory and intent-to-add membership
are unchanged. `git diff --cached --quiet` and `git diff --check` pass. Evidence:
`/tmp/totipo-j4a-evidence/post-human-audit.json`.

Only this confirmed-excluded review report was finalized after the human gate;
no included qualification input changed. Agent Nix invocations remain zero.
All changes remain uncommitted. Python and the protected workflow remain
unchanged and authoritative. J4A is complete; stop here. J4B activation and all
live release operations remain unauthorized and NOT RUN.

# Java observation composition clarification

## 1. Starting HEAD/state

2026-10-10: clean `main`, HEAD `8b24b8a1de1a20f834028b2b536f33d6a8f2e7f5`.
Read AGENTS.md before selecting validation. Required committed prerequisites:
subscriber threading `ce00f0c`, qualification ladder `b03f5b2`, release-version
ownership scanning fix `8b24b8a`. VERSION is 0.2.0; SPEC_PIN is v1/r19.

## 2. Classification

`PUBLIC_API`, `DOCS_JAVADOC`: semantic clarification of released behavior with
public-API regression protection. No CORE_RUNTIME, release preparation, dependency
or protocol work.

## 3. Exact F08 evidence and question

Read Android's committed audit at **`11d5c1215217470beff62093e0249817b1f8f2cd`**,
the commit introducing the audit, located through its Git path history. This is
not floating main. Its provenance pins Java runtime source to
`d6310c177ae930df188fd4f5798622c935698b2e` and reviewed guidance to
`b03f5b22f367723ce4a3bddf0a56b159a06f32cf`.

Exact question: can coordinated external local import, `state()`, `states()`,
`requestRefresh()` and two different Finished observations compose into Android's
import-before-publish observation barrier for released Java 0.2.0?

Sources: [pinned Android F08](https://github.com/totipo-org/totipo-android/blob/11d5c1215217470beff62093e0249817b1f8f2cd/review/ANDROID_JAVA_OPERATION_MODEL_AUDIT.md#f08--two-emission-sync-barrier-has-an-implementation-argument-not-a-refresh-acknowledgement),
[pinned Android coordinator](https://github.com/totipo-org/totipo-android/blob/11d5c1215217470beff62093e0249817b1f8f2cd/app/src/main/java/org/totipo/android/reconcile/ForegroundVaultCoordinator.java).
Read audit sections 3–6, 12–13 and F08, and coordinator `observeForSync` lines
209–244. A bare read-only evidence clone is at
`/tmp/totipo-android-f08-evidence.git`; no Android working tree was changed.

## 4. Android behavior evaluated

Complete the coordinated immutable-object import, release the bridge, capture
baseline, subscribe with positive demand, request refresh, accept a different
Finished, then repeat with a baseline captured after the first acceptance.
Final diagnostics/integrity veto outbound publication. The implementation reads
`session.state()` after wakeup rather than retaining the callback's state; those
reads can advance further. They must remain subject to the same live-session and
final diagnostic policy. A terminal wakeup is not observation evidence.

The audit found a plausible implementation argument and no demonstrated false-
publication trace. This report independently traces its Java premises; absence
of such a trace is not used as proof.

## 5. Released implementation authority

Inspected released [ApplicationSession](https://github.com/totipo-org/totipo-java/blob/d6310c177ae930df188fd4f5798622c935698b2e/core/src/main/java/org/totipo/format/ApplicationSession.java),
[ApplicationStates](https://github.com/totipo-org/totipo-java/blob/d6310c177ae930df188fd4f5798622c935698b2e/core/src/main/java/org/totipo/format/ApplicationStates.java),
TokenStoreReader, VaultLifecycle, State construction, and NIO composition/scan/read
paths. ApplicationSession and ApplicationStates are byte-identical between release
and starting HEAD. Relevant released line ranges in ApplicationSession:
19–24 locks/executor; 44–76 initialization/refresh; 77–135 observe/emission;
151–178 termination; 258–275 State; 382–405 save/merge observation;
525–544 successful publication and refresh.

Only constructor initialization and `observePass` construct public states;
only `observePass` calls `emit`. Repository searches for `new State`, `emit` and
`observe` found no competing session state producer. `ApplicationStates.emit`
installs its volatile latest under its monitor and rejects nonincreasing sequences.
All completed-pass callers hold the provider gate through emission.

## 6. Documentation authority before change

Current committed API_DESIGN lineage: operation model `3b24b54`, callback correction
`ce00f0c`, guidance pin `b03f5b2`, starting descendant `8b24b8a`. Starting API_DESIGN
has the same blob as the Android guidance pin. It already says observation/emission
is serialized, stale work cannot replace newer emission, states replay latest in
increasing hidden sequence order, and requests coalesce without completion-barrier
meaning. It does not define the read/publication boundary or deliberately specify
per-pass reference identity. Those gaps require clarification; implementation
allocation alone would not establish a contract.

## 7. Complete state-emitter inventory

| Trigger | Thread/executor and gate | Store observation / Finished | Publication serialization |
| --- | --- | --- | --- |
| Successful open/create session construction | Factory caller; before session escape | Initial Enumerating(0), no completed-pass claim | Publisher initialized before refresh scheduling |
| Initial observation requested by constructor | Single observation executor; provider gate | Scan/read/validate/evaluate/project; one Finished on normal completion | Gate held through latest installation and offer |
| Explicit requestRefresh | Requesting caller only admits; executor later acquires gate | Same complete observation procedure; may end with diagnostics | Same gate through publication |
| Pending/coalesced refresh, including a request during a pass | Executor; pending flag cleared after gate acquisition before observing | Pending requests coalesce; request during execution can queue another pass | Same gate; no overlapping store observations |
| Successful create/update/accepted merge/frozen partial/retry publication | Save/retry caller requests refresh after acknowledged installation; later executor pass | Publication itself emits no state; later observation can emit Finished | Later pass uses same gate |
| Normal merge-save prepublication check | Save caller under providerAccess gate | Direct observe(), even if result is observation-unavailable or AdditionalConflict; one Finished if normally completed | Same gate through publication; can precede separate write attempt |
| Ordinary storage failures | Whichever observation caller is active | Diagnostic Finished on normal completion, including caught security/unsupported-store failures | Same pass path, not separate emitter |
| Fatal observation RuntimeException/Error | Observer or merge-save caller | Session terminates; no synthetic Finished for failed pass | Cleanup waits for gate; stream onError, no extra VaultState |
| Explicit/racing close | Closing caller marks lifecycle under local, waits for gate | No extra state; an already admitted pass may finish/install before cleanup | Terminal signal follows gate-protected work; pending tasks skip observation |
| Invalid demand, subscriber exception, cancellation | Per-subscription machinery/common-pool drain | No session state; subscriber error/cancellation only | Cannot produce/reorder session states |

Candidate validation, local builders/TOTP, failed/uncertain publication and handle
closure emit no states themselves. Fatal termination chooses the first lifecycle
cause; close completes normally otherwise. Terminal delivery can supersede an
undelivered latest state. There is no requirement to drain every state before close.

## 8. Definition of observation pass

The operation is `observePass` under the provider gate. Its store-observation
boundary is entry into TokenStoreReader.read / scanObjects. It scans names, reads
each canonical candidate, validates, evaluates the graph, then under local projects
values/heads/diagnostics and causal facts. The logical result completes with a
new Finished State. `emit` assigns private current then publisher.emit installs
volatile latest and signals drains under the publisher monitor. Public `state()`
reads publisher latest, not the earlier private assignment.

The provider gate remains held across all this, including installation and offer
for delivery. Only afterward can another pass start reading. Merge and executor
passes cannot overlap for NIO/non-reentrant stores. The gate is a ReentrantLock:
an arbitrary custom SPI that calls back into semantic session operations from a
provider call could recursively invoke merge observation. Such provider reentry is
explicitly outside the clarified composition; ordinary NIO observation has no such
callback. No other completed-state emitter can interleave in this scope. Installation
order cannot differ from completed-pass order. Client callback entry/return is
outside the pass and may occur after later passes. A pass is a bounded attempted
observation, not necessarily a successful/exhaustive store snapshot.

## 9–12. Refresh, replay, serialization and emission ordering

Refresh return means request admission/scheduling, possibly coalesced with already
pending work. It promises no started/completed observation, callback count, deadline
or request/result correlation. Close/fatal termination can prevent execution.

Per-subscription delivery is serialized and strictly advances installed pass order.
Intermediate states can be skipped even with unlimited demand. Two subscribers
have no mutual callback ordering. `state()` can already be later than an entered
callback's argument. Cancellation cannot retract entered client callbacks.

Pass serialization is intentional contract: the next pass cannot start its store
observation until the preceding pass has installed and offered its result. Later
Finished delivery within a subscription represents a later completed pass. A fresh
subscription replays latest, which is at least as new as its previously captured
baseline; rejecting that exact baseline therefore rejects its pass.

## 13. VaultState reference identity decision

**A: YES, INTENTIONAL CONTRACT**, narrowly for library-created Java 0.2.0 sessions:
each separately completed pass installs a distinct VaultState reference, including
identical semantic results. A replay/read of the same instance is not another pass.

Deliberate justification: the API exposes immutable observation instances, currently
has no public pass token, and existing released PublicApiTest refresh/stream coverage
already uses reference distinction, including unchanged-content refreshes. Reviewed
guidance already gives each emission an increasing internal sequence. Making the
observation wrapper distinct is a small, useful observable contract consistent with
those authorities. It does not require new decrypted values/graphs per pass; internal
sharing remains free. This decision intentionally rules out reusing a completed-
pass wrapper as a later pass's public state. It is not deduced solely from `new State`
or generalized to arbitrary external VaultSession implementations. Future redesign
would need to preserve this contract or explicitly revise it; the API has no general
stability promise. New identical-content regression protection makes the decision
explicit rather than leaving Android dependent on an allocation accident.

## 14. Coordinated external mutation

NioStoreComposition.coordinatedDelegate requires the owner to serialize every call,
session, writer, handle and bridge mutation for the root's lifetime. It does not
implement the external lock. Normal NIO scans/read calls freshly consult storage;
there is no retained discovery cache. A successfully installed immutable batch
completed before a later pass starts reading is visible to that pass, subject to
storage availability/errors and later mutations. The owner's synchronization supplies
the happens-before relationship. Java's session gate alone supplies no ordering with
external mutation. Per-call coordination does not make a multi-call scan/read atomic.
No SAF/provider transport promise follows.

## 15–16. F08 decision and necessary assumptions

**SUPPORTED_WITH_ASSUMPTIONS** — decided before final broad qualification.

1. Same live library-created 0.2.0 session throughout; application ownership rejects
   stale/closed/replaced session results before outbound authorization.
2. Successful local immutable import completes before first baseline capture; the
   bridge is released before calling Java. Failed/uncertain imports do not qualify.
3. Import and every store call/writer/session/handle use the same root-wide domain;
   no independent writer bypasses it. Storage visibility and ordinary availability
   remain required. Imported immutable data is not removed/replaced before observation.
   Provider calls do not reenter session operations; ordinary NIO satisfies this.
4. Baselines and acceptance/wakeup are correctly synchronized. Each wait has positive
   demand and rejects the baseline reference; the second baseline is captured after
   first acceptance. Terminals, failure, cancellation and timeout cannot authorize.
5. Nonoverlap through installation, distinct per-pass identity and ordered latest
   replay are the Java contracts deliberately specified here. Other completed
   observations, including merge, obey them and may satisfy a wait.
6. The final state used for authorization is Finished and separately meets Android's
   diagnostic/integrity/transport policy. Java does not define that policy.

Proof: first accepted state is installed after the first baseline's read and hence
after import completion, although its pass may have started earlier. The second
baseline is that state or a later state. A different Finished accepted afterward
belongs to a still later pass. Its reads cannot start before installation of the
first accepted state, so they start after import. Skipped states strengthen the
ordering; unrelated passes do not break it. A later `state()` read used by Android
can only advance this same session ordering. This is a safety argument; it does
not promise timely progress or make either request an acknowledgement.

## 17. Explicit non-guarantees

No cloud/provider freshness, other-device receipt, remote history completeness,
rollback resistance, current global heads, Java merge freshness, pass per request,
first Finished/request correlation or delivery of all states. Finished can have
diagnostics. No claim of absent unavailable/unknown history follows merely from
Finished or an empty diagnostic list; only the observation's actual evidence counts.
The composition establishes local ordering and cannot authorize remote publication
without application policy. Android's SAF rules remain Android responsibilities.

## 18–19. Documentation and Javadoc disposition

API_DESIGN's replay-latest section now contains Observation-pass ordering and
composition, defining all boundaries, identity, coordination, assumptions and limits.
No separate competing contract document was created. VaultSession.state Javadoc
briefly states per-pass reference distinction and points to the detailed rules;
requestRefresh Javadoc makes its lack of acknowledgement/correlation explicit.
states Javadoc remains correct and unchanged. No executable production edit.

## 20. Executable contract evidence

Released/current PublicApiTest already covers ordered streams, diagnostic states,
replay coalescing, unchanged-content distinct references, fatal refresh and merge
termination, close and subscriber behavior. Current adds the reviewed threading test
(two parameter cases), absent at release; these authorities are kept separate.

New deterministic public-API coverage:

- `observationPassPublishesBeforeNextScanAndRefreshReturnIsNotCompletion`, two
  parameter cases: initial pass A blocks in controlled scan; requests return with
  A unfinished and B not entered; release A, then B records public latest at its
  actual scan entry. It must already be A's Finished. B remains blocked while A
  is consumed, then B completes. Controlled demand verifies ordered delivery;
  no promise of every uncontrolled pass's delivery. Equal-content case explicitly
  requires distinct wrappers; differing-content case uses processed/diagnostic
  evidence to attribute results to passes. No sleeps or runtime hooks.
- `observationAfterCoordinatedExternalImportReadsInstalledLocalObjects`: persistent
  coordinated NIO delegate wrapped in a shared monitor, initial empty observation,
  externally installed immutable objects under that monitor, then requested
  observation establishes exact expected revision identities without diagnostics.

Tests establish Java primitives, not Android's two-wait algorithm. Timeouts bound
test failure, not successful correctness. No production code changed for testing.

## 21–23. Scope invariance and Android consequence

No runtime change is needed: released locking, state construction and publisher
ordering implement the chosen narrow contract. Only public comments change in
production Java. VERSION 0.2.0, SPEC_PIN v1/r19, coordinates, dependencies/BC,
locks/verification, package-deps, wrapper, Nix inputs and CI/release workflow remain
unchanged. No Android changes were made.

Android F08 may be closed as supported under these enumerated assumptions; this
is not a fresh Android adversarial-scheduling qualification. Its separate next
change should update audit/provenance guidance from
`b03f5b22f367723ce4a3bddf0a56b159a06f32cf` to the eventual reviewed Java commit
containing this clarification. This work is intentionally uncommitted, so no new
commit SHA is invented. Runtime dependency/source pin stays at d6310c1 / 0.2.0.
Android should explicitly preserve lifecycle/terminal refusal and diagnostic policy;
no algorithm or runtime upgrade is required by this Java decision.

## 24–25. Qualification accounting

Baseline `./gradlew test`: **PASS**, 13 actionable tasks, 2 executed (bytecode
checks), 11 up-to-date, including both module test tasks. It reused full-suite
evidence; no claim of newly executed 402 tests. Before focused runs,
`python3 -B publishing/release.py tests` verified 402 JUnit tests, zero failures/
errors/skips and exact portable/snapshot/profile accounting. Baseline XML copies:
`/tmp/totipo-java-f08-evidence/baseline-{core,nio}-test-results`.

Focused Gradle invocations: **2 PASS** (first the two controlled ordering cases;
second those plus coordinated import). No focused Python test invocation. No failed
attempt or included-input invalidation occurred. Final stable normal gate: **PASS**.
consumerSmoke's dependency on verifyPublication is one task execution, not a second broad invocation.
Separate host forced-offline full suites: **0**; no resolution/build inputs changed.
The required independent offline POM-only consumer is a separate consumer gate,
not a second offline full Java suite.

| Invocation category | Count / result |
| --- | --- |
| Small broad Gradle baseline | 1 PASS; module tests up-to-date |
| Focused Gradle tests | 2 PASS; core tests executed both times |
| Focused Python tests | 0 |
| Final full Java gate | 1 PASS, 45s; 27 tasks: 14 executed, 13 up-to-date; core:test executed, storage-nio:test up-to-date |
| Full XML accounting | 2 PASS: baseline 402, final 405; final immediately after full gate |
| Full Python guardrails | 1 PASS; 41 tests |
| Gradle verifyPublication | 1 executed within final gate |
| Python publication verifier | 2 executions: nested Gradle verifier plus required standalone PASS |
| Module-metadata consumer | 1 nested independent build PASS; 5 tasks executed |
| POM-only consumer | 1 independent offline invocation PASS; 5 tasks executed |
| Separate host offline full Java suites | 0 |
| Human / agent Nix | 1 human-reported PASS / 0 |
| Failed attempts / justified reruns | 0 / 0 |

Final Java accounting: **405 JUnit tests, zero failures/errors/skips**, all 92/92
portable cases, snapshot 3/3, profile 1/1. Three new parameter/test cases account
for the increase from 402. Full-suite XML was accounted for immediately after the
Java gate and copied to `/tmp/totipo-java-f08-evidence/final-{core,nio}-test-results`.
Final logs in that directory: final-java.log, final-test-accounting.log,
final-python-tests.log, final-publication.log and final-pom-consumer.log.
Javadoc completed with existing missing-comment/tag warnings. Python guardrails
passed with HTTPError fixture ResourceWarnings. No warning prompted build changes.
Publication verifier accepted both modules' metadata/contents, Java 17 bytecode,
runtime BC 1.86 and the ten unsigned artifacts in local build staging. These are
qualification artifacts, not a remote publication or release.

## 26. Input freeze and human Nix

Inspected qualification.nix's exact selection: core/src, storage-nio/src, gradle,
publishing, nix and listed build/version/spec/license/lock files. API_DESIGN and
review are excluded. flake.nix consumes qualification.nix, package-deps, pinned
flake inputs and wrapper-check files; no changed report/guidance Markdown is read.
Tests and source Javadocs are included inputs. Human final Nix must follow their
freeze. Included inputs are now frozen. A sorted-path/NUL/raw-per-file-SHA256
inventory of 276 tracked qualification/evaluator/cache inputs has aggregate SHA-256
`3d99989c226ac6afae3ef044b276989fdc31ceef97f50c2acd65a6a3d2359ddb`;
per-file inventory is `/tmp/totipo-java-f08-evidence/frozen-inputs.sha256`.
After final Java checks only excluded API_DESIGN/report prose was refined to spell
out the custom-provider non-reentry assumption; no included input changed.
**Human Nix PASS, agent Nix 0.** After the freeze and final normal qualification,
the human reported “nix flake check passed” in response to the requested
`nix flake check path:.` gate. This records human-reported evidence; the agent
did not execute Nix or independently inspect its output. Invocation count: one
human run, no agent runs. The frozen per-file inventory was rechecked successfully
after that report. Only this excluded report was updated afterward, so the result
remains applicable. No second build or qualification run was requested.

## 27. Release disposition

Routine contract milestone only. No reproducibility release procedure, signing,
publication, tags, staging, commit, push or CI dispatch. No release authorization.

## 28. Final Git state

Branch remains `main`; HEAD remains
`8b24b8a1de1a20f834028b2b536f33d6a8f2e7f5`; index is empty. Exactly these paths
remain unstaged/uncommitted:

```text
 M API_DESIGN.md
 M core/src/main/java/org/totipo/VaultSession.java
 M core/src/test/java/org/totipo/api/PublicApiTest.java
?? review/JAVA_OBSERVATION_COMPOSITION_CLARIFICATION_REPORT.md
```

Changed-path allowlist, empty index, comment-only production Java comparison and
`git diff --check`: PASS. All other tracked files remain at starting HEAD, including
every version/spec/dependency/build/release input. No Android working tree changed.

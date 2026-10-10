# Java operation/state-snapshot documentation report

## 1. Starting HEAD/state

Started on clean `main` at `632c7d522156ec19e61b0ef505224aa89ccf22fb`
(`Unify Java Nix qualification and CI`). `git status --short` was empty.
VERSION was 0.2.0; SPEC_PIN identified Totipo Vault Format v1/r19 at
`cdb4e91be1c6d3704874b2b92457ffe7be5e9084`. Work stayed in totipo-java.

## 2. Existing relevant documentation inventory

Read API_DESIGN.md, README.md, AGENTS.md and RELEASE_CHECKLIST.md, and inspected
public Javadocs, ApplicationSession/ApplicationStates, and relevant PublicApiTest
coverage before editing.

| Subject | Existing guidance/evidence |
| --- | --- |
| Immutable VaultState | API_DESIGN session ownership and blocking sections; VaultState class comment; immutable State lists/maps. |
| Older references | Session ownership explicitly accepts historical same-session heads/alternatives, retains values until close, rejects foreign sessions; PublicApiTest historical and session-scope cases. |
| Hidden sequence | Session ownership and replay-latest stream: monotonic session-local emission ordering, no public generation/freshness/validity token; ApplicationStates rejects older emissions. |
| Builder causal basis | Editing section and VaultState method comments specify receiving-state equal heads, historical fallback, exact head and selected-merge bases; updateUsesReceivingStateEvenWhenSessionHasAdvanced test. |
| Refresh | Replay-latest section and VaultSession requestRefresh comment: non-blocking, coalescible local observation, no polling/sync claim; observer executor implementation. |
| Merge freshness | Dedicated M/F0/F1 containment rules, unavailable observation gate, AdditionalConflict and explicit partial resolution; Editor.save and Merge.additional. |
| Frozen publication/retry | Persistence knowledge section and retry/partial Javadocs: exact bytes/identities, independent transferable handles, no semantic rebase, no automatic retries; Frozen/Retry/Partial implementation. |
| Blocking/provider I/O | README introduction and synchronization paragraph; API_DESIGN blocking section and validation exception; provider gate versus local lock; blocked-provider local-operation tests. |
| Thread confinement | API_DESIGN editing/blocking sections and TokenEditor class comment; builders not thread-safe, including save/setter use. |

RELEASE_CHECKLIST.md contains build/Javadoc and release-provenance checks, but no
general API-documentation consistency check to extend. It is unchanged; this is
not a release milestone or release preparation.

## 3. Why Javadocs alone were insufficient

The existing rules were spread across ownership, editing, merge, persistence and
threading sections and individual methods. Method comments alone did not give
integrators a common model separating snapshot validity, presentation relevance
and operation-specific freshness. One canonical design section now connects them;
short Javadocs link consumers to it and related API contracts.

## 4. Canonical operation taxonomy

API_DESIGN.md's new **Operation classes and state-snapshot semantics** section,
near session ownership, contains a combined taxonomy/scheduling table:
immutable/descriptive projection; local secret-backed projection; local state
construction; observation request; freshness-gated publication; frozen publication/
continuation; session/storage lifecycle; independent candidate validation.
The last row preserves the important no-I/O-but-provider-gate distinction.
Detailed operation contracts remain authoritative.

## 5. VaultState snapshot-validity rule

A VaultState is an immutable valid observation, not a lease on currentness.
Validity describes that observation, including its progress/diagnostics, without
claiming complete/freshest history. A later emission alone does not invalidate
old snapshots, same-session references or local computations, require cancellation,
or require global application serialization. Open-session and ownership limits
remain explicit; descriptive reads survive close.

## 6. Validity versus application-presentation relevance

The S1/start, S2, S3, S1/completion example separates semantic validity from
session/lifecycle ownership, request generations, selection, lock state and newer
presentation requests. Those application decisions are not Java invalidation rules.
TOTP time validity is separately acknowledged.

## 7. Local projection semantics

Descriptive projections are immutable/I/O-free. TOTP resolves the supplied
same-session alternative through retained session-owned values and bounded local
crypto, without provider freshness, configured-store I/O or provider-gate entry.
Historical/conflicting/tombstoned alternatives remain supported while open.
Asynchronous presentation is suitable; application cancellation policy remains
application-owned, without a new Java cancellation contract.

## 8. Builder/causal-basis semantics

Builders retain captured parent bases rather than following later session states.
The table links to existing exact selection rules, including receiving-state equal
heads and historical fallback for update(alternative), and separate selected heads
and receiving-state frontier for merges. Local construction publishes nothing;
builders remain thread-confined, including save/setter use.

## 9. Observation semantics

requestRefresh requests another local observation, may coalesce and is non-blocking.
The scheduled pass reads the store and emits states; it does not mutate semantic
vault history or invalidate older observations. It is not a completion barrier or
remote synchronization operation.

## 10. Freshness-gated publication

Normal merge save explicitly observes newer evidence. Relevant new heads can
produce AdditionalConflict without publication; unavailable observation can gate
publication as already documented. The existing causal-containment rules and
compare/check-to-publication race remain untouched. Create/update save does not
acquire a merge-only freshness rule.

## 11. Frozen publication/retry

Publication freezes semantic output and exact encrypted stages. Retry republishes
those bytes/identities without reobservation/rebase. PartialResolution.save uses
the frozen original resolution without repeating the semantic gate. New semantics
require an explicitly new operation. Handle transfer/consumption, session lifetime
and uncertainty rules remain authoritative; abandoning work does not undo persistence.

## 12. Lifecycle/blocking guidance

The application subsection distinguishes immutable reads, local lock work,
non-blocking refresh requests, provider calls and thread-confined builders.
Open/create, saves, partial saves, retries and close belong off Swing EDT/Android
main thread. Candidate validation and closing partial/retry handles may wait on
the provider gate. No stronger latency/thread-safety guarantees are introduced.
Subscriber and independent-session/provider coordination rules are preserved.

## 13. Application anti-patterns

The canonical section warns against treating state emission order as a generic
cancellation token, cancelling all projections, invalidating historical references,
one global UI BUSY state for all operations, and silent builder rebasing. It points
to lifecycle ownership, explicit request generations, token identity and specific
freshness outcomes instead.

## 14. TOTP example

S1 supplies Alternative A; asynchronous S1.generateTotp(A, instant) overlaps S2.
S2 alone does not invalidate the calculation. Display depends on current presentation
intent, open session and the code's time interval; changed requirements are an
independent application decision. The example is platform-neutral.

## 15. Merge-save counterexample

Immediately after TOTP, the text contrasts a resolution constructed from S1 whose
normal save observes additional relevant heads and returns AdditionalConflict.
That explicit freshness gate is not generalized to local/immutable projections.
Retry is then contrasted as exact frozen publication with no semantic rebase.

## 16. Javadoc changes

Only comments changed in six Java files:

- VaultState: immutable observation/ownership/lifetime model link; captured bases
  for update/merge; merge-save cross-references; local TOTP and presentation limits.
- VaultSession: model link, VaultState reference, refresh observation semantics.
  Existing detailed close/validation comments already supply their specific limits.
- TokenEditor.save: blocking, merge-only freshness, no rebase, model and result/
  continuation cross-references.
- PublicationRetry: exact frozen output, no reobservation/rebase, open session and
  capability transfer; save cross-reference.
- PartialResolution: frozen original output, no repeated gate/rebase, open session;
  conflict/retry cross-references.
- Totipo: concise open/create blocking guidance and close cross-references.

## 17. AGENTS.md guidance

Requires reading the canonical operation/state-snapshot section before changes to
concurrency, scheduling, VaultState handling, session lifecycle, publication/retry
or UI/client interaction. Explicitly forbids inferring
`newer VaultState == old work invalid` absent the relevant operation contract.
Existing Nix/qualification instructions remain intact.

## 18. README pointer

Added a small integrator pointer beside existing API/SPI guidance for UI-thread
scheduling, refresh, publication and asynchronous projections. Existing publication
coordinates, release statements and historical examples remain unchanged.

## 19. Implementation/documentation consistency review

Inspected ApplicationSession.State reference resolution, local access/open checks,
refresh executor/coalescing, Editor.save, Merge.additional, Frozen.attempt and
Retry/Partial capability handling. Inspected existing PublicApiTest cases for
historical TOTP, blocked-provider local operations, receiving-state bases, foreign
references, additional conflict, partial saves, retry transfer and closure.
No stop-condition contradiction was found; no behavior change was required.

Searched current/latest/stale/invalid/freshness/rebase/retry/blocking/thread-safety/
thread-confinement language in the design, README, AGENTS and touched Javadocs.
Existing emission-order suppression concerns publisher ordering, not reference
invalidation; merge freshness concerns explicit provider evidence, not generic
sequence validity. The canonical section defers to the detailed rules.
A comparison stripping block comments/whitespace confirmed all six changed Java
files retain exactly their prior code and API signatures.

## 20. Test/Javadoc build results

Passed with host JDK 25.0.4.1 and the pinned Gradle 9.8.0 wrapper:

- `./gradlew clean test build javadoc verifyPublication consumerSmoke`: BUILD
  SUCCESSFUL; 30 tasks executed. This combines both requested Gradle suites in
  one clean run. Javadocs built successfully; missing-comment/@param/@return
  warnings remain (85 storage-nio, capped at 100 core), with no Javadoc errors.
- `python3 -B publishing/release.py tests`: 400 JUnit tests, zero failures/errors/
  skips; exact-set 92/92 portable cases; snapshot 3/3 and profile 1/1 passed.
  Snapshot tests verify all 99 pinned records.
- `python3 -B -m unittest discover -s publishing -p 'test_*.py'`: 38 tests, OK.
  Python emitted ResourceWarnings for mocked HTTPError cleanup; no test failed.
- Independent staged consumer: module-metadata mode via consumerSmoke and separate
  `./gradlew -p publishing/consumer-smoke --offline --no-daemon --no-build-cache
  --rerun-tasks --dependency-verification=strict -PpomOnly clean check`: both passed
  runtime smoke, dependency boundary and repository-selection checks.
- Publication verification: both modules' metadata, scopes, contents, Java 17
  bytecode and ten-artifact unsigned local inventory passed.
- `python3 -B nix/verify-wrapper.py gradle/wrapper/gradle-wrapper.jar
  gradle/wrapper/gradle-wrapper.properties`: wrapper JAR, URL and distribution
  hash passed. An initial invocation omitted its required arguments and failed;
  the corrected invocation passed. This is a Python script, not a Nix command.
- `git diff --check`: passed.

No release artifact normal/offline comparison was requested or performed: this
is documentation-only source qualification, separate from protected release work.
Logs are in `/tmp/totipo-operation-model-{gradle,python,pom}.log`.

## 21. VERSION/SPEC/dependency invariance

VERSION remains 0.2.0. SPEC_PIN.md, vendored v1/r19 snapshot, dependency versions,
Gradle locks/verification, build configuration, publication coordinates, release
notes/checklist and release workflow are unchanged. No implementation/protocol,
API signature, tag or published artifact was changed. No staging, commit, push,
CI dispatch, signing, release or publication was performed. verifyPublication
uses only the existing unsigned local staging repository.

## 22. Human Nix result

The human reported that `nix flake check path:.` passed after the agent checks.
This result is human-reported; no agent Nix command was run. This qualification
is not a release action; no second build was requested. The required human gate
is complete.

## 23. Published-0.2.0 Javadoc disposition

The Maven Central 0.2.0 Javadoc artifact is already published and will not change.
Repository/source Javadocs improve immediately; these improvements naturally
appear in the next ordinary Java release. No 0.2.0 republishing/replacement or
0.2.1 preparation is part of this work.

## 24. Final Git state

Final review: main remains at `632c7d522156ec19e61b0ef505224aa89ccf22fb`.
Nine tracked files modified (AGENTS.md, API_DESIGN.md, README.md and the six
comment-only Java files listed above); this report is the sole untracked file.
The index is unchanged; all edits remain unstaged/uncommitted. No other tracked
files differ from the starting HEAD. Human Nix qualification passed, as reported by the human.

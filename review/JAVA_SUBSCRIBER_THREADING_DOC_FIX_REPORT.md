# Java subscriber threading documentation correction

## 1. Starting HEAD/state

Started on clean `main` at `3b24b54becde0c93479c1fbd80ea0fbd2026e2a8`
(`Document Java operation and VaultState scheduling model`). The required
operation/state-snapshot milestone was already committed. `VERSION` was `0.2.0`;
`SPEC_PIN.md` identified Vault Format v1/r19, upstream commit
`cdb4e91be1c6d3704874b2b92457ffe7be5e9084`.

## 2. Original inaccurate/ambiguous wording

`API_DESIGN.md` said both “Subscriber callbacks are serialized per subscription
on the common pool” and “Callbacks are serialized per subscription and dispatched
on the common pool”. Its replay-latest section only said `onSubscribe` came first.
The public `states()` Javadoc described replay/backpressure without threading.
`review/PUBLIC_API_FACADE_REPORT.md` repeated the broad common-pool claim.

## 3. Current implementation evidence

Inspected current production source, rather than relying on the desktop audit:

- `ApplicationSession.states()` returns its `ApplicationStates` publisher.
- `ApplicationStates.subscribe(...)` directly invokes
  `subscriber.onSubscribe(subscription)` on the caller before setting `started`
  and calling `signal()`. Reentrant demand cannot start delivery because
  `signal()` returns while `started` is false. Callback rejection cancels delivery.
- `Subscription.signal()` uses `ForkJoinPool.commonPool().execute(this)`.
  `started`, `scheduled`, and `ended` are checked under the publisher monitor;
  at most one drain is scheduled for a subscription.
- Only `Subscription.run()` invokes `onNext`, `onError`, or `onComplete`.
  It invokes callbacks outside the publisher monitor, sequentially within its
  drain, and ends/removes the subscription before terminal delivery.
- Session closure/fatal failure invokes publisher termination; terminal callbacks
  still use that drain, including for subscribers joining a terminated session.

## 4. Existing test evidence

Read `PublicApiTest`'s `Probe` and existing stream tests:
`replayLatestCoalescesWithoutDemandAndSubscribersAreIndependent`,
`streamOrdersEmissionsAndObservationDiagnosticsDoNotTerminate`,
`nonPositiveDemandTerminatesOnlyThatSubscriber`,
`closeFromOnNextCompletesWithoutWaitingForItsOwnCallback`,
`fatalObservationErrorsCurrentAndLateSubscribersExactlyOnce`,
`fatalMergeObservationAlsoTerminatesTheSessionWithItsCause`, and
`explicitCloseWinningFatalRaceKeepsCompletionForAllSubscribers`.
They cover replay-latest, independent demand, state ordering, invalid-demand error,
reentrant close, fatal errors, late subscribers, and terminal selection/order.
They did not explicitly establish callback thread identity/non-inline delivery.

## 5. Final documented threading model

The thread calling `states().subscribe(...)` synchronously receives `onSubscribe`
before asynchronous draining begins. Subsequent `onNext`, `onError`, and
`onComplete` callbacks use the asynchronous common-pool drain and are serialized
per subscription. Different subscriptions can execute concurrently. This does
not promise worker names, a fixed worker across callbacks, UI-thread delivery,
latency, or that asynchronous delivery waits until `subscribe` returns.

## 6. API_DESIGN changes

Qualified both broad common-pool statements in the application threading and
replay-latest sections. Made synchronous caller-thread establishment explicit.
Preserved replay, demand, cancellation, ordering, terminal/close behavior,
independent subscriptions, and operation/state-snapshot semantics.
The historical `review/PUBLIC_API_FACADE_REPORT.md` retains its original broad
common-pool wording as evidence of the wording at that time. On this specific
callback-thread point, it is superseded by the current `API_DESIGN.md`,
`VaultSession.states()` Javadoc, and this correction report. Its temporary edit
was reverted before handoff.
`README.md` and `AGENTS.md` contain no such inaccurate claim and remain unchanged.

## 7. Javadoc changes

Added a concise clarification to `VaultSession.states()` describing synchronous
caller-thread `onSubscribe` and subsequent asynchronous, serialized common-pool
delivery. `ApplicationStates` and its comments remain unchanged.

## 8. Focused test addition/disposition

Added parameterized public API regression test
`subscriptionEstablishmentIsSynchronousAndLaterCallbacksAreAsynchronousAndSerialized`
with normal completion and invalid-demand error cases. It records the subscribing
test thread, checks establishment immediately after `subscribe` without waiting,
and requests one replayed state from `onSubscribe`. State/terminal callbacks must
run on a different thread from the caller, demonstrating non-inline delivery.
A latch holds `onNext` while closure or invalid demand selects the terminal;
the test checks no terminal overlaps it and verifies the full callback sequence.
Futures carry callback failures to the test. No sleeps or worker-name checks;
five-second waits follow the suite's existing hang-avoidance convention.

## 9. Implementation/API invariance

Only `VaultSession.java` changed under production source, solely in Javadoc.
Compared its HEAD and working-tree bytes after removing block comments: exactly
equal. Executable statements and public signatures are unchanged.
`ApplicationStates` and all other production sources are unchanged; no existing
test expectation was modified or weakened. Searched Markdown and Java for
common-pool, callback, onSubscribe, subscriber, subscription, asynchronous, and
serialized wording; no contradictory common-pool callback claim remains in the
current canonical design or public Javadoc. The historical facade report retains
the original wording, superseded on this point as described in section 6.

## 10. Validation results

- `./gradlew clean test build javadoc verifyPublication consumerSmoke`: passed,
  all 30 tasks executed. Isolated consumer smoke passed, including API/runtime
  loading, dependency boundary and repository selection. Javadoc emitted
  missing-comment/tag warnings in existing declarations, with no failure.
- `python3 -B publishing/release.py tests`: passed after the clean build;
  402 JUnit tests, zero failures/errors/skips, 92/92 exact-set portable cases,
  snapshot 3/3 and profile 1/1.
- `python3 -B -m unittest discover -s publishing -p 'test_*.py'`: passed,
  38 tests. Python emitted HTTPError fixture cleanup ResourceWarnings.
- `python3 -B publishing/verify-publication.py`: passed separately; POM,
  metadata, contents, Java 17 bytecode and unsigned inventory verified for
  both modules.
- Focused direct run: passed, both parameter cases, zero failures/errors/skips:
  `./gradlew :core:test --tests 'org.totipo.api.PublicApiTest.subscriptionEstablishmentIsSynchronousAndLaterCallbacksAreAsynchronousAndSerialized' -I /tmp/totipo-subscriber-threading.gradle`.
  The temporary init script only redirects XML/HTML test reports to
  `test-results/subscriber-threading` and `reports/tests/subscriber-threading`,
  preserving the full-suite XML evidence. No build file was changed.
- `git diff --check`: passed.
- Production comment-stripping equality, changed-file allowlist and empty-index
  checks: passed.

The already-passed Java/Python validation and human Nix qualification apply to
the executable, documentation, and test changes that remain. Restoring the
historical report and recording its disposition did not change those qualified
files. Full Java/Nix qualification was not rerun solely for that restoration;
`git diff --check` and the final changed-path check passed after it.

## 11. VERSION/SPEC/dependency invariance

`VERSION` remains `0.2.0`. `SPEC_PIN.md`, the v1/r19 snapshot, dependencies,
Gradle locks, strict verification metadata, `package-deps.json`, `flake.lock`,
publication coordinates, and release workflow remain unchanged.

## 12. Human Nix result

The human reported that `nix flake check path:.` passed after agent validation.
This records the human-reported result; the agent did not run Nix.

## 13. Release disposition

No version preparation, release, remote publication, tag, push, or CI dispatch.
The requested Gradle verification uses only its local unsigned staging repository
and isolated consumer smoke build; this is not a release action.

## 14. Final Git state

Still on `main` at starting HEAD. Index is empty (`git diff --cached --exit-code`
passed). Exactly these working-tree changes remain unstaged/uncommitted:

```text
 M API_DESIGN.md
 M core/src/main/java/org/totipo/VaultSession.java
 M core/src/test/java/org/totipo/api/PublicApiTest.java
?? review/JAVA_SUBSCRIBER_THREADING_DOC_FIX_REPORT.md
```

All agent work and the human-reported Nix qualification are complete.

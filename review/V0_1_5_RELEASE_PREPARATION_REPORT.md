# Totipo Java 0.1.5 release preparation

## 1. Starting state

Preparation date: 2026-10-07. Branch: `main`. Exact clean starting HEAD:
`b3de4ad2b05fb65b412a7fdc862280998352c266` (`test fix`), following candidate
implementation commit `b4c8aee3608f230c51b41238b6706c76c88c200a` (`candidate validation`). The agent ran
`git branch --show-current`, `git rev-parse HEAD`, and `git status --short`
before editing; status was empty. Starting VERSION was 0.1.4. Current committed
HEAD contains the API, shared validation path/parser cleanup, four focused tests,
consumer compile call and candidate API report. That committed HEAD, rather than
the earlier report's uncommitted state, is the release implementation baseline.
No applicable AGENTS.md was found.

Inspected VERSION, README, API_DESIGN, SPI_DESIGN, SPEC_PIN, Gradle publication
logic, publishing scripts/consumer/locks/verification template, release checklist,
CI and release workflows, flake, previous 0.1.3/0.1.4 reports/notes and candidate
review. Version-specific notes retain the established Highlights / Compatibility
and scope / Validation format; no new release-note format or dependency is added.

## 2. Release identity

VERSION is the single source of truth, advanced to **0.1.5**. Coordinates:
`org.totipo:totipo-core:0.1.5` and `org.totipo:totipo-storage-nio:0.1.5`.
Protocol remains **Totipo Vault Format v1/r18**, exact spec commit
`4623a7e1718e23504903096c92332597057bd8f0`.

SPEC_PIN, normative snapshot, requirements profile, 90-case corpus, schemas,
manifest and checksums are byte-identical to starting HEAD. Normative SHA-256:
`8a357e75f3ddd92efa954fde2ffc9af33f40a2c2396d6afbf1d5de5f00bc4f8a`;
profile SHA-256:
`4c7954cd2b59aa0afbbe3c22080cadddf72135d884b186a671266c29d58418df`.
No wire, key derivation, crypto, TOKEN, graph or VAULT behavior changed in release
preparation. The committed capability is protocol-neutral.

Every original 0.1.4 occurrence was classified before editing:

| Occurrence | Classification/action |
| --- | --- |
| VERSION; checklist identity/coordinates/tag/dispatch; release workflow input example | Current metadata: advance to 0.1.5. Checklist's stale 0.1.3 notes path corrected to current 0.1.5. |
| Consumer lock's two Totipo entries | Self-resolution: advance to 0.1.5; third-party entries unchanged. |
| README prepared-release paragraph and report/notes links | Current release status: prepared/unreleased 0.1.5. |
| README published dependency examples | Keep a published version: canonical Central confirmed 0.1.4, so update stale 0.1.3 examples to published 0.1.4. These references do not claim 0.1.5 availability. |
| 0.1.4 release notes/report, candidate API report and private-local portability report | Historical provenance: unchanged. |
| Python guardrail fixtures/mock versions | Inspected; original fixtures use older versions and remain unchanged. |
| New notes/report baseline references | Intentional provenance. |

No indiscriminate replacement. API_DESIGN now identifies prepared/unreleased
0.1.5; README adds only a concise generic validation use case. SPI_DESIGN was
reviewed for consistency and remains unchanged: validation belongs to the core
application session, and TotipoStore has no change.

## 3. Included change

Only the committed additive `VaultSession.validateObject(RevisionId, byte[])`
capability, `ObjectCandidateValidation.Valid`/`Invalid`, internal reuse of existing
envelope/TOKEN validation, associated safe parser cleanup, focused tests, consumer
compile support and documentation, plus release metadata/docs. Production release
edits are Javadocs only; no API signature or implementation changes.

## 4. Deferred/excluded

No Android/SAF integration or qualification, provider identifiers/metadata,
reconciliation/import, VAULT candidate validation/reconciliation, storage-NIO
behavior change, protocol revision, exact-existing acknowledgement redesign,
unrelated Java contract fix or new external dependency. A single call determines
neither contradiction against another candidate nor current state/freshness.
This release does not claim a synchronization feature.

## 5. API compatibility

Canonical Maven Central 0.1.4 core/NIO binary and source artifacts were downloaded
read-only to `/tmp/totipo-0.1.5-release/`. `javap -protected -s` inventories compare
public declarations/headers/descriptors: core **102 -> 105** public types; NIO
**7 -> 7**. The only changed existing type is VaultSession, with exactly one added
default method. The three added types are ObjectCandidateValidation and its
Valid/Invalid records. No old public type/member was removed and no existing
descriptor changed. `javap -v` confirms all **12** existing public sealed
hierarchy permits unchanged; the added result hierarchy is entirely new.

A consumer preserving the published shared/private NIO smoke and an external
session implementing all old methods were compiled using `javac --release 17`
against only released 0.1.4 Totipo JARs. Running with only 0.1.5 Totipo JARs and
BC 1.86 passes: old calls link, factories open read-only, and an additional
0.1.5-compiled probe calls validation on the old-compiled session and receives
`UnsupportedOperationException`. Current consumer compiles against 0.1.5 in
both metadata modes. Evidence supports source/binary compatibility of this
additive delta, without expanding the project's long-term stability promise.

## 6. Security/API boundary

Success establishes exact 1024-byte physical representation length, envelope
authentication, keyed OBJECT_ID match, framing/zero-padding validity and current
semantic TOKEN validity under the authenticated vault root. It establishes no
current head, graph completeness, freshness, persistence, provider/store origin,
remote synchronization, import or contradiction against another candidate.

Validation requires an open authenticated library session, is synchronous and
enters the normal serialization/lifecycle gate. Closing/closed sessions reject
calls; close waits for admitted work before wiping the root. There is no password
re-entry/KDF, key export or public parser/crypto context. It performs no store
access/write, graph evaluation, state emission, refresh, history registration or
lifecycle transition. Existing candidate tests remain in the full suite: unchanged
state identity/heads, scan/write/publication counters and VAULT bytes, repeated and
absent-local validation, ownership, concurrent close/rejection and post-close
result readability. Source inspection confirms no revision/history/refresh path
is invoked; those internal facts are not all separately exposed by public tests.

Input is not mutated or retained; callers must not mutate during the call.
Valid defensively owns only the supplied canonical object ID and exact validated
opaque ciphertext snapshot, returning defensive copies. It retains no TOKEN
plaintext, secret, issuer/account, parsed parent model, metadata projection,
root/fingerprint/key, diagnostics or session reference. It may outlive the session;
toString redacts ciphertext. Values are descriptive and publicly constructible,
not unforgeable capabilities. Only a successful session call establishes validation.

Wrong-root, AEAD, keyed-ID, framing, padding and grammar failures remain collapsed
to Invalid. No finer failure reasons were introduced. Generated public Javadocs
and sources JAR were inspected for these contracts; core binary/source/Javadoc
inventories contain the new API. Existing JVM best-effort plaintext cleanup
qualifications in the candidate report remain applicable.

## 7. Publication metadata

Core/NIO generated POMs and Gradle module metadata consistently target 0.1.5.
NIO's sole compile POM dependency and API/runtime variant requires-version are
core **exactly 0.1.5**. Core's sole runtime dependency remains
`org.bouncycastle:bcprov-jdk18on:1.86`; BC is absent from the consumer compile
boundary and enters runtime through core. Documentation variants add no dependencies.
Java 17 module attributes and classfile major 61/minor 0 are retained.

The existing verifier checks exactly ten unsigned artifacts and binary/source/
Javadoc allowlists against production/generated files, metadata scopes, checksums
and staging structure. No provider/Android dependency, local path, test/debug/
review file, spec snapshot or fixture capability appears in the artifacts.
NIO binary and sources JAR bytes match released 0.1.4 exactly; its metadata changes
for 0.1.5; Javadoc differences are exclusively generated version titles
(verified entry by entry against released 0.1.4).

Build JVM remains installed JDK **25.0.4.1**; wrapper remains Gradle **9.8.0**.
**116** tracked protocol/dependency/toolchain files are byte-identical to starting
HEAD, including wrapper, catalog, locks, third-party verification metadata/template,
Gradle build configuration, flake inputs/lock and spec snapshot. Only consumer
self-version locks advance; generated consumer verification metadata is ignored
and produced by the existing verifier. No unrelated drift.

## 8. Tests

All required agent-run release gates pass. Logs, full normal/offline JUnit XML,
released baseline artifacts, scripts, public inventories and compatibility probes
are retained outside publication inputs at `/tmp/totipo-0.1.5-release/`.

| Command/check | Result |
| --- | --- |
| `./gradlew clean test build verifyPublication consumerSmoke` | PASS: 3m12s, all 30 tasks executed. |
| `./gradlew --offline --no-daemon --no-build-cache --rerun-tasks clean test build verifyPublication consumerSmoke` | PASS: 1m55s, all 30 tasks executed. |
| `python3 -B publishing/release.py tests` after each completed full build | PASS: 528 JUnit tests (382 core + 146 NIO), zero failures/errors/skips; exact-set 90/90 portable cases, snapshot 3/3, profile 1/1. |
| `python3 -B -m unittest discover -s publishing -p test_release.py` | PASS: 21 guardrail tests, remote/signing/Git mutation mocked. |
| `python3 -B publishing/verify-publication.py` | PASS: ten artifacts, Java 17, inventories, metadata/scopes/checksums. |
| `python3 -B publishing/release.py compare` with saved normal EXPECTED_INVENTORY and validated local identity inputs | PASS: 10/10 final offline hashes match normal. No identity mutation, dispatch or remote publication action. |
| `./gradlew -p publishing/consumer-smoke --offline -PpomOnly clean check` | PASS: compile/runtime and repository boundaries, shared/private NIO smoke. |
| `./gradlew :core:test --tests org.totipo.conformance.SpecSnapshotIntegrityTest --tests org.totipo.conformance.R18ProfileIntegrityTest` | PASS: four focused integrity tests. |
| `python3 -B publishing/release.py focused-tests` | PASS: snapshot 3/3, profile 1/1, zero failures/errors/skips. |
| `(cd core/src/test/resources/totipo-spec/v1-pre-rc && sha256sum -c SNAPSHOT.sha256)` | PASS: 97/97 records. |
| `reviewed_notes('0.1.5')` helper | PASS: exact version-specific file and heading. |
| `javap -protected -s`, `javap -v`, old-compiled Java 17 consumers and default-method probe | PASS: additive delta only, old consumers link/run, unchanged existing sealed hierarchies. |
| Generated public Javadocs/source inspection and published class presence | PASS: required security/lifecycle/ownership/scope documentation and API artifacts. |
| Wrapper SHA-256 | PASS: `238e777fcddd7e34f9708186085def2abd6e08e658505b38718d79d74c21abd5`, matching CI. |
| `git diff --check`, new report/notes whitespace, index inspection | PASS; index empty. |

Counts remain the committed 528; no new release test duplicates were added.
Existing Javadoc missing-comment/tag warnings and Python HTTPError mock
ResourceWarnings remain non-failing; nothing was skipped or suppressed.

An early accounting attempt ran before normal core tests completed and correctly
rejected incomplete XML. A queued offline invocation was stopped before it
executed clean. The normal build then completed successfully; final accounting,
XML/inventory preservation and the complete forced offline build were performed
sequentially. An initial temporary API comparison script also needed blank-line
normalization; final descriptor comparison passes. Neither correction changed
production code or acceptance requirements.

## 9. Reproducibility

**10/10 final normal/forced-offline artifact paths and SHA-256 hashes match.**
The exact release-helper inventory comparison follows existing release.yml practice:
save the final normal `build/publication-sha256.json`, perform the full forced
no-cache offline clean build, then compare with `EXPECTED_INVENTORY`. Scope is
six JARs, two POMs and two .module files, on this same environment with JDK
25.0.4.1 and Gradle 9.8.0. Repository-index timestamps/checksums and signatures
are excluded by established convention. No wider reproducibility claim.

| Artifact | SHA-256 |
| --- | --- |
| `totipo-core-0.1.5-javadoc.jar` | `58c232bab5a4cee5321940ef2229a15bf08760917c6e1da32aefee02fc3b3d1f` |
| `totipo-core-0.1.5-sources.jar` | `d797c0854db469e8288b787dcbeb3dd3eb8e83062bea5c42b0013082b2cccdc4` |
| `totipo-core-0.1.5.jar` | `e99609e59db1d9f52f80c446060e63ce7dde17ad69252fa87d397d0cb1577f81` |
| `totipo-core-0.1.5.module` | `8a3bd815956d59f470f2639cfd95697496abac8c24b6b848579855c05f40bbe5` |
| `totipo-core-0.1.5.pom` | `886ae205c99b23aef40332102b7c31393e88a8b3a513f21f5f18a6a93307e3a2` |
| `totipo-storage-nio-0.1.5-javadoc.jar` | `a9cf8cf97c4eb38e7f1b100c0c364816032737bcfbfef5913e8d56d1ad5e0276` |
| `totipo-storage-nio-0.1.5-sources.jar` | `bee2a5c9f941360b9c499f1518e3b2201e1e94405a34a8d2709033419e2ed67f` |
| `totipo-storage-nio-0.1.5.jar` | `9e559ec75fb09af068f876751f32d696c50c40988a16d28419dfa05d1d1c4dae` |
| `totipo-storage-nio-0.1.5.module` | `4339f56533be8af2fa42b283b7a8513b51bae9de2d7f9aca3294dfec7b03dd88` |
| `totipo-storage-nio-0.1.5.pom` | `1065a65031308327d660e2290213b5489ceeb13a5f11507b13b45ed4d926bb3d` |

## 10. Consumer smoke

The committed consumer already includes a public `validate(VaultSession,
RevisionId, byte[])` call: no transport/provider implementation was needed.
Independent Gradle-metadata and `-PpomOnly` builds compile/run staged 0.1.5 JARs
and preserve existing shared/private NIO opening, empty-root nonmutation and
runtime-only BC loading. Compile boundary is core/NIO 0.1.5; runtime adds only
BC 1.86. No Maven-local/composite/source fallback. The old-compiled consumer and
external-session probe additionally establish the released 0.1.4 binary boundary.

## 11. Human Nix

**PASS — user-reported for this 0.1.5 preparation.** The user reported
“all good with `nix flake check`” after all agent-run qualification passed.
The exact requested and confirmed command was:

```sh
nix flake check
```

The agent executed no Nix command and did not independently capture its terminal
output or exit code. flake.nix remains unchanged and exposes development shells/
formatter, without a Gradle check derivation. This checkpoint validates Nix outputs;
no duplicate Gradle suite through nix develop was requested. This is new
release-preparation evidence, separate from historical implementation Nix results.
After the user report, the agent confirmed unchanged branch/HEAD, an empty index,
passing whitespace checks, protocol/dependency/toolchain invariance and all ten
staged artifact hashes still matching the saved normal inventory.

## 12. Remote CI

| Evidence boundary | Status |
| --- | --- |
| Implementation commit CI | **PASS**: authoritative GitHub API run **37701298434**, exact commit **b3de4ad2b05fb65b412a7fdc862280998352c266**, status **completed**, conclusion **success**. [Run](https://github.com/totipo-org/totipo-java/actions/runs/37701298434). |
| Release-preparation local | **PASS**, qualification recorded above. |
| Human Nix | **PASS**, user-reported `nix flake check` for this preparation. |
| Release-preparation remote CI | **Not run**: preparation remains uncommitted/unpushed; no workflows triggered. |

Read-only GitHub Actions API GETs and canonical Maven Central artifact downloads
are the only remote actions. Implementation CI was initially in progress and
subsequently authoritatively completed successfully; no guessed green status.

## 13. Release-notes draft

[V0_1_5_RELEASE_NOTES.md](V0_1_5_RELEASE_NOTES.md) follows the prior reviewed
notes format and emphasizes the generic unlocked-session candidate capability,
exact defensive ciphertext, two outcomes, compatibility and explicit non-goals.
No sync or Android support marketing claim.

## 14. Release readiness

**READY FOR FINAL HUMAN REVIEW AND RELEASE-SOURCE CI.**
Implementation CI is already green at the exact starting HEAD. Agent qualification
and the human Nix checkpoint pass; release preparation is complete. Publication
readiness remains conditional only on final human review and release-source CI
for the reviewed committed preparation. The protected release workflow must
independently qualify that exact source before any authorized publication.

## 15. Final Git state

The agent ran `git diff --check`, `git status --short`, `git diff --stat`,
`git diff --cached --stat`, branch and HEAD checks. Branch remains `main` and HEAD
remains `b3de4ad2b05fb65b412a7fdc862280998352c266`. The index is empty.
Eight tracked files are modified and two new notes/report files are untracked.
Ordinary diff statistics exclude these untracked documents.

```text
 M .github/workflows/release.yml
 M API_DESIGN.md
 M README.md
 M RELEASE_CHECKLIST.md
 M VERSION
 M core/src/main/java/org/totipo/ObjectCandidateValidation.java
 M core/src/main/java/org/totipo/VaultSession.java
 M publishing/consumer-smoke/gradle.lockfile
?? review/V0_1_5_RELEASE_NOTES.md
?? review/V0_1_5_RELEASE_PREPARATION_REPORT.md
```

Protocol/dependency/toolchain invariance and new-document whitespace checks pass.
Full normal/offline XML is saved outside the checkout before focused filtering;
the final core XML in build is the four-test focused run, not a replacement for
the saved 528-test qualification evidence.

Nothing staged, committed, tagged, released, published or pushed. No remote state
was altered. All preparation changes remain unstaged/uncommitted.

# Whole-Alternative merge selection review (0.1.2 preparation)

## Baseline and scope

- Working directory: `/home/niki/Sources/totipo-java`.
- Starting branch: `main`.
- Full starting HEAD: `dfd76a9a9cff261fdb214ac85457bddb06f1d777`.
- Starting `git status --short`: empty; clean-tree requirement satisfied before edits.
- Inspected 0.1.1 source and retained binary JARs, including VaultState merge/update,
  MergeToken/TokenEditor, secret choices, Alternative/competition/group projections,
  and Saved/AdditionalConflict/PublicationUncertain publication behavior.
- No blocked desktop S4 report was found in the available workspace/home file inventory.
  The supplied capability and contract are the basis for this change.

## Public API and behavior

Exactly one public operation is added:

```java
MergeToken keep(TokenAlternative alternative)
```

It selects a semantic Alternative, not a Head. Multiple Heads may support one
Alternative; neither count nor order conveys preference. Issuer, account, status,
hidden secret equivalence, algorithm, digits and period transfer together from one
captured semantic value, including TOMBSTONED values. No preference for ACTIVE is
introduced. Metadata is per-object and is not part of this semantic transfer;
existing metadata builder behavior remains unchanged.

The operation runs inside the existing builder edit/session lock. It validates
before mutating any field. Repeated keep calls replace all seven semantic fields;
subsequent explicit setters deliberately modify the result. Caller-provided secret
overrides are cleared when keep selects an existing secret. Closed/terminal builders
retain the existing IllegalStateException behavior; closed sessions retain their
existing domain error.

`update(alternative)` is not equivalent: it chooses the matching current or
historical captured Heads for that Alternative and lacks the merge-only freshness
gate. It does not resolve a captured full conflict frontier. Field-composed merges
are equivalent internally and remain supported, but desktop callers otherwise
have to correlate every visible field with the correct hidden secret group. That
makes whole-value selection unnecessarily error-prone. keep performs that operation
atomically without caller inspection of Heads or secret material.

## Membership and secret transfer

The reference must be the implementation's same-session Alternative. Its semantic
identity must occur in the captured merge secret choices, and all of its captured
Heads must belong to the immutable merge basis. Semantic equality includes logical
token and hidden secret equivalence, while excluding Heads. Checking captured Heads
as well rejects historical/new equal-valued references outside the basis. References
with a subset of supporting Heads already in the basis are accepted; they still
select the same captured semantic value. Omitted, foreign-session, unrelated-token,
stale and newly observed out-of-basis references throw IllegalArgumentException;
null throws NullPointerException. Rejection leaves the builder unchanged and causes
no publication.

The implementation finds the existing builder-owned MergeSecretChoice containing
the selected captured value and calls the same private selectSecret helper used by
`secret(MergeSecretChoice)`. This transfers an internal inherited-secret identity,
never returns secret bytes, and never infers equality from TOTP output. Visible setup
fields come from the same captured Alternative. No new secret export capability,
public helper type or overlapping selection operation is added.

## Publication, protocol and compatibility

keep performs no observation, recapture, storage operation or publication. save uses
the unchanged freeze/publication plan, captured-basis AdditionalConflict gate and
retry mechanism. Newly relevant information still returns AdditionalConflict with
zero publication; the desktop can reopen a merge on its latest state. Existing
independent partial-resolution ownership and PublicationUncertain monotonic exact
retries remain unchanged.

Protocol impact: **NONE**. No protocol revision, wire format, TOKEN encoding,
conformance vectors/expectations, storage behavior, graph/fold policy or dependency
changes. Existing field-composed callers remain source-compatible. The interface
addition is additive for consumers of the library's builders; third-party custom
implementations of MergeToken would need to implement the new method.

VERSION is prepared as 0.1.2; repository policy allows preparation separately from
manual protected release dispatch. The two local consumer lock entries advance
with it. Published README consumption examples remain 0.1.1 and explicitly distinguish
unpublished preparation. The 0.1.1 release checklist is identified as historical
and states the existing requirement for separately reviewed 0.1.2 notes/workflow
updates before any future release. No release workflow is changed or invoked.

## Focused coverage

Fourteen new test invocations cover:

- Every semantic field and hidden-secret identity for both ACTIVE and TOMBSTONED
  selections with distinct identity/setup fields; no field synthesis.
- Existing caller-secret override replaced by keep, with no publication until save.
- Manual composition for either selection and identical revision IDs for the
  keep/composed equivalent, including the intervening freshness gate.
- Repeated selection, later explicit setters, terminal builder rejection and
  unchanged inherited-secret equivalence after deliberate identity edits.
- Selecting majority or minority semantic Alternatives when one has two Heads.
- Omitted, unrelated, foreign-session, null, historical equal-valued and newly
  observed equal-valued references rejected before publication.
- Distinct/equal-valued AdditionalConflict, zero publication and reopening against
  the returned latest state.
- Publication uncertainty, failed retry remaining uncertain, and exact retry bytes.
- Reflection verifies the sole keep signature and no secret-returning method on
  MergeToken, MergeSecretChoice or TokenAlternative.

Semantic equality assertions compare the internal secret-equivalent identity as
well as the descriptor; TOTP codes are not used to infer secret equality.

## Validation and artifacts

Environment: OpenJDK 25.0.4.1+1 (Nix-built JDK), pinned Gradle 9.8.0 and Python 3.
Nix itself is absent from PATH, so `nix develop`/Nix evaluation were not run. The
flake supplies a development shell, not a separate test/check output. No Maven
POM build or Maven wrapper exists; Gradle performs Maven artifact validation.

| Command/check | Result |
| --- | --- |
| `./gradlew :core:test --tests org.totipo.api.PublicApiTest` | PASS, preliminary focused run before the final two additional invocations. |
| `./gradlew clean test build verifyPublication consumerSmoke` | PASS, 1m 39s; 377 core + 115 NIO tests, zero failures/errors/skips. |
| `./gradlew --offline --no-daemon --no-build-cache --rerun-tasks clean test build verifyPublication consumerSmoke` | PASS, 1m 43s; all 30 tasks executed, no build cache; 377 core + 115 NIO tests, zero failures/errors/skips. |
| `./gradlew -p publishing/consumer-smoke --offline -PpomOnly clean check` | PASS; separate POM-only compile/runtime boundary and local repository selection. |
| Wrapper SHA-256 versus CI pin | PASS: `238e777fcddd7e34f9708186085def2abd6e08e658505b38718d79d74c21abd5`. |
| `git diff --check`, plus untracked-report whitespace check | PASS. |

The full suite includes the unchanged portable corpus and snapshot/profile tests.
Final PublicApiTest has 81 invocations, including 14 new invocations. Existing
composition, partial-resolution, frozen fold retries, storage and conformance
coverage remain enabled.

Normal publication verification inspected all binary/source/Javadoc JAR entries,
Java 17 production bytecode, POM/module metadata, checksums, unsigned artifact
inventory and dependencies. The independent consumer compiles against local staged
artifacts: compile dependencies are exactly core + NIO 0.1.2; runtime adds only
Bouncy Castle 1.86. Local unsigned staging under build/repository is validation,
not remote publication.

Retained baseline 0.1.1 JARs were inspected with `javap -public -s` for every class
and compared with 0.1.2 outputs. All 176 core and 26 NIO class names are unchanged.
The only declaration additions are the keep signature on the public MergeToken
interface and its implementation on the private ApplicationSession.Merge class.
All pre-existing declarations/descriptors remain. NIO class bytes are identical.
No additional public types, signatures returning secret material or dependencies
appear. Temporary logs, retained baseline JARs and API diffs live under `/tmp`,
outside the review worktree.

## Changed files and final review state

- `core/src/main/java/org/totipo/MergeToken.java`: sole public method and contract.
- `core/src/main/java/org/totipo/format/ApplicationSession.java`: captured selection
  validation, atomic field transfer and shared private secret selection helper.
- `core/src/test/java/org/totipo/api/PublicApiTest.java`: focused regression coverage.
- `API_DESIGN.md`: selection semantics, errors and update/composition distinctions.
- `VERSION`: 0.1.2 preparation.
- `publishing/consumer-smoke/gradle.lockfile`: two local Totipo versions only.
- `README.md`: distinguishes prepared 0.1.2 from published 0.1.1.
- `RELEASE_CHECKLIST.md`: clarifies historical checklist and future release gate.
- `review/WHOLE_ALTERNATIVE_KEEP_REPORT.md`: this review report.

Final branch/HEAD remain `main` / `dfd76a9a9cff261fdb214ac85457bddb06f1d777`.
`git diff --cached --stat` is empty. All work remains unstaged and uncommitted;
no commit, tag, release or push is performed.

Final `git status --short`:

```text
 M API_DESIGN.md
 M README.md
 M RELEASE_CHECKLIST.md
 M VERSION
 M core/src/main/java/org/totipo/MergeToken.java
 M core/src/main/java/org/totipo/format/ApplicationSession.java
 M core/src/test/java/org/totipo/api/PublicApiTest.java
 M publishing/consumer-smoke/gradle.lockfile
?? review/WHOLE_ALTERNATIVE_KEEP_REPORT.md
```

Final tracked `git diff --stat` (the new untracked report is additional):

```text
 API_DESIGN.md                                      |  22 +++
 README.md                                          |   5 +-
 RELEASE_CHECKLIST.md                               |   5 +
 VERSION                                            |   2 +-
 core/src/main/java/org/totipo/MergeToken.java      |  20 ++-
 .../java/org/totipo/format/ApplicationSession.java |  23 +++-
 .../test/java/org/totipo/api/PublicApiTest.java    | 153 +++++++++++++++++++++
 publishing/consumer-smoke/gradle.lockfile          |   4 +-
 8 files changed, 227 insertions(+), 7 deletions(-)
```

Final whitespace checks pass for both the tracked diff and this untracked report.

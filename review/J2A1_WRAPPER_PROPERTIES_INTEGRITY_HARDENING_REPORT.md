# J2A.1 — Wrapper properties integrity hardening

Status: **PASS — J2A.1 complete. Host validation and fresh human Nix qualification passed.**
The owner reported `nix flake check passed` against the frozen inputs; the
post-gate hash/mode audit passes. Previous J2A qualification is not reused.
Changes remain uncommitted. Stop after this milestone; J2B and J4 are deferred.

## Starting state and scope

**PASS** — worked exclusively in `/home/niki/Sources/totipo-java`; no sibling
worktrees inspected. Read all of AGENTS.md, J2A and J1 review reports before
editing. Branch `main`; starting HEAD
`8747a79d30fd0bc17004415eed9c10152e2f6eea` (`Introduce replacement for python`).
Starting `git status --short` empty. J2A's verifier, Nix wiring and report are
committed at HEAD; historical uncommitted wording in its report is superseded.
Classifications: **DEPENDENCY_BUILD** (standalone wrapper qualification inputs)
and **RELEASE_TOOLING** (protected-release Python verifier). No dependency update,
release preparation, runtime/API or protocol change.

Evidence: `/tmp/totipo-j2a1-evidence/`, outside Git source membership.
`baseline-files.json` records starting SHA-256/modes for all tracked regular files.
Temporary classes, fixture files, logs and audit JSON are not intent-added.

## Security issue and reviewed identity

The old raw-line checkers accepted an additional declaration such as:

```properties
distribution\u0055rl=https\://example.invalid/other.zip
```

Java Properties interprets that escaped spelling as `distributionUrl`. Appending
it overrides the canonical URL. This was an existing weakness in both checkers,
not a J2A regression. Other protected keys have the same class of ambiguity.

**PASS** — independently computed before changes; exact checked-out identities
match J2A and the authorized values:

- JAR SHA-256: `238e777fcddd7e34f9708186085def2abd6e08e658505b38718d79d74c21abd5`.
- Complete properties SHA-256: `e81b90975868be2513ca00d6a5a01303a406a5cb01a306210b887e983e4b17ae`.
- Literal `distributionUrl=https\://services.gradle.org/distributions/gradle-9.8.0-bin.zip`.
- Literal `distributionSha256Sum=bafd5ce9cfaea0fbccfdc8439a1ac42fbd4cd9c89dc9a988228d8a2639a58e6c`.

Wrapper files retain exact bytes and mode 0644. No regeneration or upgrade.

## Implementation and acceptance contract

**PASS** — Java independently embeds the properties SHA-256, reads one byte
snapshot, hashes it with JDK MessageDigest and rejects mismatches. That same
snapshot goes through a strict UTF-8 decoder and the existing declaration checks.
The JAR streaming SHA-256, exact declarations, duplicate counts, continuation
rejection and Python-compatible whitespace/line-boundary rules remain intact.
The declaration validator is package-private for independent unit coverage;
the production CLI and `verify()` have no authentication bypass option.
CLI retains two arguments, success 0, verification failure 1 and usage error 2.

**PASS** — Python independently embeds the same reviewed properties digest,
hashes `read_bytes()` with hashlib, then strictly decodes the same bytes as UTF-8
for the unchanged raw-line rules. Existing JAR pin, argument interface, unavailable
file rejection and invocation paths are retained. No third-party dependency.
Neither production verifier obtains a pin from its input, the other verifier,
a generated manifest or runtime configuration. Neither executes Gradle or
uses network APIs.

**Intentional acceptance tightening:** only exact reviewed properties bytes
are accepted. Comments (addition/removal), whitespace, line endings, declaration
order, unrelated properties, escaped keys, values and final newline changes
require a deliberate review and pin update in both implementations. The current
canonical file has no comments to remove; the whole-file identity still covers
any such future change. Semantic checks remain defense in depth; no second
Java-properties parser was introduced. README documents the contract and both
identities required for wrapper maintenance.

## Permanent tests and parser proof

**PASS** — 199 deterministic fixtures: **1 ACCEPT, 198 REJECT**, checked through
both direct Java verification and real CLI subprocesses. Four additional CLI
probes reject missing/extra arguments and an empty JAR path. A separate NUL-path
assertion remains (OS argv cannot carry NUL).

All 183 J2A fixtures remain. Their top-level expectations now accept only exact
canonical bytes; none were deleted to obtain parity. Separately, **190** textual
fixtures exercise original declaration rules, preserving previously accepted raw
forms and all historical duplicate/continuation/whitespace cases independently
of the hash gate.

Added fixtures cover escaped URL keys before/after canonical declarations, same
and different effective URL values, escaped checksum keys, and multiple Unicode
escapes before/after declarations. Added full-file cases cover a harmless
comment, unrelated property, canonical LF-to-CRLF conversion, canonical missing
final newline, reordered declarations, trailing whitespace, truncation and a
one-byte change to an unrelated value. Existing wrong URL/checksum, duplicates,
continuations, empty input, invalid UTF-8 (including in a comment), missing files,
directories and JAR mutations remain negative coverage.

**PASS** — a temporary escaped-key fixture is loaded by the actual JDK
`Properties.load(InputStream)`: its effective `distributionUrl` equals
`https://example.invalid/other.zip`. A second `Properties.load(Reader)` assertion
proves placing the escaped declaration first allows the later canonical value
to win. Both files are nevertheless rejected by whole-file authentication.
No Gradle execution is involved in these regressions.

## Differential and pin consistency evidence

**PASS** — `parity.py` runs strengthened Python and Java on the same fixture paths
and bytes, compares success/failure dispositions, and also executes both original
HEAD implementations against those same inputs. **203 comparisons: 1 ACCEPT,
202 REJECT, zero unexplained differences.** This includes four CLI argument probes.
Only canonical reviewed configuration is accepted by both strengthened checkers.
`parity.json` records every disposition. The final reviewed runner's 199 fixture
inputs and dispositions were independently compared with the differential inputs;
all file bytes, missing/directory kinds and expected/actual dispositions match.

**63 intentional acceptance tightenings**, including 48 formerly accepted J2A
cases and 15 newly added historically accepted variants, now reject. These are
contract changes, not parity failures. Full case names:

```text
lf
crlf
no-final-newline
comments-and-unrelated
commented-misleading-extras
key-prefix-unrelated
escaped-key-extra
non-whitespace-prefix-extra
non-whitespace-suffix-extra
line-boundary-a
line-boundary-d
line-boundary-b
line-boundary-c
line-boundary-1c
line-boundary-1d
line-boundary-1e
line-boundary-85
line-boundary-2028
line-boundary-2029
whitespace-comment-9
whitespace-comment-a
whitespace-comment-b
whitespace-comment-c
whitespace-comment-d
whitespace-comment-1c
whitespace-comment-1d
whitespace-comment-1e
whitespace-comment-1f
whitespace-comment-20
whitespace-comment-85
whitespace-comment-a0
whitespace-comment-1680
whitespace-comment-2000
whitespace-comment-2001
whitespace-comment-2002
whitespace-comment-2003
whitespace-comment-2004
whitespace-comment-2005
whitespace-comment-2006
whitespace-comment-2007
whitespace-comment-2008
whitespace-comment-2009
whitespace-comment-200a
whitespace-comment-2028
whitespace-comment-2029
whitespace-comment-202f
whitespace-comment-205f
whitespace-comment-3000
added-comment
added-unrelated
canonical-crlf
canonical-no-final-newline
reordered-declarations
trailing-whitespace
one-byte-properties
escaped-url-override-after
escaped-url-override-before
escaped-url-same-after
escaped-url-same-before
escaped-checksum-after
escaped-checksum-before
escaped-multiple-after
escaped-multiple-before
```

**PASS** — permanent tests independently hash canonical JAR/properties bytes and
compare fixed reviewed test pins against Java constants (reflection) and Python
source declarations (extraction). Canonical Java verification enforces its URL
and distribution checksum; tests assert Python's literal declarations match the
canonical ones. Neither production implementation depends on this extraction.
Two temporary Python source copies with separately zeroed JAR/properties pins
are correctly rejected by those permanent tests (`pin-divergence.log`).

## JDK-only and Nix integration

**PASS — host compilation / inspected integration.** JDK 25.0.4.1+1; javac uses
`--release 25 -encoding UTF-8 -Xlint:all -Werror -proc:none`, exact source files
and explicit sourcepaths. Main compiles with an empty external classpath directory;
tests compile against main classes only. No Jackson, JUnit, Gradle or production
classes are needed. All runner subprocesses invoke only the JDK Java executable.

`checks.wrapper` still uses `nix/wrapper-verifier.nix`, pinned JDK 25 and the exact
two-source fileset. Its complete runner executes all regressions and canonical
hash assertions, followed by canonical CLI verification. The sole Nix wiring
change supplies `nix/verify-wrapper.py` as a read-only source input for test-time
pin consistency; Python is neither a dependency nor executed inside this check.
No redundant check or new Nix dependency. `flake.nix` and source filters unchanged;
`checks.java`, `checks.wrapper`, `checks.tooling` and `apps.tooling` retained.

Inspected flake inputs and qualification.nix: `nix/` is selected by the existing
Java source filter; wrapper Java sources are separately selected by the wrapper
fileset. README and review reports are excluded. Included inputs are frozen;
only excluded report updates may follow a successful human gate.

## Protected release compatibility and remaining Python

**PASS** — inspected actual `.github/workflows/release.yml` call sites at lines
74, 144 and 204: preflight, publish-central and verify-central all still run
`python3 -B nix/verify-wrapper.py gradle/wrapper/gradle-wrapper.jar gradle/wrapper/gradle-wrapper.properties`.
Canonical host invocation with that interface passes. The script remains mode
0644, invoked by the Python interpreter as before. Workflow bytes/mode unchanged.
No protected-job Nix, approval, identity, secrets, signing, retry, remote check,
tagging or release policy changed. Runtime CI execution is **NOT RUN** before
commit/push; no remote success claimed.

Python remains required for the protected wrapper check until J4, publication
verification/check/copy, release controller, test accounting, guardrail tests,
qualification and development environments. The repository is not Python-free.

## Host qualification and execution accounting

**PASS** — original Python canonical verification and original JDK-only runner:
183 fixtures, 49 ACCEPT / 134 REJECT, plus four CLI probes and NUL-path assertion.

**PASS** — baseline `./gradlew test`: 28s; 13 tasks, 2 executed / 11 up-to-date.
Immediate accounting: 405 tests, zero failures/errors/skips, 92/92 portable cases,
snapshot 3/3, profile 1/1. Baseline XML preserved outside build outputs.

**PASS** — hardened direct compilation and runner, final reviewed runner, pin
consistency negatives and differential suite described above. Expected negative
fixtures and intentionally divergent pins are successful test evidence.

**PASS** — first final `./gradlew test build javadoc verifyPublication consumerSmoke`:
15s; 27 outer tasks, 8 executed / 19 up-to-date; nested module consumer 13s,
5 tasks executed. Immediate accounting again 405 tests and all required cases.
Full XML and accepted manifest preserved. Python guardrails: **51 tests, OK**;
existing synthetic HTTPError tests emit ResourceWarnings without failures.
Manifest fresh-byte check: 10/10 artifacts. Independent POM-only consumer:
8s, 5 tasks executed.

A test-runner comment was corrected (optional evidence is now argument four),
imports sorted and an extra blank line removed after that first gate. To honor
input invalidation policy, recompiled/reran the runner and repeated the affected
final Java/publication/consumer qualification. No behavioral or fixture changes;
no unexpected failed build/test attempt.

**PASS — frozen final gate:** same full Gradle task set: 6s; 27 outer tasks,
8 executed / 19 up-to-date; module consumer 5s, 5 tasks executed. Immediate
accounting: **405 tests, zero failures/errors/skips; 92/92 cases; snapshot 3/3;
profile 1/1**. XML and manifest preserved in `frozen-xml/` and
`frozen-publication-manifest.json`. Fresh-byte manifest check 10/10; independent
POM-only consumer 985ms, 5 tasks executed. Python helpers did not change after
passing their full guardrails, so those tests were not redundantly repeated.

| Invocation category | Count / result |
| --- | --- |
| Broad baseline Gradle tests / XML accounting | 1 / 1 PASS |
| Focused Gradle tests / focused Python tests | 0 / 0 |
| Direct javac main / test | 2 / 3 PASS (includes original baseline) |
| Original / hardened full standalone runner | 1 / 2 PASS |
| Pin-divergence negative runner probes | 2 expected failures, PASS |
| Final differential suite | 1 PASS, 203 comparisons |
| Canonical Python outside parity | 2 PASS, original and hardened |
| Canonical Java CLI outside runner/parity | 1 PASS, hardened |
| Final full Java gate / immediate accounting | 2 / 2 PASS; justified invalidation rerun |
| Publication verification / module consumer | 2 each, nested in those final gates |
| Full Python guardrails | 1 PASS, 51 tests |
| Manifest fresh-byte check / POM-only consumer | 2 / 2 PASS |
| Separate host forced-offline full suite | 0 NOT RUN |
| Human cache update / agent Nix | 0 / 0 NOT RUN |
| Fresh human J2A.1 Nix | 1 human-reported PASS |
| Remote CI / release reproducibility / commit / push / release actions | 0 NOT RUN |

Up-to-date test tasks reuse current full XML; they do not imply fresh test task
execution. Nested consumers do not count as additional full suite invocations.
No focused Gradle run overwrote full XML. No separate host forced-offline full
suite: acquisition, Gradle behavior, resolution and fixed cache are unchanged.
The required isolated consumers did run. Release-only normal/offline ten-artifact
comparison, signing, live remote publication, tagging and GitHub releases did not.

## Protected invariance and input freeze

**PASS** — 215 explicitly protected files retain starting SHA-256 and modes:
production trees, spec corpus, SPEC_PIN, VERSION, gradle/, publishing/,
package-deps.json and flake.lock. All other existing tracked files except the
five listed below also retain exact starting bytes/modes, including workflow,
historical reports, flake.nix, qualification.nix and J1 tooling. Protected identities
remain Java 17 production bytecode, JDK 25 tooling, Jackson Core 2.21.7, BC 1.86,
Gradle 9.8.0, protocol v1/r19 and all 92 cases. Publication checks and independent
consumers retain existing Maven dependency semantics. No dependency/cache update,
protocol/version bump or release-controller change.

`final-qualification-inputs.json` freezes all **298** Git-known non-review regular
files (a conservative superset of included inputs), with SHA-256 and filesystem
modes. Inventory SHA-256:
`01b7b3123de39a8d4f38569a1a2c0c189fdc10cc1f542af0acfc536fee82cf71`.
Changed pre-existing files:

| File | SHA-256 | Mode |
| --- | --- | --- |
| `README.md` | `e170d3589609616997e82d37ed94bb38775036fc6e58f682fbb1e0ea0302e58c` | `0o644` |
| `nix/verify-wrapper.py` | `92a29c82a730c43166115f00472376196241caa1f9709f1c50a7cf73a4d95947` | `0o644` |
| `nix/wrapper-verifier.nix` | `9626fa7f3a3cc3fa7c11963c634842e141e61c4e645faa66c164ae545bc77eeb` | `0o644` |
| `tooling/wrapper/src/main/java/org/totipo/tooling/wrapper/WrapperVerifier.java` | `d599b577330f01e8a0dd643935bab8ff7b79f57099b10771413667f493b2b327` | `0o644` |
| `tooling/wrapper/src/test/java/org/totipo/tooling/wrapper/WrapperVerifierTest.java` | `04361c7c342a75a8c7f8e75215414998e3824d346bc2cba94e401326fb87ff6e` | `0o644` |

The sixth changed file is this excluded review report, mode 0644. No other file
is intended for the milestone. Report-only finalization cannot change compiled
or included Nix inputs. Any included-input edit invalidates the human result.

## Human qualification and remaining work

**PASS — human-reported qualification.** The owner reported
“nix flake check passed” after the final input freeze. One fresh human J2A.1
gate invocation is reported. No detailed log, duration or task/cache execution
counts were supplied; none are inferred. This qualifies the existing Java and
tooling checks and hardened independent Java wrapper check. The agent executed
no Nix command. No second build or cache regeneration was requested.

**PASS — post-gate audit.** All 298 frozen non-review files still match their
recorded SHA-256 and filesystem modes. Branch `main`, starting HEAD, six-file
changed inventory and Git source membership are unchanged. No actual content is
staged; the report's intent-to-add entry remains. Only this explicitly excluded
report was finalized after the gate; no included input changed or invalidated
qualification. Post-gate evidence: `post-human-freeze-audit.json`.

**FAIL — none:** no unexpected qualification failures or unresolved in-scope
weakness observed. **PENDING — none** for this milestone. **NOT RUN:** remote CI,
release reproducibility, signing, remote publication, tagging, commit and push.
J2A.1 is complete; J2B/J4 and broader migrations remain deferred pending owner
review, commit and CI qualification.

## Final Git evidence

Only the new report receives intent-to-add; all existing changes remain unstaged.
`git diff --cached --quiet` and `git diff --check` pass after finalization.
Final recorded inventories follow.

`git status --short`:

```text
 M README.md
 M nix/verify-wrapper.py
 M nix/wrapper-verifier.nix
 A review/J2A1_WRAPPER_PROPERTIES_INTEGRITY_HARDENING_REPORT.md
 M tooling/wrapper/src/main/java/org/totipo/tooling/wrapper/WrapperVerifier.java
 M tooling/wrapper/src/test/java/org/totipo/tooling/wrapper/WrapperVerifierTest.java
```

`git diff --name-only`:

```text
README.md
nix/verify-wrapper.py
nix/wrapper-verifier.nix
review/J2A1_WRAPPER_PROPERTIES_INTEGRITY_HARDENING_REPORT.md
tooling/wrapper/src/main/java/org/totipo/tooling/wrapper/WrapperVerifier.java
tooling/wrapper/src/test/java/org/totipo/tooling/wrapper/WrapperVerifierTest.java
```

`git diff --cached --name-only`: empty; `git diff --cached --quiet`: **PASS**.
`git diff --check`: **PASS**. Branch/HEAD unchanged from the recorded start.

`git diff --stat`:

```text
 README.md                                          |   8 +-
 nix/verify-wrapper.py                              |   8 +-
 nix/wrapper-verifier.nix                           |   3 +-
 ...RAPPER_PROPERTIES_INTEGRITY_HARDENING_REPORT.md | 382 +++++++++++++++++++++
 .../totipo/tooling/wrapper/WrapperVerifier.java    |  19 +-
 .../tooling/wrapper/WrapperVerifierTest.java       | 107 +++++-
 6 files changed, 513 insertions(+), 14 deletions(-)
```

# Totipo Java 0.1.3 release preparation

## Baseline and intent

Starting branch: `main`. Full HEAD:
`f0a481d6261a3d01c78577622fdf24bc1e5ee607`. Starting `git status --short`
was empty. This is the committed 0.1.2 whole-Alternative implementation.

The reported 0.1.2 workflow failed in tag-and-github-release because release.py
always read V0_1_1_RELEASE_NOTES.md. The heading check correctly rejected v0.1.1
notes for a v0.1.2 release, but ran only after publishing and remote verification.
The user chose a fresh 0.1.3 release instead of repairing the old run.

## Changes and guardrails

- VERSION and the two local consumer dependency locks advance to 0.1.3.
- Added reviewed release notes at review/V0_1_3_RELEASE_NOTES.md.
- release.py selects V<version-with-dots-replaced-by-underscores>_RELEASE_NOTES.md
  using the validated requested version; no hardcoded current-version notes file.
- The shared identity check validates the notes file and exact version heading
  during preflight, after approval and immediately before upload. Missing or
  mismatched notes fail before network identity checks or publication.
- The final tag/release job reads the same version-specific notes.
- Tests cover selection while older notes coexist, missing notes, mismatched
  headings, preflight rejection before remote calls, and the mocked 0.1.3
  annotated-tag/GitHub-release path. Existing 0.1.1 idempotency and immutable
  release/tag/remote-artifact checks remain enabled.
- Normal CI now runs release guardrail tests as well as the existing release
  preflight job. Updated workflow labels/examples and the current release checklist.
- README distinguishes prepared 0.1.3 from its existing 0.1.1 consumption examples.

Identity, current-main, clean-tree, immutable Central/tag/release absence,
protected environment approval, signing, exact remote bytes, independent consumers
and same-run retry requirements are unchanged. No bypass or republish path for
0.1.2 is added. No Java production source/API, wire, storage, conformance corpus,
external dependency or protocol revision changes from 0.1.2. keep remains present
with its previously reviewed complete-value, captured-basis and save behavior.

## Validation

Environment: JDK 25, Gradle 9.8.0 and Python 3. Nix is absent from PATH.

| Check | Result |
| --- | --- |
| `python3 -B -m unittest discover -s publishing -p test_release.py` | PASS: 21 tests, including the new failure paths and mocked 0.1.3 tag/release. |
| Version-specific notes validation against actual VERSION | PASS: `## v0.1.3`. |
| `./gradlew clean test build verifyPublication consumerSmoke` | PASS: 2m 27s, all 30 tasks executed. |
| `python3 -B publishing/release.py tests` (also checked after normal build via helper) | PASS: 492 JUnit tests, zero failures/errors/skips; exact-set 90/90 portable cases, snapshot 3/3, profile 1/1. |
| `./gradlew --offline --no-daemon --no-build-cache --rerun-tasks clean test build verifyPublication consumerSmoke` | PASS: 1m 51s, all 30 tasks executed; 492 tests and 90/90 corpus cases pass. |
| Exact normal/offline artifact inventory comparison | PASS: all 10 SHA-256 hashes identical. |
| `./gradlew -p publishing/consumer-smoke --offline -PpomOnly clean check` | PASS: 5 tasks executed; only core/NIO 0.1.3 on compile boundary, BC 1.86 added at runtime. |
| Focused spec/profile integrity and `release.py focused-tests` | PASS: 4 tests, zero failures/errors/skips; snapshot 3/3, profile 1/1. |
| `sha256sum -c SNAPSHOT.sha256` in the pinned snapshot | PASS: 97/97 files. |
| Wrapper SHA-256 | Matches the unchanged CI/release pin. |
| Tracked and untracked whitespace checks | PASS. |

Normal verification confirms 0.1.3 POM/module metadata, all ten unsigned staged
artifacts, Java 17 bytecode, binary/source/Javadoc contents and both module
compile/runtime dependency boundaries. Nix validation is unavailable; flake files
are unchanged. Notes explicitly include the 0.1.2 API highlights alongside the
0.1.3 release fix. Historical reports and 0.1.1 notes are preserved.

The Python suite mocks all network calls, signing and Git mutations. Actual
tag-release/identity commands with remote Git writes are not executed locally.
Local unsigned Maven staging validates packaging only; it does not publish remotely.

## Review and next release

All changes remain unstaged and uncommitted. No commit, push, tag, GitHub release,
Central upload or workflow dispatch is performed. For the new release, review and
commit these changes, push through the normal review process, and dispatch Release
on main with version 0.1.3 and that new full reviewed commit SHA. Keep main at that
commit throughout the release. Do not use the prior 0.1.2 SHA or rerun its old
workflow expecting these changes to appear.

Final branch and HEAD remain main / f0a481d6261a3d01c78577622fdf24bc1e5ee607.
Index is empty. Changed files are shown below; the two new notes/report files are
untracked and additional to the tracked diff statistics.

```text
 M .github/workflows/ci.yml
 M .github/workflows/release.yml
 M README.md
 M RELEASE_CHECKLIST.md
 M VERSION
 M publishing/consumer-smoke/gradle.lockfile
 M publishing/release.py
 M publishing/test_release.py
?? review/V0_1_3_RELEASE_NOTES.md
?? review/V0_1_3_RELEASE_PREPARATION_REPORT.md
```

```text
 .github/workflows/ci.yml                  |  3 +++
 .github/workflows/release.yml             |  4 ++--
 README.md                                 |  9 +++++----
 RELEASE_CHECKLIST.md                      | 30 +++++++++++++---------------
 VERSION                                   |  2 +-
 publishing/consumer-smoke/gradle.lockfile |  4 ++--
 publishing/release.py                     | 12 +++++++++--
 publishing/test_release.py                | 33 +++++++++++++++++++++++++++----
 8 files changed, 66 insertions(+), 31 deletions(-)
```

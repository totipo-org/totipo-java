# 0.3.0 initial release preflight diagnostics

Starting HEAD: `83324c78abc9893fdefed22201d6c658731b89bb`, clean `main`.
Classification selected before edits: `RELEASE_TOOLING`; Markdown report only
also falls under `DOCS_JAVADOC`. This is a diagnostic helper change, not a new
release preparation or publication attempt.

## Investigation

The user supplied the failed `release initial` log. Read-only GitHub run/job
inspection identified run [43775744045](https://github.com/totipo-org/totipo-java/actions/runs/43775744045).
Initial source/eligibility failed; qualification and publication jobs were skipped.
The failure occurred before signing, reservation or upload in this run.

Local checks against the exact release commit passed dispatch/source identity,
VERSION/LF, permanent notes validation and the exact r19 pin. An isolated temporary
clone passed the production GitQueries HEAD, cleanliness and acquired-main ancestry
checks without modifying this checkout's refs. Production HttpQueries returned 404
for both Central POMs. Anonymous read-only GitHub checks returned 404 for both the
v0.3.0 tag and Release. These observations do not establish the hosted token's
behavior or identify the hosted failure. No credentials were requested or printed.

## Change and limits

`release initial` now prints fixed phase labels on stderr before input, dispatch,
source, Git, GitHub client, GitHub absence and Central absence checks, followed by
a success label only when all checks pass. The existing generic failure message
remains. Labels contain no environment values, exception messages, process output
or HTTP bodies. Validation, fail-closed behavior and all mutation paths are unchanged.
Other release operations emit no new progress messages.

This removes the diagnostic blind spot, not the unknown hosted failure. The last
phase label on a subsequent failed initial check will narrow the next investigation.
No release dispatch is performed here.

## Validation and accounting

Evidence: `/tmp/totipo-0.3.0-initial-diagnostics-83324c7`.
Baseline: one focused standalone ReleaseTest invocation, 46 assertions passed.
Direct JDK 25 compilation of main and test tooling passed with release 25,
strict lint, warnings-as-errors, no annotation processing and only the pinned
Jackson Core 2.21.7 JAR on the external classpath. The pinned JAR was already
hash-verified during release preparation.

Final stable standalone guardrails: one complete pass of all eight permanent
runners listed in `nix/tooling.nix` plus the launcher smoke:

- StrictJsonTest: 43 assertions.
- PublicationTest: 127 assertions (synthetic staging only).
- ReleaseTest: 46 assertions.
- AuthorizationTest: 144 assertions (synthetic signing/inventory).
- RemoteVerificationTest: passed (transport fixtures).
- FinalizationTest: passed (create-only tag/release fixtures).
- FinalizationProcessTest: passed (process isolation fixtures).
- TestResultsTest: 29 assertions and 26 accounting fixtures.
- Main smoke: passed.

Also passed: launcher unknown-command and extra-empty-argument rejection (exit 2),
and two ad hoc initial CLI invocations in the temporary clean clone. The successful
initial check used synthetic GitHub 404 responses, a synthetic token and production
read-only Central acquisition. The deliberate dispatch-mismatch invocation exited
1 at the correct phase; neither transcript contained the sentinel dispatch value
or synthetic token. These fixtures do not establish live hosted-token behavior.

Invocation accounting: one focused standalone baseline, one final full standalone
guardrail pass, two ad hoc initial CLI checks, two CLI argument rejection checks.
No failed validation attempt or rerun; no included edits after final validation.

Broad Gradle baselines, focused Gradle tests, final full Java gates,
publication/consumer builds and separate host offline builds: 0; no Java library,
publication or build behavior changed. No release reproducibility rerun is needed.
Human Nix runs for this helper change: 1, passed as reported by the user:
"ok nix flake check passed, I'm committing and rerunning the release".
No command log was supplied. The agent ran no Nix. Only this excluded report was
updated afterward; included inputs remain frozen and need no qualification rerun.

## Git and handoff

Tooling Java is included independently through `nix/tooling.nix`; `review/` is
excluded. Included inputs are frozen after the helper edit. Only this excluded
report was finalized afterward. Actual contents remain unstaged; the new report has
retained intent-to-add membership. `git diff --check` and `git diff --cached --quiet`
pass. Final HEAD remains `83324c78abc9893fdefed22201d6c658731b89bb` on `main`.
Recorded `git status --short`:

```text
 A review/V0_3_0_INITIAL_DIAGNOSTICS_REPORT.md
 M tooling/src/main/java/org/totipo/tooling/release/ReleaseInspectionCli.java
```

`git diff --name-only` lists exactly those two paths; `git diff --cached
--name-only` emits nothing. No content was staged or committed.

Review/commit and successful ordinary CI must
precede any separately authorized fresh Release dispatch using the new reviewed SHA.
The publisher's first-attempt rule is unchanged. This run did not reserve a tag;
the original failed workflow must not be rerun as a publication retry.

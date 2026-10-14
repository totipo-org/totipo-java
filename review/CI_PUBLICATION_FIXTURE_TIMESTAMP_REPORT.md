# CI publication fixture timestamp fix

Starting state: clean `main`, HEAD
`0cd0631c56810a97da0f456793ebbd5b4921b18f` (`release simplification`).
Classification: `RELEASE_TOOLING` (standalone publication test fixture only).

## Cause and change

`PublicationTest.manifestTests` accepts a fixture, recreates it while testing
failure cleanup, then compares the integrated CLI manifest against the original.
The fixture ZIP writer implicitly used current timestamps. Crossing a ZIP DOS
timestamp boundary changed JAR bytes, module hashes and the manifest, despite
identical logical contents. This explains the reported line-311 assertion.

Set every synthetic ZIP entry's local timestamp to 2000-01-01 00:00:00, inside
the DOS timestamp range. Keep exact manifest equality and all rejection checks.
Production publication code, dependencies, locks, verification, Nix inputs and
release activation settings are unchanged.

## Validation and invocation accounting

Evidence is outside the checkout at `/tmp/totipo-ci-fix/`.
Used installed JDK 25.0.4.1, direct `javac --release 25`, UTF-8,
`-Xlint:all -Werror -proc:none`, explicit sourcepaths and only pinned Jackson
Core 2.21.7 externally. Downloaded Jackson from canonical Maven Central; SHA-256
`8c5623b98f32d5f7e1287ff61ed50e08ab7b11381e991fff89327faabba42261`
matches both committed verification metadata and package-deps SRI.

- Baseline broad Gradle invocations: 0; helper-test-only scope.
- Focused Gradle tests: 0.
- Focused standalone test invocations: 2. Before the fix, an external copy of
  PublicationTest with a 2200 ms delay before fixture recreation reproduced the
  reported assertion (line 312 after diagnostic insertion), exit 1. After the
  fix, an external copy with that delay before both recreations passed all 137
  assertions. Logs: `baseline-delayed.log`, `fixed-delayed.log`. Diagnostic
  sleeps are not committed and do not affect the normal tests.
- Final full standalone guardrail passes: 1. Independently recompiled main/test
  tooling and executed every permanent runner in `nix/tooling.nix`: StrictJsonTest
  (43), PublicationTest (137), ReleaseTest (46), AuthorizationTest (144),
  RemoteVerificationTest, FinalizationTest, FinalizationProcessTest,
  TestResultsTest (29 assertions, 26 fixtures), WorkflowBoundaryTest and Main
  smoke. All passed. Source-dependent runners used the current checkout;
  process tests used installed Git/Bash. Log: `final-guardrails.log`.
- JDK-only wrapper passes: 1; independently compiled main/test with no external
  dependencies, then ran WrapperVerifierTest and WrapperVerifier successfully.
- Final full library Java gate: 0; no library/build/publication behavior changed.
- Gradle publication/consumer passes: 0. PublicationTest's three nested synthetic
  CLI semantic verifications per successful run are not Gradle publication gates.
- Separate host offline full passes: 0; no dependency acquisition/resolution or
  build changes.
- Human Nix invocations reported in this repair: 1; user confirmed
  `nix flake check` passed. No agent Nix invocations.
- Setup failure: attempted Python orchestration could not start because python3
  is unavailable; no compilation/tests ran in that attempt. Switched to shell.

## Freeze and final Git state

Inspected `qualification.nix`, `flake.nix`, `nix/tooling.nix` and
`nix/wrapper-verifier.nix`. The changed Java test is included by tooling's Java
fileset; `review/` is excluded. The user confirmed the final human
`nix flake check` passed. Subsequent inspection found the same starting HEAD and
the same test patch; only this excluded report was updated afterward, so the
qualification remains valid. No dependency cache checkpoint or release
reproducibility procedure applies to this fixture repair.

The new report has intent-to-add membership only; no file contents are staged.
`git diff --cached --quiet` passes. Final recorded state:

```text
$ git status --short
 A review/CI_PUBLICATION_FIXTURE_TIMESTAMP_REPORT.md
 M tooling/src/test/java/org/totipo/tooling/publication/PublicationTest.java
$ git diff --name-only
review/CI_PUBLICATION_FIXTURE_TIMESTAMP_REPORT.md
tooling/src/test/java/org/totipo/tooling/publication/PublicationTest.java
$ git diff --cached --name-only
(empty)
```

No commits, agent Nix commands or live release mutations performed.

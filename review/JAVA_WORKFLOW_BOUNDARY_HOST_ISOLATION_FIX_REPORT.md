# Workflow boundary host isolation correction

Started from clean `main` at `1ef3786d40cac11b7315d54f938b564dd32b8e43`.
Classification: `RELEASE_TOOLING`; test-fixture correction only.

## Cause and correction

The reported CI failure at `WorkflowBoundaryTest.java:86` occurs in the
installer-acquisition success fixture. The fixture mocked curl and sha256sum,
but the actual action also rejects Nix on PATH or at
`/nix/var/nix/profiles/default/bin/nix`. Those probes still observed the test
host, making the fixture dependent on the Nix build environment.

The original runner passed on this host. An independently compiled temporary
copy of the original test, with the profile-presence probe returning false for
the absence assertion, reproduced the exact line-86 boundary failure.

The corrected fixture supplies explicit statuses for both presence probes and
fails with status 99 for unexpected probe arguments. It executes the actual
action shell with built-in function fakes and covers: both probes absent,
checksum rejection, download failure, PATH presence, profile presence, and
both forms of presence. Rejected preinstalled cases make downloader/hash fakes
fail with 99 if reached. No real Nix or downloader runs in these fixtures.

The production action, release workflows, disabled activation guards, Java
library, dependencies, locks, cache, source filter and Nix definitions are
unchanged. No release preparation or remote mutation occurred.

## Validation and invocation accounting

Compiled all standalone main/test classes directly with JDK 25 using
`--release 25 -encoding UTF-8 -Xlint:all -Werror -proc:none`, with only Jackson
Core 2.21.7 as the external dependency. Downloaded that JAR into temporary
evidence storage and verified its SRI against the existing package-deps.json.

| Category | Invocations and results |
| --- | --- |
| Broad baseline Gradle | 0; release-tooling fixture scope |
| Focused Gradle tests | 0 |
| Original boundary baseline | 3: unavailable `/bin/bash` launch failed; corrected store Bash passed; controlled profile-present reproduction failed at line 86 as expected |
| Corrected focused standalone runner | 1; passed all acquisition, cleanup, release-boundary and input fixtures |
| Stable standalone guardrails | 1 complete sequence; all nine permanent test runners in nix/tooling.nix plus Main smoke passed |
| Standalone CLI rejection checks | 2; unknown command and extra empty argument each returned status 2 |
| JDK-only wrapper guardrails | 1 successful sequence; WrapperVerifierTest and actual wrapper verification passed |
| Final full Java gate | 0; no library/build/publication behavior changed |
| Gradle publication/consumer passes | 0; synthetic PublicationTest ran in standalone guardrails |
| Separate host offline full Gradle | 0; no acquisition/resolution/build change |
| Human Nix runs for this correction | 1; `nix flake check` passed as reported by the human |
| Release artifact reproducibility | 0; routine fixture correction |

Setup failures: initial preparation used unavailable `python3` on PATH; retried
with the installed absolute Python path. The first wrapper compilation lacked
the empty classpath directory, so strict compilation rejected the path warning.
Created the directory and reran only wrapper compilation/tests successfully.
The main standalone sequence had already passed and was not repeated.

Evidence remains outside build outputs at
`/tmp/totipo-workflow-fixture-wj88oxp7/`: baseline.log (missing Bash),
baseline-host.log, baseline-ci-reproduction.log, focused.log,
full-guardrails.log and wrapper-guardrails.log. No full-suite Java XML was
produced or overwritten. No Gradle tasks or nested consumer tasks executed.

## Freeze and Git state

Inspected qualification.nix and flake.nix: review reports are excluded from
library qualification; tooling Java and the workflow/action fixture inputs are
included through nix/tooling.nix. Included inputs remain frozen after the human gate.
Changed test SHA-256:
`c6aa469ab88cb6e7d446cc7fdb11d124c736fd60845a26527c01dc0f0bdb5266`.

HEAD remains the starting commit on main. Changes are unstaged; this report has
intent-to-add source membership only. Final expected status/name-only output:

```text
git status --short
 A review/JAVA_WORKFLOW_BOUNDARY_HOST_ISOLATION_FIX_REPORT.md
 M tooling/src/test/java/org/totipo/tooling/release/WorkflowBoundaryTest.java

git diff --name-only
review/JAVA_WORKFLOW_BOUNDARY_HOST_ISOLATION_FIX_REPORT.md
tooling/src/test/java/org/totipo/tooling/release/WorkflowBoundaryTest.java

git diff --cached --name-only
(empty)
```

`git diff --check` and `git diff --cached --quiet` passed at handoff and again
after recording the human result. The human reported `nix flake check` passed.
The changed test hash still matches the frozen value above; only this excluded
report was updated afterward. Qualification is complete; the agent ran no Nix.

# 0.3.0 GitHub runtime token transport fix

Starting HEAD: `dc2c6e3cb25d94720343532a97691ae04fcc482d`, clean `main`.
Classification selected before edits: `RELEASE_TOOLING`; the Markdown report also
falls under `DOCS_JAVADOC`. This changes standalone credential transport validation,
not library behavior or publication authorization.

## Diagnosis

The new user-supplied log stops at "checking GitHub client configuration", before
the GitHub absence phase. The configured RELEASE_CURL path is absolute and GH_TOKEN
comes from github.token. The token character check accepts only letters, digits
and underscores, so it rejects modern stateless installation tokens.

GitHub documents that the rollout includes Actions GITHUB_TOKEN, with the new
ghs_APPID_JWT format and a variable length around 520 characters:
[format announcement](https://github.blog/changelog/2026-04-24-notice-about-upcoming-new-format-for-github-app-installation-tokens/).
The [October rollout completion](https://github.blog/changelog/2026-10-02-stateless-github-app-installation-tokens-rolled-out/)
confirms the new default. JWT dots and base64url hyphens are incompatible with the
old check. This is a reproducible source defect and explains the hosted phase
failure; the live token has not been inspected or requested.

## Change and regression coverage

Accept opaque printable ASCII credentials, including JWT punctuation, while
preserving the 1024-character bound and rejecting whitespace, controls, Unicode,
quotes and backslashes. Tokens remain stdin-only to curl's quoted Authorization
configuration; no token parsing, signature checks, logging or credential argv is
introduced. GitHub authenticates the opaque token. All release eligibility,
reservation, signing, publication and retry restrictions remain unchanged.

The user questioned format validation beyond presence. The check now enforces
only presence, the existing size bound and safe insertion into this quoted curl
configuration. It does not match a bearer/JWT/GitHub syntax or inspect claims.

Process-fixture regressions cover legacy tokens, a modern JWT-shaped token, opaque
punctuation/padding (including non-bearer-syntax punctuation) and the length bound.
Unsafe transport-input tests cover empty,
null, oversized, CR/LF/NUL/tab/space, quotes, backslashes and Unicode. No live GitHub
request, tag operation, signing or upload is performed by these tests.

## Validation and accounting

Evidence: `/tmp/totipo-0.3.0-token-fix-dc2c6e3`.
Baseline: one focused standalone FinalizationProcessTest invocation, passed.
One regression invocation with the new process-fixture tests against the old
implementation failed at the modern-token fixture with "Missing or invalid GitHub
runtime token", before curl. This independently reproduces the format defect.

Direct JDK 25 main/test compilation passed with release 25, strict lint,
warnings-as-errors, no annotation processing and only pinned Jackson Core 2.21.7
on the external classpath. The cached JAR was hash-verified in release preparation.
All eight permanent runners from nix/tooling.nix and the launcher smoke passed:
StrictJsonTest (43 assertions), PublicationTest (127), ReleaseTest (46),
AuthorizationTest (144), RemoteVerificationTest, FinalizationTest,
FinalizationProcessTest and TestResultsTest (29 assertions, 26 fixtures).
The process fixture additionally verifies that a failing curl which echoes its
modern synthetic credential does not expose that credential through the exception.
Unknown-command and extra-empty-argument CLI rejection checks both passed (exit 2).

Invocation counts: 1 focused standalone baseline, 1 intentionally failing focused
regression reproduction, 2 full standalone guardrail passes including smoke,
and 2 CLI argument checks. The first full pass tested a bearer-character allowlist;
the user then clarified that token format validation was unnecessary. The final
pass qualifies the transport-only check and added arbitrary-punctuation fixture.
No other failures or reruns occurred. Final included source/test inputs are frozen.

Broad/focused Gradle, full Java, publication/consumer and separate host offline
build invocations: 0; production Java and build/publication behavior are unchanged.
Release artifact reproducibility is not rerun for this helper-only fix.
Human Nix invocations for this fix: 1, passed as reported by the user:
"nix flake check passed". No command log was supplied. The agent ran no Nix.
Follow-up inspection found the same four-path diff and unchanged tooling edits.
Only this excluded report was updated to record the result; no included input
was edited after the human gate, so no qualification rerun is required.

## Handoff

The report is excluded from qualification; intended source/test inputs must be
frozen before the human gate. They are now frozen; only this excluded report was
finalized afterward. Actual contents remain unstaged; the report has retained
intent-to-add membership. Final HEAD remains
`dc2c6e3cb25d94720343532a97691ae04fcc482d` on `main`.
`git diff --check` and `git diff --cached --quiet` pass. Recorded status:

```text
 A review/V0_3_0_GITHUB_TOKEN_FIX_REPORT.md
 M tooling/src/main/java/org/totipo/tooling/release/FinalizationProcesses.java
 M tooling/src/test/java/org/totipo/tooling/release/FakeFinalizationCli.java
 M tooling/src/test/java/org/totipo/tooling/release/FinalizationProcessTest.java
```

`git diff --name-only` lists exactly those four paths; `git diff --cached
--name-only` emits nothing. Nothing was committed, pushed or dispatched.

Review/commit and successful ordinary CI precede
any separately authorized fresh Release dispatch for 0.3.0 and its new exact SHA.
The current initial-phase failure precedes any reservation/upload in that run.

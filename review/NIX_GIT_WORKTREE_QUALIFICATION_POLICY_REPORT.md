# Git-aware Nix worktree qualification policy report

## Repository and starting state

Work is confined to `/home/niki/Sources/totipo-java`, identified by the Java
core/storage-nio layout and `origin` = `git@github.com:totipo-org/totipo-java.git`.
No sibling checkout was inspected. Starting branch: `main`; HEAD:
`c66cd805126ff5ab3ecd3ea6a61b08fd7fc84cc9`. Initial `git status --short` was
empty and `git diff --cached --quiet` passed. No starting untracked files.

Classifications: `BUILD_TOOLING`, `QUALIFICATION_POLICY`, and repository ladder
`DEPENDENCY_BUILD` for Nix invocation/configuration guidance. This is not dependency
migration, release preparation, product/API work or release semantic redesign.

Read AGENTS.md, README.md, flake.nix, qualification.nix, .envrc,
RELEASE_CHECKLIST.md, both CI/release workflows, workflow-pin tests and recent
qualification ladder, CI unification, Actions refresh and support-tooling reports.
There is no tracked package.nix or Makefile in this repository.

## Complete prior explicit path inventory

The inventory used repository-wide `rg --hidden` (excluding .git), covering
`path:`, `nix flake check`, `nix build`, `nix run`, `nix develop`, `nix eval` and
`nix flake metadata`; the table below records every starting-HEAD literal `path:`
match with original line numbers. Historical reports are C evidence/examples,
not active instructions to execute. Their original command/result records remain
unchanged. No B intentional raw-path flake source or D unknown occurrence exists.
The Nix lambda and Java diagnostics are lexical matches, not source references.

| File | Starting lines | Classification | Disposition / reason |
| --- | --- | --- | --- |
| `.github/workflows/ci.yml` | 29 | A: CURRENT_GIT_WORKTREE | Converted current-checkout commands. |
| `AGENTS.md` | 6, 12, 111, 120, 127 | A: CURRENT_GIT_WORKTREE | Converted current-checkout commands. |
| `README.md` | 81, 99 | A: CURRENT_GIT_WORKTREE | Converted current-checkout commands. |
| `RELEASE_CHECKLIST.md` | 77, 173 | A: CURRENT_GIT_WORKTREE | Converted current-checkout commands. |
| `core/src/test/java/org/totipo/conformance/SpecSnapshotIntegrityTest.java` | 72, 75, 78, 79 | Non-source lexical match | Java diagnostic strings containing `path:`; no Nix source reference. |
| `qualification.nix` | 52 | A: CURRENT_GIT_WORKTREE | Converted current-checkout commands. |
| `qualification.nix` | 20 | Non-source lexical match | Nix `path: type:` lambda; preserved syntax, not a flake source. |
| `review/JAVA_GITHUB_ACTIONS_REFRESH_REPORT.md` | 94, 188 | C: EXAMPLE_OR_TEST_FIXTURE (historical evidence) | Retained exact past commands/results; superseded for current use by this milestone. |
| `review/JAVA_NIX_CI_UNIFICATION_REPORT.md` | 4, 304, 342, 403, 411, 415, 436, 440 | C: EXAMPLE_OR_TEST_FIXTURE (historical evidence) | Retained exact past commands/results; superseded for current use by this milestone. |
| `review/JAVA_OBSERVATION_COMPOSITION_CLARIFICATION_REPORT.md` | 316 | C: EXAMPLE_OR_TEST_FIXTURE (historical evidence) | Retained exact past commands/results; superseded for current use by this milestone. |
| `review/JAVA_OPERATION_MODEL_DOCUMENTATION_REPORT.md` | 224 | C: EXAMPLE_OR_TEST_FIXTURE (historical evidence) | Retained exact past commands/results; superseded for current use by this milestone. |
| `review/JAVA_QUALIFICATION_LADDER_REPORT.md` | 30, 95, 122 | C: EXAMPLE_OR_TEST_FIXTURE (historical evidence) | Retained exact past commands/results; superseded for current use by this milestone. |
| `review/JAVA_RELEASE_VERSION_SCAN_FIX_REPORT.md` | 185 | C: EXAMPLE_OR_TEST_FIXTURE (historical evidence) | Retained exact past commands/results; superseded for current use by this milestone. |
| `review/JAVA_SUBSCRIBER_THREADING_DOC_FIX_REPORT.md` | 141 | C: EXAMPLE_OR_TEST_FIXTURE (historical evidence) | Retained exact past commands/results; superseded for current use by this milestone. |
| `review/JAVA_SUPPORT_TOOLING_AUDIT.md` | 101, 103 | C: EXAMPLE_OR_TEST_FIXTURE (historical evidence) | Retained exact past commands/results; superseded for current use by this milestone. |
| `review/JAVA_SUPPORT_TOOLING_WAVE1_REPORT.md` | 37, 137 | C: EXAMPLE_OR_TEST_FIXTURE (historical evidence) | Retained exact past commands/results; superseded for current use by this milestone. |
| `review/JAVA_SUPPORT_TOOLING_WAVE2_REPORT.md` | 315 | C: EXAMPLE_OR_TEST_FIXTURE (historical evidence) | Retained exact past commands/results; superseded for current use by this milestone. |

New `path:` mentions in AGENTS/README and this report explain the intentional
raw/non-Git exception or record prior command forms; they are policy/examples,
not executable current-checkout commands. No custom source-copy/filter mechanism
was added and the existing qualification filter was preserved exactly.

## Current commands and authoritative policy

Eleven active occurrences changed across five files:

- AGENTS.md: three `nix flake check path:.` forms become `nix flake check`;
  two `nix run path:.#update-package-deps` forms become `nix run .#update-package-deps`.
- README.md: the same qualification and dependency-update command corrections.
- RELEASE_CHECKLIST.md: two routine human gate forms become `nix flake check`.
- CI: `nix flake check --print-build-logs path:.` becomes
  `nix flake check --print-build-logs`, preserving the one invocation and log flag.
- qualification.nix: its ungenerated-cache diagnostic recommends
  `nix run .#update-package-deps`; the same refusal condition and exit remain.

AGENTS.md adds authoritative Git-aware local source membership policy. README and
the checklist carry the contributor-facing rule/link. Current unstaged tracked
bytes participate in qualification. For each new intended milestone/commit file,
`git add --intent-to-add <file>` establishes source membership only. Actual staging
requires explicit user authorization. The invariant is no staged file content,
verified with `git diff --cached --quiet`, rather than a literally empty index.
Intent-to-add entries remain for human review/commit. Ignored/untracked caches,
build outputs, temporary evidence and secrets must remain outside source membership.
Explicit raw/non-Git filesystem sources remain allowed when intentional.

`.envrc` already contains `use flake` and is unchanged. flake.nix exposes only the
existing update-package-deps app for this purpose; its implementation, arguments,
outputs and cache behavior are unchanged. The protected release workflow contains
no explicit local Nix source command and is byte-for-byte unchanged.

CI preceding steps are checkout, credential-free workflow-pin unittest discovery
with `-B`, and Nix installation/configuration. None creates an untracked project
source required by the flake. No actions, dependency, network behavior, permissions,
checkout options or secret access changed. Remote CI for this uncommitted diff
was not run; no push or workflow dispatch occurred.

## Direct validation and accounting

Passed:

- `git diff --check`.
- Existing `python3 -B -m unittest discover -s publishing -p 'test_release.py'
  -k WorkflowPinTests`: one pre-edit baseline and one post-edit invocation;
  two tests passed each time, no skips. This is the established workflow
  action-pin/security static guard. actionlint is not installed and was not added.
- Read-only static assertions: Nix and CI diffs are exactly the intended command
  substitutions; the five-file implementation allowlist is exact; changed
  Markdown links/fences and the new policy anchor are valid; .envrc is unchanged;
  no Git-known build/cache directories are present.

No standalone shell script changed. The Nix diagnostic remains the same shell
quoted string with only the literal recommendation corrected. No tests asserting
old Nix command strings were found. No release app/controller code changed.
No failed validation attempts or invalidating edits occurred.

Invocation counts: broad Java baseline 0; focused Gradle tests 0; final full Java
gate 0; publication/module-consumer/POM-consumer passes 0 each; focused Python
baseline 1 and final focused Python 1; full Python guardrails/accounting 0;
separate host forced-offline suite 0; release reproducibility/actions 0;
agent Nix 0; human Nix 1 (PASS, human-reported below).

The affected checks are command/policy/static checks. No Java/publication/build
behavior, resolution or dependency input changed; the Nix file delta is diagnostic
wording only. The repository ladder requires affected checks and final human Nix,
not unrelated Java/artifact reruns or cache regeneration for this scope.

## Source membership, freeze and human qualification

The only new intended milestone file is
`review/NIX_GIT_WORKTREE_QUALIFICATION_POLICY_REPORT.md`.
It is intent-added before the human gate; no content is staged. No caches,
generated output, temporary evidence or local secrets were added.

flake.nix calls qualification.nix and separately checks the wrapper using its
tracked Python checker and wrapper JAR/properties. qualification.nix selects
core/src, storage-nio/src, gradle, publishing, nix and its explicit source files;
its filter excludes AGENTS.md, README.md, RELEASE_CHECKLIST.md, .github and review.
No relevant check consumes this report; release-note readers name specific
versioned release-note files, not this report. Thus only this excluded report may
be finalized after human PASS. Every other Git-known file is frozen with a SHA-256
inventory outside the checkout for post-Nix comparison. No inventory/filtering
mechanism is used to supply Nix source.

Freeze inventory: 338 Git-known paths, excluding only this report, at
`/tmp/totipo-java-nix-git-freeze-1ywh3dqs/frozen-inputs.json`.
Inventory SHA-256:
`bbf984b8418b0814a3ded28108cea87c3f11ae92f3bbfa5d087555b876f9f1f2`.

Human gate: **PASS**, reported by the human on 2026-10-11 as “flake check passed”
in response to the requested command from this repository root:

```sh
nix flake check
```

This is human-reported evidence; the agent did not run Nix or independently
inspect its build log. After the report of PASS, all 338 frozen Git-known paths,
their modes and source membership matched the pre-gate inventory. Only this
confirmed excluded report was finalized; no included effective input changed.

## Preserved behavior and Git state

Java production/tests/APIs, protocol/spec/vector bytes, Java 17 bytecode policy,
r19 integrity, publication manifests/artifact layout, isolated independent
consumers, release controller/refusal/signing/tagging behavior, all dependency
versions, Gradle locks/verification, package-deps.json, flake.lock, toolchain and
dev-shell contents are unchanged. The existing qualification task coverage is
unchanged. No release semantic redesign or custom source filtering was introduced.

HEAD and branch remain the starting identity; all changes remain uncommitted.
Final post-human-PASS state (rechecked after report finalization):

```text
 M .github/workflows/ci.yml
 M AGENTS.md
 M README.md
 M RELEASE_CHECKLIST.md
 M qualification.nix
 A review/NIX_GIT_WORKTREE_QUALIFICATION_POLICY_REPORT.md
```

`git diff --name-only` lists those six paths. `git diff --cached --name-only`
is empty and `git diff --cached --quiet` passes. The report's ` A` is intent-to-add,
not staged content. No normal git add, commit, reset, push or tag was performed.

Suggested commit subject: `Adopt Git-aware Nix worktree qualification`.

Final verification: `git diff --check` and `git diff --cached --quiet` passed;
`git diff --cached --name-only` remained empty. Intent-to-add was preserved.
The milestone is complete, with no post-Nix included-input edits.

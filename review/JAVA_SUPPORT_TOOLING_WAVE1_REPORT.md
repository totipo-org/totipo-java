# Java support-tooling pruning Wave 1

## Starting state and classification

Date: 2026-10-11. Started on clean `main` at
`b3bdfa45b209f55b7a7ad0140808eb83de281cb8`; `git status --short` was empty.
VERSION was and remains `0.2.0` with a final newline. SPEC_PIN.md remains
v1/r19 at upstream `cdb4e91be1c6d3704874b2b92457ffe7be5e9084`, profile
SHA-256 `7242fc557a7ca56452c934bedc5e7dc2835248f5cd36fa220f05588dc967ec80`,
92 cases / 99 upstream files. No starting-state exception was necessary.

Committed prerequisites: audit in starting HEAD (`b3bdfa4`), Actions guard
`27f47cb`, release-version ownership fix `8b24b8a`. Read AGENTS.md, README.md,
RELEASE_CHECKLIST.md, the audit, release.py and test_prepare.py before editing.

Classification: **RELEASE_TOOLING / support-tool cleanup**. No Java runtime/API,
artifact construction, publication metadata, consumer or release transaction change.
Publishing Python is a qualification input, so final human Nix remains required.

## Bootstrap caller proof and deletion

Before deletion, repository-wide search:

```sh
rg -n --hidden -g '!.git/**' -g '!**/build/**' -g '!**/.gradle/**' 'bootstrap-m0|M0 bootstrap' .
```

Matches were only the script's own usage/completion text, the committed tooling
audit and JAVA_NIX_CI_UNIFICATION_REPORT.md. No workflow, Gradle/Nix helper,
Python, README, checklist or AGENTS caller existed. Deleted `bootstrap-m0.sh`;
no replacement bootstrap was created. The post-deletion search finds only review
reports, including this report. Historical reports were not edited.

README now documents the existing pinned wrapper task (two wrapper invocations),
nonexecuting wrapper identity verifier, and existing module dependency command
with `--write-locks --write-verification-metadata sha256`. It retains the existing
human `nix run path:.#update-package-deps` checkpoint and exhaustive cache review,
and connects intentional dependency maintenance to that checkpoint. These commands
were documented, not executed. No maintenance inputs were regenerated.

## Legacy preparation branch removed

Removed `status_block()`'s `legacy` argument, the missing-marker fallback guarded
by `old == "0.1.5"`, and its `replace_once(text, legacy, after, path)` conversion.
Removed the three exact pre-marker paragraph literals in preparation_plan() for
README, API_DESIGN and RELEASE_CHECKLIST. The latter now calls status_block()
without conversion inputs. Updated its ownership docstring accordingly.

Current marked status logic remains: exactly one start/end marker, exact current
prepared or released status, same-version released checks preserve released text,
and future preparation produces prepared status. Missing markers now always fail.
No global version-string scan was introduced. Checklist prose now states that
current source requires the owned markers.

## Tests and preserved contract

Replaced the unmarked 0.1.5 fixture/setup with marked 0.2.0 source and reviewed
0.2.0 notes; preparation tests advance to 0.2.1/0.2.2. Removed legacy paragraphs
and the old transition-only notes fixture. Released-source fixtures start directly
from marked source. All 20 durable test methods remain; no replacement test for
the deleted conversion was added. Remaining 0.1.5 strings are historical prose or
negative stale/conflicting metadata examples, not conversion fixtures.

Retained coverage includes exact changed paths and lock configurations, historical
notes/examples unchanged, marker presence/uniqueness, stale/conflicting owned slots,
explanatory version prose outside owned slots, prepared/released states, dry-run
and repeated/detached write-free checks, dirty/wrong-branch rejection, canonical
version advance, version-specific notes and templates, failure before writes,
real spec-pin/snapshot integrity and corruption. Network/publication/process
tripwires in preparation tests remain.

## Validation evidence and invocation accounting

| Invocation category | Count | Result / disposition |
| --- | ---: | --- |
| Broad Java baseline | 0 | Helper-only classification; not required |
| Focused Gradle tests | 0 | No Java/build behavior change |
| Focused Python preparation suite | 3 | Baseline 20/20; first post-edit 19 pass / 1 error; corrected rerun 20/20 |
| Current-version preparation CLI check | 1 | Passed for 0.2.0; real r19 integrity; existing tracked-file SHA-256 map unchanged |
| Final full publishing Python suite | 1 | 43/43 passed |
| Final full Java gate | 0 | AGENTS helper-only disposition applies |
| Publication/consumer passes | 0 | Their behavior and construction inputs unchanged |
| Separate host forced-offline passes | 0 | No dependency/build/resolution change justifies one |
| Release ten-artifact reproducibility | 0 | No explicit release preparation performed |
| Agent Nix invocations | 0 | Prohibited |
| Human Nix runs reported | 1 | PASS reported by the human after freeze |

Focused command, before edits and both post-edit attempts:

```sh
python3 -B -m unittest discover -s publishing -p 'test_prepare.py'
```

The post-edit error was confined to a CLI dry-run test still targeting 0.2.0 after
the fixture moved to 0.2.0. Changed that test target to 0.2.1 and reran successfully.
No pre-existing failure occurred. No broad/focused Java outputs were overwritten.

Current-version command:

```sh
python3 -B publishing/release.py prepare 0.2.0 --check
```

Output: `Prepared source OK: 0.2.0; exact r19 integrity valid (qualification is separate)`.
An enclosing standard-library check compared SHA-256 maps of all existing tracked
files before/after and found no changes. VERSION was not changed; no new version
was prepared in the repository. Future preparations occurred only in temporary
unit-test fixtures.

Final stable Python command:

```sh
python3 -B -m unittest discover -s publishing -p 'test_*.py'
```

Passed 43 tests. Python 3.14 emitted ResourceWarnings for mocked HTTPError objects
(401/429/500/503); there were zero test failures/errors. No repeat full run was made.

## Static validation, freeze and human Nix

`git diff --check` passes. Reference search finds no live bootstrap reference.
Search in release.py/test_prepare.py confirms legacy conversion identifiers and
paragraphs are gone. Current status_block/preparation_plan/prepare remain.
Changed-path review confirms no protected workflow, VERSION, SPEC_PIN, Java,
Gradle, dependency, verification metadata, cache, lock or Nix-definition change.

Inspected both qualification.nix's source filter and flake.nix inputs/checks.
`publishing/` is included; README.md, RELEASE_CHECKLIST.md, bootstrap-m0.sh and
`review/` are outside the selected derivation source. Flake lock/cache and wrapper
check inputs remain unchanged. Executable qualification inputs are frozen at
handoff. Only this excluded report may be updated to record a human result.

Human qualification status: **PASS reported by the human** after the frozen-input
handoff, for the requested command:

```sh
nix flake check path:.
```

The agent did not run Nix or independently inspect its logs. After the human
reported PASS, only this excluded report was edited; no executable qualification
input was changed by the agent.

Any subsequent included-input edit invalidates the qualification result. No second
Nix build, dependency cache update or release action is requested by this wave.

## Changed files and final Git state

- README.md: supported modern maintenance documentation.
- RELEASE_CHECKLIST.md: remove obsolete conversion statement; require markers.
- bootstrap-m0.sh: deleted.
- publishing/release.py: remove obsolete conversion only.
- publishing/test_prepare.py: marked current fixture and retained regression coverage.
- review/JAVA_SUPPORT_TOOLING_WAVE1_REPORT.md: this new report.

Final branch/HEAD remain `main` / `b3bdfa45b209f55b7a7ad0140808eb83de281cb8`.
Five tracked paths are unstaged (four modified, one deleted), and this report is
untracked. The index is empty of changes. No stage, commit, push, tag, signing,
publishing, workflow dispatch, manifest consolidation or Java migration occurred.

Suggested commit subject: `Remove obsolete Java bootstrap and preparation transition`.

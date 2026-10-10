# Release version scan ownership correction

## 1. Starting HEAD and state

Started on clean `main` at `b03f5b22f367723ce4a3bddf0a56b159a06f32cf`.
`VERSION` was `0.2.0`; `SPEC_PIN.md` identified v1/r19 at upstream commit
`cdb4e91be1c6d3704874b2b92457ffe7be5e9084`.
Prerequisites were committed: `ce00f0c` (subscriber callback threading) and
`b03f5b2` (qualification ladder). Read AGENTS.md before selecting validation.

## 2. Milestone classification

`RELEASE_TOOLING`. No release preparation, Java/API, dependency or build change.
The user explicitly required the final normal Java gate for this correction.

## 3. Original false positive

Before editing, imported the current `publishing/release.py`, read VERSION, and
called `release.preparation_plan(current, current)`. It failed with:

```text
RuntimeError: Ambiguous old-version occurrence in API_DESIGN.md: This is the application scheduling model for the existing 0.2.0 API. The detailed
```

This is an explanatory implementation-version mention in the operation scheduling
section (original line 115), outside the prepared-release block. The final loop
in preparation_plan removed that block, searched each remaining line for the
current version substring, then required one of a few recognized phrases.
The scheduling sentence matched none, so even same-version validation failed.
The prose was not changed to accommodate the scanner.

## 4. Version-scan ownership model

Preparation already had a bounded set of file edits, exact replacements, marked
status blocks, a bounded legacy 0.1.5 migration and version-specific notes.
Refined those contracts into explicit version-slot templates. Validation checks
the slot's identity, cardinality and expected current value, rather than every
version-looking string in the surrounding document.

## 5. Release-owned locations

| Location | Ownership |
| --- | --- |
| VERSION | Exact current value and one final newline; target value in plan |
| RELEASE_CHECKLIST.md | Title, two Totipo Maven coordinates, annotated source tag convention, reviewed-notes path, workflow `version` input, prepared-release block |
| .github/workflows/release.yml | Exact VERSION input example |
| publishing/consumer-smoke/gradle.lockfile | Two Totipo self-lock declarations with their exact existing configuration contract |
| README.md and API_DESIGN.md | Prepared-release block only, or exact one-time legacy paragraph |
| review/version-specific release notes | Existing prepare/check selection, heading, section and review requirements; missing target notes get the existing skeleton during actual preparation |

Publication configuration reads VERSION: root/subproject versions, coordinates
and the independent consumer use that source. Publication verification separately
checks staged POM/module coordinates and versions. Those contracts are unchanged.

## 6. Explanatory and historical locations

README published examples, README/API compatibility discussion, API_DESIGN's
existing-0.2.0 scheduling context, historical references in checklist prose and
review/history reports are outside the declared slots. A previous version in
those locations is not automatically stale release metadata. Such prose may
remain historical after a release or receive a separate editorial update.
The checklist documents this policy. No generic prose-ignore regex was added.

## 7. Implementation

`owned_version` validates exactly one occurrence of each declared slot, including
conflicting values, then uses the existing exact replacement guard. The tag slot
includes its explicit convention label, preserving historical `v0.1.0` examples.
Status-block validation requires exactly one start and end marker and the exact
expected status body. preparation_plan now also verifies VERSION against its
current argument. Removed document-wide old-version substring rejection and the
README/API phrase allowlist. Only the declared locations enter the plan.

## 8. Fail-closed behavior

Missing/stale owned values still fail; extra same-version or different-version
owned declarations fail. Duplicate, partial, mismatched or stale status blocks
fail. Consumer locks retain their exact configuration validation and reject
additional Totipo entries even with different configurations. Notes validation,
source-integrity checks, branch/clean-tree mutation requirements, credentials and
remote-release guards are unchanged. Existing arbitrary-prose rejection fixtures
were changed to duplicate owned slots to test the intended ownership boundary.

## 9. Same-version behavior

The actual repository's `prepare 0.2.0 --check` succeeds. A direct current/current
plan and check also succeeded with Path.write_text patched to raise; every plan
value equaled its existing source. Released status remains released. No source
preparation writes occurred in the working repository.

## 10. Synthetic future behavior

Temporary fixtures model released 0.2.0 source and a 0.2.1 plan. The plan contains
only VERSION, checklist, workflow example, consumer lock, README and API_DESIGN.
README/API change only their status blocks. The scheduling paragraph and added
historical examples remain byte-for-byte unchanged. Title/tag/notes selection,
workflow example and locks advance as expected. Existing tests also verify notes
skeleton generation and history preservation inside temporary fixtures only.
No future-version preparation was performed on this repository.

## 11. Focused tests

`python3 -B -m unittest discover -s publishing -p 'test_prepare.py'`:
baseline 17 tests passed; subsequent focused runs passed 20 tests each.
New coverage includes realistic scheduling prose, same-version write prevention,
historical references, future owned-only plans, stale VERSION/status/coordinates/
notes/input/locks, duplicate markers and conflicting declarations. Tests use
deterministic temporary files and existing remote/process/Git tripwires.
Root Markdown is excluded from Nix qualification, so the fixture embeds the actual
scheduling paragraph instead of reading excluded files. This fixture refinement
occurred while the Java command was running, before final Python guardrails;
no Java source, test or build input changed and no Java rerun was needed.

## 12. Full release guardrails

Full discovery passed before the Java gate and again as part of its final sequence:
41 tests, zero failures/errors. Python emitted ResourceWarnings for mocked
HTTPError cleanup; these did not fail tests or involve network requests.
Two standalone CLI same-version checks passed, followed by the direct write-free
plan/check verification described above.

## 13. Invariance

The only tracked edits are publishing/release.py, publishing/test_prepare.py and
RELEASE_CHECKLIST.md, plus this new report. Java production/test sources and APIs,
API_DESIGN wording, README, VERSION (0.2.0), SPEC_PIN (v1/r19), dependencies,
Gradle locks, verification metadata, package-deps.json, flake.lock, Nix definitions
and the protected release workflow remain unchanged. Java 17 bytecode and
publication checks passed. Exact r19 source integrity passed in prepare --check.

## 14. Qualification-ladder invocation counts

| Invocation category | Count and result |
| --- | --- |
| Broad baseline Gradle test | 0; release-helper milestone used focused Python baseline |
| Focused Gradle tests | 0 |
| Focused Python suites | 4 total: baseline 17/17, three implementation/fixture runs 20/20 |
| Full Python guardrails | 2 total, both 41/41; pre-Java stable boundary and required final sequence |
| Final full Java command | 1, passed |
| Full-suite release.py tests accounting | 1, passed immediately after Java command |
| Publication passes | 1 verifyPublication task inside final Java command; 1 standalone Python verifier |
| Consumers | 1 consumerSmoke task inside final Java command; 1 independent POM-only command |
| Separate host forced-offline full suite | 0; no dependency/acquisition/resolution changes |
| Same-version repository checks | 2 standalone CLI checks plus 1 direct plan/check with writes forbidden, all passed |
| Original false-positive reproduction | 1 direct current/current invocation, expected failure before edits |
| Human Nix | 1, passed as reported by the human |
| Release-only artifact reproducibility | 0; not a release-preparation milestone |

No failed qualification commands or qualification reruns. Task executions are not
extra command invocations: the Java command reported 27 actionable tasks (9
executed, 18 up-to-date); storage-nio tests and Javadocs were up-to-date, core tests
executed. consumerSmoke depends on verifyPublication; nested isolated consumer
tasks executed once and are included in that single consumer pass. Both consumer
passes ran five actionable tasks. The normal wrapper acquired Gradle 9.8.0; no
dependency/cache/lock inputs were updated.

## 15. Final Java qualification

All required commands passed:

```sh
./gradlew test build javadoc verifyPublication consumerSmoke
python3 -B publishing/release.py tests
python3 -B -m unittest discover -s publishing -p 'test_*.py'
python3 -B publishing/verify-publication.py
./gradlew -p publishing/consumer-smoke --offline -PpomOnly clean check
```

Accounting: 402 JUnit tests, zero failures/errors/skips; 92/92 portable cases;
snapshot 3/3 and profile 1/1. Local unsigned staging passed POM/module/content/
bytecode/inventory verification. Both independent consumer modes passed using
local staging. No release-only normal/offline ten-artifact comparison ran.

Evidence preserved outside build outputs in `/tmp/totipo-version-scan-evidence/`
(full-suite XML, accounting and publication inventory). Logs are
`/tmp/totipo-version-scan-final-java.log`, `-test-accounting.log`,
`-final-python.log`, `-final-publication.log`, and `-final-pom.log` with the same
`/tmp/totipo-version-scan` prefix.

## 16. Human Nix result and freeze

The human reported successful execution of exactly:

```sh
nix flake check path:.
```

Human result: passed. The agent did not run Nix. Frozen-file hashes were checked
after the reported pass and still match the values below.
Inspected qualification.nix's source filter and flake.nix's inputs: publishing
sources/tests are included; RELEASE_CHECKLIST.md and review/ are excluded.
No included-input edits are planned after this freeze. Only the excluded report
may be updated to record the human result.

Frozen changed files (SHA-256):

```text
06dc32cc72b865f6a8686a48f1ea7fc05536a32e476f2b8e8f35bad66af710b5  publishing/release.py
7003474853b24de8c16c227a76ce05af19b6b3b95139c1d6c7d48ab8c85916bc  publishing/test_prepare.py
6b05464c21600938de2e7081347619c3a08ac8617e71ab537a096e15f0e309f6  RELEASE_CHECKLIST.md
```

## 17. Release disposition

No release prepared or published. No signing, credentials, Central upload,
release environment, tag, commit, staging, push, CI dispatch or remote mutation.
VERSION remains 0.2.0. Synthetic mutating preparation occurs only in temp tests.

## 18. Final Git state

HEAD remains `b03f5b22f367723ce4a3bddf0a56b159a06f32cf` on main; index unchanged.
All changes are unstaged/uncommitted; git diff --check passes.

```text
 M RELEASE_CHECKLIST.md
 M publishing/release.py
 M publishing/test_prepare.py
?? review/JAVA_RELEASE_VERSION_SCAN_FIX_REPORT.md
```

Implementation, host qualification and human Nix qualification are complete.
The only edit after the reported Nix pass records that result in this excluded
report; no included input changed and no qualification rerun is required.

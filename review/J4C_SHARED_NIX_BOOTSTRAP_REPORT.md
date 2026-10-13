# J4C — Shared Nix bootstrap

**Correction host qualification PASS; renewed human Nix gate PASS (owner reported); release mutations DISABLED.**
Section 15 records the correction and supersedes the initial source freeze/Nix result.
Sections 1–14 below retain the initial J4C history, including its owner-reported PASS.
All changes remain uncommitted. No Nix command, release operation, settings change,
commit, tag, push or workflow dispatch was performed by the agent. Evidence and
host-only validation helpers are outside source membership in
`/tmp/totipo-j4c-evidence/`. This is structural simplification, not release activation
or release preparation. Stop after J4C.

## 1. Starting checkout and committed authority

- Branch: `main`.
- Full HEAD: `2597be7838da5f1eeab94b1e5ed9d1b07a7ed300`.
- Initial `git status --short`: empty, including nonignored untracked files.
- That HEAD committed J4B (`simplify ci and release`); `git log -1 --
  review/J4B_SIMPLIFIED_RELEASE_CUTOVER_REPORT.md` identifies that same commit.
- Read AGENTS.md completely, J4B (including sequencing, bootstrap, tooling,
  permissions, activation and source-freeze sections), and the relevant J4S design.
  Historical reports' uncommitted statements describe their own handoffs.
- Classifications: RELEASE_TOOLING; DEPENDENCY_BUILD (Nix tooling qualification
  source wiring only); DOCS_JAVADOC (excluded report prose). No runtime/API/library,
  dependency acquisition, publication behavior or release-preparation change.
- Work confined to this checkout and disposable fixtures exported from its files.
  No sibling repository, other worktree or sub-agent was used.
- Owner reports repository protections reviewed/configured. No settings were
  changed or queried; this report does not claim an authenticated effective-settings audit.

VERSION remains `0.2.0` plus LF. Specification remains v1/r19 at
`cdb4e91be1c6d3704874b2b92457ffe7be5e9084`; requirements profile SHA-256
`7242fc557a7ca56452c934bedc5e7dc2835248f5cd36fa220f05588dc967ec80`.
Existing workflows were exactly `ci.yml`, `release.yml`, `verify-release.yml`.

## 2. Original bootstrap inventory and extraction

Five identical bootstrap blocks existed across three workflows: CI/build,
Release/preflight, Release/publish-central, Verify Release/verify and
Verify Release/finalize. The host extraction helper asserted all five blocks were
byte-identical before edits. Each contained:

1. Reject PATH-visible Nix and the default installation binary.
2. Download the versioned official installer with HTTPS-only curl and a 60-second bound.
3. Verify its SHA-256 before executing anything from it.
4. Invoke the full-SHA-pinned installer action with the verified local file URL,
   KVM disabled, accept-flake-config enabled and empty access-tokens configuration.
5. Require the exact Nix implementation/version string.
6. Remove all access-tokens declarations from daemon and optional user configuration.
7. Restart the daemon; scrub token/config/Gradle property environment variables.
8. Require empty effective access-tokens before any flake evaluation/build.

Checkout, exact-source authentication, locked flake builds, standalone runtime
selection, wrapper checks, Java qualification, release eligibility, signing, tag
reservation, publication, remote verification and finalization are job-specific.
They remain outside the shared action.

## 3. Shared action and preserved pins

The only added executable file is `.github/actions/setup-nix/action.yml` (38 lines,
mode 100644). It has the requested name/description, `runs.using: composite`, and
three ordered steps: acquire/checksum, install, validate identity/clean configuration.
Both run steps specify `shell: bash`, which uses GitHub's fail-fast Bash/pipefail
execution. No inputs, supporting script, framework, cache, fallback, credential
requirement or job-specific flake command was added.

| Identity | Preserved value |
| --- | --- |
| cachix/install-nix-action | `13d8dd58da0234aa297dedd986986ccb8e7f3e24` / v31.11.1 |
| Installed identity | `nix (Nix) 2.35.2` |
| Official installer | `https://releases.nixos.org/nix/nix-2.35.2/install` |
| Installer SHA-256 | `9adda97297d9e8ab360df95c729eabff4f4f93d6db091953c3a68f29e3fb130c` |
| Embedded x86_64-linux tarball SHA-256 | `0c3960a9792331a22081c3c7a5d8465db9b17c50b3acdf18587fa4c6f2cb1158` |
| actions/checkout | `3d3c42e5aac5ba805825da76410c181273ba90b1` / v7.0.1 |

Verified against committed J4B source/report and fresh inert downloads of the
[official installer](https://releases.nixos.org/nix/nix-2.35.2/install),
[pinned upstream action](https://raw.githubusercontent.com/cachix/install-nix-action/13d8dd58da0234aa297dedd986986ccb8e7f3e24/action.yml)
and [pinned installation script](https://raw.githubusercontent.com/cachix/install-nix-action/13d8dd58da0234aa297dedd986986ccb8e7f3e24/install-nix.sh).
Upstream metadata SHA-256 remains
`46d90d7cd0453aefd350f837a57f669199227668564b10ad58a05bbf862a3bf0`;
script SHA-256 remains
`836671507d9b4ea84252f968bd0622c5c2fa72f5deea93f5764f1c60821062f6`.
The verified installer embeds the recorded tarball hash. No installer/tarball was executed.

## 4. Workflow call sites, provenance and older sources

All five job call sites now use exactly:

```yaml
- name: Setup pinned Nix
  uses: $/.github/actions/setup-nix
```

Each is followed by its existing source-specific runtime build in a separate run
step. The extraction comparison proves all surrounding workflow bytes unchanged;
the action differs from the extracted commands only by explicit Bash shells and
the fail-closed corrections described below.
Before: inline acquire → install → cleanup/validate/build. After: shared action
(acquire → install → cleanup/validate) → job-specific build.

The current [official GitHub workflow syntax documentation](https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-syntax#example-using-an-action-in-the-same-repository-as-the-workflow-at-the-running-commit-recommended)
confirms that `$/` binds to the repository and running commit of the containing
workflow/action, independently of workspace contents, and takes no `@ref` suffix.
This is supported on GitHub.com, the target environment; GitHub Enterprise Server
is explicitly excluded by the documentation. The
[GitHub announcement](https://github.blog/changelog/2026-07-30-reference-same-repository-actions-with-self-repository-syntax/)
corroborates availability.

Thus an older release checkout cannot substitute its own action implementation.
Verify Release installs the bootstrap from its running workflow commit, then uses
the requested authenticated old source's flake.lock and standalone tooling. Its
source-specific workflow guardrail command still inspects that checked-out source;
older source uses its own earlier guardrail implementation. Nothing copies newer
workflow/tooling bytes into the release source or changes the intended commit.
The reusable-workflow reference remains exactly
`./.github/workflows/verify-release.yml`; it is distinct from the self-repository action reference.

## 5. Token/configuration and credential boundary

The pinned upstream action still internally supplies `github.token` to its installer.
The extraction preserves the explicit response: delete daemon/user token declarations,
restart nix-daemon.service, unset GH_TOKEN/GITHUB_TOKEN/NIX_CONFIG/NIX_USER_CONF_FILES
and all ORG_GRADLE_PROJECT_* variables, then capture the successful
`nix config show access-tokens` query and require `test -z "$effective_tokens"`.
Exact installed identity validation
precedes cleanup. Failure stops the composite and its caller before runtime builds.

Unsets in a composite run step do not persist into the caller's next shell.
Therefore each source-specific runtime build retains its original environment
scrubbing immediately before `nix build`. This preserves the credential-free build
boundary across the new step separation. The action has no outputs or environment
writes that propagate credentials. Shared cleanup/effective validation is complete
before release secrets are introduced in the existing protected publishing step.

Permissions remain identical: defaults read; preflight read; publisher write with
release environment; reusable-call write ceiling; verifier read; finalizer write
without release environment. No permission increase, `secrets: inherit`, personal
access token, GitHub App credential, secret scope change or new credential propagation.
All signing/Central secrets remain in the original publishing runtime step. No
release secret enters Nix derivations, store, command arguments or configuration.
The existing verification-only isolation checks remain permanent and passing.

Final shell review exposed two inherited failure-propagation holes. Bash exempts
`! command -v nix` from errexit, so an unexpected PATH installation could continue.
Also, `test -z "$(nix config show access-tokens)"` could accept empty output from a
failed query. The action now uses `! command -v nix || exit 1`, captures each Nix
query in a separate assignment, and then checks the version/token value. Assignment
failure propagates before output is accepted. Installer/action pins, download,
checksum, cleanup and restart commands are unchanged. These minimal strengthening
deviations are necessary to satisfy the requested rejection/fail-closed contract.
No baseline bootstrap difference between jobs was concealed.

## 6. Release behavior and disabled activation

The direct old/new workflow comparison proves inputs, source/version eligibility,
initial dispatch-SHA equality, reachability, first-time tag reservation, signing
checks, the one-authorized-Gradle-invocation policy, Central task, remote byte checks,
consumer modes, verification retry behavior and create-or-validate finalization
unchanged. No route back to publishing was added.

Both exact source guards survive, and negative fixtures reject their activation:

- release.yml/publish-central: `if: ${{ false && github.run_attempt == 1 }}`.
- verify-release.yml/finalize: `if: ${{ false }}`.

No input, variable or action parameter bypasses them. No live signing, upload,
Central publication, tag creation or GitHub Release operation was performed.

## 7. Git-aware qualification/source selection

Reviewed qualification.nix, flake.nix, nix/tooling.nix and nix/wrapper-verifier.nix.
The production qualification source filter is unchanged, including its deliberate
`.github` and historical-report exclusions. The existing tooling derivation already
receives read-only workflow fixtures; its narrow fileset now additionally includes
exactly `.github/actions/setup-nix/action.yml` and `nix/tooling.nix`. The latter lets
the permanent source-inclusion test inspect the actual fileset declaration. No whole
hidden tree or historical reports were added. Wrapper input selection is unchanged.

The tooling check now reads actual action bytes; editing the action changes its
fixture source and invalidates tooling qualification. Both new milestone files
(action/report) are Git-known via intent-to-add. `git diff --cached --quiet` passes;
no content is staged. Generated outputs, dependencies, caches, temporary evidence
and secrets remain outside Git source membership.

A host fixture export selected only Git-known paths in the actual tooling fixture
union. ReleaseTest, FinalizationTest, WorkflowPinsTest and WorkflowBoundaryTest all
pass against that narrow export, which includes the action and excludes review/.
This is static/projection evidence; evaluating the actual Nix fileset remains part
of the human Nix gate, not a claimed host Nix execution.

## 8. Permanent guardrails and negative fixtures

WorkflowPins securely reads the actual action with a 64-KiB bound and no symlink
following. It requires its reviewed full-byte SHA-256, then also applies the existing
external-action pin validator inside the composite. The byte contract binds YAML
structure, shell options, command order, checks and absence of added inputs/secrets/
commands; merely retaining required strings while skipping them cannot pass.
Future action edits, including harmless formatting, require review and deliberate
renewal of that digest. This strict contract avoids a new YAML parser or workflow
simulation framework while protecting the complete small implementation.

The three expected workflow files must contain exactly 1/2/2 canonical self-reference
call sites, each immediately followed by the existing credential-scrubbed locked
runtime build. Unexpected workflow files, other local references, mutable refs,
duplicate installer actions and inline bootstrap markers fail closed. Original
external-action reference injection and pin-bypass negatives remain.

New fixtures mutate the actual action: unpinned installer; checksum; official URL;
PATH and default-install rejection; Nix implementation identity; effective empty-token
assertion; daemon and user cleanup; daemon restart; checksum verification command;
composite structure; fail-fast shell; conditional skip; appended unpinned action;
missing action. Each is rejected. All three workflows are separately mutated to
workspace-relative/other action references, conditional skips, removed environment
scrubbing and alternate inline installation. Each is rejected.

WorkflowBoundaryTest retains disabled guards, source/permission/secret isolation,
one-invocation sequencing and shell input-injection cases. It additionally checks
setup order per release job and the narrow source union, with negative fixtures
removing the action or fileset-definition input. Existing negative cases still reject
publisher/finalizer activation and verification acquiring publishing capabilities.
It also executes the actual acquisition/cleanup shell bodies with Bash function
fakes, accepting successful empty tokens and rejecting PATH-visible Nix, failed
version queries (even with the expected stdout), failed empty-output token queries,
and nonempty tokens. No real Nix/downloader/sudo/daemon is invoked in these fixtures.

## 9. Host qualification and invocation accounting

All required host checks passed on the frozen implementation. Independent tooling
uses existing JDK 25.0.4.1+1 directly (no Nix invocation), explicit sourcepaths,
`--release 25 -encoding UTF-8 -Xlint:all -Werror -proc:none`, and only pinned Jackson
Core 2.21.7 on the external classpath. Its freshly acquired canonical JAR was first
verified against SHA-256
`8c5623b98f32d5f7e1287ff61ed50e08ab7b11381e991fff89327faabba42261`.
No Gradle compilation of independent tooling. Wrapper compilation is JDK-only,
with an empty external classpath. Production/Gradle import probes fail as expected.

| Invocation category | Count and result |
| --- | --- |
| Broad Gradle baseline | 0: helper/source-wiring milestone uses the applicable focused standalone baseline |
| Baseline independent main/test compilation | 1 pair PASS |
| Baseline focused standalone tests | 2 PASS: WorkflowPinsTest, WorkflowBoundaryTest before edits |
| Iteration independent main/test compilation | 1 pair PASS |
| Iteration focused standalone tests | 2 PASS: updated pin/boundary tests |
| Final independent main/test compilation | 2 pairs PASS: initial boundary and renewed boundary after shell corrections |
| Final complete standalone permanent runner set | 20 PASS: twice every runner listed in nix/tooling.nix; renewed set authoritative |
| Narrow fixture-export runner checks | 8 PASS: twice ReleaseTest, FinalizationTest, WorkflowPinsTest, WorkflowBoundaryTest |
| Wrapper main/test compilation | 2 pairs PASS; independent empty-classpath boundary |
| Wrapper runner / direct verifier | 2 / 2 PASS; each runner covers 199 fixtures, 190 raw-declaration cases and CLI/path probes |
| Standalone launcher smoke / unknown / empty-argument probes | 2 PASS / 2 expected status-2 rejections / 2 expected status-2 rejections |
| Forbidden production/Gradle import compilation | 2 expected rejections with both missing-package diagnostics |
| Final full normal Java gate | 2 PASS: `./gradlew test build javadoc verifyPublication consumerSmoke`; renewed boundary authoritative |
| Full test accounting | 2 PASS, each immediately after its full gate before focused XML replacement |
| Full-checkout CLI workflow-pins | 2 PASS, one after each full gate |
| Publication verification / manifest check | 2 / 2 PASS; verifyPublication is also a consumerSmoke dependency, not an extra invocation |
| Local module-metadata consumer | 2 PASS nested within consumerSmoke; isolated forced-offline task options unchanged |
| Independent local POM-only consumer | 2 PASS: `./gradlew -p publishing/consumer-smoke --offline -PpomOnly clean check` |
| Focused Gradle integrity tests / accounting | 2 / 2 PASS: snapshot and r19 profile |
| Snapshot inventory | 2 PASS: all 99 hashes each time |
| Separate host forced-offline full suite | 0: no dependency acquisition, Gradle, publication/consumer resolution or cache behavior changed |
| Release ten-artifact reproducibility procedure | 0: not release preparation; existing protected workflow procedure unchanged |
| Agent Nix / human J4C Nix / cache updater | 0 / 1 reported PASS / 0 |
| Live release, settings, dispatch, commit, tag, push operations | 0 each |

Final permanent suite: StrictJson 43 assertions; PublicationTest 137; ReleaseTest 40;
signing/inventory 141; remote verification, create-only tags/releases, process
isolation, workflow pins/boundaries PASS; TestResultsTest 29 assertions with 26
accounting fixtures. Exact logs: renewed-final-tooling.log, renewed-final-java.log, initial boundary logs and compile logs
under the external evidence directory. Baseline/focused test PASS output was also
captured in this task's tool transcript.

The initial normal Java invocation reports 27 actionable tasks: 9 executed,
18 up-to-date. The renewed invocation reports 27 actionable tasks: 8 executed,
1 from cache, 18 up-to-date; core:test is FROM-CACHE, storage-nio:test and Javadocs
are UP-TO-DATE. These are two invocations, preserving existing Gradle incremental
behavior. Each nested module consumer reports five executed tasks. Both full
accounting checks accept 405 JUnit tests, zero failures/errors/skips, exactly all
92 portable cases, snapshot 3/3 and profile 1/1. Both full XML trees/accounting
sets are preserved outside build outputs (initial-* and current full-*). Each POM
consumer reports five executed tasks. Initial focused invocation: ten actionable
tasks, two executed/eight up-to-date; renewed focused invocation: ten actionable
tasks, one executed/one from cache/eight up-to-date. Each immediate accounting
accepts four integrity tests with zero failures/errors/skips. Focused XML is not
full-suite evidence.

Direct extraction/surrounding-workflow equality, all run-body `bash -n` syntax checks,
actual Java workflow structural checks, source membership, protected-input byte audit
and `git diff --check` pass. No qualification invocation failed. The initially passing gates and source freeze
were superseded when final shell review exposed the inherited failure-propagation
holes. The action, approved digest and shell regressions were corrected, then the
complete standalone/wrapper suite, all four fixture-export checks and complete
Java/publication/consumer/integrity boundary were renewed. The second boundary and
freeze are authoritative; no included input changed afterward. This justified
rerun is explicitly counted above. An optional host PyYAML availability probe found
that module absent; no dependency was installed or added, and the established Java
structural checks plus shell syntax checks were used. No general YAML/runtime
simulator or additional downloader was introduced.

## 10. Dependency and protected-input accounting

No dependency versions, Gradle locks, strict verification metadata, fixed cache,
flake.lock, toolchains, VERSION, SPEC_PIN, production library/test sources, consumer
builds or signing/publication configuration changed. Protected path comparison
against HEAD passes. No cache update checkpoint is required.

Unchanged SHA-256:

- flake.lock: `44728fcfd8529462cb415b186ee4ce3400343dd911d325b6a965bd386b9f7749`.
- package-deps.json: `ee87ee929d064232e30b0bb91c8aaa4a249ae5f0fad7d85180a9833709f6a879`.
- Gradle verification metadata: `383e4bc19e0b17b635408740f9ebf1681842ae8203a102baab4c831d9ec1b16a`.

## 11. Frozen source inputs and human gate

All included implementation bytes and Git membership are frozen. A conservative
inventory of all 324 Git-known regular non-review files, including excluded prose
as a superset, is saved as `/tmp/totipo-j4c-evidence/frozen-inputs.txt` with mode,
SHA-256 and relative path for every entry. Inventory SHA-256:
`a7f8b6c521ff8d3d314822a418e337ec5e80d4dbfb209bf186f55ec0900a94f6`.
All eight changed non-report inputs have mode 100644:

| Input | SHA-256 |
| --- | --- |
| .github/actions/setup-nix/action.yml | `b6734223d6a2bf74a860ec4941380443f2b11fe71a7de182fdefe4b8f4a5f9b0` |
| .github/workflows/ci.yml | `6855a00af73cf9167f138841d353fb8aeed71afe356d3177eee5ec89e906a7b9` |
| .github/workflows/release.yml | `33cfa24d5f90a18ccf28dc9bfcc928790da4462a5c2023da95206ac0928cc92f` |
| .github/workflows/verify-release.yml | `df863f6cc17d8543f685fe1fe6f541ef045d3e088c7feb2006ed45a47eb1a056` |
| nix/tooling.nix | `f0af310b11e1f1b9e5f2d649128dd2eb9f1d645c18c36ca41827a4baebde9883` |
| tooling/src/main/java/org/totipo/tooling/release/WorkflowPins.java | `a0d2eb22f37d4d497cb2f5a8c1545c05fafa446f6dc60d82600248cba8e36b80` |
| tooling/src/test/java/org/totipo/tooling/release/WorkflowBoundaryTest.java | `87c4dcdd3fc95a713ea434ad7548e03bff3bf1748b2f95d461fc40c63f04f269` |
| tooling/src/test/java/org/totipo/tooling/release/WorkflowPinsTest.java | `6b01bb3fc1d7fe8b0b79fcd4070bea616b81817c0b9a694fcf913f7d56330fbe` |

The owner reported on 2026-10-13 that the single final Git-aware routine gate passed:

```sh
nix flake check
```

This is the owner's J4C result, separate from J4B's earlier qualification. Agent
Nix invocations remain zero. After receiving the result, all 324 frozen file hashes,
modes and exact Git source membership were rechecked and remain unchanged. No second
nix build, cache updater or release-only reproducibility operation is requested.
Any later included-input edit invalidates this freeze and the human result;
requalify the affected host boundary and renew the human gate. Only this
confirmed-excluded report was updated after qualification, to record the result
and the read-only LOC comparison below. Intent-to-add entries remain for review/commit.

## 12. Hosted runtime limitation

Host/static checks do not prove GitHub's actual hosted self-reference/composite
resolution, nested installer execution, preinstalled runner state, daemon restart
or effective hosted Nix configuration. After the owner qualifies and commits J4C,
ordinary GitHub CI should exercise the shared action on a hosted runner. Investigate
actual failures while preserving pins and cleanup checks. No release dispatch,
publication or finalization is needed to exercise CI. Repository protection and
secret behavior remain outside this source-only audit.

## 13. Measured simplification and final Git state

| YAML scope | Before lines | After lines |
| --- | --- | --- |
| CI | 57 | 35 |
| Release | 255 | 211 |
| Verify Release | 212 | 168 |
| Shared action | 0 | 38 |
| Total | 524 | 452 |

The three workflows remove 110 lines net; the action adds 38: **72 fewer YAML lines**.
The runtime implementation plus production WorkflowPins and Nix wiring is 39 lines
smaller overall. Permanent regression tests add 86 net lines; all eight executable/
configuration paths together are 47 lines larger after strengthening guardrails.
The separate review report is excluded from implementation comparisons. Eight
existing/new executable/configuration files plus this report are changed. No
unrelated source file changed.

One reviewed bootstrap definition replaces five copies. Nix is still installed
once per executing job; this shares configuration, not an installed environment
between GitHub-hosted jobs. There is no Nix-store caching or new reusable workflow.

Final branch/HEAD match the starting values. Full `git status --short --untracked-files=all`:

```text
 A .github/actions/setup-nix/action.yml
 M .github/workflows/ci.yml
 M .github/workflows/release.yml
 M .github/workflows/verify-release.yml
 M nix/tooling.nix
 A review/J4C_SHARED_NIX_BOOTSTRAP_REPORT.md
 M tooling/src/main/java/org/totipo/tooling/release/WorkflowPins.java
 M tooling/src/test/java/org/totipo/tooling/release/WorkflowBoundaryTest.java
 M tooling/src/test/java/org/totipo/tooling/release/WorkflowPinsTest.java
```

`git diff --name-only` lists those same nine paths without status prefixes.
`git diff --cached --name-only` is empty; `git diff --cached --quiet` and
`git diff --check` pass. Both added files are intent-to-add only, mode 100644.
External evidence/cache/build state is not included. No implementation blocker remains. The only implementation deviation is the
minimal fail-closed shell strengthening documented in section 5. Human Nix qualification
passed as reported by the owner; ordinary hosted CI remains untested. No unperformed
check is reported as passed.

## 14. Library versus tooling Java LOC

Measured from the final Git-known working-tree Java files using `rg --files` over
`core/src`, `storage-nio/src` and `tooling`, filtered to `*.java`. LOC here means
physical lines, including comments and blank lines; this is not a statement count.
Production is `src/main/java`; tests include `src/test/java` and the NIO
`src/testFixtures/java` helper. Standalone tooling includes both the publication/
release tools and the JDK-only wrapper verifier. Every discovered Java file is
Git-known; all 180 Java files are accounted for. Generated/build files, vendored
specification resources, Gradle/Nix configuration, workflow/action YAML and Markdown
are excluded from this like-for-like Java comparison.

| Scope | Production files | Production LOC | Test/fixture files | Test/fixture LOC | Total LOC |
| --- | ---: | ---: | ---: | ---: | ---: |
| Core library | 82 | 3,103 | 43 | 5,338 | 8,441 |
| NIO library | 12 | 622 | 7 | 822 | 1,444 |
| **Library total** | **94** | **3,725** | **50** | **6,160** | **9,885** |
| Publication/release tooling | 23 | 2,059 | 11 | 1,314 | 3,373 |
| Wrapper tooling | 1 | 137 | 1 | 293 | 430 |
| **Tooling total** | **24** | **2,196** | **12** | **1,607** | **3,803** |

Tooling production LOC is **59.0%** of library production LOC. Including tests and
fixtures, tooling LOC is **38.5%** of library LOC. Excluding blank lines (but retaining
comments), library production/tests total 3,422/5,798 lines (**9,220** combined),
versus tooling 2,131/1,567 (**3,698** combined). The separate test columns make the
library's larger test suite visible rather than conflating it with shipped code.

### Excluding comments and blank lines

Using the same 180 files, a raw-source Java lexical scan removes line/block comments
while preserving string literals, character literals and text blocks. A remaining
nonblank physical line counts once; a mixed code/comment line counts as code.
Comment-only and blank lines do not count. Braces, declarations and literal-content
lines count, so this remains a physical code-line metric rather than a statement
count. Focused counting fixtures cover multiline/mixed comments, escapes, URLs and
comment delimiters inside literals. The read-only measurement helper and results
are external evidence: `/tmp/totipo-j4c-evidence/java-code-loc.py` and
`java-code-loc.txt`; no repository dependency or executable input was added.

| Scope | Production code LOC | Test/fixture code LOC | Total code LOC |
| --- | ---: | ---: | ---: |
| Core library | 2,525 | 4,951 | 7,476 |
| NIO library | 542 | 766 | 1,308 |
| **Library total** | **3,067** | **5,717** | **8,784** |
| Publication/release tooling | 1,942 | 1,265 | 3,207 |
| Wrapper tooling | 122 | 271 | 393 |
| **Tooling total** | **2,064** | **1,536** | **3,600** |

With comments and blank lines excluded, tooling production is **67.3%** of library
production code LOC; including tests/fixtures, tooling is **41.0%** of library code
LOC. Compared with the physical totals, this removes 1,101 library lines and 203
tooling lines. The larger proportional reduction in the library reflects more
comments/blank lines, particularly in production source.

This measurement and recording the owner's Nix result changed only this excluded
report. Frozen implementation inputs, final Git path/status inventory and staged
content remain unchanged; no Java rebuild or renewed Nix gate is needed.

## 15. J4C correction — regression checks without local self-authentication

This section supersedes the initial implementation's self-hash design, final source
freeze and human Nix result above. Sections 1–14 preserve the original J4C history
and evidence; their qualification claims apply to that earlier implementation.
The correction starts from the intended uncommitted J4C checkout, not a clean tree:
`main`, HEAD `2597be7838da5f1eeab94b1e5ed9d1b07a7ed300`, exactly the nine paths
listed in section 13, with both new files intent-to-add and no staged contents.
AGENTS.md and this report were read, with relevant J4B/J4S bootstrap, permission,
activation and qualification guidance consulted. Work remains exclusively here,
without sibling repositories, worktrees, sub-agents or release operations.
Classifications: RELEASE_TOOLING; DEPENDENCY_BUILD for the retained J4C Nix source
wiring; DOCS_JAVADOC for excluded report prose. No dependencies/build behavior,
production library, public API, publication architecture or release preparation changed.

### Design decision and changes

Removed repository-local full-file SHA-256 enforcement from WorkflowPins, its
helper and the dedicated mutation matrix that maintained that byte contract.
No normalized hash, selected-section hash, generated lock or replacement local
authentication mechanism was added. Git identifies the committed composite source;
GitHub supplies repository-local action resolution at the workflow commit.
Repository tests catch accidental regressions; they do not independently authorize
source or defend against an actor who can change both implementation and tests.
External action commit pins and the external installer version/checksum remain
required acquisition identities. Release source/version and artifact identity
checks retain their separate purpose and were not weakened.

The entire composite action, all five `$/` references, all three workflows and
`nix/tooling.nix` are byte-identical to the correction baseline. This preserves
installer acquisition/checksum-before-installation, identity validation, daemon/user
token cleanup/restart, effective token rejection and J4C's failure propagation fixes.
It also preserves older-checkout source-sensitive builds and narrow Git-aware
qualification membership, without trying to simulate GitHub action resolution.

Removed the exact step-name/build-command adjacency contract from WorkflowPins.
Its tests no longer mutate every shell command, variable, shell declaration,
conditional or YAML layout merely to prove that local bytes changed. A positive
fixture now accepts a comment addition and equivalent local variable rename.
Missing/redirected action invocations and alternate bootstrap paths still fail.
Build credential scrubbing is checked separately at the release job boundary,
without requiring immediate textual adjacency to setup.

### Remaining permanent regression coverage

- WorkflowPinsTest: reviewed immutable external action references; missing action;
  expected CI/release/verification invocation counts (1/2/2); removed/redirected local
  references; unreviewed external actions and alternate inline installation paths.
- WorkflowBoundaryTest: actual Bash acquisition/checksum failure and successful
  acquisition using existing function fakes; PATH-visible Nix rejection; successful
  identity/empty-token validation; failed version queries with expected or empty
  stdout; failed empty-output configuration query; nonempty effective tokens.
  No real Nix, installer, downloader, sudo or daemon executes in these fixtures.
- The installer URL/version and SHA-256 assertions identify an external input.
  They are intentionally retained; they are not a hash of repository-local code.
- Existing disabled publication/finalization guards, verification without Maven
  publication capabilities or inherited publishing secrets, one-authorized-Gradle
  invocation sequencing, permissions, source identity and independent consumer
  boundaries remain checked. The actual source-selection declaration still includes
  the action and excludes historical reports/broad action trees.
- All ten permanent standalone runners remain: StrictJsonTest, PublicationTest,
  ReleaseTest, AuthorizationTest, RemoteVerificationTest, FinalizationTest,
  FinalizationProcessTest, TestResultsTest, WorkflowPinsTest, WorkflowBoundaryTest.
  JDK-only WrapperVerifierTest and direct wrapper verification also remain.

Intentionally implementation-specific assertions are small regression conventions:
reviewed external `uses` syntax supports the existing bounded line scanner; the
local reference spelling and 1/2/2 counts catch missing/redirected setup; release-job
setup-before-runtime-build and environment-scrub markers catch accidental credential
exposure; literal false guards catch accidental activation. Existing named-step/run
block extraction selects the actual Bash bodies under test. Actual Nix fileset
path assertions catch lost fixture membership and accidentally broad source inputs.
Changing those conventions can require corresponding tests to change, but ordinary
action shell edits no longer require a second local identity assertion. None of
these tests claims to authenticate the repository against its own authority.
No broader tooling cleanup or new validation framework was implemented.

### Actual reduction relative to the initial J4C implementation

Physical line diffs, including comments/blanks, measured against the captured
correction baseline under `/tmp/totipo-j4c-correction/baseline/`:

| Java file | Added | Removed | Net |
| --- | ---: | ---: | ---: |
| Production WorkflowPins.java | 4 | 18 | -14 |
| Test WorkflowPinsTest.java | 7 | 23 | -16 |
| Test WorkflowBoundaryTest.java | 10 | 0 | +10 |
| Tests total | 17 | 23 | -6 |
| Java total | 21 | 41 | -20 |

Only these three Java files and this existing report change during the correction.
Line reduction is descriptive, not an acceptance threshold. The initial J4C YAML
reduction remains unchanged. The section 14 LOC tables describe the historical
implementation and are not renewed measurements of this correction.

### Renewed host qualification and accounting

Evidence is outside source membership in `/tmp/totipo-j4c-correction/`.
Direct JDK 25.0.4.1+1 compilation used `--release 25 -encoding UTF-8 -Xlint:all
-Werror -proc:none`, explicit main/test sourcepaths and only pinned Jackson Core
2.21.7 on the external classpath. Its canonical JAR was acquired and checked against
`8c5623b98f32d5f7e1287ff61ed50e08ab7b11381e991fff89327faabba42261`.
Independent tooling was never compiled by Gradle.

| Correction invocation category | Count / result |
| --- | --- |
| Broad Gradle baseline / focused Gradle tests | 0 / 0; relevant standalone baseline used |
| Independent main/test compilation | 2 pairs PASS: baseline and corrected stable source |
| Focused standalone runners | 4 PASS: pin/boundary pair before and after edits |
| Full permanent standalone suite | 1 set / 10 runners PASS |
| Actual fixture-union Git-known export | 108 files; all 10 runners above PASS on this narrow projection |
| JDK-only wrapper main/test compilation | 1 pair PASS, empty external classpath |
| Wrapper permanent runner / direct verifier | 1 / 1 PASS; 199 fixtures and 190 raw-declaration cases |
| Standalone launcher smoke / argument probes | 1 PASS / 2 expected status-2 rejections |
| Forbidden production/Gradle import compilation | 1 expected rejection, both missing-package diagnostics checked |
| Final normal Java gate | 1 PASS: `./gradlew test build javadoc verifyPublication consumerSmoke` |
| Full test accounting | 1 PASS immediately after the full gate; full XML preserved externally |
| Full-checkout workflow-pins CLI / manifest check | 1 / 1 PASS |
| Publication verification / nested module consumer | 1 / 1 PASS within final Java invocation |
| Independent offline POM-only consumer | 1 PASS: `./gradlew -p publishing/consumer-smoke --offline -PpomOnly clean check` |
| Separate host forced-offline full suite | 0; no acquisition/resolution/cache/build behavior change |
| Release-only ten-artifact reproducibility | 0; support-tooling correction, no release preparation |
| Agent Nix / renewed human Nix / cache updater | 0 / 1 reported PASS / 0 |
| Commit, tag, push, dispatch, signing, publication, settings mutation | 0 each |

The final Java invocation reports 27 actionable tasks: 9 executed, 18 up-to-date.
Its nested isolated module consumer reports five executed tasks, not another full
suite invocation. Accounting accepts 405 JUnit tests, zero failures/errors/skips,
exactly 92 portable cases, snapshot 3/3 and profile 1/1. No focused Gradle run replaced
that XML. Independent POM-only consumer results are recorded in `pom-consumer.log`.
The full gate was renewed conservatively to exercise standalone-launcher integration
with publication/consumption; no library/build/publication behavior changed.
Nix retains its authoritative focused integrity accounting and snapshot hash checks.
No additional host full-offline or release reproducibility run was required.

One host helper setup attempt failed because `python3` and `/bin/bash` were absent
from the host's conventional paths; its script was not created and subsequent Java
launches had no compiled classes. This was before baseline qualification, with no
source edits. Using the existing explicit Python executable and PATH-resolved Bash
fixed the host invocation. Both actual compilation pairs and all subsequently run
qualification checks passed; no source correction or repeated final gate was needed.

### Renewed source freeze, Git membership and human gate

The earlier J4C freeze and owner-reported Nix PASS are superseded by the included
Java edits. The owner reported that the renewed gate passed on this source boundary:

```sh
nix flake check
```

**Renewed human Nix status: PASS (owner reported on 2026-10-13).**
After the report, all 324 frozen hashes/modes and exact non-review Git membership
were rechecked and remain unchanged. Branch, HEAD, nine-path Git status and absence
of staged content also remain unchanged. Only this excluded report was updated;
no Java rebuild or further Nix run is required.
No second build or dependency-cache update is required. Any later included-source
edit invalidates this boundary. Only the confirmed-excluded report is finalized
after the freeze. qualification.nix, flake.nix and tooling/wrapper filesets were
inspected; report exclusion and action inclusion remain intact.

All 324 Git-known regular non-review files are inventoried with modes and hashes
in `/tmp/totipo-j4c-correction/frozen-inputs.txt` (conservative input superset).
Inventory SHA-256:
`3a208c0cd70c1d1eab2c64a0fac251359659fa1ecf2cb0478d8e0fdfb9da3d68`.
These are handoff evidence hashes, not a permanent local authentication contract.
All eight changed non-report inputs remain mode 100644:

| Input | Frozen SHA-256 |
| --- | --- |
| .github/actions/setup-nix/action.yml | `b6734223d6a2bf74a860ec4941380443f2b11fe71a7de182fdefe4b8f4a5f9b0` |
| .github/workflows/ci.yml | `6855a00af73cf9167f138841d353fb8aeed71afe356d3177eee5ec89e906a7b9` |
| .github/workflows/release.yml | `33cfa24d5f90a18ccf28dc9bfcc928790da4462a5c2023da95206ac0928cc92f` |
| .github/workflows/verify-release.yml | `df863f6cc17d8543f685fe1fe6f541ef045d3e088c7feb2006ed45a47eb1a056` |
| nix/tooling.nix | `f0af310b11e1f1b9e5f2d649128dd2eb9f1d645c18c36ca41827a4baebde9883` |
| tooling/src/main/java/org/totipo/tooling/release/WorkflowPins.java | `f6f6faa283307e7b41a02c52aa68ed19099a1c42e59586ccdb6dd7238b6b420b` |
| tooling/src/test/java/org/totipo/tooling/release/WorkflowBoundaryTest.java | `1295aab4858a3854669a6b6f6909aa40f109c62556d15b37ad2df55a93787eba` |
| tooling/src/test/java/org/totipo/tooling/release/WorkflowPinsTest.java | `4831c02ceac653de9759ae684f8d3b2ff7aeba25e38fba82c7aa0a9e837f3b90` |

Final branch/HEAD and complete Git status remain exactly those recorded in section
13. `git diff --name-only` lists those same nine paths; `git diff --cached
--name-only` is empty; `git diff --cached --quiet` and `git diff --check` pass.
Action/report intent-to-add entries remain; no content is staged. No cache,
generated output, evidence or secret was added to source membership.
Both exact activation guards remain disabled: `if: ${{ false && github.run_attempt
== 1 }}` for publishing and `if: ${{ false }}` for finalization. No live release
operation was performed. J4C simplification and qualification are complete; stop here.

# Java GitHub Actions refresh audit

## Starting state and scope

Audit date: 2026-10-10. Work is confined to `totipo-java`.
Starting branch: clean `main`; HEAD: `f0a028676c1801a10b2d8d2650bf9c24357d1fa1`.
`git status --short` was empty. VERSION is `0.2.0`; SPEC_PIN.md remains
r19 at upstream `cdb4e91be1c6d3704874b2b92457ffe7be5e9084`.
Before edits, read AGENTS.md, README.md, RELEASE_CHECKLIST.md, both workflow
files, flake.nix, qualification.nix and package-deps.json.
Classifications recorded before editing: `DEPENDENCY_BUILD`, `RELEASE_TOOLING`
(the guard covers the protected release workflow). No production/API change,
release preparation, Java dependency change or fixed-cache update.

## Complete external uses inventory

Both `.github/workflows/*.yml` and `*.yaml` were inspected. There are two files,
nine external uses entries and three distinct repositories; no local, Docker,
reusable-workflow or artifact action references.

| Workflow | Job | Repository | Existing version | Existing full SHA | Type/runtime | Maintenance |
| --- | --- | --- | --- | --- | --- | --- |
| ci.yml | build | actions/checkout | v7.0.1 | 3d3c42e5aac5ba805825da76410c181273ba90b1 | JavaScript / node24 | Current stable; maintained security/major-fix support |
| ci.yml | build | cachix/install-nix-action | v31.11.1 | 13d8dd58da0234aa297dedd986986ccb8e7f3e24 | Composite / Node not applicable | Current stable; maintained |
| release.yml | preflight | actions/checkout | v7.0.1 | 3d3c42e5aac5ba805825da76410c181273ba90b1 | JavaScript / node24 | Current stable |
| release.yml | preflight | actions/setup-java | v6.0.1 | de7274f081f381c8f8158605e0321c36c376e2e6 | JavaScript / node24 | Current stable; maintained |
| release.yml | publish-central | actions/checkout | v7.0.1 | 3d3c42e5aac5ba805825da76410c181273ba90b1 | JavaScript / node24 | Current stable |
| release.yml | publish-central | actions/setup-java | v6.0.1 | de7274f081f381c8f8158605e0321c36c376e2e6 | JavaScript / node24 | Current stable |
| release.yml | verify-central | actions/checkout | v7.0.1 | 3d3c42e5aac5ba805825da76410c181273ba90b1 | JavaScript / node24 | Current stable |
| release.yml | verify-central | actions/setup-java | v6.0.1 | de7274f081f381c8f8158605e0321c36c376e2e6 | JavaScript / node24 | Current stable |
| release.yml | tag-and-github-release | actions/checkout | v7.0.1 | 3d3c42e5aac5ba805825da76410c181273ba90b1 | JavaScript / node24 | Current stable |

## Upstream resolution, release notes and final pins

Canonical GitHub release listings and release bodies were read using anonymous
HTTPS. All repositories report archived=false and disabled=false. Candidate tags
were independently resolved with `git ls-remote` (including peeled-tag queries),
and exact action.yml plus README were fetched at each full SHA. No Android
repository or copied Android pin was used.

| Repository | Old → final release | Old → final SHA | Old → final runtime | Runner requirement |
| --- | --- | --- | --- | --- |
| actions/checkout | v7.0.1 → v7.0.1 | 3d3c42e5aac5ba805825da76410c181273ba90b1 → same | node24 → node24 | >= 2.327.1; authenticated Git from Docker would require >= 2.329.0, unused here |
| actions/setup-java | v6.0.1 → v6.0.1 | de7274f081f381c8f8158605e0321c36c376e2e6 → same | node24 → node24 | >= 2.327.1 |
| cachix/install-nix-action | v31.11.1 → v31.11.1 | 13d8dd58da0234aa297dedd986986ccb8e7f3e24 → same | composite → composite | No numeric runner minimum documented; Linux/macOS, Bash/Python/curl/sudo prerequisites |

All existing pins already resolve to the latest stable maintained releases. There
is no intervening release range to upgrade, and no speculative pin churn was made.
No repository-controlled JavaScript action intentionally uses Node 20. Node 24
metadata is exact-commit evidence, not inferred from the major version. Existing
GitHub-hosted ubuntu-latest / ubuntu-24.04 runner selections are retained.

Reviewed sources and compatibility findings:

- [Checkout v7.0.1 release](https://github.com/actions/checkout/releases/tag/v7.0.1)
  (2026-07-20): default-input unsafe-PR checking, branch whitespace handling,
  Git unset-value escaping and dependency fixes. The pinned
  [metadata](https://github.com/actions/checkout/blob/3d3c42e5aac5ba805825da76410c181273ba90b1/action.yml)
  declares node24. The exact README also documents v7's ESM migration and safer
  fork checkout defaults for pull_request_target/workflow_run. Neither trigger
  is used here. Checkout restricts general contributions but explicitly retains
  security updates and major breaking-fix support; it is not abandoned.
- [Setup Java v6.0.1 release](https://github.com/actions/setup-java/releases/tag/v6.0.1)
  (2026-09-09): Temurin installation/signature fixes, Alpine key handling,
  symlink-safe scripts and macOS GPG path fixes. The pinned
  [metadata](https://github.com/actions/setup-java/blob/de7274f081f381c8f8158605e0321c36c376e2e6/action.yml)
  declares node24. Reviewed README highlights include ESM, additional distributions,
  expanded caching and isolated optional GPG handling. Existing Temurin 25 inputs
  remain unchanged; no cache, gpg-private-key, Maven signing or new input is enabled.
- [Install Nix v31.11.1 release](https://github.com/cachix/install-nix-action/releases/tag/v31.11.1)
  (2026-08-13): updates installer Nix 2.35.1 to 2.35.2 for a crash fix. Exact
  [metadata](https://github.com/cachix/install-nix-action/blob/13d8dd58da0234aa297dedd986986ccb8e7f3e24/action.yml)
  is composite and invokes one local Bash script with no nested uses entries.

## Third-party trust review

Cachix's canonical repository is `cachix/install-nix-action`. Reviewed exact
[install-nix.sh](https://github.com/cachix/install-nix-action/blob/13d8dd58da0234aa297dedd986986ccb8e7f3e24/install-nix.sh):
its existing behavior configures KVM where available, trusted users, flakes,
substitution and the read-scoped GitHub token for Nix GitHub fetches; installs the
Nix daemon where supported; and exports Nix paths/certificates. The default
installer URL is releases.nixos.org for Nix 2.35.2. Read that installer without
executing it: it selects the platform tarball, checks its embedded SHA-256 and
invokes the extracted installer. The initial HTTPS installer script is not
separately digest-pinned by this action; this existing upstream trust remains
unchanged. No installer override, new nested action, project replacement,
permission expansion or release-secret access was introduced. No Nix/install
script was executed during this audit.

## Ordinary CI and checkout invariance

Ordinary CI retains push/pull_request, contents:read, ubuntu-latest, SHA-pinned
checkout and Nix installation, accept-flake-config=true, and exactly the existing
qualification command `nix flake check --print-build-logs path:.`.
One credential-free Python static pin check is added before Nix installation.
There is no ordinary setup-java, host Gradle qualification or extra nix build gate.
All five checkout entries retain persist-credentials:false. Ordinary checkout
retains default fetch depth 1. All four release checkouts retain fetch-depth:0
and the same validated exact-commit refs. No unsafe-PR opt-in was added.

## Protected release and artifact semantics

release.yml is byte-for-byte unchanged. Manual dispatch, exact input/main/commit/
version/tag checks, release concurrency without cancellation, and protected
`release` environment are preserved. Only publish-central references the signing/
Central secrets; action inputs receive none of those secrets. Secret variable
names, Java signing key selection, in-memory signing, Gradle signing/publication
commands and Maven Central identity remain unchanged. Global contents:read and
final-job-only contents:write remain unchanged; no new permissions were added.
Normal/forced-offline comparison, ten-artifact verification, remote-byte checks,
fresh Central-only module/POM consumers, retry checks and final tag/release order
remain intact.

There are no upload-artifact or download-artifact actions. Artifact naming,
archiving, directory extraction, overwrites, retention, hidden-file filtering,
artifact-service digest validation and multi-upload/download settings therefore
do not apply. Cross-job transfer remains the small preflight inventory output,
not an archive: downstream jobs rebuild the Maven-shaped staging tree locally
and compare all ten unsigned artifact hashes. No directory layout or release
artifact set changes. No artifact-action major-version migration was needed.

## Static coverage and changed files

`publishing/test_release.py` extends existing standard-library unittest coverage
with WorkflowPinTests. It scans every .yml/.yaml workflow, requires canonical
owner/action@40-lowercase-hex-SHA plus exact semantic-release comment, checks the
reviewed repository/SHA/release allowlist and records the expected runtime for
each reviewed SHA. It rejects unknown repositories/subactions, floating refs,
wrong SHAs/releases, Docker/local actions and alternate YAML uses spellings.
The runtime mapping relies on the exact upstream metadata review above; tests
perform no network requests. No YAML library or new language/tool was added.

CI executes these tests on the complete checkout before Nix, and existing release
preflight's full Python guardrails execute them too. The file-reading test explicitly
skips when workflow files are absent in Nix's filtered source; the rejection
fixtures still run there. This does not remove any existing guardrail. A future
workflow syntax outside the canonical style requires an explicit guard review.

Changed files:

- `.github/workflows/ci.yml`: add static action-pin check only.
- `publishing/test_release.py`: approved pins/runtime mapping and positive/negative
  workflow guard tests.
- `review/JAVA_GITHUB_ACTIONS_REFRESH_REPORT.md`: this audit and qualification record.

No Java source/test, VERSION, SPEC_PIN.md, dependency, Gradle lock/verification,
package-deps.json, flake.lock, flake.nix, qualification.nix, release helper
implementation or protected release workflow changes.

## Qualification accounting

| Evidence | Invocations | Result / reason |
| --- | --- | --- |
| Broad Java baseline | 0 | No Java/build/publication behavior changes |
| Focused Gradle tests | 0 | Not applicable |
| Focused Python baseline | 1 | test_release.py: 21 tests passed before edits |
| Focused Python iteration | 1 | WorkflowPinTests: 2 tests passed |
| Final full Python guardrails | 1 | test_*.py: 43 tests passed, no skips on full checkout |
| Final full Java gate | 0 | Release-guard-only change; AGENTS.md helper-only policy applies |
| Publication/consumer passes | 0 | No artifact or consumer behavior change |
| Separate host offline suite | 0 | No resolution/cache/build change requiring independent evidence |
| Human Nix | 1 | Passed; human reported success on 2026-10-11 |
| Release artifact reproducibility | 0 | No explicit release preparation; protected procedure untouched |

`git diff --check` passed after implementation and again at handoff. Action YAML
was inspected at all three exact upstream SHAs and repository workflow uses were
checked statically. actionlint is not installed/established locally and was not
installed for this task. Python emitted existing mocked-HTTPError ResourceWarnings;
all tests passed. One initial upstream API resolution attempt returned HTTP 404;
exact tag-to-SHA resolution succeeded using git ls-remote, and raw exact-SHA metadata
fetches succeeded. No validation test failures or qualification reruns occurred.

## Nix disposition and freeze

qualification.nix selects core/src, storage-nio/src, gradle, publishing and nix,
plus its explicit source-file list. It excludes .github and review. Its Python
checkPhase consumes publishing/test_*.py, so the changed test_release.py IS an
included executable qualification input. flake.nix's java check calls this
qualification.nix; the separate wrapper check consumes nix/verify-wrapper.py and
wrapper JAR/properties, all unchanged. Cache, Nix definitions and lockfile are
unchanged. Excluded YAML alone would not require Nix, but this included test does.
No cache regeneration is needed.

Included inputs were frozen after passing the final Python guardrails. The human
reported successful qualification on 2026-10-11 with:

```sh
nix flake check path:.
```

The pass is recorded from the human report, not an agent execution or independently
inspected build log. At follow-up, HEAD and the implementation diff remain unchanged
from handoff. Only this excluded report is updated after qualification; no included
input is edited and no further Java or Nix run is required.

## Release disposition and final Git state

No staging, commits, pushes, tags, publication, workflow dispatches, credential
access or agent Nix execution occurred. The human ran the qualification gate once.
No release action is authorized by this audit.
HEAD/branch remain the starting clean-main identity with these local changes:

```text
 M .github/workflows/ci.yml
 M publishing/test_release.py
?? review/JAVA_GITHUB_ACTIONS_REFRESH_REPORT.md
```

All changes remain unstaged/uncommitted. Action audit, static checks and Python
qualification are complete, including the human-reported final hermetic pass.
The action pins need no refresh because each is already current.

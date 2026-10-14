# Java qualification

Supported development, qualification and release require Nix. The locked flake
supplies JDK 25 and Gradle 9.8.0; activate the devShell through direnv or
`nix develop`. There is no separate wrapper or host-toolchain build path.
CI/release use `TOTIPO_GRADLE`, the explicit release-runtime executable; nested
consumers inherit it. Local commands below run in the activated devShell.

Agents must not run Nix. The human routine Java Nix gate is exactly:

```sh
nix flake check
```

Do not request a second build for safety. `nix build` only materializes the same
qualified unsigned library artifact output and is not another qualification gate.
Dependency changes alone require the separate human cache update checkpoint:
`nix run .#update-package-deps`, followed by cache review before qualification.
Keep the protected release workflow and its normal/offline ten-artifact comparison
separate from routine qualification. Preserve Gradle locks, strict verification,
consumer isolation, Java 17 bytecode and r19 integrity.

## Git-aware local source membership

Local pre-commit Nix qualification uses the current Git-aware working tree.
Modified tracked files remain unstaged; their current working-tree bytes are
qualified. For each new file intended for the milestone and eventual commit,
agents may run:

```sh
git add --intent-to-add <file>
```

Intent-to-add establishes source membership only. Staging actual file contents
remains prohibited without explicit user authorization. The invariant is no
staged file content, rather than a literally empty index. After intent-to-add and
before the final human gate, require `git diff --cached --quiet` to pass; record
`git status --short`, `git diff --name-only` and `git diff --cached --name-only`.
Every intended new milestone file, including its review report, must be Git-known
before qualification. Leave intent-to-add entries in place for human review/commit.

Use `nix flake check` for the final local human gate. Current-checkout commands
use Git-aware forms such as `nix build .#<target>`, `nix run .#<app> -- ...` and
`nix develop`. Explicit `path:` sources are allowed only when generated, exported,
non-Git or deliberately raw filesystem semantics are intentional. Ignored/untracked
local build, cache and secret state must remain outside Git source membership;
never intent-add caches, generated build outputs, temporary evidence or secrets.
Preserve the qualification source filter for required production inputs. Freeze included
inputs before the human gate; later included-input edits invalidate its result.
Only confirmed excluded reports may be finalized afterward.

## Milestone qualification ladder

Never trade final qualification coverage for faster iteration.
Optimize by moving expensive checks to stable boundaries, not by deleting them.
Unit/API tests, publication verification, independent consumers, standalone Java release
guardrails, fixed/offline Nix qualification and release-only reproducibility are
distinct evidence layers. Do not run every layer after every edit.

Start work from clean `main`; record starting HEAD and milestone classification
before selecting validation. Record every applicable classification:

| Classification | Scope / focused iteration |
| --- | --- |
| `DOCS_JAVADOC` | Prose or source Javadocs; Javadoc build, plus affected contract tests where needed. |
| `CORE_RUNTIME` | Core implementation; focused `:core:test`. |
| `STORAGE_NIO` | Filesystem provider; focused `:storage-nio:test`. |
| `PUBLIC_API` | Public contracts/signatures; affected module/API tests. |
| `PUBLICATION_METADATA` | Maven metadata or packaged contents; `verifyPublication`. |
| `CONSUMER_BOUNDARY` | Independent consumption/resolution; `consumerSmoke` and affected POM-only checks. |
| `RELEASE_TOOLING` | Release helpers/guardrails; focused standalone Java tests. |
| `DEPENDENCY_BUILD` | Dependency inputs, Gradle/build configuration or fixed cache; focused resolution/build work. |
| `RELEASE_PREPARATION` | Explicit release-source preparation; routine qualification plus the separate mandatory release reproducibility procedure. |

### Baseline and inner loop

For an ordinary Java/API change, take one small baseline:

```sh
gradle test
```

Do not add `clean`, `--rerun-tasks`, `--offline`, publication or consumers by
default. For publication/release-tooling work, add only the relevant focused
baseline. For pure docs/Javadocs use `gradle javadoc` instead, plus focused
tests only where the documentation describes a subtle executable contract.

Iterate with the affected module/API or helper, for example:

```sh
gradle :core:test --tests 'fully.qualified.TestClass'
gradle :storage-nio:test --tests 'fully.qualified.TestClass'
gradle verifyPublication
gradle consumerSmoke
java -cp "$TOTIPO_TOOLING_TEST_CLASSPATH" org.totipo.tooling.release.ReleaseTest "$PWD"
```

Select the relevant command, not the entire list. Do not run
`clean test build verifyPublication consumerSmoke`, full standalone tooling guardrails or
Nix after every edit. `consumerSmoke` already depends on `verifyPublication`;
its standalone consumer uses its existing isolated, forced-offline task options.
Do not weaken those options to accelerate iteration.

Diagnose broad failures narrowly. Preserve failing XML, logs and inventories
outside build outputs before `clean` or another test run replaces them. Once a
focused reproduction is stable, rerun the affected broad gate. Do not repeatedly
destroy test XML/build outputs with unrelated focused/full runs; preserve the
evidence required by `TestResults` accounting. Account for full-suite
XML before focused tests overwrite it; focused XML is not full-suite evidence.

### Stable final normal Java gate

For runtime/API/publication-affecting changes, including consumer, dependency or
build changes affecting that boundary, run once when stable:

```sh
gradle test build javadoc verifyPublication consumerSmoke
"$TOTIPO_PUBLICATION_TOOL" release test-results "$PWD"
"$TOTIPO_PUBLICATION_TOOL" publication check "$PWD" "$PWD/build/repository" "$PWD/build/publication-manifest.json"
gradle -p publishing/consumer-smoke --offline -PpomOnly clean check
```

`TOTIPO_PUBLICATION_TOOL` must name the prebuilt standalone Java launcher (see
RELEASE_CHECKLIST.md); Gradle must never compile the independent tooling.
Tooling changes also require direct JDK 25 compilation and all permanent runners
listed in `nix/tooling.nix`, with only pinned Jackson Core on the classpath. `TOTIPO_TOOLING_TEST_CLASSPATH` names those independently
compiled test/main classes and Jackson. Nix runs these guardrails as a separate
check/dependency. GitHub's configured native policy requires immutable full-commit
SHA references for external actions; do not maintain a duplicate local registry.

Gradle owns standard archive assembly. Publication acceptance checks essential
Maven metadata, expected inventory and ten whole-artifact SHA-256 hashes; it
does not compare JAR members with classes, sources or generated Javadocs.
Consistent mispackaging satisfying metadata and consumer checks is an accepted
residual risk. Hash identity alone does not prove packaging correctness.

The manifest check rereads staged bytes and proves the repository is unchanged
since `verifyPublication` accepted it; it does not repeat semantic verification. Each newly produced staging
repository must pass `verifyPublication` once before inventory, comparison, signing
or remote verification; those operations authenticate current bytes against the
accepted schema-1 manifest, which is trusted job-local output, not authorization
from an arbitrary supplied hash list.
The POM-only consumer remains an independent current gate; `consumerSmoke`
exercises module metadata. Run full accounting immediately after the full Java
suite. Preserve its evidence before diagnostic focused tests or clean. Full-suite
accounting requires successful nonzero snapshot/profile suites and the conformance
execution assertion, with no failures/errors/skips. The full tests already validate
the complete snapshot and requirements profile; no focused integrity rerun or
separate shell corpus hashing is required. For release
helper-only changes, use the relevant standalone Java baseline/focused tests and full
standalone tooling guardrails at the stable boundary, then final Nix if included; Java
coverage is required if the change also affects Java/publication behavior.

For pure docs/Javadocs without executable/build/publication behavior changes,
run Javadoc and any appropriate focused contract test. Source-Javadoc changes
also require the normal full Java gate once before handoff to qualify packaged
sources/Javadocs through the existing publication checks. Markdown-only process
or report changes do not require that full gate. A Markdown/report edit after
completed Java qualification does not automatically invalidate compiled artifacts.
Do not run release artifact reproducibility for routine code/docs milestones.

### Hermetic qualification, dependencies and freeze

Routine hermetic/fixed-dependency proof is the single human command at the top
of this file: `nix flake check`. Its authoritative suite is already offline.
Do not automatically add a second host forced-offline full Gradle suite. Use a
separate host forced-offline pass only for changes to dependency acquisition,
Gradle/build behavior, publication resolution, consumer resolution or the fixed
cache, and record the independent evidence it supplies.

For dependency-input changes, use this order:

1. Focused Gradle resolution/update work; preserve locks and strict verification.
2. Human cache checkpoint: `nix run .#update-package-deps`.
3. Exhaustively inspect `package-deps.json`: every coordinate/version, URL and SRI
   hash against locks, verification metadata and canonical artifacts, including
   plugins/transitives and parent/BOM metadata. Reject unexpected modules, dynamic
   versions and snapshots; local Totipo artifacts must resolve from staging.
4. Final normal Java gate.
5. Freeze `qualification.nix`, Nix helpers, cache and every included source path.
6. Final human `nix flake check`.

Do not regenerate `package-deps` for unrelated work or implicitly update
`flake.lock`/toolchains. Before requesting final Nix, freeze Java source, tests,
publishing tools, Nix helpers, `package-deps` and relevant included documentation.
Inspect both `qualification.nix`'s source filter and `flake.nix` inputs rather
than assuming every Markdown path is included. Currently source Javadocs under
`core/src` and `storage-nio/src` are included; `AGENTS.md`, `README.md`,
`RELEASE_CHECKLIST.md`, `API_DESIGN.md` and `review/` are excluded. Nix definitions,
lockfile, cache inputs also determine qualification.
Any later edit to an included input invalidates the final Nix result.

Normal/offline ten-artifact hash comparison remains separately mandatory for
explicit release preparation under [RELEASE_CHECKLIST.md](RELEASE_CHECKLIST.md).
It is not a routine milestone gate and Nix does not replace it.
`nix build --rebuild` is not a routine milestone command. Keep protected workflow
approval, signing, publication, tags and remote verification separate; routine
qualification authorizes no release action.

### Invalidation and execution accounting

| Later edit | Required revalidation |
| --- | --- |
| Java executable/test/build change after final Java gate | Rerun the affected final Java gate; included inputs also invalidate Nix. |
| Javadoc-only change | Rerun Javadoc and the source-Javadoc full gate policy above; included source invalidates Nix. |
| Java release-tool change | Relevant standalone tests and stable full guardrails; final Nix if included. |
| `qualification.nix`, Nix helper or `package-deps` change | Final Nix invalidated; affected Java/build/resolution checks as applicable. |
| Report-only edit excluded from derivation | No Java rebuild or Nix rerun. |
| Release-checklist prose | No artifact reproducibility rerun unless the release operation itself changed; inspect inclusion for Nix. |

Reports must record starting HEAD, classifications, validation evidence and final
Git state, plus invocation counts for baseline broad runs, focused Gradle tests,
focused standalone tooling tests, final full Java gate, publication/consumer passes, separate
host offline passes with their independent purpose, and human Nix runs. Count
invocations separately from task executions: note up-to-date tasks and nested
publication/consumer dependencies rather than implying extra full suite runs.
Record failed attempts and justified reruns, including invalidations.

For a normal Java milestone the clean target is one small broad baseline, one
final full Java qualification, zero separate host forced-offline full runs unless
justified, and one final human Nix. Docs-only excluded prose may have zero full
Java/Nix runs; record why. Never claim an unperformed human Nix run as passed.

## Application operation model

Before changing concurrency, operation scheduling, VaultState handling, session
lifecycle, publication/retry, or UI/client interaction with Java operations, read
[API_DESIGN.md's operation/state-snapshot guidance](API_DESIGN.md#operation-classes-and-state-snapshot-semantics).
Do not infer `newer VaultState == old work invalid` unless the relevant Java
operation contract says so. Preserve the distinction between snapshot validity,
application presentation relevance, and operation-specific freshness checks.

## Release implementation and activation

GitHub Actions owns release orchestration; standalone Java owns focused identity,
artifact, tag-reservation and create-or-validate checks. Repository Python release
tooling and migration oracles are removed. Do not recreate history-based upload
recovery or a coordinator. At most one authorized Gradle Central invocation per
version follows successful signing/input acceptance, producer/Sign freezing and
first-time annotated tag reservation, then fresh source/tag/artifact binding.
Signing failure must stop before reservation. Internal plugin HTTP retries
are accepted without claiming exactly-once delivery. Existing tags prohibit ordinary
republication. Use Verify Release for independently rerunnable verification.

J7 enables `release.yml`'s first-attempt publisher and `verify-release.yml`'s
success-only finalizer. Publication still requires explicit manual version/SHA
dispatch and owner confirmation of Central capacity or an approved adjustment/exemption.
Routine qualification authorizes no live release mutation.
See RELEASE_CHECKLIST.md for effective settings and pre-release prerequisites.

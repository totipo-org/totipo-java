# Java qualification

Agents must not run Nix. The human routine Java Nix gate is exactly:

```sh
nix flake check path:.
```

Do not request a second build for safety. `nix build` only materializes the same
qualified unsigned library artifact output and is not another qualification gate.
Dependency changes alone require the separate human cache update checkpoint:
`nix run path:.#update-package-deps`, followed by cache review before qualification.
Keep the protected release workflow and its normal/offline ten-artifact comparison
separate from routine qualification. Preserve Gradle locks, strict verification,
consumer isolation, Java 17 bytecode and r19 integrity.

## Milestone qualification ladder

Never trade final qualification coverage for faster iteration.
Optimize by moving expensive checks to stable boundaries, not by deleting them.
Unit/API tests, publication verification, independent consumers, Python release
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
| `RELEASE_TOOLING` | Release helpers/guardrails; specific Python unit tests. |
| `DEPENDENCY_BUILD` | Dependency inputs, Gradle/build configuration or fixed cache; focused resolution/build work. |
| `RELEASE_PREPARATION` | Explicit release-source preparation; routine qualification plus the separate mandatory release reproducibility procedure. |

### Baseline and inner loop

For an ordinary Java/API change, take one small baseline:

```sh
./gradlew test
```

Do not add `clean`, `--rerun-tasks`, `--offline`, publication or consumers by
default. For publication/release-tooling work, add only the relevant focused
baseline. For pure docs/Javadocs use `./gradlew javadoc` instead, plus focused
tests only where the documentation describes a subtle executable contract.

Iterate with the affected module/API or helper, for example:

```sh
./gradlew :core:test --tests 'fully.qualified.TestClass'
./gradlew :storage-nio:test --tests 'fully.qualified.TestClass'
./gradlew verifyPublication
./gradlew consumerSmoke
python3 -B -m unittest discover -s publishing -p 'test_release.py' -k test_exact_inputs
```

Select the relevant command, not the entire list. Do not run
`clean test build verifyPublication consumerSmoke`, full Python guardrails or
Nix after every edit. `consumerSmoke` already depends on `verifyPublication`;
its standalone consumer uses its existing isolated, forced-offline task options.
Do not weaken those options to accelerate iteration.

Diagnose broad failures narrowly. Preserve failing XML, logs and inventories
outside build outputs before `clean` or another test run replaces them. Once a
focused reproduction is stable, rerun the affected broad gate. Do not repeatedly
destroy test XML/build outputs with unrelated focused/full runs; preserve the
evidence required by `publishing/release.py` accounting. Account for full-suite
XML before focused tests overwrite it; focused XML is not full-suite evidence.

### Stable final normal Java gate

For runtime/API/publication-affecting changes, including consumer, dependency or
build changes affecting that boundary, run once when stable:

```sh
./gradlew test build javadoc verifyPublication consumerSmoke
python3 -B publishing/release.py tests
python3 -B -m unittest discover -s publishing -p 'test_*.py'
python3 -B publishing/verify-publication.py
./gradlew -p publishing/consumer-smoke --offline -PpomOnly clean check
```

The POM-only consumer remains an independent current gate; `consumerSmoke`
exercises module metadata. Run full accounting immediately after the full Java
suite, before any focused module tests. Preserve its evidence. The authoritative
Nix suite additionally runs focused snapshot/profile integrity accounting and
checks all snapshot hashes; those checks remain required there. For release
helper-only changes, use the relevant Python baseline/focused tests and full
Python guardrails at the stable boundary, then final Nix if included; Java
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
of this file: `nix flake check path:.`. Its authoritative suite is already offline.
Do not automatically add a second host forced-offline full Gradle suite. Use a
separate host forced-offline pass only for changes to dependency acquisition,
Gradle/build behavior, publication resolution, consumer resolution or the fixed
cache, and record the independent evidence it supplies.

For dependency-input changes, use this order:

1. Focused Gradle resolution/update work; preserve locks and strict verification.
2. Human cache checkpoint: `nix run path:.#update-package-deps`.
3. Exhaustively inspect `package-deps.json`: every coordinate/version, URL and SRI
   hash against locks, verification metadata and canonical artifacts, including
   plugins/transitives and parent/BOM metadata. Reject unexpected modules, dynamic
   versions and snapshots; local Totipo artifacts must resolve from staging.
4. Final normal Java gate.
5. Freeze `qualification.nix`, Nix helpers, cache and every included source path.
6. Final human `nix flake check path:.`.

Do not regenerate `package-deps` for unrelated work or implicitly update
`flake.lock`/toolchains. Before requesting final Nix, freeze Java source, tests,
publishing tools, Nix helpers, `package-deps` and relevant included documentation.
Inspect both `qualification.nix`'s source filter and `flake.nix` inputs rather
than assuming every Markdown path is included. Currently source Javadocs under
`core/src` and `storage-nio/src` are included; `AGENTS.md`, `README.md`,
`RELEASE_CHECKLIST.md`, `API_DESIGN.md` and `review/` are excluded. Nix definitions,
lockfile, cache and wrapper-check inputs also determine qualification.
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
| Python release-helper change | Relevant Python tests and stable full guardrails; final Nix if included. |
| `qualification.nix`, Nix helper or `package-deps` change | Final Nix invalidated; affected Java/build/resolution checks as applicable. |
| Report-only edit excluded from derivation | No Java rebuild or Nix rerun. |
| Release-checklist prose | No artifact reproducibility rerun unless the release operation itself changed; inspect inclusion for Nix. |

Reports must record starting HEAD, classifications, validation evidence and final
Git state, plus invocation counts for baseline broad runs, focused Gradle tests,
focused Python tests, final full Java gate, publication/consumer passes, separate
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

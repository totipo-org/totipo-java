# Totipo Java 0.3.0 release preparation

## Starting state and scope

Starting HEAD: `3d108274ce18fe698935ee76f089f9f2c6f744dd` on clean `main`.
Starting VERSION: `0.2.0`. The user selected `0.3.0` and confirmed that the Central
capacity prerequisite had already been checked. This authorizes source preparation;
publication remains a separate action.

Classifications selected before release edits: `PUBLICATION_METADATA`,
`CONSUMER_BOUNDARY`, `DEPENDENCY_BUILD` (consumer self-coordinate locks only),
`DOCS_JAVADOC` (Markdown release notes/report only), and `RELEASE_PREPARATION`.
No executable Java, public signatures, external dependencies, build configuration,
fixed cache, tooling or specification inputs are changed by this preparation.
The consumer self-version update requires no external dependency/cache update.

## Changes

- Set VERSION to `0.3.0` with exactly one final LF.
- Advance both Totipo self-coordinates in the independent consumer lockfile.
- Add finished release notes covering changes since the 0.2.0 preparation commit
  `03afb32`: causal ancestry metadata, encrypted backup capture/inspection,
  authenticated additive history import, and detached new-location restore.
- Keep published 0.2.0 README coordinates and historical notes unchanged.

## Validation and execution accounting

Local agent qualification passed. Evidence directory:
`/tmp/totipo-0.3.0-preparation-3d10827` (external temporary evidence, not Git source).

- Baseline broad invocations: 1, `gradle test`, passed; 13 actionable tasks,
  2 executed and 11 up-to-date. Test tasks were up-to-date; this is not a fresh
  test-suite execution.
- Focused Gradle tests: 0.
- Focused standalone tooling tests: 0.
- Final normal full Java gate: 1, passed, with 30/30 root tasks executed.
- Publication acceptance: 2 task executions, one in each full build; consumerSmoke
  depends on verifyPublication, not an additional acceptance invocation.
  Direct publication manifest checks: 2, passed (one per staging repository).
- Consumer passes: 2 nested module-metadata consumer invocations (one per full
  build), plus 1 explicit offline POM-only consumer invocation. Each nested build
  and the POM-only build executed 4/4 actionable tasks.
- Separate host offline full passes: 1, passed, with 30/30 root tasks executed.
  Its independent purpose is the mandatory release normal/offline ten-artifact
  reproducibility comparison, not another routine hermetic qualification gate.
- Human Nix runs: 1, passed as reported by the user: "nix flake check passed".
  No command log was supplied. The agent ran no Nix.

The exact release commands were:

```sh
gradle --no-daemon --no-build-cache --no-configuration-cache clean test build javadoc verifyPublication consumerSmoke
"$TOTIPO_PUBLICATION_TOOL" release test-results "$PWD"
"$TOTIPO_PUBLICATION_TOOL" publication check "$PWD" "$PWD/build/repository" "$PWD/build/publication-manifest.json"
# Preserve normal manifest and full XML outside build outputs before clean.
gradle --offline --no-daemon --no-build-cache --no-configuration-cache --rerun-tasks clean test build javadoc verifyPublication consumerSmoke
"$TOTIPO_PUBLICATION_TOOL" release test-results "$PWD"
"$TOTIPO_PUBLICATION_TOOL" publication check "$PWD" "$PWD/build/repository" "$PWD/build/publication-manifest.json"
EXPECTED_INVENTORY="$(cat /tmp/totipo-0.3.0-preparation-3d10827/normal-publication-manifest.json)" \
  REQUESTED_VERSION="$(cat VERSION)" REQUESTED_COMMIT="$(git rev-parse HEAD)" \
  "$TOTIPO_PUBLICATION_TOOL" release compare "$PWD" "$PWD/build/repository" "$PWD/build/publication-manifest.json"
gradle -p publishing/consumer-smoke --offline -PpomOnly clean check
```

Both fresh full runs passed **534 JUnit tests**, zero failures/errors/skips,
**92/92** portable cases with the exact-set execution assertion, snapshot suite
3 and profile suite 1. Full accounting ran immediately after each build, and
full XML and manifests were preserved externally before any subsequent clean.
The full suite validates all 99 snapshot records. Java 17 bytecode verification
passed for both modules. Existing Javadoc missing-comment warnings did not fail
either build. Both independent consumers resolved precisely core/NIO 0.3.0 and
Bouncy Castle 1.86 at runtime, with no source substitution.

All **10/10 unsigned artifact hashes matched** across normal/offline builds:
binary/source/Javadoc JARs, POMs and module metadata for both modules. The existing
standalone compare operation exited successfully. Both accepted manifests have
repository SHA-256 `be2ccc4f5d04d31d5d4cb437aaf3c5dfb978b7304e47f152bc619e1ce9d20c28`.
The manifests bind accepted bytes; this does not independently prove JAR packaging
correctness or cryptographic signer trust.

Toolchain: Nix-provided JDK **25.0.5.0.3+1** and Gradle **9.8.2**, already active
in the environment. No prebuilt standalone launcher was supplied, so unchanged
tooling main sources were compiled directly with JDK 25, release 25, strict lint,
warnings-as-errors and only pinned Jackson Core 2.21.7 on the classpath. Its cached
JAR hash matched package-deps.json. The resulting standalone launcher, outside the
checkout, was supplied through TOTIPO_PUBLICATION_TOOL; Gradle never compiled it.
One launcher smoke and one ad hoc source-contract check passed (exact VERSION/LF,
both self-locks, the permanent release-note validator and exact r19 pin). No tooling
source changes required the standalone regression runner suite.

No Gradle qualification attempt failed and no gate was invalidated or rerun.
One initial tooling-setup command failed because Python was unavailable, before
executing any script; setup was completed using shell and direct javac instead.

## Source freeze and remaining handoff

Inspected `qualification.nix` and `flake.nix`: VERSION and publishing inputs are
included; `review/` is excluded. Tooling Java is independently selected by
`nix/tooling.nix`. No flake input, lockfile, toolchain or cache update is needed.
Included inputs are frozen after the version/self-lock edits. Only excluded
review Markdown was finalized afterward. Both new review files have intent-to-add
membership; no generated output, temporary evidence or cache was added.

Final branch and HEAD remain `main` and
`3d108274ce18fe698935ee76f089f9f2c6f744dd`. `git diff --check` and
`git diff --cached --quiet` pass. Recorded `git status --short`:

```text
 M VERSION
 M publishing/consumer-smoke/gradle.lockfile
 A review/V0_3_0_RELEASE_NOTES.md
 A review/V0_3_0_RELEASE_PREPARATION_REPORT.md
```

`git diff --name-only` lists exactly those four paths; `git diff --cached
--name-only` emits nothing. All content is unstaged, with intent-to-add retained
for the notes and report. No commit or remote mutation was performed.

The human reported successful `nix flake check` after local qualification and
included-input freeze. The follow-up inspection found the same four-path diff
and unchanged version/self-lock edits. Only this excluded report was updated to
record the result; no included input was edited, so no qualification rerun is
required. Source preparation is ready for review and commit.

Review/commit and ordinary CI for the exact commit must precede
any separately authorized Release dispatch. No tag, signing, upload, workflow
dispatch or remote release action is performed by source preparation.

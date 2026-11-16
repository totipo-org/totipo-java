# Totipo Java 0.3.1 release preparation

## Starting state and scope

Starting HEAD: `89c9821489e5074b2f819ca7ab432e1d83f9ef9b`, clean `main`, one commit
ahead of local origin/main. The committed signature-freeze fix and token fix are
already present. The user selected 0.3.1 and requested carrying forward/enhancing
the 0.3.0 notes. The user previously confirmed the Central capacity prerequisite.
Source preparation does not dispatch a release, retry 0.3.0 or mutate its tag.

Classifications selected before edits: `PUBLICATION_METADATA`, `CONSUMER_BOUNDARY`,
`DEPENDENCY_BUILD` (consumer self-coordinate locks only), `DOCS_JAVADOC` (Markdown
only) and `RELEASE_PREPARATION`. No executable Java, API signatures, external
dependencies, build configuration, tooling, cache, flake or specification inputs
are changed by this preparation. The self-coordinate update requires no external
dependency/cache update checkpoint.

## Changes

- Set VERSION to 0.3.1 with exactly one final LF.
- Set both independent consumer Totipo coordinates to 0.3.1.
- Copy the 0.3.0 feature notes into version-specific 0.3.1 notes, preserving backup
  capture, import, restore, causal ancestry and compatibility guidance.
- Explain the failed 0.3.0 signature upload and distinct 0.3.1 release identity;
  describe token transport, safe phase diagnostics and signature-freeze fixes.
- Retain historical reports/notes and published 0.2.0 README coordinates.

## Validation and execution accounting

Evidence directory: `/tmp/totipo-0.3.1-preparation-89c9821`.
Baseline: one `gradle test` invocation, passed; 13 actionable tasks, 2 executed and
11 up-to-date. Library test tasks were up-to-date, not a fresh baseline suite
execution. Baseline XML and accounting were preserved externally before clean.

Focused Gradle tests: 0. Focused standalone tooling tests: 0. No tooling source
changed. The independent launcher reuses the previously direct-compiled JDK 25
tooling classes and pinned Jackson Core 2.21.7 from the qualified token fix; a
Git diff confirmed tooling sources unchanged since that compilation. Launcher
smoke passed. Gradle does not compile independent tooling.

Final normal full Java gate: 1 invocation, passed; 30/30 root actionable tasks
executed. Separate host forced-offline full pass: 1 invocation, passed; 30/30 root
tasks executed. The offline pass supplies the mandatory explicit-release
normal/offline ten-artifact comparison, not an extra routine hermetic gate.

Both fresh full builds passed **534 JUnit tests**, zero failures/errors/skips,
**92/92** portable cases through the executed-set assertion, snapshot suite 3 and
profile suite 1. Full accounting ran immediately after each build; full XML and
accepted manifests were preserved externally before any subsequent clean. The
full suite validates all 99 snapshot records. Both Java 17 bytecode checks passed.
Existing missing-comment Javadoc warnings did not fail either build.

Publication acceptance: 2 verifyPublication task executions, one per full build.
Direct manifest checks: 2, one per newly produced staging repository, both passed.
ConsumerSmoke depends on verifyPublication; it does not cause another acceptance
invocation. Independent consumers: 2 nested module-metadata consumer builds and
1 explicit offline POM-only build; each executed 4/4 actionable tasks. Runtime
resolution was precisely Totipo core/NIO 0.3.1 plus Bouncy Castle 1.86.

All **10/10 whole-artifact SHA-256 hashes matched** across normal/offline builds:
binary/source/Javadoc JARs, POMs and module metadata for both modules. The standalone
release compare operation exited successfully. Each build's accepted staging
fingerprint validated independently. The repository fingerprints differ because
they also bind ephemeral repository contents; they were not compared as artifact
identity or normalized. Acceptance/hashes do not independently establish correct
JAR member packaging, cryptographic signer trust or live Central acceptance.

Commands, using the independent launcher through TOTIPO_PUBLICATION_TOOL:

```sh
gradle --no-daemon --no-build-cache --no-configuration-cache clean test build javadoc verifyPublication consumerSmoke
"$TOTIPO_PUBLICATION_TOOL" release test-results "$PWD"
"$TOTIPO_PUBLICATION_TOOL" publication check "$PWD" "$PWD/build/repository" "$PWD/build/publication-manifest.json"
# Preserve normal manifest and full XML externally before the next clean.
gradle --offline --no-daemon --no-build-cache --no-configuration-cache --rerun-tasks clean test build javadoc verifyPublication consumerSmoke
"$TOTIPO_PUBLICATION_TOOL" release test-results "$PWD"
"$TOTIPO_PUBLICATION_TOOL" publication check "$PWD" "$PWD/build/repository" "$PWD/build/publication-manifest.json"
EXPECTED_INVENTORY="$(cat /tmp/totipo-0.3.1-preparation-89c9821/normal-publication-manifest.json)" \
  REQUESTED_VERSION="$(cat VERSION)" REQUESTED_COMMIT="$(git rev-parse HEAD)" \
  "$TOTIPO_PUBLICATION_TOOL" release compare "$PWD" "$PWD/build/repository" "$PWD/build/publication-manifest.json"
gradle -p publishing/consumer-smoke --offline -PpomOnly clean check
```

The exact VERSION/final LF, both self-locks, permanent release-note validator and
exact r19 pin check also passed in one ad hoc source check. Nix-provided host tools
remain JDK 25.0.5.0.3+1 and Gradle 9.8.2. No validation attempt failed, no gate was
invalidated and no rerun was needed. No frozen-signature fixture rerun was needed:
this preparation changes only version identity and Markdown; existing fixture
evidence for the committed build fix is recorded in its separate report.

Human Nix runs for this preparation: 1, passed as reported by the user:
"nix flake check passed". No command log was supplied. The agent ran no Nix.
Follow-up inspection found the same four-path diff and unchanged version/self-lock
edits. Only this excluded report was updated afterward; no included input changed,
so no qualification rerun is required.

## Source membership and handoff

Inspected flake inputs and qualification/tooling selection: VERSION and publishing
inputs are included; review/ is excluded; tooling Java is selected independently
through nix/tooling.nix. No Nix/helper/cache/toolchain inputs are changed. Included
inputs are frozen after the version/self-lock edits. Only excluded review Markdown
was finalized after qualification. Both new files have retained intent-to-add
membership; no actual contents are staged. No generated build/cache/temporary
evidence or secrets were added to source membership.

Final branch and HEAD remain `main` and
`89c9821489e5074b2f819ca7ab432e1d83f9ef9b`, still one commit ahead of local
origin/main. `git diff --check` and `git diff --cached --quiet` pass. Status:

```text
 M VERSION
 M publishing/consumer-smoke/gradle.lockfile
 A review/V0_3_1_RELEASE_NOTES.md
 A review/V0_3_1_RELEASE_PREPARATION_REPORT.md
```

`git diff --name-only` lists exactly those four paths; `git diff --cached
--name-only` emits nothing. No commit, push or remote release action was performed.

Local qualification and the human-reported Nix gate are complete. The preparation
is ready for review/commit. Successful ordinary CI must precede a fresh explicitly
authorized Release dispatch using
0.3.1 and its exact reviewed commit SHA. No remote mutation is part of preparation.

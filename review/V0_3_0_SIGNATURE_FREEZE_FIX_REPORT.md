# 0.3.0 missing Central signatures: freeze diagnosis and fix

Starting HEAD: `b7c5e7c51c9a31d88e4d230856a996576e52a978`, clean `main`.
Classifications selected before edits: `DEPENDENCY_BUILD`, `PUBLICATION_METADATA`,
`CONSUMER_BOUNDARY` and Markdown-only `DOCS_JAVADOC`. This is a release-build fix,
not authorization to repeat publication or prepare another version.

## Incident and diagnosis

The user reports Central deployment `530b42aa-85e2-4a6c-bb6d-f1b0cffc06f9`
uploaded but failed validation because all ten detached signatures were absent.
Read-only GitHub inspection confirms `v0.3.0` exists as an annotated tag, object
`17553f5338aa95358f6faa4f801d84349c647f55`. No tag was modified and no upload
retry was performed. Under the current release contract, 0.3.0's authorized Gradle
publication invocation has been used. The failed deployment must be investigated
in Portal; existing reservation cannot be deleted, moved or bypassed for a retry.

Upload mode freezes Sign tasks with `onlyIf(false)`. Gradle 9.8.2's signing plugin
also uses that predicate to decide whether derived signature artifacts should be
published. Thus accepted signatures can exist on disk yet all be omitted by Maven
publication. The existing signed-input guard checks files, not the actual local
Central publication tree.

Primary source: [Gradle 9.8.2 SigningExtension, DefaultDerivedArtifactFile](https://github.com/gradle/gradle/blob/v9.8.2/platforms/software/signing/src/main/java/org/gradle/plugins/signing/SigningExtension.java#L654).

## Validation plan and evidence

Evidence: `/tmp/totipo-0.3.0-signature-freeze-b7c5e7c`.
Focused baseline: one `gradle verifyPublication` invocation.
A temporary clone uses the actual pinned Gradle and plugin, a synthetic signatory,
and an init-script override restricting Maven publication to a file repository.
Remote Central tasks are prohibited. This tests file inclusion and frozen-byte
reuse, not cryptographic validity or Sonatype acceptance. No real signing key or
publishing credential is used.

## Fix

Frozen Sign tasks now stop before their generation action using
`StopExecutionException` in `doFirst`, while retaining their enabled/onlyIf state.
This preserves derived signature publication without re-signing accepted inputs.
All other artifact producers retain their existing freeze predicate. The shared
signed-input guard still runs before either local Central publisher. No signing,
tag-reservation, upload, approval or retry condition is weakened.

## Results and invocation accounting

- Root focused publication baseline: 1 `gradle verifyPublication`, passed;
  17 actionable tasks, 5 executed and 12 up-to-date.
- Broad test baseline: 0; this release-build issue used the relevant focused
  publication baseline. Focused Gradle unit-test and standalone tooling runner
  invocations: 0. No Java helper source changed.
- Temporary-clone offline fixture builds: 8 invocations: unsigned staging (1),
  synthetic signing (2), and frozen local Maven publication (5). No invocation of
  the remote Central upload/release task occurred. These isolated local publisher
  invocations do not repeat the authorized remote publication.
- Final normal Java gate: 1 `gradle test build javadoc verifyPublication consumerSmoke`,
  passed; 27 root actionable tasks, 8 executed and 19 up-to-date. Both library test
  tasks were up-to-date; this was not a fresh execution of the 534-test suite.
- Full result accounting and direct manifest check: 1 each immediately after the
  final normal gate; 534 tests, zero failures/errors/skips, 92/92 executed-set
  assertion, snapshot 3 and profile 1, and 10/10 accepted artifacts. Full XML and
  the manifest were preserved externally. Full suite evidence validates all 99
  snapshot records. Java 17 bytecode checks remain in the successful build.
- Publication metadata acceptance: 3 task executions (root baseline, temporary
  unsigned staging, final root build). The nested consumer depends on acceptance;
  it does not create another semantic publication pass.
- Local independent consumers: 1 nested module-metadata build and 1 explicit
  `gradle -p publishing/consumer-smoke --offline -PpomOnly clean check`; both passed,
  each executing 4/4 actionable tasks. Runtime graph: core/NIO 0.3.0 and BC 1.86.
- Separate host offline full Java suites: 0. The focused offline file-publication
  fixture independently exercises the changed Gradle release path and frozen
  signature selection without remote dependency resolution. This is a build fix,
  not explicit preparation of another release version; artifact reproducibility
  for a new release has not been claimed.
- Human Nix runs for this build fix: 1, passed as reported by the user:
  "ok, nix flake check passed". No command log was supplied. The agent ran no Nix.
  Follow-up inspection found the same two-path diff and unchanged build fix.
  Only this excluded report was updated afterward; no included input changed,
  so no qualification rerun is required.

The standalone publication launcher was prebuilt independently with pinned JDK 25
and Jackson Core 2.21.7 during earlier preparation; the publication/signed-input
verification code used here is unchanged. Gradle never compiled that tooling.
Current host Gradle is the Nix-provided 9.8.2 and JDK is 25.0.5.0.3+1.

Reproduction with the old build configuration: signed-input acceptance passed and
all ten original signature files remained present and unchanged, but the actual
per-module local Central repository contained **zero signatures**. The fixed build
published **10/10 accepted unsigned artifacts and 10/10 signatures**. Every staged
unsigned SHA-256 matched its accepted manifest, and each staged signature equaled
its frozen original byte-for-byte. A strengthened final fixture made the synthetic
signatory throw if called during upload mode; publication still passed and all
ten signature hashes remained unchanged. This establishes reuse, not regeneration.
Synthetic signatures provide transport/inclusion evidence only, not cryptographic
validity or successful Sonatype acceptance.

Fixture setup failures and justified reruns are retained in the external logs:
the first synthetic signatory produced MESSAGE rather than SIGNATURE armor, so
the existing guard correctly rejected it; the fixture was corrected and signing
rerun. One initial local-publisher attempt stopped at the plugin's credential
preparation task; disabling that remote-only preparation in the fixture permitted
file-only testing without credentials. The final no-re-sign fixture initially
failed Gradle validation because its new fixture property lacked `@Internal`;
the fixture annotation was corrected and the same test passed. An inventory query
initially looked in a non-existent root staging directory; evidence was collected
from the actual per-module `build/publishing/mavenCentral` trees instead. One ad hoc
diagnostic Java invocation omitted Jackson from its classpath and was corrected.
None of these fixture corrections changed qualified repository inputs or require
repeating the already successful final normal Java gate.

## Source freeze, Git state and handoff

Inspected the qualification/source selection: build.gradle.kts is included;
review/ is excluded. Flake/tooling definitions, locks, cache and dependency inputs
are unchanged. No external dependency/cache checkpoint is required. Included
inputs are frozen after the build fix; only this excluded report was finalized
after validation. The new report has intent-to-add membership. No temporary
clone, synthetic signatures, output or evidence was added to Git membership.

Final HEAD remains `b7c5e7c51c9a31d88e4d230856a996576e52a978` on `main`.
`git diff --check` and `git diff --cached --quiet` pass. Recorded status:

```text
 M build.gradle.kts
 A review/V0_3_0_SIGNATURE_FREEZE_FIX_REPORT.md
```

`git diff --name-only` lists exactly those two paths; `git diff --cached
--name-only` emits nothing. All content remains unstaged. No commit, push, workflow
dispatch, real signing, tag mutation or upload was performed.

With the human-reported Nix pass, this fix is ready for review/commit and ordinary CI.
VERSION remains 0.3.0; it has not been advanced to select another release.
The current release contract permits no ordinary republication of reserved 0.3.0.
Any next publication requires separately prepared and qualified source for a new
version and a fresh explicit owner-authorized dispatch. Verify Release remains
verification-only; it cannot repair this failed deployment or upload missing files.

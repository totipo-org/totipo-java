# Java editor integration

Starting HEAD: `952c7c3a2ec2115063a1a602254d8f7ed8d77e44`, branch `main`.
Classification: `DEPENDENCY_BUILD` for source verification, IDE output layout and
flake consumption, with prose documentation. This report consolidation is
Markdown-only (`DOCS_JAVADOC`), with no source/Javadoc/build changes. The checkout
already contained user flake/editor changes; they were preserved rather than
resetting to clean main. No release preparation occurred.

## Ownership and current integration

Totipo consumes [java-dev-tools](https://github.com/ingon/java-dev-tools).
Its `INTEGRATION.md` provides the generic recipe for other projects; the guide is
currently a local, unpublished addition there. Reusable implementation and
validation history belong in its `review/JAVA_EDITOR_TOOLKIT_REPORT.md`.

- `flake.nix` adds the GitHub input following this repository's nixpkgs and calls
  `java-dev-tools.lib.mkJavaEditor { inherit pkgs jdk gradle; }`. The devShell
  merges `javaEditor.shellEnv` and appends `javaEditor.packages`, retaining its
  Java/publication runtime and jailed-agent settings. The toolkit supplies
  `GRADLE_HOME`, the JDTLS launcher/proxy and `java-ide-sources`.
- `flake.lock` pins toolkit revision
  `0685f64189151a77d0c399441c0dffb311541ec2`. Only the toolkit node/root reference
  were added; existing input/toolchain pins are unchanged.
- `gradle/verification-metadata.xml` adds exactly 12 reviewed source-JAR SHA-256
  entries. Existing hashes remain unchanged; Gradle reordered one Jackson POM
  entry. Strict verification, dependency versions, locks and fixed cache remain
  intact. This project's dependency verification belongs here.
- `build.gradle.kts` lazily configures module Eclipse models when their plugin
  is applied: default output `build/eclipse/default`, source-set output under
  `build/eclipse/main`, `test` and `testFixtures`. This project-owned layout is
  separate from Gradle class output and cleaned with build outputs. Ordinary
  builds do not acquire Eclipse plugin application from this callback.
- `.gitignore` ignores generated `.project`, `.classpath` and `.settings/`.
  Existing build rules cover the new output. The user's removal of obsolete
  module `bin/` exceptions is preserved; no global `bin/` ignore was introduced.

No copied proxy package, Go patch/tests, source helper, Helix capability file or
nested toolkit repository remains here. The toolkit automatically negotiates
source support. README documents its consumption and `java-ide-sources`.
For future source-checksum updates, run
`java-ide-sources --write-verification-metadata sha256` in the devShell, review
the canonical artifacts and metadata, then resume strict resolution. Remove IDE
verification bypasses. Opt-in source acquisition is separate from the fixed cache.

## Consumer evidence and invocation accounting

Source verification: four focused Gradle invocations, each executing one helper
task: expected strict failure on 12 sources, checksum generation, strict online
pass and strict offline pass. Independent fresh Maven Central downloads matched
all 12 hashes and contained Java sources; this proves canonical download identity,
not publisher signatures. Core attached 12 sources and storage-nio 11 (12 unique).
Evidence: `/tmp/totipo-ide-source-audit/`.

The human reported one successful `nix run .#update-package-deps`.
`package-deps.json` stayed byte-identical to HEAD. Exhaustive review fetched all
92 artifacts across 39 coordinates, matching canonical URLs/SRI hashes,
verification metadata and locks, including plugin/transitive and parent/BOM
metadata. No dynamic/snapshot versions or remote Totipo artifacts appeared.
Opt-in source JARs were not added to the fixed cache.

Output layout: three focused Gradle model invocations, each executing one task:
expected baseline failure at `core/bin/default`, a temporary checker failure
because project references also have `kind == 'src'`, and a pass after selecting
`SourceFolder` instances. All six source folders across both modules passed;
no Eclipse project files were generated. Evidence: `/tmp/totipo-ide-output-audit/`.

Two full Java gate invocations passed: one after checksums, one justified rerun
after the included build-script edit:

```sh
gradle test build javadoc verifyPublication consumerSmoke
```

Each had 27 actionable tasks, 8 executed and 19 up-to-date. Tests, compilation
and Javadocs were up-to-date; this does not claim fresh test execution. Java 17
bytecode checks executed. Immediate standalone accounting after each accepted
413 tests, zero failures/errors/skips, snapshot 3, profile 1 and the 92/92
conformance exact-set assertion. XML/manifests were preserved in each audit's
`final-evidence/` before subsequent work.

Publication acceptance ran twice (ten artifacts per gate), standalone manifest
rechecks twice, test-results accounting twice. Two nested module-metadata and
two independent POM-only consumers passed, each executing four actions. Nested
dependencies did not repeat publication verification. POM-only command:
`gradle -p publishing/consumer-smoke --offline -PpomOnly clean check`.

The publication launcher was independently compiled before Gradle with pinned
JDK 25, `--release 25 -Xlint:all -Werror -proc:none`, and only verified Jackson
Core 2.21.7. Its temporary smoke initially failed because `/usr/bin/env` was
absent; using the available Nix Bash shebang fixed it. Gradle did not compile
independent tooling. No tooling implementation changed.

Broad `gradle test` baselines, focused Java unit/API invocations, standalone
release-tool tests and separate host forced-offline full-suite runs: all zero.
Relevant resolution/model baselines were used. The focused offline source run
proved the changed acquisition path; isolated offline consumers and authoritative
Nix qualification remain separate evidence. No release reproducibility ran.

Extraction used one further focused source invocation on this checkout; the
toolkit's later generic follow-up used another. Both executed one task and
attached 12/11 sources. Their full cross-project/helper/launcher/proxy accounting
belongs in the toolkit report; they are not further Java qualification gates.

The human reported four successful consumer `nix flake check` invocations across
source verification, layout, proxy adoption and extraction. Each included-input
change invalidated its predecessor; the fourth qualifies the current pinned
integration. The user confirmed internal Helix navigation and JDK/Bouncy Castle
source navigation work. Direct Eclipse testing was not separately reported.
Agents ran no Nix, committed nothing and pushed nothing.

## Freeze and Git state

The final included-input diff matches
`/tmp/totipo-java-editor-final-audit/frozen-included-diff.patch`, SHA-256
`b454c9438057e4e16c9d0e7235b9384df62262c9950ba761dc92fdff821eb4d7`.
`qualification.nix` includes the root build and `gradle/`; flake/lock changes
also affect qualification. README, `.gitignore` and `review/` are excluded.
This report consolidation edits only excluded prose and requires no Java/Nix
rerun. The toolkit's unpublished generic helper/docs follow-up still awaits its
own human Nix gate; the consumer's immutable qualified pin is unchanged.

This report replaces the four earlier IDE reports. Their temporary originals
remain in `/tmp/java-editor-report-consolidation/`, outside source membership;
reusable history/evidence is now consolidated in the toolkit report.

Final `git status --short` and `git diff --name-only` list `.gitignore`,
`README.md`, `build.gradle.kts`, `flake.lock`, `flake.nix`,
`gradle/verification-metadata.xml` and this intent-added report. The four old
reports were intent-only and absent from HEAD; removing their membership stages
no deletion. `git diff --check` and `git diff --cached --quiet` pass;
`git diff --cached --name-only` is empty. No generated output/cache/download or
temporary evidence is Git-known. No actual contents were staged or committed.

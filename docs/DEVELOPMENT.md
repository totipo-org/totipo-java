# Developing Totipo Java

This guide covers repository setup, editor integration and dependency maintenance.
For library integration and published dependencies, start with the [README](../README.md).
[AGENTS.md](../AGENTS.md) owns contributor rules, Git source membership and the
qualification ladder. [RELEASE_CHECKLIST.md](../RELEASE_CHECKLIST.md) owns publication
acceptance, tooling setup and release procedures; those procedures are not duplicated here.

## Environment and focused development

Supported development, qualification and release require Nix. The locked flake
supplies **JDK 25 and Gradle 9.8.0**. Activate the development shell through direnv
(`direnv allow`) or `nix develop`; `java` and `gradle` are then available.
There is no wrapper or separate host-toolchain build path. Production libraries
compile with `--release 17`; checks inspect every production class for major 61,
minor 0. The development JDK requirement does not raise the Java 17 consumer minimum.

Record milestone classification before selecting validation. Choose the applicable
baseline and focused checks from the
[qualification ladder](../AGENTS.md#milestone-qualification-ladder), rather than
running every layer after each edit. For example, a focused core test is:

```sh
gradle :core:test --tests 'fully.qualified.TestClass'
```

Pure docs/Javadocs use `gradle javadoc` and any appropriate focused contract tests;
source-Javadoc changes additionally require the normal full Java gate. Excluded
Markdown-only edits do not require a full Java or Nix rerun. Preserve full-suite
XML/accounting before focused tests or clean replace it.

The final routine hermetic gate on x86_64-linux is the human-run `nix flake check`.
Agents must not run Nix. It uses the fixed dependency cache and an isolated offline
Gradle home, retains locks and strict verification, and covers library tests,
r19 accounting/integrity, archives/Javadocs, publication acceptance, both consumer
modes and standalone Java tooling guardrails. It performs no release mutation.
No second ordinary Nix build is needed. An optional `nix build` materializes the
same qualified unsigned artifacts under `result/publication/`, with inventory and
qualification summaries; it is not another qualification gate.

Follow [Git-aware source membership](../AGENTS.md#git-aware-local-source-membership)
and freeze included inputs before the human gate. Inspect `qualification.nix` and
`flake.nix` for inclusion rather than assuming all documentation is included.
The authoritative suite already runs offline; a separate host forced-offline full
suite needs the specific build/dependency/resolution justification required by
AGENTS.md. The normal/offline ten-artifact comparison is separately mandatory for
explicit release preparation, not routine milestones.

The NIO provider suite assumes a case-sensitive Linux host with symlinks, hard
links, directory channels accepting force, POSIX permissions and `mkfifo`.
Optional provider fixtures can use assumptions; the normative corpus must run
without skips. Java tests do not qualify other platforms or physical crash behavior.
See [API scope](../API_DESIGN.md#boundaries-and-non-goals) and
[NIO durability semantics](../SPI_DESIGN.md#certainty-ownership-and-synchronization).

## IDE configuration and dependency sources

Eclipse/Buildship and JDTLS resolve dependency source JARs, which require their
own verification entries. To download and verify sources without generating IDE files:

```sh
java-ide-sources
```

After an intentional dependency update, append `--write-verification-metadata sha256`
to generate missing source checksums. Review the diff against fresh canonical Maven
Central artifacts before accepting hashes. The opt-in helper uses Gradle's Eclipse
classpath model; it does not change locks or disable verification. Ordinary tests
and dependency reports do not request these sources. Once checksums are accepted,
remove any IDE-local `--dependency-verification=off` arguments and refresh the
Gradle project. Dependency-input changes still require the cache checkpoint below.

Eclipse/JDTLS compiler output goes under each module's `build/eclipse/`, separate
from Gradle classes. Refresh the Gradle project after importing changes to pick up
these paths. Generated `.project`, `.classpath` and `.settings/` files are ignored
local state.

The devShell consumes `java-dev-tools.lib.mkJavaEditor` with this project's JDK
and Gradle, merging `shellEnv` for Eclipse/Buildship Gradle discovery. The separate
[java-dev-tools](https://github.com/ingon/java-dev-tools) flake owns the JDTLS
source-navigation proxy, its tests, automatic client-capability negotiation,
workspace isolation and `java-ide-sources`. Its input is locked and follows this
repository's nixpkgs. Source checksums and Eclipse output layout remain project-owned.
Helix uses built-in `jdtls` configuration without project language-server settings.
Reload the development environment and restart Helix after changing that input.

## Intentional dependency maintenance

For intentional library dependency updates, use:

```sh
gradle :core:dependencies :storage-nio:dependencies --write-locks --write-verification-metadata sha256
```

Review every lock and checksum change against canonical artifacts before trusting
generated metadata. Keep strict verification enabled. This command does not
implicitly authorize build-plugin or toolchain updates.

`package-deps.json` materializes locked/verified dependencies through nixpkgs
`gradle.fetchDeps`. Only dependency-input changes require the separate human cache
checkpoint, `nix run .#update-package-deps`. Cache regeneration never runs in CI.
Review every coordinate/version, URL and SRI hash, including plugin/transitive
artifacts and parent/BOM metadata, against locks, verification metadata and canonical
artifacts. Reject unexpected modules, dynamic versions and snapshots. Local Totipo
consumer artifacts must resolve from staging, never this download cache.
Keep `flake.lock` pinned; a dependency update does not authorize Nix/toolchain updates.
Follow the exact [dependency checkpoint order](../AGENTS.md#hermetic-qualification-dependencies-and-freeze)
through final Java qualification, input freeze and the final human Nix gate.
Do not regenerate caches for unrelated work.

## Publication and release maintenance

Publication checks require `TOTIPO_PUBLICATION_TOOL` naming the prebuilt standalone
Java launcher, compiled independently of Gradle. See the
[tooling setup and verification limits](../RELEASE_CHECKLIST.md#qualification-and-release-reproducibility).
CI/release use the pinned release-runtime's explicit `TOTIPO_GRADLE` executable;
nested consumers inherit it. Repository release tooling has no Python requirement.

Gradle owns standard archive assembly. Publication acceptance checks essential
metadata, inventory and ten whole-artifact hashes; JAR members are opaque to that
checker. Hash identity alone does not prove packaging correctness. Consistent
mispackaging satisfying metadata and consumer checks is an accepted residual risk.
Remote detached-signature checks establish presence, not independent signer trust.
The release checklist retains the full acceptance and reproducibility procedures.
Routine qualification authorizes no signing, tag reservation, upload or release.

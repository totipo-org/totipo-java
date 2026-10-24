# Documentation entry point cleanup report

## Scope and starting state

- Starting HEAD: `fb309f01d36a7a01075ed38fc505a08e73b7927a`.
- Branch: clean `main`; initial `git status --short`, `git diff --name-only`
  and `git diff --cached --name-only` were empty. The cached-content quiet check passed.
  There were no user changes to preserve.
- Classification: `DOCS_JAVADOC` (Markdown prose and an embedded example only).
  No source Javadocs, executable source, tests, build/dependency configuration,
  protocol pins or publishing configuration changed.
- Read AGENTS.md, README.md, API_DESIGN.md (including operation/state-snapshot
  guidance), SPI_DESIGN.md, SPEC_PIN.md, RELEASE_CHECKLIST.md and SECURITY.md.
  Inspected root/module Gradle publication configuration, independent consumer
  configuration, VERSION, qualification.nix and flake.nix.

## README before and after

| Measure | Before | After |
| --- | --- | --- |
| Lines | 309 | 156 |
| Words (`wc -w`) | 2,140 | 808 |
| Bytes (`wc -c`) | 17,894 | 7,514 |

Before: a long sequence of protocol implementation details, environment/cache/IDE
procedures, qualification policies, provider internals and release orchestration,
with dependencies near the end and no complete application example.

After: a product introduction followed by module/dependency selection, a complete
filesystem create/open example, ownership and lifecycle outcomes, essential API
and security boundaries, and a documentation map. Length reduction is incidental
to moving information to its owner; the example includes explicit outcomes,
caller-owned password cleanup and try-with-resources session closure.

## Material moved, consolidated and corrected

- Added [docs/DEVELOPMENT.md](../docs/DEVELOPMENT.md) because editor and dependency
  maintenance instructions had no suitable dedicated home. It owns Nix/devShell
  setup, IDE/source JAR verification, output layout and dependency-refresh usage.
  It links authoritative qualification and cache ordering in AGENTS.md and
  independent publication-tool setup in RELEASE_CHECKLIST.md.
- Replaced duplicated qualification command lists with the existing
  [qualification ladder](../AGENTS.md#milestone-qualification-ladder).
  Retained distinctions among baseline/focused checks, final Java qualification,
  human Nix qualification and release-only reproducibility. Preserved strict
  verification, lock/cache review, source-membership, freeze and evidence rules
  through direct cross-references and a short development overview.
- Removed release orchestration from README and API_DESIGN in favor of
  [RELEASE_CHECKLIST.md](../RELEASE_CHECKLIST.md), which remains unchanged and
  authoritative. No release authorization, capacity prerequisite, accepted
  packaging risk or signature-verification limitation was weakened.
- Consolidated creation verification, orphan-object observation, VaultId details,
  provider composition, bounded reads and locking discussions into their existing
  owners, [API_DESIGN.md](../API_DESIGN.md) and [SPI_DESIGN.md](../SPI_DESIGN.md).
  README retains the integration consequences and links the detailed contracts.
- Made API_DESIGN's conformance section state its own scope instead of assigning
  ownership to README. Preserved operation-specific snapshot validity, experimental
  stability, application responsibilities and scoped platform/conformance claims.
- Removed old namespace/preparation report navigation from the entry point;
  historical reports remain unchanged. Added the relevant 0.2.0 migration link.
- Kept Java 17 consumer compatibility distinct from Nix JDK 25 / Gradle 9.8.0
  development requirements. Retained production/test dependency distinctions.

## Obsolete or contradictory statements

- README used published 0.1.5 examples alongside a 0.2.0 released statement and
  current incompatible API guidance. Updated both Maven and Gradle examples to
  confirmed published 0.2.0.
- API_DESIGN still said J4B disabled mutation jobs pending activation, contradicting
  current J7 guidance and workflow configuration. Removed the obsolete duplicate
  release narrative and linked the authoritative release checklist.
- The prepared-release comment block in README no longer served a purpose in the
  rewritten entry point; availability now has direct Central POM evidence. No
  active tooling references that block. API_DESIGN's existing release statement
  remains consistent with verified availability.
- Historical preparation reports and release notes describe the then-unreleased
  state; they are historical evidence, not current distribution status, and were
  deliberately left unchanged.

## Publication evidence and example validation

Read-only canonical Maven Central requests on 2026-10-24 confirmed latest/release
0.2.0 in metadata for both modules. Downloaded both 0.2.0 POMs, Gradle module
metadata, binary JARs and source JARs. POM/module metadata agrees with VERSION and
Gradle configuration: Java 17 variants, NIO exposes core 0.2.0 at compile/runtime,
core has only BC 1.86 at runtime and no BC API dependency.

Canonical evidence:
[core metadata](https://repo.maven.apache.org/maven2/org/totipo/totipo-core/maven-metadata.xml),
[NIO metadata](https://repo.maven.apache.org/maven2/org/totipo/totipo-storage-nio/maven-metadata.xml),
[core POM](https://repo.maven.apache.org/maven2/org/totipo/totipo-core/0.2.0/totipo-core-0.2.0.pom),
[NIO POM](https://repo.maven.apache.org/maven2/org/totipo/totipo-storage-nio/0.2.0/totipo-storage-nio-0.2.0.pom).

Downloaded binary/source SHA-256 hashes match all four corresponding entries in
[the 0.2.0 preparation report](V0_2_0_RELEASE_PREPARATION_REPORT.md):

| Artifact | SHA-256 |
| --- | --- |
| Core binary | `4f1fb4bb1ab5f0a78c0f2d9a1ed3146413c94f631e7f9b95477968a66968520f` |
| NIO binary | `776068249e689e94136748fb8ffd86c837e0af8bbb6cec8ae5e785c4eca4ba93` |
| Core sources | `e2661770e0e59ca733959b4931689a9253988352605087c390bfc3a045dd9b6c` |
| NIO sources | `bb690c98837f938b1cce75cdfc06b44b47b066c4cb937a4ec461f2dc82f5b04b` |

Extracted the exact Java fence to VaultExample.java and compiled twice with
`javac --release 17 -Xlint:all -Werror`: once against only published core/NIO
JARs, once against existing current build classes. Both passed. `javap` confirms
major 61/minor 0 for the compiled example. Reviewed actual NioTotipo, lifecycle
result types and VaultSession contracts; published NIO/results sources match
current sources. VaultSession source comparison shows existing Javadoc additions
in the checkout, with unchanged signatures used by this example.

Ran the exact published-artifact example twice in a terminal, using BC 1.86:
explicit create, then explicit open of the same test vault. Both exited successfully
and printed the same VaultId. Session ownership and password cleanup are visible
in the compiled example. These are focused documentation probes, not full library
qualification or independent interoperability evidence.

Downloads, the probe vault, extracted example, compiled classes and validation
helper remain ignored under `build/documentation-cleanup/`; none is intent-added.
A first download-script attempt found no Python interpreter and made no changes;
retried with curl. The web reader could not access Central XML/POM URLs; direct
canonical HTTPS downloads succeeded. No build or test gate failed.

## Validation performed and skipped

- `gradle javadoc`: one successful invocation using the already available pinned
  JDK 25 / Gradle 9.8.0 environment. Four actionable tasks were up-to-date:
  both module compilation tasks and both Javadoc tasks. No clean/rerun flags.
- Checked Markdown structure manually: headings, fenced language blocks, table
  structure and list spacing. A temporary Java checker verifies balanced fences,
  final newlines, trailing whitespace and every relative link/heading anchor in
  all changed documents. No repository Markdown linter is configured; no new
  dependency was installed for this task.
- Maven/Gradle snippets reviewed against published POM/module metadata and build
  declarations. Java example compilation and create/open execution passed as above.
- Reviewed the full final diff and `git diff --check`; no whitespace errors.
- No subtle executable contract was changed; no focused contract tests needed.
- No full Java, publication/consumer, standalone release-tooling, host offline or
  release reproducibility gates: only excluded Markdown changed. Source Javadocs
  and packaged inputs did not change.
- No Nix run or human Nix pass claimed/requested. Inspected qualification.nix's
  explicit file/tree filter and flake.nix inputs: README.md, API_DESIGN.md, docs/
  and review/ are excluded. No qualification inputs or flake references changed.

Invocation counts (not task executions):

| Category | Count |
| --- | --- |
| Broad test baseline | 0 |
| Documentation baseline (`gradle javadoc`) | 1 |
| Focused Gradle tests | 0 |
| Focused standalone tooling tests | 0 |
| Final full Java gate | 0 |
| Publication verification / module consumer / POM-only consumer passes | 0 / 0 / 0 |
| Separate host offline full passes | 0 |
| Human Nix runs | 0 |
| Example javac compilations / create-open executions | 2 / 2 |

There were no gate reruns or included-input invalidations. Up-to-date Javadoc tasks
are reported as such, not as regenerated output or a full suite execution.

## Remaining documentation gaps

- SECURITY.md says pre-release while 0.2.0 binaries are published. This may mean
  pre-1.0 maturity rather than unavailable artifacts; the maintainer should define
  that terminology. Private reporting instructions remain untouched.
- No dedicated CONTRIBUTING.md exists. README links AGENTS.md as the existing
  contributor/qualification authority rather than creating a competing procedure.
- API_DESIGN is extensive. This milestone adds navigation rather than a separate
  tutorial for TOKEN editing, conflict resolution and asynchronous state handling.
- Existing Linux-only qualification, best-effort JVM erasure, lack of a security
  audit and independent interoperability evidence remain limitations, not claims
  resolved by this documentation work.

## Final Git state

Changes are uncommitted on the original main HEAD. Modified tracked files:
README.md and API_DESIGN.md. New files docs/DEVELOPMENT.md and this report are
Git-known through intent-to-add only; their contents remain unstaged.

Expected `git status --short`:

```text
 M API_DESIGN.md
 M README.md
 A docs/DEVELOPMENT.md
 A review/DOCUMENTATION_ENTRYPOINT_CLEANUP_REPORT.md
```

`git diff --name-only` lists exactly those four paths.
`git diff --cached --name-only` is empty and `git diff --cached --quiet` passes.
No content was staged, committed, tagged, published or pushed. No Nix command ran.

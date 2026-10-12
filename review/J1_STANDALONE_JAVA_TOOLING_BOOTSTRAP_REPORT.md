# J1 — Standalone Java tooling and Nix bootstrap

Status: **PASS — J1 complete. Host checks pass; owner reported the final Nix gate passed.**
All changes remain uncommitted. No agent Nix invocation, commit, tag, push,
signing, remote publication or release action occurred. Stop at J1.
Evidence directory: `/tmp/totipo-j1-Q9RFES/` (outside Git source membership).

## Baseline and classification

Read all of `AGENTS.md` before editing. Work exclusively in this repository;
no sibling repositories or isolated worktrees inspected.
Starting branch: `main`; starting HEAD:
`ccc7fdc1d77a0ca01882bbfdaa7ac065aabf2c9c` (`Update jackson lib`).
Starting `git status --short`: empty. J0 changes and its report are committed
at this HEAD. The J0 report's historical statement that work was uncommitted
is superseded by the current committed checkout.

Classification: **DEPENDENCY_BUILD**: standalone JDK compilation and Nix build
wiring. No production runtime/API, publication metadata, consumer resolution,
existing release helper or release preparation change. No additional
classification applies. The final normal host gate is retained because build
inputs and flake wiring change; it verifies the existing publication boundary.

## Reviewed dependency and authentication

Inherited evidence: the committed J0 report records prior dependency review and
human Nix success; the owner separately reports J0 CI success. Neither qualifies
J1. Fresh agent audit (`pin-audit.txt`) reconciles the catalog, core lock,
verification metadata and fixed cache with J0:

- Coordinate: `com.fasterxml.jackson.core:jackson-core:2.21.7`.
- SHA-256: `8c5623b98f32d5f7e1287ff61ed50e08ab7b11381e991fff89327faabba42261`.
- SRI: `sha256-jFYjuY8y1ffhKH/2HtUOCKt7ETgemR//iTJ/qrukImE=`.
- Canonical URL: `https://repo.maven.apache.org/maven2/com/fasterxml/jackson/core/jackson-core/2.21.7/jackson-core-2.21.7.jar`.

The JSON cache has top-level metadata (`!comment`, `!version`) and repository
objects. The exact JAR SRI is extracted from
`cache."https://repo.maven.apache.org/maven2"."com/fasterxml/jackson/core#jackson-core/2.21.7".jar`.
The same record also has `module` and `pom` hashes. No checksum is maintained in
a second bootstrap pin file. The reference digest in this report is evidence.

`nix/tooling.nix` reads the catalog with `builtins.fromTOML`, requires version
2.21.7, enumerates Jackson Core records across repository objects, and requires
exactly one identity at the approved coordinate and location. It rejects missing
records/hashes, other versions/locations, ambiguous identities, unsupported
artifact variants, and noncanonical SHA-256 SRI syntax. `fetchurl` receives the
record's hash and exact canonical URL. A malformed configuration fails closed;
there is no fallback to another version or local cache. The owner-reported
J1 Nix gate passed against the unchanged frozen inputs (see below).

Host validation freshly downloaded canonical bytes into the evidence directory,
checked the exact reviewed SHA-256 before compilation, and rechecked it in the
final host script. Gradle was not used to acquire the tooling JAR. This is host
development evidence, not a substitute for fixed-output Nix qualification.
Byte identity relative to the reviewed J0 pin is the trust claim; no new claim
about upstream signing-key trust or complete publisher supply-chain integrity.

Catalog, all locks, verification metadata, `package-deps.json` and `flake.lock`
are unchanged. No cache update checkpoint is needed or requested.

## Compilation architecture and boundary

Sources:

- `tooling/src/main/java/org/totipo/tooling/Main.java`
- `tooling/src/main/java/org/totipo/tooling/StrictJson.java`
- `tooling/src/test/java/org/totipo/tooling/StrictJsonTest.java`

A `stdenvNoCC.mkDerivation` compiles main sources directly with the exact
`jdk25_headless` executable, `--release 25`, UTF-8, `-Xlint:all -Werror`, disabled
annotation processing and explicit sourcepath. JDK major version 25 is asserted.
The only external compilation classpath entry is fixed-output Jackson Core.
Tests compile against those main classes and that same JAR. No Gradle, Totipo
production classes/JARs, Databind, Annotations or test framework is included.
Only main classes are installed; test classes and negative fixtures stay in the
build directory. The tooling is not a Gradle module or Maven publication.

Positive host builds use JDK 25.0.4.1+1 and the authenticated JAR. The synthetic
`Forbidden.java` imports the real production type `org.totipo.RevisionId` and
fails under the restricted classpath with `package org.totipo does not exist`.
The Nix derivation repeats this test using an empty sourcepath and verifies that
diagnostic. Actual production sources are never modified for the test.
This proves a direct compilation boundary, not impossibility of arbitrary future
reflection, subprocesses, or user edits.

## Strict JSON contract and tests

`StrictJson` uses Jackson's streaming API; no second parser or object mapper.
It explicitly enables strict duplicate detection and disables every
`JsonReadFeature` syntax extension. Exactly one root value is consumed; a second
value or non-whitespace suffix fails. Lazy strings are decoded explicitly so
string constraint checks cannot be skipped.

Limits: 16,384 raw bytes, depth 16, string length 256, member-name length 128,
number length 128 and 4,096 tokens. Jackson receives explicit constraints;
a separate byte-array length check runs before parser construction. This adapter
accepts already-materialized bytes; future file/stream readers must bound input
acquisition too. It does not implement a release schema.

The JDK-only runner passes **43 assertions**, covering nested arrays/objects,
whitespace, scoped member names, escaped duplicate names, malformed/truncated
input, extra root values, trailing garbage/comments, comments, trailing commas,
unquoted/single-quoted names, nonfinite numbers, leading zero/plus/decimal
extensions, raw control characters, byte/depth/string/name/number/token bounds
and multibyte input. Exact-boundary byte/depth/string fixtures succeed.

Integer handling checks the token is `VALUE_NUMBER_INT` and obtains
`BigInteger`, preserving `18446744073709551616` exactly. Numeric strings,
decimal/exponent forms, booleans and null are rejected as integer fields.
Future owning schemas must enforce their own sign, range and representation
rules. No floating-point conversion is used for exact integers.

## Launcher and flake wiring

`writeShellScriptBin` generates `totipo-java-tool`: one `exec` of the exact JDK
Java executable, explicit immutable main-classes/Jackson classpath, fixed main
class and quoted `"$@"`. No dynamic resolution, network operation, Gradle or
Python interpreter is invoked. The wrapper has no publication/release policy.
`smoke` performs deterministic valid/malformed/duplicate/trailing-value checks;
unknown commands and wrong argument counts return status 2.

On the existing supported qualification system (`x86_64-linux`):

- `checks.java` and `checks.wrapper` retain their existing implementation.
- `checks.tooling` requires the checked main derivation (parser runner, smoke and
  negative compilation), then executes the generated launcher, verifies smoke
  success and status 2 for unknown/extra arguments, including an empty argument.
- `apps.tooling` exposes `nix run .#tooling -- smoke`.

No extra CI job or protected workflow change. Existing ordinary CI's
`nix flake check` will include the new check. No redundant developer shell
interface was added. Launcher execution is qualified by the owner-reported
Nix check; it is not claimed as agent-executed host evidence.

The eventual release controller must run outside Nix build derivations using a
built immutable executable. Credentials/signing secrets must never be derivation
arguments or embedded in Nix store outputs. J1 contains no credential-using code
and does not qualify any real release operation. Existing Python workflows retain
all responsibilities; no Python file is removed or changed.

## Source membership and qualification inputs

The flake's Git source uses working-tree bytes for tracked/intent-to-add paths.
The new derivation further restricts its source with `lib.fileset.toSource`, root
`tooling/`, selecting only `.java` files beneath `tooling/src`. It does not copy
the repository root, production modules, caches, logs or secrets.
`nix/tooling.nix` is an imported flake input; catalog/cache are explicit reads.
All intended new milestone files, including this report, are intent-to-add only.
No actual file content is staged.

`qualification.nix` is byte-identical. Its existing `nix/` tree selection means
the new Nix expression is also included in the existing Java derivation's source;
this is an input change, without changing its filter, tasks or unsigned output
semantics. `tooling/` is not added to that derivation. `review/` is excluded by
both source selections, so this report can be finalized after the human gate.

Freeze hashes/modes and final Git inventories are recorded below. No included
input may change after freeze without invalidating human qualification.

## Agent-executed validation

All host checks passed:

1. Pin/catalog/lock/verification/cache/canonical-byte audit (`pin-audit.txt`).
2. `./gradlew test`: 27s, 13 tasks, 2 executed/11 up-to-date; both module tests
   up-to-date (`baseline-test.log`). Immediate baseline XML accounting:
   405 tests, zero failures/errors/skips; 92/92 cases, snapshot 3/3, profile 1/1
   (`baseline-accounting.log`). No focused Gradle run overwrote baseline XML.
3. Direct host Java compile/test (`host-tooling.sh`, `host-tooling.log`), smoke,
   status-2 command checks and expected negative import (`forbidden.log`).
4. `./gradlew test build javadoc verifyPublication consumerSmoke`: 13s,
   27 outer tasks, 8 executed/19 up-to-date; both module test tasks up-to-date.
   Nested module consumer: 11s, 5/5 tasks executed (`final-normal.log`).
5. Immediate `python3 -B publishing/release.py tests`: 405 tests, zero
   failures/errors/skips; 92/92 cases, snapshot 3/3, profile 1/1
   (`final-accounting.log`). XML and accepted manifest preserved in
   `normal-evidence/` before any further consumer work.
6. `python3 -B -m unittest discover -s publishing -p 'test_*.py'`:
   51 tests, OK (`python-guardrails.log`).
7. `python3 -B publishing/publication.py check`: all 10 accepted artifacts'
   fresh-byte hashes match (`manifest-check.log`).
8. `./gradlew -p publishing/consumer-smoke --offline -PpomOnly clean check`:
   5s, 5/5 tasks executed (`pom-consumer.log`).
9. `git diff --check`, protected hash/mode audit and unstaged-content checks.

Existing publication verification confirms exact library contents, unchanged
production dependency sets and Java 17 bytecode. Both consumers resolve only
core/storage at compile time and BC 1.86 additionally at runtime; no tooling or
Jackson enters production artifacts. Existing strict verification is intact.
Manual source formatting follows existing four-space Java style; compilation
uses all lint warnings as errors. No formatter tool was available on the host.

No separate host forced-offline full suite: Gradle dependency acquisition,
compilation, publication resolution and fixed cache are unchanged. The existing
isolated consumer passes remain required; human Nix supplies fixed/offline proof.
No routine release ten-artifact reproducibility comparison was performed.

## Invocation accounting

| Category | Count / result |
| --- | --- |
| Broad baseline Gradle tests | 1 PASS, test tasks up-to-date |
| Baseline XML accounting | 1 PASS |
| Focused Gradle tests | 0 |
| Focused Python tests | 0, helpers unchanged |
| Standalone host positive compilation | 2 main + 2 test javac invocations PASS |
| Standalone parser runner | 2 PASS: initial 41, final expanded 43 assertions |
| Host smoke / negative compile | 1 PASS each; negative failure is expected |
| Host invalid-command probes | 3 expected status-2 results |
| Final normal full Java gate | 1 PASS |
| Final full XML accounting | 1 PASS |
| Full Python guardrails | 1 PASS, 51 tests |
| Publication verification / module consumer | 1 each, nested in final gate |
| Manifest byte check / POM-only consumer | 1 each PASS |
| Separate host forced-offline full passes | 0, justification above |
| Human cache updates | 0, no dependency changes |
| Agent Nix invocations | 0, prohibited |
| Human J1 Nix invocations | 1 human-reported PASS |
| Release reproducibility/sign/publish/tag/push/commit | 0 |

No unexpected failed build/test attempts or qualification invalidations so far.
Expected negative compilation and invalid-command probes are successful tests.
Up-to-date tasks and nested consumers do not constitute extra full-suite runs.

## Human qualification and remaining work

**PASS — human-reported:** owner reported `nix flake check passed` after the
final input freeze. One human J1 gate invocation is reported. No detailed log,
duration or Nix task/cache execution counts were supplied; none are inferred.
This qualifies the existing Java and wrapper checks and new tooling check,
including fixed-output Jackson acquisition, standalone compilation/parser and
negative tests, and generated-launcher probes. The agent did not execute Nix.

**PASS — agent verification after the owner report:** all **295** frozen
non-review file hashes and modes still match `final-qualification-inputs.json`.
Branch and HEAD remain the starting clean-main revision, and actual staged
content is empty. The six-file changed inventory and intent-to-add membership
remain unchanged. Only this excluded review report was finalized after the gate;
no included input changed or invalidated qualification.

The optional separate `nix run .#tooling -- smoke` manual demonstration was not
reported and is not required in addition to the check's launcher probes. No
second build, dependency-cache updater or release reproducibility gate ran.

J2/J3/J4 migration work is deferred: no independent Java publication verifier,
release controller, signing, retry, remote verification or protected-job Nix
migration. Python remains authoritative. Bootstrap success alone cannot establish
safety of a future release process.

## Protected invariants, changed inventory and final freeze

Final machine-generated evidence follows.

**PASS** — 219 protected files byte/mode identical to starting HEAD.
Only modified pre-existing file: `flake.nix`.
All other pre-existing tracked files likewise retain baseline hashes/modes.
Protected workflows, protocol v1/r19, 92 conformance fixtures, BC 1.86,
Java 17 library target, JDK baseline, Gradle wrapper and unsigned artifact semantics
remain unchanged. No library/protocol version bump.

Freeze inventory: `/tmp/totipo-j1-Q9RFES/final-qualification-inputs.json`; **295** files, SHA-256
`5a9a8b89bc322db179ed5283211eedaf4b3d993556b87e18510ba14711e57fad`. This conservatively freezes
all Git-known non-review regular files, a superset of included qualification inputs.
`baseline-files.json` holds starting hashes/modes.

Changed included input hashes/modes:

| File | SHA-256 | Mode |
| --- | --- | --- |
| `flake.nix` | `696a241770307b2e97a3068816b01c1a04ae37a03b1b17e5f09143bf43026167` | `0o644` |
| `nix/tooling.nix` | `6de824a6bf7be8c32d699737cdccc624a122329f988bc3f8aa201ae0a163936c` | `0o644` |
| `tooling/src/main/java/org/totipo/tooling/Main.java` | `9c2d7a285dc237c440050c5a6a0f5abd1bc4182993cea6098d10de6bcab4194f` | `0o644` |
| `tooling/src/main/java/org/totipo/tooling/StrictJson.java` | `162c3d309c12ab877d42a0e5eb6e35be8da89f555d5c5a602f807a29c323a8f9` | `0o644` |
| `tooling/src/test/java/org/totipo/tooling/StrictJsonTest.java` | `ddec656b91e07c7d3a88cde352a8a91452d63a84ee8f781ee80f6e470ea5e00e` | `0o644` |

Full changed-file inventory (`git diff --name-only`):

```text
flake.nix
nix/tooling.nix
review/J1_STANDALONE_JAVA_TOOLING_BOOTSTRAP_REPORT.md
tooling/src/main/java/org/totipo/tooling/Main.java
tooling/src/main/java/org/totipo/tooling/StrictJson.java
tooling/src/test/java/org/totipo/tooling/StrictJsonTest.java
```

`git status --short`:

```text
 M flake.nix
 A nix/tooling.nix
 A review/J1_STANDALONE_JAVA_TOOLING_BOOTSTRAP_REPORT.md
 A tooling/src/main/java/org/totipo/tooling/Main.java
 A tooling/src/main/java/org/totipo/tooling/StrictJson.java
 A tooling/src/test/java/org/totipo/tooling/StrictJsonTest.java
```

`git diff --cached --name-only`: empty. `git diff --cached --quiet` and
`git diff --check` pass. New-file intent-to-add entries remain for human review;
no actual content is staged. Branch/HEAD remain the starting main revision.
Only this excluded report may be finalized after the gate without invalidating it.

A report-generation pass introduced one trailing space; `git diff --check`
reported it once, then passed after the report-only correction. No included
qualification input changed and no compilation/test rerun was needed.

# JAVA_ANDROID_API30_COMPAT — Shared runtime compatibility

Status: implementation, full normal Java qualification, publication and independent
consumer checks PASS. Human-reported `nix flake check` PASS. No Android runtime or
physical-device qualification is claimed. All changes remain uncommitted.

## Scope and starting evidence

Started on clean `main` at `27cb53a1a5f2cc3d5f221ef790f694b26b90f8a8`, the released
Java 0.3.1 source commit. Classifications: CORE_RUNTIME, STORAGE_NIO, PUBLIC_API
(observable identifier/validation contracts), CONSUMER_BOUNDARY and DOCS_JAVADOC
(internal source documentation/report). Application/SPI signatures are unchanged;
only two explicitly internal implementation bridges are added. No dependency,
build, version, protocol, release-tooling or release-preparation change.

Read AGENTS.md, API_DESIGN.md including operation/state-snapshot semantics,
SPI_DESIGN.md, SPEC_PIN.md, docs/BACKUP_ARCHIVE.md, Java backup R1/R2/R3 reports,
docs/DEVELOPMENT.md, RELEASE_CHECKLIST.md, qualification.nix, flake.nix and
nix/tooling.nix. The existing r19 pin, corpus, locks, verification, cache and
consumer isolation remain unchanged.

Read Android's [R4A export findings](https://github.com/totipo-org/totipo-android/blob/569986718d2f4eeb55d8d381cb42ea37a7404903/review/BACKUP_RESTORE_R4A_ANDROID_EXPORT_REPORT.md),
[R4B restore findings](https://github.com/totipo-org/totipo-android/blob/569986718d2f4eeb55d8d381cb42ea37a7404903/review/BACKUP_RESTORE_R4B_ANDROID_RESTORE_REPORT.md)
and Gradle configuration at Android HEAD
`569986718d2f4eeb55d8d381cb42ea37a7404903` over read-only HTTPS. Android remains
untouched. Its minSdk is 30, Java target is 17, AGP is 9.4.0, and it configures
**no core-library desugaring flag or desugar_jdk_libs dependency**. Language
lowering and D8 method backports are distinct from that optional library feature.

Inspected both published Maven Central 0.3.1 source JARs. Every Java source entry
matches starting HEAD byte for byte (96 core, 13 NIO). Source JAR SHA-256:

- core: `d335377024d66d43c9a6e0a40dfec7deec7f8c6d91a56e0285c8012763249257`
- NIO: `da6dfc3f058d38847d86e55ff3301a057317e1eb96f45945bda88ee9af2b6298`

Inspection downloads, temporary audit programs, compiled tooling, logs and XML
are ignored under `.gradle/api30-inspection/`; none are source members.

## Complete production API findings within the bounded audit

The audit covers all compiled production classes in core and storage-nio, without
restricting to a chosen entry point. Constant-pool class, method and field references
and descriptors were checked against AOSP's API 30 public android.jar, resolving
inherited members, including inherited calls whose bytecode owner is a Totipo
subclass. This specifically found TlvWriter.Buffer.writeBytes, which a simple
search for owners beginning `java/` would miss. JDK-only tests and standalone
release/publication tooling are outside the shipped runtime.

| Production API absent on API 30 | Platform introduction | Handling |
| --- | --- | --- |
| `HexFormat.of/formatHex/parseHex` | API 34 | Replace with one shared internal ASCII hex implementation. |
| `Arrays.compareUnsigned(byte[], byte[])` | API 33 | Token IDs use a fixed-32-byte unsigned comparison; object parents reuse the existing canonical hex comparator. Actual app-version D8 leaves this call unresolved. |
| `Stream.toList()` | API 34 | Use Java 8 collectingAndThen(toList(), Collections::unmodifiableList), retaining encounter order, null acceptance and immutability. |
| `InputStream.readNBytes(int)` (including ZipInputStream inheritance) | API 33 | Shared internal bounded read loop used by ZIP inspection and NIO reads. |
| `OutputStream.nullOutputStream()` | API 33 | Private bulk-aware discard sink only for backup digest calculation; it never escapes or receives post-close writes. |
| `ByteArrayOutputStream.writeBytes(byte[])` via TlvWriter.Buffer | API 33 | Equivalent write(value, 0, value.length); reserve/wipe behavior stays intact. |
| `ZipEntry.setTimeLocal(LocalDateTime)` | API 35 | setTime with the instant corresponding to the frozen local 1980-01-02 midnight; golden-byte checks cover all JDK timezone IDs. This also fixes a gap on API 34. |
| `List/Set/Map.copyOf` | API 31 | Retained: verified D8 backports on minSdk 30. |
| `Optional.isEmpty`, no-argument `orElseThrow`, `stream` | API 33 | Retained: verified D8 backports on minSdk 30. |
| Record base class/ObjectMethods and lambda/string-concat bootstrap references | Language lowering, not directly callable API 30 library methods | Verified removal/lowering by app-version D8. No Android code added to Java. |

API-level references: [HexFormat](https://developer.android.com/reference/java/util/HexFormat),
[Arrays](https://developer.android.com/reference/java/util/Arrays),
[Stream](https://developer.android.com/reference/java/util/stream/Stream),
[InputStream](https://developer.android.com/reference/java/io/InputStream),
[OutputStream](https://developer.android.com/reference/java/io/OutputStream),
[ByteArrayOutputStream](https://developer.android.com/reference/java/io/ByteArrayOutputStream),
[ZipEntry](https://developer.android.com/reference/java/util/zip/ZipEntry),
[List](https://developer.android.com/reference/java/util/List),
[Optional](https://developer.android.com/reference/java/util/Optional).
[Android desugaring configuration](https://developer.android.com/studio/write/java8-support)
requires opt-in library configuration; none is assumed here.

Other production APIs actually exist on API 30: collection `of` factories,
java.time, streams/collectors used by the replacement, Objects range checks,
Flow, ForkJoinPool, standard NIO Files/Path/channels/attributes, charset codecs,
SHA-256/HMAC/AES-GCM API types and ZIP constructors with Charset. Availability of
a signature does not prove provider algorithms, filesystem capabilities, ZIP
implementation details or device behavior. No additional unresolved platform API
was found after the changes and D8 lowering in the bounded check below.

### Bounded D8/API 30 check

Used AOSP's [API 30 public android.jar](https://android.googlesource.com/platform/prebuilts/sdk/+/refs/heads/main/30/public/android.jar):
SHA-256 `2f0e6e4ec44e95beefe0c041541afcec4f55f8b964b33599a987efb6e972f990`.
Used the [AGP 9.4.0 builder JAR](https://dl.google.com/dl/android/maven2/com/android/tools/build/builder/9.4.0/builder-9.4.0.jar),
SHA-256 `dcc7126ce9077ab8113bc29518e68e42b05ab1c9ed053b9f97f46e257396485f`,
which embeds **D8 9.4.14**, build `b1e20dd52788b7a226f83e24d49d0a53108bc84a`.
These inspection tools are not new project dependencies or build inputs.

Combined the two modules' compiled main classes into a temporary JAR, then ran:

```sh
java -cp builder.jar com.android.tools.r8.D8 --min-api 30 \
  --lib android-30.jar --classpath bcprov-jdk18on-1.86.jar \
  --output dex runtime.jar
```

No core-library desugaring configuration was supplied. D8 completed without
warnings. A temporary DEX table reader checks platform type and method/field
references against the API 30 classfile member tables, including superclass and
interface lookup. Initial post-fix DEX check exposed the remaining unsigned-array
comparison; the focused correction was made before final Java qualification.
The final DEX/API audit has zero missing references. The 208 compiled production classes produce 504 DEX types and 352 unique
standard-library method/field table references, all resolving on API 30. Before
D8 there are still ten unavailable member signatures (six collection/Optional
backports and four compiler bootstrap/base-class references); those disappear
through the verified lowering/backports rather than being assumed native.

This is a bounded static availability check of Totipo's production code and
D8-generated support, not an APK build or Android execution. BC is supplied as a
classpath dependency, not audited/executed as a full DEX program. Native/provider
behavior, reflective loading and Android-specific implementations remain outside
this proof. No production reflection-based platform fallback was introduced.

## Implementation and equivalence

`org.totipo.internal.Hex` supplies lowercase formatting and case-insensitive,
strict ASCII parsing. All shipped HexFormat uses previously formatted lowercase;
there was no uppercase-configured production formatter to migrate. Uppercase
and mixed-case input is still understood by the internal decoder, while public
TokenId/RevisionId and object names still reject uppercase through their original
canonical validators. VaultId, keyed object IDs, manifest hex and whole-ZIP hashes
remain byte-identical. No Unicode digit acceptance, normalization or separators.

Hex tests compare directly with the JDK oracle for every byte, empty and boundary
lengths, lowercase/uppercase encodings, all 65,536 UTF-16 code units in both digit
positions, odd lengths, malformed surrogates, separators, whitespace and null.
Odd length remains IllegalArgumentException, invalid even-length digits remain
NumberFormatException, and null remains NullPointerException. Decoder exception
messages are internal and not preserved verbatim; external canonical validation
still occurs first and retains its existing messages and exception types.
IdentifierCompatibilityTest fixes literal VaultId/object/token encodings and
round trips, rejects uppercase/non-ASCII canonical names, and compares token and
parent order with the JDK unsigned-array oracle at every byte position/value.

`BoundedInput` is the only shared read helper. It accumulates at most the requested
number of bytes, never consumes the following lookahead byte or closes the borrowed
stream, and uses an at-most-8192-byte scratch buffer. Huge requested bounds on tiny
files do not allocate that bound up front. Tests compare short reads, EOF, zero,
chunk edges and Integer.MAX_VALUE with the JDK; zero-progress fallback, IOException
identity, negative bounds and borrowed ownership are exercised. NIO still separately
performs its existing lookahead, exact-name/no-follow checks and outcome mapping.
The helper handles ciphertext/ZIP bytes; plaintext TLV reserve/wipe is unchanged.

Collection substitutions retain immutable lists with encounter order and null
acceptance, without changing operation admission, state validity, session ownership,
publication, retries or any storage mechanism. There is no compatibility framework,
new crypto, storage redesign or Android-specific source.

The ZIP writer still uses JDK archive assembly and profile 1. Timestamp selection
uses the default timezone only to obtain the instant that setTime converts back to
the fixed DOS local time. Tests build each frozen fixture in every available JDK
timezone and compare every archive byte and digest, restoring the default afterward.
Actual Android timezone/ZIP implementations still need device qualification.

### Explicit HexFormat production-reference audit

```sh
rg -n 'HexFormat' core/src/main storage-nio/src/main
```

PASS: zero matches. Classfile and post-D8 checks likewise contain no production
java/util/HexFormat class or method reference. Replacements cover VaultId.hex,
ObjectId.fromFilename/filename, ApplicationSession public/internal TokenId conversions,
and all three BackupZip digest conversions. JDK oracle tests/conformance loaders
and standalone JDK 25 release/publication tooling intentionally retain HexFormat;
they are not in the Android runtime. The other unsupported APIs listed as replaced
are also absent from production call sites.

## Protocol, backup regression and qualification

Baseline `gradle test`: PASS, 21s, 13 actionable tasks (2 executed, 11 up-to-date).
Both module tests were up-to-date. Independently accounted the existing full XML
before focused tests: **534 tests, zero failures/errors/skips**, snapshot 3/profile 1,
92/92 portable cases with exact-set execution assertion. Baseline XML is preserved
outside build outputs under the ignored inspection directory.

Focused invocation 1: PASS, 9s, internal hex/read tests, VaultId and backup goldens.
Focused invocation 2: PASS, 5s, internal tests plus identifier, token codec/fold/graph
and backup golden regressions after replacing unsigned-array comparisons. No failed
Gradle/test attempt. The expected failing intermediate API audit is diagnostic
evidence, not a claimed compatibility pass. One initial launcher invocation failed
before Java execution because this Nix environment lacks /usr/bin/env; corrected
the ignored launcher shebang, then baseline accounting passed. Tooling main sources
were compiled once directly by pinned JDK 25 with only pinned Jackson Core 2.21.7,
independently of Gradle. No tooling source was changed.

Final normal gate: **PASS**, 56s, 27 actionable tasks (16 executed, 11 up-to-date):

```sh
gradle test build javadoc verifyPublication consumerSmoke
"$TOTIPO_PUBLICATION_TOOL" release test-results "$PWD"
"$TOTIPO_PUBLICATION_TOOL" publication check "$PWD" "$PWD/build/repository" "$PWD/build/publication-manifest.json"
gradle -p publishing/consumer-smoke --offline -PpomOnly clean check
```

Both module test tasks executed. Immediate standalone full-suite accounting:
**542 JUnit tests, zero failures/errors/skips**, snapshot 3, profile 1, and 92/92
portable cases with the exact-set execution assertion. Java 17 bytecode checks
passed. Javadoc completed with nonfatal missing-documentation warnings (100 shown).
verifyPublication semantically accepted all ten artifacts once; the standalone
manifest recheck reread and accepted 10/10 current artifacts. Module-metadata
consumerSmoke ran its existing isolated forced-offline consumer (13s, four tasks
executed). Separate POM-only consumer passed (5s, four tasks executed). These
consume the local 0.3.1 staging coordinates; nothing was uploaded or version-bumped.

Full XML and manifest are preserved in `.gradle/api30-inspection/final-xml/` and
`final-publication-manifest.json`. Full-suite evidence includes BackupArchiveTest
8, BackupZipAdversarialTest 28, BackupGoldenTest 6 (including all 632 timezone IDs
for each of the three unchanged fixtures), HistoryImportTest 36,
NewLocationRestoreTest 4 and NioRestoreTest 39, all successful. Thus export,
ZIP inspection, additive import, detached restore and real NIO restoration all
ran in addition to protocol/core/NIO regressions. Fixture tests authenticate
objects and assert fixed VaultId, archive digests and exact writer bytes.

| Invocation category | Count and result |
| --- | --- |
| Small broad baseline Gradle | 1 PASS; module test tasks up-to-date, not newly executed. |
| Focused Gradle test invocations | 2 PASS; core test task executed both times. |
| Compile-only Gradle invocations | 0; compilation was part of selected tests/gates. |
| Final full Java Gradle gate | 1 PASS; core and NIO test tasks executed. |
| Full-suite standalone accounting | 2 PASS: baseline 534, final 542. One earlier launcher start failed before Java execution. |
| Publication semantic acceptance | 1 PASS, nested verifyPublication; consumerSmoke's dependency does not add another execution. |
| Standalone current-byte manifest check | 1 PASS, 10/10 artifacts. |
| Module-metadata consumer | 1 PASS, nested consumerSmoke. |
| Separate POM-only consumer | 1 PASS. |
| Separate host forced-offline full suites | 0; no dependency/build/resolution inputs changed. Consumer-only offline checks above are distinct. |
| Focused/full standalone tooling test runners | 0; tooling unchanged. Main tooling compiled independently once as launcher preparation. |
| Agent/human Nix invocations | 0 / 1; final human-reported `nix flake check` PASS. |
| Release reproducibility, signing, tags, publishing, commits | 0; outside routine milestone scope. |

No final Java gate was invalidated or rerun. The final static audit runs twice
after the initial API inventory: the first correctly rejected an unresolved
Arrays.compareUnsigned call; the corrected audit passed. No failing test was
suppressed. Only this excluded report is finalized after Java qualification.


Frozen fixtures remain unchanged:

| Fixture | SHA-256 |
| --- | --- |
| empty.zip | `603eb0dd84f67678bab82c78ee684dc22592b5e4de37f24d16f5694c382fa425` |
| history.zip | `369b77c872a32f2474d867e0653e39b66befdfc6cbd1244da486505ba8bca95b` |
| missing-parent.zip | `6991ee0b764409728a10ee6612d6eced56207dbefa58a35611ea3d6112c25e58` |

## Freeze, human gate and release boundary

Inspected qualification.nix's source filter and flake.nix inputs. Java source/tests
and all required production inputs are included; review/ is excluded. Every new
milestone source/test/report is Git-known using intent-to-add only, established
before final Java qualification and the human gate. No file contents are staged. Caches, inspection tools and generated
outputs remain ignored. Included inputs are frozen before requesting the one human
`nix flake check`; no subsequent included-input edits are permitted without renewing
qualification. No cache update is needed because dependency inputs are unchanged.

Included inputs are now frozen. Their Git diff (all changes except excluded
review/**) has SHA-256:
`c9dc613b63f2949ef71e980b2101b5e8ce232132dffdf10a012d653c26672622`.
The diff hash matches before and after report finalization. qualification.nix,
Nix helpers, flake.lock, package-deps, Gradle configuration/locks/verification,
publishing consumer sources, protocol corpus and fixtures are unchanged.

**Completed human gate:** on 2026-11-16 the user reported `nix flake check` PASS.
This is human-reported evidence, not agent execution or an inference from
Gradle/static results. Starting HEAD and the included-input diff hash above were
rechecked and still match the qualification freeze. Only this excluded report
was edited to record the result; no Java or Nix rerun is required. Agent Nix
invocations remain zero; human invocations total one. A later included-input
edit invalidates this freeze and requires the affected Java gate and human Nix
gate again.


With repository qualification complete, recommend a separately authorized **Java 0.3.2 patch release**
with both modules kept aligned. This milestone does not bump versions, sign, tag,
publish or authorize release. Android must then consume the published artifacts
through its normal lock/hash/provenance/cache review and qualify API 30–33 plus a
current supported device. Do not claim shipment readiness from JVM/static results.

Remaining physical acceptance: normal create/open/observation/edit/TOTP and encrypted
object validation; real export/import/new-location restore with all three fixtures;
exact output bytes, DOS timestamp/extra fields/UTF-8 flags/CRC and ZIP digests on
Android; provider crypto/Argon2 resource behavior; NIO no-follow/create-only and
file/directory-force semantics; actual SAF read/write/close/readback, cancellation,
Lock/background/recreation/process death and partial/uncertain recovery. Test
release-packaged D8 output as well as debug. Existing product physical procedures
remain the appropriate matrix. No fundamental unreplaceable API blocker was found
within the bounded shared-production reference audit.

## Exact final Git state

Branch `main`; HEAD remains `27cb53a1a5f2cc3d5f221ef790f694b26b90f8a8`.
All 17 paths below are uncommitted. New paths retain intent-to-add only.
`git diff --cached --quiet`: PASS. `git diff --check`: PASS.
Ignored inspection/build/cache state remains outside source membership.

`git status --short`:

```text
 M core/src/main/java/org/totipo/VaultId.java
 M core/src/main/java/org/totipo/format/ApplicationSession.java
 M core/src/main/java/org/totipo/format/BackupZip.java
 M core/src/main/java/org/totipo/format/ObjectId.java
 M core/src/main/java/org/totipo/format/TlvWriter.java
 M core/src/main/java/org/totipo/format/TokenGraph.java
 M core/src/main/java/org/totipo/format/TokenId.java
 M core/src/main/java/org/totipo/format/TokenObject.java
 A core/src/main/java/org/totipo/internal/BoundedInput.java
 A core/src/main/java/org/totipo/internal/Hex.java
 M core/src/test/java/org/totipo/format/BackupGoldenTest.java
 A core/src/test/java/org/totipo/format/IdentifierCompatibilityTest.java
 A core/src/test/java/org/totipo/internal/BoundedInputTest.java
 A core/src/test/java/org/totipo/internal/HexTest.java
 A review/JAVA_ANDROID_API30_COMPAT_REPORT.md
 M storage-nio/src/main/java/org/totipo/storage/nio/NioFiles.java
 M storage-nio/src/main/java/org/totipo/storage/nio/NioReads.java
```

`git diff --name-only`:

```text
core/src/main/java/org/totipo/VaultId.java
core/src/main/java/org/totipo/format/ApplicationSession.java
core/src/main/java/org/totipo/format/BackupZip.java
core/src/main/java/org/totipo/format/ObjectId.java
core/src/main/java/org/totipo/format/TlvWriter.java
core/src/main/java/org/totipo/format/TokenGraph.java
core/src/main/java/org/totipo/format/TokenId.java
core/src/main/java/org/totipo/format/TokenObject.java
core/src/main/java/org/totipo/internal/BoundedInput.java
core/src/main/java/org/totipo/internal/Hex.java
core/src/test/java/org/totipo/format/BackupGoldenTest.java
core/src/test/java/org/totipo/format/IdentifierCompatibilityTest.java
core/src/test/java/org/totipo/internal/BoundedInputTest.java
core/src/test/java/org/totipo/internal/HexTest.java
review/JAVA_ANDROID_API30_COMPAT_REPORT.md
storage-nio/src/main/java/org/totipo/storage/nio/NioFiles.java
storage-nio/src/main/java/org/totipo/storage/nio/NioReads.java
```

`git diff --cached --name-only`:

```text
```

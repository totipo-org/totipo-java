# J2B — Independent Java publication verification and manifest handling

Status: **PASS — J2B complete. Agent host qualification and fresh human `nix flake check` passed.**
The owner reported the Nix PASS against the frozen inputs; the post-gate hash/mode audit passes.
Python remains the authoritative live publication implementation. All changes are
uncommitted; no signing, remote publication, commit, tag, release or push occurred.

## Starting checkout and classifications

**PASS** — started on clean `main` at
`45c2fc55d9bc9f6896639071db8d6f5860309765` (`Hardening wrapper integrity`).
`git status --short` was empty. J2A.1 and all required J0/J1/J2A/J2A.1 reports
are committed. Read AGENTS.md completely, the four prerequisite reports, both
publication scripts, publication/release tests and controller, consumer build,
template and source, module/root builds, Nix definitions and protected workflow.
Work stayed in this checkout; temporary evidence is under
`/tmp/totipo-j2b-evidence/`, outside Git membership. No sibling checkout, isolated
worktree, sub-agent or Nix command was used.

Applicable repository classifications:

| Classification | Scope / focused iteration |
| --- | --- |
| PUBLICATION_METADATA | Maven metadata or packaged contents; `verifyPublication`. |
| CONSUMER_BOUNDARY | Independent consumption/resolution; `consumerSmoke` and affected POM-only checks. |
| RELEASE_TOOLING | Release helpers/guardrails; specific Python unit tests. |
| DEPENDENCY_BUILD | Dependency inputs, Gradle/build configuration or fixed cache; focused resolution/build work. Applies to standalone compilation/Nix wiring; no dependency change. |

Not release preparation, production runtime/API, protocol or source-Javadoc work.
The small relevant baseline was publication verification, rather than an
additional broad production test run; the full normal gate remains mandatory.

## Complete Python responsibility inventory

**PASS — contract inspected and retained.** `verify-publication.py`:

1. Reads VERSION and independently derives core/storage coordinates and five
   roles each; deletes stale authoritative evidence before semantic verification.
2. Requires ten primary unsigned artifacts. Allows only MD5/SHA-1/SHA-256/SHA-512
   sidecars of those artifacts, independently checks sidecar contents, checks both
   Maven indexes and their exact single-version inventory and optional checksums,
   and rejects extra repository files.
3. Verifies POM group/artifact/version, module title, nonempty description, URL,
   Apache license, developer and SCM fields; exactly BC 1.86/runtime for core and
   local core/current version/compile for storage. Rejects optional/classifier tags.
4. Parses Gradle module JSON; checks component identity/status, format 1.1,
   variant names, exact dependencies, forbidden constraints/capabilities/available-at,
   JVM 17, artifact name/URL/length and declared hashes, and project-path leakage.
5. Reads archives, rejects duplicate file entries, compares exact production class,
   source and generated Javadoc membership/bytes, requires archive manifest entries,
   Javadoc index and class header CAFEBABE/0/61. No tooling/test classes are accepted.
6. Only after semantic success calls `publication.accept`, serializes sorted,
   two-space-indented schema-1 JSON with a final newline, and generates independent
   consumer verification XML by inserting binary/POM/module hashes into the reviewed
   template. External BC pins and verification policy remain template-owned.

`publication.py` separately owns version syntax; canonical module/role/suffix/path
identities; exact manifest/record fields; schema/version/hash validation; duplicate
JSON rejection; parsing/loading; ten-artifact hash extraction; and complete staging
fingerprinting. Its internal `accept` constructs hashes after semantic acceptance.
`check_artifacts` rereads every artifact; `check_staging` additionally fingerprints
all staged files, including sidecars/indexes; `copy_accepted` checks staging, copies
only the ten artifacts, then rehashes destination bytes and checks destination
inventory. Its live CLI still exposes only `check` and `copy`.

**PASS — release-controller consumption unchanged.** `accepted_manifest` loads and
checks fresh staging; `inventory` extracts hashes; `compare_inventory` parses
preflight evidence and compares rebuilt hashes. Signing-output binding and remote
byte/signature verification consume that inventory. Workflow inventory transport
serializes the existing schema; release authorization, retry/history, credentials,
Central checks, signing and source tags remain Python responsibilities. J2B
qualifies none of those live release operations.

## Standalone architecture and compilation authority

**PASS** — J1 tooling extended with `publication/PublicationVerifier`, typed
`PublicationManifest`/`Artifact` records and role enum, `PublicationRepository`,
`PublicationCli`, and small package-private JSON/filesystem helpers. Manifest maps
exist only at the parsing/serialization boundary; downstream operations use typed
records. Serialized Gradle metadata is inspected as bounded JSON, never as Gradle
implementation objects. Expected names come from the reviewed module/role contract,
not candidate filenames, Gradle models or producer helpers.

JDK **25.0.4.1+1**, direct `javac --release 25 -encoding UTF-8 -Xlint:all -Werror
-proc:none`, explicit sourcepaths. Main compilation classpath is exactly the
reviewed **Jackson Core 2.21.7 JAR**; test compilation adds only standalone main
classes. No Databind, Annotations, Gradle API/implementation, production classes,
JUnit or new dependency. Host downloaded the existing canonical JAR and authenticated
SHA-256 `8c5623b98f32d5f7e1287ff61ed50e08ab7b11381e991fff89327faabba42261`
against the unchanged J1/cache pin before compilation. Nix retains its original
fixed-output dependency construction.

Final host commands: `frozen-host-tooling.sh`; output:
`frozen-host-tooling.log`. Runners and smoke pass with PATH pointing to an empty
directory and CLASSPATH empty, using the explicit JDK executable. Independent
negative compilation imports `org.totipo.RevisionId` and `org.gradle.api.Project`;
both fail with missing-package diagnostics (`forbidden.log`). Production code has
no subprocess, Python or network invocation. Sources/classes are read as files,
never loaded into the verifier's classpath.

CLI, all paths explicit:

```text
publication verify <root> <repository> <manifest-output> <consumer-metadata-output>
publication check <root> <repository> <manifest>
publication copy <root> <repository> <manifest> <destination>
```

Valid operations return 0; failures/invalid arguments return nonzero with operation
and module/artifact context. Empty arguments fail. `smoke` remains available.
The only production evidence-generation command executes semantic verification.
Hash-only acceptance and failure injection are package-private test seams.

## Semantic verification

**PASS — inventory.** VERSION is read from the specified root. Each module has
exactly binary, sources, Javadoc, POM and module artifacts. Actual artifact/sidecar
bytes and Maven index identity/version/checksum bytes are independently observed.
Only the reviewed four sidecar algorithms are allowed; sidecars are optional as in
Python. Python's Unicode `strip` whitespace semantics are explicitly preserved.
All staged file paths must belong to the independently derived inventory. Empty
directories do not contribute to the existing Python fingerprint/file inventory.

**PASS — XML/POM.** JDK namespace-aware DOM verifies all Python fields/dependencies.
The Python contract requires a nonempty description rather than exact description
prose; Java preserves that distinction. Leading element text preserves ElementTree
`findtext` semantics, including rejection of nested-element substitutions.
XML input acquisition is bounded to 64 KiB. Secure processing is enabled; DOCTYPE,
external general/parameter entities, external DTD loading, XInclude expansion and
external schema/DTD access are disabled. The resolver also rejects acquisition.
Malformed XML and file/network entity fixtures reject without resource access.
Rejecting otherwise harmless internal DTD declarations is deliberate tightening.

**PASS — Gradle metadata.** Exact component/status/format, exactly four unique
expected variant names, reviewed dependency declarations, Java 17 integer target,
artifact references, integer sizes and actual declared checksums are checked.
SHA-256 is required; every additional recognized declared checksum is checked.
Unexpected constraints/capabilities/available-at and project paths reject.
Metadata never supplies its own expected artifact identities.

**PASS — archives.** ZIPs are bounded snapshots, then checked through both the
central directory (`ZipFile`) and local headers/data (`ZipInputStream`). Duplicate
names (including directories), invalid/traversing/absolute/control-character names,
nonempty directory entries, CRC/length inconsistencies, corrupt/truncated archives
and local/central disagreement reject. Limits: 64 MiB compressed file, 10,000 ZIP
entries, 16 MiB per decompressed entry, 128 MiB total materialized archive content.
Expected filesystem inventories are bounded to 20,000 entries/128 MiB selected
bytes. Exact file sets/bytes are compared; all three archive manifests must be
`Manifest-Version: 1.0\r\n\r\n`. Production classes must match compiled bytes and
header 61.0; sources match checked-out Java files; docs match generated files and
include index.html. No byte comparison is weakened for the ZIP API.

## JSON profiles, manifest and Python-compatible serialization

**PASS** — strict duplicate detection, no syntax extensions, one complete document,
raw byte bounds, bounded depth/strings/names/numbers/tokens and exact BigInteger
interpretation remain. The original J1 bootstrap limits and **43 assertions** are
unchanged. Publication JSON explicitly requires UTF-8 without a BOM, preventing
Jackson auto-detection from accepting UTF-16/32 that Python's text reader rejects.

| Profile | Raw bytes | Depth | String | Name | Number | Tokens |
| --- | --- | --- | --- | --- | --- | --- |
| J1 bootstrap | 16,384 | 16 | 256 | 128 | 128 | 4,096 |
| Gradle module | 65,536 | 16 | 2,048 | 128 | 128 | 8,192 |
| Accepted manifest | 32,768 | 8 | 1,024 | 128 | 128 | 4,096 |

Observed real files: core module **3,669 B**, storage module **3,910 B**, manifest
**3,605 B**. Profiles leave bounded headroom without raising all bootstrap limits.
Permanent tests cover each profile's depth/string/name/number/token constraints,
malformed/trailing/duplicate input and oversized publication input.

**PASS — schema unchanged.** Exact root and record fields, schema integer 1,
current version, ten records, unique canonical paths/identities/roles and lowercase
64-digit SHA-256 are validated. Unknown fields, substitutions, wrong types,
missing/extra/duplicate records and invalid hashes reject. Swapped valid-looking
hashes reject during fresh-byte observation. Generated records retain Python's
module order core/storage and role order binary/sources/javadoc/pom/module.

**PASS — fingerprint byte compatibility.** Every staged file, index and sidecar
is freshly SHA-256 hashed. Repository-relative POSIX paths become keys in a sorted
JSON object. Dedicated serialization reproduces Python `json.dumps(sort_keys=True)`:
comma-space and colon-space separators; ASCII escaping, lowercase `\u` escapes,
UTF-16 surrogate escape pairs, unescaped forward slash, escaped backslash, quotes,
controls and Unicode code-point sorting (including BMP versus supplementary keys).
The serialized UTF-8 bytes are SHA-256 hashed. Empty repository is SHA-256 of `{}`:
`44136fa355b3678a1146ad16f7e8649e94fb4fc21fe77e8310c060f61caaff8a`.
Tests compare literal expected Java bytes with independently Python-generated bytes
and digest, plus actual Unicode/backslash/control-name file-tree fingerprints.
There is no new fingerprint format. Total repository observation is bounded to
256 MiB and 20,000 entries; individual files to 64 MiB.

**PASS — manifest byte compatibility.** Sorted keys, two-space indentation, record
order, integer rendering, ASCII escaping, UTF-8 and final newline reproduce Python
exactly for accepted inputs. Real and positive synthetic output comparisons use
`cmp`/independent Python parsing, rather than Java roundtrip alone.

## Fresh staging, copying, evidence ownership and consumer XML

**PASS** — check parses the manifest, rehashes all ten artifacts and recomputes the
complete staging fingerprint. Verification observes a fingerprint before semantic
inspection and again during acceptance, then rechecks staging before emitting
manifest evidence. No cached producer hashes replace fresh reads.

Copy revalidates even caller-constructed records, checks staging immediately before
copy, requires a disjoint empty destination, copies exactly ten files, checks each
source snapshot against its accepted hash, rereads destination bytes, validates
exact destination file inventory, and rechecks source staging. Existing destination
files are deliberately rejected rather than overwritten. Partial failed copies
may remain for inspection but never return success or acquire an accepted manifest.

All source/destination byte reads/writes use `SecureDirectoryStream` and NOFOLLOW
for every path component, with create-new writes. Symlinks and nonregular inputs
reject. Missing destination directories are created privately in the trusted JVM
temporary directory and moved through anchored directory handles; Java has no
mkdirat API, and path-based mkdir would leave a symlink-swap window. This avoids
following mutable destination parents. Unsupported secure filesystem providers
fail closed. Moving newly created directories requires the JVM temporary directory
and destination to share a filesystem; on other layouts the caller can provide
precreated empty directories or a trusted temporary directory on that filesystem.
This portability limit is retained as a residual constraint, not silently worked
around with unsafe filesystem writes.

**PASS — evidence ownership.** Shadow outputs must be explicit paths outside the
input checkout/repository and distinct from each other. Stale evidence is removed
before VERSION/semantic checks. Metadata is written first; fresh staging is checked
again; manifest is the final atomic rename of a complete temporary file. Exceptions
clean up both outputs. Permanent tests cover stale evidence after semantic/VERSION
failure, output failure, injected failure after metadata write, and temp cleanup.
Python-authoritative output files are never overwritten by normal Java verification.

**PASS — consumer metadata.** The reviewed template is read separately; exactly
one insertion marker is required. Only the core/storage binary/POM/module records
are inserted, with actual reviewed staging hashes and the existing origin text.
Template BC pins, metadata verification and repository isolation stay unchanged.
Real generated XML is byte-identical to Python; both independent Gradle consumers
exercise those same bytes. No trusted-key exemption, remote substitution, composite
build or source substitution is introduced.

## Differential and permanent negative evidence

**PASS** — final standalone suite: **132 assertions**, **64 exported semantic
fixtures**. It runs without Python/Gradle/production dependencies. Synthetic expected
POM/module contracts are independently written; hashes use JDK MessageDigest in
the test rather than verifier digest helpers. Archive mutations refresh metadata
assertions so semantic archive checks are reached. The independent Python oracle
then reads the identical exported fixture bytes. Existing Python sources are copied
unchanged into disposable fixture roots; live staging is never mutated.

Final differential suite: **90 cases; 11 deliberate tightenings; zero unexplained
acceptance drift** (`frozen-parity.log`, `frozen-fixtures/parity-results.json`).
Includes repository inventory/checksums/index versions; POM coordinates/dependencies,
scopes/extra dependencies/optional/classifier/license/SCM/malformed/nested/entity
inputs; module identity/format/variant/dependency/Java/size/hash/constraints/capabilities/
available-at/project paths/duplicate/truncated/oversized/encoding inputs; missing,
extra, changed and wrong-version classes, source and Javadoc mutations; corrupt,
truncated, duplicate, invalid/excessive and local-header-mismatched archives.
Manifest cases cover schema/types/version/unknown/duplicate fields, count/path/role/
coordinate/hash substitutions and swapped hashes. Staging cases alter artifact,
index, sidecar and extra-file bytes. Copy cases verify exact output bytes, corrupt
copies, extra files, symlinks and repeated destination use. Permanent failure tests
also cover partial/stale evidence and parser resource limits.

Measured intentional Python ACCEPT / Java REJECT cases:

1. Harmless internal DTD declaration: no DTD is needed by these publications.
2. Duplicate module JSON member: prevents ambiguous producer metadata.
3. Oversized module JSON: explicit resource profile.
4. Fifth duplicate variant: requires exactly four unique expected variants.
5. Missing all module SHA-256 declarations: requires actual artifact hash binding.
6. Changed archive manifest: exact expected archive manifest, rather than mere presence.
7. Integral floating Java target `17.0`: exact integer token required.
8. Integral floating artifact size: exact integer token required.
9. Unused NaN metadata value: strict standard JSON, preserving J1 policy.
10. Boolean capabilities value: rejects malformed field types even when Python-falsey.
11. Copy into already populated accepted destination: no overwriting existing files.

Additional defensive restrictions are explicit and permanently tested: XML/JSON/ZIP/
filesystem bounds, unsafe ZIP names, symlinks, nonregular files, secure-provider
availability and atomic output ownership. Falsey scalar prohibited-metadata fields
are rejected as a type family. Python's optional sidecars, nonempty description,
empty prohibited collections, and ordinary whitespace remain accepted semantics.
No existing Python check was modified or deleted to obtain parity.

## Real-artifact and Nix shadow qualification

**PASS — agent host.** Initial normally produced staging was accepted by Python,
then independently by Java without regeneration between inspections. Manifest and
consumer metadata were byte-identical. Final frozen normal gate supplies fresh
Python evidence; Java independently verifies the same complete staging, compares
both files byte-for-byte, checks staging again and copies ten artifacts into a
separate temporary destination. Independent Python rereads the Java copy and compares
its exact artifact inventory/hashes. Initial/final ordinary artifact hash inventories
also match; timestamped repository indexes are intentionally outside that artifact
invariance comparison. This is not release normal/offline reproducibility.

**PASS — agent-inspected Nix wiring and owner-reported human execution.** `nix/tooling.nix`
retains JDK25, fixed-output Jackson, direct javac, restricted classpath, J1 parser
runner/smoke and launcher checks, and adds PublicationTest plus Gradle negative import
coverage. Its existing Java-only fileset under `tooling/src` includes all new main
and test Java files. The standalone tests need no production build.

`flake.nix` supplies `tooling.launcher` to `qualification.nix`. After Python has
accepted the real Gradle staging and the fresh Python check, qualification invokes
that prebuilt Java executable with temporary external output paths, compares the
entire manifest and consumer XML using cmp, and runs Java fresh staging check.
No additional Gradle build, recursive build, dependency cycle or producer classpath
is introduced. Standalone compilation reads only its Java source files and fixed
Jackson input; the dependency flows only from production qualification to tooling.
Existing offline consumers, Python accounting/guardrails, snapshot hashes and
Python-authoritative install copy remain unchanged. The wrapper check is unchanged.

The production qualification source filter is unchanged: selected source/build/
publishing/nix trees and enumerated identities; generated consumer verification
metadata, caches/build outputs and symlinks remain excluded. `review/`, AGENTS,
README, API_DESIGN and release-checklist prose are excluded. Flake definitions,
cache/lock/catalog/wrapper inputs are independently accounted for. The new Python
migration runner is host-only; it is not installed or executed by Java or Nix.
All intended new files, including this report, are Git-known by intent-to-add only.

## Host validation and invocation accounting

**PASS** — relevant baseline `./gradlew verifyPublication`: 26s, 17 tasks,
5 executed / 12 up-to-date; authoritative Python acceptance.
Focused baseline publication tests: 9 PASS; release tests: 22 PASS.

**PASS** — first full normal gate: 13s, 27 outer tasks, 8 executed / 19 up-to-date;
nested module consumer 10s, 5 tasks executed. Immediate accounting: 405 tests,
zero failures/errors/skips, 92/92 cases, snapshot 3/3 and profile 1/1. Full XML
preserved in `final-xml/`. Full Python guardrails: 51 PASS; existing mocked HTTPError
cleanup ResourceWarnings are non-failing. Python fresh manifest check 10/10;
POM-only consumer 4s, 5 executed. First final real Java verify/check/copy all pass.

A final security review replaced path-based destination-directory creation with
anchored moves and added explicit integral-float/type/NaN/repeated-copy parity
coverage. These included-input edits invalidated that earlier final qualification;
standalone tests/parity and the affected full normal gate were rerun. The frozen
results and final counts are recorded below. No coverage was removed to save time.

| Invocation category | Count / disposition |
| --- | --- |
| Broad baseline Gradle test suite | 0; publication-specific baseline selected |
| Baseline publication Gradle gate | 1 PASS |
| Focused production Gradle tests | 0 |
| Focused Python baseline tests | 2 PASS (9 publication, 22 release) |
| Direct positive main javac attempts | 8 total: 7 PASS, 1 initial lint failure |
| Direct positive test javac | 6 PASS |
| Standalone publication runner | 6 PASS (48, 52, 57, 60, 60, 64 exported semantic fixtures) |
| Original J1 parser runner | 3 PASS, 43 assertions each |
| Bootstrap smoke / forbidden-import compile | 2 PASS each; import failure expected |
| Invalid Main command probes | 6 expected nonzero results |
| Differential suites | 5 PASS (73, 77, 85, 85, 90 cases) |
| Final full normal Java gates / immediate accounting | 2 / 2 PASS; justified security-review invalidation |
| Authoritative publication acceptance invocations | 3 PASS: baseline plus 2 final gates |
| Module-metadata independent consumer | 2 PASS, nested in final gates |
| Full Python guardrails | 1 PASS, 51 tests; unchanged Python helpers |
| Python fresh manifest check / POM-only consumer | 2 / 2 PASS |
| Real Java verify / check / copy | 3 / 2 / 2 PASS (initial verify plus 2 final comparisons) |
| Separate host forced-offline full suite | 0 NOT RUN; Gradle acquisition/build/resolution/cache behavior unchanged |
| Cache updater / agent Nix | 0 / 0 NOT RUN |
| Fresh human J2B Nix | 1 human-reported PASS |
| Release reproducibility, remote CI, signing, upload, tags, commit, push | 0 NOT RUN |

The initial javac failure was two unused try-resource lint warnings under Werror;
corrected before acceptance, without disabling lint. All fixture rejections and
forbidden-import failures are expected negative tests. Up-to-date production test
tasks reuse valid full XML and do not mean new execution of every test. Nested
consumers are not additional full production suites. No focused test overwrote
full-suite accounting. No second host offline full suite: standalone/Nix shadow
wiring changes do not change Gradle acquisition or publication/consumer resolution.
The existing isolated forced-offline consumer options remain mandatory and intact.

## Protected invariants, review inventory and human gate

**PASS — agent audit.** Baseline SHA-256/modes cover every tracked regular file.
**220 protected files** retain exact bytes/modes, covering production trees, r19
corpus, SPEC_PIN, VERSION, gradle, all publishing scripts/tests/consumer inputs,
workflows, dependency cache/lock and production build definitions. Only five
pre-existing files changed: flake.nix, qualification.nix, nix/tooling.nix, Main.java
and StrictJson.java. No wrapper, dependency/lock/cache, production/public API,
protocol, release-controller or protected workflow edit. JDK25/Jackson2.21.7,
BC1.86, Java17, protocol v1/r19, 92 cases and ten unsigned artifacts are retained.

**PASS — human-reported Nix.** The owner reported “nix flake check passed” after
the input freeze. One fresh human J2B invocation is reported; no detailed log,
duration or task/cache counts were supplied, and none are inferred. AGENTS.md
prohibits agent Nix execution; agent invocations remain zero. No earlier J2A.1
result is reused. Included inputs remain frozen; only this excluded report was
finalized after the successful human gate. No Maven Central or release execution
qualification is claimed. J3 release-controller migration and J4 workflow/Python
cutover are deferred; stop after J2B.

Residual constraints: filesystem operations require a secure directory provider;
missing copy directories require same-filesystem anchored moves as described above;
publication input/resource bounds are intentional; coherent verification assumes
included source/build inputs remain frozen. Python retains live authority throughout
this milestone. Detailed freeze, final Git inventory and frozen gate evidence follow.

## Frozen final gate and source membership

**PASS — agent-performed frozen gate.** Second full normal gate: **6s**, **27 outer tasks**, **8 executed / 19 up-to-date**; module consumer **5s**, **5 executed**. Immediate accounting again **405 tests**, zero failures/errors/skips, **92/92**, snapshot **3/3**, profile **1/1**. XML: `frozen-xml/`; logs: `frozen-normal.log`, `frozen-accounting.log`. Final real Java verify/check/copy and byte comparisons pass (`frozen-shadow.log`); independent Python checks both Java copies and confirms all ten baseline/final artifact hashes unchanged (`artifact-invariance.json`). Final Python fresh check and POM-only consumer results are recorded below.

**PASS — input freeze.** `/tmp/totipo-j2b-evidence/final-qualification-inputs.json` records **306** Git-known non-review regular-file SHA-256 hashes and modes, a conservative superset of qualification inputs. Inventory SHA-256: `8490db36d8962c9207cc610d70dbe6efdc90180bdadb09cb3066d04c35b2cca6`. All included source, tests, Nix/cache/lock/wrapper inputs are frozen. Only this confirmed-excluded report may change after the human gate.

**PASS — changed included/host input hashes and modes:**

| File | SHA-256 | Mode |
| --- | --- | --- |
| `flake.nix` | `4e896b7f13f83e5bc05adf523a39568f4da590d3efcbe04ca46ad6c06afbb638` | `0644` |
| `nix/tooling.nix` | `df4d5ebc733fc7925db47db6aa4c293b1341ea70c3da48e4e21b41099dfee57a` | `0644` |
| `qualification.nix` | `baec0ac63e473471c41f57bf51f1b1e4d7eaa272341b349ac1bf9c7c3ee91ced` | `0644` |
| `tooling/publication-parity.py` | `7f48d0b7a313610e58b5b2468e6563f0f78fa2d2ecca8b7c4865d78a77f6a3a4` | `0644` |
| `tooling/src/main/java/org/totipo/tooling/Main.java` | `1e2af565fa806d6501d9b969ff79f819c7113c4d18328bf9d614fc39658efd27` | `0644` |
| `tooling/src/main/java/org/totipo/tooling/StrictJson.java` | `316d5495103a5b812182dfddfecfddd70673f630fc248abb8afdc46c23ec0b2f` | `0644` |
| `tooling/src/main/java/org/totipo/tooling/publication/FilesSupport.java` | `f63310b702e32c8509010504314dcae9c9736aa9acc0ef0741c04dfba7da22ab` | `0644` |
| `tooling/src/main/java/org/totipo/tooling/publication/Json.java` | `49ea2a6b07429c11a9b3184fb9d0a6e04addc200eed2d2f2509fc2676c8dd11b` | `0644` |
| `tooling/src/main/java/org/totipo/tooling/publication/PublicationCli.java` | `6d83b2c1cc0941639d95626058c9c1e01c3ae1bbc3c2397f333e16d084a97fcd` | `0644` |
| `tooling/src/main/java/org/totipo/tooling/publication/PublicationManifest.java` | `b7d37542f8e473e4846ca2cb9db2a44911dccdc7c671db6a63ee4a84c9fdecf3` | `0644` |
| `tooling/src/main/java/org/totipo/tooling/publication/PublicationRepository.java` | `642f8157e6a77c019b5b90c729b2daa01acc28badea031bd6723722364636df5` | `0644` |
| `tooling/src/main/java/org/totipo/tooling/publication/PublicationVerifier.java` | `231c0aef3bb041ffbea443eb826b578dbb54b53629738326b5a61ea5b1785cd1` | `0644` |
| `tooling/src/test/java/org/totipo/tooling/publication/PublicationTest.java` | `d9372f920bc3886f02fa17a4044120507021452fa13b02d91c3cb7980f8951f0` | `0644` |

**PASS — final membership.** All nine intended new milestone files received intent-to-add only. No caches, build products, temporary evidence or secrets are Git-known. Five existing files remain unstaged. `git diff --cached --quiet` and `git diff --check` pass; cached-name inventory is empty. Branch/HEAD remain the starting main revision.

`git status --short`:

```text
 M flake.nix
 M nix/tooling.nix
 M qualification.nix
 A review/J2B_JAVA_PUBLICATION_VERIFICATION_REPORT.md
 A tooling/publication-parity.py
 M tooling/src/main/java/org/totipo/tooling/Main.java
 M tooling/src/main/java/org/totipo/tooling/StrictJson.java
 A tooling/src/main/java/org/totipo/tooling/publication/FilesSupport.java
 A tooling/src/main/java/org/totipo/tooling/publication/Json.java
 A tooling/src/main/java/org/totipo/tooling/publication/PublicationCli.java
 A tooling/src/main/java/org/totipo/tooling/publication/PublicationManifest.java
 A tooling/src/main/java/org/totipo/tooling/publication/PublicationRepository.java
 A tooling/src/main/java/org/totipo/tooling/publication/PublicationVerifier.java
 A tooling/src/test/java/org/totipo/tooling/publication/PublicationTest.java
```

`git diff --name-only`:

```text
flake.nix
nix/tooling.nix
qualification.nix
review/J2B_JAVA_PUBLICATION_VERIFICATION_REPORT.md
tooling/publication-parity.py
tooling/src/main/java/org/totipo/tooling/Main.java
tooling/src/main/java/org/totipo/tooling/StrictJson.java
tooling/src/main/java/org/totipo/tooling/publication/FilesSupport.java
tooling/src/main/java/org/totipo/tooling/publication/Json.java
tooling/src/main/java/org/totipo/tooling/publication/PublicationCli.java
tooling/src/main/java/org/totipo/tooling/publication/PublicationManifest.java
tooling/src/main/java/org/totipo/tooling/publication/PublicationRepository.java
tooling/src/main/java/org/totipo/tooling/publication/PublicationVerifier.java
tooling/src/test/java/org/totipo/tooling/publication/PublicationTest.java
```

`git diff --cached --name-only`: empty.

Complete changed-file inventory: **14 files** (five modified, nine new); all are listed above. The review report is excluded from derivations. No actual file content is staged and all changes remain uncommitted.

**PASS — frozen remaining gates.** `python3 -B publishing/publication.py check`
accepted **10/10** fresh observations. Independent POM-only consumer passed in
**906ms**, **5/5 tasks executed** (`frozen-pom-consumer.log`). Full Python guardrails
remain the already passing 51-test run; unchanged helpers were not redundantly
rerun. No included source changed after the frozen host gates.

**PASS — final diff summary.** Fourteen changed files: three Nix wiring files;
two existing standalone Java files; six new publication Java implementation files;
one permanent Java test runner; one host migration-oracle runner; one excluded
review report. Final `git diff --check` and `git diff --cached --quiet` pass.

**PASS — human qualification and post-gate audit.** The owner reported
“nix flake check passed”. Human J2B Nix invocations: **1 reported PASS**;
agent Nix invocations: **0**. No detailed execution log or duration was supplied.
The gate includes the existing Java/wrapper/tooling checks, new permanent
publication tests, real-artifact shadow parity and independent consumers through
the inspected wiring. No earlier milestone result is reused.

**PASS — agent verification after the owner report.** All **306** frozen
non-review file hashes and modes still match `final-qualification-inputs.json`.
Branch, HEAD, fourteen-file change inventory and Git source membership are
unchanged. No actual file content is staged. `git diff --cached --quiet` and
`git diff --check` pass. Only this explicitly excluded report was finalized;
no included input changed or invalidated qualification. Machine-readable audit:
`/tmp/totipo-j2b-evidence/post-human-freeze-audit.json`.

**PASS — J2B complete. PENDING — none for J2B.** All changes remain uncommitted
for owner review. Python remains authoritative. J3/J4 are deferred; no signing,
Maven Central publication, release execution, tag, commit or push was performed
or qualified. Stop after J2B.

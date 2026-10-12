# J2A — Independent JDK-only Gradle wrapper verification

Status: **PASS — J2A complete. Host validation passes; owner reported fresh Nix qualification passed.**
Changes remain uncommitted for owner review. No agent Nix, commit, tag, push,
remote publication, signing or release operation ran. Stop at J2A; J2B/J4 are
outside this milestone.

Evidence directory: /tmp/totipo-j2a-iwmnggkn (temporary evidence outside Git source membership).
Permanent deterministic fixtures live in WrapperVerifierTest.java; its optional
third argument retains fixture inputs and fixtures.tsv for independent Python
comparison. Temporary evidence and classes are not Git-known.

## Starting state and classification

**PASS** — read all of AGENTS.md before changes. Work exclusively in
/home/niki/Sources/totipo-java; no sibling repository or worktree access.
Starting git status --short: empty; branch main; HEAD
36bb7b6d802232c170b854552e4a4c2a421f047f (Enroll standalone java tooling).
Read the committed J0 and J1 reports. Current HEAD commits J1's six files;
J1's report records owner-reported normal Nix success and standalone tooling
checks. Its historical uncommitted wording is superseded by this committed HEAD.
J1's gate is established but is **not** reused for J2A.

Classifications: **DEPENDENCY_BUILD** (direct compilation/Nix check wiring)
and **RELEASE_TOOLING** (independent wrapper checker alongside protected-release
oracle). No dependency update, runtime/API, publication metadata, protocol or
release-preparation change. The stable full host Java gate is retained to
qualify the existing publication/consumer boundary.

## Authoritative Python contract and call sites

**PASS** — inspected nix/verify-wrapper.py, nix/tooling.nix, flake.nix,
qualification.nix, canonical wrapper files, the complete protected release
workflow, README and relevant contributor/checklist guidance.

Python first SHA-256 hashes the JAR, then reads properties as text and uses
str.splitlines(). It rejects any noncomment line whose rstrip() ends in a
backslash, including unrelated properties and two trailing backslashes.
Comment recognition uses lstrip().startswith(("#", "!")).
For each protected key it selects lines matching the start expression
\s*KEY(?:\s|=|:|$); the selected list must equal the single exact literal
KEY=EXPECTED. Leading/trailing whitespace, alternate separators, comments
appended to values, duplicates and continuations fail. Unrecognized unrelated
lines are otherwise permitted. It does not load a normalized Properties map,
execute Gradle or access the network. Argument unpacking/file/decode exceptions
reject malformed arguments and unavailable inputs; Java supplies concise errors.

Before J2A, ordinary checks.wrapper invoked this script using Python.
The protected jobs still invoke it unchanged at .github/workflows/release.yml
lines 74, 144 and 204: preflight, publish-central and verify-central.
No workflow bytes changed.

## Reviewed identities

**PASS** — constants independently match Python and checked-out inputs.
These pins do not authorize wrapper regeneration or an upgrade.

- Wrapper JAR SHA-256:
  238e777fcddd7e34f9708186085def2abd6e08e658505b38718d79d74c21abd5.
- Exact declaration:
  distributionUrl=https\://services.gradle.org/distributions/gradle-9.8.0-bin.zip
- Exact declaration:
  distributionSha256Sum=bafd5ce9cfaea0fbccfdc8439a1ac42fbd4cd9c89dc9a988228d8a2639a58e6c

## Java verifier and restricted compilation

**PASS** — tooling/wrapper/src/main/java/org/totipo/tooling/wrapper/WrapperVerifier.java
is independently compiled outside J1's Jackson-bearing source tree.
MessageDigest hashes actual JAR bytes incrementally. Expected identities are
private constants independent of supplied properties. Strict UTF-8 decoding
rejects invalid bytes. Exact raw declaration comparison/counting preserves
Python's contract. All noncomment continuations fail before success.
Explicit Python whitespace/line-boundary tables avoid Java Properties,
String.lines(), trim(), default regex whitespace and Character.isWhitespace().

CLI: WrapperVerifier <wrapper-jar> <wrapper-properties>.
Success prints a concise diagnostic and exits 0. Verification/I/O/path errors
print actionable diagnostics and exit 1; wrong argument count prints usage and
exits 2. Missing files, directories and invalid UTF-8 fail closed; I/O failures
retain file context. The verifier only reads inputs: no writes, subprocesses or
network APIs. The runner writes temporary fixtures and starts only the exact JDK
Java executable for CLI tests. Canonical wrapper files are never mutated.

Host main compilation uses an explicitly empty classpath directory; test
compilation uses only main classes. Both specify --release 25 -encoding UTF-8
-Xlint:all -Werror -proc:none, explicit sourcepath and exact source files.
Host JDK: OpenJDK 25.0.4.1+1. Final compile, runner and canonical verification
also passed with PATH set to an empty directory and CLASSPATH empty.
Exact commands: restricted-commands.txt; output: restricted-validation.log.
No Gradle, Jackson, production class, JUnit, Python or other library is needed.
This is not a Gradle module, launcher framework or Maven publication.

## Deterministic tests and parity

**PASS** — final JDK-only runner: **183 fixtures, 49 ACCEPT and 134 REJECT**,
each checked through the direct verifier and a real CLI subprocess.
Four additional CLI probes reject zero/one/extra arguments and an empty JAR path.
A separate invalid-path assertion checks NUL rejection: NUL cannot pass through
OS argv, so it is not described as an executed CLI case.

Fixtures cover reviewed inputs, LF/CRLF/no-final-newline, harmless comments,
unrelated properties, one-byte/empty/truncated JARs, wrong/missing/duplicate pins,
alternate separators/escaping/whitespace, leading/trailing characters,
protected/unrelated/double-backslash continuations, whitespace after a
continuation, misleading comments, empty/BOM/NUL properties, missing files,
directories and invalid UTF-8 (including an invalid comment).
All ten Python line separators and all 29 Python whitespace code points receive
tests of declaration/comment/continuation behavior. No test framework, random
fixture or network. Expected negative results are successful tests.

**PASS** — initial comparison before switching checks.wrapper:
183 fixtures, 49 ACCEPT, 134 REJECT, zero disagreements.
After a test-runner clarity edit, restricted compilation/tests and fresh parity
again passed. Final comparison includes four additional CLI cases:
**187 total, 49 ACCEPT, 138 REJECT, zero disagreements**.
Python and Java received identical fixture paths/bytes and CLI argument lists.
Disposition, not diagnostic equality, was compared. Full paths/diagnostics:
final-parity.json. No intentional acceptance difference or unresolved drift.

Permanent fixtures preserve historical accepted edge cases. In particular,
escaped-key-extra appends distribution\u0055rl=bad after canonical declarations:
both raw-line checkers accept it because the source matcher does not recognize
that escaped spelling. A normalized Gradle properties parser can interpret it
as an overriding protected key. This is an existing oracle limitation.
**Recommend a separately reviewed contract correction in both implementations**,
including release qualification; J2A does not silently tighten acceptance.
Non-whitespace U+200B prefixes/suffixes and Python-only line separators are also
retained exactly. These tests prove historical raw-source parity, not equivalence
to every Gradle properties-parser spelling.

Per-case names describe mutations; code-point suffixes are hexadecimal.
line-boundary-X separates canonical declarations with U+X.
boundary-duplicate-X inserts U+X after a comment marker before a duplicate.
whitespace-duplicate-X prefixes a duplicate with U+X.
whitespace-key-delimiter-X inserts U+X after a protected key in an extra declaration.
whitespace-continuation-X appends U+X after an unrelated trailing backslash.
whitespace-comment-X prefixes a comment ending in a backslash with U+X.
Exact fixture bytes are generated by the permanent Java tests.

| Test / input mutation | Python | Java | Agreement |
| --- | --- | --- | --- |
| original | ACCEPT | ACCEPT | PASS |
| lf | ACCEPT | ACCEPT | PASS |
| crlf | ACCEPT | ACCEPT | PASS |
| no-final-newline | ACCEPT | ACCEPT | PASS |
| comments-and-unrelated | ACCEPT | ACCEPT | PASS |
| commented-misleading-extras | ACCEPT | ACCEPT | PASS |
| key-prefix-unrelated | ACCEPT | ACCEPT | PASS |
| wrong-url | REJECT | REJECT | PASS |
| wrong-checksum | REJECT | REJECT | PASS |
| missing-url | REJECT | REJECT | PASS |
| missing-checksum | REJECT | REJECT | PASS |
| duplicate-url | REJECT | REJECT | PASS |
| duplicate-checksum | REJECT | REJECT | PASS |
| different-duplicate-url | REJECT | REJECT | PASS |
| commented-only-url | REJECT | REJECT | PASS |
| commented-only-checksum | REJECT | REJECT | PASS |
| url-leading-space | REJECT | REJECT | PASS |
| url-trailing-space | REJECT | REJECT | PASS |
| url-leading-character | REJECT | REJECT | PASS |
| url-trailing-character | REJECT | REJECT | PASS |
| checksum-leading-tab | REJECT | REJECT | PASS |
| checksum-trailing-comment | REJECT | REJECT | PASS |
| url-colon-separator | REJECT | REJECT | PASS |
| url-space-separator | REJECT | REJECT | PASS |
| url-space-before-equals | REJECT | REJECT | PASS |
| url-space-after-equals | REJECT | REJECT | PASS |
| unescaped-url | REJECT | REJECT | PASS |
| escaped-key-extra | ACCEPT | ACCEPT | PASS |
| escaped-key-only | REJECT | REJECT | PASS |
| protected-continuation | REJECT | REJECT | PASS |
| unrelated-continuation | REJECT | REJECT | PASS |
| double-backslash-continuation | REJECT | REJECT | PASS |
| continuation-trailing-space | REJECT | REJECT | PASS |
| empty-properties | REJECT | REJECT | PASS |
| bom-url | REJECT | REJECT | PASS |
| nul-url | REJECT | REJECT | PASS |
| non-whitespace-prefix-extra | ACCEPT | ACCEPT | PASS |
| non-whitespace-suffix-extra | ACCEPT | ACCEPT | PASS |
| line-boundary-a | ACCEPT | ACCEPT | PASS |
| boundary-duplicate-a | REJECT | REJECT | PASS |
| line-boundary-d | ACCEPT | ACCEPT | PASS |
| boundary-duplicate-d | REJECT | REJECT | PASS |
| line-boundary-b | ACCEPT | ACCEPT | PASS |
| boundary-duplicate-b | REJECT | REJECT | PASS |
| line-boundary-c | ACCEPT | ACCEPT | PASS |
| boundary-duplicate-c | REJECT | REJECT | PASS |
| line-boundary-1c | ACCEPT | ACCEPT | PASS |
| boundary-duplicate-1c | REJECT | REJECT | PASS |
| line-boundary-1d | ACCEPT | ACCEPT | PASS |
| boundary-duplicate-1d | REJECT | REJECT | PASS |
| line-boundary-1e | ACCEPT | ACCEPT | PASS |
| boundary-duplicate-1e | REJECT | REJECT | PASS |
| line-boundary-85 | ACCEPT | ACCEPT | PASS |
| boundary-duplicate-85 | REJECT | REJECT | PASS |
| line-boundary-2028 | ACCEPT | ACCEPT | PASS |
| boundary-duplicate-2028 | REJECT | REJECT | PASS |
| line-boundary-2029 | ACCEPT | ACCEPT | PASS |
| boundary-duplicate-2029 | REJECT | REJECT | PASS |
| whitespace-duplicate-9 | REJECT | REJECT | PASS |
| whitespace-key-delimiter-9 | REJECT | REJECT | PASS |
| whitespace-continuation-9 | REJECT | REJECT | PASS |
| whitespace-comment-9 | ACCEPT | ACCEPT | PASS |
| whitespace-duplicate-a | REJECT | REJECT | PASS |
| whitespace-key-delimiter-a | REJECT | REJECT | PASS |
| whitespace-continuation-a | REJECT | REJECT | PASS |
| whitespace-comment-a | ACCEPT | ACCEPT | PASS |
| whitespace-duplicate-b | REJECT | REJECT | PASS |
| whitespace-key-delimiter-b | REJECT | REJECT | PASS |
| whitespace-continuation-b | REJECT | REJECT | PASS |
| whitespace-comment-b | ACCEPT | ACCEPT | PASS |
| whitespace-duplicate-c | REJECT | REJECT | PASS |
| whitespace-key-delimiter-c | REJECT | REJECT | PASS |
| whitespace-continuation-c | REJECT | REJECT | PASS |
| whitespace-comment-c | ACCEPT | ACCEPT | PASS |
| whitespace-duplicate-d | REJECT | REJECT | PASS |
| whitespace-key-delimiter-d | REJECT | REJECT | PASS |
| whitespace-continuation-d | REJECT | REJECT | PASS |
| whitespace-comment-d | ACCEPT | ACCEPT | PASS |
| whitespace-duplicate-1c | REJECT | REJECT | PASS |
| whitespace-key-delimiter-1c | REJECT | REJECT | PASS |
| whitespace-continuation-1c | REJECT | REJECT | PASS |
| whitespace-comment-1c | ACCEPT | ACCEPT | PASS |
| whitespace-duplicate-1d | REJECT | REJECT | PASS |
| whitespace-key-delimiter-1d | REJECT | REJECT | PASS |
| whitespace-continuation-1d | REJECT | REJECT | PASS |
| whitespace-comment-1d | ACCEPT | ACCEPT | PASS |
| whitespace-duplicate-1e | REJECT | REJECT | PASS |
| whitespace-key-delimiter-1e | REJECT | REJECT | PASS |
| whitespace-continuation-1e | REJECT | REJECT | PASS |
| whitespace-comment-1e | ACCEPT | ACCEPT | PASS |
| whitespace-duplicate-1f | REJECT | REJECT | PASS |
| whitespace-key-delimiter-1f | REJECT | REJECT | PASS |
| whitespace-continuation-1f | REJECT | REJECT | PASS |
| whitespace-comment-1f | ACCEPT | ACCEPT | PASS |
| whitespace-duplicate-20 | REJECT | REJECT | PASS |
| whitespace-key-delimiter-20 | REJECT | REJECT | PASS |
| whitespace-continuation-20 | REJECT | REJECT | PASS |
| whitespace-comment-20 | ACCEPT | ACCEPT | PASS |
| whitespace-duplicate-85 | REJECT | REJECT | PASS |
| whitespace-key-delimiter-85 | REJECT | REJECT | PASS |
| whitespace-continuation-85 | REJECT | REJECT | PASS |
| whitespace-comment-85 | ACCEPT | ACCEPT | PASS |
| whitespace-duplicate-a0 | REJECT | REJECT | PASS |
| whitespace-key-delimiter-a0 | REJECT | REJECT | PASS |
| whitespace-continuation-a0 | REJECT | REJECT | PASS |
| whitespace-comment-a0 | ACCEPT | ACCEPT | PASS |
| whitespace-duplicate-1680 | REJECT | REJECT | PASS |
| whitespace-key-delimiter-1680 | REJECT | REJECT | PASS |
| whitespace-continuation-1680 | REJECT | REJECT | PASS |
| whitespace-comment-1680 | ACCEPT | ACCEPT | PASS |
| whitespace-duplicate-2000 | REJECT | REJECT | PASS |
| whitespace-key-delimiter-2000 | REJECT | REJECT | PASS |
| whitespace-continuation-2000 | REJECT | REJECT | PASS |
| whitespace-comment-2000 | ACCEPT | ACCEPT | PASS |
| whitespace-duplicate-2001 | REJECT | REJECT | PASS |
| whitespace-key-delimiter-2001 | REJECT | REJECT | PASS |
| whitespace-continuation-2001 | REJECT | REJECT | PASS |
| whitespace-comment-2001 | ACCEPT | ACCEPT | PASS |
| whitespace-duplicate-2002 | REJECT | REJECT | PASS |
| whitespace-key-delimiter-2002 | REJECT | REJECT | PASS |
| whitespace-continuation-2002 | REJECT | REJECT | PASS |
| whitespace-comment-2002 | ACCEPT | ACCEPT | PASS |
| whitespace-duplicate-2003 | REJECT | REJECT | PASS |
| whitespace-key-delimiter-2003 | REJECT | REJECT | PASS |
| whitespace-continuation-2003 | REJECT | REJECT | PASS |
| whitespace-comment-2003 | ACCEPT | ACCEPT | PASS |
| whitespace-duplicate-2004 | REJECT | REJECT | PASS |
| whitespace-key-delimiter-2004 | REJECT | REJECT | PASS |
| whitespace-continuation-2004 | REJECT | REJECT | PASS |
| whitespace-comment-2004 | ACCEPT | ACCEPT | PASS |
| whitespace-duplicate-2005 | REJECT | REJECT | PASS |
| whitespace-key-delimiter-2005 | REJECT | REJECT | PASS |
| whitespace-continuation-2005 | REJECT | REJECT | PASS |
| whitespace-comment-2005 | ACCEPT | ACCEPT | PASS |
| whitespace-duplicate-2006 | REJECT | REJECT | PASS |
| whitespace-key-delimiter-2006 | REJECT | REJECT | PASS |
| whitespace-continuation-2006 | REJECT | REJECT | PASS |
| whitespace-comment-2006 | ACCEPT | ACCEPT | PASS |
| whitespace-duplicate-2007 | REJECT | REJECT | PASS |
| whitespace-key-delimiter-2007 | REJECT | REJECT | PASS |
| whitespace-continuation-2007 | REJECT | REJECT | PASS |
| whitespace-comment-2007 | ACCEPT | ACCEPT | PASS |
| whitespace-duplicate-2008 | REJECT | REJECT | PASS |
| whitespace-key-delimiter-2008 | REJECT | REJECT | PASS |
| whitespace-continuation-2008 | REJECT | REJECT | PASS |
| whitespace-comment-2008 | ACCEPT | ACCEPT | PASS |
| whitespace-duplicate-2009 | REJECT | REJECT | PASS |
| whitespace-key-delimiter-2009 | REJECT | REJECT | PASS |
| whitespace-continuation-2009 | REJECT | REJECT | PASS |
| whitespace-comment-2009 | ACCEPT | ACCEPT | PASS |
| whitespace-duplicate-200a | REJECT | REJECT | PASS |
| whitespace-key-delimiter-200a | REJECT | REJECT | PASS |
| whitespace-continuation-200a | REJECT | REJECT | PASS |
| whitespace-comment-200a | ACCEPT | ACCEPT | PASS |
| whitespace-duplicate-2028 | REJECT | REJECT | PASS |
| whitespace-key-delimiter-2028 | REJECT | REJECT | PASS |
| whitespace-continuation-2028 | REJECT | REJECT | PASS |
| whitespace-comment-2028 | ACCEPT | ACCEPT | PASS |
| whitespace-duplicate-2029 | REJECT | REJECT | PASS |
| whitespace-key-delimiter-2029 | REJECT | REJECT | PASS |
| whitespace-continuation-2029 | REJECT | REJECT | PASS |
| whitespace-comment-2029 | ACCEPT | ACCEPT | PASS |
| whitespace-duplicate-202f | REJECT | REJECT | PASS |
| whitespace-key-delimiter-202f | REJECT | REJECT | PASS |
| whitespace-continuation-202f | REJECT | REJECT | PASS |
| whitespace-comment-202f | ACCEPT | ACCEPT | PASS |
| whitespace-duplicate-205f | REJECT | REJECT | PASS |
| whitespace-key-delimiter-205f | REJECT | REJECT | PASS |
| whitespace-continuation-205f | REJECT | REJECT | PASS |
| whitespace-comment-205f | ACCEPT | ACCEPT | PASS |
| whitespace-duplicate-3000 | REJECT | REJECT | PASS |
| whitespace-key-delimiter-3000 | REJECT | REJECT | PASS |
| whitespace-continuation-3000 | REJECT | REJECT | PASS |
| whitespace-comment-3000 | ACCEPT | ACCEPT | PASS |
| invalid-utf8 | REJECT | REJECT | PASS |
| invalid-utf8-comment | REJECT | REJECT | PASS |
| one-byte-jar | REJECT | REJECT | PASS |
| empty-jar | REJECT | REJECT | PASS |
| truncated-jar | REJECT | REJECT | PASS |
| missing-jar | REJECT | REJECT | PASS |
| missing-properties | REJECT | REJECT | PASS |
| directory-jar | REJECT | REJECT | PASS |
| directory-properties | REJECT | REJECT | PASS |
| cli-no-arguments | REJECT | REJECT | PASS |
| cli-one-argument | REJECT | REJECT | PASS |
| cli-extra-argument | REJECT | REJECT | PASS |
| cli-empty-jar-path | REJECT | REJECT | PASS |


## Nix migration and source membership

**PASS — inspected; execution qualified by the owner-reported human gate.**
checks.wrapper calls nix/wrapper-verifier.nix. It asserts JDK major 25 and uses
pinned jdk25_headless directly. Fileset root: tooling/wrapper; exactly the two
named Java sources are selected. Main classpath: empty directory; test classpath:
main classes only. It compiles both, executes the runner, and independently runs
the CLI on explicit canonical wrapper JAR/properties store paths. Those files
are verification inputs, never compilation classpath dependencies.
No Gradle/Jackson/Python input/invocation; no wrapper execution. Output is a check
marker, not published classes or an additional user-facing app.

checks.java, checks.tooling, apps.tooling and unsigned Maven output remain intact.
Sibling checks have no guaranteed execution order. Existing Java qualification
uses pinned Gradle independently of wrapper verification. qualification.nix's
source filter is unchanged: its nix/ selection includes the new Nix expression,
changing a derivation input without changing its behavior. Wrapper Java inputs
are selected by their separate exact fileset; J1 still selects only tooling/src.
Flake/lock/cache/wrapper paths are also explicit inputs.
README, AGENTS, API_DESIGN, RELEASE_CHECKLIST and review/ are excluded from the
qualification selections. Only this excluded report may be finalized after the
human gate; included-input edits require requalification.

## Protected workflows, dependencies and artifacts

**PASS** — Python checker and all three protected invocations retain exact bytes
and modes. No protected-job Nix/bootstrap, approval/secrets/signing/retry/Central/
tag/source identity policy, release controller or publication manifest changes.
Python remains available in existing development/qualification environments.
No checker deleted; no claim of a Python-free repository.

**PASS** — production trees, r19 corpus, SPEC_PIN, VERSION, publishing and gradle
trees, dependencies/locks/strict verification, package-deps and flake.lock remain
unchanged. JDK 25, Gradle 9.8.0, Jackson Core 2.21.7, BC 1.86, Java 17 published
bytecode, protocol v1/r19, all 92 portable cases and consumer isolation are
preserved. No dependency/toolchain/cache update or version bump; no new artifact.
Final verifyPublication accepted exact production classes/sources, Java 17,
POM/module boundaries and ten unsigned artifacts. Both consumers compile only
against core/storage 0.2.0; runtime adds BC 1.86. Tooling and Jackson do not enter
publications. Local staging tasks ran only as required by qualification;
no remote publication occurred.

## Host validation and execution accounting

All agent-executed gates **PASS**:

1. Original Python checker on canonical inputs.
2. ./gradlew test: 29s, 13 tasks, 2 executed/11 up-to-date; both module test
   tasks up-to-date. The authorized ordinary Gradle baseline downloaded the
   pinned distribution; neither verifier downloaded or executed it.
3. Immediate python3 -B publishing/release.py tests: 405 tests, zero failures/
   errors/skips; 92/92 cases, snapshot 3/3, profile 1/1. Baseline XML saved in
   baseline-xml/ before further gates.
4. Initial strict main/test javac, 183-case runner and Python comparison
   before changing checks.wrapper.
5. After a test-only clarity edit separating the NUL assertion from CLI probes,
   final restricted compile, runner and canonical Java check with empty PATH.
   Fresh 187-case parity and independent canonical Python/Java checks passed.
6. ./gradlew test build javadoc verifyPublication consumerSmoke: 13s,
   27 outer tasks, 8 executed/19 up-to-date; both module test tasks up-to-date.
   Nested module consumer: 11s, 5/5 tasks executed. Nested tasks do not imply
   additional full suite invocations.
7. Immediate full XML accounting: 405 tests, zero failures/errors/skips,
   92/92 portable cases, snapshot 3/3, profile 1/1. Full XML and accepted manifest
   preserved in normal-evidence/.
8. python3 -B -m unittest discover -s publishing -p 'test_*.py': 51 tests, OK.
   Existing synthetic HTTPError cleanup emitted ResourceWarnings; no test failed.
9. python3 -B publishing/publication.py check: 10/10 fresh-byte hashes PASS.
10. ./gradlew -p publishing/consumer-smoke --offline -PpomOnly clean check:
    6s, 5/5 tasks executed; independent POM-only boundary PASS.
11. git diff --check, protected byte/hash/mode audit, changed-file membership
    review and no-staged-content verification PASS.

No focused Gradle tests replaced full-suite XML. Java sources/tests and Nix wiring
were stable before the final full host gate. No unexpected failed build, test or
parity attempt. One report-generation tool call had a JavaScript syntax error
before execution; corrected without repository edits or qualification impact.
The intermediate test clarity edit was followed by final compile/tests/parity.

| Invocation category | Count / disposition |
| --- | --- |
| Broad baseline Gradle tests | 1 PASS; module tests up-to-date |
| Baseline XML accounting | 1 PASS |
| Focused Gradle / focused Python tests | 0 / 0; no production/helper edits |
| Direct main/test javac | 2 main + 2 test PASS |
| Standalone Java runner | 2 PASS, 183 fixtures each |
| Python/Java parity suite | 2 PASS: 183 initial, 187 final |
| Canonical Python checker outside parity | 2 PASS |
| Canonical Java CLI outside runner/parity | 2 PASS |
| Final normal full Java gate | 1 PASS |
| Final full XML accounting | 1 PASS |
| Full Python guardrails | 1 PASS, 51 tests |
| Publication verification / module consumer | 1 each PASS, nested in final gate |
| Manifest fresh-byte check / POM-only consumer | 1 each PASS |
| Separate host forced-offline full suite | 0 NOT RUN; existing acquisition/build/resolution/cache unchanged |
| Human cache update | 0 NOT RUN; no dependency inputs changed |
| Agent Nix | 0 NOT RUN; prohibited |
| Human J2A Nix | 1 human-reported PASS |
| Release reproducibility/signing/remote publishing/tagging/pushing/committing | 0 NOT RUN |

Negative runner dispositions: 134 expected rejections each, checked by API and CLI;
four additional CLI rejections each. NUL assertion is separate. Python comparison:
134 expected rejections initially, 138 finally. These are PASS evidence.
The Python guardrails' synthetic artifact comparison is not the release-only
ten-artifact normal/offline reproducibility procedure. That procedure is NOT RUN.
No separate host forced-offline full suite: existing Gradle acquisition,
compilation, publication resolution and cache are unchanged. The required
isolated consumers still ran; final human Nix provides fixed/offline proof.

## Protected byte/hash/mode audit

**PASS** — baseline-files.json records every starting tracked regular file.
protected-audit.json confirms **222 protected files** retain SHA-256 and modes:
requested production/spec/publishing/gradle/cache files, workflow/Python checker,
flake.lock, J1 sources/Nix and qualification.nix.
Only pre-existing README.md and flake.nix changed anywhere in the tracked tree.
Historical reports remain byte/mode identical. Audit group digests below hash
sorted JSON of path-to-SHA-256/mode records; groups may overlap.

| Protected group | Files | SHA-256 of hash/mode inventory |
| --- | --- | --- |
| core/src/main/ | 82 | dd317f44622fad4eba5b34bef7ee19e9e7da0f0c291e51ae5793aa6e1af6faec |
| storage-nio/src/main/ | 12 | c712cc3ffe06007099aff9f87f9df5af89fc3556dde614dd4d393e2af6a5ea0e |
| core/src/test/resources/totipo-spec/ | 100 | ec154d017c1c0614b9dc73e71c32a069c96bc1eff3945f7d37e911753f6e756c |
| publishing/ | 13 | f8854c28b44da4dea394cae2019de1b886598f78f46b5de9604fd50708dee66e |
| gradle/ | 4 | 37b9b10c6232ce0bd4835d01f4f5230230fc32db56352b3ec1276562ad59f833 |
| identity and qualification files | 8 | 20f8678e7807ba28a106968a5c17ecbfba28eb59040b9e2b7a06304f049c06b6 |
| J1 Java sources | 3 | 29abb928e5e719a5bc0b6ff4efa820aeecb17115b3f21918f6f2ec43bb766389 |

| Key protected file | SHA-256 | Mode |
| --- | --- | --- |
| gradle/wrapper/gradle-wrapper.jar | 238e777fcddd7e34f9708186085def2abd6e08e658505b38718d79d74c21abd5 | 0o644 |
| gradle/wrapper/gradle-wrapper.properties | e81b90975868be2513ca00d6a5a01303a406a5cb01a306210b887e983e4b17ae | 0o644 |
| nix/verify-wrapper.py | cafbad362e1ea696d15dd3c428b3b8c48744a32d82e634ad7005f465a0ee1afc | 0o644 |
| .github/workflows/release.yml | 65d8920d8db4a3c348828232be6a4addd70fac2e52b48b0a3d79730094ed1835 | 0o644 |
| package-deps.json | ee87ee929d064232e30b0bb91c8aaa4a249ae5f0fad7d85180a9833709f6a879 | 0o644 |
| flake.lock | 44728fcfd8529462cb415b186ee4ce3400343dd911d325b6a965bd386b9f7749 | 0o644 |
| SPEC_PIN.md | 736f9489852dcf2d0f22fb13f7da9fcd2dca4de18f8d735fbb34aad6162dedd0 | 0o644 |
| VERSION | 1f930dd1f133c1f97a94fe3acb8db34372cf4c01ffdb2b3ff4ca72f9494121e9 | 0o644 |


## Remaining Python and human qualification

Python remains in the protected workflow (wrapper checker and release-controller
operations), Gradle verifyPublication via publishing/verify-publication.py,
qualification.nix full/focused accounting, guardrails and publication check/copy,
documented release procedures and development environments.
Temporary migration comparison used Python. No Python environment removed.
The standalone wrapper derivation/verifier/tests invoke no Python.

**PASS — human-reported J2A qualification.** The owner reported
"nix flake check passed" after the input freeze. One fresh human J2A invocation
is reported; no detailed log, duration or task/cache counts were supplied.
This qualifies checks.java, checks.tooling and the migrated checks.wrapper.
No J1 result is reused. The agent executed no Nix command; no redundant build,
dependency-cache update or release-only reproducibility gate was requested.

**PASS — agent verification after the owner report.** All 298 frozen non-review
file hashes and modes still match final-qualification-inputs.json and its
recorded inventory digest. Git source membership, main branch, starting HEAD
and the six-file changed inventory are unchanged. No actual content is staged;
intent-to-add entries remain. Post-gate evidence: post-human-freeze-audit.json.
Only this explicitly excluded report was finalized afterward, so no included
input changed and the successful qualification remains valid. J2A is complete.
J2B/J4 and protected-workflow migration remain deferred.

## Complete changed inventory and freeze

Six intended files; actual contents remain unstaged:

- README.md: ordinary Java/protected Python arrangement and unchanged pins.
- flake.nix: delegate only checks.wrapper to independent Java derivation.
- nix/wrapper-verifier.nix: exact-source JDK-only compile/test/check.
- tooling/wrapper/src/main/java/org/totipo/tooling/wrapper/WrapperVerifier.java
- tooling/wrapper/src/test/java/org/totipo/tooling/wrapper/WrapperVerifierTest.java
- review/J2A_JAVA_WRAPPER_VERIFICATION_REPORT.md: excluded report.

New files receive only git add --intent-to-add; entries remain for owner review.
Freeze conservatively covers every Git-known non-review regular file, a superset
of included inputs. Review is confirmed excluded. Freeze digest, changed input
hashes and final Git evidence follow.

**PASS — frozen before requesting the human gate.** 298 Git-known
non-review regular files recorded in /tmp/totipo-j2a-iwmnggkn/final-qualification-inputs.json.
Inventory SHA-256: 6d3d6c36f12a647d1a9b82029ff38086a90cc3b216b270089b2e9f49439f6891.
Branch remains main; HEAD remains 36bb7b6d802232c170b854552e4a4c2a421f047f.
All intended new files, including this report, are intent-to-add only.
git diff --cached --quiet PASS; staged-content inventory empty.
git diff --check PASS. No qualification input may change after this point.

| Changed non-review input | SHA-256 | Mode |
| --- | --- | --- |
| README.md | 6efc604c05854dc2f3303da734597324f01bab296b216e905e9be6e89e561972 | 0o644 |
| flake.nix | 990555571b78074656c101391f17ca5fe7e1572e5ca23504d75632aad40ac49b | 0o644 |
| nix/wrapper-verifier.nix | 73cb3a0922f01936b7cbce09787ead14cc24f3ceccdc2183f838488dcd745368 | 0o644 |
| tooling/wrapper/src/main/java/org/totipo/tooling/wrapper/WrapperVerifier.java | bb529d60641aaa7f9baeae558d6c24b850f743f3bff981a534b3b13265a4fff7 | 0o644 |
| tooling/wrapper/src/test/java/org/totipo/tooling/wrapper/WrapperVerifierTest.java | 5addc7c68ae924e1b7126d4be1216474263db65e85d3a6e7cd0eb7d14cd253cf | 0o644 |

git status --short:

```text
 M README.md
 M flake.nix
 A nix/wrapper-verifier.nix
 A review/J2A_JAVA_WRAPPER_VERIFICATION_REPORT.md
 A tooling/wrapper/src/main/java/org/totipo/tooling/wrapper/WrapperVerifier.java
 A tooling/wrapper/src/test/java/org/totipo/tooling/wrapper/WrapperVerifierTest.java
```

git diff --name-only:

```text
README.md
flake.nix
nix/wrapper-verifier.nix
review/J2A_JAVA_WRAPPER_VERIFICATION_REPORT.md
tooling/wrapper/src/main/java/org/totipo/tooling/wrapper/WrapperVerifier.java
tooling/wrapper/src/test/java/org/totipo/tooling/wrapper/WrapperVerifierTest.java
```

git diff --cached --name-only:

```text
(empty)
```

git diff --stat (including this finalized report):

```text
 README.md                                          |  20 +-
 flake.nix                                          |   9 +-
 nix/wrapper-verifier.nix                           |  45 ++
 review/J2A_JAVA_WRAPPER_VERIFICATION_REPORT.md     | 567 +++++++++++++++++++++
 .../totipo/tooling/wrapper/WrapperVerifier.java    | 124 +++++
 .../tooling/wrapper/WrapperVerifierTest.java       | 209 ++++++++
 6 files changed, 962 insertions(+), 12 deletions(-)
```

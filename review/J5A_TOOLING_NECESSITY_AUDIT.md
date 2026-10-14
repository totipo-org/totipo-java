# J5A — Tooling necessity and simplification audit

## 1. Executive summary

**Recommend one bounded conservative simplification pass.** Keep independent checks of Maven outputs, signing inputs and Central bytes; keep actual protocol/security tests, controlled inputs and both consumer modes. Remove historical wrapper parsing, workflow convention enforcement and repeated acceptance/integrity work. Do not replace a reasonably small release helper with a larger shell implementation just to delete Java.

Current support Java is **2,182 production + 1,601 test lines**, compared with **3,725 library production + 6,160 library test/fixture lines**. Much support code has a real purpose, but independence varies by operation. Artifact validation crosses useful producer/output boundaries; workflow spelling, repeated source checks and historical Python compatibility mostly do not. The recommended pass should remove approximately **520–740 Java lines net**, plus **35–70 configuration lines**, without changing the version + exact SHA + one-dispatch successful experience. These are estimates, not deletion targets or an implemented result.

The smallest credible system still needs independent semantic artifact inspection, exact signing-input/remote identity and real consumers. GitHub should own permissions, job dependencies, action resolution and protected references; Gradle should own execution, dependency resolution, signing and publishing; Nix should own the fixed build environment. Repository tests do not independently authorize their own mutable source.

This is design only. Only this report changes; both mutation guards remain false. No Gradle/test/Nix/release qualification ran, and no human gate is requested.

## 2. Starting HEAD, version, pin and Git state

| Item | Observed current value |
| --- | --- |
| Branch / full HEAD | `main` / `7c40b66485c7760b944b7dccf3a3d671fdcdaa94` |
| Initial status | `git status --short` empty; no initial tracked or nonignored untracked changes |
| Committed milestone | HEAD contains J4C, including section 15's correction removing the local-action self-hash; parent `2597be7838da5f1eeab94b1e5ed9d1b07a7ed300` contains J4B |
| Classification | `DOCS_JAVADOC`: excluded Markdown design report. Subject is release/build tooling; no executable `RELEASE_TOOLING`, `DEPENDENCY_BUILD` or `RELEASE_PREPARATION` change |
| VERSION | `0.2.0` plus LF; no new version prepared |
| Specification | v1/r19, `cdb4e91be1c6d3704874b2b92457ffe7be5e9084` |
| Requirements profile hash | `7242fc557a7ca56452c934bedc5e7dc2835248f5cd36fa220f05588dc967ec80` |
| Snapshot | 99 imported files / 92 portable cases |
| JDK | `java --version`: OpenJDK `25.0.4.1+1`; tooling targets 25, libraries target 17 / class version 61.0 |
| Gradle / publishing plugin | Wrapper and qualification assert `9.8.0`; Vanniktech `0.37.0` |
| Dependencies | Jackson Core `2.21.7`, BC `1.86`, JUnit `6.1.3`; publishing cache includes OkHttp `5.1.0` |
| Nix bootstrap | `2.35.2`; installer SHA-256 `9adda97297d9e8ab360df95c729eabff4f4f93d6db091953c3a68f29e3fb130c` |
| Action identities | checkout `3d3c42e5aac5ba805825da76410c181273ba90b1` / v7.0.1; install-nix `13d8dd58da0234aa297dedd986986ccb8e7f3e24` / v31.11.1 |
| Publishing guard | `release.yml`, `publish-central.if: ${{ false && github.run_attempt == 1 }}` |
| Finalization guard | `verify-release.yml`, `finalize.if: ${{ false }}` |

Actual lock graph matters: `flake.lock`'s **root nixpkgs input is `nixpkgs_3`**, revision `39ad350a0602fa0a58a544344e3e9187526ea45c`; the `nixpkgs` node at `a799d3e3886da994fa307f817a6bc705ae538eeb` belongs to jailed-agents. `nixpkgs_2` at `f45c6f04c2f013f004bf94e284e95d72898d9393` belongs to llm-agents. Do not reuse J4B's historical root revision as current authority. Other direct input pins: flake-utils `11707dc2f618dd54ca8739b309ec4fc024de578b`, llm-agents `4bb57cff45b5554a02dba0cf7a8c7f4f010a864d`, jailed-agents `82b9d99d454bd752ae028e8a4bf0c90daf1c23ef`.

Read AGENTS.md completely. Consulted J4C (including final correction), J4B, J4S and the J1 publication/bootstrap, J2A/J2A.1 wrapper, J2B publication and J3A/J3B.1/J3B.2 boundary history. Recommendations below derive from current implementation/callers, not historical preservation requirements. No sibling repository, worktree or sub-agent was used. The owner reports protections configured; no settings were queried or changed. Checklist language about earlier absent protections is history, not a current effective-settings finding.

## 3. Actual source/tooling inventory

Paths in the next two tables are relative to `tooling/src/main/java/org/totipo/tooling/`; test names are under the corresponding `tooling/src/test/java/org/totipo/tooling/` package. LOC is physical lines, including blanks/comments. Shared tests are listed once in section 4; attribution below is overlapping, not additive. All standalone code is build/release support, **none is shipped to Maven consumers**.

| Component / LOC | Callers, execution stage and inputs → outputs | Purpose, boundary and overlap |
| --- | --- | --- |
| `Main.java` 48 | Nix launcher, Gradle Exec, CI/workflows; args/environment → dispatch/status; StrictJson smoke | CLI/support wiring, no independent authorization; smoke repeats parser tests |
| `StrictJson.java` 96 | Publication JSON, GitHub JSON, smoke; bounded bytes/profile → Jackson parser/exact integer | Bounded duplicate-rejecting parse; existing Jackson only; tests 84 plus publication profiles |
| `publication/PublicationVerifier.java` 254 | `PublicationCli.verify`, `AcceptedInventory.verify`; source/classes/docs + staging/version → manifest | Independent serialized POM/module/archive semantics; not independent compilation; `PublicationTest` 349 shared |
| `publication/PublicationManifest.java` 93 | Verifier, CLI, accepted/remote paths; schema-1 bytes ↔ ten typed artifact identities/hashes | Safe transport/identity validation; does not authorize publication; overlaps input grammar and hash comparisons |
| `publication/PublicationRepository.java` 52 | Verifier/CLI/AcceptedArtifacts; staged files + manifest → checks/fingerprint/copy | Byte freshness and Nix output installation; copying is packaging, not semantic validation |
| `publication/FilesSupport.java` 119 | Publication package; paths → anchored bounded reads/writes/inventory | NOFOLLOW/SecureDirectoryStream and atomic directory moves; duplicates `EvidenceFiles`; limits useful, concurrent hostile-writer defense usually outside actual job model |
| `publication/Json.java` 122 | Manifest, module metadata, fingerprint; JSON ↔ maps/serialized text | General tree decode plus Python-compatible serializer/sorting; much historical format convention, not external authorization |
| `release/AcceptedArtifacts.java` 54 | CLI compare/signed/remote, Gradle upload gate; manifest/staging/expectation + signing tree → acceptance | Ten-hash cross-build and actual unsigned signing-input identity; `AuthorizationTest` 137 shared; fingerprints repeated |
| `release/AcceptedInventory.java` 23 | CLI inventory/compare/signed/remote; source/staging/version → freshly verified compact manifest | Wrapper around verifier; repeatedly redoes Gradle's earlier semantic acceptance; `compare` helper has no production caller |
| `release/EvidenceFiles.java` 75 | AcceptedArtifacts, TestResults, WorkflowPins; evidence/build paths → bounded anchored reads and signing pair | Secure filesystem duplicate and recursive signing-output discovery; Authorization/XML/workflow tests shared |
| `release/RemoteArtifactVerifier.java` 109 | CLI remote; accepted local hashes + canonical HTTP → verified ten-pair result | Actual external distribution boundary; only armor presence, no cryptographic signature verification; `RemoteVerificationTest` 162 shared with transport |
| `release/HttpQueries.java` 163 | Central absence and remote artifact reads; canonical URI/deadline → bounded response/digest | JDK HTTP, no redirects, streaming digest/subscribers; two acquisition implementations; unused GitHub GET endpoint branch remains from history |
| `release/ReleaseInputs.java` 31 | CLI identity/artifact operations; env → validated version/SHA/initial dispatch match | Input safety and identity; duplicates precheckout shell intentionally before checkout; ReleaseTest 87 shared |
| `release/SourceFiles.java` 88 | SourceIdentity/Finalizer; source root → bounded text, notes, exact pin and snapshot validation | Useful read helper, but repeats library integrity tests and shell hashes; document whitespace/heading conventions |
| `release/SourceIdentity.java` 71 | CLI initial/eligible/tagged/reserve/finalize/signed; source + API → validation or create-only tag | Mixed documentation checks and meaningful remote ref/source identity/reservation; FinalizationTest 118 shared |
| `release/GitQueries.java` 54 | Every initial/eligible/tagged/reserve/finalize command; checkout + canonical fetch → HEAD/clean/main-ancestor proof | Git supplies actual proof; Java adds subprocess/config allowlist. Duplicates every workflow's pretool shell authentication |
| `release/RemoteReleaseState.java` 116 | SourceIdentity, Finalizer, CLI; Central statuses/GitHub JSON → absence/record/maps | Two-POM veto and bounded API decoding; two JSON object decoders exist across packages |
| `release/ReleaseFinalizer.java` 60 | CLI finalize; notes/source/tag + GitHub state → create or validate release | External announcement boundary; no consumer receipt/state machine; Python trailing-whitespace compatibility and static provenance assertion |
| `release/FinalizationProcesses.java` 91 | GitQueries and GitHub API adapter; commands/token via stdin → bounded status/JSON | Process cleanup/timeouts/redaction and curl API. ProcessTest 44 + FakeFinalizationCli 21; no shell in Java adapter |
| `release/TestResults.java` 111 | Local ladder, qualification, release full/focused stages; JUnit XML → totals/critical presence | Detects missing/skipped critical tests; repeats Gradle failure status and exact 3/1 counts; TestResultsTest 91 |
| `release/WorkflowPins.java` 66 | CI and all four release jobs; full checkout workflow/action text → acceptance | External action allowlist mixed with expected workflow names/counts/YAML spelling/inline bootstrap bans; WorkflowPinsTest 57 |

Independent wrapper tree: `tooling/wrapper/src/main/java/org/totipo/tooling/wrapper/WrapperVerifier.java` **137**, test **293**. Nix compiles with JDK only; CI and all four release jobs call it before `./gradlew`. Two file hashes plus a Python-compatible declaration parser; no distribution download or authentication of executed cached distribution bytes. Wrapper tests generate fixtures at runtime; no separate committed tooling fixture-data tree exists. `WorkflowBoundaryTest.java` **158** reads actual YAML/build/settings, executes extracted Bash with fakes, and mutates text. It is a test runner, not installed production code, but heavily affects maintenance.

| Build/configuration component / LOC | Stage, inputs/outputs, dependencies and purpose |
| --- | --- |
| `build.gradle.kts` 195; module builds 22/30; `settings.gradle.kts` 16 | Producer tasks, archives/POM/module metadata, Java 17 check, independent Exec integration, signing freeze and Central plugin. Gradle/JDK/plugin dependency; ordinary staging only unless Central mode explicitly selected |
| `publishing/consumer-smoke/build.gradle.kts` 43, settings 52, properties 5, Java 78, template XML 17 | Independent build; local module/POM qualification and remote Central module/POM verification; coordinates → compile/runtime/API smoke and exact graph assertions; no composite/source substitution |
| `nix/dependencies.gradle` 12 | Fixed-cache acquisition/replay preparation; resolve all resolvable project/buildscript configurations → isolated Gradle-home acquisition state |
| `qualification.nix` 96 | Fixed/offline normal CI and human gate; selected source + fixed cache + pinned JDK/Gradle/tool → tests/publication/consumers and unsigned ten-file output |
| `nix/tooling.nix` 136 | Independent javac, ten standalone runners, smoke/forbidden-import probes, launcher; Jackson from fixed cache and narrow source/fixture filesets |
| `nix/wrapper-verifier.nix` 51 | Independent wrapper javac/tests/launcher and committed wrapper inputs; separate flake check |
| `flake.nix` 77 | Four input families, qualification/tooling/wrapper packages/checks, release-runtime join, developer shell; lock determines tool closure |
| `.github/workflows/ci.yml` 35 | Push/PR read-only; checkout → setup Nix → release-runtime/wrapper/workflow checks → flake qualification |
| `.github/workflows/release.yml` 211 | Manual dispatch; preflight outputs ten-hash inventory; disabled write/environment publisher reserves/signs/uploads; reusable verification follows only success |
| `.github/workflows/verify-release.yml` 168 | Manual/reusable credential-free reconstruction/remote/two-consumer job; disabled separate write finalizer. Does not require earlier publication outputs |
| `.github/actions/setup-nix/action.yml` 38 | Five uses, one per CI/release job: installer acquisition/hash, pinned install action, installed version check, token-config cleanup/restart/effective check |
| `gradle/`, locks, `package-deps.json`, `flake.lock` | External input control, not executable custom verifiers; artifact checksum/coordinate records and dependency/toolchain closure |
| `AGENTS.md` 230; `RELEASE_CHECKLIST.md` 164; `README.md` 278 | Human process/API/build/release documentation; qualification ordering/freeze and recovery rules create maintenance cost beyond executable LOC |

The embedded workflow shell owns bounded precheckout validation, canonical Git acquisition/ancestry, Nix materialization/PATH exports and credential scrubbing; preflight owns normal/offline builds, XML preservation, ten-hash comparison and focused integrity reexecution. Publisher owns secret checks, first-time reservation, signing and one frozen upload invocation. Verification owns reconstruction, anonymous Central checks, distinct temporary consumer homes and final cleanliness. Finalizer repeats bootstrap/source checks but performs no build/publish. These are real callers; installed package presence alone is not an execution claim. All mutation stages are currently disabled.

## 4. Current tooling and test LOC accounting

Metric: Git-tracked physical lines from `git ls-files` and `awk` per-file record counts, including comments/blanks, excluding generated/ignored outputs and binary bytes. This measures maintenance surface, not executable complexity or coverage. No LOC target is imposed.

| Category | Files | Physical LOC |
| --- | ---: | ---: |
| Production libraries (`core`, `storage-nio`, main Java) | 94 | 3,725 |
| Library tests/helpers/testFixtures Java | 50 | 6,160 |
| Standalone tooling main Java, including wrapper | 24 | 2,182 |
| Standalone tooling test Java, including wrapper | 12 | 1,601 |
| Consumer smoke Java, separate support code | 1 | 78 |
| Workflows + local action YAML | 4 | 452 |
| Nix definitions (root + `nix/`) | 4 | 360 |
| Gradle/configuration scripts, properties/catalog/wrapper properties | 11 | 402 |

Tooling subtotal: general main 144 / test 84; publication main 715 / test 349; release main 1,186 / test 875; wrapper main 137 / test 293. Total **3,783**. This is the corrected current source, not J4C section 14's earlier implementation: its subsequent correction removed 20 Java lines net. Tooling main is 58.6% of library main; main+test tooling is 38.3% of library main+tests. Consumers/configuration are excluded from these ratios.

Separate declarative/generated-by-maintenance surfaces: `flake.lock` 279, `package-deps.json` 178, root verification XML 307, consumer template 17, four Gradle lockfiles 48. Standard wrapper scripts total 360 (248 Unix/112 Windows), plus binary JAR; they are upstream bootstrap assets, not authored tooling LOC. Vendored specification/corpus resources are library test data, not custom tooling code and not counted as Java. No binary size or historical review prose is mixed into implementation totals.

The wrapper's 293 test lines exercise 199 fixtures through API and subprocess CLI, plus 190 raw-declaration cases and extra probes. Publication's 349 test lines contain archive/POM/module negatives, synthetic producers, manifest/copy/JSON/profile fixtures. Release's 875 test lines cover source/inputs, inventory/signing, polling/acquisition, reservation/finalization/processes, XML and workflow conventions. These costs matter more than a production-only deletion estimate.

## 5. Threat model and engineering priorities

Protect against accidental implementation regressions, wrong/unexecuted conformance, packaging/configuration mistakes, external artifact substitution relative to reviewed pins, nondeterminism, wrong signing inputs, incomplete/wrong remote distribution and accidental repeated ordinary publishing. Maintain reviewable boundaries and useful failure diagnostics for one maintainer and infrequent releases.

Trust reviewed source, GitHub's execution/permission model, the selected runner, pinned tools and secret-bearing steps. Repository-controlled expected values are useful regression specifications, but not independent authorization against someone able to modify implementation, tests, workflows and pins together. External protection of main/tags/environments and credential ownership must supply that authority. No local validator proves effective rulesets, authenticates a malicious runner or makes an unsigned tag cryptographically trusted.

Independence has several meanings: a verifier compiled without producer APIs can independently inspect serialized outputs, while sharing the same reviewed repository authority. A remote byte comparison crosses an external distribution boundary. A second assertion of a workflow string crosses neither. Controlled inputs and reproducibility do not prove protocol correctness; tests do not prove all possible security behavior. The recommendations preserve these distinctions.

## 6. Map of desired guarantees to current mechanisms

| Desired assurance | Current enforcing components | Independent value / simplest owner |
| --- | --- | --- |
| Correct production protocol/API/security behavior | Core/NIO unit/API tests; 92-case runner and vectors; production code exercised | High; actual executable assertions stay in JUnit |
| Correct r19 corpus/profile and required cases execute | Snapshot/profile tests, Phase2 exact executed set; SourceFiles; XML; focused rerun; sha256sum | Tests provide substantive assurance; repeated same-fixture hashing adds little |
| Reviewed source/version | Dispatch env equality, exact checkout/cleanliness, VERSION, canonical main ancestry, SourceIdentity | Useful; Git provides identity/ancestry; prose conventions weaker |
| Controlled acquired tool/dependency bytes | flake lock, fixed fetch hashes/cache, strict Gradle verification, locks, wrapper distribution checksum/JAR hash, installer hash | Distinct acquisition layers; keep reviewed external hashes, no claim against mutable repository authority |
| Correct Maven identity/content/API dependencies | Gradle configuration + independent POM/module/archive verification + consumer graph | Valuable producer/output corroboration; Central does not know Totipo's desired graph |
| Java 17 compatibility | compile `--release 17`, Gradle class-header task, verifier headers and metadata, consumer compile 17 | Corroboration; consumer runs on JDK25, so it does not independently prove Java17 runtime compatibility |
| Deterministic ten artifacts | archive settings, normal/offline comparison; separate job reconstruction | Reproducibility observation plus controlled inputs; separate paths/jobs detect leaked build state |
| Accepted bytes unchanged before use | manifest ten hashes + whole-staging fingerprint + repeated semantic checks | Artifact hashes useful; repeated same-process acceptance and ephemeral-index freshness overdo it |
| Intended signing inputs | actual Sign tasks + AcceptedArtifacts unsigned input hashes + upload producer freeze/shared gate | High; late signing can regenerate POM/module files, so early staging alone is insufficient |
| One ordinary publishing attempt/version | run attempt guard, concurrency, first-time tag reference creation, protected immutable tag | Durable ref is key; concurrency alone only serializes; Central 404 is not permission |
| Correct Central bytes | independent reconstruction + anonymous canonical ten-artifact hash comparison | High external boundary; not supplied by local build or signature markers |
| Signature files available | local signing success/markers, remote ten `.asc` markers, Central requirements | Presence only; no custom cryptographic verification/key trust exists |
| Real downstream use | local and remote independent module/POM consumers; fresh remote homes; exact graph | Local catches defects before immutable upload; remote catches availability/resolution boundary |
| Correct GitHub announcement | finalizer validates tag/title/state/body/create/readback | External record validation; lesser impact than Maven identity but useful for unattended success/recovery |
| Permission/job/action authority | GitHub scopes, needs/success, environment, protected refs and action commit resolution | Provider guarantee; regex tests can catch mistakes but cannot enforce authority independently |

**Q1:** The artifact-oriented publication package plus AcceptedArtifacts/Inventory/EvidenceFiles/RemoteArtifactVerifier/HttpQueries accounts for **1,139 main lines (52.2%)**; wrapper adds 137. These are the principal assurance-bearing areas, but include copying, serialization and repeated reads, so 52.2% is a responsibility grouping, not a measured independence score. Source/tag/finalization code also validates real external identities. WorkflowPins + TestResults are 177 main lines, with 306 directly associated test lines (57+158+91), and mix useful regression/discovery checks with conventions. It would be misleading to call all remaining code either independent security or dispensable self-checks.

## 7. Independent publication verifier evaluation

`PublicationVerifier.verify` inspects actual serialized artifacts, not Gradle model objects. It compares binary members/bytes to `build/classes/java/main`, source members/bytes to `src/main/java`, and Javadocs to `build/docs/javadoc`. It detects omitted/extra/stale classes, accidentally shipped tests/fixtures/tooling, wrong sources/docs, Java target increases, missing primary artifacts, bad dependency scope/version, bad variant artifact links/checksums and unexpected publication paths. This remains useful even when deterministic builds consistently produce a wrong package.

Contract checks worth retaining: `org.totipo:totipo-{core,storage-nio}:VERSION`, five roles each, Java17 metadata/headers, NIO exposes core at compile time, BC1.86 stays runtime-only, no fixture capabilities/source substitution, matching archive contents and usable sources/docs. The verifier compares against producer compilation/docs outputs; it does **not** independently compile source or establish semantic Java correctness. Source-JAR checks do compare directly to reviewed source bytes.

POM license/SCM/developer presence supports Central distribution quality. Exact developer name/URL/title strings, exact minimal archive-manifest bytes, four exact Gradle variant names, broad serialized `:core` text rejection and Python whitespace/JSON ordering are project conventions. Some are sensible cheap regression contracts, but do not all merit their current strictness. Central validates repository publication requirements, not Totipo-specific dependency scope, Java target or source correspondence. Removing the independent verifier outright would lose those checks even if consumers pass. [Central requirements](https://central.sonatype.org/publish/requirements/).

The ten-artifact manifest is useful current-process/cross-job transport. Exact fields, canonical paths, hashes, duplicate rejection and bounded input prevent accidental wrong identities. Parsing a manifest proves structural validity, not that artifacts were reviewed or authorized. `PublicationRepository.fingerprint` includes local Maven indexes and optional checksums; those files are not among the ten uploaded/reconstructed expectations. Their mutation is a local diagnostic, not distribution identity. Keeping schema 1 for compatibility is inexpensive; inventing a successor is unwarranted.

`Json.java` has a second general object decoder beside `RemoteReleaseState.value` and 122 lines including code-point sorting/ASCII escaping. The byte serialization conventions principally preserve the removed Python implementation. No live Python oracle remains. Option A leaves serialization/fingerprint intact to limit coupling; Option B uses the already available Jackson generator, keeps the same schema, and compares parsed identities rather than historical serialized bytes. Changing fingerprint serialization must be atomic across producers/readers; old temporary acceptance files need not remain authority across code versions. No new format is needed.

Filesystem bounds, regular-file checks, safe derived paths and non-following inventory deserve retention. Requiring SecureDirectoryStream from filesystem root for every read/write and creating directories by temporary-directory atomic moves defends against concurrent hostile ancestor swaps. Actual jobs own private checkout/staging/output trees; signing/upload already trust Gradle to execute in those same trees. This is not an adversarial shared-filesystem service. The complexity is disproportionate there, duplicates EvidenceFiles, limits portability and caused TMPDIR/cross-mount integration requirements in J4B. Simplifying to ordinary bounded NIO plus explicit symlink/path rejection is an acceptable **owner-approved threat-model relaxation**, not equivalent race protection. Leave it outside the recommended first pass.

Copy/install belongs to the Nix install phase. `publication copy` only installs ten accepted files; it is not release upload. Its path derivation/hash checks are useful, but its secure directory machinery should not define verifier semantics. Option B can have the Nix installation loop copy the existing independently derived ten paths and verify destination hashes, then remove CLI copy/directory-creation support. Replacing 20 lines of Java with 50 lines of shell is not progress; evaluate net concepts, and do not copy the whole staging tree or add an inventory format.

Archive dual reading is more defensible than local manifest spelling: local/central ZIP disagreement can give different consumers different members. Keep duplicate/path/size/CRC and dual-view checks for now; it is a bounded part of a 254-line semantic verifier. No extraction occurs. Tests that refresh module hashes before corrupting an archive correctly isolate the archive guarantee and should stay.

## 8. Wrapper/dependency-integrity evaluation

The wrapper verifier authenticates committed JAR/properties **declarations**, not the externally downloaded Gradle distribution. Native `distributionSha256Sum` authenticates the distribution on initial acquisition; Gradle documents that it is not repeated when already downloaded. It does not authenticate the wrapper JAR or protect malicious build scripts. Nix qualification uses pinned packaged Gradle directly, so a wrapper checksum is not a prerequisite to that build; host/release `./gradlew` still executes the wrapper. [Gradle wrapper integrity](https://docs.gradle.org/current/userguide/gradle_wrapper.html).

JAR pin `238e777fcddd7e34f9708186085def2abd6e08e658505b38718d79d74c21abd5` is useful external executable identity. Properties pin `e81b90975868be2513ca00d6a5a01303a406a5cb01a306210b887e983e4b17ae` deliberately rejects all edits, including escaped overrides missed by the historical raw parser. Given successful whole-file hash authentication, UTF-8 decoding and `validateDeclarations` accept only one already known byte string. The parser's Unicode whitespace/line-boundary machinery and 190 semantic cases add **zero additional normal acceptance restriction**. Its test-only bypass remains solely to test superseded behavior.

**Recommendation:** keep a tiny JDK-only two-file hash verifier and its preexecution calls; delete declaration parsing and historical fixture matrix. Keep canonical success, changed JAR/properties, missing files and a few CLI status probes. Independently duplicated constants in tests are regression references under the same authority, not independent approval. Native distribution checksum remains unchanged. A simple `sha256sum -c` with reviewed hashes is a sufficient Option B replacement on supported Linux; native checksum alone is not.

Gradle's setup action offers wrapper-JAR validation, but it is not currently installed and does not replace the complete-properties contract. Do not add an action/dependency just to remove this small verifier. Nix does not repair a poisoned host wrapper cache; private fresh release job homes reduce that assumption. The tiny verifier still does not hash the executed cached distribution. This gap should be stated, not turned into another verifier.

Locks select resolved versions, strict verification checks acquired dependency/metadata bytes, fixed Nix cache enables reviewed offline replay, and flake pins tools/environment. These are distinct sufficient layers for host plus hermetic builds, even though hashes repeat. Retain them. Tooling's Jackson fetch uses the reviewed cache hash and exact canonical URL; searching all cache repositories/versions and hardcoding the catalog version is overvalidation because a direct Nix attribute lookup plus fetch hash already fixes the selected input. Simplify that lookup without weakening the fixed hash or adding a pin source. [Gradle dependency verification](https://docs.gradle.org/current/userguide/dependency_verification.html).

## 9. Release source/tag/finalization evaluation

| Operation | Necessary property | Current implementation / recommendation |
| --- | --- | --- |
| Supplied commit is intended source | Initial main dispatch SHA equals request; checkout HEAD exact and clean | Precheckout shell is necessary before executing source; repeated Java Git checks are corroboration, not new authority. Keep shell; simplify Java duplication |
| Version belongs to commit | Raw VERSION matches requested version | Keep. Exact LF is cheap repository convention; consumer resolution already rejects stale self-lock coordinates |
| Trusted-main reachability | Canonical fresh main fetched; `merge-base --is-ancestor` | Keep Git operation, not moving-tip equality. GitHub branch rules protect main; Java cannot emulate them |
| Existing version cannot ordinarily publish | Existing tag always vetoes, independently of Central | Keep durable tag policy. Two Central POM 404s only provide an extra wrong-version veto |
| First-time protected tag creation | Atomic create-ref success, exact object; uncertain response stops | Existing Java/API is small and correct in shape; GitHub supplies atomicity/rules, tests do not prove live settings |
| Authenticate created/existing tag | Annotated direct commit target and version, readback | Keep; Git tag reads/API can do it. Exact bot email/message whitespace is weaker convention |
| Correct GitHub Release | Existing exact record accepted without modification; absent record create once; ambiguous stop | Keep narrow helper in A; direct API or gh could replace in B, but CLI must not implicitly create missing tags/update releases |

`SourceIdentity.source` additionally enforces README/API prepared-release blocks, required note headings/no TODO, exact consumer lock line layout, full SPEC_PIN file hash and all snapshot bytes. These are not all release authorization requirements. Missing/wrong notes should fail; exact section names and status prose should be review conventions. The consumer will independently enforce resolution locks. r19 tests pin semantic snapshot/profile independently of README/checklist text. Remove full repeated corpus checks from release identity after preserving qualification's real integrity tests; retain VERSION, selected notes/version and existing-tag target checks.

`GitQueries.source` repeats the canonical fetch/HEAD/clean/ancestry already executed before tooling in every job. Its complete local config allowlist can fail harmless checkout/config updates. It adds little protection once reviewed Git/build tooling runs in a trusted fresh runner. Recommendation A retains focused Java HEAD/clean checks where useful, removes a second fetch and the closed config-key list, and keeps pretool shell acquisition plus its no-hooks/no-replace/no-credentials options. Do not remove both owners. Reachability in finalization/verification stays meaningful; it must be checked in each fresh job, not against stale preflight output.

`ReleaseInputs` is only 31 lines and has non-workflow callers; do not delete its grammar just because Bash repeats validation before checkout. `SourceFiles` is a useful bounded source reader after its repeated integrity responsibility is removed. `RemoteReleaseState`/`FinalizationProcesses` avoid logging token-bearing responses and fail ambiguous mutations; replacing them requires preserving that behavior, not merely replacing POST with `curl -f`.

Tag-first is simple durable at-most-one **ordinary invocation** reservation. GitHub concurrency prevents races inside an active group but allows later runs; Central immutability/absence is not an authorization receipt and visibility can lag. Git push without force also has an “already up to date” success problem if used as a fresh-reservation signal. Keep POST create-ref and require first-time success. GitHub documents separate create/update APIs; the current helper uses no update/delete operation. [Git reference API](https://docs.github.com/en/rest/git/refs).

Cost: missing/bad signing credentials or failure before upload consumes the reserved version. That can be surprisingly expensive for one maintainer. Explicit alternative: sign and bind artifacts first, then reserve immediately before upload in the same step; this avoids consuming versions on signing failure while preserving tag-before-upload. Signing may run twice for racing dispatches, but only a successful first ref creation authorizes upload. It changes no ambiguity policy after reservation. **DEFER pending owner policy decision; not part of recommended pass, not changed here.** Never restore post-upload tagging as the only replay guard.

Finalizer's static “92/92 executed” provenance is not fresh XML evidence in its job; it relies on preceding workflow/qualification. Verification-only does reconstruction and consumers, not a full conformance run. Treat this as a statement about qualified source, not proof of new test execution. Option A keeps current body contract; a future prose adjustment could state that distinction without adding evidence machinery.

## 10. Workflow and self-referential check evaluation

`WorkflowPins` is not a YAML parser. It accepts exact canonical line forms and approved versions/comments; fixes the workflow set to three and setup counts to 1/2/2; bans selected shell strings and inline installers. It catches accidental pin/setup changes, but does not inspect upstream action content/runtime or effective permission settings. Its `runtime` field is a locally asserted label checked against a local set. Comment version matching is not action resolution.

`WorkflowBoundaryTest` checks literal false guards, permissions, stage order, one publication spelling, verification secret/uploader exclusions, invocation counts, concurrency names, consumer home strings, build task descriptions, exact fileset membership and source-level Bash markers. Some are valuable accidental regression checks; many are simply “continue to resemble this workflow.” A string match inside a comment/dead shell branch can pass; equivalent valid YAML/shell can fail. Exact invocation counts do not prove number of commands executed. Fake-API reservation tests do test application error propagation/race handling but not GitHub's atomicity implementation.

| Assertion family | Real purpose / decision |
| --- | --- |
| External full SHA action refs | External acquisition identity: KEEP actual refs; review updates. REMOVE duplicate approved-SHA/version/runtime Java registry |
| `$/` local action and reusable workflow resolution | Provider semantics: KEEP references; REMOVE tests trying to reenforce the platform's resolution |
| 1/2/2 setup counts, fixed workflow list, lexical inline-install bans | Local convention: REMOVE; ordinary source review/Actions validation sufficient |
| Activation false guards | Useful temporary accident check: KEEP tiny focused assertion until separately authorized activation |
| Verify job lacks Central/signing secrets/tasks | Meaningful credential/capability boundary: KEEP small regression check and real permissions/configuration |
| At-most-one publication spelling and dozens of stage-text checks | Some useful regression, mostly structure imitation: SIMPLIFY to essential reservation-before-upload/frozen input wiring, no full template |
| GitHub `needs`, success/permission ceilings | KEEP configuration; native enforcement, no test simulation required |
| Fileset source text, exact self-inclusion/exclusion | Self-referential implementation convention: REMOVE; actual build inputs/filesets and review suffice |
| Token-scrub markers, exact step/run extraction | Regression test of actual shell failures, not credential authority: retain compact failure tests while cleanup remains; remove spelling/name duplication |

**Delete WorkflowPins.java and WorkflowPinsTest.java entirely**, their CLI route, repeated workflow invocations and Nix runner entry. Keep external SHA pins in YAML. Reduce WorkflowBoundaryTest to the few material boundaries and executable input/bootstrap failure propagation tests, without adding a parser/framework. Losing automatic detection of an accidental mutable action tag is a real but small review-responsibility tradeoff; GitHub resolves a supplied SHA but does not require the source to use one. If the owner requires automatic repository-wide pin policy, keep the existing narrow scanner with SHA shape only instead of a second registry; do not claim GitHub natively mandates pins.

GitHub owns action-at-workflow-commit resolution for `$/`, job dependencies and permission limits; those are verified provider contracts, not assumptions generated from local tests. [Workflow syntax](https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-syntax). Keep repository/environment protections owner-managed; nothing here certifies effective settings.

## 11. Test/conformance accounting evaluation

`Phase2ConformanceTest.everyImplementedCaseExecutesWithoutSkipping` executes production behavior and records IDs **after** assertions return, requires exact expected/executed sets and 92 cases/category counts. This is much stronger than counting XML rows. `R19ProfileIntegrityTest` checks revision/profile fields, linked hashes, required-case set, physical paths, categories and loader agreement. `SpecSnapshotIntegrityTest` checks all records/actual files, independent pinned key artifacts, specification document identity, and corruption/path negatives. **KEEP the actual protocol/profile/snapshot tests and their corpus.** A checksum pass alone cannot replace behavioral conformance.

`TestResults` checks each module has XML, exact 3 snapshot/1 profile test rows, corpus method-name presence, zero failure/error/skip rows and attributes, duplicates and bounds. It does not inspect 92 individual executions: its printed 92 claim trusts the runner method. It cannot distinguish fresh XML from stale results; source comments correctly admit up-to-date reuse. It does not enforce the complete expected set of all library tests or a fixed 405 total. Therefore it is useful critical discovery/skip accounting, not independent proof of full execution.

Gradle already fails failed tests. Its no-discovered-tests default does not ensure every critical suite was discovered, nor reject every skipped test. Existing optional provider assumptions make zero-skip a Linux qualification policy rather than universal development truth. A small Gradle `TestListener` can enforce skips and observe critical suites in the normal execution; a mere success exit cannot. Current official Test docs expose `addTestListener` and `failOnNoDiscoveredTests`; older closure convenience hooks are deprecated in current docs. Version-specific 9.8.0 docs were unavailable during this audit, so any listener migration must be qualified against the pinned API/configuration-cache behavior. [Gradle Test API](https://docs.gradle.org/current/dsl/org.gradle.api.tasks.testing.Test.html).

Option A keeps the existing hardened XML accounting at one full-suite boundary, removes exact 3/1 row conventions in favor of nonzero successful critical-suite presence, and removes the second focused rerun/accounting plus `sha256sum -c` in Nix/release preflight. Full qualification already runs the same snapshot/profile tests and hashes. XML failure parsing is modest (111 lines), and preserving it avoids a new listener implementation in the bounded pass. Keep missing-critical-suite, skip, failure/malformed/XXE negatives; delete focused-mode/count fixtures when obsolete. No total test-count ratchet is needed.

Option B removes standalone XML accounting using Gradle's actual test events for nonzero critical-suite presence and forbidden qualification skips, while keeping Phase2 exact-case assertions and snapshot/profile tests. This is producer-owned discovery validation with weaker independent report checking, acceptable under the stated authority model. It is not a new evidence format. Do not remove XML accounting until those actual task hooks and fresh sandbox execution demonstrably enforce the intended risk; do not expand this into another test framework.

The fragile “account full XML before focused runs overwrite it” requirement largely disappears if qualification does not rerun the same integrity tests. Preserve full results before intentional diagnostic focused reruns/clean, but do not require source freezing/evidence inventories to compensate for unnecessary sequencing. Developer focused tests remain useful; their XML is still not full-suite evidence.

## 12. Reproducibility and artifact-reconstruction evaluation

Current release preflight does: hermetic Nix full qualification; normal host clean full suite/build/verification/module consumer; forced-offline clean full suite/build/verification/module consumer; immediate XML accounting on each; ten-hash comparison; POM consumer; focused integrity run/accounting/hash pass; semantic inventory generation. Publisher separately rebuilds/accepts ten artifacts and compares preflight. Verification separately reconstructs/accepts ten artifacts before Central comparisons.

| Layer | Unique property / proposed treatment |
| --- | --- |
| Deterministic archive settings, pinned JDK/Gradle/plugins | Preconditions for reproducible bytes; KEEP even if a particular comparison passes |
| Strict locks/verification, fixed cache and sandbox offline | Controlled acquisition/replay and no missing network input; KEEP. A normal/offline byte match alone does not supply this |
| Normal vs forced-offline ten-artifact comparison | Detects acquisition/cache-sensitive output; KEEP release-only for recommended pass. Full Java tests need not execute twice to compare archives, but that is a separate scope decision |
| Independent publish-job reconstruction | Fresh filesystem/job before secrets, comparing qualified output expectation; KEEP, stronger than rereading one manifest |
| Independent verification-job reconstruction | Expected hashes available without prior run output; KEEP for rerunnable verification and source-to-Central binding |
| Semantic acceptance | Detects consistently wrong publication; KEEP once per produced staging, not every inventory/query |
| Staging fingerprints | Detect mutation including disposable indexes/sidecars; SIMPLIFY later to ten-artifact identity if owner accepts reduced local diagnostics |
| Signing-input hashes and upload freeze | Binds producer/signing transition; KEEP; cannot be replaced by reproducibility elsewhere |
| Remote ten hashes / two consumers | Exact external output vs resolution/usability; KEEP both, different boundaries |
| Separate manually exported source rebuild | Historical engineering experiment validating path/cache independence; DEFER repeat to build-tool/determinism changes. It is not a current permanent workflow stage or an extra gate every release |

Nix output is not currently compared to host output; it establishes controlled qualification, while normal/offline and job reconstructions establish observed equality across those producer contexts. Do not describe Nix as automatically proving all release environments produce identical bytes. Publish/verify rebuilds use the wrapper with the Nix-supplied JDK; release-runtime itself does not include Gradle. Qualification uses Nix packaged Gradle. This duplicated acquisition is intentional today, not a single universal Gradle execution path.

Minimal option can run the full tests once in hermetic qualification, retain normal/offline **artifact-producing** builds and both local consumer modes once, then separate publisher/remote reconstruction. It loses repeated runtime regression execution under host acquisition modes, not the deterministic bytes comparison. Moving to only one build plus remote equality would lose reproducibility evidence and is not recommended. No normalization, new provenance, additional manifest or Nix-store caching is proposed.

## 13. Central consumer evaluation

One 78-line client and 95 lines of Gradle/settings are already reasonably small. It compiles representative public API, loads runtime-only BC, checks read-only NIO factories and VaultId ownership. Some assertions overlap library tests; that is acceptable at the distribution boundary, though further protocol behavior belongs in library tests. It uses JDK25 with Java17 compilation; do not call it a Java17 runtime matrix.

Both modes remain justified. Module mode detects wrong Gradle variant/capability/target/dependency metadata; POM-only with `ignoreGradleMetadataRedirection()` detects Maven-scope/POM defects that a correct module file can hide. Removing POM-only loses Maven-style resolution evidence; removing module mode loses Gradle-specific metadata evidence. Both execute Gradle, so POM-only is not a real Maven executable/client test. Adding Maven merely for diversity is not required.

Local staging exclusiveContent keeps unpublished Totipo local and BC canonical; it avoids accidentally validating an older remote Totipo. Root `consumerSmoke` forces offline/rerun/no-cache in a standalone build, but local checks normally share the prepared Gradle home; **fresh distinct homes are a remote workflow property**, not all local consumers. Remote settings have exactly canonical Central, no staging/mavenLocal/composite fallback. Distinct temporary homes ensure module metadata/cache cannot mask the POM-only lookup. Template's external BC pins plus verifier-generated Totipo hashes retain strict verification. Keep graph assertions: compile exactly NIO/core, runtime adds exactly BC1.86, no fixture capabilities.

`verifyRepositorySelection` simply rechecks the settings repository declarations it just constructed. Remove this task and its `check` dependency; preserve exclusive repository configuration and actual resolution checks. Source substitution is already rejected in resolved component IDs. Local and remote reuse the same client, not two implementations; local preflight and remote checks are valuable distinct boundaries rather than unnecessary duplication.

## 14. Nix/bootstrap/qualification evaluation

Nix pinning/fixed caches and independent javac are high value. The Java verifier should remain compiled without Gradle/Totipo APIs; explicit classpath/sourcepath provides that boundary. `Forbidden.java` synthetic import compilation and matching compiler diagnostic strings test the declared classpath convention, not an adversarial future import/reflection threat. Remove that negative compilation probe; accidental real forbidden imports already fail compilation. Keep ordinary successful direct javac/tests and a launcher smoke.

Three source projections serve different products: library qualification source filter, standalone Java source plus read-only fixture union, and JDK-only wrapper inputs. That separation is reasonable. Tests that scan the textual spelling of their own fileset declaration are not. `review/` is excluded by qualification's explicit tree/file allowlist, not merely by Markdown extension; tooling fixture union and wrapper fileset also exclude this report. `flake.nix` inputs still affect qualification even when a root file is outside `src`. Preserve Git-aware membership and no staged contents; current freeze is a process boundary, not another permanent source manifest. No stored global freeze framework exists or is needed.

Shared bootstrap's installer hash validates an externally acquired executable; installed version check catches installer skip/unexpected installation but is not independent byte authentication. Rejecting preinstalled Nix addresses the pinned action's “already installed” early exit. These are cheap and useful, not a local self-hash. Keep current 38-line action in A. Its temporary installer copy is fetched by the pinned action's shell through `file://`; avoid replacing it with an unpinned URL.

The pinned install action explicitly passes `${{ github.token }}` internally. Its script adds access-tokens from explicit input or default token, then appends extra config. Thus an empty `github_access_token` input is **not** a proven opt-out. Current cleanup removes declarations from daemon/user configuration, restarts daemon, and rejects nonempty effective tokens before flake work. That complexity has a concrete cause; do not delete it on the assumption that unsetting one workflow variable removes daemon state. [Pinned action metadata](https://raw.githubusercontent.com/cachix/install-nix-action/13d8dd58da0234aa297dedd986986ccb8e7f3e24/action.yml), [pinned installer adapter](https://raw.githubusercontent.com/cachix/install-nix-action/13d8dd58da0234aa297dedd986986ccb8e7f3e24/install-nix.sh).

Token cleanup can potentially become one verified empty effective configuration, but daemon reload and duplicate configuration ordering need real installer qualification. There is no material reason to take that risk in the first pass. Pin upgrades must reconsider behavior. Signing/Maven credentials enter only runtime steps outside derivations; keep that separation. Installed-tool identity checks are diagnostics, not a substitute for reviewed installer/fetch identities.

Five fresh jobs install Nix/materialize release-runtime. Publication/reconstruction require pinned tools; finalization only reads source/GitHub yet inherits all standalone tooling/tests and wrapper runtime wiring. It could use runner Git/curl/gh in Option B, losing identical pinned finalizer environment and introducing hosted-runner version assumptions (gh is not in current release-runtime). Keeping Nix there costs installation/reacquisition but little custom code. No new timing was measured; historical host gate seconds do not measure current CI bootstrap bottlenecks. Do not propose caching or job merging solely from speculation. Keep read/write job separation.

Developer jailed-agent inputs and extra substituter policy are not required for Maven qualification semantics, but provide developer convenience. Do not delete those tools in a release simplification pass without owner preference. They enlarge lock updates; **DEFER** isolating developer-shell inputs. Root nixpkgs indirection should be documented accurately, not automatically repinned.

## 15. Duplicate or overlapping mechanisms

| Overlap | Same assertion or different boundary? | Action |
| --- | --- | --- |
| Properties hash then raw declarations | Same immutable byte snapshot; parser unreachable for all changed inputs | Delete parser/old matrix |
| Wrapper JAR vs distribution checksum vs Nix Gradle | Different acquired executables/paths | Retain sufficient controls for each actual execution path |
| Gradle verifyPublication then inventory/compare/signed/remote semantic verify | Same source/staging repeatedly; no new producer output in many calls | Verify once per produced staging, rehash when crossing signing/use boundary |
| `checkArtifacts` plus fingerprint | Ten files read twice; fingerprint additionally includes local nonuploaded metadata | Keep freshness, avoid repeated reads; index diagnostics optional later |
| FilesSupport vs EvidenceFiles | Independently maintained root-anchored file access implementations | Consolidate/simplify only with explicit filesystem threat decision; no third helper framework |
| Json.value vs RemoteReleaseState.value | Two tree decoders; different schema clients | Use existing Jackson/common existing decoder if net smaller; do not merge schema validation |
| SourceFiles corpus hashing, snapshot test, sha256sum, focused rerun | Same pinned fixtures repeatedly, not independent upstream acquisition | Retain snapshot/profile/conformance tests, remove repeated release/focused/hash passes |
| XML zero failures/errors vs Gradle exit | Same producer status; missing/skips add value | Retain small critical accounting in A, delegate failure status |
| Shell Git identity/fetch vs Java GitQueries | Same job/source/canonical main with no new meaningful boundary | One acquisition owner per job, tiny later HEAD/clean checks as needed |
| Library class-header task vs verifier headers | Producer output vs packaged bytes; useful corroboration | Keep, low cost; no need for extra bytecode verifier |
| POM/module explicit graph vs consumer graphs | Serialized contract vs actual downstream resolution | Keep meaningful graph checks; complementary diagnostics |
| Local vs Central consumers | Preupload packaging vs external availability/resolution | Keep both boundaries/modes |
| `WorkflowPins` registry vs YAML SHA | Same mutable repository assertion; YAML SHA used by GitHub | Remove duplicate registry/conventions, review actual pins |
| Workflow permission/needs text tests vs GitHub enforcement | Tests detect edits; provider supplies authority | Shrink tests to material accidental-regression boundaries |
| ReleaseFinalizer body/spec checks vs CLI SourceIdentity | Same source repeatedly | Derive once; retain external record validation |

**Q3:** Yes: two filesystem readers, two JSON tree decoders, shell/Java Git checks, repeated corpus validators, whole-hash-plus-parser wrapper verification and multiple entry paths to the same publication semantic verifier. Not all constitute independent implementations; repeated calls to one verifier are repeated work, whereas two source/corpus validators actually duplicate logic. Both consumer modes and remote/local identity checks are not this kind of redundancy.

## 16. KEEP/SIMPLIFY/REPLACE/REMOVE/DEFER classification

Decisions refer to a future authorized pass, not permissions to modify now.

| Significant mechanism | Decision | Specific reason |
| --- | --- | --- |
| Production/API/security tests and 92-case execution | KEEP | Primary correctness assurance; source hashes/build success cannot replace assertions |
| Snapshot/profile integrity tests | KEEP | Detect wrong/changed pinned fixtures and incomplete loader/corpus/profile |
| Standalone semantic publication verifier | KEEP | Independently inspects meaningful producer outputs; only 254 core semantic lines |
| Publication helper/manifest/JSON surface | SIMPLIFY | Transport useful, Python formatting/full-staging conventions disproportionate; keep schema |
| Repeated semantic inventory acceptance / AcceptedInventory wrapper | REMOVE | Existing accepted manifest plus fresh artifact hashes suffice after each producer; delete wrapper and redundant entry path |
| Signing input binding/freeze/shared upload gate | KEEP | Late producer regeneration is a real preupload risk; verify actual bytes |
| Remote exact bytes and bounded acquisition | KEEP | Meaningful external output; local checks do not prove Central identity |
| Armor presence checks | KEEP | Cheap availability evidence, honestly limited; no cryptographic trust claim |
| Remote transport's unused GitHub-read endpoint branch | REMOVE | All production GitHub API calls go through curl adapter; no live caller |
| Wrapper JAR/properties identity checker | SIMPLIFY | Small independent preexecution check justified; parser and 293-line matrix are not |
| Native distribution checksum/Gradle verification/locks/Nix cache | KEEP | Different acquisition/resolution guarantees; avoid dependency/pin churn |
| Duplicate-Jackson identity search in tooling.nix | SIMPLIFY | Direct fixed coordinate/hash lookup pins actual selected bytes without policing unused records |
| Source VERSION/exact checkout/main ancestry | KEEP | Necessary source-to-release binding; use existing Git operations |
| Repeated Java canonical Git fetch/config allowlist | REPLACE WITH EXISTING FACILITY | Existing pretool Git workflow operations already supply proof per job |
| README/API blocks, notes heading/lock-layout release enforcement | REMOVE | Review and real version/notes/consumer checks suffice; cosmetic edits should not prevent release |
| SourceFiles full pin-document/snapshot hashing at every operation | REMOVE | Qualified snapshot/profile tests already establish substantive r19 integrity; keep selected source identity |
| First-time tag reservation/existing-tag veto | KEEP | Durable ordinary replay prevention, provider-enforced create operation |
| Tag-before-signing order | DEFER | Meaningful availability/usability tradeoff; change only by explicit owner policy |
| Narrow finalizer | KEEP | 60-line exact create/validate supports automatic success/retry; shell rewrite may increase cost |
| API process/redaction/time limits | KEEP | Handles actual network/mutation uncertainty and credentials; fake process tests useful |
| WorkflowPins + WorkflowPinsTest | REMOVE | Duplicate registry and workflow template rules; pins stay in YAML, authority belongs to GitHub/review |
| WorkflowBoundaryTest | SIMPLIFY | Few material regressions worth retaining; remove count/name/fileset/template constraints |
| XML accounting | SIMPLIFY | Critical suite presence/skips useful; exact row counts/focused mode redundant |
| Native test-event replacement | DEFER for recommended pass; REPLACE in B | Existing Gradle capability sufficient with explicit discovery checks, but migration has cache/API risk |
| Focused integrity rerun/accounting + shell hashes | REMOVE | Full suite already exercises same fixture integrity; no separate trust boundary |
| Deterministic builds/ten-hash comparison/reconstruction | KEEP | Distinct reproducibility/source/distribution guarantees |
| Duplicate host full test executions solely for artifact comparison | DEFER in A; SIMPLIFY in B | Tests once plus independent artifact rebuilds sufficient under trusted tool model, but changes qualification policy |
| Local/remote module + POM-only consumers/graph assertions | KEEP | Both metadata ecosystems and distribution boundaries need real use |
| Consumer verifyRepositorySelection | REMOVE | Reasserts immediately constructed settings; actual resolution/configuration remain |
| Secure directory handles / installation copier | SIMPLIFY, owner decision in B | Concurrent hostile tree swaps outside actual private-job threat; retain bounds/no-follow checks |
| Shared Nix installer acquisition/config cleanup | KEEP | Real external executable and inherited daemon credential state; 38 lines already small |
| Synthetic forbidden-import compile | REMOVE | Explicit javac classpath already rejects real forbidden imports; compiler diagnostic string test adds convention |
| Three product-specific source selections | KEEP | Different build products/test fixtures, not three authorizations |
| Fileset text assertions/source self-membership test | REMOVE | Review actual derivation inputs; no independent authority |
| Git-aware source membership/freeze process | KEEP | Prevent accidentally qualifying different bytes; no new persistent fingerprint machinery |
| Finalizer without Nix / developer-input isolation | DEFER | Potential reduced operations, but runner/tool/owner assumptions change; no measured bottleneck |

## 17. Conservative simplification option

**Option A:** retain architecture and all important boundary checks, trim obsolete owners and conventions. Keep standalone JDK25/Jackson verifier, schema 1, fingerprints and secure filesystem operations, small wrapper hash check, source/tag/API/finalizer helpers, reduced XML/workflow regression tests, fixed Nix qualification, release normal/offline ten-hash comparison, publisher/verify reconstruction, signing freeze, both local/remote consumers and current tag-first policy.

Remove WorkflowPins/its tests and runtime route; wrapper declaration parser/matrix; AcceptedInventory wrapper/repeated semantic paths; unused anonymous GitHub-read endpoint branch; source prose/lock-layout/full-corpus revalidation; redundant Java fetch/config allowlist; focused integrity reexecution/accounting/hash stage; self-referential fileset/build/step/count checks; consumer repository-selection task; forbidden-import compile. Simplify Jackson fixed-record lookup. Keep a tiny temporary false-guard test, credential-free verifier boundary and actual input/bootstrap failure tests in the existing WorkflowBoundaryTest.

Guarantees remain: protocol/security conformance, r19 input integrity, controlled dependencies/tools, independent package semantics, reproducibility, source/signing/Central exact identity, ordinary replay prevention, both forms of consumption and exact announcement. Deliberately lose automatic workflow pin allowlist/template policing, repeated corpus checks, closed Git config layout enforcement and exact critical-test method counts. GitHub does not prevent every semantic workflow mistake; review assumes that responsibility.

No new dependency/provider assumption; private runner trust was already required. Successful release inputs/dispatch and verification recovery remain unchanged. Risk low to moderate: CLI caller removal and Nix fixtures/wiring must close; artifact/signing interfaces must still reread current bytes rather than blindly trust parsed evidence. Estimate net **520–740 Java + 35–70 config lines**. No library behavior change, settings change or activation is included.

## 18. Minimal sufficient tooling option

**Option B:** A plus removal of most source/announcement Java and generalized local evidence infrastructure. Retain the semantic publication verifier, existing schema-1 ten hashes, compact signing-input and remote-byte checks with bounded HTTP, actual library/corpus tests, two-mode consumer and fixed Nix build. Use ordinary workflow Git for source/ancestry and GitHub create/ref/release API operations; Gradle test events enforce qualification skips/critical discovery; Linux `sha256sum` replaces wrapper Java; Nix owns installation of the ten accepted artifacts. Simplify existing JSON writing via Jackson, remove legacy Python serialization requirements and whole-staging index/sidecar freshness enforcement. Ordinary regular-file/no-follow/bounded reads replace directory-handle race protection under private-tree ownership.

Potential whole-file removals beyond A: wrapper main/test/Nix derivation; TestResults/TestResultsTest; GitQueries; ReleaseFinalizer; FinalizationProcesses/ProcessTest/FakeFinalizationCli; most SourceIdentity/RemoteReleaseState source/GitHub responsibilities. Do not promise all these disappear without replacement cost: tag readback/exact release conflict handling and safely encoded JSON remain necessary workflow operations. Use currently available Git/curl; gh is only an alternative with a new runner-tool assumption, not an existing pinned dependency. Never use a command that silently creates tags or overwrites releases. Official [release API](https://docs.github.com/en/rest/releases) supports explicit create/read, but does not implement Totipo's exact body policy for us.

Smallest credible pipeline: reviewed exact source → fixed hermetic full tests/conformance plus independent semantic artifacts and both local consumer modes → normal/offline ten-artifact reproducibility → fresh publisher reconstruction/compare → first-time protected tag reservation → actual signing/binding → sole upload invocation → fresh verification reconstruction → ten canonical Central hash/signature-presence pairs → fresh separate module/POM consumers → separate write-only announcement creation/validation. Use native GitHub permissions/success/concurrency; no controller/recovery state machine. A manual Verify Release repeats only reconstruction/remote/consumers/finalization without publishing credentials. Full tests need run once per release qualification, not in every artifact-producing build. Moving reservation after signing is only the separately identified owner alternative in section 9.

Lost confidence/diagnostics: independent XML structural/status rechecking; automatic workflow conventions; hostile ancestor-swap defense; local sidecar/index freshness; exact developer/manifest formatting checks if loosened; repeated host-mode test execution; pinned finalization-tool environment if Nix omitted there. Retained artifact path/hash checks, package membership/Java17/metadata semantics and signing freeze still matter. This does not weaken cryptographic signature verification, because none currently exists.

Assumptions: hosted runner Git/curl (or gh) are trusted if Nix omitted, provider operations/status parsing are correctly wired, Gradle discovery listener behaves under pinned version/cache settings, private job trees remain privately owned. Estimated net **850–1,250 Java lines**, but **30–100 net additional configuration lines** may replace APIs/tests; range includes replacement shell and native hooks. Medium/high migration risk, especially ambiguous mutation responses, encoding, signing task output paths and cache behavior. Maintainer success experience can remain identical. A superficially smaller Java count could still be harder to maintain; this is why B is not the recommended single pass.

## 19. Recommended end state and rationale

Choose **Option A**, with no filesystem/serialization/finalization rewrite or tag-order change. It removes whole obsolete concepts, not high-value external checks. It leaves current manifest/fingerprint/copy contracts intact while cutting repetitive calls. The verifier's core semantics and the finalizer are already small enough; deletion would move important logic into shell and require new failure fixtures. B is a credible lower bound, not a mandatory next milestone.

**Q2:** Delegate job dependency/permission/action resolution and immutable-ref protection to GitHub; HEAD/ancestry to Git; execution/failure/resolution/signing/publishing to Gradle; fixed inputs/offline isolation to Nix. Do not delegate Totipo-specific package contents, dependency API scope or expected Central bytes to providers that do not know those expectations.

**Q4:** First complete deletions: WorkflowPins.java, WorkflowPinsTest.java and AcceptedInventory.java; remove their callers. First partial deletions: WrapperVerifier.validateDeclarations and historical tests, focused accounting path, source prose/corpus revalidation, WorkflowBoundary fileset/template checks. None requires deleting the semantic verifier, actual protocol tests or remote verification.

**Q5:** Section 18 gives the smallest credible pipeline. The recommended implementation retains more diagnostic helpers but no extra authorization controller.

**Q6:** Yes. None of A changes version + exact reviewed SHA + one manual dispatch on success. Verification remains independently rerunnable without publishing credentials; uncertain publication never initiates another invocation.

**Q7:** Lose automatic detection of some repository convention/pin/source-layout mistakes and repetitive corroboration; accept ordinary code review for them. Retain independent meaningful output checks. Losing parser-convention/duplicate-integrity diagnostics is justified. Losing preupload binding, remote identity, conformance or a consumer metadata mode would not be justified by this audit.

**Q8:** Highest long-term benefit in one pass is shrinking wrapper/workflow guardrails and removing duplicate validation owners/calls. Resist rewriting the whole release API/HTTP layer; its failure behavior is more valuable than a cosmetic Java LOC reduction.

## 20. Prioritized exact-file removal/refactoring plan

All paths below refer to current files; no implementation is authorized by this report. Each item is independently performable unless dependency noted. Update `AGENTS.md`, `RELEASE_CHECKLIST.md`, `README.md` in the eventual pass to describe the resulting policy; historical reports stay unchanged. The following is findings-based scope for one bounded pass, not an implementation agent prompt.

### P1 — Wrapper parser and obsolete test matrix (highest confidence)

- Files: `tooling/wrapper/src/main/java/org/totipo/tooling/wrapper/WrapperVerifier.java`, its `src/test/.../WrapperVerifierTest.java`; README wrapper guidance.
- Current: exact JAR/properties hashes, then raw parser; hundreds of Unicode/text fixtures execute through parser, API and CLI.
- Proposed: only hash both files with existing JDK API; retain CLI/status behavior and current pins. Remove URL/declaration/whitespace helpers and semanticFixture/parser equivalence/reflection pin machinery.
- Safe because every changed properties byte already fails authentication. Required external distribution integrity remains native wrapper checksum; JAR/properties hashes remain independent preexecution checks.
- Retain canonical success, wrong/missing JAR/properties, properties override rejection and representative CLI failures. Old harmless-format/Unicode/raw-parser acceptance tests become obsolete; no need for a new matrix. No build/Nix dependency change; existing runner remains.
- Validate direct JDK-only compilation/runner and actual committed file hashes, then applicable standalone guardrails. Risk low; independently performable.

### P2 — Delete workflow convention validator; shrink boundary tests

- Delete `tooling/src/main/java/org/totipo/tooling/release/WorkflowPins.java`, `tooling/src/test/java/org/totipo/tooling/release/WorkflowPinsTest.java`.
- Edit `ReleaseInspectionCli.java`, `WorkflowBoundaryTest.java`, `nix/tooling.nix`, all three `.github/workflows/*.yml`, AGENTS/README/checklist runtime commands. Remove the production CLI case, five repeated workflow-pins calls and runner entry; keep actual external SHA refs and setup action.
- Current: duplicate action allowlist/runtime/version comments, fixed workflow list and counts, formatting/shell text policing. Proposed: review actual pins, rely on GitHub action resolution; retain only material temporary activation/verification-secret/upload-order regression checks in existing boundary runner plus bounded-input/bootstrap failure tests.
- Safe tradeoff: provider supplies actual execution/permissions; review catches mutable-tag mistakes. No claim of automatic pin policy remains. Existing pins do not change. If automatic pin policy is an owner requirement, use the narrower alternative in section 10, not both registries.
- Keep false guards, credential-free verify and producer-freeze/reservation sequencing checks; remove 1/2/2, exact task-description/step/file-set strings, duplicate `needs` semantics mutation matrices. Keep tests of our shell failure propagation, not GitHub's job graph implementation.
- Validate read-only workflow review, source caller scan and standalone runners; future normal Java gate exercises launcher integration, human Nix covers new source projection. Risk low/moderate; P2 must update its own test references atomically. No action parser/dependency added.

### P3 — One semantic acceptance per staging; delete AcceptedInventory

- Delete `tooling/src/main/java/org/totipo/tooling/release/AcceptedInventory.java`; edit `ReleaseInspectionCli.java`, `AcceptedArtifacts.java`, `AuthorizationTest.java`, `RemoteVerificationTest.java`, `PublicationTest.java` only as callers/contracts require; adjust release workflow inventory call arguments if needed.
- Current: verifyPublication writes accepted manifest; inventory and every compare/signed/remote operation independently rerun `PublicationVerifier.verify`, then compare hashes/fingerprints, often multiple times.
- Proposed: keep semantic acceptance in `publication verify` after each producing build; inventory/compare/signed/remote read that existing canonical manifest and check current staged bytes. Derive compact inventory using PublicationManifest, not a new receipt. Keep preflight-vs-publisher comparison, actual signing-input equality and rehash at the upload gate. Delete unused AcceptedInventory.compare/version wrappers.
- Safe because no new producer output is accepted without `verifyPublication`; late mutation still fails fresh hashes. A malicious producer that rewrites implementation/verifier/expectations is outside threat model. Caller-constructed parsed manifest is not called authorization.
- Retain bad artifact/version/path/hash/missing/duplicate cases and changed staging/signing/remote negatives; remove tests solely proving repeated semantic calls or wrapper-object conventions. Preserve semantic verifier tests.
- Wiring: same schema and Gradle independent launcher; no cache/dependency change. Validate real unsigned producer → accepted manifest → inventory/compare, negative changed-byte tests and signing freeze fixtures. Risk moderate if a caller currently relies on implicit semantic verification; audit all CLI routes. Independent of P1/P2.

### P4 — Eliminate repeated integrity and prose gates

- Files: `SourceFiles.java`, `SourceIdentity.java`, `ReleaseFinalizer.java`, `ReleaseInspectionCli.java`, `TestResults.java`, `ReleaseTest.java`, `FinalizationTest.java`, `TestResultsTest.java`, `qualification.nix`, `.github/workflows/release.yml`, `nix/tooling.nix`; process docs.
- Current: full suite hashes fixtures; release identity hashes entire pin/corpus repeatedly; Nix/release focused rerun replaces XML, accounts exact 3/1 and repeats shell hashes.
- Proposed: keep real snapshot/profile/conformance tests in full suite and full accounting once per suite; remove focused command/mode, rerun/shell hash stage and release corpus rehash/full pin-document hash. Keep VERSION/notes identity, source/tag checks; stop enforcing README/API block spelling, note section titles/TODO marker and exact consumer-lock line formatting. Review notes content; real consumer enforces version locks.
- Required guarantee comes from full qualification's snapshot/profile/Phase2 tests and critical discovery/skip accounting; Git reviewed commit determines source. Neither notes formatting nor a second checksum pass adds independent protocol validation.
- Keep all library integrity/corpus tests and their negative fixtures. Keep XML missing-critical/skip/parse/XXE tests; relax exact method count to successful nonzero presence. Delete focused/count-specific tooling fixtures and copied-corpus tooling source mutations; shrink Nix fixture union to actual remaining runner needs.
- Validate full suite accounting before diagnostics, all 92 cases through the real runner, r19 integrity negatives, standalone source/finalizer tests. Future Nix must cover this new single boundary. Risk moderate because current AGENTS policy explicitly requires focused accounting; owner must approve that policy change with implementation. Independent, but apply after P2 if removing source-filter assertions there.

### P5 — Give Git source acquisition one owner per job

- Files: `GitQueries.java`, `ReleaseInspectionCli.java`, `.github/workflows/release.yml`, `.github/workflows/verify-release.yml`, `ReleaseTest.java`, `FinalizationProcessTest.java` where affected.
- Current: canonical pretool shell fetch/ancestry, then Java config whitelist/HEAD/clean/fetch/ancestry in each initial/eligible/tagged/reserve/finalize invocation.
- Proposed: retain canonical shell authentication before running source in every fresh job; Java may keep small HEAD/clean checks after building/signing but removes duplicate network fetch and exhaustive local config whitelist. Continue using exact source SHA rather than advancing-main equality.
- Git supplies canonical acquisition/ancestor proof; job isolation and protected source supply authority. Do not remove sanitization from the remaining Git command or allow fallback to local stale origin/main. No new script/action owner needed.
- Retain wrong SHA/dirty source/bad input/source-tag cases and mutation ambiguity tests. Config-order/key conventions and duplicate-fetch invocation expectations become obsolete. No change to API credentials or tag reservation.
- Validate canonical Git commands against disposable fixtures/qualified workflow source, source changes detected at intended boundaries, main advancement accepted. Risk moderate; independent of artifact work, but do not delete GitQueries if that merely moves its remaining small checks into a larger helper.

### P6 — Small build cleanup with clear callers

- `publishing/consumer-smoke/settings.gradle.kts`/`build.gradle.kts`: remove `verifyRepositorySelection` and check dependency; keep actual exclusive/Central-only metadataSources and graph/runtime tasks. Existing consumers validate both modes; no new test needed for deleting a declaration self-check.
- `nix/tooling.nix`: replace all-repository Jackson identity enumeration/SRI syntax/version duplication with fixed canonical coordinate/version lookup from existing catalog/cache plus fetch hash; remove synthetic forbidden-import compilation/diagnostic matches, keep restricted classpath/direct compilation and launcher smoke. Validate missing/wrong fetch hash fails through Nix's existing machinery at future human qualification; no new verifier fixtures.
- `HttpQueries.java`/`ReleaseTest.java`: delete anonymous GitHub GET endpoint policy/unused SetNames helper, preserve canonical Central absence/artifact constraints, bounded HTTP, no redirects/deadlines. Keep actual Central URL/status/bounds tests; delete tests of unused GitHub endpoint grammar. GitHub API still uses FinalizationProcesses.
- Independent and low risk with caller closure; consumers/build changes require relevant normal/offline qualification under existing ladder. No dependency/pin regeneration merely for these edits. Together modest code savings, useful concept reduction.

### Explicitly outside the recommended pass

Filesystem race relaxation/copy relocation, Python JSON serialization removal, whole-staging fingerprint removal, native TestListener migration, shell/gh API/finalizer rewrite, tag-after-signing reorder, reducing full host release suites and dropping Nix from finalizer. Each has a real tradeoff described above; none is a prerequisite for P1–P6. Keep the implementation pass bounded instead of chaining speculative migrations. No runtime/API/library change, dependency upgrade, release activation or new assurance framework belongs in it.

## 21. Guarantees retained and guarantees consciously relaxed

Retain actual production correctness/security tests, exact portable execution, r19 snapshot/profile integrity, Java17 output, reviewed source/version, strict dependencies/fixed cache, independent serialized publication checks, normal/offline ten-hash reproducibility, independent job reconstruction, ten signing-input identity/freeze, durable first-time protected tag reservation, one ordinary authorized upload invocation, no automatic ambiguous recovery, canonical Central byte comparison, signature presence, both consumer metadata modes and exact GitHub create-or-validate.

Relax duplicated checks of local text/configuration conventions: workflow count/pin registry, README/API status blocks, notes section titles/TODO string, exact lock line layout, full pin-document prose hash, fixed 3/1 method counts and repeated integrity execution. Actual r19 bytes/required-case semantics remain pinned and tested. Pins remain reviewed in their enforcing files. Retain bounds/no-follow/secure directory operations in A, so that threat relaxation is not silently bundled.

No option promises defense against fully mutable repository authority, exactly-once HTTP/Sonatype delivery, cryptographic signature verification/key trust, a Java17 runtime/platform matrix, universal crash durability, all tests' presence from XML alone, or authenticated effective GitHub settings. These limits already exist; avoid reporting retained markers/checks as stronger assurance.

## 22. Estimated production/test/configuration maintenance reduction

Estimates are net of replacement wiring and small retained tests, use the physical metric from section 4, and overlap is accounted for at totals. They are not measured implementation diffs.

| Recommended A cluster | Main Java reduction | Test Java reduction | Config reduction |
| --- | ---: | ---: | ---: |
| Wrapper parser/matrix | 65–80 | 210–245 | Approximately 0 |
| WorkflowPins deletion + boundary trimming | 66–70 | 100–130 | 8–15 |
| Acceptance/source/integrity/Git/unused HTTP cleanup | 55–110 | 30–80 | 15–25 |
| Jackson/bootstrap compilation and consumer self-check wiring | 0 | 0 | 12–30 |
| **Rounded total, allowing coupled edits/replacement code** | **180–260** | **340–480** | **35–70** |

A total 520–740 of 3,783 standalone Java lines (approximately 14–20%), and 35–70 of 1,214 Gradle/Nix/workflow config lines (approximately 3–6%). Library production/tests/corpus unchanged. Consumer Java stays 78. Documentation can become shorter, but no saving is counted; this report itself is excluded review prose, not claimed implementation reduction. Dependency count unchanged; qualifying fewer textual conventions reduces pin/upgrade/diagnostic churn beyond LOC.

B total roughly 850–1,250 Java lines (22–33%), with 30–100 additional net config lines possible and fewer independently compiled runners. It removes more classes but substitutes shell/API plumbing and native test hooks. Operational bootstrap savings unmeasured. Do not add A and B estimates; B includes A. No full removal of artifact semantic/remote/signing checks is included in either estimate.

## 23. Validation needed for a future implementation pass

No validation below was executed in J5A. Under then-current authorized policy, start from clean committed source, record classifications and baseline. Wrapper/CLI/test-runner cleanup needs direct pinned JDK25 compilation and all remaining standalone runners, JDK-only wrapper tests, and changed-byte/invalid-input/ambiguous API fixtures. Workflow changes require exact review of permissions/secrets/precheckout input handling/reservation-to-upload order and unchanged disabled guards. Tests catch regressions, not provider authorization.

At the stable boundary, launcher/publication/consumer/Nix changes require the normal Java/publication/two-consumer gate, full accounting at the agreed single boundary, direct tooling guardrails, and the human final `nix flake check`. A separate host offline pass is justified if acquisition/build/resolution behavior changes; do not run it for parser-only deletion. Independent artifact reconstruction and signing-input fixture coverage must prove no stale manifest or changed bytes are accepted. No real signing/tag/upload is a test substitute.

If altering release reconstruction/build sequencing, perform the separate release normal/offline ten-artifact comparison and disposable source-export reconstruction under the documented qualification process. Do not require release-only reproducibility for a parser-only routine milestone merely by association. No dependency change is expected; only an actual dependency-input change needs human cache updater/review. Freeze all included inputs after those checks; source membership and no staged contents remain requirements. P4's accounting simplification requires explicit policy approval, not silently skipping the existing mandatory gates before that approval.

Minimum retained adverse cases: wrong version/SHA/main reachability; missing/modified snapshot/profile/required cases; missing/changed/extra artifact and bad scope/Java target; changed signing input; duplicate/ambiguous signing output; reserved version despite Central404; ambiguous tag create and existing ref; altered/truncated/oversized Central response; missing signature; either consumer mode failure; exact existing release/no mutation and conflicting release rejection. Keep these concrete boundary negatives rather than test every spelling or historical helper method.

## 24. Remaining genuine design decisions

1. Accept code review in place of automatic external-action pin policy, or retain a much smaller SHA-shape scanner. GitHub does not itself mandate immutable pins. Recommended A accepts review.
2. Approve removing focused integrity reexecution and exact critical method counts while retaining full-suite critical discovery and real integrity/conformance assertions. This changes current process policy, not protocol coverage.
3. Decide whether tag reservation should remain before signing or move immediately after successful signing/binding. Current policy remains; availability versus durable reservation timing is explicit.
4. Decide whether private build trees justify relaxing directory-handle race defenses/whole-staging fingerprints. Recommended pass leaves them intact.
5. If pursuing B, choose whether exact announcement/body and source/API adapters belong in Java or workflow commands, and accept runner-tool/listener assumptions. Java deletion alone is not a success metric.

Cryptographic signature checking or broader runtime/platform support would be new assurance work, not simplification prerequisites. Do not invent them to justify this audit. No additional milestone series is recommended: review A, select any owner policy exclusions, implement one bounded pass, qualify and stop.

## 25. Git status and source-invariance evidence

Initial branch/HEAD and clean status are recorded in section 2. Final intended change is exactly `review/J5A_TOOLING_NECESSITY_AUDIT.md`, intent-to-add only; content remains unstaged and uncommitted. All original tracked source/workflow/input bytes remain equal to HEAD. Report exclusion was confirmed by reading qualification.nix's explicit source selection, tooling source/fixture filesets, wrapper source selection and flake inputs. No new Java/Nix/human qualification is needed for this excluded prose.

Read-only inspection, official provider documentation lookups and line counting supplied the audit evidence. One counting attempt using unavailable `python3` exited 127 without creating a file; counting was completed with existing awk. Version-specific Gradle documentation URLs returned tool errors; current official docs were consulted and version-sensitive migration remains explicitly unqualified. Neither event is a failed qualification gate. `java --version` only inspected the installed runtime. No focused experiment/test was necessary.

| J5A invocation category | Count |
| --- | ---: |
| Baseline broad Java/Gradle runs | 0 |
| Focused Gradle / focused standalone tests | 0 / 0 |
| Standalone compilation/full guardrails | 0 |
| Final full Java gate | 0 |
| Publication verification / local consumers / remote consumers | 0 / 0 / 0 |
| Separate host offline pass / release reproducibility / exported rebuild | 0 / 0 / 0 |
| Agent Nix / human J5A Nix / cache updater | 0 / 0 / 0 |
| Commit/tag/push/dispatch/sign/upload/release/settings mutation | 0 each |

No failed test attempts, reruns or qualification invalidations: no executable input changed. No previous milestone's owner-reported Nix result is claimed as a J5A run.

Final checks: `git diff --check` and `git diff --cached --quiet` pass. `git diff --exit-code HEAD -- . ':!review/J5A_TOOLING_NECESSITY_AUDIT.md'` passes. Branch/full HEAD unchanged; exactly one nonignored intended new report; no generated output added. Both activation expressions remain byte-identical to HEAD.

`git status --short --untracked-files=all`:

```text
 A review/J5A_TOOLING_NECESSITY_AUDIT.md
```

`git diff --name-only`:

```text
review/J5A_TOOLING_NECESSITY_AUDIT.md
```

`git diff --cached --name-only`: empty. Intent-to-add remains for owner review/commit. Stop after J5A; no implementation, activation or signing rehearsal begun.

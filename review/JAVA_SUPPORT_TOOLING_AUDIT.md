# Java support/tooling architecture audit

## 1. Starting state

Read-only architecture / support-surface audit, classified `DOCS_JAVADOC` / documentation-only audit. Date: 2026-10-11. No executable input changes are authorized or proposed as part of this milestone.

Before inspection, ran the five requested commands in order: `git branch --show-current`, `git rev-parse HEAD`, `git status --short`, `cat VERSION`, `cat SPEC_PIN.md`.

- Branch: clean `main`; status output empty.
- Starting HEAD: `27f47cb076bf3fdd03183eaae603288abb80ad6c`.
- VERSION: `0.2.0` with final newline.
- Spec: v1/r19, commit `cdb4e91be1c6d3704874b2b92457ffe7be5e9084`, profile SHA-256 `7242fc557a7ca56452c934bedc5e7dc2835248f5cd36fa220f05588dc967ec80`, 92 cases / 99 upstream files.
- Required committed prerequisites: `b03f5b2` qualification ladder, `ce00f0c` subscriber threading, `f0a0286` observation composition, `8b24b8a` release-version ownership, `27f47cb` GitHub Actions audit/pin guard. All are ancestors visible in recent main history.

**Answer to the primary question:** the minimum is one Gradle producer, one artifact semantic verifier and inventory format, one independently resolved consumer project exercised in two metadata modes, one hermetic Nix qualification composition with fixed dependencies and wrapper identity, and one release controller enforcing exact source, approval, signing-input identity, retry safety, normal/offline equality and remote-byte verification before tags. Preserve the Java semantic corpus and integrity guards. Python is currently a small standard-library implementation of necessary responsibilities, not evidence that an additional Java framework is needed.

The clearest removable component is the uncalled M0 bootstrap. Larger savings come from fewer owned version slots, shared Gradle task definitions, and one orchestration contract reused by local/release paths. Independent verification must survive these changes.

## 2. Methodology and limits

Inspected tracked files with `git ls-files`, `rg`, file reads, Python AST/counting in inline analysis, and local Git history. Read every tracked `.py` file, all Gradle/Nix/workflow implementations, wrapper properties, cache/lock/verification inputs, consumer source and settings, process documentation, integrity/profile/loader tests and semantic accounting. Searched live references outside historical reports, then used history/reports only to establish origins. No newly created audit program or script was placed in the repository.

Evidence anchors:

- [Root Gradle build](../build.gradle.kts), [core build](../core/build.gradle.kts), [NIO build](../storage-nio/build.gradle.kts), [settings](../settings.gradle.kts).
- [Qualification](../qualification.nix), [flake](../flake.nix), [dependency replay bridge](../nix/dependencies.gradle), [wrapper identity verifier](../nix/verify-wrapper.py).
- [Publication verifier](../publishing/verify-publication.py), [release controller](../publishing/release.py), [release tests](../publishing/test_release.py), [preparation tests](../publishing/test_prepare.py).
- [Consumer build](../publishing/consumer-smoke/build.gradle.kts), [consumer repositories](../publishing/consumer-smoke/settings.gradle.kts), [consumer Java](../publishing/consumer-smoke/src/main/java/ConsumerSmoke.java).
- [CI](../.github/workflows/ci.yml), [release workflow](../.github/workflows/release.yml), [checklist](../RELEASE_CHECKLIST.md), [README](../README.md), [qualification rules](../AGENTS.md).

This is static architecture evidence, not fresh execution qualification. No Gradle, publication verifier, release CLI, Python test suite, Nix, workflow dispatch or external network verification ran. Existing reports' successes are not claimed as this audit's results. Local files cannot prove hosted environment protection, current Central state, action upstream metadata, or undocumented operator usage.

LOC below is approximate: nonblank lines excluding lines beginning with common comment markers. Multiline strings, braces and shell heredocs remain counted; Python docstrings can add a few lines. These are consistent surface estimates, not parser-normalized statements. Generated data and historical prose are separated from implementation. Counts describe starting HEAD, excluding this report.

## 3. Language inventory

| Language/surface | Files | LOC approx | Executable entry points | Test files / boundary |
| --- | ---: | ---: | --- | --- |
| Java, directly scoped support | 5 | 529 | `ConsumerSmoke.main`; JUnit integrity/profile/loader tests | ConsumerSmoke plus SpecSnapshotIntegrityTest, R19ProfileIntegrityTest, VectorCaseLoader and VectorCaseLoaderTest |
| Java, adjacent semantic accounting | 1 | 54 | Phase2ConformanceTest JUnit method | Executes protocol assertions; retain as semantic coverage, not tooling overhead |
| Gradle Kotlin | 6 | 297 | Root/module lifecycle, staging/signing/Central tasks; consumer checks | No dedicated build-logic test files; artifact/consumer checks exercise outputs |
| Gradle Groovy | 1 | 10 | `qualificationDependencies` via init script | No dedicated test; fixed replay qualification exercises it |
| Python | 5 | 1,065 | Two publishing CLIs, wrapper verifier, unittest discovery | 2 test files, 43 test methods: 20 preparation, 21 release, 2 workflow-pin |
| Shell, repository-owned | 1 | 59 | `bootstrap-m0.sh` | None; legacy setup |
| Shell, generated launcher | 1 | 97 | `gradlew` | Wrapper identity is checked, launch scripts are not independently hash-pinned by verifier |
| Windows batch, generated launcher | 1 | 57 | `gradlew.bat` | No Windows execution qualification |
| Nix | 2 | 163 | `checks.java`, `checks.wrapper`, default package, cache app, devShell, formatter | Composition includes existing Java/Python checks |
| YAML | 2 | 248 | Push/PR CI; manual protected release | WorkflowPinTests; no general YAML parser/schema test |
| Other configuration/data | 17 | 842 | `.envrc` loads flake; other files are inputs | VERSION, flake.lock, package-deps, Gradle properties/catalog/locks/XML, ignore/attribute files |
| Markdown outside vendored corpus | 37 | 8,048 | Documented human commands only | Process/API/security/spec docs and historical reports |
| Historical `review/`, all formats | 44 | 10,641 | No execution callers except current version's release notes | Overlaps preceding Markdown row; do not sum |
| Binary support | 1 | N/A | Wrapper JAR | Reviewed JAR hash; do not count as source LOC |

The directly scoped Java count deliberately excludes runtime API tests, test stores, cryptographic/vector assertions and NIO fault fixtures. Those support testing but are not release/build tooling. Their continued need is determined by runtime semantics, not this pruning audit.

Nix and workflow files contain embedded shell; qualification also contains a short embedded Python inventory-copy program. They are counted in their host language rows, not double-counted as separate files. Removing `.py` files would not by itself eliminate Python while that embedded program remains.

Contributor understanding, where **operate** means following a documented command and **edit** means understanding implementation:

| Language/system | Core Java | Dependencies | API contracts | Publishing | Release |
| --- | --- | --- | --- | --- | --- |
| Java/JUnit | Edit | Usually read tests | Edit | Read consumer/API | Review API delta |
| Gradle Kotlin/Groovy | Operate; edit only build changes | Edit Kotlin; understand replay bridge when affected | Operate; edit consumer when API changes | Edit | Operate; understand signing tasks |
| Python | Operate accounting/guardrails | Operate verifier; edit hardcoded dependency expectations if changed | Operate; consumer is Java | Edit verifier | Edit/understand controller and tests |
| Nix | Human operates final gate | Edit/review cache and toolchain boundary | Human operates final gate | Understand composition if resolution changes | Operate qualification, separate from release pair |
| YAML/shell | No normal edits | CI changes only | No normal edits | Release orchestration edits | Understand approval/secret/retry paths |
| Data/Markdown | Read ladder/contracts | Review locks/XML/cache/catalog | Edit contracts | Review published expectations | Edit/review exact owned slots and notes |

## 4. Executable entry points

`R` = reads local inputs only; `L` = mutates local outputs/configuration; `N` = network access, possibly reads; `X` = external mutation. Build/check commands are **L**, even when they only verify behavior conceptually. Security/release sensitivity concerns the guarantee protected, not necessarily secret access. All entries are current unless explicitly labeled otherwise. The table covers custom entry points and standard/plugin task families actually invoked; incidental Gradle plugin tasks are not new independent tools.

| Entry point | Actual callers | Effect | Visibility | Security / release sensitivity | Documentation/status |
| --- | --- | --- | --- | --- | --- |
| `gradlew`, `gradlew.bat` | Humans; Unix launcher also root consumerSmoke and release YAML | L/N distribution/cache/build | Human + internal | Executable supply chain / yes | README/checklist; batch current but unqualified platform |
| `test`, `:core:test`, `:storage-nio:test` | Ladder, Nix, release preflight | L | Human + CI | API/corpus / yes | AGENTS/README/checklist |
| `build`, `check`, `assemble`, `clean`, `javadoc` | Humans, qualification, release task graph | L | Human + CI | Artifacts/bytecode / yes | Root task aggregation; Javadoc/source archives pulled by build/publication |
| Module `verifyJava17Bytecode` | Each module test/check | R after compile L | Internal, directly callable | Java 17/preview exclusion / yes | Build comments + README |
| Module staging publish tasks | Root verifyPublication | L | Internal, callable | Public artifact construction / yes | Build/checklist; default unsigned local-only |
| `verifyPublication` | Humans, consumerSmoke, qualification, release jobs | L | Human + internal | Boundary integrity / yes | AGENTS/README/checklist |
| `python3 publishing/verify-publication.py` | Gradle Exec; direct qualification and documented final gate | L: inventory and consumer XML | Human + internal | Same check as preceding row / yes | AGENTS/checklist; current, repeated invocation is a simplification candidate |
| `consumerSmoke` | Humans, Nix, release pair | L; nested forced offline Gradle | Human + internal | Independent module resolution / yes | AGENTS/README/checklist |
| Consumer `check`, `verifyBoundary`, `runtimeSmoke` | Nested consumerSmoke; direct POM/remote invocation | L; runtime creates/deletes temp dir | Human + internal | Exact compile/runtime closure and linked API / yes | Build + checklist |
| Consumer `verifyRepositorySelection` | Consumer check; direct documented diagnostic | R, Gradle startup may write cache | Internal + diagnostic | No remote staging fallback / yes | Checklist |
| Consumer `-PpomOnly ... clean check` | Human final gate; qualification; release preflight/remote job | L/N if remote | Human + internal | POM resolution without metadata redirection / yes | AGENTS/checklist |
| Consumer `-PremoteCentral` with/without `-PpomOnly` | verify-central YAML; manual fallback docs | L/N, fresh homes | Release internal + human fallback | Hosted repository only / yes | Checklist |
| `release.py prepare V` | Human checklist | L source edits, no network/build | Human | Exact owned version/source metadata / yes | Current |
| `prepare V --dry-run`, `prepare V --check` | Human; --check also preflight | R | Human + release | Write-free preparation validation / yes | Current; --check is not authorization or qualification |
| `release.py inputs` | verify-central YAML; helper internals | R env | Internal | Exact dispatch/version/commit / yes | Workflow + controller; not separately explained in human command guide |
| `release.py identity` | Preflight, after approval, before upload; tag_release internally | L/N: Git fetch updates refs | Internal + fallback | Reviewed HEAD/current-main/tag/notes/spec / yes | Checklist describes; not read-only despite name |
| `release.py state` | Preflight + publishing job | N/L GITHUB_OUTPUT | Internal | Two-GAV absence, same-run upload provenance / yes | Workflow/checklist |
| `release.py upload-ready` | Immediately before Central upload | N | Internal | Stops duplicate/ambiguous deployment / yes | Workflow/checklist |
| `release.py inventory` | Preflight output step | L GITHUB_OUTPUT; reads inventory | Internal | Exact ten path/hash schema / yes | Checklist + workflow |
| `release.py compare` | Local release pair; preflight/publish rebuild; remote internally | R | Human + internal | Ten unsigned hashes equal reviewed inventory / yes | Checklist |
| `release.py signed` | Signing preflight | R | Internal | Actual sign-task unsigned inputs match inventory / yes | Checklist |
| `release.py remote` | verify-central job; fallback | N | Internal + fallback | Exact hosted bytes plus signature presence / yes | Checklist |
| `release.py secrets` | Approved publishing job | L temp GPG home; reads secret env | Internal | Key packet/password prerequisites without importing key / yes | Checklist |
| `release.py tests`, `focused-tests` | Qualification, release preflight, documented human accounting | R XML | Human + internal | Successful evidence with named corpus/integrity tests / yes | AGENTS/checklist |
| `python3 -B -m unittest discover ... test_*.py` | Qualification, preflight, human final gate | L temporary fixtures; mocked network/Git mutation | Human + internal | Release/preparation failure behavior / yes | AGENTS/checklist |
| Discovery `-k WorkflowPinTests` | CI before Nix; targeted human maintenance | R workflow text plus Python loader | CI + human | Approved action repository/SHA/version syntax / yes | CI + refresh report; no production CLI |
| `nix flake check path:.` | Human routine gate, CI with log flag | L/N fixed inputs | Human + CI | Hermetic qualification + wrapper identity / yes | AGENTS/README; never run by audit |
| `nix build` default package | Optional human artifact materialization | L/N fixed inputs | Human | Same qualified unsigned outputs / yes | README; current, not second gate |
| `nix run path:.#update-package-deps` | Human dependency checkpoint only | L/N cache regeneration | Human | Reviewed fixed dependency acquisition / yes | README/AGENTS |
| `qualificationDependencies` | qualification preBuild using init script | L replay into isolated Gradle home | Internal | Resolves library/buildscript configs under locks/verification / yes | nix/dependencies.gradle + qualification comments |
| `python3 -B nix/verify-wrapper.py JAR PROPERTIES` | flake wrapper check | R | Internal, callable | JAR/URL/distribution hash identities / yes | Flake + verifier; no main human guide command |
| `sha256sum -c SNAPSHOT.sha256` | Qualification and focused release step | R | Internal + documented human | All listed upstream bytes / yes | SPEC_PIN/checklist |
| `sha256sum -c -` wrapper JAR | Three release jobs before wrapper use | R | Internal | JAR identity only / yes | Workflow |
| `wrapper` generation; dependencies `--write-locks --write-verification-metadata` | Bootstrap; maintenance human can invoke Gradle | L/N trust input generation | Human maintenance | Supply chain / yes | Legacy script; generation requires review, never qualification |
| `-PcentralRelease=true` sign tasks / `publishAndReleaseToMavenCentral` | Approved publish job; emergency operator | L / X network upload | Protected human + release | Signing secrets and immutable publication / critical | Checklist; generic publish rejected in this mode |
| `release.py tag-release` | Final workflow job only | X Git config/tag/push and gh release creation | Release internal | Immutable annotated exact source tag, consistent release / critical | Checklist |
| `bootstrap-m0.sh` | Nobody outside its own help text in live tracked sources | L/N locks/wrapper/trust inputs; may run Nix | Legacy human | Broad trust-input mutation / release indirect | Superseded M0 setup; DELETE_CANDIDATE |
| `nix develop`, direnv `.envrc`, `nix fmt` formatter | Human development; `.envrc` automatic after authorization | L/N env; formatter edits source | Human convenience | Tool environment; not qualification | README/.envrc/flake; not a release prerequisite |

## 5. Actual call graph

The following edges come from executable references, rather than filenames or historical claims:

```text
push / pull_request
  .github/workflows/ci.yml
    unittest test_release.py -k WorkflowPinTests (full checkout)
    nix flake check
      flake checks.java == packages.default == qualification.nix
        fetchDeps(package-deps.json) / fixed replay
        gradle --init-script nix/dependencies.gradle qualificationDependencies
        offline clean test build verifyPublication consumerSmoke
          module test/check -> compileJava -> verifyJava17Bytecode
          build/staging -> binary + sources + Javadoc + POM + module
          verifyPublication -> publishAllPublicationsToStagingRepository (both)
                            -> python verify-publication.py
                               -> publication-sha256.json
                               -> ignored consumer verification-metadata.xml
          consumerSmoke -> verifyPublication (one dependency execution)
                        -> separate Gradle -p consumer-smoke --offline clean check
                           -> verifyBoundary + runtimeSmoke + verifyRepositorySelection
        release.py tests (full XML, before focused overwrite)
        unittest discover test_*.py -> imports release.py
        direct verify-publication.py (same implementation again)
        separate POM-only consumer clean check
        focused snapshot/profile Java tests -> release.py focused-tests
        sha256sum snapshot
        embedded Python -> copy exactly inventory artifacts to Nix output
      flake checks.wrapper -> nix/verify-wrapper.py (JAR + properties)

human dependency maintenance
  update-package-deps app -> fetchDeps updateScript
    same qualification gradleUpdateTask / replay bridge
  human cache review -> final normal gate -> freeze -> human Nix

workflow_dispatch release
  shell input validation (before checkout)
  preflight -> release.py identity -> inputs/notes/spec/git/GitHub release lookup
            -> prepare --check -> source_integrity + preparation_plan
            -> state -> inputs/Central POMs/prior_upload GitHub attempt history
            -> Python suites + wrapper JAR check
            -> normal + forced-offline Gradle pairs / tests / compare
            -> POM-only + focused integrity + clean Git -> inventory job output
  publish-central (environment release)
    secrets -> gpg packet inspection
    identity/state after approval
    fresh staging -> compare -> sign tasks -> signed
    identity/upload-ready immediately before plugin Central upload
  verify-central
    inputs + shell HEAD/VERSION -> fresh staging -> remote
      compare_inventory -> inventory schema -> poll ten hosted bytes + signatures
    fresh Central-only module consumer + fresh Central-only POM consumer
  tag-and-github-release
    tag-release -> identity(allow_tag=True) -> annotated tag/push -> gh release

human local preparation -> release.py prepare -> exact local plan/notes writes
human local release comparison -> saved normal inventory -> release.py compare
bootstrap-m0.sh -> Nix lock / Gradle wrapper twice / trust inputs / build
  incoming live repository edges: none
```

`release.py` does not call the publication verifier or run a build. It consumes that verifier's JSON and Gradle XML. Its `prepare` branch deliberately has no route to signing/network helpers. Nix's update app is generated by nixpkgs, not a missing local script. Neither `release.py` nor the Python tests invoke `verify-wrapper.py`. The pin guard is reached by unittest, not by a standalone action-check program.

## 6. Component dispositions and origins

Origins use the required vocabulary. `DURABLE_DESIGN_REQUIREMENT` means an enduring guarantee explains the component, not that its current implementation is minimal. `REGRESSION_FROM_REAL_FAILURE` is reserved for documented failures. No inspected substantial component could confidently be labeled `TEMPORARY_DIAGNOSTIC_THAT_BECAME_PERMANENT`.

| Component | Language | LOC approx | Callers | Responsibility | Unique invariant | Origin | Disposition | Confidence |
| --- | --- | ---: | --- | --- | --- | --- | --- | --- |
| Root build/settings | Gradle Kotlin | 124 | Wrapper/Nix/CI/release | Coordinates, compile settings, staging/Central isolation, task orchestration | One VERSION; default build cannot sign/upload; deterministic archive ordering | DURABLE_DESIGN_REQUIREMENT | KEEP_BUT_SIMPLIFY | HIGH |
| Core/NIO build definitions | Gradle Kotlin | 83 | Root tasks | Dependencies, release 17, bytecode checks, fixtures exclusion | Every production class major 61/minor 0; BC implementation scope; NIO core API scope; no fixtures published | DURABLE_DESIGN_REQUIREMENT | KEEP_BUT_SIMPLIFY | HIGH |
| Publication verifier | Python | 121 | Gradle, Nix, human gate | Exact Maven boundary and accepted inventory | No extra artifacts/classes/sources/variants; exact scopes/bytes; verified consumer hashes | DURABLE_DESIGN_REQUIREMENT | MIGRATION_CANDIDATE | MEDIUM |
| Release controller, authority/retry/sign/remote/tag domains | Python | Part of 391 | Release YAML, fallback, tests | External release transaction | Reviewed source -> exact signed/hosted bytes -> immutable tag; ambiguity never permits reupload | DURABLE_DESIGN_REQUIREMENT | KEEP_BUT_SIMPLIFY | HIGH |
| Controller preparation/version domain | Python | Part of 391 | Human, preflight, tests | Bounded version-owned plan and write-free check | No historical-prose ownership; all declared slots consistent before writes; exact matching reviewed notes | REGRESSION_FROM_REAL_FAILURE | KEEP_BUT_SIMPLIFY | HIGH |
| Controller inventory/schema/compare domain | Python | Part of 391 | Preflight/publish/remote/human | Bind all phases to ten path/hash records | Rebuilt and remote bytes match accepted preflight set | DURABLE_DESIGN_REQUIREMENT | MERGE_CANDIDATE | HIGH |
| Controller full/focused XML accounting | Python | Part of 391 | Nix/release/human | Report and assert named successful test evidence | Named integrity suites and full corpus execution assertion cannot silently disappear | DURABLE_DESIGN_REQUIREMENT | KEEP_BUT_SIMPLIFY | HIGH |
| Legacy 0.1.5 status conversion branch | Python | ~10 plus fixture setup | prepare/status_block; tests | One-time old unmarked docs conversion | None needed for future releases from current marked main | LEGACY_FROM_OLDER_WORKFLOW | DELETE_CANDIDATE | HIGH |
| Preparation tests | Python | 285 | Unittest in Nix/preflight/human | Mutation boundaries, ownership, notes and source-integrity negatives | Historical prose preserved and failed validation writes nothing | REGRESSION_FROM_REAL_FAILURE | KEEP_BUT_SIMPLIFY | HIGH |
| ReleaseTests | Python | Most of 247 | Unittest in Nix/preflight/human | Negative network/retry/source/signature/tag contracts | No duplicate upload, byte mismatch, conflicting tag or note misselection accepted | DURABLE_DESIGN_REQUIREMENT | KEEP_BUT_SIMPLIFY | HIGH |
| WorkflowPinTests | Python | Within 247 | CI before Nix, full Python suites | Exact reviewed action allowlist | Canonical uses cannot silently switch to unreviewed repositories/tags/SHAs | DURABLE_DESIGN_REQUIREMENT | KEEP_BUT_SIMPLIFY | HIGH |
| Consumer build/settings | Gradle Kotlin | 90 | consumerSmoke/Nix/POM/release | Independent resolution and exact closure | Published artifacts, no project substitution; module vs POM independent metadata; Central-only remote mode | DURABLE_DESIGN_REQUIREMENT | KEEP | HIGH |
| ConsumerSmoke | Java | 73 | Consumer runtimeSmoke + compilation | External API compilation/link/runtime loading | NIO exposes core compile API; runtime-only BC is loadable; factories link against packaged classes | DURABLE_DESIGN_REQUIREMENT | KEEP_BUT_SIMPLIFY | HIGH |
| Consumer locks/properties/template | XML/lock/properties | ~30 | Independent Gradle/verifier | Strict consumer verification from accepted staging plus fixed BC | No unverified external BC or unresolved source substitute | DURABLE_DESIGN_REQUIREMENT | KEEP | HIGH |
| qualification.nix | Nix + shell/Python | 97 | flake package/check/update app | Hermetic composition and ten-artifact output | Full layered proof in fixed offline environment with retained accounting | DURABLE_DESIGN_REQUIREMENT | KEEP_BUT_SIMPLIFY | HIGH |
| flake qualification outputs and pinned toolchain | Nix | Part of 66 | Human/CI | Single routine qualification + useful unsigned output + cache app | One authoritative fixed-input Java/wrapper gate | DURABLE_DESIGN_REQUIREMENT | KEEP | HIGH |
| nix/dependencies.gradle | Groovy | 10 | qualification preBuild | Populate isolated Gradle resolution from fixed replay | Offline execution resolves all required Gradle/buildscript configurations under strict verification | DURABLE_DESIGN_REQUIREMENT | KEEP | HIGH |
| package-deps.json | JSON | 178 | fetchDeps/check/cache updater | Fixed URLs/SRI for dependency materialization | Acquisition replay is fixed, not live repository resolution | DURABLE_DESIGN_REQUIREMENT | KEEP | HIGH |
| Gradle catalog/locks/root verification XML | TOML/lock/XML | ~322 | Producer Gradle | Approved versions and bytes | Locked resolution and strict checksum verification independent of Nix cache | DURABLE_DESIGN_REQUIREMENT | KEEP | HIGH |
| nix/verify-wrapper.py | Python | 21 | flake wrapper check | Non-executing wrapper identity | Exact wrapper JAR, distribution URL and SHA-256 with duplicate/continuation rejection | DURABLE_DESIGN_REQUIREMENT | KEEP | HIGH |
| Wrapper JAR/properties/launchers | Binary/properties/shell/batch | 164 text | Human/Gradle/release | Reviewed distribution launcher | Reviewed wrapper can acquire exact distribution; contributor build entry remains available | DURABLE_DESIGN_REQUIREMENT | KEEP | HIGH |
| Release inline wrapper JAR checks | Shell in YAML | ~3 repeated | Three release jobs | Authenticate JAR before running wrapper | Release jobs cannot execute a changed wrapper JAR | DURABLE_DESIGN_REQUIREMENT | MERGE_CANDIDATE | HIGH |
| CI workflow | YAML | 23 | Push/PR | Full-checkout action guard then fixed qualification | Hosted commits receive same routine Nix gate and workflow pins are checked outside source projection | DURABLE_DESIGN_REQUIREMENT | KEEP | HIGH |
| Protected release workflow | YAML/shell | 225 | Explicit dispatch | Approval, secrets, jobs and evidence handoffs | No automatic release on push; write authority only downstream of hosted bytes/consumers | DURABLE_DESIGN_REQUIREMENT | KEEP_BUT_SIMPLIFY | HIGH |
| bootstrap-m0.sh | Shell | 59 | Nobody outside own text | Initial wrapper/lock/trust generation | None unique now; maintained inputs already exist | LEGACY_FROM_OLDER_WORKFLOW | DELETE_CANDIDATE | HIGH |
| flake agent dependencies/devShell integration | Nix/JSON | Part of 66/279 | devShell only; lock graph | Jailed Codex developer convenience | No Maven/API/reproducibility invariant unique to agent packages | CONVENIENCE | UNKNOWN | MEDIUM |
| flake-utils eachDefaultSystem | Nix dependency | Few lines + lock nodes | Flake output expansion | Multi-system shell/formatter convenience | Qualification itself is x86_64-linux only | CONVENIENCE | KEEP_BUT_SIMPLIFY | MEDIUM |
| Formatter/.envrc | Nix/shell | ~3 | Human/direnv | Development setup | No release invariant; optional consistent development environment | CONVENIENCE | MERGE_CANDIDATE | MEDIUM |
| Ignore/attribute files | Git config | ~17 | Git/Gradle developer workflow | Ignore generated outputs; preserve upstream bytes | Snapshot line endings unchanged and generated verification inputs not committed accidentally | DURABLE_DESIGN_REQUIREMENT | KEEP | HIGH |
| SpecSnapshotIntegrityTest | Java | 132 | Core test + focused qualification | Safe exact snapshot inventory and independently pinned upstream artifacts | No changed/missing/extra/unsafe snapshot files or pin/profile drift | DURABLE_DESIGN_REQUIREMENT | KEEP | HIGH |
| R19ProfileIntegrityTest | Java | 71 | Core test + focused qualification | Manifest/profile/physical case exact sets and hashes | Requirements actually identify the complete 92-case r19 corpus | DURABLE_DESIGN_REQUIREMENT | KEEP | HIGH |
| VectorCaseLoader + tests | Java | 253 | Vector consumers/profile test | Strict test-only JSON tree/resource loader | No coercion, duplicate keys, unsafe paths or lost unsigned integers in semantic corpus execution | DURABLE_DESIGN_REQUIREMENT | KEEP | HIGH |
| Phase2ConformanceTest | Java | 54 | Core full suite, release XML accounting observes result | Executed-set proof | Every required portable case executes assertions successfully, not just exists | DURABLE_DESIGN_REQUIREMENT | KEEP | HIGH |
| API tests / Javadoc tasks / API and SPI docs | Java/Gradle/Markdown | Outside tooling total | Java test/build; human/API reviewers | Executable contract and packaged documentation | Public API contracts remain tested and documented; source/Javadoc archives match outputs | DURABLE_DESIGN_REQUIREMENT | KEEP | HIGH |
| SPEC_PIN.md + upstream snapshot | Markdown/JSON/hash data | 28 physical pin + 100 snapshot files including inventory | Java tests/release preparation/hash checks | Normative source identity | Exact r19 source/profile/corpus provenance | DURABLE_DESIGN_REQUIREMENT | KEEP | HIGH |
| AGENTS/README/checklist process instructions | Markdown | 604 | Human/agent workflow | Validation ladder, invalidation, source/release procedures | Coverage and approval boundaries cannot silently become optional during maintenance | DURABLE_DESIGN_REQUIREMENT | KEEP_BUT_SIMPLIFY | HIGH |
| SECURITY.md / LICENSE | Markdown/text | Small | Contributors/publication context | Reporting and legal attribution | Maintained vulnerability contact and licensing context; no executable release check inferred | DURABLE_DESIGN_REQUIREMENT | KEEP | HIGH |
| Current version release notes | Markdown | Small | reviewed_notes / tag-release | Reviewed human release description | Exact version's reviewed notes available before publication | REGRESSION_FROM_REAL_FAILURE | KEEP | HIGH |
| Other historical review reports/evidence | Markdown/text/JSON/diff | Most of 10,641 | Human provenance only | Past evidence, API comparisons, origins | No live verification invariant | LEGACY_FROM_OLDER_WORKFLOW | HISTORICAL_ONLY | HIGH |

For the convenience `KEEP_BUT_SIMPLIFY` flake-utils row, the concrete guarantee is stable development-shell/formatter output selection across advertised systems, not library qualification. It can be replaced with simpler output definitions if that convenience contract is deliberately narrowed. It is not non-negotiable. Agent dependencies are deliberately `UNKNOWN` pending owner preference, rather than falsely declaring them dead.

### Origin evidence

- `26a8482` introduced M0 setup; later changes updated its pins, but current live references remain absent. Age alone is not the deletion argument.
- `e84767f` and [J1 publication report](J1_MAVEN_PUBLICATION_REPORT.md) establish Maven boundary construction and negative coordinate probes. Documented failures involved duplicate archive tasks and setup issues; there is no evidence that every metadata assertion originated in a deployed release incident.
- `dfd76a9` and [0.1.1 workflow report](V0_1_1_RELEASE_WORKFLOW_REPORT.md) introduced protected release/retry/remote guards. Same-run partial-upload handling is preventive hardening; do not claim an observed partial publication without evidence.
- `e2326ac` and [0.1.3 notes](V0_1_3_RELEASE_NOTES.md) document the real 0.1.2 final-job failure from selecting 0.1.1 notes. Version-specific notes and early validation are durable regression protections.
- `608f96d`/`d6310c1` and [0.2.0 preparation report](V0_2_0_RELEASE_PREPARATION_REPORT.md) establish local preparation and released-status handling. `8b24b8a` and [ownership correction](JAVA_RELEASE_VERSION_SCAN_FIX_REPORT.md) document a real false positive on explanatory API prose. This supports bounded ownership, not a global version scanner.
- `857b71c` and the r19 report explain the 92/99 integrity expectations; they are semantic provenance, not obsolete release scaffolding.
- `632c7d5` unified Nix qualification; `27f47cb` added the current action guard. Neither is a reason to delete release-only normal/offline equality.

## 7. Every Python file

All five use the standard library only. External **processes** are still dependencies: release.py invokes Git, GPG and gh; the verifier reads Gradle outputs. No pip environment, third-party Python parser or Python packaging system exists here.

| File/domain | Responsibility and callers | Tests | Overlap | Criticality / approximate complexity | Disposition |
| --- | --- | --- | --- | --- | --- |
| publishing/release.py: release preparation/version ownership | Local plan, stable version advance, notes skeleton/check; human + workflow preflight | test_prepare.py, 20 methods | VERSION validation in Gradle/consumer; pin hashes in Java; bounded Markdown slots | High release-source integrity; plan + mode branches; whole file 26 functions / 391 LOC | KEEP_BUT_SIMPLIFY |
| release.py: release guardrails | Inputs/identity, Central absence, same-run history, secrets, signatures, tag/release | 21 ReleaseTests methods, with mocked network/sign/Git and temp roots | Shell validation before checkout; wrapper checks external; some secret-presence checks repeated | Critical external state machine; pagination/backoff/side effects; cannot replace by Gradle publish task alone | KEEP_BUT_SIMPLIFY |
| release.py: reproducibility/inventory | Schema, compare, exact remote hashes; local pair + workflow | Compare/remote/signing negative tests; inventory schema itself lacks dedicated exhaustive tests | Publication verifier creates inventory; YAML stages/copies it | High release identity; small schema, reused in remote/signing | MERGE_CANDIDATE with publication inventory model |
| release.py: test evidence | Parse full/focused JUnit XML; Nix/YAML/human | No dedicated XML failure-path tests found | Java actually proves corpus/integrity; shell separately hashes snapshot | Medium/high false-green prevention; hardcoded class/method/count coupling | KEEP_BUT_SIMPLIFY |
| publishing/verify-publication.py | Exact staged files/checksums/POM/module/class/source/doc bytes; root task + Nix/human | No dedicated Python test file; exercised by full gates/consumers; historical negative probes only | Root task is wrapper, not second implementation; Gradle checks loose classes; consumer checks resolved semantics | High; 121 dense LOC, top-level loop and nested helper; output-writing import unsuitable as shared library | MIGRATION_CANDIDATE after modular responsibility merge |
| publishing/test_prepare.py | Isolated source preparation tripwires, history, ambiguous owned slots, actual pin corruption | Is itself a test suite | Legacy 0.1.5 fixture and later marker fixtures | 285 LOC / 20 tests; fixture setup is substantial; no network/real repository mutation | KEEP_BUT_SIMPLIFY; migrate durable cases with controller if justified |
| publishing/test_release.py | Release negative/idempotency/signing/retry tests; workflow pin guard | Is itself a test suite: 21 + 2 methods | Static pin policy resides inside tests; exact Git call expectations | 247 LOC; important behavior plus implementation-shaped mocks | KEEP_BUT_SIMPLIFY; retain behavioral negatives, relax call-shape coupling |
| nix/verify-wrapper.py | Exact JAR + canonical unique URL/hash properties; flake check only | No dedicated test file | Release YAML and bootstrap repeat JAR hash; Gradle wrapper task repeats distribution identity | Security-critical 21 LOC, no networking or wrapper execution | KEEP; too small to justify a separate Java runtime/bootstrap path |

Domain coverage: release preparation and version ownership live in release.py/test_prepare.py; publication verification in verify-publication.py; guardrails and reproducibility in release.py/test_release.py; wrapper/build verification in nix/verify-wrapper.py; miscellaneous workflow action policy in WorkflowPinTests. There are no other tracked `.py` files.

Ask whether a responsibility should exist before porting it: remove M0/legacy transitions and reduce document version slots first. Do not translate the release monolith and its mocks file for file. The publication verifier is the best conditional Java candidate because JAR/class/XML inspection is close to the Java build; the release transaction is less naturally Java-native and currently avoids extra dependencies. A Java XML implementation must preserve namespace behavior and avoid adding external entity/network resolution; module JSON parsing must not pull a new runtime library into published artifacts.

## 8. Gradle / Nix responsibilities

Gradle already owns compilation/tests, release-17 bytecode, Java archives, coordinates/POM/module production, test-fixture exclusion and the independent consumer build. Nix owns JDK25/Gradle9.8/Python tooling, fixed replay, source projection, offline composition and output materialization. This is substantially the correct boundary.

Specific simplifications and boundary issues:

1. Two near-identical `verifyJava17Bytecode` definitions are shared-library/convention candidates. Keep both module checks and their test/check dependencies; share definition only. Packaged-byte inspection remains independent.
2. Nix's inline Python copier understands inventory paths but does not invent release policy. Keep copying an accepted inventory; it should not grow POM/tag/version business rules.
3. Nix repeats direct publication verification after verifyPublication, which already ran as consumerSmoke's dependency. No intervening staged producer mutation is evident. Re-running the same verifier is not independent verification. A future evidence-producing aggregate can remove redundant execution after preserving task ordering and output identity.
4. Nix's separate POM consumer command does not explicitly spell `--offline`; the derivation sandbox has no external network and its Gradle hook replay is offline. Do not infer that root `gradleFlagsArray` necessarily rewrites this explicit shell command. Keep this distinction in future orchestration; explicit offline consumer intent would improve clarity.
5. `qualificationDependencies` resolves all resolvable configurations to populate isolated Gradle home. This is a narrow replay adapter, not a second dependency resolver, and only 10 LOC. Deleting it without proving replay coverage risks breaking offline behavior.
6. Flake agent/devShell dependencies are convenience and enlarge the lock/evaluation supply chain. They do not determine Java semantics; moving agent convenience to user-level configuration is a possible later simplification requiring owner agreement. The package/check restriction to x86_64-linux is explicit; multi-system devShell does not qualify other platforms.
7. A future support Java CLI must be buildable from fixed inputs **before** it is needed. Wrapper verification cannot depend on first executing the unverified wrapper. No support CLI or JSON dependency should enter production Maven JARs.

## 9. Publishing responsibility matrix

`D` = direct assertion, `C` = composes/delegates to that assertion, `U` = uses accepted evidence without independently proving the property, `—` = does not verify. Gradle verifyPublication and direct Python are the **same implementation**. The workflow column includes its ordered local commands and remote steps; Nix does not release.

| Property | Gradle verifyPublication | verify-publication.py | release.py | Module consumer | POM-only consumer | qualification.nix | Protected release workflow |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Exact ten unsigned artifacts | C | D | D inventory length/path schema | — | — | C + copies inventory | C, handed between jobs |
| Exact filenames/repository inventory | C | D including no unexpected files | D schema, not repository walk | U strict artifact hashes | U strict artifact hashes | C | C |
| Hashes/checksum sidecars | C | D local/module/checksums | D compare/sign-input/remote | U generated verification XML | U generated verification XML | C fixed dependencies + artifacts | C cross-job and hosted checks |
| Binary JAR contents/classes | C | D exact production class bytes | — | Link/load selected API | Link/load selected API | C | C |
| Java 17 bytecode | C | D class headers major61/minor0 | — | Compile target17, not run on JDK17 | Same | C + module checks | C + module checks |
| POM dependencies/scopes | C | D exact core runtime BC/NIO compile core | — | Uses module metadata normally | D resolved compile/runtime closure | C | C |
| Gradle module metadata | C | D exact variants/deps/files/JVM target | — | D resolved closure/capabilities | Ignored deliberately | C | C |
| Sources/Javadoc JAR contents | C | D exact source and generated doc bytes | — | — | — | C | C |
| Independent consumer resolution | — | Produces accepted hashes only | — | D independent project | D independent POM mode | C both | C local and fresh hosted modes |
| Offline resolution | — | — | — | Root invocation forces offline | Human gate explicitly offline | C sandbox/replay; explicit POM caveat above | C offline pair/local POM |
| Remote Central bytes | — | — | D ten SHA-256 + signature presence | Hosted mode resolves Central | Hosted mode resolves Central | — | C remote + fresh homes |
| Normal/offline ten-artifact equality | — | Produces each inventory | D compare maps | — | — | — single hermetic build | C normal/offline preflight pair |

The verifier accepts only valid checksum sidecars and one-version Maven repository indexes in addition to the ten unsigned artifacts. Timestamped index bytes and detached signatures are deliberately outside the ten-artifact reproducibility inventory. Signing checks use actual sign-task inputs, not staging copies. Signature checks establish armor/presence, **not cryptographic trust verification**.

Overlap classification:

- Static POM/module checks versus their resolved consumers: `INTENTIONAL_INDEPENDENT_CHECK`. Syntax and exact publication shape do not prove real resolution/compilation.
- Module versus POM consumers: `INTENTIONAL_INDEPENDENT_CHECK`. One can pass while the other metadata format is broken.
- Module class-header checks versus packaged JAR class-header/byte equality: `INTENTIONAL_INDEPENDENT_CHECK`. Compilation and packaging are different boundaries.
- Gradle task versus its Python Exec implementation: `SHARED_LIBRARY_CANDIDATE` for future packaging, not two checks today.
- Direct verifier immediately after the same Gradle verifier with no producer change: `ACCIDENTAL_DUPLICATION` of execution/evidence, not duplicate semantic implementations.
- Inventory path rules shared by publication/signing/remote: `SHARED_LIBRARY_CANDIDATE`; producer acceptance and downstream external-byte comparisons remain distinct assertions.
- Normal/offline pair versus Nix check: `INTENTIONAL_INDEPENDENT_CHECK`; section 11.

## 10. Consumer-smoke audit

There is **one** independent nested project, **one** Java smoke implementation and **two** metadata modes. The existing design already shares code while independently resolving published coordinates. Local Totipo is exclusive staging; BC alone is fetched from Central/cache. Remote mode switches to one canonical Central repository and forbids staging fallback. `pomOnly` uses `mavenPom()` and `ignoreGradleMetadataRedirection()` so Gradle module metadata cannot repair a broken POM.

Unique module invariant: Gradle consumers select correct variants/capabilities and get core on compile classpath, BC only at runtime. Unique POM invariant: Maven-style dependency scopes alone produce that same exact closure. Both assert module identities, reject source substitution and test/fixture leakage, compile an external API client and run selected linkage/factory behavior.

A separate Gradle project remains the simplest existing isolation boundary. Converting it to an ordinary subproject or composite would risk source/project substitution and weaken the point of the test. Two copies or two build files are unnecessary; the metadata property already provides the right sharing. POM mode is a Gradle POM resolver, **not an actual Maven CLI build**; no extra Maven implementation is justified by current guarantees.

Default consumerSmoke disables daemon/build cache, forces rerun, enforces strict verification and clean/check. Root and nested project state are separate, though local modes do not use a fresh GRADLE_USER_HOME per invocation. Remote release consumers do: one new home for each metadata mode, removed afterward. Do not conflate these two isolation levels.

Consumer Java includes VaultId value/ownership and filesystem read-only checks that overlap runtime tests. Retain representative linkage/entry-point smoke; consider moving expanded semantic assertions back to runtime tests when simplifying. Imports or unused compile helpers alone do not justify weakening the boundary. No substantial current consumer configuration is proven historical scaffolding.

## 11. Release reproducibility audit

Routine qualification proves the library, tests, metadata and consumers work in one fixed offline Nix environment. Release-only comparison proves a normal host build followed by a clean forced-offline/no-build-cache/rerun build produces the **same ten unsigned bytes** for the selected release. This catches acquisition/cache/task-state influences that a single hermetic success does not compare. It does not prove universal equality across arbitrary JDK vendors/platforms.

Implementation is not fragmented into many staging scripts: Gradle produces files, verify-publication.py creates both inventories, shell saves normal JSON outside build outputs, release.py compare validates and compares the map. The Nix output copier is unrelated to the normal/offline pair. Signatures and timestamped repository indexes are excluded intentionally.

Local checklist and release preflight duplicate orchestration recipes; publishing and verifying jobs rebuild independently against preflight to establish exact bytes on different workers. Those rebuilds protect a new trust boundary and should survive. A future one-command release-verification mode could share the normal/offline recipe and immediate XML accounting, without sharing secret-bearing upload or tag authority. Keep the normal inventory and full XML evidence before clean/focused runs overwrite outputs. No release reproducibility was performed for this audit.

## 12. Tooling-test audit and API/snapshot guards

| Tests/guard | Primary subject | Assessment | Future deletion/migration consequence |
| --- | --- | --- | --- |
| test_prepare.py (20 methods) | Ownership, no-write/dry-run/check, clean branch, notes, real snapshot corruption | Durable behavior; version-scan/released-state regressions; many fixtures still begin with unmarked 0.1.5 | Remove old conversion fixture branches if old transition deleted; retain modern marker, historical-prose and failure-before-writes cases |
| ReleaseTests (21 methods) | HTTP errors vs absence, same-run history, notes, identity, hashes, signatures, idempotent tags/releases | Durable safety behavior, including real notes-selection regression | Port behavior with controller if migrated; some exact subprocess/Git call-sequence mocks can become semantic assertions |
| WorkflowPinTests (2 methods) | Exact allowlist/canonical syntax and malformed alternatives | Process/supply-chain guard; reviewed runtime labels are constants, not fresh upstream validation | Preserve pre-Nix full-checkout execution; could move policy out of unittest into support CLI after merge |
| SpecSnapshotIntegrityTest (3 methods) | Exact hashes/set/safe paths and negative corruption/inventory cases | Durable provenance behavior, not implementation-shape tests | Retain even if release helper pin validation shares data |
| R19ProfileIntegrityTest (1 method) | Requirements/manifest/physical files/loader exact agreement | Durable semantic corpus integrity | Retain; no pruning due to support minimization |
| VectorCaseLoaderTest (3 methods) | Strict hex/JSON/context/unsigned integer behavior | Durable test infrastructure parsing behavior | Only disappears if loader replaced with equivalent strict parsing and tests |
| Phase2ConformanceTest (1 method) | Actually executes exact required case set | Runtime/API correctness, not merely tooling accounting | Never delete as a tooling optimization |
| Module verifyJava17Bytecode | Produced loose classes | Durable build behavior, no standalone test suite | Share definitions while checking both modules |
| Consumer build check + ConsumerSmoke | Packaged API/scopes/repository independence | Durable publication boundary; selected semantic overlap | Keep independent compilation/resolution after any rewrite |
| PublicApiTest / VaultIdTest / NIO suites | Public operation/value and removal contracts | Runtime/API tests; reflection does not turn them into build tooling | No deletion recommendation; inspect separately when changing API |
| Javadoc tasks plus source/doc archive verifier | Generated documentation and packaged source consistency | Publication correctness; not a complete API compatibility or prose quality check | Retain packaging validation |
| Wrapper verifier | Reviewed executable identity | Durable supply-chain assertion, but no dedicated negative tests | Add focused identity/duplicate/continuation negatives only if rewriting it |

A reflection assertion on TokenPublicationPlan fields or an API method count is implementation/API shape evidence within runtime suites, not an obsolete release helper fixture. This audit does not classify those full suites as support overhead.

Coverage gaps to address **when changing tooling**, not with new scripts now: publication verifier has no dedicated automated malformed-publication fixture suite; release inventory and test XML accounting lack exhaustive direct tests; wrapper checker lacks dedicated negative cases. Historical manual probes are useful origin evidence but are not ongoing tests.

Nix intentionally excludes `.github`; WorkflowPinTests skips its full-workflow scan when workflows are absent. CI explicitly executes it on the full checkout first, and release preflight runs discovery on full checkout. Therefore Nix alone does not prove workflow pins; the companion full-checkout guard is necessary. The Python skip is separate from the zero-skip **Java** accounting contract.

## 13. Duplication matrix

| Responsibility | Current owners | Classification | Recommended boundary |
| --- | --- | --- | --- |
| VERSION parsing | Root strict regex/newline; consumer read; verifier read; release validate/stable_version/inputs; shell precheckout/version checks; Nix removeSuffix | SHARED_LIBRARY_CANDIDATE | One specified version format; consumer stays independent; precheckout validation stays before executing repo code; release stable-only policy distinct from general build qualifier support |
| Spec pin parsing/identity | Java exact lines + independent hashes; release exact full-document SHA; profile hash checks; docs | INTENTIONAL_INDEPENDENT_CHECK | Pin bytes/provenance separately from semantic profile/case consistency; consider manifest data ownership without removing independent assertions |
| Release source/tag checking | release inputs/identity/tag_release; YAML initial dispatch and verify-central shell checks | INTENTIONAL_INDEPENDENT_CHECK | Keep checks at approval/upload/tag boundaries and bootstrap validation; consolidate redundant postcheckout parsing only |
| SHA-256 calculation | Java digest helpers, Python hashlib, shell sha256sum, Nix SRI | INTENTIONAL_INDEPENDENT_CHECK | Trivial hashing is not a framework requirement; share domain schema, not one universal hash dependency |
| XML/POM parsing | Publication ET parses Maven semantics; release ET parses JUnit; Gradle resolves POM | INTENTIONAL_INDEPENDENT_CHECK | These are different XML domains; POM static assertions and real resolver remain separate |
| Gradle module parsing | Python JSON structural checks; independent Gradle variant resolution | INTENTIONAL_INDEPENDENT_CHECK | Do not replace resolver smoke with the structural parser |
| Publication inventory | Verifier expected names/maps; release ten-path regex; Nix copy; YAML saved output | SHARED_LIBRARY_CANDIDATE | One artifact manifest representation/validator; retain fresh local, sign-input and remote byte reads |
| Artifact hashing | Verifier inventory/checksums/module hashes; release compare/signed/remote | INTENTIONAL_INDEPENDENT_CHECK | Different byte origins must each be hashed; accepted manifest can be shared |
| Remote-byte verification | release.verify_remote only; consumer downloads/strict hashes | INTENTIONAL_INDEPENDENT_CHECK | Remote download/hash and actual consumer resolution provide different evidence |
| Wrapper verification | Python all three identities; YAML repeated JAR checks; bootstrap JAR/distribution constants; root wrapper config | SHARED_LIBRARY_CANDIDATE | Call non-executing full identity verifier before release wrapper use; eliminate bootstrap only; preserve independent Nix check |
| Action-pin validation | WorkflowPinTests allowlist + YAML uses declarations | INTENTIONAL_INDEPENDENT_CHECK | Declarations need independently reviewed policy; no second validator found |
| Bytecode task implementation | Core and NIO same header checks | SHARED_LIBRARY_CANDIDATE | Share Gradle convention; preserve checks of all classes and independent packaged verification |
| Publication verifier execution | Gradle Exec then same direct Python in normal/Nix sequence | ACCIDENTAL_DUPLICATION | One stable invocation/evidence output after producer finishes; no loss of semantic coverage |
| Normal/offline recipes | Checklist shell and preflight YAML | SHARED_LIBRARY_CANDIDATE | One credential-free verification operation with separate local/CI callers |

## 14. Intentional independent checks

Keep all of these proof boundaries even if one CLI owns common data:

1. Runtime/API tests versus artifact packaging checks.
2. Loose Java17 class checks versus packaged production-byte equality.
3. Static POM/module shape versus independent external compilation/resolution/runtime linkage.
4. Module metadata versus POM-only, with metadata redirection disabled.
5. Strict Gradle dependency verification/locks versus Nix fixed URL/hash replay.
6. Exact snapshot provenance versus profile/manifest physical-set integrity versus executed semantic cases.
7. One fixed hermetic qualification versus release normal/offline byte equality.
8. Accepted staged inventory versus actual sign-task unsigned inputs versus fresh rebuilds versus hosted Central bytes.
9. Hosted-byte equality versus fresh Central-only consumers.
10. CI full-checkout action policy versus filtered Nix qualification.
11. Checks before environment approval versus repeated checks after approval and immediately before irreversible operations.

An extra invocation of the same verifier against unchanged bytes is not an eleventh artifact semantic implementation or independent proof.

## 15. Supply-chain and tool dependencies

The producer uses pinned Gradle 9.8.0, JDK25 toolchain, `--release 17`, Vanniktech publish plugin 0.37.0, JUnit 6.1.3, Jackson core 2.18.2 for tests and BC 1.86 for runtime Argon2id. Plugin transitives include Central Portal/Moshi/OkHttp/Retrofit/Kotlin/SLF4J; they are support dependencies, not unexpected published runtime API dependencies.

The fixed cache currently contains two repository roots, 39 coordinate/version records and 92 artifact entries including parent/BOM/plugin metadata. Those counts are descriptive, not a cache-update review or canonical artifact verification. The extra older JUnit BOM/Kotlin metadata versions are not proof of dead dependencies: parent/transitive metadata can require them. Do not hand-prune the cache by eyeballing names. Gradle root verification XML, locks, catalog and cache serve different constraints and must be reviewed together on a dependency change.

Nix lock includes nixpkgs, flake-utils and the agent/devShell dependency graph (llm-agents, jailed-agents, jail.nix, bun2nix, treefmt and additional nixpkgs/system nodes). The agent graph increases developer supply-chain surface; it is live through makeJailedCodex, not unreferenced lock debris. Separating it is optional architecture work, not an automatic low-risk deletion.

CI uses SHA-pinned checkout, setup-java and install-nix actions. The allowlist includes reviewed tag/runtime labels, but this audit did not re-review upstream action.yml. CI's `ubuntu-latest` host and release's `ubuntu-24.04` plus setup-java Temurin25 are not the Nix fixed environment. Release byte equality is scoped to its selected workers/toolchain. Hosted actions, Nix cache configuration, Git/gh/GPG/curl-equivalent urllib and canonical Central/GitHub API are external trust/availability dependencies.

The wrapper verifier pins JAR/URL/distribution checksum; launch scripts themselves are not separately asserted there. Gradle strict verification checks checksums, not signature trust in the consumer template. Release GPG checks key metadata and Gradle signs; remote `.asc` checks only presence/armor. Do not advertise independent signature trust certification or Java17-runtime execution (consumer runs JDK25).

## 16. Contributor cognitive surface

| Task | Required systems | Meaningful burden / minimal future path |
| --- | --- | --- |
| A. Core Java change | Java/JUnit, Gradle commands, ladder, human Nix | No need to edit Python/YAML/Nix normally; XML accounting and freeze timing are operational knowledge. Keep baseline/focused/final boundary rather than every gate on every edit. |
| B. Public API change | Java tests/Javadocs/API docs, external Java consumer, publication gate, human Nix | Need understand selected compile/link expectations and intentional breaking releases; no automated binary-compatibility baseline exists in current support. Historical javap/API inventories are review evidence. |
| C. Dependency update | Catalog/Gradle locks/verification, verifier BC expectations, consumer closure/lock/template, Nix cache/toolchain | Largest cross-language coupling: BC version appears in producer, Python POM expectations, consumer Gradle and template/cache. Shared declared expectations can help, while independent resolver assertions stay. Human cache review remains. |
| D. Release preparation | Python CLI, VERSION/owned Markdown/workflow example/consumer locks, Java API review, Gradle normal/offline, human Nix | Ownership fix avoids needing to classify all historical version strings. Fewer version-bearing docs slots would reduce fixture and scanning cost. |
| E. Maven Central release | YAML environment/permissions, shell/env, Gradle plugin/sign tasks, Python state machine, Git/gh/GPG, Central propagation | Highest security-sensitive cognitive burden. One auditable release controller with thin YAML helps; secrecy/approval boundaries and rerun provenance must remain explicit. |
| F. CI maintenance | YAML actions/pin guard, Nix/lock/cache, Gradle tool versions, wrapper identities | Pin constants and source projection mean Python suite in Nix cannot substitute for full-checkout guard. Review hosting changes independently of Java source. |

## 17. Dead / unreferenced tooling

- `bootstrap-m0.sh`: no live incoming reference from Gradle, Nix, workflows, helpers, README, checklist or AGENTS. Its own usage text is not a caller. It invokes lock regeneration, wrapper generation, verification-input generation and build, which are inappropriate as routine setup now that reviewed inputs are committed. Historical reports describe it as bootstrap/maintenance, not qualification.
- Legacy unmarked 0.1.5 conversion in `status_block` and three hardcoded prose arguments: reachable in synthetic tests/old source layouts, not needed to advance marked current main. This is obsolete transition support, not an uncalled whole function. Ordinary `status_block` remains essential until doc ownership is simplified.
- Historical API inventories, reports and release artifacts: human provenance, not executable tools. Current selected release notes are live, including review/ paths excluded from Nix. Do not label all review/ content dead or delete evidence by default.
- All five Python files, both Nix files, the init script, both workflows and consumer files have live callers. No other entire support program is proven dead. No executable staging/compare micro-helper collection was found.
- Former manual `publishToMavenCentral` plus Portal Publish is labeled historical in checklist prose; no separate current implementation needs deletion. Emergency manual operation uses the same explicit current tasks and guarantees.

## 18. Low-risk delete candidates

**These can likely be removed now with LOW risk** in a separately authorized pruning milestone:

| HIGH-confidence candidate | Why low risk | Evidence required then |
| --- | --- | --- |
| bootstrap-m0.sh (59 approximate implementation LOC / 79 physical lines) | No live caller, no unique guarantee, broad initial trust-generation behavior superseded by reviewed committed inputs and documented maintenance | Repeat reference search; document supported wrapper/dependency update commands; changed-path review; applicable build-input qualification only if replacement docs/build changes require it |
| Legacy 0.1.5 unmarked-status conversion only (~10 LOC plus bounded obsolete fixture scaffolding) | Current main has markers in all owned docs; future prepare operates on markers; historical checkout carries its own old helper | Preserve modern prepared/released current/current check, future plan, missing/duplicate marker rejection and history-preservation tests; relevant Python baseline/focused/full stable guardrails and included-input Nix qualification |

This is not permission to delete now. No whole Python file, independent consumer mode, semantic test, fixed cache or release job meets this low-risk deletion threshold. Owner-dependent agent convenience is excluded from this list.

## 19. Merge before migrate

**These responsibilities should be merged before any Python -> Java migration.**

1. Define one accepted unsigned publication manifest model: module/version/artifact suffixes/path/hash records. Let staging validation, compare, signing-input and remote verification use it; preserve separate byte reads/assertions at each boundary.
2. Share bytecode Gradle task definition across modules, rather than porting duplicate tasks into a new CLI. Preserve packaged verification independently.
3. Use the existing non-executing wrapper identity verifier consistently in release jobs; remove M0 generation and repeated inline constants where safe. Keep the Gradle wrapper generation configuration and distribution properties as independently reviewed inputs.
4. Reduce version ownership slots: generic workflow example and less repeated checklist metadata can be derived/referenced from VERSION. Keep exact notes selection and consumer locked versions. Do not restore global substring scanning or rewrite historical prose.
5. Make credential-free qualification/release verification orchestration explicit and reusable, including full XML accounting before overwrite, without merging signing/upload/tag authority into routine qualification.
6. Separate reusable publication semantics from top-level side effects before any reuse or port. Importing the current verifier executes it and writes files; it is not a library today.

## 20. Migration candidates

Primary candidate: the irreducible artifact semantic verifier (JAR/class/source/doc/POM/module/inventory rules) may become an isolated Java support CLI or Gradle build-logic implementation, **after** inventory ownership and tests are settled. It must remain absent from public runtime artifacts and preserve independent consumer resolution. Compare a small Gradle-owned implementation against a standalone support subproject before adding either.

Conditional candidate: release transaction/controller and guardrail tests could move to Java if eliminating Python is a maintained project objective and a source-launch/bootstrap path plus JSON/HTTP/subprocess tests is demonstrably simpler. Current stdlib urllib/XML/JSON/tempfile/subprocess functionality is compact; Java may add more code, a JSON dependency and bootstrap coupling. Confidence that a port reduces LOC: LOW. Confidence that the guarantees are necessary: HIGH.

Poor migration candidate: the 21-LOC wrapper checker. It runs before wrapper trust, and Python is already part of the Nix tools. Keep it unless an already available non-executing Java launcher reduces total machinery. Do not add a wrapper-dependent build just to validate the wrapper.

Workflow action policy could migrate with an existing support CLI, but avoid adding a YAML dependency solely to parse this narrow canonical style. Its reviewed allowlist and full-checkout placement matter more than language. No Python port is recommended solely because Python differs from runtime Java.

## 21. Non-negotiable contracts

- Java17 production bytecode (major61/minor0), including packaged contents; JDK25 is the build/runtime qualification host, not a claim of Java17 runtime testing.
- Exact two Maven modules / ten unsigned artifacts: binary, sources, Javadoc, POM and module for each; no test/fixture/spec leaks or extra variants/dependencies.
- Correct core runtime-only BC and NIO compile/API core exposure; exact POM scopes and Gradle metadata.
- Independent module-metadata and POM-only consumers, no source/composite/Maven-local substitution, strict hashes, and fresh Central-only consumers for releases.
- Dependency locks, strict verification and separately reviewed fixed URL/SRI acquisition; do not weaken consumer isolation to accelerate iteration.
- Exact source/spec/profile/corpus identity, safe complete snapshot inventory, all 92 semantic cases actually executed without skips.
- Exact release VERSION, reviewed source/current-main/dispatch SHA, version-specific reviewed notes and immutable annotated source tag relationship. Historical prose stays outside release-owned slots.
- Release-only normal/offline equality of the ten unsigned artifacts; Nix does not replace it.
- Exact sign-task unsigned input identity and hosted Central byte verification before tags/releases; bounded propagation retry and no reupload on ambiguity.
- Single authoritative human/CI Nix routine qualification, offline fixed replay, preserved layered evidence and correct source-filter freeze/invalidation.
- Wrapper JAR/distribution URL/checksum identities checked without first trusting the wrapper.
- Explicit protected release dispatch/environment approval and secret/write-permission separation. Routine qualification authorizes no release.

## 22. Target minimal architecture

```text
Java runtime + tests + normative corpus
        |
Gradle producer + shared build conventions
        |-> one publication semantic verifier -> accepted ten-artifact manifest
        |-> one independent consumer project
              |-> module metadata resolution
              |-> POM-only resolution
Nix -> fixed tools/dependency replay -> routine qualification composition
Release controller -> source/approval/rebuild/compare/sign/upload/remote/tag sequence
GitHub Actions -> remote jobs, permissions, environments, secrets and handoffs
Shell -> precheckout validation, environment/process glue only
```

The verifier may ultimately be Java/Gradle-owned; release controller may remain stdlib Python. This is the minimum **responsibility** architecture, not a mandate for a language count. Prefer one support CLI with explicit read/check/prepare/release authority boundaries over many translated helper files if a Java migration is selected. Keep non-executing wrapper validation available before any Gradle trust. No extra Maven consumer project, release framework, audit script or runtime dependency is justified by the current evidence.

Nix should consume Gradle/verifier evidence and select fixed environment/input projection. Gradle should construct/inspect Java artifacts and orchestrate the independent consumer. YAML should expose approval/secret/permission and job boundaries, with only necessary precheckout validation in shell. Release business rules should have one owner; remote checks and rebuild comparisons remain independent operations even under shared ownership.

## 23. Pruning waves

These are proposals; every later included-input edit invalidates previous qualification. Never optimize by dropping final evidence layers.

| Wave | Components/files | Invariant preserved | Regression evidence required | Risk | Expected reduction |
| --- | --- | --- | --- | --- | --- |
| 1 — obvious dead/superseded deletion | bootstrap-m0.sh; legacy 0.1.5 status conversion/fixture portions | All current wrapper, locked dependency, marked preparation and release contracts | Live-reference check; modern preparation ownership/no-write negatives; focused then stable Python guardrails for helper change; human Nix when included inputs change | LOW; tests must stop relying on deleted transition | ~59 shell LOC; ~10 Python implementation + ~10–30 fixture LOC; remove one shell file, not shell language entirely |
| 2 — merge duplicated publication/release logic | build.gradle.kts/module bytecode conventions; verifier/release inventory schema; YAML wrapper checks; checklist vs YAML verification recipes | All ten-artifact acceptance, all byte origins, independent consumers, bytecode and full evidence accounting | Negative artifact fixtures/schema/sign-input/remote mismatch tests; exact consumer graphs; relevant final normal gate; host offline pass for changed build/resolution behavior; separate release pair for changed release preparation procedure; final human Nix | MEDIUM to HIGH near release transaction; stage subchanges | ~30–100 net implementation LOC plausible; some tests added; no whole-language removal |
| 3 — migrate irreducible Python to Java, if justified | Publication semantic implementation first; controller only after a separate cost/bootstrapping decision; embedded Nix copier considered too | Identical acceptance/rejection behavior, source isolation, no new published dependency, pre-wrapper trust verification | Existing failure behavior plus differential old/new fixtures; complete stable Java/publication/consumer/Python transition checks; fixed/offline cache review if dependencies added; release equality and hosted sandbox tests as applicable | HIGH for controller; MEDIUM/HIGH for verifier | Could remove ~121 Python production LOC first, adding Java/build/test LOC; full Python removal would replace up to 1,065 LOC plus embedded code, with unknown or negative net savings |
| 4 — simplify qualification/docs | AGENTS/README/checklist cross-references; remove unchanged-byte repeated verifier invocations through explicit aggregate evidence; optional agent devShell split | Same final layers, immediate XML accounting, one human Nix, separate release pair, correct freeze | Task-graph/evidence comparison; all applicable final gates if executable orchestration changes; docs-only allowlist if prose only; check full-checkout action guard placement | LOW for prose; MEDIUM for orchestration; owner decision for devShell | ~40–100 prose/recipe LOC; ~10–30 orchestration LOC; optional lock graph shrink not estimated without evaluation |

Wave 3 is conditional, not a prerequisite to wave 4. Do not postpone straightforward documentation simplification for a speculative Java port. Conversely, a port must not precede removal/merging of obsolete responsibilities just to hit a language target.

## 24. Expected language / LOC reduction

Conservative immediate proposal: remove one obsolete repository-owned shell program and ~20–40 Python code/fixture lines, about **79–99 approximate LOC** overall. This does not remove shell from wrappers/YAML/Nix or Python from support.

After merging, another **30–100 implementation LOC** and **40–100 prose LOC** may be removable. These are planning ranges, not measured patches; count new tests and shared-convention overhead before claiming net reductions. Invocation/time reduction from avoiding repeated verifier execution is distinct from LOC reduction.

The current Python total is **1,065 LOC**, of which **532** is test code and **533** production verification/controller code. Porting all of it is primarily a language relocation. A Java JSON/HTTP/process/test solution can be larger. Target one owner per business rule and fewer contributor concepts; do not promise net LOC savings or a Python-free repository without including wrapper verification and embedded Nix Python.

Historical reports dominate prose/data counts and are excluded from executable qualification. Deleting them would improve a file-count metric while losing provenance; it is not a support-architecture optimization recommended here.

## 25. Open questions

- Is agent tooling an intentional repository-level developer requirement, or should jailed-agents/llm-agents live in personal environment configuration? Live usage is proven; necessity to library guarantees is not.
- Is eliminating Python itself a maintained goal worth a new Java support build/JSON dependency, or is a small stdlib controller acceptable? No evidence presently proves a Java port will simplify releases.
- Which repeated version-bearing checklist/workflow slots can become generic without losing review clarity? Ownership must stay explicit and tests must preserve explanatory prose.
- Can one explicit verification aggregate replace unchanged-byte direct verifier reruns while preserving current documented final coverage? This requires a future policy/implementation change, not silently omitting commands now.
- Should bytecode compatibility also be execution-tested on Java17? Current guarantee is compilation/class format and JDK25 consumer execution; strengthening it would be new scope.
- Are hosted `release` environment reviewers/main restrictions configured as checklist requires? Static YAML cannot prove them.
- Should remote signature **cryptographic trust** be verified? Current guarantee is byte identity plus detached signature presence. A stronger policy is a separate decision, not implied by this audit.
- Any undocumented operator use of M0 bootstrap? No tracked callers exist; announce removal and preserve explicit maintenance instructions in the pruning milestone.
- Should full-suite XML accounting additionally assert a declared suite-set/freshness manifest? Current parser checks present suite failures and named integrity/corpus anchors, not exhaustive source-to-XML suite correspondence. Preserve existing coverage first; avoid inventing a framework without a demonstrated need.

## 26. Qualification disposition and validation

Inspected qualification.nix source selection: only explicit root files and core/src, storage-nio/src, gradle, publishing and nix trees enter the Java source projection. `review/` is not selected; this report is excluded. Flake inputs/outputs reference qualification.nix, package-deps, VERSION, wrapper checker/JAR/properties and lock/tool inputs; there is no report reference. No unexpected inclusion was found, so the requested report addition is permitted.

This is excluded Markdown-only prose. No Java executable/test/source-Javadoc, Python helper, Gradle, Nix, workflow, lock/cache, wrapper, spec or consumer input changed. No Java rebuild, Javadoc, publication test, release reproducibility or human Nix request is warranted.

Validation performed after writing this report:

- Changed-path allowlist: only `review/JAVA_SUPPORT_TOOLING_AUDIT.md`, untracked/unstaged; tracked diff and staged diff empty.
- Executable/input byte invariance: SHA-256 comparison of every starting tracked file against both starting HEAD blob bytes and the saved pre-report filesystem manifest. All identical; tracked path set and index entries unchanged.
- Report local Markdown link targets exist; fenced code blocks balanced; required 27 numbered sections present.
- `git diff --check` passed. Because Git omits an untracked report from ordinary diff, also ran a no-index whitespace check against `/dev/null` for the report; no whitespace errors.

| Invocation accounting | Count |
| --- | ---: |
| Broad baseline Gradle | 0 |
| Focused Gradle tests | 0 |
| Focused Python tests | 0 |
| Full Python guardrails / release XML accounting | 0 / 0 |
| Final full Java gate | 0 |
| Publication / module consumer / POM consumer passes | 0 / 0 / 0 |
| Separate host forced-offline full runs | 0 |
| Human Nix runs for this audit | 0; not requested or claimed |
| Release reproducibility / release actions | 0 / 0 |

Inline Python used for static file counting/AST inventory and report validation is not a Python test suite invocation. There were no failed qualification attempts, reruns or invalidated qualification results during this audit. Some inspection outputs were truncated and relevant files/sections were read separately; no acceptance claim depends on truncated terminal inventories.

## 27. Final Git state

Branch remains `main`, HEAD remains `27f47cb076bf3fdd03183eaae603288abb80ad6c`.

```text
?? review/JAVA_SUPPORT_TOOLING_AUDIT.md
```

No tracked modifications, staged changes, executable/input changes, deletions, renames, commits, pushes, tags, publication or dispatched workflows. The report is the sole added file and remains unstaged/uncommitted. Proposed pruning and migration are future work only.

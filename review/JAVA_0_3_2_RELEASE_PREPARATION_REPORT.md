# JAVA_0_3_2 — Compatibility patch release preparation

Status: normal/offline Java release qualification, both consumer modes and
ten-artifact reproducibility PASS. Human-reported `nix flake check` PASS.
No publication is claimed.

## Source identity and scope

Started on clean `main` at `080f392d38896f18678709d9edfbb3fd336e4fc8`
(`fix android compatibility`). This is the committed, previously qualified
compatibility implementation, following released 0.3.1 source
`27cb53a1a5f2cc3d5f221ef790f694b26b90f8a8`. HEAD remains unchanged.
The compatibility report's uncommitted-state description is its historical
handoff state; commit `080f392` contains those changes and the report.

Classifications: `PUBLICATION_METADATA`, `CONSUMER_BOUNDARY`, `DEPENDENCY_BUILD`
(consumer self-coordinate locks only), `DOCS_JAVADOC` (Markdown only) and
`RELEASE_PREPARATION`. No compatibility implementation was redone. No executable
source, tests, public API, external dependency, build logic or release tooling
is changed by this preparation.

Read AGENTS.md, RELEASE_CHECKLIST.md, SPEC_PIN.md, docs/BACKUP_ARCHIVE.md,
the committed [compatibility report](JAVA_ANDROID_API30_COMPAT_REPORT.md),
the preceding release preparation/notes, canonical version/build files,
publication verifier, consumer boundary checks, standalone release identity and
finalization tooling, qualification.nix, flake.nix and nix/tooling.nix.
All work was confined to totipo-java.

Prepared changes:

- `VERSION`: exactly `0.3.2` plus one LF, replacing 0.3.1. Both Gradle modules
  inherit the canonical version and generate Maven coordinates from it.
- `publishing/consumer-smoke/gradle.lockfile`: both Totipo self-coordinates
  advance together to 0.3.2. The Bouncy Castle lock and all external locks remain
  unchanged; no dependency/cache update checkpoint is required.
- `review/V0_3_2_RELEASE_NOTES.md`: concise compatibility patch notes. The
  existing finalizer requires the GitHub release title **`v0.3.2`** and appends
  exact reviewed source provenance to these notes.
- This preparation report.

This working tree is not a committed 0.3.2 release revision. Its exact production
identity is starting HEAD plus the two included-input edits above. SHA-256:

| Input | SHA-256 |
| --- | --- |
| `git diff -- VERSION publishing/consumer-smoke/gradle.lockfile` | `616095607299530f4ddb10a28c0877fa24daccb7c91b30e1b6c18e0ebe604e8a` |
| `VERSION` | `511a100eedfb9ea66441e5d506392dba2d8aa290f98c4c544c70ae75ec3c3396` |
| Consumer lockfile | `0d078228f8f767232a618d59583e6f6ecb285313619b3610d66d924196cf8431` |

The eventual reviewed release commit SHA must be established after human
review/commit. Do not dispatch release against the starting 0.3.1-version HEAD.

## API, protocol, archive and dependency boundaries

Production Java and all tests are byte-unchanged from starting HEAD. Relative to
released 0.3.1, a direct JDK 25 compilation of the previously inspected published
source JARs supplied a signature baseline. Their SHA-256 matches the committed
compatibility evidence:

- Core sources: `d335377024d66d43c9a6e0a40dfec7deec7f8c6d91a56e0285c8012763249257`.
- NIO sources: `da6dfc3f058d38847d86e55ff3301a057317e1eb96f45945bda88ee9af2b6298`.

`javap -protected -s` outputs match exactly for 204 existing classes. The remaining
old class is an internal anonymous ZIP stream class whose numbering/implementation
changed with the compatibility fix; the new inventory adds another anonymous ZIP
stream class and the explicitly internal `Hex`/`BoundedInput` bridges. No existing
application/provider public or protected signature changed. The matching signature
output SHA-256 is `096a8924cb6533ba758d8d8957b5f68010c55676d6e5b0e26286d2cf1df683d1`.
The full suite also exercises the existing public API contracts.

Both modules retain `--release 17`; both full builds run the all-production-class
major-version-61/no-preview guard. No Android dependency or variant is introduced.
Strict verification, Gradle locks, consumer isolation and the runtime boundary
remain intact: compile exposes core/NIO 0.3.2, runtime adds only
`org.bouncycastle:bcprov-jdk18on:1.86`. NIO's POM/module metadata depends on core
0.3.2; core's POM/module metadata retains the sole external runtime dependency.

SPEC_PIN.md, the complete v1/r19 snapshot and requirements profile are unchanged.
The exact specification commit remains
`cdb4e91be1c6d3704874b2b92457ffe7be5e9084`. Full tests validate all 99 snapshot
records, the profile and the 92-case exact execution set; no separate shell
corpus hashing or focused integrity rerun substitutes for that accounting.

Backup profile 1, its documentation and frozen fixture bytes are unchanged:

| Fixture | SHA-256 |
| --- | --- |
| `empty.zip` | `603eb0dd84f67678bab82c78ee684dc22592b5e4de37f24d16f5694c382fa425` |
| `history.zip` | `369b77c872a32f2474d867e0653e39b66befdfc6cbd1244da486505ba8bca95b` |
| `missing-parent.zip` | `6991ee0b764409728a10ee6612d6eced56207dbefa58a35611ea3d6112c25e58` |

## Qualification evidence and invocation accounting

Evidence is retained outside build outputs in
`/tmp/totipo-0.3.2-preparation-080f392/`: logs, baseline/normal/offline XML,
accounting, accepted manifests, API comparison, source patch and local provenance
preview. These temporary files are not Git source members.
Tools are the existing Nix devShell's JDK `25.0.5.0.3+1` and Gradle `9.8.2`.
The independent verifier's main sources were compiled directly once using JDK 25
and only pinned Jackson Core 2.21.7 (JAR SHA-256
`8c5623b98f32d5f7e1287ff61ed50e08ab7b11381e991fff89327faabba42261`).
Its bootstrap smoke passed; Gradle never compiled this tooling.

| Invocation category | Count/result |
| --- | --- |
| Small broad baseline `gradle test` | 1 PASS, 26s; 13 actionable tasks, 2 executed/11 up-to-date. Module tests up-to-date; XML accounted and preserved before clean. |
| Focused Gradle tests | 0; no executable edits. |
| Focused/full standalone tooling test runners | 0; tooling unchanged. Launcher compilation/smoke and read-only source/provenance checks are separate preparation operations. |
| Final normal full Java release gate | 1 PASS, 1m57s; 30 root tasks executed. |
| Separate host forced-offline full gate | 1 PASS, 1m44s; 30 root tasks executed. Mandatory release reproducibility evidence, distinct from routine human Nix qualification. |
| Publication acceptance | 2 PASS, one per full build. `consumerSmoke` depends on `verifyPublication` without an extra acceptance invocation. |
| Direct accepted-manifest rechecks | 2 PASS, one per full build. |
| Module-metadata consumers | 2 PASS, 4 tasks executed each; normal 13s, offline 6s. |
| Explicit POM-only consumer | 1 PASS, 6s; 4 tasks executed, exact compile/runtime boundary confirmed. |
| Human/agent Nix runs for this preparation | 1 / 0; human-reported `nix flake check` PASS for the frozen version preparation. |
| Signing, tag creation, publication, push, GitHub release, commit | 0. |

Each full build's accounting: **542 JUnit tests, zero failures/errors/skips**, snapshot
suite 3, profile suite 1, **92/92 portable cases** with exact-set execution
assertion. Accounted immediately after the full build, before any clean; XML and
accepted manifests preserved externally. Javadoc completed with existing nonfatal
missing-documentation warnings (100 shown). Publication acceptance and recheck
passed 10/10 artifacts.

Archive regression coverage includes BackupArchiveTest 8, BackupZipAdversarialTest
28, BackupGoldenTest 6 (all JDK timezone IDs for each fixture), HistoryImportTest
36, NewLocationRestoreTest 4 and NioRestoreTest 39. These execute exact golden
writer bytes/digests, inspection, additive import, detached restore and real NIO
restore alongside the protocol, snapshot/profile and API tests.

Commands (independent launcher supplied as `TOTIPO_PUBLICATION_TOOL`):

```sh
gradle test
gradle --no-daemon --no-build-cache --no-configuration-cache clean test build javadoc verifyPublication consumerSmoke
"$TOTIPO_PUBLICATION_TOOL" release test-results "$PWD"
"$TOTIPO_PUBLICATION_TOOL" publication check "$PWD" "$PWD/build/repository" "$PWD/build/publication-manifest.json"
# Preserve normal XML/accounting and accepted manifest externally before clean.
gradle --offline --no-daemon --no-build-cache --no-configuration-cache --rerun-tasks clean test build javadoc verifyPublication consumerSmoke
"$TOTIPO_PUBLICATION_TOOL" release test-results "$PWD"
"$TOTIPO_PUBLICATION_TOOL" publication check "$PWD" "$PWD/build/repository" "$PWD/build/publication-manifest.json"
EXPECTED_INVENTORY="$(cat /tmp/totipo-0.3.2-preparation-080f392/normal-publication-manifest.json)" \
  REQUESTED_VERSION=0.3.2 REQUESTED_COMMIT=080f392d38896f18678709d9edfbb3fd336e4fc8 \
  "$TOTIPO_PUBLICATION_TOOL" release compare "$PWD" "$PWD/build/repository" "$PWD/build/publication-manifest.json"
gradle -p publishing/consumer-smoke --offline -PpomOnly clean check
```

The comparison command's starting SHA is a syntactically required input, not
committed release-source proof: `compare` validates version/current artifacts and
accepted inventories, without checking clean Git source/tag identity. A separate
read-only call to the permanent source/notes/provenance validator passed exact
VERSION/LF, exact r19 pin and versioned notes. Its rendered provenance is a local
preview using starting HEAD, not a body authorized for publication. The protected
workflow must bind artifacts and final notes to the eventual reviewed commit.

No Gradle or JUnit invocation failed or required a rerun. Auxiliary preparation
issues were corrected without source edits: an old launcher initially lacked its
cached Jackson JAR; a temporary launcher-writing attempt found Python unavailable
and used shell instead; an all-class API diff exposed only the expected internal
anonymous stream change and was then scoped explicitly; an early JAR listing ran
while offline `clean` had removed staging and passed when repeated after staging.
No gate was weakened or test failure suppressed.

## Expected unsigned artifacts and reproducibility

Each artifact is under
`build/repository/org/totipo/<artifact>/0.3.2/`. These are local unsigned artifacts;
detached signatures are produced only by the human-authorized protected workflow.
Identical normal/offline accepted SHA-256 values:

| Artifact filename | SHA-256 |
| --- | --- |
| `totipo-core-0.3.2.jar` | `db35f01b51ba1777b3411306824559126179d868199c40281534b8b02da6bd98` |
| `totipo-core-0.3.2-sources.jar` | `044ea95e2d6eafe2b34f2accb2c959a12a5bdd008d350c9841fb75c9bac6c0d0` |
| `totipo-core-0.3.2-javadoc.jar` | `f41f0322247bd87fcb88d399fca446dc90b7b0e3e93e87cf26273d99f0d5685f` |
| `totipo-core-0.3.2.pom` | `3f873b517e72f70bbcd5d47ee7df8c102ac0f1dc05ab23aaf23d42c481802cd7` |
| `totipo-core-0.3.2.module` | `a46eb72f8c00060244f67667dcd462bc4f3a557a9fa02eb687b2eeee1a681f74` |
| `totipo-storage-nio-0.3.2.jar` | `c680ac4b09d68b2635b7bd4bfc3fb77b2519634df57c91b57a7b63045ea458bb` |
| `totipo-storage-nio-0.3.2-sources.jar` | `14a691e74bcde1f5cddd5a5b70783b53bc9f85aa36890dd877e5126d607a6968` |
| `totipo-storage-nio-0.3.2-javadoc.jar` | `56aa65e7447bce979d19f0a1c7eb12e5616fc87f858d0578913c2f836d52aaa3` |
| `totipo-storage-nio-0.3.2.pom` | `7bf48a433e23ba97a5db72586a0fa10bb1b297ae2a2254ade80054c198fd4ce9` |
| `totipo-storage-nio-0.3.2.module` | `a9962bde6e871ad8aba84f193cddf5a44954dd415c388bd776ad503ce089f86b` |

The permanent standalone `release compare` operation passed (exit 0): **10/10
whole-artifact hashes match**, including both binary/source/Javadoc JARs, POMs
and module metadata. Each new staging repository received semantic publication
acceptance before manifest recheck/comparison. The verifier checks
essential Maven metadata and the ten expected opaque artifacts; Gradle owns JAR
assembly. Whole-artifact hash identity does not independently prove member-level
packaging correctness. Consistent mispackaging satisfying metadata/consumer gates
remains the checklist's accepted residual risk; no such stronger proof is claimed.
Ephemeral Maven indexes are not artifact identity and are not normalized/compared.
Manual binary JAR inventories show 187 core and 21 NIO classes, with no test,
fixture, Android or bundled Bouncy Castle entries. This is a bounded inventory
inspection, not a member-by-member assembly correctness comparison.

## Source membership, freeze and remaining gates

Inspected qualification.nix's source filter and flake.nix inputs: VERSION and
publishing inputs are included; review/ is excluded. Nix definitions, flake.lock,
cache inputs, all Java/test sources and required production inputs are unchanged.
The two included edits are frozen at the hashes above. Only excluded report prose
may be finalized after the human gate without invalidating it. Any later included
input edit requires the affected qualification again.

Both new Markdown files are Git-known with retained intent-to-add entries.
No actual file contents are staged. No build output, cache, temporary evidence
or secret was intent-added. `git diff --cached --quiet` and `git diff --check`
pass. Final branch is `main`, HEAD `080f392d38896f18678709d9edfbb3fd336e4fc8`.

`git status --short`:

```text
 M VERSION
 M publishing/consumer-smoke/gradle.lockfile
 A review/JAVA_0_3_2_RELEASE_PREPARATION_REPORT.md
 A review/V0_3_2_RELEASE_NOTES.md
```

`git diff --name-only`:

```text
VERSION
publishing/consumer-smoke/gradle.lockfile
review/JAVA_0_3_2_RELEASE_PREPARATION_REPORT.md
review/V0_3_2_RELEASE_NOTES.md
```

`git diff --cached --name-only` emits nothing.

The user reported **"nix flake check passed"** for this preparation. This is
human-reported evidence; no command log was supplied and the agent ran no Nix.
Follow-up inspection confirmed the same starting HEAD, four-path working-tree
diff and included-input patch SHA-256
`616095607299530f4ddb10a28c0877fa24daccb7c91b30e1b6c18e0ebe604e8a`.
Only this excluded report was updated to record the result. Included inputs
remain frozen, so no Java or Nix rerun is required. Local release preparation
qualification is complete and ready for review/commit.

Remaining release gates/blockers:

1. Human review/commit and successful ordinary CI for that exact release source.
2. Before a separately authorized live dispatch, owner confirms Central capacity
   or an approved adjustment/exemption and the checklist's effective protections
   and credentials. This preparation does not establish those external settings,
   tag/coordinate availability, signing or Central acceptance.

The committed compatibility report is release evidence for JVM/static API 30
compatibility checks, including D8 lowering. No physical Android API 30–33 or
current-device execution is claimed. Android product admission still requires
its normal crypto, backup/ZIP/export/import/restore, provider I/O and lifecycle
qualification, using the released artifacts through its lock/hash/cache process.

All changes remain uncommitted for review. No tag, publication, push or GitHub
release was created, and no live release workflow was dispatched.

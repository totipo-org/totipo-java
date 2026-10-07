# Private local NIO portability investigation

## 1. Starting state

Investigation date: 2026-10-07. Repository: `/home/niki/Sources/totipo-java`.

- Branch: `main`.
- Exact Java HEAD: `e2326aca5f5aac661d74925a30fcc57cd91014e8`.
- Starting `git status --short`: empty; clean before edits.
- `VERSION`: `0.1.3`, unchanged by this milestone.
- Protocol: Totipo Vault Format v1/r18.
- Exact spec pin: `4623a7e1718e23504903096c92332597057bd8f0`.
- Normative snapshot SHA-256: `8a357e75f3ddd92efa954fde2ffc9af33f40a2c2396d6afbf1d5de5f00bc4f8a`.
- Requirements profile SHA-256: `4c7954cd2b59aa0afbbe3c22080cadddf72135d884b186a671266c29d58418df`.

Inspected the committed README, SPI_DESIGN, API_DESIGN, SPEC_PIN, root/module build
files, all `org.totipo.spi` types, current NIO engines and adapters, durability,
namespace/read/temp helpers, core StoreAdapter/VaultLifecycle/ApplicationSession,
and publication/vault/SPI/durability tests. STORAGE_SPI_REPORT (including stateless
root-barrier remediation), V1_R18_REPIN_REPORT, PUBLIC_API_FACADE_REPORT and release
preparation/checklist evidence establish the current contracts and release practice.
Historical `dev.totipo` names in older reports are not current class names.
The pinned, hash-checked normative snapshot is available here; no sibling spec or
Android checkout/report was available. No external repository was modified.

## 2. Android evidence being addressed

The supplied Android M1C physical-device evidence reports `Files.createLink`
failing with `AccessDeniedException` on one API-37 app-private filesystem.
Released `Totipo.create(NioTotipoStore.open(root), credential)` returned Uncertain.
That is enough to reject unchanged released 0.1.3 for this deployment. The exact
kernel/filesystem/SELinux cause is unknown; this is not an Android-wide claim.

File force, directory open/force, released root persistence, atomic replacement
move, ordinary replacement move, temp creation, exact/no-follow observations and
bounded reads succeeded in that run. Dependent immutable publication and VAULT
replacement workflows were not exercised. In particular, success of replacement
move does not qualify a new move-without-options installation path.

## 3. Current NIO installation model

At the starting HEAD:

- Immutable: `NioTotipoStore.publishObject` -> `NioObjectStorage.publish(..., true)`
  -> complete temporary write -> stage `FileChannel.force(true)` ->
  `Operations.link(target, temp)` -> `Files.createLink` -> post-link file force ->
  objects directory persistence -> best-effort temp cleanup. A root-directory
  barrier precedes every new object-target mutation, including reopened stores.
- Initial VAULT: core validates candidate and actual prepared representation ->
  `Prepared.installCanonicalIfAbsent` freshly checks exact `vault` ->
  `NioVaultStorage.Stage.installInitialDurably` -> `Operations.link` ->
  `Files.createLink(root/vault, temp)` -> root persistence. Preparation already
  forced the stage. Close cleans that same stage best-effort.
- Default link installation connects the canonical name to the complete forced
  stage inode without replacing an existing name. Link errors after entry are
  conservatively Uncertain unless a definite collision proves no effect.
- VAULT replacement is separate: exact CURRENT/BASE comparison in core, then the
  same stage moves with atomic replacement and ordinary replacement fallback.
  The legacy `NioVaultBootstrapStorage` adapter retains atomic-only replacement.

`NioV1ObjectPublicationStore` and `NioApplicationPublication` delegate to the same
object engine; `NioVaultBootstrapStorage` delegates to the vault engine. None of
these existing entry points implicitly selects private installation.

## 4. Threat-model comparison

| Property | Shared/direct-sync store | Private/exclusive local replica |
| --- | --- | --- |
| External sync mutates directory | yes/possible | no; separate controlled bridge |
| Independent ordinary writer | possible | excluded by configuration |
| Same-privilege malicious race | baseline trusted | baseline trusted |
| Hard-link exclusion valuable | yes; protects honest competing installations | useful hardening, not required under root-wide serialization |
| Move-if-absent candidate | current no; keep links | acceptable with explicit exclusivity and serialization |

Private means that only the Totipo application has ordinary write access and it
controls every writer, including its reconciliation bridge. It must serialize all
operations for the root across handles/sessions. `TotipoStore` calls and the core
provider gate are serialized per session, not across independent sessions/processes.
Therefore per-session serialization alone is insufficient. Opening does not lock,
verify permissions, or establish exclusivity; the explicit factory documents the
additional application-wide obligation. A single owned session is the simplest
valid arrangement. Bridge mutations must not run concurrently with its operations.

Ordinary errors, interrupted processes, obsolete observations and application bugs
still exist. Absence is refreshed after stage construction; enumeration errors are
not absence. Unexpected targets visible before move are preserved. Generic errors
once move may have begun remain Uncertain, even if a particular injected error
actually occurred before effect. No automatic retry, rollback, deletion of canonical
files, or inference of absence follows uncertainty. Cleanup concerns staging only.

A bug that permits concurrent root writers violates this mode's configuration;
it can cause overwrite inside a provider's check/rename window. The implementation
cannot cure that bug with portable NIO. It does not promise race immunity merely
because the directory is private. Use shared mode when such exclusion cannot be
established. Same-privilege malicious races remain outside baseline r18 in both modes.

## 5. Spec/SPI analysis

### r18 §§2, 7 and 18

§2 trusts the local execution environment and same-privilege processes for baseline
conformance. §§7/18 mandate separately constructed complete representations,
reasonable crash-safe publication and required persistence acknowledgement, without
mandating hard links or a particular atomic primitive. §7 prohibits knowingly
overwriting existing canonical VAULT. Wrong-kind presence is not absence.
§18 requires unsuitable/different existing objects to survive unchanged, forbids
deliberate progressive canonical fill/truncation, and requires ambiguous failure
never to be success or inferred absence. Its last paragraph expressly accounts for
intermediate effects despite reasonable complete-byte staging on weaker stores.

Moving a complete forced stage in the same directory satisfies those construction
requirements. Provider-internal partial effects from an unsuccessful ordinary move
are not an application strategy of progressively constructing the canonical file.
Such effects are Uncertain, remain invalid diagnostic material, and are never
cleaned up or repaired as ordinary immutable publication. No reinterpretation of
§18's prohibition is needed. The strongest reasonable mechanism must also respect
existing-target preservation; atomic move is not a stronger usable no-replace
primitive under Java's contract. Default shared deployments retain hard links.

Two honest first creators may have different roots. Shared mode still excludes the
loser at hard-link installation. Private mode requires root-wide serialization:
the next creator freshly sees the first canonical VAULT and returns AlreadyPresent
(SPI), translated to AlreadyExists by core. Independently concurrent honest creators
are unsupported private configuration, not made safe by root equality assumptions.

### Released/current SPI and ownership

The SPI mandates create-or-confirm-exact, actual-stage read-back, same-stage mutation,
one mutation attempt, positive configured durability acknowledgement, and honest
Failed/Uncertain outcomes. It specifies no atomic exclusion primitive or CAS.
Its local trust qualifications already disclaim adversarial namespace snapshots.
Under exclusive serialized root use, a non-replacing complete-stage move meets the
existing contracts. No result variant, interface member, sealed hierarchy or core
serialization implementation needs changing.

Core continues to authenticate the actual VAULT stage before mutation and to own
protocol sizes, object identities, graph semantics and exact compare-before-replace.
Private installation does not reconstruct the stage from the original input array.
`Totipo.open/create` still transfers ownership on every outcome; success transfers
the store to the session, and failure closes it.

The stronger SPI/NIO exact-existing acknowledgement remains a separate Java contract
issue: r18 permits read-only equality success without a new persistence barrier.
Legacy low-level publication uses that read-only path; SPI publication still forces
the existing file, objects directory and root, then reconfirms bytes. Private mode
retains this behavior, including Uncertain if any acknowledgement fails. Revisiting
that broader contract is not necessary to make private installation work.

## 6. Candidate strategies

| Strategy | Decision | Reason |
| --- | --- | --- |
| A. Complete stage + hard link | retain as default | strongest current shared-store exclusion; tested Android installation denied |
| B. Complete stage + fresh absence + ordinary move without options | implement privately | preservation contract plus root-wide application exclusion; explicit uncertainty and canonical force |
| B. ATOMIC_MOVE without REPLACE_EXISTING | reject for initial installation | Java leaves existing-target behavior implementation-specific |
| C. CREATE_NEW + copy/materialize canonical | reject | deliberate progressive canonical fill violates §18; would require semantic/spec review |

Java contract summary: ordinary move fails for an existing distinct target; the
same-file case has no effect. `FileAlreadyExistsException` is an optional specific
exception, so generic IOException is not proof of collision. ATOMIC_MOVE ignores
other options and leaves existing-target replacement provider-specific. Unsuccessful
non-atomic moves may leave undefined source/target state, including partial targets.
Ordinary move is used directly, with no atomic attempt/fallback for installation.
This follows the [Java SE 17 Files.move contract](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html#move(java.nio.file.Path,java.nio.file.Path,java.nio.file.CopyOption...)).

The installed JDK 25.0.4.1 `src.zip`, `sun/nio/fs/UnixFileSystem.java`, confirms the
local provider's ordinary move checks target attributes, rejects existing targets
without replacement, then calls rename, with copy/delete for certain fallback
errors. Atomic move calls rename directly. There is a real provider-internal
check/rename race: omission of REPLACE_EXISTING does not supply atomic exclusion.
The corresponding [OpenJDK 25 implementation](https://github.com/openjdk/jdk/blob/jdk-25-ga/src/java.base/unix/classes/sun/nio/fs/UnixFileSystem.java)
is implementation evidence, not a dependency of correctness or an Android claim.

## 7. Decision

**IMPLEMENT — IMPLEMENTED PRIVATE MODE.**

A small additive mode is sufficient for an exclusively controlled, serialized local
replica, subject to provider qualification. Shared behavior remains unchanged.
This decision does not qualify the Android device or shared directories for moves.

## 8. API design

Added only:

```java
NioTotipoStore.openPrivate(Path root)
NioTotipoStore.openPrivate(Path root, StorageDurability durability)
```

For a later Android probe or another private replica:

```java
Totipo.create(NioTotipoStore.openPrivate(root), password);
Totipo.open(NioTotipoStore.openPrivate(root), password);
```

The existing root must be an application-private/exclusive local store not
independently mutated by synchronization software or other ordinary writers.
Root-wide serialization includes bridge work and all handles/sessions. This mode
is inappropriate for a desktop directly synchronized/shared vault directory unless
its exclusivity assumptions are separately established. No opaque boolean option,
public policy hierarchy, automatic capability fallback, or convenience-facade
expansion is introduced.

## 9. Implementation

- `NioCanonicalInstaller`: small internal HARD_LINK/PRIVATE_MOVE strategy; private
  absence observation and ordinary `Files.move(stage, target)` with zero options.
- `NioTotipoStore`: two additive private factories; existing factories unchanged.
- `NioObjectStorage`: shared complete-byte staging, namespace/root barriers,
  collision comparison, outcome mapping and cleanup. Private mode closes the forced
  stage before movement and forces the actual canonical file afterward. Default
  mode retains the original stage channel and hard-link force sequence.
- `NioVaultStorage`: shared staging/read-back/lifecycle/cleanup; private installation
  forces the actual canonical file before root persistence. Replacement and its
  fallback logic remain the same in both modes.
- README, API_DESIGN, SPI_DESIGN and public factory Javadocs document assumptions.
- `NioPrivateStoreTest` and `PrivateNioWorkflowTest` add focused provider and facade
  coverage. No production core, Android, Nix, dependency, protocol or version edits.

Historical test event names `before-link`, `after-link`, `post-link-sync` are retained
for existing fault fixtures; private mode uses these shared mutation/force checkpoints
without linking. They do not imply private mode invokes createLink.

Private object sequence:

```text
ensure exact safe namespace -> root force -> complete stage -> stage force/close
-> freshly establish exact absence -> enter ordinary move -> exact name confirmation
-> canonical file force -> objects directory force -> best-effort stage cleanup
```

Initial VAULT sequence:

```text
complete stage -> stage force/close -> core actual-stage validation
-> exact presence handling -> fresh private absence check -> enter ordinary move
-> canonical file force -> root force -> close/cleanup same stage
```

| Outcome | Immutable object | Initial VAULT |
| --- | --- | --- |
| Observed existing target | exact -> existing barrier -> AlreadyPresentExact; different -> ExistingDifferent; unsuitable -> Failed | AlreadyPresent, including exact wrong-kind presence; unchanged |
| Definite move collision | inspect exact winner; same outcomes as above; unresolved alias -> Failed(UNSAFE_NAMESPACE) | exact winner -> AlreadyPresent; unresolved alias -> Failed(UNSAFE_NAMESPACE) |
| Failure before mutation entry | Failed | Failed, or VaultPrepare.Failed during preparation |
| Generic error after entry, even actual pre-effect error | Uncertain | Uncertain |
| Canonical/namespace force failure after installation | Uncertain; retain canonical | Uncertain; retain canonical |
| Successful installation and persistence acknowledgement | Written | Installed |

Existing result types express all outcomes. Definite collision resets mutation
certainty before observation/handling; an exact-object retry re-enters the stronger
acknowledgement path and can still become Uncertain. No generic exception is treated
as proof of absence or as permission to overwrite.

## 10. Tests

Focused tests include both private public factories; read-only opening; full
create/save/rewrap/reopen; first publication and two serialized object names; exact
retry and every existing acknowledgement failure; different and nonregular object
preservation; initial VAULT success, different bytes, directory/symlink presence,
handle consumption, actual-stage installation and cleanup; unchanged replacement
fallback; shared hard-link failure without automatic fallback; private mode avoiding
hard-link seams; root/stage/canonical/directory force failures; definite preconditions;
move collision; generic pre-effect and post-effect move errors; copy-based provider
move forcing the canonical file; partial move failure without rollback or repair.

Deterministic unexpected-target injection occurs after private fresh absence and
before Files.move invocation; target bytes survive and collision results are correct.
A separate actual Files.move collision test establishes the host behavior. These
fixtures do not prove immunity to a writer inside the provider's own check/rename
window. The latter violates private root-wide exclusion and is explicitly disclaimed.
Existing shared publication/concurrency, VAULT, SPI, durability, exact-name, facade,
replacement and core conformance tests are retained unchanged.

## 11. Compatibility

Source and binary compatible additive public static factories. Existing constructors,
method descriptors, sealed result exhaustiveness, modules and Maven coordinates/
transitive dependencies are unchanged. Default `NioTotipoStore.open`, `NioTotipo.*`
and legacy adapters retain the existing shared behavior. Private mode is an explicit
behavioral choice for new callers. Desktop clients receive no implicit weakening.

The next suitable Java version is **0.1.4** under this repository's pre-1.0 additive
patch-release practice (the additive keep API was prepared as 0.1.3). `VERSION`
remains 0.1.3: release preparation/version selection is separate operator work.
Locally verified artifacts use that unchanged development version, not a new release.
No source/binary break or Maven module migration is required.

## 12. Protocol impact

**No protocol change.** No spec revision, wire-format change, cryptographic change,
conformance-vector change or normative semantic change is required. Complete staging,
existing-target preservation under the selected deployment assumptions, persistence
acknowledgements and uncertainty conform to the same pinned r18 requirements.
The 90-case corpus and pinned snapshot remain unchanged. Shared-store guarantees
and the separate exact-existing acknowledgement contract issue remain unchanged.

## 13. Validation

### Agent non-Nix

Final validation results are recorded below after execution. The existing installed
JDK 25.0.4.1 and repository Gradle 9.8.0 wrapper are used; no toolchain replacement
and no Nix command was executed. The wrapper downloaded its pinned distribution.
Publication verification writes only the repository's local unsigned staging build
output and never publishes to a remote repository.

| Check | Result |
| --- | --- |
| Explicit NIO private/SPI/publication/VAULT/durability/contract suites | PASS; followed by complete final suite |
| `./gradlew clean test build verifyPublication consumerSmoke` | PASS, 30 tasks executed, 1m40s; before final extra test fixtures |
| `./gradlew --offline --no-daemon --no-build-cache --rerun-tasks clean test build verifyPublication consumerSmoke` | PASS, final code, 30 tasks executed, 1m53s |
| Final JVM test inventory | 524 tests: 378 core + 146 NIO; zero failures/errors/skips |
| New private coverage | 31 NIO test invocations + 1 facade workflow |
| Portable corpus / integrity | 90/90 corpus; snapshot integrity 3/3, r18 profile 1/1; 97/97 snapshot SHA-256 records match |
| Unsigned artifact inventory | all ten normal/forced-offline artifact hashes identical |
| POM-only standalone consumer | PASS after final forced build; Gradle metadata consumer also passed |
| Python release guardrails | 21/21 pass; Python 3.14 emitted HTTPError fixture ResourceWarnings |
| Compiler/static/build boundaries | Java 17 classfiles, -Xlint:all/-Werror, strict dependency verification/locks, publication contents/scopes and consumer dependency boundaries pass |
| Formatting | no separate formatter/static-analysis Gradle task configured; diff whitespace checks pass |

Javadocs build successfully with the repository's existing missing-comment/tag
warnings (including internal methods); compiler warnings remain fatal as configured.
No claim of universal physical power-loss behavior, platform-wide move atomicity,
or Android qualification is made.

### Human Nix

**PASS — reported by the user and followed by agent inspection.** `flake.nix` exposes development
shells and a formatter, with no package/default build or check derivations. Therefore
plain `nix build` has no repository-defined default target and is not proposed.
The repository-standard Nix-shell validation is:

```sh
nix flake check
nix develop --command ./gradlew --offline --no-daemon --no-build-cache --rerun-tasks clean test build verifyPublication consumerSmoke
```

The user reported successful execution of both commands and `git status --short`,
supplying the resulting status. The agent then independently inspected branch, HEAD,
status, diff statistics, whitespace checks and the index. Status exactly matches the
supplied output and the recorded milestone changes; no additional tracked or
untracked changes were generated. Branch and HEAD remain unchanged, `git diff --check`
passes, and `git diff --cached --stat` is empty. Nix outputs were not independently
captured by the agent; their success is user-reported. Human Nix validation is complete.
No Nix inputs/files were edited and the agent executed no Nix command.

### Remote CI

Not run; no commit/push/remote CI dispatch was authorized or performed. Existing CI
runs build/publication/consumer checks and Python release guardrails; local equivalents
are used here. No remote qualification is claimed.

## 14. Android follow-up

If a separately reviewed 0.1.4 release is cut, Android should retain the original
shared-mode M1C rejection and explicitly use `NioTotipoStore.openPrivate(root)` for
its exclusive app-local replica. Establish that synchronization/SAF/cloud bytes
arrive only through a serialized controlled bridge, never direct directory mutation.

On the physical device retest move-without-options initial installation, actual-stage
VAULT validation/creation/opening, immutable TOKEN publication/exact retry/different
preservation, namespace/file/root persistence acknowledgements, close/reopen and
password rewrap preserving root through the existing replacement path. Test existing
VAULT preservation, uncertainty recovery and stage cleanup, and report platform,
filesystem/provider and app-directory conditions. Recheck operation capabilities
rather than generalizing M1C's successful force/replacement calls. JVM tests and the
new factory do not establish Android qualification or physical crash survival.

## 15. Final Git state

```text
$ git status --short
 M API_DESIGN.md
 M README.md
 M SPI_DESIGN.md
 M storage-nio/src/main/java/org/totipo/storage/nio/NioObjectStorage.java
 M storage-nio/src/main/java/org/totipo/storage/nio/NioTotipoStore.java
 M storage-nio/src/main/java/org/totipo/storage/nio/NioVaultStorage.java
?? core/src/test/java/org/totipo/api/PrivateNioWorkflowTest.java
?? review/NIO_PRIVATE_LOCAL_PORTABILITY_REPORT.md
?? storage-nio/src/main/java/org/totipo/storage/nio/NioCanonicalInstaller.java
?? storage-nio/src/test/java/org/totipo/storage/nio/NioPrivateStoreTest.java

$ git diff --stat
 API_DESIGN.md                                      | 14 +++++++
 README.md                                          | 15 ++++++++
 SPI_DESIGN.md                                      | 45 ++++++++++++++++++++++
 .../org/totipo/storage/nio/NioObjectStorage.java   | 34 ++++++++++++++--
 .../org/totipo/storage/nio/NioTotipoStore.java     | 27 +++++++++++++
 .../org/totipo/storage/nio/NioVaultStorage.java    | 24 +++++++++++-
 6 files changed, 154 insertions(+), 5 deletions(-)

$ git diff --check
(empty; exit 0)

$ git diff --cached --stat
(empty; exit 0)
```

`git diff --stat` omits untracked files: the report, internal installer, private NIO
tests and facade workflow are listed in status and remain untracked/unstaged.
No existing tests were edited. HEAD and branch remain exactly as recorded above.

Nothing staged, committed, tagged, released or pushed. Agent non-Nix and human Nix
validation are complete. Work is ready for review; Android follow-up and remote CI
remain separately identified qualification steps, not claims of this milestone.

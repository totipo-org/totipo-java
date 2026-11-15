# BACKUP_RESTORE_R3 — Shared Java new-location restore

Started from clean `main` at `1912779bdd9e6b75a6f4faa1b4857f38356e5f55`.
Classifications: CORE_RUNTIME, STORAGE_NIO, PUBLIC_API (including experimental
SPI), CONSUMER_BOUNDARY, DOCS_JAVADOC. Publication acceptance qualifies the new
packaged API; Maven metadata policy itself is unchanged. No dependency, build,
cache, toolchain, release tooling, protocol, golden fixture or other repository
change. Implementation remains uncommitted, without staged file contents.

## Reviewed contracts and consumer constraints

Reviewed AGENTS.md, API_DESIGN.md including operation/state-snapshot semantics,
SPI_DESIGN.md, docs/BACKUP_ARCHIVE.md, all Java R0–R2 reports, SPEC_PIN.md and the
vendored v1/r19 specification. Implementation review covered bootstrap unlock and
creation, password/root ownership, ordinary object validation, R1 bounded archive
verification, R2 authenticated replay/accounting, NIO exact-name reads, exclusive
hard-link publication, coordinated moves, durability and exact-existing recovery.

Read the pinned [Android R0](https://github.com/totipo-org/totipo-android/blob/a67d09c5e308a46da3562e7de2a2df9181a77ee4/review/BACKUP_RESTORE_R0_ANDROID_DESIGN.md)
and [Desktop R0](https://github.com/totipo-org/totipo-desktop/blob/0acbb61ca074bfa0a7aeed15758553d765375a57/review/BACKUP_RESTORE_R0_DESKTOP_DESIGN.md)
over read-only HTTPS. Android requires its retained-root duplicate checks, durable
restore intent, PENDING accounting and whole-root storage owner before promotion.
Desktop requires a nonexistent child destination, application-owned worker
retirement and explicit recovery. Neither consumer treats bootstrap-only open,
stream completion, or observation requests as completed restoration. No Android or
Desktop repository was checked out or modified.

## Public API and detached authority

`BackupArchives.prepareRestore(InputStream, BackupLimits, char[])` first freezes
and completely verifies R1's exact bounded ZIP profile. The caller owns the stream
and password characters. Existing limits remain 4096 objects/6 MiB ingress; lower
caller budgets are honored. Complete ZIP structure/inventory verification precedes
KDF work. Invalid password encoding is a programmer error; a new coarse
AUTHENTICATION_FAILED reason does not claim a definitely wrong password.

VaultUnlocker performs the existing bootstrap authentication. The factored
HistoryImport.validateArchive uses TokenStoreReader.validate for every object:
AEAD, keyed ID, envelope framing/padding and canonical TOKEN interpretation are
unchanged. All candidates validate before authority returns. Parsed secrets are
cleared immediately, and owned password encoding, wrapping key and root copies are
wiped before prepare returns. The plan retains neither root nor password nor a
session. Manifest gap claims are ignored for authenticated diagnostics; missing
and wrong-token ancestry are counted and preserved without inventing parents.

The owned BackupRestore exposes only immutable summary, one reservation install,
explicit retry, cancel and close. It never exposes ciphertext arrays, a mutable
store, arbitrary publication parameters or a root. Public inspection/summaries and
constructible result records remain descriptive, not authentication authority.
Existing session-bound additive import still requires its authenticated matching
session. Its preparation/preflight/binding, immutable replay, closure and outcomes
are preserved; only genuinely shared validation and receipt support were factored.

## Provider reservation and no-overwrite decisions

The narrow opt-in RestoreReservation extends the existing opaque TotipoStore;
this avoids duplicating provider reads/publication. It adds only archive intent
recognition, installVault's bootstrap exact-retry acknowledgement, and a location
persistence barrier. Ordinary TotipoStore/createVault are unchanged. There is no
new delete/replace/rename API or filesystem transaction framework.

NioRestore.reserve(parent, child, archiveDigest) exclusively creates exactly the
requested direct child using Files.createDirectory. Every preexisting entry,
including an empty directory, symlink or regular file, blocks it. Concurrent
reservations have one winner. Exact spelling is checked. No preliminary emptiness
check authorizes reuse. The path is parent.resolve(child), so Desktop can display
it before work. Failed reservation may leave allocation evidence at that path;
canonical installation has not begun. No recursive cleanup/reuse occurs.

A noncanonical `.totipo-restore-v1` marker binds the allocation to the exact frozen
ZIP SHA-256. It is forced along with root and parent before bootstrap installation
and at completion. It remains after COMPLETE for recovery. An interrupted/torn
marker is refused and retained for investigation, not silently repaired. This
marker is local provider allocation evidence, not protocol material or proof
against a malicious same-privilege process. Digest recognition never substitutes
for core's independent authentication.

Ordinary reservations retain existing complete-stage hard-link publication and
no-overwrite exclusion. There is no link-failure fallback and no ATOMIC_MOVE
assumption. NioStoreComposition.reserveRestore/recoverRestore expose the existing
move-based composition only for one externally serialized whole-root owner, with
all bridges, sessions and handles in that domain. An independent writer outside
that domain is unsupported for coordinated moves. No weaker portable no-replace
claim is made.

## Bootstrap durability and exact recovery

Installation never calls Totipo.create, draws a new root, rewraps, re-encrypts or
extracts archive paths. It publishes the exact authenticated bootstrap first via
the existing create-only provider, then reuses R2 object preflight/replay in sorted
ID order. It freshly binds exact VAULT before object publications and at completion.
After all acknowledgements it rereads the complete planned object inventory and
VAULT, acknowledges root/parent persistence, then reconfirms inventory and VAULT.

The privately frozen bootstrap was already password-authenticated. Fresh exact
byte equality revalidates that same authenticated representation/root without
retaining credentials or repeating Argon2 at installation. This concretizes R0's
post-install authentication intent by exact authenticated-byte binding, rather
than retaining a password/root solely for another KDF. Original-password reopening
is also covered by real-NIO tests.

R0's bootstrap acknowledgement gap is resolved by restore-specific exact-existing
acknowledgement: force the actual canonical regular file, root and parent; freshly
reconfirm exact bytes. Never rewrite, rename, touch, chmod or replace the bootstrap
to obtain acknowledgement. Ordinary createVault still returns AlreadyPresent and
cannot masquerade as a recovery acknowledgement. Object exact retries retain their
existing file/object-directory/root barriers.

While a plan is alive, retry() explicitly replays its frozen bytes on its sole
owned reservation. After cancel/close/shutdown, authenticate the same exact ZIP
again and explicitly NioRestore.recover(originalPath, digest), then install the
new plan. Recovery requires the original provider marker and real root; ordinary
existing vaults or empty folders do not qualify. Different/unsuitable canonical
entries remain untouched. Missing staging, mismatched archive or corrupt intent
never permits fresh creation in that root. A new process cannot infer which old
process created an exact object: new acknowledgements are retained receipts, while
prior application journal evidence remains independently meaningful.

## Partial, uncertain and interrupted outcomes

RestoreResult independently reports overall outcome, bootstrap knowledge,
per-attempt location acknowledgement, cumulative ImportResult counts and bounded
immutable sorted per-revision receipts (remaining/installed/retained/uncertain).
This identifies the known installed objects rather than reporting token counts.
Acknowledgement is knowledge, not a perpetual retention guarantee.

| Outcome | Meaning |
| --- | --- |
| REJECTED | This plan has no acknowledged or possible canonical installation; reserved directory/marker may remain. On explicit cross-process recovery, NOT_INSTALLED is not an assertion that old canonical data is absent. |
| COMPLETE | Bootstrap, location and every archived object acknowledged, with complete final exact identity/inventory checks. Does not imply complete ancestry, globally newest history, registration or observation. |
| PARTIAL | Bootstrap acknowledged but restoration not complete, including zero objects installed, target conflict, cancellation, or detected later object loss. |
| UNCERTAIN | Entered bootstrap/object/location work lacks acknowledgement. Takes precedence over partial progress; later failed/cancelled calls preserve cumulative uncertainty until positive exact recovery. |

An existing valid bootstrap and subset of objects can be opened by another process;
that observation is not a restore completion receipt. Never roll back canonical
data: another observer may rely on it. Partial/unregistered allocations remain
retained and require explicit product recovery. No bootstrap-only promotion or
silent conversion to ordinary Create/Join/import exists.

Cancel permanently stops new work at bounded boundaries. Close requests cancellation
before waiting for admitted work, wipes ciphertext and closes the owned reservation
without deleting the directory or canonical data. Already entered provider work
retires normally and may acknowledge, fail or remain uncertain. Thread interruption
can prevent provider force acknowledgement; late cancellation may lose to complete
acknowledged work. A cleared thread interruption permits explicit same-plan retry;
a permanent cancel requires a new plan/recovered reservation. Failed provider calls
are conservatively accounted, including unchecked lost acknowledgements. Provider
cleanup follows the existing best-effort close convention and cannot erase receipts.

One monitor serializes only this detached plan. There is no cryptographic root or
application lock across filesystem work, no application callback, and no state
emission freshness gate. Potentially blocking preparation/install/retry/close belong
off UI threads. Applications own blocked ingress/provider cancellation and shutdown
retirement; releasing UI ownership cannot retract already entered storage work.

## Android/Desktop integration requirements and limits

Android may implement the SPI using its existing exclusive private allocation and
storage authority. Core has no Path requirement; SAF is only ZIP transport. Android
must journal intended local ID, archive digest, expected identity/inventory and
attempted restore before publication, retain partial/unregistered roots, and decide
registry promotion only after storage completion plus its own normal authentication/
observation requirements. It must wrap the coordinated delegate in its existing
whole-root domain and call plan operations outside bridge/application locks.

Desktop supplies an existing parent and new child, keeps workers alive through safe
retirement, presents the actual path/limited-history diagnostics, and offers explicit
recovery of retained allocations. Shared Java does not register, select, unlock,
start Sync, choose duplicate policy, or alter platform permissions. Successful normal
opening is a separate application action. Both clients own duplicate recognition,
known-gap disclosure/acknowledgement and late-result presentation relevance.

Directory-channel force acknowledgement remains the existing platform capability,
not universal physical power-loss proof. Unsupported barriers refuse completion;
unsupported hard links have no move fallback. Tests qualify the local Linux NIO
behavior and injected real-filesystem fault boundaries, not Android physical-device,
Windows/macOS, SAF, cloud durability or hostile local namespace transactions.
Missing/torn intent requires explicit investigation/new allocation; arbitrary damaged
or orphan stores are not repaired. Histories exceeding existing limits still fail
capacity without clipping. There is no overwrite restoration, migration or UI.

## Tests and qualification evidence

Evidence lives outside build outputs in ignored `.gradle/backup-r3-evidence/`.
The active locked environment provides JDK 25 and Gradle 9.8.2; the agent executed
no Nix. The prebuilt independent launcher is reused from
`.gradle/backup-r1-evidence/tooling/bin/totipo-java-tool`; Gradle does not compile it.

NewLocationRestoreTest covers all R1 goldens, detached ownership, caller password/
source mutation, malformed/short/zero-read/capacity/interrupted ingress, wrong
password, invalid encoding, authenticated-invalid TOKEN, keyed ID mismatch and AEAD
tampering. NioRestoreTest covers empty/history/missing-parent restoration in shared
and coordinated modes; exact bootstrap/object inventory; original-password open;
conflicting live/tombstoned heads; existing/empty/file/link/wrong-kind destinations;
concurrent reservation; absent/mismatched intent; pre-bootstrap failure; lost
bootstrap acknowledgement and explicit reopen recovery without mtime change; first/
middle/final object failures and lost acknowledgements (IO and unchecked); cumulative
per-object receipts across failed/cancelled retries; explicit exact retry; bootstrap/
object-boundary interruption; blocked publication with cancel/close; retained
unregistered partial roots; final inventory loss; lost location acknowledgement;
unsupported durability; and preservation of unrelated/preexisting bytes.

Focused campaigns also run R2 HistoryImportTest and the R1 archive/adversarial/golden
suites unchanged. The full normal gate supplies the existing protocol/API, publication
and independent consumer evidence. Invocation counts, final results and Git state
are finalized below, including the subsequently reported successful human Nix gate.

## Final validation accounting

| Layer | Invocations and outcome |
| --- | --- |
| Small broad baseline `gradle test` | 1 PASS; module test tasks up-to-date; 13 actionable tasks, 2 executed/11 up-to-date. Existing complete 491-test XML accounted and preserved before focused runs. |
| Compile-only Gradle iteration | 1 PASS, `:core:compileJava :storage-nio:compileJava`. |
| Focused Gradle test invocations | 4: first stopped at test compilation (`-Werror` unused try-resource); corrected by reading the resource's digest. Subsequent three campaigns PASS. No test failure was suppressed. |
| Focused standalone tooling tests | 0; no tooling change. |
| Stable full Java gate | 1 PASS: `gradle test build javadoc verifyPublication consumerSmoke`; 1m 13s; 27 actionable tasks, 21 executed/6 up-to-date. Both module test tasks executed. |
| Full-suite standalone accounting | 2 PASS invocations: baseline 491 and final 534; snapshot 3/profile 1 nonzero, 92/92 portable exact-set execution assertion, no failures/errors/skips. |
| Publication semantic acceptance | 1 PASS, nested final verifyPublication; 10/10 artifacts accepted. consumerSmoke's dependency is not a second acceptance execution. |
| Standalone current-byte manifest recheck | 1 PASS, `publication check`; 10/10 accepted artifacts reread. |
| Module-metadata independent consumer | 1 PASS, nested isolated forced-offline consumerSmoke; 4 tasks executed. Existing isolation/offline flags unchanged. |
| POM-only independent consumer | 1 PASS, `gradle -p publishing/consumer-smoke --offline -PpomOnly clean check`; 8s, 4 tasks executed. |
| Separate host forced-offline full Gradle suite | 0; no dependency acquisition, resolution, fixed-cache or build-behavior change requiring independent host-offline evidence. The required consumers above are not full-suite reruns. |
| Agent Nix / human Nix | 0 / 1; user reported the final `nix flake check` passed. The agent executed no Nix. |
| Dependency/cache checkpoint / release reproducibility | 0 / 0; neither applies to this routine runtime/API milestone. |
| Commit / tag / release / other-repository mutation | 0 / 0 / 0 / 0. |

Final accounting, immediately after full Gradle completion and before any other
test invocation (there were no subsequent test runs):

```text
92/92 portable cases: exact-set execution assertion passed
534 JUnit tests; zero failures/errors/skips; snapshot 3; profile 1
publication verify: 10/10 accepted artifacts passed
publication check: 10/10 accepted artifacts passed
```

The 43 new executions comprise NewLocationRestoreTest's 4 and NioRestoreTest's 39.
Full XML is preserved as `final-xml` before consumer-only cleanup. Baseline and
successful focused XML are separately retained; focused XML is not presented as
full-suite evidence. Logs record up-to-date/nested task execution. Javadoc completed
with the repository's nonfatal documentation warnings; strict compilation and
Java 17 production-bytecode checks passed. No included input changed after this
stable full gate, and no Java qualification invalidation/rerun is required.

## Frozen inputs and completed human qualification

Inspected qualification.nix's selected trees/files and flake.nix's qualification
inputs. Core/NIO production and test sources, and publishing consumer sources are
included. API_DESIGN.md, SPI_DESIGN.md, docs/BACKUP_ARCHIVE.md and review/ are excluded.
Nix definitions, flake.lock, package-deps, Gradle configuration, locks and strict
verification metadata remain unchanged. Every new milestone file including this
report is Git-known through intent-to-add only. Ignored evidence/build/cache state
has not been added to source membership. Included inputs are frozen; only this
excluded report is finalized after Java qualification.

AGENTS.md says “Agents must not run Nix” and reserves the single final routine gate
to the human. The user reported that the following command passed on the frozen
Git-aware working tree:

```sh
nix flake check
```

This human-reported pass completes milestone qualification; it is not represented
as an agent-executed check. Only this excluded report was edited afterward by the
agent. No second build/cache update, Nix rerun, release comparison or release action
is needed. A later included-input edit would invalidate the human result. Changes
remain uncommitted with no staged contents and intent-to-add entries retained.

## Exact final Git state and changed files

Branch remains `main`; HEAD remains
`1912779bdd9e6b75a6f4faa1b4857f38356e5f55`. `git diff --check` and
`git diff --cached --quiet` pass. The following outputs are the complete milestone
inventory. Leading spaces matter: M denotes unstaged modifications; A denotes
intent-to-add without staged contents. All 17 paths remain uncommitted.

`git status --short`:

```text
 M API_DESIGN.md
 M SPI_DESIGN.md
 M core/src/main/java/org/totipo/backup/BackupArchives.java
 M core/src/main/java/org/totipo/backup/BackupException.java
 A core/src/main/java/org/totipo/backup/BackupRestore.java
 M core/src/main/java/org/totipo/backup/BackupSummary.java
 A core/src/main/java/org/totipo/backup/RestoreResult.java
 M core/src/main/java/org/totipo/format/HistoryImport.java
 A core/src/main/java/org/totipo/format/NewLocationRestore.java
 A core/src/main/java/org/totipo/spi/RestoreReservation.java
 A core/src/test/java/org/totipo/format/NewLocationRestoreTest.java
 M docs/BACKUP_ARCHIVE.md
 M publishing/consumer-smoke/src/main/java/ConsumerSmoke.java
 A review/BACKUP_RESTORE_R3_JAVA_NEW_LOCATION_RESTORE_REPORT.md
 A storage-nio/src/main/java/org/totipo/storage/nio/NioRestore.java
 M storage-nio/src/main/java/org/totipo/storage/nio/NioStoreComposition.java
 A storage-nio/src/test/java/org/totipo/storage/nio/NioRestoreTest.java
```

`git diff --name-only`:

```text
API_DESIGN.md
SPI_DESIGN.md
core/src/main/java/org/totipo/backup/BackupArchives.java
core/src/main/java/org/totipo/backup/BackupException.java
core/src/main/java/org/totipo/backup/BackupRestore.java
core/src/main/java/org/totipo/backup/BackupSummary.java
core/src/main/java/org/totipo/backup/RestoreResult.java
core/src/main/java/org/totipo/format/HistoryImport.java
core/src/main/java/org/totipo/format/NewLocationRestore.java
core/src/main/java/org/totipo/spi/RestoreReservation.java
core/src/test/java/org/totipo/format/NewLocationRestoreTest.java
docs/BACKUP_ARCHIVE.md
publishing/consumer-smoke/src/main/java/ConsumerSmoke.java
review/BACKUP_RESTORE_R3_JAVA_NEW_LOCATION_RESTORE_REPORT.md
storage-nio/src/main/java/org/totipo/storage/nio/NioRestore.java
storage-nio/src/main/java/org/totipo/storage/nio/NioStoreComposition.java
storage-nio/src/test/java/org/totipo/storage/nio/NioRestoreTest.java
```

`git diff --cached --name-only`:

```text
```

The cached-name output is empty; no staged file content exists. Intent-to-add
entries remain in place for human review and eventual commit. No commit is made.

# BACKUP_RESTORE_R2 — Shared Java additive history import

Started clean on `main` at `fde6fbee592b4f12fdc54cd443eaa8f71041e4fb`.
Classifications: CORE_RUNTIME, PUBLIC_API, CONSUMER_BOUNDARY, DOCS_JAVADOC.
Provider behavior is exercised through real shared/coordinated NIO integration;
no SPI/provider implementation, dependency, build configuration, protocol, fixture
bytes, release tooling or release-preparation change is included. All changes
remain uncommitted, with no staged contents. Nix has not been executed by the agent.

## Reviewed contracts and consumer constraints

Read AGENTS.md, API_DESIGN.md (especially operation/state-snapshot semantics),
SPI_DESIGN.md, docs/BACKUP_ARCHIVE.md, Java R0, R1's report, SPEC_PIN.md and the
vendored pinned v1/r19 specification. Reviewed session validation, observation,
publication/retry, ownership/closure, and NIO create-only publication contracts.

Reviewed the pinned [Android R0](https://github.com/totipo-org/totipo-android/blob/a67d09c5e308a46da3562e7de2a2df9181a77ee4/review/BACKUP_RESTORE_R0_ANDROID_DESIGN.md)
and [Desktop R0](https://github.com/totipo-org/totipo-desktop/blob/0acbb61ca074bfa0a7aeed15758553d765375a57/review/BACKUP_RESTORE_R0_DESKTOP_DESIGN.md)
through read-only HTTPS; no other repository was modified. Both require a matching
open session, immutable validated evidence, exact-existing acknowledgement,
non-destructive collisions, explicit partial/uncertain accounting and truthful
observation. Desktop owns worker/lifecycle and import admission. Android owns its
whole-root coordinated delegate/bridge, automatic Sync exclusion, cancellation
owner and observation barrier assumptions. These are not implemented as Java UI or
new provider-independent bridge locks.

## Public API and ownership

Added required `VaultSession.prepareImport(InputStream, BackupLimits)` and public
`org.totipo.backup.BackupImport` plus `ImportResult`. A handle exposes only summary,
apply and close: no roots, mutable stores, arbitrary publication inputs or paths.
Existing public methods and profile-1 ZIP bytes are preserved. BackupException
adds TARGET_CONFLICT; BackupSummary's authentication description now covers the
session-authenticated import summary as well as capture.

Preparation borrows an ordinary nonseekable stream through EOF without closing it.
Untrusted ingress and complete structural verification occur before taking the
provider gate. It retains one fixed archive-budget array (maximum 6 MiB), bounded
record metadata and bounded non-secret token/parent facts during validation.
Payloads are copied one at a time, not retained as a growing payload list. No
filesystem scratch, extraction, new module, dependency or transaction framework.
Concurrent jobs each consume their own bound; callers control concurrent admission.

The returned handle belongs to the open session. Summary/apply/close serialize
through its provider gate; handle close waits for apply and wipes/releases encrypted
evidence. Session ownership machinery also disposes handles on session close.
Prior immutable summaries/results remain descriptive; the handle itself cannot be
used after close. A blocked ingress stream does not delay session close, but the
caller must retire ingress before disposing its own resources. Retry after session
close requires new preparation in a new session. New state arrival does not cancel
or invalidate a prepared import.

## Authentication and immutable plan

ApplicationSession now retains a defensive copy of the exact authenticated
bootstrap supplied by VaultLifecycle. Preparation compares archived VAULT bytes
directly with that copy and with fresh destination bytes; it does not authorize
using a hash alone. Even a valid alternative wrapping of the same root is refused.
No password re-entry, root export or second KDF is needed.

BackupZip's existing complete profile verifier now has an internal owned ingress
result. Public inspection still disposes payloads and returns descriptive data
only. Only successful complete container verification constructs internal replay
material. Import accepts neither ObjectCandidateValidation.Valid nor a public
summary/capture as authority. It independently validates every archived object
through TokenStoreReader.validate (ordinary envelope, keyed ID, padding/framing
and canonical TOKEN validation), clearing each temporary parsed secret immediately.
It recomputes identity/digest and archive-only token-aware parent gaps. A legal but
false manifest gap claim is ignored in favor of computed diagnostics. Missing or
wrong-token parents are permitted, never downloaded, synthesized or discarded.
Archive-origin authenticity, omissions, completeness and freshness remain unproven.

Every archived candidate is authenticated before destination preflight and before
any installation. A known-invalid late candidate rejects the entire preparation;
there is no ordinary salvage prefix. Preflight reads every planned destination
name, accepting only positive absence or exact bytes. It rejects different bytes,
wrong lengths, wrong kinds and unavailable reads before returning authority.
Existing invalid objects outside archive membership remain untouched and available
for ordinary observation diagnostics. Raw byte differences are target conflicts,
not claims to have manufactured a cryptographic collision. Existing graph
contradiction-exclusion behavior is unchanged.

## Installation, collisions and persistence knowledge

Each explicit apply repeats full target preflight and binding checks, then replays
all frozen objects in stable ID order through TotipoStore.publishObject. Exact
existing objects also receive provider acknowledgement and its recovery/durability
barriers. VAULT is freshly compared immediately before each publication and at
completion. The provider rechecks safe namespace and no replacement at publication;
preflight is not a reservation. Concurrent same-byte publication acknowledges;
different-byte publication stops without replacement. No independent copier, VAULT
write, overwrite, delete, prune, parent rewrite, new assertion or merge gate exists.

ImportResult counts cumulative unique-object knowledge: installed (positive Written),
retained (positive exact acknowledgement without a known Written), uncertain
(publication entered without a subsequent positive acknowledgement), and remaining.
These partition archive membership. An uncertain original write later acknowledged
as exact is retained; creation cannot be inferred retrospectively. Definite Failed
and ExistingDifferent preserve previous knowledge. Uncertain/unchecked publication
failure conservatively retain possible effects. Reads/equality never recover lost
acknowledgements. Explicit repeat uses the same validated ciphertext, not a mutable
stream, reauthored TOKEN or reinterpretation of source contents.

COMPLETE requires a full acknowledged replay plus final exact VAULT check. NO_OP
also requires no Written/possible installation ever observed on this handle; empty
archives succeed this way. UNCERTAIN takes precedence over incomplete positive
progress. PARTIAL preserves acknowledged objects after stopping; a failed final
binding can produce PARTIAL even if all objects were acknowledged. FAILED has no
acknowledged or possibly published objects on this handle. Reasons distinguish
storage, binding, target conflict/unavailability, cancellation and closure. Complete
import is not an atomic all-or-nothing filesystem transaction or future retention
promise. External malicious VAULT substitution between checks remains outside the
existing trusted-local namespace model; this adds no cross-file transaction.

## Coordination, cancellation and observation

Apply holds the existing session provider gate for its bounded batch, without the
local secret lock across provider I/O. It adds no external/root lock acquisition
around session calls. Android must enter prepare/apply outside its bridge lock and
use its existing whole-root owner/product admission to exclude bridge workflows.
The coordinated-delegate test wraps the existing NIO ordinary-move provider in one
whole-root domain and releases a simulated bridge before waiting for session work.
This is Java integration evidence, not Android physical-device qualification.

Interruption and closing are checked between objects and before entering each
publication after binding. Already entered provider work retires normally. Prefix
acknowledgements/uncertainty survive cancellation and failure. Completion can win
late cancellation/closure after the last acknowledged object. Session close rejects
new entrants, waits for admitted work, and then disposes import handles and secrets.
Closing/abandoning a handle never rolls back canonical history.

Storage installation is deliberately separate from state observation. Apply neither
requests refresh nor treats an existing Finished state as an import receipt. After
possible writes, applications arrange ordinary observation outside external
coordination, including on partial/uncertain/cancelled outcomes. requestRefresh is
only a request. Android's conditional two-pass barrier remains subject to its
reviewed composition assumptions; Desktop uses ordinary observation convergence.
Tests demonstrate unchanged state immediately after apply, followed by explicit
observation and ordinary merge of imported conflicts.

## Tests and qualification

HistoryImportTest reuses all three frozen R1 golden ZIPs. Coverage includes empty
and exact-existing no-op; repeat/idempotent import; missing ancestors into an
advanced vault; old history preserving newer descendants; concurrent branch and
complete tombstone; equal-valued distinct heads and exact optional metadata;
wrong-token/missing-parent gaps and forged manifest gap claims; different exact
bootstrap (including authenticated same-root alternate wrapping); malformed,
unsupported, AEAD-invalid, wrong-keyed-ID and authenticated-invalid TOKEN input;
known target differences, short/long targets, unavailable reads, directory/link
kinds; collision changes after preparation; same/different-byte concurrent winners;
first/middle/last definite failures and lost acknowledgement by both explicit
Uncertain and unchecked exception; uncertainty across failed/cancelled retries;
source mutation after preparation; cancellation before and between objects and
late cancellation; close during publication; blocked ingress concurrent with close;
stream ownership, short/zero reads, capacity and disposal; and actual shared and
coordinated NIO publication. Preexisting VAULT, exact objects, unrelated files and
invalid unrelated canonical evidence remain byte-identical.

The focused campaign also runs existing R1 archive/adversarial/golden tests and
candidate-validation corpus checks. The final full suite covers existing normal
observation, publication, retry, merge and protocol conformance unchanged.

Final evidence and invocation accounting are recorded below.

## Deviations and remaining restoration work

No frozen R1 profile or existing public operation was removed. R0's unfinalized
scratch abstraction is not needed within R1's existing bounded-memory ceiling;
owned private memory supplies immutable replay. R0 requested an explicit choice
of plan lifetime and observation responsibility: R2 chooses a session-owned handle
and caller-arranged observation, without a new correlated scan API. These choices
are now documented in API_DESIGN.md and docs/BACKUP_ARCHIVE.md.

New-location restoration still needs detached password authentication, a narrow
provider-owned exclusive location reservation, exact archived bootstrap creation
and post-create authentication, location/completion/uncertainty accounting, and
platform lifecycle/UI/Sync qualification. Ordinary random-root creation is not
repurposed. This milestone authorizes no release, publication, tag or commit.

## Final qualification evidence and invocation counts

All commands used the active locked development environment: JDK 25 and Gradle
9.8.2. Reused the existing prebuilt independent standalone Java launcher at
`.gradle/backup-r1-evidence/tooling/bin/totipo-java-tool` (pinned Jackson Core
2.21.7); Gradle did not compile tooling. Validation logs and full/focused XML are
preserved outside build outputs under ignored `.gradle/backup-r2-evidence/`.

| Layer | Invocations | Evidence |
| --- | ---: | --- |
| Small broad baseline `gradle test` | 1 | PASS; both module test tasks up-to-date; full existing 455-test XML accounted and preserved before focused runs. |
| Focused Gradle test invocations | 3 | All PASS. First: HistoryImportTest; second: import plus existing R1 archive/adversarial/golden and candidate corpus tests; third: final import refinements including real NIO link/unrelated-evidence preservation. |
| Focused standalone tooling test runners | 0 | No tooling changes. |
| Final `gradle test build javadoc verifyPublication consumerSmoke` | 1 | PASS; 27 actionable tasks, 14 executed/13 up-to-date. Core tests executed; unchanged storage-nio test task was up-to-date. Java 17 bytecode checks passed. Javadoc completed with the repository's nonfatal documentation warnings. |
| Publication semantic acceptance | 1 | Nested final-gate verifyPublication: 10/10 artifacts accepted. |
| Standalone manifest/current-byte recheck | 1 | `publication check`: 10/10 accepted artifacts passed. |
| Isolated module-metadata consumer | 1 | Nested final-gate consumerSmoke, including its verifyPublication dependency; existing isolated forced-offline options preserved. |
| Independent POM-only consumer | 1 | `gradle -p publishing/consumer-smoke --offline -PpomOnly clean check`: PASS. |
| Separate host forced-offline full Gradle passes | 0 | No dependency acquisition, resolution policy, fixed cache or build behavior changed. POM-only/nested consumer offline checks above are not full-suite passes. |
| Full-suite accounting | 3 | Baseline PASS; one premature rejected invocation while final Gradle was still active; final PASS after Gradle completed. |
| Agent Nix / human Nix | 0 / 1 | User reported the final `nix flake check` passed; agent executed no Nix. |
| Dependency/cache checkpoints / release reproducibility | 0 / 0 | Neither applicable to this routine runtime/API milestone. |

Final accounting:

```text
92/92 portable cases: exact-set execution assertion passed
491 JUnit tests; zero failures/errors/skips; snapshot 3; profile 1
publication check: 10/10 accepted artifacts passed
```

The 36 added import test executions are included in the 491-test final accounting.
All three focused runs and the final Gradle invocation passed; no Gradle rerun or
included-input invalidation followed the stable gate. Invocation counts are not
counts of executed test tasks: up-to-date and nested dependencies are explicit above.

One accounting attempt was made too early, before `:core:test` had finished in the
parallel full Gradle invocation. It correctly rejected the still-focused XML
(`Release operation failed (details suppressed)`). That output and its XML inventory
are preserved as `premature-accounting.txt`/`premature-accounting-xml`; they are not
full-suite evidence. Accounting was rerun after the same Gradle invocation finished,
and the complete successful XML was preserved as `final-xml` before further work.
No test failure was hidden or broad test run repeated to repair accounting.

## Freeze and completed human gate

Inspected qualification.nix's source filter and flake.nix inputs. Core production
and test sources and publishing consumer sources are included. API_DESIGN.md,
docs/BACKUP_ARCHIVE.md and review/ are excluded. Nix/toolchain/cache inputs are
unchanged. Every new milestone file, including this report, is Git-known through
intent-to-add only. No cache, build output, temporary evidence or secret was added
to Git source membership. Included inputs are frozen following successful Java
qualification; only this excluded report was finalized afterward.

The user reported the final human-owned routine qualification passed:

```sh
nix flake check
```

AGENTS.md says “Agents must not run Nix” and requires that single final human gate.
No second build, dependency-cache update, host offline full run, release comparison,
commit, tag or publication is requested. A later included-input edit invalidates
that human result. The reported human pass completes milestone qualification.
Only this excluded report was updated afterward; no included input was edited by
the agent following the gate. The user confirmation is recorded as human-reported
evidence, not as an agent-executed command. No additional build or Nix run is needed.

## Exact final Git state and changed files

Branch remains `main`; HEAD remains
`fde6fbee592b4f12fdc54cd443eaa8f71041e4fb`. `git diff --cached --quiet` and
`git diff --check` pass. Leading spaces below are significant: M entries are
unstaged modifications; A entries are intent-to-add, with no staged file contents.
The following command outputs were recorded after all included inputs stabilized.

`git status --short`:

```text
 M API_DESIGN.md
 M core/src/main/java/org/totipo/VaultSession.java
 M core/src/main/java/org/totipo/backup/BackupException.java
 A core/src/main/java/org/totipo/backup/BackupImport.java
 M core/src/main/java/org/totipo/backup/BackupSummary.java
 A core/src/main/java/org/totipo/backup/ImportResult.java
 M core/src/main/java/org/totipo/format/ApplicationSession.java
 M core/src/main/java/org/totipo/format/BackupZip.java
 A core/src/main/java/org/totipo/format/HistoryImport.java
 M core/src/main/java/org/totipo/format/VaultLifecycle.java
 A core/src/test/java/org/totipo/format/HistoryImportTest.java
 M core/src/test/java/org/totipo/format/ObjectCandidateBoundaryTest.java
 M docs/BACKUP_ARCHIVE.md
 M publishing/consumer-smoke/src/main/java/ConsumerSmoke.java
 A review/BACKUP_RESTORE_R2_JAVA_ADDITIVE_IMPORT_REPORT.md
```

`git diff --name-only` (exact complete changed-file inventory):

```text
API_DESIGN.md
core/src/main/java/org/totipo/VaultSession.java
core/src/main/java/org/totipo/backup/BackupException.java
core/src/main/java/org/totipo/backup/BackupImport.java
core/src/main/java/org/totipo/backup/BackupSummary.java
core/src/main/java/org/totipo/backup/ImportResult.java
core/src/main/java/org/totipo/format/ApplicationSession.java
core/src/main/java/org/totipo/format/BackupZip.java
core/src/main/java/org/totipo/format/HistoryImport.java
core/src/main/java/org/totipo/format/VaultLifecycle.java
core/src/test/java/org/totipo/format/HistoryImportTest.java
core/src/test/java/org/totipo/format/ObjectCandidateBoundaryTest.java
docs/BACKUP_ARCHIVE.md
publishing/consumer-smoke/src/main/java/ConsumerSmoke.java
review/BACKUP_RESTORE_R2_JAVA_ADDITIVE_IMPORT_REPORT.md
```

`git diff --cached --name-only`:

```text
```

The cached-name output above is empty. All 15 changed paths are listed verbatim;
no other repository, release state or commit was modified.

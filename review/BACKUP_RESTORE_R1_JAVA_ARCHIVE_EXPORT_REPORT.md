# BACKUP_RESTORE_R1 — Java archive and export

Status: implementation, required-method API follow-up Java qualification and final
human Nix qualification complete. The second human Nix pass qualifies the follow-up.
No restore or additive-import
implementation or qualification is claimed. Changes remain uncommitted.

Started clean on `main` at `4adcb48144a60d86554acfc3d5090f13b68852c3`.
Classifications: CORE_RUNTIME, STORAGE_NIO, PUBLIC_API, DOCS_JAVADOC,
CONSUMER_BOUNDARY. The SPI extension is included under storage/public boundary
work. No dependency/build-input version, publication metadata, release tooling,
protocol or release-preparation change.

## Design inputs and frozen contract

Read AGENTS.md, API_DESIGN.md (including operation/state-snapshot semantics),
SPI_DESIGN.md, Java R0, the pinned v1/r19 specification, session/lifecycle and NIO
scan/read/publication implementations. The existing r19 pin/corpus is unchanged.
Read the supplied [Desktop R0](https://github.com/totipo-org/totipo-desktop/blob/0acbb61ca074bfa0a7aeed15758553d765375a57/review/BACKUP_RESTORE_R0_DESKTOP_DESIGN.md)
and [Android R0](https://github.com/totipo-org/totipo-android/blob/a67d09c5e308a46da3562e7de2a2df9181a77ee4/review/BACKUP_RESTORE_R0_ANDROID_DESIGN.md)
through read-only HTTPS; neither repository was modified.

The normative frozen application contract and literal examples are in
[docs/BACKUP_ARCHIVE.md](../docs/BACKUP_ARCHIVE.md). One shared classic ZIP profile:
STORED only; fixed versioned ASCII manifest; exact 87-byte `vault`; sorted unique
`objects-v1/<64 lowercase hex>` containing exact 1024-byte encrypted representations.
No directories, extras, comments, ZIP64, descriptors, compression, encryption,
unsafe names, prefix/trailing bytes, alternate namespaces or special attributes.
Local headers, central directory and EOCD must agree and cover the complete file.
The canonical writer fixes DOS timestamp to 1980-01-02 and UTF-8 flag. JDK's
1980-01-01 timestamp extras were observed during prototyping and deliberately
avoided before freezing the golden bytes.

Manifest fields, in exact order: `totipo-backup 1`, `protocol totipo-v1`,
`vault-id <SHA-256>`, `consistency observed-set`, `enumeration complete`,
`authentication checked`, `object-count <N>`, `unresolved-references <G>`.
Each line has LF termination. Decimal integers have no leading zero except 0.
N must equal archive membership; 0 <= G <= 4N. Unknown format/claims fail closed.
The manifest improves portable inventory, identity and coverage interpretation;
per-object ciphertext hashes would add no authentication over CRC/inventory and
are not included. A whole-ZIP SHA-256 identifies exact bytes for readback.

Capture requires the authenticated open session and a completed bounded scan.
Any required supported candidate unreadable, wrong-sized or invalid blocks
ordinary success. Unrelated names are excluded. Valid missing/wrong-token parent
edges are retained and explicitly counted; no ancestry is invented. Historical
objects, conflicts, tombstones and encrypted metadata are preserved verbatim.
No plaintext, password, registry data, device credentials, root export, new
assertions, TOTP recomputation, re-encryption or second encryption scheme.

Even with G=0, coverage is a finite locally observed set, not a simultaneous
snapshot, remote freshness or global completeness. ZIP CRC, VaultId and SHA-256
are not cryptographic archive authentication. Inspection treats manifest claims
as untrusted and never authenticates objects or establishes installation authority.

## API, ownership and implementation evidence

Public application API is in `org.totipo.backup`:
`BackupLimits`, `BackupException`, `BackupSummary`, `BackupCapture`,
`BackupArchives.inspect(InputStream, BackupLimits)`, and
`VaultSession.captureBackup(BackupLimits)`.

Capture holds the normal session provider gate, reads/binds VAULT, freezes a
bounded sorted name set, freshly reads/authenticates all candidates through
TokenStoreReader's existing validator, clears each temporary parsed secret, counts
non-secret graph gaps, rechecks exact VAULT, preflights ZIP size and computes the
deterministic archive digest. The detached capture owns one contiguous ciphertext
spool and no session/root/provider. Writes never traverse live storage. It can
outlive session close; close serializes with writes and wipes/releases the spool.

The opt-in `TotipoStore.scanObjects(int)` defaults to Incomplete(UNSUPPORTED),
without calling the unbounded legacy scan. NIO counts every direct child,
including junk/kinds, retains at most the bound and reports Incomplete(CAPACITY)
when another entry exists. EOF exactly at the bound remains Complete if otherwise
successful. Ordinary unbounded observation and provider publication are unchanged.
Namespace/exact-name lookups retain their existing semantics; this is not a
wall-clock bound on filesystem calls.

Concurrent publication from another process/session may or may not enter the
frozen inventory. Once inventory is frozen, late additions do not invalidate it.
A missing fresh read fails; a missing parent is a diagnostic. State emission order
never cancels capture. Session close cannot wipe an admitted capture's root before
gate release; detached ZIP writing does not hold session ownership. The owner of
an Android coordinated store must preserve root-wide serialization and avoid
session-under-bridge deadlocks. No new snapshot/lease or coordinated-local claim
is exposed; Android's stronger product capture policy needs its own admission.

JDK ZipOutputStream assembles output; ZipInputStream handles payload reading and
CRC validation. BackupZip additionally checks the restricted complete ZIP layout,
because ZipInputStream does not validate central-directory/EOCD agreement or
trailing data. This limited parser is justified by concrete ambiguity and unsafe
metadata requirements, not an attempt to implement arbitrary ZIP. No dependencies,
archive extraction, filesystem scratch or new build/release scripts were added.

Streams are borrowed at their current positions. Output finishes without caller
flush/close; caller reservation, close, readback, destination placement, cloud
completion and durability remain separate. Failed writes can leave partial output;
retry a frozen capture with a new destination. Inspection consumes through EOF,
uses bounded memory, releases staging and retains no payload. Thread interruption
is checked between records/calls; blocked I/O and gate acquisition require caller
resource/lifecycle cancellation and retirement. Typed sanitized backup failures
separate capacity, cancellation, source availability, invalid object, changed
vault, malformed and unsupported inputs. Stream IOExceptions propagate.

## Shared fixtures and adversarial evidence

[Shared fixtures](../core/src/test/resources/backup/profile-1/README.md) use only
published r19 test credentials (`fixture password`). Independently assembled ZIPs
are checked byte for byte against the writer and authenticated through normal
session/candidate APIs. Fixed digests are asserted, not regenerated by tests.

| Fixture | Bytes | Objects / gaps | SHA-256 |
| --- | ---: | --- | --- |
| empty.zip | 522 | 0 / 0 | 603eb0dd84f67678bab82c78ee684dc22592b5e4de37f24d16f5694c382fa425 |
| history.zip | 4272 | 3 / 0 | 369b77c872a32f2474d867e0653e39b66befdfc6cbd1244da486505ba8bca95b |
| missing-parent.zip | 1772 | 1 / 1 | 6991ee0b764409728a10ee6612d6eced56207dbefa58a35611ea3d6112c25e58 |

BackupArchiveTest covers empty NIO capture, exact encrypted history/metadata,
concurrent branches and tombstones, missing ancestry, detached lifetime and retry,
external publication after inventory, concurrent close/gate behavior, unavailable
and invalid evidence, disappearance, VAULT substitution, symlinks/directories,
budgets, interruption, short/zero-progress reads, broken streams and output failures
during payload/finish. No temporary filesystem artifacts are created by backup.

BackupZipAdversarialTest tests every truncation of the 4272-byte history fixture,
corruption/CRC lies, central/local/EOCD disagreements, duplicate offsets/names,
unsorted names, orphan membership, path escapes and aliases, unsupported flags,
compression/descriptors, ZIP64 versions, symlink metadata, prefix/concatenation/
trailing bytes, malformed manifest claims/integers and exact capacity boundaries.
1000 deterministic random short inputs must produce typed rejection. A deliberately
corrupted ciphertext with recomputed CRC is structurally accepted with
sourceAuthenticated=false, proving that inspection does not claim authentication.

BackupGoldenTest checks the fixed fixtures, wrong-token parent gaps and a real
4096-object encrypted historical chain (well above Android R0's 512-object Sync
batch limit), no head-only export, exact archive length, reduced and default object
capacity failures. NioSpiTest checks bounded enumeration including junk/kinds and
EOF exactly at the bound. The independent published consumer compiles the new
capture/inspection APIs and exercises bounded NIO and malformed ZIP rejection.

## Resource limits and measurement

Initial hard support ceilings: 4096 objects, 8192 observed children, 6 MiB ZIP.
Caller budgets may be lower; larger budgets are programmer errors. Exhaustion
never returns a successful truncated archive. The spool is exactly 1024N bytes,
maximum 4 MiB; ingress uses a fixed maximum 6 MiB array without geometric growth.
Metadata and non-secret parent facts are bounded; only one decrypted TOKEN is
processed at a time. No payload list, decompression or disk cleanup is needed.

Direct JDK 25 measurement on Linux, 4096-object encrypted chain, no session/KDF
included, `-Xms32m -Xmx32m`:

```text
objects=4096 zipBytes=5120525 captureMs=429.1
captureThreadAllocatedBytes=99476056 retainedCaptureBytes=4935208
writeMs=39.3 inspectMs=198.3 maxHeapBytes=33554432
```

The process also retained source ciphertext and both caller ZIP output buffers.
Thread allocation was measured using ThreadMXBean; retained heap delta used GC
before/after capture, so it is approximate. Capture includes its digest ZIP pass;
write timing includes the post-capture GC in this probe. These are illustrative
single-run costs, not performance guarantees or Android qualification. Transient
allocation is much larger than retained ciphertext because existing crypto and
JDK ZIP allocate per object. Applications must budget live-session/KDF memory,
source/output storage and concurrent operations separately. Histories exceeding
the ceiling need a separately reviewed disk-spool/larger-capacity capability;
clients must report failure and preserve history, never silently truncate it.

## Qualification and invocation accounting

Baseline `gradle test`: PASS, 413 JUnit tests, zero failures/errors/skips,
92/92 portable cases, snapshot 3 and profile 1. Full XML was copied into ignored
`.gradle/backup-r1-evidence/baseline` before focused tests; standalone TestResults
accounting passed before focused overwrites.

The prebuilt publication launcher was absent from this shell. Existing standalone
Java tooling was compiled directly with JDK 25, release 25, strict lint and only
the pinned Jackson Core 2.21.7 classpath, into ignored local evidence. No Gradle
compilation of tooling, source changes or dependency acquisition were introduced.
The first launcher invocation could not start because this environment lacks
`/usr/bin/env`; its ignored launcher shebang was corrected to the active Nix bash.
No Java tool ran in that failed launcher attempt.

Focused archive/NIO tests passed before the stable final normal gate. Four earlier
focused attempts failed test compilation while authoring fixtures/tests: wrong
TokenStatus enum spelling, nonexistent TokenMetadata factory, explicit close
lint, and unused try-resource lint. All were corrected; no production test failure
was suppressed. An initial ZIP prototype emitted timestamp extras; fixture bytes
were regenerated before freezing. Baseline evidence remained preserved throughout.

Final gate, exact test totals, counts and Git inventory are recorded below after
execution. The human subsequently reported `nix flake check` passed. No separate host forced-offline
full suite is selected: no acquisition, build behavior, resolution or fixed-cache
change. Consumer source coverage is exercised by the mandatory existing isolated
module-metadata and POM-only gates, without weakening their offline options.

## Deferred restore and additive import

Still required: credential-based staged archive authentication, exact matching-vault
binding and independent validation of every object; bounded immutable replay plans;
new-location exclusive reservation/no-replace installation and durable uncertain
outcomes; additive missing-only publication, exact-existing acknowledgements,
occupied-different refusal and per-stage partial/uncertain accounting; operation
cancel/retry/revalidation authority; physical provider and crash tests. Descriptive
summaries and CRC/digests must never authorize canonical writes.

Android still needs owner/bridge admission, SAF close/readback, selection/lifecycle
retirement, interrupted intent and registry recovery; Desktop needs destination
reservation/installation and lifecycle ownership. Cross-client/device interoperability
must use the frozen fixtures. No restore, import, registry mutation, migration,
new encryption, password recovery, protocol revision or release was implemented.

## Initial normal qualification results and freeze

PASS, one stable invocation:

```text
gradle test build javadoc verifyPublication consumerSmoke
BUILD SUCCESSFUL in 50s
27 actionable tasks: 19 executed, 8 up-to-date
```

The standalone launcher immediately accounted full XML:
`455 JUnit tests; zero failures/errors/skips; snapshot 3; profile 1`, with
`92/92 portable cases: exact-set execution assertion passed`. Full XML and
accounting are preserved under ignored `.gradle/backup-r1-evidence/final-xml` and
`final-test-accounting.txt`. Archive suites contain 41 JUnit cases (8 capture,
28 adversarial, 5 golden/capacity); the new bounded NIO case adds one to the
existing suite. Exhaustive truncations and random inputs execute within cases,
not as inflated individual JUnit totals.

PASS: `verifyPublication` accepted 10/10 artifacts. PASS: standalone
`publication check` reread 10/10 accepted staged artifacts against the job-local
manifest. PASS: `consumerSmoke` compiled and ran in its existing isolated,
forced-offline nested Gradle invocation. PASS: separate mandatory
`gradle -p publishing/consumer-smoke --offline -PpomOnly clean check`, including
runtime and compile/runtime boundary assertions. Compile classpath contains only
Totipo core/NIO; runtime adds pinned BC. Java 17 production bytecode checks passed.
Javadoc completed with the repository's missing-comment/documentation warnings;
strict Java compilation passed. No release reproducibility comparison was run,
because this is routine implementation rather than release preparation.

| Invocation category | Count and outcome |
| --- | --- |
| Broad baseline Gradle | 1, PASS (`gradle test`) |
| Compile-only Gradle iteration | 1, PASS (`:core:compileJava :storage-nio:compileJava`) |
| Focused Gradle test invocations | 7: 4 test-compilation failures described above, 3 successful archive campaigns |
| Focused standalone tooling test runners | 0; no tooling change |
| Final full Java Gradle gate | 2 total; initial PASS, required-method follow-up recorded below |
| verifyPublication | 2 total gate executions; consumerSmoke dependencies do not count as additional executions |
| Standalone publication manifest check | Initial PASS; follow-up recorded below |
| Module-metadata consumer | 2 total nested isolated offline invocations |
| POM-only consumer | Initial PASS; follow-up recorded below |
| Full-suite XML accounting | Baseline and initial final PASS; follow-up recorded below; one earlier launcher start failed before Java execution |
| Separate host forced-offline full suite | 0; no independent purpose for this change |
| Direct standalone tooling compilation | 1, PASS; existing main sources only, prebuilt before Gradle publication |
| Capacity/memory probe | 1 successful 4096-object probe with 32 MiB heap |
| Agent Nix / human Nix | 0 / 2; first human-reported PASS invalidated by required-method follow-up; second human-reported PASS qualifies current inputs |
| Commits / tags / release actions | 0 / 0 / 0 |

Within focused invocations, core archive tests executed successfully three times.
The NioSpiTest suite executed successfully three times, including the invocation
whose core test compilation failed. Up-to-date tasks and nested dependencies are
not additional broad-suite invocations. The last small default-4097-object capacity
assertion was covered by the final full gate; it did not invalidate a completed
final gate. The later required-method API change necessitated the additional
full Java gate documented below.

Inspected `qualification.nix` source filter and `flake.nix` inputs. Included Java,
tests, fixture resources and publishing consumer source are frozen. Nix helpers,
lock/cache/toolchain inputs are unchanged. `review/`, `docs/`, API_DESIGN.md and
SPI_DESIGN.md are excluded from the derivation, so finalizing this report does not
invalidate compiled artifacts. The required-method source edit invalidated the
initial Nix result. No further included-input
edits are planned. All intended new files have intent-to-add Git membership;
ignored local evidence, compiled tooling and probe sources remain outside it.

Initial completed human gate, not executed by the agent:

```sh
nix flake check
```

The user reported this command passed in the follow-up to the implementation
handoff. This is human-reported evidence; the agent did not execute Nix. Initially
only the excluded report was updated. The subsequent user-requested required-method
API change edited included source and therefore invalidated that result. HEAD and
the recorded Git file inventory remain unchanged, with no staged file contents.
A new human `nix flake check` is required after the follow-up Java qualification.

This requirement comes from AGENTS.md, which says agents must not run Nix and
requires this single human routine qualification gate. No cache-update checkpoint
is needed because dependency inputs did not change. No separate build, release
reproducibility run, commit or publication approval is requested.

## Exact changed files and final Git state

`main`, HEAD unchanged at `4adcb48144a60d86554acfc3d5090f13b68852c3`.
`git diff --cached --quiet`: PASS. `git diff --cached --name-only`: empty.
`git diff --check`: PASS. The space before M/A below is significant: tracked
modifications remain unstaged, and A entries are intent-to-add only.

`git status --short`:

```text
 M API_DESIGN.md
 M SPI_DESIGN.md
 M core/src/main/java/org/totipo/VaultSession.java
 A core/src/main/java/org/totipo/backup/BackupArchives.java
 A core/src/main/java/org/totipo/backup/BackupCapture.java
 A core/src/main/java/org/totipo/backup/BackupException.java
 A core/src/main/java/org/totipo/backup/BackupLimits.java
 A core/src/main/java/org/totipo/backup/BackupSummary.java
 M core/src/main/java/org/totipo/format/ApplicationSession.java
 A core/src/main/java/org/totipo/format/BackupSource.java
 A core/src/main/java/org/totipo/format/BackupZip.java
 M core/src/main/java/org/totipo/spi/StoreFailure.java
 M core/src/main/java/org/totipo/spi/TotipoStore.java
 A core/src/test/java/org/totipo/api/BackupArchiveTest.java
 A core/src/test/java/org/totipo/api/BackupZipAdversarialTest.java
 A core/src/test/java/org/totipo/format/BackupGoldenTest.java
 A core/src/test/resources/backup/profile-1/README.md
 A core/src/test/resources/backup/profile-1/empty.zip
 A core/src/test/resources/backup/profile-1/history.zip
 A core/src/test/resources/backup/profile-1/missing-parent.zip
 A docs/BACKUP_ARCHIVE.md
 M publishing/consumer-smoke/src/main/java/ConsumerSmoke.java
 A review/BACKUP_RESTORE_R1_JAVA_ARCHIVE_EXPORT_REPORT.md
 M storage-nio/src/main/java/org/totipo/storage/nio/NioObjectScan.java
 M storage-nio/src/main/java/org/totipo/storage/nio/NioTotipoStore.java
 M storage-nio/src/test/java/org/totipo/storage/nio/NioSpiTest.java
```

`git diff --name-only` (exact complete file inventory):

```text
API_DESIGN.md
SPI_DESIGN.md
core/src/main/java/org/totipo/VaultSession.java
core/src/main/java/org/totipo/backup/BackupArchives.java
core/src/main/java/org/totipo/backup/BackupCapture.java
core/src/main/java/org/totipo/backup/BackupException.java
core/src/main/java/org/totipo/backup/BackupLimits.java
core/src/main/java/org/totipo/backup/BackupSummary.java
core/src/main/java/org/totipo/format/ApplicationSession.java
core/src/main/java/org/totipo/format/BackupSource.java
core/src/main/java/org/totipo/format/BackupZip.java
core/src/main/java/org/totipo/spi/StoreFailure.java
core/src/main/java/org/totipo/spi/TotipoStore.java
core/src/test/java/org/totipo/api/BackupArchiveTest.java
core/src/test/java/org/totipo/api/BackupZipAdversarialTest.java
core/src/test/java/org/totipo/format/BackupGoldenTest.java
core/src/test/resources/backup/profile-1/README.md
core/src/test/resources/backup/profile-1/empty.zip
core/src/test/resources/backup/profile-1/history.zip
core/src/test/resources/backup/profile-1/missing-parent.zip
docs/BACKUP_ARCHIVE.md
publishing/consumer-smoke/src/main/java/ConsumerSmoke.java
review/BACKUP_RESTORE_R1_JAVA_ARCHIVE_EXPORT_REPORT.md
storage-nio/src/main/java/org/totipo/storage/nio/NioObjectScan.java
storage-nio/src/main/java/org/totipo/storage/nio/NioTotipoStore.java
storage-nio/src/test/java/org/totipo/storage/nio/NioSpiTest.java
```

`git diff --cached --name-only`:

```text
(empty)
```

## Required VaultSession methods follow-up

After the initial human Nix pass, the user clarified that there are no external
session consumers and no API stability promise, and requested required methods
instead of compatibility defaults. `VaultSession.captureBackup` and
`VaultSession.validateObject` are now abstract interface methods. Their existing
ApplicationSession implementations are unchanged. Unsupported-operation defaults
and compatibility wording were removed from Javadoc, API_DESIGN.md and the archive
contract. No archive bytes, cryptographic behavior or ownership rules changed.

The SPI bounded-scan default remains: it explicitly reports a provider capability
as unsupported without invoking unbounded enumeration. It is a separate provider
capability contract rather than an optional VaultSession operation.

This continues the authorized milestone on the existing uncommitted working tree;
no reset, new broad baseline or redundant focused campaign was needed. Prior
full XML remains preserved. The included VaultSession source change invalidated
the initial Java/Nix qualification, so the stable full normal Java gate was rerun
once after the edits:

```text
gradle test build javadoc verifyPublication consumerSmoke
455 JUnit tests; zero failures/errors/skips; snapshot 3; profile 1
92/92 portable cases: exact-set execution assertion passed
publication check: 10/10 accepted artifacts passed
gradle -p publishing/consumer-smoke --offline -PpomOnly clean check
```

All passed, including publication acceptance and both isolated consumers. Follow-up
logs/accounting and full XML are preserved under ignored
`.gradle/backup-r1-evidence/required-session-methods-*`. Totals are now two final
full Java invocations, two publication acceptance executions, two standalone
manifest checks, two module-metadata consumer invocations, two POM-only consumer
invocations and three successful full-suite accounting invocations. Baseline,
focused, tooling, dependency/cache and release counts are otherwise unchanged.
The second full gate was required by the user-requested public source change;
it is not a precautionary repeat of unchanged inputs.

Included inputs are frozen again. Only this excluded report was finalized after
the follow-up Java gate. The first historical human Nix pass was invalidated;
the user subsequently reported the final `nix flake check` passed. Total human
Nix runs: two; agent Nix runs: zero. No included input was edited by the agent
after this final human pass. The initial Git inventory above records the earlier
unstaged handoff; the current index state is recorded below. HEAD is unchanged
and all work remains uncommitted for review.

## Final human gate confirmation and current Git state

The user reported the second `nix flake check` passed after the required-method
follow-up. This completes current milestone qualification. The agent executed no
Nix and changed only this excluded report afterward; no requalification is needed.

At this follow-up, all milestone files were already staged by an external action.
The agent preserved that index and did not stage or unstage any contents. The
report update is therefore an unstaged change over its staged version. Unlike the
initial handoff, `git diff --cached --quiet` now exits 1 because staged contents
exist. HEAD remains `4adcb48144a60d86554acfc3d5090f13b68852c3` on `main`.
The earlier no-staged-content statements describe their historical checks, not
this final state. `git diff --check` and `git diff --cached --check` pass.

Current `git status --short`:

```text
M  API_DESIGN.md
M  SPI_DESIGN.md
M  core/src/main/java/org/totipo/VaultSession.java
A  core/src/main/java/org/totipo/backup/BackupArchives.java
A  core/src/main/java/org/totipo/backup/BackupCapture.java
A  core/src/main/java/org/totipo/backup/BackupException.java
A  core/src/main/java/org/totipo/backup/BackupLimits.java
A  core/src/main/java/org/totipo/backup/BackupSummary.java
M  core/src/main/java/org/totipo/format/ApplicationSession.java
A  core/src/main/java/org/totipo/format/BackupSource.java
A  core/src/main/java/org/totipo/format/BackupZip.java
M  core/src/main/java/org/totipo/spi/StoreFailure.java
M  core/src/main/java/org/totipo/spi/TotipoStore.java
A  core/src/test/java/org/totipo/api/BackupArchiveTest.java
A  core/src/test/java/org/totipo/api/BackupZipAdversarialTest.java
A  core/src/test/java/org/totipo/format/BackupGoldenTest.java
A  core/src/test/resources/backup/profile-1/README.md
A  core/src/test/resources/backup/profile-1/empty.zip
A  core/src/test/resources/backup/profile-1/history.zip
A  core/src/test/resources/backup/profile-1/missing-parent.zip
A  docs/BACKUP_ARCHIVE.md
M  publishing/consumer-smoke/src/main/java/ConsumerSmoke.java
AM review/BACKUP_RESTORE_R1_JAVA_ARCHIVE_EXPORT_REPORT.md
M  storage-nio/src/main/java/org/totipo/storage/nio/NioObjectScan.java
M  storage-nio/src/main/java/org/totipo/storage/nio/NioTotipoStore.java
M  storage-nio/src/test/java/org/totipo/storage/nio/NioSpiTest.java
```

Current `git diff --name-only`:

```text
review/BACKUP_RESTORE_R1_JAVA_ARCHIVE_EXPORT_REPORT.md
```

Current `git diff --cached --name-only`:

```text
API_DESIGN.md
SPI_DESIGN.md
core/src/main/java/org/totipo/VaultSession.java
core/src/main/java/org/totipo/backup/BackupArchives.java
core/src/main/java/org/totipo/backup/BackupCapture.java
core/src/main/java/org/totipo/backup/BackupException.java
core/src/main/java/org/totipo/backup/BackupLimits.java
core/src/main/java/org/totipo/backup/BackupSummary.java
core/src/main/java/org/totipo/format/ApplicationSession.java
core/src/main/java/org/totipo/format/BackupSource.java
core/src/main/java/org/totipo/format/BackupZip.java
core/src/main/java/org/totipo/spi/StoreFailure.java
core/src/main/java/org/totipo/spi/TotipoStore.java
core/src/test/java/org/totipo/api/BackupArchiveTest.java
core/src/test/java/org/totipo/api/BackupZipAdversarialTest.java
core/src/test/java/org/totipo/format/BackupGoldenTest.java
core/src/test/resources/backup/profile-1/README.md
core/src/test/resources/backup/profile-1/empty.zip
core/src/test/resources/backup/profile-1/history.zip
core/src/test/resources/backup/profile-1/missing-parent.zip
docs/BACKUP_ARCHIVE.md
publishing/consumer-smoke/src/main/java/ConsumerSmoke.java
review/BACKUP_RESTORE_R1_JAVA_ARCHIVE_EXPORT_REPORT.md
storage-nio/src/main/java/org/totipo/storage/nio/NioObjectScan.java
storage-nio/src/main/java/org/totipo/storage/nio/NioTotipoStore.java
storage-nio/src/test/java/org/totipo/storage/nio/NioSpiTest.java
```

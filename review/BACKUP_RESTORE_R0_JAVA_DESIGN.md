# BACKUP_RESTORE_R0_JAVA_DESIGN

## Status, scope and recommendation

Research/design only, 2026-10-26. Started from clean `main` at
`8f3adc0b19751cd3e9f6094644708a3f6869ff7b`; initial `git status --short` was empty.
Classification: `DOCS_JAVADOC` (excluded Markdown report only). The proposed
runtime, public API and provider changes below are future work, not changes made
by this milestone. No dependency, protocol, Java, build or release change is made.

**Recommend a shared portable backup service in `totipo-core`, with separate
capture, archive inspection, validation and application steps.** Use streams for
archive ingress/egress and bounded provider-backed scratch storage for replay.
Keep filesystem reservation, no-replace installation and durability in
`totipo-storage-nio` or another provider. Do not require Android SAF documents to
be `Path`s. Do not introduce a third published module or new dependencies now.

The three operations have different authority:

| Operation | Meaning and authority |
| --- | --- |
| Export evidence | Freeze exact locally read encrypted representations and describe capture limitations. It cannot prove complete or freshest history. |
| Restore at a new location | Reproduce the archive's exact immutable VAULT and object bytes in a newly reserved destination. This is another location for the **same vault**, with the same password and root. |
| Additive import | Validate against an already authenticated matching destination; publish missing exact objects, acknowledge exact existing objects, and leave every other entry untouched. Never create or replace VAULT. |

Restore is distinct from `Totipo.create`, which generates a new independent root,
salt and nonce. Credential/root migration is also distinct: r19 §8 requires fresh
independent keys for that workflow. This proposal does no rewrap, re-encryption,
semantic copying, conflict resolution, tombstone resurrection or garbage collection.
Archive format versioning belongs to this application feature, not to VAULT/TOKEN.

## Evidence and existing boundaries

Authority is the vendored [v1/r19 specification](../core/src/test/resources/totipo-spec/v1-pre-rc/spec/totipo-vault-format-v1.md),
not a moving upstream branch. [SPEC_PIN.md](../SPEC_PIN.md) identifies commit
`cdb4e91be1c6d3704874b2b92457ffe7be5e9084`, specification SHA-256
`8bb76b890086eb4bf89271edd5861e02f833f3ffa11a83b5865080ed4e4228cb`,
92 cases and 99 imported files. Read the full specification, AGENTS.md,
[API_DESIGN.md](../API_DESIGN.md), including operation/state-snapshot guidance,
and [SPI_DESIGN.md](../SPI_DESIGN.md).

Implementation review covered every production class in
`storage-nio/src/main/java/org/totipo/storage/nio`, the public Totipo/VaultSession
and TotipoStore boundaries, VaultLifecycle, TokenStoreReader and ApplicationSession
observation, candidate validation, locks and closure. Relevant existing provider
tests were inspected for create races, exact names, bounded reads, private moves,
publication fault points and durability. These tests were not run in this milestone.

| Existing fact | Design consequence |
| --- | --- |
| r19 §§3, 6–9: exact lowercase `vault`, immutable 87-byte VAULT; `VaultId = SHA-256(exact VAULT)` | Carry exact bytes. Recognition is structural, not authentication. Compare exact bytes as well as identity before adding to an existing store. |
| r19 §§10–13: keyed object IDs commit to complete canonical plaintext; deterministic 1024-byte encrypted envelopes | Preserve ciphertext verbatim, including parents and metadata. The name is not an ordinary SHA-256 of ciphertext. |
| r19 §§13–16: validate independently; incomplete observations and missing/wrong-token parents do not invalidate complete children | A portable backup need not be dependency-closed. Preserve known gaps without inventing ancestry or omitting conflicts. |
| TotipoStore scan/read contract | `Complete` means enumeration ended, not an atomic snapshot. Fresh bounded reads, not scan metadata, determine captured bytes. |
| VaultState/TokenHead projections | No ciphertext inventory; descriptive retained history is not a raw backup source. A state reference cannot freeze storage. |
| `validateObject` | Reuse its internal validation path. Public `Valid` records are constructible descriptive values, not import authorization capabilities; no parent information is exposed there. |
| Totipo.open/create ownership | Store ownership transfers on every outcome. Applications cannot borrow a session's provider or open a second handle and assume it joins the session gate. |
| Ordinary NIO | Shared honest writers use exclusive hard links. Link failure never selects a weaker fallback. |
| Coordinated NIO composition | Move installation requires one external whole-root coordination domain, including bridges and all sessions/handles. Core's gate coordinates one session only. |
| NIO exact-existing acknowledgement | Byte equality alone is insufficient for its durability acknowledgement; it forces file/object directory/root and reconfirms bytes. Preserve this stronger Java contract despite r19's lower minimum. |

External references checked for container and platform boundaries:
[PKWARE ZIP specification](https://pkware.cachefly.net/webdocs/casestudies/APPNOTE.TXT),
[Java 17 ZipInputStream API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/zip/ZipInputStream.html),
and [Android SAF document access](https://developer.android.com/training/data-storage/shared/documents-files).
ZIP supports stored entries and application manifests. Java supplies stream ZIP
reading. SAF supplies URI-based document access, including provider/cloud documents.
These sources inform the proposed narrow profile and stream boundary below; they
do not qualify a future implementation or establish provider crash durability.

## Capture and consistent-snapshot claims

Define a **capture** as an immutable owned inventory of names and exact bytes read
during one bounded pass, bound to exact VAULT bytes. Once captured, neither archive
writing nor retries reread the live source. This supplies a consistent replayable
archive byte set, not a claim that the entire set existed at one instant.

Proposed capture sequence:

1. Read exact canonical VAULT using the existing 87-byte bounded read. Require
   structure; for session capture, also require exact binding to the session's
   authenticated VAULT identity. Pin these bytes privately for the operation.
2. Enumerate only direct `objects-v1` candidates, with a finite enumeration budget.
   Freshly read each canonical lowercase-hex name with a 1024-byte bound plus
   lookahead. Ignore unrelated names; never recurse or follow observed links.
   Retain exact-sized ciphertext in owned scratch, with ciphertext SHA-256.
3. For authenticated capture, validate each retained candidate using the common
   protocol path, record validation diagnostics and compute known dependency
   gaps internally. Clear temporary decrypted values promptly. No need to intern
   every value in the session merely to back it up.
4. Reread VAULT and require exact equality with the pinned bytes. Disappearance,
   malformed bytes, identity substitution or inability to recheck prevents a
   successful bound capture. Freeze captured inventory, coverage and diagnostics.
5. Write only the frozen capture to the caller's output. No live-directory traversal
   while emitting ZIP. Source mutations after capture do not alter the capture.

In an ordinary shared directory, concurrent writers may publish before, during or
after enumeration. An object read early may disappear before a later object is
read; new children may be captured without parents published earlier but not seen.
Repeated equal scans and two equal VAULT reads do not prove an atomic snapshot.
Concurrent additions do not invalidate already captured immutable objects.
Capture never blocks other processes or waits for remote synchronization.

The default guarantee is `observed-set`, even for a complete diagnostic-free scan.
Offer stronger `coordinated-local` capture only when the provider/owner can hold a
whole-root reservation for the **entire** VAULT/scan/read/recheck pass and exclude
all relevant mutations. Per-call coordination is insufficient. A genuine provider
snapshot is a possible future capability, not a mandatory SPI or protocol addition.
Neither stronger local mode proves remote completeness or freshness.

Report independently: enumeration ended versus incomplete; capture limitations;
invalid/unavailable candidates; and unresolved references relative to the captured
validated set. `Finished` and an empty diagnostic list are not completeness proofs.
Do not bind capture validity to VaultState emission order or cancel merely because
a newer state arrives. Application presentation relevance remains separate.

### Evidence policy

Recommend an evidence-preserving archive profile: carry every successfully read
exact-sized canonical object candidate, including candidates that have not been
authenticated or fail validation. Such bytes are **candidate evidence**, not valid
TOKENs. A locked read-only export can therefore preserve data without prompting
for a password or deriving roots. Authenticated capture adds useful diagnostics.

Malformed-size/unreadable entries, unsafe kinds and scan failures are counted as
capture limitations; they cannot fit the normal object-entry contract and are not
silently described as backed up. Bounded diagnostics belong to the result/report;
the archive carries aggregate coverage counters. This is a portable vault-evidence
backup, not a forensic filesystem image. Unknown sibling namespaces, conflict copies,
wrong-case names, temp files and arbitrary damaged files are excluded. A forensic
rescue format or future object-family support needs a separate explicit design.

Default restore/import rejects an archive containing an invalid candidate before
canonical writes. Preserving invalid evidence does not grant permission to install
it. A future explicitly selected salvage subset could import validated candidates
and report exclusions, but silent salvage is out of scope for the initial writer.
An evidence export with limitations must be visibly distinguished from a fully
captured local pass; locked export says authentication/dependency analysis was not
performed. Missing parents alone do not make an authenticated object invalid.

## Proposed archive contract: application profile 1

This is a reviewable candidate contract, not a newly frozen interoperable format.
Freeze exact grammar and golden fixtures before implementation; do not modify the
pinned vault specification to adopt it.

One ZIP, no outer folder, with exactly these entries in order:

| Entry | Contents |
| --- | --- |
| `totipo-backup.txt` | Versioned bounded ASCII manifest, first entry |
| `vault` | Exact 87-byte canonical VAULT |
| `objects-v1/<64 lowercase hex>` | Exact 1024-byte candidate representations, strictly sorted by ID |

Zero objects is valid. No directory records, extra files, alternate-case aliases,
duplicate entries (even identical), nested archives, optional UI files, credentials,
plaintext token exports or recovery keys. Unknown archive versions/namespaces fail
closed for this profile; do not recursively interpret them. Ordinary vault readers
continue their existing unknown-sibling behavior: this stricter rule is for the
archive profile only.

Proposed manifest grammar: ASCII, LF only, one space between fields, final LF,
fixed field order, no BOM, comments, blank lines, escapes or extension fields.
Unsigned decimal counters use `0` or a nonzero first digit; all hex is lowercase.
Reject duplicates and overflow before allocation. Example (placeholders are not
literal grammar):

```text
totipo-backup 1
protocol totipo-v1
vault-id <64 hex SHA-256 of exact VAULT>
consistency observed-set
enumeration complete
authentication unchecked
unavailable-count 0
invalid-count 0
omitted-count 0
object-count <N>
object <ID> <64 hex SHA-256 of exact ciphertext>
... exactly N sorted object lines ...
```

`consistency` is `observed-set` or `coordinated-local`; `enumeration` is `complete`
or `incomplete`; `authentication` is `unchecked` or `checked`. Checked means every
included candidate was tested, not that all passed. With unchecked capture,
`invalid-count` is zero **known** invalids, not a validity claim. Unavailable counts
refer to candidate read failures; invalid counts to included rejected candidates;
omitted counts to observed canonical candidates excluded by size/kind/policy.
Enumeration errors can mean unknown additional omissions; counters are not an
exhaustive inventory. Resource-budget exhaustion aborts capture by default rather
than silently selecting an arbitrary prefix. All these claims are untrusted
producer descriptions, never input to import authorization.

Manifest object IDs must equal the object-entry set exactly. Verify hashes over
actual read bytes, not size metadata. `vault-id` also binds the exact VAULT bytes.
Ciphertext hashes are transport/inventory checks only: object identity remains
keyed HMAC over canonical plaintext. ZIP CRC and manifest hashes provide no
authenticity for the archive set, omissions, capture claims or origin. An attacker
can rebuild them around a replayed valid subset. No archive MAC, signature, new
password, KDF or protocol object is introduced. A trusted out-of-band archive
digest is an optional application feature, not freshness proof.

### Narrow ZIP profile and portability

Recommend single-disk classic ZIP, **STORED only**, no ZIP encryption, descriptors,
ZIP64, extra fields, comments, executable prefix, appended archives or trailing
bytes. Only flags zero or UTF-8-name flag are allowed; names are exact ASCII in
either case. Local sizes/CRC are known in advance from capture. Local headers,
central-directory entries and EOCD must describe exactly the same ordered entries,
offsets, sizes, CRCs, flags and methods. Forbid overlapping data ranges, orphan
local records, duplicate central references and special-file/link attributes.
Ignore permissions/times as destination instructions; the profile accepts only
zero or explicitly regular-file attributes. Fix writer timestamps to a representable
DOS epoch and avoid generated timestamp extras. Reader timestamps carry no meaning.

Encrypted fixed-size objects offer little compression benefit. Rejecting compressed
entries removes decompression from the initial untrusted-input surface and permits
exact entry budgets. This deliberately accepts a portable application ZIP subset,
not every ZIP produced by arbitrary archivers. Repacking with a generic ZIP tool
may make a backup unsupported. DEFLATE and large archives can be separately reviewed
later; do not quietly widen the parser.

Use JDK ZIP writing where it satisfies the exact profile and Java 17/Android runtime
requirements. Reading entry payloads through ZipInputStream alone is not sufficient
evidence of full central-directory/EOCD agreement. Prototype a small bounded
profile verifier before deciding whether to pair it with JDK ZIP reading or use
one restricted reader. Do not write a general ZIP engine or add an archive library
without documenting a concrete gap and reviewing its dependency/security cost.

## API and module boundaries

New shared APIs are needed. Existing public APIs cannot export raw store bytes,
install validated external candidates under session coordination, or restore the
original bootstrap without generating a new vault. Rebuilding these functions
independently in desktop/Android risks inconsistent identity and uncertainty rules.

| Owner | Proposed responsibility |
| --- | --- |
| `totipo-core`, public application package (tentatively `org.totipo.backup`) | Capture/inspection results, archive limits/version, stream codec, identity checks, authenticated validation, import orchestration and partial-result accounting |
| `totipo-core` internals | Reuse bootstrap/object validation, scoped root ownership, bounded graph diagnostics and same-ID contradiction handling; no exported keys or plaintext archive API |
| `org.totipo.spi` | Continue opaque exact bounded reads and create-only publication. Add only demonstrated capabilities: bounded enumeration and new-location reservation/completion contracts. No ZIP/TOKEN parsing in providers. |
| `totipo-storage-nio` | Path convenience facade, private scratch implementation, exclusive new directory allocation, existing hard-link publication/durability, provider-specific recovery |
| Android application/provider | SAF stream opening, permissions and cancellation; app-private scratch; provider/root coordination; location selection and truthful output-completion UI |

Do not place the shared codec/orchestration in storage-nio, expose `format.*` as
normal application APIs, or publish a separate archive module before isolation
benefits justify its build/publication surface. Java 17 bytecode remains required.
Jackson is not a core runtime dependency; a small fixed manifest grammar avoids
adding JSON just for this feature.

Conceptual operations, not approved Java signatures:

```text
captureEvidence(ownedReadOnlySource, limits, scratch) -> FrozenCapture
session.captureBackup(limits, scratch) -> FrozenCapture
writeArchive(capture, borrowedOutputStream) -> WriteResult
inspectArchive(borrowedInputStream, limits, scratch) -> InspectedArchive
validateArchive(inspected, credentials OR matching live session) -> ValidatedArchive
restoreNew(validated, ownedReservedDestination) -> RestoreResult
session.importBackup(inspectedOrValidated, limits) -> ImportResult
```

Use a type distinction between structurally inspected ciphertext and authenticated
evidence. Validation handles must be library-created, scoped to vault identity and
immutable owned bytes, and cannot be forged with a public result constructor.
For session import, recheck session binding and validate through that session's
authenticated root; no caller-provided `Valid` record bypass. An independent
credential validation context is short-lived, not a publicly exported root owner.
Exact method placement, external VaultSession default-method behavior and handle
consumption must be decided with API tests before signatures are committed.

Streams are borrowed, consumed from their current position, and not closed by core.
The writer must finish ZIP and surface errors without closing the caller's stream;
the caller must also check its own close/provider completion result. Library-owned
scratch and internal streams are closed. Clearly document handle lifetimes:
FrozenCapture/InspectedArchive own immutable ciphertext scratch until close and
can survive source-session closure; secret-backed admission/validation cannot.
Closing a validated plan disposes replay resources and any temporary secrets.
Never retain password arrays; encode strictly and wipe temporary copies.

Scratch should expose bounded append/seal and sequential replay, not Path or SAF
URI. A private sequential record spool is enough; callers need not supply seekable
input, reopening, mark/reset, or an entire in-memory archive. Replay after inspection
must read the sealed owned bytes, not reopen a mutable user-selected document.
NIO may use private files; Android may use its private cache/files directory.
Untrusted archive names never become scratch filenames. Limit memory as well as
disk; do not implement the default as `readAllBytes()` or a list of all payloads.

### Concrete SPI gaps

`scanObjects()` currently materializes all entries and NIO enumeration has no
limit parameter. Limiting the returned list in backup code is too late to bound
hostile local directories. Before shipping capture, design a bounded scan/cursor
capability with entry/name-byte budgets, positive observations and explicit
incomplete/resource-limit termination. A compatibility fallback to the old
unbounded scan cannot claim bounded capture. Keep ordinary observation semantics
unchanged until separately reviewed.

TotipoStore is rooted at an already existing directory and cannot reserve a new
location or prove root ownership/exclusivity. A separate provider-owned reservation
handle should allocate a fresh location, supply its store, and report completion
certainty. Do not add generic delete/replace/move authority to TotipoStore. NIO
can allocate an exclusive child of a selected parent; a SAF tree adapter must
qualify exact-name/no-replace behavior or return Unsupported. Selecting an existing
directory and checking it is empty is not a race-safe reservation. The application
may choose a parent; the actual fresh location returned can differ from a requested
display name, and must be shown truthfully.

## Validation, dependencies and collisions

Inspection first enforces the complete container contract, exact inventory, limits,
CRC and SHA-256 checks, and structural VAULT validation. **No destination canonical
writes occur during inspection.** Then authenticate VAULT before using its root,
and validate every candidate with existing envelope, keyed-ID, zero-padding,
canonical TOKEN grammar and field bounds. Structural VaultId alone is insufficient.
Authentication failure does not prove the password is wrong. Run at most one
bounded unlock per validation attempt; reject malformed structure before Argon2.

Existing-vault import also freshly reads canonical destination VAULT and compares
its exact bytes to the archive and to the session binding before publishing. A
matching ID is a useful early check, never the sole authorization. Different VAULT,
absent VAULT, wrong kind, unreadable VAULT or changed binding produces no import.
Do not accept an alternate bootstrap with the same recovered root. Restoring into
a vault with a different password/root is migration, not encrypted additive import.

Compute dependency diagnostics over **all** authenticated captured objects, not
only current heads. Parent lookup must respect TOKEN_ID; a valid object for another
token does not resolve the edge. Missing or invalid parents remain unresolved.
No parent download, closure chase, synthetic root, parent rewriting, topological
publication prerequisite or application-wide block follows. Cycles use existing
bounded SCC semantics; preserve equal heads, complete tombstones, metadata and
all branches verbatim. Report archive-only gaps separately from gaps after a
destination observation: destination objects may resolve archive gaps and vice versa.
Resource-limited graph analysis reports incomplete analysis, never zero gaps.

Preflight target checks can identify a conflict before any writes, but are not
reservations. Each actual publication must still enforce no replacement:

| Target observation/publication | Required behavior |
| --- | --- |
| Absent regular-object target | Publish complete frozen bytes through provider create-only publication. |
| Exact regular bytes | Idempotent acknowledgement through existing provider contract; no rewrite, touch, chmod or rename. Preserve NIO acknowledgement barriers. |
| Different bytes, including invalid local ciphertext | Leave untouched, report target conflict; never repair it automatically. |
| Wrong kind, alias collision, unreadable target | Leave untouched, report unsafe/unavailable rather than infer absence or equality. |
| Two independently validated representations with one keyed ID but different ciphertext | Integrity contradiction; do not choose a winner or continue ordinary evaluation for that identity. |

A byte conflict alone is not proof of a cryptographic collision. Validate local
bytes independently before claiming the last row. Exact duplicates in a ZIP are
container errors even when content matches; idempotence applies across operations
against a destination, not to ambiguous entries inside one archive. Defensive
same-ID tests should inject abstract validation outcomes, not claim to generate
an HMAC collision. A contradiction discovered against already retained session
evidence must use existing exclusion behavior, not silently clear it on import.

## Application, concurrency and failure model

### Additive import

After complete prevalidation, apply the immutable plan in stable ID order using
the session's provider/lifecycle gate. Do not bypass it with a separately opened
store or hold the local secret lock over I/O. A gate covering the apply batch
simplifies identity recheck and close accounting but can delay observation/saves;
bound batch size/work and assess chunked admission before freezing the API.
Never hold that gate while reading an arbitrary SAF archive stream or delivering
application callbacks. External whole-root composition retains its own lock domain
and lock-order requirements; avoid introducing session/root lock inversion.

Independent honest writers may add objects during apply. Exclusive no-replace
publication resolves same-ID races: same bytes acknowledge, different bytes fail.
New unrelated heads do not trigger the semantic merge gate: import authors no
new assertion and is not a resolution of all current alternatives. A newer
VaultState alone never invalidates the plan. VAULT substitution and closure remain
real admission/binding failures; per-session locking cannot prevent an external
actor changing VAULT between checks. Recheck binding at completion and stop on
detected change; claim only the existing trusted-local namespace model, not an
adversarial cross-file transaction.

After successful or partial publication, request/recommend a fresh observation.
Import completion proves storage acknowledgement, not that a state callback has
already incorporated it. Prefer a directly correlated completed local observation
if the new API promises one. Otherwise retain the documented distinction and,
for externally coordinated bridges, API_DESIGN's conditional two-Finished-pass
barrier. `requestRefresh()` returning is never such a barrier. No remote sync
acknowledgement or globally newest-state claim is added.

### Restore at a new location

1. Inspect and authenticate the whole frozen archive before allocating a destination.
2. Obtain a fresh exclusively owned location reservation. Refuse any preexisting
   destination; no implicit conversion to additive import, even if VAULT is equal.
3. Create the exact archived VAULT using create-only publication, required
   durability and fresh exact canonical reread plus wrap authentication/root check.
   Do not call ordinary random-root creation. This is explicit recovery, not a
   bypass of ordinary creation's orphan-data veto for arbitrary existing roots.
4. Publish validated object bytes through the same immutable-object path. VAULT
   first makes a crashed partial restore recognizable as the intended vault;
   objects-first would leave orphans and complicate ordinary creation safeguards.
5. Recheck identity, account for every planned object and provider completion,
   and return the actual location and capture limitations. Open normally for a
   fresh state; no claim that restored data is globally current.

Whole-directory atomic visibility is not assumed. Until complete, another reader
could see the valid bootstrap and a subset of objects. The reservation belongs
outside ordinary application configuration until completion, but external discovery
may still occur. A provider-specific hidden staging tree plus no-replace final
installation is an optional stronger strategy requiring its own qualification,
not a portable promise about directory rename or SAF.

Any exact existing VAULT blocks a **new** restore attempt; it is not automatically
an idempotent create success. After uncertain/partial restore, reobserve the location
and explicitly resume through matching-vault additive import. An uncertain bootstrap
install also needs provider-specific durability recovery acknowledgement before
claiming the overall restore durable: current createVault cannot reconfirm an
existing bootstrap's durability. Do not rewrite it to obtain acknowledgement.
This recovery gap must be resolved or exposed before restore shipping.

### Results, cancellation and crash recovery

Use archive-specific results, not SaveResult or the token publication retry handle.
They must include plan identity/inventory, destination binding, phase, capture
limitations, per-object outcomes (bounded/paged), counts and continuation guidance.
Separate archive rejection/authentication failure from application persistence.

| Aggregate outcome | Meaning |
| --- | --- |
| Rejected / definite pre-apply failure | No canonical mutation by this operation; scratch/allocation residue may exist and is reported separately. |
| Applied | Every planned object and, for restore, bootstrap/location completion has required acknowledgement. Does not mean dependency-complete or fully observed. |
| Partial | Some acknowledged writes, some definite failures/not attempted; prior writes remain. Never label the whole operation no-effect Failed. |
| Uncertain (possibly also partial) | At least one relevant mutation may have occurred without acknowledgement; retain this knowledge until exact recovery acknowledges the whole relevant plan. |

Stop at the first target conflict, storage failure or uncertainty by default; mark
remaining entries not attempted. Continuing useful work is a possible explicit
policy later. Do not roll back by deleting published canonical entries: another
writer may have observed/relied on them. Never delete preexisting data. Cleanup
removes only owned noncanonical scratch/stages; failed cleanup is reported without
turning a failed or partial apply into success. Leave a partial reserved location
available for explicit recovery rather than recursively deleting a possibly used vault.

Retries replay sealed exact bytes and identities, never regenerate semantic values,
rebase parents or refresh metadata. Re-running import converges under stable storage
and no conflicts; operation status is not guaranteed to be identical across runs.
Record uncertainty at the whole-plan level: a later definite per-object failure
cannot erase earlier possible persistence. A crash loses in-memory results, so
recover by observing canonical bytes and exact retry, not an invented wire journal.
Retain/reselect the original archive if scratch was lost; fully reinspect it.

Cancellation before apply can establish no canonical writes. Once publication is
entered, cancellation/timeout must wait for or report the provider's certainty;
interrupting a thread does not establish no effect. Session close rejects new work
and waits for admitted mutations before root wiping. Core cannot promise prompt
cancellation of arbitrary blocking InputStreams; applications own stream closure
and transport deadlines. No callbacks under provider/root/local locks, and no UI
thread execution for capture, inspection, KDF, validation, apply or close.

## Bounded malicious-ZIP and confidentiality requirements

Suggested initial hard ceilings, to be measured and reviewed before freeze:

| Resource | Proposed profile ceiling |
| --- | --- |
| Object entries | 50,000 (50,002 total ZIP entries) |
| Manifest bytes | 8 MiB; bounded lines, object-line maximum 136 bytes including LF |
| Entry names | Exact whitelist, longest 75 ASCII bytes |
| VAULT / object payload | Exactly 87 / 1024 bytes; bounded read plus one-byte lookahead |
| Entire encoded archive | 80 MiB, counting all headers, central directory and terminator |
| Total payload bytes | 64 MiB, checked independently of entry counts |
| Scratch | 160 MiB per operation including simultaneous spool/copies; enforce before writes |
| Diagnostic detail | 1,000 records plus exact/saturated-overflow-safe totals and truncation indicator |
| Dependency edges | At most four per validated object; bounded iterative traversal |

These are application-profile limits, not a new bound on a Totipo vault. Callers
may lower limits. Higher-capacity profiles need explicit compatibility/security
review; do not silently split or omit objects to fit. The 50,000-object limit stays
below classic ZIP entry-count limits; resource arithmetic uses checked wide
integers and budgets both metadata and payload. Bounded enumeration must also
cap observed junk entries/name bytes so an otherwise small vault cannot exhaust
capture memory. Exact scan and memory ceilings remain an implementation decision
requiring measured object/graph overhead on Android.

Additional mandatory rules:

- Validate header lengths/flags/methods and remaining budgets before allocating or
  reading fields. Reject compressed/encrypted/unsupported entries immediately;
  never drain a rejected huge entry with `closeEntry()` or auto-skip logic.
- Do not trust declared sizes, CRC, manifest counters, central offsets or EOCD
  counts. Verify actual bytes, relationships, EOF and overflow at every layer.
  Validate the whole central directory/terminator before destination writes;
  local-entry EOF alone cannot establish a complete archive.
- Reject absolute paths, traversal, backslashes, drive/UNC names, empty components,
  NUL, Unicode lookalikes, percent-encoded substitutes and alternate case by exact
  whitelist matching, not by normalizing then checking a destination prefix.
  Never extract entries using names as filesystem paths or SAF display names.
- Reject link/special-file attributes; never restore permissions, ownership,
  timestamps, executable bits or references to files outside the destination.
- One byte beyond a budget is enough to reject; do not continue consuming unlimited
  input to find a terminator. Transport stall timeouts require platform assistance.
  Limit concurrent operations and KDF attempts as well as per-operation work.
- Treat scratch as sensitive even though payload is encrypted. Use private storage,
  quota, best-effort cleanup, and app-owned generated names; recheck sealed record
  digests on replay. A mutable public scratch provider cannot underwrite validated
  bytes. No secure-erasure claim on JVM/filesystem/cloud storage.
- Do not log passwords, roots, decrypted fields, arbitrary entry text or raw provider
  exception messages. Diagnostics use sanitized codes/IDs with bounded rendering.

The archive exposes stable VaultId, object IDs, object count and capture limitations.
It also contains the offline password verifier and retains historical/tombstoned
secrets in encrypted form. ZIP itself adds no encryption; users still need the
original password. Copying a backup expands retention/exposure, does not revoke old
copies, and provides no rollback protection. Imported valid older evidence can
change displayed heads or reveal conflicts; it is never automatically a winner.

## Unresolved issues and decisions required before implementation

1. **Freeze the archive subset:** exact ZIP header/attribute acceptance, manifest
   grammar/coverage counters, writer timestamps and extension policy need shared
   golden fixtures. Decide whether evidence-preserving export plus strict import
   is the initial UX, or whether a distinct validated-only export is also needed.
   Never silently discard failed candidates while claiming all evidence.
2. **Bounded enumeration:** settle cursor/callback versus bounded scan, cancellation,
   name-byte limits, and compatibility for external providers. Existing SPI cannot
   establish the required bound by post-filtering.
3. **New-location reservation:** settle portable handle/result ownership and the
   bootstrap durability recovery gap. Reject unsupported providers honestly rather
   than promise desktop hard-link/rename semantics for SAF.
4. **Session integration:** decide batch versus chunked apply, root coordination
   lock order, close semantics, correlated observation results and API compatibility
   defaults. No new generic VaultState generation/validity token is necessary.
5. **Scale:** measure memory, scratch, graph analysis and KDF scheduling on the
   intended Android baseline. Decide explicit larger profiles only after evidence.
6. **Inspection implementation:** verify central-directory/EOCD validation on Java
   17 and target Android runtimes. A parser prototype/security review is needed;
   JDK convenience APIs alone must not be assumed to enforce the proposed subset.
7. **Damage recovery:** decide later whether explicit salvage of a selected valid
   subset and export of noncanonical forensic bytes deserve separate APIs/formats.
   No automatic invalid-target repair or bootstrap recovery into arbitrary orphan
   stores is authorized by this design.
8. **Interoperability and deployment:** obtain independent Android/desktop fixtures
   and physical provider fault evidence before broader platform claims. URI grants,
   SAF output-close failures, app restart and low-storage cleanup require application
   qualification; current Linux provider tests are not that evidence.

## Test design and acceptance evidence for future phases

| Area | Required meaningful cases |
| --- | --- |
| Byte fidelity | Empty vault; many objects; complete available history, metadata absent/present-empty/zero/u64 max, tombstones and conflicts; identical VAULT/IDs/ciphertexts after export/restore; same password unlocks; no new root/nonce generation. |
| Capture | Concurrent add before/during/after scan; disappearance between scan/read; wrong size/kind; incomplete enumeration; VAULT substitution/recheck failure; frozen spool remains stable after source change/session close; whole-pass coordination versus per-call locking. |
| Scope and gaps | Missing intermediate and wrong-token parent; child before parent; importing parent resolves a gap; lost object doesn't invent ancestry; all historical revisions analyzed; cycle-safe abstract graphs and resource-limited diagnostics. |
| Authentication | Wrong credentials, tampered VAULT, wrong-root ciphertext, AEAD failure, bad keyed ID, padding/length/TOKEN grammar errors; untrusted manifest claims and forged public Valid values cannot authorize writes; invalid archive causes zero canonical mutation. |
| ZIP parser | Duplicate identical/different records; local/central disagreements; missing EOCD; truncation at every boundary; fake counts/offsets; overflow/overlap; extra/trailing/prefixed data; directory/link/special attrs; malformed names/UTF-8; encrypted, DEFLATE, ZIP64 and descriptor rejection. |
| Budgets | Exactly at and one beyond each count/name/header/manifest/input/payload/scratch/diagnostic bound; junk directory flood; huge declared length fails before allocation; endless input bounded; blocked input cancellation delegated truthfully. |
| Identity | Same vault at two locations accepted for import; different vault rejected before writes; same root with alternate bootstrap rejected; canonical VAULT missing/unreadable/wrong kind; session opened before substitution; exact bytes compared beyond manifest ID. |
| Target collisions | Exact bytes idempotent with no data/mtime/permission rewrite; different invalid target preserved; aliases, links, directories, unavailable reads; two writer races same/different bytes; abstract two-valid-same-ID contradiction excluded. |
| Restore reservation | Existing destination always refused; concurrent reservation winner; fresh-location actual-name reporting; partial VAULT/object effects; no ordinary-create orphan bypass; no destructive rollback; explicit recovery/resume and bootstrap durability uncertainty. |
| Partial failure | Fault before/after every stage/install/force/directory barrier, lost acknowledgement, failure at object N, bounded result accounting, never-attempted entries, monotonic uncertainty and exact retry after reopening. |
| Lifecycle/threading | Close during inspect/validation/apply; cancellation before/after mutation; scratch lifetime; no provider reentry/callback deadlock; local TOTP still follows existing lock rules; new state arrival doesn't invalidate captured evidence. |
| Stream/platform | Nonseekable short/zero-progress/error streams; output finish/close failure; caller streams remain open; private scratch quota/cleanup failure; desktop and Android stream adapters produce equivalent archives; SAF-specific physical tests separately. |

Use pinned conformance cases and existing provider fault seams; do not modify r19
fixtures to fit the archive design. Parser tests must assert allocations/consumption
and **no canonical writes on rejected input**, not just expected exception text.
Round trips alone cannot establish malicious-input safety. Add fuzz/property tests
for header lengths, manifest parsing and offset accounting with deterministic
resource ceilings; dependency choice remains a later review. Real collision/cycle
cryptographic fixtures are unnecessary; test defensive branches at an honest
abstract seam. Fault injection proves result handling, not universal power-loss safety.

## Phased implementation plan

1. **R0 — this report:** agree semantics, evidence policy, stream ownership,
   nontransactional outcomes and unresolved decisions. No executable change.
2. **R1 — archive/capture substrate:** freeze subset and golden fixtures, prototype
   strict bounded inspector/writer and sealed scratch, add bounded provider discovery.
   Ship inspection/export first if independently useful. No destination mutation API
   until malicious-input and byte-fidelity tests pass.
3. **R2 — additive import:** integrate identity/authentication with the session gate,
   reuse immutable publication, implement per-object/aggregate certainty and exact
   replay, preflight/no-overwrite checks and observation reporting. Qualify concurrent
   writers, close and failure accounting before exposing ordinary application entry points.
4. **R3 — new-location restore:** add reservation and bootstrap restore/recovery
   contracts, NIO implementation and Path conveniences. Retain exact bootstrap and
   object bytes, refuse existing roots, prove partial-failure behavior. Resolve the
   existing-bootstrap durability acknowledgement limitation before release.
5. **R4 — platform integration/interoperability:** Android SAF stream adapter and
   app-private scratch/provider integration, physical durability/permission/restart
   tests, independent cross-platform archives and scale measurement. Consider larger
   profiles or explicit salvage only as separately reviewed extensions.

For each executable phase classify all applicable `CORE_RUNTIME`, `PUBLIC_API`,
`STORAGE_NIO`, `CONSUMER_BOUNDARY`, `PUBLICATION_METADATA` or `DEPENDENCY_BUILD`
changes before validation. Use one small ordinary Java baseline and focused module/
API/provider tests in the inner loop, then the stable full Java/publication and
independent consumer gate with immediate full-suite accounting. Source-Javadoc
changes follow their packaged-source/Javadoc gate. Freeze included inputs before
the single final **human** `nix flake check`; agents never run Nix. Only dependency
changes trigger the separate human cache-update/review checkpoint. Do not add a
host forced-offline full run without a build/resolution reason, or release artifact
reproducibility for a routine implementation milestone.

## This milestone's validation and final Git state

Only this excluded Markdown report was created. Inspected `qualification.nix`'s
selected files/trees and `flake.nix`'s qualification/tooling wiring: `review/` is
outside the Java derivation source filter and is not a separately referenced
qualification input. No source Javadocs or executable contracts were changed.
No Java/Javadoc build, dependency/cache update or human Nix gate is required for
this report-only milestone; no unperformed gate is claimed as passed.

| Invocation category | Count |
| --- | --- |
| Broad baseline Gradle | 0 |
| Focused Gradle tests / Javadoc | 0 / 0 |
| Focused standalone tooling / full tooling guardrails | 0 / 0 |
| Final full Java gate | 0 |
| Publication verification / manifest checks / consumer passes | 0 / 0 / 0 |
| Separate host offline full passes | 0; no build/resolution change |
| Human Nix runs / agent Nix runs | 0 / 0 |
| Failed validation attempts / qualification invalidations | 0 / 0 |

Document checks: local reference resolution, code-fence balance, whitespace and Git
membership/index checks. An initial document-check command could not run because
`python3` is unavailable; equivalent shell checks were used instead. No production
tests were written or executed; future test cases above
are a design, not test results. Read-only external reference lookup included one
failed Java 25 documentation fetch; Java 17 API documentation was available. This
did not affect qualification or require a repository change.

Final expected state, verified at handoff: `main` and HEAD unchanged; sole working
tree change `review/BACKUP_RESTORE_R0_JAVA_DESIGN.md`, intent-to-add only. Commands:

```text
git status --short
 A review/BACKUP_RESTORE_R0_JAVA_DESIGN.md
git diff --name-only
review/BACKUP_RESTORE_R0_JAVA_DESIGN.md
git diff --cached --name-only
(empty)
git diff --cached --quiet
(exit 0)
git diff --check
(exit 0)
```

No staged file contents, commits, tags, releases, production modifications or Nix
execution. Intent-to-add remains for human review/commit; it is source membership,
not staged content. No second build or release action is requested.

# Totipo portable backup archive, profile 1

This is the frozen application archive contract introduced by BACKUP_RESTORE_R1.
It does not modify the pinned v1/r19 vault protocol. `totipo-core` owns the shared
codec. Android and Desktop use the same bytes and stream interfaces.

## ZIP container

One single-disk classic ZIP, starting at offset zero and ending immediately after
its 22-byte EOCD. Exactly these regular payload entries occur in both local and
central order:

1. `totipo-backup.txt`: the manifest below, at most 512 bytes.
2. `vault`: exact 87-byte encrypted v1 VAULT representation.
3. Zero or more `objects-v1/<ID>`: exact 1024-byte encrypted immutable objects,
   with 64-character lowercase hexadecimal IDs in strictly increasing ASCII order.

Directories, duplicates (including identical duplicates), unsafe/unknown names,
uppercase aliases, other namespaces and arbitrary application data are rejected.
There is no outer folder. No extraction path is ever constructed from an entry.

STORED only; version-needed and DOS version-made-by are both 10 (ZIP 1.0).
General-purpose flags are exactly 0 or 0x0800; names are exact ASCII in either
case. No compression, encryption, descriptors, ZIP64, extras, comments, disk
spanning, prefix, trailing data, central digital-signature record, symlink or
special-file metadata. Central internal/external attributes are zero. Local and
central method, flags, time/date, CRC-32, compressed/uncompressed lengths and names
must agree; central offsets must identify the corresponding sequential local
records. Local data occupies precisely the region before the central directory;
the central directory occupies precisely the region before EOCD. No orphan records,
overlaps, unreferenced payload or alternate directory interpretation is accepted.

DOS time/date fields are opaque, must agree between headers, and confer no
freshness or installation instructions. The canonical writer uses 1980-01-02
00:00:00, flag 0x0800, and zero attributes. January 1 triggers JDK timestamp extras;
January 2 avoids those extras while remaining portable. The JDK owns ZIP assembly
and payload/CRC reading. A bounded restricted layout verifier supplies the full
central-directory/EOCD checks absent from ZipInputStream. This is deliberately
an application subset, not a general-purpose ZIP implementation. Generic archiver
repacking can make an otherwise recognizable backup unsupported.

## Manifest grammar and example

Exact ASCII, LF only, final LF, fixed field order, one ASCII space, no BOM, blank
lines, comments, extension fields or escapes. Decimal integers are `0` or an
unprefixed nonzero digit followed by digits. No leading zeros or signs. The
manifest must equal the template below after replacing placeholders:

```text
totipo-backup 1
protocol totipo-v1
vault-id <64 lowercase hex SHA-256 of exact vault>
consistency observed-set
enumeration complete
authentication checked
object-count <N>
unresolved-references <G>
```

N equals the exact object-entry count. G is in 0..4N, counting each authenticated
child/parent edge whose target is missing or has a different TOKEN_ID in this
captured set. It does not count missing ancestors that cannot be inferred from
available edges. Empty vaults use N=0, G=0. The canonical empty golden manifest is:

```text
totipo-backup 1
protocol totipo-v1
vault-id 8bef25c2a2b81a0691b4f719eabed9c001a1778af415ddccab380eba9b408351
consistency observed-set
enumeration complete
authentication checked
object-count 0
unresolved-references 0
```

Profile 1 permits only authenticated, successfully enumerated captures. Locked,
forensic and partial evidence export modes are deferred. An invalid/unreadable
supported candidate prevents an ordinary capture; no salvage prefix is returned.
Unrelated direct names, sibling namespaces, temporary files, registry state,
passwords, decrypted values and device credentials are outside this profile.
Exact ciphertext preserves historical assertions, conflicts, tombstones, parents
and metadata without regenerating assertions or TOTP codes.

All manifest capture statements are untrusted producer assertions when inspecting
an archive. Inspection checks only container grammar, byte lengths, CRC and
structural vault identity; it cannot authenticate encrypted objects or verify G.
`sourceAuthenticated=false` makes that boundary explicit. Even an authenticated
capture cannot authenticate the archive's origin, absence of omissions or freshness.
CRC and SHA-256 are integrity checks, not authentication. Object names remain
keyed v1 IDs, not ciphertext hashes. Per-object ciphertext SHA fields would duplicate
container inventory/CRC checks without adding authenticity and are omitted.
`archiveSha256` identifies every byte including the directory and manifest; callers
can compare it with a trusted expected digest or destination readback. An attacker
can repackage any replayed subset with a new valid manifest, CRCs and digest.

## Finite capture and concurrency

`VaultSession.captureBackup(BackupLimits)` is synchronous, authenticated provider
work under the normal session provider gate. It is a required VaultSession
operation, with no unsupported-operation fallback. Library sessions:

1. Freshly read structural canonical VAULT and require its SHA-256 identity to
   match the authenticated session.
2. Invoke the opt-in bounded SPI scan. Require enumeration ended without errors.
   Count every direct child against the enumeration budget, including junk.
3. Freeze sorted supported names; freshly read every supported candidate with the
   existing exact 1024-byte read, irrespective of stale scan kind/length.
   Independently authenticate, verify keyed ID, framing/padding and TOKEN grammar.
   Copy exact ciphertext into one private contiguous spool. Retain only non-secret
   token/parent facts for gap accounting; promptly clear each parsed secret.
4. Count unresolved references within the frozen set. Freshly reread canonical
   VAULT, checking identity and exact equality with the first read.
5. Check exact resulting ZIP capacity, compute its deterministic whole-ZIP digest,
   and return an owned detached capture. Failures return no capture/output.

Enumeration ending proves neither simultaneous existence nor remote completeness.
External honest publication before/during the pass may or may not be included;
publication after inventory freezing is not included. A child without its parent
is retained with a gap diagnostic. Disappearance before a required fresh read
fails capture. Disappearance after capture cannot change its bytes. A later
VaultState emission does not invalidate the frozen capture. Backup does not settle
uncertain publication, refresh state, mutate history or impose merge freshness.

The provider gate excludes this session's scans, writes, candidate validations
and cleanup throughout capture. Local secret operations keep their existing
separate lock. It does not exclude other sessions/processes or an Android bridge.
A coordinated provider owner must preserve its whole-root serialization and avoid
calling a session while holding a bridge lock that would deadlock store calls.
This milestone offers only observed-set coverage; it adds no snapshot/lease SPI
or stronger coordinated-local claim. An Android product requiring an exclusive
whole-capture boundary must arrange that ownership outside Java before admission.
Validation currently occurs inside the session gate; ZIP I/O afterward is detached.

## Stream ownership, lifetime and outcomes

`BackupCapture` owns its memory until close, can outlive the session, supports
repeat deterministic writes, and serializes write/summary/close. Close is
idempotent, waits for an active write, wipes/releases its ciphertext spool and
vault, and leaves no temporary files. Calls after close fail with
IllegalStateException. Source/provider/session/root references are not retained.

`writeTo(OutputStream)` borrows the stream at its current position and finishes
the ZIP without flushing or closing it. The JDK ZIP wrapper is closed to release
its native resources. Return acknowledges only that stream writes and ZIP finish
completed. The caller owns destination reservation, flush/close, readback,
durability, output placement and any cloud/provider outcome. On any error the
destination can contain arbitrary partial output; discard/reconcile it at the
application boundary. Retry the same frozen capture with a new stream.

`BackupArchives.inspect(InputStream, BackupLimits)` borrows and consumes through
EOF, accepts nonseekable/short-read input, stages bounded private memory, verifies
the whole container and returns a descriptive `BackupSummary`. It closes only
internal wrappers and wipes/releases staging on every exit. It retains no payload,
key or storage authority. Publicly constructed summaries/captures confer no import
permission. There are no restore/import APIs, target paths, canonical writers or
password-based archive-validation contexts in this milestone.

`BackupException` extends IOException with CAPACITY, CANCELLED,
SOURCE_UNAVAILABLE, INVALID_OBJECT, VAULT_CHANGED, MALFORMED and UNSUPPORTED.
Unsupported ZIP features are classified where recognized; malformed/unknown
manifest versions fail closed as MALFORMED. Input/output IOExceptions propagate;
provider failures use sanitized coarse outcomes. Programmer null/budget errors
remain NullPointerException/IllegalArgumentException; closed sessions reject with
SessionClosedException. Unexpected runtime/crypto failure never returns success.

Thread interruption requests cancellation, checked between provider calls, stream
reads and records. The interrupt flag remains set. Blocking provider/stream calls
and waiting on the session/capture gate cannot be forcibly interrupted by this
API; callers own cancellation of those resources and must wait for operation
retirement before disposing them. An admitted capture may finish while session
close waits; root wiping follows gate release. No cancellation rolls back already
written external output. Applications should bound concurrently admitted jobs.

## Initial implementation limits and interoperability

`BackupLimits.DEFAULT`: 4096 objects, 8192 observed direct children, 6 MiB input/output
ZIP bytes. Callers may lower each budget; larger values are rejected. Exceeding a
budget returns CAPACITY, with no successful truncation. Incomplete provider scans
remain SOURCE_UNAVAILABLE unless they explicitly report CAPACITY or UNSUPPORTED.
The default SPI bounded scan returns Incomplete(UNSUPPORTED) without invoking the
unbounded legacy scan; providers opt in. Legacy observation semantics are unchanged.

The capture spool is exactly 1024N bytes (4 MiB maximum). Inspection allocates one
fixed archive-budget array (6 MiB maximum), then bounded entry metadata and at most
one 1025-byte object read at a time. No filesystem scratch, archive-derived paths,
whole-payload list, growing input accumulator or decompression is needed. Source
observations and graph facts are bounded separately; authenticated capture processes
only one decrypted TOKEN at a time. The caller's live session, source store, output
buffer and concurrent operations consume additional memory.

A 4096-object chain produces 5,120,525 ZIP bytes; 4097 objects fail rather than lose
history. A direct JDK 25 probe (without session/KDF overhead) completed capture,
writing and inspection with a 32 MiB heap while retaining source ciphertext and
both caller output buffers. Capture retained approximately 4.94 MB and allocated
approximately 99.5 MB transiently through existing crypto and JDK ZIP machinery.
These are Linux measurements, not Android device qualification. Bounds are an
initial support policy, not a claim that arbitrary histories fit. A larger/disk
spool profile requires separate measurement and review; clients must report
capacity failure honestly and preserve the original history.

Golden fixtures and their fixed digests live in
`core/src/test/resources/backup/profile-1/`. Java tests authenticate their test
credentials, compare writer output byte for byte, and inspect all three fixtures.
Android API 30/current-device ZIP behavior, SAF close/readback/cancellation and
Desktop destination installation remain product qualification requirements.

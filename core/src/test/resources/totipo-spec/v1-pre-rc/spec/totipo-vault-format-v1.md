# Totipo Vault Format v1

**Status:** design draft, revision 19  
**Protocol version:** 1  
**Revision:** r19  
**Scope:** encrypted complete-state TOTP assertions, causal interpretation,
canonical encoding, cryptography, and durable store operations.

The key words MUST, MUST NOT, SHOULD, SHOULD NOT, and MAY express normative
requirements. This is a pre-RC semantic revision: r19 makes VAULT immutable and
create-once, removes password rewrap and replacement, and introduces VAULT_ID.
The bootstrap encoding and cryptography, object wire format, and TOKEN semantics
are unchanged. Conformance scopes are defined in Section 20. Earlier revision
history is historical, not an alternative grammar.

**Historical note:** v0 was an unreleased design draft. v1 defines no migration
protocol from v0. Historical artifacts are outside the v1 protocol.

## 1. Goals and authority

Totipo v1 operates on a configured durable object store. Synchronization is
optional and external. A local-only store, USB/removable storage, shared
filesystem, network mount, manually copied directory, or directory watched by
synchronization software uses the same protocol. Totipo validates and authenticates
what it observes; it does not cryptographically prove that the configured store
presents the complete or freshest valid history (Section 2).

TOKEN is the sole semantic object grammar. Each immutable TOKEN is a complete
assertion concerning one logical token. No value field is inherited. Explicit
parent references express causal incorporation; current values follow from the
valid observed objects, with concurrent disagreement preserved as whole-state
conflict. Possession of the unlocked vault root is the authority to author valid
vault state. There is no synchronized author identity object.

v1 does not define membership, secure deletion, garbage collection, root-key
rotation, consensus, or a global vault frontier. Future object families define
their own compatibility relationship to v1.

## 2. Threat model and storage boundary

Every configured-store entry and byte sequence that an implementation observes
MUST be treated as untrusted input and MUST obtain v1 protocol meaning only through
the applicable structural, cryptographic, and semantic validation. These checks
establish validity of the observed representation, not completeness or freshness
of the configured-store view.

Totipo v1 does not guarantee that the configured store presents a complete or
freshest view of object history. A store may hide or delete VAULT, substitute
another VAULT, or return malformed VAULT bytes. It may hide, delete, or return
malformed objects, replay stale subsets of object history, and present incomplete
observations. v1 provides no cryptographic availability, global freshness,
rollback, or deletion resistance for object history no longer presented by the
configured store. Valid older objects may still authenticate correctly; content
addressing authenticates object identity, not the observed set or its freshness.

One vault has only one legitimate immutable canonical VAULT. A different
representation is another vault or invalid/substituted storage evidence, never
a newer version of that vault. This removes choosing among legitimate bootstrap
generations; it adds no rollback resistance for object availability.

This limitation MUST NOT weaken validation of observed candidate material:
applicable path/name/type rules, bounded reads, physical size, AEAD authentication,
padding and length, keyed OBJECT_ID, exact TOKEN grammar and semantic bounds, and
VAULT authentication still apply (Sections 3, 6, 11, 12, and 13). Stale but valid
authenticated material is distinct from forged or tampered bytes that fail these
checks.

The local kernel, filesystem implementation, process namespace, and processes
with the application's privileges are trusted for baseline conformance. Stronger defenses against
malicious same-privilege races are optional implementation hardening.

Without K_root, an attacker cannot decrypt TOKEN contents, compute keyed IDs
for guessed plaintext, or author new valid objects, assuming the cryptographic
primitives hold. A compromised unlocked client can read secrets and author valid
state.

The configured durable store is expected to retain successfully published objects
under ordinary successful operation. Success does not promise perpetual retention.
Protocol correctness MUST NOT depend on sync acknowledgement, peer count, remote
completion, provider snapshots, conflict-copy names, or remote version retention.
Local publication establishes no remote propagation or globally complete history.
The success, durability acknowledgement, and ambiguous-failure requirements for
immutable TOKEN publication and VAULT creation (Sections 7
and 18) remain mandatory; they describe what success means when Totipo writes,
not what history the configured environment continues to present later.

**Informative deployment note:** Freshness, rollback detection, retained history,
auditability, and stronger deletion resistance beyond Totipo's own publication
requirements may be supplied by the configured storage/synchronization environment
or additional application mechanisms. Deployments requiring these properties
need to select or provide them explicitly; v1 conformance does not assume them.
Such guarantees depend on that layer's trust and retention model; version history
alone is not cryptographic rollback protection. Synchronization remains optional.

No separate persistent graph database or local protocol object cache is required.
Implementation caches MAY hold validated bytes or derived indexes for performance;
they are reconstructible, disposable implementation details, not a second
normative object source. They MUST NOT silently supply missing store ancestry.
Loss of prior observations or application configuration is not protocol corruption.

## 3. Configured store and names

The layout is:

```text
configured Totipo store
    vault
    objects-v1/
        <lowercase OBJECT_ID>
```

`vault` is the exact lowercase ASCII, case-sensitive canonical bootstrap pathname.
VAULT denotes the bootstrap representation, not an alternate pathname.
Alternate-case names MUST NOT be used as automatic aliases or fallbacks. Only that
pathname is automatically opened as the bootstrap; temporary files, alternate
names, backups, and provider conflict copies do not compete with it. Applications
may expose them as diagnostic or explicitly selected recovery material.

When present, canonical `vault` MUST be observed as a regular file before it is
interpreted as a bootstrap. An observed symlink, directory, FIFO, socket, device,
or other non-regular entry there is not a valid bootstrap candidate and MUST NOT
be deliberately followed or interpreted as one. Diagnose/fail as appropriate;
no automatic fallback to another pathname is allowed.

When present, exact `objects-v1` MUST be observed as a directory to serve as the
v1 object namespace. An observed symlink, regular file, FIFO, socket, device, or
other non-directory entry there supplies no valid v1 namespace and MUST NOT be
deliberately traversed as one. The observed-entry rules are:

| Canonical entry | Accepted observed type | Absence |
| --- | --- | --- |
| `vault` | regular file only | creation subject to Section 7 |
| `objects-v1` | directory only | no currently available namespace; lazy creation permitted |

These are accepted observed entry-type requirements, not proof against malicious
same-privilege races. They require no stable inode identity or particular platform
open/traversal primitive. Stronger race hardening remains optional under Section 2.
Namespace absence or wrong type produces no vault-wide operation gate and does not
invalidate already validated information under the normal observation model.

Only direct regular-file children of exact `objects-v1/` with exactly 64 lowercase
hexadecimal characters as names are object candidates. The name encodes the
32-byte OBJECT_ID. Readers MUST use bounded reads and allocations, MUST NOT
deliberately follow observed symlinks, and MUST ignore subdirectories, special
files, temporary/conflict names, and unrelated names. Metadata is not authentication.

Unknown sibling namespaces are outside v1 interpretation; their presence alone
creates no semantic state or operation block. Do not recursively scan them for
v1 objects. `objects-v1/` MAY be created lazily. Independent vaults MUST NOT
intentionally share this namespace. Observed plausible current-family object
candidates block ordinary creation when VAULT is absent (Section 7).

VAULT and each `objects-v1/<OBJECT_ID>` entry are create-once and immutable.
Totipo v1 protocol state is create-only after vault initialization: no
protocol-visible durable entry is intentionally replaced in-place. External
actors and storage can still mutate or delete bytes; the protocol does not
prevent such interference.

## 4. Cryptographic suite

v1 fixes the following primitives:

| Purpose | Primitive |
|---|---|
| Password KDF | Argon2id, version `0x13` |
| Root/subkey derivation | HKDF-SHA-256 |
| Private content addressing | HMAC-SHA-256 |
| Semantic object encryption | AES-256-GCM, 16-byte tag |
| Root-key wrapping | AES-256-GCM, 16-byte tag |
| Random values | platform CSPRNG |

The v1 Argon2id parameters are:

```text
memory       = 65536 KiB
iterations   = 3
parallelism  = 4
salt         = 16 bytes
output       = 32 bytes
secret K     = empty byte string
associated X = empty byte string
version      = 0x13
```

These parameters match the RFC 9106 second recommended Argon2id profile.

The number of execution threads used to evaluate the four Argon2 lanes is an implementation detail.

## 5. Password bytes

The format password domain is exactly `0..1024` bytes of well-formed UTF-8. The empty UTF-8 byte string is format-valid.

A character/string password MUST be encoded to UTF-8 strictly. Encoding errors, including unpaired surrogate code units in APIs capable of representing them, MUST be rejected rather than replaced. Pre-encoded password bytes MUST be validated as well-formed UTF-8.

The protocol performs no Unicode normalization. Applications MUST NOT silently normalize, trim, case-fold, or otherwise transform password bytes before Argon2id.

Inputs longer than 1024 UTF-8 bytes MUST be rejected. Application policy MAY require a non-empty or stronger password for creation, but readers MUST remain able to process every format-valid password byte string.

An interactive application MUST require explicit confirmation before creating a
vault with an empty password and SHOULD clearly explain that possession of the
vault bootstrap permits offline password guessing. Argon2id increases the cost
of guesses; it does not prevent offline guessing. Applications MAY apply stronger
local password policy at creation, but such policy MUST NOT make existing
format-valid vaults unreadable.

## 6. Root and bootstrap encoding

At vault creation, implementations MUST generate `K_root = CSPRNG(32 bytes)`.
K_root is permanent and immutable for the complete lifetime of that vault and
MUST NOT be rotated in-place. The password wraps K_root; it is not the object
encryption key. A different K_root always denotes a different cryptographic vault.

A vault has exactly one immutable canonical VAULT representation and exactly one
immutable K_root. The exact canonical VAULT representation is immutable for the
lifetime of the vault. After successful initial publication, VAULT MUST NOT be
intentionally modified, replaced, rewritten, rewrapped, or removed as part of
ordinary vault operation. Any different canonical VAULT representation denotes
another vault or invalid/substituted storage evidence; it is NEVER a newer version
of the same vault. There are no VAULT versions, generations, bootstrap lineage,
wrapper heads, or same-root alternate legitimate bootstrap representations.

The bootstrap is exactly 87 bytes, not TLV:

```text
offset  size  field
0       10    MAGIC = ASCII("TOTIPO-VLT")
10       1    BOOTSTRAP_VERSION = 0x01
11      16    ARGON2_SALT
27      12    WRAP_NONCE
39      32    WRAPPED_ROOT
71      16    WRAP_TAG
```

At creation, generate fresh CSPRNG salt (16 bytes) and
nonce (12 bytes). Derive K_wrap from the exact password and salt using Section 4.

```text
VAULT_HEADER = MAGIC || BOOTSTRAP_VERSION || ARGON2_SALT || WRAP_NONCE
WRAPPED_ROOT || WRAP_TAG = AES-256-GCM-Seal(
    key = K_wrap, nonce = WRAP_NONCE, plaintext = K_root,
    AAD = VAULT_HEADER, tag_len = 16)
```

Readers MUST first apply the canonical regular-file entry rule in Section 3.
Readers MUST reject incorrect size, magic, bootstrap version, or invalid password
bytes before running Argon2id. Readers MUST authenticate the wrapping before
using the recovered root. The VAULT record is an offline password verifier;
anyone possessing it may attempt password guesses.

## 7. Initial VAULT creation

Creation requires canonical `vault` absence. An observed wrong-type entry is not
absence and MUST NOT be treated as permission to overwrite it. If canonical
VAULT already exists, creation MUST NOT replace it, even if it is exact-identical
to the intended representation. Exact existing VAULT may be read and recognized;
a different existing canonical VAULT is another vault or invalid/substituted
storage evidence and blocks creation. No later lifecycle operation gains
replacement authority.

If canonical VAULT is absent but available observation reveals plausible
current-family protocol object candidates under `objects-v1/` (the direct regular
files with exactly 64 lowercase hexadecimal names defined in Section 3), ordinary
vault creation MUST NOT silently initialize a new unrelated vault into that
namespace. Implementations MUST require an explicit recovery, reconfiguration,
or new-location workflow. Applications SHOULD explain that VAULT absence may be
temporary or caused by incomplete/untrusted storage and recommend checking
synchronization/provider state and recovering the missing canonical bootstrap.
These candidates are unauthenticated contextual evidence, not proof of valid
objects. They MUST NOT be classified as garbage merely because VAULT is absent.
Creation MUST NOT delete them. This safeguard requires no exhaustive enumeration
or proof that unseen objects are absent.

Generate a fresh root, salt, and nonce; construct complete canonical VAULT bytes
separately and validate them locally, including authenticating the wrap and
checking the recovered root. Use the strongest reasonable crash-safe NO-REPLACE
publication mechanism available. Implementations MUST NOT knowingly overwrite an
existing canonical `vault`. Attempt required file/storage and containing-namespace
persistence before reporting success. Reopen canonical `vault` as a regular file,
compare its exact bytes to the intended representation, and revalidate its wrap
and recovered root. Report success only after all required publication,
persistence, and revalidation work succeeds. If no-replace publication or that
verification cannot be established, MUST NOT report success. No particular
platform syscall or atomic primitive is required.

An ambiguous failure MUST NOT be reported as success or as proof of absence.
It creates no persistent pending protocol state or required journal. The next
ordinary open validates canonical `vault` actually present; creation never
acquires authority to replace it.

## 8. Credential changes and independent vaults

v1 has no in-place password change and defines no password-rewrap operation.
Changing the vault password, bootstrap/KDF policy, or K_root requires creation
of a new independent vault. The destination MUST receive a fresh independently
generated 32-byte K_root, a fresh Argon2 salt, a fresh wrap nonce, a newly created
immutable VAULT, and a distinct vault identity. Reusing K_root for migration is
forbidden. v1 fixes its KDF policy in Section 4; this rule does not permit alternate
v1 KDF parameters or introduce a compatibility branch.

Migration MUST NOT replace the source VAULT, modify its object bag, convert the
source namespace in place, or rewrap the source root. Source and destination are
independent vaults; the source remains valid and unchanged. Applications may offer
“Migrate to a new vault” as a higher-level workflow; that workflow is not required
for r19 conformance.

### 8.1. Migration guidance (INFORMATIVE)

This section is application guidance, not normative protocol state or a transfer
protocol. A normal migration uses a separate destination store/namespace.
Applications may copy selected validated semantic state or available history
from an unlocked source into a new independent destination with fresh K_root.
Copied data is re-authored and re-encrypted under the destination vault. The
source VAULT and store remain unchanged. A destination need not retain any
permanent protocol fact about its source; no migration ancestry, identifiers,
receipts, or generations are added to VAULT or TOKEN.

A client can migrate only source state/history it currently observes and validates.
It cannot prove another device has no unsynchronized changes, that a provider is
globally current, that an entirely absent token does not exist elsewhere, or that
remote history has fully converged. Applications SHOULD disclose that migration
reflects source data currently visible to that client and may omit not-yet-
synchronized data. Applications SHOULD retain the old vault until the user has
verified the new vault. These SHOULDs are informative application recommendations,
not conformance requirements or claims of exhaustive migration.

Applications may choose what validated semantic state/history to copy and should
describe that choice honestly. “Preserve available history” and “Copy current
state” are application policy; the protocol imposes no history preservation,
compaction, conflict-selection, or tombstone-discard policy.

An old vault copy plus its password can continue to reveal the old vault. After
migration with a fresh independently generated K_root, old VAULT plus old password
does not derive the destination K_root. Copying does not revoke information an
attacker already learned: re-encrypting the same compromised TOTP secret does not
erase that knowledge. Issuer-side credential rotation is still required to revoke
a compromised TOTP seed. Where root compromise is suspected, rotate or reissue
affected credentials with their issuers and place replacement credentials in the
new vault; do not treat old encrypted history as sanitized data. Provider retention
may preserve old copies regardless of retirement/deletion of the old store.

## 9. Vault identity and recognition

```text
VAULT_ID = SHA-256(exact canonical VAULT representation)
```

The input is exactly the canonical 87-byte VAULT representation, with no pathname,
text encoding, prefix, or suffix. The output is 32 raw bytes; its ordinary text
form, when needed, is lowercase hexadecimal. VAULT_ID is non-secret and stable
because VAULT is immutable. Different canonical bytes produce a different vault
identity except with negligible SHA-256 collision probability.

VAULT_ID is recognition data and a convenient store/application binding identity.
It is not authorization, a password verifier beyond what VAULT already is, proof
of first-contact authenticity, proof of storage freshness or object completeness,
or rollback protection for the object bag. Computing it does not authenticate
VAULT; structural validation and successful root-wrap authentication still apply.

Applications MAY remember a configured store/location, expected VAULT_ID, friendly
name, and preferences. These are application configuration, not protocol-authoritative
state. If an expected VAULT_ID is retained, the exact current canonical VAULT MUST
hash to it for the expected binding to match. Mismatch means a different vault,
not a newer or older wrapper; malformed bytes remain invalid storage evidence.
A new client needs no remembered identifier. Loss of remembered configuration does
not invalidate the vault, prevent authorship, or prevent computation with a known
credential.

## 10. Object key hierarchy and identity

All domain strings below are exact ASCII without terminating NUL. HKDF uses
SHA-256; `||` means byte concatenation.

```text
PRK = HKDF-Extract(salt = 32 zero bytes, IKM = K_root)
K_id = HKDF-Expand(PRK, ASCII("totipo/v1/object-id"), 32)
K_object_root = HKDF-Expand(PRK, ASCII("totipo/v1/object-key-root"), 32)
OBJECT_ID = HMAC-SHA-256(K_id, P)
K_object = HKDF-Expand(K_object_root, ASCII("totipo/v1/object-key") || OBJECT_ID, 32)
nonce = first 12 bytes of OBJECT_ID
AAD = ASCII("totipo/v1/object") || OBJECT_ID
```

P is the exact canonical TOKEN semantic plaintext (Section 12). OBJECT_ID is
32 bytes and commits to all fields including metadata and parents. Identical
canonical assertions in one vault intentionally collapse to one content-addressed
object. No event identifier or random authoring nonce preserves indistinguishable
event multiplicity. Different metadata can produce distinct objects with equal
TokenValues. Keyed addressing prevents offline guessed-plaintext ID tests without
K_id.

## 11. Fixed encrypted envelope

Every valid object in objects-v1/ is exactly 1024 bytes.

```text
ENCRYPTION_PLAINTEXT = u16be(len(P)) || P || ZERO_PADDING
object file = AES-256-GCM-Seal(K_object, nonce, ENCRYPTION_PLAINTEXT, AAD)
object file size                  1024 bytes
AES-GCM tag                         16 bytes
encrypted plaintext               1008 bytes
authenticated semantic length        2 bytes
maximum semantic plaintext        1006 bytes
```

The tag follows the 1008 ciphertext bytes. Padding fills the remaining plaintext
space and every padding byte MUST be zero. No random or alternate padding is
permitted. Construct canonical P, compute its ID, derive its key/nonce/AAD, then
encrypt. Deterministic encryption uses a distinct derived key for each distinct
ID; accidental key/nonce reuse requires a cryptographic collision. Equality of
exact assertions within one vault is observable. Across independent roots, keys
and IDs differ.

The fixed shape simplifies bounded reading, storage validation, publication,
allocation, fixtures, and cross-platform interoperability. Hiding exact semantic
length is secondary. Variable-size authenticated encryption would be technically
viable; its length leakage is not a major security defect. This choice is not
primarily a filesystem-block-size optimization.

The maximum legal TOKEN is 1005 semantic bytes (Section 12), fitting the 1006-byte
capacity with one spare byte. Shrinking would reopen settled field limits or the
four-parent invariant. A larger envelope has no current semantic justification;
bucketed sizes add unnecessary canonicalization complexity. v1 reserves no
speculative in-family semantic-version headroom.

## 12. Canonical TOKEN grammar

TLV framing is `TAG:u16be || LENGTH:u16be || VALUE[LENGTH]`. Integers are
unsigned big-endian. The following registry and order are exact; every field is
mandatory exactly once except PARENT_ID repetitions and the two optional fields.
No unknown, nested, duplicate, reordered, or trailing TLV is accepted.

| Tag | Field | Value |
| --- | --- | --- |
| 0x0001 | TOKEN_ID | exact 32 bytes |
| 0x0002 | PARENT_COUNT | exact u16be, 0..4 |
| 0x0003 | PARENT_ID | exact 32 bytes, repeated PARENT_COUNT times |
| 0x0004 | STATUS | exact u8: 1 LIVE, 2 TOMBSTONE |
| 0x0005 | ISSUER | 0..256 UTF-8 bytes |
| 0x0006 | ACCOUNT | 0..256 UTF-8 bytes |
| 0x0007 | ALGORITHM | exact u8: 1 SHA-1, 2 SHA-256, 3 SHA-512 |
| 0x0008 | DIGITS | exact u8: 6, 7, or 8 |
| 0x0009 | PERIOD | exact u32be: 1..2^32-1 seconds |
| 0x000a | SECRET_BYTES | 1..128 raw bytes |
| 0x000b | CLIENT_NAME | optional, 0..128 UTF-8 bytes |
| 0x000c | CLIENT_TIME | optional, exact u64be Unix seconds |

```text
TOKEN_ID
PARENT_COUNT
PARENT_ID...   # exactly PARENT_COUNT occurrences
STATUS
ISSUER
ACCOUNT
ALGORITHM
DIGITS
PERIOD
SECRET_BYTES
CLIENT_NAME?
CLIENT_TIME?
MAX_PARENTS = 4
```

A new logical TOKEN_ID MUST be independently generated by CSPRNG(32 bytes).
Updates, deletion, restoration, and folds retain that ID. No semantic restriction
on an otherwise exact 32-byte TOKEN_ID is imposed by readers.

Parents MUST be sorted by unsigned lexicographic byte order and duplicate-free.
`0 <= PARENT_COUNT <= 4` applies to every object irrespective of field lengths.
A parentless TOKEN is valid; multiple roots with the same TOKEN_ID are concurrent.

Strings MUST be well-formed UTF-8, counted in bytes, with no normalization,
trimming, case folding, or locale transformation. Embedded NUL and control
characters do not invalidate UTF-8. Presentation SHOULD escape or safely render
untrusted text without altering stored values or treating it as markup/code.

CLIENT_NAME absence and present empty string are distinct on the wire.
CLIENT_TIME absence differs from zero; zero is an ordinary value, not a sentinel.
Both fields are informational and may be inaccurate. Subject to their canonical
encoding/width rules, their presence or values MUST NOT affect TokenValue validity,
authorization, equality, causal ordering, parent/head selection, conflict
resolution, or TOTP. CLIENT_TIME MUST NOT establish freshness or winner priority.

For as long as a validated TOKEN object is represented, returned, exposed, or
retained by an implementation, its CLIENT_NAME and CLIENT_TIME metadata MUST be
preserved exactly with that object: presence versus absence, present-empty name
versus absence, exact validated UTF-8 bytes/value, and absent time versus zero or
any other exact unsigned u64 value. While representing that object, implementations
MUST NOT normalize, synthesize, replace, merge, arbitrarily select, or discard its
metadata. Metadata belongs to its individual object.

Metadata preservation does not require persistent retention of a TOKEN object,
its metadata, or its graph facts after the configured store no longer supplies it.
It creates no advisory-history, remembered-head, protocol-cache, or cross-run
persistence requirement. Optional caches remain disposable; a fresh client may
derive state from the currently available configured store. The exact-preservation
rule above applies to any representation still retained;
prior observation alone does not require keeping it.
The operation-wide fold metadata rule in Section 17 remains unchanged.

Mandatory framing and fixed-width fields cost 77 bytes before variable values
and parents. Maximum non-parent fields cost
`77 + 256 + 256 + 128 + (4 + 128) + (4 + 8) = 861` bytes.
Four explicit parents cost `4 * (4 + 32) = 144` bytes.
Thus the maximum legal TOKEN is `861 + 144 = 1005` bytes; every legal value
always fits four parents.

## 13. Reading and exact validation

A reader MUST validate the canonical filename and exact 1024-byte size, derive
the object key/nonce/AAD, authenticate/decrypt, check semantic length at most
1006 and all-zero padding, recompute and match the keyed OBJECT_ID, and validate
the exact Section 12 grammar and field rules. Only objects passing all checks
contribute TOKEN state. Authenticated nonmatching grammar contributes no TOKEN
state; there is no alternative semantic dispatch within objects-v1/.

Invalid entries may produce diagnostics. A malformed or unavailable parent does
not invalidate a valid complete child. No file mtime, filename outside the canonical
namespace, or claimed metadata substitutes for authentication.

If two distinct successfully authenticated canonical TOKEN plaintexts validate
under the same OBJECT_ID, an implementation MUST NOT choose arbitrarily between
them. It MUST treat that object identity as an integrity/cryptographic failure
and exclude it from normal semantic evaluation; references to it remain unresolved.
This is distinct from exact-identical content collapse. No persistent continuity,
reset, or recovery state machine is required.

**Informative defensive-case note:** An ordinary store namespace has one canonical
path per lowercase OBJECT_ID. Two different valid canonical plaintexts with the
same ID would require a collision or failure of the keyed identity construction.
An untrusted or mutable provider may also present inconsistent bytes for one path
across separate observations over time. Changed bytes alone do not establish two
valid plaintexts: every candidate still needs all validation above. The defensive
rule remains appropriate even though this is not an expected normal operational
state; it introduces no collision-recovery procedure.

## 14. Observation and diagnostics

Evaluation receives valid observed TOKEN objects, unresolved parent references,
and diagnostics. It MUST NOT require exhaustive enumeration. Unreadable files,
wrong sizes, failed AEAD, invalid grammar, enumeration errors, resource limits,
and disappearance during observation may be diagnostic evidence. Those diagnostics
MUST NOT globally invalidate already validated objects or prohibit protocol
operations. Implementations SHOULD continue useful processing where practical.

Claims such as “this is every token,” “no other branch exists,” or “no other
TOKEN_ID exists” are not required. Optional inventory features are application
features. Every current-state description is relative to the valid object set
being evaluated, not proof of freshness or completeness. Later observations cause
ordinary recomputation. Loss of old objects does not invalidate remaining objects
or supply a reason to invent missing ancestry.

Disappearance or presentation of an older valid store view MUST NOT cause a crash
or turn lack of freshness evidence into protocol facts. Implementations MUST
derive current observable state from the available validated inputs under
Sections 15 and 16 and truthfully report unresolved references. A missing object
MUST NOT be treated as having never existed within the currently supplied graph
facts, nor may an unresolved parent be silently skipped. Reappearance of valid
objects permits ordinary recomputation. This requires no cross-run remembered
history; a fresh client may know only what the configured store currently supplies.

## 15. Causal equivalence and current heads

Within one TOKEN_ID, resolve a child-to-parent edge only when the referenced ID
is available as a valid TOKEN of that same TOKEN_ID. Otherwise leave the edge
unresolved, including a reference to a valid object of another TOKEN_ID.
Implementations MUST NOT invent ancestry, skip unavailable parents, infer unseen
grandparents, or reconstruct unobserved history. A later valid parent may resolve
an edge through ordinary recomputation.

Reachability follows zero or more resolved child-to-parent edges. Define:

```text
A ≡c B iff A reaches B and B reaches A
```

These causal-equivalence groups are the strongly connected components (SCCs).
Conceptually collapse them to a DAG. A group is maximal/current when no distinct
group is its strict descendant: no distinct group reaches it. Current heads are
every TOKEN object belonging to every maximal causal-equivalence group.

Heads remain complete validated TOKEN objects, exposing their OBJECT_ID, parents,
TokenValue, and exact CLIENT_NAME / CLIENT_TIME presence and values for truthful
presentation. The semantic-value projection may ignore metadata for equality;
it MUST NOT erase metadata from head objects or choose a synthetic metadata winner.
The authoritative per-object metadata-preservation rule is in Section 12; it
applies to every member of a maximal causal-equivalence group.

Members of one group are equally current; no representative wins. A strict
descendant of any member supersedes the whole group. Resolved cycles are ordinary
causal equivalence, not fatal integrity failures or operation blocks. Traversal
MUST be cycle-safe and bounded by available resources. Resource limits remain
diagnostics under Section 14.

**Informative constructibility note:** These semantics make the graph algorithm
total, and abstract tests may model resolved cycles. Each object's HMAC-SHA-256
identifier commits to plaintext containing its parent identifiers. Under the
assumed security of that construction, a genuine resolved cycle is not expected
from honestly constructed cryptographically valid v1 objects without a
cryptographic break or pathological fixed-point construction. This is not a
mathematical impossibility claim and does not relax cycle-safe traversal or
change the interpretation of an abstract resolved SCC.

For A <-> B, both are heads if their group is maximal. If D lists A and no object
descends from D, D supersedes both. Late arrival that completes a cycle simply
changes the groups and heads.

## 16. Complete values and truthful descriptions

TokenValue is the complete tuple `(STATUS, ISSUER, ACCOUNT, ALGORITHM, DIGITS,
PERIOD, SECRET_BYTES)`. Equality compares exact values/bytes in that tuple;
TOKEN_ID, parents, and client metadata are excluded. There is no field-level
merge or timestamp winner. Every object, including a TOMBSTONE, carries this
complete tuple.

Distinct TokenValues among current heads are ordinary whole-state conflict,
including within a single causal-equivalence group. Equal-valued current heads
represent one semantic value with multiple causal heads; equality does not make
one head an ancestor of another. No value is synthesized from several assertions.
For example, equal-valued heads A and B with CLIENT_NAME "Laptop" and "Phone"
represent one semantic TokenValue but retain both causal objects with A's and B's
respective metadata. The same rule applies to equal-valued members of a maximal
SCC; no representative metadata is selected. Section 12 governs exact metadata
preservation for both current and retained historical objects.

STATUS is lifecycle bookkeeping. TOMBSTONE marks deleted presentation state,
not an unusable or unavailable credential. Inspection and TOTP computation remain
permitted. Restoration is an ordinary complete assertion with STATUS = LIVE,
optionally changing other fields. No history reconstruction is needed to restore
a readable complete tombstone.

For application presentation, tombstoning means “not part of current logical
state,” not secure erasure; a current tombstone still remains a complete assertion
under the graph rules above. Historical immutable objects may retain the token
secret, and tombstones themselves retain SECRET_BYTES. Synchronization and storage
providers may independently retain historical copies. An application MUST NOT
describe tombstoning or deleting a token as securely erasing its secret from
history. v1 defines no garbage-collection or secure-erasure protocol.

Applications MUST truthfully distinguish current, historical/stale, conflicting,
equal-valued current heads, tombstoned, unavailable, missing ancestry, complete
value known, and complete value unavailable as applicable. MUST NOT present a
historical value as unique current state, a conflict branch as conflict-free,
or a tombstone as ordinary active state without indicating deletion. Known
unavailability MUST NOT be silently omitted from claims of complete state.
These are descriptive obligations, not credential-use permissions. Any complete
known credential may be used for TOTP, including historical or conflicting values.

## 17. Authorship and bounded folding

A writer authors a complete desired TokenValue for one TOKEN_ID. For a new logical
token, generate a new CSPRNG TOKEN_ID as in Section 12, supply or generate the complete
desired TokenValue, and use an empty parent set. An update intended to incorporate the observed frontier MUST
incorporate all selected current heads, directly or through the fixed fold below.
A claimed resolution of all known alternatives MUST disclose newly learned
relevant alternatives before making that claim. Hidden or later-discovered history
may remain concurrent; it does not retroactively invalidate a published assertion.

Unavailability of history alone MUST NOT prohibit authorship when the TOKEN_ID and
complete desired TokenValue needed for the new assertion are otherwise known or
supplied. This applies to assertions about a known logical token without changing
the new-token creation rule above. Unknown history does not authorize inventing
its contents or ancestry; the required authoring inputs must still be supplied.
Known parent IDs may be referenced without their currently readable bytes.
Disappearance of history already considered does not stale an informed decision.
Previously learned facts do not become false on disappearance, but remembered facts
are not a second source of current store ancestry. Retention across runs is not
required. Confirmation is informational application/UI policy, not a persistent
protocol authorization object. Unrelated changes do not change the decision's
TOKEN context. New relevant alternatives call for disclosure, not a blanket
authorship prohibition.

For a selected frontier of at most four heads, one object lists all selected IDs.
For width N > 4, sort original heads lexicographically and use fixed linear folding:

```text
F1 <- A B C D
F2 <- F1 E F G
F3 <- F2 H I J
...
```

The first stage incorporates four original heads; each later stage carries the
previous fold plus up to three remaining originals. Sort each stage's encoded
parent IDs and remove no required original merely to fit. All stages MUST carry
the same complete desired TokenValue and the operation's exact client metadata:

| Field carried by every stage | Required preservation |
| --- | --- |
| TokenValue | same complete desired value |
| CLIENT_NAME | same exact presence and UTF-8 value, including present-empty |
| CLIENT_TIME | same exact presence and unsigned u64 value, including zero |

Metadata is optional for the authoring operation; absence MUST be preserved on all
stages when absent for that operation. The table above is the operation-wide
preservation rule; Section 12 governs each resulting object. Only the direct parent
lists differ according to the linear-carry fold; TOKEN_ID also remains unchanged.
MUST NOT independently omit metadata, refresh CLIENT_TIME, or change CLIENT_NAME
between stages. Stages do not represent separate UI authoring events. This
preservation rule does not change the informational-only role of metadata defined
in Section 12. The final stage causally incorporates every selected original
through the constructed fold.
There are `ceil((N - 1) / 3)` stages for N > 4.

Each stage is an ordinary complete TOKEN. There is no multi-object transaction,
wire batch marker, or rollback of earlier published stages after a later failure.
Other clients may observe intermediate stages. Missing stages leave unresolved
edges. New alternatives may require a further assertion to make a resolution
claim accurate, without invalidating already published stages.

## 18. Immutable TOKEN publication

Publication addresses `objects-v1/<lowercase OBJECT_ID>` and supplies the complete
intended 1024 bytes. Construct bytes away from the canonical target. Implementations
MUST NOT deliberately publish by progressively filling or truncating a canonical
file. Use the strongest reasonable crash-safe publication method available;
attempt file/storage and containing-namespace persistence before reporting new
publication success. Report success only when the implementation believes its
required durability work succeeded. No particular syscall, atomic primitive,
mandatory reread, decrypt, or semantic self-validation is required at this layer.
Writer correctness and observation validation remain separately required.

If the exact intended bytes already exist at a regular canonical target, return
idempotent success after read-only exact comparison. MUST NOT rewrite, replace,
rename, touch, chmod, or otherwise mutate that entry merely to reconfirm it.
No fresh fsync or other persistence barrier is required merely for exact-existing
success, nor any semantic reread/decrypt.

If an existing target is non-exact, unsuitable, or cannot be established as exact,
leave it untouched and fail publication. MUST NOT repair or replace it in ordinary
publication. Ambiguous failure MUST NOT report success and MUST NOT infer absence.
No persistent publication-pending/unknown state is created. Later observation or
an exact retry handles recovery: absent permits another attempt, exact permits
idempotent success, different/unsuitable fails unchanged.

Parent availability is not a publication prerequisite. Lazy namespace creation
uses the same durability intent. Stores lacking atomic visibility can expose
intermediate effects despite a reasonable complete-byte staging strategy; this
does not create a new protocol state. Observed partial files are invalid diagnostic
evidence. Success promises neither perpetual retention nor remote completion.

## 19. TOTP computation

The flattened credential fields are:

```text
ALGORITHM
DIGITS
PERIOD
SECRET_BYTES
```

The credential is atomic.

v1 fixes:

```text
T0 = 0
PERIOD is integer seconds
PERIOD > 0
T = floor(Unix_time_seconds / PERIOD)
```

`Unix_time_seconds` MUST be non-negative.

The counter MUST fit in `0..2^64-1` and is encoded as unsigned 8-byte big-endian.

TOTP follows RFC 6238 with RFC 4226 dynamic truncation.

Supported algorithms:

```text
0x01 = HMAC-SHA-1
0x02 = HMAC-SHA-256
0x03 = HMAC-SHA-512
```

`DIGITS` MUST be exactly 6, 7, or 8.

`PERIOD` is `u32be` in `1..2^32-1`.

`SECRET_BYTES` is `1..128` raw bytes.

Newly generated secrets MUST be at least 20 bytes long and every byte MUST be generated by the platform CSPRNG.

v1 does not represent HOTP/event-counter credentials, `T0 != 0`, non-decimal/alphanumeric OTP formats, or digit counts other than 6/7/8.

Importers MUST NOT silently coerce unsupported credential types.


To compute a code, HMAC the eight-byte counter using SECRET_BYTES and the chosen
algorithm. Let offset be the low four bits of the final HMAC byte. Read four
bytes at that offset as big-endian, clear the high bit, reduce modulo 10^DIGITS,
and render exactly DIGITS decimal characters with leading zeros. STATUS and
client metadata do not enter the computation.

## 20. Conformance scopes and evidence

An implementation MAY claim the scopes it actually implements: “v1 core
conforming,” “v1 store conforming,” and/or “v1 application conforming.” Claims MUST
identify their scopes and applicable supported operations (for example reading,
writing, or TOTP computation), rather than imply an undifferentiated full
conformance claim. The requirements in this specification remain normative within
their applicable scopes; classification does not weaken them.

| Scope | Requirements covered |
| --- | --- |
| Core protocol | Exact encoding/framing, bootstrap and object cryptography, OBJECT_ID validation, TOKEN parsing and validation, metadata preservation, causal graph and current-state computation, complete-value equality, parent/fold behavior, TOTP where implemented, and other portable byte/semantic behavior. |
| Store/writer | Observation and store qualification assumptions; create-only publication, crash-safe initial creation and object publication under Sections 7 and 18, no-replace behavior, orphan-object creation safety, durability-result handling, and preservation of required object bytes and metadata. No stronger atomic primitive is imposed by this scope. |
| Application | Truthful current/stale/conflicting/tombstoned/unavailable and incomplete-state presentation; relevant-alternative disclosure during resolution; warnings and explicit confirmation; delete-versus-erasure and credential-change security wording; safe presentation of untrusted text. |

The per-object metadata rule in Section 12 applies wherever objects are
represented, and Section 17 additionally governs operation-wide fold metadata.
Store and application behavior relying on core operations MUST satisfy the
applicable core requirements, whether implemented directly or through a library.
A core library need not guarantee the behavior of a GUI built on it. Merely
exposing enough information for a UI to comply does not establish application
conformance; that scope requires the application's actual behavior to comply,
including Sections 5, 7, 8, 12, 16, and 17.

Conformance evidence MUST cover the claimed scopes and operations. Applicable
core evidence includes valid and invalid bytes, metadata presence and limits,
four-parent bounds, maximum size, fixed folds, missing parents, equal and
conflicting cycles, complete tombstones, bootstrap known answers, and VAULT_ID recognition.
Store evidence includes diagnostic-only incomplete observation and truthful
create-only publication outcomes. Application evidence additionally covers the
required disclosures, warnings, confirmations, and truthful state descriptions;
portable corpus success alone does not establish application conformance.

Abstract graph fixtures may model cycles and same-ID failures without pretending
to construct cryptographic collisions. Storage workflow fixtures model platform
outcomes; they are not proof of a particular filesystem's crash durability.
The platform-neutral durability contracts in Sections 7 and 18 still apply.

The repository moving pre-RC requirements profile pins this specification, schema,
manifest, and exact required cases. It is not a frozen RC profile. Every case
remains required to pass that profile; scope claims introduce no corpus SKIP or
capability mechanism. Historical revision records do not confer compatibility
with their old TOKEN bytes.

## 21. Revision history

### v1/r19

Nineteenth v1 design draft. VAULT becomes immutable/create-once; password rewrap
and VAULT replacement are removed. Credential/KDF/root changes create independent
vaults with fresh roots. Introduces VAULT_ID and removes redundant VAULT_FINGERPRINT.
Strengthens initial no-replace publication and orphan-object creation safety;
protocol state is create-only after initialization. Migration guidance is
informative and non-protocol; conformance is adjusted accordingly. VAULT encoding,
bootstrap cryptography, object wire format, and TOKEN semantics are unchanged.

### v1/r18

Eighteenth v1 design draft. Application-safety and conformance hardening,
compromise-recovery guidance, and editorial clarification; no portable protocol
semantics or vector outcomes changed. Strengthens creation warnings and
confirmation, makes erasure and rewrap disclosures explicit, clarifies defensive
graph cases, consolidates metadata references, and archives detailed pre-r16
history. No wire-format or cryptographic construction change.

### v1/r17

Seventeenth v1 design draft. Threat-model clarification from r16:

- distinguishes hostile/untrusted observed content from completeness and freshness
  of the configured-store view;
- explicitly locates rollback detection, freshness, and retained-history guarantees
  beyond v1 at the storage/application layer, with synchronization still optional;
- makes no wire-format, cryptographic, graph, storage-publication, or semantic
  change and changes no conformance-case outcome.

### v1/r16

Simplifies to flattened TOKEN-only complete state with optional CLIENT_NAME and
CLIENT_TIME; removes DEVICE, provenance signatures, in-family semantic dispatch,
readiness and candidate-use gates, advisory history, and mandatory VAULT_BINDING.
Fixes four parents and linear folds, interprets cycles as causal equivalence,
retains the 1024-byte envelope and outer object cryptography, introduces stable
VAULT_FINGERPRINT recognition, and specifies platform-neutral durable publication
and exact compare-before-replace. TOKEN wire bytes intentionally change.

### v1/r1–r15

Earlier drafts explored designs subsequently removed or replaced by r16.
The [detailed r1–r15 history](../review/V1_PRE_R16_REVISION_HISTORY.md) preserves
those historical entries; none defines current requirements.

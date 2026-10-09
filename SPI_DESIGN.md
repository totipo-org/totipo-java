# Experimental storage-provider boundary

The SPI is experimental and not frozen for source/binary compatibility.
The provider understands storage layout and opaque bytes; core interprets v1/r19.
Ordinary applications use `org.totipo` with `NioTotipo`.

```text
Totipo / VaultSession
    -> TotipoStore
    -> NioTotipoStore

<root>/vault
<root>/objects-v1/<direct-child-name>
```

## Cohesive provider contract

`TotipoStore` offers `readVault(expectedBytes)`, `scanObjects()`,
`readObject(name, expectedBytes)`, `publishObject(name, bytes)`,
`createVault(bytes)`, and `close()`.
It knows exact root/namespace names, direct-child names, entry kinds, bounded
content, create-only opaque publication, durability acknowledgement and lifetime.
It has no TokenId, RevisionId, K_root, authentication, keyed object addressing,
TOKEN grammar, parent/merge/TOTP or synchronization responsibility.
There are no protocol-aware provider overloads or public staging handles.

| Type | Role |
| --- | --- |
| TotipoStore | Cohesive synchronous storage and lifetime boundary |
| ObjectName | Exact name value, without protocol grammar |
| ObjectEntry | Name, observed kind and optional logical content length |
| EntryKind | REGULAR, DIRECTORY, SYMLINK, OTHER, UNKNOWN |
| ObjectScan | Complete or Incomplete enumeration retaining observations |
| BoundedRead | Present, Undersized, Oversized, Absent, WrongKind, Unavailable |
| ObjectWrite | Written, AlreadyPresentExact, ExistingDifferent, Failed, Uncertain |
| VaultCreate | Created, AlreadyPresent, Failed, Uncertain |
| StoreFailure | UNAVAILABLE, UNSAFE_NAMESPACE, UNSUPPORTED |

## Exact names and observations

ObjectName preserves exact Java strings without normalization or case folding.
Providers reject names that cannot safely identify one direct child. NIO rejects
empty/dot/dot-dot, absolute and multi-component paths. That is storage path safety.

A scan observes all direct children of exact `objects-v1`, including junk,
uppercase names, temporary names, directories, links and unknown kinds. It never
recurses. Results defensively copy and sort entries by exact String value;
adjacent exact duplicates are rejected without hashing hostile names. NIO retains
an anomalous enumeration duplicate once and reports Incomplete. Failed kind/length
observation retains the name with UNKNOWN/empty length and makes the scan Incomplete.
Regular-file length is logical content bytes, never allocated blocks.

Complete means enumeration reached its end, without claiming an atomic snapshot,
remote completeness, freshness or simultaneous existence. Incomplete preserves
positive observations. Positive absence of `objects-v1` is Complete/empty;
unsafe/unavailable namespace is Incomplete. Neither reads nor scans create it.
An alternate-case namespace cannot supply canonical data. A lookup alias without
an exact directory-entry spelling is unsafe; a distinct uppercase sibling is unrelated.

Core interprets exact lowercase-hex object names and freshly reads every candidate,
regardless of stale scan kind/length. Fresh WrongKind contributes unavailable-object
evidence. Independent valid observations survive diagnostic failures; merge freshness
continues to use known observation limitations. Creation uses observed plausible names
as unauthenticated contextual evidence and vetoes before entropy, including incomplete
observations. No enumeration-completeness proof or authenticated-object claim follows.

## Fresh bounded reads

readObject takes ObjectName, never ObjectEntry. readVault selects only exact `vault`.
Both freshly observe no-follow kind and bounded bytes. NIO consumes at most
expectedBytes + 1 content bytes, without relying on size metadata. Bounds are
nonnegative ints, with overflow-safe separate lookahead at Integer.MAX_VALUE.

Present requires an exact regular target and exact length; it defensively owns
bytes and returns copies. Undersized requires observed EOF, Oversized an extra
byte and returns no truncated prefix. Absent requires positive exact-name absence.
WrongKind and Unavailable remain distinct. Disappearance after exact selection may
be Unavailable. Exact spelling is selected from actual directory entries;
NOFOLLOW_LINKS prevents final symlink following. The trusted local OS, filesystem,
mount/process namespace and same-privilege-process model is unchanged. There is no
new descriptor-pinned snapshot or adversarial namespace transaction guarantee.

## Create-only VAULT

createVault publishes complete opaque bytes only when exact canonical `vault` is
absent. Any exact existing entry, including wrong kind, blocks publication unchanged.
An alias collision without exact spelling is unsafe, rather than an existing canonical
acknowledgement. Existing VAULT is never replaced, including exact-identical bytes.

Created positively acknowledges new publication and required storage durability.
AlreadyPresent positively establishes existing canonical presence and no mutation.
Failed establishes no canonical mutation by this call. Uncertain permits possible
mutation without acknowledgement. Temporary residue is nonauthoritative; cleanup
never deletes or repairs canonical data.

Core constructs and locally validates intended 87-byte VAULT, then invokes createVault.
After Created it freshly rereads exact canonical bytes, requires equality, valid
structural bootstrap, credential authentication, and equality with the generated root.
Only then can the application receive Created(session). A failed post-publication
check is Uncertain. Staging is an internal provider mechanism: exposing a handle
would add no security property to this sequence under the existing trust model.

## Immutable object publication

Written acknowledges a newly installed exact child and required durability.
AlreadyPresentExact acknowledges exact bytes plus required durability; equality
alone does not establish recovery after an uncertain write. ExistingDifferent
leaves different bytes untouched. Failed proves this call did not mutate the exact
target; Uncertain permits possible mutation. All input arrays remain caller-owned.

Before every new object-target mutation NIO ensures a safe exact `objects-v1`
namespace and acknowledges root-directory persistence. This stateless barrier
applies after provider close/reopen and to pre-existing namespaces. It then writes
and forces a complete stage, installs without overwrite, forces installed content,
and persists the containing directory. Exact-existing retries force the file,
objects directory and root, then confirm exact bytes again. Failed barriers cannot
acknowledge success. Namespace creation alone can coexist with Failed for the target.

VAULT and object publication share NioFiles staging/write/cleanup helpers,
NioCanonicalInstaller and StorageDurability. Their sequencing remains explicit:
VAULT has one fixed root name and no exact-existing success; objects need namespace
creation, exact-existing recovery and additional root barriers. A generic publication
framework would obscure these differences.

## Shared NIO and coordinated provider composition

Ordinary `NioTotipoStore.open`, `NioTotipo.open` and `NioTotipo.create` retain exclusive
hard-link installation for independent honest shared-store writers. No link failure
selects a weaker fallback. Concurrent initial creators retain strong no-replace
exclusion; only one can install a canonical VAULT.

The separate experimental integration boundary is:

```java
TotipoStore delegate = NioStoreComposition.coordinatedDelegate(root, durability);
```

An outer owner such as Android CoordinatedPrivateStore must obtain one persistent
delegate, wrap it, and serialize every store call, session, writer, bridge mutation
and handle across the entire root for its lifetime. Direct use without that
whole-root serialization violates the factory contract. Opening cannot enforce
this policy; core's provider gate coordinates only one session. Remote bytes enter
through the owner's controlled bridge. Ordinary desktop shared directories use
the shared facade.

This delegate preserves complete-stage ordinary `Files.move` without options,
closes the stage before moving, freshly checks absence, and forces the actual
canonical file afterward because providers may implement moves by copying.
It then persists the containing directory. Move errors after entry are uncertain
unless no effect is positively established. Ordinary moves provide no atomic
no-replace exclusion against independent writers; the outer owner supplies that
exclusion. The mechanism remains in storage-nio, with no Android dependency or
raw filesystem logic in core. Java tests do not extend Android physical qualification.

## Certainty, ownership and synchronization

A mutation operation exception alone cannot prove no effect. Link/move entry is
the uncertainty boundary; definite exclusive-create collisions preserve positive
no-mutation knowledge. Ordinary failures use coarse StoreFailure values, not
exception messages. Known non-directory/link components, unsafe publication targets
and alias collisions are UNSAFE_NAMESPACE. Unsupported capability is UNSUPPORTED;
ordinary inability to observe/access is UNAVAILABLE.

Positive durability means the configured provider accepted required force work;
it implies no remote propagation, universal crash survival or peer observation.
StorageDurability remains injectable. Session publication uncertainty is monotonic
across the entire frozen operation and its retries until every stage is acknowledged.

Passing a nonnull store to Totipo.open/create transfers ownership on every outcome.
Failure/invalid password closes it; success transfers it to VaultSession. The caller
must no longer use or close it. Close is idempotent, never removes canonical data,
and performs best-effort resource cleanup. Provider operations on a handle are
serialized by core. The provider gate coordinates scans, validation, saves, retries,
cleanup and close; the local secret/lifecycle lock remains separate.

Opening NIO observes an existing safe root without creating namespaces, objects,
VAULT, normalization or cleanup. The two-module implementation bridge
`format.VaultLifecycle` uses TotipoStore directly; codecs and protocol algorithms
remain internal. There is one provider boundary.

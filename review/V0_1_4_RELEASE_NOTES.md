## v0.1.4

### Highlights

Adds a private/exclusive-local NIO storage mode intended for separately controlled
local replicas, including platforms/filesystems where hard-link installation is
unavailable:

- Added `NioTotipoStore.openPrivate(Path)` and
  `NioTotipoStore.openPrivate(Path, StorageDurability)`.
- Private mode installs complete staged representations using ordinary moves
  without replacement options. The application must exclusively control the root
  and serialize all writers, handles/sessions and bridge operations. Independent
  synchronization software or unrelated writers must not mutate the root.
- Existing `open(...)`, `NioTotipo` entry points and shared/default mode retain
  hard-link publication with no automatic fallback. Directly synchronized/shared
  directories should continue using shared mode unless separately qualified.
- VAULT replacement, persistence acknowledgement and explicit uncertainty behavior
  remain unchanged.

### Compatibility and scope

Source/binary compatibility is preserved: the public API adds two static factories
without removing or changing existing descriptors or sealed hierarchies. Maven
coordinates remain `org.totipo:totipo-core` and `org.totipo:totipo-storage-nio`,
advancing together to 0.1.4; Java 17 remains the production target.

No protocol revision, spec repin, wire-format, cryptographic, conformance-vector
or external dependency change. Totipo Vault Format v1/r18 remains pinned to
`4623a7e1718e23504903096c92332597057bd8f0`. The separate stronger Java exact-existing
acknowledgement contract remains unchanged. No provider/SAF bridge is supplied.

JVM and fault tests do not establish Android filesystem, API-37 runtime, Android
crash-safety or physical power-loss qualification. Physical Android 0.1.4
requalification is a separate follow-up. Existing operation-scoped qualification
and application responsibilities remain in README, API_DESIGN and SPI_DESIGN.

### Validation

The preparation report records local qualification and the human Nix checkpoint.
The protected release workflow must revalidate the reviewed commit, normal/offline
suites, all 90 portable cases, Java 17 production bytecode, artifact inventories
and both consumer modes before publication. Exact remote bytes and fresh Central
consumers remain required before creating the source tag and GitHub release.

See [the 0.1.4 preparation report](V0_1_4_RELEASE_PREPARATION_REPORT.md) and
[the private-local portability investigation](NIO_PRIVATE_LOCAL_PORTABILITY_REPORT.md).

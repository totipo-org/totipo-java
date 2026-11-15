## v0.3.0

### Highlights

Encrypted backup, history import and new-location restore are now available through
`org.totipo.backup`. Profile-1 archives retain exact encrypted VAULT and immutable
object bytes, including observed historical revisions, conflicts and tombstones.
The bounded ZIP profile and shared fixtures are documented in
[the archive contract](../docs/BACKUP_ARCHIVE.md).

- `VaultSession.captureBackup(BackupLimits)` authenticates locally observed history
  and returns an owned `BackupCapture` that can write an archive after session
  closure. `BackupArchives.inspect(...)` checks archive structure without a password;
  inspection does not authenticate its contents, origin, completeness or freshness.
- `VaultSession.prepareImport(...)` authenticates an archive for the exact same
  vault and preflights its destination. `BackupImport.apply()` adds immutable
  history without replacing VAULT, pruning objects or creating merge assertions.
  Results distinguish completion, partial progress and uncertain persistence;
  explicit retries replay the frozen ciphertext. Applications request ordinary
  observation separately after installation.
- `BackupArchives.prepareRestore(...)` authenticates the archive using its original
  password and returns a detached restore plan. `NioRestore.reserve(...)` exclusively
  allocates a new child directory, refusing every preexisting entry. Restore installs
  the original VAULT and objects with create-only publication and persistence
  acknowledgement. It preserves vault identity and password. Incomplete restoration
  must not be presented as complete; closing a plan does not remove installed files.
- `TokenHead.parents()` exposes immutable, validated immediate causal parents, each
  with its own revision metadata and ancestry. Captured graphs remain readable after
  refresh and session closure. Unavailable or invalid parents are not fabricated;
  applications should consult the corresponding observation diagnostics for gaps.

### Compatibility and scope

Both Maven modules advance together to 0.3.0. The API and provider SPI remain
experimental. Custom implementations of `TokenHead` and `VaultSession` must implement
the new parent and backup methods. Custom stores must implement bounded
`TotipoStore.scanObjects(int)` to support capture: its default returns an incomplete,
unsupported result rather than invoking unbounded enumeration. The NIO provider
implements bounded enumeration.
`StoreFailure` adds `CAPACITY`; consumers with exhaustive enum switches must
account for that value.

The vault protocol remains exact Totipo Vault Format v1/r19 at
`cdb4e91be1c6d3704874b2b92457ffe7be5e9084`. Backup profile 1 is an application
archive format, not a vault protocol revision. Java 17 production bytecode and
Bouncy Castle 1.86 as core's sole runtime dependency remain unchanged. No new
production dependency or native/FFM boundary is introduced.

Backup operations are blocking and belong off the UI thread. Captures describe a
finite observed set, not an atomic snapshot or proof of remote/global completeness.
Archives may retain explicitly diagnosed missing ancestry. Applications own stream
closure, destination durability, cancellation of blocked I/O, observation and UI
orchestration. Coordinated providers retain their external whole-root serialization
requirement. Android SAF integration, sync and physical platform qualification are
separate application responsibilities.

### Validation

Local preparation evidence and outstanding gates are recorded in
[the preparation report](V0_3_0_RELEASE_PREPARATION_REPORT.md). Release-source CI and
the protected workflow independently qualify the exact reviewed commit, including
full test accounting, normal/offline ten-artifact hash comparison and both consumer
modes. These notes describe the prepared source and do not claim publication.

# Shared Totipo backup profile 1 fixtures

These are portable ZIP bytes for every Totipo client, independent of Android and
Desktop. The normative application profile is `docs/BACKUP_ARCHIVE.md` at the
repository root. They are **test credentials and test vaults only**.

Password: `fixture password` (ASCII). VaultId:
`8bef25c2a2b81a0691b4f719eabed9c001a1778af415ddccab380eba9b408351`.

| File | Bytes | Objects | Unresolved parent edges | SHA-256 |
| --- | ---: | ---: | ---: | --- |
| empty.zip | 522 | 0 | 0 | 603eb0dd84f67678bab82c78ee684dc22592b5e4de37f24d16f5694c382fa425 |
| history.zip | 4272 | 3 | 0 | 369b77c872a32f2474d867e0653e39b66befdfc6cbd1244da486505ba8bca95b |
| missing-parent.zip | 1772 | 1 | 1 | 6991ee0b764409728a10ee6612d6eced56207dbefa58a35611ea3d6112c25e58 |

VAULT comes verbatim from pinned r19 `v1.bootstrap.ascii.001`. The history fixture
contains the exact `object_hex` values from `v1.crypto.token-root.001`,
`v1.crypto.token-child.001`, and `v1.encoding.complete-tombstone.001`. Thus it
contains an ancestor, its live child, and a concurrent complete tombstone, all
under one TOKEN_ID. The missing-parent fixture contains only that live child.
These fixtures do not include metadata; runtime tests separately exercise exact
optional metadata, branch and tombstone preservation.

ZIPs were assembled independently using JDK ZipOutputStream with the literal
manifest grammar, STORED method, UTF-8 flag and DOS timestamp 1980-01-02 00:00:00,
then checked against the production writer byte for byte and authenticated through
the ordinary session candidate validator. No vendor specification file was edited.
Expected bytes and hashes are fixed; tests do not regenerate fixtures.

Standard ZIP tools can list these files. Repacking with another tool can introduce
unsupported extras, directories, compression or attributes. Passing inspection is
structural evidence only. These files qualify no restore/import operation.

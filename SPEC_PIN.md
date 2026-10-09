# Totipo specification pin

- Upstream: https://github.com/totipo-org/totipo-spec
- Exact upstream commit: `cdb4e91be1c6d3704874b2b92457ffe7be5e9084`
- Release/tag: committed revision pin; no release implied.
- Normative revision: `r19`
- Requirements profile: `requirements/v1-pre-rc.json` (moving-pre-rc)
- Profile file SHA-256: `7242fc557a7ca56452c934bedc5e7dc2835248f5cd36fa220f05588dc967ec80`
- Local snapshot: `core/src/test/resources/totipo-spec/v1-pre-rc/`
- Vendored cases: **92**; upstream files: **99** (92 cases and 7 supporting files).

| Artifact | SHA-256 |
| --- | --- |
| `spec/totipo-vault-format-v1.md` | `8bb76b890086eb4bf89271edd5861e02f833f3ffa11a83b5865080ed4e4228cb` |
| `vectors/manifest.json` | `3953dbc315b4dcb3d0d31dd399bf82a15cb38c49fde2e2b93ea81c5cfe0ec714` |
| `requirements/v1-pre-rc.json` | `7242fc557a7ca56452c934bedc5e7dc2835248f5cd36fa220f05588dc967ec80` |
| `vectors/case.schema.json` | `99805d442f13872cd4febe9ac8ae4f36fd25607580bc1e457ff5ae2efe199e23` |
| `vectors/manifest.schema.json` | `f265771f904be57d19602dd6da9a572c16b3f80fdd166c5177721aaa3f9a91ec` |

The committed source identifies Totipo Vault Format v1/r19. All five hashes match
the reviewed r19 artifacts. SNAPSHOT.sha256 covers every imported upstream file,
excluding itself, in sorted path order. Upstream bytes are imported unchanged;
no upstream implementation or historical review materials determine behavior.

r19 makes VAULT immutable and create-only, replaces root recognition with VAULT_ID,
and requires orphan-candidate creation safety and canonical post-create verification.
Bootstrap encoding/Argon2/AES-GCM, object crypto, TOKEN encoding and TOTP are unchanged.
See [the implementation report](review/V1_R19_REPIN_SIMPLIFICATION_REPORT.md).

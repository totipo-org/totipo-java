## v0.1.3

### Highlights

Fix release preparation for versions after 0.1.1. Reviewed notes are selected by
the requested version and validated during preflight, before publication. The
0.1.2 run reached its final tag/GitHub-release job but failed because that job
still read the 0.1.1 notes. This release uses its own matching notes.

The 0.1.2 API changes are included in this release:

- Added `MergeToken.keep(TokenAlternative)` for whole-Alternative conflict
  resolution. Issuer, account, status, hidden secret equivalence, algorithm,
  digits and period transfer atomically from one complete captured value.
- Both Active and Deleted/TOMBSTONED values can be selected. This selects a
  semantic Alternative, not a Head; multiple supporting Heads imply no preference.
- The selected Alternative must belong to the captured merge basis. Foreign,
  unrelated or out-of-basis references are rejected before publication.
- Existing secrets transfer internally through the builder's secret choices;
  callers receive no secret material and need not reconstruct fields or inspect Heads.
- Later explicit field setters may modify the selected value. The field-composed
  merge API remains supported.
- Publication occurs only on save, through the existing path. AdditionalConflict
  still publishes nothing when new relevant information appears, and
  PublicationUncertain retains the existing exact retry behavior.

### Compatibility and scope

No Java production API or behavior change from 0.1.2. No protocol revision,
wire/storage format, cryptography, graph, fold, TOTP, conformance vector or
external dependency change. Totipo Vault Format v1/r18 remains pinned to
`4623a7e1718e23504903096c92332597057bd8f0`. Artifact coordinates advance to 0.1.3.

Existing operation-scoped core/store qualification and application responsibilities
in README, API_DESIGN and SPEC_PIN remain unchanged. No expanded conformance,
platform, secure deletion or interoperability claim follows from this release.

### Validation

The preparation report records local validation. The protected release workflow
must revalidate the reviewed commit, normal/offline suites, all 90 portable cases,
Java 17 production bytecode, artifact inventories and both consumer modes before
publication. Exact remote bytes and fresh Central consumers remain required before
the source tag and GitHub release are created.

See [the 0.1.3 preparation report](V0_1_3_RELEASE_PREPARATION_REPORT.md) and
[the whole-Alternative API review](WHOLE_ALTERNATIVE_KEEP_REPORT.md).

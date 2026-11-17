# Current release documentation review

Starting HEAD: `af11553659dd8bd4157064c7e3de27bed8980ac5`, clean `main`.
Classification: `DOCS_JAVADOC`, Markdown-only current-release guidance. No source
Javadocs, executable contracts, build inputs or release operations changed.

## Findings and changes

- README dependency examples and Central links still selected 0.2.0 even though
  VERSION is 0.3.2. Updated both examples and both POM links to 0.3.2.
- README omitted encrypted backup, additive import and detached new-location
  restoration. Added a short feature paragraph linking to the existing detailed
  contracts. Following user review, kept ownership, retries, partial progress and
  compatibility details in the linked guides/notes. README retains upgrade links,
  including historical 0.1.x migration guidance, and the 0.3.2 release-notes link.
- API_DESIGN described the release and operation model as 0.2.0. Updated release
  status and compatibility context; removed obsolete version qualifiers from
  current scheduling/observation guidance without changing its contracts.
- SPI_DESIGN's StoreFailure table omitted CAPACITY, already documented in its
  bounded-enumeration prose and present in the production enum. Corrected the table.
- SECURITY called the library pre-release. Clarified published availability and
  retained experimental API/SPI and qualification limits, without changing private
  vulnerability-reporting instructions.

Reviewed root guides, docs/, backup fixture documentation and release notes against
VERSION, current API documentation and production StoreFailure. Historical review
reports, versioned release notes and the vendored specification remain unchanged.
Remaining 0.2.0 references in current guides describe upgrade history.

## Validation and accounting

Read-only canonical Maven Central requests on 2026-11-17 returned both module
metadata documents and both 0.3.2 POMs successfully. Both metadata documents list
latest/release 0.3.2 (lastUpdated 20261116055818). Core's sole external dependency
is Bouncy Castle 1.86 at runtime; NIO depends on core 0.3.2 at compile scope:

- [Core metadata](https://repo.maven.apache.org/maven2/org/totipo/totipo-core/maven-metadata.xml)
- [NIO metadata](https://repo.maven.apache.org/maven2/org/totipo/totipo-storage-nio/maven-metadata.xml)
- [Core POM](https://repo.maven.apache.org/maven2/org/totipo/totipo-core/0.3.2/totipo-core-0.3.2.pom)
- [NIO POM](https://repo.maven.apache.org/maven2/org/totipo/totipo-storage-nio/0.3.2/totipo-storage-nio-0.3.2.pom)

The web reader could not access these XML URLs; curl retrieved them successfully.
An initial Python retrieval attempt could not start because Python is unavailable.
Neither issue required source changes or a qualification rerun.

Reviewed the final diff, checked local Markdown link destinations and relevant
heading anchors, and passed `git diff --check`. Inspected qualification.nix's
explicit files/trees and flake.nix's inputs: all five changed paths are excluded.
No included qualification input changed, so no previous qualification is invalidated.

Invocation counts for this milestone:

| Validation | Invocations | Reason |
| --- | ---: | --- |
| Broad Gradle baseline | 0 | Excluded Markdown prose only |
| Javadoc | 0 | No source/Javadoc changes |
| Focused Gradle tests | 0 | No executable contract change |
| Focused standalone tooling tests | 0 | No tooling change |
| Final full Java gate | 0 | Excluded Markdown prose only |
| Publication/consumer passes | 0 | No artifact or resolution change |
| Separate host offline passes | 0 | No dependency/build/resolution change |
| Human Nix runs | 0 | Excluded Markdown prose only; no run requested or claimed |
| Agent Nix runs | 0 | Prohibited by repository policy |

## Final Git state

Branch and HEAD remain `main` at the starting commit. The report is Git-known via
intent-to-add; actual file contents remain unstaged. No generated output, cache,
temporary evidence or secret was added. `git diff --cached --quiet` passes.

`git status --short`:

```text
 M API_DESIGN.md
 M README.md
 M SECURITY.md
 M SPI_DESIGN.md
 A review/CURRENT_RELEASE_DOCUMENTATION_REVIEW.md
```

`git diff --name-only`:

```text
API_DESIGN.md
README.md
SECURITY.md
SPI_DESIGN.md
review/CURRENT_RELEASE_DOCUMENTATION_REVIEW.md
```

`git diff --cached --name-only` emits nothing. Changes are uncommitted for review.

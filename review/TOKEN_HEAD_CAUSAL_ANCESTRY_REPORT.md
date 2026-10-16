# TokenHead causal ancestry report

## Starting point and scope

- Repository: totipo-org/totipo-java.
- Starting branch: `main`.
- Starting HEAD: `03afb32fc82226209cacd7f6e2ac9136bab51552`.
- Starting working tree: clean; no staged content.
- Classifications: `CORE_RUNTIME`, `PUBLIC_API`, `DOCS_JAVADOC`.
- Reviewed AGENTS.md, API_DESIGN.md (including operation/state-snapshot rules),
  SPI_DESIGN.md, SPEC_PIN.md, application session/state/head implementations,
  TokenGraph/TokenFold/publication planning and existing graph/fold, metadata,
  projection and session tests. No dependency/build, SPI or release changes.

## Public contract and internal design

`TokenHead.parents()` returns an immutable list of validated, resolved immediate
same-token parents in canonical revision order. Every historical head exposes its
original token ID, revision, exact client metadata and its own parents. Traversal
preserves actual branches, merges, resolution/fold revisions, equal semantic values
with distinct metadata and tombstones. It performs no provider I/O.

CausalFact now retains exact non-secret ClientMetadata and an opaque existing value
key alongside token identity and parent IDs. Observation constructs one immutable
History map per token, with one Head per validated available revision. Immutable
parent views resolve solely within that sealed map. Shared ancestors are identical
objects within an observation. Construction is iterative over facts and does not
follow edges recursively, even for defensive cycles. The graph map is assigned
before publication through the existing synchronized/volatile state machinery;
there is no lazy mutation. Traversals should track visited heads.

Heads use a dedicated identity marker rather than retaining ApplicationSession.
Equality/hash remain precisely session + logical token + revision; metadata,
ancestry, value key and originating state do not affect them. Equal revisions in
later observations need not have identical Java object references. Existing
same-session `update(head)` works for historical heads; foreign-session references
remain rejected. No new mutation method or secret-export capability was added.

Historical metadata preserves absent/present-empty names, exact Unicode and all
unsigned time bits. Existing parameterized metadata tests now also check metadata
through historical parent traversal. History retains no complete TokenValue,
decrypted secret or encrypted payload. Opaque value keys support the existing
update contract against the unchanged session-owned value table. Existing value
interning and best-effort close-time secret wiping remain unchanged.

## Partial ancestry and snapshots

Missing, invalid, unauthenticated and wrong-token references do not produce heads.
TokenGraph already reports unresolved edges for every available validated revision,
including historical children. No diagnostic behavior correction was necessary;
TokenState Javadoc now makes this scope explicit. Pair the corresponding state's
unresolvedReferences with observation progress and diagnostics. A revision with no
available parents need not be a root. No API can guarantee that an unavailable
revision never existed; unobserved ancestors beyond a gap cannot be enumerated.

Refresh builds a new graph from that observation, without filling gaps in captured
graphs or reviving disappeared revisions from old observations. Captured heads,
metadata, identity and ancestry remain descriptively readable after refresh and
session closure. A head retains only its identity marker and immutable descriptive
graph, with no path to an active session/store through the history implementation.
Existing State/Alternative ownership and operation freshness semantics are unchanged.

## Memory and performance

Construction and retained graph size are O(V + E) per token observation. Historical
metadata is retained once per revision per observation and shared with causal facts;
heads and alternatives share the same graph nodes. `parents()` returns the same
immutable list each time; indexed parent lookup uses the immutable map and creates
no head or list. Common ancestors are never copied per current head or path.

Keeping one head keeps that token observation's entire graph, including other
branches. Keeping many states across refreshes can retain multiple O(V + E) graphs.
There is no cross-observation cache or historical-secret retention added. Closing
releases the session's causal index/value tables under the existing cleanup rules;
externally captured descriptions survive. This explicit retention tradeoff keeps
snapshot semantics simple and avoids a caching framework.

Measurements on the locked Nix-provided JDK 25, without running Nix:

- Permanent synthetic construction test: 50,000 revisions / 49,999 edges;
  focused run 84,236,304 allocated bytes and 78.2 ms; final full run
  85,736,496 allocated bytes and 110.3 ms. Allocation includes temporary
  revision formatting/validation and map construction, not just retained objects.
- An external temporary Java instrumentation agent measured the complete reachable
  object graph using Instrumentation.getObjectSize, identity-based iterative walks,
  and reflective access to java.lang/java.util. The synthetic 50,000-revision linear
  fixture uses distinct parent ObjectIds, absent per-revision ClientMetadata, and
  one shared token ID. One captured head retained 21,600,144 bytes; graph plus
  causal fact map retained 26,932,240 bytes. A matching previous-layout causal map
  retained 11,332,128 bytes: approximately 15,600,112 additional bytes, or 312 bytes
  per revision. This isolates descriptive retention, without credentials, store,
  session value tables or ciphertext. Nonempty metadata strings add their own size.
- These are local JVM measurements, not portable heap-size or latency guarantees.
  The temporary probe lives outside the repository and adds no dependency or
  production profiling framework. Structural tests verify single-node sharing;
  retained observations can still multiply graph memory as described above.

## Tests and compatibility

New synthetic-vault API tests cover linear A/B/C exact identities and metadata,
replacement of source-marker metadata, tombstones, historical update and foreign
session rejection, equal-valued branches and merge with shared ancestors, canonical
parent order, historical and immediate gaps, corrupt evidence, incomplete scans,
late parent availability, captured snapshots, fold-generated histories, and the
four-accessor descriptive-only TokenHead boundary. Internal tests cover 50,000
revisions, one node per revision, repeated list reuse, defensive cyclic traversal,
and identity independent of metadata/ancestry/value keys. Existing application
operations and pinned r19 graph/fold/conformance suites remain part of the final gate.

No wire-format, TOKEN grammar, identity, crypto, protocol validation, conflict,
publication, storage SPI, dependency, lock, verification, Java 17 target or spec
snapshot changes. The application interface gains one abstract method; external
custom implementations must implement it. There is no API stability promise yet.

## Validation accounting

Supported JDK 25 / Gradle 9.8.0 were already available from the locked Nix store;
no agent Nix invocation or host-toolchain fallback was used. No prebuilt publication
launcher was installed in this workspace, so tooling main sources were independently
compiled with direct JDK 25 javac, --release 25, -Xlint:all -Werror, -proc:none and
only pinned Jackson Core 2.21.7 on the classpath. The executable launcher and all
transient evidence are under `/tmp/totipo-ancestry-evidence/`, outside source
membership. Gradle never compiled the independent tooling. No tooling source changed,
so the tooling-change-only permanent standalone guardrail suite was not required.

Invocation counts and results:

| Layer | Invocations | Result |
| --- | ---: | --- |
| Broad baseline `gradle test` | 1 | Passed; 405 JUnit tests, zero failures/errors/skips, snapshot 3, profile 1, exact 92/92 case assertion. |
| Focused Gradle tests | 4 | First three stopped at compilation; fourth passed 86 tests across PublicApiTest, TokenAncestryTest and ApplicationHistoryTest. |
| Focused standalone tooling tests | 0 | No tooling changes. |
| Final `gradle test build javadoc verifyPublication consumerSmoke` | 1 | Passed; 413 JUnit tests, zero failures/errors/skips, snapshot 3, profile 1, exact 92/92 case assertion. |
| Full-suite standalone test-results accounting | 2 | Baseline and final passed, immediately preserving XML before replacement. |
| Publication semantic acceptance | 1 | Final Gradle invocation accepted 10/10 artifacts; also a dependency of consumerSmoke, not a second invocation. |
| Publication manifest recheck | 1 | Standalone publication check accepted 10/10 current artifacts. |
| Module-metadata consumer | 1 | Nested isolated forced-offline consumer passed compile/runtime and boundary checks. |
| Independent POM-only consumer | 1 | Passed in 4 seconds; all four actionable tasks executed, including compile/runtime and boundary checks. Required offline clean/check is scoped to the consumer. |
| Separate host forced-offline full suite | 0 | No dependency acquisition/build/resolution/cache changes requiring one. |
| Human Nix runs | 1 | `nix flake check` passed, as reported by the human after Java qualification. The agent did not run Nix. |
| Release artifact reproducibility | 0 | Routine application milestone, not release preparation. |

Focused compile attempts found and corrected: an unqualified static revision helper
shadowed by Head.revision(), Thread.threadId() unavailable under Java 17 --release,
and the repository's -Werror unused try-resource check in the new fixture. No tests
executed in those failed attempts, so no failing test XML existed to preserve.
Baseline XML/accounting was preserved before focused runs; focused XML was preserved
before the final full run. Final test assertions were strengthened after the
successful focused compilation (metadata traversal in existing parameterized tests,
and canonical order/explicit missing-child selection); the final full run compiled
and exercised their final bytes. No included source inputs changed afterward.

Final Gradle task accounting: 27 actionable tasks, 19 executed and 8 up-to-date;
core and storage-nio test tasks both executed. Compilation/archive reuse does not
represent extra suite executions. Javadocs completed with missing-comment warnings
in the existing minimally documented API (100 core / 85 storage-nio warnings); no
Javadoc failure. Java 17 bytecode guard tasks passed. Nested consumer tasks are
counted separately from the parent Gradle invocation. Normal publication acceptance
is not the release-only normal/offline ten-artifact reproducibility procedure.

Logs: baseline-accounting.txt, final-java.log, final-accounting.txt,
final-manifest-check.txt, pom-only.log, retained-heap.txt and preserved suite XML
in the temporary evidence directory. No locks, verification metadata or cache inputs
were updated.

## Final Git state and handoff

Inspected qualification.nix's explicit source filter and flake.nix inputs. All
changed production/test Java files are included. API_DESIGN.md and review/ are
excluded; report-only finalization does not invalidate compiled artifacts or Nix.
Included inputs are frozen at the successful final Java gate. New tests and this
report are Git-known via intent-to-add. No actual file contents are staged.
No commit, tag, push, release or dependency update was performed. The human reported
that `nix flake check` passed; the agent performed no Nix operation. Only this
excluded report was updated afterward, preserving the qualified included inputs.

Final `git status --short`:

```text
 M API_DESIGN.md
 M core/src/main/java/org/totipo/TokenHead.java
 M core/src/main/java/org/totipo/TokenState.java
 M core/src/main/java/org/totipo/format/ApplicationSession.java
 M core/src/test/java/org/totipo/api/PublicApiTest.java
 A core/src/test/java/org/totipo/api/TokenAncestryTest.java
 A core/src/test/java/org/totipo/format/ApplicationHistoryTest.java
 A review/TOKEN_HEAD_CAUSAL_ANCESTRY_REPORT.md
```

`git diff --name-only` lists exactly those eight paths. `git diff --cached --name-only`
is empty, and `git diff --cached --quiet` passes. `git diff --check` passes.
Starting HEAD and branch remain unchanged. Intent-to-add entries remain for human
review/commit. The final human gate passed, as reported by the human:

```sh
nix flake check
```

Known limitations are partial observation/history, inability to infer never-observed
revisions beyond gaps, defensive cycles requiring visited sets, whole-token graph
retention by a captured head and per-observation duplication when many states are
retained. Publication retains the repository's accepted residual packaging risk:
whole-artifact hashes/metadata/consumers do not compare JAR members to source/class
inventories. No live release authorization is implied.

# Object candidate validation API review

## 1. Starting state

Work began on `main` at `44332d459e0cbec74e93ea5fb77280596c505732`, after
release 0.1.4. The agent personally ran `git branch --show-current`,
`git rev-parse HEAD`, and `git status --short` before editing; status was empty.
VERSION is 0.1.4. No applicable AGENTS.md was found.

Inspected API_DESIGN.md, SPI_DESIGN.md, VaultSession/VaultState and projections,
Totipo/ApplicationVaults open/create, StoreAdapter, the envelope/crypto and TOKEN
codecs, bounded object reading, graph evaluation, session locking/close, existing
malformed/authentication/identity/graph tests, publication and release checks.
The authoritative local v1/r18 snapshot is pinned to spec commit
`4623a7e1718e23504903096c92332597057bd8f0`. Separate current totipo-spec and Android
checkouts/reports were not available in this workspace; Android requirements
come from the supplied task and existing Java private-replica report. No external
repository was modified or newer upstream normative semantics assumed.

## 2. Android reconciliation requirement

A private canonical replica must remain distinct from externally collected
candidate representations. The bridge needs to authenticate and validate bytes
before controlled import, using the unlocked session without another password
prompt. Missing/unavailable transport reads remain an application observation;
they cannot be validated as byte representations. Duplicate representations,
malformed/truncated representations and conflicting authenticated content must
be distinguished without moving protocol interpretation into Android.

## 3. Existing validation pipeline

| Level | Existing mechanism | What is established |
| --- | --- | --- |
| Candidate identity and bytes | StoreAdapter / ObjectId; BoundedObjectRead | Canonical lowercase 64-hex direct-child ID; bounded read, not authenticity |
| Structural envelope admission | EnvelopeReader | Exactly 1024 ciphertext/tag bytes; no separate public header or family discriminator |
| Cryptographic authentication | EnvelopeReader / CryptoSupport | ID-derived key, nonce and AAD; AES-GCM successful before any plaintext parsing |
| Decrypted framing | EnvelopeReader | 1008 padded bytes, semantic length at most 1006, zero padding |
| Keyed identity | EnvelopeReader / ObjectId | Recomputed keyed ID matches the supplied canonical name |
| Complete semantic TOKEN | TokenReader / TokenObject / TokenValue | Exact TLV order/width/UTF-8, parent order/count, field bounds, optional exact metadata; maximum legal TOKEN 1005 bytes |
| Graph facts and current state | TokenGraph then ApplicationSession.observePass | Duplicate collapse, contradiction exclusion, same-token ancestry, unresolved parents, SCCs/current heads and semantic alternatives |

An authenticated envelope alone can still have invalid padding, keyed ID or
TOKEN grammar. A valid TOKEN alone proves neither graph relevance, a current
head, history completeness, freshness nor persistence. Missing parents do not
invalidate a complete child. TokenGraph detects contradictory identity/content
facts across its input set, not through single-object envelope validation.

The v1/r18 spec §13 explicitly has no alternative semantic dispatch within
objects-v1. Unknown siblings/namespaces are discovery policy, not a future-family
object accepted by this parser. Invalid grammar remains invalid.

## 4. API alternatives considered

* Existing API only: isolated stores and password reopening work but unnecessarily
  repeat authentication/KDF and add temporary ownership machinery. No clean live
  session candidate API existed.
* Root export or a public parser/crypto context: rejected; they enlarge custody
  and let integrations reconstruct protocol logic.
* Derived validator: unnecessary lifecycle/key-holder complexity. A session method
  already has the required authority and serialization gate.
* Return complete plaintext/TOKEN/parents: unnecessary for candidate comparison;
  it would expose existing secrets and introduce long-lived plaintext ownership.
* Return only valid/invalid: workable with careful caller snapshots, but a copied
  validated ciphertext result binds the compared/imported bytes to the validated
  snapshot and simplifies defensive ownership.
* Stateful local comparison/import: unnecessary and would couple validation to
  observation/history/provider policy. Validate local and incoming bytes independently.
* Unsupported or contradiction result on a single call: unsupported has no current
  meaningful grammar classification; contradiction needs two validated facts.

## 5. Security/lifecycle analysis

* **Root custody:** the existing ApplicationSession owns the only retained root
  for this capability. No key type or root bytes become public, and no new root
  holder or root-derived public comparison hash is created.
* **Plaintext:** the existing parser temporarily creates bounded TOKEN values.
  Envelope and semantic byte arrays are cleared through the shared reader;
  candidate-only parsed secrets are cleared in a finally block. A small parser
  cleanup fix also clears the copied secret when TokenObject intrinsic validation
  throws. Temporary immutable issuer/account/metadata strings and JCA copies
  cannot be reliably wiped; JVM wiping remains best effort. No plaintext or
  semantic model is retained by the result or interned into session history.
* **Lifecycle:** synchronous calls use the existing gate and open checks. Close
  marks closing before waiting, rejects queued/new entrants and waits for admitted
  validation before root wiping. An admitted call may finish while close waits.
  Ciphertext results can outlive the session; no validator or key holder does.
* **Malicious inputs/bounds:** only 1024 bytes are copied or processed. Other sizes
  return Invalid before copy/crypto, including oversized arrays. Caller allocation
  of ingress occurs before the library call; Android must bound its own reads.
  TLV allocations and parents are constrained by the existing bounded parser.
* **Repeated work/DoS:** each call has bounded input/CPU/storage retention and no
  KDF, but unlimited repeated calls still consume CPU and can contend with the
  operation gate. No cache, rate limiter or unbounded asynchronous queue is added;
  applications control scheduling, batch size and aggregate work off UI threads.
* **Exceptions/oracle:** candidate authentication, wrong root, padding, keyed ID
  and grammar failures collapse to Invalid. Null/invalid RevisionId are programmer
  errors. Crypto-provider unavailability retains the existing sanitized runtime
  convention; internal crypto exceptions do not escape. No constant-time whole
  validation guarantee is made. The valid/invalid oracle adds a convenient means
  of using already authenticated vault authority, without increasing key or
  plaintext access beyond opening the vault. It should remain an unlocked-session
  operation rather than an unauthenticated externally exposed service.
* **Concurrency/isolation:** gate serialization covers validation, scans, writes,
  password changes and cleanup; no lock-order change. Validation performs no
  provider I/O, graph evaluation, emissions, refresh request, value interning,
  durable writes, history registration or lifecycle transition. Existing private
  replica application serialization obligations still apply across bridge/store
  operations. Validation can wait behind unrelated provider I/O.
* **Comparison integrity:** deterministic encryption and canonical zero padding
  bind complete canonical plaintext to exact ciphertext under the same root/ID.
  Results from the same root with equal IDs but unequal validated ciphertext
  establish the defensive contradiction case. Changed unauthenticated bytes do
  not. No impossible encrypted HMAC collision fixture or invented contradiction
  state is introduced. Results are descriptive and publicly constructible, not
  unforgeable trusted import credentials; only a session call proves validation.

## 6. Decision

**IMPLEMENTED** — a protocol-neutral additive session API is sufficient. It
requires neither spec clarification nor a protocol change. Password re-entry is
avoided; root ownership and state neutrality remain intact.

## 7. Public API

Method: `VaultSession.validateObject(RevisionId objectId, byte[] representation)`
returns `ObjectCandidateValidation`, a sealed interface whose outcomes are:

* `Invalid()` — no retained candidate content.
* `Valid(RevisionId objectId, byte[] representation)` — exact validated snapshot.

RevisionId is the existing canonical OBJECT_ID type (also used for TOKEN heads).
The method validates every current immutable semantic family: in v1/r18 this is
TOKEN alone. It does not validate VAULT or interpret other namespaces.

Valid contains the exact ID and complete 1024-byte opaque ciphertext. Constructor
and accessor defensively copy; equals/hashCode compare ID and bytes by content.
No complete plaintext, semantic token value, parent IDs, plaintext metadata or
diagnostic details are returned. Exact metadata remains preserved in the exact
ciphertext. toString redacts representation bytes. The caller must not modify
input during the synchronous call; the library never mutates or retains the caller
array. Results remain immutable/readable after close, without session references.

Library-created sessions implement the method. Its default throws
UnsupportedOperationException for external implementations lacking support,
preserving their existing interface implementation compatibility. Closed library
sessions throw SessionClosedException, even for wrong-length input.

Example of the comparison boundary:

```java
var local = session.validateObject(id, boundedLocalBytes);
var incoming = session.validateObject(id, boundedIncomingBytes);
if (local instanceof ObjectCandidateValidation.Valid a
        && incoming instanceof ObjectCandidateValidation.Valid b) {
    boolean identical = a.equals(b);
    // Same id and same authenticated vault root: !identical is an integrity
    // contradiction. Exclude/quarantine both; do not choose a representation.
}
```

A single candidate call does not compare against previously observed state.
There is no special known-local lookup: current heads and retained causal facts
are not a complete authenticated object representation cache.

## 8. Internal reuse

TokenStoreReader.validate extracts the existing envelope → semantic reader path
without duplicating crypto, padding, keyed identity or grammar checks. Both store
observation and candidate validation call it. Existing observation diagnostics
still distinguish INVALID_STORAGE from INVALID_TOKEN. ApplicationSession adapts
its result to the public two-outcome model, then clears the parsed candidate
secret. Store observation retains parsed models for its unchanged graph/projection
path. No broad parser/public crypto refactor was needed.

## 9. Tests

Four new focused test methods cover core API-authored TOKENs, matching identity,
wrong supplied ID, different root, physical sizes 0/1/87/1008/1023/1025/100000,
AEAD corruption, authenticated malformed semantic length, nonzero padding,
post-AEAD keyed-ID mismatch, and the encoding corpus's authenticated grammar and
bounds cases. Existing fixtures/writer create representations; crypto is not
reimplemented in these tests.

Tests also cover identical duplicates, content equality/hash, exact metadata
comparison, ingress and accessor independence, caller-array nonmutation, repeated
validation, unchanged state identity/current heads and store scan/write counters,
absent-local candidate validation without import, concurrent callers and close,
post-close rejection and result readability. Existing symbolic TokenGraph same-ID
contradiction tests remain the appropriate defensive collision tests.

An initial state-neutrality assertion raced an independently requested extra
refresh. The fixture was corrected to await only the save's automatic observation;
focused tests then passed. ConsumerSmoke now compiles the candidate method using
published artifact dependencies, in addition to existing runtime/boundary checks.

## 10. Compatibility/version recommendation

The API adds one default interface method and one sealed result interface with
Valid/Invalid records; all previous public methods remain. A manual javap inventory
comparison preserved all six old VaultSession signatures. An external session
compiled against HEAD's original interface links against the changed classes,
can still call old methods, and inherits the safe UnsupportedOperationException
default for the new method. Existing application callers are unaffected; providers
need no SPI changes. Java 17 target and existing dependencies remain unchanged.

Recommend **0.1.5** for the next pre-1.0 release containing this additive capability.
VERSION remains 0.1.4, and no release has been prepared or performed. Released
0.1.4 consumers do not yet have this method; Android must consume the shared Java
change or a subsequent approved release.

## 11. Protocol impact

None. Wire bytes, key derivation, envelope/parser acceptance, keyed identity,
TOKEN semantics, graph/current-state evaluation and VAULT behavior are unchanged.
The spec pin, snapshot, profiles and all vectors are unchanged. No sync/provider
concepts, transport metadata or import operation were added to the Java API.

## 12. Validation

### Agent

All agent checks passed on the final code:

| Check | Result |
| --- | --- |
| `./gradlew clean test build verifyPublication consumerSmoke` | PASS; 2m 7s |
| `./gradlew --offline --no-daemon --no-build-cache --rerun-tasks clean test build verifyPublication consumerSmoke` | PASS; 1m 44s; all 30 tasks executed |
| `python3 -B -m unittest discover -s publishing -p test_release.py` | PASS; 21 release guardrail tests |
| `python3 publishing/release.py tests` | PASS; 528 JUnit tests, zero failures/errors/skips; 90/90 portable cases; snapshot 3/3 and profile 1/1 |
| `python3 publishing/verify-publication.py` | PASS; both modules, exact binary/source/Javadoc inventory, POM/module metadata, checksums and Java 17 |
| `./gradlew -p publishing/consumer-smoke --offline -PpomOnly clean check` | PASS; POM-only compile/runtime dependency and repository boundary |
| Wrapper JAR SHA-256 | PASS; matches CI's `238e777fcddd7e34f9708186085def2abd6e08e658505b38718d79d74c21abd5` |
| Manual javap/API inventory and old-compiled session runtime probe | PASS; six old signatures preserved, one default method added, external session links safely |
| Application API source inventory | PASS; existing top-level types unchanged except additive VaultSession method |
| VERSION / SPEC_PIN comparison with HEAD | PASS; unchanged |
| `git diff --check` | PASS |
| `git diff --cached --stat` | Empty |

The build emitted non-failing missing-Javadoc warnings; Python 3.14 emitted
ResourceWarnings from mocked HTTPError cleanup in existing release tests. No
errors/skips were suppressed. No Nix command was run. Gradle publication
verification uses only the repository's local unsigned staging repository; no
external publication occurs. These local staging artifacts still use unchanged
VERSION 0.1.4 and are verification outputs, not a replacement release.

### Human Nix

PASS — the user reported that `nix flake check` passed in totipo-java. This
is user-reported evidence; the agent did not run Nix or capture its output.
The current flake supplies development shells/formatter rather than a Gradle
check derivation; this check validates the repository's Nix outputs. No duplicate
`nix develop` Gradle run was requested. Agent and human Nix validation are complete;
remote CI remains unrun for these uncommitted changes.

### Remote CI

Not run for these uncommitted changes. Nothing was pushed or dispatched. Existing
CI covers build/publication/consumer/Python checks; local equivalents are run here.

## 13. Android follow-up

Android can obtain bounded candidate bytes and a canonical OBJECT_ID, invoke
validateObject on its open session off the UI thread, reject Invalid candidates,
and retain the immutable Valid ciphertext snapshot for controlled import or
comparison. Validate local representations with the same session before comparing.
Exact duplicates collapse by value equality; two validated representations under
the same root and ID that differ must be excluded/quarantined as an integrity
condition. Transport read unavailability stays distinct from invalid bytes.

Import remains a separate application-controlled private-replica bridge action
subject to exclusive ownership/serialization and persistence certainty. Use the
validated result's defensive representation copy for that later action, then let
ordinary Java observation evaluate graph/current heads. Validation itself does
not import, assess freshness, establish completeness or select heads. No Android
TOKEN/envelope/crypto/graph implementation is required.

VAULT wrapper reconciliation is a separate future API design question: wrappers
are password-wrapped, may differ for one root and have separate lifecycle rules.
This API neither solves nor implicitly authorizes VAULT replacement.

## 14. Final Git state

The agent ran final `git diff --check`, `git status --short`, `git diff --stat`,
`git diff --cached --stat`, branch and HEAD checks. The branch remains `main` and
HEAD remains `44332d459e0cbec74e93ea5fb77280596c505732`. The index is empty of
changes. Six tracked files are modified and four new files are untracked
(including this report); ordinary git diff statistics exclude the new files.

```text
 M API_DESIGN.md
 M core/src/main/java/org/totipo/VaultSession.java
 M core/src/main/java/org/totipo/format/ApplicationSession.java
 M core/src/main/java/org/totipo/format/TokenReader.java
 M core/src/main/java/org/totipo/format/TokenStoreReader.java
 M publishing/consumer-smoke/src/main/java/ConsumerSmoke.java
?? core/src/main/java/org/totipo/ObjectCandidateValidation.java
?? core/src/test/java/org/totipo/api/ObjectCandidateValidationTest.java
?? core/src/test/java/org/totipo/format/ObjectCandidateBoundaryTest.java
?? review/OBJECT_CANDIDATE_VALIDATION_API_REPORT.md
```

Changes remain unstaged and uncommitted. No staging, commit, tag, release, push,
remote publication or modification to another repository was performed.

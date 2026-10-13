# Totipo Java release checklist

**J4B is implemented for review, with live mutations disabled.** The procedure
below is the intended experience after a separately authorized activation change.
Merging these workflows does not activate tag creation, signing, Central upload
or GitHub Release creation.

## Successful release after activation

1. Prepare `VERSION` (exact version plus LF), both Totipo self-coordinates in
   `publishing/consumer-smoke/gradle.lockfile`, and
   `review/V<version-with-dots-as-underscores>_RELEASE_NOTES.md`. Notes begin with
   `## v<version>` and contain completed Highlights, Compatibility and scope, and
   Validation sections. Review source metadata and README/API release status.
   Keep the exact r19 specification pin and complete snapshot intact.
2. Run the applicable [qualification ladder](AGENTS.md#milestone-qualification-ladder)
   and the separate release reproducibility procedure below. Review and commit
   the intended release source through the normal review process.
3. Confirm ordinary CI succeeds for that commit.
4. Open GitHub Actions → **Release**, select `main`, enter the version and its
   exact lowercase 40-character reviewed commit SHA, then start the workflow.
5. Expect qualification, first-time annotated tag reservation, signing, one
   Gradle Maven Central publication invocation, independent Central verification,
   both Central consumers, and automatic GitHub Release creation. Intervene only
   on failure. No manual tag, Portal promotion or GitHub Release creation is needed.

Initial dispatch SHA must equal the requested source. Subsequent checks require
that exact source to remain reachable from freshly acquired canonical `main`;
normal development may advance main during publication or later verification.
Source and artifact identity always remain bound to the original commit.

The tag `v<version>` is an unsigned annotated tag at the reviewed commit, with
annotation `totipo-java <version>` followed by LF. It permanently reserves the
version **before** signing/upload. Existing tags always prevent ordinary
publication, even on a new dispatch at attempt 1 and even if both Central POMs
return 404. Workflow and publishing-job reruns cannot authorize publication.

The accepted guarantee is **at most one authorized Gradle Central publication
invocation per version**, not exactly-once HTTP transmission or Sonatype delivery.
Vanniktech 0.37.0 / OkHttp 5.1.0 may internally retransmit within that invocation.
That behavior is accepted; retransmission is not asserted harmless or idempotent.
There is no workflow retry wrapper, upload recovery mode or manual override flag.
Ordinary Gradle configuration exposes unsigned local staging only.

## Verification-only recovery

Open GitHub Actions → **Verify Release**, select `main`, enter the **original**
version and reviewed source SHA, and start verification. Never invoke Release
merely to retry verification.

This fresh run authenticates the existing annotated tag and source, bootstraps the
same pinned tools, independently rebuilds and accepts all ten unsigned artifacts,
checks Central bytes and all ten detached-signature files, executes both remote
consumers with distinct fresh external Gradle homes, and creates or validates the
GitHub Release. It needs no earlier Actions artifact, Maven credentials, signing
secret or `release` environment. It cannot upload to Central or create a missing tag.
While J4B is inert, read-only verification can run but GitHub finalization is skipped.

| Failure | Maintainer response |
| --- | --- |
| Before tag creation | Investigate and correct source/environment; review and commit source corrections before another dispatch. |
| Tag reserved, including when no upload started | Ordinary publication cannot restart. Investigate; never move/delete the reservation or infer upload permission from 404. |
| Failed, cancelled, timed-out or uncertain upload | Inspect Central Portal and run evidence manually. No second publication invocation is authorized. Use verification-only if publication may have completed. |
| Verification timeout or temporary consumer failure | Investigate, then explicitly start Verify Release again with the original identity. |
| GitHub Release failure/uncertainty | Explicitly repeat Verify Release. An exact existing release succeeds without mutation. |
| Conflicting tag, artifact bytes or GitHub Release | Stop; automation never repairs, overwrites, moves or deletes them. |

Central verification polls for at most 40 minutes, with requests bounded to 20
seconds and backoff from 10 to 60 seconds. Only 404 is pending. Incorrect hashes,
malformed/oversized evidence, network errors and unexpected statuses fail closed.
All ten adjacent `.asc` files need both ASCII armor markers; this is signature
presence evidence, not independent cryptographic validity or signer trust.
Both remote module-metadata and POM-only consumers must pass before finalization.
Their Central-only repositories prohibit local fallback/source substitution and
require exactly NIO/core plus Bouncy Castle 1.86 at runtime.

GitHub finalization independently derives notes/provenance from reviewed source.
It requires an existing authenticated tag, exact title/tag, published/non-prerelease
state and matching reviewed body under established CRLF/trailing-whitespace
normalization. It creates an absent release once and validates readback; an exact
existing release is unchanged. Ambiguous creation stops; a later explicit run may
observe and accept the exact release.

## Qualification and release reproducibility

The final human-owned routine hermetic gate remains exactly:

```sh
nix flake check
```

Agents do not execute Nix. Use Git-aware source membership, intent-to-add for every
intended new source/report, no staged contents, and freeze included inputs before
this gate. Dependency/cache updates follow the separate AGENTS policy; release
simplification does not itself authorize dependency or lock updates.

Publication verification uses the prebuilt independent Java launcher, compiled
with pinned JDK 25 and Jackson Core 2.21.7, independently of Gradle/production code.
A human can materialize it with `nix build .#tooling --no-update-lock-file --no-write-lock-file`
and set `TOTIPO_PUBLICATION_TOOL="$PWD/result/bin/totipo-java-tool"` before direct
host Gradle publication checks. This is tooling materialization, not a second
qualification gate. In CI the pinned runtime supplies the launcher and JDK.

For explicit release-source preparation, retain the normal/offline comparison;
preserve full XML/accounting before any clean or focused run replaces it:

```sh
./gradlew --no-daemon --no-build-cache --no-configuration-cache clean test build javadoc verifyPublication consumerSmoke
"$TOTIPO_PUBLICATION_TOOL" release test-results "$PWD"
# Copy manifest and full XML to an external evidence directory before the next run.
cp build/publication-manifest.json /tmp/totipo-normal-publication-manifest.json
./gradlew --offline --no-daemon --no-build-cache --no-configuration-cache --rerun-tasks clean test build javadoc verifyPublication consumerSmoke
"$TOTIPO_PUBLICATION_TOOL" release test-results "$PWD"
EXPECTED_INVENTORY="$(cat /tmp/totipo-normal-publication-manifest.json)" \
  REQUESTED_VERSION="$(cat VERSION)" REQUESTED_COMMIT="$(git rev-parse HEAD)" \
  "$TOTIPO_PUBLICATION_TOOL" release compare "$PWD" "$PWD/build/repository" "$PWD/build/publication-manifest.json"
./gradlew -p publishing/consumer-smoke --offline -PpomOnly clean check
```

Require all ten hashes to match: binary/source/Javadoc JARs, POMs and module
metadata for both modules. Each build's staging fingerprint must validate locally;
do not compare ephemeral Maven repository indexes across builds. Do not normalize
or omit differing artifact bytes. Retain full corpus accounting (92/92, no skips),
snapshot/profile accounting (3/3 and 1/1), all 99 snapshot hashes, Java 17 output,
locks/strict verification, independent publication validation and both local consumers.
The release preflight repeats these gates on the exact committed source.

## Owner-operated settings and separate activation

These are owner-owned prerequisites; public unauthenticated observations do not
prove effective privileged authorization. J4S found the intended protections absent.
Do not assume they are configured. No extra approval prompt is required by this
design; respect actual organization/environment policies.

- Protect `main`: require reviewed pull requests and successful ordinary CI,
  restrict direct changes, force pushes and deletion, and apply the intended
  no-bypass policy to administrators and workflow actors.
- Configure an active repository tag ruleset targeting `v*` that restricts
  **updates and deletions**, without granting the publishing actor an immutability
  bypass. Do not use a creation restriction that prevents the chosen Actions
  token from creating a new release tag. Confirm effective rules permit first-time
  creation but forbid subsequent updates/deletion for that same actor. If settings
  cannot express this distinction for the selected token, stop before activation.
- Configure the `release` environment to allow deployments only from protected
  `main`. Respect any effective organization/environment protection; do not add
  an otherwise unnecessary approval requirement.
- Store `MAVEN_CENTRAL_USERNAME`, `MAVEN_CENTRAL_PASSWORD`,
  `SIGNING_IN_MEMORY_KEY`, and optional `SIGNING_IN_MEMORY_KEY_PASSWORD` /
  `SIGNING_IN_MEMORY_KEY_ID` **only in that environment**, without repository-wide
  fallback. Do not expose them to Verify Release or reusable-workflow inputs.
- Keep default Actions permissions read-only. Allow explicit `contents:write`
  only for the publishing job, reusable-call ceiling and GitHub finalization job;
  verification itself has `contents:read`. No `secrets: inherit`.
- Review qualification and effective settings with appropriate owner access, then
  separately authorize activation. Change exactly the `publish-central` job guard
  in `release.yml` from `${{ false && github.run_attempt == 1 }}` to
  `${{ github.run_attempt == 1 }}`, and the `finalize` guard in
  `verify-release.yml` from `${{ false }}` to `${{ success() }}`. Update the
  structural activation tests in that same reviewed activation change. Preserve
  all attempt, first-time reservation, source, signing-input and dependency guards.

No live tag/sign/upload/release test is part of J4B qualification. Successful live
execution and effective protected-token behavior remain unverified until separately
authorized activation/release work.

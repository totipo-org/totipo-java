# JDTLS JDK argument compatibility

Starting HEAD: `2e97fe2d6c16a34984c1a44a1289e23d405e8087`, branch `main`.
Classifications: `DEPENDENCY_BUILD` (editor integration and intentional Gradle
upgrade alignment), `DOCS_JAVADOC` (Markdown version references).
The checkout already contained user changes to `flake.lock` and the Gradle
version assertion in `flake.nix`; these were preserved. Work therefore did not
start from clean main. No release preparation or release action occurred.

## Change and evidence

The original locked java-dev-tools helper at
`d18e1767593b3ac16da6c76a8f36fed36ac4c807`,
`lib/mk-java-editor.nix`, calls
`pkgs.jdt-language-server.override { jdk = serverJdk; }`.
The root locked nixpkgs at `e126e9e73c335158ff8279ac14d5f4afec153716`
declares `jdk25` instead. Both pinned upstream source files were inspected.

An initial shallow package-set adapter translated that one override argument
from `jdk` to `jdk25`. The user subsequently updated the toolkit and Totipo's
lock to `2906b647468049fa9be8b5f49ac86cb91a16f1b4`. Its helper now uses a
functional override to select the existing `jdk25` or `jdk` argument. That
immutable upstream helper was inspected directly. The temporary adapter accepted
only a set, causing the user's reported `expected a set but found a function`
error at the updated helper's override call. The adapter has been removed;
Totipo again passes the real package set directly to the toolkit. No additional
toolkit implementation patch is needed for that reported error.
The user confirmed the Gradle 9.8.2 upgrade is intentional after updating flake
inputs. `qualification.nix` now asserts 9.8.2 consistently with `flake.nix`.
Current version references in AGENTS.md, README.md, docs/DEVELOPMENT.md and
RELEASE_CHECKLIST.md were aligned. Historical reports remain unchanged.
No lock or dependency cache was changed by the agent; Java and publication
implementation remain unchanged.

`git diff --check` and `git diff --cached --quiet` pass. The user subsequently
reported successful human Nix qualification, as recorded below.

## Qualification and invocation accounting

Agent execution counts remain zero: baseline broad Gradle runs, focused Gradle
tests, focused standalone tooling tests, final full Java gates,
publication/consumer passes and separate host offline passes. Agents ran no Nix.

After the Gradle 9.8.2 assertion alignment, the user reported one human cache
update checkpoint with no cache changes and one successful human
`nix flake check`. `git diff --quiet -- package-deps.json` independently confirms
the cache matches HEAD. No new cache entries require artifact review.
The successful final human Nix gate is recorded as user-reported evidence;
no logs, task-execution counts or cache-hit breakdown were supplied. Nested
qualification tasks are not counted as separate host invocations.

The separate normal Java gate and host offline validation described by the
qualification ladder were not executed by the agent or independently reported
by the user. The successful Nix gate does not claim those separate invocations.
No second Nix build or release reproducibility run is requested.

## Source membership and final Git state

`qualification.nix` includes production source/build/tooling trees and excludes
the edited Markdown paths, including `docs/DEVELOPMENT.md` and `review/`.
`qualification.nix`, `flake.nix` and `flake.lock` determine qualification;
the new report is excluded. Only this excluded report was edited after the
user reported the successful Nix gate; no included input was changed afterward.

The report is intent-added only. Final `git status --short` lists modified
AGENTS.md, README.md, RELEASE_CHECKLIST.md, docs/DEVELOPMENT.md, flake.lock,
flake.nix, qualification.nix, and this added report.
`git diff --name-only` lists the same eight paths;
`git diff --cached --name-only` is empty and
`git diff --cached --quiet` passes. No actual contents were staged or committed.

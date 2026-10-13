"""J3B.2 migration-only oracle. Remove with CompletionFixture in J4.

Usage: python3 -B tooling/completion-parity.py CLASSPATH EVIDENCE_DIR
Unchanged Python, explicit fixture roots, strict fake requests/processes; no live services.
"""
import base64
import contextlib
import copy
import hashlib
import io
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "publishing"))
import release
import publication

CP, OUT = sys.argv[1:]
OUT = Path(OUT)
OUT.mkdir(parents=True, exist_ok=True)
JAVA = str(Path(os.environ["JAVA_HOME"]) / "bin/java")
RUN = subprocess.run
SHA = "a" * 40
ENV = dict(REQUESTED_VERSION="0.2.0", REQUESTED_COMMIT=SHA, DISPATCH_REF="refs/heads/main", DISPATCH_SHA=SHA,
           GITHUB_REPOSITORY="totipo-org/totipo-java", GH_TOKEN="synthetic-token")
RESULTS = []


def java(*args):
    return RUN([JAVA, "-cp", CP, "org.totipo.tooling.release.CompletionFixture", *map(str, args)],
               capture_output=True, text=True, timeout=40, env=ENV)


def record(name, py, j, tightening=False, details=None):
    agreement = py == j or (tightening and py and not j)
    RESULTS.append(dict(case=name, python=py, java=j, tightening=py and not j, agreement=agreement, details=details))
    assert agreement, RESULTS[-1]


def fixture(path):
    path.mkdir(parents=True)
    p = RUN([JAVA, "-cp", CP, "org.totipo.tooling.release.AuthorizationFixture", "create", str(path)], capture_output=True, text=True, timeout=30)
    assert p.returncode == 0, p.stderr
    (path / "expected.json").write_bytes((path / "build/publication-manifest.json").read_bytes())
    return path


def remote_cases():
    root = fixture(OUT / "remote")
    manifest = publication.load(root, "0.2.0")
    paths = [a["path"] for a in manifest["artifacts"]]
    bodies = {release.CENTRAL + p: (root / "build/repository" / p).read_bytes() for p in paths}
    armor = b"-----BEGIN PGP SIGNATURE-----\nfixture\n-----END PGP SIGNATURE-----"
    bodies.update({release.CENTRAL + p + ".asc": armor for p in paths})
    first, third = release.CENTRAL + paths[0], release.CENTRAL + paths[2]
    cases = [("immediate", {}, {}, 2400, 0, False),
             ("artifact-delayed", {first: 1}, {}, 2400, 0, False),
             ("signature-delayed", {first + ".asc": 1}, {}, 2400, 0, False),
             ("different-polls", {first: 4, third: 2, release.CENTRAL + paths[4] + ".asc": 3}, {}, 2400, 0, False),
             ("signature-timeout", {first + ".asc": 100}, {}, 25, 0, True),
             ("overall-timeout", {u: 100 for u in bodies}, {}, 25, 0, True),
             ("timeout-during-acquisition", {}, {}, 1, 2, True),
             ("mismatch-at-deadline", {}, {first: (200, b"wrong")}, 1, 2, False),
             ("mismatch-after-404", {first: 1}, {first: (200, b"wrong")}, 2400, 0, False),
             ("mismatch-after-progress", {}, {third: (200, b"wrong")}, 2400, 0, False),
             ("malformed-signature", {}, {third + ".asc": (200, b"invalid")}, 2400, 0, False),
             ("oversized-signature", {}, {first + ".asc": (200, armor + b" " * 65536)}, 2400, 0, True)]
    for status in (-1, 301, 302, 401, 403, 429, 500, 503):
        cases.append((f"status-{status}", {}, {third: (status, b"")}, 2400, 0, False))
    for name, absence, override, timeout, acquire, tightening in cases:
        absent = dict(absence)
        trace, rows, clock, requests = [], [], [0], []
        def request(url, token=None):
            assert url in bodies, url
            requests.append(url)
            trace.append("REQUEST " + url)
            clock[0] += acquire
            if absent.get(url, 0):
                absent[url] -= 1
                status, body = 404, b""
            else:
                status, body = override.get(url, (200, bodies[url]))
            rows.append("\t".join((url, str(status), base64.b64encode(body).decode(), str(acquire))))
            if status not in (200, 404):
                raise RuntimeError("Network failure" if status == -1 else f"Unexpected HTTP {status}")
            return status, body
        def sleep(seconds):
            trace.append("SLEEP " + str(int(seconds)))
            clock[0] += seconds
        error = None
        with patch.object(release, "ROOT", root), patch.dict(os.environ, dict(ENV, EXPECTED_INVENTORY=(root / "expected.json").read_text()), clear=True), \
             patch.object(release, "request", side_effect=request), patch.object(release.time, "monotonic", side_effect=lambda: clock[0]), \
             patch.object(release.time, "sleep", side_effect=sleep), contextlib.redirect_stdout(io.StringIO()):
            try:
                release.verify_remote(timeout)
                py = True
            except (RuntimeError, OSError) as e:
                py, error = False, str(e)
        script = OUT / (name + ".responses")
        script.write_text("\n".join(rows) + "\n")
        result = java("remote", root, script, timeout)
        assert result.returncode == 0, result.stderr
        lines = result.stdout.splitlines()
        j = lines[-1] == "PASS"
        record("remote:" + name, py, j, tightening, dict(python_trace=trace, java_trace=lines, python_error=error))
        if not tightening:
            assert lines[:-1] == trace, (name, trace, lines)
        else:
            # Deadline enforcement can stop before Python's next request or before its final success.
            assert trace[:len(lines)-1] == lines[:-1], (name, trace, lines)
        if error and "PERMANENT" in error:
            assert "PERMANENT_MISMATCH" in lines[-1]
        if error and "deadline exceeded" in error:
            expected_count = re.search(r"(\d+) artifacts/signatures", error).group(1)
            assert lines[-1].endswith(" " + expected_count)
    # Fresh local evidence must reject before any HTTP, including a changed whole-repository fingerprint.
    for name, mutation in (("expected-hash", "expected"), ("staging-index", "index"), ("malformed-manifest", "manifest")):
        fresh = fixture(OUT / name)
        if mutation == "expected":
            data = json.loads((fresh / "expected.json").read_text()); data["artifacts"][0]["sha256"] = "f" * 64
            (fresh / "expected.json").write_text(json.dumps(data))
        elif mutation == "index":
            (fresh / "build/repository/extra").write_text("changed")
        else:
            (fresh / "build/publication-manifest.json").write_text("{}")
        with patch.object(release, "ROOT", fresh), patch.dict(os.environ, dict(ENV, EXPECTED_INVENTORY=(fresh / "expected.json").read_text()), clear=True), \
             patch.object(release, "request", side_effect=AssertionError("No unchecked HTTP")), contextlib.redirect_stdout(io.StringIO()):
            try: release.verify_remote(1); py = True
            except (RuntimeError, OSError): py = False
        script = OUT / "empty.responses"; script.write_text("")
        result = java("remote", fresh, script, 1)
        assert result.returncode == 0 and not any(l.startswith("REQUEST") for l in result.stdout.splitlines()), result.stderr
        record("remote:local-" + name, py, result.stdout.strip() == "PASS")


def source(root):
    root.mkdir(parents=True, exist_ok=True)
    status = "<!-- prepared-release:start -->\nJava 0.2.0 is released. See RELEASE_CHECKLIST.md.\n<!-- prepared-release:end -->\n"
    files = {"VERSION": "0.2.0\n", "RELEASE_CHECKLIST.md": "# Totipo Java 0.2.0 release checklist\norg.totipo:totipo-core:0.2.0\norg.totipo:totipo-storage-nio:0.2.0\nAnnotated source tag convention: `v0.2.0`\n`version`: `0.2.0`\nreview/V0_2_0_RELEASE_NOTES.md\n" + status,
             ".github/workflows/release.yml": "Exact VERSION to release (for example, 0.2.0)\n",
             "publishing/consumer-smoke/gradle.lockfile": "org.bouncycastle:bcprov-jdk18on:1.86=runtimeClasspath\n" + "".join(f"org.totipo:totipo-{m}:0.2.0=compileClasspath,runtimeClasspath,testCompileClasspath,testRuntimeClasspath\n" for m in ("core", "storage-nio")),
             "README.md": status + "Historical org.totipo:totipo-core:0.1.4\n", "API_DESIGN.md": status + "The existing 0.2.0 API.\n",
             "review/V0_2_0_RELEASE_NOTES.md": release.NOTES_TEMPLATE.format(version="0.2.0").replace("TODO:", "Reviewed:"), "review/history.md": "Historical 0.1.5 / r19 provenance\n"}
    for name, text in files.items():
        path = root / name; path.parent.mkdir(parents=True, exist_ok=True); path.write_text(text)
    shutil.copyfile(ROOT / "SPEC_PIN.md", root / "SPEC_PIN.md")
    snapshot = "core/src/test/resources/totipo-spec/v1-pre-rc"
    shutil.copytree(ROOT / snapshot, root / snapshot, dirs_exist_ok=True)


def inventory(root):
    return {p.relative_to(root).as_posix(): p.read_bytes() for p in root.rglob("*") if p.is_file()}


def preparation_cases():
    scenarios = ["released", "prepared", "existing-notes", "crlf", "historical", "equal", "downgrade", "noncanonical", "dirty", "detached", "branch", "missing-slot", "duplicate-slot", "lock", "pin", "snapshot", "missing-file", "symlink"]
    for name in scenarios:
        base = OUT / ("prepare-base-" + name); source(base)
        target, branch, dirty = "0.2.1", "main", ""
        if name == "prepared":
            for file in ("README.md", "API_DESIGN.md", "RELEASE_CHECKLIST.md"):
                path = base / file; path.write_text(path.read_text().replace("Java 0.2.0 is released.", "Java 0.2.0 is prepared locally and not yet released."))
        if name == "existing-notes": (base / "review/V0_2_1_RELEASE_NOTES.md").write_text(release.NOTES_TEMPLATE.format(version="0.2.1").replace("TODO:", "Reviewed:"))
        if name == "crlf":
            for path in base.rglob("*"):
                if path.is_file() and (path.name in {"VERSION", "gradle.lockfile", "release.yml"} or path.suffix == ".md") and "core" not in path.parts:
                    path.write_bytes(path.read_bytes().replace(b"\n", b"\r\n"))
        if name == "historical":
            for file in ("README.md", "API_DESIGN.md", "RELEASE_CHECKLIST.md"):
                with (base / file).open("a") as out: out.write("\nHistorical 0.2.0 / 0.1.5 / r18 example.\n")
        if name in ("equal", "downgrade", "noncanonical"): target = {"equal": "0.2.0", "downgrade": "0.1.5", "noncanonical": "0.02.1"}[name]
        if name in ("detached", "branch"): branch = "" if name == "detached" else "feature"
        if name == "dirty": dirty = " M VERSION"
        if name in ("missing-slot", "duplicate-slot", "lock", "pin", "snapshot"):
            file = {"missing-slot": "README.md", "duplicate-slot": ".github/workflows/release.yml", "lock": "publishing/consumer-smoke/gradle.lockfile", "pin": "SPEC_PIN.md", "snapshot": "core/src/test/resources/totipo-spec/v1-pre-rc/SNAPSHOT.sha256"}[name]
            path = base / file; text = path.read_text()
            path.write_text(text + text if name == "duplicate-slot" else text.replace("compileClasspath,runtimeClasspath,testCompileClasspath,testRuntimeClasspath", "runtimeClasspath") if name == "lock" else "wrong")
        if name == "missing-file": (base / "API_DESIGN.md").unlink()
        if name == "symlink":
            (base / "README.md").unlink(); (base / "README.md").symlink_to("API_DESIGN.md")
        pyroot, jroot = OUT / ("prepare-python-" + name), OUT / ("prepare-java-" + name)
        shutil.copytree(base, pyroot, symlinks=True); shutil.copytree(base, jroot, symlinks=True)
        def git(*args, **kwargs):
            if args == ("branch", "--show-current"): return branch
            if args == ("status", "--porcelain", "--untracked-files=all"): return dirty
            raise AssertionError(args)
        with patch.object(release, "ROOT", pyroot), patch.object(release, "git", side_effect=git), contextlib.redirect_stdout(io.StringIO()):
            try: release.prepare(target); py = True
            except (RuntimeError, OSError): py = False
        props = OUT / "prepare.properties"; props.write_text(f"branch={branch}\nstatus={dirty}\n")
        result = java("apply", jroot, target, props)
        assert result.returncode == 0, result.stderr
        j = result.stdout.strip() == "PASS"
        record("prepare:" + name, py, j, name == "symlink")
        if py and j:
            assert inventory(pyroot) == inventory(jroot), name
            for root in (pyroot, jroot):
                notes = root / "review/V0_2_1_RELEASE_NOTES.md"; notes.write_text(notes.read_text().replace("TODO:", "Reviewed:"))
                with patch.object(release, "ROOT", root), contextlib.redirect_stdout(io.StringIO()): release.prepare(target, check=True)
            before = inventory(jroot); result = java("apply", jroot, target, props); assert result.stdout.strip() == "FAIL" and inventory(jroot) == before
            record("prepare:repeat-" + name, False, False)


def finalization_cases():
    root = fixture(OUT / "finalize"); source(root)
    notes = "## v0.2.0\n\nReviewed notes with Unicode α.\n\n \t\u00a0\n"
    (root / "review/V0_2_0_RELEASE_NOTES.md").write_text(notes)
    body = notes.rstrip() + f"\n\n### Release provenance\n\n- Java source commit: `{SHA}`\n- Totipo v1/r19 spec commit: `{release.SPEC_COMMIT}`\n- Portable corpus: 92/92 executed, none deferred.\n"
    exact = dict(tag_name="v0.2.0", name="v0.2.0", draft=False, prerelease=False, body=body)
    cases = [("new", "absent", "absent", None, None, False, 1, False),
             ("local", "valid", "absent", None, None, False, 1, False),
             ("remote", "valid", "valid", None, None, False, 1, False),
             ("complete", "valid", "valid", exact, None, False, 1, False),
             ("crlf", "valid", "valid", dict(exact, body=body.replace("\n", "\r\n") + " \r\n"), None, False, 1, False),
             ("lightweight", "lightweight", "absent", None, None, False, 1, False),
             ("wrong-local", "wrong", "absent", None, None, False, 1, False),
             ("wrong-remote", "valid", "wrong", None, None, False, 1, False),
             ("tag-conflict", "valid", "conflict", None, None, False, 1, True),
             ("missing-peeled", "absent", "missing-peeled", None, None, False, 1, True),
             ("wrong-annotation", "annotation", "absent", None, None, False, 1, True),
             ("release-no-tag", "valid", "absent", exact, None, False, 1, False)]
    for key, value in (("tag_name", "v0.2.1"), ("name", "wrong"), ("draft", True), ("prerelease", True), ("body", "wrong")):
        cases.append(("release-" + key, "valid", "valid", dict(exact, **{key: value}), None, False, 1, False))
    for operation in ("tag", "push", "release"):
        for effects in (False, True):
            cases.append((f"retry-{operation}-{effects}", "absent", "absent", None, operation, effects, 2, False))
    for name, local, remote, record_value, fail, effects, attempts, tightening in cases:
        state = dict(local=local, remote=remote, record=copy.deepcopy(record_value))
        props = dict(local=local, remote=remote, attempts=str(attempts))
        if record_value is not None: props["release"] = base64.b64encode(json.dumps(record_value).encode()).decode()
        if fail: props.update(fail=fail, effects=str(effects).lower())
        file = OUT / "finalize.properties"; file.write_text("".join(f"{k}={v}\n" for k,v in props.items()))
        trace = []
        def mutation(kind):
            trace.append(kind)
            if kind != fail or effects:
                if kind == "tag": state["local"] = "valid"
                elif kind == "push": state["remote"] = state["local"]
                else: state["record"] = exact
            if kind == fail: raise RuntimeError("uncertain mutation")
        def git(*args, **kwargs):
            if args == ("rev-parse", "HEAD") or args == ("rev-parse", "refs/remotes/origin/main"): return SHA
            if args[0] == "status": return ""
            if args[0] == "fetch": return ""
            if args[0] == "ls-remote":
                if state["remote"] == "absent": return ""
                commit = "f" * 40 if state["remote"] in ("wrong", "missing-peeled") else SHA
                obj = SHA if state["remote"] == "missing-peeled" else "c" * 40 if state["remote"] == "conflict" else "b" * 40
                return obj + " refs/tags/v0.2.0" + ("" if state["remote"] == "missing-peeled" else "\n" + commit + " refs/tags/v0.2.0^{}")
            if args[:2] == ("tag", "--list"): return "" if state["local"] == "absent" else "v0.2.0"
            if args[0] == "rev-parse": return "f" * 40 if state["local"] == "wrong" else SHA
            if args[:3] == ("cat-file", "-t", "v0.1.0"): return "tag"
            if args[:3] == ("cat-file", "-p", "v0.1.0"): return "unsigned annotation"
            if args[:2] == ("cat-file", "-t"):
                if state["local"] == "absent": raise RuntimeError("Missing local tag")
                return "commit" if state["local"] == "lightweight" else "tag"
            if args[0] == "config": return ""
            if args[:3] == ("-c", "tag.gpgSign=false", "tag"): mutation("tag"); return ""
            if args[0] == "push": mutation("push"); return ""
            raise AssertionError(args)
        def gh(command, **kwargs):
            assert command[:4] == ["gh", "release", "create", "v0.2.0"] and "--verify-tag" in command
            assert Path(command[command.index("--notes-file") + 1]).read_bytes() == body.encode()
            mutation("release")
        py_results, py_traces, remote_before = [], [], []
        for attempt in range(attempts):
            start = len(trace)
            remote_before.append(state["remote"])
            with patch.dict(os.environ, ENV, clear=True), patch.object(release, "ROOT", root), patch.object(release, "git", side_effect=git), \
                 patch.object(release, "release_record", side_effect=lambda v: state["record"]), patch.object(release.subprocess, "run", side_effect=gh), contextlib.redirect_stdout(io.StringIO()):
                try: release.tag_release(); py_results.append(True)
                except (RuntimeError, OSError, KeyError): py_results.append(False)
            py_traces.append(trace[start:]); fail = None
        result = java("finalize", root, file)
        if result.returncode != 0:
            assert not py_results[-1], (name, result.stderr)
            record("finalize:" + name, False, False); continue
        lines = result.stdout.splitlines(); assert base64.b64decode(lines[-1][5:]) == body.encode(), name
        for attempt, py in enumerate(py_results):
            j = lines[2 * attempt] == "COMPLETE"
            record(f"finalize:{name}:{attempt}", py, j, tightening)
            j_mutations = [a for a in lines[2 * attempt + 1].split(",") if a != "observe"]
            expected_mutations = list(py_traces[attempt])
            if remote_before[attempt] in ("valid", "conflict") and "push" in expected_mutations:
                # Reviewed intentional omission: no redundant push when remote identity is already exact.
                expected_mutations.remove("push")
            if not tightening: assert j_mutations == expected_mutations, (name, j_mutations, expected_mutations)
            RESULTS[-1]["details"] = dict(python_mutations=py_traces[attempt], java_mutations=j_mutations,
                                          omission="valid remote tag needs no redundant push" if py_traces[attempt] != expected_mutations else None)


remote_cases()
preparation_cases()
finalization_cases()
(OUT / "results.json").write_text(json.dumps(RESULTS, indent=2) + "\n")
print(f"{len(RESULTS)} J3B.2 comparisons; {sum(r['tightening'] for r in RESULTS)} intentional tightenings; zero unexplained drift")

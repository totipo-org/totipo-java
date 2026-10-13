"""Host-only J3A differential oracle. No live service/process fallbacks or live source writes.

Usage: python3 -B tooling/release-parity.py <standalone-main-and-test-classpath> <evidence-dir>
Java test classes are required; the installed tool has no plan-apply capability.
"""
import base64
import contextlib
import hashlib
import io
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "publishing"))
import release

CP, OUTPUT = sys.argv[1:]
OUTPUT = Path(OUTPUT)
OUTPUT.mkdir(parents=True, exist_ok=True)
JAVA = str(Path(os.environ["JAVA_HOME"]) / "bin/java")
SHA, OTHER = "a" * 40, "b" * 40
ENV = dict(REQUESTED_VERSION="0.2.0", REQUESTED_COMMIT=SHA, DISPATCH_REF="refs/heads/main",
           DISPATCH_SHA=SHA, GITHUB_REPOSITORY="totipo-org/totipo-java", GITHUB_RUN_ID="123", GITHUB_RUN_ATTEMPT="1")
RESULTS = []


def java(*args, env=None):
    result = subprocess.run([JAVA, "-cp", CP, "org.totipo.tooling.release.ReleaseFixture", *map(str, args)],
                            env={"JAVA_HOME": os.environ["JAVA_HOME"], **(ENV if env is None else env)},
                            capture_output=True, text=True, timeout=30)
    return result.returncode == 0, result.stdout.strip(), result.stderr.strip()


def python(action):
    try:
        with contextlib.redirect_stdout(io.StringIO()):
            result = action()
        return True, result
    except (RuntimeError, KeyError, OSError, ValueError, TypeError, AttributeError, IndexError) as error:
        return False, str(error)


def compare(name, action, args, env=None, tightening=False, values=None):
    with patch.dict(os.environ, ENV if env is None else env, clear=True):
        py_ok, py_result = python(action)
    j_ok, j_out, j_err = java(*args, env=env)
    expected = (py_ok == j_ok) or (tightening and py_ok and not j_ok)
    if values is None and args[0] == "history":
        values = lambda result, output: output == ("POSSIBLE_UPLOAD" if result else "NO_RECORDED_UPLOAD")
    if values is None and args[0] == "release":
        values = lambda result, output: output == ("ABSENT" if result is None else "PRESENT")
    if expected and py_ok and j_ok and values:
        expected = values(py_result, j_out)
    record = dict(name=name, python_accept=py_ok, java_accept=j_ok, intentional_tightening=py_ok and not j_ok and tightening,
                  agreement=expected, java_output=j_out, java_error=j_err)
    RESULTS.append(record)
    if not expected:
        raise AssertionError(f"Unexplained drift: {name}: Python={py_ok,py_result}; Java={j_ok,j_out,j_err}")


def write(root, path, text):
    p = root / path
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(text)


def inventory(root):
    return {p.relative_to(root).as_posix(): p.read_bytes() for p in root.rglob("*") if p.is_file()}


def git_records(root, **overrides):
    defaults = dict(BRANCH="main", STATUS="", HEAD=SHA, REMOTE_MAIN=SHA, REMOTE_TAG="", LOCAL_TAG="", LOCAL_TAG_COMMIT=SHA, LOCAL_TAG_TYPE="tag")
    defaults.update(overrides)
    for key, value in defaults.items():
        write(root, f"git/{key}.txt", value + "\n")
    return defaults


def py_git(records):
    def query(*args):
        names = {("branch", "--show-current"): "BRANCH", ("status", "--porcelain", "--untracked-files=all"): "STATUS",
                 ("rev-parse", "HEAD"): "HEAD", ("rev-parse", "refs/remotes/origin/main"): "REMOTE_MAIN",
                 ("tag", "--list", "v0.2.0"): "LOCAL_TAG", ("rev-parse", "refs/tags/v0.2.0^{commit}"): "LOCAL_TAG_COMMIT",
                 ("ls-remote", "--tags", "origin", "refs/tags/v0.2.0", "refs/tags/v0.2.0^{}"): "REMOTE_TAG"}
        if args == ("fetch", "--no-recurse-submodules", "--tags", "origin", "+refs/heads/main:refs/remotes/origin/main"):
            return ""  # Python's fetch is simulated; no actual Git/network operation.
        if args not in names:
            raise AssertionError(f"Unexpected Python Git query: {args}")
        return records[names[args]]
    return query


def record_http(root, responses):
    shutil.rmtree(root / "http", ignore_errors=True)
    for url, (status, body) in responses.items():
        key = hashlib.sha256(url.encode("ascii")).hexdigest()
        write(root, f"http/{key}.status", f"{status}\n")
        p = root / f"http/{key}.body"
        p.write_bytes(body if isinstance(body, bytes) else body.encode())


def py_http(responses):
    def request(url, token=None):
        if url not in responses:
            raise RuntimeError("Unknown fake HTTP request (no network fallback)")
        status, body = responses[url]
        if status not in (200, 404):
            raise RuntimeError("Unexpected HTTP")
        return status, body if isinstance(body, bytes) else body.encode()
    return request


def publish(start="now", conclusion="failure", sha=SHA):
    return dict(name="publish-central", head_sha=sha, steps=[dict(name=release.UPLOAD_STEP, started_at=start, conclusion=conclusion)])


def history_url(attempt, page):
    return f"https://api.github.com/repos/{ENV['GITHUB_REPOSITORY']}/actions/runs/123/attempts/{attempt}/jobs?per_page=100&page={page}"


def fixture(root):
    status = "<!-- prepared-release:start -->\nJava 0.2.0 is released. See RELEASE_CHECKLIST.md.\n<!-- prepared-release:end -->\n"
    write(root, "VERSION", "0.2.0\n")
    write(root, "RELEASE_CHECKLIST.md", "# Totipo Java 0.2.0 release checklist\norg.totipo:totipo-core:0.2.0\norg.totipo:totipo-storage-nio:0.2.0\nAnnotated source tag convention: `v0.2.0`\n`version`: `0.2.0`\nreview/V0_2_0_RELEASE_NOTES.md\n" + status)
    write(root, ".github/workflows/release.yml", "Exact VERSION to release (for example, 0.2.0)\n")
    write(root, "publishing/consumer-smoke/gradle.lockfile", "org.bouncycastle:bcprov-jdk18on:1.86=runtimeClasspath\n" + "".join(
        f"org.totipo:totipo-{m}:0.2.0=compileClasspath,runtimeClasspath,testCompileClasspath,testRuntimeClasspath\n" for m in ("core", "storage-nio")))
    write(root, "README.md", status + "Historical org.totipo:totipo-core:0.1.4\n")
    write(root, "API_DESIGN.md", status + "This is the existing 0.2.0 API.\n")
    write(root, "review/V0_2_0_RELEASE_NOTES.md", release.NOTES_TEMPLATE.format(version="0.2.0").replace("TODO:", "Reviewed:"))
    write(root, "review/history.md", "0.1.4, r18, r19: preserve historical prose\n")
    shutil.copyfile(ROOT / "SPEC_PIN.md", root / "SPEC_PIN.md")
    shutil.copytree(ROOT / "core/src/test/resources/totipo-spec/v1-pre-rc", root / "core/src/test/resources/totipo-spec/v1-pre-rc")


def run():
    for version in ("0.0.0", "00.02.003", "1.2.3-rc.1", "1.2.3-alpha-2", "1.2", "1.2.3-SNAPSHOT", "1.2.3-snapshot", "1.2.3\n", "$(id)", "1.2.3-", "1.2.3-rc..1", "１.2.3", "999999999999999999999.0.0"):
        compare(f"version:{version!r}", lambda: release.validate_version(version), ["version", version])
        compare(f"stable:{version!r}", lambda: release.stable_version(version), ["stable", version])
    compare("inputs:valid", release.inputs, ["inputs"])
    for key in ("REQUESTED_VERSION", "REQUESTED_COMMIT", "DISPATCH_REF", "DISPATCH_SHA"):
        env = dict(ENV); env.pop(key)
        compare(f"inputs:missing:{key}", release.inputs, ["inputs"], env=env)
        for value in (ENV[key] + "\n", "$(id)", "wrong"):
            compare(f"inputs:{key}:{value!r}", release.inputs, ["inputs"], env=dict(ENV, **{key: value}))
    for commit in ("A" * 40, "a" * 39, OTHER):
        compare(f"inputs:commit:{commit}", release.inputs, ["inputs"], env=dict(ENV, REQUESTED_COMMIT=commit))
    with tempfile.TemporaryDirectory(prefix="totipo-j3a-parity-") as temp:
        temp = Path(temp); source = temp / "source"; source.mkdir(); fixture(source)
        evidence = temp / "evidence"; evidence.mkdir(); records = git_records(evidence)
        with patch.object(release, "ROOT", source), patch.object(release, "git", side_effect=py_git(records)), patch.object(release, "request", side_effect=AssertionError("No preparation network")):
            before = inventory(source)
            compare("integrity:reviewed", release.source_integrity, ["integrity", source])
            compare("prepare:check-detached-no-git", lambda: release.prepare("0.2.0", check=True), ["prepare", source, "0.2.0", evidence, "true"])
            compare("prepare:dry-run", lambda: release.prepare("0.2.1", dry_run=True), ["prepare", source, "0.2.1", evidence, "false"])
            assert inventory(source) == before
            for target in ("0.2.0", "0.1.9", "0.02.1", "0.2.1-rc1", "0.2.1-SNAPSHOT", "$(id)"):
                compare(f"prepare:bad-target:{target}", lambda: release.prepare(target, dry_run=True), ["prepare", source, target, evidence, "false"])
            for key, value in (("BRANCH", ""), ("BRANCH", "feature"), ("STATUS", "?? untracked"), ("STATUS", " M VERSION")):
                old = records[key]; records[key] = value; git_records(evidence, **records)
                compare(f"prepare:{key}:{value}", lambda: release.prepare("0.2.1", dry_run=True), ["prepare", source, "0.2.1", evidence, "false"])
                records[key] = old; git_records(evidence, **records)
            for path, old, new in (
                ("VERSION", "0.2.0\n", "0.2.0"), ("VERSION", "0.2.0", "0.1.5"),
                ("README.md", "<!-- prepared-release:start -->", "missing"),
                ("API_DESIGN.md", "Java 0.2.0 is released.", "Java 0.1.5 is released."),
                ("RELEASE_CHECKLIST.md", "org.totipo:totipo-core:0.2.0", "org.totipo:totipo-core:0.1.5"),
                (".github/workflows/release.yml", "0.2.0", "0.1.5"),
                ("publishing/consumer-smoke/gradle.lockfile", "=compileClasspath,runtimeClasspath,testCompileClasspath,testRuntimeClasspath", "=runtimeClasspath")):
                file = source / path; original = file.read_text(); file.write_text(original.replace(old, new))
                compare(f"prepare:changed:{path}", lambda: release.prepare("0.2.1", dry_run=True), ["prepare", source, "0.2.1", evidence, "false"])
                file.write_text(original)
            for path in ("RELEASE_CHECKLIST.md", ".github/workflows/release.yml", "publishing/consumer-smoke/gradle.lockfile", "README.md", "API_DESIGN.md"):
                file = source / path; original = file.read_text(); file.write_text(original + original)
                compare(f"prepare:duplicate:{path}", lambda: release.prepare("0.2.1", dry_run=True), ["prepare", source, "0.2.1", evidence, "false"])
                file.write_text(original)
            for path in ("SPEC_PIN.md", "core/src/test/resources/totipo-spec/v1-pre-rc/SNAPSHOT.sha256", "core/src/test/resources/totipo-spec/v1-pre-rc/requirements/v1-pre-rc.json"):
                file = source / path; original = file.read_bytes(); file.write_bytes(original + b" ")
                compare(f"integrity:corrupt:{path}", release.source_integrity, ["integrity", source])
                file.unlink(); compare(f"integrity:missing:{path}", release.source_integrity, ["integrity", source]); file.write_bytes(original)
            note = source / "review/V0_2_0_RELEASE_NOTES.md"; original = note.read_text()
            for name, text in (("wrong-version", "## v0.1.5\n"), ("missing-sections", "## v0.2.0\n"), ("TODO", release.NOTES_TEMPLATE.format(version="0.2.0"))):
                note.write_text(text)
                compare(f"notes:{name}:identity", lambda: release.reviewed_notes("0.2.0"), ["notes", source, "0.2.0", "false", "false"])
                compare(f"notes:{name}:prepared", lambda: release.prepare("0.2.0", check=True), ["prepare", source, "0.2.0", evidence, "true"])
            note.unlink(); compare("notes:missing", lambda: release.reviewed_notes("0.2.0"), ["notes", source, "0.2.0", "false", "false"]); note.write_text(original)
            # Real Python application vs test-only Java plan application, solely on disposable copies.
            for variant in ("released", "prepared", "existing-notes", "crlf"):
                py_root, j_root = temp / f"py-{variant}", temp / f"java-{variant}"
                shutil.copytree(source, py_root); shutil.copytree(source, j_root)
                for root in (py_root, j_root):
                    if variant == "prepared":
                        for path in ("README.md", "API_DESIGN.md", "RELEASE_CHECKLIST.md"):
                            p = root / path; p.write_text(p.read_text().replace(" is released.", " is prepared locally and not yet released."))
                    if variant == "existing-notes": write(root, "review/V0_2_1_RELEASE_NOTES.md", release.NOTES_TEMPLATE.format(version="0.2.1"))
                    if variant == "crlf":
                        for path in ("README.md", "API_DESIGN.md", "RELEASE_CHECKLIST.md", ".github/workflows/release.yml", "publishing/consumer-smoke/gradle.lockfile", "VERSION"):
                            p = root / path; p.write_bytes(p.read_bytes().replace(b"\n", b"\r\n"))
                with patch.object(release, "ROOT", py_root):
                    compare(f"plan:{variant}", lambda: release.preparation_plan("0.2.0", "0.2.1"), ["prepare", j_root, "0.2.1", evidence, "false"],
                            values=lambda plan, out: all(dict(line.split("\t") for line in out.splitlines())[p] == base64.b64encode(text.encode()).decode() for p, text in plan.items()))
                    with contextlib.redirect_stdout(io.StringIO()): release.prepare("0.2.1")
                accepted, out, err = java("prepare", j_root, "0.2.1", evidence, "false"); assert accepted, err
                java_plan = {p: base64.b64decode(data) for p, data in (line.split("\t") for line in out.splitlines())}
                for path, data in java_plan.items():
                    p = j_root / path; p.parent.mkdir(parents=True, exist_ok=True); p.write_bytes(data)
                assert inventory(py_root) == inventory(j_root), variant
                RESULTS.append(dict(name=f"applied-byte-and-unchanged-inventory:{variant}", agreement=True))
                for root in (py_root, j_root):
                    p = root / "review/V0_2_1_RELEASE_NOTES.md"; p.write_text(p.read_text().replace("TODO:", "Reviewed:"))
                with patch.object(release, "ROOT", py_root):
                    compare(f"prepared-result:{variant}", lambda: release.prepare("0.2.1", check=True), ["prepare", j_root, "0.2.1", evidence, "true"])
        # Identity against deterministic Git and HTTP evidence, including peeled tags.
        release_url = f"https://api.github.com/repos/{ENV['GITHUB_REPOSITORY']}/releases/tags/v0.2.0"
        responses = {release_url: (404, "")}; record_http(evidence, responses)
        with patch.object(release, "ROOT", source), patch.object(release, "git", side_effect=py_git(records)), patch.object(release, "request", side_effect=py_http(responses)):
            compare("identity:valid", release.identity, ["identity", source, evidence, "false"])
            for key, value in (("HEAD", OTHER), ("REMOTE_MAIN", OTHER), ("REMOTE_MAIN", ""), ("STATUS", "?? untracked"), ("LOCAL_TAG", "v0.2.0")):
                old = records[key]; records[key] = value; git_records(evidence, **records)
                compare(f"identity:{key}:{value}", release.identity, ["identity", source, evidence, "false"])
                records[key] = old; git_records(evidence, **records)
            ref = "refs/tags/v0.2.0"
            for name, value, tightening in (("lightweight", SHA + "\t" + ref, False), ("wrong", OTHER + "\t" + ref, False),
                ("annotated", OTHER + "\t" + ref + "\n" + SHA + "\t" + ref + "^{}", False),
                ("wrong-peeled", SHA + "\t" + ref + "\n" + OTHER + "\t" + ref + "^{}", False),
                ("peeled-only", SHA + "\t" + ref + "^{}", True), ("duplicate", SHA + "\t" + ref + "\n" + SHA + "\t" + ref, True)):
                records["REMOTE_TAG"] = value; git_records(evidence, **records)
                compare(f"identity:tag:{name}:allow", lambda: release.identity(allow_tag=True), ["identity", source, evidence, "true"], tightening=tightening)
                compare(f"identity:tag:{name}:initial", release.identity, ["identity", source, evidence, "false"])
            records["REMOTE_TAG"] = ""; records["LOCAL_TAG"] = "v0.2.0"
            for target in (SHA, OTHER):
                records["LOCAL_TAG_COMMIT"] = target; git_records(evidence, **records)
                compare(f"identity:local-tag:{target}", lambda: release.identity(allow_tag=True), ["identity", source, evidence, "true"])
            records["LOCAL_TAG"] = ""; git_records(evidence, **records)
        # GitHub records: extra schema validation is an intentional fail-closed tightening.
        good_release = dict(tag_name="v0.2.0", name="v0.2.0", body="reviewed", draft=False, prerelease=False)
        for name, status, body, tightening in (("absent", 404, "", False), ("present", 200, json.dumps(good_release), False),
                ("missing", 200, "{}", True), ("wrong-version", 200, json.dumps(dict(good_release, tag_name="v0.1.5")), True),
                ("duplicate", 200, '{"tag_name":"wrong",' + json.dumps(good_release)[1:], True), ("truncated", 200, "{", False)):
            responses = {release_url: (status, body)}; record_http(evidence, responses)
            with patch.object(release, "request", side_effect=py_http(responses)):
                compare(f"release-record:{name}", lambda: release.release_record("0.2.0"), ["release", evidence], tightening=tightening)
        cases = []
        for conclusion in ("failure", "success", "timed_out", "cancelled", "skipped", None):
            cases.append((f"started-{conclusion}", [publish(conclusion=conclusion)], False))
        cases += [("not-started", [publish(start=None)], False), ("different-commit", [publish(sha=OTHER)], False),
                  ("empty", [], True), ("missing-job", [dict(name="preflight", head_sha=SHA, steps=[])], True),
                  ("missing-steps", [dict(name="publish-central", head_sha=SHA)], True),
                  ("missing-upload", [dict(name="publish-central", head_sha=SHA, steps=[])], True),
                  ("missing-step-fields", [dict(name="publish-central", head_sha=SHA, steps=[dict(name=release.UPLOAD_STEP)])], True),
                  ("wrong-run-id", [dict(publish(start=None), run_id=124)], True),
                  ("wrong-attempt", [dict(publish(start=None), run_attempt=2)], True),
                  ("duplicate-jobs", [publish(), publish()], True),
                  ("duplicate-steps", [dict(name="publish-central", head_sha=SHA, steps=publish()["steps"] * 2)], True)]
        for name, jobs, tightening in cases:
            responses = {history_url(1, 1): (200, json.dumps(dict(jobs=jobs)))}; record_http(evidence, responses)
            with patch.object(release, "request", side_effect=py_http(responses)):
                compare(f"history:{name}", release.prior_upload, ["history", evidence], env=dict(ENV, GITHUB_RUN_ATTEMPT="2"), tightening=tightening,
                        values=lambda result, output: output == ("POSSIBLE_UPLOAD" if result else "NO_RECORDED_UPLOAD"))
        full = [dict(name=f"job-{i}", head_sha=SHA, steps=[]) for i in range(100)]
        for name, first, later, tightening in (
                ("full-page", full, (200, json.dumps(dict(jobs=[publish(start=None)]))), False),
                ("exactly-full", full[:-1] + [publish(start=None)], (200, '{"jobs":[]}'), False),
                ("multiple-pages-upload", full, (200, json.dumps(dict(jobs=[publish()]))), False),
                ("later-page-failure", full, (503, ""), False),
                ("upload-then-later-page-failure", full[:-1] + [publish()], (503, ""), True)):
            responses = {history_url(1, 1): (200, json.dumps(dict(jobs=first))), history_url(1, 2): later}; record_http(evidence, responses)
            with patch.object(release, "request", side_effect=py_http(responses)):
                compare(f"history:{name}", release.prior_upload, ["history", evidence], env=dict(ENV, GITHUB_RUN_ATTEMPT="2"), tightening=tightening)
        for status in (401, 403, 404, 429, 500, 503):
            responses = {history_url(1, 1): (status, "")}; record_http(evidence, responses)
            with patch.object(release, "request", side_effect=py_http(responses)):
                compare(f"history:http-{status}", release.prior_upload, ["history", evidence], env=dict(ENV, GITHUB_RUN_ATTEMPT="2"))
        for name, body, tightening in (("truncated", '{"jobs":[', False), ("missing-fields", "{}", False),
                ("duplicate-json", '{"jobs":[],"jobs":[' + json.dumps(publish()) + '] }', True),
                ("incomplete-total-count", json.dumps(dict(jobs=[publish(start=None)], total_count=2)), True),
                ("contradictory-total-count", json.dumps(dict(jobs=[publish(start=None)], total_count=0)), True)):
            responses = {history_url(1, 1): (200, body)}; record_http(evidence, responses)
            with patch.object(release, "request", side_effect=py_http(responses)):
                compare(f"history:{name}", release.prior_upload, ["history", evidence], env=dict(ENV, GITHUB_RUN_ATTEMPT="2"), tightening=tightening)
        urls = [f"{release.CENTRAL}org/totipo/totipo-{m}/0.2.0/totipo-{m}-0.2.0.pom" for m in ("core", "storage-nio")]
        # Java emits facts; Python's live dispositions are mapped ONLY in this test oracle.
        def equivalent(py, out):
            fields = out.split()
            mapped = "error" if fields[3] == "POSSIBLE_UPLOAD_NOT_VISIBLE" or ("PRESENT" in fields[:2] and fields[2] == "NO_RECORDED_UPLOAD") else "verify" if fields[2] == "POSSIBLE_UPLOAD" else "publish"
            return py == mapped
        for core in (200, 404):
            for storage in (200, 404):
                for attempted in (False, True):
                    responses = {urls[0]: (core, ""), urls[1]: (storage, ""), history_url(1, 1): (200, json.dumps(dict(jobs=[publish()]))) }
                    record_http(evidence, responses)
                    env = dict(ENV, GITHUB_RUN_ATTEMPT="2" if attempted else "1")
                    def disposition():
                        accepted, result = python(release.central_state)
                        return result if accepted else "error"
                    with patch.object(release, "request", side_effect=py_http(responses)):
                        compare(f"central:{core}/{storage}/prior={attempted}", disposition, ["state", evidence], env=env, values=equivalent)
        for status in (401, 403, 429, 500, 503):
            responses = {urls[0]: (status, ""), urls[1]: (404, "")}; record_http(evidence, responses)
            with patch.object(release, "request", side_effect=py_http(responses)):
                compare(f"central:http-{status}", release.central_state, ["state", evidence])
        # Missing recorded inputs must fail closed on both sides; fresh directory has no prior fixtures.
        empty = temp / "empty"; empty.mkdir()
        with patch.object(release, "request", side_effect=py_http({})):
            compare("central:transport-timeout-unavailable", release.central_state, ["state", empty])
    (OUTPUT / "results.json").write_text(json.dumps(RESULTS, indent=2) + "\n")
    print(f"J3A parity: {len(RESULTS)} cases; {sum(r.get('intentional_tightening', False) for r in RESULTS)} intentional tightenings; zero unexplained drift")


if __name__ == "__main__":
    run()

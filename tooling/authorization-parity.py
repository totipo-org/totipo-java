"""J3B.1 host-only migration oracle; remove with the other Python oracles at J4.

Usage: python3 -B tooling/authorization-parity.py CLASSPATH EVIDENCE_DIR
No live network, key inspection, signing, or source mutation. All secrets are synthetic.
"""
import contextlib
import copy
import hashlib
import io
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
from types import SimpleNamespace
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "publishing"))
import release

CP, OUTPUT = sys.argv[1:]
OUTPUT = Path(OUTPUT)
OUTPUT.mkdir(parents=True, exist_ok=True)
JAVA = str(Path(os.environ["JAVA_HOME"]) / "bin/java")
SHA = "a" * 40
ENV = dict(REQUESTED_VERSION="0.2.0", REQUESTED_COMMIT=SHA, DISPATCH_REF="refs/heads/main",
           DISPATCH_SHA=SHA, GITHUB_REPOSITORY="totipo-org/totipo-java", GITHUB_RUN_ID="123", GITHUB_RUN_ATTEMPT="2")
RESULTS = []
RUN = subprocess.run


def java(*args, env=None):
    p = RUN([JAVA, "-cp", CP, "org.totipo.tooling.release.AuthorizationFixture", *map(str, args)],
                       capture_output=True, text=True, timeout=30, env=ENV if env is None else env)
    return p.returncode == 0, p.stdout.strip()


def python(action):
    try:
        with contextlib.redirect_stdout(io.StringIO()):
            return True, action()
    except (RuntimeError, KeyError, OSError, ValueError, TypeError, AttributeError, IndexError):
        return False, None


def compare(name, action, args, tightening=False, values=None, env=None):
    with patch.dict(os.environ, ENV if env is None else env, clear=True):
        py_ok, py_value = python(action)
    j_ok, j_value = java(*args, env=env)
    if args[0] == "policy":
        expected = {"publish": "CANDIDATE_PUBLISH", "verify": "VERIFY_EXISTING"}.get(py_value, "BLOCKED")
        agree = j_ok and (j_value == expected or (tightening and j_value == "BLOCKED" and expected != "BLOCKED"))
        tightened = expected != "BLOCKED" and j_value == "BLOCKED"
    else:
        agree = py_ok == j_ok or (tightening and py_ok and not j_ok)
        if agree and py_ok and j_ok and values:
            agree = values(py_value, j_value)
        tightened = py_ok and not j_ok
    RESULTS.append(dict(case=name, python=py_ok, java=j_ok, tightening=tightened, agreement=agree))
    assert agree, (name, py_ok, py_value, j_ok, j_value)


def history_url(page=1):
    return f"https://api.github.com/repos/{ENV['GITHUB_REPOSITORY']}/actions/runs/123/attempts/1/jobs?per_page=100&page={page}"


def job(start="now", conclusion="failure"):
    return dict(name="publish-central", head_sha=SHA,
                steps=[dict(name=release.UPLOAD_STEP, started_at=start, conclusion=conclusion)])


def recorded(root, responses):
    shutil.rmtree(root / "http", ignore_errors=True)
    (root / "http").mkdir()
    for url, (status, body) in responses.items():
        key = hashlib.sha256(url.encode("ascii")).hexdigest()
        (root / "http" / (key + ".status")).write_text(str(status) + "\n")
        (root / "http" / (key + ".body")).write_bytes(body)


def request(responses):
    def get(url, token=None):
        if url not in responses or responses[url][0] not in (200, 404):
            raise RuntimeError("Simulated API/transport failure")
        return responses[url]
    return get


def run():
    with tempfile.TemporaryDirectory(prefix="j3b1-parity-") as directory:
        work = Path(directory)
        evidence = work / "recorded"; evidence.mkdir()
        histories = [("no-start", job(None, "skipped"), False), ("skipped", job("now", "skipped"), False)]
        for conclusion in ("success", "failure", "cancelled", "timed_out", None):
            histories.append((str(conclusion), job("now", conclusion), False))
        for name, transform in [("missing-upload", lambda j: j.update(steps=[])),
                                ("wrong-run", lambda j: j.update(run_id=999)),
                                ("wrong-commit", lambda j: j.update(head_sha="b" * 40))]:
            j = job(None, "skipped"); transform(j); histories.append((name, j, name != "wrong-commit"))
        for core in (200, 404):
            for storage in (200, 404):
                base = {release.CENTRAL + f"org/totipo/totipo-{m}/0.2.0/totipo-{m}-0.2.0.pom": (s, b"")
                        for m, s in (("core", core), ("storage-nio", storage))}
                for name, j, tightening in histories:
                    responses = dict(base, **{history_url(): (200, json.dumps(dict(jobs=[j])).encode())})
                    recorded(evidence, responses)
                    with patch.object(release, "request", side_effect=request(responses)):
                        compare(f"policy:{core}:{storage}:{name}", release.central_state, ["policy", evidence], tightening=tightening)
                for name, status, body, tightening in [
                    ("missing-history", 404, b"", False), ("malformed", 200, b"{", False),
                    ("api-error", 503, b"", False), ("timeout", 0, b"", False),
                    ("empty-history", 200, b'{"jobs":[]}', True),
                    ("incomplete-total", 200, json.dumps(dict(total_count=2, jobs=[job(None, "skipped")])).encode(), True),
                    ("duplicate-json", 200, b'{"jobs":[],"jobs":[' + json.dumps(job(None, "skipped")).encode() + b']}', True)]:
                    responses = dict(base, **{history_url(): (status, body)})
                    recorded(evidence, responses)
                    with patch.object(release, "request", side_effect=request(responses)):
                        compare(f"policy:{core}:{storage}:{name}", release.central_state, ["policy", evidence], tightening=tightening)
                # Earlier upload cannot bypass incomplete later-page inspection.
                jobs = [job()] + [dict(name=f"job-{i}", head_sha=SHA, steps=[]) for i in range(99)]
                responses = dict(base, **{history_url(): (200, json.dumps(dict(jobs=jobs)).encode()), history_url(2): (503, b"")})
                recorded(evidence, responses)
                with patch.object(release, "request", side_effect=request(responses)):
                    compare(f"policy:{core}:{storage}:later-page-error", release.central_state, ["policy", evidence], tightening=True)
                # Attempt one has no history requests.
                recorded(evidence, base)
                with patch.object(release, "request", side_effect=request(base)):
                    compare(f"policy:{core}:{storage}:attempt-one", release.central_state, ["policy", evidence], env=dict(ENV, GITHUB_RUN_ATTEMPT="1"))
        for status in (401, 403, 429, 500, 0):
            responses = {release.CENTRAL + "org/totipo/totipo-core/0.2.0/totipo-core-0.2.0.pom": (status, b"")}
            recorded(evidence, responses)
            with patch.object(release, "request", side_effect=request(responses)):
                compare(f"policy:central-error:{status}", release.central_state, ["policy", evidence])

        root = work / "publication"
        assert java("create", root)[0]
        manifest = release.publication.load(root, "0.2.0")
        expected = work / "expected.json"
        with patch.object(release, "ROOT", root):
            compare("inventory:exact", release.inventory, ["inventory", root],
                    values=lambda value, output: dict(line.split("\t") for line in output.splitlines()) == value)
            variants = [("exact", json.dumps(manifest)), ("reordered", json.dumps({k: manifest[k] for k in reversed(manifest)}))]
            for key, value in (("schema", 2), ("version", "0.2.1")):
                variants.append((key, json.dumps(dict(manifest, **{key: value}))))
            for name, records in (("missing", manifest["artifacts"][:-1]), ("extra", manifest["artifacts"] * 2)):
                variants.append((name, json.dumps(dict(manifest, artifacts=records))))
            for key, value in (("module", "storage-nio"), ("role", "pom"), ("group", "other"), ("path", "../escape"), ("sha256", "0" * 64)):
                changed = copy.deepcopy(manifest); changed["artifacts"][0][key] = value
                variants.append(("artifact-" + key, json.dumps(changed)))
            changed = copy.deepcopy(manifest)
            for a in changed["artifacts"]:
                a["sha256"] = "0" * 64
            variants += [("all-ten-wrong", json.dumps(changed)), ("duplicate-key", '{"schema":1,' + json.dumps(manifest)[1:]),
                         ("trailing", json.dumps(manifest) + "{}"), ("malformed", "{")]
            for name, text in variants:
                expected.write_text(text)
                compare("compare:" + name, release.compare_inventory, ["compare", root, expected], env=dict(ENV, EXPECTED_INVENTORY=text))
            for name, relative in (("staging-artifact", manifest["artifacts"][0]["path"]), ("staging-index", "org/totipo/totipo-core/maven-metadata.xml"), ("sidecar", "sidecar.sha256")):
                file = root / "build/repository" / relative
                before = file.read_bytes() if file.exists() else None
                file.write_bytes(b"changed")
                compare(name, release.inventory, ["inventory", root])
                compare("signed:" + name, release.signed, ["signed", root])
                if before is None:
                    file.unlink()
                else:
                    file.write_bytes(before)
            compare("signed:exact", release.signed, ["signed", root])
            altered_fingerprint = dict(manifest, repositorySha256="0" * 64)
            text = json.dumps(altered_fingerprint); expected.write_text(text)
            compare("compare:index-fingerprint-excluded", release.compare_inventory, ["compare", root, expected], env=dict(ENV, EXPECTED_INVENTORY=text))
            for a in manifest["artifacts"]:
                name = {"pom": "pom-default.xml", "module": "module.json"}.get(a["role"], Path(a["path"]).name)
                unsigned = root / a["module"] / "build/signing" / name
                signature = unsigned.with_name(name + ".asc")
                sig = signature.read_bytes()
                signature.unlink(); compare("signed:missing:" + a["role"] + a["module"], release.signed, ["signed", root]); signature.write_bytes(sig)
                signature.write_bytes(b"-----BEGIN PGP SIGNATURE-----"); compare("signed:truncated:" + name, release.signed, ["signed", root]); signature.write_bytes(sig)
                before = unsigned.read_bytes(); unsigned.write_bytes(b"wrong")
                compare("signed:wrong-input:" + name, release.signed, ["signed", root]); unsigned.write_bytes(before)
                duplicate = signature.parent.parent / "duplicate" / signature.name
                duplicate.parent.mkdir(exist_ok=True); duplicate.write_bytes(sig)
                compare("signed:duplicate:" + name, release.signed, ["signed", root]); duplicate.unlink()
                if a["role"] in ("pom", "module"):
                    wrong = signature.with_name(Path(a["path"]).name + ".asc")
                    signature.rename(wrong)
                    compare("signed:wrong-filename:" + name + a["module"], release.signed, ["signed", root])
                    wrong.rename(signature)
                other = "storage-nio" if a["module"] == "core" else "core"
                wrong = root / other / "build/wrong" / signature.name
                wrong.parent.mkdir(exist_ok=True); signature.rename(wrong)
                compare("signed:wrong-module:" + name + a["module"], release.signed, ["signed", root]); wrong.rename(signature)
            # Explicit measured filesystem tightening: Python follows a symlink to identical bytes.
            unsigned = root / "core/build/signing/totipo-core-0.2.0.jar"
            before = unsigned.read_bytes(); outside = work / "outside"; outside.write_bytes(before)
            unsigned.unlink(); unsigned.symlink_to(outside)
            compare("signed:symlink-tightening", release.signed, ["signed", root], tightening=True)
            unsigned.unlink(); unsigned.write_bytes(before)
        packet = work / "packets.txt"
        secrets = {release_key: "synthetic-secret" for release_key in ("ORG_GRADLE_PROJECT_" + p for p in ("mavenCentralUsername", "mavenCentralPassword", "signingInMemoryKey"))}
        for name, text, code, password, missing, tightening in [
            ("unprotected", ":secret key packet:\n\tskey[3]: [2048 bits]\n", 0, False, None, False),
            ("protected-missing-password", ":secret key packet:\n\tskey[3]: [v4 protected]\n", 0, False, None, False),
            ("protected-password", ":secret key packet:\n\tprotect algo: 7\n", 0, True, None, False),
            ("unreadable", ":public key packet:\n", 0, False, None, False),
            ("gpg-failure", ":secret key packet:\n", 1, False, None, True),
            ("unexpected-output", ":secret key packet:\nnot packet output\n", 0, False, None, True),
            ("malformed", "", 0, False, None, False),
            ("oversized", ":secret key packet:\n\t" + "x" * (1024 * 1024) + "\n", 0, False, None, True),
            ("missing-username", ":secret key packet:\n", 0, False, "mavenCentralUsername", False),
            ("missing-password", ":secret key packet:\n", 0, False, "mavenCentralPassword", False),
            ("missing-key", ":secret key packet:\n", 0, False, "signingInMemoryKey", False),
        ]:
            packet.write_text(text)
            env = dict(ENV, **secrets)
            if password:
                env["ORG_GRADLE_PROJECT_signingInMemoryKeyPassword"] = "synthetic-password"
            if missing:
                env.pop("ORG_GRADLE_PROJECT_" + missing)
            with patch.object(release.subprocess, "run", return_value=SimpleNamespace(stdout=text, stderr="private diagnostic", returncode=code)):
                compare("secrets:" + name, release.secrets, ["secrets", packet, code], env=env, tightening=tightening)
        for name, failure in (("timeout", subprocess.TimeoutExpired("fake-gpg", 1)), ("unavailable", OSError("fake failure"))):
            packet.write_text("")
            def fail():
                try:
                    release.secrets()
                except subprocess.TimeoutExpired:
                    raise RuntimeError("Simulated timeout") from None
            with patch.object(release.subprocess, "run", side_effect=failure):
                compare("secrets:" + name, fail, ["secrets", packet, "1"], env=dict(ENV, **secrets))
    (OUTPUT / "results.json").write_text(json.dumps(RESULTS, indent=2, sort_keys=True) + "\n")
    print(f"PASS: {len(RESULTS)} differential cases; {sum(r['tightening'] for r in RESULTS)} conservative tightenings; zero unexplained drift")


if __name__ == "__main__":
    run()

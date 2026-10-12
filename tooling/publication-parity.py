"""J2B migration oracle runner. Never called by Java or live publication tasks.

Usage: python3 -B tooling/publication-parity.py FIXTURE_EXPORT LAUNCHER [ARG ...]
Run PublicationTest with FIXTURE_EXPORT first. LAUNCHER may be `java -cp ... Main`.
All oracle invocations operate on disposable fixture roots, never live staging.
"""
import copy
import hashlib
import importlib.util
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[1]


def load_publication():
    spec = importlib.util.spec_from_file_location("publication", ROOT / "publishing/publication.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def main():
    fixtures = Path(sys.argv[1]).resolve()
    launcher = sys.argv[2:]
    assert launcher
    publication = load_publication()
    results = []

    def java(*args):
        return subprocess.run(launcher + ["publication", *map(str, args)], capture_output=True, text=True)

    for line in (fixtures / "cases.tsv").read_text().splitlines():
        name, expected_java, expected_python = line.split("\t")
        root = fixtures / name
        publishing = root / "publishing"
        publishing.mkdir(exist_ok=True)
        for script in ("publication.py", "verify-publication.py"):
            shutil.copyfile(ROOT / "publishing" / script, publishing / script)
        py = subprocess.run([sys.executable, "-B", str(publishing / "verify-publication.py")], capture_output=True, text=True)
        with tempfile.TemporaryDirectory(prefix="j2b-parity-output-") as directory:
            output = Path(directory)
            j = java("verify", root, root / "build/repository", output / "manifest.json", output / "consumer.xml")
            dispositions = (j.returncode == 0, py.returncode == 0)
            assert dispositions == (expected_java == "true", expected_python == "true"), (name, dispositions, j.stderr, py.stderr)
            if all(dispositions):
                assert (output / "manifest.json").read_bytes() == (root / publication.MANIFEST).read_bytes(), name
                assert (output / "consumer.xml").read_bytes() == (publishing / "consumer-smoke/gradle/verification-metadata.xml").read_bytes(), name
            results.append(dict(case=name, java=dispositions[0], python=dispositions[1], tightening=dispositions[0] != dispositions[1]))

    # Independent Python serialization bytes and SHA, including supplementary/BMP sorting.
    values = {'z/\\"\n': 'é😀\x7f\b\f\r\t', '\ue000': 'BMP', '😀': 'astral'}
    encoded = json.dumps(values, sort_keys=True).encode()
    assert encoded == (fixtures / "fingerprint-encoding.json").read_bytes()
    assert hashlib.sha256(encoded).hexdigest() == (fixtures / "fingerprint-encoding.sha256").read_text()

    assert publication.repository_sha256(fixtures / "fingerprint-repository") == (fixtures / "repository-fingerprint.sha256").read_text()

    with tempfile.TemporaryDirectory(prefix="j2b-manifest-parity-") as directory:
        work = Path(directory)
        root = work / "root"
        shutil.copytree(fixtures / "valid", root)
        repo = root / "build/repository"
        accepted = publication.load(root, "0.2.0")
        manifest = work / "manifest.json"

        def compare(name, data, mutate=None, operation="check", destination=None, expected=False):
            manifest.write_text(data)
            if mutate:
                mutate()
            try:
                parsed = publication.parse(data, "0.2.0")
                if operation == "check":
                    publication.check_staging(parsed, repo)
                else:
                    publication.copy_accepted(parsed, repo, destination / "python")
                py_ok = True
            except (RuntimeError, OSError, ValueError, TypeError):
                py_ok = False
            args = [operation, root, repo, manifest]
            if destination:
                args.append(destination / "java")
            j = java(*args)
            assert py_ok == expected and (j.returncode == 0) == expected, (name, py_ok, j.stderr)
            results.append(dict(case=name, java=j.returncode == 0, python=py_ok, tightening=False))

        compare("manifest-valid", json.dumps(accepted), expected=True)
        for name, key, value in [("schema-bool", "schema", True), ("schema-float", "schema", 1.0), ("schema-wrong", "schema", 2), ("version-wrong", "version", "0.3.0"), ("unknown-field", "unknown", 1)]:
            data = dict(accepted, **{key: value})
            compare(name, json.dumps(data))
        compare("duplicate-manifest-key", '{"schema":1,' + json.dumps(accepted)[1:])
        for name, records in [("missing-record", accepted["artifacts"][:-1]), ("extra-record", accepted["artifacts"] * 2), ("duplicate-path", [accepted["artifacts"][0]] * 10)]:
            compare(name, json.dumps(dict(accepted, artifacts=records)))
        for key, value in [("module", "wrong"), ("group", "wrong"), ("artifact", "wrong"), ("version", "0.3.0"), ("role", "bad"), ("suffix", ".bad"), ("path", "../escape"), ("sha256", "G" * 64)]:
            data = copy.deepcopy(accepted); data["artifacts"][0][key] = value
            compare("artifact-" + key, json.dumps(data))
        data = copy.deepcopy(accepted)
        a, b = data["artifacts"][:2]; a["sha256"], b["sha256"] = b["sha256"], a["sha256"]
        compare("swapped-hashes", json.dumps(data))
        for name, path in [("changed-artifact", accepted["artifacts"][0]["path"]), ("changed-index", "org/totipo/totipo-core/maven-metadata.xml"), ("changed-sidecar", "index.sha256"), ("extra-staging", "unexpected")]:
            file = repo / path
            prior = file.read_bytes() if file.exists() else None
            compare(name, json.dumps(accepted), lambda: file.write_bytes(b"changed"))
            if prior is None:
                file.unlink()
            else:
                file.write_bytes(prior)
        compare("copy-valid", json.dumps(accepted), operation="copy", destination=work / "copy", expected=True)
        # Java deliberately requires an empty destination rather than overwriting.
        publication.copy_accepted(accepted, repo, work / "copy/python")
        repeat = java("copy", root, repo, manifest, work / "copy/java")
        assert repeat.returncode != 0
        results.append(dict(case="copy-existing-tightening", java=False, python=True, tightening=True))
        for implementation in ("java", "python"):
            dest = work / "copy" / implementation
            publication.check_artifacts(accepted, dest)
            (dest / accepted["artifacts"][0]["path"]).write_bytes(b"corrupt")
            try:
                publication.check_artifacts(accepted, dest)
            except RuntimeError:
                pass
            else:
                raise AssertionError("Corrupt copy accepted")
        for implementation in ("java", "python"):
            dest = work / "extra" / implementation
            dest.mkdir(parents=True); (dest / "unexpected").write_bytes(b"extra")
        compare("copy-extra-destination", json.dumps(accepted), operation="copy", destination=work / "extra")

    (fixtures / "parity-results.json").write_text(json.dumps(results, indent=2, sort_keys=True) + "\n")
    print(f"PASS: {len(results)} differential cases; {sum(r['tightening'] for r in results)} documented tightenings; zero unexplained drift")


if __name__ == "__main__":
    main()

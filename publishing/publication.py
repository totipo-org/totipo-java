"""Accepted unsigned publication model; semantic acceptance belongs to the verifier.

Candidates never define expected names. Consumers validate identity here and make
fresh byte observations at their own staging, copy, signing or hosted boundary.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = Path("build/publication-manifest.json")
MODULES = ("core", "storage-nio")
ROLES = {"binary": ".jar", "sources": "-sources.jar", "javadoc": "-javadoc.jar",
         "pom": ".pom", "module": ".module"}


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def expected_artifacts(version):
    require(isinstance(version, str) and re.fullmatch(
        r"[0-9]+\.[0-9]+\.[0-9]+(?:-[A-Za-z0-9]+(?:[.-][A-Za-z0-9]+)*)?", version), "Invalid manifest version")
    return [dict(module=module, group="org.totipo", artifact=f"totipo-{module}",
                 version=version, role=role, suffix=suffix,
                 path=f"org/totipo/totipo-{module}/{version}/totipo-{module}-{version}{suffix}")
            for module in MODULES for role, suffix in ROLES.items()]


def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def repository_sha256(repository):
    # Include indexes/sidecars and the exact file set, so evidence reuse proves
    # unchanged staging, including files outside the ten reproducible artifacts.
    files = {p.relative_to(repository).as_posix(): sha256(p)
             for p in sorted(repository.rglob("*")) if p.is_file()}
    return hashlib.sha256(json.dumps(files, sort_keys=True).encode()).hexdigest()


def validate(data, version):
    require(isinstance(data, dict) and set(data) == {"schema", "version", "repositorySha256", "artifacts"}, "Invalid manifest fields")
    require(type(data["schema"]) is int and data["schema"] == 1 and data["version"] == version, "Wrong manifest schema/version")
    records = data["artifacts"]
    expected = {a["path"]: a for a in expected_artifacts(version)}
    require(isinstance(records, list) and len(records) == len(expected), "Expected 10 unsigned artifacts")
    paths = set()
    for record in records:
        require(isinstance(record, dict) and set(record) == set(next(iter(expected.values()))) | {"sha256"}, "Invalid artifact fields")
        path = record["path"]
        require(isinstance(path, str) and path not in paths, "Duplicate/invalid artifact path")
        paths.add(path)
        require(path in expected and {k: v for k, v in record.items() if k != "sha256"} == expected[path], "Wrong artifact module/path/role")
        require(isinstance(record["sha256"], str) and re.fullmatch(r"[0-9a-f]{64}", record["sha256"]), "Invalid artifact SHA-256")
    require(paths == set(expected), "Missing/extra artifact")
    require(isinstance(data["repositorySha256"], str) and re.fullmatch(r"[0-9a-f]{64}", data["repositorySha256"]), "Invalid repository SHA-256")
    return data


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        require(key not in result, f"Duplicate JSON field: {key}")
        result[key] = value
    return result


def parse(text, version):
    try:
        return validate(json.loads(text, object_pairs_hook=unique_object), version)
    except (ValueError, TypeError) as error:
        raise RuntimeError("Malformed publication manifest") from error


def load(root, version):
    return parse((root / MANIFEST).read_text(), version)


def hashes(manifest):
    return {a["path"]: a["sha256"] for a in manifest["artifacts"]}


def check_artifacts(manifest, repository):
    for path, digest in hashes(manifest).items():
        require(sha256(repository / path) == digest, f"Artifact hash mismatch: {path}")


def check_staging(manifest, repository):
    check_artifacts(manifest, repository)
    require(repository_sha256(repository) == manifest["repositorySha256"], "Staged repository changed since semantic acceptance")


def accept(repository, version):
    """Called only after the semantic verifier accepts the complete repository."""
    records = [dict(a, sha256=sha256(repository / a["path"])) for a in expected_artifacts(version)]
    return validate(dict(schema=1, version=version, artifacts=records,
                         repositorySha256=repository_sha256(repository)), version)


def copy_accepted(manifest, repository, destination):
    check_staging(manifest, repository)
    for path in hashes(manifest):
        target = destination / path
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(repository / path, target)
    # Copying is a transformation boundary: read destination bytes independently.
    check_artifacts(manifest, destination)
    require({p.relative_to(destination).as_posix() for p in destination.rglob("*") if p.is_file()} == set(hashes(manifest)), "Unexpected copied artifacts")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("check", "copy"))
    parser.add_argument("destination", nargs="?", type=Path)
    args = parser.parse_args()
    version = (ROOT / "VERSION").read_text().removesuffix("\n")
    manifest = load(ROOT, version)
    if args.command == "copy":
        require(args.destination is not None, "copy requires destination")
        copy_accepted(manifest, ROOT / "build/repository", args.destination)
    else:
        require(args.destination is None, "check takes no destination")
        check_staging(manifest, ROOT / "build/repository")
    print("10/10 accepted publication artifacts: fresh byte checks passed")


if __name__ == "__main__":
    main()

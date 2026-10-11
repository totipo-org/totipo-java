"""Manifest identity and fresh-byte observations without Gradle or network."""
import copy
import json
from pathlib import Path
import tempfile
import unittest

import publication
import release
from unittest.mock import patch


class ManifestTests(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.root = Path(directory.name)
        self.repo = self.root / "build/repository"
        self.version = "0.2.0"
        for artifact in publication.expected_artifacts(self.version):
            path = self.repo / artifact["path"]
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(artifact["module"] + artifact["role"])
        self.manifest = publication.accept(self.repo, self.version)

    def test_roundtrip_and_copy_fresh_bytes(self):
        parsed = publication.parse(json.dumps(self.manifest), self.version)
        self.assertEqual(parsed, self.manifest)
        publication.check_staging(parsed, self.repo)
        target = self.root / "output"
        publication.copy_accepted(parsed, self.repo, target)
        self.assertEqual(publication.hashes(parsed), {
            p.relative_to(target).as_posix(): publication.sha256(p)
            for p in target.rglob("*") if p.is_file()})
        (target / parsed["artifacts"][0]["path"]).write_bytes(b"corrupt copy")
        with self.assertRaisesRegex(RuntimeError, "hash mismatch"):
            publication.check_artifacts(parsed, target)

    def test_missing_extra_and_wrong_count(self):
        for records in (self.manifest["artifacts"][:-1], self.manifest["artifacts"] * 2, []):
            candidate = dict(self.manifest, artifacts=records)
            with self.subTest(count=len(records)), self.assertRaises(RuntimeError):
                publication.validate(candidate, self.version)

    def test_duplicate_path(self):
        candidate = copy.deepcopy(self.manifest)
        candidate["artifacts"][-1] = candidate["artifacts"][0]
        with self.assertRaisesRegex(RuntimeError, "Duplicate"):
            publication.validate(candidate, self.version)

    def test_wrong_identity_path_and_artifact_substitution(self):
        for key, value in (("module", "storage-nio"), ("group", "other"),
                           ("artifact", "totipo-storage-nio"), ("version", "0.2.1"),
                           ("role", "sources"), ("suffix", "-sources.jar"),
                           ("path", "../outside.jar"), ("path", "unlisted-extra.jar")):
            candidate = copy.deepcopy(self.manifest)
            candidate["artifacts"][0][key] = value
            with self.subTest(key=key), self.assertRaisesRegex(RuntimeError, "module/path/role"):
                publication.validate(candidate, self.version)
        # Swapping hashes across roles/modules cannot satisfy fresh observations.
        for other in (1, 5):
            candidate = copy.deepcopy(self.manifest)
            a, b = candidate["artifacts"][0], candidate["artifacts"][other]
            a["sha256"], b["sha256"] = b["sha256"], a["sha256"]
            with self.subTest(other=other), self.assertRaisesRegex(RuntimeError, "hash mismatch"):
                publication.check_staging(publication.validate(candidate, self.version), self.repo)

    def test_malformed_sha(self):
        for digest in ("0" * 63, "G" * 64, "A" * 64, "0" * 64 + "\n", None, 42):
            candidate = copy.deepcopy(self.manifest)
            candidate["artifacts"][0]["sha256"] = digest
            with self.subTest(digest=digest), self.assertRaisesRegex(RuntimeError, "SHA-256"):
                publication.validate(candidate, self.version)

    def test_hash_mismatch_and_missing_bytes(self):
        path = self.repo / self.manifest["artifacts"][0]["path"]
        path.write_bytes(b"changed")
        with self.assertRaisesRegex(RuntimeError, "hash mismatch"):
            publication.check_staging(self.manifest, self.repo)
        path.unlink()
        with self.assertRaises(OSError):
            publication.check_staging(self.manifest, self.repo)

    def test_staging_extra_or_changed_sidecar_invalidates_acceptance(self):
        extra = self.repo / "unexpected.jar"
        extra.write_bytes(b"unexpected")
        with self.assertRaisesRegex(RuntimeError, "repository changed"):
            publication.check_staging(self.manifest, self.repo)
        extra.unlink()
        sidecar = self.repo / "maven-metadata.xml"
        sidecar.write_bytes(b"accepted index")
        accepted = publication.accept(self.repo, self.version)
        sidecar.write_bytes(b"changed index")
        with self.assertRaisesRegex(RuntimeError, "repository changed"):
            publication.check_staging(accepted, self.repo)

    def test_schema_version_fields_and_duplicate_json_rejected(self):
        for candidate in (dict(self.manifest, schema=True), dict(self.manifest, schema=2),
                          dict(self.manifest, version="0.2.1"), dict(self.manifest, extra=1), {}):
            with self.subTest(candidate=candidate), self.assertRaises(RuntimeError):
                publication.validate(candidate, self.version)
        text = json.dumps(self.manifest)
        with self.assertRaisesRegex(RuntimeError, "Duplicate JSON"):
            publication.parse('{"schema":1,' + text[1:], self.version)

    def test_release_parses_expected_and_rereads_current_bytes(self):
        (self.root / publication.MANIFEST).write_text(json.dumps(self.manifest))
        with patch.object(release, "ROOT", self.root), patch.object(release, "inputs", return_value=(self.version, "a" * 40)), patch.dict(release.os.environ, EXPECTED_INVENTORY=json.dumps(self.manifest)):
            release.compare_inventory()
            changed = copy.deepcopy(self.manifest)
            changed["artifacts"][0]["sha256"] = "0" * 64
            with patch.dict(release.os.environ, EXPECTED_INVENTORY=json.dumps(changed)):
                with self.assertRaisesRegex(RuntimeError, "differs from preflight"):
                    release.compare_inventory()
            with patch.dict(release.os.environ, EXPECTED_INVENTORY='{}'):
                with self.assertRaises(RuntimeError):
                    release.compare_inventory()
            (self.repo / self.manifest["artifacts"][0]["path"]).write_bytes(b"later mutation")
            with self.assertRaisesRegex(RuntimeError, "hash mismatch"):
                release.inventory()

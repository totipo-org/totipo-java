"""Isolated local preparation tests; remote and publication paths are tripwires."""
import contextlib
import io
from pathlib import Path
import tempfile
import shutil
import sys
import unittest
from unittest.mock import patch

import release


class PrepareTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.addCleanup(patch.stopall)
        patch.object(release, "ROOT", self.root).start()
        self.real_integrity = release.source_integrity
        self.repository = Path(release.__file__).resolve().parents[1]
        self.integrity = patch.object(release, "source_integrity").start()
        self.git = patch.object(release, "git", side_effect=self.read_git).start()
        for name in ("request", "identity", "tag_release", "secrets", "central_state"):
            patch.object(release, name, side_effect=AssertionError(f"Forbidden: {name}")).start()
        patch.object(release.subprocess, "run", side_effect=AssertionError("Forbidden process")).start()
        patch.object(release.subprocess, "check_output", side_effect=AssertionError("Forbidden process")).start()
        self.branch, self.dirty = "main", ""
        self.write("VERSION", "0.1.5\n")
        self.write("RELEASE_CHECKLIST.md", """# Totipo Java 0.1.5 release checklist
VERSION remains 0.1.5 during the unreleased v1/r19 breaking simplification. Choose
and review a new release version and matching notes before using this checklist.
org.totipo:totipo-core:0.1.5
org.totipo:totipo-storage-nio:0.1.5
`v0.1.5`
review/V0_1_5_RELEASE_NOTES.md
`version`: `0.1.5`
""")
        self.write(".github/workflows/release.yml", "Exact VERSION to release (for example, 0.1.5)\n")
        self.write("publishing/consumer-smoke/gradle.lockfile", "org.bouncycastle:bcprov-jdk18on:1.86=runtimeClasspath\n" + "".join(
            f"org.totipo:totipo-{module}:0.1.5=compileClasspath,runtimeClasspath,testCompileClasspath,testRuntimeClasspath\n"
            for module in ("core", "storage-nio")))
        self.write("README.md", """Published dependency org.totipo:totipo-core:0.1.4
`VERSION` is the single implementation version source and remains **0.1.5** for
this local unreleased implementation. The current v1/r19 change intentionally
simplifies pre-1.0 APIs and is breaking; the report recommends a separate minor
release selection. The published 0.1.4 examples above are unchanged.
""")
        self.write("API_DESIGN.md", "VERSION remains 0.1.5 during this unreleased breaking simplification; release\nversion selection is separate.\n")
        self.write("review/V0_1_5_RELEASE_NOTES.md", "## v0.1.5\nHistorical r18 notes\n")
        self.write("review/history.md", "0.1.4 / 0.1.5 / r18 / r19 provenance\n")
        output = contextlib.redirect_stdout(io.StringIO())
        output.__enter__()
        self.addCleanup(output.__exit__, None, None, None)

    def read_git(self, *args):
        if args == ("branch", "--show-current"):
            return self.branch
        if args == ("status", "--porcelain", "--untracked-files=all"):
            return self.dirty
        raise AssertionError(f"Forbidden Git operation: {args}")

    def write(self, path, text):
        file = self.root / path
        file.parent.mkdir(parents=True, exist_ok=True)
        file.write_text(text)

    def files(self):
        return {str(p.relative_to(self.root)): p.read_bytes() for p in self.root.rglob("*") if p.is_file()}

    def finish_notes(self):
        path = self.root / "review/V0_2_0_RELEASE_NOTES.md"
        path.write_text(path.read_text().replace("TODO:", "Reviewed:"))

    def test_valid_preparation_exact_paths_history_and_locks(self):
        before = self.files()
        release.prepare("0.2.0")
        after = self.files()
        changed = {p for p in after if before.get(p) != after[p]}
        self.assertEqual(changed, {"VERSION", "RELEASE_CHECKLIST.md", ".github/workflows/release.yml",
            "README.md", "API_DESIGN.md", "publishing/consumer-smoke/gradle.lockfile", "review/V0_2_0_RELEASE_NOTES.md"})
        self.assertEqual(after["review/history.md"], before["review/history.md"])
        self.assertEqual(after["review/V0_1_5_RELEASE_NOTES.md"], before["review/V0_1_5_RELEASE_NOTES.md"])
        self.assertIn(b"totipo-core:0.1.4", after["README.md"])
        self.assertEqual(after["publishing/consumer-smoke/gradle.lockfile"], before["publishing/consumer-smoke/gradle.lockfile"].replace(b":0.1.5=", b":0.2.0="))
        self.assertTrue(release.reviewed_notes("0.2.0").startswith("## v0.2.0\n"))
        self.integrity.assert_called_once()

    def test_dry_run_writes_nothing(self):
        before = self.files()
        release.prepare("0.2.0", dry_run=True)
        self.assertEqual(self.files(), before)

    def test_repeated_check_is_write_free_mutation_rejects_equal(self):
        release.prepare("0.2.0")
        with self.assertRaisesRegex(RuntimeError, "still need review"):
            release.prepare("0.2.0", check=True)
        self.finish_notes()
        self.dirty = " M VERSION"
        before = self.files()
        release.prepare("0.2.0", check=True)
        release.prepare("0.2.0", check=True)
        self.assertEqual(self.files(), before)
        with self.assertRaisesRegex(RuntimeError, "newer"):
            release.prepare("0.2.0")

    def test_dirty_initial_tree_and_dry_run_rejected(self):
        self.dirty = "?? implementation.java"
        for dry in (False, True):
            with self.assertRaisesRegex(RuntimeError, "clean working tree"):
                release.prepare("0.2.0", dry_run=dry)

    def test_malformed_equal_and_downgrade_rejected(self):
        for target in ("0.1.5", "0.1.4", "0.0.9", "0.02.0", "0.2", "0.2.0\n", "0.2.0-SNAPSHOT", "0.2.0-rc1", "$(id)"):
            with self.subTest(target=target), self.assertRaises(RuntimeError):
                release.prepare(target)

    def test_unexpected_current_version_and_layout_rejected(self):
        for value in ("0.1.5", "0.1.5\n\n", "0.1.6\n", "nonsense\n"):
            self.write("VERSION", value)
            with self.subTest(value=value), self.assertRaises(RuntimeError):
                release.prepare("0.2.0")

    def test_wrong_branch_and_integrity_rejected(self):
        self.branch = "feature"
        with self.assertRaisesRegex(RuntimeError, "main"):
            release.prepare("0.2.0")
        self.branch = "main"
        self.integrity.side_effect = RuntimeError("Exact reviewed r19 spec pin changed")
        with self.assertRaisesRegex(RuntimeError, "pin changed"):
            release.prepare("0.2.0")

    def test_missing_metadata_and_ambiguous_occurrences_fail_before_writes(self):
        for path, extra in (("API_DESIGN.md", "unexpected layout"),
                            (".github/workflows/release.yml", "extra 0.1.5"),
                            ("README.md", "extra 0.1.5"),
                            ("RELEASE_CHECKLIST.md", "extra 0.1.5")):
            before = self.files()
            file = self.root / path
            file.write_text(extra if path == "API_DESIGN.md" else file.read_text() + extra)
            modified = self.files()
            with self.subTest(path=path), self.assertRaises(RuntimeError):
                release.prepare("0.2.0")
            self.assertEqual(self.files(), modified)
            file.write_bytes(before[path])

    def test_notes_exact_selection_heading_and_template_contract(self):
        self.write("review/V0_2_0_RELEASE_NOTES.md", "## v0.1.5\n")
        with self.assertRaisesRegex(RuntimeError, "requested version"):
            release.prepare("0.2.0")
        self.write("review/V0_2_0_RELEASE_NOTES.md", "## v0.2.0\n")
        with self.assertRaisesRegex(RuntimeError, "section contract"):
            release.prepare("0.2.0")
        (self.root / "review/V0_2_0_RELEASE_NOTES.md").unlink()
        with patch.object(release, "NOTES_TEMPLATE", "## v{version}\n"), self.assertRaisesRegex(RuntimeError, "template contract"):
            release.prepare("0.2.0")

    def test_future_release_reuses_markers(self):
        release.prepare("0.2.0")
        release.prepare("0.2.1")
        self.assertEqual((self.root / "VERSION").read_text(), "0.2.1\n")
        self.assertIn("Java 0.2.1 is prepared", (self.root / "README.md").read_text())

    def test_check_rejects_inconsistent_lock(self):
        release.prepare("0.2.0")
        self.finish_notes()
        lock = self.root / "publishing/consumer-smoke/gradle.lockfile"
        lock.write_text(lock.read_text().replace("totipo-core:0.2.0", "totipo-core:0.1.5"))
        with self.assertRaises(RuntimeError):
            release.prepare("0.2.0", check=True)

    def test_real_pin_and_snapshot_integrity_and_corruption(self):
        shutil.copyfile(self.repository / "SPEC_PIN.md", self.root / "SPEC_PIN.md")
        relative = "core/src/test/resources/totipo-spec/v1-pre-rc"
        shutil.copytree(self.repository / relative, self.root / relative)
        self.real_integrity()
        profile = self.root / relative / "requirements/v1-pre-rc.json"
        profile.write_bytes(profile.read_bytes() + b" ")
        with self.assertRaisesRegex(RuntimeError, "Snapshot integrity failure"):
            self.real_integrity()
        (self.root / "SPEC_PIN.md").write_text("wrong r19 pin")
        with self.assertRaisesRegex(RuntimeError, "pin changed"):
            self.real_integrity()

    def test_cli_routes_only_preparation_and_rejects_unrelated_options(self):
        before = self.files()
        with patch.object(sys, "argv", ["release.py", "prepare", "0.2.0", "--dry-run"]):
            release.main()
        self.assertEqual(self.files(), before)
        for argv in (["release.py", "prepare"], ["release.py", "tests", "--check"]):
            with patch.object(sys, "argv", argv), self.assertRaises(RuntimeError):
                release.main()

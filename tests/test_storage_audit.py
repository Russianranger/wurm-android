"""Real SQLite tests on disposable handwritten fixtures; no proprietary Wurm files."""
import hashlib
import os
from pathlib import Path
import shutil
import sqlite3
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
JAR = os.environ.get("WURM_TEST_SQLITE_JAR")


@unittest.skipUnless(JAR and shutil.which("java"), "Set WURM_TEST_SQLITE_JAR to the pinned SQLite JDBC JAR")
class StorageAuditTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        jar = Path(JAR).resolve()
        if hashlib.sha256(jar.read_bytes()).hexdigest() != "f55e405ed96d5ffe629e05b7b51b059e1c7d64527c0cc90a972fbac06730ccc1":
            raise AssertionError("Test JDBC JAR does not match the Thor pin")
        cls.compiled = tempfile.TemporaryDirectory(prefix="wurm-audit-classes-")
        cls.addClassCleanup(cls.compiled.cleanup)
        subprocess.run(["java", "com.sun.tools.javac.Main", "--release", "17", "-d", cls.compiled.name,
                        str(ROOT / "runtime-probe/src/persistence/StorageAudit.java")], check=True, capture_output=True)
        cls.cp = cls.compiled.name + os.pathsep + str(jar)

    def setUp(self):
        self.work = tempfile.TemporaryDirectory(prefix="wurm-audit-test-")
        self.addCleanup(self.work.cleanup)
        self.folder = Path(self.work.name)
        self.runtime = self.folder / "work-fixture"
        (self.runtime / "Adventure").mkdir(parents=True)
        (self.runtime / "localhost/sqlite").mkdir(parents=True)
        self.database = self.runtime / "localhost/sqlite/wurm ?# test.db"
        with sqlite3.connect(self.database) as db:
            db.execute('CREATE TABLE "quoted""table" (id INTEGER PRIMARY KEY, value TEXT)')
            db.execute('INSERT INTO "quoted""table" VALUES (1, "fixture")')
        self.store = self.folder / "audit"

    def audit(self, mode="check", world="Adventure", root=None, success=True):
        result = subprocess.run(["java", "-cp", self.cp, "persistence.StorageAudit", str(root or self.runtime),
                                 str(self.store), world, mode], capture_output=True, text=True, timeout=30)
        if success:
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        else:
            self.assertNotEqual(result.returncode, 0, result.stdout + result.stderr)
            self.assertNotIn("[audit] STORAGE_CHECK_COMPLETE", result.stdout)
            self.assertNotIn("[audit] BASELINE_CAPTURED", result.stdout)
        return (self.store / "report.txt").read_text()

    def identities(self):
        return {str(p.relative_to(self.runtime)): hashlib.sha256(p.read_bytes()).hexdigest()
                for p in self.runtime.rglob("*") if p.is_file()}

    def test_baseline_diff_and_last_check_survive_separate_jvms_without_source_writes(self):
        (self.runtime / "Adventure/removed").write_text("old")
        (self.runtime / "Adventure/changed").write_text("before")
        before = self.identities()
        self.assertIn("BASELINE_CAPTURED", self.audit("baseline"))
        self.assertEqual(before, self.identities())
        report = self.audit()
        self.assertIn("BASELINE_MATCH", report)
        self.assertIn("LAST_CHECK_NONE", report)
        (self.runtime / "Adventure/removed").unlink()
        (self.runtime / "Adventure/changed").write_text("after")
        (self.runtime / "Adventure/added").write_text("new")
        before = self.identities()
        report = self.audit()
        self.assertIn("added=1 changed=1 removed=1", report)
        self.assertIn("SOURCE_UNCHANGED", report)
        self.assertEqual(before, self.identities())
        self.assertIn("LAST_CHECK_MATCH", self.audit())
        self.audit("baseline")
        self.assertIn("LAST_CHECK_NONE", self.audit())

    def test_real_database_change_is_counted_then_survives_reopen(self):
        self.audit("baseline")
        with sqlite3.connect(self.database) as db:
            db.execute('INSERT INTO "quoted""table" VALUES (2, "saved change")')
        before = self.identities()
        report = self.audit()
        self.assertIn("BASELINE_DIFF", report)
        self.assertIn("tables=1 rows=2", report)
        self.assertEqual(before, self.identities())
        self.assertIn("LAST_CHECK_MATCH", self.audit())

    def test_wal_commits_are_checked_on_copies_with_original_sidecars_unchanged(self):
        connection = sqlite3.connect(self.database)
        self.addCleanup(connection.close)
        connection.execute("PRAGMA journal_mode=WAL")
        connection.execute("PRAGMA wal_autocheckpoint=0")
        connection.execute('INSERT INTO "quoted""table" VALUES (2, "wal fixture")')
        connection.commit()
        self.assertTrue(Path(str(self.database) + "-wal").is_file())
        before = self.identities()
        report = self.audit("baseline")
        self.assertIn("tables=1 rows=2", report)
        self.assertIn("wal=true", report)
        self.assertEqual(before, self.identities())

    def test_corrupt_database_never_replaces_successful_snapshot(self):
        self.audit("baseline")
        baseline = (self.store / "baseline.bin").read_bytes()
        self.database.write_bytes(b"not a sqlite database" * 100)
        before = self.identities()
        report = self.audit("baseline", success=False)
        self.assertIn("DB_CHECK_FAILED", report)
        self.assertEqual(baseline, (self.store / "baseline.bin").read_bytes())
        self.assertEqual(before, self.identities())

    def test_world_or_working_copy_change_requires_explicit_new_baseline(self):
        self.audit("baseline")
        (self.runtime / "Other").mkdir()
        self.assertIn("another working copy/world", self.audit(world="Other", success=False))
        replacement = self.folder / "work-restored"
        shutil.copytree(self.runtime, replacement)
        self.assertIn("another working copy/world", self.audit(root=replacement, success=False))

    def test_symlinks_and_no_database_cannot_produce_success(self):
        self.audit("baseline")
        outside = self.folder / "outside"
        outside.write_text("untouched")
        link = self.runtime / "linked"
        link.symlink_to(outside)
        self.assertIn("Unsupported runtime entry", self.audit(success=False))
        link.unlink()
        self.database.unlink()
        self.assertIn("DATABASES=0", self.audit("baseline", success=False))
        self.assertEqual("untouched", outside.read_text())


if __name__ == "__main__":
    unittest.main()

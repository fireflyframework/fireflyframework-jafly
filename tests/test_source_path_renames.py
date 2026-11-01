import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("migration_path_verifier", ROOT / "scripts/verify_migration.py")
verifier = importlib.util.module_from_spec(spec)
spec.loader.exec_module(verifier)


class SourcePathRenameTest(unittest.TestCase):
    def fixture(self, root, entries):
        path = root / "docs/migration/source-path-renames.json"
        path.parent.mkdir(parents=True)
        path.write_text(json.dumps({"paths": entries}))

    def target(self, root, name="ServiceImpl"):
        path = root / ("modules/test/src/main/java/example/" + name + ".java")
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text("package example; public class " + name + " {}")
        return path

    def test_explicit_same_directory_implementation_rename_is_preserved(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            target = self.target(root)
            old = "modules/test/src/main/java/example/Service.java"
            self.fixture(root, {old: target.relative_to(root).as_posix()})
            with patch.object(verifier, "ROOT", root):
                self.assertEqual({old: target.relative_to(root).as_posix()}, verifier.source_path_renames())

    def test_external_or_wrong_name_target_cannot_replace_preserved_source(self):
        for target in ["../ServiceImpl.java", "/tmp/ServiceImpl.java", "modules/test/src/main/java/example/Other.java"]:
            with self.subTest(target=target), tempfile.TemporaryDirectory() as tmp:
                root = Path(tmp)
                self.target(root)
                self.fixture(root, {"modules/test/src/main/java/example/Service.java": target})
                with patch.object(verifier, "ROOT", root), self.assertRaises(ValueError):
                    verifier.source_path_renames()

    def test_missing_or_symbolic_target_does_not_preserve_source(self):
        for symlink in [False, True]:
            with self.subTest(symlink=symlink), tempfile.TemporaryDirectory() as tmp:
                root = Path(tmp)
                target = root / "modules/test/src/main/java/example/ServiceImpl.java"
                if symlink:
                    actual = self.target(root, "Actual")
                    target.symlink_to(actual)
                self.fixture(root, {"modules/test/src/main/java/example/Service.java": target.relative_to(root).as_posix()})
                with patch.object(verifier, "ROOT", root), self.assertRaises(ValueError):
                    verifier.source_path_renames()

    def test_present_old_path_or_wrong_declaration_is_not_an_intentional_rename(self):
        for present_old in [False, True]:
            with self.subTest(present_old=present_old), tempfile.TemporaryDirectory() as tmp:
                root = Path(tmp)
                target = self.target(root)
                old = target.with_name("Service.java")
                if present_old:
                    old.write_text("package example; public class Service {}")
                else:
                    target.write_text("package example; public class Different {}")
                self.fixture(root, {old.relative_to(root).as_posix(): target.relative_to(root).as_posix()})
                with patch.object(verifier, "ROOT", root), self.assertRaises(ValueError):
                    verifier.source_path_renames()

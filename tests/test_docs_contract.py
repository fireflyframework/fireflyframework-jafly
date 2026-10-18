"""Documentation coverage must follow real artifact coordinates and checked artwork."""
import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / 'scripts' / 'check_docs.py'


class DocumentationContractTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if SCRIPT.exists():
            spec = importlib.util.spec_from_file_location('check_docs', SCRIPT)
            cls.tools = importlib.util.module_from_spec(spec)
            spec.loader.exec_module(cls.tools)

    def setUp(self):
        if not SCRIPT.exists():
            self.fail('Documentation coverage checker is not implemented')
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        (self.root / 'modules/kernel').mkdir(parents=True)
        (self.root / 'modules/kernel/pom.xml').write_text('<project xmlns="http://maven.apache.org/POM/4.0.0"><artifactId>fireflyframework-kernel</artifactId></project>')
        (self.root / 'modules/kernel/README.md').write_text('# Kernel')
        self.docs = self.root / 'docs/site'
        (self.docs / 'modules').mkdir(parents=True)
        (self.docs / 'assets/modules').mkdir(parents=True)
        (self.docs / 'modules/kernel.md').write_text('# Kernel\n\nGuide')
        (self.docs / 'assets/modules/kernel.svg').write_text('<svg/>')
        self.catalog = [{'artifact': 'fireflyframework-kernel', 'slug': 'kernel', 'path': 'modules/kernel'}]

    def test_complete_catalog_is_accepted(self):
        self.tools.validate_catalog(self.root, self.catalog)

    def test_missing_guide_is_rejected(self):
        (self.docs / 'modules/kernel.md').unlink()
        with self.assertRaisesRegex(ValueError, 'guide'):
            self.tools.validate_catalog(self.root, self.catalog)

    def test_source_coordinate_mismatch_is_rejected(self):
        self.catalog[0]['artifact'] = 'fireflyframework-other'
        with self.assertRaisesRegex(ValueError, 'artifact'):
            self.tools.validate_catalog(self.root, self.catalog)

    def test_new_artifact_missing_from_catalog_is_rejected(self):
        (self.root / 'modules/new').mkdir()
        (self.root / 'modules/new/pom.xml').write_text('<project xmlns="http://maven.apache.org/POM/4.0.0"><artifactId>fireflyframework-new</artifactId></project>')
        with self.assertRaisesRegex(ValueError, 'catalog'):
            self.tools.validate_catalog(self.root, self.catalog)

    def test_invalid_svg_export_is_rejected_even_when_hash_matches(self):
        directory = self.docs / 'assets/modules'
        invalid = b'<svg width="100" width="200"/>'
        (directory / 'kernel.svg').write_bytes(invalid)
        (directory / 'ORIGIN.json').write_text(json.dumps({'exports': {'kernel.svg': hashlib.sha256(invalid).hexdigest()}}))
        with self.assertRaisesRegex(ValueError, 'SVG'):
            self.tools.validate_artwork(self.docs)

    def test_changed_artwork_is_rejected(self):
        directory = self.docs / 'assets/modules'
        (directory / 'ORIGIN.json').write_text(json.dumps({'exports': {'kernel.svg': hashlib.sha256(b'original').hexdigest()}}))
        with self.assertRaisesRegex(ValueError, 'hash'):
            self.tools.validate_artwork(self.docs)

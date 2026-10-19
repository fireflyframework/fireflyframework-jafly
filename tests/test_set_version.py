import importlib.util
import pathlib
import tempfile
import unittest

MODULE = pathlib.Path(__file__).resolve().parents[1] / 'scripts/set_version.py'


class SetVersionTest(unittest.TestCase):
    def test_consumer_application_version_remains_independent(self):
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            pom = root / 'pom.xml'
            pom.write_text('''<project xmlns="http://maven.apache.org/POM/4.0.0"><parent><groupId>org.fireflyframework</groupId><artifactId>fireflyframework-parent</artifactId><version>26.07.06</version></parent><groupId>com.example</groupId><artifactId>application</artifactId><version>26.07.06</version></project>''')
            self.tool().set_version(root, '26.10.18')
            content = pom.read_text()
            self.assertIn('<artifactId>application</artifactId><version>26.07.06</version>', content)
            self.assertIn('<artifactId>fireflyframework-parent</artifactId><version>26.10.18</version>', content)

    def tool(self):
        self.assertTrue(MODULE.exists(), 'Version updater is missing')
        spec = importlib.util.spec_from_file_location('set_version', MODULE)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        return module

    def test_monthly_patch_is_not_a_calendar_day(self):
        tool = self.tool()
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            pom = root / 'pom.xml'
            pom.write_text('<project xmlns="http://maven.apache.org/POM/4.0.0"><groupId>org.fireflyframework</groupId><version>26.07.06</version></project>')
            tool.set_version(root, '26.10.32')
            self.assertIn('26.10.32', pom.read_text())
            for invalid in ('26.00.01', '26.13.01', '26.10.001'):
                with self.assertRaises(ValueError):
                    tool.set_version(root, invalid)
            self.assertIn('26.10.32', pom.read_text())

    def test_update_projects_framework_parents_dependencies_and_property(self):
        tool = self.tool()
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            p = root / 'pom.xml'
            p.write_text('''<project xmlns="http://maven.apache.org/POM/4.0.0"><modelVersion>4.0.0</modelVersion><parent><groupId>org.fireflyframework</groupId><artifactId>fireflyframework-parent</artifactId><version>26.07.06</version></parent><artifactId>example</artifactId><version>26.07.06</version><properties><fireflyframework.version>26.07.06</fireflyframework.version></properties><dependencies><dependency><groupId>org.fireflyframework</groupId><artifactId>fireflyframework-web</artifactId><version>26.07.06</version></dependency><dependency><groupId>external</groupId><artifactId>library</artifactId><version>26.07.06</version></dependency></dependencies></project>''')
            tool.set_version(root, '26.10.18')
            content = p.read_text()
            self.assertEqual(4, content.count('26.10.18'))
            self.assertEqual(1, content.count('26.07.06'))

    def test_invalid_version_changes_nothing_and_target_is_ignored(self):
        tool = self.tool()
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            p = root / 'pom.xml'
            original = '<project xmlns="http://maven.apache.org/POM/4.0.0"><groupId>org.fireflyframework</groupId><version>26.07.06</version></project>'
            p.write_text(original)
            with self.assertRaises(ValueError):
                tool.set_version(root, '4.2.0-M1')
            self.assertEqual(original, p.read_text())
            target = root / 'target/pom.xml'
            target.parent.mkdir()
            target.write_text('generated not XML')
            tool.set_version(root, '26.10.18-SNAPSHOT')
            self.assertIn('26.10.18-SNAPSHOT', p.read_text())
            self.assertEqual('generated not XML', target.read_text())

    def test_preserves_shared_property_references_and_validates_all_files_first(self):
        tool = self.tool()
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            p = root / 'pom.xml'
            original = '<project xmlns="http://maven.apache.org/POM/4.0.0"><groupId>org.fireflyframework</groupId><version>26.07.06</version><dependencies><dependency><groupId>org.fireflyframework</groupId><artifactId>module</artifactId><version>${fireflyframework.version}</version></dependency></dependencies></project>'
            p.write_text(original)
            tool.set_version(root, '26.10.18')
            self.assertIn('${fireflyframework.version}', p.read_text())
            p.write_text(original)
            broken = root / 'z/pom.xml'
            broken.parent.mkdir()
            broken.write_text('invalid')
            with self.assertRaises(Exception):
                tool.set_version(root, '26.10.18')
            self.assertEqual(original, p.read_text())

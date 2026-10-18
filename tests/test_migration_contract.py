import json
import pathlib
import unittest
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[1]
NS = {'m': 'http://maven.apache.org/POM/4.0.0'}
VERSION = ET.parse(ROOT / 'pom.xml').getroot().findtext('m:version', namespaces=NS)


def read(path):
    return ET.parse(path).getroot()


def value(node, name):
    return node.findtext('m:' + name, namespaces=NS)


class MigrationContractTest(unittest.TestCase):
    def test_every_source_is_in_the_root_reactor(self):
        self.assertTrue((ROOT / 'pom.xml').is_file(), 'Jafly root reactor is missing')
        root = read(ROOT / 'pom.xml')
        modules = {x.text for x in root.findall('m:modules/m:module', NS)}
        sources = json.loads((ROOT / 'docs/migration/source-manifest.json').read_text())['sources']
        self.assertEqual({x['destination'] for x in sources}, modules)

    def test_framework_parents_resolve_from_source(self):
        for path in list((ROOT / 'modules').rglob('pom.xml')):
            if 'target' in path.parts:
                continue
            parent = read(path).find('m:parent', NS)
            if parent is None or value(parent, 'groupId') != 'org.fireflyframework':
                continue
            relative = value(parent, 'relativePath')
            with self.subTest(path=path.relative_to(ROOT)):
                self.assertTrue(relative, 'Framework parent must resolve locally')
                target = (path.parent / relative).resolve()
                self.assertTrue(target.is_file(), str(target))
                self.assertEqual(value(parent, 'artifactId'), value(read(target), 'artifactId'))

    def test_one_version_and_latest_stable_spring(self):
        for path in list((ROOT / 'modules').rglob('pom.xml')) + list((ROOT / 'build').rglob('pom.xml')):
            if 'target' in path.parts:
                continue
            pom = read(path)
            with self.subTest(path=path.relative_to(ROOT)):
                version = value(pom, 'version') or value(pom, 'parent/m:version')
                self.assertEqual(VERSION, version)
                for dep in pom.findall('.//m:dependency', NS):
                    if value(dep, 'groupId') == 'org.fireflyframework':
                        self.assertIn(value(dep, 'version'), [None, VERSION, '${fireflyframework.version}'])
        parent = read(ROOT / 'build/parent/pom.xml')
        self.assertEqual('4.1.1', value(parent, 'properties/m:spring-boot.version'))
        self.assertEqual('2025.1.3', value(parent, 'properties/m:spring-cloud.version'))

    def test_bom_covers_every_library_and_aggregator(self):
        pom = read(ROOT / 'build/bom/pom.xml')
        managed = {value(d, 'artifactId') for d in pom.findall('m:dependencyManagement/m:dependencies/m:dependency', NS) if value(d, 'groupId') == 'org.fireflyframework'}
        artifacts = set()
        for path in (ROOT / 'modules').rglob('pom.xml'):
            if 'target' in path.parts:
                continue
            p = read(path)
            artifact = value(p, 'artifactId')
            if 'examples' not in path.parts and not artifact.endswith('-samples'):
                artifacts.add(artifact)
        self.assertEqual(artifacts, managed)

    def test_public_parent_does_not_use_consumer_project_version(self):
        for relative in ['build/parent/pom.xml', 'build/bom/pom.xml']:
            pom = read(ROOT / relative)
            self.assertEqual(VERSION, value(pom, 'properties/m:fireflyframework.version'))
            for dependency in pom.findall('m:dependencyManagement/m:dependencies/m:dependency', NS):
                if value(dependency, 'groupId') == 'org.fireflyframework':
                    self.assertEqual('${fireflyframework.version}', value(dependency, 'version'))

    def test_source_history_and_all_original_files_are_preserved(self):
        import subprocess
        verifier = ROOT / 'scripts/verify_migration.py'
        self.assertTrue(verifier.is_file(), 'Source provenance verifier is missing')
        result = subprocess.run(['python3', str(verifier)], cwd=ROOT, capture_output=True, text=True)
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)

    def test_jackson_compatibility_uses_the_boot_managed_release_train(self):
        for path in (ROOT / 'modules').rglob('pom.xml'):
            if 'target' in path.parts:
                continue
            pom = read(path)
            for dependency in pom.findall('.//m:dependency', NS):
                group = value(dependency, 'groupId') or ''
                if group.startswith('com.fasterxml.jackson'):
                    with self.subTest(path=path.relative_to(ROOT), artifact=value(dependency, 'artifactId')):
                        self.assertIsNone(value(dependency, 'version'), 'Boot coordinates the Jackson 2 compatibility modules')

    def test_consumer_parent_does_not_add_runtime_libraries(self):
        parent = read(ROOT / 'build/parent/pom.xml')
        self.assertEqual([], parent.findall('m:dependencies/m:dependency', NS), 'A build-management parent must not force runtime libraries into every consumer and foundation module')

    def test_parent_and_bom_publish_the_same_framework_catalog(self):
        catalogs = []
        for name in ['parent', 'bom']:
            pom = read(ROOT / 'build' / name / 'pom.xml')
            catalogs.append({(value(d, 'artifactId'), value(d, 'version'), value(d, 'type') or 'jar') for d in pom.findall('m:dependencyManagement/m:dependencies/m:dependency', NS) if value(d, 'groupId') == 'org.fireflyframework'})
        self.assertEqual(catalogs[0], catalogs[1], 'The consumer parent and independent BOM must select the same framework artifacts')

import pathlib
import unittest
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[1]
NS = {'m': 'http://maven.apache.org/POM/4.0.0'}
ENTRYPOINTS = ('build/parent/pom.xml', 'build/bom/pom.xml')


def managed(path):
    pom = ET.parse(ROOT / path).getroot()
    properties = {child.tag.split('}')[-1]: child.text for child in pom.find('m:properties', NS)}
    dependencies = pom.findall('m:dependencyManagement/m:dependencies/m:dependency', NS)

    def version(dependency):
        value = dependency.findtext('m:version', namespaces=NS)
        if value and value.startswith('${') and value.endswith('}'):
            return properties[value[2:-1]]
        return value

    return dependencies, version


class RuntimeSecurityConstraintsTest(unittest.TestCase):
    def test_guava_safe_jre_release_covers_parent_and_bom_consumers(self):
        for path in ENTRYPOINTS:
            dependencies, version = managed(path)
            selected = [d for d in dependencies if d.findtext('m:groupId', namespaces=NS) == 'com.google.guava'
                        and d.findtext('m:artifactId', namespaces=NS) == 'guava']
            with self.subTest(path=path):
                self.assertEqual(1, len(selected))
                self.assertEqual('33.7.2-jre', version(selected[0]))

    def test_snappy_safe_release_covers_parent_and_bom_consumers(self):
        for path in ENTRYPOINTS:
            dependencies, version = managed(path)
            selected = [d for d in dependencies if d.findtext('m:groupId', namespaces=NS) == 'org.xerial.snappy'
                        and d.findtext('m:artifactId', namespaces=NS) == 'snappy-java']
            with self.subTest(path=path):
                self.assertEqual(1, len(selected))
                self.assertEqual('1.1.10.9', version(selected[0]))

    def test_opentelemetry_uses_one_safe_family_bom(self):
        for path in ENTRYPOINTS:
            dependencies, version = managed(path)
            family = [d for d in dependencies if d.findtext('m:groupId', namespaces=NS) == 'io.opentelemetry']
            with self.subTest(path=path):
                self.assertEqual(1, len(family), 'Keep API, context, SDK and exporters on the same BOM')
                self.assertEqual('opentelemetry-bom', family[0].findtext('m:artifactId', namespaces=NS))
                self.assertEqual('pom', family[0].findtext('m:type', namespaces=NS))
                self.assertEqual('import', family[0].findtext('m:scope', namespaces=NS))
                self.assertEqual('1.67.0', version(family[0]))

    def test_opentelemetry_family_precedes_other_imported_platforms(self):
        for path in ENTRYPOINTS:
            dependencies, _ = managed(path)
            imports = [d for d in dependencies if d.findtext('m:scope', namespaces=NS) == 'import']
            with self.subTest(path=path):
                self.assertTrue(imports)
                self.assertEqual('io.opentelemetry', imports[0].findtext('m:groupId', namespaces=NS),
                                 'Maven keeps the first imported constraint for overlapping artifacts')
                self.assertEqual('opentelemetry-bom', imports[0].findtext('m:artifactId', namespaces=NS))


if __name__ == '__main__':
    unittest.main()

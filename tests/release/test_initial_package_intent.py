"""Source intent is distinct from provider existence and publication authority."""
import copy, io, json, os, subprocess, tempfile, unittest, urllib.error
from pathlib import Path
from unittest.mock import patch
from test_github_packages import m, r, CURRENT_PROJECTS, PROJECTS, ASSOCIATIONS, SOURCE, fixture_inventory
INTENT = json.loads((SOURCE / 'scripts/initial-package-intent.json').read_text())
BATCH = 'org.fireflyframework.fireflyframework-batch'

class InitialNamespaceTests(unittest.TestCase):

    def homes(self):
        try:
            return m.build_homes(CURRENT_PROJECTS, ASSOCIATIONS, initial_intent=INTENT)
        except (TypeError, ValueError) as error:
            self.fail('Explicit source intent must compose85 homes without fabricating history: ' + str(error))

    def test_exact_source_intent_composes85_complete_homes_and316_files(self):
        before = copy.deepcopy(ASSOCIATIONS)
        homes = self.homes()
        self.assertEqual(96, len(ASSOCIATIONS))
        self.assertEqual(before, ASSOCIATIONS)
        self.assertEqual(85, len(homes['artifacts']))
        self.assertEqual(316, len(m.required_paths(CURRENT_PROJECTS)))
        m.validate_inventory(CURRENT_PROJECTS, fixture_inventory(CURRENT_PROJECTS))
        m.validate_associations(CURRENT_PROJECTS, homes, ASSOCIATIONS, initial_intent=INTENT)
        m.validate_canonical_homes(CURRENT_PROJECTS, homes, initial_intent=INTENT)

    def test_missing_intent_still_refuses_unmapped_batch(self):
        with self.assertRaisesRegex(ValueError, 'missing existing package home'):
            m.build_homes(CURRENT_PROJECTS, ASSOCIATIONS)

    def test_intent_cannot_override_history_or_name_another_coordinate_target_or_policy(self):
        for key, value in [('schema', True), ('schema', 1.0), ('artifactId', 'another'), ('repositoryId', 1423952544), ('repository', 'another'), ('visibility', 'private'), ('on404', 'ALLOW'), ('requiredProviderQualification', 'FIRST_UPLOAD')]:
            bad = copy.deepcopy(INTENT)
            bad[key] = value
            with self.subTest(key=key), self.assertRaises(ValueError):
                m.build_homes(CURRENT_PROJECTS, ASSOCIATIONS, initial_intent=bad)
        with self.assertRaises(ValueError):
            m.build_homes(PROJECTS, ASSOCIATIONS, initial_intent=INTENT)

    def test_initial_intent_binding_must_survive_home_and_canonical_checks(self):
        homes = self.homes()
        homes['initialNamespaceIntentSha256'] = '0' * 64
        with self.assertRaises(ValueError):
            m.validate_associations(CURRENT_PROJECTS, homes, ASSOCIATIONS, initial_intent=INTENT)
        with self.assertRaises(ValueError):
            m.validate_canonical_homes(CURRENT_PROJECTS, homes, initial_intent=INTENT)

    def execute(self, batch_reply, maven_code=0):
        homes = self.homes()
        calls = []

        class Api:

            def open(inner, request, timeout):
                name = request.full_url.rsplit('/', 1)[-1]
                calls.append(name)
                if name == BATCH and isinstance(batch_reply, int):
                    raise urllib.error.HTTPError(request.full_url, batch_reply, 'ordinary status', {}, io.BytesIO())
                row = {'name': name, 'package_type': 'maven', 'visibility': 'public', 'owner': {'login': 'fireflyframework'}, 'repository': {'full_name': 'fireflyframework/fireflyframework-jafly', 'id': 1423952543}}
                status = 200
                if name == BATCH and isinstance(batch_reply, dict):
                    row.update(batch_reply)
                if name == BATCH and isinstance(batch_reply, tuple):
                    status = batch_reply[0]
                    row.update(batch_reply[1])
                response = io.BytesIO(json.dumps(row).encode())
                response.status = status
                return response
        with tempfile.TemporaryDirectory() as directory:
            plan = Path(directory)
            inventory = fixture_inventory(CURRENT_PROJECTS)
            for name, data in [('reactor-inventory.json', CURRENT_PROJECTS), ('package-homes.json', homes), ('github-manifest.json', inventory), ('source-associations.json', ASSOCIATIONS), ('source-initial-intent.json', INTENT)]:
                (plan / name).write_text(json.dumps(data))
            for item in inventory:
                path = plan / 'artifacts' / item['path']
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(item['path'].encode())
            with patch.dict(os.environ, {'JAFLY_PACKAGES_TOKEN': 'SYNTHETIC_INITIAL_TEST', 'GITHUB_ACTOR': 'ordinary-test'}, clear=True), patch.object(m.urllib.request, 'build_opener', return_value=Api()), patch.object(m.subprocess, 'run', return_value=subprocess.CompletedProcess([], maven_code, 'HTTP409 ordinary conflict' if maven_code else '', '')) as run:
                try:
                    m.run_batch('/not/executed/maven', plan)
                except ValueError:
                    if run.called:
                        self.assertNotEqual(0, maven_code)
                    else:
                        self.assertFalse((plan / 'publisher.pom.xml').exists())
                        self.assertFalse((plan / 'settings.xml').exists())
                    raise
                self.assertEqual(1, run.call_count)
                self.assertEqual(85, len(calls))
                self.assertEqual(85, len(json.loads((plan / 'association-preflight.json').read_text())))

    def test_unknown404_refuses_before_any_publisher_settings_or_maven(self):
        with self.assertRaisesRegex(ValueError, 'UNKNOWN'):
            self.execute(404)

    def test_only_actual_exact_existing_provider200_permits_full_mocked_plan(self):
        self.execute({})

    def test_provider_conflict_or_authorization_failure_never_qualifies(self):
        for reply in [401, 403, 409, 500, (201, {}), (204, {}), {'repository': {'full_name': 'fireflyframework/fireflyframework-jafly', 'id': 1}}, {'visibility': 'private'}]:
            with self.subTest(reply=reply), self.assertRaises(ValueError):
                self.execute(reply)

    def test_nonzero_maven_conflict_never_becomes_initial_publication_success(self):
        with self.assertRaisesRegex(ValueError, 'Maven deployment failed'):
            self.execute({}, maven_code=1)

    def test_partial84_inventory_never_qualifies85_coordinate_plan(self):
        with self.assertRaises(ValueError):
            m.validate_inventory(CURRENT_PROJECTS, fixture_inventory(PROJECTS))

    def test_explicit_initial_cli_stages316_files_without_publisher_or_settings(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            repository = root / 'repository'
            output = root / 'plan'
            projects = m.publishable(CURRENT_PROJECTS)
            for project in projects:
                prefix = project['groupId'].replace('.', '/') + '/' + project['artifactId'] + '/' + project['version'] + '/'
                for name in m.required_paths([project]):
                    path = repository / name
                    path.parent.mkdir(parents=True, exist_ok=True)
                    content = name.encode()
                    if name.endswith('.pom'):
                        content = ('<project xmlns="http://maven.apache.org/POM/4.0.0"><groupId>' + project['groupId'] + '</groupId><artifactId>' + project['artifactId'] + '</artifactId><version>' + project['version'] + '</version><packaging>' + project['packaging'] + '</packaging></project>').encode()
                    path.write_bytes(content)
            argv = ['github_packages.py', 'plan', '--root', str(SOURCE), '--repository', str(repository), '--output', str(output), '--initial-intent', str(SOURCE / 'scripts/initial-package-intent.json')]
            with patch.object(m.sys, 'argv', argv):
                m.main()
            self.assertEqual(316, len(json.loads((output / 'github-manifest.json').read_text())))
            self.assertEqual(85, len(json.loads((output / 'package-homes.json').read_text())['artifacts']))
            self.assertEqual(INTENT, json.loads((output / 'source-initial-intent.json').read_text()))
            self.assertFalse((output / 'publisher.pom.xml').exists())
            self.assertFalse((output / 'settings.xml').exists())
            self.assertFalse((output / 'goals.json').exists())

    def test_legacy84_canonical_homes_and_associations_remain_byte_exact(self):
        self.assertEqual(84, len(m.build_homes(PROJECTS, ASSOCIATIONS)['artifacts']))
        self.assertEqual(ASSOCIATIONS, [json.loads(line) for line in (SOURCE / 'scripts/package-associations.jsonl').read_text().splitlines()])
if __name__ == '__main__':
    unittest.main()

import copy
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
from test_github_packages import PROJECTS, ASSOCIATIONS, fixture_inventory
import github_packages as m
import verify_github_packages as compat

class CompatibilityTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.path = Path(self.temp.name) / 'package-homes.json'
        self.homes = m.build_homes(PROJECTS, ASSOCIATIONS)
        self.path.write_text(json.dumps(self.homes))
        self.inventory = fixture_inventory(PROJECTS)

    def test_current_cli_jafly_base_uses_exact_tag_map_without_manifest_changes(self):
        with patch.object(m, 'verify_mapped', return_value=['proof']) as verify:
            result = compat.verify_github_layout(self.inventory, 'https://maven.pkg.github.com/fireflyframework/fireflyframework-jafly', PROJECTS, self.path, 'synthetic-user', 'synthetic-token')
            self.assertEqual(result, ['proof'])
            self.assertEqual(verify.call_args.args[1], self.homes)
            self.assertEqual(verify.call_args.kwargs['username'], 'synthetic-user')

    def test_mismatched_owner_or_injected_url_is_rejected(self):
        for url in ['https://maven.pkg.github.com/other/fireflyframework-jafly',
                    'https://maven.pkg.github.com/fireflyframework/fireflyframework-jafly?secret=x',
                    'http://maven.pkg.github.com/fireflyframework/fireflyframework-jafly',
                    'https://maven.pkg.github.com@evil.example/fireflyframework/fireflyframework-jafly']:
            with self.subTest(url=url), patch.object(m, 'verify_mapped') as verify:
                with self.assertRaises(ValueError):
                    compat.verify_github_layout(self.inventory, url, PROJECTS, self.path)
                verify.assert_not_called()

    def test_missing_map_or_inventory_entry_fails_before_network(self):
        with patch.object(m, 'verify_mapped') as verify:
            with self.assertRaises(ValueError):
                compat.verify_github_layout(self.inventory[:-1], 'https://maven.pkg.github.com/fireflyframework/fireflyframework-jafly', PROJECTS, self.path)
            self.path.unlink()
            with self.assertRaises(FileNotFoundError):
                compat.verify_github_layout(self.inventory, 'https://maven.pkg.github.com/fireflyframework/fireflyframework-jafly', PROJECTS, self.path)
            verify.assert_not_called()

    def test_release_tools_hook_keeps_cli_signature_and_requires_its_source_map(self):
        import test_github_packages as fixture
        tools = fixture.r
        inventory = self.inventory
        script = self.path.parent / 'scripts/release_tools.py'
        script.parent.mkdir()
        map_path = script.with_name('package-homes.json')
        with patch.object(tools, '__file__', str(script)), patch.object(tools, 'check_reactor', return_value=PROJECTS), patch.object(m, 'verify_mapped', return_value=['proof']) as verify:
            with self.assertRaises(FileNotFoundError):
                tools.verify_remote(inventory, 'https://maven.pkg.github.com/fireflyframework/fireflyframework-jafly')
            verify.assert_not_called()
            map_path.write_text(json.dumps(self.homes))
            self.assertEqual(tools.verify_remote(inventory, 'https://maven.pkg.github.com/fireflyframework/fireflyframework-jafly'), ['proof'])

    def test_historical_exact_tag_layout_remains_readable_after_bootstrap(self):
        homes = copy.deepcopy(self.homes)
        homes.pop('publicationMode')
        captured = {row['name']: row['repository'] for row in ASSOCIATIONS}
        for row in homes['artifacts']:
            row['repository'] = captured[row['groupId'] + '.' + row['artifactId']]
        self.path.write_text(json.dumps(homes))
        with patch.object(m, 'verify_mapped') as verify:
            compat.verify_github_layout(self.inventory, 'https://maven.pkg.github.com/fireflyframework/fireflyframework-jafly', PROJECTS, self.path)
            self.assertTrue(any(row['repository']=='fireflyframework-bom' for row in verify.call_args.args[1]['artifacts']))
        with self.assertRaisesRegex(ValueError, 'canonical'):
            m.validate_canonical_homes(PROJECTS, homes, Path(__file__).resolve().parents[2] / 'scripts/package-homes.json')

if __name__ == '__main__': unittest.main()

import copy
import io
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
import urllib.error
from unittest.mock import patch

from test_github_packages import ASSOCIATIONS, PROJECTS, fixture_inventory
import github_packages as m


class ApiFixture:
    def __init__(self, homes, replies):
        self.homes = homes
        self.replies = iter(replies)
        self.requests = []

    def open(self, request, timeout):
        name = request.full_url.removeprefix('https://api.github.com/orgs/fireflyframework/packages/maven/')
        if name not in {row['groupId'] + '.' + row['artifactId'] for row in self.homes['artifacts']}:
            raise AssertionError('API request escaped the fixed package endpoint')
        if request.get_header('Authorization') != 'Bearer SYNTHETIC_BOOTSTRAP_TOKEN':
            raise AssertionError('API credential missing or changed')
        self.requests.append(request.full_url)
        reply = next(self.replies)
        if isinstance(reply, int):
            raise urllib.error.HTTPError(request.full_url, reply, 'synthetic status', {}, io.BytesIO())
        if reply == 'invalid-json':
            return io.BytesIO(b'not json')
        return io.BytesIO(json.dumps(reply).encode())


class BootstrapPublishingTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.projects = copy.deepcopy(m.publishable(PROJECTS)[:2])
        self.homes = m.build_homes(self.projects, ASSOCIATIONS)
        self.plan = self.root / 'plan'
        self.plan.mkdir()
        for name, data in [('reactor-inventory.json', self.projects),
                           ('package-homes.json', self.homes),
                           ('source-associations.json', ASSOCIATIONS),
                           ('github-manifest.json', fixture_inventory(self.projects))]:
            (self.plan / name).write_text(json.dumps(data))
        for row in fixture_inventory(self.projects):
            path = self.plan / 'artifacts' / row['path']
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(row['path'].encode())
        canonical = self.root / 'canonical.json'
        canonical.write_text(json.dumps(self.homes))
        self.addCleanup(patch.stopall)
        patch.object(m, 'CANONICAL_HOMES', canonical).start()

    def existing(self, index=0, repository=None):
        item = self.homes['artifacts'][index]
        return {'name': item['groupId'] + '.' + item['artifactId'],
                'package_type': 'maven', 'visibility': 'public',
                'owner': {'login': 'fireflyframework'},
                'repository': repository or {'id': 1423952543,
                    'full_name': 'fireflyframework/fireflyframework-jafly'}}

    def execute(self, replies):
        api = ApiFixture(self.homes, replies)
        def maven(command, **kwargs):
            # Every association must be checked before the first deployment process.
            if len(api.requests) != 2:
                raise AssertionError('Maven started before all association checks completed')
            if 'SYNTHETIC_BOOTSTRAP_TOKEN' in str(command):
                raise AssertionError('credential exposed in Maven arguments')
            return subprocess.CompletedProcess(command, 0, '', '')
        with patch.dict(os.environ, {'JAFLY_PACKAGES_TOKEN': 'SYNTHETIC_BOOTSTRAP_TOKEN',
                                     'GITHUB_ACTOR': 'synthetic-user'}, clear=True), \
             patch.object(m.urllib.request, 'build_opener', return_value=api), \
             patch.object(m.subprocess, 'run', side_effect=maven):
            m.run_batch('/not/executed/mvn', self.plan)
        return api

    def test_generated_map_targets_jafly_without_rewriting_captured_history(self):
        before = copy.deepcopy(ASSOCIATIONS)
        homes = m.build_homes(PROJECTS, ASSOCIATIONS)
        self.assertEqual(homes.get('publicationMode'), 'jafly-bootstrap')
        self.assertEqual({entry['repository'] for entry in homes['artifacts']}, {'fireflyframework-jafly'})
        self.assertEqual(ASSOCIATIONS, before)
        self.assertIn('fireflyframework-parent', {row['repository'] for row in ASSOCIATIONS})

    def test_absent_packages_allow_bootstrap_after_all_reads(self):
        api = self.execute([404, 404])
        self.assertEqual(len(api.requests), 2)
        self.assertTrue((self.plan / 'publisher.pom.xml').is_file())
        self.assertTrue((self.plan / 'settings.xml').is_file())
        self.assertNotIn('SYNTHETIC_BOOTSTRAP_TOKEN', (self.plan / 'settings.xml').read_text())

    def test_existing_jafly_packages_allow_update(self):
        api = self.execute([self.existing(0), self.existing(1)])
        self.assertEqual(len(api.requests), 2)

    def test_any_legacy_or_orphan_package_blocks_before_writes(self):
        for repository in [{'id': 1151407707, 'full_name': 'fireflyframework/fireflyframework-parent'},
                           {'id': 1423952544, 'full_name': 'fireflyframework/fireflyframework-jafly'},
                           {'id': 1423952543, 'full_name': 'other/fireflyframework-jafly'}, None]:
            with self.subTest(repository=repository):
                reply = self.existing(1)
                reply['repository'] = repository
                with self.assertRaisesRegex(ValueError, 'association'):
                    self.execute([404, reply])
                self.assertFalse((self.plan / 'publisher.pom.xml').exists())
                self.assertFalse((self.plan / 'settings.xml').exists())

    def test_auth_failures_and_other_http_errors_cannot_become_absent_packages(self):
        for status in [401, 403, 301, 429, 500]:
            with self.subTest(status=status):
                with self.assertRaisesRegex(ValueError, 'HTTP ' + str(status)):
                    self.execute([status])
                self.assertFalse((self.plan / 'settings.xml').exists())

    def test_wrong_identity_visibility_and_malformed_responses_fail_closed(self):
        wrong_name = self.existing()
        wrong_name['name'] = 'org.fireflyframework.another-artifact'
        private = self.existing()
        private['visibility'] = 'private'
        for reply in [wrong_name, private, {}, [], 'invalid-json']:
            with self.subTest(reply=reply):
                with self.assertRaisesRegex(ValueError, 'association'):
                    self.execute([reply])
                self.assertFalse((self.plan / 'settings.xml').exists())


if __name__ == '__main__':
    unittest.main()

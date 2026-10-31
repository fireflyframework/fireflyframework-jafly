"""A final-release upload attempt is not a namespace-existence assertion."""
import copy
import io
import json
import os
from pathlib import Path
import shlex
import subprocess
import tempfile
import unittest
import urllib.error
from unittest.mock import patch

from test_github_packages import m, CURRENT_PROJECTS, ASSOCIATIONS, SOURCE, fixture_inventory

INTENT = json.loads((SOURCE / 'scripts/initial-package-intent.json').read_text())
BATCH = 'org.fireflyframework.fireflyframework-batch'
REPOSITORY = 'https://api.github.com/repos/fireflyframework/fireflyframework-jafly'


class InitialPublicationAuthorizationTests(unittest.TestCase):
    def execute(self, *, allow=True, first=404, after=None, repository=None,
                maven_code=0, snapshot=False, omit_intent=False, via_cli=False):
        projects = copy.deepcopy(CURRENT_PROJECTS)
        if snapshot:
            for project in projects:
                project['version'] += '-SNAPSHOT'
        homes = m.build_homes(projects, ASSOCIATIONS, initial_intent=INTENT)
        package_reads, repository_reads, deployments = [], [], []
        batch_reads = 0

        class Api:
            def open(inner, request, timeout):
                nonlocal batch_reads
                if request.full_url == REPOSITORY:
                    repository_reads.append(request.full_url)
                    reply = repository if repository is not None else {
                        'id': 1423952543, 'full_name': 'fireflyframework/fireflyframework-jafly',
                        'private': False, 'archived': False, 'disabled': False,
                        'owner': {'login': 'fireflyframework'}, 'permissions': {'push': True}}
                else:
                    prefix = 'https://api.github.com/orgs/fireflyframework/packages/maven/'
                    if not request.full_url.startswith(prefix):
                        raise AssertionError('API request escaped the fixed endpoints')
                    name = request.full_url.removeprefix(prefix)
                    package_reads.append(name)
                    reply = {'name': name, 'package_type': 'maven', 'visibility': 'public',
                             'owner': {'login': 'fireflyframework'}, 'repository': {
                                 'id': 1423952543, 'full_name': 'fireflyframework/fireflyframework-jafly'}}
                    if name == BATCH:
                        batch_reads += 1
                        selected = first if batch_reads == 1 else after
                        if selected is not None:
                            if isinstance(selected, dict):
                                reply.update(selected)
                            else:
                                reply = selected
                if isinstance(reply, int):
                    raise urllib.error.HTTPError(request.full_url, reply, 'synthetic status', {}, io.BytesIO())
                response = io.BytesIO(json.dumps(reply).encode())
                response.status = 200
                return response

        def deploy(command, **kwargs):
            self.assertEqual(85, len(package_reads))
            self.assertIn(BATCH, package_reads)
            self.assertNotIn('SYNTHETIC_FIRST_PUBLICATION', str(command))
            deployments.append(command)
            return subprocess.CompletedProcess(command, maven_code, '', '')

        with tempfile.TemporaryDirectory() as directory:
            plan = Path(directory)
            rows = [('reactor-inventory.json', projects), ('package-homes.json', homes),
                    ('github-manifest.json', fixture_inventory(projects)),
                    ('source-associations.json', ASSOCIATIONS)]
            if not omit_intent:
                rows.append(('source-initial-intent.json', INTENT))
            for name, value in rows:
                (plan / name).write_text(json.dumps(value))
            for item in fixture_inventory(projects):
                path = plan / 'artifacts' / item['path']
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(item['path'].encode())
            with patch.dict(os.environ, {'JAFLY_PACKAGES_TOKEN': 'SYNTHETIC_FIRST_PUBLICATION',
                                         'GITHUB_ACTOR': 'synthetic-user'}, clear=True), \
                 patch.object(m.urllib.request, 'build_opener', return_value=Api()), \
                 patch.object(m.subprocess, 'run', side_effect=deploy):
                try:
                    if via_cli:
                        with patch.object(m.sys, 'argv', ['github_packages.py', 'execute',
                                '--plan', str(plan), '--maven', '/not/executed/maven',
                                '--allow-initial-batch-publication']):
                            m.main()
                    else:
                        m.run_batch('/not/executed/maven', plan, allow_initial_batch_publication=allow)
                except (ValueError, TypeError):
                    if not deployments:
                        self.assertFalse((plan / 'publisher.pom.xml').exists())
                        self.assertFalse((plan / 'settings.xml').exists())
                    raise
            self.assertEqual(1, len(deployments))
            proof = json.loads((plan / 'association-preflight.json').read_text())
            batch = next(row for row in proof if row['name'] == BATCH)
            if first == 404:
                self.assertEqual('initial-publish-attempt-authorized', batch['status'])
                self.assertEqual(1, len(repository_reads))
                post = json.loads((plan / 'association-postflight.json').read_text())
                self.assertEqual('jafly-associated', next(row for row in post if row['name'] == BATCH)['status'])
                self.assertEqual(170, len(package_reads))
            else:
                self.assertEqual('jafly-associated', batch['status'])
                self.assertEqual([], repository_reads)

    def test_explicit_final_attempt_requires_all_reads_then_exact_repository_and_postflight(self):
        self.execute(via_cli=True)

    def test_existing_package_uses_original_strict_path(self):
        self.execute(first=None)

    def test_default_still_refuses_unknown_namespace(self):
        with self.assertRaisesRegex(ValueError, 'UNKNOWN'):
            self.execute(allow=False)

    def test_snapshot_cannot_authorize_initial_final_publication(self):
        with self.assertRaisesRegex(ValueError, 'final'):
            self.execute(snapshot=True)

    def test_local_mode_cannot_impersonate_provider_authorization(self):
        with self.assertRaisesRegex(ValueError, 'local'):
            m.run_batch('/not/executed/maven', '/no-plan', local=True,
                        allow_initial_batch_publication=True)

    def test_nonboolean_authorization_is_refused(self):
        with self.assertRaisesRegex(ValueError, 'boolean'):
            self.execute(allow='true')

    def test_failed_repository_read_cannot_authorize_unknown_namespace(self):
        for status in [301, 403, 404, 429, 500]:
            with self.subTest(status=status), self.assertRaisesRegex(ValueError, 'HTTP ' + str(status)):
                self.execute(repository=status)

    def test_missing_intent_cannot_authorize_upload(self):
        with self.assertRaisesRegex(ValueError, 'intent'):
            self.execute(omit_intent=True)

    def test_auth_redirect_rate_limit_conflict_and_server_errors_refuse_before_maven(self):
        for status in [301, 401, 403, 409, 429, 500]:
            with self.subTest(status=status), self.assertRaisesRegex(ValueError, 'HTTP ' + str(status)):
                self.execute(first=status)

    def test_repository_identity_and_write_permission_are_not_inferred_from404(self):
        valid = {'id': 1423952543, 'full_name': 'fireflyframework/fireflyframework-jafly',
                 'private': False, 'archived': False, 'disabled': False,
                 'owner': {'login': 'fireflyframework'}, 'permissions': {'push': True}}
        for key, value in [('id', 1), ('full_name', 'other/repo'), ('private', True),
                           ('archived', True), ('disabled', True), ('owner', {}),
                           ('permissions', {}), ('permissions', {'push': 'true'})]:
            with self.subTest(key=key), self.assertRaisesRegex(ValueError, 'repository'):
                self.execute(repository=valid | {key: value})

    def test_failed_or_conflicting_upload_never_becomes_success(self):
        with self.assertRaisesRegex(ValueError, 'Maven deployment failed'):
            self.execute(maven_code=1)

    def test_successful_maven_exit_does_not_replace_exact_provider_postflight(self):
        for after in [404, 403, {'repository': {'id': 1, 'full_name': 'other/repo'}}]:
            with self.subTest(after=after), self.assertRaises(ValueError):
                self.execute(after=after)

    def test_both_workflow_plans_stage_the_complete85_coordinate_train(self):
        for name in ['release.yml', 'snapshots.yml']:
            with self.subTest(workflow=name), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                repository, output = root / 'repository', root / 'stage'
                for project in m.publishable(CURRENT_PROJECTS):
                    for name_in_repo in m.required_paths([project]):
                        path = repository / name_in_repo
                        path.parent.mkdir(parents=True, exist_ok=True)
                        data = name_in_repo.encode()
                        if name_in_repo.endswith('.pom'):
                            data = ('<project xmlns="http://maven.apache.org/POM/4.0.0">' +
                                    ''.join('<' + key + '>' + project[key] + '</' + key + '>'
                                            for key in ['groupId', 'artifactId', 'version', 'packaging']) + '</project>').encode()
                        path.write_bytes(data)
                text = (SOURCE / '.github/workflows' / name).read_text()
                start = text.index('python3 scripts/github_packages.py plan')
                logical = text[start:].replace('\\\n', '').splitlines()[0]
                args = shlex.split(logical)[1:]
                for flag, value in [('--root', str(SOURCE)), ('--repository', str(repository)), ('--output', str(output))]:
                    args[args.index(flag) + 1] = value
                if '--initial-intent' in args:
                    args[args.index('--initial-intent') + 1] = str(SOURCE / args[args.index('--initial-intent') + 1])
                with patch.object(m.sys, 'argv', args):
                    m.main()
                self.assertEqual(85, len(json.loads((output / 'package-homes.json').read_text())['artifacts']))
                self.assertEqual(316, len(json.loads((output / 'github-manifest.json').read_text())))


if __name__ == '__main__':
    unittest.main()

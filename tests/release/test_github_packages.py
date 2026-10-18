import copy
from contextlib import redirect_stdout
import io
import hashlib
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
import importlib.util
import json
from pathlib import Path
import tempfile
import os
import subprocess
import urllib.request
import threading
import unittest
import xml.etree.ElementTree as ET
from unittest.mock import patch

import sys
SCRIPT_DIRECTORY = Path(__file__).resolve().parents[2] / 'scripts'
sys.path.insert(0, str(SCRIPT_DIRECTORY))
import github_packages as m

SOURCE = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('release_tools', SOURCE / 'scripts/release_tools.py')
r = importlib.util.module_from_spec(spec)
spec.loader.exec_module(r)
PROJECTS = r.check_reactor(SOURCE, expected=86)
ASSOCIATIONS = [json.loads(line) for line in (SOURCE / 'scripts/package-associations.jsonl').read_text().splitlines()]


def fixture_inventory(projects):
    return [{'path': path, 'sha256': hashlib.sha256(path.encode()).hexdigest()}
            for path in sorted(r.required_paths(projects))]


class QuietHandler(SimpleHTTPRequestHandler):
    def log_message(self, *args):
        pass

    def do_GET(self):
        self.server.requests.append(self.path)
        super().do_GET()


class MappedPublishingTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.homes = m.build_homes(PROJECTS, ASSOCIATIONS)
        self.inventory = fixture_inventory(PROJECTS)

    def server(self):
        handler = lambda *args, **kwargs: QuietHandler(*args, directory=str(self.root), **kwargs)
        server = ThreadingHTTPServer(('127.0.0.1', 0), handler)
        server.requests = []
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        self.addCleanup(server.server_close)
        self.addCleanup(server.shutdown)
        return server, f'http://127.0.0.1:{server.server_port}'

    def write_registry(self, projects, inventory, snapshot):
        homes = {entry['groupId'] + ':' + entry['artifactId']: entry for entry in self.homes['artifacts']}
        for project in projects:
            if not project['publish']:
                continue
            home = homes[project['groupId'] + ':' + project['artifactId']]
            base = project['groupId'].replace('.', '/') + '/' + project['artifactId'] + '/' + project['version']
            items = [item for item in inventory if item['path'].startswith(base + '/')]
            versions = []
            for item in items:
                path = item['path']
                if snapshot:
                    unique = project['version'].removesuffix('-SNAPSHOT') + '-20261018.123456-7'
                    remote = path.replace(project['artifactId'] + '-' + project['version'], project['artifactId'] + '-' + unique)
                    suffix = path.rsplit('/', 1)[1].removeprefix(project['artifactId'] + '-' + project['version'])
                    classifier = 'sources' if suffix == '-sources.jar' else 'javadoc' if suffix == '-javadoc.jar' else ''
                    extension = 'jar' if suffix.endswith('.jar') else 'pom'
                    versions.append(f'<snapshotVersion><extension>{extension}</extension><classifier>{classifier}</classifier><value>{unique}</value></snapshotVersion>')
                else:
                    remote = path
                target = self.root / home['repository'] / remote
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(path.encode())
            if snapshot:
                metadata = f'''<metadata><groupId>{project['groupId']}</groupId><artifactId>{project['artifactId']}</artifactId><version>{project['version']}</version><versioning><snapshot><timestamp>20261018.123456</timestamp><buildNumber>7</buildNumber></snapshot><snapshotVersions>{''.join(versions)}</snapshotVersions></versioning></metadata>'''
                (self.root / home['repository'] / base / 'maven-metadata.xml').write_text(metadata)

    def test_all84_homes_match_existing_package_associations(self):
        self.assertEqual(len(self.homes['artifacts']), 84)
        self.assertEqual(m.validate_homes(PROJECTS, self.homes), self.homes['artifacts'])
        self.assertEqual(len(m.validate_inventory(PROJECTS, self.inventory)), 312)

    def test_missing_duplicate_extra_or_changed_home_fails(self):
        for mutation in ['missing', 'duplicate', 'extra', 'changed']:
            changed = copy.deepcopy(self.homes)
            if mutation == 'missing': changed['artifacts'].pop()
            if mutation == 'duplicate': changed['artifacts'].append(changed['artifacts'][0])
            if mutation == 'extra': changed['artifacts'].append({'groupId':'org.fireflyframework','artifactId':'extra','repository':'extra','visibility':'public'})
            if mutation == 'changed': changed['artifacts'][0]['repository'] = '../unsafe'
            with self.subTest(mutation=mutation), self.assertRaises(ValueError):
                m.validate_homes(PROJECTS, changed)
        with self.assertRaises(ValueError):
            m.build_homes(PROJECTS, ASSOCIATIONS[:-20])

    def test_incomplete_duplicate_extra_or_bad_digest_inventory_fails(self):
        for mutation in ['missing', 'duplicate', 'extra', 'digest']:
            changed = copy.deepcopy(self.inventory)
            if mutation == 'missing': changed.pop()
            if mutation == 'duplicate': changed.append(changed[0])
            if mutation == 'extra': changed.append({'path':'../outside','sha256':'0'*64})
            if mutation == 'digest': changed[0]['sha256'] = 'invalid'
            with self.subTest(mutation=mutation), self.assertRaises(ValueError):
                m.validate_inventory(PROJECTS, changed)

    def test_snapshot_metadata_and_all312_timestamped_files_are_read(self):
        self.write_registry(PROJECTS, self.inventory, snapshot=True)
        server, base = self.server()
        proof = m.verify_mapped(PROJECTS, self.homes, self.inventory, base=base)
        self.assertEqual(len(proof), 312)
        self.assertEqual(len(server.requests), 312 + 168)
        self.assertEqual(sum(path.endswith('/maven-metadata.xml') for path in server.requests), 168)
        self.assertTrue(all('-20261018.123456-7' in row['remotePath'] for row in proof))
        self.assertTrue(all('-SNAPSHOT/' in path for path in server.requests))

    def test_final_release_all312_files_use_original_mapped_homes(self):
        projects = copy.deepcopy(PROJECTS)
        for project in projects: project['version'] = project['version'].removesuffix('-SNAPSHOT')
        inventory = fixture_inventory(projects)
        self.write_registry(projects, inventory, snapshot=False)
        server, base = self.server()
        proof = m.verify_mapped(projects, self.homes, inventory, base=base)
        self.assertEqual(len(proof), 312)
        self.assertEqual(len(server.requests), 312)
        self.assertFalse(any('maven-metadata' in path for path in server.requests))
        self.assertTrue(any('/fireflyframework-bom/' in path for path in server.requests))

    def test_tampered_or_missing_artifact_fails_verification(self):
        self.write_registry(PROJECTS, self.inventory, snapshot=True)
        server, base = self.server()
        target = next(self.root.rglob('*-sources.jar'))
        target.write_bytes(b'wrong bytes')
        with self.assertRaisesRegex(ValueError, 'digest'):
            m.verify_mapped(PROJECTS, self.homes, self.inventory, base=base)
        target.unlink()
        with self.assertRaisesRegex(ValueError, 'HTTP 404'):
            m.verify_mapped(PROJECTS, self.homes, self.inventory, base=base)

    def test_wrong_metadata_coordinates_missing_variant_or_mixed_timestamp_fails(self):
        self.write_registry(PROJECTS, self.inventory, snapshot=True)
        server, base = self.server()
        metadata = next(self.root.rglob('maven-metadata.xml'))
        original = metadata.read_text()
        cases = [original.replace('<groupId>org.fireflyframework</groupId>', '<groupId>wrong</groupId>'),
                 original.replace('<snapshotVersion>', '<removed>', 1).replace('</snapshotVersion>', '</removed>', 1),
                 original.replace('-20261018.123456-7</value>', '-20261018.111111-8</value>', 1)]
        for changed in cases:
            metadata.write_text(changed)
            with self.assertRaises(ValueError):
                m.verify_mapped(PROJECTS, self.homes, self.inventory, base=base)
        metadata.write_text(original)

    def test_plan_home_cannot_be_changed_to_another_valid_repository(self):
        changed = copy.deepcopy(self.homes)
        changed['artifacts'][0]['repository'] = 'another-valid-name'
        with self.assertRaisesRegex(ValueError, 'association'):
            m.validate_associations(PROJECTS, changed, ASSOCIATIONS)

    def test_snapshot_metadata_change_after_downloads_is_rejected(self):
        self.write_registry(PROJECTS, self.inventory, snapshot=True)
        server, base = self.server()
        original_read = m.Reader.read
        seen = set()
        def changed_read(reader, url, metadata=False):
            data = original_read(reader, url, metadata)
            if metadata and url in seen:
                return data + b'\n'
            if metadata:
                seen.add(url)
            return data
        with patch.object(m.Reader, 'read', changed_read):
            with self.assertRaisesRegex(ValueError, 'changed during verification'):
                m.verify_mapped(PROJECTS, self.homes, self.inventory, base=base)
        self.assertEqual(len(seen), 84)
        self.assertEqual(len(server.requests), 312 + 84 + 1)

    def test_staging_rejects_wrong_pom_and_changed_bytes(self):
        project = copy.deepcopy(m.publishable(PROJECTS)[0])
        project['packaging'] = 'pom'
        projects = [project]
        homes = m.build_homes(projects, ASSOCIATIONS)
        path = next(iter(m.required_paths(projects)))
        source = self.root / 'repository' / path
        source.parent.mkdir(parents=True)
        source.write_text('<project xmlns="http://maven.apache.org/POM/4.0.0"><groupId>wrong</groupId><artifactId>wrong</artifactId><version>1</version><packaging>pom</packaging></project>')
        inventory = [{'path':path, 'sha256':m.digest_file(source)}]
        with self.assertRaisesRegex(ValueError, 'coordinate'):
            m.prepare_stage(projects, homes, inventory, self.root / 'repository', self.root / 'stage')
        source.write_bytes(b'changed')
        with self.assertRaisesRegex(ValueError, 'changed'):
            m.prepare_stage(projects, homes, inventory, self.root / 'repository', self.root / 'stage')

    def test_regenerated_map_must_equal_canonical_committed_map(self):
        canonical = self.root / 'canonical.json'
        canonical.write_text(json.dumps(self.homes))
        m.validate_canonical_homes(PROJECTS, self.homes, canonical)
        changed = copy.deepcopy(ASSOCIATIONS)
        package = next(row for row in changed if row['name'].endswith('.fireflyframework-bom'))
        package['repository'] = 'another-valid-home'
        regenerated = m.build_homes(PROJECTS, changed)
        with self.assertRaisesRegex(ValueError, 'canonical'):
            m.validate_canonical_homes(PROJECTS, regenerated, canonical)
        with self.assertRaises(FileNotFoundError):
            m.validate_canonical_homes(PROJECTS, self.homes, self.root / 'missing.json')

    def test_final_zero_patch_matches_the_existing_calver_validator(self):
        projects = copy.deepcopy(PROJECTS)
        for project in projects:
            project['version'] = '26.10.00'
        self.assertEqual(len(m.required_paths(projects)), 312)

    def test_retry_validation_and_complete_verification_before_returning_proof(self):
        with patch.object(m, 'verify_mapped') as verify, patch.object(m.time, 'sleep') as sleep:
            for attempts, delay in [(0, 0), (1, -1)]:
                with self.assertRaisesRegex(ValueError, 'attempts'):
                    m.verify_with_retry(PROJECTS, self.homes, self.inventory, attempts=attempts, delay=delay)
            verify.assert_not_called()
            verify.side_effect = [ValueError('transient read'), ['complete proof']]
            self.assertEqual(m.verify_with_retry(PROJECTS, self.homes, self.inventory, attempts=2, delay=10), ['complete proof'])
            self.assertEqual(verify.call_count, 2)
            sleep.assert_called_once_with(10)

    def test_exhausted_verification_retries_fail_instead_of_returning_partial_proof(self):
        with patch.object(m, 'verify_mapped', side_effect=ValueError('digest mismatch')) as verify, patch.object(m.time, 'sleep'):
            with self.assertRaisesRegex(ValueError, 'digest mismatch'):
                m.verify_with_retry(PROJECTS, self.homes, self.inventory, attempts=3, delay=0)
            self.assertEqual(verify.call_count, 3)

    def test_verify_cli_retry_contract_writes_proof_only_after_complete_success(self):
        projects, homes, manifest, output = (self.root / name for name in ('projects.json', 'homes.json', 'manifest.json', 'proof.json'))
        for path, data in [(projects, PROJECTS), (homes, self.homes), (manifest, self.inventory)]:
            path.write_text(json.dumps(data))
        argv = ['github_packages.py', 'verify', '--projects', str(projects), '--homes', str(homes),
                '--manifest', str(manifest), '--output', str(output), '--attempts', '12', '--delay', '10']
        with patch.dict(os.environ, {'JAFLY_PACKAGES_TOKEN':'synthetic-only', 'GITHUB_ACTOR':'test-user'}, clear=True), patch.object(m.sys, 'argv', argv), patch.object(m, 'verify_with_retry') as verify, redirect_stdout(io.StringIO()):
            verify.side_effect = ValueError('incomplete read')
            with self.assertRaisesRegex(ValueError, 'incomplete'):
                m.main()
            self.assertFalse(output.exists())
            self.assertEqual(verify.call_args.kwargs['attempts'], 12)
            self.assertEqual(verify.call_args.kwargs['delay'], 10)
            verify.side_effect = None
            verify.return_value = self.inventory
            m.main()
            self.assertEqual(json.loads(output.read_text()), self.inventory)

    def test_plan_rejects_association_changes_before_staging(self):
        changed = copy.deepcopy(ASSOCIATIONS)
        next(row for row in changed if row['name'].endswith('.fireflyframework-bom'))['repository'] = 'different-home'
        associations = self.root / 'associations.jsonl'
        associations.write_text(''.join(json.dumps(row) + '\n' for row in changed))
        output = self.root / 'output'
        argv = ['github_packages.py', 'plan', '--root', str(SOURCE), '--associations', str(associations),
                '--repository', str(self.root / 'missing-repository'), '--output', str(output)]
        with patch.object(m.sys, 'argv', argv):
            with self.assertRaisesRegex(ValueError, 'canonical'):
                m.main()
        self.assertFalse(output.exists())

    def test_execution_rechecks_canonical_map_before_credentials_or_maven(self):
        plan = self.small_plan()
        canonical = json.loads(m.CANONICAL_HOMES.read_text())
        canonical['artifacts'][0]['repository'] = 'different-home'
        m.CANONICAL_HOMES.write_text(json.dumps(canonical))
        with patch.dict(os.environ, {}, clear=True), patch.object(m.subprocess, 'run') as run:
            with self.assertRaisesRegex(ValueError, 'canonical'):
                m.run_batch('/not/executed/mvn', plan)
            run.assert_not_called()

    def small_plan(self):
        project = copy.deepcopy(m.publishable(PROJECTS)[0])
        project['packaging'] = 'pom'
        projects = [project]
        homes = m.build_homes(projects, ASSOCIATIONS)
        inventory = fixture_inventory(projects)
        plan = self.root / 'plan'
        plan.mkdir()
        for filename, data in [('reactor-inventory.json',projects), ('package-homes.json',homes),
                               ('github-manifest.json',inventory), ('source-associations.json',ASSOCIATIONS)]:
            (plan / filename).write_text(json.dumps(data))
        for row in inventory:
            file = plan / 'artifacts' / row['path']
            file.parent.mkdir(parents=True, exist_ok=True)
            file.write_bytes(row['path'].encode())
        canonical = self.root / 'canonical.json'
        canonical.write_text(json.dumps(homes))
        canonical_patch = patch.object(m, 'CANONICAL_HOMES', canonical)
        canonical_patch.start()
        self.addCleanup(canonical_patch.stop)
        return plan

    def test_remote_execution_requires_credentials_before_starting_maven(self):
        plan = self.small_plan()
        with patch.dict(os.environ, {}, clear=True), patch.object(m.subprocess, 'run') as run:
            with self.assertRaisesRegex(ValueError, 'required'):
                m.run_batch('/not/executed/mvn', plan)
            run.assert_not_called()

    def test_maven_nonzero_fails_and_synthetic_token_is_redacted(self):
        plan = self.small_plan()
        fake = 'SYNTHETIC_TEST_CREDENTIAL_ONLY'
        with patch.dict(os.environ, {'JAFLY_PACKAGES_TOKEN':fake, 'GITHUB_ACTOR':'test-actor', 'MAVEN_ARGS':'-X'}, clear=True), patch.object(m.subprocess, 'run') as run:
            encoded = m.base64.b64encode(('test-actor:' + fake).encode()).decode()
            run.return_value = subprocess.CompletedProcess([], 1, 'HTTP 422 '+fake+' Basic '+encoded, '')
            with self.assertRaisesRegex(ValueError, 'failed'):
                m.run_batch('/not/executed/mvn', plan)
            call = run.call_args
            self.assertNotIn(fake, str(call.args[0]))
            self.assertNotIn('MAVEN_ARGS', call.kwargs['env'])
            self.assertNotIn(fake, (plan / 'settings.xml').read_text())
            self.assertNotIn(fake, (plan / 'publish-maven.log').read_text())
            self.assertNotIn(encoded, (plan / 'publish-maven.log').read_text())
            self.assertIn('[REDACTED]', (plan / 'publish-maven.log').read_text())
            self.assertEqual((plan / 'settings.xml').stat().st_mode & 0o777, 0o600)

    def test_unsafe_version_fails_before_accessing_files_or_network(self):
        projects = copy.deepcopy(PROJECTS)
        for project in projects:
            project['version'] = '../../outside'
        with self.assertRaisesRegex(ValueError, 'coordinate'):
            m.required_paths(projects)

    def test_redirect_strips_cross_origin_auth_and_rejects_https_downgrade(self):
        redirect = m.SafeRedirect()
        req = urllib.request.Request('https://maven.pkg.github.com/path', headers={'Authorization':'Basic synthetic-only'})
        target = redirect.redirect_request(req, None, 302, 'Found', {}, 'https://blob.example/path')
        self.assertNotIn('Authorization', target.headers)
        same = redirect.redirect_request(req, None, 302, 'Found', {}, 'https://maven.pkg.github.com/another')
        self.assertIn('Authorization', same.headers)
        with self.assertRaisesRegex(ValueError, 'insecure'):
            redirect.redirect_request(req, None, 302, 'Found', {}, 'http://blob.example/path')

    def test_one_pinned_goal_per_project_retains_original_pom_and_attachments(self):
        rendered, goals = m.render_publisher(PROJECTS, self.homes, self.root, self.root / 'repository')
        xml = ET.fromstring(rendered)
        ns = {'m':'http://maven.apache.org/POM/4.0.0'}
        executions = xml.findall('.//m:execution', ns)
        self.assertEqual(len(executions), 84)
        self.assertTrue(all(goal.startswith('org.apache.maven.plugins:maven-deploy-plugin:3.1.4:deploy-file@') for goal in goals))
        jar = next(e.find('m:configuration', ns) for e in executions if e.findtext('m:configuration/m:packaging', namespaces=ns) == 'jar')
        self.assertIsNotNone(jar.find('m:sources', ns))
        self.assertIsNotNone(jar.find('m:javadoc', ns))
        self.assertEqual(jar.findtext('m:generatePom', namespaces=ns), 'false')
        self.assertEqual(jar.findtext('m:skip', namespaces=ns), 'false')
        self.assertNotIn('TOKEN', rendered)


if __name__ == '__main__': unittest.main()

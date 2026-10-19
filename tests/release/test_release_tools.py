"""Release guards reject mixed versions, substituted tags, and incomplete registries."""
import importlib.util
import json
import subprocess
from pathlib import Path
import tempfile
import threading
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import zipfile

SCRIPT = Path(__file__).resolve().parents[2] / 'scripts' / 'release_tools.py'


class ReleaseToolsTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if not SCRIPT.exists():
            return
        spec = importlib.util.spec_from_file_location('release_tools', SCRIPT)
        cls.tools = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(cls.tools)

    def test_release_tooling_exists(self):
        self.assertTrue(SCRIPT.exists(), 'Release tooling must exist before qualification')

    def setUp(self):
        if not SCRIPT.exists() and self._testMethodName != 'test_release_tooling_exists':
            self.skipTest('Release tooling not implemented yet')
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.pom(self.root, 'fireflyframework-jafly', modules=['build/parent', 'build/bom', 'modules/kernel'])
        self.pom(self.root / 'build/parent', 'fireflyframework-parent')
        self.pom(self.root / 'build/bom', 'fireflyframework-bom')
        self.pom(self.root / 'modules/kernel', 'fireflyframework-kernel', packaging='jar')

    def pom(self, root, artifact, version='26.10.18', modules=(), packaging='pom', extra=''):
        root.mkdir(parents=True, exist_ok=True)
        (root / 'pom.xml').write_text(
            '<project xmlns="http://maven.apache.org/POM/4.0.0"><modelVersion>4.0.0</modelVersion>'
            f'<groupId>org.fireflyframework</groupId><artifactId>{artifact}</artifactId>'
            f'<version>{version}</version><packaging>{packaging}</packaging>{extra}'
            '<modules>' + ''.join(f'<module>{m}</module>' for m in modules) + '</modules></project>')

    def test_accepts_unified_explicit_versions(self):
        projects = self.tools.check_reactor(self.root, 'v26.10.18', expected=4)
        self.assertEqual(len(projects), 4)

    def test_accepts_version_inherited_from_the_local_parent(self):
        path = self.root / 'modules/kernel/pom.xml'
        source = path.read_text().replace('<version>26.10.18</version>', '', 1)
        source = source.replace('<artifactId>fireflyframework-kernel</artifactId>', '<parent><groupId>org.fireflyframework</groupId><artifactId>fireflyframework-parent</artifactId><version>26.10.18</version></parent><artifactId>fireflyframework-kernel</artifactId>')
        path.write_text(source)
        try:
            projects = self.tools.check_reactor(self.root)
        except ValueError as error:
            self.fail(f'Valid local version inheritance must work: {error}')
        self.assertEqual(projects[-1]['version'], '26.10.18')

    def test_explicitly_nonpublishable_projects_are_excluded(self):
        self.pom(self.root / 'modules/kernel', 'fireflyframework-kernel', packaging='jar', extra='<properties><maven.deploy.skip>true</maven.deploy.skip></properties>')
        projects = self.tools.check_reactor(self.root)
        paths = self.tools.required_paths(projects)
        self.assertFalse(any('fireflyframework-kernel/' in path for path in paths))

    def test_rejects_mixed_version(self):
        self.pom(self.root / 'modules/kernel', 'fireflyframework-kernel', '26.07.06')
        with self.assertRaisesRegex(ValueError, 'version'):
            self.tools.check_reactor(self.root, 'v26.10.18')

    def test_rejects_snapshot_release_tag(self):
        with self.assertRaisesRegex(ValueError, 'tag'):
            self.tools.check_reactor(self.root, 'v26.10.18-SNAPSHOT')

    def test_rejects_tag_different_from_pom(self):
        with self.assertRaisesRegex(ValueError, 'tag'):
            self.tools.check_reactor(self.root, 'v99.01.01')

    def test_rejects_snapshot_for_final_and_final_for_snapshot_job(self):
        with self.assertRaisesRegex(ValueError, 'SNAPSHOT'):
            self.tools.check_reactor(self.root, snapshot=True)

    def test_rejects_missing_original_projects(self):
        with self.assertRaisesRegex(ValueError, 'count'):
            self.tools.check_reactor(self.root, expected=86)

    def test_rejects_duplicate_coordinates(self):
        self.pom(self.root / 'modules/kernel', 'fireflyframework-parent')
        with self.assertRaisesRegex(ValueError, 'duplicate'):
            self.tools.check_reactor(self.root)

    def test_rejects_module_path_escape(self):
        self.pom(self.root, 'fireflyframework-jafly', modules=['../escape'])
        with self.assertRaisesRegex(ValueError, 'outside'):
            self.tools.check_reactor(self.root)

    def test_rejects_parent_bom_cycle(self):
        self.pom(self.root / 'build/bom', 'fireflyframework-bom', extra='<parent><groupId>org.fireflyframework</groupId><artifactId>fireflyframework-parent</artifactId><version>26.10.18</version></parent>')
        with self.assertRaisesRegex(ValueError, 'standalone'):
            self.tools.check_reactor(self.root)

    def test_rejects_stale_internal_dependency_version(self):
        self.pom(self.root / 'modules/kernel', 'fireflyframework-kernel', extra='<dependencies><dependency><groupId>org.fireflyframework</groupId><artifactId>fireflyframework-parent</artifactId><version>26.07.06</version></dependency></dependencies>')
        with self.assertRaisesRegex(ValueError, 'internal'):
            self.tools.check_reactor(self.root)

    def test_tag_resolves_exact_commit(self):
        subprocess.run(['git', 'init', '-q', str(self.root)], check=True)
        subprocess.run(['git', '-C', str(self.root), '-c', 'user.name=Test', '-c', 'user.email=test@example.org', 'commit', '--allow-empty', '-qm', 'Initial'], check=True)
        subprocess.run(['git', '-C', str(self.root), 'tag', 'v26.10.18'], check=True)
        self.tools.check_tag_commit(self.root, 'v26.10.18')
        subprocess.run(['git', '-C', str(self.root), '-c', 'user.name=Test', '-c', 'user.email=test@example.org', 'commit', '--allow-empty', '-qm', 'Different'], check=True)
        with self.assertRaisesRegex(ValueError, 'commit'):
            self.tools.check_tag_commit(self.root, 'v26.10.18')

    def tagged_remote(self, directory, annotated=False):
        directory.mkdir()
        source, remote, runner = (directory / name for name in ('source', 'remote.git', 'runner'))
        source.mkdir()
        self.git(source, 'init', '-q')
        self.git(source, 'commit', '--allow-empty', '-qm', 'Initial')
        arguments = ['tag', 'v26.10.18']
        if annotated:
            arguments.extend(['-a', '-m', 'Release'])
        self.git(source, *arguments)
        self.git(directory, 'init', '--bare', '-q', str(remote))
        self.git(source, 'remote', 'add', 'origin', str(remote))
        self.git(source, 'push', 'origin', 'HEAD:main', 'refs/tags/v26.10.18')
        self.git(directory, 'clone', '-q', '--branch', 'v26.10.18', str(remote), str(runner))
        return source, runner

    def git(self, directory, *arguments):
        return subprocess.check_output(
            ['git', '-C', str(directory), '-c', 'user.name=Test', '-c',
             'user.email=test@example.org', *arguments], text=True, stderr=subprocess.DEVNULL).strip()

    def test_remote_lightweight_and_annotated_tags_match_the_checkout(self):
        for annotated in (False, True):
            with self.subTest(annotated=annotated):
                _, runner = self.tagged_remote(self.root / str(annotated), annotated)
                self.tools.check_tag_commit(runner, 'v26.10.18', remote='origin')

    def test_rejects_remote_tag_moved_after_the_runner_checkout(self):
        source, runner = self.tagged_remote(self.root / 'moved', annotated=True)
        self.git(source, 'commit', '--allow-empty', '-qm', 'Different')
        self.git(source, 'tag', '-f', 'v26.10.18')
        self.git(source, 'push', '--force', 'origin', 'refs/tags/v26.10.18')
        self.tools.check_tag_commit(runner, 'v26.10.18')
        with self.assertRaisesRegex(ValueError, 'remote.*commit'):
            self.tools.check_tag_commit(runner, 'v26.10.18', remote='origin')

    def test_rejects_remote_tag_deleted_after_the_runner_checkout(self):
        source, runner = self.tagged_remote(self.root / 'deleted')
        self.git(source, 'push', 'origin', ':refs/tags/v26.10.18')
        with self.assertRaises(subprocess.CalledProcessError):
            self.tools.check_tag_commit(runner, 'v26.10.18', remote='origin')

    def test_rejects_unavailable_release_remote(self):
        _, runner = self.tagged_remote(self.root / 'unavailable')
        self.git(runner, 'remote', 'set-url', 'origin', str(self.root / 'missing.git'))
        with self.assertRaises(subprocess.CalledProcessError):
            self.tools.check_tag_commit(runner, 'v26.10.18', remote='origin')

    def test_repository_inventory_requires_all_attachments(self):
        projects = self.tools.check_reactor(self.root)
        repository = self.root / 'repository'
        for project in projects[1:]:
            directory = repository / 'org/fireflyframework' / project['artifactId'] / project['version']
            directory.mkdir(parents=True)
            base = f"{project['artifactId']}-{project['version']}"
            (directory / f'{base}.pom').write_text('test-pom')
        with self.assertRaisesRegex(ValueError, 'missing'):
            self.tools.inventory_repository(projects, repository)

    def test_bundle_inventory_rejects_missing_project(self):
        bundle = self.root / 'bundle.zip'
        with zipfile.ZipFile(bundle, 'w') as archive:
            archive.writestr('org/fireflyframework/fireflyframework-parent/26.10.18/fireflyframework-parent-26.10.18.pom', 'pom')
        with self.assertRaisesRegex(ValueError, 'missing'):
            self.tools.inventory_bundle(self.tools.check_reactor(self.root), bundle)

    def test_bundle_inventory_rejects_path_traversal(self):
        bundle = self.root / 'bundle.zip'
        with zipfile.ZipFile(bundle, 'w') as archive:
            archive.writestr('../escape', 'bad')
        with self.assertRaisesRegex(ValueError, 'path'):
            self.tools.inventory_bundle(self.tools.check_reactor(self.root), bundle)

    def test_remote_verification_cannot_skip_all_attempts(self):
        with self.assertRaisesRegex(ValueError, 'attempts'):
            self.tools.verify_remote([{'path': 'artifact', 'sha256': '0' * 64}], 'https://example.invalid', attempts=0)

    def test_complete_inventory_and_signed_bundle(self):
        projects = self.tools.check_reactor(self.root)
        repository = self.root / 'repository'
        for path in self.tools.required_paths(projects):
            file = repository / path
            file.parent.mkdir(parents=True, exist_ok=True)
            file.write_bytes(b'artifact')
        inventory = self.tools.inventory_repository(projects, repository)
        self.assertEqual(len(inventory), 6)
        self.assertFalse(any('fireflyframework-jafly/' in item['path'] for item in inventory))
        bundle = self.root / 'complete.zip'
        with zipfile.ZipFile(bundle, 'w') as archive:
            for path in self.tools.required_paths(projects, signatures=True):
                archive.writestr(path, 'signed-artifact')
        self.assertEqual(len(self.tools.inventory_bundle(projects, bundle)), 12)


    def test_bundle_inventory_allows_optional_tests_classifier(self):
        projects = self.tools.check_reactor(self.root)
        # Build a signed-required set plus an optional -tests.jar for a jar module.
        jar = next(p for p in projects if p['packaging'] == 'jar' and p.get('publish', True))
        required = self.tools.required_paths(projects, signatures=True)
        tests = (
            f"{jar['groupId'].replace('.', '/')}/{jar['artifactId']}/{jar['version']}/"
            f"{jar['artifactId']}-{jar['version']}-tests.jar"
        )
        with tempfile.NamedTemporaryFile(suffix='.zip') as handle:
            with zipfile.ZipFile(handle.name, 'w') as archive:
                for path in sorted(required):
                    archive.writestr(path, path.encode())
                archive.writestr(tests, b'test-classes')
                archive.writestr(tests + '.asc', b'sig')
            inventory = self.tools.inventory_bundle(projects, handle.name)
        paths = {row['path'] for row in inventory}
        self.assertTrue(tests in paths)
        self.assertTrue((tests + '.asc') in paths)
        self.assertTrue(required.issubset(paths))

    def test_remote_verification_rejects_wrong_bytes_and_http_failure(self):
        import hashlib
        class Handler(BaseHTTPRequestHandler):
            def do_GET(self):
                self.send_response(404 if self.path.endswith('missing') else 200)
                self.end_headers()
                self.wfile.write(b'actual')
            def log_message(self, *args):
                pass
        server = ThreadingHTTPServer(('127.0.0.1', 0), Handler)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        self.addCleanup(server.server_close)
        self.addCleanup(server.shutdown)
        url = f'http://127.0.0.1:{server.server_port}'
        correct = {'path': 'artifact.jar', 'sha256': hashlib.sha256(b'actual').hexdigest()}
        self.tools.verify_remote([correct], url)
        with self.assertRaisesRegex(ValueError, 'digest'):
            self.tools.verify_remote([dict(correct, sha256='0' * 64)], url)
        with self.assertRaisesRegex(ValueError, 'HTTP'):
            self.tools.verify_remote([dict(correct, path='missing')], url)


if __name__ == '__main__':
    unittest.main()

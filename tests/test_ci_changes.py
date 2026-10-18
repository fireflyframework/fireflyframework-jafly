import importlib.util
import os
from pathlib import Path
import subprocess
import tempfile
import unittest


SCRIPT = Path(__file__).resolve().parents[1] / 'scripts/ci_changes.py'


class CiChangeTests(unittest.TestCase):
    def setUp(self):
        self.assertTrue(SCRIPT.exists(), 'CI change classification is missing')
        spec = importlib.util.spec_from_file_location('ci_changes', SCRIPT)
        self.tools = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(self.tools)

    def test_prose_and_published_artwork_do_not_need_java(self):
        self.assertFalse(self.tools.requires_java(['README.md', 'modules/cqrs/README.md',
                                                  'docs/site/stylesheets/jafly.css',
                                                  'docs/site/assets/diagrams/request-flow.svg']))

    def test_a_tutorial_java_change_still_needs_java(self):
        self.assertTrue(self.tools.requires_java(['docs/site/getting-started.md',
                                                 'docs/examples/getting-started/src/main/java/com/example/jafly/ApplicationsController.java']))

    def test_build_configuration_and_resources_need_java(self):
        for path in ['build/parent/pom.xml', 'docs/examples/onboarding-event-store/pom.xml',
                     'modules/security-idp-internal-db/src/main/resources/application-internal-db-example.yml',
                     '.github/workflows/ci.yml', 'scripts/ci_changes.py']:
            with self.subTest(path=path):
                self.assertTrue(self.tools.requires_java([path]))

    def test_a_similarly_named_source_directory_is_not_documentation(self):
        self.assertTrue(self.tools.requires_java(['docs/site-extension/Runtime.java']))

    def test_moving_java_source_to_a_readme_still_runs_java_verification(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            def git(*args):
                return subprocess.check_output(['git', '-c', 'user.name=CI test',
                                                '-c', 'user.email=ci@example.invalid', *args],
                                               cwd=root, stderr=subprocess.DEVNULL, text=True).strip()
            git('init')
            (root / 'Service.java').write_text('class Service {}\n')
            git('add', 'Service.java')
            git('commit', '-m', 'Add Java source')
            base = git('rev-parse', 'HEAD')
            git('mv', 'Service.java', 'README.md')
            git('commit', '-m', 'Move source to documentation')
            output = root / 'output'
            environment = {**os.environ, 'GITHUB_OUTPUT': str(output)}
            subprocess.run(['python3', str(SCRIPT), '--event', 'push', '--base', base],
                           cwd=root, env=environment, check=True, capture_output=True)
            self.assertIn('java=true', output.read_text())

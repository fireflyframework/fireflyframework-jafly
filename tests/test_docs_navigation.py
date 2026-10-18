"""The learning path must retain complete capability and module access."""
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('generate_docs_nav', ROOT / 'scripts/generate_docs_nav.py')
nav = importlib.util.module_from_spec(spec)
spec.loader.exec_module(nav)


class JourneyNavigationTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        paths = ['installation.md', 'getting-started.md', 'examples.md',
                 'guides/onboarding-event-store.md', 'guides/onboarding-coordination.md']
        paths += [f'capabilities/{slug}.md' for slug, _, _ in nav.CAPABILITIES]
        for name in paths:
            path = self.root / 'docs/site' / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text('# Guide\n')

    def test_learning_path_and_all_eight_journeys_have_ordered_navigation(self):
        rendered = nav.journey_navigation(self.root)
        self.assertIn('  - Learning path:', rendered)
        self.assertLess(rendered.index('getting-started.md'), rendered.index('guides/onboarding-event-store.md'))
        self.assertLess(rendered.index('guides/onboarding-event-store.md'), rendered.index('guides/onboarding-coordination.md'))
        positions = [rendered.index(f'capabilities/{slug}.md') for slug, _, _ in nav.CAPABILITIES]
        self.assertEqual(positions, sorted(positions))
        self.assertEqual(rendered.count('capabilities/'), 8)

    def test_every_catalog_module_keeps_exactly_one_sidebar_link(self):
        rendered, _ = nav.generated(ROOT)
        catalog = json.loads((ROOT / 'docs/site/assets/module-catalog.json').read_text())
        for item in catalog:
            self.assertEqual(rendered.count(f"modules/{item['slug']}.md"), 1, item['slug'])

    def test_missing_learning_guide_fails_instead_of_hiding_the_route(self):
        (self.root / 'docs/site/guides/onboarding-coordination.md').unlink()
        with self.assertRaisesRegex(ValueError, 'onboarding-coordination'):
            nav.journey_navigation(self.root)

    def test_missing_capability_page_fails_instead_of_hiding_the_route(self):
        (self.root / 'docs/site/capabilities/domain-and-data.md').unlink()
        with self.assertRaisesRegex(ValueError, 'domain-and-data'):
            nav.journey_navigation(self.root)


if __name__ == '__main__':
    unittest.main()

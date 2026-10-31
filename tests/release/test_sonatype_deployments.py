"""Read-only Portal reconciliation refuses incomplete or ambiguous inventory."""
import base64
import copy
import importlib.util
import io
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch
import urllib.error

SOURCE = Path(__file__).resolve().parents[2]
SCRIPT = SOURCE / 'scripts/sonatype_deployments.py'
spec = importlib.util.spec_from_file_location('sonatype_deployments', SCRIPT)
m = importlib.util.module_from_spec(spec)
if SCRIPT.exists():
    spec.loader.exec_module(m)


def deployment(number=1, state='VALIDATED', version='26.10.22'):
    return {'deploymentId': f'deployment-{number}', 'deploymentName': 'PRIVATE-NAME',
            'namespace': 'org.fireflyframework', 'deploymentState': state,
            'createTimestamp': '2026-10-31T10:00:00.000Z',
            'updateTimestamp': '2026-10-31T10:01:00.000Z',
            'deploymentComponents': [
                {'purl': f'pkg:maven/org.fireflyframework/fireflyframework-parent@{version}',
                 'name': 'PRIVATE-FILENAME', 'path': 'PRIVATE-PATH',
                 'errors': ['PRIVATE-ERROR'], 'warnings': ['PRIVATE-WARNING']} ]}


def page(rows=None, number=0, total=None, size=2):
    rows = [deployment()] if rows is None else rows
    total = len(rows) if total is None else total
    return {'deployments': rows, 'page': number, 'pageSize': size,
            'pageCount': (total + size - 1) // size, 'totalResultCount': total}


TEST_URL = 'https://central.sonatype.com/api/v1/publisher/deployments?namespace=org.fireflyframework&page=0&size=2&sortField=createTimestamp&sortDirection=asc'


class Response(io.BytesIO):
    status = 200
    headers = {'Content-Type': 'application/json'}

    def __init__(self, payload, url):
        super().__init__(payload if isinstance(payload, bytes) else json.dumps(payload).encode())
        self.url = url

    def geturl(self):
        return self.url


class Api:
    def __init__(self, replies):
        self.replies = list(replies)
        self.requests = []

    def open(self, request, timeout):
        self.requests.append((request, timeout))
        reply = self.replies.pop(0)
        if isinstance(reply, Exception):
            raise reply
        return reply if isinstance(reply, Response) else Response(reply, request.full_url)


class PortalInventoryTests(unittest.TestCase):
    def setUp(self):
        if not hasattr(m, 'collect'):
            self.fail('The read-only deployment inventory is not implemented.')

    def collect(self, replies):
        api = Api(replies)
        result = m.collect('TOKEN-SENTINEL', opener=api, page_size=2)
        return result, api

    def test_complete_inventory_has_exact_pending_gav_and_no_raw_fields(self):
        result, api = self.collect([page(), page()])
        self.assertEqual('ACCOUNT_VISIBLE_NAMESPACE_STABLE_TWO_SWEEPS', result['status'])
        self.assertEqual([{'groupId': 'org.fireflyframework',
                          'artifactId': 'fireflyframework-parent', 'version': '26.10.22'}],
                         result['pendingDeployments'][0]['components'])
        self.assertEqual('VALIDATED', result['pendingDeployments'][0]['state'])
        self.assertFalse(result['versionAllocated'])
        self.assertFalse(result['atomicSnapshot'])
        self.assertNotIn('PRIVATE-', json.dumps(result))
        self.assertEqual(2, len(api.requests))
        for request, timeout in api.requests:
            self.assertEqual('GET', request.get_method())
            self.assertEqual('https://central.sonatype.com/api/v1/publisher/deployments?namespace=org.fireflyframework&page=0&size=2&sortField=createTimestamp&sortDirection=asc', request.full_url)
            self.assertEqual('Bearer TOKEN-SENTINEL', request.get_header('Authorization'))
            self.assertLessEqual(timeout, 10)

    def test_multiple_pages_all_states_and_duplicate_files_collapse_to_one_gav(self):
        first = deployment(1, 'PUBLISHED')
        first['deploymentComponents'].append(copy.deepcopy(first['deploymentComponents'][0]))
        pages = [page([first, deployment(2, 'FAILED')], total=3),
                 page([deployment(3, 'VALIDATING')], number=1, total=3)]
        result, _ = self.collect(pages + copy.deepcopy(pages))
        self.assertEqual(3, result['deploymentCount'])
        self.assertEqual(2, len(result['pendingDeployments']))
        self.assertEqual(1, len(result['deployments'][0]['components']))

    def test_empty_namespace_is_explicit_authenticated_zero_not_a_404(self):
        for count in (0, 1):
            reply = page([]); reply['pageCount'] = count
            result, _ = self.collect([reply, copy.deepcopy(reply)])
            self.assertEqual([], result['pendingDeployments'])
            self.assertEqual(0, result['deploymentCount'])

    def test_auth_not_found_server_and_redirect_errors_never_become_empty(self):
        for code in (301, 302, 307, 308, 400, 401, 403, 404, 429, 500):
            error = urllib.error.HTTPError('https://central.sonatype.com', code,
                                           'TOKEN-SENTINEL PRIVATE-ERROR', {}, io.BytesIO(b'SECRET'))
            with self.subTest(code=code), self.assertRaises(m.InventoryError) as caught:
                self.collect([error])
            self.assertNotIn('SECRET', str(caught.exception))
            self.assertNotIn('TOKEN', str(caught.exception))

    def test_unknown_pending_and_missing_or_empty_components_refuse(self):
        for state in ('PENDING', 'DELETING', '', None):
            with self.subTest(state=state), self.assertRaises(m.InventoryError):
                self.collect([page([deployment(state=state)])])
        for value in ([], None, {}, 'SECRET'):
            row = deployment(); row['deploymentComponents'] = value
            with self.subTest(value=value), self.assertRaises(m.InventoryError):
                self.collect([page([row])])

    def test_missing_duplicate_wrong_namespace_and_malformed_purls_refuse(self):
        for purl in ('pkg:maven/another/group@26.10.22', 'pkg:npm/foo@1',
                     'pkg:maven/org.fireflyframework/a@',
                     'pkg:maven/org.fireflyframework/a@26.10.22?classifier=sources',
                     'pkg:maven/org.fireflyframework/a@26.10.22%0Asecret'):
            row = deployment(); row['deploymentComponents'][0]['purl'] = purl
            with self.subTest(purl=purl), self.assertRaises(m.InventoryError):
                self.collect([page([row])])
        for key in ('deploymentId', 'namespace', 'deploymentState', 'deploymentComponents',
                    'createTimestamp', 'updateTimestamp'):
            row = deployment(); del row[key]
            with self.subTest(key=key), self.assertRaises(m.InventoryError):
                self.collect([page([row])])
        row = deployment(); row['namespace'] = 'another'
        with self.assertRaises(m.InventoryError): self.collect([page([row])])
        with self.assertRaises(m.InventoryError):
            self.collect([page([deployment(), deployment()], total=2)])

    def test_page_metadata_bool_negative_missing_wrong_page_and_counts_refuse(self):
        for key in ('page', 'pageSize', 'pageCount', 'totalResultCount'):
            for value in (True, -1, '1', None):
                reply = page(); reply[key] = value
                with self.subTest(key=key, value=value), self.assertRaises(m.InventoryError):
                    self.collect([reply])
            reply = page(); del reply[key]
            with self.assertRaises(m.InventoryError): self.collect([reply])
        for patch_value in ({'page': 1}, {'pageSize': 0}, {'pageCount': 2},
                            {'totalResultCount': 2}, {'pageSize': 3}):
            reply = page(); reply.update(patch_value)
            with self.subTest(patch=patch_value), self.assertRaises(m.InventoryError):
                self.collect([reply])

    def test_shifted_total_between_pages_refuses(self):
        with self.assertRaises(m.InventoryError):
            self.collect([page([deployment(1), deployment(2)], total=3),
                          page([deployment(3)], number=1, total=4)])

    def test_repeated_sweep_change_in_state_component_or_timestamp_refuses(self):
        for field, value in (('deploymentState', 'FAILED'),
                             ('updateTimestamp', '2026-10-31T10:02:00Z'),
                             ('deploymentComponents', deployment(version='26.10.23')['deploymentComponents'])):
            second = deployment(); second[field] = value
            with self.subTest(field=field), self.assertRaises(m.InventoryError):
                self.collect([page(), page([second])])

    def test_caps_refuse_without_unbounded_pagination(self):
        reply = page(); reply.update(pageCount=101, totalResultCount=202)
        with self.assertRaises(m.InventoryError): self.collect([reply])
        row = deployment(); row['deploymentComponents'] *= 10001
        with self.assertRaises(m.InventoryError): self.collect([page([row])])

    def test_json_bytes_duplicate_keys_invalid_utf8_nonobject_and_body_cap_refuse(self):
        for body in (b'{"page":0,"page":1}', b'\xff', b'[]', b'null', b'{',
                     b' ' * (2 * 1024 * 1024 + 1)):
            api = Api([Response(body, TEST_URL)])
            with self.subTest(body=body[:20]), self.assertRaises(m.InventoryError):
                m.collect('TOKEN', opener=api, page_size=2)

    def test_official_host_no_redirect_and_json_content_type_are_mandatory(self):
        for url, content in (('https://evil.example/api', 'application/json'),
                             (TEST_URL, 'text/html')):
            response = Response(page(), url); response.headers = {'Content-Type': content}
            with self.assertRaises(m.InventoryError): self.collect([response])
        request = __import__('urllib.request', fromlist=['Request']).Request('https://central.sonatype.com')
        with self.assertRaises(m.InventoryError):
            m.NoRedirect().redirect_request(request, None, 302, 'SECRET', {}, 'https://evil.example')

    def test_transport_errors_are_constant_without_url_credentials_or_response(self):
        with self.assertRaises(m.InventoryError) as caught:
            self.collect([OSError('TOKEN-SENTINEL SECRET response')])
        self.assertEqual('Portal transport failed.', str(caught.exception))

    def test_token_pair_is_encoded_only_in_memory_and_missing_or_control_values_refuse(self):
        self.assertEqual(base64.b64encode(b'user:password').decode(), m.token('user', 'password'))
        for user, password in (('', 'p'), ('u', ''), ('u\n', 'p'), ('u:x', 'p')):
            with self.assertRaises(m.InventoryError): m.token(user, password)

    def test_outer_deadline_kills_worker_and_writes_only_safe_failure(self):
        with tempfile.TemporaryDirectory() as directory:
            out = Path(directory) / 'report.json'
            with patch.object(m.subprocess, 'run', side_effect=subprocess.TimeoutExpired('SECRET', 120)) as run:
                self.assertEqual(1, m.run_bounded(out))
            self.assertEqual(120, run.call_args.kwargs['timeout'])
            result = json.loads(out.read_text())
            self.assertEqual('INCOMPLETE', result['status'])
            self.assertFalse(result['versionAllocated'])
            self.assertNotIn('SECRET', out.read_text())

    def test_default_presence_step_remains_presence_only_and_does_not_launch_inventory(self):
        workflow = (SOURCE / '.github/workflows/publishing-preflight.yml').read_text()
        block = workflow.split("          python3 - <<'PY'\n", 1)[1].split("          PY\n", 1)[0]
        script = '\n'.join(line[10:] for line in block.splitlines())
        with tempfile.TemporaryDirectory() as directory:
            environment = {'PATH': os.environ['PATH'], 'REQUIRE_CROSS_REPOSITORY_TOKEN': 'true'}
            for name in ('JAFLY_PACKAGES_TOKEN', 'SONATYPE_USERNAME', 'SONATYPE_PASSWORD',
                         'GPG_PRIVATE_KEY', 'GPG_PASSPHRASE'):
                environment[name] = 'SYNTHETIC-' + name
            result = subprocess.run([os.sys.executable, '-B', '-c', script], cwd=directory,
                                    env=environment, capture_output=True, text=True, timeout=10)
            self.assertEqual(0, result.returncode)
            report = json.loads((Path(directory) / 'publishing-configuration.json').read_text())
            self.assertEqual([], report['missing_required_names'])
            self.assertEqual('Presence only; no registry or signing authorization proved.', report['qualification'])
            self.assertNotIn('SYNTHETIC-', result.stdout)
            self.assertEqual(['publishing-configuration.json'], sorted(p.name for p in Path(directory).iterdir()))

    def test_timeout_overwrites_a_worker_report_that_finished_after_its_deadline(self):
        with tempfile.TemporaryDirectory() as directory:
            out = Path(directory) / 'report.json'
            def overrun(*args, **kwargs):
                out.write_text('{"status":"ACCOUNT_VISIBLE_NAMESPACE_STABLE_TWO_SWEEPS"}')
                raise subprocess.TimeoutExpired('SECRET', 120)
            with patch.object(m.subprocess, 'run', side_effect=overrun):
                self.assertEqual(1, m.run_bounded(out))
            self.assertEqual('INCOMPLETE', json.loads(out.read_text())['status'])
            self.assertEqual(0o600, out.stat().st_mode & 0o777)

    def test_cli_missing_credentials_refuses_without_network_or_secret_output(self):
        with tempfile.TemporaryDirectory() as directory:
            out = Path(directory) / 'report.json'
            result = subprocess.run([os.sys.executable, '-B', str(SCRIPT), '--output', str(out)],
                                    env={'PATH': os.environ['PATH']}, capture_output=True, text=True, timeout=10)
            self.assertEqual(1, result.returncode)
            self.assertEqual('INCOMPLETE', json.loads(out.read_text())['status'])
            self.assertNotIn('Traceback', result.stderr)


if __name__ == '__main__':
    unittest.main()

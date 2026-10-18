"""Publish frozen Maven artifacts using the reviewed package-home policy."""
import argparse
import base64
from datetime import datetime
import hashlib
import importlib.util
import json
import os
from pathlib import Path, PurePosixPath
import re
import shutil
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET

OWNER = 'fireflyframework'
JAFLY_REPOSITORY = 'fireflyframework-jafly'
JAFLY_REPOSITORY_ID = 1423952543
PLUGIN = 'org.apache.maven.plugins:maven-deploy-plugin:3.1.4:deploy-file'
NS_URI = 'http://maven.apache.org/POM/4.0.0'
NS = {'m': NS_URI}
REPO = re.compile(r'[a-z0-9][a-z0-9._-]*')
SHA = re.compile(r'[0-9a-f]{64}')
CANONICAL_HOMES = Path(__file__).with_name('package-homes.json')
VERSION = re.compile(r'\d{2}\.(?:0[1-9]|1[0-2])\.\d{2}(?:-SNAPSHOT)?')


def release_tools(root):
    spec = importlib.util.spec_from_file_location('jafly_release_tools', Path(root) / 'scripts/release_tools.py')
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def publishable(projects):
    result = [project for project in projects if project.get('publish') is True]
    versions = {p['version'] for p in result}
    if not result or len(versions) != 1:
        raise ValueError('one nonempty publication version required')
    if any(p['groupId'] != 'org.fireflyframework' or p['packaging'] not in ('pom', 'jar')
           or not REPO.fullmatch(p['artifactId']) or not VERSION.fullmatch(p['version']) for p in result):
        raise ValueError('unsupported publication coordinate or packaging')
    keys = [p['groupId'] + ':' + p['artifactId'] for p in result]
    if len(keys) != len(set(keys)):
        raise ValueError('duplicate reactor publication coordinate')
    return result


def required_paths(projects):
    paths = set()
    for project in publishable(projects):
        base = f'{project["groupId"].replace(".", "/")}/{project["artifactId"]}/{project["version"]}/{project["artifactId"]}-{project["version"]}'
        paths.add(base + '.pom')
        if project['packaging'] == 'jar':
            paths.update(base + suffix for suffix in ('.jar', '-sources.jar', '-javadoc.jar'))
    return paths


def build_homes(projects, associations):
    by_name = {}
    for row in associations:
        if row['name'] in by_name:
            raise ValueError('duplicate package association')
        by_name[row['name']] = row
    entries = []
    for project in publishable(projects):
        name = project['groupId'] + '.' + project['artifactId']
        if name not in by_name:
            raise ValueError('missing existing package home: ' + name)
        row = by_name[name]
        if not REPO.fullmatch(row['repository']) or row['visibility'] not in ('public', 'private'):
            raise ValueError('invalid captured package association')
        entries.append({key: project[key] for key in ('groupId', 'artifactId')} |
                       {'repository': JAFLY_REPOSITORY, 'visibility': row['visibility']})
    result = {'schema': 1, 'owner': OWNER, 'publicationMode': 'jafly-bootstrap', 'associationSha256':
              hashlib.sha256(json.dumps(associations, sort_keys=True).encode()).hexdigest(),
              'artifacts': sorted(entries, key=lambda row: row['artifactId'])}
    validate_homes(projects, result)
    return result


def validate_homes(projects, homes):
    if homes.get('schema') != 1 or homes.get('owner') != OWNER or not SHA.fullmatch(homes.get('associationSha256', '')):
        raise ValueError('untrusted package-home schema or owner')
    mode = homes.get('publicationMode')
    if mode not in (None, 'jafly-bootstrap'):
        raise ValueError('unsupported package publication mode')
    expected = {p['groupId'] + ':' + p['artifactId'] for p in publishable(projects)}
    entries = homes.get('artifacts', [])
    seen = set()
    for entry in entries:
        if set(entry) != {'groupId', 'artifactId', 'repository', 'visibility'}:
            raise ValueError('unexpected package-home fields')
        key = entry['groupId'] + ':' + entry['artifactId']
        if key in seen or key not in expected or not REPO.fullmatch(entry['repository']) or entry['visibility'] not in ('public', 'private'):
            raise ValueError('invalid, duplicate or extra package home')
        if mode == 'jafly-bootstrap' and entry['repository'] != JAFLY_REPOSITORY:
            raise ValueError('bootstrap package association must target Jafly')
        seen.add(key)
    if seen != expected:
        raise ValueError('missing package homes')
    return entries


def validate_associations(projects, homes, associations):
    validate_homes(projects, homes)
    if homes != build_homes(projects, associations):
        raise ValueError('package-home map differs from captured associations')


def validate_canonical_homes(projects, homes, canonical=None):
    canonical = CANONICAL_HOMES if canonical is None else Path(canonical)
    committed = json.loads(canonical.read_text())
    try:
        validate_homes(projects, committed)
    except ValueError:
        raise ValueError('invalid canonical package-home map') from None
    if homes != committed:
        raise ValueError('regenerated package homes differ from the canonical committed map')


def validate_inventory(projects, inventory):
    expected = required_paths(projects)
    seen = set()
    for item in inventory:
        path = PurePosixPath(item.get('path', ''))
        if set(item) != {'path', 'sha256'} or path.is_absolute() or '..' in path.parts or str(path) not in expected:
            raise ValueError('unexpected inventory path or fields')
        if str(path) in seen or not SHA.fullmatch(item['sha256']):
            raise ValueError('duplicate path or invalid digest')
        seen.add(str(path))
    if seen != expected:
        raise ValueError('incomplete artifact inventory')
    return inventory


def digest_file(path):
    digest = hashlib.sha256()
    with Path(path).open('rb') as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b''):
            digest.update(chunk)
    return digest.hexdigest()


def validate_installed_pom(project, path):
    document = ET.parse(path).getroot()
    values = {}
    for name in ('groupId', 'artifactId', 'version'):
        values[name] = document.findtext('m:' + name, namespaces=NS)
        if name != 'artifactId' and not values[name]:
            values[name] = document.findtext('m:parent/m:' + name, namespaces=NS)
    values['packaging'] = document.findtext('m:packaging', namespaces=NS) or 'jar'
    if any(values[name] != project[name] for name in values):
        raise ValueError('installed POM coordinate differs from reactor: ' + project['artifactId'])


def prepare_stage(projects, homes, inventory, repository, output):
    validate_homes(projects, homes)
    validate_inventory(projects, inventory)
    repository, output = Path(repository).resolve(), Path(output).resolve()
    if output.exists():
        raise ValueError('refusing to overwrite an existing artifact stage')
    for item in inventory:
        source = (repository / item['path']).resolve()
        if not source.is_relative_to(repository) or not source.is_file() or digest_file(source) != item['sha256']:
            raise ValueError('missing or changed installed artifact: ' + item['path'])
    for project in publishable(projects):
        base = f'{project["groupId"].replace(".", "/")}/{project["artifactId"]}/{project["version"]}/{project["artifactId"]}-{project["version"]}.pom'
        validate_installed_pom(project, repository / base)
    output.mkdir(parents=True)
    for item in inventory:
        target = output / item['path']
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(repository / item['path'], target)
        if digest_file(target) != item['sha256']:
            raise ValueError('artifact changed during staging: ' + item['path'])


def render_publisher(projects, homes, artifacts, local_repository=None):
    entries = validate_homes(projects, homes)
    by_ga = {entry['groupId'] + ':' + entry['artifactId']: entry for entry in entries}
    ET.register_namespace('', NS_URI)
    tag = lambda name: '{' + NS_URI + '}' + name
    project = ET.Element(tag('project'))
    def child(parent, name, value):
        node = ET.SubElement(parent, tag(name))
        node.text = str(value)
        return node
    for name, value in [('modelVersion','4.0.0'), ('groupId','com.example.jafly.tools'),
                        ('artifactId','mapped-publisher'), ('version','1'), ('packaging','pom')]:
        child(project, name, value)
    plugin = ET.SubElement(ET.SubElement(ET.SubElement(project, tag('build')), tag('plugins')), tag('plugin'))
    for name, value in [('groupId','org.apache.maven.plugins'),('artifactId','maven-deploy-plugin'),('version','3.1.4')]:
        child(plugin, name, value)
    executions = ET.SubElement(plugin, tag('executions'))
    goals = []
    for item in publishable(projects):
        home = by_ga[item['groupId'] + ':' + item['artifactId']]
        base = Path(artifacts).resolve() / item['groupId'].replace('.', '/') / item['artifactId'] / item['version'] / (item['artifactId'] + '-' + item['version'])
        url = (Path(local_repository).resolve() / home['repository']).as_uri() if local_repository else f'https://maven.pkg.github.com/{OWNER}/{home["repository"]}'
        execution = ET.SubElement(executions, tag('execution'))
        child(execution, 'id', item['artifactId'])
        config = ET.SubElement(execution, tag('configuration'))
        values = {'file': str(base) + '.' + item['packaging'], 'pomFile': str(base) + '.pom',
                  'groupId': item['groupId'], 'artifactId': item['artifactId'], 'version': item['version'],
                  'packaging': item['packaging'], 'generatePom':'false', 'url':url,
                  'repositoryId':'github-packages', 'retryFailedDeploymentCount':'1', 'skip':'false'}
        if item['packaging'] == 'jar':
            values.update({'sources':str(base) + '-sources.jar', 'javadoc':str(base) + '-javadoc.jar'})
        for name, value in values.items(): child(config, name, value)
        goals.append(PLUGIN + '@' + item['artifactId'])
    ET.indent(project)
    return ET.tostring(project, encoding='unicode') + '\n', goals


class SafeRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        previous, target = urllib.parse.urlsplit(req.full_url), urllib.parse.urlsplit(newurl)
        if previous.scheme == 'https' and target.scheme != 'https':
            raise ValueError('refusing an insecure registry redirect')
        redirected = super().redirect_request(req, fp, code, msg, headers, newurl)
        if redirected is not None and (previous.scheme, previous.netloc) != (target.scheme, target.netloc):
            redirected.remove_header('Authorization')
        return redirected


class Reader:
    def __init__(self, username=None, token=None):
        self.opener = urllib.request.build_opener(SafeRedirect())
        self.auth = 'Basic ' + base64.b64encode(f'{username or ""}:{token}'.encode()).decode() if token else None

    def read(self, url, metadata=False):
        request = urllib.request.Request(url)
        if self.auth: request.add_header('Authorization', self.auth)
        try:
            with self.opener.open(request, timeout=60) as response:
                if metadata:
                    data = response.read(1024 * 1024 + 1)
                    if len(data) > 1024 * 1024: raise ValueError('snapshot metadata exceeds size limit')
                    return data
                digest = hashlib.sha256()
                for chunk in iter(lambda: response.read(1024 * 1024), b''): digest.update(chunk)
                return digest.hexdigest()
        except urllib.error.HTTPError as error:
            status = error.code
            error.close()
            raise ValueError(f'HTTP {status} reading registry artifact') from None
        except (urllib.error.URLError, TimeoutError):
            raise ValueError('registry read failed') from None


def repository_url(home, base=None):
    if base is None: return f'https://maven.pkg.github.com/{OWNER}/{home["repository"]}'
    parsed = urllib.parse.urlsplit(base)
    if parsed.scheme != 'http' or parsed.hostname not in ('127.0.0.1', 'localhost') or parsed.username or parsed.password or parsed.query or parsed.fragment:
        raise ValueError('test registry override must be localhost HTTP')
    return base.rstrip('/') + '/' + home['repository']


def snapshot_values(project, data, needed):
    if b'<!DOCTYPE' in data or b'<!ENTITY' in data:
        raise ValueError('unsafe snapshot metadata')
    metadata = ET.fromstring(data)
    for node in metadata.iter(): node.tag = node.tag.split('}')[-1]
    if metadata.tag != 'metadata' or any(metadata.findtext(name) != project[name]
                                      for name in ('groupId','artifactId','version')):
        raise ValueError('snapshot metadata coordinate mismatch')
    timestamp = metadata.findtext('versioning/snapshot/timestamp', '')
    build = metadata.findtext('versioning/snapshot/buildNumber', '')
    if not re.fullmatch(r'\d{8}\.\d{6}', timestamp) or not re.fullmatch(r'[1-9]\d*', build):
        raise ValueError('invalid unique snapshot timestamp or build number')
    datetime.strptime(timestamp, '%Y%m%d.%H%M%S')
    unique = project['version'].removesuffix('-SNAPSHOT') + '-' + timestamp + '-' + build
    values = {}
    for node in metadata.findall('versioning/snapshotVersions/snapshotVersion'):
        key = (node.findtext('classifier') or '', node.findtext('extension'))
        if key in values: raise ValueError('duplicate snapshot artifact variant')
        values[key] = node.findtext('value')
    if any(values.get(key) != unique for key in needed):
        raise ValueError('missing or inconsistent timestamped snapshot variant')
    return unique


def verify_mapped(projects, homes, inventory, base=None, username=None, token=None):
    home_entries = validate_homes(projects, homes)
    validate_inventory(projects, inventory)
    by_ga = {h['groupId'] + ':' + h['artifactId']: h for h in home_entries}
    expected = {row['path']: row['sha256'] for row in inventory}
    reader, proof, metadata_reads = Reader(username, token), [], []
    for project in publishable(projects):
        home = by_ga[project['groupId'] + ':' + project['artifactId']]
        url = repository_url(home, base)
        prefix = project['groupId'].replace('.', '/') + '/' + project['artifactId'] + '/' + project['version'] + '/'
        files = sorted(path for path in expected if path.startswith(prefix))
        unique = project['version']
        if unique.endswith('-SNAPSHOT'):
            meta_url = url + '/' + prefix + 'maven-metadata.xml'
            data = reader.read(meta_url, metadata=True)
            variants = {('', 'pom')}
            if project['packaging'] == 'jar': variants.update({('', 'jar'), ('sources', 'jar'), ('javadoc', 'jar')})
            unique = snapshot_values(project, data, variants)
            metadata_reads.append((meta_url, data))
        name_prefix = project['artifactId'] + '-' + project['version']
        for path in files:
            filename = path.removeprefix(prefix)
            remote = prefix + project['artifactId'] + '-' + unique + filename.removeprefix(name_prefix)
            digest = reader.read(url + '/' + urllib.parse.quote(remote, safe='/'))
            if digest != expected[path]: raise ValueError('publication digest mismatch: ' + path)
            proof.append({'path':path, 'remotePath':remote, 'repository':home['repository'], 'sha256':digest})
    for url, original in metadata_reads:
        if reader.read(url, metadata=True) != original:
            raise ValueError('snapshot metadata changed during verification')
    return proof


def verify_with_retry(projects, homes, inventory, username=None, token=None,
                      attempts=1, delay=0):
    if attempts < 1 or delay < 0:
        raise ValueError('attempts must be positive and delay nonnegative')
    validate_homes(projects, homes)
    validate_inventory(projects, inventory)
    for attempt in range(attempts):
        try:
            return verify_mapped(projects, homes, inventory, username=username, token=token)
        except (ValueError, ET.ParseError):
            if attempt + 1 == attempts:
                raise
            time.sleep(delay)


class NoApiRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def preflight_associations(projects, homes, token):
    entries = validate_homes(projects, homes)
    if homes.get('publicationMode') != 'jafly-bootstrap':
        raise ValueError('explicit Jafly bootstrap publication policy required')
    opener = urllib.request.build_opener(NoApiRedirect())
    proof = []
    for entry in entries:
        name = entry['groupId'] + '.' + entry['artifactId']
        url = 'https://api.github.com/orgs/' + OWNER + '/packages/maven/' + urllib.parse.quote(name, safe='')
        request = urllib.request.Request(url, headers={
            'Authorization': 'Bearer ' + token,
            'Accept': 'application/vnd.github+json',
            'X-GitHub-Api-Version': '2022-11-28',
        })
        try:
            with opener.open(request, timeout=60) as response:
                data = response.read(1024 * 1024 + 1)
        except urllib.error.HTTPError as error:
            status = error.code
            error.close()
            if status == 404:
                proof.append({'name': name, 'status': 'absent'})
                continue
            raise ValueError(f'HTTP {status} checking package association: {name}') from None
        except (urllib.error.URLError, TimeoutError, OSError):
            raise ValueError('package association read failed: ' + name) from None
        try:
            if len(data) > 1024 * 1024:
                raise ValueError('oversized response')
            package = json.loads(data)
            repository = package.get('repository') or {}
            if (package.get('name') != name or package.get('package_type') != 'maven'
                    or package.get('visibility') != entry['visibility']
                    or package.get('owner', {}).get('login') != OWNER
                    or repository.get('full_name') != OWNER + '/' + JAFLY_REPOSITORY
                    or repository.get('id') != JAFLY_REPOSITORY_ID):
                raise ValueError('unexpected package identity or repository')
        except (ValueError, TypeError, AttributeError, UnicodeError):
            raise ValueError('conflicting or invalid package association: ' + name) from None
        proof.append({'name': name, 'status': 'jafly-associated'})
    return proof


def run_batch(maven, plan, local=False):
    plan = Path(plan).resolve()
    projects = json.loads((plan / 'reactor-inventory.json').read_text())
    homes = json.loads((plan / 'package-homes.json').read_text())
    inventory = json.loads((plan / 'github-manifest.json').read_text())
    associations = json.loads((plan / 'source-associations.json').read_text())
    validate_associations(projects, homes, associations)
    validate_canonical_homes(projects, homes)
    validate_inventory(projects, inventory)
    for row in inventory:
        if digest_file(plan / 'artifacts' / row['path']) != row['sha256']:
            raise ValueError('staged artifact changed')
    if local:
        token = None
        settings = '<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0"/>\n'
        rendered, goals = render_publisher(projects, homes, plan / 'artifacts', plan / 'registry')
    else:
        token = os.environ.get('JAFLY_PACKAGES_TOKEN')
        username = os.environ.get('JAFLY_PACKAGES_USER') or os.environ.get('GITHUB_ACTOR')
        if not token or not username: raise ValueError('JAFLY_PACKAGES_TOKEN and JAFLY_PACKAGES_USER/GITHUB_ACTOR are required')
        association_proof = preflight_associations(projects, homes, token)
        settings = '<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0"><servers><server><id>github-packages</id><username>${env.JAFLY_PACKAGES_USER}</username><password>${env.JAFLY_PACKAGES_TOKEN}</password></server></servers></settings>\n'
        rendered, goals = render_publisher(projects, homes, plan / 'artifacts')
    pom = plan / ('local-publisher.pom.xml' if local else 'publisher.pom.xml')
    settings_path = plan / ('local-settings.xml' if local else 'settings.xml')
    pom.write_text(rendered)
    settings_path.write_text(settings)
    settings_path.chmod(0o600)
    if not local:
        (plan / 'association-preflight.json').write_text(json.dumps(association_proof, indent=2) + '\n')
    environment = dict(os.environ)
    environment.pop('MAVEN_ARGS', None)
    if not local: environment['JAFLY_PACKAGES_USER'] = username
    command = [str(Path(maven).resolve()), '-B', '-ntp', '-s', str(settings_path), '-f', str(pom), *goals]
    result = subprocess.run(command, cwd=plan, env=environment, capture_output=True, text=True)
    output = result.stdout + result.stderr
    if token:
        encoded = base64.b64encode(f'{username}:{token}'.encode()).decode()
        for sensitive in (token, urllib.parse.quote(token, safe=''), encoded):
            output = output.replace(sensitive, '[REDACTED]')
    (plan / ('local-maven.log' if local else 'publish-maven.log')).write_text(output)
    if result.returncode: raise ValueError('Maven deployment failed; inspect the redacted log')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest='command', required=True)
    plan = commands.add_parser('plan')
    plan.add_argument('--root', required=True, type=Path)
    plan.add_argument('--associations', type=Path, help='Captured package metadata; defaults to scripts/package-associations.jsonl')
    plan.add_argument('--repository', required=True, type=Path)
    plan.add_argument('--output', required=True, type=Path)
    execute = commands.add_parser('execute')
    execute.add_argument('--plan', required=True, type=Path)
    execute.add_argument('--maven', required=True, type=Path)
    verify = commands.add_parser('verify')
    verify.add_argument('--projects', required=True, type=Path)
    verify.add_argument('--homes', required=True, type=Path)
    verify.add_argument('--manifest', required=True, type=Path)
    verify.add_argument('--output', required=True, type=Path)
    verify.add_argument('--token-env', default='JAFLY_PACKAGES_TOKEN')
    verify.add_argument('--attempts', type=int, default=1)
    verify.add_argument('--delay', type=int, default=0)
    args = parser.parse_args()
    if args.command == 'plan':
        tools = release_tools(args.root)
        projects = tools.check_reactor(args.root, expected=86)
        association_path = args.associations or args.root / 'scripts/package-associations.jsonl'
        associations = [json.loads(line) for line in association_path.read_text().splitlines() if line.strip()]
        homes = build_homes(projects, associations)
        validate_canonical_homes(projects, homes, args.root / 'scripts/package-homes.json')
        inventory = tools.inventory_repository(projects, args.repository)
        if args.output.exists(): raise ValueError('refusing to overwrite an existing plan')
        prepare_stage(projects, homes, inventory, args.repository, args.output / 'artifacts')
        for filename, value in [('reactor-inventory.json', projects),('package-homes.json',homes),('github-manifest.json',inventory),('source-associations.json',associations)]:
            (args.output / filename).write_text(json.dumps(value, indent=2) + '\n')
        rendered, goals = render_publisher(projects, homes, args.output / 'artifacts')
        (args.output / 'publisher.pom.xml').write_text(rendered)
        (args.output / 'goals.json').write_text(json.dumps(goals, indent=2) + '\n')
        print(f'Prepared {len(homes["artifacts"])} package homes and {len(inventory)} staged files; no upload performed')
    elif args.command == 'execute':
        run_batch(args.maven, args.plan)
        print('Maven upload finished; independent verification is still required')
    else:
        if not re.fullmatch(r'[A-Z][A-Z0-9_]*', args.token_env): raise ValueError('invalid credential environment variable name')
        token = os.environ.get(args.token_env)
        username = os.environ.get('JAFLY_PACKAGES_USER') or os.environ.get('GITHUB_ACTOR')
        if not token or not username: raise ValueError('read credentials are required')
        projects = json.loads(args.projects.read_text())
        homes = json.loads(args.homes.read_text())
        validate_canonical_homes(projects, homes)
        proof = verify_with_retry(projects, homes, json.loads(args.manifest.read_text()),
                                  username=username, token=token, attempts=args.attempts, delay=args.delay)
        args.output.write_text(json.dumps(proof, indent=2) + '\n')
        print(f'Independently verified all {len(proof)} artifact digests')


if __name__ == '__main__':
    try: main()
    except (ValueError, OSError, ET.ParseError) as error:
        print('ERROR: ' + str(error), file=sys.stderr)
        sys.exit(1)

#!/usr/bin/env python3
"""Fail-closed reactor, immutable release tag, and registry verification tools."""
import argparse
import base64
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET
import zipfile

NS = {'m': 'http://maven.apache.org/POM/4.0.0'}
FINAL_VERSION = re.compile(r'\d{2}\.(?:0[1-9]|1[0-2])\.\d{2}')


def check_reactor(root, tag=None, expected=None, snapshot=False):
    root = Path(root).resolve()
    projects = []
    visited = set()
    coordinates = set()
    documents = []

    def read(directory):
        directory = directory.resolve()
        if not directory.is_relative_to(root):
            raise ValueError(f'module outside reactor: {directory}')
        if directory in visited:
            raise ValueError(f'duplicate module: {directory}')
        visited.add(directory)
        document = ET.parse(directory / 'pom.xml').getroot()
        value = lambda name: document.findtext('m:' + name, namespaces=NS)
        parent = document.find('m:parent', NS)
        group = value('groupId') or (parent.findtext('m:groupId', namespaces=NS) if parent is not None else None)
        project = {'groupId': group, 'artifactId': value('artifactId'), 'version': value('version') or (parent.findtext('m:version', namespaces=NS) if parent is not None else None),
                   'packaging': value('packaging') or 'jar', 'directory': str(directory.relative_to(root))}
        if not all(project[key] for key in ('groupId', 'artifactId', 'version')):
            raise ValueError(f'resolvable project version and coordinates required: {directory}')
        if '${' in project['version']:
            raise ValueError(f'concrete project version required: {directory}')
        coordinate = (group, project['artifactId'])
        if coordinate in coordinates:
            raise ValueError(f'duplicate coordinate: {coordinate}')
        coordinates.add(coordinate)
        project['publish'] = (document.findtext('m:properties/m:maven.deploy.skip', namespaces=NS) != 'true'
                              and project['artifactId'] != 'fireflyframework-jafly')
        projects.append(project)
        documents.append(document)
        for module in document.findall('m:modules/m:module', NS):
            read(directory / module.text)

    read(root)
    version = projects[0]['version']
    for project in projects:
        if project['groupId'] != 'org.fireflyframework' or project['version'] != version:
            raise ValueError(f'mixed framework group/version: {project}')
    if expected is not None and len(projects) != expected:
        raise ValueError(f'project count {len(projects)} differs from expected count {expected}')
    if tag is not None and (not FINAL_VERSION.fullmatch(version) or tag != 'v' + version):
        raise ValueError(f'release tag {tag!r} must equal final POM version v{version}')
    if snapshot and not version.endswith('-SNAPSHOT'):
        raise ValueError('snapshot publication requires a SNAPSHOT POM version')
    by_artifact = {p['artifactId']: (p, d) for p, d in zip(projects, documents)}
    for artifact in ('fireflyframework-parent', 'fireflyframework-bom'):
        if artifact in by_artifact:
            _, document = by_artifact[artifact]
            if document.find('m:parent', NS) is not None or document.findall('m:modules/m:module', NS):
                raise ValueError(f'{artifact} must remain standalone')
    if documents[0].find('m:parent', NS) is not None:
        raise ValueError('root aggregator must remain standalone')

    def properties(document, seen=None):
        seen = set() if seen is None else seen
        parent_artifact = document.findtext('m:parent/m:artifactId', namespaces=NS)
        inherited = {}
        if parent_artifact in by_artifact:
            if parent_artifact in seen:
                raise ValueError('parent cycle')
            inherited.update(properties(by_artifact[parent_artifact][1], seen | {parent_artifact}))
        node = document.find('m:properties', NS)
        if node is not None:
            inherited.update({child.tag.split('}')[-1]: child.text or '' for child in node})
        inherited.update({'project.version': version, 'pom.version': version,
                          'project.parent.version': version, 'parent.version': version})
        return inherited

    for project, document in zip(projects, documents):
        props = properties(document)
        for dependency in document.findall('.//m:dependency', NS) + document.findall('m:parent', NS):
            group = dependency.findtext('m:groupId', namespaces=NS)
            declared = dependency.findtext('m:version', namespaces=NS)
            if group != 'org.fireflyframework' or declared is None:
                continue
            resolved = declared
            for _ in range(10):
                replaced = re.sub(r'\$\{([^}]+)\}', lambda match: props.get(match[1], match[0]), resolved)
                if replaced == resolved:
                    break
                resolved = replaced
            if resolved != version:
                raise ValueError(f'internal dependency version {declared} != {version}: {project["artifactId"]}')
        if project['artifactId'] == 'fireflyframework-parent':
            for dependency in document.findall('.//m:dependency', NS):
                if dependency.findtext('m:artifactId', namespaces=NS) == 'fireflyframework-bom':
                    raise ValueError('parent must not import the framework BOM')
    return projects


def check_tag_commit(root, tag, remote=None):
    if not tag.startswith('v') or not FINAL_VERSION.fullmatch(tag[1:]):
        raise ValueError('invalid release tag')
    def git(*arguments):
        return subprocess.check_output(['git', '-C', str(root), *arguments], text=True).strip()
    commit = git('rev-parse', 'HEAD')
    if commit != git('rev-parse', f'refs/tags/{tag}^{{commit}}'):
        raise ValueError('release tag commit differs from checkout commit')
    if remote is not None:
        reference = f'refs/tags/{tag}'
        advertised = git('ls-remote', '--exit-code', '--tags', '--', remote,
                         reference, reference + '^{}')
        references = {}
        for line in advertised.splitlines():
            sha, name = line.split()
            references[name] = sha
        remote_commit = references.get(reference + '^{}', references.get(reference))
        if remote_commit != commit:
            raise ValueError('remote release tag commit differs from checkout commit')


def required_paths(projects, signatures=False):
    required = set()
    for project in projects:
        if not project.get('publish', project['artifactId'] != 'fireflyframework-jafly'):
            continue
        artifact, version = project['artifactId'], project['version']
        base = f'{project["groupId"].replace(".", "/")}/{artifact}/{version}/{artifact}-{version}'
        files = [base + '.pom']
        if project['packaging'] != 'pom':
            files.append(base + '.' + project['packaging'])
            if project['packaging'] == 'jar':
                files.extend([base + '-sources.jar', base + '-javadoc.jar'])
        required.update(files)
        if signatures:
            required.update(path + '.asc' for path in files)
    return required


def inventory_repository(projects, repository):
    inventory = []
    for path in sorted(required_paths(projects)):
        file = Path(repository) / path
        if not file.is_file():
            raise ValueError(f'missing required artifact: {path}')
        inventory.append({'path': path, 'sha256': hashlib.sha256(file.read_bytes()).hexdigest()})
    return inventory


def optional_test_jar_paths(projects, signatures=False):
    """Classifier test jars some modules attach for inter-module test reuse."""
    optional = set()
    for project in projects:
        if not project.get('publish', project['artifactId'] != 'fireflyframework-jafly'):
            continue
        if project.get('packaging') != 'jar':
            continue
        artifact, version = project['artifactId'], project['version']
        base = f'{project["groupId"].replace(".", "/")}/{artifact}/{version}/{artifact}-{version}-tests.jar'
        optional.add(base)
        if signatures:
            optional.add(base + '.asc')
    return optional


def inventory_bundle(projects, bundle):
    with zipfile.ZipFile(bundle) as archive:
        names = [item.filename for item in archive.infolist() if not item.is_dir()]
        if len(names) != len(set(names)):
            raise ValueError('duplicate bundle path')
        expected = required_paths(projects, signatures=True)
        optional = optional_test_jar_paths(projects, signatures=True)
        allowed_directories = {str(PurePosixPath(path).parent) for path in expected}
        present_optional = set()
        for name in names:
            path = PurePosixPath(name)
            if path.is_absolute() or '..' in path.parts or str(path.parent) not in allowed_directories:
                raise ValueError(f'unexpected bundle path: {name}')
            # Checksums are permitted; optional -tests.jar classifiers are allowed when present.
            artifact_name = re.sub(r'\.(md5|sha1|sha256|sha512)$', '', name)
            if artifact_name in expected:
                continue
            if artifact_name in optional:
                present_optional.add(artifact_name)
                continue
            raise ValueError(f'unexpected bundle artifact path: {name}')
        if expected - set(names):
            raise ValueError(f'missing bundle artifacts: {sorted(expected - set(names))}')
        selected = set(expected) | present_optional
        return [{'path': path, 'sha256': hashlib.sha256(archive.read(path)).hexdigest()}
                for path in sorted(selected)]


def verify_remote(inventory, url, username=None, token=None, attempts=1, delay=0):
    homes = Path(__file__).with_name('package-homes.json')
    if urllib.parse.urlsplit(url).hostname == 'maven.pkg.github.com':
        from verify_github_packages import verify_github_layout
        projects = check_reactor(Path(__file__).resolve().parents[1], expected=87)
        return verify_github_layout(inventory, url, projects, homes, username, token, attempts, delay)
    if attempts < 1 or delay < 0:
        raise ValueError('attempts must be positive and delay nonnegative')
    if not inventory:
        raise ValueError('empty publication inventory')
    for item in inventory:
        path = PurePosixPath(item['path'])
        if path.is_absolute() or '..' in path.parts:
            raise ValueError('invalid manifest path')
        request = urllib.request.Request(url.rstrip('/') + '/' + urllib.parse.quote(str(path), safe='/'))
        if token:
            auth = base64.b64encode(f'{username or ""}:{token}'.encode()).decode()
            request.add_header('Authorization', 'Basic ' + auth)
        for attempt in range(attempts):
            try:
                with urllib.request.urlopen(request, timeout=60) as response:
                    digest = hashlib.sha256(response.read()).hexdigest()
                if digest != item['sha256']:
                    raise ValueError(f'publication digest mismatch: {path}')
                break
            except (urllib.error.URLError, TimeoutError) as error:
                if isinstance(error, urllib.error.HTTPError):
                    error.close()
                if attempt + 1 == attempts:
                    raise ValueError(f'publication HTTP verification failed: {path}: {error}') from error
                time.sleep(delay)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest='command', required=True)
    check = commands.add_parser('check')
    check.add_argument('--root', type=Path, default=Path('.'))
    check.add_argument('--tag')
    check.add_argument('--remote', help='Require the remote tag to resolve to the checkout commit')
    check.add_argument('--snapshot', action='store_true')
    check.add_argument('--expected', type=int, default=87)
    check.add_argument('--output', type=Path)
    inventory = commands.add_parser('inventory')
    inventory.add_argument('--root', type=Path, default=Path('.'))
    source = inventory.add_mutually_exclusive_group(required=True)
    source.add_argument('--repository', type=Path)
    source.add_argument('--bundle', type=Path)
    inventory.add_argument('--expected', type=int, default=87)
    inventory.add_argument('--output', type=Path, required=True)
    verify = commands.add_parser('verify')
    verify.add_argument('--manifest', type=Path, required=True)
    verify.add_argument('--url', required=True)
    verify.add_argument('--username-env')
    verify.add_argument('--token-env')
    verify.add_argument('--attempts', type=int, default=1)
    verify.add_argument('--delay', type=int, default=0)
    args = parser.parse_args()
    if args.command == 'check':
        result = check_reactor(args.root, args.tag, args.expected, args.snapshot)
        if args.remote is not None and args.tag is None:
            raise ValueError('--remote requires --tag')
        if args.tag:
            check_tag_commit(args.root, args.tag, args.remote)
        if args.output:
            args.output.parent.mkdir(parents=True, exist_ok=True)
            args.output.write_text(json.dumps(result, indent=2) + '\n')
        print(f'Validated {len(result)} projects at {result[0]["version"]}')
    elif args.command == 'inventory':
        projects = check_reactor(args.root, expected=args.expected)
        files = inventory_bundle(projects, args.bundle) if args.bundle else inventory_repository(projects, args.repository)
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(files, indent=2) + '\n')
        print(f'Inventoried {len(files)} release files')
    else:
        if args.attempts < 1 or args.delay < 0:
            raise ValueError('attempts must be positive and delay nonnegative')
        token = os.environ.get(args.token_env) if args.token_env else None
        if args.token_env and not token:
            raise ValueError(f'required credential {args.token_env} is unavailable')
        username = os.environ.get(args.username_env) if args.username_env else None
        files = json.loads(args.manifest.read_text())
        verify_remote(files, args.url, username, token, args.attempts, args.delay)
        print(f'Verified {len(files)} published files')


if __name__ == '__main__':
    try:
        main()
    except (ValueError, OSError, ET.ParseError, subprocess.CalledProcessError, zipfile.BadZipFile) as error:
        print(f'ERROR: {error}', file=sys.stderr)
        sys.exit(1)

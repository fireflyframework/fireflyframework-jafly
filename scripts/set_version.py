#!/usr/bin/env python3
"""Update the concrete CalVer release train without changing third-party versions."""
import argparse
import pathlib
import re
import xml.etree.ElementTree as ET

NS = {'m': 'http://maven.apache.org/POM/4.0.0'}
PATTERN = re.compile(r'^(\d{2})\.(0[1-9]|1[0-2])\.(\d{2})(?:-SNAPSHOT)?$')


def set_version(root: pathlib.Path, version: str) -> list[pathlib.Path]:
    match = PATTERN.fullmatch(version)
    if match is None:
        raise ValueError('Version must be YY.MM.PP or YY.MM.PP-SNAPSHOT with a valid month')
    changes = {}
    for path in sorted(root.rglob('pom.xml')):
        if {'target', '.git'} & set(path.relative_to(root).parts):
            continue
        content = path.read_text()
        ET.fromstring(content)
        # XML blocks are replaced in place so namespace prefixes and formatting survive.
        def block(match):
            value = match[0]
            node = ET.fromstring('<root>' + value + '</root>')[0]
            if node.findtext('groupId') == 'org.fireflyframework' and not (node.findtext('version') or '').startswith('${'):
                value = re.sub(r'<version>[^<]+</version>', '<version>' + version + '</version>', value, count=1)
            return value
        content = re.sub(r'<(?:parent|dependency)>.*?</(?:parent|dependency)>', block, content, flags=re.S)
        # Only the project's own direct version, never versions in plugins or foreign parents.
        parsed = ET.fromstring(content)
        own = parsed.findtext('m:version', namespaces=NS)
        group = parsed.findtext('m:groupId', namespaces=NS) or parsed.findtext('m:parent/m:groupId', namespaces=NS)
        if own and group and (group == 'org.fireflyframework' or group.startswith('org.fireflyframework.')):
            # Temporarily mask child blocks to locate the direct project version.
            masked = re.sub(r'<(parent|dependencies|dependencyManagement|build|profiles|properties)>.*?</\1>', lambda m: ' ' * len(m[0]), content, flags=re.S)
            location = re.search(r'<version>[^<]+</version>', masked)
            if location:
                content = content[:location.start()] + '<version>' + version + '</version>' + content[location.end():]
        content = re.sub(r'<(fireflyframework(?:[\w.-]*)\.version)>[^<]+</\1>', lambda m: '<' + m[1] + '>' + version + '</' + m[1] + '>', content)
        ET.fromstring(content)
        if content != path.read_text():
            changes[path] = content
    # Validate all input files first; malformed files cannot leave a partial version bump.
    for path, content in changes.items():
        path.write_text(content)
    return list(changes)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('version')
    args = parser.parse_args()
    files = set_version(pathlib.Path(__file__).resolve().parents[1], args.version)
    print(f'Updated {len(files)} POM files to {args.version}')

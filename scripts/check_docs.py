#!/usr/bin/env python3
"""Verify all source artifacts have guides, covers, READMEs, and authentic exports."""
import hashlib
import json
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

NS = {'m': 'http://maven.apache.org/POM/4.0.0'}


def validate_catalog(root, catalog):
    root = Path(root).resolve()
    docs = root / 'docs/site'
    source_paths = {str(path.parent.relative_to(root))
                    for folder in ('modules', 'build')
                    for path in (root / folder).rglob('pom.xml')
                    if not {'target', '.git'} & set(path.parts)}
    catalog_paths = [item['path'] for item in catalog]
    slugs = [item['slug'] for item in catalog]
    if len(set(catalog_paths)) != len(catalog_paths) or len(set(slugs)) != len(slugs):
        raise ValueError('duplicate module catalog path or slug')
    if source_paths != set(catalog_paths):
        raise ValueError(f'module catalog differs from source POMs: {sorted(source_paths ^ set(catalog_paths))}')
    for item in catalog:
        source = (root / item['path']).resolve()
        if not source.is_relative_to(root):
            raise ValueError('module catalog path outside repository')
        actual = ET.parse(source / 'pom.xml').getroot().findtext('m:artifactId', namespaces=NS)
        if actual != item['artifact']:
            raise ValueError(f'artifact mismatch: {item["path"]}: {actual} != {item["artifact"]}')
        for path, kind in ((docs / 'modules' / (item['slug'] + '.md'), 'guide'),
                           (docs / 'assets/modules' / (item['slug'] + '.svg'), 'cover'),
                           (source / 'README.md', 'README')):
            if not path.is_file() or not path.stat().st_size:
                raise ValueError(f'missing module {kind}: {path.relative_to(root)}')


def validate_artwork(docs):
    docs = Path(docs).resolve()
    origins = sorted((docs / 'assets').rglob('ORIGIN.json'))
    if not origins:
        raise ValueError('brand provenance is missing')
    count = 0
    for origin in origins:
        manifest = json.loads(origin.read_text())
        for name, expected in manifest['exports'].items():
            path = (origin.parent / name).resolve()
            if not path.is_relative_to(docs / 'assets') or not path.is_file():
                raise ValueError(f'brand export path invalid: {name}')
            digest = hashlib.sha256(path.read_bytes()).hexdigest()
            if digest != expected:
                raise ValueError(f'brand export hash mismatch: {path.relative_to(docs)}')
            if path.suffix == '.svg':
                try:
                    ET.parse(path)
                except ET.ParseError as error:
                    raise ValueError(f'invalid SVG export: {path.relative_to(docs)}: {error}') from error
            count += 1
    return count


def main():
    root = Path(__file__).resolve().parents[1]
    catalog = json.loads((root / 'docs/site/assets/module-catalog.json').read_text())
    validate_catalog(root, catalog)
    count = validate_artwork(root / 'docs/site')
    print(f'Validated {len(catalog)} artifact guides, covers, and READMEs; {count} branded exports match provenance')


if __name__ == '__main__':
    try:
        main()
    except (ValueError, OSError, ET.ParseError) as error:
        print(f'ERROR: {error}', file=sys.stderr)
        sys.exit(1)

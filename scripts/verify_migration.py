#!/usr/bin/env python3
"""Prove exact source imports and continued preservation of original files and refs."""
import json
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]


def git(*args):
    return subprocess.check_output(['git', '-C', str(ROOT), *args]).decode().strip()


def verify():
    sources = json.loads((ROOT / 'docs/migration/source-manifest.json').read_text())['sources']
    if len(sources) != 63 or len({s['repository'] for s in sources}) != 63:
        raise ValueError('Expected 63 unique source repositories')
    refs = dict(line.split(' ', 1)[::-1] for line in git('for-each-ref', '--format=%(objectname) %(refname)', 'refs/heads/legacy', 'refs/tags/legacy', 'refs/remotes/origin/legacy').splitlines())
    excluded = set(json.loads((ROOT / 'docs/migration/generated-output-exclusions.json').read_text())['paths'])
    if any('target' not in Path(p).parts for p in excluded):
        raise ValueError('Only original Maven build outputs can be excluded')
    file_count = 0
    for source in sources:
        imported = source['import_commit'] + ':' + source['destination']
        if git('rev-parse', imported) != source['source_tree']:
            raise ValueError('Original import tree differs: ' + source['repository'])
        subprocess.run(['git', '-C', str(ROOT), 'merge-base', '--is-ancestor', source['head'], source['import_commit']], check=True)
        subprocess.run(['git', '-C', str(ROOT), 'merge-base', '--is-ancestor', source['import_commit'], 'HEAD'], check=True)
        for reference in source['refs']:
            prefix = 'refs/heads/' if reference['ref'].startswith('refs/heads/') else 'refs/tags/'
            historical = prefix + 'legacy/' + source['repository'] + '/' + reference['ref'].removeprefix(prefix)
            alternate = historical.replace('refs/heads/', 'refs/remotes/origin/', 1)
            if refs.get(historical, refs.get(alternate)) != reference['object']:
                raise ValueError('Historical reference is missing or changed: ' + historical)
        tree = subprocess.check_output(['git', '-C', str(ROOT), 'ls-tree', '-rz', imported])
        for entry in tree.split(b'\0'):
            if not entry:
                continue
            metadata, name = entry.split(b'\t', 1)
            path = ROOT / source['destination'] / name.decode()
            if str(path.relative_to(ROOT)) in excluded:
                continue
            if not path.exists() and not path.is_symlink():
                raise ValueError('Original source path is missing: ' + str(path.relative_to(ROOT)))
            mode = metadata.split()[0]
            if mode == b'120000' and not path.is_symlink():
                raise ValueError('Original symbolic link changed: ' + str(path))
            if mode == b'100755' and not path.stat().st_mode & 0o111:
                raise ValueError('Original executable lost its permission: ' + str(path))
            file_count += 1
    print(f'Verified {len(sources)} exact source import trees, {sum(len(s["refs"]) for s in sources)} original refs, and {file_count} preserved files')


if __name__ == '__main__':
    verify()

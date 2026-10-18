#!/usr/bin/env python3
"""Keep the required CI check present while avoiding Java builds for prose edits."""
import argparse
import os
from pathlib import Path
import subprocess


def requires_java(paths):
    return any(not (path.lower().endswith('.md') or path.startswith('docs/site/'))
               for path in paths)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--event', required=True)
    parser.add_argument('--base', default='')
    args = parser.parse_args()
    verify = True
    if args.event != 'workflow_dispatch' and args.base and set(args.base) != {'0'}:
        diff = subprocess.run(['git', 'diff', '--no-renames', '--name-only', '-z', args.base, 'HEAD'],
                              capture_output=True, check=False)
        if diff.returncode == 0:
            paths = [path for path in diff.stdout.decode().split('\0') if path]
            verify = requires_java(paths)
    value = str(verify).lower()
    if output := os.environ.get('GITHUB_OUTPUT'):
        with Path(output).open('a') as stream:
            stream.write(f'java={value}\n')
    print(f'Java compilation and runtime tests required: {value}')


if __name__ == '__main__':
    main()

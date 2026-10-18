"""Adapter for the existing exact-tag release_tools.py verify CLI."""
import json
from pathlib import Path
import urllib.parse
import github_packages as mapped


def verify_github_layout(inventory, url, projects, map_path, username=None, token=None,
                         attempts=1, delay=0):
    parsed = urllib.parse.urlsplit(url)
    parts = parsed.path.strip('/').split('/')
    if (parsed.scheme != 'https' or parsed.hostname != 'maven.pkg.github.com'
            or parsed.netloc != 'maven.pkg.github.com' or parsed.query or parsed.fragment
            or len(parts) != 2 or parts[0] != mapped.OWNER or not mapped.REPO.fullmatch(parts[1])):
        raise ValueError('expected approved GitHub Maven owner/repository URL')
    if attempts < 1 or delay < 0:
        raise ValueError('attempts must be positive and delay nonnegative')
    homes = json.loads(Path(map_path).read_text())
    mapped.validate_homes(projects, homes)
    mapped.validate_inventory(projects, inventory)
    return mapped.verify_with_retry(projects, homes, inventory, username=username,
                                    token=token, attempts=attempts, delay=delay)

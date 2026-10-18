# Contributing to Jafly

## Development

1. Use JDK 25 and the checked-in Maven wrapper (`./mvnw`).
2. Docker is required for the full reactor and example gates.
3. Prefer focused builds: `./mvnw -pl :artifact-id -am …`.

Local checks before a pull request:

```sh
python3 -m unittest discover -s tests -v
python3 -m unittest discover -s tests/release -v
python3 scripts/release_tools.py check --snapshot
./mvnw -B -ntp clean verify
```

## Pull requests

- Target `main` (or `develop` when that branch is active).
- Keep unrelated formatting and generated churn out of review diffs.
- CI runs release-tool tests on every PR; Java changes also run the full reactor.

## Releases (maintainers)

Final CalVer trains are published only from an immutable `vYY.MM.PP` tag through [`.github/workflows/release.yml`](.github/workflows/release.yml):

1. Qualify on `main`, then set every framework POM to the final version (`python3 scripts/set_version.py YY.MM.PP`).
2. Merge via PR; create and push tag `vYY.MM.PP` matching that commit (never retag after a partial publish).
3. Inspect the sole `release` job: GitHub Packages verify → Central Portal `waitUntil=published` → GitHub Release.
4. Confirm Maven Central CDN (`repo1.maven.org`) and the GitHub Release assets before announcing.

Snapshot publishes use the manual [`.github/workflows/snapshots.yml`](.github/workflows/snapshots.yml) workflow (GitHub Packages only).

Full runbook, credential names, and recovery rules: [docs/site/releases.md](docs/site/releases.md). Version train semantics: [docs/site/versioning.md](docs/site/versioning.md).

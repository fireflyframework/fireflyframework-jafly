# Unified Jafly releases

![Unified Jafly releases](assets/banners/releases.svg)

The parent, BOM, and libraries share one version train. The working version, `26.10.17-SNAPSHOT`, identifies development builds. See GitHub Releases and the registries for available final versions.

## Workflows

- `ci.yml` runs the full reactor on JDK 25 with the checked-in Maven 3.9 wrapper, plus release-tool tests and structural checks. Docker must be available. The lifecycle is `clean verify`; configured unit and integration test failures stop the job.
- `snapshots.yml` is manual and accepts only main/develop with a snapshot version. It verifies before deploying to GitHub Packages. Its green result proves Maven deployment completed; independent timestamped-snapshot download checks are not currently implemented.
- `release.yml` runs on a final `v*` tag or manual dispatch with the required `tag` input. The sole publishing job is named `release`. It validates all 86 projects, checks tag/version and checked-out commit equality, verifies the full reactor, deploys and verifies GitHub Packages, then deploys and verifies Maven Central. Only then does it create the GitHub Release.

The root aggregator has no parent and is not published. The standalone artifacts in `build/parent` and `build/bom` remain published. Nested modules may inherit their effective version from a local framework parent. Explicitly nonpublishable samples remain in the test reactor and are excluded from both registries. With the current source inventory, 84 projects are publishable: 312 required main/POM/source/Javadoc files and 624 files when detached Central signatures are included.

The root and nonpublishable samples must have both normal Maven deploy suppression and Central `skipPublishing` configured. The Central extension replaces the normal deploy binding; `maven.deploy.skip` alone cannot suppress its publication.

## Final-version procedure

1. Qualify the source changes and set every explicit project/parent reference and internal version property to the same final version, for example `26.10.17`. Inherited versions resolve to that train.
2. Run `python3 scripts/release_tools.py check` and `./mvnw -B -ntp clean verify`.
3. Create an immutable tag whose name is exactly `v` plus the final POM version. The release workflow refuses a snapshot, a mismatched tag, or a checkout that differs from the tag commit.
4. Inspect the complete `release` job result. A final release requires both registries and the GitHub Release to succeed. GitHub Packages can succeed before a later Central failure; this is a partial release, even though some artifacts are already downloadable.

The workflow uses direct Maven deployments for compatibility with the existing parent/BOM release profiles. Each deployment executes the Maven lifecycle with tests enabled. They use the same checked-out commit and isolated Maven repository; they are separate lifecycle executions, so this workflow does not promise byte-identical regenerated archives or signatures across registries. Each registry is independently checked against the bytes produced for its own deployment.

The Central plugin version is 0.10.0. It stages the publishable artifacts in a shared session directory and uploads one aggregate ZIP when the reactor finishes. The workflow retains that ZIP. `autoPublish=true`, `waitUntil=published`, and `waitMaxTime=3600` require completed publication. Timeouts, duplicate-coordinate errors, missing files, and digest mismatches fail the job.

## Credentials and package access

The workflow uses `GITHUB_TOKEN`, `SONATYPE_USERNAME`, `SONATYPE_PASSWORD`, `GPG_PRIVATE_KEY`, and `GPG_PASSPHRASE`. The GPG private key must be base64 encoded, matching the legacy configuration. The runtime preflight reports missing credential names without exposing values. Credentials are read only from GitHub secrets and the environment; the checked-in Maven settings contain environment references only.

The monorepo must have package write access to the retained Maven artifact IDs. Packages associated with old repositories may require access/linkage changes. An authorization or duplicate-coordinate failure is not evidence of publication success. Namespace authorization and key acceptance are verified by the live publishing run; local tests do not establish them.

## Failure and retry

Do not delete old releases, replace tags, or overwrite immutable artifacts to make a retry pass. Download the retained release evidence and determine which registry completed. Manual dispatch accepts an existing tag, but this minimal direct-deploy workflow does not implement automatic partial-publication recovery: rerunning after a successful immutable deployment may fail at that registry with a conflict. Keep the failure visible and resolve the incomplete target with the retained tag, artifact inventory, and Central deployment details. Never claim that a retry succeeded merely because one artifact already exists.

A Maven Central timeout may leave a deployment running in the Portal. Check its deployment ID from the workflow log before any new upload. Preserve the Central ZIP from the workflow artifact. This workflow does not automatically poll an old deployment on rerun.

## Evidence

Successful final releases attach:

- `release-manifest.json`: the Central inventory, as an array of `{"path": "org/fireflyframework/...", "sha256": "..."}` records, including signatures.
- `github-manifest.json`: the independently verified GitHub Packages inventory in the same format.
- `reactor-inventory.json`: group ID, artifact ID, version, packaging, source directory, and publication flag for each project.
- `release-commit.txt`: the checked-out immutable source commit.

Every run retains available manifests, the Central aggregate ZIP, and Surefire/Failsafe reports even on failure. Artifact retention is 90 days; the successful GitHub Release keeps the attached manifests. Test report retention and upload do not suppress failed commands.

## Local validation

```sh
python3 -m unittest discover -s tests/release -v
python3 -m py_compile scripts/release_tools.py
python3 scripts/release_tools.py check --snapshot
actionlint .github/workflows/*.yml
./mvnw -B -ntp clean verify
```

Unit tests cover valid and invalid version inheritance, tag/commit mismatches, mixed versions, parent/BOM cycles, duplicate GAVs, missing projects, path traversal, required attachments, signed bundle completeness, and remote HTTP/digest failures using an isolated local HTTP server. These tests do not publish artifacts or inspect credentials.

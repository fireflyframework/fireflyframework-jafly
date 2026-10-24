# Unified Jafly releases

The monorepo builds one version train. The working source version is `26.10.22`; publication is a separate release-workflow operation.

## Workflows

- `publishing-preflight.yml` is a manual configuration check. It reports credential names as configured or missing, retains that report, and fails when required names are absent. Presence does not prove registry authorization or signing-key acceptance.
- `ci.yml` runs release-tool tests and structural checks for every pull request and branch update. Java, POM, resource, build-tool, and workflow changes also run the full reactor, including the optional Avro and Protobuf serializer tests, on JDK 25 with the checked-in Maven 3.9 wrapper, then verify the standalone examples. Prose and site-asset changes keep the required check present without repeating Java tests. A manual dispatch always runs Java verification. Docker must be available for the full build; configured test failures stop the job.
- `dependency-graph.yml` resolves every reactor project on Java 25 and submits the complete Maven dependency snapshot from `main`. Dependabot can then check transitive dependencies as well as versions declared in POMs.
- `codeql-advanced.yml` compiles Java sources, tests, and standalone examples on Java 25, and analyzes Java, Python, JavaScript/TypeScript, and Actions with the extended security queries. It retains SARIF for review. Uploads are controlled by `JAFLY_CODEQL_UPLOAD`; enable them only after the advanced workflow passes and default setup has been switched off.
- `snapshots.yml` is manual and accepts only main/develop with a snapshot version. It runs the full framework and standalone-example gates, freezes the release-profile artifacts, and publishes them to the Jafly Maven repository. Independent reads verify every timestamped artifact and reread snapshot metadata to detect a concurrent replacement.
- `release.yml` runs on a final `v*` tag or manual dispatch with the required `tag` input. The sole publishing job is named `release`. It validates all 86 projects, checks tag/version and local/remote tag commit equality, verifies the full reactor, deploys and verifies GitHub Packages, then deploys and verifies Maven Central. Only then does it create the GitHub Release.

The root aggregator has no parent and is not published. The standalone artifacts in `build/parent` and `build/bom` remain published. Nested modules may inherit their effective version from a local framework parent. Explicitly nonpublishable samples remain in the test reactor and are excluded from both registries. With the current source inventory, 84 projects are publishable: 312 required main/POM/source/Javadoc files and 624 files when detached Central signatures are included.

The root and nonpublishable samples must have both normal Maven deploy suppression and Central `skipPublishing` configured. The Central extension replaces the normal deploy binding; `maven.deploy.skip` alone cannot suppress its publication.

## Final-version procedure

1. Qualify the source changes and set every explicit project/parent reference and internal version property to the same final version, for example `26.10.21`. Inherited versions resolve to that train.
2. Run `python3 scripts/release_tools.py check` and `./mvnw -B -ntp clean verify`.
3. Create an immutable tag whose name is exactly `v` plus the final POM version. The release workflow refuses a snapshot, a mismatched tag, or a checkout that differs from the tag commit.
4. Inspect the complete `release` job result. A final release requires both registries and the GitHub Release to succeed. GitHub Packages can succeed before a later Central failure; this is a partial release, even though some artifacts are already downloadable.

The first gate runs `clean install` with tests enabled, followed by every maintained standalone example. A release-profile installation then prepares the POMs, main JARs, sources, and Javadoc with tests skipped only for this packaging step. `github_packages.py` freezes and hashes those installed files, validates the reviewed package-home map and live GitHub package associations, then submits all 84 pinned Maven deploy-file executions in one process. It publishes each installed POM unchanged, preserving the parent and BOM metadata.

GitHub verification downloads all 312 required files from the Jafly repository. Snapshot verification resolves each timestamped variant and reads all 84 metadata documents again after the downloads. Maven Central uses the existing signed aggregate-bundle profile and runs its lifecycle with tests enabled. These are separate artifact-generation steps; each registry is verified against its own produced bytes, without a promise of byte-identical archives or signatures across registries.

The Central plugin version is [0.12.0](https://central.sonatype.org/publish/publish-portal-maven/). Its source stages each project's artifacts into a shared session directory and creates/uploads one aggregate ZIP after the last publishing project. The workflow forces its output directory to retain that ZIP. `autoPublish=true`, `waitUntil=published`, and `waitMaxTime=3600` require completed publication. Timeout, duplicate-coordinate errors, missing files, and digest mismatches fail the job. There are no log-grep exceptions or soft-success handlers.

## Credentials and package access

Publishing uses the **Sonatype Central Portal** plugin (`central-publishing-maven-plugin` 0.12.0), not legacy OSSRH/`nexus-staging-maven-plugin`. Maven settings server id `central` maps to Portal user tokens.

| Secret / variable | Purpose |
| --- | --- |
| `JAFLY_PACKAGES_TOKEN` | Classic PAT with `write:packages` (and repo access) for the Jafly Maven repository |
| `JAFLY_PACKAGES_USER` (repo variable, optional) | Token owner when different from the workflow actor |
| `SONATYPE_USERNAME` | Central Portal **username** (Portal account user token username) |
| `SONATYPE_PASSWORD` | Central Portal **user token** password (Portal → View Account → User Token) |
| `GPG_PRIVATE_KEY` | Base64-encoded ASCII-armored private key used to sign the aggregate bundle |
| `GPG_PASSPHRASE` | Passphrase for that key (`MAVEN_GPG_PASSPHRASE` in the Central deploy step) |

`GITHUB_TOKEN` (built-in, `contents: write`) creates the final GitHub Release after both registries verify. Run `publishing-preflight.yml` to confirm secret *names* are present; presence does not prove Portal namespace or signing-key acceptance. Generated Maven settings use environment references, and publication logs redact the package token.

GitHub Maven packages are repository-scoped. The reviewed `scripts/package-homes.json` directs all 84 publishable artifacts to `fireflyframework-jafly`. Before uploading any files, the publisher checks every package through the GitHub API. An absent package can be created; an existing package must belong to Jafly. A package still associated with another repository, an inaccessible association, or an API failure stops publication before Maven starts. Changing a deployment URL does not transfer a package or preserve its old versions. The captured `scripts/package-associations.jsonl` remains historical evidence of the previous registry layout. See [GitHub package permissions](https://docs.github.com/en/packages/learn-github-packages/about-permissions-for-github-packages).

Namespace authorization and signing-key acceptance require a live publishing run. Credential presence, local tests, and a source POM version do not prove publication.

## Failure and retry

Do not delete old releases, replace tags, or overwrite immutable artifacts to make a retry pass. Download the retained release evidence and determine which registry completed. Manual dispatch accepts an existing tag, but this minimal direct-deploy workflow does not implement automatic partial-publication recovery: rerunning after a successful immutable deployment may fail at that registry with a conflict. Keep the failure visible and resolve the incomplete target with the retained tag, artifact inventory, and Central deployment details. Never claim that a retry succeeded merely because one artifact already exists.

A Maven Central timeout may leave a deployment running in the Portal. Check its deployment ID from the workflow log before any new upload. Preserve the Central ZIP from the workflow artifact. This workflow does not automatically poll an old deployment on rerun.

## Evidence

Successful final releases attach:

- `release-manifest.json`: the Central inventory, as an array of `{"path": "org/fireflyframework/...", "sha256": "..."}` records, including signatures.
- `github-manifest.json`: the independently verified GitHub Packages inventory in the same format.
- `reactor-inventory.json`: group ID, artifact ID, version, packaging, source directory, and publication flag for each project.
- `package-homes.json`: the reviewed artifact-to-Jafly publication map.
- `github-verification.json`: independently read artifact paths, repository homes, and matching SHA-256 digests.
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

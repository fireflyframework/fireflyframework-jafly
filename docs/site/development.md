# Develop in one reactor

![Develop in one reactor](assets/banners/development.svg)

Use one checkout for the framework parent, BOM, and Java components. Maven orders the projects from their declared dependencies and parent relationships.

## Repository map

```text
pom.xml                 Source reactor; not published
build/parent/           Consumer parent and build management
build/bom/              Independent framework dependency catalog
modules/                Component sources and nested projects
scripts/                Version, provenance, release, and docs checks
tests/                  Monorepo and publishing-tool contract tests
docs/site/              Published documentation source
docs/migration/         Source history and migration evidence
```

Each component directory contains its source, tests, resources, and licenses, with examples where the component supplies them. Start with its README or find the guide in the [module catalog](modules/index.md).

## Branches and pull requests

Use a short-lived feature or fix branch and open a pull request into `develop`. Both `develop` and `main` require the reactor verification and documentation checks. Resolve review conversations before merging. These branches reject deletion and force pushes.

`develop` carries the next snapshot train. Promote a qualified train to `main` through a pull request. `main` supplies the published documentation and final release tags. Snapshot publication is an explicit workflow dispatch from either active branch; a final release uses an immutable `vYY.MM.PP` tag whose version matches the source POMs.

Imported branches and tags live under `legacy/<source-repository>/...`. They are historical references, not development targets. Keep their original commits and namespaced names intact. When work from an original pull request is still relevant, create a new branch from `develop`, port the change, and link the original conversation.

## Verify before changing behavior

Use JDK 25, the wrapper, Python, and Docker:

```sh
python3 -m unittest discover -s tests -v
python3 -m unittest discover -s tests/release -v
python3 scripts/release_tools.py check
./mvnw -B -ntp clean verify
```

The Maven lifecycle runs the tests configured by each project. Check the project's Surefire/Failsafe includes, exclusions, and profiles before assuming a focused command covers every integration case.

CI always reports its required verification check. Changes limited to Markdown or published site assets run structural and release-tool checks; source, resource, POM, and build-tool changes also run the complete Java build and standalone examples. Manual CI dispatch runs the full Java checks. A new prose update does not cancel a Java build already in progress.

## Work on a component

```sh
./mvnw -B -ntp -pl :fireflyframework-web -am verify
```

The selected project and its prerequisites come from the local reactor. To compile consumers outside it, first run `./mvnw install`.

The complete client example under `modules/client/examples/complete-example` retains a standalone Spring Boot parent and sits outside the default reactor. Build it separately after local framework installation. Agentic bridge samples remain inside the reactor for compilation/testing and are not published.

## Keep dependency management consistent

Add public artifacts to both the parent and BOM catalogs. Preserve artifact IDs and package names unless a separately reviewed API change requires otherwise. Use the framework train for internal references and the selected platform's management for third-party versions.

The root aggregator does not become a consumer parent. The standalone parent/BOM do not inherit it or introduce a parent/BOM import cycle.

## Review evidence

Surefire and Failsafe reports live under each project's `target` directory. CI uploads those reports even when a build fails. Report skipped tests separately from executed tests, and identify which providers were exercised. Check consumer builds and published downloads separately from the library tests.

For documentation changes, run the [strict site checks](documentation.md). For version or publishing changes, run the [release-tool tests](releases.md) and inspect the immutable-tag contract.

## Source history

The original imports preserve source commit ancestry. `docs/migration/source-manifest.json` maps repositories to destination paths and source commits. `legacy/<repository>/...` refs retain historical branches and tags. Do not rewrite these refs to simplify the new release history.

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs/site/assets/brand/jafly-banner-dark.svg">
  <img src="docs/site/assets/brand/jafly-banner-light.svg" alt="jafly, Java framework by Firefly" width="100%">
</picture>

<p align="center">
  <a href="https://fireflyframework.github.io/fireflyframework-jafly/">Documentation</a> ·
  <a href="docs/site/getting-started.md">Get started</a> ·
  <a href="docs/site/modules/index.md">Module catalog</a> ·
  <a href="https://fireflyframework.org">Framework by Firefly</a>
</p>

<p align="center">
  <a href="https://github.com/fireflyframework/fireflyframework-jafly/actions/workflows/ci.yml"><img src="https://github.com/fireflyframework/fireflyframework-jafly/actions/workflows/ci.yml/badge.svg?branch=main" alt="Framework CI"></a>
  <a href="https://github.com/fireflyframework/fireflyframework-jafly/actions/workflows/docs.yml"><img src="https://github.com/fireflyframework/fireflyframework-jafly/actions/workflows/docs.yml/badge.svg?branch=main" alt="Documentation build and publication"></a>
  <img src="https://img.shields.io/badge/Java-25-10110f?style=flat-square" alt="Java 25">
  <img src="https://img.shields.io/badge/Spring_Boot-4.1.1-10110f?style=flat-square" alt="Spring Boot 4.1.1">
  <img src="https://img.shields.io/badge/Maven-3.9.16-10110f?style=flat-square" alt="Maven 3.9.16 wrapper">
  <a href="LICENSE"><img src="https://img.shields.io/badge/Code_license-Apache_2.0-855414?style=flat-square" alt="Code license: Apache 2.0"></a>
</p>

Jafly adds application libraries to Spring Boot, WebFlux, and Reactor. Use its command and query buses to structure operations, then add coordination, messaging, security, or provider adapters where your service needs them.

The framework parent and BOM manage compatible module versions. The platform uses Spring Boot 4.1.1, Spring Cloud 2025.1.3, and Java 25.

The [first service tutorial](docs/site/getting-started.md) builds a customer onboarding service with command and query handlers, then tests it over HTTP. See [installation](docs/site/installation.md) for the current source version and registry setup.

## Start with the source

Use JDK 25, Python 3, Git, and a running Docker engine for container-backed integration tests. The checked-in wrapper downloads checksum-pinned Maven 3.9.16.

```sh
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -B -ntp clean verify
```

Build one component together with its prerequisites:

```sh
./mvnw -B -ntp -pl :fireflyframework-cqrs -am verify
```

Install the framework locally before consuming the development train from a separate application:

```sh
./mvnw -B -ntp install
```

Windows users can use `mvnw.cmd`. See [installation](docs/site/installation.md) for prerequisites and setup troubleshooting, or the [first application tutorial](docs/site/getting-started.md) for a complete small application POM.

## Choose your building blocks

| Responsibility | Components |
| --- | --- |
| Application structure | [Kernel](docs/site/modules/kernel.md), [CQRS](docs/site/modules/cqrs.md), [orchestration](docs/site/modules/orchestration.md), [event sourcing](docs/site/modules/eventsourcing.md), role-specific starters |
| Messaging and integration | [EDA](docs/site/modules/eda.md), Kafka, RabbitMQ, PostgreSQL, [HTTP client](docs/site/modules/client.md), callbacks, webhooks, agentic bridge |
| Data and content | [R2DBC](docs/site/modules/r2dbc.md), cache stores, [ECM](docs/site/modules/ecm.md), content storage and e-signature adapters |
| Security | API, SPI, core, WebFlux, resource server, method policy, identity providers, and authorization adapters |
| Delivery and operations | Notifications and providers, observability, validation, configuration, and application/domain/data/core starters |

Use the [module catalog](docs/site/modules/index.md) to choose a library and find its configuration and examples. Read the [architecture guide](docs/site/architecture.md) before deciding which responsibilities belong in your handlers or provider adapters.

## Add Jafly to an application

Use the framework parent when you want its build and dependency management:

```xml
<parent>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-parent</artifactId>
    <version>26.10.17-SNAPSHOT</version>
    <relativePath/>
</parent>
```

Or retain your existing parent and import the independent BOM:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.fireflyframework</groupId>
            <artifactId>fireflyframework-bom</artifactId>
            <version>26.10.17-SNAPSHOT</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

Then declare the libraries you need:

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-cqrs</artifactId>
</dependency>
```

The parent and BOM manage the same framework artifact versions. Your application keeps its own version and declares its runtime dependencies. Choose the parent for build management, or import the BOM to keep your existing parent. See [Maven integration details](docs/site/maven.md).

## Find your way around

```text
pom.xml                    Source aggregation; not a consumer parent
build/parent/              Published fireflyframework-parent
build/bom/                 Published fireflyframework-bom
modules/                   61 component repositories and nested projects
scripts/                   Version, provenance, release, and documentation tools
tests/                     Monorepo and publishing contract tests
docs/site/                 Documentation site and brand assets
docs/migration/            Source history, original commit IDs, migration evidence
```

Components retain their source, tests, examples, resources, and licenses. The standalone complete client example stays outside the default reactor; build it after local framework installation. Agentic bridge samples remain in the reactor for testing and are not published.

Python, PHP, Agentic, Go, Rust, .NET, and frontend implementations retain their own repositories. The Java agentic bridge integrates with the separate Agentic runtime.

## Develop, document, and release

```sh
python3 -m unittest discover -s tests -v
python3 -m unittest discover -s tests/release -v
python3 scripts/release_tools.py check
./mvnw -B -ntp clean verify
```

- [Development workflow](docs/site/development.md): focused builds, tests, and source boundaries.
- [Documentation workflow](docs/site/documentation.md): pinned MkDocs environment, complete artifact coverage, and strict site builds.
- [Versioning](docs/site/versioning.md): one `YY.MM.PATCH` train, with a monthly release counter.
- [Release workflow](docs/site/releases.md): immutable matching tags and independently verified GitHub Packages/Maven Central publication.

Use the workflows at the repository root for Java builds and releases. Workflows retained inside module directories belong to the imported history.

## Preserved history

The [source provenance manifest](docs/migration/source-manifest.json) maps all 63 repositories to destination paths, original source commits, imported branches, and tags. Original main commit IDs remain ancestors of the monorepo. Historical refs use `legacy/<source-repository>/<original-ref>` so identical original release tags do not collide.

Historical GitHub issues, pull requests, and release records retain their original source URLs. See [migration and compatibility](docs/migration/README.md) for import boundaries, cutover, and rollback.

Framework code is [Apache License 2.0](LICENSE). Official Jafly/Firefly artwork and the self-hosted Manrope font retain their separate [brand provenance and notices](docs/site/brand.md).

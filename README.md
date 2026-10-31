<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs/site/assets/brand/jafly-banner-dark.svg">
  <img src="docs/site/assets/brand/jafly-banner-light.svg" alt="jafly, Java framework by Firefly" width="100%">
</picture>

<p align="center">
  <a href="https://fireflyframework.github.io/fireflyframework-jafly/">Documentation</a> ·
  <a href="https://fireflyframework.github.io/fireflyframework-jafly/getting-started/">Get started</a> ·
  <a href="https://fireflyframework.github.io/fireflyframework-jafly/modules/">Module catalog</a> ·
  <a href="https://fireflyframework.github.io/fireflyframework-jafly/maven/">Maven</a> ·
  <a href="https://fireflyframework.org/frameworks/java/">Framework by Firefly</a>
</p>

<p align="center">
  <a href="https://github.com/fireflyframework/fireflyframework-jafly/actions/workflows/ci.yml"><img src="https://github.com/fireflyframework/fireflyframework-jafly/actions/workflows/ci.yml/badge.svg?branch=main" alt="Framework CI"></a>
  <a href="https://github.com/fireflyframework/fireflyframework-jafly/actions/workflows/docs.yml"><img src="https://github.com/fireflyframework/fireflyframework-jafly/actions/workflows/docs.yml/badge.svg?branch=main" alt="Documentation build and publication"></a>
  <a href="https://github.com/fireflyframework/fireflyframework-jafly/actions/workflows/release.yml"><img src="https://github.com/fireflyframework/fireflyframework-jafly/actions/workflows/release.yml/badge.svg" alt="Unified release"></a>
  <a href="https://central.sonatype.com/artifact/org.fireflyframework/fireflyframework-parent"><img src="https://img.shields.io/maven-central/v/org.fireflyframework/fireflyframework-parent?color=10110f&style=flat-square&label=Maven%20Central" alt="Maven Central"></a>
  <img src="https://img.shields.io/badge/Java-25-10110f?style=flat-square" alt="Java 25">
  <img src="https://img.shields.io/badge/Spring_Boot-4.1.1-10110f?style=flat-square" alt="Spring Boot 4.1.1">
  <img src="https://img.shields.io/badge/Maven-3.9.16-10110f?style=flat-square" alt="Maven 3.9.16 wrapper">
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-Apache_2.0-855414?style=flat-square" alt="License: Apache 2.0"></a>
</p>

## The layers between Spring Boot and your business rules

Jafly is the Java framework in Framework by Firefly. It brings command and query buses, saga and TCC coordination, event transports, service clients, policy-based security, and observability to applications built with Spring Boot, WebFlux, and Reactor.

Spring continues to host your application, route HTTP requests, manage dependencies, and provide data access. Jafly connects application operations and provider adapters through shared contracts. Your team writes the business rules and chooses authorization, transaction boundaries, and recovery behavior.

Choose the libraries your service needs; the parent and BOM keep their versions aligned. The current platform uses Java 25, Spring Boot 4.1.1, and Spring Cloud 2025.1.3.

## Documentation map

| Start here | What you get |
| --- | --- |
| [Documentation site](https://fireflyframework.github.io/fireflyframework-jafly/) | Published guides, architecture, Maven, releases, and the full module catalog |
| [Getting started](docs/site/getting-started.md) | First onboarding service: HTTP → command → storage → query |
| [Module catalog](docs/site/modules/index.md) | Every component, grouped by capability, with configuration and examples |
| [Architecture](docs/site/architecture.md) | Boundaries between handlers, ports, and provider adapters |
| [Maven parent and BOM](docs/site/maven.md) | How applications consume the train |
| [Installation](docs/site/installation.md) | JDK, Maven wrapper, Docker, and local setup |
| [Examples and tutorials](docs/site/examples.md) | Onboarding paths and integration samples |
| [Development](docs/site/development.md) · [Docs workflow](docs/site/documentation.md) · [Versioning](docs/site/versioning.md) · [Releases](docs/site/releases.md) | Contribute, build docs, and publish |

Source-backed copies of the same pages live under [`docs/site/`](docs/site/). The MkDocs site is the preferred reading experience.

## What Jafly adds

The documentation follows the same eight capability areas as [Jafly on Framework by Firefly](https://fireflyframework.org/frameworks/java/).

| Capability | What you can build with it |
| --- | --- |
| [Security and identity](docs/site/capabilities/security-and-identity.md) | Map JWTs to principals, protect methods and routes, and connect identity and policy providers through shared ports. |
| [Business operations](docs/site/capabilities/business-operations.md) | Dispatch commands and queries to typed handlers, apply configured validation and authorization, and evaluate business rules. |
| [External services](docs/site/capabilities/external-services.md) | Call REST, SOAP, and gRPC services, send notifications, manage callbacks, and connect to the separate Agentic runtime. |
| [Event-driven integration](docs/site/capabilities/event-driven-integration.md) | Publish and consume through Spring events, Kafka, RabbitMQ, or PostgreSQL; receive webhooks with provider-specific processing. |
| [Domain and data](docs/site/capabilities/domain-and-data.md) | Use reactive PostgreSQL access, filtering and pagination, query caches, content storage, and optional event sourcing. |
| [Business coordination](docs/site/capabilities/business-coordination.md) | Coordinate saga steps and compensations, try-confirm-cancel participants, and workflows with explicit application recovery policies. |
| [Service operations](docs/site/capabilities/service-operations.md) | Configure metrics, traces, health checks, HTTP conventions, and shared configuration for your services. |
| [Team-wide architecture](docs/site/capabilities/team-wide-architecture.md) | Adopt the parent or BOM, service-layer starters, shared kernel contracts, and plugin interfaces across a team's applications. |

Use the [module catalog](docs/site/modules/index.md) to pick a concrete library (for example CQRS, a Kafka EDA adapter, or a security IDP). Each catalog entry links to its guide, source tree under `modules/`, and tests.

<picture>
  <source media="(max-width: 600px)" srcset="docs/site/assets/diagrams/request-flow-narrow.svg">
  <img src="docs/site/assets/diagrams/request-flow.svg" alt="An onboarding request reaches the command bus and application handler, then returns a result. Event publishing and query-cache invalidation require their own configuration." width="100%">
</picture>

## Follow the onboarding examples

The tutorials follow a customer application through three separate projects. Each has its own executable tests and states its storage and provider boundaries.

| Project | What you learn |
| --- | --- |
| [Accept an application](docs/site/getting-started.md) | Validate HTTP input, dispatch commands and queries, and retrieve an application from an in-memory repository. |
| [Retain its event history](docs/site/guides/onboarding-event-store.md) | Append typed events to PostgreSQL, reconstruct an aggregate, reject a stale writer, and roll back an invalid batch. |
| [Coordinate a review](docs/site/guides/onboarding-coordination.md) | Compensate a failed Saga, confirm or cancel TCC reservations, and order Workflow steps with explicit recovery. |

Continue with [examples and tutorials](docs/site/examples.md) for the next integration. The three projects share the onboarding story; each runs independently so its failure and transaction behavior can be tested directly.

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

## Add Jafly to an application

Use the framework parent when you want its build and dependency management:

```xml
<parent>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-parent</artifactId>
    <version>26.10.23</version>
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
            <version>26.10.23</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

Then declare the libraries you need (see the [module catalog](docs/site/modules/index.md) for artifact IDs):

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-cqrs</artifactId>
</dependency>
```

The parent and BOM manage the same framework artifact versions. Your application keeps its own version and declares its runtime dependencies. Details: [Maven integration](docs/site/maven.md).

Published coordinates use `org.fireflyframework`. Stable releases go to Maven Central; development snapshots publish to the Jafly GitHub Packages repository (`maven.pkg.github.com/fireflyframework/fireflyframework-jafly`).

## Repository layout

```text
pom.xml           Reactor aggregation POM (not a consumer parent)
build/parent/     Published fireflyframework-parent
build/bom/        Published fireflyframework-bom
modules/          Framework components (source, tests, examples, guides)
docs/site/        Documentation source for the published site
scripts/          Version, release, docs, and publishing tools
tests/            Repository contract tests
```

Active CI and release workflows live at the repository root. Nested workflow files under `modules/` are inactive historical copies and are not the publishing entry point.

Sibling language frameworks (pyfly, larafly, rsfly, netfly) and products such as Agentic, Forge, and Weave keep their own repositories. The [Java Agentic Bridge](modules/agentic-bridge) in this tree integrates Java services with the separate Agentic runtime.

## Develop, document, and release

```sh
python3 -m unittest discover -s tests -v
python3 -m unittest discover -s tests/release -v
python3 scripts/release_tools.py check
./mvnw -B -ntp clean verify
```

- [Development workflow](docs/site/development.md) — focused builds, tests, and module boundaries
- [Documentation workflow](docs/site/documentation.md) — pinned MkDocs environment and catalog completeness checks
- [Versioning](docs/site/versioning.md) — one `YY.MM.PATCH` train
- [Release workflow](docs/site/releases.md) — verified GitHub Packages and Maven Central publication

## License

Framework source code is licensed under the [Apache License 2.0](LICENSE).

```text
Copyright © Firefly Software Solutions Inc. and contributors

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0
```

Official Jafly and Firefly artwork and the self-hosted Manrope font retain separate [brand provenance and notices](docs/site/brand.md). Those assets are not covered by the Apache 2.0 grant for the framework code.

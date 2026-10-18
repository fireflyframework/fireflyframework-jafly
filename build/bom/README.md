# Firefly Framework - Bill of Materials (BOM)

[![CI](https://github.com/fireflyframework/fireflyframework-bom/actions/workflows/ci.yml/badge.svg)](https://github.com/fireflyframework/fireflyframework-bom/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21%2B-orange.svg)](https://openjdk.org)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-green.svg)](https://spring.io/projects/spring-boot)

> Centralized Maven dependency management for the Firefly Framework — import one BOM and every framework module resolves to a single, tested, conflict-free version.

---

## Table of Contents

- [Overview](#overview)
  - [Managed Modules](#managed-modules)
- [Features](#features)
- [Requirements](#requirements)
- [Installation](#installation)
- [Quick Start](#quick-start)
- [Configuration](#configuration)
- [Versioning & Release](#versioning--release)
- [Documentation](#documentation)
- [Contributing](#contributing)
- [License](#license)

## Overview

`fireflyframework-bom` is the **Bill of Materials** for the Firefly Framework. A BOM is a `pom`-packaged artifact that contains nothing but a `<dependencyManagement>` block: it declares the exact, mutually compatible version of every Firefly Framework module under a single coordinate. By importing it into your project's `dependencyManagement`, you can then depend on any framework module **without specifying a version** — the BOM pins them all for you.

This solves the classic multi-module versioning problem. The Firefly Framework is published as ~70 independently released artifacts (the cache core and its four adapters, the EDA core and its three transports, CQRS, event sourcing, the orchestration engine, ECM, IDP, notifications, rule engine, webhooks, callbacks, plugins, and the Spring Boot starters). Hand-managing those versions invites drift, transitive conflicts, and "works on my machine" mismatches. The BOM guarantees that, say, `fireflyframework-cqrs` and `fireflyframework-eda-kafka` always agree on the same `fireflyframework-kernel`.

Within the framework's build infrastructure there are two complementary artifacts:

- **`fireflyframework-parent`** — a *parent POM* you inherit from (`<parent>`). It supplies the Java/Spring Boot baseline, plugin configuration, and also imports this BOM, so inheriting the parent is the most complete path.
- **`fireflyframework-bom`** (this repo) — a *BOM* you import (`<scope>import</scope>`). Use it when your project already has its own parent (for example a corporate parent POM or `spring-boot-starter-parent`) and therefore cannot inherit `fireflyframework-parent`, but you still want one-line version management for all Firefly modules.

The BOM is published to Maven Central and uses **calendar versioning** (`YY.MM.PATCH`, e.g. `26.05.08`). It carries **no code and no runtime behavior** — it only constrains versions.

### Managed Modules

The BOM pins versions for every module in the framework. They are grouped below by family. Cache and EDA follow a core-plus-adapter design: the **core** module defines the SPI and ships a sensible default, while **providers/transports live in separate adapter modules** that you add only when you need them.

| Family | Managed Artifacts | Purpose |
|--------|-------------------|---------|
| **Foundation** | `fireflyframework-kernel`, `fireflyframework-utils`, `fireflyframework-validators` | Shared primitives, utilities, and validation that every module depends on. |
| **Cache** | `fireflyframework-cache` (core) + `-cache-redis`, `-cache-hazelcast`, `-cache-jcache`, `-cache-postgres` | Reactive multi-provider cache abstraction; Caffeine is the in-core default, the listed adapters are pluggable providers. |
| **EDA** | `fireflyframework-eda` (core) + `-eda-kafka`, `-eda-rabbitmq`, `-eda-postgres` | Event-driven architecture abstraction; transports are separate adapter modules. |
| **Data** | `fireflyframework-r2dbc` | Reactive relational data access (R2DBC) building blocks. |
| **CQRS & Event Sourcing** | `fireflyframework-cqrs`, `fireflyframework-eventsourcing` | Command/query buses and event-sourced aggregates over a reactive event store. |
| **Orchestration** | `fireflyframework-orchestration` | Saga, workflow, and TCC distributed-transaction engine. |
| **Web & Client** | `fireflyframework-web`, `fireflyframework-client`, `fireflyframework-observability` | WebFlux server scaffolding, REST/SOAP/gRPC/GraphQL/WS client builders, and Micrometer/OpenTelemetry observability. |
| **Starters** | `fireflyframework-starter-core`, `-starter-domain`, `-starter-data`, `-starter-application` | Opinionated Spring Boot starters per architectural tier. |
| **Application Layer** | `fireflyframework-backoffice` | Back-office application building blocks. |
| **ECM** | `fireflyframework-ecm` + `-ecm-storage-aws`, `-ecm-storage-azure`, `-ecm-esignature-docusign`, `-ecm-esignature-adobe-sign`, `-ecm-esignature-logalty` | Enterprise content management with pluggable storage (S3, Azure Blob) and e-signature adapters. |
| **IDP** | `fireflyframework-security-idp` + `-idp-keycloak`, `-idp-aws-cognito`, `-idp-azure-ad`, `-idp-internal-db` | Identity-provider abstraction with pluggable provider adapters. |
| **Notifications** | `fireflyframework-notifications`, `-notifications-core` + `-notifications-firebase`, `-twilio`, `-sendgrid`, `-resend` | Multi-channel notifications (push, SMS, email) with provider adapters. |
| **Rule Engine** | `fireflyframework-rule-engine` (aggregator) + `-core`, `-interfaces`, `-models`, `-web`, `-sdk` | YAML-DSL business rule engine. |
| **Webhooks** | `fireflyframework-webhooks` (aggregator) + `-interfaces`, `-core`, `-web`, `-processor`, `-sdk` | Inbound/outbound webhook delivery and processing. |
| **Callbacks** | `fireflyframework-callbacks` (aggregator) + `-interfaces`, `-models`, `-core`, `-web`, `-sdk` | Asynchronous callback management. |
| **Plugins** | `fireflyframework-plugins` (aggregator) + `plugin-api`, `plugin-core` | Plugin/extensibility system. |
| **Config Server** | `fireflyframework-config-server` | Centralized externalized configuration server. |
| **Agentic Bridge** | `fireflyframework-agentic-bridge` (aggregator) + `-core`, `-autoconfigure`, `-starter` | Bridge for AI/LLM agent integration. |

> Aggregator entries (rule engine, webhooks, callbacks, plugins, agentic bridge) are declared with `<type>pom</type>` so importing or depending on them pulls the family's reactor POM.

## Features

- **One-line version management** — import the BOM once, depend on any Firefly module with no `<version>`.
- **Conflict-free resolution** — all ~70 managed artifacts share `${project.version}`, so every module agrees on the same `fireflyframework-kernel` and transitive baseline.
- **Parent-agnostic** — works alongside any existing parent POM (`spring-boot-starter-parent`, a corporate parent, etc.) when you cannot inherit `fireflyframework-parent`.
- **Complete coverage** — foundation, cache core + adapters, EDA core + transports, data/R2DBC, CQRS, event sourcing, orchestration, web/client/observability, starters, ECM, IDP, notifications, rule engine, webhooks, callbacks, plugins, config server, and the agentic bridge.
- **Calendar-versioned & Maven Central published** — predictable `YY.MM.PATCH` releases, signed and published via the Sonatype Central publishing pipeline.
- **Zero runtime footprint** — a `pom`-only artifact; it never lands on your classpath, it only constrains versions.

## Requirements

- Java 21+ (Java 25 recommended; the CI builds with JDK 25)
- Spring Boot 3.x (for the modules whose versions the BOM manages)
- Maven 3.9+

## Installation

Import the BOM into your project's `dependencyManagement`. Replace the version with the latest release; the BOM itself is the single source of truth for every other Firefly version.

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.fireflyframework</groupId>
            <artifactId>fireflyframework-bom</artifactId>
            <version>26.05.08</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

### Alternative: inherit the parent

If your project does not already have a parent POM, inheriting `fireflyframework-parent` is the more complete option — it imports this BOM **and** supplies the shared Java/Spring Boot baseline and plugin configuration:

```xml
<parent>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-parent</artifactId>
    <version>26.05.08</version>
</parent>
```

Use the **BOM import** when you already have a parent; use the **parent** when you do not.

## Quick Start

After importing the BOM (or inheriting the parent), declare framework modules **without versions** — they are resolved from the BOM:

```xml
<dependencies>
    <!-- Opinionated Spring Boot starter for a core-tier microservice -->
    <dependency>
        <groupId>org.fireflyframework</groupId>
        <artifactId>fireflyframework-starter-core</artifactId>
    </dependency>

    <!-- WebFlux server scaffolding -->
    <dependency>
        <groupId>org.fireflyframework</groupId>
        <artifactId>fireflyframework-web</artifactId>
    </dependency>

    <!-- Event-driven architecture: core SPI ... -->
    <dependency>
        <groupId>org.fireflyframework</groupId>
        <artifactId>fireflyframework-eda</artifactId>
    </dependency>

    <!-- ... plus the Kafka transport adapter (added only when needed) -->
    <dependency>
        <groupId>org.fireflyframework</groupId>
        <artifactId>fireflyframework-eda-kafka</artifactId>
    </dependency>
</dependencies>
```

Need a different cache provider or messaging transport? Just add the matching adapter artifact — for example `fireflyframework-cache-redis` or `fireflyframework-eda-rabbitmq` — still with no `<version>`.

## Configuration

The BOM has **no configuration**. It contributes nothing to the classpath and exposes no `firefly.*` properties or auto-configuration — it exists purely to manage versions in your build. Runtime configuration belongs to the individual modules you import (each module's own README documents its `firefly.*` properties).

## Versioning & Release

- **Scheme** — calendar versioning `YY.MM.PATCH` (current: `26.05.08`). Every managed module is released in lockstep at the same version, which is what guarantees compatibility.
- **Branches** — `main` (released) and `develop` (integration). Documentation-only changes (`**.md`) are ignored by CI and trigger no build or release.
- **Publishing** — tagging `v*` runs the shared release and Maven Central workflows: the POM is GPG-signed (`maven-gpg-plugin`), flattened for OSSRH (`flatten-maven-plugin`), and published through the Sonatype `central-publishing-maven-plugin`.

## Documentation

- **Framework hub & module catalog**: [github.com/fireflyframework](https://github.com/fireflyframework)
- **Per-module documentation**: each Firefly module ships its own README with usage and `firefly.*` configuration details.

## Contributing

Contributions are welcome. Please read the [CONTRIBUTING.md](CONTRIBUTING.md) guide for details on our code of conduct, development process, and how to submit pull requests.

## License

Copyright 2024-2026 Firefly Software Foundation.

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for details.

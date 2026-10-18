# Firefly Framework - Config Server

[![CI](https://github.com/fireflyframework/fireflyframework-config-server/actions/workflows/ci.yml/badge.svg)](https://github.com/fireflyframework/fireflyframework-config-server/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21%2B-orange.svg)](https://openjdk.org)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-green.svg)](https://spring.io/projects/spring-boot)

> A ready-to-run Spring Cloud Config Server for the Firefly Framework — serves centralized, hierarchical configuration (common / core / domain tiers) to every Firefly microservice over HTTP.

---

## Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Requirements](#requirements)
- [Installation](#installation)
- [Quick Start](#quick-start)
- [Configuration](#configuration)
- [Config Repository Layout](#config-repository-layout)
- [Client Integration](#client-integration)
- [Documentation](#documentation)
- [Contributing](#contributing)
- [License](#license)

## Overview

Firefly Framework Config Server is a **standalone, runnable Spring Boot application** that provides centralized configuration management for Firefly-based microservices. It is built on [Spring Cloud Config Server](https://spring.io/projects/spring-cloud) (`@EnableConfigServer`) and acts as a single source of truth for application properties, so individual services no longer have to bundle environment-specific configuration.

Unlike a typical Git-backed config server, this module ships preconfigured with the Spring Cloud Config **`native` profile**: configuration is served directly from the classpath (and can equally be pointed at the filesystem or a Git backend). The bundled configuration is organized into three **tiers** that mirror the Firefly architecture — `common`, `core`, and `domain` — plus per-application overlays. This lets shared defaults live once at the `common`/`core` level while each microservice contributes only its own deltas.

The server exposes the standard Spring Cloud Config HTTP API (`/{application}/{profile}[/{label}]`), so any client built on `fireflyframework-starter-core` (or plain Spring Cloud Config Client) can fetch its merged configuration at startup, with support for environment profiles (`dev`, `pre`, `pro`) and runtime refresh via `/actuator/refresh`.

This module also depends on `fireflyframework-observability`, so the running server emits unified Firefly metrics, distributed traces, and structured logs out of the box, and exposes Actuator health/info/Prometheus endpoints consistent with the rest of the framework.

## Features

- **Standalone Spring Cloud Config Server** — a single `@SpringBootApplication @EnableConfigServer` entry point (`FireflyConfigServerApplication`) you can run as a dedicated microservice or container.
- **Hierarchical, tier-based configuration** — bundled `common`, `core`, and `domain` search locations let shared properties be defined once and overridden per layer and per application.
- **Native (classpath/filesystem) backend by default** — no external Git repository required to get started; the `native` profile serves config straight from `classpath:/config/...`. Easily switchable to a Git or filesystem backend.
- **Environment profile support** — first-class `dev` / `pre` / `pro` profiles via the standard `{application}-{profile}.yaml` convention.
- **Standard Config Server HTTP API** — serve any service's merged properties at `/{application}/{profile}` for clients to consume at bootstrap.
- **Encryption & decryption** — Spring Cloud Config Server's `{cipher}` property encryption and `/encrypt` `/decrypt` endpoints for protecting secrets (when a key is configured).
- **Runtime refresh** — clients can pull updated configuration without a restart via the Spring Cloud `/actuator/refresh` and bus mechanisms.
- **Built-in observability** — bundles `fireflyframework-observability` for unified metrics, tracing, structured logging, and Actuator `health`, `info`, and `prometheus` endpoints.

## Requirements

- Java 21+ (Java 25 recommended)
- Spring Boot 3.5.x
- Spring Cloud 2025.0.x (managed by the Firefly parent BOM)
- Maven 3.9+
- A configuration source: the bundled classpath config (default), or a filesystem path / Git repository for the corresponding backend

## Installation

This module is a runnable application rather than a library you embed, but the artifact is published so you can extend it, build a container image, or run it via the Spring Boot Maven plugin.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-config-server</artifactId>
    <!-- Version is managed by the Firefly BOM / parent POM -->
</dependency>
```

The module inherits from the Firefly parent, which manages the Spring Boot and Spring Cloud versions:

```xml
<parent>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-parent</artifactId>
    <!-- Version managed centrally -->
</parent>
```

## Quick Start

The application class is provided for you — there is nothing to write to stand up a config server:

```java
package org.fireflyframework.config.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;

@SpringBootApplication
@EnableConfigServer
public class FireflyConfigServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(FireflyConfigServerApplication.class, args);
    }
}
```

Run it locally:

```bash
mvn spring-boot:run
# or, after packaging
java -jar target/fireflyframework-config-server-*.jar
```

The server starts on port **8888** by default. Fetch a service's merged configuration over the Config Server HTTP API (`/{application}/{profile}`):

```bash
# Merged config for the "distributor-domain-branding" app in the dev profile
curl http://localhost:8888/distributor-domain-branding/dev

# Health, info and Prometheus endpoints (via fireflyframework-observability)
curl http://localhost:8888/actuator/health
curl http://localhost:8888/actuator/prometheus
```

## Configuration

The server's own behavior is defined in `src/main/resources/application.yml`. It runs the Spring Cloud Config **`native`** profile and serves configuration from a layered set of classpath search locations:

```yaml
server:
  port: 8888

spring:
  profiles:
    active: native
  cloud:
    config:
      server:
        native:
          search-locations: >
            classpath:/config/common,
            classpath:/config/core,
            classpath:/config/domain,
            classpath:/config/common/{application},
            classpath:/config/core/{application},
            classpath:/config/domain/{application}
```

| Property | Default | Description |
| --- | --- | --- |
| `server.port` | `8888` | HTTP port the config server listens on. |
| `spring.profiles.active` | `native` | Selects the native (classpath/filesystem) backend rather than Git. |
| `spring.cloud.config.server.native.search-locations` | the six tiered locations above | Ordered list of locations searched per request; later/more-specific locations override earlier shared ones. `{application}` is substituted with the requested application name. |

### Switching to a Git backend

The native backend is the default, but Spring Cloud Config Server supports a Git backend with no code changes — set the `git` profile and a repository URI:

```yaml
spring:
  profiles:
    active: git
  cloud:
    config:
      server:
        git:
          uri: https://github.com/your-org/config-repo
          default-label: main
          search-paths: '{application}'
```

## Config Repository Layout

Bundled configuration lives under `src/main/resources/config/` and is organized into three tiers, each searched in order. Shared defaults are resolved from the broad tiers first, then refined by more specific, per-application files:

```
config/
├── common/                         # Cross-cutting defaults for every service
│   ├── application.yaml            # base
│   ├── application-dev.yaml        # dev profile overlay
│   ├── application-pre.yaml        # pre (staging) profile overlay
│   └── application-pro.yaml        # pro (production) profile overlay
├── core/                           # Core-tier defaults
│   ├── application.yaml
│   ├── application-{dev,pre,pro}.yaml
└── domain/                         # Domain-tier defaults
    ├── application.yaml
    ├── application-{dev,pre,pro}.yaml
    └── distributor-domain-branding/        # Per-application overlay
        ├── distributor-domain-branding.yaml
        └── distributor-domain-branding-{dev,pre,pro}.yaml
```

- **Tiers** (`common`, `core`, `domain`) hold defaults shared by all services in that layer of the Firefly architecture.
- **Profiles** (`dev`, `pre`, `pro`) provide environment-specific overrides via the `{name}-{profile}.yaml` suffix.
- **Per-application directories** (e.g. `distributor-domain-branding/`) carry the deltas specific to one microservice, layered on top of the shared tiers.

Per-application files configure the conventional Firefly stack a service expects — `firefly.cqrs`, `firefly.eda`, `firefly.stepevents`, `springdoc`, Actuator exposure, and logging — so onboarding a new service is mostly a matter of adding its overlay.

## Client Integration

Firefly microservices consume this server through the Spring Cloud Config client wired up by `fireflyframework-starter-core`. A client points at the server and identifies itself by application name and active profile:

```yaml
spring:
  application:
    name: distributor-domain-branding
  config:
    import: optional:configserver:http://localhost:8888
  profiles:
    active: dev
```

At bootstrap the client fetches `http://localhost:8888/distributor-domain-branding/dev`, receiving the merged result of the `common`, `core`, and `domain` tiers plus its own per-application overlay.

## Documentation

- [Firefly Framework Module Catalog](https://github.com/fireflyframework/.github/blob/main/profile/MODULE_CATALOG.md) — the full catalog of framework modules.
- [Spring Cloud Config Reference](https://docs.spring.io/spring-cloud-config/reference/) — backend options, the HTTP API, encryption, and refresh.
- `fireflyframework-starter-core` — the client-side auto-configuration that connects Firefly services to this server.

## Contributing

Contributions are welcome. Please read the [CONTRIBUTING.md](CONTRIBUTING.md) guide for details on our code of conduct, development process, and how to submit pull requests.

## License

Copyright 2024-2026 Firefly Software Foundation.

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for details.

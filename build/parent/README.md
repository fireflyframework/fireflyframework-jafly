# Firefly Framework - Parent POM

[![CI](https://github.com/fireflyframework/fireflyframework-parent/actions/workflows/ci.yml/badge.svg)](https://github.com/fireflyframework/fireflyframework-parent/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21%2B-orange.svg)](https://openjdk.org)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-green.svg)](https://spring.io/projects/spring-boot)

> Parent POM for the Firefly Framework providing centralized dependency management, plugin configuration, and build standards inherited by every framework module.

---

## Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Requirements](#requirements)
- [Installation](#installation)
- [Quick Start](#quick-start)
- [Managed Dependencies](#managed-dependencies)
- [Managed Plugins](#managed-plugins)
- [Configuration](#configuration)
- [Build Profiles](#build-profiles)
- [Native Image Builds](#native-image-builds)
- [SBOM Generation](#sbom-generation)
- [Documentation](#documentation)
- [Contributing](#contributing)
- [License](#license)

## Overview

The Firefly Framework Parent POM (`org.fireflyframework:fireflyframework-parent`) is the foundational build artifact of the Firefly Framework — a Spring Boot superset providing CQRS, event-driven architecture, a transactional/saga engine, caching, and more. It is the single point of truth for dependency versions, plugin configuration, and build standards inherited by every framework module and downstream microservice.

By declaring this artifact as their Maven `<parent>`, Firefly modules and applications automatically receive aligned, conflict-free versions for Spring Boot 3.5.x, Spring Cloud 2025.0.x, reactive (R2DBC) and JDBC database drivers, mapping and serialization libraries, gRPC/Protobuf, OpenTelemetry, the major cloud SDKs (AWS, Azure, GCP), SOAP/WS stacks, and testing frameworks — plus ready-to-use build plugins. This removes per-module version bookkeeping and guarantees the whole ecosystem builds against one coherent dependency graph.

Beyond version management, the parent wires the compiler annotation-processor chain (Lombok, MapStruct, the Lombok-MapStruct binding, and the Spring Boot Configuration Processor), enables runtime-visible method parameter names (`-parameters`), and enforces a JDK 21+ floor via the Maven Enforcer plugin. It defaults to Java 25 with a `-Pjava21` profile for backward compatibility, and ships opt-in profiles for GraalVM native images, source/Javadoc release artifacts, and Maven Central publishing.

This is a pure build-infrastructure POM (`<packaging>pom</packaging>`): it has no Java source, no submodules, and no Spring auto-configuration. It is a parent to inherit, not a dependency to add. Modules that need framework capabilities (caching, EDA, CQRS, orchestration, etc.) inherit this parent and then declare the relevant Firefly module dependencies, whose versions are themselves governed here.

## Features

**Core Platform**
- Centralized dependency management for Spring Boot 3.5.x and Spring Cloud 2025.0.x
- Pre-configured annotation processors (Lombok, MapStruct, Spring Boot Configuration Processor)
- Maven Enforcer plugin requiring JDK 21+
- Java 25 by default with Java 21 backward compatibility via `-Pjava21`

**Database**
- Managed versions for PostgreSQL (JDBC and R2DBC), MySQL (R2DBC)
- Flyway database migrations with multi-database support (PostgreSQL, MySQL, SQL Server, Oracle, DB2, HSQLDB, Derby, Informix, Cassandra, ClickHouse, TiDB)

**API & Messaging**
- OpenAPI Generator plugin for server/client code generation from API specs
- SpringDoc OpenAPI for reactive API documentation
- gRPC 1.79.x and Protobuf 4.x version management
- Apache Avro serialization

**Cloud Providers**
- AWS SDK 2.x and Spring Cloud AWS
- Spring Cloud Azure
- Spring Cloud GCP

**Resilience & Observability**
- Resilience4j BOM for fault tolerance patterns
- OpenTelemetry BOM for distributed tracing and metrics
- Logstash Logback Encoder for structured logging

**Web Services**
- Apache CXF and JAX-WS for SOAP/WS-Security
- WSS4J for WS-Security processing

**Testing**
- Testcontainers BOM for integration testing
- WireMock for HTTP service simulation

**Security & Supply Chain**
- GraalVM Native Image support via `-Pnative` profile with Paketo Buildpacks
- CycloneDX SBOM generation for application dependency transparency
- BouncyCastle for cryptographic operations

## Requirements

- Java 21+ (Java 25 recommended; build defaults to 25, use `-Pjava21` for 21)
- Spring Boot 3.x (3.5.x is managed here)
- Maven 3.9+
- Docker (optional — for native image container builds with Paketo Buildpacks)
- GraalVM (optional — for local native image builds)

## Installation

Declare this as the `<parent>` of your Firefly Framework module or application. Pin it to the latest released version (see the [GitHub releases](https://github.com/fireflyframework/fireflyframework-parent/releases)); use `<relativePath/>` so Maven resolves it from your repository rather than the local filesystem:

```xml
<parent>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-parent</artifactId>
    <version>26.05.08</version>
    <relativePath/>
</parent>
```

## Quick Start

Create a new Firefly Framework module by referencing the parent POM:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.fireflyframework</groupId>
        <artifactId>fireflyframework-parent</artifactId>
        <version>26.05.08</version>
        <relativePath/>
    </parent>

    <artifactId>my-firefly-module</artifactId>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webflux</artifactId>
        </dependency>
    </dependencies>
</project>
```

All dependency versions are inherited automatically from the parent POM.

## Managed Dependencies

### BOMs (Bill of Materials)

| BOM | Version | Description |
|-----|---------|-------------|
| Spring Boot | 3.5.10 | Core Spring Boot dependencies |
| Spring Cloud | 2025.0.1 | Spring Cloud integrations |
| Testcontainers | 1.21.4 | Container-based integration testing |
| Resilience4j | 2.3.0 | Fault tolerance patterns |
| gRPC | 1.79.0 | gRPC framework |
| OpenTelemetry | 1.59.0 | Observability and tracing |
| AWS SDK v2 | 2.41.24 | Amazon Web Services |
| Spring Cloud AWS | 3.4.2 | Spring Cloud AWS integrations |
| Spring Cloud Azure | 5.24.1 | Azure service integrations |
| Spring Cloud GCP | 6.5.4 | Google Cloud integrations |

### Individual Dependencies

| Dependency | Version | Description |
|------------|---------|-------------|
| PostgreSQL JDBC | 42.7.9 | PostgreSQL JDBC driver |
| R2DBC PostgreSQL | 1.1.1.RELEASE | PostgreSQL reactive driver |
| R2DBC MySQL | 1.3.1 | MySQL reactive driver |
| Flyway | 11.20.3 | Database migration tool |
| SpringDoc OpenAPI | 2.8.15 | API documentation (WebFlux) |
| OpenAPI Generator | 7.19.0 | Code generation from API specs |
| Swagger Annotations | 2.2.42 | OpenAPI annotations |
| MapStruct | 1.6.3 | Bean mapping code generation |
| Lombok | 1.18.42 | Boilerplate code reduction |
| Protobuf | 4.33.5 | Protocol Buffers serialization |
| Avro | 1.12.1 | Apache Avro serialization |
| Logstash Logback Encoder | 8.1 | Structured JSON logging |
| Janino | 3.1.12 | Logback conditional expressions |
| Reflections | 0.10.2 | Runtime class scanning |
| Apache CXF | 4.1.4 | SOAP/WS framework |
| JAX-WS RT | 4.0.3 | JAX-WS runtime |
| WSS4J | 3.0.2 | WS-Security implementation |
| WSDL4J | 1.6.3 | WSDL processing |
| JAXB XJC | 4.0.6 | XML binding code generation |
| BouncyCastle | 1.77 | Cryptographic library |
| ThreeTen-Extra | 1.7.2 | Additional date/time classes |
| Commons Math3 | 3.6.1 | Mathematical functions |
| Amazon Kinesis Client | 2.5.1 | Kinesis stream processing |
| WireMock | 3.13.2 | HTTP mock server for testing |

## Managed Plugins

All plugins are declared in `<pluginManagement>` so child modules inherit versions and configuration without activating them by default.

| Plugin | Version | Description |
|--------|---------|-------------|
| spring-boot-maven-plugin | 3.5.10 | Spring Boot packaging (Lombok excluded) |
| maven-compiler-plugin | 3.15.0 | Java compilation with annotation processors |
| maven-deploy-plugin | 3.1.4 | Artifact deployment |
| openapi-generator-maven-plugin | 7.19.0 | OpenAPI code generation |
| maven-source-plugin | 3.4.0 | Source JAR generation |
| maven-javadoc-plugin | 3.12.0 | Javadoc JAR generation |
| maven-surefire-plugin | 3.5.4 | Unit test execution |
| maven-failsafe-plugin | 3.5.4 | Integration test execution |
| maven-enforcer-plugin | 3.6.2 | Build rule enforcement (JDK 21+) |
| cyclonedx-maven-plugin | 2.9.1 | CycloneDX SBOM generation |

### Compiler Configuration

The maven-compiler-plugin is pre-configured with:
- Annotation processors: Lombok, MapStruct, Lombok-MapStruct binding, Spring Boot Configuration Processor
- Compiler arguments: `-parameters` (method parameter names at runtime), `-proc:full` (full annotation processing)

## Configuration

Key properties that can be overridden in child modules:

```xml
<properties>
    <!-- Java version (default: 25) -->
    <java.version>25</java.version>

    <!-- Base package for code generation -->
    <base.package>org.fireflyframework</base.package>

    <!-- OpenAPI generation settings -->
    <openapi.skip.validate>false</openapi.skip.validate>
    <openapi.use.tags>true</openapi.use.tags>
    <openapi.use.optional>true</openapi.use.optional>
    <openapi.date.library>java8</openapi.date.library>
</properties>
```

## Build Profiles

| Profile | Activation | Description |
|---------|-----------|-------------|
| `java21` | `-Pjava21` | Sets `java.version` to 21 for backward compatibility |
| `native` | `-Pnative` | Enables Spring AOT processing and GraalVM native image builds with Paketo Buildpacks |
| `release` | `-Prelease` | Generates source and Javadoc JARs |
| `maven-central` | `-Pmaven-central` | GPG signing, POM flattening, and Maven Central publishing |

## Native Image Builds

The `native` profile provides infrastructure for building GraalVM native images. It configures two plugins via `pluginManagement`:

- **spring-boot-maven-plugin** with `process-aot` goal for Spring Ahead-of-Time processing
- **native-maven-plugin** (GraalVM 0.11.4) with `build` goal for native compilation
- **Paketo Buildpacks** (`paketobuildpacks/builder-jammy-tiny`) for containerized native image builds

### Enabling Native Builds in a Child Module

Child modules opt in by declaring both plugins (version and configuration are inherited):

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-maven-plugin</artifactId>
        </plugin>
        <plugin>
            <groupId>org.graalvm.buildtools</groupId>
            <artifactId>native-maven-plugin</artifactId>
        </plugin>
    </plugins>
</build>
```

### Building

```bash
# Local GraalVM native executable (requires GraalVM installed)
mvn -Pnative package

# Containerized native image via Paketo Buildpacks (requires Docker)
mvn -Pnative spring-boot:build-image
```

Paketo Buildpacks automatically generate container-level SBOMs (Syft, SPDX, CycloneDX) embedded in the built image, covering OS packages, JDK, and buildpack-installed components.

## SBOM Generation

The parent POM provides managed configuration for the [CycloneDX Maven Plugin](https://github.com/CycloneDX/cyclonedx-maven-plugin), generating application-level Software Bill of Materials (SBOM) in CycloneDX JSON format.

### Enabling SBOM in a Child Module

Declare the plugin (version and configuration are inherited):

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.cyclonedx</groupId>
            <artifactId>cyclonedx-maven-plugin</artifactId>
        </plugin>
    </plugins>
</build>
```

This generates `application.cdx.json` at `META-INF/sbom/` inside the built JAR, containing all direct and transitive Maven dependencies with versions, licenses, and hashes.

### Spring Boot Actuator Integration

Spring Boot 3.3+ automatically detects the SBOM at `META-INF/sbom/application.cdx.json` and exposes it via the actuator:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,sbom
```

The SBOM is then available at `GET /actuator/sbom/application`.

### Full Supply-Chain Coverage

| Layer | Tool | Coverage |
|-------|------|----------|
| Application | CycloneDX Maven Plugin | Maven dependencies, transitive deps, licenses |
| Container | Paketo Buildpacks | OS packages, JDK runtime, buildpack components |

## Documentation

- [Firefly Framework on GitHub](https://github.com/fireflyframework) — the full module catalog and sibling repositories that inherit this parent
- [AOT / Native Image Design](docs/plans/2026-02-19-aot-native-image-design.md) — design notes for the `native` profile in this repo
- Release versions and changelogs: [GitHub releases](https://github.com/fireflyframework/fireflyframework-parent/releases)

## Contributing

Contributions are welcome. Please read the [CONTRIBUTING.md](CONTRIBUTING.md) guide for details on our code of conduct, development process, and how to submit pull requests.

## License

Copyright 2024-2026 Firefly Software Foundation.

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for details.

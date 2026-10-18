# Firefly Framework - Kernel

[![CI](https://github.com/fireflyframework/fireflyframework-kernel/actions/workflows/ci.yml/badge.svg)](https://github.com/fireflyframework/fireflyframework-kernel/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21%2B-orange.svg)](https://openjdk.org)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-green.svg)](https://spring.io/projects/spring-boot)

> The zero-dependency foundation of the Firefly Framework: a unified exception hierarchy and shared abstractions that every other module builds on.

---

## Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Requirements](#requirements)
- [Installation](#installation)
- [Quick Start](#quick-start)
- [Configuration](#configuration)
- [Documentation](#documentation)
- [Contributing](#contributing)
- [License](#license)

## Overview

`fireflyframework-kernel` is the foundational module of the Firefly Framework. It contains the
unified exception hierarchy, shared interfaces, and common abstractions that all other framework
modules depend on. It sits at the very root of the dependency graph and has **zero internal
framework dependencies** — its only runtime dependency is the [SLF4J](https://www.slf4j.org/) API.
This deliberate minimalism keeps the kernel safe to depend on from anywhere without creating
dependency cycles or pulling in transitive weight.

The problem the kernel solves is **consistent error handling across a large, modular framework**.
Without a shared root, every module would invent its own exception types, forcing callers to catch
an unpredictable set of unrelated exceptions. The kernel establishes a single base type,
`FireflyException`, so application code can catch all framework-level errors with one
`catch (FireflyException e)` block while module-specific subclasses still carry rich, typed context.

Because `FireflyException extends RuntimeException`, adopting the kernel is fully backwards
compatible — existing `catch (RuntimeException)` blocks continue to work unchanged. Every framework
module (cache, EDA, CQRS, event sourcing, orchestration, IDP, ECM, the rule engine, the agentic
bridge, the web/client layers, and the Spring Boot starters) extends these kernel types. For example,
`fireflyframework-cache` defines `CacheException extends FireflyInfrastructureException`, and
`fireflyframework-agentic-bridge` defines `AgenticBridgeException extends FireflyInfrastructureException` —
so an outer layer can catch `FireflyInfrastructureException` to handle any backing-service failure
uniformly, regardless of which adapter raised it.

### Exception hierarchy

```
java.lang.RuntimeException
└── FireflyException                    // base for ALL framework errors; carries errorCode + context
    ├── FireflyInfrastructureException  // database, cache, messaging, networking failures
    └── FireflySecurityException        // authentication & authorization failures
```

## Features

- **Single root exception** — `FireflyException` is the common ancestor for every framework error,
  enabling one-catch error handling across all modules.
- **Typed error codes** — every exception can carry a machine-readable `errorCode` (`getErrorCode()`)
  for stable, log/alert/metric-friendly categorization independent of the human-readable message.
- **Structured context** — an immutable `Map<String, Object>` of diagnostic context
  (`getContext()`) travels with the exception; the map is defensively copied and never `null`
  (an empty, immutable map is used when no context is supplied).
- **Specialized base types** — `FireflyInfrastructureException` for backing-service failures
  (database, cache, messaging, networking) and `FireflySecurityException` for authentication and
  authorization errors, so cross-cutting layers can react to broad categories without coupling to
  concrete modules.
- **Backwards compatible** — all kernel exceptions are unchecked (`RuntimeException`), so they
  compose cleanly with reactive pipelines (Project Reactor) and existing `RuntimeException` handlers.
- **Zero internal dependencies** — depends only on the SLF4J API, making it safe to place at the
  bottom of any module's dependency stack without risk of cycles.

## Requirements

- Java 21+ (Java 25 recommended)
- Spring Boot 3.x (the kernel itself is framework-agnostic; the BOM aligns it with the Spring Boot 3.x line)
- Maven 3.9+

> The kernel has no runtime infrastructure requirements (no database, broker, or cache) — it is a
> pure-Java library.

## Installation

Add the dependency using the `org.fireflyframework` group id. The version is managed by the Firefly
Framework BOM / parent POM, so you normally omit `<version>`:

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-kernel</artifactId>
    <!-- version managed by the Firefly Framework BOM / parent -->
</dependency>
```

If your project inherits the Firefly parent or imports the BOM, the version is supplied for you:

```xml
<!-- Option A: inherit the parent -->
<parent>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-parent</artifactId>
    <version>${firefly.version}</version>
</parent>

<!-- Option B: import the BOM -->
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.fireflyframework</groupId>
            <artifactId>fireflyframework-bom</artifactId>
            <version>${firefly.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

In practice you rarely add the kernel directly — it arrives transitively with almost any other
Firefly module. Declare it explicitly only when you want to throw or catch kernel exception types in
a module that does not already depend on another Firefly artifact.

## Quick Start

Throw a kernel exception with an error code and structured diagnostic context:

```java
import org.fireflyframework.kernel.exception.FireflyException;
import org.fireflyframework.kernel.exception.FireflyInfrastructureException;

import java.util.Map;

// A plain framework error with a stable error code
throw new FireflyException("Account not found", "ACCOUNT_NOT_FOUND");

// An infrastructure failure with cause + structured context
throw new FireflyInfrastructureException(
        "Cache write failed",
        "CACHE_WRITE_FAILED",
        originalCause);
```

Catch broadly at a framework boundary, then read the typed metadata:

```java
try {
    accountService.debit(accountId, amount);
} catch (FireflyException ex) {
    log.error("Firefly error [{}]: {} context={}",
            ex.getErrorCode(),   // stable, machine-readable code (may be null)
            ex.getMessage(),
            ex.getContext());    // immutable Map<String, Object>, never null
    throw ex;
}
```

Define a module-specific exception by extending the right base type — exactly how the framework's own
modules do it:

```java
import org.fireflyframework.kernel.exception.FireflyInfrastructureException;

// Backing-service failure → extend FireflyInfrastructureException
public class CacheException extends FireflyInfrastructureException {
    public CacheException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

```java
import org.fireflyframework.kernel.exception.FireflySecurityException;

// Auth/authorization failure → extend FireflySecurityException
public class TokenExpiredException extends FireflySecurityException {
    public TokenExpiredException(String message) {
        super(message, "TOKEN_EXPIRED");
    }
}
```

Because these are unchecked exceptions, they propagate naturally through reactive chains:

```java
return cache.get(key)
        .switchIfEmpty(Mono.error(
                new CacheException("Cache miss for key=" + key, null)));
```

## Configuration

This module has **no configuration properties**. It exposes no `@ConfigurationProperties` classes,
no Spring auto-configuration, and no `META-INF/spring/...AutoConfiguration.imports` — it is a plain
library of types, not an auto-configured component. There is nothing to set in `application.yml`;
simply put it on the classpath (directly or transitively) and use the exception types.

## Documentation

- Framework documentation hub and module catalog: [github.com/fireflyframework](https://github.com/fireflyframework)
- Source and issues: [fireflyframework-kernel](https://github.com/fireflyframework/fireflyframework-kernel)

The public API is intentionally small; the Javadoc on `FireflyException`,
`FireflyInfrastructureException`, and `FireflySecurityException` is the authoritative reference for
the available constructors and accessors.

## Contributing

Contributions are welcome. Please read the [CONTRIBUTING.md](CONTRIBUTING.md) guide for details on our code of conduct, development process, and how to submit pull requests.

## License

Copyright 2024-2026 Firefly Software Foundation.

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for details.

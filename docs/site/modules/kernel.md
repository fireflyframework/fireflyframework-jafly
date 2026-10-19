![Jafly. Kernel](../assets/modules/kernel.svg)

# Kernel

Share a consistent exception vocabulary across application, domain, and infrastructure boundaries.

Choose FireflyBusinessException for a domain rejection and FireflyInfrastructureException for a technical failure. Preserve the error code when translating exceptions at a transport boundary. This module has no internal Jafly dependency.

| Maven artifact | Packaging | Source project |
| --- | --- | --- |
| `org.fireflyframework:fireflyframework-kernel` | `jar` | [modules/kernel](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/kernel) |

Part of [Team-wide architecture](index.md#team-wide-architecture).

## In the onboarding service

A rejected application and an unavailable verification partner need different error handling. Use the business and infrastructure exception types to preserve that distinction through the service boundary.

## Add the component

[Installation and version availability](../installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-kernel</artifactId>
    <version>26.10.20</version>
</dependency>
```

## Quick start

The Java examples in this section are API fragments for application code. Use the stated imports and supply the application-owned collaborators described alongside them.

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

Define a module-specific exception by extending the right base type. Exactly how the framework's own
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

## Configuration and wiring

This module declares no `@ConfigurationProperties` class of its own. Use the constructor/port contracts above and the configuration of the host or related core module.

## Public API and extension points

| Type | Contract and responsibility |
| --- | --- |
| [`FireflyException`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/kernel/src/main/java/org/fireflyframework/kernel/exception/FireflyException.java) | Base exception for all Firefly Framework errors. |
| [`FireflyInfrastructureException`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/kernel/src/main/java/org/fireflyframework/kernel/exception/FireflyInfrastructureException.java) | Base exception for infrastructure failures (database, cache, messaging, networking). |
| [`FireflySecurityException`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/kernel/src/main/java/org/fireflyframework/kernel/exception/FireflySecurityException.java) | Base exception for all security and authorization related errors across the framework. |

## Build and test

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-kernel -am verify
./mvnw -pl :fireflyframework-kernel -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

This subproject has no authored `src/test/java` suite. Its Maven build still validates the configured compilation/generation and any reactor prerequisites. Use the consuming component tests to validate integration behavior.

## API reference excerpt

Contract excerpt from [FireflyException.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/kernel/src/main/java/org/fireflyframework/kernel/exception/FireflyException.java). This fragment needs the full type and its imports.

```java
public class FireflyException extends RuntimeException {
    private final String errorCode;
    private final Map<String, Object> context;
    public FireflyException(String message) {
        this(message, null, null, null);
    }
    public FireflyException(Throwable cause) {
        this(cause != null ? cause.getMessage() : null, null, null, cause);
    }
    public FireflyException(String message, Throwable cause) {
        this(message, null, null, cause);
    }
    public FireflyException(String message, String errorCode) {
        this(message, errorCode, null, null);
    }
    public FireflyException(String message, String errorCode, Throwable cause) {
        this(message, errorCode, null, cause);
    }
    public FireflyException(String message, String errorCode, Map<String, Object> context, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.context = context != null ? Map.copyOf(context) : Collections.emptyMap();
    }
    public String getErrorCode() {
```

## Detailed reference

### Overview

`fireflyframework-kernel` is the foundational module of the Firefly Framework. It contains the
unified exception hierarchy, shared interfaces, and common abstractions that all other framework
modules depend on. It sits at the very root of the dependency graph and has zero internal
framework dependencies. Its only runtime dependency is the [SLF4J](https://www.slf4j.org/) API.
This deliberate minimalism keeps the kernel safe to depend on from anywhere without creating
dependency cycles or pulling in transitive weight.

The problem the kernel solves is consistent error handling across a large, modular framework.
Without a shared root, every module would invent its own exception types, forcing callers to catch
an unpredictable set of unrelated exceptions. The kernel establishes a single base type,
`FireflyException`, so application code can catch all framework-level errors with one
`catch (FireflyException e)` block while module-specific subclasses still carry rich, typed context.

Because `FireflyException extends RuntimeException`, adopting the kernel is fully backwards
compatible. Existing `catch (RuntimeException)` blocks continue to work unchanged. Every framework
module (cache, EDA, CQRS, event sourcing, orchestration, IDP, ECM, the rule engine, the agentic
bridge, the web/client layers, and the Spring Boot starters) extends these kernel types. For example,
`fireflyframework-cache` defines `CacheException extends FireflyInfrastructureException`, and
`fireflyframework-agentic-bridge` defines `AgenticBridgeException extends FireflyInfrastructureException` :
so an outer layer can catch `FireflyInfrastructureException` to handle any backing-service failure
uniformly, regardless of which adapter raised it.

#### Exception hierarchy

```
java.lang.RuntimeException
└── FireflyException                    // base for ALL framework errors; carries errorCode + context
    ├── FireflyInfrastructureException  // database, cache, messaging, networking failures
    └── FireflySecurityException        // authentication & authorization failures
```

### Features

- Single root exception. `FireflyException` is the common ancestor for every framework error,
  enabling one-catch error handling across all modules.
- Typed error codes. every exception can carry a machine-readable `errorCode` (`getErrorCode()`)
  for stable, log/alert/metric-friendly categorization independent of the human-readable message.
- Structured context. an immutable `Map<String, Object>` of diagnostic context
  (`getContext()`) travels with the exception; the map is defensively copied and never `null`
  (an empty, immutable map is used when no context is supplied).
- Specialized base types. `FireflyInfrastructureException` for backing-service failures
  (database, cache, messaging, networking) and `FireflySecurityException` for authentication and
  authorization errors, so cross-cutting layers can react to broad categories without coupling to
  concrete modules.
- Backwards compatible. all kernel exceptions are unchecked (`RuntimeException`), so they
  compose cleanly with reactive pipelines (Project Reactor) and existing `RuntimeException` handlers.
- Zero internal dependencies. depends only on the SLF4J API, making it safe to place at the
  bottom of any module's dependency stack without risk of cycles.

### Configuration

This module has no configuration properties. It exposes no `@ConfigurationProperties` classes,
no Spring auto-configuration, and no `META-INF/spring/...AutoConfiguration.imports`. It is a plain
library of types, not an auto-configured component. There is nothing to set in `application.yml`;
simply put it on the classpath (directly or transitively) and use the exception types.

## Continue the application

Use the [Web guide](web.md) for the next part of this integration. The runnable submission/query service is in [Getting started](../getting-started.md).

## Source and further reading

- [Maven project and dependency declarations](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/kernel/pom.xml).
- [Production source](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/kernel/src/main/java).
- [Module catalog](index.md) and [getting started](../getting-started.md).
- [Maven platform](../maven.md) and [architecture](../architecture.md).

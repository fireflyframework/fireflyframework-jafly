![Jafly. Kernel](../../docs/site/assets/modules/kernel.svg)

# Kernel

Share a consistent exception vocabulary across application, domain, and infrastructure boundaries.

Choose FireflyBusinessException for a domain rejection and FireflyInfrastructureException for a technical failure. Preserve the error code when translating exceptions at a transport boundary. This module has no internal Jafly dependency.

[Module guide](../../docs/site/modules/kernel.md) · [Jafly README](../../README.md) · [Module catalog](../../docs/site/modules/index.md)

## In the onboarding service

A rejected application and an unavailable verification partner need different error handling. Use the business and infrastructure exception types to preserve that distinction through the service boundary.

## Installation

[Installation and version availability](../../docs/site/installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-kernel</artifactId>
    <version>26.10.19</version>
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

## Configuration

This module declares no `@ConfigurationProperties` class of its own. Use the constructor/port contracts above and the configuration of the host or related core module.

## API overview

| Type | Contract and responsibility |
| --- | --- |
| [`FireflyException`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/kernel/src/main/java/org/fireflyframework/kernel/exception/FireflyException.java) | Base exception for all Firefly Framework errors. |
| [`FireflyInfrastructureException`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/kernel/src/main/java/org/fireflyframework/kernel/exception/FireflyInfrastructureException.java) | Base exception for infrastructure failures (database, cache, messaging, networking). |
| [`FireflySecurityException`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/kernel/src/main/java/org/fireflyframework/kernel/exception/FireflySecurityException.java) | Base exception for all security and authorization related errors across the framework. |

## Continue the application

Use the [Web guide](../../docs/site/modules/web.md) for the next part of this integration.

## Build and validation

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-kernel -am verify
./mvnw -pl :fireflyframework-kernel -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Configuration details and source references: [module guide](../../docs/site/modules/kernel.md).

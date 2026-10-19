![Jafly. Security API](../../docs/site/assets/modules/security-api.svg)

# Security API

Define product-neutral principals, authorization decisions, driving use cases, and the Secure annotation.

Use the API model at application boundaries without coupling a domain service to Spring Security or a particular policy product. Treat non-permit decisions explicitly and supply the driving port implementation through the security-core layer.

[Module guide](../../docs/site/modules/security-api.md) · [Jafly README](../../README.md) · [Module catalog](../../docs/site/modules/index.md)

## In the onboarding service

Represent an authenticated applicant or reviewer with SecurityPrincipal and evaluate operations using the security contracts. The onboarding application record is business data; it does not establish an authenticated identity.

## Installation

[Installation and version availability](../../docs/site/installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-security-api</artifactId>
    <version>26.10.21-SNAPSHOT</version>
</dependency>
```

## Quick start

The Java examples in this section are API fragments for application code. Use the stated imports and supply the application-owned collaborators described alongside them.

`SecurityPrincipal` is built by the validation layer and read by application code:

```java
import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.fireflyframework.security.api.usecase.AuthorizeUseCase;
import org.fireflyframework.security.api.domain.Decision;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Set;

// A validated principal (normally produced by AuthenticateUseCase / the resource server)
SecurityPrincipal principal = SecurityPrincipal.builder()
        .subject("a1b2c3")
        .issuer("https://issuer.example.com")
        .tenantId("acme")
        .authorities(Set.of("ROLE_admin", "ROLE_user"))
        .scopes(Set.of("accounts:read"))
        .claims(Map.of("email", "user@example.com"))
        .build();

boolean canRead = principal.hasScope("accounts:read");        // true
String email    = principal.claim("email", String.class);     // "user@example.com"

// Authorize an action through the driving port (implemented downstream)
AuthorizeUseCase authorize = ...;
Mono<Decision> decision = authorize.authorize(principal, "read", "account:123", Map.of());
decision.filter(Decision::granted)
        .switchIfEmpty(Mono.error(new IllegalStateException("denied")));
```

For declarative method authorization, also add `fireflyframework-security-method-policy` and apply `@Secure` to a reactive method on a Spring-managed bean. `security-api` declares the annotation; it does not install the interceptor by itself:

```java
import org.fireflyframework.security.api.annotation.Secure;

@Secure(roles = {"ROLE_admin"}, scopes = {"accounts:write"}, requireAllScopes = true)
public Mono<Void> closeAccount(String accountId) {
    // reached only when the current principal holds ROLE_admin AND every declared scope
    ...
}
```

## Configuration

This module declares no `@ConfigurationProperties` class of its own. Use the constructor/port contracts above and the configuration of the host or related core module.

## API overview

| Type | Contract and responsibility |
| --- | --- |
| [`SecurityPrincipal`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-api/src/main/java/org/fireflyframework/security/api/domain/SecurityPrincipal.java) | Immutable, product-agnostic representation of an authenticated subject. |
| [`Decision`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-api/src/main/java/org/fireflyframework/security/api/domain/Decision.java) | The outcome of an authorization evaluation. |
| [`Secure`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-api/src/main/java/org/fireflyframework/security/api/annotation/Secure.java) | Defines `roles`, `scopes`, `permissions`, `requireAllRoles` operations. |
| [`AuthenticateUseCase`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-api/src/main/java/org/fireflyframework/security/api/usecase/AuthenticateUseCase.java) | Driving port: turn a presented bearer credential into a validated `SecurityPrincipal`. |
| [`AuthorizeUseCase`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-api/src/main/java/org/fireflyframework/security/api/usecase/AuthorizeUseCase.java) | Driving port: decide whether a `SecurityPrincipal` may perform `action` on `resource` within the supplied context. |

## Continue the application

Use the [Security Resource Server guide](../../docs/site/modules/security-resource-server.md) for the next part of this integration.

## Build and validation

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-security-api -am verify
./mvnw -pl :fireflyframework-security-api -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Configuration details and source references: [module guide](../../docs/site/modules/security-api.md).

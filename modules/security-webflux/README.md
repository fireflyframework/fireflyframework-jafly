# Firefly Framework - Security WebFlux Binding

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21%2B-orange.svg)](https://openjdk.org)
[![Spring Security](https://img.shields.io/badge/Spring%20Security-6.x-green.svg)](https://spring.io/projects/spring-security)

> The reactive Spring Security binding for the Firefly security platform. It adapts the framework's stack-neutral ports and domain model onto Spring Security WebFlux: a principal-carrying `Authentication`, a `ReactiveSecurityContextHolder`-backed `SecurityContextPort`, and the URL policy enforcement point that turns `authorizeExchange()` into fail-closed ABAC.

---

## Table of Contents

- [Overview](#overview)
- [Where it sits](#where-it-sits)
- [Key types](#key-types)
- [Usage](#usage)
- [Secure-by-default behavior](#secure-by-default-behavior)
- [Dependencies](#dependencies)
- [Testing](#testing)
- [License](#license)

## Overview

`fireflyframework-security-webflux` is the layer where the framework's hexagonal security model meets Spring Security. The `api`/`spi`/`core` modules below it are deliberately free of any web stack: they speak in `SecurityPrincipal`, `Decision`, `PolicyDecisionPort` and `SecurityContextPort`, never in `Authentication`, `ServerHttpSecurity` or `ReactiveSecurityContextHolder`. This module is the single place that imports `spring-security-core` / `spring-security-web` / `spring-webflux` and bridges the two worlds, so that everything beneath it — and all application code — stays Spring-Security-agnostic.

It provides three things:

- A Spring `Authentication` (`FireflyAuthenticationToken`) whose principal **is** the framework's `SecurityPrincipal`, so Spring's native `hasAuthority(...)` / `hasRole(...)` keep working while application code can still recover the rich, validated principal.
- A `SecurityContextPort` implementation (`ReactorSecurityContextAdapter`) backed by `ReactiveSecurityContextHolder`, letting services read the current principal through a port instead of importing Spring Security.
- A reactive URL policy enforcement point (`PolicyAuthorizationManager`) that plugs into `authorizeExchange().anyExchange().access(...)` and delegates every authorization decision to the `PolicyDecisionPort`, secure-by-default and fail-closed.

Consistent with the platform design (decisions D4/D10), the binding is **reactive-only** and **secure-by-default-hard**: unauthenticated requests are denied, and any error in the decision path denies rather than permits.

## Where it sits

The Firefly security platform is hexagonal; modules depend strictly inward:

```
security-api      driving ports + domain (SecurityPrincipal, Decision, Obligation, use-cases)
   ▲
security-spi      driven ports (PolicyDecisionPort, SecurityContextPort, TokenValidationPort, ...)
   ▲
security-core     framework-neutral engine (EmbeddedPolicyDecisionAdapter, authority normalization, authz engine)
   ▲
security-webflux  ◀── THIS MODULE — Spring Security WebFlux binding
   ▲
security-resource-server-starter   JWT / opaque resource server, issuer resolution, headers/CORS
   ▲
adapters          OPA / Cerbos / OpenFGA · Vault / KMS · idp providers (keycloak, cognito, azure-ad, internal-db)
```

This module depends only on `security-api` and `security-core` (which transitively pulls `security-spi`) plus Spring Security itself. It defines no provider, no transport and no token format of its own — it is pure glue. The bearer-validation pipeline, multi-tenant issuer resolution, security headers and auto-configuration live one layer up in `security-resource-server-starter` and `security-autoconfigure`, which wire the beans this module supplies into a `SecurityWebFilterChain`. This module ships no `AutoConfiguration.imports` and no runnable `application.yaml`; its types are constructed and wired by those higher layers.

## Key types

| Type | Package | Role |
| --- | --- | --- |
| `FireflyAuthenticationToken` | `...webflux.authentication` | A Spring `AbstractAuthenticationToken` whose principal is a `SecurityPrincipal`. Authorities are derived from `principal.authorities()` as `SimpleGrantedAuthority`s; `getName()` returns `principal.subject()`. Exposes `securityPrincipal()` to recover the rich principal. Marked authenticated on construction (it is only ever built from an already-validated principal). |
| `PrincipalSupport` | `...webflux.authentication` | Stateless helper that recovers a `SecurityPrincipal` from **any** Spring `Authentication`. Prefers the principal carried by a `FireflyAuthenticationToken`; falls back to a principal already set as `getPrincipal()`; otherwise builds a minimal `SecurityPrincipal` from the authentication's name and granted authorities (so third-party / JWT `Authentication`s still project cleanly). Null-safe. |
| `ReactorSecurityContextAdapter` | `...webflux.context` | Implements `SecurityContextPort` (SPI). Reads `ReactiveSecurityContextHolder.getContext()`, keeps only an authenticated `Authentication`, and projects it via `PrincipalSupport::extract`. Emits an empty `Mono` when there is no authenticated context. |
| `PolicyAuthorizationManager` | `...webflux.authz` | Implements Spring's `ReactiveAuthorizationManager<AuthorizationContext>` — the URL PEP. Denies unauthenticated requests; for authenticated requests it calls `PolicyDecisionPort.authorize(...)` with the HTTP method as the `action`, the request path as the `resource`, and a `{method, path}` context map, mapping `Decision.granted()` to an `AuthorizationDecision`. |

### Ports

- **Implements** `org.fireflyframework.security.spi.SecurityContextPort` (via `ReactorSecurityContextAdapter`).
- **Consumes** `org.fireflyframework.security.spi.PolicyDecisionPort` (injected into `PolicyAuthorizationManager`; satisfied by `security-core`'s `EmbeddedPolicyDecisionAdapter` by default, or by an OPA/Cerbos/OpenFGA adapter).
- **Projects** `org.fireflyframework.security.api.domain.SecurityPrincipal` and reads `org.fireflyframework.security.api.domain.Decision`.

## Usage

Wire the PEP into a `SecurityWebFilterChain` (normally done for you by `security-autoconfigure`):

```java
import org.fireflyframework.security.spi.PolicyDecisionPort;
import org.fireflyframework.security.webflux.authz.PolicyAuthorizationManager;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Bean
SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http, PolicyDecisionPort pdp) {
    return http
            .authorizeExchange(ex -> ex
                    .pathMatchers("/actuator/health").permitAll()
                    // every other exchange goes through the policy decision point (fail-closed)
                    .anyExchange().access(new PolicyAuthorizationManager(pdp)))
            .build();
}
```

Read the current principal from a service through the port — no Spring Security import required:

```java
import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.fireflyframework.security.spi.SecurityContextPort;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class GreetingService {

    private final SecurityContextPort securityContext;

    public GreetingService(SecurityContextPort securityContext) {
        this.securityContext = securityContext; // ReactorSecurityContextAdapter at runtime
    }

    public Mono<String> greet() {
        return securityContext.currentPrincipal()
                .map(SecurityPrincipal::subject)
                .map(subject -> "Hello, " + subject)
                .defaultIfEmpty("Hello, anonymous");
    }
}
```

When you mint a principal yourself (e.g. in a custom authentication converter), wrap it so Spring authorities and the rich principal stay in sync:

```java
SecurityPrincipal principal = SecurityPrincipal.builder()
        .subject("u1")
        .authorities(java.util.Set.of("ROLE_ADMIN"))
        .build();
Authentication auth = new FireflyAuthenticationToken(principal);
```

## Secure-by-default behavior

`PolicyAuthorizationManager.check(...)` is deny-biased at every branch:

- **No authentication / unauthenticated** → `AuthorizationDecision(false)` (the `defaultIfEmpty` and `isAuthenticated()` filter close the gap).
- **Authenticated, policy abstains** → permit. With `security-core`'s `EmbeddedPolicyDecisionAdapter` and no rules registered, ABAC is not in use and the decision defers to RBAC at the PEP; configured rules apply deny-overrides.
- **Authenticated, policy denies** → `AuthorizationDecision(false)`.
- **Policy errors / PDP unreachable** → `onErrorReturn(new AuthorizationDecision(false))` — fail-closed, never permit-on-error.

This matches design decision D4 (secure-by-default-hard): default-deny, validated bearer upstream, fail-closed everywhere.

## Dependencies

Provided by the Firefly parent/BOM (`org.fireflyframework:fireflyframework-parent`), so versions are managed for you:

- `fireflyframework-security-api` — `SecurityPrincipal`, `Decision`.
- `fireflyframework-security-core` — neutral engine, incl. `EmbeddedPolicyDecisionAdapter` (transitively brings `fireflyframework-security-spi`).
- `spring-security-core`, `spring-security-web`, `spring-webflux` — the binding surface this module exists to adapt.
- `slf4j-api`, `lombok` (provided).

It has **no** Spring Boot, provider-SDK or auto-configuration dependency; that wiring belongs to the starters above it.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-security-webflux</artifactId>
</dependency>
```

## Testing

Unit-tested with JUnit 5, AssertJ, `reactor-test` (`StepVerifier`), `spring-security-test` and `spring-test` mocks — no running server, no Spring context:

- `PolicyAuthorizationManagerTest` drives the PEP through `MockServerWebExchange` / `AuthorizationContext` and asserts all four decision branches: denies unauthenticated requests (`Mono.empty()`), permits an authenticated request when the `EmbeddedPolicyDecisionAdapter` abstains, denies when a `PolicyDecisionPort` returns `Decision.deny(...)`, and **fails closed** when the port emits an error.
- `ReactorSecurityContextAdapterTest` verifies the principal is projected out of the reactive context (seeded via `ReactiveSecurityContextHolder.withAuthentication(...)`) and that `currentPrincipal()` completes empty when no security context is present.

Run them with:

```bash
mvn -pl fireflyframework-security-webflux test
```

## License

Copyright 2024-2026 Firefly Software Foundation.

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for details.

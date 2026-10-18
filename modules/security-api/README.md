# Firefly Framework - Security API

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21%2B-orange.svg)](https://openjdk.org)

> Product-agnostic security domain model and driving ports for the Firefly hexagonal security platform — `SecurityPrincipal`, `Decision`, `BearerToken`, `SigningKey`, the `@Secure` annotation, reactive use-case interfaces, and security exceptions extending the kernel's `FireflySecurityException`. No Spring web or provider dependencies.

---

## Table of Contents

- [Overview](#overview)
- [Where it sits](#where-it-sits)
- [What it defines](#what-it-defines)
- [Design principles](#design-principles)
- [Usage](#usage)
- [Dependencies](#dependencies)
- [Testing](#testing)
- [Documentation](#documentation)
- [License](#license)

## Overview

`fireflyframework-security-api` is the innermost module of the Firefly security platform — the `api` layer of a hexagonal architecture. It declares the **stack-neutral security domain model** and the **driving ports** (inbound use-cases) that the rest of the platform implements and that application code programs against.

This module is deliberately minimal and dependency-light. It contains **no** Spring Security, no Spring web, no JOSE library, and no provider SDK. Domain types use only the JDK and Lombok; the use-case ports use Project Reactor (`Mono`). Everything downstream — token validation, JWKS, issuer resolution, policy evaluation, the resource server, the IdP adapters — depends on the types declared here, not the other way around.

Crucially, the model is **product-agnostic**: it carries no `party`, `contract`, `product`, business-role enum, or `X-Party-Id` concept. `SecurityPrincipal` exposes generic primitives (subject, issuer, optional `tenantId`, authorities, scopes, claims, attributes); products re-introduce their own domain via the extension SPIs declared in `fireflyframework-security-spi` and read it back off `claims()` / `attributes()`.

## Where it sits

The Firefly security platform is layered strictly inward-to-outward; each layer depends only on those to its left:

```
security-api  →  security-spi  →  security-core  →  security-webflux  →  resource-server-starter  →  adapters
(this module)    (driven ports)   (neutral engine)   (reactive binding)    (Spring Security wiring)    (idp / opa / vault / ...)
```

- **`security-api`** (this module) — driving ports + domain model. The vocabulary everything else speaks.
- **`security-spi`** — driven ports (outbound SPIs): `TokenValidationPort`, `IssuerRegistryPort`, `AuthorityMappingPort`, `PolicyDecisionPort`, `KeyManagementPort`, and the segregated IdP capability ports.
- **`security-core`** — framework-neutral engine: default token validators, authority normalization, the `@Secure` / default-deny authorization engine. No provider or web-stack lock-in.
- **`security-webflux`** — reactive bindings onto Spring Security (`ServerHttpSecurity`, `AuthorizationManager` / `WebFilter` PEPs, `ReactiveSecurityContextHolder`).
- **`resource-server-starter`** and the **adapters** (idp-internal-db, keycloak, cognito, azure-ad, OPA, Vault, KMS, ...) — the outermost ring, where concrete providers and Spring Security plug in.

The `api` layer never imports a vendor SDK or a web framework; providers are adapters that depend inward on `api` and `spi`.

## What it defines

### Domain model (`org.fireflyframework.security.api.domain`)

| Type | Kind | Purpose |
| --- | --- | --- |
| `SecurityPrincipal` | `record` (Lombok `@Builder(toBuilder = true)`) | Immutable, product-agnostic projection of a signature-validated authentication: `subject`, `issuer`, `tenantId`, `authorities`, `scopes`, `claims`, `authTime`, `acr`, `amr`, `attributes`. Null collections are normalized to empty immutables in the canonical constructor. Helpers: `hasAuthority`, `hasAnyAuthority`, `hasAllAuthorities`, `hasScope`, and a typed `claim(name, type)`. |
| `Decision` | `record` | Outcome of an authorization evaluation. Nested `Effect { PERMIT, DENY, INDETERMINATE }`; carries `obligations` and a `reason`. **Fail-closed** — `granted()` is `true` only for `PERMIT`. Factories: `permit()`, `permit(obligations)`, `deny(reason)`, `indeterminate(reason)`. |
| `Obligation` | `record` | A directive the policy enforcement point must honour when permitting (e.g. `type = "mask"` / `"row-filter"`) plus an `attributes` map. Factory `Obligation.of(type, attributes)`. |
| `BearerToken` | `record` | A raw bearer credential with a best-effort `TokenType { JWT, OPAQUE, UNKNOWN }` classification so the validation layer can pick a strategy. `BearerToken.of(value)` classifies by structure (a three-segment dotted token → `JWT`); `masked()` renders the value safe for logging (`abcd…ijkl`). |
| `SigningKey` | `record` | A `kid`-identified key pair for the key-management layer to sign first-party tokens and publish a JWKS. Uses only JDK crypto types (`PrivateKey` / `PublicKey`) so the SPI stays free of third-party JOSE deps. `canSign()` is `true` only when a private key is present. |
| `TrustedIssuer` | `record` | A trusted token issuer for a multi-tenant resource server: expected `issuer` (`iss`), `jwksUri`, acceptable `audiences` (`aud`), and the `tenantId` it maps to. |
| `SecurityAuditEvent` | `record` | A security audit record emitted at authentication / authorization / token / key decision points: `type`, `subject`, `tenantId`, `outcome`, `resource`, `action`, `correlationId`, `timestamp`, `attributes`. |

### Driving ports (`org.fireflyframework.security.api.usecase`)

Reactive inbound use-cases — what application code and the resource server call into:

- **`AuthenticateUseCase`** — `Mono<SecurityPrincipal> authenticate(BearerToken token)`. Turns a presented bearer credential into a validated principal; signals `TokenValidationException` on failure.
- **`AuthorizeUseCase`** — `Mono<Decision> authorize(SecurityPrincipal principal, String action, String resource, Map<String, Object> context)`. Decides whether a principal may perform an action on a resource. Implementations are fail-closed.

### Annotation (`org.fireflyframework.security.api.annotation`)

- **`@Secure`** — declarative method/class-level authorization (`@Target({METHOD, TYPE})`, `RUNTIME`). Dimensions: `roles`, `scopes`, `permissions`, and an optional SpEL `expression`. Across dimensions, **all** must pass; within a dimension, `requireAllRoles` / `requireAllScopes` / `requireAllPermissions` switch between ANY (default) and ALL. Semantics are **fail-closed** — a missing or unsatisfied principal denies. The engine that interprets it lives in `security-core`.

### Exceptions (`org.fireflyframework.security.api.exception`)

Both extend the kernel's `org.fireflyframework.kernel.exception.FireflySecurityException`, so they carry a stable error code and flow into the framework's RFC-7807 problem envelope:

- **`TokenValidationException`** (`ERROR_CODE = "SECURITY_TOKEN_INVALID"`) — a presented bearer token cannot be validated (bad signature, untrusted issuer, wrong audience, expired/not-yet-valid, revoked). Maps to HTTP 401.
- **`UnsupportedSecurityOperationException`** (`ERROR_CODE = "SECURITY_OPERATION_UNSUPPORTED"`) — thrown by a segregated capability port when the underlying provider does not implement an operation; adapters return it (as a failed `Mono`) from the `default` methods of ports they do not support, rather than faking a result.

## Design principles

This module embodies the platform's secure-by-default, product-agnostic stance:

- **Product-agnostic, always.** No `party` / `contract` / `product` / `X-Party-Id` / business-role enums. Generic primitives only; products extend through the `PrincipalAttributeContributorPort` seam declared in `security-spi`.
- **Fail-closed everywhere.** `Decision.granted()` is `true` only for `PERMIT`; `INDETERMINATE` (e.g. a policy-engine error) is treated as a denial by callers. `@Secure` denies unless its requirements are satisfied.
- **Immutable domain.** Records with defensive copies; collections are unmodifiable (`SecurityPrincipalTest` asserts an `add` throws `UnsupportedOperationException`).
- **Reactive-first.** Driving ports return `Mono`, composing end-to-end into the WebFlux pipeline.
- **No leakage.** JDK crypto types only on `SigningKey`; raw token values are never logged in full (`BearerToken.masked()`).

## Usage

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

Declarative authorization with `@Secure` (interpreted by the `security-core` engine):

```java
import org.fireflyframework.security.api.annotation.Secure;

@Secure(roles = {"ROLE_admin"}, scopes = {"accounts:write"}, requireAllScopes = true)
public Mono<Void> closeAccount(String accountId) {
    // reached only when the current principal holds ROLE_admin AND every declared scope
    ...
}
```

## Dependencies

Runtime footprint is intentionally tiny (see `pom.xml`):

| Dependency | Scope | Why |
| --- | --- | --- |
| `org.fireflyframework:fireflyframework-kernel` | compile | Foundational exception hierarchy (`FireflySecurityException`) the security exceptions extend. |
| `io.projectreactor:reactor-core` | compile | Driving ports are reactive (`Mono`). |
| `org.slf4j:slf4j-api` | compile | Logging facade. |
| `org.projectlombok:lombok` | provided | `@Builder` on `SecurityPrincipal`. |
| `junit-jupiter`, `assertj-core`, `reactor-test` | test | Unit tests. |

Version and parent are managed by `fireflyframework-parent` (CalVer `26.06.01`); consumers inheriting the parent/BOM can omit the version.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-security-api</artifactId>
</dependency>
```

## Testing

The module is covered by plain JUnit 5 + AssertJ unit tests under `src/test/java`, with no Spring context — fast, deterministic, and exercising the domain invariants directly:

- `SecurityPrincipalTest` — null collections normalize to empty immutables; authority/scope/claim helpers; immutability of the exposed collections.
- `DecisionTest` — `permit` is granted; `deny` / `indeterminate` are not; obligations carried on permit.
- `BearerTokenTest` — JWT vs opaque vs unknown classification; log masking.

Run them with:

```bash
mvn -q test
```

Behavioural contracts that depend on a running validator, policy engine, or provider are verified in the downstream modules (`security-core`, `security-webflux`, the resource-server starter) and via the per-SPI contract-test base classes in `fireflyframework-security-test`.

## Documentation

- Firefly Framework documentation hub and module catalog: [github.com/fireflyframework](https://github.com/fireflyframework)
- Downstream layers: `fireflyframework-security-spi`, `fireflyframework-security-core`, `fireflyframework-security-webflux`, `fireflyframework-security-resource-server-starter`.

## License

Copyright 2024-2026 Firefly Software Foundation.

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for details.

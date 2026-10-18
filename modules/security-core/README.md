# Firefly Framework - Security Core

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21%2B-orange.svg)](https://openjdk.org)

> The framework-neutral engine of the Firefly security platform: authority mapping, the `@Secure` authorization evaluator, the embedded policy decision point, introspection caching, a dev key source and principal projection — no provider SDKs, no web-stack lock-in.

---

## Table of Contents

- [Overview](#overview)
- [Where it sits](#where-it-sits)
- [Requirements](#requirements)
- [Installation](#installation)
- [What this module provides](#what-this-module-provides)
- [Secure-by-default behavior](#secure-by-default-behavior)
- [Usage](#usage)
- [Dependencies](#dependencies)
- [Testing](#testing)
- [Documentation](#documentation)
- [Contributing](#contributing)
- [License](#license)

## Overview

`fireflyframework-security-core` is the **neutral engine tier** of Firefly's hexagonal security platform. It contains the default, in-process implementations of the driven ports declared in `fireflyframework-security-spi`, plus the authorization logic that backs the `@Secure` annotation defined in `fireflyframework-security-api`.

The design constraint is strict: **the core never imports a vendor SDK and never binds to a web stack.** It depends only on `security-api`, `security-spi`, Project Reactor, SpEL (for `@Secure.expression`), Caffeine (the default cache) and SLF4J. Concrete providers (Keycloak, Cognito, Entra, OPA, Vault, KMS, Redis/R2DBC) are separate adapter modules that depend on `security-spi` only; the reactive binding (`ServerHttpSecurity` wiring, PEPs, `ReactiveSecurityContextHolder` access) lives in `fireflyframework-security-webflux`. This module is the part you can unit-test with plain JUnit and reuse from non-web modules (`starter-core`, `-domain`, `-data`) for `SecurityPrincipal` access without pulling in Spring Security web.

Every component here is **secure-by-default, hard**: a `null` principal is denied, a throwing SpEL expression denies, a policy error is `INDETERMINATE` (never permit), and the dev key source is explicitly marked not-for-production. There is no shadow mode and no fail-open switch.

## Where it sits

The platform is layered in dependency order; this module is layer 4:

```
security-api  ──→  security-spi  ──→  security-core  ──→  security-webflux  ──→  security-resource-server  ──→  adapters
(driving ports,    (driven ports,     (this module:        (reactive binding,      (JWT/opaque RFC 7662         (keycloak, cognito,
 domain model,      interfaces only)   neutral engine,      ServerHttpSecurity,      resource server,            entra, opa, vault,
 @Secure)                              default adapters)    PEPs, context)           default-deny chain)         kms, r2dbc, ...)
```

The core consumes the **domain model** from `security-api` (`SecurityPrincipal`, `Decision`, `BearerToken`, `SigningKey`, `SecurityAuditEvent`, the `@Secure` annotation) and **implements** the SPI ports from `security-spi`. Higher layers wire these defaults into the request path; adapters replace any individual default by providing their own bean for the same port.

## Requirements

- Java 21+ (Java 25 recommended)
- Maven 3.9+
- The Firefly parent / BOM (version `26.06.01`), which manages all transitive versions

No web stack, no running identity provider, and no external policy/key service are required to use the core's defaults.

## Installation

The core is normally pulled in transitively by `fireflyframework-security-webflux` or `fireflyframework-security-resource-server`. To depend on it directly (e.g. from a non-web module that only needs `SecurityPrincipal` and the authorization engine):

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-security-core</artifactId>
</dependency>
```

The version is managed by the Firefly parent/BOM. If you are not inheriting it, pin the version explicitly:

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-security-core</artifactId>
    <version>26.06.01</version>
</dependency>
```

## What this module provides

Each type below is a default implementation of a `security-spi` port, or a self-contained helper. They are plain objects with no Spring wiring — `security-autoconfigure` registers them conditionally; you can also construct them by hand.

| Type | Port implemented | Responsibility |
| --- | --- | --- |
| `ConfigurableAuthorityMapper` | `AuthorityMappingPort` | Reads roles/groups and scopes from configured dot-path claims and normalizes them into authorities. Absorbs the old `JwtClaimsRoleExtractor`. |
| `SecureAuthorizationEvaluator` | _(backs `@Secure`)_ | Evaluates a `SecureRequirement` against a `SecurityPrincipal` with fixed AND-across-dimensions semantics, real SpEL, and default-deny. |
| `EmbeddedPolicyDecisionAdapter` | `PolicyDecisionPort` | Zero-dependency in-process PDP combining `PolicyRule`s with deny-overrides; fail-closed. The default until OPA/Cerbos/OpenFGA adapters are bound. |
| `CaffeineIntrospectionCache` | `TokenIntrospectionCachePort` | In-memory cache of opaque-introspection results with per-entry TTL bounded by token expiry. |
| `InMemoryKeyManagementAdapter` | `KeyManagementPort` | Dev-default RS256 key source: generates a key pair, serves a JWKS, rotates with an overlap window. **Not for production.** |
| `PrincipalFactory` | _(projection helper)_ | Projects a validated claims map into a `SecurityPrincipal` via the `AuthorityMappingPort`; stack-neutral (JWT and opaque paths). |
| `LoggingAuditEventAdapter` | `AuditEventPort` | Emits structured audit lines to a dedicated logger; never throws, never blocks the request path. |
| `BearerTokenExtractor` | _(parsing helper)_ | Parses the `Authorization` header into a classified `BearerToken`, matching the `Bearer` scheme case-insensitively (RFC 6750). |

Supporting types: `SecureRequirement` (a framework-neutral view of `@Secure`, built from the annotation or programmatically), `AuthorityMappingProperties` (role/scope claim paths + authority prefix; defaults cover Keycloak `realm_access.roles`, Cognito `cognito:groups`, and generic `roles`/`groups`/`scope`/`scp`), `ClaimPaths` (dot-path traversal + string-set coercion), and `PolicyRule` (a `@FunctionalInterface` ABAC rule returning PERMIT / DENY / INDETERMINATE).

## Secure-by-default behavior

The engine is deliberately fail-closed at every decision point:

- **`SecureAuthorizationEvaluator`** — a `null` (unauthenticated) principal is denied. Declared dimensions (roles, scopes, permissions, expression) are **AND-ed**; within a dimension, `requireAll*` selects ALL vs ANY (ANY by default). An empty requirement permits any authenticated principal ("require authentication"). A SpEL expression that evaluates false, or **throws / is malformed**, denies.
- **`EmbeddedPolicyDecisionAdapter`** — deny-overrides: any rule DENY wins; rules registered but none permitting yields DENY; a rule that errors becomes INDETERMINATE (never PERMIT). With no rules registered it permits, deferring to RBAC at the PEP (ABAC simply not in use).
- **`InMemoryKeyManagementAdapter`** — flagged not-for-production; the autoconfigure layer fails closed at startup if no real key source resolves under prod profiles.
- **`CaffeineIntrospectionCache`** — TTL is bounded by the token's own expiry, so a revoked-by-expiry token cannot outlive its `exp`.

## Usage

The core is normally wired by `security-autoconfigure`, but every component is a constructable plain object. Direct authorization-engine usage:

```java
import org.fireflyframework.security.api.domain.Decision;
import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.fireflyframework.security.core.authority.ConfigurableAuthorityMapper;
import org.fireflyframework.security.core.authz.SecureAuthorizationEvaluator;
import org.fireflyframework.security.core.authz.SecureRequirement;
import org.fireflyframework.security.core.context.PrincipalFactory;

import java.util.List;
import java.util.Map;

// 1. Project validated claims into a generic principal, normalizing authorities/scopes.
var authorityMapper = new ConfigurableAuthorityMapper(null); // null -> sensible defaults
var principalFactory = new PrincipalFactory(authorityMapper);

Map<String, Object> claims = Map.of(
        "sub", "user-123",
        "realm_access", Map.of("roles", List.of("admin")),
        "scope", "accounts:read accounts:write");
SecurityPrincipal principal = principalFactory.fromClaims("user-123", "https://issuer.example", claims);

// 2. Evaluate a requirement (here: ALL of these roles AND a scope), fail-closed.
var evaluator = new SecureAuthorizationEvaluator();
var requirement = new SecureRequirement(
        List.of("admin"),            // roles
        List.of("accounts:write"),   // scopes
        List.of(),                   // permissions
        true, false, false,          // requireAllRoles / Scopes / Permissions
        "principal.subject != null"); // SpEL expression (denies on throw)

Decision decision = evaluator.evaluate(principal, requirement);
boolean allowed = decision.granted();
```

`SecureRequirement.from(secure)` builds the same requirement straight from a `@Secure` annotation, which is how the `security-webflux` / method-policy PEPs invoke the evaluator.

## Dependencies

Runtime dependencies are intentionally minimal and SDK-free:

- `fireflyframework-security-api` — domain model + driving ports + `@Secure`
- `fireflyframework-security-spi` — the driven-port interfaces implemented here
- `io.projectreactor:reactor-core` — `Mono`/`Flux` for the reactive ports
- `org.springframework:spring-expression` — SpEL for `@Secure.expression` (the expression engine only — **not** Spring Security or Spring web)
- `com.github.ben-manes.caffeine:caffeine` — backing store for `CaffeineIntrospectionCache`
- `org.slf4j:slf4j-api` — logging facade (audit + policy/expression denials)
- `org.projectlombok:lombok` — provided-scope, compile-time only

No `spring-security-*`, no `spring-webflux`, no provider SDK appears on this module's classpath.

## Testing

Each component ships with a focused JUnit 5 unit test (AssertJ assertions, `reactor-test` for the reactive ports) — no Spring context, no containers, fast and deterministic:

- `SecureAuthorizationEvaluatorTest` — null-principal denial, ANY-by-default vs `requireAll*` AND-semantics, cross-dimension AND, empty-requirement-permits-authenticated, SpEL evaluation, and **malformed-expression-fails-closed**.
- `ConfigurableAuthorityMapperTest` — dot-path role/scope extraction across the default Keycloak/Cognito/Entra claim shapes and prefix application.
- `EmbeddedPolicyDecisionAdapterTest` — deny-overrides combination, no-rules-permit, all-abstain-denies, and rule-error → INDETERMINATE.
- `CaffeineIntrospectionCacheTest` — put/get/evict and per-entry TTL expiry.
- `InMemoryKeyManagementAdapterTest` — active key, JWKS emission, and rotation-with-overlap.

The negative paths (forged/malformed input must deny) are first-class test cases, mirroring the platform's negative-path verification strategy. Run them with `mvn -pl fireflyframework-security-core test`.

## Documentation

- Platform design: `docs/superpowers/specs/2026-06-18-fireflyframework-security-design.md`
- Firefly Framework documentation hub and module catalog: [github.com/fireflyframework](https://github.com/fireflyframework)

## Contributing

Contributions are welcome. Please read the [CONTRIBUTING.md](CONTRIBUTING.md) guide for details on our code of conduct, development process, and how to submit pull requests.

## License

Copyright 2024-2026 Firefly Software Foundation.

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for details.

# Firefly Framework - Security SPI

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21%2B-orange.svg)](https://openjdk.org)
[![Reactor](https://img.shields.io/badge/Project%20Reactor-3.x-green.svg)](https://projectreactor.io)

> Driven ports (service-provider interfaces) for the Firefly security platform. Pure reactive contracts — no Spring web, no provider SDK. Providers implement these as adapters; the core engine programs against them.

---

## Table of Contents

- [Overview](#overview)
- [Where it sits](#where-it-sits)
- [Ports](#ports)
- [Design contract](#design-contract)
- [Requirements](#requirements)
- [Installation](#installation)
- [Usage](#usage)
- [Dependencies](#dependencies)
- [Testing](#testing)
- [Documentation](#documentation)
- [Contributing](#contributing)
- [License](#license)

## Overview

`fireflyframework-security-spi` is the **outbound (driven) side** of the Firefly security platform's hexagon. It declares the interfaces the framework *needs from the outside world* — token validation, authority mapping, policy decisions, key management, secrets, audit, caching, revocation, issuer registry, tenant resolution and principal enrichment — and **nothing else**. There are no implementations here, no auto-configuration, and no transitive dependency on Spring Security or any vendor SDK.

The module exists to enforce the [hexagonal](https://alistair.cockburn.us/hexagonal-architecture/) inversion described in the security design: the framework-neutral engine (`fireflyframework-security-core`) and its reactive binding (`fireflyframework-security-webflux`) depend only on these interfaces, while concrete behavior — Nimbus JWT decoding, OPA/Cerbos policy engines, Vault/KMS key stores, Redis caches, Keycloak/Cognito/Entra issuer metadata — lives in adapter modules that implement them. Swapping a provider is a dependency swap, not a code change.

Two principles from the platform design shape every contract in this module:

- **Secure-by-default, hard.** Validating ports (`TokenValidationPort`, `PolicyDecisionPort`, `RelationshipPort`) are **fail-closed by contract**: any error must surface as a denial or a `TokenValidationException`, never as a silent permit. The Javadoc on each port states the fail-closed expectation that adapters and contract tests enforce.
- **Product-agnostic, always.** The ports speak only in generic primitives — `subject`, `tenantId`, `Set<String>` authorities/scopes, `Map<String, Object>` claims/attributes. No `party`, `contract`, `product` or business-role enum appears. Products re-introduce their own domain through the `PrincipalAttributeContributorPort` extension seam, without the framework ever knowing about it.

## Where it sits

The platform is built strictly in dependency order; this module is layer 3.

```
security-bom              version/dependency pins (Spring Security 6.x, Nimbus, provider SDKs)
security-api              driving ports + domain model (SecurityPrincipal, Decision, BearerToken, ...)
security-spi   ◀── this   driven ports (interfaces only)
security-core             framework-neutral engine; programs against the SPI
security-webflux          reactive bindings (ServerHttpSecurity, ReactiveSecurityContextHolder → SecurityContextPort)
*-resource-server-starter JWT + opaque resource server
adapters                  concrete implementations of these ports (opa, vault, redis, keycloak, internal-db, ...)
```

`security-spi` depends **only** on `security-api` (for the shared domain model) plus `reactor-core` and `slf4j-api`. It is the single seam through which `security-core` reaches every external system, and it is the join between the two platform tiers: the IdP tier and the security tier connect through exactly one of these ports — `TokenValidationPort`.

## Ports

All ports live in `org.fireflyframework.security.spi`. Every method that performs I/O returns a reactive type (`Mono`/`Flux`); the one pure mapping port (`AuthorityMappingPort`) returns plain collections.

| Port | Signature(s) | Role | Typical adapters |
| --- | --- | --- | --- |
| `TokenValidationPort` | `Mono<SecurityPrincipal> validate(BearerToken)` | **The single seam between the IdP and security tiers.** Validates a bearer token (JWT signature/issuer/audience/expiry, or RFC 7662 introspection for opaque tokens) and projects it into a `SecurityPrincipal`. Throws `TokenValidationException` on any failure. | Nimbus JWT decoder, idp introspection bridge |
| `AuthorityMappingPort` | `Set<String> mapAuthorities(Map<String,Object>)`, `Set<String> mapScopes(Map<String,Object>)` | Normalizes provider-specific claims into framework authorities/scopes (`realm_access.roles`, `cognito:groups`, `roles`, …). Pure, synchronous. | per-issuer claim mappers |
| `PolicyDecisionPort` | `Mono<Decision> authorize(SecurityPrincipal, String action, String resource, Map<String,Object> context)` | Externalized ABAC decision point. Fail-closed: any error yields denial/indeterminate. | embedded engine, OPA, Cerbos |
| `RelationshipPort` | `Mono<Boolean> check(String subject, String relation, String object)` | Zanzibar-style ReBAC relationship check. Fail-closed. | OpenFGA, SpiceDB |
| `KeyManagementPort` | `Mono<SigningKey> activeSigningKey()`, `Flux<SigningKey> verificationKeys()`, `Mono<String> jwkSetJson()`, `Mono<Void> rotate()` | Manages first-party signing keys and the published JWKS, with rotation-with-overlap so in-flight tokens stay verifiable. | in-memory dev, Vault Transit, AWS/Azure KMS |
| `SecretsPort` | `Mono<String> getSecret(String name)` | Resolves named secrets from an external store; fail-closed at startup. | Vault, AWS Secrets Manager, Azure Key Vault |
| `AuditEventPort` | `Mono<Void> emit(SecurityAuditEvent)` | Sink for security audit events. Must never block the request path or throw. | log, JDBC, Kafka, SIEM |
| `TokenIntrospectionCachePort` | `Mono<SecurityPrincipal> get(String)`, `Mono<Void> put(String, SecurityPrincipal, Duration)`, `Mono<Void> evict(String)` | Caches opaque-token introspection results, TTL bounded by the token's `exp`. | Caffeine, Redis |
| `RevocationPort` | `Mono<Boolean> isRevoked(String tokenId)`, `Mono<Void> revoke(String tokenId, Instant expiresAt)` | Tracks revoked tokens/sessions so an unexpired, validly-signed token can still be rejected. | Redis |
| `IssuerRegistryPort` | `Mono<Boolean> isTrusted(String)`, `Mono<TrustedIssuer> findByIssuer(String)`, `Flux<TrustedIssuer> issuers()` | Registry of trusted issuers for a multi-tenant resource server; allow-lists `iss` and selects the per-issuer decoder. | static config, Keycloak/Cognito/Entra metadata |
| `TenantResolverPort` | `Mono<String> resolveTenant(SecurityPrincipal)` | Resolves the generic tenant discriminator (from issuer, a claim, or host mapping). | claim/host resolvers |
| `SecurityContextPort` | `Mono<SecurityPrincipal> currentPrincipal()` | Stack-neutral accessor for the current validated principal; empty when unauthenticated. | webflux `ReactiveSecurityContextHolder` binding |
| `PrincipalAttributeContributorPort` | `Mono<Map<String,Object>> contribute(SecurityPrincipal)` | **Product extension seam.** Enriches a validated principal with domain-specific attributes; merged into `SecurityPrincipal.attributes`. The framework contributes nothing here. | product-supplied enrichers |

These ports reference the domain model owned by `fireflyframework-security-api`: `BearerToken`, `SecurityPrincipal`, `Decision`, `SigningKey`, `TrustedIssuer` and `SecurityAuditEvent`, plus the `TokenValidationException` raised by `TokenValidationPort`.

## Design contract

- **Interfaces only.** This module defines no classes, no beans, no auto-configuration. Wiring happens in `security-core`/`security-webflux` and the starters; implementations live in adapters.
- **Reactive-first.** Every I/O method returns `Mono`/`Flux`. The lone exception is `AuthorityMappingPort`, which is a pure, in-memory claim transformation and intentionally synchronous.
- **Fail-closed.** Validating ports must treat errors as denials, never as permits — this is the heart of the secure-by-default posture.
- **No domain leakage.** Contracts carry only generic primitives (`String`, `Set<String>`, `Map<String, Object>`, `Duration`, `Instant`) and api-owned domain types. Product concepts enter only via `PrincipalAttributeContributorPort`.
- **Interface Segregation.** Ports are narrow and capability-scoped; an adapter implements only the seams it can satisfy, rather than one fat aggregate.

## Requirements

- Java 21+
- Project Reactor 3.x (`reactor-core`)
- Maven 3.9+

## Installation

Most consumers depend on `security-core`/`security-webflux` (which pull this in transitively). Adapter authors depend on it directly. Versions are managed by the Firefly parent/BOM, so the version can be omitted:

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-security-spi</artifactId>
</dependency>
```

If you are not inheriting the Firefly parent, pin the version explicitly:

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-security-spi</artifactId>
    <version>26.06.01</version>
</dependency>
```

## Usage

You implement these ports in an **adapter** module and let the platform's auto-configuration discover your bean. A minimal `PolicyDecisionPort` adapter, fail-closed by contract:

```java
package com.example.security;

import org.fireflyframework.security.api.domain.Decision;
import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.fireflyframework.security.spi.PolicyDecisionPort;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;

@Component
public class OpaPolicyDecisionAdapter implements PolicyDecisionPort {

    private final OpaClient opa; // your engine binding

    public OpaPolicyDecisionAdapter(OpaClient opa) {
        this.opa = opa;
    }

    @Override
    public Mono<Decision> authorize(SecurityPrincipal principal, String action,
                                    String resource, Map<String, Object> context) {
        return opa.evaluate(principal.subject(), action, resource, context)
                  .map(allowed -> allowed ? Decision.permit() : Decision.deny())
                  // fail-closed: any error becomes a denial, never a permit
                  .onErrorReturn(Decision.deny());
    }
}
```

Conversely, the core engine **consumes** the ports — it never sees your adapter type, only the interface:

```java
import org.fireflyframework.security.spi.TokenValidationPort;
import org.fireflyframework.security.spi.PolicyDecisionPort;

public class AuthorizationFilter {

    private final TokenValidationPort tokenValidation;
    private final PolicyDecisionPort policy;

    public AuthorizationFilter(TokenValidationPort tokenValidation, PolicyDecisionPort policy) {
        this.tokenValidation = tokenValidation;
        this.policy = policy;
    }

    public Mono<Decision> authorize(BearerToken token, String action, String resource) {
        return tokenValidation.validate(token)                          // → SecurityPrincipal (or TokenValidationException)
                .flatMap(p -> policy.authorize(p, action, resource, Map.of()));
    }
}
```

## Dependencies

Deliberately minimal — this is a contracts module:

| Dependency | Scope | Why |
| --- | --- | --- |
| `fireflyframework-security-api` | compile | shared domain model (`SecurityPrincipal`, `Decision`, `BearerToken`, `SigningKey`, `TrustedIssuer`, `SecurityAuditEvent`) and `TokenValidationException` |
| `reactor-core` | compile | `Mono`/`Flux` return types |
| `slf4j-api` | compile | logging facade for adapters; no binding shipped |
| `junit-jupiter`, `assertj-core` | test | contract guard test |

There is **no** dependency on Spring, Spring Security, Spring Web, or any provider SDK — those belong to the binding and adapter modules downstream.

## Testing

The module ships one guard test, `SpiContractTest`, that reflectively enforces the SPI's structural contract so it cannot drift:

- **`allPortsAreInterfaces`** — every port (including `AuthorityMappingPort`) is declared as an interface, never an abstract class.
- **`reactivePortMethodsReturnPublishers`** — every method on every reactive port returns a `Mono` or `Flux`. The pure mapping port (`AuthorityMappingPort`) is explicitly exempted and listed separately, documenting *why* it is allowed to be synchronous.

Behavioral conformance is not tested here — adapters are verified against the **per-SPI contract-test base classes** and **negative-path suites** (expired / wrong-aud / wrong-iss / unknown-`kid` / forged-signature-rejected / denied-policy) provided by `fireflyframework-security-test`. This module proves only that the contracts themselves stay reactive, interface-only and fail-closed by shape.

Run the guard test with:

```bash
mvn -pl fireflyframework-security-spi test
```

## Documentation

- Firefly Framework documentation hub and module catalog: [github.com/fireflyframework](https://github.com/fireflyframework)
- Security platform design: `fireflyframework-security` hexagonal architecture (api → spi → core → webflux → resource-server → adapters)

## Contributing

Contributions are welcome. Please read the [CONTRIBUTING.md](CONTRIBUTING.md) guide for details on our code of conduct, development process, and how to submit pull requests. When adding or changing a port, keep it interface-only, reactive (or document why it is not), fail-closed, and free of product-domain types — and update `SpiContractTest` accordingly.

## License

Copyright 2024-2026 Firefly Software Foundation.

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for details.

![Jafly. Security SPI](../assets/modules/security-spi.svg)

# Security SPI

Define driven ports for policy, relationships, revocation, keys, secrets, and other external security services.

Implement only the ports required by your security deployment and inject those implementations into the security engine. Keep external product types behind the adapter boundary; use the port contract for fail-closed behavior and reactive error handling.

| Maven artifact | Packaging | Source project |
| --- | --- | --- |
| `org.fireflyframework:fireflyframework-security-spi` | `jar` | [modules/security-spi](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/security-spi) |

Part of [Security and identity](index.md#security-and-identity).

## In the onboarding service

Connect the onboarding service's policy, relationship, revocation, key, or secret source through the required port. Add only the adapter contracts needed by that deployment.

## Add the component

[Installation and version availability](../installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-security-spi</artifactId>
    <version>26.10.22</version>
</dependency>
```

## Quick start

The injection examples use Lombok `@RequiredArgsConstructor`. Add its provided dependency and annotation processor as described in [Constructor injection](../maven.md#constructor-injection).

The Java examples in this section are API fragments for application code. Use the stated imports and supply the application-owned collaborators described alongside them.

You implement these ports in an adapter module and let the platform's auto-configuration discover your bean. A minimal `PolicyDecisionPort` adapter, fail-closed by contract:

```java
package com.example.security;

import lombok.RequiredArgsConstructor;
import org.fireflyframework.security.api.domain.Decision;
import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.fireflyframework.security.spi.PolicyDecisionPort;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class OpaPolicyDecisionAdapterImpl implements PolicyDecisionPort {

    private final OpaClient opa; // your engine binding



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

Conversely, the core engine consumes the ports. It never sees your adapter type, only the interface:

```java
import lombok.RequiredArgsConstructor;
import org.fireflyframework.security.spi.TokenValidationPort;
import org.fireflyframework.security.spi.PolicyDecisionPort;

@RequiredArgsConstructor
public class AuthorizationFilter {

    private final TokenValidationPort tokenValidation;
    private final PolicyDecisionPort policy;



    public Mono<Decision> authorize(BearerToken token, String action, String resource) {
        return tokenValidation.validate(token)                          // → SecurityPrincipal (or TokenValidationException)
                .flatMap(p -> policy.authorize(p, action, resource, Map.of()));
    }
}
```

## Configuration and wiring

This module declares no `@ConfigurationProperties` class of its own. Use the constructor/port contracts above and the configuration of the host or related core module.

## Public API and extension points

| Type | Contract and responsibility |
| --- | --- |
| [`PolicyDecisionPort`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-spi/src/main/java/org/fireflyframework/security/spi/PolicyDecisionPort.java) | Externalized authorization decision point (ABAC). |
| [`RelationshipPort`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-spi/src/main/java/org/fireflyframework/security/spi/RelationshipPort.java) | Relationship-based access control (ReBAC / Zanzibar) check. |
| [`SecretsPort`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-spi/src/main/java/org/fireflyframework/security/spi/SecretsPort.java) | Resolves named secrets from an external store (Vault, AWS Secrets Manager, Azure Key Vault), so credentials never live in YAML. |
| [`RevocationPort`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-spi/src/main/java/org/fireflyframework/security/spi/RevocationPort.java) | Tracks revoked tokens/sessions so even an unexpired, validly-signed token can be rejected. |
| [`KeyManagementPort`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-spi/src/main/java/org/fireflyframework/security/spi/KeyManagementPort.java) | Manages first-party token signing keys and the published JWKS. |

## Related components

| Related component | Why it belongs nearby |
| --- | --- |
| [Security API](security-api.md) | Define product-neutral principals, authorization decisions, driving use cases, and the Secure annotation. |

## Build and test

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-security-spi -am verify
./mvnw -pl :fireflyframework-security-spi -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Representative test sources:

- [SpiContractTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-spi/src/test/java/org/fireflyframework/security/spi/SpiContractTest.java).

## Test fixture

The `reactivePortMethodsReturnPublishers` test demonstrates one concrete behavior. Excerpt from [SpiContractTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-spi/src/test/java/org/fireflyframework/security/spi/SpiContractTest.java). The test class supplies its fixtures and imports.

```java
@Test
void reactivePortMethodsReturnPublishers() {
    for (Class<?> port : REACTIVE_PORTS) {
        for (Method m : port.getDeclaredMethods()) {
            Class<?> rt = m.getReturnType();
            assertThat(Mono.class.isAssignableFrom(rt) || Flux.class.isAssignableFrom(rt))
                    .as("%s.%s returns a reactive type", port.getSimpleName(), m.getName())
                    .isTrue();
        }
    }
}
```

## Detailed reference

### Where it sits

The platform is built strictly in dependency order; this module is layer 3.

```
security-bom              version/dependency pins (Spring Security.x, Nimbus, provider SDKs)
security-api              driving ports + domain model (SecurityPrincipal, Decision, BearerToken, ...)
security-spi   ◀── this   driven ports (interfaces only)
security-core             framework-neutral engine; programs against the SPI
security-webflux          reactive bindings (ServerHttpSecurity, ReactiveSecurityContextHolder → SecurityContextPort)
*-resource-server-starter JWT + opaque resource server
adapters                  concrete implementations of these ports (opa, vault, redis, keycloak, internal-db, ...)
```

`security-spi` depends only on `security-api` (for the shared domain model) plus `reactor-core` and `slf4j-api`. It is the single seam through which `security-core` reaches every external system, and it is the join between the two platform tiers: the IdP tier and the security tier connect through exactly one of these ports. `TokenValidationPort`.

### Ports

All ports live in `org.fireflyframework.security.spi`. Every method that performs I/O returns a reactive type (`Mono`/`Flux`); the one pure mapping port (`AuthorityMappingPort`) returns plain collections.

| Port | Signature(s) | Role | Typical adapters |
| --- | --- | --- | --- |
| `TokenValidationPort` | `Mono<SecurityPrincipal> validate(BearerToken)` | The single seam between the IdP and security tiers. Validates a bearer token (JWT signature/issuer/audience/expiry, or RFC 7662 introspection for opaque tokens) and projects it into a `SecurityPrincipal`. Throws `TokenValidationException` on any failure. | Nimbus JWT decoder, idp introspection bridge |
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
| `PrincipalAttributeContributorPort` | `Mono<Map<String,Object>> contribute(SecurityPrincipal)` | Product extension seam. Enriches a validated principal with domain-specific attributes; merged into `SecurityPrincipal.attributes`. The framework contributes nothing here. | product-supplied enrichers |

These ports reference the domain model owned by `fireflyframework-security-api`: `BearerToken`, `SecurityPrincipal`, `Decision`, `SigningKey`, `TrustedIssuer` and `SecurityAuditEvent`, plus the `TokenValidationException` raised by `TokenValidationPort`.

### Design contract

- Interfaces only. This module defines no classes, no beans, no auto-configuration. Wiring happens in `security-core`/`security-webflux` and the starters; implementations live in adapters.
- Reactive-first. Every I/O method returns `Mono`/`Flux`. The lone exception is `AuthorityMappingPort`, which is a pure, in-memory claim transformation and intentionally synchronous.
- Fail-closed. Validating ports must treat errors as denials, never as permits. this is the heart of the secure-by-default posture.
- No domain leakage. Contracts carry only generic primitives (`String`, `Set<String>`, `Map<String, Object>`, `Duration`, `Instant`) and api-owned domain types. Product concepts enter only via `PrincipalAttributeContributorPort`.
- Interface Segregation. Ports are narrow and capability-scoped; an adapter implements only the seams it can satisfy, rather than one fat aggregate.

### Dependencies

Deliberately minimal. This is a contracts module:

| Dependency | Scope | Why |
| --- | --- | --- |
| `fireflyframework-security-api` | compile | shared domain model (`SecurityPrincipal`, `Decision`, `BearerToken`, `SigningKey`, `TrustedIssuer`, `SecurityAuditEvent`) and `TokenValidationException` |
| `reactor-core` | compile | `Mono`/`Flux` return types |
| `slf4j-api` | compile | logging facade for adapters; no binding shipped |
| `junit-jupiter`, `assertj-core` | test | contract guard test |

There is no dependency on Spring, Spring Security, Spring Web, or any provider SDK. Those belong to the binding and adapter modules downstream.

### Testing

The module ships one guard test, `SpiContractTest`, that reflectively enforces the SPI's structural contract so it cannot drift:

- `allPortsAreInterfaces`. every port (including `AuthorityMappingPort`) is declared as an interface, never an abstract class.
- `reactivePortMethodsReturnPublishers`. every method on every reactive port returns a `Mono` or `Flux`. The pure mapping port (`AuthorityMappingPort`) is explicitly exempted and listed separately, documenting *why* it is allowed to be synchronous.

Behavioral conformance is not tested here. Adapters are verified against the per-SPI contract-test base classes and negative-path suites (expired / wrong-aud / wrong-iss / unknown-`kid` / forged-signature-rejected / denied-policy) provided by `fireflyframework-security-test`. This module proves only that the contracts themselves stay reactive, interface-only and fail-closed by shape.

Run the guard test with:

```bash
./mvnw -pl :fireflyframework-security-spi -am test
```

## Continue the application

Use the [Security Core guide](security-core.md) for the next part of this integration. The runnable submission/query service is in [Getting started](../getting-started.md).

## Source and further reading

- [Maven project and dependency declarations](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-spi/pom.xml).
- [Production source](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/security-spi/src/main/java).
- [Module catalog](index.md) and [getting started](../getting-started.md).
- [Maven platform](../maven.md) and [architecture](../architecture.md).

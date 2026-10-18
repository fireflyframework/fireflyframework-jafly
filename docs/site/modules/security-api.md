![Jafly. Security API](../assets/modules/security-api.svg)

# Security API

Define product-neutral principals, authorization decisions, driving use cases, and the Secure annotation.

Use the API model at application boundaries without coupling a domain service to Spring Security or a particular policy product. Treat non-permit decisions explicitly and supply the driving port implementation through the security-core layer.

| Maven artifact | Packaging | Source project |
| --- | --- | --- |
| `org.fireflyframework:fireflyframework-security-api` | `jar` | [modules/security-api](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/security-api) |

Part of [Security and identity](index.md#security-and-identity).

## In the onboarding service

Represent an authenticated applicant or reviewer with SecurityPrincipal and evaluate operations using the security contracts. The onboarding application record is business data; it does not establish an authenticated identity.

## Add the component

[Installation and version availability](../installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-security-api</artifactId>
    <version>26.10.17-SNAPSHOT</version>
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

## Configuration and wiring

This module declares no `@ConfigurationProperties` class of its own. Use the constructor/port contracts above and the configuration of the host or related core module.

## Public API and extension points

| Type | Contract and responsibility |
| --- | --- |
| [`SecurityPrincipal`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-api/src/main/java/org/fireflyframework/security/api/domain/SecurityPrincipal.java) | Immutable, product-agnostic representation of an authenticated subject. |
| [`Decision`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-api/src/main/java/org/fireflyframework/security/api/domain/Decision.java) | The outcome of an authorization evaluation. |
| [`Secure`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-api/src/main/java/org/fireflyframework/security/api/annotation/Secure.java) | Defines `roles`, `scopes`, `permissions`, `requireAllRoles` operations. |
| [`AuthenticateUseCase`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-api/src/main/java/org/fireflyframework/security/api/usecase/AuthenticateUseCase.java) | Driving port: turn a presented bearer credential into a validated `SecurityPrincipal`. |
| [`AuthorizeUseCase`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-api/src/main/java/org/fireflyframework/security/api/usecase/AuthorizeUseCase.java) | Driving port: decide whether a `SecurityPrincipal` may perform `action` on `resource` within the supplied context. |

## Related components

| Related component | Why it belongs nearby |
| --- | --- |
| [Kernel](kernel.md) | Share a consistent exception vocabulary across application, domain, and infrastructure boundaries. |

## Build and test

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-security-api -am verify
./mvnw -pl :fireflyframework-security-api -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Representative test sources:

- [BearerTokenTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-api/src/test/java/org/fireflyframework/security/api/domain/BearerTokenTest.java).
- [DecisionTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-api/src/test/java/org/fireflyframework/security/api/domain/DecisionTest.java).
- [SecurityPrincipalTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-api/src/test/java/org/fireflyframework/security/api/domain/SecurityPrincipalTest.java).

## Test fixture

The `scopeAndClaimHelpersWork` test demonstrates one concrete behavior. Excerpt from [SecurityPrincipalTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-api/src/test/java/org/fireflyframework/security/api/domain/SecurityPrincipalTest.java). The test class supplies its fixtures and imports.

```java
@Test
void scopeAndClaimHelpersWork() {
    SecurityPrincipal p = SecurityPrincipal.builder()
            .subject("u1")
            .scopes(Set.of("read", "write"))
            .claims(Map.of("email", "a@b.com", "ver", 2))
            .build();

    assertThat(p.hasScope("read")).isTrue();
    assertThat(p.hasScope("delete")).isFalse();
    assertThat(p.claim("email", String.class)).isEqualTo("a@b.com");
    assertThat(p.claim("ver", Integer.class)).isEqualTo(2);
    assertThat(p.claim("email", Integer.class)).isNull();
}
```

## Detailed reference

### Where it sits

The Firefly security platform is layered strictly inward-to-outward; each layer depends only on those to its left:

```
security-api  →  security-spi  →  security-core  →  security-webflux  →  resource-server-starter  →  adapters
(this module)    (driven ports)   (neutral engine)   (reactive binding)    (Spring Security wiring)    (idp / opa / vault / ...)
```

- `security-api` (this module). driving ports + domain model. The vocabulary everything else speaks.
- `security-spi`. driven ports (outbound SPIs): `TokenValidationPort`, `IssuerRegistryPort`, `AuthorityMappingPort`, `PolicyDecisionPort`, `KeyManagementPort`, and the segregated IdP capability ports.
- `security-core`. framework-neutral engine: default token validators, authority normalization, the `@Secure` / default-deny authorization engine. No provider or web-stack lock-in.
- `security-webflux`. reactive bindings onto Spring Security (`ServerHttpSecurity`, `AuthorizationManager` / `WebFilter` PEPs, `ReactiveSecurityContextHolder`).
- `resource-server-starter` and the adapters (idp-internal-db, keycloak, cognito, azure-ad, OPA, Vault, KMS, ...). the outermost ring, where concrete providers and Spring Security plug in.

The `api` layer never imports a vendor SDK or a web framework; providers are adapters that depend inward on `api` and `spi`.

### What it defines

#### Domain model (`org.fireflyframework.security.api.domain`)

| Type | Kind | Purpose |
| --- | --- | --- |
| `SecurityPrincipal` | `record` (Lombok `@Builder(toBuilder = true)`) | Immutable, product-agnostic projection of a signature-validated authentication: `subject`, `issuer`, `tenantId`, `authorities`, `scopes`, `claims`, `authTime`, `acr`, `amr`, `attributes`. Null collections are normalized to empty immutables in the canonical constructor. Helpers: `hasAuthority`, `hasAnyAuthority`, `hasAllAuthorities`, `hasScope`, and a typed `claim(name, type)`. |
| `Decision` | `record` | Outcome of an authorization evaluation. Nested `Effect { PERMIT, DENY, INDETERMINATE }`; carries `obligations` and a `reason`. Fail-closed. `granted()` is `true` only for `PERMIT`. Factories: `permit()`, `permit(obligations)`, `deny(reason)`, `indeterminate(reason)`. |
| `Obligation` | `record` | A directive the policy enforcement point must honour when permitting (e.g. `type = "mask"` / `"row-filter"`) plus an `attributes` map. Factory `Obligation.of(type, attributes)`. |
| `BearerToken` | `record` | A raw bearer credential with a best-effort `TokenType { JWT, OPAQUE, UNKNOWN }` classification so the validation layer can pick a strategy. `BearerToken.of(value)` classifies by structure (a three-segment dotted token → `JWT`); `masked()` renders the value safe for logging (`abcd…ijkl`). |
| `SigningKey` | `record` | A `kid`-identified key pair for the key-management layer to sign first-party tokens and publish a JWKS. Uses only JDK crypto types (`PrivateKey` / `PublicKey`) so the SPI stays free of third-party JOSE deps. `canSign()` is `true` only when a private key is present. |
| `TrustedIssuer` | `record` | A trusted token issuer for a multi-tenant resource server: expected `issuer` (`iss`), `jwksUri`, acceptable `audiences` (`aud`), and the `tenantId` it maps to. |
| `SecurityAuditEvent` | `record` | A security audit record emitted at authentication / authorization / token / key decision points: `type`, `subject`, `tenantId`, `outcome`, `resource`, `action`, `correlationId`, `timestamp`, `attributes`. |

#### Driving ports (`org.fireflyframework.security.api.usecase`)

Reactive inbound use-cases. What application code and the resource server call into:

- `AuthenticateUseCase`. `Mono<SecurityPrincipal> authenticate(BearerToken token)`. Turns a presented bearer credential into a validated principal; signals `TokenValidationException` on failure.
- `AuthorizeUseCase`. `Mono<Decision> authorize(SecurityPrincipal principal, String action, String resource, Map<String, Object> context)`. Decides whether a principal may perform an action on a resource. Implementations are fail-closed.

#### Annotation (`org.fireflyframework.security.api.annotation`)

- `@Secure`. declarative method/class-level authorization (`@Target({METHOD, TYPE})`, `RUNTIME`). Dimensions: `roles`, `scopes`, `permissions`, and an optional SpEL `expression`. Across dimensions, all must pass; within a dimension, `requireAllRoles` / `requireAllScopes` / `requireAllPermissions` switch between ANY (default) and ALL. Semantics are fail-closed. a missing or unsatisfied principal denies. The engine that interprets it lives in `security-core`.

#### Exceptions (`org.fireflyframework.security.api.exception`)

Both extend the kernel's `org.fireflyframework.kernel.exception.FireflySecurityException`, so they carry a stable error code and flow into the framework's RFC-7807 problem envelope:

- `TokenValidationException` (`ERROR_CODE = "SECURITY_TOKEN_INVALID"`). a presented bearer token cannot be validated (bad signature, untrusted issuer, wrong audience, expired/not-yet-valid, revoked). Maps to HTTP 401.
- `UnsupportedSecurityOperationException` (`ERROR_CODE = "SECURITY_OPERATION_UNSUPPORTED"`). thrown by a segregated capability port when the underlying provider does not implement an operation; adapters return it (as a failed `Mono`) from the `default` methods of ports they do not support, rather than faking a result.

### Design principles

This module embodies the platform's secure-by-default, product-agnostic stance:

- Product-agnostic, always. No `party` / `contract` / `product` / `X-Party-Id` / business-role enums. Generic primitives only; products extend through the `PrincipalAttributeContributorPort` seam declared in `security-spi`.
- Fail-closed everywhere. `Decision.granted()` is `true` only for `PERMIT`; `INDETERMINATE` (e.g. a policy-engine error) is treated as a denial by callers. `@Secure` denies unless its requirements are satisfied.
- Immutable domain. Records with defensive copies; collections are unmodifiable (`SecurityPrincipalTest` asserts an `add` throws `UnsupportedOperationException`).
- Reactive-first. Driving ports return `Mono`, composing end-to-end into the WebFlux pipeline.
- No leakage. JDK crypto types only on `SigningKey`; raw token values are never logged in full (`BearerToken.masked()`).

### Dependencies

Runtime footprint is intentionally tiny (see `pom.xml`):

| Dependency | Scope | Why |
| --- | --- | --- |
| `org.fireflyframework:fireflyframework-kernel` | compile | Foundational exception hierarchy (`FireflySecurityException`) the security exceptions extend. |
| `io.projectreactor:reactor-core` | compile | Driving ports are reactive (`Mono`). |
| `org.slf4j:slf4j-api` | compile | Logging facade. |
| `org.projectlombok:lombok` | provided | `@Builder` on `SecurityPrincipal`. |
| `junit-jupiter`, `assertj-core`, `reactor-test` | test | Unit tests. |

Version and parent are managed by `fireflyframework-parent` (CalVer `26.10.17-SNAPSHOT`); consumers inheriting the parent/BOM can omit the version.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-security-api</artifactId>
</dependency>
```

### Testing

The module is covered by plain JUnit 5 + AssertJ unit tests under `src/test/java`, with no Spring context. Fast, deterministic, and exercising the domain invariants directly:

- `SecurityPrincipalTest`. null collections normalize to empty immutables; authority/scope/claim helpers; immutability of the exposed collections.
- `DecisionTest`. `permit` is granted; `deny` / `indeterminate` are not; obligations carried on permit.
- `BearerTokenTest`. JWT vs opaque vs unknown classification; log masking.

Run them with:

```bash
./mvnw -pl :fireflyframework-security-api -am test
```

Behavioural contracts that depend on a running validator, policy engine, or provider are verified in the downstream modules (`security-core`, `security-webflux`, the resource-server starter) and via the per-SPI contract-test base classes in `fireflyframework-security-test`.

## Continue the application

Use the [Security Resource Server guide](security-resource-server.md) for the next part of this integration. The runnable submission/query service is in [Getting started](../getting-started.md).

## Source and further reading

- [Maven project and dependency declarations](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-api/pom.xml).
- [Production source](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/security-api/src/main/java).
- [Module catalog](index.md) and [getting started](../getting-started.md).
- [Maven platform](../maven.md) and [architecture](../architecture.md).

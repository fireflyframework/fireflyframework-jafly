![Jafly. Security WebFlux Binding](../assets/modules/security-webflux.svg)

# Security WebFlux Binding

Bind the framework principal and authorization policy to reactive Spring Security/WebFlux.

Use the principal projection and URL policy enforcement components in a reactive security chain. Configure security-core and the relevant token validation module; this binding alone is not a complete identity provider deployment.

| Maven artifact | Packaging | Source project |
| --- | --- | --- |
| `org.fireflyframework:fireflyframework-security-webflux` | `jar` | [modules/security-webflux](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/security-webflux) |

Part of [Security and identity](index.md#security-and-identity).

## In the onboarding service

Project the authenticated reviewer into the framework principal and connect URL policy decisions to WebFlux. Token signature validation is configured in the resource-server module.

## Add the component

[Installation and version availability](../installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-security-webflux</artifactId>
    <version>26.10.22</version>
</dependency>
```

## Quick start

The Java examples in this section are API fragments for application code. Use the stated imports and supply the application-owned collaborators described alongside them.

Wire the PEP into a `SecurityWebFilterChain` (normally done for you by `security-autoconfigure`):

```java
import org.fireflyframework.security.spi.PolicyDecisionPort;
import org.fireflyframework.security.webflux.authz.PolicyAuthorizationManagerImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Bean
SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http, PolicyDecisionPort pdp) {
    return http
            .authorizeExchange(ex -> ex
                    .pathMatchers("/actuator/health").permitAll()
                    // every other exchange goes through the policy decision point (fail-closed)
                    .anyExchange().access(new PolicyAuthorizationManagerImpl(pdp)))
            .build();
}
```

Read the current principal from a service through the port. No Spring Security import required:

```java
import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.fireflyframework.security.spi.SecurityContextPort;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class GreetingService {

    private final SecurityContextPort securityContext;

    public GreetingService(SecurityContextPort securityContext) {
        this.securityContext = securityContext; // ReactorSecurityContextAdapterImpl at runtime
    }

    public Mono<String> greet() {
        return securityContext.currentPrincipal()
                .map(SecurityPrincipal::subject)
                .map(subject -> "Hello, " + subject)
                .defaultIfEmpty("Hello, anonymous");
    }
}
```

When you mint a principal yourself (e.g. In a custom authentication converter), wrap it so Spring authorities and the rich principal stay in sync:

```java
SecurityPrincipal principal = SecurityPrincipal.builder()
        .subject("u1")
        .authorities(java.util.Set.of("ROLE_ADMIN"))
        .build();
Authentication auth = new FireflyAuthenticationToken(principal);
```

## Configuration and wiring

The `firefly.security.webflux.header-bridge` namespace is bound by [PrincipalHeaderBridgeProperties.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-webflux/src/main/java/org/fireflyframework/security/webflux/bridge/PrincipalHeaderBridgeProperties.java). Nested configuration is declared in the linked class. Defaults are the field initializers.

| Setting | Java type | Source default |
| --- | --- | --- |
| `firefly.security.webflux.header-bridge.enabled` | `boolean` | `false` |
| `firefly.security.webflux.header-bridge.tenant-header` | `String` | `"X-Tenant-Id"` |
| `firefly.security.webflux.header-bridge.user-header` | `String` | `"X-User-Id"` |
| `firefly.security.webflux.header-bridge.roles-header` | `String` | `"X-User-Roles"` |
| `firefly.security.webflux.header-bridge.tenant-required` | `boolean` | `true` |
| `firefly.security.webflux.header-bridge.tenant-claim-paths` | `List<String>` | `new ArrayList<>(List.of("tenant_ids"))` |

Property-based wiring conditions found in this module:

- `prefix = "firefly.security.webflux.header-bridge", name = "enabled", havingValue = "true"`. [WebFluxBridgeAutoConfiguration.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-webflux/src/main/java/org/fireflyframework/security/webflux/bridge/WebFluxBridgeAutoConfiguration.java).

## Public API and extension points

| Type | Contract and responsibility |
| --- | --- |
| [`PrincipalHeaderBridgeProperties`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-webflux/src/main/java/org/fireflyframework/security/webflux/bridge/PrincipalHeaderBridgeProperties.java) | Configuration for the principal→header bridge. |
| [`FireflyAuthenticationToken`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-webflux/src/main/java/org/fireflyframework/security/webflux/authentication/FireflyAuthenticationToken.java) | Spring Security `org.springframework.security.core.Authentication` whose principal is the framework's `SecurityPrincipal`. |
| [`PrincipalSupport`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-webflux/src/main/java/org/fireflyframework/security/webflux/authentication/PrincipalSupport.java) | Recovers a `SecurityPrincipal` from any Spring `Authentication`. |
| [`PolicyAuthorizationManagerImpl`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-webflux/src/main/java/org/fireflyframework/security/webflux/authz/PolicyAuthorizationManagerImpl.java) | URL policy enforcement point. |
| [`PrincipalHeaderBridgeWebFilterImpl`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-webflux/src/main/java/org/fireflyframework/security/webflux/bridge/PrincipalHeaderBridgeWebFilterImpl.java) | Derives trusted internal request headers from the validated token , never from the client. |

## Related components

| Related component | Why it belongs nearby |
| --- | --- |
| [Security API](security-api.md) | Define product-neutral principals, authorization decisions, driving use cases, and the Secure annotation. |
| [Security Core](security-core.md) | Evaluate the framework security model with authority mapping, policy decisions, introspection caching, and principal projection. |

## Build and test

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-security-webflux -am verify
./mvnw -pl :fireflyframework-security-webflux -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Representative test sources:

- [PolicyAuthorizationManagerTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-webflux/src/test/java/org/fireflyframework/security/webflux/authz/PolicyAuthorizationManagerTest.java).
- [PrincipalHeaderBridgeWebFilterTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-webflux/src/test/java/org/fireflyframework/security/webflux/bridge/PrincipalHeaderBridgeWebFilterTest.java).
- [ReactorSecurityContextAdapterTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-webflux/src/test/java/org/fireflyframework/security/webflux/context/ReactorSecurityContextAdapterTest.java).

## Test fixture

The `overwritesTrustedHeadersFromToken` test demonstrates one concrete behavior. Excerpt from [PrincipalHeaderBridgeWebFilterTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-webflux/src/test/java/org/fireflyframework/security/webflux/bridge/PrincipalHeaderBridgeWebFilterTest.java). The test class supplies its fixtures and imports.

```java
@Test
void overwritesTrustedHeadersFromToken() {
    SecurityPrincipal principal = SecurityPrincipal.builder()
            .subject("user-1")
            .authorities(Set.of("idp-admin"))
            .claims(Map.of("tenant_ids", List.of("tenant-a")))
            .build();
    MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/x").header("X-Tenant-Id", "evil")); // client spoof
    CapturingChain chain = new CapturingChain();

    StepVerifier.create(filter(principalPort(principal)).filter(exchange, chain)).verifyComplete();

    assertThat(chain.calls).hasValue(1);
    HttpHeaders headers = chain.captured.get().getRequest().getHeaders();
    assertThat(headers.getFirst("X-Tenant-Id")).isEqualTo("tenant-a"); // spoof overwritten
    assertThat(headers.getFirst("X-User-Id")).isEqualTo("user-1");
    assertThat(headers.getFirst("X-User-Roles")).isEqualTo("idp-admin");
}
```

## Detailed reference

### Where it sits

The Firefly security platform is hexagonal; modules depend strictly inward:

```
security-api      driving ports + domain (SecurityPrincipal, Decision, Obligation, use-cases)
   ▲
security-spi      driven ports (PolicyDecisionPort, SecurityContextPort, TokenValidationPort, ...)
   ▲
security-core     framework-neutral engine (EmbeddedPolicyDecisionAdapterImpl, authority normalization, authz engine)
   ▲
security-webflux  ◀── THIS MODULE — Spring Security WebFlux binding
   ▲
security-resource-server-starter   JWT resource server, explicit signing-key configuration, hardened headers
   ▲
adapters          OPA / Cerbos / OpenFGA · Vault / KMS · idp providers (keycloak, cognito, azure-ad, internal-db)
```

This module depends only on `security-api` and `security-core` (which transitively pulls `security-spi`) plus Spring Security itself. It defines no provider, no transport and no token format of its own. It is pure glue. The bearer-validation pipeline, multi-tenant issuer resolution, security headers and auto-configuration live one layer up in `security-resource-server-starter` and `security-autoconfigure`, which wire the beans this module supplies into a `SecurityWebFilterChain`. This module ships no `AutoConfiguration.imports` and no runnable `application.yaml`; its types are constructed and wired by those higher layers.

### Key types

| Type | Package | Role |
| --- | --- | --- |
| `FireflyAuthenticationToken` | `...webflux.authentication` | A Spring `AbstractAuthenticationToken` whose principal is a `SecurityPrincipal`. Authorities are derived from `principal.authorities()` as `SimpleGrantedAuthority`s; `getName()` returns `principal.subject()`. Exposes `securityPrincipal()` to recover the rich principal. Marked authenticated on construction (it is only ever built from an already-validated principal). |
| `PrincipalSupport` | `...webflux.authentication` | Stateless helper that recovers a `SecurityPrincipal` from any Spring `Authentication`. Prefers the principal carried by a `FireflyAuthenticationToken`; falls back to a principal already set as `getPrincipal()`; otherwise builds a minimal `SecurityPrincipal` from the authentication's name and granted authorities (so third-party / JWT `Authentication`s still project cleanly). Null-safe. |
| `ReactorSecurityContextAdapterImpl` | `...webflux.context` | Implements `SecurityContextPort` (SPI). Reads `ReactiveSecurityContextHolder.getContext()`, keeps only an authenticated `Authentication`, and projects it via `PrincipalSupport::extract`. Emits an empty `Mono` when there is no authenticated context. |
| `PolicyAuthorizationManagerImpl` | `...webflux.authz` | Implements Spring's `ReactiveAuthorizationManager<AuthorizationContext>`. the URL PEP. Denies unauthenticated requests; for authenticated requests it calls `PolicyDecisionPort.authorize(...)` with the HTTP method as the `action`, the request path as the `resource`, and a `{method, path}` context map, mapping `Decision.granted()` to an `AuthorizationDecision`. |

#### Ports

- Implements `org.fireflyframework.security.spi.SecurityContextPort` (via `ReactorSecurityContextAdapterImpl`).
- Consumes `org.fireflyframework.security.spi.PolicyDecisionPort` (injected into `PolicyAuthorizationManagerImpl`; satisfied by `security-core`'s `EmbeddedPolicyDecisionAdapterImpl` by default, or by an OPA/Cerbos/OpenFGA adapter).
- Projects `org.fireflyframework.security.api.domain.SecurityPrincipal` and reads `org.fireflyframework.security.api.domain.Decision`.

### Secure-by-default behavior

`PolicyAuthorizationManagerImpl.check(...)` is deny-biased at every branch:

- No authentication / unauthenticated → `AuthorizationDecision(false)` (the `defaultIfEmpty` and `isAuthenticated()` filter close the gap).
- Authenticated, policy abstains → permit. With `security-core`'s `EmbeddedPolicyDecisionAdapterImpl` and no rules registered, ABAC is not in use and the decision defers to RBAC at the PEP; configured rules apply deny-overrides.
- Authenticated, policy denies → `AuthorizationDecision(false)`.
- Policy errors / PDP unreachable → `onErrorReturn(new AuthorizationDecision(false))`. fail-closed, never permit-on-error.

This matches design decision D4 (secure-by-default-hard): default-deny, validated bearer upstream, fail-closed everywhere.

### Dependencies

Provided by the Firefly parent/BOM (`org.fireflyframework:fireflyframework-parent`), so versions are managed for you:

- `fireflyframework-security-api`. `SecurityPrincipal`, `Decision`.
- `fireflyframework-security-core`. neutral engine, incl. `EmbeddedPolicyDecisionAdapterImpl` (transitively brings `fireflyframework-security-spi`).
- `spring-security-core`, `spring-security-web`, `spring-webflux`. the binding surface this module exists to adapt.
- `slf4j-api`, `lombok` (provided).

It has no Spring Boot, provider-SDK or auto-configuration dependency; that wiring belongs to the starters above it.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-security-webflux</artifactId>
</dependency>
```

### Testing

Unit-tested with JUnit 5, AssertJ, `reactor-test` (`StepVerifier`), `spring-security-test` and `spring-test` mocks. No running server, no Spring context:

- `PolicyAuthorizationManagerTest` drives the PEP through `MockServerWebExchange` / `AuthorizationContext` and asserts all four decision branches: denies unauthenticated requests (`Mono.empty()`), permits an authenticated request when the `EmbeddedPolicyDecisionAdapterImpl` abstains, denies when a `PolicyDecisionPort` returns `Decision.deny(...)`, and fails closed when the port emits an error.
- `ReactorSecurityContextAdapterTest` verifies the principal is projected out of the reactive context (seeded via `ReactiveSecurityContextHolder.withAuthentication(...)`) and that `currentPrincipal()` completes empty when no security context is present.

Run them with:

```bash
./mvnw -pl :fireflyframework-security-webflux -am test
```

## Continue the application

Use the [Security Resource Server guide](security-resource-server.md) for the next part of this integration. The runnable submission/query service is in [Getting started](../getting-started.md).

## Source and further reading

- [Maven project and dependency declarations](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-webflux/pom.xml).
- [Production source](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/security-webflux/src/main/java).
- [org.springframework.boot.autoconfigure.AutoConfiguration.imports](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-webflux/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports).
- [Module catalog](index.md) and [getting started](../getting-started.md).
- [Maven platform](../maven.md) and [architecture](../architecture.md).

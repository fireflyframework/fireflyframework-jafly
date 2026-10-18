![Jafly. Security Resource Server](../assets/modules/security-resource-server.svg)

# Security Resource Server

Validate signed JWT bearer tokens in a reactive resource server with hardened default security behavior.

Configure the issuer and signing-key source for the JWT authority and review the permitted routes. Combine with method-policy when application methods require Secure evaluation as well as URL authentication.

| Maven artifact | Packaging | Source project |
| --- | --- | --- |
| `org.fireflyframework:fireflyframework-security-resource-server` | `jar` | [modules/security-resource-server](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/security-resource-server) |

Part of [Security and identity](index.md#security-and-identity).

## In the onboarding service

Protect onboarding HTTP routes with JWTs from the configured identity authority. Set the external JWKS endpoint and issuer, then define which operations the applicant or reviewer may perform.

## Add the component

[Installation and version availability](../installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-security-resource-server</artifactId>
    <version>26.10.17</version>
</dependency>
```

## Quick start

The Java examples in this section are API fragments for application code. Use the stated imports and supply the application-owned collaborators described alongside them.

This binding configures JWT validation. Opaque-token introspection ports exist elsewhere in the security APIs, but this module does not auto-configure an opaque-token resource-server chain.

Set `OIDC_ISSUER` to the expected token issuer and `OIDC_JWK_SET_URI` to that provider's JWKS endpoint. For an external identity provider, configure both: `issuer` validates the `iss` claim; it does not discover signing keys. Without `jwk-set-uri`, the decoder uses the configured `KeyManagementPort` signing key instead.

With the module on the classpath, the resource server is active with zero code. Every route is locked down. Mark public routes explicitly and read the validated principal directly:

```yaml
firefly:
  security:
    resource-server:
      permit-matchers:
        - /actuator/health
        - /public/**
      issuer: ${OIDC_ISSUER}
      jwk-set-uri: ${OIDC_JWK_SET_URI}
      audiences:
        - firefly-api
```

```java
import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
class MeController {

    @GetMapping("/api/me")
    Mono<String> me(@AuthenticationPrincipal SecurityPrincipal principal) {
        return Mono.just(principal.subject());
    }
}
```

Add ABAC/ReBAC decisions by contributing one or more `PolicyRule` beans; they are collected by the default `EmbeddedPolicyDecisionAdapter` and consulted on every non-permitted exchange, fail-closed:

```java
@Bean
PolicyRule denyReports() {
    return (principal, action, resource, context) ->
            resource.startsWith("/api/reports") && !principal.scopes().contains("reports.read")
                    ? Mono.just(Decision.deny("reports.read scope required"))
                    : Mono.just(Decision.permit());
}
```

## Configuration and wiring

The `firefly.security.resource-server` namespace is bound by [ResourceServerProperties.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-resource-server/src/main/java/org/fireflyframework/security/rs/ResourceServerProperties.java). Nested configuration is declared in the linked class. Defaults are the field initializers.

| Setting | Java type | Source default |
| --- | --- | --- |
| `firefly.security.resource-server.permit-matchers` | `List<String>` | `new ArrayList<>()` |
| `firefly.security.resource-server.issuer` | `String` | `not initialized` |
| `firefly.security.resource-server.audiences` | `List<String>` | `new ArrayList<>()` |
| `firefly.security.resource-server.authority-prefix` | `String` | `""` |
| `firefly.security.resource-server.role-claim-paths` | `List<String>` | `new ArrayList<>()` |
| `firefly.security.resource-server.scope-claim-paths` | `List<String>` | `new ArrayList<>()` |
| `firefly.security.resource-server.jwk-set-uri` | `String` | `not initialized` |
| `firefly.security.resource-server.signature-algorithms` | `List<String>` | `new ArrayList<>(List.of("RS256", "PS256", "ES256"))` |

Property-based wiring conditions found in this module:

- `prefix = "firefly.security.resource-server", name = "enabled", matchIfMissing = true`. [ResourceServerAutoConfiguration.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-resource-server/src/main/java/org/fireflyframework/security/rs/ResourceServerAutoConfiguration.java).

## Public API and extension points

| Type | Contract and responsibility |
| --- | --- |
| [`ResourceServerAutoConfiguration`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-resource-server/src/main/java/org/fireflyframework/security/rs/ResourceServerAutoConfiguration.java) | Auto-configures a secure-by-default reactive OAuth2 resource server. |
| [`ResourceServerProperties`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-resource-server/src/main/java/org/fireflyframework/security/rs/ResourceServerProperties.java) | Configuration for the secure-by-default resource server. |
| [`JwtToFireflyPrincipalConverter`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-resource-server/src/main/java/org/fireflyframework/security/rs/JwtToFireflyPrincipalConverter.java) | Converts a signature-validated `Jwt` into a `FireflyAuthenticationToken` whose principal is a `SecurityPrincipal` with normalized authorities/scopes. |

## Related components

| Related component | Why it belongs nearby |
| --- | --- |
| [Security WebFlux Binding](security-webflux.md) | Bind the framework principal and authorization policy to reactive Spring Security/WebFlux. |

## Build and test

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-security-resource-server -am verify
./mvnw -pl :fireflyframework-security-resource-server -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Representative test sources:

- [ResourceServerDecoderSelectionTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-resource-server/src/test/java/org/fireflyframework/security/rs/ResourceServerDecoderSelectionTest.java).
- [ResourceServerIntegrationTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-resource-server/src/test/java/org/fireflyframework/security/rs/ResourceServerIntegrationTest.java).

## Test fixture

The `usesRemoteJwksAndSkipsKeyManagementPortWhenJwkSetUriConfigured` test demonstrates one concrete behavior. Excerpt from [ResourceServerDecoderSelectionTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-resource-server/src/test/java/org/fireflyframework/security/rs/ResourceServerDecoderSelectionTest.java). The test class supplies its fixtures and imports.

```java
@Test
void usesRemoteJwksAndSkipsKeyManagementPortWhenJwkSetUriConfigured() {
    KeyManagementPort keyManagementPort = mock(KeyManagementPort.class);
    ResourceServerProperties properties = new ResourceServerProperties();
    properties.setJwkSetUri("https://idp.test/realms/r/protocol/openid-connect/certs");

    NimbusReactiveJwtDecoder decoder = ResourceServerAutoConfiguration.buildDecoder(keyManagementPort, properties);

    assertThat(decoder).isNotNull();
    // The external-JWKS path must never consult the in-memory signing key.
    verifyNoInteractions(keyManagementPort);
}
```

## Detailed reference

### Where it sits in the platform

The security platform is layered hexagonally; dependencies point inward, and providers attach as outboard adapters:

```
security-api  →  security-spi  →  security-core  →  security-webflux  →  security-resource-server  →  adapters
 (ports +         (driven           (neutral          (reactive             (this module:               (Vault, KMS,
  domain)          ports)            engine)            Spring Security        Spring Security           OPA, Keycloak,
                                                        bindings)              resource-server wiring)     internal-db, …)
```

- `security-api` defines the domain (`SecurityPrincipal`, `Decision`, `SigningKey`) and driving ports.
- `security-spi` defines the driven ports this module consumes: `KeyManagementPort`, `AuthorityMappingPort`, `PolicyDecisionPort`, `AuditEventPort`, `SecurityContextPort`.
- `security-core` supplies the framework-neutral default implementations (`InMemoryKeyManagementAdapter`, `ConfigurableAuthorityMapper`, `PrincipalFactory`, `EmbeddedPolicyDecisionAdapter`, `LoggingAuditEventAdapter`).
- `security-webflux` supplies the reactive Spring Security glue (`FireflyAuthenticationToken`, `PolicyAuthorizationManager`, `ReactorSecurityContextAdapter`).
- This module binds all of the above into a concrete `SecurityWebFilterChain` and is delivered to applications transitively via the application starter.
- Adapters (key management, policy, idp providers) replace the in-process defaults by simply contributing their own port beans.

This module depends only on `security-webflux` (which transitively brings `security-core`, `-spi`, `-api`) and the Spring Security OAuth2 resource-server / JOSE artifacts. It imports no vendor SDK.

### What it provides

`ResourceServerAutoConfiguration` contributes, each gated by `@ConditionalOnMissingBean`:

- A verifying JWT decoder. `NimbusReactiveJwtDecoder.withPublicKey(...)` built from the active RSA `SigningKey` resolved through `KeyManagementPort.activeSigningKey()`, pinned to `RS256`. If no RSA key resolves, the context fails to start (fail-closed) rather than booting an unprotected server.
- A validator chain. A `DelegatingOAuth2TokenValidator<Jwt>` always enforcing `JwtTimestampValidator` (exp/nbf with skew), plus `JwtIssuerValidator` when `issuer` is configured and a custom audience validator when `audiences` is non-empty.
- Claim-to-authority mapping. An `AuthorityMappingPort` (default `ConfigurableAuthorityMapper`) driven by configurable role/scope claim paths and authority prefix, normalizing provider-specific claims (Keycloak/Cognito/Entra) into a uniform authority/scope set.
- A framework principal. `JwtToFireflyPrincipalConverter` replaces Spring's default `JwtAuthenticationToken` with a `FireflyAuthenticationToken` whose principal is a `SecurityPrincipal` (built by `PrincipalFactory.fromClaims(...)`), so `@AuthenticationPrincipal SecurityPrincipal` works with its default configuration.
- A default-deny filter chain. `fireflySecurityWebFilterChain` disables CSRF, HTTP-Basic, form-login, and logout; permits only the explicitly configured `permitMatchers`; and routes `anyExchange()` through a `PolicyAuthorizationManager` backed by `PolicyDecisionPort`. Unconfigured routes are denied.
- Hardened response headers on every response: `X-Frame-Options: DENY`, `Referrer-Policy: no-referrer`, a strict `Content-Security-Policy` (`default-src 'none'; frame-ancestors 'none'`), and HSTS (`includeSubDomains`, 365-day max-age).
- Framework defaults for the remaining ports. `EmbeddedPolicyDecisionAdapter` (collecting any `PolicyRule` beans), `LoggingAuditEventAdapter`, and `ReactorSecurityContextAdapter`. all overridable.

### Key types

| Type | Role |
| --- | --- |
| `ResourceServerAutoConfiguration` | `@AutoConfiguration @EnableWebFluxSecurity` entry point; builds the decoder, converter, and `SecurityWebFilterChain`. |
| `ResourceServerProperties` | `@ConfigurationProperties("firefly.security.resource-server")`. permit matchers, issuer, audiences, authority prefix, role/scope claim paths. |
| `JwtToFireflyPrincipalConverter` | `Converter<Jwt, Mono<AbstractAuthenticationToken>>` mapping a validated `Jwt` to a `FireflyAuthenticationToken` carrying a `SecurityPrincipal`. |

Ports consumed (from `security-spi`): `KeyManagementPort`, `AuthorityMappingPort`, `PolicyDecisionPort`, `AuditEventPort`, `SecurityContextPort`. Domain types (from `security-api`): `SecurityPrincipal`, `Decision`, `SigningKey`.

The auto-configuration is registered via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.

### Configuration

All keys live under `firefly.security.resource-server`:

| Property | Default | Description |
| --- | --- | --- |
| `permit-matchers` | _(empty)_ | Ant-style path patterns served without authentication. The only opt-out from default-deny. |
| `issuer` | _(none)_ | Expected `iss`; when set, a `JwtIssuerValidator` is enforced. |
| `audiences` | _(empty)_ | Acceptable `aud` values; when non-empty, an audience validator is enforced. |
| `authority-prefix` | `""` | Prefix applied to mapped authorities (e.g. `ROLE_`). |
| `role-claim-paths` | _(empty)_ | Dot-path claims inspected for roles/groups (defaults cover Keycloak/Cognito/Entra). |
| `scope-claim-paths` | _(empty)_ | Dot-path claims inspected for OAuth2 scopes. |

With no configuration at all, every route requires a validated bearer token and timestamp validation is always on.

### Request flow

```
Bearer token → fireflySecurityWebFilterChain
   → NimbusReactiveJwtDecoder (RS256, key from KeyManagementPort)
   → DelegatingOAuth2TokenValidator (timestamp [+ issuer] [+ audience])
   → JwtToFireflyPrincipalConverter → FireflyAuthenticationToken(SecurityPrincipal)
   → authorizeExchange: permitMatchers → permitAll, else PolicyAuthorizationManager (PolicyDecisionPort, default-deny)
   → controller / handler
```

A missing or malformed token, a failed signature, an expired token, or a wrong issuer/audience yields 401. A validated token whose policy decision is DENY (or whose route is not permitted) yields 403. Only a validated token on an allowed route reaches the handler with 200.

### Testing

The module ships a `@SpringBootTest(webEnvironment = RANDOM_PORT)` integration test, `ResourceServerIntegrationTest`, that boots a real reactive server with the real auto-configuration and exercises the full filter chain over HTTP with `WebTestClient`. It signs JWTs in-test with Nimbus (`RSASSASigner`) using the active `SigningKey` from the wired `KeyManagementPort`, and asserts the secure-by-default behavior end to end:

- 200. a public route (`/public/**`) is reachable without a token; a protected route returns the `SecurityPrincipal.subject()` for a valid signed JWT.
- 401. a protected route with no token; an expired token; a forged token signed by an unknown key (a fresh `InMemoryKeyManagementAdapter` the server doesn't trust).
- 403. a route a contributed `PolicyRule` denies, even with a valid token (proving default-deny / policy enforcement is independent of authentication).
- Headers. `X-Frame-Options: DENY`, `Referrer-Policy: no-referrer`, and `Content-Security-Policy` are present on responses.

These mirror the platform's negative-path verification strategy: expired, forged-signature-rejected, and denied-policy paths are proven, not assumed.

## Continue the application

Use the [Security Method Policy guide](security-method-policy.md) for the next part of this integration. The runnable submission/query service is in [Getting started](../getting-started.md).

## Source and further reading

- [Maven project and dependency declarations](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-resource-server/pom.xml).
- [Production source](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/security-resource-server/src/main/java).
- [org.springframework.boot.autoconfigure.AutoConfiguration.imports](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-resource-server/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports).
- [Module catalog](index.md) and [getting started](../getting-started.md).
- [Maven platform](../maven.md) and [architecture](../architecture.md).

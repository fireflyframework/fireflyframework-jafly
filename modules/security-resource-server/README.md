![Jafly. Security Resource Server](../../docs/site/assets/modules/security-resource-server.svg)

# Security Resource Server

Validate signed JWT bearer tokens in a reactive resource server with hardened default security behavior.

Configure the issuer and signing-key source for the JWT authority and review the permitted routes. Combine with method-policy when application methods require Secure evaluation as well as URL authentication.

[Module guide](../../docs/site/modules/security-resource-server.md) · [Jafly README](../../README.md) · [Module catalog](../../docs/site/modules/index.md)

## In the onboarding service

Protect onboarding HTTP routes with JWTs from the configured identity authority. Set the external JWKS endpoint and issuer, then define which operations the applicant or reviewer may perform.

## Installation

[Installation and version availability](../../docs/site/installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-security-resource-server</artifactId>
    <version>26.10.21-SNAPSHOT</version>
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

## Configuration

The `firefly.security.resource-server` namespace is bound by [ResourceServerProperties.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-resource-server/src/main/java/org/fireflyframework/security/rs/ResourceServerProperties.java). Nested configuration is declared in the linked class. Defaults are the field initializers.

| Setting | Java type | Source default |
| --- | --- | --- |
| `firefly.security.resource-server.permit-matchers` | `List<String>` | `new ArrayList<>()` |
| `firefly.security.resource-server.issuer` | `String` | `not initialized` |
| `firefly.security.resource-server.audiences` | `List<String>` | `new ArrayList<>()` |
| `firefly.security.resource-server.authority-prefix` | `String` | `""` |
| `firefly.security.resource-server.role-claim-paths` | `List<String>` | `new ArrayList<>()` |

## API overview

| Type | Contract and responsibility |
| --- | --- |
| [`ResourceServerAutoConfiguration`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-resource-server/src/main/java/org/fireflyframework/security/rs/ResourceServerAutoConfiguration.java) | Auto-configures a secure-by-default reactive OAuth2 resource server. |
| [`ResourceServerProperties`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-resource-server/src/main/java/org/fireflyframework/security/rs/ResourceServerProperties.java) | Configuration for the secure-by-default resource server. |
| [`JwtToFireflyPrincipalConverter`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-resource-server/src/main/java/org/fireflyframework/security/rs/JwtToFireflyPrincipalConverter.java) | Converts a signature-validated `Jwt` into a `FireflyAuthenticationToken` whose principal is a `SecurityPrincipal` with normalized authorities/scopes. |

## Continue the application

Use the [Security Method Policy guide](../../docs/site/modules/security-method-policy.md) for the next part of this integration.

## Build and validation

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-security-resource-server -am verify
./mvnw -pl :fireflyframework-security-resource-server -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Configuration details and source references: [module guide](../../docs/site/modules/security-resource-server.md).

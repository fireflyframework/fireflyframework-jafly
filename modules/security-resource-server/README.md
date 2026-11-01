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
    <version>26.10.22</version>
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

Add ABAC/ReBAC decisions by contributing one or more `PolicyRule` beans; they are collected by the default `EmbeddedPolicyDecisionAdapterImpl` and consulted on every non-permitted exchange, fail-closed:

```java
@Bean
PolicyRule denyReports() {
    return (principal, action, resource, context) ->
            resource.startsWith("/api/reports") && !principal.scopes().contains("reports.read")
                    ? Mono.just(Decision.deny("reports.read scope required"))
                    : Mono.just(Decision.permit());
}
```

## Header bearer and browser requests

The default chain uses explicit header bearer authentication and never restores authentication from a session cookie. Native Spring Security CSRF protection remains enabled with its bearer exemption: valid signed bearer unsafe requests need no CSRF token; unsafe non-bearer requests require CSRF, including allowlisted browser routes. Missing CSRF can return 403 before authentication. See the [header bearer and CSRF contract](docs/bearer-api-and-csrf.md) for transport, status and application-owned browser-chain details.

## Configuration

The `firefly.security.resource-server` namespace is bound by [ResourceServerProperties.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-resource-server/src/main/java/org/fireflyframework/security/rs/ResourceServerProperties.java). Nested configuration is declared in the linked class. Defaults are the field initializers.

| Setting | Java type | Source default |
| --- | --- | --- |
| `firefly.security.resource-server.permit-matchers` | `List<String>` | `new ArrayList<>()` |
| `firefly.security.resource-server.issuer` | `String` | `not initialized` |
| `firefly.security.resource-server.audiences` | `List<String>` | `new ArrayList<>()` |
| `firefly.security.resource-server.authority-prefix` | `String` | `""` |
| `firefly.security.resource-server.role-claim-paths` | `List<String>` | `new ArrayList<>()` |
| `firefly.security.resource-server.tenant-claim-paths` | `List<String>` | `new ArrayList<>()` |
| `firefly.security.resource-server.tenant-required` | `boolean` | `false` |

Set `tenant-claim-paths: [tenant_id]` to project exactly one distinct tenant from validated token claims into `SecurityPrincipal.tenantId`; request headers are not used. The empty default preserves the existing unconfigured principal. With `tenant-required: true`, unresolved or ambiguous tenants reject authentication, and missing or invalid configured paths fail startup. Optional configured projection yields a null tenant when unresolved or ambiguous. Resolver errors reject authentication in either mode without exposing claim values.

Tenant identifiers are opaque. A nonblank scalar String and the same String as a collection element retain identical spaces, commas, case and valid Unicode, including leading/trailing spaces and supplementary characters. Nothing is trimmed or split into authority-style tokens. Multiple distinct resulting identifiers remain unresolved. Use a JSON array to represent multiple tenants: scalar `"tenant-a,tenant-b"` now identifies that exact tenant rather than a delimited list. This intentionally corrects the previous implicit use of authority/scope tokenization for scalar tenant claims; role and scope parsing are unchanged.

ISO control characters and unpaired UTF-16 surrogates make the whole tenant candidate unresolved, including when a malformed element appears beside another otherwise valid value or in another configured claim path. No malformed element is silently dropped to choose a different tenant. This intentionally tightens the previous acceptance of control-bearing collection elements. Valid surrogate pairs are preserved. Required projection rejects unresolved candidates; optional projection retains its existing null-tenant behavior. JWT signature, issuer, audience and timestamp validation are unchanged, and projection does not prove tenant existence or resource authority.

Legacy non-String coercion remains for compatibility: for example, numeric `17` and a sole collection element `17` project to `"17"`; a mixed collection containing `"tenant-a"` and `17` remains ambiguous. Null and plain blank collection entries retain their prior omission behavior, while malformed control-bearing blanks invalidate the whole candidate. Stricter typed tenant claims are a separate security/design gate. Generic String tenant discriminators remain distinct from owner-specific UUID resource identifiers; this projection performs no UUID conversion or cross-tenant mapping.


## API overview

| Type | Contract and responsibility |
| --- | --- |
| [`ResourceServerAutoConfiguration`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-resource-server/src/main/java/org/fireflyframework/security/rs/ResourceServerAutoConfiguration.java) | Auto-configures a secure-by-default reactive OAuth2 resource server. |
| [`ResourceServerProperties`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-resource-server/src/main/java/org/fireflyframework/security/rs/ResourceServerProperties.java) | Configuration for the secure-by-default resource server. |
| [`JwtToFireflyPrincipalConverterImpl`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-resource-server/src/main/java/org/fireflyframework/security/rs/JwtToFireflyPrincipalConverterImpl.java) | Converts a signature-validated `Jwt` into a `FireflyAuthenticationToken` whose principal is a `SecurityPrincipal` with normalized authorities/scopes. |

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

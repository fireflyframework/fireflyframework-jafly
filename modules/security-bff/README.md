![Jafly. Security BFF](../../docs/site/assets/modules/security-bff.svg)

# Security BFF

Handle browser OAuth2 login with server-side tokens, an opaque host cookie, CSRF protection, relay, and logout.

Configure the provider registration and BFF settings, then test the browser/session and XHR flows. Keep the session store and cookie deployment aligned with HTTPS and the reverse proxy; the module does not require a specific gateway product.

[Module guide](../../docs/site/modules/security-bff.md) · [Jafly README](../../README.md) · [Module catalog](../../docs/site/modules/index.md)

## In the onboarding service

A browser applicant portal can keep tokens in a server-side session and present the opaque BFF cookie to the browser. Configure the OAuth registration, session storage, HTTPS cookie behavior, and CSRF flow together.

## Installation

[Installation and version availability](../../docs/site/installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-security-bff</artifactId>
    <version>26.10.17-SNAPSHOT</version>
</dependency>
```

## Quick start

The Java examples in this section are API fragments for application code. Use the stated imports and supply the application-owned collaborators described alongside them.

With the module on the classpath and an OAuth2 client registration configured, the token-handler chain is active with zero security code. The consuming gateway keeps its routes and the `TokenRelay` + header-stripping filters:

```yaml
spring:
  security:
    oauth2:
      client:
        provider:
          idp:
            issuer-uri: https://idp.example.com/realms/firefly
        registration:
          idp:
            provider: idp
            client-id: my-bff
            client-secret: ${BFF_CLIENT_SECRET}
            authorization-grant-type: authorization_code
            scope: openid, profile, email
            redirect-uri: "{baseUrl}/login/oauth2/code/idp"
  session:
    store-type: redis
  cloud:
    gateway:
      default-filters:
        - TokenRelay=
        - RemoveRequestHeader=X-Tenant-Id
        - RemoveRequestHeader=X-User-Id
      routes:
        - id: api
          uri: lb://downstream-service
          predicates:
            - Path=/api/**

firefly:
  security:
    bff:
      registration-id: idp
      permit-matchers:
        - /actuator/health/**
```

## Configuration

The `firefly.security.bff` namespace is bound by [BffSecurityProperties.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-bff/src/main/java/org/fireflyframework/security/bff/BffSecurityProperties.java). Nested configuration is declared in the linked class. Defaults are the field initializers.

| Setting | Java type | Source default |
| --- | --- | --- |
| `firefly.security.bff.registration-id` | `String` | `"idp"` |
| `firefly.security.bff.permit-matchers` | `List<String>` | `new ArrayList<>()` |
| `firefly.security.bff.csrf-exempt-matchers` | `List<String>` | `new ArrayList<>()` |
| `firefly.security.bff.post-logout-redirect-uri` | `String` | `"{baseUrl}"` |
| `firefly.security.bff.xhr-aware-logout` | `boolean` | `false` |

## API overview

| Type | Contract and responsibility |
| --- | --- |
| [`BffLoginHook`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-bff/src/main/java/org/fireflyframework/security/bff/BffLoginHook.java) | Opt-in extension point invoked once per successful login , in the oauth2Login success handler, before the post-login redirect. |
| [`BffSecurityProperties`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-bff/src/main/java/org/fireflyframework/security/bff/BffSecurityProperties.java) | Configuration for the token-handler BFF security chain (`BffSecurityAutoConfiguration`). |
| [`BffRefreshTokenFailureWebFilter`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-bff/src/main/java/org/fireflyframework/security/bff/BffRefreshTokenFailureWebFilter.java) | Turns a dead-session token failure into a clean, actionable response. |
| [`BffSecurityAutoConfiguration`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-bff/src/main/java/org/fireflyframework/security/bff/BffSecurityAutoConfiguration.java) | Defines `bffSecurityWebFilterChain`, `NegatedServerWebExchangeMatcher`, `bffAuthorizedClientManager`, `DefaultReactiveOAuth2AuthorizedClientManager` operations. |
| [`CsrfCookieWebFilter`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-bff/src/main/java/org/fireflyframework/security/bff/CsrfCookieWebFilter.java) | Materializes the CSRF token so the `XSRF-TOKEN` cookie is actually delivered to the browser. |

## Continue the application

Use the [Security Resource Server guide](../../docs/site/modules/security-resource-server.md) for the next part of this integration.

## Build and validation

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-security-bff -am verify
./mvnw -pl :fireflyframework-security-bff -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Configuration details and source references: [module guide](../../docs/site/modules/security-bff.md).

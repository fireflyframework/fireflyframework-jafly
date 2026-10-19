![Jafly. Security BFF](../assets/modules/security-bff.svg)

# Security BFF

Handle browser OAuth2 login with server-side tokens, an opaque host cookie, CSRF protection, relay, and logout.

Configure the provider registration and BFF settings, then test the browser/session and XHR flows. Keep the session store and cookie deployment aligned with HTTPS and the reverse proxy; the module does not require a specific gateway product.

| Maven artifact | Packaging | Source project |
| --- | --- | --- |
| `org.fireflyframework:fireflyframework-security-bff` | `jar` | [modules/security-bff](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/security-bff) |

Part of [Security and identity](index.md#security-and-identity).

## In the onboarding service

A browser applicant portal can keep tokens in a server-side session and present the opaque BFF cookie to the browser. Configure the OAuth registration, session storage, HTTPS cookie behavior, and CSRF flow together.

## Add the component

[Installation and version availability](../installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-security-bff</artifactId>
    <version>26.10.20-SNAPSHOT</version>
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

## Configuration and wiring

The `firefly.security.bff` namespace is bound by [BffSecurityProperties.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-bff/src/main/java/org/fireflyframework/security/bff/BffSecurityProperties.java). Nested configuration is declared in the linked class. Defaults are the field initializers.

| Setting | Java type | Source default |
| --- | --- | --- |
| `firefly.security.bff.registration-id` | `String` | `"idp"` |
| `firefly.security.bff.permit-matchers` | `List<String>` | `new ArrayList<>()` |
| `firefly.security.bff.csrf-exempt-matchers` | `List<String>` | `new ArrayList<>()` |
| `firefly.security.bff.post-logout-redirect-uri` | `String` | `"{baseUrl}"` |
| `firefly.security.bff.xhr-aware-logout` | `boolean` | `false` |
| `firefly.security.bff.refresh-failure401` | `boolean` | `false` |
| `firefly.security.bff.csrf-cookie` | `boolean` | `false` |
| `firefly.security.bff.login-error-redirect-uri` | `String` | `not initialized` |

Property-based wiring conditions found in this module:

- `prefix = "firefly.security.bff", name = "refresh-failure-401", havingValue = "true"`. [BffSecurityAutoConfiguration.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-bff/src/main/java/org/fireflyframework/security/bff/BffSecurityAutoConfiguration.java).
- `prefix = "firefly.security.bff", name = "csrf-cookie", havingValue = "true"`. [BffSecurityAutoConfiguration.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-bff/src/main/java/org/fireflyframework/security/bff/BffSecurityAutoConfiguration.java).

## Public API and extension points

| Type | Contract and responsibility |
| --- | --- |
| [`BffLoginHook`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-bff/src/main/java/org/fireflyframework/security/bff/BffLoginHook.java) | Opt-in extension point invoked once per successful login , in the oauth2Login success handler, before the post-login redirect. |
| [`BffSecurityProperties`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-bff/src/main/java/org/fireflyframework/security/bff/BffSecurityProperties.java) | Configuration for the token-handler BFF security chain (`BffSecurityAutoConfiguration`). |
| [`BffRefreshTokenFailureWebFilter`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-bff/src/main/java/org/fireflyframework/security/bff/BffRefreshTokenFailureWebFilter.java) | Turns a dead-session token failure into a clean, actionable response. |
| [`BffSecurityAutoConfiguration`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-bff/src/main/java/org/fireflyframework/security/bff/BffSecurityAutoConfiguration.java) | Defines `bffSecurityWebFilterChain`, `NegatedServerWebExchangeMatcher`, `bffAuthorizedClientManager`, `DefaultReactiveOAuth2AuthorizedClientManager` operations. |
| [`CsrfCookieWebFilter`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-bff/src/main/java/org/fireflyframework/security/bff/CsrfCookieWebFilter.java) | Materializes the CSRF token so the `XSRF-TOKEN` cookie is actually delivered to the browser. |

## Build and test

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-security-bff -am verify
./mvnw -pl :fireflyframework-security-bff -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Representative test sources:

- [BffRefreshFilterWiringTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-bff/src/test/java/org/fireflyframework/security/bff/BffRefreshFilterWiringTest.java).
- [BffRefreshTokenFailureWebFilterTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-bff/src/test/java/org/fireflyframework/security/bff/BffRefreshTokenFailureWebFilterTest.java).
- [BffSecurityAutoConfigurationTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-bff/src/test/java/org/fireflyframework/security/bff/BffSecurityAutoConfigurationTest.java).
- [CsrfCookieWebFilterTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-bff/src/test/java/org/fireflyframework/security/bff/CsrfCookieWebFilterTest.java).
- [SpaServerCsrfTokenRequestHandlerTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-bff/src/test/java/org/fireflyframework/security/bff/SpaServerCsrfTokenRequestHandlerTest.java).
- [XhrAwareLogoutSuccessHandlerTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-bff/src/test/java/org/fireflyframework/security/bff/XhrAwareLogoutSuccessHandlerTest.java).

## Test fixture

The `subscribesDeferredTokenThenProceeds` test demonstrates one concrete behavior. Excerpt from [CsrfCookieWebFilterTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-bff/src/test/java/org/fireflyframework/security/bff/CsrfCookieWebFilterTest.java). The test class supplies its fixtures and imports.

```java
@Test
void subscribesDeferredTokenThenProceeds() {
    MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/x"));
    AtomicBoolean tokenSubscribed = new AtomicBoolean(false);
    Mono<CsrfToken> token = Mono.<CsrfToken>just(new DefaultCsrfToken("X-XSRF-TOKEN", "_csrf", "v"))
            .doOnSubscribe(s -> tokenSubscribed.set(true));
    exchange.getAttributes().put(CsrfToken.class.getName(), token);

    AtomicBoolean chained = new AtomicBoolean(false);
    WebFilterChain chain = e -> Mono.fromRunnable(() -> chained.set(true));

    StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

    assertThat(tokenSubscribed).as("deferred token must be subscribed to emit the cookie").isTrue();
    assertThat(chained).as("chain must still proceed").isTrue();
}
```

## Detailed reference

### Where it sits in the platform

The security platform is layered hexagonally; this module is the edge / confidential-client binding, a sibling of the resource-server binding:

```
security-api  →  security-spi  →  security-core  →  security-webflux  →  security-bff           →  application
 (ports +         (driven           (neutral          (reactive             (this module:             (Spring Cloud
  domain)          ports)            engine)            Spring Security        oauth2Login token-        Gateway: routes +
                                                        bindings)              handler chain)            TokenRelay)
```

- This module wires Spring Security's reactive `oauth2Login` into the token-handler chain and exposes a session-bound `ReactiveOAuth2AuthorizedClientManager` so a gateway `TokenRelay` can attach the logged-in user's access token downstream.
- It pairs with `security-resource-server` on the other side of the call: the BFF relays a bearer token to services that this module's resource-server sibling validates.

This module depends only on Spring Security's OAuth2 client + reactive web stack; it imports no vendor SDK and does not require Spring Cloud Gateway at compile time.

### What it provides

`BffSecurityAutoConfiguration` contributes, each gated by `@ConditionalOnMissingBean`:

- A token-handler `SecurityWebFilterChain` (`bffSecurityWebFilterChain`):
  - `oauth2Login` with forced PKCE. A `DefaultServerOAuth2AuthorizationRequestResolver` with `OAuth2AuthorizationRequestCustomizers.withPkce()` so the authorization request always carries `code_challenge` (S256). Spring enables PKCE automatically only for *public* clients, but a confidential token-handler must still send it (required when the IdP enforces PKCE).
  - Default-deny authorization. Only the configured `permit-matchers` are public; every other exchange requires authentication.
  - Cookie-mode CSRF. `CookieServerCsrfTokenRepository.withHttpOnlyFalse()` so a SPA can read and echo the `XSRF-TOKEN`.
  - RP-initiated logout. `OidcClientInitiatedServerLogoutSuccessHandler` clears the session and redirects to the IdP end-session endpoint.
  - A browser-aware entry point. A document request (`Accept: text/html`) is redirected to start the login (`/oauth2/authorization/<registrationId>`); everything else (XHR/fetch) gets a 401 so the SPA can trigger the login navigation itself.
- A session-bound authorized-client manager (`bffAuthorizedClientManager`): a `DefaultReactiveOAuth2AuthorizedClientManager` (authorization-code + refresh-token providers) that reads the token from the `ServerOAuth2AuthorizedClientRepository` (the WebSession, Redis-backed via Spring Session). A gateway `TokenRelay` uses this bean to attach the logged-in user's access token to downstream calls. not an M2M token.

### Key types

| Type | Role |
| --- | --- |
| `BffSecurityAutoConfiguration` | `@AutoConfiguration` entry point (`@ConditionalOnWebApplication(REACTIVE)`, `@ConditionalOnClass(ServerHttpSecurity, ReactiveClientRegistrationRepository)`); builds the token-handler `SecurityWebFilterChain` and the session-bound authorized-client manager. |
| `BffSecurityProperties` | `@ConfigurationProperties("firefly.security.bff")`. neutral `registration-id`, public `permit-matchers`, `post-logout-redirect-uri`. |

The auto-configuration is registered via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.

### Configuration

All keys live under `firefly.security.bff`:

| Property | Default | Description |
| --- | --- | --- |
| `registration-id` | `idp` | Spring OAuth2 client `registration-id`. Neutral on purpose (not the provider name) so the login endpoints are `/oauth2/authorization/<id>` and `/login/oauth2/code/<id>`. |
| `permit-matchers` | _(empty)_ | Ant-style paths served without authentication (e.g. actuator, docs). The OAuth2 login endpoints are handled by the login filter and need not be listed. |
| `post-logout-redirect-uri` | `{baseUrl}` | Where to send the browser after an RP-initiated logout completes at the IdP. |

The OAuth2 client itself (provider, client-id/secret, scopes, redirect-uri) is configured with the standard `spring.security.oauth2.client.*` keys, and the session store with `spring.session.*`. This module does not duplicate them.

### Why not `security-oauth2-client`

The framework's `security-oauth2-client` module is intentionally not used here. Its authorized-client manager is service/M2M-oriented (`client_credentials`); on a gateway it would hijack the `TokenRelay` and attach a service token instead of the logged-in user's token. This module fills that gap with a manager bound to the user's WebSession, which is exactly what the token-handler pattern needs.

### Login & relay flow

```
Browser (cookie)
  → /oauth2/authorization/<id>     → 302 to IdP /authorize (code_challenge S256)
  → IdP login
  → /login/oauth2/code/<id>        → server-side code→token exchange
  → tokens stored in the WebSession (Spring Session / Redis); browser gets an opaque __Host- cookie
  → API call (cookie, no bearer)   → TokenRelay reads the session-bound authorized client
                                    → adds Authorization: Bearer <user access token> downstream
```

An unauthenticated document request is redirected to the IdP login; an unauthenticated XHR gets 401. The browser never receives or stores a token.

### Testing

The module ships a structural test, `BffSecurityAutoConfigurationTest`, that boots the real auto-configuration with a static (non-discovery) OAuth client registration so the context starts without a live IdP, Redis or Docker (session in-memory; Redis/Session autoconfig excluded). It asserts the secure-by-default behavior:

- the permit list is reachable without authentication;
- an unauthenticated XHR call returns 401, while a browser document request is redirected to `/oauth2/authorization/<id>`;
- `oauth2Login` is wired under the neutral `registration-id` and the authorize redirect targets the IdP authorization endpoint.

## Continue the application

Use the [Security Resource Server guide](security-resource-server.md) for the next part of this integration. The runnable submission/query service is in [Getting started](../getting-started.md).

## Source and further reading

- [Maven project and dependency declarations](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-bff/pom.xml).
- [Production source](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/security-bff/src/main/java).
- [org.springframework.boot.autoconfigure.AutoConfiguration.imports](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-bff/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports).
- [Module catalog](index.md) and [getting started](../getting-started.md).
- [Maven platform](../maven.md) and [architecture](../architecture.md).

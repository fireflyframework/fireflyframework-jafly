![Jafly. Security OAuth2 Client](../assets/modules/security-oauth2-client.svg)

# Security OAuth2 Client

Provide reactive OAuth2/OIDC authorized-client support and WebClient token relay.

Configure Spring Security client registrations for the selected grant and use the module relay filter for outbound calls. For browser sessions and the dedicated token-handler flow, evaluate security-bff as the focused component.

| Maven artifact | Packaging | Source project |
| --- | --- | --- |
| `org.fireflyframework:fireflyframework-security-oauth2-client` | `jar` | [modules/security-oauth2-client](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/security-oauth2-client) |

Part of [Security and identity](index.md#security-and-identity).

## In the onboarding service

The onboarding service may need an authorized outbound call to a protected partner API. Configure the relevant client registration/grant and keep token relay scoped to that destination.

## Add the component

[Installation and version availability](../installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-security-oauth2-client</artifactId>
    <version>26.10.21-SNAPSHOT</version>
</dependency>
```

## Quick start

The Java examples in this section are API fragments for application code. Use the stated imports and supply the application-owned collaborators described alongside them.

Declare a client registration (here a `client_credentials` registration). Spring Boot's standard `spring.security.oauth2.client.registration.*` / `.provider.*` properties produce a `ReactiveClientRegistrationRepository`, which switches the auto-configuration on:

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          ledger-api:
            authorization-grant-type: client_credentials
            client-id: firefly-bff
            client-secret: ${LEDGER_CLIENT_SECRET}
        provider:
          ledger-api:
            token-uri: https://idp.example.com/oauth2/token
```

Build a `WebClient` with the token-relay filter and pin a registration; the bearer token is acquired (and refreshed) and attached automatically:

```java
import org.springframework.security.oauth2.client.web.reactive.function.client.ServerOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
class LedgerClient {

    private final WebClient webClient;

    LedgerClient(WebClient.Builder builder,
                 ServerOAuth2AuthorizedClientExchangeFilterFunction tokenRelay) {
        tokenRelay.setDefaultClientRegistrationId("ledger-api");
        this.webClient = builder
                .baseUrl("https://ledger.internal")
                .filter(tokenRelay)
                .build();
    }

    Mono<String> balances() {
        return webClient.get().uri("/api/balances").retrieve().bodyToMono(String.class);
    }
}
```

For machine-to-machine code paths you can also drive the manager directly:

```java
OAuth2AuthorizeRequest request = OAuth2AuthorizeRequest
        .withClientRegistrationId("ledger-api")
        .principal("system")
        .build();

Mono<String> token = authorizedClientManager.authorize(request)
        .map(client -> client.getAccessToken().getTokenValue());
```

## Configuration and wiring

This module declares no `@ConfigurationProperties` class of its own. Use the constructor/port contracts above and the configuration of the host or related core module.

## Public API and extension points

| Type | Contract and responsibility |
| --- | --- |
| [`OAuth2ClientAutoConfiguration`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-oauth2-client/src/main/java/org/fireflyframework/security/client/OAuth2ClientAutoConfiguration.java) | Auto-configures the reactive OAuth2 client (BFF) building blocks when a `ReactiveClientRegistrationRepository` is present: an authorized-client manager supporting `authorization_code`, `refresh_token` and `client_credentials`, and a WebClient token-relay filter. |

## Build and test

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-security-oauth2-client -am verify
./mvnw -pl :fireflyframework-security-oauth2-client -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Representative test sources:

- [OAuth2ClientCredentialsIntegrationTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-oauth2-client/src/test/java/org/fireflyframework/security/client/OAuth2ClientCredentialsIntegrationTest.java).

## Test fixture

The `acquiresClientCredentialsAccessToken` test demonstrates one concrete behavior. Excerpt from [OAuth2ClientCredentialsIntegrationTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-oauth2-client/src/test/java/org/fireflyframework/security/client/OAuth2ClientCredentialsIntegrationTest.java). The test class supplies its fixtures and imports.

```java
@Test
void acquiresClientCredentialsAccessToken() {
    ClientRegistration registration = ClientRegistration.withRegistrationId("demo")
            .tokenUri("http://localhost:" + wireMock.port() + "/oauth2/token")
            .clientId("client")
            .clientSecret("secret")
            .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
            .build();

    ReactiveClientRegistrationRepository repository = new InMemoryReactiveClientRegistrationRepository(registration);
    ReactiveOAuth2AuthorizedClientService service = autoConfiguration.fireflyAuthorizedClientService(repository);
    ReactiveOAuth2AuthorizedClientManager manager = autoConfiguration.fireflyAuthorizedClientManager(repository, service);

    OAuth2AuthorizeRequest request = OAuth2AuthorizeRequest
            .withClientRegistrationId("demo")
            .principal("system")
            .build();

    OAuth2AuthorizedClient client = manager.authorize(request).block();

    assertThat(client).isNotNull();
    assertThat(client.getAccessToken().getTokenValue()).isEqualTo("abc123");
}
```

## Detailed reference

### Where it sits in the platform

The security platform is layered hexagonally; dependencies point inward, and provider/protocol bindings attach as outboard adapters:

```
security-api  →  security-spi  →  security-core  →  security-webflux  →  security-resource-server   (inbound tokens)
 (ports +         (driven           (neutral          (reactive          └ security-oauth2-client    (outbound tokens:
  domain)          ports)            engine)            Spring Security      this module — Spring         this module)
                                                        bindings)            Security 6 OAuth2 client
                                                                             wiring + token relay)
```

- `security-resource-server` is the inbound counterpart: it validates bearer tokens presented to this service.
- This module is the outbound counterpart: it obtains bearer tokens from an authorization server and relays them to downstream services.

Unlike the rest of the platform, this binding is intentionally decoupled from the framework's internal ports (`KeyManagementPort`, `PolicyDecisionPort`, …): a client obtains tokens from an external IdP rather than minting or deciding on them, so the module depends only on Spring Security's OAuth2 client artifacts and Spring WebFlux. It imports no vendor SDK and contributes nothing unless a `ReactiveClientRegistrationRepository` is present.

### What it provides

`OAuth2ClientAutoConfiguration` (active `@ConditionalOnClass(ReactiveOAuth2AuthorizedClientManager.class)`) contributes the following, each `@ConditionalOnBean(ReactiveClientRegistrationRepository.class)` and `@ConditionalOnMissingBean`:

- An authorized-client service. `fireflyAuthorizedClientService`. an `InMemoryReactiveOAuth2AuthorizedClientService` over the application's `ReactiveClientRegistrationRepository`, holding authorized clients (access/refresh tokens) per registration and principal.
- An authorized-client manager. `fireflyAuthorizedClientManager`. an `AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager` configured with a `ReactiveOAuth2AuthorizedClientProvider` built via `ReactiveOAuth2AuthorizedClientProviderBuilder` for `.authorizationCode()`, `.refreshToken()`, and `.clientCredentials()`. This is the single entry point that resolves (and, when expired, refreshes) an `OAuth2AuthorizedClient` for a given registration. The service-based manager is suited to machine-to-machine and BFF flows where authorization is driven outside an inbound request.
- A token-relay filter. `fireflyOAuth2TokenRelayFilter`. a `ServerOAuth2AuthorizedClientExchangeFilterFunction` wrapping the authorized-client manager. Registered on a `WebClient`, it attaches the authorized client's bearer token to downstream calls and triggers acquisition/refresh as needed.

### Key types

| Type | Role |
| --- | --- |
| `OAuth2ClientAutoConfiguration` | `@AutoConfiguration` entry point; assembles the authorized-client service, manager, and token-relay filter. |
| `ReactiveOAuth2AuthorizedClientManager` (bean `fireflyAuthorizedClientManager`) | Resolves and refreshes `OAuth2AuthorizedClient`s for `authorization_code`, `refresh_token`, and `client_credentials`. |
| `ReactiveOAuth2AuthorizedClientService` (bean `fireflyAuthorizedClientService`) | In-memory store of authorized clients per registration/principal. |
| `ServerOAuth2AuthorizedClientExchangeFilterFunction` (bean `fireflyOAuth2TokenRelayFilter`) | `WebClient` filter that attaches the bearer token to downstream calls. |

Application-supplied input: a `ReactiveClientRegistrationRepository` (e.g. `InMemoryReactiveClientRegistrationRepository`) holding one or more `ClientRegistration`s. All Spring Security OAuth2 client types (`OAuth2AuthorizeRequest`, `OAuth2AuthorizedClient`, `AuthorizationGrantType`) come from `spring-security-oauth2-client`.

The auto-configuration is registered via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.

### Configuration

This module defines no `firefly.*` properties of its own. Client registrations and providers are configured with Spring Boot's standard OAuth2 client keys under `spring.security.oauth2.client.registration.<id>` and `spring.security.oauth2.client.provider.<id>` (or by contributing a `ReactiveClientRegistrationRepository` bean directly). Each registration's `authorization-grant-type` selects the flow; the bundled manager already supports `authorization_code`, `refresh_token`, and `client_credentials`.

### Token flow

```
WebClient call (registrationId pinned)
   → fireflyOAuth2TokenRelayFilter (ServerOAuth2AuthorizedClientExchangeFilterFunction)
   → fireflyAuthorizedClientManager.authorize(OAuth2AuthorizeRequest)
       → ReactiveOAuth2AuthorizedClientProvider (authorization_code | refresh_token | client_credentials)
       → token endpoint exchange / refresh as needed
   → OAuth2AuthorizedClient cached in fireflyAuthorizedClientService
   → Authorization: Bearer <access_token> attached to the downstream request
```

A cached, still-valid authorized client is reused; an expired token is refreshed (or re-acquired for `client_credentials`) before the downstream call proceeds.

### Testing

The module ships an integration test, `OAuth2ClientCredentialsIntegrationTest`, that performs a real `client_credentials` token exchange against a live HTTP token endpoint. A WireMock server stubbing `POST /oauth2/token` to return an `access_token`. It builds a `ClientRegistration` (grant type `CLIENT_CREDENTIALS`) pointed at the WireMock port, wires it through the actual auto-configuration's `fireflyAuthorizedClientService` and `fireflyAuthorizedClientManager`, and calls `manager.authorize(...)`:

- It asserts a non-null `OAuth2AuthorizedClient` is returned, and that `client.getAccessToken().getTokenValue()` equals the token the stub issued (`abc123`).

This proves the wiring end to end over a real network exchange: the manager performs an HTTP POST to the token endpoint, parses the token response, and stores the authorized client. Rather than mocking the exchange away.

## Continue the application

Use the [Client guide](client.md) for the next part of this integration. The runnable submission/query service is in [Getting started](../getting-started.md).

## Source and further reading

- [Maven project and dependency declarations](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-oauth2-client/pom.xml).
- [Production source](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/security-oauth2-client/src/main/java).
- [org.springframework.boot.autoconfigure.AutoConfiguration.imports](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-oauth2-client/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports).
- [Module catalog](index.md) and [getting started](../getting-started.md).
- [Maven platform](../maven.md) and [architecture](../architecture.md).

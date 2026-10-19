![Jafly. Security OAuth2 Client](../../docs/site/assets/modules/security-oauth2-client.svg)

# Security OAuth2 Client

Provide reactive OAuth2/OIDC authorized-client support and WebClient token relay.

Configure Spring Security client registrations for the selected grant and use the module relay filter for outbound calls. For browser sessions and the dedicated token-handler flow, evaluate security-bff as the focused component.

[Module guide](../../docs/site/modules/security-oauth2-client.md) · [Jafly README](../../README.md) · [Module catalog](../../docs/site/modules/index.md)

## In the onboarding service

The onboarding service may need an authorized outbound call to a protected partner API. Configure the relevant client registration/grant and keep token relay scoped to that destination.

## Installation

[Installation and version availability](../../docs/site/installation.md) explains source builds and published releases.

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

## Configuration

This module declares no `@ConfigurationProperties` class of its own. Use the constructor/port contracts above and the configuration of the host or related core module.

## API overview

| Type | Contract and responsibility |
| --- | --- |
| [`OAuth2ClientAutoConfiguration`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-oauth2-client/src/main/java/org/fireflyframework/security/client/OAuth2ClientAutoConfiguration.java) | Auto-configures the reactive OAuth2 client (BFF) building blocks when a `ReactiveClientRegistrationRepository` is present: an authorized-client manager supporting `authorization_code`, `refresh_token` and `client_credentials`, and a WebClient token-relay filter. |

## Continue the application

Use the [Client guide](../../docs/site/modules/client.md) for the next part of this integration.

## Build and validation

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-security-oauth2-client -am verify
./mvnw -pl :fireflyframework-security-oauth2-client -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Configuration details and source references: [module guide](../../docs/site/modules/security-oauth2-client.md).

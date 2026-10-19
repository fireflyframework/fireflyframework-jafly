![Jafly. Security Authorization Server](../../docs/site/assets/modules/security-authorization-server.svg)

# Security Authorization Server

Configure a servlet OAuth2/OIDC authorization server with JWKS backed by KeyManagementPort.

Run this component in a dedicated servlet deployment with registered clients and key-management wiring. Reactive resource servers consume its tokens separately; do not place incompatible servlet and reactive security assumptions into one application.

[Module guide](../../docs/site/modules/security-authorization-server.md) · [Jafly README](../../README.md) · [Module catalog](../../docs/site/modules/index.md)

## In the onboarding service

Use this separate servlet service when the deployment needs to issue its own OAuth2/OIDC tokens. The reactive onboarding service must trust its published JWKS; the two processes do not share default development keys.

## Installation

[Installation and version availability](../../docs/site/installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-security-authorization-server</artifactId>
    <version>26.10.21-SNAPSHOT</version>
</dependency>
```

## Quick start

The Java examples in this section are API fragments for application code. Use the stated imports and supply the application-owned collaborators described alongside them.

With the module on the classpath of a servlet Spring Boot application, the authorization server is fully wired. OIDC discovery, JWKS, and the token endpoint are live with zero code:

```java
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AuthorizationServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuthorizationServerApplication.class, args);
    }
}
```

Request a token with the bundled demo client:

```bash
curl -u firefly-demo:firefly-secret \
  -d 'grant_type=client_credentials&scope=read' \
  http://localhost:8080/oauth2/token
```

The response carries a signed JWT (`access_token`, `"token_type":"Bearer"`) verifiable against this authorization server's published JWKS. A separately deployed resource server has a different default in-memory key: configure its `jwk-set-uri` to this server's `/oauth2/jwks` endpoint and set the expected issuer, or supply explicitly shared key management.

For production, override the client store and the key port by contributing your own beans (both are `@ConditionalOnMissingBean`):

```java
@Bean
RegisteredClientRepository registeredClientRepository() {
    RegisteredClient client = RegisteredClient.withId(UUID.randomUUID().toString())
            .clientId("payments-service")
            .clientSecret(System.getenv("PAYMENTS_CLIENT_SECRET_HASH"))
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
            .scope("payments.read")
            .build();
    return new InMemoryRegisteredClientRepository(client);
}

```

Set `PAYMENTS_CLIENT_SECRET_HASH` to an encoded secret accepted by Spring Security's password encoder. Provide an application-owned `KeyManagementPort` implementation for durable signing keys and rotation; the shipped Vault adapter implements `SecretsPort`, not key management. Configure downstream services from the [resource-server guide](../../docs/site/modules/security-resource-server.md).

## Configuration

This module declares no `@ConfigurationProperties` class of its own. Use the constructor/port contracts above and the configuration of the host or related core module.

## API overview

| Type | Contract and responsibility |
| --- | --- |
| [`AuthorizationServerConfiguration`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-authorization-server/src/main/java/org/fireflyframework/security/authz/AuthorizationServerConfiguration.java) | Optional, servlet-only OAuth2/OIDC Authorization Server built on Spring Authorization Server. |

## Continue the application

Use the [Security Resource Server guide](../../docs/site/modules/security-resource-server.md) for the next part of this integration.

## Build and validation

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-security-authorization-server -am verify
./mvnw -pl :fireflyframework-security-authorization-server -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Configuration details and source references: [module guide](../../docs/site/modules/security-authorization-server.md).

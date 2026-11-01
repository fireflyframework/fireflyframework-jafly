![Jafly. Security R2DBC Adapter](../../docs/site/assets/modules/security-adapter-r2dbc.svg)

# Security R2DBC Adapter

Persist token revocation records through a relational R2DBC adapter.

Provide DatabaseClient, apply the documented schema, and wire R2dbcRevocationAdapterImpl into the security engine. Verify expiry and revocation queries against the same database dialect and clock behavior used in production.

[Module guide](../../docs/site/modules/security-adapter-r2dbc.md) · [Jafly README](../../README.md) · [Module catalog](../../docs/site/modules/index.md)

## In the onboarding service

Store revoked token IDs when the application uses PostgreSQL for that security state. Explicitly connect the revocation port to the JWT validation policy; registering its bean alone does not reject a token.

## Installation

[Installation and version availability](../../docs/site/installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-security-adapter-r2dbc</artifactId>
    <version>26.10.22</version>
</dependency>
```

## Quick start

The Java examples in this section are API fragments for application code. Use the stated imports and supply the application-owned collaborators described alongside them.

`R2dbcRevocationAdapterImpl` implements `RevocationPort` only. It does not implement `TokenIntrospectionCachePort`, and the default resource-server JWT decoder does not consult it automatically. Connect the port explicitly to the application's token-validation policy before relying on revocation.

Provide the adapter as the application's `RevocationPort` bean, wired from a Spring R2DBC `DatabaseClient`:

```java
import org.fireflyframework.security.adapter.r2dbc.R2dbcRevocationAdapterImpl;
import org.fireflyframework.security.spi.RevocationPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.r2dbc.core.DatabaseClient;

@Configuration
class SecurityRevocationConfig {

    @Bean
    RevocationPort revocationPort(DatabaseClient databaseClient) {
        return new R2dbcRevocationAdapterImpl(databaseClient);
    }
}
```

Application code (or the resource server) then uses the port:

```java
// Revoke a token until its own expiry (e.g. on logout)
revocationPort.revoke(jti, accessTokenExpiry).then(...);

// Fail-closed check during bearer validation
revocationPort.isRevoked(jti)
        .flatMap(revoked -> revoked ? Mono.error(new SecurityException("Token is revoked")) : continueChain());
```

With `spring.r2dbc.*` configured and the `security_revoked_token` table present, no further setup is needed.

## Configuration

This module declares no `@ConfigurationProperties` class of its own. Use the constructor/port contracts above and the configuration of the host or related core module.

## API overview

| Type | Contract and responsibility |
| --- | --- |
| [`R2dbcRevocationAdapterImpl`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-adapter-r2dbc/src/main/java/org/fireflyframework/security/adapter/r2dbc/R2dbcRevocationAdapterImpl.java) | Relational-database `RevocationPort` backed by R2DBC. |

## Continue the application

Use the [Security Resource Server guide](../../docs/site/modules/security-resource-server.md) for the next part of this integration.

## Build and validation

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-security-adapter-r2dbc -am verify
./mvnw -pl :fireflyframework-security-adapter-r2dbc -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Configuration details and source references: [module guide](../../docs/site/modules/security-adapter-r2dbc.md).

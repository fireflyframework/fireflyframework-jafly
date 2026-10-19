![Jafly. Security R2DBC Adapter](../assets/modules/security-adapter-r2dbc.svg)

# Security R2DBC Adapter

Persist token revocation records through a relational R2DBC adapter.

Provide DatabaseClient, apply the documented schema, and wire R2dbcRevocationAdapter into the security engine. Verify expiry and revocation queries against the same database dialect and clock behavior used in production.

| Maven artifact | Packaging | Source project |
| --- | --- | --- |
| `org.fireflyframework:fireflyframework-security-adapter-r2dbc` | `jar` | [modules/security-adapter-r2dbc](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/security-adapter-r2dbc) |

Part of [Security and identity](index.md#security-and-identity).

## In the onboarding service

Store revoked token IDs when the application uses PostgreSQL for that security state. Explicitly connect the revocation port to the JWT validation policy; registering its bean alone does not reject a token.

## Add the component

[Installation and version availability](../installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-security-adapter-r2dbc</artifactId>
    <version>26.10.19</version>
</dependency>
```

## Quick start

The Java examples in this section are API fragments for application code. Use the stated imports and supply the application-owned collaborators described alongside them.

`R2dbcRevocationAdapter` implements `RevocationPort` only. It does not implement `TokenIntrospectionCachePort`, and the default resource-server JWT decoder does not consult it automatically. Connect the port explicitly to the application's token-validation policy before relying on revocation.

Provide the adapter as the application's `RevocationPort` bean, wired from a Spring R2DBC `DatabaseClient`:

```java
import org.fireflyframework.security.adapter.r2dbc.R2dbcRevocationAdapter;
import org.fireflyframework.security.spi.RevocationPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.r2dbc.core.DatabaseClient;

@Configuration
class SecurityRevocationConfig {

    @Bean
    RevocationPort revocationPort(DatabaseClient databaseClient) {
        return new R2dbcRevocationAdapter(databaseClient);
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

## Configuration and wiring

This module declares no `@ConfigurationProperties` class of its own. Use the constructor/port contracts above and the configuration of the host or related core module.

## Public API and extension points

| Type | Contract and responsibility |
| --- | --- |
| [`R2dbcRevocationAdapter`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-adapter-r2dbc/src/main/java/org/fireflyframework/security/adapter/r2dbc/R2dbcRevocationAdapter.java) | Relational-database `RevocationPort` backed by R2DBC. |

## Related components

| Related component | Why it belongs nearby |
| --- | --- |
| [Security API](security-api.md) | Define product-neutral principals, authorization decisions, driving use cases, and the Secure annotation. |
| [Security SPI](security-spi.md) | Define driven ports for policy, relationships, revocation, keys, secrets, and other external security services. |

## Build and test

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-security-adapter-r2dbc -am verify
./mvnw -pl :fireflyframework-security-adapter-r2dbc -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Representative test sources:

- [R2dbcRevocationAdapterIntegrationTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-adapter-r2dbc/src/test/java/org/fireflyframework/security/adapter/r2dbc/R2dbcRevocationAdapterIntegrationTest.java).

Some tests use Testcontainers. A working Docker-compatible container runtime is required for those fixtures; inspect their annotations and the build output to distinguish executed tests from environment-dependent skips.

Mock, local-container, and application-context tests do not establish acceptance against a live provider account. Validate the intended provider deployment and credentials separately.

## Test fixture

The `revokeIsIdempotentUpsert` test demonstrates one concrete behavior. Excerpt from [R2dbcRevocationAdapterIntegrationTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-adapter-r2dbc/src/test/java/org/fireflyframework/security/adapter/r2dbc/R2dbcRevocationAdapterIntegrationTest.java). The test class supplies its fixtures and imports.

```java
@Test
void revokeIsIdempotentUpsert() {
    revocationPort.revoke("token-2", Instant.now().plusSeconds(120)).block();
    revocationPort.revoke("token-2", Instant.now().plusSeconds(600)).block();
    StepVerifier.create(revocationPort.isRevoked("token-2")).expectNext(true).verifyComplete();
}
```

## Detailed reference

### Where it sits in the hexagonal security platform

```
api  ->  spi  ->  core  ->  webflux  ->  resource-server  ->  adapters
```

| Layer | Module | Role |
| --- | --- | --- |
| `api` | `fireflyframework-security-api` | Driving ports + neutral domain (`SecurityPrincipal`, decisions, …). |
| `spi` | `fireflyframework-security-spi` | Driven ports, including `RevocationPort`. |
| `core` | `fireflyframework-security-core` | Vendor-neutral engine; depends on ports, never on adapters. |
| `webflux` | `fireflyframework-security-webflux` | Reactive binding of the core to Spring WebFlux. |
| `resource-server` | `fireflyframework-security-resource-server` | JWT validation and hardened response headers; revocation integration is application-owned. |
| `adapters` | `fireflyframework-security-adapter-r2dbc` (this module) | Concrete `RevocationPort` (and sibling-port) implementations. |

The shipped resource-server decoder does not automatically call `RevocationPort`. Connect `isRevoked(tokenId)` to an application-owned reactive JWT validation/authorization path, reject revoked tokens, and choose fail-closed handling for storage errors. Adding this adapter alone does not invalidate bearer tokens. This adapter depends only on `fireflyframework-security-api` and `fireflyframework-security-spi`. It never reaches back into the core or webflux layers, preserving the hexagonal dependency direction.

### What it provides

#### `R2dbcRevocationAdapter` (implements `org.fireflyframework.security.spi.RevocationPort`)

A reactive, R2DBC-backed revocation list. Constructed with a single Spring `DatabaseClient`:

```java
public R2dbcRevocationAdapter(DatabaseClient databaseClient)
```

It implements the two `RevocationPort` operations:

| Method | Behavior |
| --- | --- |
| `Mono<Void> revoke(String tokenId, Instant expiresAt)` | Idempotent upsert into `security_revoked_token` (`INSERT ... ON CONFLICT (token_id) DO UPDATE`). `expiresAt` may be `null` (bound as a SQL `NULL`), meaning the entry never self-expires; otherwise it is stored as a `TIMESTAMPTZ` at UTC. |
| `Mono<Boolean> isRevoked(String tokenId)` | Returns `true` only for an entry that exists and is still active (`expires_at IS NULL OR expires_at > now()`). An unknown token returns `false` via `defaultIfEmpty(false)`. |

Self-expiring entries. Because `isRevoked` ignores rows whose `expires_at` has passed, an expired revocation no longer counts as revoked. The token has expired anyway. This decouples correctness from cleanup: a scheduled job can prune the table at leisure (the `idx_security_revoked_token_expires_at` index supports this) without any race against active checks.

### Schema

The adapter targets a single table. A reference PostgreSQL DDL ships at
`src/main/resources/org/fireflyframework/security/adapter/r2dbc/schema-postgresql.sql`:

```sql
CREATE TABLE IF NOT EXISTS security_revoked_token (
    token_id   VARCHAR(512) PRIMARY KEY,
    expires_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_security_revoked_token_expires_at
    ON security_revoked_token (expires_at);
```

Apply it the way your application manages schema. Flyway, Liquibase, or `spring.sql.init.*`. The adapter does not create or migrate the table itself.

### Dependencies

Compile scope (kept deliberately minimal. One capability, no vendor lock-in beyond R2DBC):

- `fireflyframework-security-api`. neutral security domain.
- `fireflyframework-security-spi`. defines `RevocationPort`.
- `spring-r2dbc`. provides `DatabaseClient`.
- `slf4j-api`, `lombok` (provided).

Test scope only: `spring-boot-starter-data-r2dbc`, `spring-boot-starter-test`, `r2dbc-postgresql`, `testcontainers` (`postgresql` + `junit-jupiter`), and `reactor-test`.

### Testing

`R2dbcRevocationAdapterIntegrationTest` is a real end-to-end integration test. No mocks, no in-memory stand-in. It boots a `@SpringBootTest` against a live PostgreSQL 16 instance started by Testcontainers (`postgres:16-alpine`, Docker required), wires `spring.r2dbc.*` from the container via `@DynamicPropertySource`, and loads the schema through `spring.sql.init.mode=always`.

The test exercises the full contract of the adapter:

- a revoked, unexpired token reports `isRevoked == true`;
- an unknown token reports `false` (the not-revoked default);
- an entry whose `expires_at` is in the past no longer counts as revoked;
- `revoke` is an idempotent upsert (revoking the same `token_id` twice updates, not duplicates).

It is named `*Test` so it runs under Surefire. The module does not bind the Failsafe plugin. Running the suite requires a working Docker environment.

```bash
./mvnw -pl :fireflyframework-security-adapter-r2dbc -am test
```

## Continue the application

Use the [Security Resource Server guide](security-resource-server.md) for the next part of this integration. The runnable submission/query service is in [Getting started](../getting-started.md).

## Source and further reading

- [Maven project and dependency declarations](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-adapter-r2dbc/pom.xml).
- [Production source](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/security-adapter-r2dbc/src/main/java).
- [schema-postgresql.sql](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-adapter-r2dbc/src/main/resources/org/fireflyframework/security/adapter/r2dbc/schema-postgresql.sql).
- [Module catalog](index.md) and [getting started](../getting-started.md).
- [Maven platform](../maven.md) and [architecture](../architecture.md).

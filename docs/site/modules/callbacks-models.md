![Jafly. Callback Management Platform - Models](../assets/modules/callbacks-models.svg)

# Callback Management Platform - Models

Persist callback configurations, authorized domains, event subscriptions, and execution records with reactive repositories.

Use the repository interfaces from callbacks-core and apply the database migrations supplied by this module. These repositories use Spring Data relational/R2DBC contracts.

| Maven artifact | Packaging | Source project |
| --- | --- | --- |
| `org.fireflyframework:fireflyframework-callbacks-models` | `jar` | [modules/callbacks/fireflyframework-callbacks-models](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/callbacks/fireflyframework-callbacks-models) |

Part of [External services](index.md#external-services).

## In the onboarding service

The callback service stores destinations, subscriptions, and executions separately from onboarding application records. Apply its migrations and use the declared reactive repositories.

## Add the component

[Installation and version availability](../installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-callbacks-models</artifactId>
    <version>26.10.17-SNAPSHOT</version>
</dependency>
```

## Quick start

The Java examples in this section are API fragments for application code. Use the stated imports and supply the application-owned collaborators described alongside them.

Apply the SQL migration resources linked below before invoking the reactive repositories. Inject `CallbackConfigurationRepository` and query the subscription candidates for a domain event:

```java
Flux<CallbackConfiguration> candidates =
        repository.findActiveByEventType("payment.received");
```

The repository is in `org.fireflyframework.callbacks.models.repository`, and entities are in its sibling `entities` package. Compose the `Flux` within the service flow. Repositories also separate configuration, subscriptions, authorized domains, and executions; use their declared query methods rather than assuming that DTO fields imply a particular database lookup.

## Configuration and wiring

This module declares no `@ConfigurationProperties` class of its own. Use the constructor/port contracts above and the configuration of the host or related core module.

## Public API and extension points

| Type | Contract and responsibility |
| --- | --- |
| [`CallbackConfiguration`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/callbacks/fireflyframework-callbacks-models/src/main/java/org/fireflyframework/callbacks/models/entity/CallbackConfiguration.java) | Entity for callback configurations. |
| [`CallbackExecution`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/callbacks/fireflyframework-callbacks-models/src/main/java/org/fireflyframework/callbacks/models/entity/CallbackExecution.java) | Entity for callback execution tracking. |
| [`CallbackConfigurationRepository`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/callbacks/fireflyframework-callbacks-models/src/main/java/org/fireflyframework/callbacks/models/repository/CallbackConfigurationRepository.java) | Repository for callback configurations. |
| [`EventSubscriptionRepository`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/callbacks/fireflyframework-callbacks-models/src/main/java/org/fireflyframework/callbacks/models/repository/EventSubscriptionRepository.java) | Repository for event subscriptions. |
| [`AuthorizedDomainRepository`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/callbacks/fireflyframework-callbacks-models/src/main/java/org/fireflyframework/callbacks/models/repository/AuthorizedDomainRepository.java) | Repository for authorized domains. |

## Related components

| Related component | Why it belongs nearby |
| --- | --- |
| [Callbacks Library](callbacks.md) | Group the outbound callback platform: configuration, domain authorization, subscriptions, execution tracking, and dispatch. |
| [Callback Management Platform - Interfaces](callbacks-interfaces.md) | Share callback configuration, subscription, domain, and execution DTOs and status enums. |
| [R2DBC](r2dbc.md) | Build reactive repository filtering and pagination on Spring Data R2DBC. |

## Build and test

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-callbacks-models -am verify
./mvnw -pl :fireflyframework-callbacks-models -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

This subproject has no authored `src/test/java` suite. Its Maven build still validates the configured compilation/generation and any reactor prerequisites. Use the consuming component tests to validate integration behavior.

## API reference excerpt

Contract excerpt from [CallbackConfiguration.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/callbacks/fireflyframework-callbacks-models/src/main/java/org/fireflyframework/callbacks/models/entity/CallbackConfiguration.java). This fragment needs the full type and its imports.

```java
public class CallbackConfiguration {
    private UUID id;
    private String name;
    private String description;
    private String url;
    private HttpMethod httpMethod;
    private CallbackStatus status;
    private String[] subscribedEventTypes;
    private String customHeaders;
    private String secret;
    private Boolean signatureEnabled;
    private String signatureHeader;
    private Integer maxRetries;
    private Integer retryDelayMs;
    private Double retryBackoffMultiplier;
    private Integer timeoutMs;
    private Boolean active;
    private String tenantId;
    private String filterExpression;
    private String metadata;
    private Integer failureThreshold;
    private Integer failureCount;
    private Instant lastSuccessAt;
    private Instant lastFailureAt;
```

## Continue the application

Use the [Callback Management Platform - Core guide](callbacks-core.md) for the next part of this integration. The runnable submission/query service is in [Getting started](../getting-started.md).

## Source and further reading

- [Maven project and dependency declarations](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/callbacks/fireflyframework-callbacks-models/pom.xml).
- [Production source](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/callbacks/fireflyframework-callbacks-models/src/main/java).
- [V1__create_callback_tables.sql](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/callbacks/fireflyframework-callbacks-models/src/main/resources/db/migration/V1__create_callback_tables.sql).
- [Module catalog](index.md) and [getting started](../getting-started.md).
- [Maven platform](../maven.md) and [architecture](../architecture.md).

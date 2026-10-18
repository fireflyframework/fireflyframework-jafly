![Jafly. Callback Management Platform - Interfaces](../assets/modules/callbacks-interfaces.svg)

# Callback Management Platform - Interfaces

Share callback configuration, subscription, domain, and execution DTOs and status enums.

Use these DTOs at the service/client boundary. Construct a CallbackConfigurationDTO with its endpoint, HTTP method, and relevant policy fields, and use the enums instead of duplicating wire status strings. Persistence repositories live in callbacks-models.

| Maven artifact | Packaging | Source project |
| --- | --- | --- |
| `org.fireflyframework:fireflyframework-callbacks-interfaces` | `jar` | [modules/callbacks/fireflyframework-callbacks-interfaces](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/callbacks/fireflyframework-callbacks-interfaces) |

Part of [External services](index.md#external-services).

## In the onboarding service

Use these DTOs to configure a partner callback for application-status events. Creating a DTO alone does not persist the destination or authorize its domain.

## Add the component

[Installation and version availability](../installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-callbacks-interfaces</artifactId>
    <version>26.10.17</version>
</dependency>
```

## Quick start

The Java examples in this section are API fragments for application code. Use the stated imports and supply the application-owned collaborators described alongside them.

Use the actual Lombok builder on `CallbackConfigurationDTO` to describe a callback destination. This constructs the DTO; it does not authorize the domain, persist a subscription, or perform delivery:

```java
CallbackConfigurationDTO configuration = CallbackConfigurationDTO.builder()
        .name("Payment notifications")
        .url("https://partner.example/callbacks")
        .httpMethod(HttpMethod.POST)
        .subscribedEventTypes(new String[]{"payment.received"})
        .build();
```

The DTO is in `org.fireflyframework.callbacks.interfaces.dto`; `HttpMethod` is in `org.fireflyframework.callbacks.interfaces.enums`. Jakarta validation requires a name, HTTP(S) URL, non-null method/status, and at least one subscribed event type. Configure signature secrets and retry/timeout fields when the application requires them. Return `CallbackExecutionDTO` for execution records and use the source enums for callback/execution status values.

## Configuration and wiring

This module declares no `@ConfigurationProperties` class of its own. Use the constructor/port contracts above and the configuration of the host or related core module.

## Public API and extension points

| Type | Contract and responsibility |
| --- | --- |
| [`CallbackConfigurationDTO`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/callbacks/fireflyframework-callbacks-interfaces/src/main/java/org/fireflyframework/callbacks/interfaces/dto/CallbackConfigurationDTO.java) | DTO for callback configuration. |
| [`EventSubscriptionDTO`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/callbacks/fireflyframework-callbacks-interfaces/src/main/java/org/fireflyframework/callbacks/interfaces/dto/EventSubscriptionDTO.java) | DTO for configuring dynamic event subscriptions. |
| [`AuthorizedDomainDTO`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/callbacks/fireflyframework-callbacks-interfaces/src/main/java/org/fireflyframework/callbacks/interfaces/dto/AuthorizedDomainDTO.java) | DTO for managing authorized callback domains. |
| [`CallbackExecutionDTO`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/callbacks/fireflyframework-callbacks-interfaces/src/main/java/org/fireflyframework/callbacks/interfaces/dto/CallbackExecutionDTO.java) | DTO for callback execution tracking. |
| [`CallbackExecutionStatus`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/callbacks/fireflyframework-callbacks-interfaces/src/main/java/org/fireflyframework/callbacks/interfaces/enums/CallbackExecutionStatus.java) | Status of a callback execution attempt. |

## Related components

| Related component | Why it belongs nearby |
| --- | --- |
| [Callbacks Library](callbacks.md) | Group the outbound callback platform: configuration, domain authorization, subscriptions, execution tracking, and dispatch. |

## Build and test

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-callbacks-interfaces -am verify
./mvnw -pl :fireflyframework-callbacks-interfaces -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

This subproject has no authored `src/test/java` suite. Its Maven build still validates the configured compilation/generation and any reactor prerequisites. Use the consuming component tests to validate integration behavior.

## API reference excerpt

Contract excerpt from [CallbackConfigurationDTO.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/callbacks/fireflyframework-callbacks-interfaces/src/main/java/org/fireflyframework/callbacks/interfaces/dto/CallbackConfigurationDTO.java). This fragment needs the full type and its imports.

```java
public class CallbackConfigurationDTO {
    private UUID id;
    private String name;
    private String description;
    private String url;
    private HttpMethod httpMethod = HttpMethod.POST;
    private CallbackStatus status = CallbackStatus.ACTIVE;
    private String[] subscribedEventTypes;
    private Map<String, String> customHeaders;
    private String secret;
    private Boolean signatureEnabled = false;
    private String signatureHeader;
    private Integer maxRetries = 3;
    private Integer retryDelayMs = 1000;
    private Double retryBackoffMultiplier = 2.0;
    private Integer timeoutMs = 30000;
    private Boolean active = true;
    private String tenantId;
    private String filterExpression;
    private Map<String, Object> metadata;
    private Integer failureThreshold = 10;
    private Integer failureCount = 0;
    private Instant lastSuccessAt;
    private Instant lastFailureAt;
```

## Continue the application

Use the [Callback Management Platform - Web guide](callbacks-web.md) for the next part of this integration. The runnable submission/query service is in [Getting started](../getting-started.md).

## Source and further reading

- [Maven project and dependency declarations](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/callbacks/fireflyframework-callbacks-interfaces/pom.xml).
- [Production source](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/callbacks/fireflyframework-callbacks-interfaces/src/main/java).
- [Module catalog](index.md) and [getting started](../getting-started.md).
- [Maven platform](../maven.md) and [architecture](../architecture.md).

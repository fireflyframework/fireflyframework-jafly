![Jafly. Callback Management Platform - Core](../assets/modules/callbacks-core.svg)

# Callback Management Platform - Core

Route subscribed events and dispatch outbound HTTP callbacks with signing, retries, circuit breaking, and execution tracking.

Create a callback configuration and authorized-domain/subscription records before routing events. CallbackDispatcher accepts a configuration, event type, event UUID, and JSON payload; return its Mono in the application flow so completion and errors remain observable.

| Maven artifact | Packaging | Source project |
| --- | --- | --- |
| `org.fireflyframework:fireflyframework-callbacks-core` | `jar` | [modules/callbacks/fireflyframework-callbacks-core](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/callbacks/fireflyframework-callbacks-core) |

Part of [External services](index.md#external-services).

## In the onboarding service

Route a configured application.submitted or application.reviewed event to authorized callback destinations. The dispatcher owns the HTTP attempt; the application still needs a recovery policy for delivery failures.

## Add the component

[Installation and version availability](../installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-callbacks-core</artifactId>
    <version>26.10.17-SNAPSHOT</version>
</dependency>
```

## Quick start

The Java examples in this section are API fragments for application code. Use the stated imports and supply the application-owned collaborators described alongside them.

After persistence, authorized domains, callback configurations, and subscriptions are configured, inject `org.fireflyframework.callbacks.core.service.CallbackRouter` and route a domain event:

```java
Mono<Integer> triggered = router.routeEvent(
        "payment.received", eventId, payload, Map.of());
```

`eventId` is a `UUID` and `payload` is a Jackson `JsonNode`. The returned count describes triggered callbacks; compose the `Mono` so routing failures are observed. For direct dispatch, inject `CallbackDispatcher` and pass the selected `CallbackConfigurationDTO`, event type, event UUID, and payload to `dispatch(...)`. Use the [platform workflow](callbacks.md) to set up authorized destinations and subscriptions rather than dispatching to an unreviewed URL.

## Configuration and wiring

This module declares no `@ConfigurationProperties` class of its own. Use the constructor/port contracts above and the configuration of the host or related core module.

## Public API and extension points

| Type | Contract and responsibility |
| --- | --- |
| [`CallbackDispatcher`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/callbacks/fireflyframework-callbacks-core/src/main/java/org/fireflyframework/callbacks/core/service/CallbackDispatcher.java) | Service for dispatching HTTP callbacks to external endpoints. |
| [`CallbackRouter`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/callbacks/fireflyframework-callbacks-core/src/main/java/org/fireflyframework/callbacks/core/service/CallbackRouter.java) | Service for routing events to configured callback endpoints. |
| [`DomainAuthorizationService`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/callbacks/fireflyframework-callbacks-core/src/main/java/org/fireflyframework/callbacks/core/service/DomainAuthorizationService.java) | Service for managing authorized callback domains. |
| [`EventSubscriptionService`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/callbacks/fireflyframework-callbacks-core/src/main/java/org/fireflyframework/callbacks/core/service/EventSubscriptionService.java) | Service for managing event subscriptions. |
| [`CallbackConfigurationService`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/callbacks/fireflyframework-callbacks-core/src/main/java/org/fireflyframework/callbacks/core/service/CallbackConfigurationService.java) | Service for managing callback configurations. |

## Related components

| Related component | Why it belongs nearby |
| --- | --- |
| [Callbacks Library](callbacks.md) | Group the outbound callback platform: configuration, domain authorization, subscriptions, execution tracking, and dispatch. |
| [Callback Management Platform - Interfaces](callbacks-interfaces.md) | Share callback configuration, subscription, domain, and execution DTOs and status enums. |
| [Callback Management Platform - Models](callbacks-models.md) | Persist callback configurations, authorized domains, event subscriptions, and execution records with reactive repositories. |
| [Starter Core](starter-core.md) | Assemble core infrastructure conventions for WebClient, actuator, logging, configuration, and service discovery. |
| [Common EDA Library](eda.md) | Publish and consume events through a common envelope and transport-independent APIs. |

## Build and test

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-callbacks-core -am verify
./mvnw -pl :fireflyframework-callbacks-core -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Representative test sources:

- [MappersTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/callbacks/fireflyframework-callbacks-core/src/test/java/org/fireflyframework/callbacks/core/mapper/MappersTest.java).
- [CallbackDispatcherImplTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/callbacks/fireflyframework-callbacks-core/src/test/java/org/fireflyframework/callbacks/core/service/impl/CallbackDispatcherImplTest.java).

## Test fixture

The `testMappersCompile` test demonstrates one concrete behavior. Excerpt from [MappersTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/callbacks/fireflyframework-callbacks-core/src/test/java/org/fireflyframework/callbacks/core/mapper/MappersTest.java). The test class supplies its fixtures and imports.

```java
@Test
void testMappersCompile() {
    // This test verifies that MapStruct generated the mapper implementations
    // The actual conversion logic (String <-> Map) is tested in integration tests
    assertDoesNotThrow(() -> {
        Mappers.getMapper(AuthorizedDomainMapper.class);
        Mappers.getMapper(CallbackConfigurationMapper.class);
        Mappers.getMapper(CallbackExecutionMapper.class);
        Mappers.getMapper(EventSubscriptionMapper.class);
    });
}
```

## Continue the application

Use the [Callback Management Platform - Web guide](callbacks-web.md) for the next part of this integration. The runnable submission/query service is in [Getting started](../getting-started.md).

## Source and further reading

- [Maven project and dependency declarations](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/callbacks/fireflyframework-callbacks-core/pom.xml).
- [Production source](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/callbacks/fireflyframework-callbacks-core/src/main/java).
- [Module catalog](index.md) and [getting started](../getting-started.md).
- [Maven platform](../maven.md) and [architecture](../architecture.md).

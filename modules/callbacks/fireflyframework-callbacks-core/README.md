![Jafly. Callback Management Platform - Core](../../../docs/site/assets/modules/callbacks-core.svg)

# Callback Management Platform - Core

Route subscribed events and dispatch outbound HTTP callbacks with signing, retries, circuit breaking, and execution tracking.

Create a callback configuration and authorized-domain/subscription records before routing events. CallbackDispatcher accepts a configuration, event type, event UUID, and JSON payload; return its Mono in the application flow so completion and errors remain observable.

[Module guide](../../../docs/site/modules/callbacks-core.md) · [Jafly README](../../../README.md) · [Module catalog](../../../docs/site/modules/index.md)

## In the onboarding service

Route a configured application.submitted or application.reviewed event to authorized callback destinations. The dispatcher owns the HTTP attempt; the application still needs a recovery policy for delivery failures.

## Installation

[Installation and version availability](../../../docs/site/installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-callbacks-core</artifactId>
    <version>26.10.20-SNAPSHOT</version>
</dependency>
```

## Quick start

The Java examples in this section are API fragments for application code. Use the stated imports and supply the application-owned collaborators described alongside them.

After persistence, authorized domains, callback configurations, and subscriptions are configured, inject `org.fireflyframework.callbacks.core.service.CallbackRouter` and route a domain event:

```java
Mono<Integer> triggered = router.routeEvent(
        "payment.received", eventId, payload, Map.of());
```

`eventId` is a `UUID` and `payload` is a Jackson `JsonNode`. The returned count describes triggered callbacks; compose the `Mono` so routing failures are observed. For direct dispatch, inject `CallbackDispatcher` and pass the selected `CallbackConfigurationDTO`, event type, event UUID, and payload to `dispatch(...)`. Use the [platform workflow](../../../docs/site/modules/callbacks.md) to set up authorized destinations and subscriptions rather than dispatching to an unreviewed URL.

## Configuration

This module declares no `@ConfigurationProperties` class of its own. Use the constructor/port contracts above and the configuration of the host or related core module.

## API overview

| Type | Contract and responsibility |
| --- | --- |
| [`CallbackDispatcher`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/callbacks/fireflyframework-callbacks-core/src/main/java/org/fireflyframework/callbacks/core/service/CallbackDispatcher.java) | Service for dispatching HTTP callbacks to external endpoints. |
| [`CallbackRouter`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/callbacks/fireflyframework-callbacks-core/src/main/java/org/fireflyframework/callbacks/core/service/CallbackRouter.java) | Service for routing events to configured callback endpoints. |
| [`DomainAuthorizationService`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/callbacks/fireflyframework-callbacks-core/src/main/java/org/fireflyframework/callbacks/core/service/DomainAuthorizationService.java) | Service for managing authorized callback domains. |
| [`EventSubscriptionService`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/callbacks/fireflyframework-callbacks-core/src/main/java/org/fireflyframework/callbacks/core/service/EventSubscriptionService.java) | Service for managing event subscriptions. |
| [`CallbackConfigurationService`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/callbacks/fireflyframework-callbacks-core/src/main/java/org/fireflyframework/callbacks/core/service/CallbackConfigurationService.java) | Service for managing callback configurations. |

## Continue the application

Use the [Callback Management Platform - Web guide](../../../docs/site/modules/callbacks-web.md) for the next part of this integration.

## Build and validation

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-callbacks-core -am verify
./mvnw -pl :fireflyframework-callbacks-core -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Configuration details and source references: [module guide](../../../docs/site/modules/callbacks-core.md).

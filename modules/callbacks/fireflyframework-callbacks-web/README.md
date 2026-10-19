![Jafly. Callback Management Platform - Web](../../../docs/site/assets/modules/callbacks-web.svg)

# Callback Management Platform - Web

Expose the callback-management HTTP API and its Spring Boot application.

Run CallbackManagementApplication with the database and messaging configuration expected by callbacks-core. Use the configuration, subscription, domain, and execution controllers through the published API contract.

[Module guide](../../../docs/site/modules/callbacks-web.md) · [Jafly README](../../../README.md) · [Module catalog](../../../docs/site/modules/index.md)

## In the onboarding service

Operate this service if onboarding partners manage callbacks through HTTP. Configure its database/messaging backend, register the partner domain, and inspect execution records to distinguish registration from delivery.

## Installation

[Installation and version availability](../../../docs/site/installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-callbacks-web</artifactId>
    <version>26.10.19</version>
</dependency>
```

## Quick start

The Java examples in this section are API fragments for application code. Use the stated imports and supply the application-owned collaborators described alongside them.

Install the selected service and its reactor prerequisites, configure its database and messaging settings, and start its actual Boot application from the root:

```bash
./mvnw -pl :fireflyframework-callbacks-web -am install
./mvnw -f modules/callbacks/fireflyframework-callbacks-web/pom.xml spring-boot:run
```

The controllers expose `/api/v1/authorized-domains`, `/api/v1/callback-configurations`, `/api/v1/event-subscriptions`, and `/api/v1/callback-executions`. Create/verify the destination domain, create its callback configuration, and register subscriptions before expecting event routing to deliver callbacks. See the [platform guide](../../../docs/site/modules/callbacks.md) for request examples; use the execution routes to inspect outcomes rather than assuming registration proves delivery.

## Configuration

This module declares no `@ConfigurationProperties` class of its own. Use the constructor/port contracts above and the configuration of the host or related core module.

## API overview

| Type | Contract and responsibility |
| --- | --- |
| [`CallbackManagementApplication`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/callbacks/fireflyframework-callbacks-web/src/main/java/org/fireflyframework/callbacks/web/CallbackManagementApplication.java) | Defines `main` operations. |
| [`CallbackConfigurationController`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/callbacks/fireflyframework-callbacks-web/src/main/java/org/fireflyframework/callbacks/web/controller/CallbackConfigurationController.java) | REST controller for callback configuration management. |
| [`AuthorizedDomainController`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/callbacks/fireflyframework-callbacks-web/src/main/java/org/fireflyframework/callbacks/web/controller/AuthorizedDomainController.java) | REST controller for authorized domain management. |
| [`EventSubscriptionController`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/callbacks/fireflyframework-callbacks-web/src/main/java/org/fireflyframework/callbacks/web/controller/EventSubscriptionController.java) | REST controller for event subscription management. |
| [`CallbackExecutionController`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/callbacks/fireflyframework-callbacks-web/src/main/java/org/fireflyframework/callbacks/web/controller/CallbackExecutionController.java) | REST controller for callback execution history. |

## Continue the application

Use the [Callback Management Platform - SDK guide](../../../docs/site/modules/callbacks-sdk.md) for the next part of this integration.

## Build and validation

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-callbacks-web -am verify
./mvnw -pl :fireflyframework-callbacks-web -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Configuration details and source references: [module guide](../../../docs/site/modules/callbacks-web.md).

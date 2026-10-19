![Jafly. Webhook Management Platform - Interfaces](../../../docs/site/assets/modules/webhooks-interfaces.svg)

# Webhook Management Platform - Interfaces

Share the provider-neutral inbound webhook request and response DTOs.

Create WebhookEventDTO with providerName and raw JSON payload, then preserve event ID and request metadata across processing. WebhookResponseDTO exposes success/error factories for consistent ingestion responses.

[Module guide](../../../docs/site/modules/webhooks-interfaces.md) · [Jafly README](../../../README.md) · [Module catalog](../../../docs/site/modules/index.md)

## In the onboarding service

Carry the provider name, JSON payload, event ID, and request metadata from verification-provider ingestion to processing. Keep the same provider event identity through retries.

## Installation

[Installation and version availability](../../../docs/site/installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-webhooks-interfaces</artifactId>
    <version>26.10.20</version>
</dependency>
```

## Quick start

The Java examples in this section are API fragments for application code. Use the stated imports and supply the application-owned collaborators described alongside them.

Construct the inbound DTO with its actual builder and retain the same event ID through retries:

```java
WebhookEventDTO event = WebhookEventDTO.builder()
        .eventId(UUID.randomUUID())
        .providerName("stripe")
        .payload(payload)
        .receivedAt(Instant.now())
        .httpMethod("POST")
        .build();
```

`payload` is a Jackson `JsonNode`; the DTO lives in `org.fireflyframework.webhooks.interfaces.dto`. `stripe` is an example provider key. Populate headers, query parameters, source IP, and metadata when the HTTP boundary supplies them. `WebhookResponseDTO.success(eventId, providerName)` and `.error(eventId, providerName, message)` create the corresponding result DTOs. These data types do not themselves validate a provider signature or publish an event.

## Configuration

This module declares no `@ConfigurationProperties` class of its own. Use the constructor/port contracts above and the configuration of the host or related core module.

## API overview

| Type | Contract and responsibility |
| --- | --- |
| [`WebhookEventDTO`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/webhooks/fireflyframework-webhooks-interfaces/src/main/java/org/fireflyframework/webhooks/interfaces/dto/WebhookEventDTO.java) | DTO representing a webhook event received from any provider. |
| [`WebhookResponseDTO`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/webhooks/fireflyframework-webhooks-interfaces/src/main/java/org/fireflyframework/webhooks/interfaces/dto/WebhookResponseDTO.java) | DTO representing the response after receiving a webhook. |

## Continue the application

Use the [Webhook Management Platform - Core guide](../../../docs/site/modules/webhooks-core.md) for the next part of this integration.

## Build and validation

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-webhooks-interfaces -am verify
./mvnw -pl :fireflyframework-webhooks-interfaces -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Configuration details and source references: [module guide](../../../docs/site/modules/webhooks-interfaces.md).

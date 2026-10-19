![Jafly. Webhooks Library](../../docs/site/assets/modules/webhooks.svg)

# Webhooks Library

Group incoming webhook contracts, ingestion logic, HTTP service, processing extensions, and generated SDK.

Use webhooks-web to receive provider callbacks and webhooks-processor to consume/handle the routed events. Configure transport, signature checks, and idempotency as a complete inbound flow. Outbound deliveries belong to callbacks.

[Module guide](../../docs/site/modules/webhooks.md) · [Jafly README](../../README.md) · [Module catalog](../../docs/site/modules/index.md)

## In the onboarding service

A verification partner may send an inbound status update after an application was submitted. Connect ingestion, transport, provider verification, and an application worker as separate stages.

## Choose a component

| Related component | Why it belongs nearby |
| --- | --- |
| [Webhook Management Platform - Interfaces](../../docs/site/modules/webhooks-interfaces.md) | Share the provider-neutral inbound webhook request and response DTOs. |
| [Webhook Management Platform - Core](../../docs/site/modules/webhooks-core.md) | Validate, enrich, batch/compress, and publish incoming webhook events with retry, rate-limit, and dead-letter support. |
| [Webhook Management Platform - Web Application](../../docs/site/modules/webhooks-web.md) | Expose the reactive incoming webhook endpoint and runnable ingestion application. |
| [Webhook Processor Framework](../../docs/site/modules/webhooks-processor.md) | Implement provider-specific webhook processors, listeners, signature checks, and idempotency behavior. |
| [Webhook Management Platform - Java SDK](../../docs/site/modules/webhooks-sdk.md) | Generate a reactive Java WebClient client from the incoming webhook platform OpenAPI specification. |

## Installation

[Installation and version availability](../../docs/site/installation.md) explains source builds and published releases.

This artifact has `pom` packaging and groups child projects. Select a concrete child JAR for the application classpath.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-webhooks-interfaces</artifactId>
    <version>26.10.20-SNAPSHOT</version>
</dependency>
```

## Quick start

### Receive a partner event

Run the configured `fireflyframework-webhooks-web` service and send a fictional event to its ingestion endpoint:

```sh
curl -i -X POST http://localhost:8080/api/v1/webhook/verification-partner \
  -H 'Content-Type: application/json' \
  -d '{"id":"verification-123","type":"application.reviewed","applicationId":"example-id"}'
```

The configured transport and destination must be available. Ingestion publishes the received event for a worker; an accepted request does not establish that the application record has been updated or that a provider signature has been verified. Provider verification belongs in the configured processing path.

### Process the event

Use the [processor guide](../../docs/site/modules/webhooks-processor.md) for the complete `PartnerProcessor` and `PartnerListener` API fragments, their imports, constructor dependencies, and provider-verification requirements. They compile against the current API; they still need application wiring, the selected transport, and a real verification implementation before deployment.

The worker needs an application-owned operation that maps a verified partner event to an onboarding record. Make that operation tolerate duplicate delivery. Configure a shared idempotency store for multiple instances, and verify how failures and retry result values interact with acknowledgment. A cache marker alone cannot make the application write atomic with event processing.


## Configuration

This POM has no runtime configuration namespace. Configure the selected child libraries in the application that uses them.

## Continue the application

Use the [Webhook Processor Framework guide](../../docs/site/modules/webhooks-processor.md) for the next part of this integration.

## Build and validation

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-webhooks,:fireflyframework-webhooks-core,:fireflyframework-webhooks-interfaces,:fireflyframework-webhooks-processor,:fireflyframework-webhooks-sdk,:fireflyframework-webhooks-web -am verify
./mvnw -pl :fireflyframework-webhooks,:fireflyframework-webhooks-core,:fireflyframework-webhooks-interfaces,:fireflyframework-webhooks-processor,:fireflyframework-webhooks-sdk,:fireflyframework-webhooks-web -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Configuration details and source references: [module guide](../../docs/site/modules/webhooks.md).

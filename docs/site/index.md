---
hide:
  - navigation
  - toc
---

<div class="jafly-hero" markdown>
<div markdown>

# The layers between Spring Boot and your business rules.

Keep HTTP on Spring Boot and WebFlux. Add command and query buses, coordination engines, service clients, policy-based security, and telemetry as the service needs them.

<div class="jafly-actions">
<a class="jafly-button" href="getting-started/">Build your first project</a>
<a class="jafly-button jafly-button--secondary" href="modules/">Modules</a>
<a class="jafly-button jafly-button--secondary" href="architecture/">Architecture</a>
</div>
<p class="jafly-family"><a href="https://fireflyframework.org/frameworks/java/">jafly on Framework by Firefly</a></p>
</div>
<img class="jafly-hero__identity" src="assets/brand/jafly-logo-dark.svg" alt="jafly by Firefly">
</div>

<div class="jafly-platform">
<div><strong>Java 25</strong>Language and runtime</div>
<div><strong>Spring Boot 4.1.1</strong>Application foundation</div>
<div><strong>Spring Cloud 2025.1.3</strong>Cloud integration train</div>
<div><strong>Maven 3.9.16</strong>Checked-in wrapper</div>
</div>

<div class="jafly-paths" markdown>
<div class="jafly-path" markdown>

## Start with a working build

Build a small HTTP service, send a command through Jafly, and test its response.

[Getting started](getting-started.md)
</div>
<div class="jafly-path" markdown>

## Find the right component

Choose a library or provider for the operation you need. Check its setup requirements and examples before adding it.

[Module catalog](modules/index.md)
</div>
<div class="jafly-path" markdown>

## Understand the boundaries

See what Spring provides, what Jafly adds, and which decisions belong to your service.

[Architecture guide](architecture.md)
</div>
</div>

## Find the layer your service needs

Choose a capability below to find its contracts and provider integrations.

<div class="jafly-capabilities" markdown>
<div class="jafly-capability" markdown>

### Security and identity

Map principals to policy decisions with security contracts, a reactive resource server, method guards, and provider adapters.

[Read the guide](capabilities/security-and-identity.md)
</div>
<div class="jafly-capability" markdown>

### Business operations

Dispatch commands and queries through validation, authorization, handlers, and optional event and cache bridges.

[Read the guide](capabilities/business-operations.md)
</div>
<div class="jafly-capability" markdown>

### External services

Connect HTTP, SOAP, gRPC, notifications, callbacks, and Agentic through explicit service and provider integrations.

[Read the guide](capabilities/external-services.md)
</div>
<div class="jafly-capability" markdown>

### Event-driven integration

Use event contracts and transport adapters for in-process events, Kafka, RabbitMQ, PostgreSQL, and inbound webhooks.

[Read the guide](capabilities/event-driven-integration.md)
</div>
<div class="jafly-capability" markdown>

### Domain and data

Choose reactive data helpers, optional event history, cache providers, content storage, and signature adapters.

[Read the guide](capabilities/domain-and-data.md)
</div>
<div class="jafly-capability" markdown>

### Business coordination

Coordinate workflows, compensating sagas, and try-confirm-cancel steps with explicit recovery and failure policies.

[Read the guide](capabilities/business-coordination.md)
</div>
<div class="jafly-capability" markdown>

### Service operations

Connect application telemetry, HTTP conventions, health checks, and centralized configuration.

[Read the guide](capabilities/service-operations.md)
</div>
<div class="jafly-capability" markdown>

### Team-wide architecture

Share a dependency train, build conventions, service-layer starters, common types, validation, and extension contracts.

[Read the guide](capabilities/team-wide-architecture.md)
</div>
</div>

<div class="jafly-section" markdown>
<div markdown>

## Choose what belongs in your service

A service can use CQRS without adopting an event store, or use a client adapter without a workflow engine. Add each dependency for a specific operation.
</div>
<div markdown>

Kernel types and role-specific starters provide shared service conventions. CQRS, orchestration, and event sourcing add more structure when the application needs it.

Providers remain separate dependencies, so you can choose the cache, messaging transport, identity provider, or content store for each service. The parent and BOM align their versions while your application keeps its own version.

[Choose Maven management](maven.md)
</div>
</div>

<div class="jafly-section" markdown>
<div markdown>

## Follow one operation

In the first service, a controller accepts a customer application and sends `SubmitApplication`. The command bus validates it, runs configured authorization, and invokes the handler.
</div>
<div markdown>

```java
return commandBus.send(new SubmitApplication(request.applicantName(), request.email()));
```

The handler owns the business decision. Event publishing, cached-query invalidation, and infrastructure calls depend on the modules, beans, and annotations you configure.

[Build the onboarding service](getting-started.md) · [Read the request flow](architecture.md)
</div>
</div>

<p class="jafly-source"><a href="https://github.com/fireflyframework/fireflyframework-jafly">Source code</a> · <a href="brand/">Brand provenance and licenses</a></p>

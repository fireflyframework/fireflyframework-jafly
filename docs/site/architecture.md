# The layers around your business rules

![Jafly architecture](assets/banners/architecture.svg)

Spring Boot and WebFlux host the application and handle HTTP, dependency injection, and reactive execution. Jafly adds command and query contracts, coordination engines, and integration bridges. Your service defines the business operation, including permission checks and what happens when work fails or repeats.

## Divide the responsibilities

<picture>
  <source media="(max-width: 600px)" srcset="../assets/diagrams/service-boundaries-narrow.svg">
  <img class="jafly-diagram" src="../assets/diagrams/service-boundaries.svg" alt="Spring Boot and WebFlux own native hosting and infrastructure. Jafly supplies buses, policy ports and optional integration bridges. Your service owns business rules, permission decisions, transaction boundaries, idempotency and compensations.">
</picture>

| Layer | Responsibility | Examples |
| --- | --- | --- |
| Native Spring platform | Host the service and supply its infrastructure APIs | WebFlux routing, Spring dependency injection, Reactor, Spring Data, Spring Security |
| Jafly modules | Give operations a shared shape and connect configured components | Commands/queries, policy ports, saga/TCC engines, event bridges, adapters, observability |
| Your service | Define what the business operation means and how it behaves under failure | Handlers, permissions, trusted tenant context, transaction scope, idempotency, compensation logic |

If your team already has conventions for an application layer, you may not need the corresponding Jafly module. Choose a contract or adapter when it solves a coordination or integration problem in your service.

## Follow an onboarding application

In the [first service](getting-started.md), `POST /applications` sends `SubmitApplication` to a handler that saves and returns `OnboardingApplication`. The same path can support a larger business operation, with the service defining any persistence and failure policy. The tutorial does not enable the optional bridges shown below.

<picture>
  <source media="(max-width: 600px)" srcset="../assets/diagrams/request-flow-narrow.svg">
  <img class="jafly-diagram" src="../assets/diagrams/request-flow.svg" alt="A controller sends a command. The command bus validates it, runs configured authorization, and invokes the application handler. The reactive result returns to the caller. Configured annotations and beans can add an event bridge and query cache invalidation.">
</picture>

1. Your controller accepts input. Spring WebFlux handles HTTP routing and request validation. The controller constructs a typed command and sends it through `CommandBus`.
2. The bus prepares execution. It finds the registered handler, establishes available correlation context, validates the command, and runs command authorization when configured. Annotation validation requires a Jakarta `Validator` bean.
3. Your handler makes the decision. A concrete `CommandHandler<C, R>` executes the business operation and returns `Mono<R>`. Data writes and external calls remain explicit in that operation.
4. The result completes the call. A handler annotated for event publishing can pass its result to the configured event bridge before returning. Event publishing and query-cache behavior depend on actual annotations and beans.

The [first service tutorial](getting-started.md) builds this path and tests its HTTP response.

### Conditional bridges

The CQRS/EDA configuration creates the command-event bridge when an `EventPublisherFactory` bean exists. A `@PublishDomainEvent` handler enables the result-to-event path. Transport selection is owned by the EDA publisher factory and its configured transport.

The query-cache invalidation bridge requires the relevant cache beans and matching `@InvalidateCacheOn` event types. The bridge listens to Spring application events. Broker events reach that path only when the service connects them.

A command can still return successfully when event publication fails. The bus and bridge do not turn independent work into a distributed transaction or exactly-once processing. Define the application's transaction, retry, and idempotency policy from the chosen storage and transport behavior.

Sources: [command bus pipeline](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/cqrs/src/main/java/org/fireflyframework/cqrs/command/DefaultCommandBus.java), [bridge conditions](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/cqrs/src/main/java/org/fireflyframework/cqrs/config/CqrsEdaAutoConfiguration.java), [event publisher factory](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/eda/src/main/java/org/fireflyframework/eda/publisher/EventPublisherFactory.java).

## Find a capability

<picture>
  <source media="(max-width: 600px)" srcset="../assets/diagrams/capability-map-narrow.svg">
  <img class="jafly-diagram" src="../assets/diagrams/capability-map.svg" alt="Eight capability areas: security and identity, business operations, external services, event-driven integration, domain and data, business coordination, service operations, and team-wide architecture.">
</picture>

These illustrations group capabilities. Their positions carry no meaning about runtime dependencies, deployment, or delivery guarantees.

| Capability | Start with | Design decision |
| --- | --- | --- |
| [Security and identity](capabilities/security-and-identity.md) | [Security API](modules/security-api.md), [resource server](modules/security-resource-server.md), [policy adapters](modules/index.md#security-and-identity) | Which principal and policy source may authorize an operation? |
| [Business operations](capabilities/business-operations.md) | [CQRS](modules/cqrs.md), [business rules](modules/rule-engine.md) | Where do validation, authorization, and business decisions belong? |
| [External services](capabilities/external-services.md) | [Client](modules/client.md), [notifications](modules/notifications.md), [callbacks](modules/callbacks.md), [agentic bridge](modules/agentic-bridge.md) | Which provider contract and retry behavior fit this call? |
| [Event-driven integration](capabilities/event-driven-integration.md) | [EDA](modules/eda.md), [webhooks](modules/webhooks.md) | Which transport, envelope, and delivery behavior does the service need? |
| [Domain and data](capabilities/domain-and-data.md) | [R2DBC](modules/r2dbc.md), [event sourcing](modules/eventsourcing.md), [cache](modules/cache.md), [ECM](modules/ecm.md) | What is persisted, cached, reconstructed, or retained? |
| [Business coordination](capabilities/business-coordination.md) | [Orchestration](modules/orchestration.md) | Which steps, compensations, and recovery checks define the operation? |
| [Service operations](capabilities/service-operations.md) | [Observability](modules/observability.md), [web](modules/web.md), [configuration](modules/config-server.md) | Which metrics, traces, HTTP conventions, and configuration does the service operate? |
| [Team-wide architecture](capabilities/team-wide-architecture.md) | [Parent/BOM](maven.md), [starters](modules/index.md#team-wide-architecture), [kernel](modules/kernel.md), [plugins](modules/plugins.md) | Which shared contracts and dependency conventions should teams adopt? |

## Check each provider's behavior

Security and messaging expose shared contracts with separate provider adapters. For authorization, the application asks a policy port for a decision and the configured adapter supplies the answer. For events, the common publishing API still leaves each transport with its own failure and delivery behavior.

For example, the PostgreSQL EDA publisher uses its own connection outside a business transaction. The event-sourcing module can write its configured outbox in the event-store transaction; that is a different implemented boundary. Read the selected module's contract before relying on atomicity.

Check outbound clients in the same way. SOAP and gRPC do not necessarily use the REST client's retry and circuit-breaker policies. Select a request policy that fits whether the operation can safely repeat.

## Align the libraries, then build the service

Use the [framework parent or independent BOM](maven.md) to align library versions, and a starter when its dependency set fits your service layer. Test your provider configuration and define business policy in the application.

Choose the next dependency in the [module catalog](modules/index.md), or read the product introduction at [jafly on Framework by Firefly](https://fireflyframework.org/frameworks/java/).

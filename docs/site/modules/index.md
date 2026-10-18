# Module catalog

The customer onboarding tutorial starts with submitting and reading an application. Use this catalog when adding a capability to that service: durable storage, reviewer access, provider calls, notifications, or coordinated work. Each addition needs its own configuration and application policy.

Each guide explains the component's role, a practical entry path, configuration, public APIs, and its implementation/tests. See [getting started](../getting-started.md), [installation](../installation.md), and the [architecture guide](../architecture.md) for the wider service design.

## Security and identity

Map principals to policy decisions with security contracts, a reactive resource server, method guards, and provider adapters.

| Component | Purpose | Role |
| --- | --- | --- |
| [Security Cerbos Adapter](security-adapter-cerbos.md) | Delegate PolicyDecisionPort decisions to the Cerbos CheckResources API. | provider adapter |
| [Security OPA Adapter](security-adapter-opa.md) | Delegate policy decisions to Open Policy Agent through the security SPI. | provider adapter |
| [Security OpenFGA Adapter](security-adapter-openfga.md) | Check relationship authorization through an OpenFGA store. | provider adapter |
| [Security R2DBC Adapter](security-adapter-r2dbc.md) | Persist token revocation records through a relational R2DBC adapter. | provider adapter |
| [Security Vault Adapter](security-adapter-vault.md) | Read a configured field from HashiCorp Vault KV v2 through SecretsPort. | provider adapter |
| [Security API](security-api.md) | Define product-neutral principals, authorization decisions, driving use cases, and the Secure annotation. | shared contracts |
| [Security Authorization Server](security-authorization-server.md) | Configure a servlet OAuth2/OIDC authorization server with JWKS backed by KeyManagementPort. | security integration |
| [Security BFF](security-bff.md) | Handle browser OAuth2 login with server-side tokens, an opaque host cookie, CSRF protection, relay, and logout. | security integration |
| [Security Core](security-core.md) | Evaluate the framework security model with authority mapping, policy decisions, introspection caching, and principal projection. | security integration |
| [Identity Provider](security-idp.md) | Define identity-provider contracts and common authentication/user/group management services. | security integration |
| [IDP - AWS Cognito Implementation](security-idp-aws-cognito.md) | Implement the identity-provider adapter using Amazon Cognito user pools. | provider adapter |
| [IDP - Azure AD Implementation](security-idp-azure-ad.md) | Implement identity-provider operations for Microsoft Entra ID/Azure AD and B2C. | provider adapter |
| [IDP - Internal Database Implementation](security-idp-internal-db.md) | Implement identity-provider authentication and user/group persistence with an internal reactive database. | provider adapter |
| [IDP Keycloak](security-idp-keycloak.md) | Implement identity-provider operations against Keycloak. | provider adapter |
| [Security Method Policy](security-method-policy.md) | Enable reactive method authorization through Secure and the core authorization evaluator. | security integration |
| [Security OAuth2 Client](security-oauth2-client.md) | Provide reactive OAuth2/OIDC authorized-client support and WebClient token relay. | security integration |
| [Security Resource Server](security-resource-server.md) | Validate signed JWT bearer tokens in a reactive resource server with hardened default security behavior. | security integration |
| [Security SPI](security-spi.md) | Define driven ports for policy, relationships, revocation, keys, secrets, and other external security services. | shared contracts |
| [Security Test](security-test.md) | Create test principals and deterministic policy decisions for security tests. | test fixtures |
| [Security WebFlux Binding](security-webflux.md) | Bind the framework principal and authorization policy to reactive Spring Security/WebFlux. | security integration |

## Business operations

Dispatch commands and queries through validation, authorization, handlers, and optional event and cache bridges.

| Component | Purpose | Role |
| --- | --- | --- |
| [CQRS](cqrs.md) | Separate commands and queries with reactive dispatch, execution context, validation, and authorization hooks. | application library |
| [Rule Engine Core](rule-engine-core.md) | Parse YAML rule DSL into an AST, evaluate conditions/actions, and provide rule, constant, batch, and audit services. | application library |
| [Rule Engine Interfaces](rule-engine-interfaces.md) | Share request/response DTOs, rule definitions, validation results, and value/result enums for the rule engine. | shared contracts |
| [Rule Engine Models](rule-engine-models.md) | Persist rule definitions, constants, and audit trails with reactive repositories. | persistence models |
| [Rule Engine SDK](rule-engine-sdk.md) | Generate a reactive Java WebClient SDK for the rule engine HTTP API. | generated API client |
| [Rule Engine Web](rule-engine-web.md) | Expose HTTP APIs for evaluation, stored rule definitions, constants, batch operations, validation, and audit trails. | runnable service |
| [Rule Engine](rule-engine.md) | Group the YAML rule evaluator, public DTOs, persistence models, HTTP service, and generated SDK. | component family |

## External services

Connect HTTP, SOAP, gRPC, notifications, callbacks, and Agentic through explicit service and provider integrations.

| Component | Purpose | Role |
| --- | --- | --- |
| [Agentic Bridge Autoconfigure](agentic-bridge-autoconfigure.md) | Wire the agentic client/bridge, configured queue publishers, health indicator, and metrics into Spring Boot. | Spring wiring |
| [Agentic Bridge Core](agentic-bridge-core.md) | Invoke remote agents over REST, SSE, or WebSocket and publish queue invocations through a typed Java client. | application library |
| [Agentic Bridge Samples](agentic-bridge-samples.md) | Run example HTTP endpoints for catalog lookup, agent invocation, streaming, conversation, and queue publishing. | example application |
| [Agentic Bridge Starter](agentic-bridge-starter.md) | Bundle the agentic core, auto-configuration, WebFlux, actuator, and reactive resilience dependencies. | dependency starter |
| [Agentic Bridge](agentic-bridge.md) | Group the Java client, Spring wiring, starter, and sample for remote agent integration. | component family |
| [Callback Management Platform - Core](callbacks-core.md) | Route subscribed events and dispatch outbound HTTP callbacks with signing, retries, circuit breaking, and execution tracking. | application library |
| [Callback Management Platform - Interfaces](callbacks-interfaces.md) | Share callback configuration, subscription, domain, and execution DTOs and status enums. | shared contracts |
| [Callback Management Platform - Models](callbacks-models.md) | Persist callback configurations, authorized domains, event subscriptions, and execution records with reactive repositories. | persistence models |
| [Callback Management Platform - SDK](callbacks-sdk.md) | Generate a reactive Java WebClient SDK from the callback platform OpenAPI specification. | generated API client |
| [Callback Management Platform - Web](callbacks-web.md) | Expose the callback-management HTTP API and its Spring Boot application. | runnable service |
| [Callbacks](callbacks.md) | Group the outbound callback platform: configuration, domain authorization, subscriptions, execution tracking, and dispatch. | component family |
| [Client - Complete Example](client-complete-example.md) | Explore concrete REST, GraphQL, OAuth2, upload, and WebSocket client service examples. | example application |
| [Client](client.md) | Call REST, SOAP, and gRPC services through reactive client builders with shared resilience and observability facilities. | application library |
| [Notifications Core](notifications-core.md) | Send email, SMS, and push notifications through provider ports, with templates, preferences, and metrics. | application library |
| [Notifications](notifications.md) | Group the notification service contracts and their core implementation. | component family |
| [Notifications - Firebase](notifications-firebase.md) | Send push notifications through Firebase Cloud Messaging. | provider adapter |
| [Notifications - Resend](notifications-resend.md) | Send email through the Resend provider adapter. | provider adapter |
| [Notifications - SendGrid](notifications-sendgrid.md) | Send email through SendGrid while retaining the common notification service contract. | provider adapter |
| [Notifications - Twilio](notifications-twilio.md) | Send SMS notifications through Twilio. | provider adapter |

## Event-driven integration

Use event contracts and transport adapters for in-process events, Kafka, RabbitMQ, PostgreSQL, and inbound webhooks.

| Component | Purpose | Role |
| --- | --- | --- |
| [EDA](eda.md) | Publish and consume events through a common envelope and transport-independent APIs. | application library |
| [EDA - Kafka](eda-kafka.md) | Connect the EDA publishing and consuming abstractions to Apache Kafka. | provider adapter |
| [EDA - PostgreSQL](eda-postgres.md) | Deliver EDA events through a PostgreSQL outbox with LISTEN/NOTIFY support. | provider adapter |
| [EDA - RabbitMQ](eda-rabbitmq.md) | Connect the EDA abstractions to RabbitMQ exchanges, queues, and routing keys. | provider adapter |
| [Webhook Management Platform - Core](webhooks-core.md) | Validate, enrich, batch/compress, and publish incoming webhook events with retry, rate-limit, and dead-letter support. | application library |
| [Webhook Management Platform - Interfaces](webhooks-interfaces.md) | Share the provider-neutral inbound webhook request and response DTOs. | shared contracts |
| [Webhook Processor Framework](webhooks-processor.md) | Implement provider-specific webhook processors, listeners, signature checks, and idempotency behavior. | application library |
| [Webhook Management Platform - Java SDK](webhooks-sdk.md) | Generate a reactive Java WebClient client from the incoming webhook platform OpenAPI specification. | generated API client |
| [Webhook Management Platform - Web Application](webhooks-web.md) | Expose the reactive incoming webhook endpoint and runnable ingestion application. | runnable service |
| [Webhooks](webhooks.md) | Group incoming webhook contracts, ingestion logic, HTTP service, processing extensions, and generated SDK. | component family |

## Domain and data

Choose reactive data helpers, optional event history, cache providers, content storage, and signature adapters.

| Component | Purpose | Role |
| --- | --- | --- |
| [Cache](cache.md) | Use a common cache API with a local Caffeine default and pluggable distributed providers. | application library |
| [Cache - Hazelcast](cache-hazelcast.md) | Adapt a host-managed Hazelcast map to the common cache contract. | provider adapter |
| [Cache - JCache](cache-jcache.md) | Adapt a host-managed JCache implementation to the common cache contract. | provider adapter |
| [Cache - Postgres](cache-postgres.md) | Store cache entries in PostgreSQL through R2DBC, including expiry metadata. | provider adapter |
| [Cache - Redis](cache-redis.md) | Back the shared cache abstraction with Redis and reactive serialization. | provider adapter |
| [Enterprise Content Management](ecm.md) | Define content-management ports, adapter selection, document contracts, and resilience/observability support. | application library |
| [ECM eSignature - Adobe Sign](ecm-esignature-adobe-sign.md) | Implement the ECM signature-envelope port with Adobe Sign. | provider adapter |
| [ECM eSignature - DocuSign](ecm-esignature-docusign.md) | Implement the ECM signature-envelope port with DocuSign. | provider adapter |
| [ECM eSignature - Logalty](ecm-esignature-logalty.md) | Implement the ECM signature-envelope port with Logalty. | provider adapter |
| [ECM Storage - AWS (S3)](ecm-storage-aws.md) | Implement ECM document-content storage against Amazon S3. | provider adapter |
| [ECM Storage - Azure Blob](ecm-storage-azure.md) | Implement ECM document-content storage against Azure Blob Storage. | provider adapter |
| [Event Sourcing](eventsourcing.md) | Persist domain events, rehydrate aggregates, and manage snapshots through reactive event-store contracts. | application library |
| [R2DBC](r2dbc.md) | Build reactive repository filtering and pagination on Spring Data R2DBC. | application library |

## Business coordination

Coordinate workflows, compensating sagas, and try-confirm-cancel steps with explicit recovery and failure policies.

| Component | Purpose | Role |
| --- | --- | --- |
| [Orchestration](orchestration.md) | Coordinate workflows, compensating sagas, and try-confirm-cancel operations in a reactive application. | application library |

## Service operations

Connect application telemetry, HTTP conventions, health checks, and centralized configuration.

| Component | Purpose | Role |
| --- | --- | --- |
| [Config Server](config-server.md) | Run a Spring Cloud Config Server for centralized service configuration. | runnable service |
| [Observability](observability.md) | Provide a common metrics, tracing, health, and context-propagation foundation for framework components. | application library |
| [Web](web.md) | Handle WebFlux errors, request metadata, idempotency, CORS, and sensitive-data masking consistently. | application library |

## Team-wide architecture

Share a dependency train, build conventions, service-layer starters, common types, validation, and extension contracts.

| Component | Purpose | Role |
| --- | --- | --- |
| [Bill of Materials](bom.md) | Align the versions of the 82 managed Jafly artifacts without inheriting the build parent. | build management |
| [Build Parent](parent.md) | Inherit a consistent Java build, dependency platform, and plugin configuration for framework-based services. | build management |
| [Backoffice](backoffice.md) | Extend application-layer contexts for internal portals, impersonation, and backoffice resource controllers. | application library |
| [Kernel](kernel.md) | Share a consistent exception vocabulary across application, domain, and infrastructure boundaries. | application library |
| [Plugin API](plugin-api.md) | Define plugins, extension points, extension registration, and lifecycle contracts without the host implementation. | shared contracts |
| [Plugin Core](plugin-core.md) | Load plugins, resolve dependencies, and manage extension registration in a host process. | application library |
| [Plugins](plugins.md) | Group the API and implementation of the framework plugin system. | component family |
| [Starter Application](starter-application.md) | Build application-layer request contexts and business process coordination. | dependency starter |
| [Starter Core](starter-core.md) | Assemble core infrastructure conventions for WebClient, actuator, logging, configuration, and service discovery. | dependency starter |
| [Starter Data](starter-data.md) | Build data enrichment, job orchestration, quality, lineage, and provider operation services. | dependency starter |
| [Starter Domain](starter-domain.md) | Assemble the framework dependencies used by domain services. | dependency starter |
| [Utils](utils.md) | Render FreeMarker templates and convert rendered content to document formats; mark filter DTO fields. | application library |
| [Validators](validators.md) | Apply Jakarta Bean Validation constraints to financial, identity, contact, and date fields. | application library |

## Coordinate coverage

The catalog covers 82 framework artifacts managed by [the BOM](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/build/bom/pom.xml), the build parent/BOM, and two example applications. The upstream Spring Security dependency also managed by the BOM is external and is not counted as a framework module. POM project groups organize child artifacts; choose the concrete child JAR for a runtime dependency.

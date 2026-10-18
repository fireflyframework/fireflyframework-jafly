# Structure an onboarding service

Begin with the tested submission/query application before introducing separate service layers. Its controller accepts applicant name/email, dispatches typed commands and queries, and uses an application-owned in-memory repository.

[The runnable project](../../../docs/examples/getting-started) contains all application classes and HTTP tests. [Getting started](../../../docs/site/getting-started.md) follows their request flow. The tutorial has no remote identity provider, database, broker, signature service, or account-provisioning dependency.

## Separate responsibilities when the service grows

| Responsibility | Application code | Framework choice |
| --- | --- | --- |
| HTTP input and response | Controller, validation, error mapping | WebFlux and the web component |
| Submit or read an application | Commands, queries, handlers | CQRS |
| Durable application data | Repository implementation and schema | R2DBC helpers if appropriate |
| Applicant/reviewer access | Authentication and authorization policy | Resource server and the chosen method policy |
| Verification partner call | Partner request/response mapping and failure policy | Client plus the matching protocol |
| Applicant confirmation | Delivery decision and application message | Notifications with a configured provider |
| Multi-step review | Step effects and recovery behavior | Workflow, saga, or TCC after choosing its semantics |

A service may separate HTTP, business implementation, shared contracts, and a generated client into Maven modules. Choose that structure when the boundaries need independent dependencies or consumers; the small tutorial remains one application.

## Resolve identity and tenant context

The application starter uses `org.fireflyframework.common.application` contracts. Its controller bases work with `ContextResolver`, `ConfigResolver`, and `ApplicationExecutionContext`. Resolve identity from a validated security context and load tenant configuration through the application's configured source. A request header alone does not prove a subject or tenant.

Use [the application starter guide](../../../docs/site/modules/starter-application.md) for those contracts and its own security annotation. Use [JWT resource-server validation](../../../docs/site/modules/security-resource-server.md) for the HTTP authentication boundary.

## Add one integration at a time

[The module catalog](../../../docs/site/modules/index.md) groups provider and service choices. Give each added operation a test for the result and the failure path. The tutorial's tests establish submission, validation, retrieval, and missing-ID behavior; they do not establish the behavior of later provider integrations.

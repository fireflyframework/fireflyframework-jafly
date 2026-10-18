# Choose a service integration

An onboarding service may need a partner lookup, an event for a review worker, or a multi-step verification flow. Select the contract for that operation before choosing infrastructure.

| Need | Component | Application work |
| --- | --- | --- |
| HTTP call with shared client settings | [starter-core](../../../docs/site/modules/starter-core.md) | Configure WebClient limits and permitted header propagation. |
| REST, SOAP, or gRPC partner API | [client](../../../docs/site/modules/client.md) | Map the partner request/response and configure endpoint/authentication. |
| Event delivery to a review worker | [eda](../../../docs/site/modules/eda.md) | Choose a transport adapter, destination, consumer, and duplicate/failure policy. |
| Workflow, saga, or TCC steps | [orchestration](../../../docs/site/modules/orchestration.md) | Implement business effects and the required recovery/compensation behavior. |
| Applicant confirmation | [notifications-core](../../../docs/site/modules/notifications-core.md) | Register the channel service and configure a delivery provider. |
| Application telemetry | [observability](../../../docs/site/modules/observability.md) | Configure export and the chosen monitoring backend. |

## Start with an observed result

[The onboarding tutorial](../../../docs/site/getting-started.md) has no external provider. Keep its submission/query tests passing while adding one integration at a time. A configured client or publisher does not establish that a remote provider accepted the operation; test the request, response, and failure path for that provider.

Use [the current starter API](API.md) and [configuration reference](CONFIGURATION.md) for the HTTP layer. EDA currently supplies local events, Kafka, RabbitMQ, and PostgreSQL transport paths; it does not supply enum values or adapters for every cloud messaging service.

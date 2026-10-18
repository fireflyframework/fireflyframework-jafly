# External services

![External services](../assets/banners/capability-external-services.svg)

An onboarding decision may need an identity check from another service. After acceptance, the applicant may need a confirmation. Treat each provider call as a separate boundary with its own credentials, failure policy, and observable outcome.

## Add one integration for one need

| Onboarding need | Smallest useful starting point |
| --- | --- |
| Call a remote API | [Client](../modules/client.md), using the protocol that API supports |
| Send an email, SMS, or push notification | One [notification provider](../modules/notifications.md) and its channel service |
| Deliver subscribed events to partner HTTP endpoints | [Callbacks](../modules/callbacks.md) |
| Invoke a remote Agentic runtime | [Agentic bridge core](../modules/agentic-bridge-core.md), or its starter for Spring wiring |

These are extensions to the onboarding application. The first-service and event-store examples run without those providers.

## Follow a provider call

Start with the [client guide](../modules/client.md). Identify the actual request and response contract, then select REST, SOAP, or gRPC. Set a timeout and decide whether the operation can safely repeat before enabling a retry policy.

The [complete client example](../modules/client-complete-example.md) contains concrete protocol helpers in a standalone application. Its configuration belongs to that project. Read the required service settings before running it; a successful compilation does not establish access to a real provider.

The [REST integration test](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/client/src/test/java/org/fireflyframework/client/rest/RestClientIntegrationTest.java) shows controlled requests and responses. Test your provider's timeout, error response, and duplicate-request behavior separately. SOAP and gRPC do not automatically inherit every REST retry or circuit-breaker policy.

## Send a confirmation

For email, choose a provider such as [SendGrid](../modules/notifications-sendgrid.md) or [Resend](../modules/notifications-resend.md). Their adapters bring the core contracts transitively. Follow the [notification setup](../modules/notifications.md#quick-start) to configure the provider and explicitly register the channel service before injecting it.

Keep credentials in deployment configuration. Check the provider response and any later delivery status that the application needs. A successful method invocation is not proof that a message reached the applicant's inbox.

Use [callbacks](../modules/callbacks.md) for outbound partner subscriptions and [webhooks](../modules/webhooks.md) for inbound provider notifications. They solve different directions of communication. Agentic integration also needs its separate runtime and endpoint configuration.

If a direct Spring `WebClient` call already meets a small integration's needs, adopting a larger provider platform is optional.

Next, consider [event-driven integration](event-driven-integration.md) when another operation should react asynchronously to the onboarding result.

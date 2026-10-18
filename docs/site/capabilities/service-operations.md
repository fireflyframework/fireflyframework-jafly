# Service operations

![Service operations](../assets/banners/capability-service-operations.svg)

Once onboarding accepts real applications, operators need to know whether submissions succeed, how long reviews take, and which dependency is failing. Instrument the business path and expose only the operational endpoints the deployment needs.

## Add the operational layer you need

| Requirement | Start with |
| --- | --- |
| Shared metrics, tracing, health, and context propagation | [Observability](../modules/observability.md) |
| WebFlux error and request conventions | [Web](../modules/web.md) |
| Configuration served from a separate central service | [Config server](../modules/config-server.md) |

Keep ordinary Spring Boot configuration when it meets the service's needs. A central configuration server is a separately operated application, not a prerequisite for the onboarding tutorial.

## Instrument one business outcome

Begin with the [first service](../getting-started.md). Its HTTP tests establish the submission/read contract. Add application metrics for successful submissions and validation failures, then measure the dependency calls introduced by later capabilities.

Follow the [observability guide](../modules/observability.md) for the metrics and health support classes. The [metrics tests](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/observability/src/test/java/org/fireflyframework/observability/metrics/FireflyMetricsSupportTest.java) and [reactive context tests](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/observability/src/test/java/org/fireflyframework/observability/tracing/ReactiveContextPropagationTest.java) show the library's behavior in controlled tests.

For a coordinated review, track execution failures and recovery attempts alongside request latency. The [coordination example](../guides/onboarding-coordination.md) supplies visible provider outcomes that can become application instrumentation points.

## Configure the deployment boundary

A metrics library does not configure the monitoring backend. Select exporters, configure endpoint exposure, and confirm that the deployment can reach its telemetry destination. Test those connections with the service's actual credentials and network policy.

Health checks should describe the dependency state an operator can act on. Separate a live process from a service ready to accept work. Protect sensitive actuator and administrative endpoints through the chosen [security boundary](security-and-identity.md).

Avoid customer names, email addresses, and document content in metric tags or unrestricted logs. Use bounded outcome labels and correlation identifiers appropriate to the service's data policy.

## Test what an operator will see

Run the onboarding request tests, then trigger a controlled provider failure and inspect the resulting metric, trace, or health signal. The framework's unit tests do not prove your exporter, dashboard, alert routing, or retention settings.

The [web guide](../modules/web.md) covers shared HTTP behavior. Check its error mapping against the public API contract before adopting it, especially if clients already depend on status codes and response bodies.

Next, review [team-wide architecture](team-wide-architecture.md) when several services should share these conventions without sharing business policy.

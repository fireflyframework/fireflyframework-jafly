# Observability

Copyright 2026 Firefly Software Foundation. Licensed under the Apache License 2.0.

The bridge ships first-class observability: Micrometer metrics, a
reactive Spring Boot Actuator health indicator, and W3C Trace Context
propagation. None of these are mandatory; if Micrometer or Actuator are
absent from the classpath, the bridge degrades to a no-op.

---

## Metrics

| Instrument                                       | Type    | Tags                                       |
|--------------------------------------------------|---------|--------------------------------------------|
| `firefly.agentic.bridge.invocations`             | Counter | `client`, `agent`, `transport`, `outcome`  |
| `firefly.agentic.bridge.invocation.duration`     | Timer   | `client`, `agent`, `transport`, `outcome`  |
| `firefly.agentic.bridge.streams`                 | Counter | `client`, `agent`, `transport`, `mode`     |
| `firefly.agentic.bridge.stream.duration`         | Timer   | `client`, `agent`, `mode`, `outcome`       |
| `firefly.agentic.bridge.stream.tokens`           | Counter | `client`, `agent`, `mode`                  |
| `firefly.agentic.bridge.publish`                 | Counter | `publisher`, `transport`, `outcome`        |
| `firefly.agentic.bridge.publish.duration`        | Timer   | `publisher`, `transport`, `outcome`        |

`outcome` is one of:

- `success`
- `timeout`
- `unauthorized`
- `rate_limited`
- `not_found`
- `invocation_error`
- `transport_error`

Histogram percentiles are pre-registered for the invocation timer so
Grafana boards can plot `p50`, `p95`, `p99` without extra configuration.

---

## Health Indicator

When `spring-boot-starter-actuator` is on the classpath the autoconfigure
module registers a reactive health indicator named
`firefly-agentic-bridge`. Its payload is shaped to match Boot's standard
envelope:

```json
{
  "status": "UP",
  "details": {
    "status": "UP",
    "message": "Service is healthy",
    "latencyMs": 27,
    "checkedAt": "2026-04-01T12:34:56.789Z",
    "details": {
      "status": "ok"
    }
  }
}
```

Disable it with:

```yaml
management:
  health:
    firefly-agentic-bridge:
      enabled: false
```

When publishers are configured, the indicator aggregates the upstream
client status with every publisher's status; the worst outcome wins.

---

## Distributed tracing

When OpenTelemetry (or another tracing API that hooks into the WebClient
filter chain) is configured in the host application, the bridge:

1. Reads the active span from the Reactor `Context`.
2. Adds `traceparent` and `tracestate` HTTP headers to every outbound
   REST request.
3. Adds matching headers to every WebSocket handshake.
4. Carries the same headers as Kafka / RabbitMQ / Redis message headers
   so downstream consumers join the trace seamlessly.

No additional configuration is required.

---

## Logging

The bridge logs at `INFO` for lifecycle events (publisher start/stop,
client close), at `DEBUG` for request shapes (with sensitive headers
redacted), and at `ERROR` only when the resulting exception cannot be
mapped to a typed exception. Token values and API keys are truncated to
the last four characters when logged.

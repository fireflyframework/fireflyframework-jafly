# Configuration Reference

Copyright 2026 Firefly Software Foundation. Licensed under the Apache License 2.0.

Every setting is bound to the `firefly.agentic-bridge` prefix and surfaced
as IDE auto-completion via Spring Boot's
`spring-configuration-metadata.json`.

---

## Top-level

| Key                              | Default | Description                                                                   |
|----------------------------------|---------|-------------------------------------------------------------------------------|
| `firefly.agentic-bridge.enabled` | `true`  | Master switch. When `false`, no bridge beans are created.                     |

## `firefly.agentic-bridge.primary`

| Key                  | Default                                      | Description                                                |
|----------------------|----------------------------------------------|------------------------------------------------------------|
| `name`               | `primary`                                    | Friendly name surfaced in logs and metrics.                |
| `base-url`           | *(required)*                                 | Absolute URL of the agentic REST application.              |
| `connect-timeout`    | `5s`                                         | Reactor Netty connect timeout.                             |
| `response-timeout`   | `60s`                                        | Maximum wait per response (also used for SSE streams).     |
| `write-timeout`      | `30s`                                        | Outbound write timeout.                                    |
| `compression`        | `true`                                       | Enables gzip / deflate negotiation.                        |
| `follow-redirects`   | `false`                                      | Whether to follow 3xx redirects.                           |
| `require-tls`        | `false`                                      | When `true`, rejects `http://` base URLs at startup.       |
| `user-agent`         | `fireflyframework-agentic-bridge`            | Sent as the `User-Agent` header.                           |
| `max-in-memory-size` | `16777216`                                   | Maximum buffered response size (bytes).                    |
| `default-headers`    | `{}`                                         | Map of headers added to every outbound request.            |

## `firefly.agentic-bridge.primary.auth`

| Key              | Default       | Description                                                           |
|------------------|---------------|-----------------------------------------------------------------------|
| `type`           | `NONE`        | One of `NONE`, `API_KEY`, `BEARER`.                                   |
| `api-key-header` | `X-API-Key`   | Header used when `type=API_KEY`.                                      |
| `api-key`        | *(empty)*     | API key value when `type=API_KEY`.                                    |
| `token`          | *(empty)*     | Bearer token when `type=BEARER`.                                      |

## `firefly.agentic-bridge.primary.retry`

| Key               | Default | Description                                          |
|-------------------|---------|------------------------------------------------------|
| `enabled`         | `true`  | Toggle reactive retry.                               |
| `max-attempts`    | `3`     | Maximum retry attempts after the initial failure.    |
| `initial-backoff` | `250ms` | First backoff delay; subsequent attempts double it.  |
| `max-backoff`     | `5s`    | Cap on the backoff delay.                            |
| `jitter`          | `true`  | Randomise the backoff to avoid thundering herd.      |

The default retry filter never retries authentication failures (HTTP
401/403) and limits retries to transport failures and HTTP 5xx responses.

## `firefly.agentic-bridge.primary.circuit-breaker`

| Key                              | Default | Description                                          |
|----------------------------------|---------|------------------------------------------------------|
| `enabled`                        | `false` | Opt-in. Requires Resilience4j on the classpath.       |
| `failure-rate-threshold`         | `50`    | Percentage of failures that opens the breaker.        |
| `sliding-window-size`            | `100`   | Number of calls in the sliding window.                |
| `minimum-number-of-calls`        | `10`    | Calls required before computing the failure rate.     |
| `wait-duration-in-open-state`    | `30s`   | Time to wait before trying half-open.                 |
| `slow-call-duration-threshold`   | `2s`    | Calls slower than this count as failures.             |

## `firefly.agentic-bridge.publishers.<name>`

Define one entry per queue publisher you want to expose through
`AgenticBridge.publisher(name)`.

| Key                  | Required for                | Description                              |
|----------------------|-----------------------------|------------------------------------------|
| `type`               | always                      | `KAFKA`, `RABBITMQ`, or `REDIS`.         |
| `topic`              | `KAFKA`                     | Kafka topic name.                        |
| `bootstrap-servers`  | `KAFKA`                     | Comma-separated bootstrap servers.       |
| `exchange`           | `RABBITMQ`                  | Exchange to publish to (`""` for default).|
| `routing-key`        | `RABBITMQ`                  | Routing key.                              |
| `url`                | `RABBITMQ`, `REDIS`         | Broker connection URL.                    |
| `channel`            | `REDIS`                     | Pub/Sub channel.                          |

Example:

```yaml
firefly:
  agentic-bridge:
    publishers:
      kafka-async:
        type: KAFKA
        topic: agent-invocations
        bootstrap-servers: kafka-1:9092,kafka-2:9092
      rabbit-batch:
        type: RABBITMQ
        exchange: agent.events
        routing-key: summary.long
        url: amqp://guest:guest@rabbit:5672
      redis-stream:
        type: REDIS
        channel: agentic.stream
        url: redis://redis:6379
```

The corresponding broker driver must be on the classpath; the autoconfigure
module fails fast at startup with a clear message if a publisher is
declared but its driver is missing.

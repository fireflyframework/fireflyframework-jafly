# Firefly Framework — Agentic Bridge

[![CI](https://github.com/fireflyframework/fireflyframework-agentic-bridge/actions/workflows/ci.yml/badge.svg)](https://github.com/fireflyframework/fireflyframework-agentic-bridge/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21%2B-orange.svg)](https://openjdk.org)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-green.svg)](https://spring.io/projects/spring-boot)

> Reactive, professional Java integration layer between
> [Firefly Framework](https://github.com/fireflyframework) services and
> [`fireflyframework-agentic`](https://github.com/fireflyframework/fireflyframework-agentic),
> the Python GenAI metaframework.

---

## Table of Contents

- [Overview](#overview)
- [Why a bridge?](#why-a-bridge)
- [Module Layout](#module-layout)
- [Requirements](#requirements)
- [Installation](#installation)
- [Quick Start](#quick-start)
- [Configuration](#configuration)
- [Conversation API](#conversation-api)
- [Streaming](#streaming)
- [Queue Dispatch](#queue-dispatch)
- [Observability](#observability)
- [Security](#security)
- [Documentation](#documentation)
- [Contributing](#contributing)
- [License](#license)

---

## Overview

`fireflyframework-agentic-bridge` is the canonical Java client for
`fireflyframework-agentic`. It speaks every protocol the Python service
exposes — REST, Server-Sent Events, WebSockets, Kafka, RabbitMQ, Redis —
through a single, fluent, reactive façade.

The bridge is **not a thin HTTP wrapper**. It maps every concept on the
agentic side (multimodal prompts, conversations, streaming modes, agent
catalog, queue routing) into typed, immutable Java records that fit
naturally into Firefly Framework microservices. Resilience patterns,
observability, and Spring Boot autoconfiguration are first-class concerns.

```mermaid
graph LR
    JAVA[Spring Boot service<br/>WebFlux + Reactor] --> BRIDGE[fireflyframework-agentic-bridge]
    BRIDGE -->|REST + SSE| AGENTIC[fireflyframework-agentic<br/>FastAPI host]
    BRIDGE -->|WebSocket| AGENTIC
    BRIDGE -->|Kafka / RabbitMQ / Redis| QUEUE[Message broker]
    QUEUE --> CONSUMER[fireflyframework-agentic<br/>queue consumers]
    CONSUMER --> AGENTIC
```

---

## Why a bridge?

Java services in the Firefly ecosystem need to integrate with agentic
workloads — RAG pipelines, summarisers, classifiers, multi-step reasoning
agents — without leaking the underlying transport into business logic.
Hand-rolling a `WebClient` against the agentic REST endpoints works for
single calls but quickly degrades into copy-paste once you need streaming,
conversation IDs, queue dispatch, or distributed tracing.

This module is the professional, batteries-included answer:

- **One dependency** (`fireflyframework-agentic-bridge-starter`) wires
  everything up via Spring Boot autoconfiguration.
- **Zero stubs** — every public API has a working implementation.
- **Faithful protocol** — the on-the-wire JSON matches the Pydantic models
  in `fireflyframework_agentic.exposure.rest.schemas` exactly, so existing
  agents work without any server-side changes.
- **Reactive everywhere** — `Mono`/`Flux` end to end; no thread blocking.

---

## Module Layout

| Module                                                | Purpose                                                                       |
|-------------------------------------------------------|-------------------------------------------------------------------------------|
| `fireflyframework-agentic-bridge-core`                | SDK: builders, transports, conversation manager, exception hierarchy.         |
| `fireflyframework-agentic-bridge-autoconfigure`       | Spring Boot 3 auto-configuration, properties, Actuator health indicator.      |
| `fireflyframework-agentic-bridge-starter`             | Convenience starter that pulls in core, autoconfigure, WebFlux, Actuator.     |
| `fireflyframework-agentic-bridge-samples`             | Runnable Spring Boot sample exercising every transport.                       |

See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for the full design
document.

---

## Requirements

- Java 21+ (Java 25 default — compile with `-Pjava21` for backwards compat).
- Spring Boot 3.5.x.
- A reachable `fireflyframework-agentic` deployment exposing its REST
  application factory (`create_agentic_app`).

---

## Installation

### Spring Boot starter (recommended)

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-agentic-bridge-starter</artifactId>
    <version>26.04.01</version>
</dependency>
```

### Plain Java SDK

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-agentic-bridge-core</artifactId>
    <version>26.04.01</version>
</dependency>
```

---

## Quick Start

### Spring Boot

```yaml
firefly:
  agentic-bridge:
    primary:
      base-url: http://agentic.platform.local:8000
      auth:
        type: bearer
        token: ${AGENTIC_TOKEN}
```

```java
@Service
public class SummariserService {

    private final AgenticClient agentic;

    public SummariserService(AgenticClient agentic) {
        this.agentic = agentic;
    }

    public Mono<String> summarise(String document) {
        return agentic.invoke("summariser", AgentRequest.of(document))
                .map(response -> response.outputAsString(/* mapper */));
    }
}
```

### Plain Java

```java
AgenticClient client = AgenticClient.builder("primary")
        .baseUrl("http://agentic.platform.local:8000")
        .auth(AuthStrategy.bearer(System.getenv("AGENTIC_TOKEN")))
        .timeout(Duration.ofSeconds(60))
        .retry(r -> r.maxAttempts(3).initialBackoff(Duration.ofMillis(250)))
        .build();

AgentResponse response = client.invoke("summariser", AgentRequest.of("…"))
        .block();
```

---

## Configuration

Every setting lives under `firefly.agentic-bridge.*`. The complete
reference is in [`docs/CONFIGURATION.md`](docs/CONFIGURATION.md).

```yaml
firefly:
  agentic-bridge:
    enabled: true
    primary:
      name: primary
      base-url: http://agentic.platform.local:8000
      connect-timeout: 5s
      response-timeout: 60s
      write-timeout: 30s
      compression: true
      follow-redirects: false
      require-tls: false
      user-agent: my-service/1.0.0
      max-in-memory-size: 16777216
      auth:
        type: bearer            # or NONE / API_KEY
        token: ${AGENTIC_TOKEN}
      retry:
        enabled: true
        max-attempts: 3
        initial-backoff: 250ms
        max-backoff: 5s
        jitter: true
      circuit-breaker:
        enabled: false          # opt-in; requires resilience4j on the classpath
    publishers:
      kafka-async:
        type: KAFKA
        topic: agent-invocations
        bootstrap-servers: kafka:9092
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

---

## Conversation API

```java
ConversationManager conversations = client.conversations();

Conversation session = conversations.create().block();
client.invoke("assistant",
        AgentRequest.of("Summarise the last call.")
                .withConversationId(session.id()))
      .block();
List<ConversationMessage> history = conversations.history(session.id())
        .collectList()
        .block();
conversations.delete(session.id()).block();
```

See [`docs/CONVERSATION_API.md`](docs/CONVERSATION_API.md) for the full
walkthrough.

---

## Streaming

```java
Flux<StreamEvent> stream = client.streamIncremental("writer",
        AgentRequest.of("Draft a release note."));

stream.subscribe(event -> {
    if (event instanceof TokenEvent t) {
        System.out.print(t.text());
    } else if (event instanceof DoneEvent) {
        System.out.println();
    }
});
```

See [`docs/STREAMING.md`](docs/STREAMING.md).

---

## Queue Dispatch

```java
QueueAgenticPublisher kafka = bridge.publisher("kafka-async").orElseThrow();
kafka.publish(QueueInvocation.of("summariser",
                AgentRequest.of(longDocument))
            .withRoutingKey("summary.long")
            .withHeader("tenant", "acme"))
     .block();
```

See [`docs/QUEUE_TRANSPORT.md`](docs/QUEUE_TRANSPORT.md).

---

## Observability

The bridge ships Micrometer counters and timers for every invocation,
stream, and publish. With Spring Boot Actuator on the classpath you also
get a reactive health indicator at `/actuator/health/firefly-agentic-bridge`
that aggregates the upstream service and every queue publisher.

See [`docs/OBSERVABILITY.md`](docs/OBSERVABILITY.md) for tag reference and
sample dashboards.

---

## Security

- TLS enforcement (`require-tls=true` rejects `http://` base URLs).
- Pluggable `AuthStrategy`: API key, bearer token, supplier-driven for
  rotating credentials, and a composite chain for multi-header schemes.
- W3C Trace Context propagation (`traceparent`, `tracestate`) on every REST
  request, every WebSocket handshake, and as message headers on every
  queue dispatch.

See [`docs/SECURITY.md`](docs/SECURITY.md).

---

## Documentation

- [Architecture](docs/ARCHITECTURE.md) — design, layers, dependency flow.
- [Quick Start](docs/QUICK_START.md) — end-to-end tutorial.
- [Configuration](docs/CONFIGURATION.md) — every property reference.
- [REST Transport](docs/REST_TRANSPORT.md) — invoke + SSE streaming.
- [WebSocket Transport](docs/WEBSOCKET_TRANSPORT.md) — multi-turn conversations.
- [Queue Transport](docs/QUEUE_TRANSPORT.md) — Kafka, RabbitMQ, Redis.
- [Conversation API](docs/CONVERSATION_API.md) — server-side conversation lifecycle.
- [Streaming](docs/STREAMING.md) — buffered vs incremental modes, event hierarchy.
- [Observability](docs/OBSERVABILITY.md) — Micrometer instruments and tags.
- [Security](docs/SECURITY.md) — TLS, auth, secret handling.
- [Error Handling](docs/ERROR_HANDLING.md) — exception hierarchy and mapping.
- [Examples](docs/EXAMPLES.md) — copy-paste recipes.

---

## Contributing

Contributions are welcome! Please open an issue or pull request. The bridge
adheres to the same code style, calendar versioning, and release process as
the rest of the Firefly Framework.

---

## License

Copyright 2026 Firefly Software Foundation.

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for
details.

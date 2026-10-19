![Jafly. Agentic Bridge](../assets/modules/agentic-bridge.svg)

# Agentic Bridge

Group the Java client, Spring wiring, starter, and sample for remote agent integration.

Use agentic-bridge-starter in a Spring Boot service or agentic-bridge-core for explicit client construction. The Python agent service is operated separately; it is not merged into this Java reactor.

| Maven artifact | Packaging | Source project |
| --- | --- | --- |
| `org.fireflyframework:fireflyframework-agentic-bridge` | `pom` | [modules/agentic-bridge](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/agentic-bridge) |

Part of [External services](index.md#external-services).

## Java to agent integration

```mermaid
flowchart LR
    App[Java application] --> Client[AgenticClient]
    Client --> REST[REST invocation]
    Client --> Stream[SSE / WebSocket]
    App --> Publisher[QueueAgenticPublisher]
    Publisher --> Queue[Kafka / RabbitMQ / Redis]
    REST --> Agent[Remote agent service]
    Stream --> Agent
    Queue --> Worker[Remote queue consumer]
```

The Java bridge is a client-side integration boundary. The remote agent service and queue consumers have their own deployment and completion behavior.

## In the onboarding service

A reviewer assistant could summarize application notes through a separately deployed agent. This bridge carries requests and responses; review decisions and the use of applicant data remain application policy.

## Choose a component

| Related component | Why it belongs nearby |
| --- | --- |
| [Agentic Bridge Core](agentic-bridge-core.md) | Invoke remote agents over REST, SSE, or WebSocket and publish queue invocations through a typed Java client. |
| [Agentic Bridge Autoconfigure](agentic-bridge-autoconfigure.md) | Wire the agentic client/bridge, configured queue publishers, health indicator, and metrics into Spring Boot. |
| [Agentic Bridge Starter](agentic-bridge-starter.md) | Bundle the agentic core, auto-configuration, WebFlux, actuator, and reactive resilience dependencies. |
| [Agentic Bridge Samples](agentic-bridge-samples.md) | Run example HTTP endpoints for catalog lookup, agent invocation, streaming, conversation, and queue publishing. |

## Add the component

[Installation and version availability](../installation.md) explains source builds and published releases.

This artifact has `pom` packaging and groups child projects. Select a concrete child JAR for the application classpath.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-agentic-bridge-core</artifactId>
    <version>26.10.21-SNAPSHOT</version>
</dependency>
```

## Quick start

The injection examples use Lombok `@RequiredArgsConstructor`. Add its provided dependency and annotation processor as described in [Constructor injection](../maven.md#constructor-injection).

The Java examples in this section are API fragments for application code. Use the stated imports and supply the application-owned collaborators described alongside them.

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

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SummariserService {

    private final AgenticClient agentic;



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

## Configuration and wiring

This POM has no runtime configuration namespace. Configure the selected child libraries in the application that uses them.

## Build and test

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-agentic-bridge,:fireflyframework-agentic-bridge-autoconfigure,:fireflyframework-agentic-bridge-core,:fireflyframework-agentic-bridge-samples,:fireflyframework-agentic-bridge-starter -am verify
./mvnw -pl :fireflyframework-agentic-bridge,:fireflyframework-agentic-bridge-autoconfigure,:fireflyframework-agentic-bridge-core,:fireflyframework-agentic-bridge-samples,:fireflyframework-agentic-bridge-starter -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

This subproject has no authored `src/test/java` suite. Its Maven build still validates the configured compilation/generation and any reactor prerequisites. Use the consuming component tests to validate integration behavior.

## Detailed reference

### Overview

`fireflyframework-agentic-bridge` is the canonical Java client for
`fireflyframework-agentic`. It speaks every protocol the Python service
exposes. REST, Server-Sent Events, WebSockets, Kafka, RabbitMQ, Redis :
through a single, fluent, reactive façade.

The bridge is not a thin HTTP wrapper. It maps every concept on the
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

### Why a bridge?

Java services in the Firefly ecosystem need to integrate with agentic
workloads. RAG pipelines, summarisers, classifiers, multi-step reasoning
agents. Without leaking the underlying transport into business logic.
Hand-rolling a `WebClient` against the agentic REST endpoints works for
single calls but quickly degrades into copy-paste once you need streaming,
conversation IDs, queue dispatch, or distributed tracing.

This module is the professional, batteries-included answer:

- One dependency (`fireflyframework-agentic-bridge-starter`) wires
  everything up via Spring Boot autoconfiguration.
- Zero stubs. every public API has a working implementation.
- Faithful protocol. the on-the-wire JSON matches the Pydantic models
  in `fireflyframework_agentic.exposure.rest.schemas` exactly, so existing
  agents work without any server-side changes.
- Reactive everywhere. `Mono`/`Flux` end to end; no thread blocking.

---

### Module Layout

| Module                                                | Purpose                                                                       |
|-------------------------------------------------------|-------------------------------------------------------------------------------|
| `fireflyframework-agentic-bridge-core`                | SDK: builders, transports, conversation manager, exception hierarchy.         |
| `fireflyframework-agentic-bridge-autoconfigure`       | Spring Boot 4.1.1 auto-configuration, properties, Actuator health indicator.      |
| `fireflyframework-agentic-bridge-starter`             | Convenience starter that pulls in core, autoconfigure, WebFlux, Actuator.     |
| `fireflyframework-agentic-bridge-samples`             | Runnable Spring Boot sample exercising every transport.                       |

See [`docs/ARCHITECTURE.md`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/agentic-bridge/docs/ARCHITECTURE.md) for the full design
document.

---

### Configuration

Every setting lives under `firefly.agentic-bridge.*`. The complete
reference is in [`docs/CONFIGURATION.md`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/agentic-bridge/docs/CONFIGURATION.md).

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

### Conversation API

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

See [`docs/CONVERSATION_API.md`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/agentic-bridge/docs/CONVERSATION_API.md) for the full
walkthrough.

---

### Streaming

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

See [`docs/STREAMING.md`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/agentic-bridge/docs/STREAMING.md).

---

### Queue Dispatch

```java
QueueAgenticPublisher kafka = bridge.publisher("kafka-async").orElseThrow();
kafka.publish(QueueInvocation.of("summariser",
                AgentRequest.of(longDocument))
            .withRoutingKey("summary.long")
            .withHeader("tenant", "acme"))
     .block();
```

See [`docs/QUEUE_TRANSPORT.md`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/agentic-bridge/docs/QUEUE_TRANSPORT.md).

---

### Observability

The bridge ships Micrometer counters and timers for every invocation,
stream, and publish. With Spring Boot Actuator on the classpath you also
get a reactive health indicator at `/actuator/health/firefly-agentic-bridge`
that aggregates the upstream service and every queue publisher.

See [`docs/OBSERVABILITY.md`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/agentic-bridge/docs/OBSERVABILITY.md) for tag reference and
sample dashboards.

---

### Security

- TLS enforcement (`require-tls=true` rejects `http://` base URLs).
- Pluggable `AuthStrategy`: API key, bearer token, supplier-driven for
  rotating credentials, and a composite chain for multi-header schemes.
- W3C Trace Context propagation (`traceparent`, `tracestate`) on every REST
  request, every WebSocket handshake, and as message headers on every
  queue dispatch.

See [`docs/SECURITY.md`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/agentic-bridge/docs/SECURITY.md).

---

## Continue the application

Use the [Agentic Bridge Starter guide](agentic-bridge-starter.md) for the next part of this integration. The runnable submission/query service is in [Getting started](../getting-started.md).

## Source and further reading

- [Maven project and dependency declarations](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/agentic-bridge/pom.xml).
- [Module catalog](index.md) and [getting started](../getting-started.md).
- [Maven platform](../maven.md) and [architecture](../architecture.md).

# Queue Transport

Copyright 2026 Firefly Software Foundation. Licensed under the Apache License 2.0.

The queue transport publishes `QueueInvocation` envelopes to a message
broker for asynchronous consumption by the agentic side's queue consumers
(`KafkaAgentConsumer`, `RabbitMQAgentConsumer`, `RedisAgentConsumer`).

---

## Wire envelope

Publishers serialise the `QueueInvocation` to JSON with the following
shape:

```json
{
  "agent": "summariser",
  "request": {
    "prompt": "Summarise the document.",
    "conversation_id": "conv-123"
  },
  "routing_key": "summary.long",
  "reply_to": "summariser-replies",
  "headers": {
    "tenant": "acme",
    "traceparent": "00-..."
  }
}
```

Each broker handles framing and headers natively; the bridge always
duplicates `agent` into a broker header so existing consumers can route
messages without parsing the payload.

---

## Publishing

```java
QueueAgenticPublisher kafka = bridge.publisher("kafka-async").orElseThrow();

kafka.publish(QueueInvocation.of("summariser",
        AgentRequest.of(longDocument))
    .withRoutingKey("summary.long")
    .withHeader("tenant", "acme"))
    .block();
```

`publish(...)` returns a `Mono<Void>` that completes when the broker
acknowledges delivery.

---

## Brokers

### Kafka (`reactor-kafka`)

```yaml
firefly:
  agentic-bridge:
    publishers:
      kafka-async:
        type: KAFKA
        topic: agent-invocations
        bootstrap-servers: kafka:9092
```

The bridge uses `KafkaSender.send(...)` for end-to-end backpressure. The
`routing_key` in the invocation maps to the Kafka record key.

### RabbitMQ (`reactor-rabbitmq`)

```yaml
firefly:
  agentic-bridge:
    publishers:
      rabbit-batch:
        type: RABBITMQ
        exchange: agent.events
        routing-key: summary.long
        url: amqp://guest:guest@rabbit:5672
```

`exchange` accepts `""` for the default exchange (direct-to-queue
delivery via routing key). Per-message routing keys override the
publisher default.

### Redis (Lettuce)

```yaml
firefly:
  agentic-bridge:
    publishers:
      redis-stream:
        type: REDIS
        channel: agentic.stream
        url: redis://redis:6379
```

Uses Redis Pub/Sub. The agentic side's `RedisAgentConsumer` decodes the
`{"agent","body","headers"}` envelope to extract the embedded request.

---

## Reliability

- Publishers report a degraded `AgenticHealth.Status` after the most
  recent publish failed; the next successful publish resets the status.
- The aggregate `AgenticBridge.health()` combines the upstream client
  health with every publisher's reported health.
- Errors propagate as `AgenticTransportException` carrying the broker name
  and topic / exchange / channel in the error context.

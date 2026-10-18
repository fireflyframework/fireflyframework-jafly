# Quick Start

Copyright 2026 Firefly Software Foundation. Licensed under the Apache License 2.0.

This guide walks through wiring `fireflyframework-agentic-bridge` into a
Spring Boot service in five minutes.

---

## 1. Add the starter

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-agentic-bridge-starter</artifactId>
    <version>26.04.01</version>
</dependency>
```

The starter brings in `core`, `autoconfigure`, Spring Boot WebFlux,
Actuator, and Resilience4j Reactor support.

## 2. Configure the upstream agentic service

```yaml
firefly:
  agentic-bridge:
    primary:
      base-url: http://agentic.platform.local:8000
      auth:
        type: bearer
        token: ${AGENTIC_TOKEN}
```

If the agentic service is on the same network with no auth, set
`auth.type: NONE` (the default).

## 3. Inject and use the client

```java
@Service
@RequiredArgsConstructor
public class CallSummariser {

    private final AgenticClient agentic;
    private final ObjectMapper mapper;

    public Mono<String> summarise(String transcript) {
        return agentic.invoke("summariser", AgentRequest.of(transcript))
                .map(response -> response.outputAsString(mapper));
    }
}
```

`AgenticClient` is a Spring bean wired by the autoconfigure module. It is
thread-safe and intended to be cached for the lifetime of the application.

## 4. Stream a long answer

```java
public Flux<String> draft(String topic) {
    return agentic.streamIncremental("writer", AgentRequest.of("Draft about " + topic))
            .filter(event -> event instanceof TokenEvent)
            .cast(TokenEvent.class)
            .map(TokenEvent::text);
}
```

## 5. Multi-turn conversation

```java
public Mono<Conversation> startSession() {
    return agentic.conversations().create();
}

public Mono<AgentResponse> followUp(String conversationId, String prompt) {
    return agentic.invoke("assistant",
            AgentRequest.of(prompt).withConversationId(conversationId));
}
```

## 6. Health check

The Actuator endpoint reports the upstream service status:

```bash
$ curl http://localhost:8080/actuator/health/firefly-agentic-bridge
{
  "status": "UP",
  "details": {
    "status": "UP",
    "message": "Service is healthy",
    "latencyMs": 27
  }
}
```

That's it. Continue with [`CONFIGURATION.md`](CONFIGURATION.md) to tune
timeouts, retries, and queue publishers.

# Examples

Copyright 2026 Firefly Software Foundation. Licensed under the Apache License 2.0.

Copy-paste recipes for the common Firefly Framework integration scenarios.

---

## Single invocation in a CQRS handler

```java
@Component
@RequiredArgsConstructor
public class SummariseDocumentHandler implements CommandHandler<SummariseDocument, String> {

    private final AgenticClient agentic;
    private final ObjectMapper mapper;

    @Override
    public Mono<String> handle(SummariseDocument command) {
        return agentic.invoke("summariser",
                AgentRequest.of(command.documentText()))
            .map(response -> response.outputAsString(mapper));
    }
}
```

---

## Streaming endpoint exposed through WebFlux

```java
@RestController
@RequiredArgsConstructor
public class StreamingController {

    private final AgenticClient agentic;

    @PostMapping(value = "/draft", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> draft(@RequestBody DraftRequest request) {
        return agentic.streamIncremental("writer", AgentRequest.of(request.prompt()))
            .filter(TokenEvent.class::isInstance)
            .cast(TokenEvent.class)
            .map(event -> ServerSentEvent.builder(event.text()).build());
    }
}
```

---

## Saga step that calls an agent

```java
@SagaStep(name = "classify-claim")
public class ClassifyClaimStep implements ReactiveStep<ClaimContext, ClaimContext> {

    private final AgenticClient agentic;

    @Override
    public Mono<ClaimContext> execute(ClaimContext ctx) {
        return agentic.invoke("claim-classifier", AgentRequest.of(ctx.description()))
            .map(response -> ctx.withCategory(response.outputAsString(/* mapper */)));
    }
}
```

---

## Multi-turn assistant via WebSocket

```java
@Service
@RequiredArgsConstructor
public class ChatSession {

    private final AgenticClient agentic;

    public Flux<String> reply(String conversationId, String prompt) {
        AgentRequest request = AgentRequest.of(prompt).withConversationId(conversationId);
        return agentic.conversation("assistant", request)
            .filter(TokenEvent.class::isInstance)
            .cast(TokenEvent.class)
            .map(TokenEvent::text);
    }
}
```

---

## Asynchronous queue dispatch from a transactional outbox

```java
@Component
@RequiredArgsConstructor
public class OutboxDispatcher {

    private final AgenticBridge bridge;
    private final OutboxRepository outbox;

    public Flux<UUID> dispatch() {
        return outbox.findPending()
            .flatMap(message -> bridge.publisher("kafka-async").orElseThrow()
                .publish(QueueInvocation.of(message.agent(),
                        AgentRequest.of(message.prompt()))
                    .withRoutingKey(message.routingKey()))
                .thenReturn(message.id())
                .flatMap(outbox::markPublished));
    }
}
```

---

## Pulling the catalog at startup

```java
@Component
@RequiredArgsConstructor
public class AgentCatalogWarmup implements ApplicationRunner {

    private final AgenticClient agentic;

    @Override
    public void run(ApplicationArguments args) {
        AgentCatalog catalog = agentic.catalog().block();
        log.info("Discovered {} agents on the upstream service", catalog.size());
        catalog.agents().forEach(a -> log.info("  - {} ({})", a.name(), a.model()));
    }
}
```

---

## Combined CRUD + streaming session

```java
public Flux<String> answerInBoundedSession(String prompt) {
    return Flux.usingWhen(
        agentic.conversations().create(),
        session -> agentic.streamIncremental("assistant",
                AgentRequest.of(prompt).withConversationId(session.id())),
        session -> agentic.conversations().delete(session.id()))
        .filter(TokenEvent.class::isInstance)
        .cast(TokenEvent.class)
        .map(TokenEvent::text);
}
```

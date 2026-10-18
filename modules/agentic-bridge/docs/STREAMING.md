# Streaming

Copyright 2026 Firefly Software Foundation. Licensed under the Apache License 2.0.

The bridge supports two SSE streaming modes plus a WebSocket variant for
bidirectional sessions. All three surface results through a single
`Flux<StreamEvent>` reactive pipeline.

---

## Modes

| Mode                      | Endpoint                                            | Returned events                                   |
|---------------------------|-----------------------------------------------------|---------------------------------------------------|
| Buffered SSE              | `POST /agents/{name}/stream`                        | `ChunkEvent`* + final `DoneEvent`                 |
| Incremental SSE           | `POST /agents/{name}/stream/incremental`            | `TokenEvent`* + final `DoneEvent`                 |
| WebSocket conversation    | `/ws/agents/{name}`                                 | `TokenEvent`* + `ResultEvent` (or `ErrorEvent`)   |

`*` = repeated zero or more times.

Choose **buffered** for batch-style consumers, server-rendered HTML, or
when the upstream model emits naturally chunked output. Choose
**incremental** for chat UIs and any caller that wants the smallest
possible time-to-first-token. Choose **WebSocket** when the same session
needs multiple round-trips with full duplex framing.

---

## Event hierarchy

```java
public sealed interface StreamEvent permits
    TokenEvent, ChunkEvent, ResultEvent, ErrorEvent, DoneEvent, ConversationIdEvent {}

public record TokenEvent(String text) implements StreamEvent {}
public record ChunkEvent(String text) implements StreamEvent {}
public record ResultEvent(Object output, boolean success) implements StreamEvent {}
public record ErrorEvent(String message) implements StreamEvent {}
public record DoneEvent() implements StreamEvent {}
public record ConversationIdEvent(String conversationId) implements StreamEvent {}
```

Pattern-match with a switch expression to handle each case:

```java
events.doOnNext(event -> {
    switch (event) {
        case TokenEvent t -> consume(t.text());
        case ChunkEvent c -> consume(c.text());
        case ResultEvent r -> finalise(r.output());
        case ErrorEvent e -> reportError(e.message());
        case ConversationIdEvent c -> assignId(c.conversationId());
        case DoneEvent done -> markComplete();
    }
});
```

---

## Backpressure

The reactive pipeline propagates backpressure end-to-end. If a downstream
operator (for example, a slow client over WebFlux) pauses demand, the
bridge stops requesting frames from the upstream connection and the SSE
buffer drains through Reactor's natural request-N protocol. WebSocket
streams use Reactor Netty's flow control to apply the same pressure.

---

## Cancellation

Cancelling the subscription cancels the upstream HTTP connection
immediately. The bridge issues `Subscription.cancel()` on the underlying
WebClient response, which closes the SSE socket. There is no need to send
an explicit "stop" frame.

---

## Error handling

Errors interleave with events depending on where they arise:

- HTTP errors before the first SSE event: surfaced through the
  `Flux.error(...)` path as one of the typed `AgenticBridgeException`
  subclasses.
- Errors mid-stream (malformed SSE frame, premature close, agent failure
  after partial output): surfaced as either `AgenticStreamException` (for
  decoder failures) or as in-band `ErrorEvent` (when the agent itself
  signals the error).

Plan for both: combine `.onErrorResume(...)` with an inline filter for
`ErrorEvent` to maintain a single error-handling code path.

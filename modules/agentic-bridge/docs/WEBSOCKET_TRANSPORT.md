# WebSocket Transport

Copyright 2026 Firefly Software Foundation. Licensed under the Apache License 2.0.

The WebSocket transport targets the `/ws/agents/{name}` endpoint exposed
by the agentic REST application. It provides bidirectional, multi-turn
conversations with isolated per-connection memory scopes — ideal for
chat-style UIs.

---

## Frame protocol

Outbound frames (Java → server) are JSON envelopes:

```json
{
  "prompt": "Hello, agent!",
  "conversation_id": "conv-123",
  "deps": null,
  "model_settings": { "temperature": 0.2 }
}
```

Inbound frames (server → Java) are tagged JSON:

```json
{"type": "conversation_id", "data": "conv-123"}
{"type": "token", "data": "partial text..."}
{"type": "result", "data": "full output", "success": true}
{"type": "error", "data": "error message", "success": false}
```

The bridge maps these into the strongly-typed `StreamEvent` hierarchy:

| Frame `type`        | Java event                                     |
|---------------------|------------------------------------------------|
| `token`             | `TokenEvent(text)`                             |
| `result`            | `ResultEvent(output, success)`                 |
| `error`             | `ErrorEvent(message)`                          |
| `conversation_id`   | `ConversationIdEvent(conversationId)`          |
| (any unknown)       | `TokenEvent` carrying the raw frame payload    |

---

## Usage

```java
Flux<StreamEvent> events = client.conversation("assistant",
        AgentRequest.of("Continue the discussion.").withConversationId("conv-123"));

events.subscribe(event -> {
    switch (event) {
        case TokenEvent t -> ui.appendToken(t.text());
        case ResultEvent r -> ui.finaliseTurn(r.output());
        case ErrorEvent e -> ui.showError(e.message());
        case ConversationIdEvent c -> ui.assignConversationId(c.conversationId());
        default -> {} // DoneEvent, ChunkEvent — won't occur on this transport
    }
});
```

---

## Connection management

- One `Flux` invocation == one WebSocket session.
- The session terminates when the server emits a `result` (or `error`)
  frame and closes the socket.
- `AgenticClient.conversation(...)` is safe to call concurrently — each
  invocation opens its own short-lived session.
- The bridge sets `X-Request-ID` and the configured `AuthStrategy`
  headers on the initial WebSocket handshake.

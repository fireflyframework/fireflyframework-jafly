# REST Transport

Copyright 2026 Firefly Software Foundation. Licensed under the Apache License 2.0.

The REST transport is the primary integration surface of the bridge. It
targets the FastAPI application created by
`fireflyframework_agentic.exposure.rest.create_agentic_app()` and supports
single-shot invocations, SSE streaming (buffered and incremental), the
catalog endpoint, the conversation CRUD endpoints, and the health probe.

---

## Endpoints consumed

| Bridge method                                | Agentic endpoint                                        |
|----------------------------------------------|---------------------------------------------------------|
| `AgenticClient.invoke(name, request)`        | `POST /agents/{name}/run`                               |
| `AgenticClient.stream(name, request)`        | `POST /agents/{name}/stream`                            |
| `AgenticClient.streamIncremental(name, req)` | `POST /agents/{name}/stream/incremental`                |
| `AgenticClient.catalog()`                    | `GET /agents/`                                          |
| `AgenticClient.health()`                     | `GET /health`                                           |
| `ConversationManager.create()`               | `POST /agents/conversations`                            |
| `ConversationManager.history(id)`            | `GET /agents/conversations/{id}`                        |
| `ConversationManager.delete(id)`             | `DELETE /agents/conversations/{id}`                     |

---

## Request body

`AgentRequest` serialises to the exact pydantic model on the agentic side:

```json
{
  "prompt": "Summarise the transcript.",
  "deps": null,
  "model_settings": { "temperature": 0.2 },
  "conversation_id": "conv-123"
}
```

For multimodal prompts, pass a list of `MultiModalPart`:

```java
AgentRequest.of(List.of(
    MultiModalPart.text("Describe this image:"),
    MultiModalPart.imageUrl("https://example.com/picture.png")));
```

The `MultiModalPart` types map 1-to-1 with the agentic
`MultiModalPart.type` discriminators (`text`, `image_url`, `document_url`,
`audio_url`, `video_url`, `binary`).

---

## Response body

`AgentResponse`:

```json
{
  "agent_name": "summariser",
  "output": "The document discusses...",
  "success": true,
  "error": null,
  "metadata": {}
}
```

`outputAsString(mapper)` and `outputAs(mapper, MyDomainType.class)` decode
the `output` payload into your domain types.

---

## Error mapping

| HTTP status | Bridge exception                       |
|-------------|----------------------------------------|
| 401, 403    | `AgenticAuthenticationException`       |
| 404         | `AgentNotFoundException`               |
| 429         | `AgenticRateLimitException` (with `Retry-After` if present) |
| 5xx         | `AgenticTransportException`            |
| Other 4xx   | `AgentInvocationException`             |

`AgentResponse.success=false` returned with an HTTP 200 also raises
`AgentInvocationException`, so the success path always carries a usable
result.

---

## Headers

Every request carries:

- `User-Agent` — defaults to `fireflyframework-agentic-bridge`.
- `X-Request-ID` — fresh UUID per call (also surfaced in the exception
  context for log correlation).
- The headers contributed by the configured `AuthStrategy`.
- `traceparent` / `tracestate` (W3C Trace Context) when an OpenTelemetry
  tracer is active on the calling thread / Reactor context.

---

## Streaming

See [`STREAMING.md`](STREAMING.md) for the full event hierarchy and
buffered-vs-incremental decision matrix.

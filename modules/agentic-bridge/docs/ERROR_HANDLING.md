# Error Handling

Copyright 2026 Firefly Software Foundation. Licensed under the Apache License 2.0.

The bridge raises a strict, typed exception hierarchy that integrates
seamlessly with existing Firefly Framework error handlers (e.g. RFC 7807
mappers in `fireflyframework-web`).

---

## Hierarchy

```
java.lang.RuntimeException
└── org.fireflyframework.kernel.exception.FireflyException
    └── FireflyInfrastructureException
        └── AgenticBridgeException
            ├── AgentNotFoundException                 (HTTP 404)
            ├── AgentInvocationException               (server-side success=false or 4xx)
            ├── AgenticAuthenticationException         (HTTP 401/403)
            ├── AgenticRateLimitException              (HTTP 429)
            ├── AgenticTimeoutException                (response timeout, retries exhausted)
            ├── AgenticTransportException              (network / protocol failure)
            └── AgenticStreamException                 (SSE/WebSocket parse failure)
```

Catching `AgenticBridgeException` is enough to handle every failure mode
without boilerplate. Catching `FireflyInfrastructureException` brings
agentic failures under the same umbrella as all other infrastructure
errors in the project.

---

## Diagnostic context

Every exception carries an `AgenticErrorContext` retrievable via
`getErrorContext()`. The context exposes:

| Field        | Description                                       |
|--------------|---------------------------------------------------|
| `agentName`  | Target agent (when known).                        |
| `transport`  | `rest`, `websocket`, `kafka`, `rabbitmq`, `redis`.|
| `endpoint`   | The path or queue name the call was sent to.      |
| `httpStatus` | HTTP status code (REST transport only).           |
| `requestId`  | Correlation `X-Request-ID` for log lookup.        |
| `attributes` | Free-form key/value bag for transport-specific extras (e.g. response body excerpt). |

`getMessage()` is enriched automatically with the most useful of these
fields so log lines are usable without unpacking the context.

---

## Retry semantics

The default `RetryPolicy`:

- **Retries** transport failures and 5xx responses.
- **Does not retry** authentication or rate-limit failures.
- Uses exponential backoff with jitter (`250ms`, `500ms`, `1s`, …, capped
  at `5s`).

Override via `AgenticClientBuilder.retry(...)` or via
`firefly.agentic-bridge.primary.retry.*` in YAML.

---

## Mapping to RFC 7807 problem details

If your service uses
`fireflyframework-web`'s problem-detail filter, no extra mapping is
required: the filter resolves `FireflyInfrastructureException` to
`502 Bad Gateway` by default, with the original exception's enriched
message as the `detail` field. Customise per subclass:

```java
@RestControllerAdvice
public class AgenticBridgeProblemMapper {

    @ExceptionHandler(AgentNotFoundException.class)
    ProblemDetail handleNotFound(AgentNotFoundException ex) {
        ProblemDetail p = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        p.setTitle("Agent not found");
        p.setDetail(ex.getMessage());
        ex.getErrorContext().requestId().ifPresent(id -> p.setProperty("requestId", id));
        return p;
    }
}
```

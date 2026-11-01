# HTTP idempotency and durable business effects

HTTP response reuse is an optimization around an authorized request. The business service remains responsible for recording a durable operation key and applying its effects exactly once within its own transaction boundary. A cached response is not an operation ledger.

## Configure the boundary

1. Set `spring.application.name` or `firefly.web.idempotency.namespace`. Use an explicit service/environment namespace whenever deployments share a cache backend.
2. Authenticate and authorize the request before `IdempotencyWebFilterImpl` (order `-80`). The default resolver requires an authenticated Spring `Authentication` containing a Jafly `SecurityPrincipal` with issuer and subject. It resolves tenant through the existing `TenantResolverPort`, or through the principal's nonblank `tenantId` when that port is absent. Unresolved trust bypasses response reuse.
3. For another authentication stack, implement `IdempotencyScopeResolver` as a bean. Return a stable namespace derived from verified issuer, caller, authorized tenant, and authorization state. Return `Mono.empty()` when trust cannot be resolved. Never derive authority from unvalidated request headers.
4. Select and qualify the backing cache using `firefly.cache.*`. The HTTP cache has its own manager/prefix. Namespace isolation is still required when multiple deployments use the same backend.
5. Keep the durable operation key inside the business command. Validate ownership and authorization on every request, including retries. If authorization exists only inside the handler, disable HTTP response reuse for that endpoint or move its policy check before this filter.

## Request and response contract

| Input or condition | Behavior |
| --- | --- |
| No retry header | Handler executes normally |
| `GET`, `HEAD`, `OPTIONS`, `TRACE` | Header is ignored; no mutation-cache read or write |
| Missing application namespace or unresolved trusted caller/tenant | Handler executes without response reuse |
| Multiple, blank, or longer-than-256-character keys on an eligible mutation | `400`, before handler execution |
| Same scoped key and byte-identical request fingerprint | Successful response is replayed |
| Same scoped key with changed body, content type, accepted representation/encoding/language, content encoding, or `If-Match`/`If-None-Match` | `409`, before another handler execution |
| Concurrent identical requests in one filter instance | One handler execution; other callers await its result |
| Concurrent conflicting input | Immediate `409`, without waiting for the original response |
| Successful empty response, including `204` | Empty response is reusable and waiting callers complete |
| Handled non-success response | Concurrent waiters receive it; later retries execute again |
| Handler failure/cancellation | Waiting callers terminate; local in-flight ownership is released |
| Oversized request body | `413`, before handler execution |
| Oversized response body | Buffering fails; no successful cache entry is recorded. The business effect may already exist and must be recovered through its durable key |

The scoped key includes application namespace, trusted identity namespace, method, raw URI path, raw query, and the client key. Its `v2` prefix and SHA-256 digest avoid replaying legacy unscoped entries or storing raw identity/key material as cache keys. Default trusted identity includes issuer, subject, resolved tenant, and sorted current roles/scopes.

The request fingerprint is SHA-256 over the exact request bytes plus `Content-Type`, `Accept`, `Content-Encoding`, `Accept-Encoding`, `Accept-Language`, `If-Match`, and `If-None-Match`. Semantically equivalent JSON with different bytes conflicts deliberately. Use a different retry key for changed input. Every ordered value of each selected header is included, including later field lines. Names, cardinalities, and values are framed with explicit UTF-8 byte lengths; combining two field lines into one comma-separated line may conservatively conflict even when HTTP semantics are equivalent.

Only `2xx` responses are retained. Replays preserve status, body, content type, `Location`, `ETag`, `Content-Language`, `Content-Encoding`, `Content-Location`, `Content-Disposition`, `Last-Modified`, and `Vary`. Application-produced encoded bytes retain their complete `Content-Encoding` metadata; accepted encodings participate in the fingerprint. Cookies and authentication headers are not replayed. `Vary` is supported only when every named field belongs to the selected fingerprint headers. A response with `Vary: *` or an unsupported field is not retained; an already waiting duplicate receives `409` and must use durable business recovery. Later requests execute through the normal business handler. This includes CORS policies that add `Vary: Origin`, since Origin is outside this selected representation contract; do not assume that supplying a key makes retention active for such a route. Cache retention is configured in hours (24 by default); body limits default to 1 MiB for requests and 4 MiB for responses. This filter buffers a complete response and is unsuitable for streaming: opt out with `@DisableIdempotency` or disable the filter.

## Failure, restart, and multiple processes

Bounded response bytes are captured while writing, but status, content type, and safe replay headers are read only after successful delegate completion, including Spring `beforeCommit` callbacks. Retention decisions and waiting duplicates therefore use final committed metadata. A failed commit cannot complete a waiter with an earlier successful snapshot.

Cache writes participate in the reactive request completion; they are not detached subscriptions. The local in-flight claim remains owned until cache completion, and a second lookup closes the completed-owner handoff race. A failed best-effort cache write can still leave the original business effect committed. Subsequent requests must recover from that effect's durable operation key.

A second filter instance can replay an existing cache entry, including its request fingerprint. In-flight claims are held in memory and are not shared between processes. Two instances receiving the same key concurrently can both reach the business handler; a shared cache does not make those executions atomic. Crashes, cache outages, cache expiration, and a response limit failure require durable business reconciliation. Do not describe this feature as distributed exactly-once processing.

Restart qualification of a business journey must verify its database operation claim, original effect, status history, and recovery response separately. The framework tests exercise scoped cache metadata and HTTP behavior, not bank ledger or provider transaction finality.

## Qualification

Run from the Jafly repository root with JDK 25:

```bash
./mvnw -pl :fireflyframework-web -am verify
```

`ScopedIdempotencyWebFilterTest` covers route/method/query and issuer/subject/tenant/service isolation, changed-input rejection, concurrent same-input execution, body preservation, empty responses, failure/cancellation cleanup, delayed cache persistence, and bounded buffers. `IdempotencyHttpIntegrationTest` runs actual Reactor Netty HTTP against Spring WebFlux controller methods on a private loopback port. It verifies body delivery, cross-route and read/mutation separation, payload conflict, actual `@DisableIdempotency` behavior, raw-wire gzip decoding on original/replay, safe metadata and credential/cookie exclusions, complete multi-value header conflicts, field boundaries, unsupported Vary behavior, and final status/encoding/Vary from `beforeCommit` callbacks. Deterministic concurrent HTTP fixtures verify pending duplicates and failed commits without production test hooks. `IdempotencyAutoConfigurationTest` exercises real Caffeine cache wiring, application namespace fallback, and a custom trusted resolver.

These are local framework qualification cases. Consumer adoption requires building the exact framework source artifact, qualifying each service's authentication/tenant/authorization configuration, and repeating its real experience-to-core journey tests. A framework build alone does not establish that deployed consumers use it.

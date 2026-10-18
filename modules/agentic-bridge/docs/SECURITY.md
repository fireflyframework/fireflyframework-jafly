# Security

Copyright 2026 Firefly Software Foundation. Licensed under the Apache License 2.0.

The bridge is designed to integrate with production deployments of
`fireflyframework-agentic`, where the upstream service is typically
behind TLS and gated by API keys or bearer tokens.

---

## Transport security

- **HTTPS / WSS** is the default; configure `base-url` with `https://`
  and the WebSocket transport will automatically negotiate over `wss://`.
- Set `firefly.agentic-bridge.primary.require-tls=true` in production to
  fail fast at startup if the configured `base-url` is plaintext.
- Client SSL contexts can be customised by injecting a fully-built
  `WebClient` via `AgenticClientBuilder.webClient(...)`.

## Authentication

All four shipped strategies live in `org.fireflyframework.agentic.bridge.auth`:

| Strategy                                    | Usage                                          |
|---------------------------------------------|------------------------------------------------|
| `AuthStrategy.none()`                       | No headers (mesh-internal traffic).            |
| `AuthStrategy.apiKey(value)`                | Static `X-API-Key`.                            |
| `AuthStrategy.apiKey(supplier)`             | Dynamic key (rotated externally).              |
| `AuthStrategy.apiKey(header, supplier)`     | Custom header name.                            |
| `AuthStrategy.bearer(token)`                | Static `Authorization: Bearer …`.              |
| `AuthStrategy.bearer(supplier)`             | Dynamic bearer (e.g. OAuth2 refresh).          |
| `AuthStrategy.composite(s1, s2, …)`         | Chain multiple strategies.                     |

Auto-configuration binds the YAML `auth` block to one of these via the
`AgenticBridgeFactory.authStrategy(...)` factory method.

## Token handling

- Token values are never logged at INFO. DEBUG logs redact tokens to the
  last four characters.
- Suppliers are evaluated on **every request** so token refresh requires
  no client rebuild — wire `AuthStrategy.bearer(oauth2Helper::currentToken)`
  to the existing `OAuth2ClientHelper` in `fireflyframework-client`.

## CORS / origin checks

The bridge is a client; it does not host any HTTP endpoint of its own.
CORS configuration belongs on the agentic service side and is documented
in `fireflyframework-agentic`'s
[`docs/exposure-rest.md`](https://github.com/fireflyframework/fireflyframework-agentic/blob/main/docs/exposure-rest.md).

## Defence in depth

- Configure a Resilience4j circuit breaker
  (`firefly.agentic-bridge.primary.circuit-breaker.enabled=true`) so a
  flaky upstream cannot saturate the calling thread pool.
- Set `response-timeout` to a value smaller than the upstream service's
  load balancer timeout so the bridge never holds a half-closed
  connection.
- Confine outbound traffic with a Kubernetes NetworkPolicy or AWS
  security group; the bridge respects the JVM's `https.proxyHost` /
  `https.proxyPort` settings via Reactor Netty.

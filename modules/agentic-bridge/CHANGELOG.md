# Changelog

All notable changes to `fireflyframework-agentic-bridge` are documented in
this file. The format follows [Keep a Changelog](https://keepachangelog.com/)
and the project adheres to the calendar versioning scheme used across the
Firefly Framework (`YY.MM.PATCH`).

## [26.04.01] — 2026-05-03

### Added
- Initial public release of `fireflyframework-agentic-bridge`.
- Core SDK: `AgenticClient`, `AgenticBridge`, fluent builder, REST + SSE
  + WebSocket transports, conversation manager, agent catalog.
- Queue publishers for Apache Kafka (`reactor-kafka`), RabbitMQ
  (`reactor-rabbitmq`), and Redis Pub/Sub (Lettuce).
- Spring Boot 3 auto-configuration with `firefly.agentic-bridge.*`
  configuration properties and reactive Actuator health indicator.
- Strongly-typed exception hierarchy rooted at `AgenticBridgeException`,
  derived from `FireflyInfrastructureException`.
- Pluggable `AuthStrategy` with API key, bearer token, supplier-based, and
  composite implementations.
- Resilience: configurable reactive `RetryPolicy` with exponential
  backoff and jitter; optional Resilience4j circuit breaker integration.
- Observability: Micrometer counters and timers for invocations,
  streams, and queue dispatches; W3C Trace Context propagation.
- Multi-module Maven layout: `core`, `autoconfigure`, `starter`,
  `samples`.
- Documentation: README, ARCHITECTURE, QUICK_START, CONFIGURATION,
  REST/WEBSOCKET/QUEUE transport guides, CONVERSATION_API, STREAMING,
  OBSERVABILITY, SECURITY, ERROR_HANDLING, EXAMPLES.
- 29 passing tests across core (24, including 7 WireMock-driven REST IT),
  autoconfigure (4), and samples (1).

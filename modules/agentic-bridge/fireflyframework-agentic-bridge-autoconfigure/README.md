![Jafly. Agentic Bridge Autoconfigure](../../../docs/site/assets/modules/agentic-bridge-autoconfigure.svg)

# Agentic Bridge Autoconfigure

Wire the agentic client/bridge, configured queue publishers, health indicator, and metrics into Spring Boot.

Set firefly.agentic-bridge.primary.base-url before enabling this auto-configuration. Provide authentication and publisher settings for the remote deployment. Use the starter for the ordinary application dependency, or this module when assembling the dependencies yourself.

[Module guide](../../../docs/site/modules/agentic-bridge-autoconfigure.md) · [Jafly README](../../../README.md) · [Module catalog](../../../docs/site/modules/index.md)

## In the onboarding service

Configure the primary agent endpoint and authentication for the reviewer-assistant integration. Queue publishers are optional and require the matching remote consumers.

## Installation

[Installation and version availability](../../../docs/site/installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-agentic-bridge-autoconfigure</artifactId>
    <version>26.10.19</version>
</dependency>
```

## Quick start

The Java examples in this section are API fragments for application code. Use the stated imports and supply the application-owned collaborators described alongside them.

The primary base URL is required by `AgenticBridgeFactory`. Bind it from the environment rather than embedding deployment endpoints or credentials:

```yaml
firefly:
  agentic-bridge:
    primary:
      base-url: ${AGENTIC_BASE_URL}
      response-timeout: 60s
      auth:
        type: BEARER
        token: ${AGENTIC_TOKEN}
```

Inject `org.fireflyframework.agentic.bridge.AgenticClient` for request/stream APIs or `AgenticBridge` for the named publishers. Choose the auth type and fields that match the remote service; `NONE` is also a source-defined auth type. Override the auto-configured beans when application-specific transport construction is needed.

## Configuration

The `firefly.agentic-bridge` namespace is bound by [AgenticBridgeProperties.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/agentic-bridge/fireflyframework-agentic-bridge-autoconfigure/src/main/java/org/fireflyframework/agentic/bridge/autoconfigure/AgenticBridgeProperties.java). Nested configuration is declared in the linked class. Defaults are the field initializers.

| Setting | Java type | Source default |
| --- | --- | --- |
| `firefly.agentic-bridge.enabled` | `boolean` | `true` |
| `firefly.agentic-bridge.primary` | `Client` | `new Client()` |
| `firefly.agentic-bridge.publishers` | `Map<String, Publisher>` | `new LinkedHashMap<>()` |

## API overview

| Type | Contract and responsibility |
| --- | --- |
| [`AgenticBridgeProperties`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/agentic-bridge/fireflyframework-agentic-bridge-autoconfigure/src/main/java/org/fireflyframework/agentic/bridge/autoconfigure/AgenticBridgeProperties.java) | Spring Boot configuration binding for `firefly.agentic-bridge.*`. |
| [`AgenticBridgeFactory`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/agentic-bridge/fireflyframework-agentic-bridge-autoconfigure/src/main/java/org/fireflyframework/agentic/bridge/autoconfigure/AgenticBridgeFactory.java) | Factory that materialises framework objects from `AgenticBridgeProperties`. |
| [`AgenticBridgeAutoConfiguration`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/agentic-bridge/fireflyframework-agentic-bridge-autoconfigure/src/main/java/org/fireflyframework/agentic/bridge/autoconfigure/AgenticBridgeAutoConfiguration.java) | Defines `fireflyAgenticClient`, `fireflyAgenticBridge` operations. |
| [`AgenticBridgeHealthIndicator`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/agentic-bridge/fireflyframework-agentic-bridge-autoconfigure/src/main/java/org/fireflyframework/agentic/bridge/autoconfigure/AgenticBridgeHealthIndicator.java) | Reactive Spring Boot Actuator health indicator backed by `AgenticBridge#health()`. |
| [`AgenticBridgeHealthAutoConfiguration`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/agentic-bridge/fireflyframework-agentic-bridge-autoconfigure/src/main/java/org/fireflyframework/agentic/bridge/autoconfigure/AgenticBridgeHealthAutoConfiguration.java) | Wires the `AgenticBridgeHealthIndicator` when Spring Boot Actuator is on the classpath and the indicator hasn't been disabled via `management.health.firefly-agentic-bridge.enabled=false`. |

## Continue the application

Use the [Agentic Bridge Starter guide](../../../docs/site/modules/agentic-bridge-starter.md) for the next part of this integration.

## Build and validation

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-agentic-bridge-autoconfigure -am verify
./mvnw -pl :fireflyframework-agentic-bridge-autoconfigure -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Configuration details and source references: [module guide](../../../docs/site/modules/agentic-bridge-autoconfigure.md).

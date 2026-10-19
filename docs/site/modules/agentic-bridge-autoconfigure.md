![Jafly. Agentic Bridge Autoconfigure](../assets/modules/agentic-bridge-autoconfigure.svg)

# Agentic Bridge Autoconfigure

Wire the agentic client/bridge, configured queue publishers, health indicator, and metrics into Spring Boot.

Set firefly.agentic-bridge.primary.base-url before enabling this auto-configuration. Provide authentication and publisher settings for the remote deployment. Use the starter for the ordinary application dependency, or this module when assembling the dependencies yourself.

| Maven artifact | Packaging | Source project |
| --- | --- | --- |
| `org.fireflyframework:fireflyframework-agentic-bridge-autoconfigure` | `jar` | [modules/agentic-bridge/fireflyframework-agentic-bridge-autoconfigure](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/agentic-bridge/fireflyframework-agentic-bridge-autoconfigure) |

Part of [External services](index.md#external-services).

## In the onboarding service

Configure the primary agent endpoint and authentication for the reviewer-assistant integration. Queue publishers are optional and require the matching remote consumers.

## Add the component

[Installation and version availability](../installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-agentic-bridge-autoconfigure</artifactId>
    <version>26.10.20</version>
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

## Configuration and wiring

The `firefly.agentic-bridge` namespace is bound by [AgenticBridgeProperties.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/agentic-bridge/fireflyframework-agentic-bridge-autoconfigure/src/main/java/org/fireflyframework/agentic/bridge/autoconfigure/AgenticBridgeProperties.java). Nested configuration is declared in the linked class. Defaults are the field initializers.

| Setting | Java type | Source default |
| --- | --- | --- |
| `firefly.agentic-bridge.enabled` | `boolean` | `true` |
| `firefly.agentic-bridge.primary` | `Client` | `new Client()` |
| `firefly.agentic-bridge.publishers` | `Map<String, Publisher>` | `new LinkedHashMap<>()` |

Property-based wiring conditions found in this module:

- `prefix = "firefly.agentic-bridge", name = "enabled", havingValue = "true", matchIfMissing = true`. [AgenticBridgeAutoConfiguration.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/agentic-bridge/fireflyframework-agentic-bridge-autoconfigure/src/main/java/org/fireflyframework/agentic/bridge/autoconfigure/AgenticBridgeAutoConfiguration.java).

## Public API and extension points

| Type | Contract and responsibility |
| --- | --- |
| [`AgenticBridgeProperties`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/agentic-bridge/fireflyframework-agentic-bridge-autoconfigure/src/main/java/org/fireflyframework/agentic/bridge/autoconfigure/AgenticBridgeProperties.java) | Spring Boot configuration binding for `firefly.agentic-bridge.*`. |
| [`AgenticBridgeFactory`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/agentic-bridge/fireflyframework-agentic-bridge-autoconfigure/src/main/java/org/fireflyframework/agentic/bridge/autoconfigure/AgenticBridgeFactory.java) | Factory that materialises framework objects from `AgenticBridgeProperties`. |
| [`AgenticBridgeAutoConfiguration`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/agentic-bridge/fireflyframework-agentic-bridge-autoconfigure/src/main/java/org/fireflyframework/agentic/bridge/autoconfigure/AgenticBridgeAutoConfiguration.java) | Defines `fireflyAgenticClient`, `fireflyAgenticBridge` operations. |
| [`AgenticBridgeHealthIndicator`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/agentic-bridge/fireflyframework-agentic-bridge-autoconfigure/src/main/java/org/fireflyframework/agentic/bridge/autoconfigure/AgenticBridgeHealthIndicator.java) | Reactive Spring Boot Actuator health indicator backed by `AgenticBridge#health()`. |
| [`AgenticBridgeHealthAutoConfiguration`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/agentic-bridge/fireflyframework-agentic-bridge-autoconfigure/src/main/java/org/fireflyframework/agentic/bridge/autoconfigure/AgenticBridgeHealthAutoConfiguration.java) | Wires the `AgenticBridgeHealthIndicator` when Spring Boot Actuator is on the classpath and the indicator hasn't been disabled via `management.health.firefly-agentic-bridge.enabled=false`. |

## Related components

| Related component | Why it belongs nearby |
| --- | --- |
| [Agentic Bridge](agentic-bridge.md) | Group the Java client, Spring wiring, starter, and sample for remote agent integration. |
| [Agentic Bridge Core](agentic-bridge-core.md) | Invoke remote agents over REST, SSE, or WebSocket and publish queue invocations through a typed Java client. |

## Build and test

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-agentic-bridge-autoconfigure -am verify
./mvnw -pl :fireflyframework-agentic-bridge-autoconfigure -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Representative test sources:

- [AgenticBridgeAutoConfigurationTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/agentic-bridge/fireflyframework-agentic-bridge-autoconfigure/src/test/java/org/fireflyframework/agentic/bridge/autoconfigure/AgenticBridgeAutoConfigurationTest.java).

## Test fixture

The `registersClientAndBridgeWhenBaseUrlSet` test demonstrates one concrete behavior. Excerpt from [AgenticBridgeAutoConfigurationTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/agentic-bridge/fireflyframework-agentic-bridge-autoconfigure/src/test/java/org/fireflyframework/agentic/bridge/autoconfigure/AgenticBridgeAutoConfigurationTest.java). The test class supplies its fixtures and imports.

```java
@Test
void registersClientAndBridgeWhenBaseUrlSet() {
    runner.withPropertyValues(
                    "firefly.agentic-bridge.primary.base-url=http://localhost:9999",
                    "firefly.agentic-bridge.primary.name=primary")
            .run(ctx -> {
                assertThat(ctx).hasSingleBean(AgenticClient.class);
                assertThat(ctx).hasSingleBean(AgenticBridge.class);
                AgenticClient client = ctx.getBean(AgenticClient.class);
                assertThat(client.name()).isEqualTo("primary");
            });
}
```

## Continue the application

Use the [Agentic Bridge Starter guide](agentic-bridge-starter.md) for the next part of this integration. The runnable submission/query service is in [Getting started](../getting-started.md).

## Source and further reading

- [Maven project and dependency declarations](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/agentic-bridge/fireflyframework-agentic-bridge-autoconfigure/pom.xml).
- [Production source](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/agentic-bridge/fireflyframework-agentic-bridge-autoconfigure/src/main/java).
- [org.springframework.boot.autoconfigure.AutoConfiguration.imports](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/agentic-bridge/fireflyframework-agentic-bridge-autoconfigure/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports).
- [Module catalog](index.md) and [getting started](../getting-started.md).
- [Maven platform](../maven.md) and [architecture](../architecture.md).

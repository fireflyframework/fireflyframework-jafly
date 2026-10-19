![Jafly. Agentic Bridge Starter](../assets/modules/agentic-bridge-starter.svg)

# Agentic Bridge Starter

Bundle the agentic core, auto-configuration, WebFlux, actuator, and reactive resilience dependencies.

Add this starter to a Boot application and configure the primary agent service URL. Inject AgenticClient or AgenticBridge from core; the marker class here is not the invocation API.

| Maven artifact | Packaging | Source project |
| --- | --- | --- |
| `org.fireflyframework:fireflyframework-agentic-bridge-starter` | `jar` | [modules/agentic-bridge/fireflyframework-agentic-bridge-starter](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/agentic-bridge/fireflyframework-agentic-bridge-starter) |

Part of [External services](index.md#external-services).

## In the onboarding service

Add this bundle if the onboarding service needs Spring-managed agent client/bridge beans. Configure the remote endpoint before calling the core API; the Java starter does not run the agent service.

## Add the component

[Installation and version availability](../installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-agentic-bridge-starter</artifactId>
    <version>26.10.20-SNAPSHOT</version>
</dependency>
```

## Quick start

The Java examples in this section are API fragments for application code. Use the stated imports and supply the application-owned collaborators described alongside them.

After adding the starter, configure the primary agent service and inject the API from core:

```yaml
firefly:
  agentic-bridge:
    primary:
      base-url: ${AGENTIC_BASE_URL}
```

```java
@Service
class AgentGateway {
    private final AgenticClient client;

    AgentGateway(AgenticClient client) {
        this.client = client;
    }

    Mono<AgentResponse> invoke(String agent, String prompt) {
        return client.invoke(agent, AgentRequest.of(prompt));
    }
}
```

Import `AgenticClient` from `org.fireflyframework.agentic.bridge` and `AgentRequest`/`AgentResponse` from its `invocation` package; use Spring's `Service` and Reactor's `Mono`. Configure authentication in the [auto-configuration guide](agentic-bridge-autoconfigure.md) for a protected remote service. The starter itself declares only a marker type; the invocation API is supplied by core.

## Configuration and wiring

This module declares no `@ConfigurationProperties` class of its own. Use the constructor/port contracts above and the configuration of the host or related core module.

## Public API and extension points

| Type | Contract and responsibility |
| --- | --- |
| [`AgenticBridgeStarter`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/agentic-bridge/fireflyframework-agentic-bridge-starter/src/main/java/org/fireflyframework/agentic/bridge/starter/AgenticBridgeStarter.java) | Marker class so the starter artefact is a non-empty `jar` and Maven can attach javadoc / sources to it. |

## Related components

| Related component | Why it belongs nearby |
| --- | --- |
| [Agentic Bridge](agentic-bridge.md) | Group the Java client, Spring wiring, starter, and sample for remote agent integration. |
| [Agentic Bridge Core](agentic-bridge-core.md) | Invoke remote agents over REST, SSE, or WebSocket and publish queue invocations through a typed Java client. |
| [Agentic Bridge Autoconfigure](agentic-bridge-autoconfigure.md) | Wire the agentic client/bridge, configured queue publishers, health indicator, and metrics into Spring Boot. |
| [Observability](observability.md) | Provide a common metrics, tracing, health, and context-propagation foundation for framework components. |

## Build and test

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-agentic-bridge-starter -am verify
./mvnw -pl :fireflyframework-agentic-bridge-starter -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

This subproject has no authored `src/test/java` suite. Its Maven build still validates the configured compilation/generation and any reactor prerequisites. Use the consuming component tests to validate integration behavior.

## API reference excerpt

Contract excerpt from [AgenticBridgeStarter.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/agentic-bridge/fireflyframework-agentic-bridge-starter/src/main/java/org/fireflyframework/agentic/bridge/starter/AgenticBridgeStarter.java). This fragment needs the full type and its imports.

```java
public final class AgenticBridgeStarter {
    private AgenticBridgeStarter() {
    }
}
```

## Continue the application

Use the [Agentic Bridge Samples guide](agentic-bridge-samples.md) for the next part of this integration. The runnable submission/query service is in [Getting started](../getting-started.md).

## Source and further reading

- [Maven project and dependency declarations](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/agentic-bridge/fireflyframework-agentic-bridge-starter/pom.xml).
- [Production source](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/agentic-bridge/fireflyframework-agentic-bridge-starter/src/main/java).
- [Module catalog](index.md) and [getting started](../getting-started.md).
- [Maven platform](../maven.md) and [architecture](../architecture.md).

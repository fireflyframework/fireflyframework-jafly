![Jafly. Agentic Bridge Starter](../../../docs/site/assets/modules/agentic-bridge-starter.svg)

# Agentic Bridge Starter

Bundle the agentic core, auto-configuration, WebFlux, actuator, and reactive resilience dependencies.

Add this starter to a Boot application and configure the primary agent service URL. Inject AgenticClient or AgenticBridge from core; the marker class here is not the invocation API.

[Module guide](../../../docs/site/modules/agentic-bridge-starter.md) · [Jafly README](../../../README.md) · [Module catalog](../../../docs/site/modules/index.md)

## In the onboarding service

Add this bundle if the onboarding service needs Spring-managed agent client/bridge beans. Configure the remote endpoint before calling the core API; the Java starter does not run the agent service.

## Installation

[Installation and version availability](../../../docs/site/installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-agentic-bridge-starter</artifactId>
    <version>26.10.17-SNAPSHOT</version>
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

Import `AgenticClient` from `org.fireflyframework.agentic.bridge` and `AgentRequest`/`AgentResponse` from its `invocation` package; use Spring's `Service` and Reactor's `Mono`. Configure authentication in the [auto-configuration guide](../../../docs/site/modules/agentic-bridge-autoconfigure.md) for a protected remote service. The starter itself declares only a marker type; the invocation API is supplied by core.

## Configuration

This module declares no `@ConfigurationProperties` class of its own. Use the constructor/port contracts above and the configuration of the host or related core module.

## API overview

| Type | Contract and responsibility |
| --- | --- |
| [`AgenticBridgeStarter`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/agentic-bridge/fireflyframework-agentic-bridge-starter/src/main/java/org/fireflyframework/agentic/bridge/starter/AgenticBridgeStarter.java) | Marker class so the starter artefact is a non-empty `jar` and Maven can attach javadoc / sources to it. |

## Continue the application

Use the [Agentic Bridge Samples guide](../../../docs/site/modules/agentic-bridge-samples.md) for the next part of this integration.

## Build and validation

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-agentic-bridge-starter -am verify
./mvnw -pl :fireflyframework-agentic-bridge-starter -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Configuration details and source references: [module guide](../../../docs/site/modules/agentic-bridge-starter.md).

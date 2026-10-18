![Jafly. Agentic Bridge](../../docs/site/assets/modules/agentic-bridge.svg)

# Agentic Bridge

Group the Java client, Spring wiring, starter, and sample for remote agent integration.

Use agentic-bridge-starter in a Spring Boot service or agentic-bridge-core for explicit client construction. The Python agent service is operated separately; it is not merged into this Java reactor.

[Module guide](../../docs/site/modules/agentic-bridge.md) · [Jafly README](../../README.md) · [Module catalog](../../docs/site/modules/index.md)

## In the onboarding service

A reviewer assistant could summarize application notes through a separately deployed agent. This bridge carries requests and responses; review decisions and the use of applicant data remain application policy.

## Choose a component

| Related component | Why it belongs nearby |
| --- | --- |
| [Agentic Bridge Core](../../docs/site/modules/agentic-bridge-core.md) | Invoke remote agents over REST, SSE, or WebSocket and publish queue invocations through a typed Java client. |
| [Agentic Bridge Autoconfigure](../../docs/site/modules/agentic-bridge-autoconfigure.md) | Wire the agentic client/bridge, configured queue publishers, health indicator, and metrics into Spring Boot. |
| [Agentic Bridge Starter](../../docs/site/modules/agentic-bridge-starter.md) | Bundle the agentic core, auto-configuration, WebFlux, actuator, and reactive resilience dependencies. |
| [Agentic Bridge Samples](../../docs/site/modules/agentic-bridge-samples.md) | Run example HTTP endpoints for catalog lookup, agent invocation, streaming, conversation, and queue publishing. |

## Installation

[Installation and version availability](../../docs/site/installation.md) explains source builds and published releases.

This artifact has `pom` packaging and groups child projects. Select a concrete child JAR for the application classpath.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-agentic-bridge-core</artifactId>
    <version>26.10.17-SNAPSHOT</version>
</dependency>
```

## Quick start

The Java examples in this section are API fragments for application code. Use the stated imports and supply the application-owned collaborators described alongside them.

### Spring Boot

```yaml
firefly:
  agentic-bridge:
    primary:
      base-url: http://agentic.platform.local:8000
      auth:
        type: bearer
        token: ${AGENTIC_TOKEN}
```

```java
@Service
public class SummariserService {

    private final AgenticClient agentic;

    public SummariserService(AgenticClient agentic) {
        this.agentic = agentic;
    }

    public Mono<String> summarise(String document) {
        return agentic.invoke("summariser", AgentRequest.of(document))
                .map(response -> response.outputAsString(/* mapper */));
    }
}
```

### Plain Java

```java
AgenticClient client = AgenticClient.builder("primary")
        .baseUrl("http://agentic.platform.local:8000")
        .auth(AuthStrategy.bearer(System.getenv("AGENTIC_TOKEN")))
        .timeout(Duration.ofSeconds(60))
        .retry(r -> r.maxAttempts(3).initialBackoff(Duration.ofMillis(250)))
        .build();

AgentResponse response = client.invoke("summariser", AgentRequest.of("…"))
        .block();
```

---

## Configuration

This POM has no runtime configuration namespace. Configure the selected child libraries in the application that uses them.

## Continue the application

Use the [Agentic Bridge Starter guide](../../docs/site/modules/agentic-bridge-starter.md) for the next part of this integration. ## Build and validation

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-agentic-bridge -am verify
./mvnw -pl :fireflyframework-agentic-bridge -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Configuration details and source references: [module guide](../../docs/site/modules/agentic-bridge.md).

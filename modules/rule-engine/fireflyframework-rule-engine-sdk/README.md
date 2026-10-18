![Jafly. Rule Engine SDK](../../../docs/site/assets/modules/rule-engine-sdk.svg)

# Rule Engine SDK

Generate a reactive Java WebClient SDK for the rule engine HTTP API.

Generate from the checked-in OpenAPI specification with this POM. The API/model/invoker package names below are authoritative; generated code belongs under target and should not be edited as the long-term source.

[Module guide](../../../docs/site/modules/rule-engine-sdk.md) · [Jafly README](../../../README.md) · [Module catalog](../../../docs/site/modules/index.md)

## In the onboarding service

A separate onboarding service can call the rule service through generated WebClient APIs. Supply a generated request model with valid YAML/input or a configured stored-rule code.

## Installation

[Installation and version availability](../../../docs/site/installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-rule-engine-sdk</artifactId>
    <version>26.10.17</version>
</dependency>
```

## Quick start

The Java examples in this section are API fragments for application code. Use the stated imports and supply the application-owned collaborators described alongside them.

After building the SDK, configure its generated client for the deployed rule service. The request and response below are SDK model types, distinct from the server's interfaces DTOs:

```java
import org.fireflyframework.rules.sdk.api.RulesEvaluationApi;
import org.fireflyframework.rules.sdk.invoker.ApiClient;
import org.fireflyframework.rules.sdk.model.PlainYamlEvaluationRequestDTO;
import org.fireflyframework.rules.sdk.model.RulesEvaluationResponseDTO;
import reactor.core.publisher.Mono;

ApiClient client = new ApiClient()
        .setBasePath(System.getenv("RULE_ENGINE_URL"));
RulesEvaluationApi api = new RulesEvaluationApi(client);
Mono<RulesEvaluationResponseDTO> result =
        api.evaluateRulesPlain(request, "rule-request-123");
```

`request` is a generated `PlainYamlEvaluationRequestDTO` containing valid DSL YAML and input data. The second argument is the optional idempotency key defined by the specification. Compose the returned `Mono` in the calling application and configure authentication headers on `ApiClient` for the deployed service. The generated methods also provide `WithHttpInfo` variants for status and headers.

## Configuration

This module has no authored configuration-properties class. Its behavior is configured through the Maven generator or the dependencies listed below.

## Generated client contract

The generation input is [openapi.yml](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/rule-engine/fireflyframework-rule-engine-sdk/src/main/resources/api-spec/openapi.yml). The build produces the Java surface under `target`; this module contains no authored production Java classes. The current WebClient generation explicitly uses Spring Boot 4 with Jackson 2 compatibility.

| POM setting | Value |
| --- | --- |
| `inputSpec` | `${project.basedir}/src/main/resources/api-spec/openapi.yml` |
| `generatorName` | `java` |
| `library` | `webclient` |
| `apiPackage` | `org.fireflyframework.rules.sdk.api` |
| `modelPackage` | `org.fireflyframework.rules.sdk.model` |
| `invokerPackage` | `org.fireflyframework.rules.sdk.invoker` |
| `useSpringBoot4` | `true` |
| `useJackson3` | `false` |
| `sourceFolder` | `src/gen/java/main` |
| `reactive` | `true` |

## Continue the application

Use the [Rule Engine Web guide](../../../docs/site/modules/rule-engine-web.md) for the next part of this integration.

## Build and validation

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-rule-engine-sdk -am verify
./mvnw -pl :fireflyframework-rule-engine-sdk -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Configuration details and source references: [module guide](../../../docs/site/modules/rule-engine-sdk.md).

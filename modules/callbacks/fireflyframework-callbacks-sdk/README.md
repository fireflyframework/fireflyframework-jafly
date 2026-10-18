![Jafly. Callback Management Platform - SDK](../../../docs/site/assets/modules/callbacks-sdk.svg)

# Callback Management Platform - SDK

Generate a reactive Java WebClient SDK from the callback platform OpenAPI specification.

Build the module to generate its APIs/models before importing them in a client. Treat the checked-in OpenAPI input and POM package settings as the source of the generated surface; edit the specification rather than generated files.

[Module guide](../../../docs/site/modules/callbacks-sdk.md) · [Jafly README](../../../README.md) · [Module catalog](../../../docs/site/modules/index.md)

## In the onboarding service

A partner-management client can use this generated SDK to register or inspect callbacks for onboarding events. Its model classes belong to the remote HTTP contract, not the onboarding application's persistence model.

## Installation

[Installation and version availability](../../../docs/site/installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-callbacks-sdk</artifactId>
    <version>26.10.17-SNAPSHOT</version>
</dependency>
```

## Quick start

The Java examples in this section are API fragments for application code. Use the stated imports and supply the application-owned collaborators described alongside them.

After building the SDK, create the generated client for the deployed callbacks service. Use an existing callback configuration UUID for this read operation:

```java
import com.firefly.common.callbacks.sdk.api.CallbackConfigurationsApi;
import com.firefly.common.callbacks.sdk.invoker.ApiClient;
import com.firefly.common.callbacks.sdk.model.CallbackConfigurationDTO;
import reactor.core.publisher.Mono;

ApiClient client = new ApiClient()
        .setBasePath(System.getenv("CALLBACKS_URL"));
CallbackConfigurationsApi api = new CallbackConfigurationsApi(client);
Mono<CallbackConfigurationDTO> configuration =
        api.findById2(configurationId, null);
```

`configurationId` is a `UUID`; the second argument is the optional idempotency header. The generated `findById2` name follows the checked-in OpenAPI operation IDs, which distinguish similarly named operations across tags. SDK models live under `com.firefly.common.callbacks.sdk.model`; use these generated types in a remote client rather than importing server DTOs. Configure the generated `ApiClient` authentication/default headers for the deployment and compose the returned `Mono`.

## Configuration

This module has no authored configuration-properties class. Its behavior is configured through the Maven generator or the dependencies listed below.

## Generated client contract

The generation input is [openapi.yml](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/callbacks/fireflyframework-callbacks-sdk/src/main/resources/api-spec/openapi.yml). The build produces the Java surface under `target`; this module contains no authored production Java classes. The current WebClient generation explicitly uses Spring Boot 4 with Jackson 2 compatibility.

| POM setting | Value |
| --- | --- |
| `inputSpec` | `${project.basedir}/src/main/resources/api-spec/openapi.yml` |
| `generatorName` | `java` |
| `library` | `webclient` |
| `apiPackage` | `com.firefly.common.callbacks.sdk.api` |
| `modelPackage` | `com.firefly.common.callbacks.sdk.model` |
| `invokerPackage` | `com.firefly.common.callbacks.sdk.invoker` |
| `useSpringBoot4` | `true` |
| `useJackson3` | `false` |
| `sourceFolder` | `src/gen/java/main` |
| `reactive` | `true` |

## Continue the application

Use the [Callback Management Platform - Web guide](../../../docs/site/modules/callbacks-web.md) for the next part of this integration.

## Build and validation

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-callbacks-sdk -am verify
./mvnw -pl :fireflyframework-callbacks-sdk -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Configuration details and source references: [module guide](../../../docs/site/modules/callbacks-sdk.md).

![Jafly. Security OPA Adapter](../../docs/site/assets/modules/security-adapter-opa.svg)

# Security OPA Adapter

Delegate policy decisions to Open Policy Agent through the security SPI.

Construct the adapter with the OPA endpoint/client and use it as PolicyDecisionPort. Align action/resource/context input with the deployed Rego policy and test unavailable/malformed responses as denial paths.

[Module guide](../../docs/site/modules/security-adapter-opa.md) · [Jafly README](../../README.md) · [Module catalog](../../docs/site/modules/index.md)

## In the onboarding service

Delegate an onboarding authorization decision to a configured OPA policy through PolicyDecisionPort. Match the application action/resource/context to the deployed Rego input contract.

## Installation

[Installation and version availability](../../docs/site/installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-security-adapter-opa</artifactId>
    <version>26.10.22</version>
</dependency>
```

## Quick start

The Java examples in this section are API fragments for application code. Use the stated imports and supply the application-owned collaborators described alongside them.

Contribute an `OpaPolicyDecisionAdapter` as your `PolicyDecisionPort`. Because `security-webflux` selects whatever `PolicyDecisionPort` bean is present, this single bean replaces the in-process `EmbeddedPolicyDecisionAdapter` for every non-permitted exchange. No other wiring required:

```java
import org.fireflyframework.security.adapter.opa.OpaPolicyDecisionAdapter;
import org.fireflyframework.security.spi.PolicyDecisionPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
class OpaPolicyConfiguration {

    @Bean
    PolicyDecisionPort policyDecisionPort() {
        WebClient opa = WebClient.create("http://opa:8181");
        return new OpaPolicyDecisionAdapter(opa, "firefly/allow");
    }
}
```

The decision is consumed reactively and is permit-only:

```java
adapter.authorize(principal, "read", "doc:1", Map.of())
        .map(Decision::granted); // true only when OPA returns result == true
```

## Configuration

This module declares no `@ConfigurationProperties` class of its own. Use the constructor/port contracts above and the configuration of the host or related core module.

## API overview

| Type | Contract and responsibility |
| --- | --- |
| [`OpaPolicyDecisionAdapter`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-adapter-opa/src/main/java/org/fireflyframework/security/adapter/opa/OpaPolicyDecisionAdapter.java) | `PolicyDecisionPort` backed by Open Policy Agent. |

## Continue the application

Use the [Security Resource Server guide](../../docs/site/modules/security-resource-server.md) for the next part of this integration.

## Build and validation

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-security-adapter-opa -am verify
./mvnw -pl :fireflyframework-security-adapter-opa -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Configuration details and source references: [module guide](../../docs/site/modules/security-adapter-opa.md).

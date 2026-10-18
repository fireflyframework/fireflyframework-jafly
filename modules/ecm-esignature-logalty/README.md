![Jafly. ECM eSignature - Logalty](../../docs/site/assets/modules/ecm-esignature-logalty.svg)

# ECM eSignature - Logalty

Implement the ECM signature-envelope port with Logalty.

Configure the provider credentials and base URL through external configuration and select the Logalty adapter. Check the source contract for supported envelope operations before mapping a signing workflow to it.

[Module guide](../../docs/site/modules/ecm-esignature-logalty.md) · [Jafly README](../../README.md) · [Module catalog](../../docs/site/modules/index.md)

## In the onboarding service

If onboarding includes a signed agreement, use the Logalty adapter for the configured envelope workflow. The application supplies the agreement, recipients, and account configuration.

## Installation

[Installation and version availability](../../docs/site/installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-ecm-esignature-logalty</artifactId>
    <version>26.10.18</version>
</dependency>
```

## Quick start

The injection examples use Lombok `@RequiredArgsConstructor`. Add its provided dependency and annotation processor as described in [Constructor injection](../../docs/site/maven.md#constructor-injection).

The Java examples in this section are API fragments for application code. Use the stated imports and supply the application-owned collaborators described alongside them.

### 1. Add the dependency and select the provider

With the dependency on the classpath, activate the adapter by selecting Logalty as the eSignature provider and supplying credentials:

```yaml
firefly:
  ecm:
    esignature:
      provider: logalty          # selects this adapter (required to activate)
    adapter:
      logalty:
        client-id: ${LOGALTY_CLIENT_ID}
        client-secret: ${LOGALTY_CLIENT_SECRET}
        base-url: https://api.logalty.com
```

That is all the wiring required. `LogaltyAdapterAutoConfiguration` contributes the `WebClient`, `CircuitBreaker`, `Retry`, `ObjectMapper` and the `LogaltySignatureEnvelopeAdapter` bean automatically.

### 2. Use the ECM signature port

Inject the provider-neutral `SignatureEnvelopePort`. Your code never references Logalty directly:

```java
import lombok.RequiredArgsConstructor;
import org.fireflyframework.ecm.domain.model.esignature.SignatureEnvelope;
import org.fireflyframework.ecm.port.esignature.SignatureEnvelopePort;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ContractSigningService {

    private final SignatureEnvelopePort envelopes; // backed by Logalty at runtime



    public Mono<SignatureEnvelope> startContract(SignatureEnvelope draft, UUID sender) {
        return envelopes.createEnvelope(draft)
            .flatMap(created -> envelopes.sendEnvelope(created.getId(), sender));
    }

    public Mono<String> signingLink(UUID envelopeId, String email, String name) {
        return envelopes.getSigningUrl(envelopeId, email, name, null);
    }
}
```

Switching providers later is a configuration change only: swap the dependency and set `firefly.ecm.esignature.provider` to `docusign` or `adobe-sign`.

## Configuration

The `firefly.ecm.adapter.logalty` namespace is bound by [LogaltyAdapterProperties.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/ecm-esignature-logalty/src/main/java/org/fireflyframework/ecm/adapter/logalty/LogaltyAdapterProperties.java). Nested configuration is declared in the linked class. Defaults are the field initializers.

| Setting | Java type | Source default |
| --- | --- | --- |
| `firefly.ecm.adapter.logalty.client-id` | `String` | `not initialized` |
| `firefly.ecm.adapter.logalty.client-secret` | `String` | `not initialized` |
| `firefly.ecm.adapter.logalty.username` | `String` | `not initialized` |
| `firefly.ecm.adapter.logalty.password` | `String` | `not initialized` |
| `firefly.ecm.adapter.logalty.base-url` | `String` | `"https://api.logalty.com"` |

## API overview

| Type | Contract and responsibility |
| --- | --- |
| [`LogaltySignatureEnvelopeAdapter`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/ecm-esignature-logalty/src/main/java/org/fireflyframework/ecm/adapter/logalty/LogaltySignatureEnvelopeAdapter.java) | Defines `LogaltySignatureEnvelopeAdapter`, `createEnvelope`, `updateEnvelope`, `deleteEnvelope` operations. |
| [`LogaltyAdapterProperties`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/ecm-esignature-logalty/src/main/java/org/fireflyframework/ecm/adapter/logalty/LogaltyAdapterProperties.java) | Configuration properties for the Logalty eSignature adapter. |
| [`LogaltyAdapterAutoConfiguration`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/ecm-esignature-logalty/src/main/java/org/fireflyframework/ecm/adapter/logalty/LogaltyAdapterAutoConfiguration.java) | Defines `logaltyWebClient`, `logaltyCircuitBreaker`, `logaltyRetry`, `logaltyObjectMapper` operations. |

## Continue the application

Use the [Enterprise Content Management guide](../../docs/site/modules/ecm.md) for the next part of this integration.

## Build and validation

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-ecm-esignature-logalty -am verify
./mvnw -pl :fireflyframework-ecm-esignature-logalty -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Configuration details and source references: [module guide](../../docs/site/modules/ecm-esignature-logalty.md).

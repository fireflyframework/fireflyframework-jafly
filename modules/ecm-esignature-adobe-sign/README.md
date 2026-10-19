![Jafly. ECM eSignature - Adobe Sign](../../docs/site/assets/modules/ecm-esignature-adobe-sign.svg)

# ECM eSignature - Adobe Sign

Implement the ECM signature-envelope port with Adobe Sign.

Supply the Adobe client credentials, refresh token, and regional API URL, then select the signature adapter. The provider tests use controlled fixtures; a live signing account is a separate integration step.

[Module guide](../../docs/site/modules/ecm-esignature-adobe-sign.md) · [Jafly README](../../README.md) · [Module catalog](../../docs/site/modules/index.md)

## In the onboarding service

If onboarding includes a signed agreement, use the Adobe Sign adapter for the configured envelope workflow. The application supplies the agreement, recipients, and account configuration.

## Installation

[Installation and version availability](../../docs/site/installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-ecm-esignature-adobe-sign</artifactId>
    <version>26.10.20-SNAPSHOT</version>
</dependency>
```

## Quick start

The injection examples use Lombok `@RequiredArgsConstructor`. Add its provided dependency and annotation processor as described in [Constructor injection](../../docs/site/maven.md#constructor-injection).

The Java examples in this section are API fragments for application code. Use the stated imports and supply the application-owned collaborators described alongside them.

1. Add the dependency (see [Installation](#installation)).

2. Select Adobe Sign as the eSignature provider and supply credentials in `application.yml`:

```yaml
firefly:
  ecm:
    features:
      esignature: true
    esignature:
      provider: adobe-sign
    adapter:
      adobe-sign:
        client-id: ${ADOBE_SIGN_CLIENT_ID}
        client-secret: ${ADOBE_SIGN_CLIENT_SECRET}
        refresh-token: ${ADOBE_SIGN_REFRESH_TOKEN}
        base-url: https://api.na1.adobesign.com
```

3. Inject the port and use it. Your code depends on the framework SPI, not on Adobe Sign:

```java
import lombok.RequiredArgsConstructor;
import org.fireflyframework.ecm.port.esignature.SignatureEnvelopePort;
import org.fireflyframework.ecm.domain.model.esignature.SignatureEnvelope;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class ContractService {

    private final SignatureEnvelopePort envelopes; // AdobeSignSignatureEnvelopeAdapter injected by auto-config



    public Mono<SignatureEnvelope> startSigning(SignatureEnvelope envelope) {
        return envelopes.createEnvelope(envelope)        // creates the Adobe Sign agreement
            .flatMap(created -> envelopes.sendEnvelope(created.getId(), created.getCreatedBy()));
    }
}
```

When `firefly.ecm.esignature.provider` is `adobe-sign`, `AdobeSignAutoConfiguration` registers the `WebClient`, circuit breaker, retry and `AdobeSignSignatureEnvelopeAdapter` beans automatically and exposes the adapter as the active `SignatureEnvelopePort`.

## Configuration

The `firefly.ecm.adapter.adobe-sign` namespace is bound by [AdobeSignAdapterProperties.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/ecm-esignature-adobe-sign/src/main/java/org/fireflyframework/ecm/adapter/adobesign/AdobeSignAdapterProperties.java). Nested configuration is declared in the linked class. Defaults are the field initializers.

| Setting | Java type | Source default |
| --- | --- | --- |
| `firefly.ecm.adapter.adobe-sign.client-id` | `String` | `not initialized` |
| `firefly.ecm.adapter.adobe-sign.client-secret` | `String` | `not initialized` |
| `firefly.ecm.adapter.adobe-sign.refresh-token` | `String` | `not initialized` |
| `firefly.ecm.adapter.adobe-sign.base-url` | `String` | `"https://api.na1.adobesign.com"` |
| `firefly.ecm.adapter.adobe-sign.api-version` | `String` | `"v6"` |

## API overview

| Type | Contract and responsibility |
| --- | --- |
| [`AdobeSignSignatureEnvelopeAdapter`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/ecm-esignature-adobe-sign/src/main/java/org/fireflyframework/ecm/adapter/adobesign/AdobeSignSignatureEnvelopeAdapter.java) | Defines `AdobeSignSignatureEnvelopeAdapter`, `createEnvelope`, `ensureValidAccessToken`, `buildAgreementRequest` operations. |
| [`AdobeSignAdapterProperties`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/ecm-esignature-adobe-sign/src/main/java/org/fireflyframework/ecm/adapter/adobesign/AdobeSignAdapterProperties.java) | Carries `clientId`, `clientSecret`, `refreshToken`, `baseUrl`, `apiVersion` data; field constraints and defaults are declared in the linked source. |
| [`AdobeSignAutoConfiguration`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/ecm-esignature-adobe-sign/src/main/java/org/fireflyframework/ecm/adapter/adobesign/AdobeSignAutoConfiguration.java) | Defines `adobeSignWebClient`, `adobeSignCircuitBreaker`, `adobeSignRetry`, `adobeSignSignatureEnvelopeAdapter` operations. |

## Continue the application

Use the [Enterprise Content Management guide](../../docs/site/modules/ecm.md) for the next part of this integration.

## Build and validation

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-ecm-esignature-adobe-sign -am verify
./mvnw -pl :fireflyframework-ecm-esignature-adobe-sign -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Configuration details and source references: [module guide](../../docs/site/modules/ecm-esignature-adobe-sign.md).

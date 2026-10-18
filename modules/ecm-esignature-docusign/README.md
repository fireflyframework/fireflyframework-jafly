![Jafly. ECM eSignature - DocuSign](../../docs/site/assets/modules/ecm-esignature-docusign.svg)

# ECM eSignature - DocuSign

Implement the ECM signature-envelope port with DocuSign.

Configure integration/account/user identifiers and the signing credential through environment-backed configuration. Select the adapter and verify the account, auth server, and base URL for your DocuSign environment.

[Module guide](../../docs/site/modules/ecm-esignature-docusign.md) · [Jafly README](../../README.md) · [Module catalog](../../docs/site/modules/index.md)

## In the onboarding service

If onboarding includes a signed agreement, use the DocuSign adapter for the configured envelope workflow. The application supplies the agreement, recipients, and account configuration.

## Installation

[Installation and version availability](../../docs/site/installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-ecm-esignature-docusign</artifactId>
    <version>26.10.17</version>
</dependency>
```

## Quick start

The injection examples use Lombok `@RequiredArgsConstructor`. Add its provided dependency and annotation processor as described in [Constructor injection](../../docs/site/maven.md#constructor-injection).

The Java examples in this section are API fragments for application code. Use the stated imports and supply the application-owned collaborators described alongside them.

1. Add the dependency (see [Installation](#installation)) to a service that already builds on `fireflyframework-ecm`.

2. Select DocuSign and provide JWT credentials in `application.yml`:

```yaml
firefly:
  ecm:
    features:
      esignature: true
    esignature:
      provider: docusign          # selects this adapter
    adapter:
      docusign:
        integration-key: ${DOCUSIGN_INTEGRATION_KEY}
        user-id: ${DOCUSIGN_USER_ID}
        account-id: ${DOCUSIGN_ACCOUNT_ID}
        private-key: ${DOCUSIGN_PRIVATE_KEY}   # RSA private key (PEM)
        sandbox-mode: ${DOCUSIGN_SANDBOX:false}
```

3. Inject the ECM port. Your code depends only on the provider-neutral `SignatureEnvelopePort`, never on DocuSign types:

```java
import lombok.RequiredArgsConstructor;
import org.fireflyframework.ecm.domain.model.esignature.SignatureEnvelope;
import org.fireflyframework.ecm.port.esignature.SignatureEnvelopePort;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ContractService {

    private final SignatureEnvelopePort envelopes; // bound to DocuSign at runtime



    public Mono<SignatureEnvelope> startSigning() {
        SignatureEnvelope envelope = SignatureEnvelope.builder()
                .title("Please sign this document")
                .description("Please review and sign the attached document(s).")
                .build();

        return envelopes.createEnvelope(envelope)                       // creates a DocuSign draft envelope
                .flatMap(created -> envelopes.sendEnvelope(created.getId(), UUID.randomUUID()));
    }
}
```

Swapping providers later (e.g. To Adobe Sign or Logalty) means changing the dependency and the `firefly.ecm.esignature.provider` value. The `SignatureEnvelopePort`-based code above stays unchanged.

## Configuration

The `firefly.ecm.adapter.docusign` namespace is bound by [DocuSignAdapterProperties.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/ecm-esignature-docusign/src/main/java/org/fireflyframework/ecm/adapter/docusign/DocuSignAdapterProperties.java). Nested configuration is declared in the linked class. Defaults are the field initializers.

| Setting | Java type | Source default |
| --- | --- | --- |
| `firefly.ecm.adapter.docusign.integration-key` | `String` | `not initialized` |
| `firefly.ecm.adapter.docusign.user-id` | `String` | `not initialized` |
| `firefly.ecm.adapter.docusign.account-id` | `String` | `not initialized` |
| `firefly.ecm.adapter.docusign.private-key` | `String` | `not initialized` |
| `firefly.ecm.adapter.docusign.base-url` | `String` | `"https://na3.docusign.net/restapi"` |

## API overview

| Type | Contract and responsibility |
| --- | --- |
| [`DocuSignSignatureEnvelopeAdapter`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/ecm-esignature-docusign/src/main/java/org/fireflyframework/ecm/adapter/docusign/DocuSignSignatureEnvelopeAdapter.java) | Defines `DocuSignSignatureEnvelopeAdapter`, `createEnvelope`, `updateEnvelope`, `deleteEnvelope` operations. |
| [`DocuSignAdapterProperties`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/ecm-esignature-docusign/src/main/java/org/fireflyframework/ecm/adapter/docusign/DocuSignAdapterProperties.java) | Carries `integrationKey`, `userId`, `accountId`, `privateKey`, `baseUrl` data; field constraints and defaults are declared in the linked source. |
| [`DocuSignAdapterAutoConfiguration`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/ecm-esignature-docusign/src/main/java/org/fireflyframework/ecm/adapter/docusign/DocuSignAdapterAutoConfiguration.java) | Auto-configuration for the DocuSign ECM adapter. |

## Continue the application

Use the [Enterprise Content Management guide](../../docs/site/modules/ecm.md) for the next part of this integration.

## Build and validation

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-ecm-esignature-docusign -am verify
./mvnw -pl :fireflyframework-ecm-esignature-docusign -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Configuration details and source references: [module guide](../../docs/site/modules/ecm-esignature-docusign.md).

![Jafly. ECM eSignature - DocuSign](../assets/modules/ecm-esignature-docusign.svg)

# ECM eSignature - DocuSign

Implement the ECM signature-envelope port with DocuSign.

Configure integration/account/user identifiers and the signing credential through environment-backed configuration. Select the adapter and verify the account, auth server, and base URL for your DocuSign environment.

| Maven artifact | Packaging | Source project |
| --- | --- | --- |
| `org.fireflyframework:fireflyframework-ecm-esignature-docusign` | `jar` | [modules/ecm-esignature-docusign](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/ecm-esignature-docusign) |

Part of [Domain and data](index.md#domain-and-data).

## In the onboarding service

If onboarding includes a signed agreement, use the DocuSign adapter for the configured envelope workflow. The application supplies the agreement, recipients, and account configuration.

## Add the component

[Installation and version availability](../installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-ecm-esignature-docusign</artifactId>
    <version>26.10.17-SNAPSHOT</version>
</dependency>
```

## Quick start

The injection examples use Lombok `@RequiredArgsConstructor`. Add its provided dependency and annotation processor as described in [Constructor injection](../maven.md#constructor-injection).

The Java examples in this section are API fragments for application code. Use the stated imports and supply the application-owned collaborators described alongside them.

1. Add the dependency (see [Installation](#add-the-component)) to a service that already builds on `fireflyframework-ecm`.

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

## Configuration and wiring

The `firefly.ecm.adapter.docusign` namespace is bound by [DocuSignAdapterProperties.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/ecm-esignature-docusign/src/main/java/org/fireflyframework/ecm/adapter/docusign/DocuSignAdapterProperties.java). Nested configuration is declared in the linked class. Defaults are the field initializers.

| Setting | Java type | Source default |
| --- | --- | --- |
| `firefly.ecm.adapter.docusign.integration-key` | `String` | `not initialized` |
| `firefly.ecm.adapter.docusign.user-id` | `String` | `not initialized` |
| `firefly.ecm.adapter.docusign.account-id` | `String` | `not initialized` |
| `firefly.ecm.adapter.docusign.private-key` | `String` | `not initialized` |
| `firefly.ecm.adapter.docusign.base-url` | `String` | `"https://na3.docusign.net/restapi"` |
| `firefly.ecm.adapter.docusign.auth-server` | `String` | `"https://account.docusign.com"` |
| `firefly.ecm.adapter.docusign.webhook-url` | `String` | `not initialized` |
| `firefly.ecm.adapter.docusign.webhook-secret` | `String` | `not initialized` |
| `firefly.ecm.adapter.docusign.sandbox-mode` | `Boolean` | `false` |
| `firefly.ecm.adapter.docusign.connection-timeout` | `Duration` | `Duration.ofSeconds(30)` |
| `firefly.ecm.adapter.docusign.read-timeout` | `Duration` | `Duration.ofSeconds(60)` |
| `firefly.ecm.adapter.docusign.max-retries` | `Integer` | `3` |
| `firefly.ecm.adapter.docusign.jwt-expiration` | `Long` | `3600L` |
| `firefly.ecm.adapter.docusign.enable-polling` | `Boolean` | `true` |

Property-based wiring conditions found in this module:

- `name = "firefly.ecm.esignature.provider", havingValue = "docusign"`. [DocuSignAdapterAutoConfiguration.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/ecm-esignature-docusign/src/main/java/org/fireflyframework/ecm/adapter/docusign/DocuSignAdapterAutoConfiguration.java).
- `name = "firefly.ecm.esignature.provider", havingValue = "docusign"`. [DocuSignSignatureEnvelopeAdapter.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/ecm-esignature-docusign/src/main/java/org/fireflyframework/ecm/adapter/docusign/DocuSignSignatureEnvelopeAdapter.java).

## Public API and extension points

| Type | Contract and responsibility |
| --- | --- |
| [`DocuSignSignatureEnvelopeAdapter`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/ecm-esignature-docusign/src/main/java/org/fireflyframework/ecm/adapter/docusign/DocuSignSignatureEnvelopeAdapter.java) | Defines `DocuSignSignatureEnvelopeAdapter`, `createEnvelope`, `updateEnvelope`, `deleteEnvelope` operations. |
| [`DocuSignAdapterProperties`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/ecm-esignature-docusign/src/main/java/org/fireflyframework/ecm/adapter/docusign/DocuSignAdapterProperties.java) | Carries `integrationKey`, `userId`, `accountId`, `privateKey`, `baseUrl` data; field constraints and defaults are declared in the linked source. |
| [`DocuSignAdapterAutoConfiguration`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/ecm-esignature-docusign/src/main/java/org/fireflyframework/ecm/adapter/docusign/DocuSignAdapterAutoConfiguration.java) | Auto-configuration for the DocuSign ECM adapter. |

## Related components

| Related component | Why it belongs nearby |
| --- | --- |
| [Enterprise Content Management Library](ecm.md) | Define content-management ports, adapter selection, document contracts, and resilience/observability support. |

## Build and test

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-ecm-esignature-docusign -am verify
./mvnw -pl :fireflyframework-ecm-esignature-docusign -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Representative test sources:

- [DocuSignAdapterTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/ecm-esignature-docusign/src/test/java/org/fireflyframework/ecm/adapter/docusign/DocuSignAdapterTest.java).

Mock, local-container, and application-context tests do not establish acceptance against a live provider account. Validate the intended provider deployment and credentials separately.

## API reference excerpt

Contract excerpt from [DocuSignSignatureEnvelopeAdapter.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/ecm-esignature-docusign/src/main/java/org/fireflyframework/ecm/adapter/docusign/DocuSignSignatureEnvelopeAdapter.java). This fragment needs the full type and its imports.

```java
public class DocuSignSignatureEnvelopeAdapter implements SignatureEnvelopePort {
    private final ApiClient apiClient;
    private final EnvelopesApi envelopesApi;
    private final DocuSignAdapterProperties properties;
    private final DocumentContentPort documentContentPort;
    private final DocumentPort documentPort;
    private final Map<UUID, String> envelopeIdMapping = new ConcurrentHashMap<>();
    private final Map<String, UUID> externalIdMapping = new ConcurrentHashMap<>();
    public DocuSignSignatureEnvelopeAdapter(ApiClient apiClient,
                                          DocuSignAdapterProperties properties,
                                          DocumentContentPort documentContentPort,
                                          DocumentPort documentPort) {
        this.apiClient = apiClient;
        this.properties = properties;
        this.documentContentPort = documentContentPort;
        this.documentPort = documentPort;
        this.envelopesApi = new EnvelopesApi(apiClient);
        log.info("DocuSignSignatureEnvelopeAdapter initialized for account: {} with document integration",
                properties.getAccountId());
    }
    public Mono<SignatureEnvelope> createEnvelope(SignatureEnvelope envelope) {
        return Mono.fromCallable(() -> {
            UUID envelopeId = envelope.getId() != null ? envelope.getId() : UUID.randomUUID();
            EnvelopeDefinition envelopeDefinition = buildEnvelopeDefinition(envelope);
```

## Detailed reference

### Configuration

All properties live under the `firefly.ecm.adapter.docusign` prefix and are bound by `DocuSignAdapterProperties`. Provider selection itself is the ECM core property `firefly.ecm.esignature.provider`.

```yaml
firefly:
  ecm:
    esignature:
      provider: docusign                 # required to activate this adapter
    adapter:
      docusign:
        # --- Credentials (required) ---
        integration-key: ${DOCUSIGN_INTEGRATION_KEY}   # OAuth client ID / Integration Key
        user-id: ${DOCUSIGN_USER_ID}                   # impersonated API user (GUID)
        account-id: ${DOCUSIGN_ACCOUNT_ID}             # target DocuSign account ID
        private-key: ${DOCUSIGN_PRIVATE_KEY}           # RSA private key (PEM) for JWT Grant

        # --- Endpoints ---
        base-url: https://na3.docusign.net/restapi     # production REST base (ignored when sandbox-mode=true)
        auth-server: https://account.docusign.com      # production OAuth server (ignored when sandbox-mode=true)
        sandbox-mode: false                            # true => demo.docusign.net + account-d.docusign.com

        # --- HTTP / retry ---
        connection-timeout: 30s
        read-timeout: 60s
        max-retries: 3

        # --- JWT ---
        jwt-expiration: 3600                            # requested token lifetime, seconds

        # --- Webhook (DocuSign Connect) ---
        webhook-url:                                    # inbound status callback URL
        webhook-secret:                                 # shared secret for callback verification

        # --- Polling ---
        enable-polling: true
        polling-interval: 5m

        # --- Defaults ---
        default-email-subject: "Please sign this document"
        default-email-message: "Please review and sign the attached document(s)."
```

| Property | Default | Description |
|----------|---------|-------------|
| `integration-key` | _(required)_ | DocuSign Integration Key (OAuth client ID) configured for JWT Grant. |
| `user-id` | _(required)_ | API user GUID to impersonate via the JWT Grant flow. |
| `account-id` | _(required)_ | DocuSign account ID; validated for access on startup. |
| `private-key` | _(required)_ | RSA private key (PEM) used to sign the JWT assertion. |
| `base-url` | `https://na3.docusign.net/restapi` | Production eSignature REST base path. Overridden by `demo.docusign.net` when `sandbox-mode=true`. |
| `auth-server` | `https://account.docusign.com` | Production OAuth auth server. Overridden by `account-d.docusign.com` when `sandbox-mode=true`. |
| `sandbox-mode` | `false` | When `true`, routes both REST and OAuth traffic to the DocuSign developer/demo environment. |
| `connection-timeout` | `30s` | `ApiClient` connect timeout. |
| `read-timeout` | `60s` | `ApiClient` read timeout. |
| `max-retries` | `3` | Maximum retry attempts for adapter operations. |
| `jwt-expiration` | `3600` | Requested JWT user-token lifetime, in seconds. |
| `webhook-url` | _(none)_ | DocuSign Connect callback URL for inbound envelope status events. |
| `webhook-secret` | _(none)_ | Shared secret used to verify inbound webhook callbacks. |
| `enable-polling` | `true` | Enable periodic envelope-status polling. |
| `polling-interval` | `5m` | Interval between status polls when polling is enabled. |
| `default-email-subject` | `Please sign this document` | Fallback email subject for new envelopes. |
| `default-email-message` | `Please review and sign the attached document(s).` | Fallback email body for new envelopes. |

> The credential properties (`integration-key`, `user-id`, `account-id`, `private-key`) are `@NotBlank`. The context fails fast at startup if any is missing. Keep secrets out of source control; inject them via environment variables or a secrets manager as shown above.

### How It Works

1. Activation. `DocuSignAdapterAutoConfiguration` and `DocuSignSignatureEnvelopeAdapter` are both `@ConditionalOnProperty(firefly.ecm.esignature.provider=docusign)`, and the auto-config is also `@ConditionalOnClass(ApiClient.class)`, so the adapter only engages when DocuSign is selected and the SDK is present.
2. Authentication. The auto-configuration builds a DocuSign `ApiClient`, sets the base path / timeouts, and performs the JWT Grant flow (`requestJWTUserToken` with `signature` + `impersonation` scopes), then validates that `account-id` is among the authenticated user's accounts.
3. Operations. `DocuSignSignatureEnvelopeAdapter` wraps `EnvelopesApi` and maps Firefly's `SignatureEnvelope` model to DocuSign `EnvelopeDefinition`/`Envelope` calls, returning reactive `Mono`/`Flux`. Internal maps track the Firefly `UUID` ↔ DocuSign envelope-ID correspondence.
4. Override points. The `ApiClient` bean is `@ConditionalOnMissingBean`, so you can supply your own pre-authenticated client (e.g. A custom auth strategy or a test stub) and the adapter will use it.

## Continue the application

Use the [Enterprise Content Management guide](ecm.md) for the next part of this integration. The runnable submission/query service is in [Getting started](../getting-started.md).

## Source and further reading

- [Maven project and dependency declarations](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/ecm-esignature-docusign/pom.xml).
- [Production source](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/ecm-esignature-docusign/src/main/java).
- [org.springframework.boot.autoconfigure.AutoConfiguration.imports](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/ecm-esignature-docusign/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports).
- [application-docusign.yml](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/ecm-esignature-docusign/src/main/resources/application-docusign.yml).
- [Module catalog](index.md) and [getting started](../getting-started.md).
- [Maven platform](../maven.md) and [architecture](../architecture.md).

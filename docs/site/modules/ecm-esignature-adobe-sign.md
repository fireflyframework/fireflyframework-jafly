![Jafly. ECM eSignature - Adobe Sign](../assets/modules/ecm-esignature-adobe-sign.svg)

# ECM eSignature - Adobe Sign

Implement the ECM signature-envelope port with Adobe Sign.

Supply the Adobe client credentials, refresh token, and regional API URL, then select the signature adapter. The provider tests use controlled fixtures; a live signing account is a separate integration step.

| Maven artifact | Packaging | Source project |
| --- | --- | --- |
| `org.fireflyframework:fireflyframework-ecm-esignature-adobe-sign` | `jar` | [modules/ecm-esignature-adobe-sign](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/ecm-esignature-adobe-sign) |

Part of [Domain and data](index.md#domain-and-data).

## In the onboarding service

If onboarding includes a signed agreement, use the Adobe Sign adapter for the configured envelope workflow. The application supplies the agreement, recipients, and account configuration.

## Add the component

[Installation and version availability](../installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-ecm-esignature-adobe-sign</artifactId>
    <version>26.10.19</version>
</dependency>
```

## Quick start

The injection examples use Lombok `@RequiredArgsConstructor`. Add its provided dependency and annotation processor as described in [Constructor injection](../maven.md#constructor-injection).

The Java examples in this section are API fragments for application code. Use the stated imports and supply the application-owned collaborators described alongside them.

1. Add the dependency (see [Installation](#add-the-component)).

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

## Configuration and wiring

The `firefly.ecm.adapter.adobe-sign` namespace is bound by [AdobeSignAdapterProperties.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/ecm-esignature-adobe-sign/src/main/java/org/fireflyframework/ecm/adapter/adobesign/AdobeSignAdapterProperties.java). Nested configuration is declared in the linked class. Defaults are the field initializers.

| Setting | Java type | Source default |
| --- | --- | --- |
| `firefly.ecm.adapter.adobe-sign.client-id` | `String` | `not initialized` |
| `firefly.ecm.adapter.adobe-sign.client-secret` | `String` | `not initialized` |
| `firefly.ecm.adapter.adobe-sign.refresh-token` | `String` | `not initialized` |
| `firefly.ecm.adapter.adobe-sign.base-url` | `String` | `"https://api.na1.adobesign.com"` |
| `firefly.ecm.adapter.adobe-sign.api-version` | `String` | `"v6"` |
| `firefly.ecm.adapter.adobe-sign.webhook-url` | `String` | `not initialized` |
| `firefly.ecm.adapter.adobe-sign.webhook-secret` | `String` | `not initialized` |
| `firefly.ecm.adapter.adobe-sign.connection-timeout` | `Duration` | `Duration.ofSeconds(30)` |
| `firefly.ecm.adapter.adobe-sign.read-timeout` | `Duration` | `Duration.ofSeconds(60)` |
| `firefly.ecm.adapter.adobe-sign.max-retries` | `Integer` | `3` |
| `firefly.ecm.adapter.adobe-sign.token-expiration` | `Integer` | `3600` |
| `firefly.ecm.adapter.adobe-sign.default-email-subject` | `String` | `"Please sign this document"` |
| `firefly.ecm.adapter.adobe-sign.default-email-message` | `String` | `"Please review and sign the attached document(s)."` |
| `firefly.ecm.adapter.adobe-sign.enable-embedded-signing` | `Boolean` | `false` |

Property-based wiring conditions found in this module:

- `name = "firefly.ecm.esignature.provider", havingValue = "adobe-sign"`. [AdobeSignAutoConfiguration.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/ecm-esignature-adobe-sign/src/main/java/org/fireflyframework/ecm/adapter/adobesign/AdobeSignAutoConfiguration.java).
- `name = "firefly.ecm.esignature.provider", havingValue = "adobe-sign"`. [AdobeSignSignatureEnvelopeAdapter.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/ecm-esignature-adobe-sign/src/main/java/org/fireflyframework/ecm/adapter/adobesign/AdobeSignSignatureEnvelopeAdapter.java).

## Public API and extension points

| Type | Contract and responsibility |
| --- | --- |
| [`AdobeSignSignatureEnvelopeAdapter`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/ecm-esignature-adobe-sign/src/main/java/org/fireflyframework/ecm/adapter/adobesign/AdobeSignSignatureEnvelopeAdapter.java) | Defines `AdobeSignSignatureEnvelopeAdapter`, `createEnvelope`, `ensureValidAccessToken`, `buildAgreementRequest` operations. |
| [`AdobeSignAdapterProperties`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/ecm-esignature-adobe-sign/src/main/java/org/fireflyframework/ecm/adapter/adobesign/AdobeSignAdapterProperties.java) | Carries `clientId`, `clientSecret`, `refreshToken`, `baseUrl`, `apiVersion` data; field constraints and defaults are declared in the linked source. |
| [`AdobeSignAutoConfiguration`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/ecm-esignature-adobe-sign/src/main/java/org/fireflyframework/ecm/adapter/adobesign/AdobeSignAutoConfiguration.java) | Defines `adobeSignWebClient`, `adobeSignCircuitBreaker`, `adobeSignRetry`, `adobeSignSignatureEnvelopeAdapter` operations. |

## Related components

| Related component | Why it belongs nearby |
| --- | --- |
| [Enterprise Content Management Library](ecm.md) | Define content-management ports, adapter selection, document contracts, and resilience/observability support. |

## Build and test

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-ecm-esignature-adobe-sign -am verify
./mvnw -pl :fireflyframework-ecm-esignature-adobe-sign -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Representative test sources:

- [AdobeSignAdapterTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/ecm-esignature-adobe-sign/src/test/java/org/fireflyframework/ecm/adapter/adobesign/AdobeSignAdapterTest.java).

Mock, local-container, and application-context tests do not establish acceptance against a live provider account. Validate the intended provider deployment and credentials separately.

## API reference excerpt

Contract excerpt from [AdobeSignSignatureEnvelopeAdapter.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/ecm-esignature-adobe-sign/src/main/java/org/fireflyframework/ecm/adapter/adobesign/AdobeSignSignatureEnvelopeAdapter.java). This fragment needs the full type and its imports.

```java
public class AdobeSignSignatureEnvelopeAdapter implements SignatureEnvelopePort {
    private final WebClient webClient;
    private final AdobeSignAdapterProperties properties;
    private final ObjectMapper objectMapper;
    private final DocumentContentPort documentContentPort;
    private final DocumentPort documentPort;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    private final Map<UUID, String> envelopeIdMapping = new ConcurrentHashMap<>();
    private final Map<String, UUID> externalIdMapping = new ConcurrentHashMap<>();
    private volatile String accessToken;
    private volatile Instant tokenExpiresAt;
    public AdobeSignSignatureEnvelopeAdapter(WebClient webClient,
                                           AdobeSignAdapterProperties properties,
                                           ObjectMapper objectMapper,
                                           DocumentContentPort documentContentPort,
                                           DocumentPort documentPort,
        this.webClient = webClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.documentContentPort = documentContentPort;
        this.documentPort = documentPort;
        this.circuitBreaker = circuitBreaker;
        this.retry = retry;
```

## Detailed reference

### Configuration

All properties live under the `firefly.ecm.adapter.adobe-sign` prefix (bound by `AdobeSignAdapterProperties`). The provider selector `firefly.ecm.esignature.provider` lives under `firefly.ecm.esignature`.

```yaml
firefly:
  ecm:
    esignature:
      provider: adobe-sign           # selects this adapter (required to activate)
    adapter:
      adobe-sign:
        client-id: ${ADOBE_SIGN_CLIENT_ID}          # required
        client-secret: ${ADOBE_SIGN_CLIENT_SECRET}  # required
        refresh-token: ${ADOBE_SIGN_REFRESH_TOKEN}  # required
        base-url: https://api.na1.adobesign.com     # Adobe Sign API access point (region-specific)
        api-version: v6                             # Adobe Sign REST API version
        webhook-url:                                # optional callback URL for status events
        webhook-secret:                             # optional shared secret to verify webhooks
        connection-timeout: 30s                     # HTTP connection timeout
        read-timeout: 60s                           # HTTP read timeout
        max-retries: 3                              # Resilience4j retry attempts (0-10)
        token-expiration: 3600                      # access-token lifetime hint, seconds (300-86400)
        default-email-subject: "Please sign this document"
        default-email-message: "Please review and sign the attached document(s)."
        enable-embedded-signing: false              # embedded (in-app) signing flow
        return-url:                                 # redirect URL after embedded signing
        enable-document-retention: true             # retain signed documents in Adobe Sign
        document-retention-days: 365                # retention window in days (1-3650)
        enable-reminders: true                      # send signing reminders to recipients
        reminder-frequency-days: 3                  # reminder cadence in days (1-30)
```

#### Key properties

| Property | Default | Description |
| --- | --- | --- |
| `firefly.ecm.esignature.provider` | _(none)_ | Must equal `adobe-sign` to activate this adapter. |
| `client-id` | _(required)_ | Adobe Sign OAuth client (application) id. |
| `client-secret` | _(required)_ | Adobe Sign OAuth client secret. |
| `refresh-token` | _(required)_ | Long-lived OAuth refresh token used to mint access tokens. |
| `base-url` | `https://api.na1.adobesign.com` | Region-specific Adobe Sign API access point. |
| `api-version` | `v6` | Adobe Sign REST API version used in request paths. |
| `connection-timeout` | `30s` | WebClient connection timeout (`java.time.Duration`). |
| `read-timeout` | `60s` | WebClient read timeout (`java.time.Duration`). |
| `max-retries` | `3` | Retry attempts on transient failures (validated `0`-`10`). |
| `token-expiration` | `3600` | Access-token lifetime hint in seconds (validated `300`-`86400`). |
| `enable-embedded-signing` | `false` | Enables the embedded signing flow (`return-url` used for redirect). |
| `enable-document-retention` / `document-retention-days` | `true` / `365` | Signed-document retention policy (days validated `1`-`3650`). |
| `enable-reminders` / `reminder-frequency-days` | `true` / `3` | Recipient reminder policy (cadence validated `1`-`30`). |

A ready-to-import profile is bundled at `src/main/resources/application-adobe-sign.yml`; activate it with the Spring profile `adobe-sign` to apply the baseline configuration.

### How It Works

`AdobeSignSignatureEnvelopeAdapter` translates the provider-neutral `SignatureEnvelope` domain model into Adobe Sign *agreements*:

- Authentication. `ensureValidAccessToken()` exchanges the configured `refresh-token` for an access token via Adobe Sign's `/oauth/token` endpoint, caching it until shortly before expiry.
- Create. `createEnvelope(...)` `POST`s an agreement to `/api/rest/{apiVersion}/agreements`, records the bidirectional mapping between the framework envelope `UUID` and the Adobe Sign agreement id, and returns the envelope in `DRAFT` status with provider `ADOBE_SIGN`.
- Read & sync. `getEnvelope(...)`, `getEnvelopeByExternalId(...)` and `syncEnvelopeStatus(...)` `GET` the agreement and map Adobe Sign status back onto `EnvelopeStatus`.
- Resilience. each remote pipeline is wrapped with `CircuitBreakerOperator` and `RetryOperator` so transient Adobe Sign failures are retried and sustained failures trip the breaker.

> Note: this adapter currently focuses on the agreement create/read/status path. Some `SignatureEnvelopePort` operations (e.g. Embedded signing URL generation, status-filtered listings) are intentionally minimal or return empty results in the current implementation; contributions extending coverage are welcome.

## Continue the application

Use the [Enterprise Content Management guide](ecm.md) for the next part of this integration. The runnable submission/query service is in [Getting started](../getting-started.md).

## Source and further reading

- [Maven project and dependency declarations](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/ecm-esignature-adobe-sign/pom.xml).
- [Production source](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/ecm-esignature-adobe-sign/src/main/java).
- [org.springframework.boot.autoconfigure.AutoConfiguration.imports](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/ecm-esignature-adobe-sign/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports).
- [application-adobe-sign.yml](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/ecm-esignature-adobe-sign/src/main/resources/application-adobe-sign.yml).
- [Module catalog](index.md) and [getting started](../getting-started.md).
- [Maven platform](../maven.md) and [architecture](../architecture.md).

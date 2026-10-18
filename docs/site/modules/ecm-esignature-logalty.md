![Jafly. ECM eSignature - Logalty](../assets/modules/ecm-esignature-logalty.svg)

# ECM eSignature - Logalty

Implement the ECM signature-envelope port with Logalty.

Configure the provider credentials and base URL through external configuration and select the Logalty adapter. Check the source contract for supported envelope operations before mapping a signing workflow to it.

| Maven artifact | Packaging | Source project |
| --- | --- | --- |
| `org.fireflyframework:fireflyframework-ecm-esignature-logalty` | `jar` | [modules/ecm-esignature-logalty](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/ecm-esignature-logalty) |

Part of [Domain and data](index.md#domain-and-data).

## In the onboarding service

If onboarding includes a signed agreement, use the Logalty adapter for the configured envelope workflow. The application supplies the agreement, recipients, and account configuration.

## Add the component

[Installation and version availability](../installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-ecm-esignature-logalty</artifactId>
    <version>26.10.18</version>
</dependency>
```

## Quick start

The injection examples use Lombok `@RequiredArgsConstructor`. Add its provided dependency and annotation processor as described in [Constructor injection](../maven.md#constructor-injection).

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

## Configuration and wiring

The `firefly.ecm.adapter.logalty` namespace is bound by [LogaltyAdapterProperties.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/ecm-esignature-logalty/src/main/java/org/fireflyframework/ecm/adapter/logalty/LogaltyAdapterProperties.java). Nested configuration is declared in the linked class. Defaults are the field initializers.

| Setting | Java type | Source default |
| --- | --- | --- |
| `firefly.ecm.adapter.logalty.client-id` | `String` | `not initialized` |
| `firefly.ecm.adapter.logalty.client-secret` | `String` | `not initialized` |
| `firefly.ecm.adapter.logalty.username` | `String` | `not initialized` |
| `firefly.ecm.adapter.logalty.password` | `String` | `not initialized` |
| `firefly.ecm.adapter.logalty.base-url` | `String` | `"https://api.logalty.com"` |
| `firefly.ecm.adapter.logalty.api-version` | `String` | `"v1"` |
| `firefly.ecm.adapter.logalty.webhook-url` | `String` | `not initialized` |
| `firefly.ecm.adapter.logalty.webhook-secret` | `String` | `not initialized` |
| `firefly.ecm.adapter.logalty.connection-timeout` | `Duration` | `Duration.ofSeconds(30)` |
| `firefly.ecm.adapter.logalty.read-timeout` | `Duration` | `Duration.ofSeconds(60)` |
| `firefly.ecm.adapter.logalty.max-retries` | `Integer` | `3` |
| `firefly.ecm.adapter.logalty.token-expiration` | `Integer` | `3600` |
| `firefly.ecm.adapter.logalty.default-email-subject` | `String` | `"Firma requerida / Signature required"` |
| `firefly.ecm.adapter.logalty.default-email-message` | `String` | `nested/default initializer; see source` |

Property-based wiring conditions found in this module:

- `name = "firefly.ecm.esignature.provider", havingValue = "logalty"`. [LogaltyAdapterAutoConfiguration.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/ecm-esignature-logalty/src/main/java/org/fireflyframework/ecm/adapter/logalty/LogaltyAdapterAutoConfiguration.java).
- `name = "firefly.ecm.esignature.provider", havingValue = "logalty"`. [LogaltySignatureEnvelopeAdapter.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/ecm-esignature-logalty/src/main/java/org/fireflyframework/ecm/adapter/logalty/LogaltySignatureEnvelopeAdapter.java).

## Public API and extension points

| Type | Contract and responsibility |
| --- | --- |
| [`LogaltySignatureEnvelopeAdapter`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/ecm-esignature-logalty/src/main/java/org/fireflyframework/ecm/adapter/logalty/LogaltySignatureEnvelopeAdapter.java) | Defines `LogaltySignatureEnvelopeAdapter`, `createEnvelope`, `updateEnvelope`, `deleteEnvelope` operations. |
| [`LogaltyAdapterProperties`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/ecm-esignature-logalty/src/main/java/org/fireflyframework/ecm/adapter/logalty/LogaltyAdapterProperties.java) | Configuration properties for the Logalty eSignature adapter. |
| [`LogaltyAdapterAutoConfiguration`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/ecm-esignature-logalty/src/main/java/org/fireflyframework/ecm/adapter/logalty/LogaltyAdapterAutoConfiguration.java) | Defines `logaltyWebClient`, `logaltyCircuitBreaker`, `logaltyRetry`, `logaltyObjectMapper` operations. |

## Related components

| Related component | Why it belongs nearby |
| --- | --- |
| [Enterprise Content Management Library](ecm.md) | Define content-management ports, adapter selection, document contracts, and resilience/observability support. |

## Build and test

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-ecm-esignature-logalty -am verify
./mvnw -pl :fireflyframework-ecm-esignature-logalty -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

This subproject has no authored `src/test/java` suite. Its Maven build still validates the configured compilation/generation and any reactor prerequisites. Use the consuming component tests to validate integration behavior.

## API reference excerpt

Contract excerpt from [LogaltySignatureEnvelopeAdapter.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/ecm-esignature-logalty/src/main/java/org/fireflyframework/ecm/adapter/logalty/LogaltySignatureEnvelopeAdapter.java). This fragment needs the full type and its imports.

```java
public class LogaltySignatureEnvelopeAdapter implements SignatureEnvelopePort {
    private final WebClient webClient;
    private final LogaltyAdapterProperties properties;
    private final ObjectMapper objectMapper;
    private final DocumentContentPort documentContentPort;
    private final DocumentPort documentPort;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    private final Map<UUID, String> envelopeIdMapping = new ConcurrentHashMap<>();
    private final Map<String, UUID> externalIdMapping = new ConcurrentHashMap<>();
    private volatile String accessToken;
    private volatile Instant tokenExpiresAt;
    public LogaltySignatureEnvelopeAdapter(WebClient webClient,
                                         LogaltyAdapterProperties properties,
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

All properties live under the `firefly.ecm.adapter.logalty` prefix (bound by `LogaltyAdapterProperties`). The adapter only activates when `firefly.ecm.esignature.provider=logalty`.

```yaml
firefly:
  ecm:
    esignature:
      provider: logalty                    # activation switch (no default)
    adapter:
      logalty:
        # --- Authentication (client credentials required) ---
        client-id: ${LOGALTY_CLIENT_ID}        # required
        client-secret: ${LOGALTY_CLIENT_SECRET} # required
        username:                              # optional, alternative to OAuth
        password:                              # optional, alternative to OAuth

        # --- Endpoint ---
        base-url: https://api.logalty.com      # production; sandbox: https://api-sandbox.logalty.com
        api-version: v1
        sandbox-mode: false

        # --- Webhooks ---
        webhook-url:                           # receive signature status callbacks
        webhook-secret:                        # validate incoming webhook requests

        # --- HTTP / resilience ---
        connection-timeout: 30s                # Duration
        read-timeout: 60s                      # Duration
        max-retries: 3                         # 0..10
        token-expiration: 3600                 # seconds, 300..86400

        # --- Signing behaviour ---
        default-signature-type: ADVANCED       # SIMPLE | ADVANCED | QUALIFIED
        enable-embedded-signing: false
        return-url:                            # used for embedded signing
        enable-biometric-signature: false
        enable-sms-verification: false
        enable-video-identification: false

        # --- Messaging ---
        default-email-subject: "Firma requerida / Signature required"
        default-email-message: "Por favor, revise y firme el documento adjunto / Please review and sign the attached document."

        # --- Reminders & retention ---
        enable-reminders: true
        reminder-frequency-days: 3             # 1..30
        enable-document-retention: true
        document-retention-days: 365           # 1..3650
```

#### Key properties

| Property | Default | Description |
|----------|---------|-------------|
| `firefly.ecm.esignature.provider` | _(none)_ | Must equal `logalty` to activate this adapter. |
| `client-id` | _(required)_ | Logalty API client ID used for OAuth2 `client_credentials`. |
| `client-secret` | _(required)_ | Logalty API client secret. |
| `base-url` | `https://api.logalty.com` | Logalty API endpoint; use the sandbox host for testing. |
| `api-version` | `v1` | API version segment used when building request URIs. |
| `default-signature-type` | `ADVANCED` | eIDAS signature level: `SIMPLE`, `ADVANCED` or `QUALIFIED`. |
| `connection-timeout` / `read-timeout` | `30s` / `60s` | HTTP connection and response timeouts. |
| `max-retries` | `3` | Retry attempts for transient failures (`0`-`10`). |
| `token-expiration` | `3600` | OAuth token lifetime in seconds (`300`-`86400`). |
| `enable-biometric-signature` | `false` | Capture biometric signature data. |
| `enable-sms-verification` | `false` | Require SMS-OTP signer verification. |
| `enable-video-identification` | `false` | Require video identification of signers. |
| `enable-embedded-signing` / `return-url` | `false` / _(none)_ | Enable in-app embedded signing and the post-signature redirect URL. |
| `enable-document-retention` / `document-retention-days` | `true` / `365` | Long-term archival in Logalty (`1`-`3650` days). |
| `enable-reminders` / `reminder-frequency-days` | `true` / `3` | Automatic signature reminders (`1`-`30` days). |
| `webhook-url` / `webhook-secret` | _(none)_ | Endpoint and shared secret for inbound status callbacks. |

`client-id` and `client-secret` are validated as `@NotBlank`; the bounded numeric properties are enforced via `@Min`/`@Max`. Invalid configuration fails fast at startup.

### How It Works

`LogaltyAdapterAutoConfiguration` is registered through `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` and is gated by `@ConditionalOnClass({WebClient.class, CircuitBreaker.class})` and `@ConditionalOnProperty(firefly.ecm.esignature.provider=logalty)`. When active it contributes (each as `@ConditionalOnMissingBean`, so any can be overridden):

- a reactive `WebClient` bound to `base-url` with the configured response timeout;
- a named `logaltyCircuitBreaker` (`CircuitBreaker`). 50% failure-rate threshold, 30s open state, sliding window of 10, automatic open→half-open transition;
- a named `logaltyRetry` (`Retry`). `max-retries` attempts retrying socket/IO/`WebClientRequestException` failures;
- a Logalty-tuned `ObjectMapper`; and
- the `LogaltySignatureEnvelopeAdapter` (`SignatureEnvelopePort`) wired with the ECM `DocumentPort` and `DocumentContentPort`.

Every outbound call goes through `ensureValidAccessToken()` (cached OAuth2 token with a 60-second safety margin) and is decorated with the circuit breaker and retry operators (`transformDeferred(CircuitBreakerOperator.of(...))` / `RetryOperator.of(...)`). Logalty status values are normalised to the ECM `EnvelopeStatus` enum (`DRAFT`, `SENT`, `COMPLETED`, `VOIDED`, `EXPIRED`).

## Continue the application

Use the [Enterprise Content Management guide](ecm.md) for the next part of this integration. The runnable submission/query service is in [Getting started](../getting-started.md).

## Source and further reading

- [Maven project and dependency declarations](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/ecm-esignature-logalty/pom.xml).
- [Production source](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/ecm-esignature-logalty/src/main/java).
- [org.springframework.boot.autoconfigure.AutoConfiguration.imports](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/ecm-esignature-logalty/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports).
- [Module catalog](index.md) and [getting started](../getting-started.md).
- [Maven platform](../maven.md) and [architecture](../architecture.md).

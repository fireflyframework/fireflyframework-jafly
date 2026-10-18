![Jafly. Notifications - Resend](../assets/modules/notifications-resend.svg)

# Notifications - Resend

Send email through the Resend provider adapter.

Add the adapter and notifications-core, configure the Resend API key/sender settings, and use the common email service contract. Provider acceptance of a request is distinct from delivery to the recipient mailbox.

| Maven artifact | Packaging | Source project |
| --- | --- | --- |
| `org.fireflyframework:fireflyframework-notifications-resend` | `jar` | [modules/notifications-resend](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/notifications-resend) |

Part of [External services](index.md#external-services).

## In the onboarding service

Resend is another email provider for application confirmations. Select it through its provider property, configure the API key, and register a core facade only when the application uses that service API.

## Add the component

[Installation and version availability](../installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-notifications-resend</artifactId>
    <version>26.10.17-SNAPSHOT</version>
</dependency>
```

## Quick start

The Java examples in this section are API fragments for application code. Use the stated imports and supply the application-owned collaborators described alongside them.

If you use a core channel service instead of the provider SPI directly, [register the matching service implementation explicitly](notifications-core.md#quick-start). Provider auto-configuration does not expand the application's component scan.

1. Add the dependency (see [Installation](#add-the-component)).

2. Select Resend and provide credentials in `application.yml`:

```yaml
firefly:
  notifications:
    email:
      provider: resend          # selects this adapter as the active EmailProvider
    resend:
      api-key: ${RESEND_API_KEY}
      default-from: "Acme <no-reply@acme.com>"
```

3. Inject `EmailProvider` and send a message. Your code depends only on the core SPI, never on Resend:

```java
import org.fireflyframework.notifications.interfaces.dtos.email.v1.EmailRequestDTO;
import org.fireflyframework.notifications.interfaces.dtos.email.v1.EmailResponseDTO;
import org.fireflyframework.notifications.interfaces.providers.email.v1.EmailProvider;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class WelcomeEmailService {

    private final EmailProvider emailProvider; // ResendEmailProvider, auto-wired

    public WelcomeEmailService(EmailProvider emailProvider) {
        this.emailProvider = emailProvider;
    }

    public Mono<EmailResponseDTO> sendWelcome(String recipient) {
        EmailRequestDTO request = EmailRequestDTO.builder()
                .from("Acme <no-reply@acme.com>") // optional; falls back to firefly.notifications.resend.default-from
                .to(recipient)
                .subject("Welcome to Acme")
                .html("<h1>Welcome!</h1><p>Glad to have you on board.</p>")
                .text("Welcome! Glad to have you on board.")
                .build();

        return emailProvider.sendEmail(request); // -> EmailResponseDTO.success(messageId) or .error(reason)
    }
}
```

On success the returned `EmailResponseDTO` carries the Resend message id; on failure it carries the error reason (the exception is logged and swallowed, not propagated).

## Configuration and wiring

The `firefly.notifications.resend` namespace is bound by [ResendProperties.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/notifications-resend/src/main/java/org/fireflyframework/notifications/providers/resend/properties/v1/ResendProperties.java). Nested configuration is declared in the linked class. Defaults are the field initializers.

| Setting | Java type | Source default |
| --- | --- | --- |
| `firefly.notifications.resend.api-key` | `String` | `not initialized` |
| `firefly.notifications.resend.default-from` | `String` | `not initialized` |
| `firefly.notifications.resend.base-url` | `String` | `"https://api.resend.com"` |

Property-based wiring conditions found in this module:

- `name = "firefly.notifications.email.provider", havingValue = "resend"`. [ResendAutoConfiguration.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/notifications-resend/src/main/java/org/fireflyframework/notifications/providers/resend/config/v1/ResendAutoConfiguration.java).
- `prefix = "firefly.notifications.resend", name = "api-key"`. [ResendAutoConfiguration.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/notifications-resend/src/main/java/org/fireflyframework/notifications/providers/resend/config/v1/ResendAutoConfiguration.java).

## Public API and extension points

| Type | Contract and responsibility |
| --- | --- |
| [`ResendEmailProvider`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/notifications-resend/src/main/java/org/fireflyframework/notifications/providers/resend/core/v1/ResendEmailProvider.java) | Defines `sendEmail`, `buildPayload` operations. |
| [`ResendProperties`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/notifications-resend/src/main/java/org/fireflyframework/notifications/providers/resend/properties/v1/ResendProperties.java) | Carries `apiKey`, `defaultFrom`, `baseUrl` data; field constraints and defaults are declared in the linked source. |
| [`ResendAutoConfiguration`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/notifications-resend/src/main/java/org/fireflyframework/notifications/providers/resend/config/v1/ResendAutoConfiguration.java) | Defines `resendRestClient`, `resendEmailProvider` operations. |

## Related components

| Related component | Why it belongs nearby |
| --- | --- |
| [Notifications Core](notifications-core.md) | Send email, SMS, and push notifications through provider ports, with templates, preferences, and metrics. |
| [Common Client Library](client.md) | Call REST, SOAP, and gRPC services through reactive client builders with shared resilience and observability facilities. |

## Build and test

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-notifications-resend -am verify
./mvnw -pl :fireflyframework-notifications-resend -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

This subproject has no authored `src/test/java` suite. Its Maven build still validates the configured compilation/generation and any reactor prerequisites. Use the consuming component tests to validate integration behavior.

## API reference excerpt

Contract excerpt from [ResendEmailProvider.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/notifications-resend/src/main/java/org/fireflyframework/notifications/providers/resend/core/v1/ResendEmailProvider.java). This fragment needs the full type and its imports.

```java
public class ResendEmailProvider implements EmailProvider {
    private final ResendProperties properties;
    private final RestClient resendClient;
    public Mono<EmailResponseDTO> sendEmail(EmailRequestDTO request) {
        return Mono.fromCallable(() -> buildPayload(request))
                .flatMap(payload -> resendClient
                        .post("/emails", ResendSendResponse.class)
                        .withBody(payload)
                        .execute())
                .map(resp -> EmailResponseDTO.success(resp.getId()))
                .onErrorResume(ex -> {
                    log.error("Error sending email via Resend", ex);
                    return Mono.just(EmailResponseDTO.error(ex.getMessage()));
                })
                .subscribeOn(Schedulers.boundedElastic());
    }
    private Map<String, Object> buildPayload(EmailRequestDTO req) {
        Map<String, Object> body = new LinkedHashMap<>();
        String from = StringUtils.hasText(req.getFrom()) ? req.getFrom() : properties.getDefaultFrom();
        body.put("from", from);
        body.put("to", req.getTo());
        body.put("subject", req.getSubject());
        if (StringUtils.hasText(req.getHtml())) {
            body.put("html", req.getHtml());
```

## Detailed reference

### Configuration

All properties live under the `firefly.notifications.resend` prefix, except the provider selector which is shared by the notifications core.

```yaml
firefly:
  notifications:
    email:
      provider: resend                       # required: activates this adapter
    resend:
      api-key: ${RESEND_API_KEY}             # required: your Resend API key (re_...)
      default-from: "Acme <no-reply@acme.com>" # optional: fallback sender when a message omits `from`
      base-url: https://api.resend.com        # optional: Resend API base URL (override for tests/proxies)
```

| Property | Default | Description |
| --- | --- | --- |
| `firefly.notifications.email.provider` | _(none)_ | Selector that activates this adapter. Must equal `resend` for the auto-configuration to apply. |
| `firefly.notifications.resend.api-key` | _(none)_ | Resend API key used as the `Bearer` token. Required. the `RestClient`/`EmailProvider` beans are only created when this is present. |
| `firefly.notifications.resend.default-from` | _(none)_ | Sender used when an `EmailRequestDTO` does not specify `from`. |
| `firefly.notifications.resend.base-url` | `https://api.resend.com` | Base URL of the Resend REST API. Override to point at a mock server in tests. |

The auto-configuration is gated by two conditions: `firefly.notifications.email.provider=resend` (class-level) and the presence of `firefly.notifications.resend.api-key` (on the `RestClient` bean). If either is missing, the adapter stays inactive and another provider may take over.

### How It Works

1. `ResendAutoConfiguration` is imported via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` and activates when `firefly.notifications.email.provider=resend`.
2. It builds a named `resend` `RestClient` from `ServiceClient.rest("resend")` with the configured `base-url`, JSON content type, and an `Authorization: Bearer <api-key>` header.
3. It registers `ResendEmailProvider` as the `EmailProvider` bean (unless one is already defined).
4. `ResendEmailProvider.sendEmail(...)` translates the `EmailRequestDTO` into the Resend JSON payload (`from`, `to`, `subject`, `html`/`text`, `cc`, `bcc`, Base64 `attachments`) and `POST`s it to `/emails`, mapping the response id into `EmailResponseDTO.success(id)`.

## Continue the application

Use the [Notifications Core guide](notifications-core.md) for the next part of this integration. The runnable submission/query service is in [Getting started](../getting-started.md).

## Source and further reading

- [Maven project and dependency declarations](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/notifications-resend/pom.xml).
- [Production source](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/notifications-resend/src/main/java).
- [org.springframework.boot.autoconfigure.AutoConfiguration.imports](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/notifications-resend/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports).
- [Module catalog](index.md) and [getting started](../getting-started.md).
- [Maven platform](../maven.md) and [architecture](../architecture.md).

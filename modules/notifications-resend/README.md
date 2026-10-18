![Jafly. Notifications - Resend](../../docs/site/assets/modules/notifications-resend.svg)

# Notifications - Resend

Send email through the Resend provider adapter.

Add the adapter and notifications-core, configure the Resend API key/sender settings, and use the common email service contract. Provider acceptance of a request is distinct from delivery to the recipient mailbox.

[Module guide](../../docs/site/modules/notifications-resend.md) · [Jafly README](../../README.md) · [Module catalog](../../docs/site/modules/index.md)

## In the onboarding service

Resend is another email provider for application confirmations. Select it through its provider property, configure the API key, and register a core facade only when the application uses that service API.

## Installation

[Installation and version availability](../../docs/site/installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-notifications-resend</artifactId>
    <version>26.10.19</version>
</dependency>
```

## Quick start

The injection examples use Lombok `@RequiredArgsConstructor`. Add its provided dependency and annotation processor as described in [Constructor injection](../../docs/site/maven.md#constructor-injection).

The Java examples in this section are API fragments for application code. Use the stated imports and supply the application-owned collaborators described alongside them.

If you use a core channel service instead of the provider SPI directly, [register the matching service implementation explicitly](../../docs/site/modules/notifications-core.md#quick-start). Provider auto-configuration does not expand the application's component scan.

1. Add the dependency (see [Installation](#installation)).

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
import lombok.RequiredArgsConstructor;
import org.fireflyframework.notifications.interfaces.dtos.email.v1.EmailRequestDTO;
import org.fireflyframework.notifications.interfaces.dtos.email.v1.EmailResponseDTO;
import org.fireflyframework.notifications.interfaces.providers.email.v1.EmailProvider;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class WelcomeEmailService {

    private final EmailProvider emailProvider; // ResendEmailProvider, auto-wired



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

## Configuration

The `firefly.notifications.resend` namespace is bound by [ResendProperties.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/notifications-resend/src/main/java/org/fireflyframework/notifications/providers/resend/properties/v1/ResendProperties.java). Nested configuration is declared in the linked class. Defaults are the field initializers.

| Setting | Java type | Source default |
| --- | --- | --- |
| `firefly.notifications.resend.api-key` | `String` | `not initialized` |
| `firefly.notifications.resend.default-from` | `String` | `not initialized` |
| `firefly.notifications.resend.base-url` | `String` | `"https://api.resend.com"` |

## API overview

| Type | Contract and responsibility |
| --- | --- |
| [`ResendEmailProvider`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/notifications-resend/src/main/java/org/fireflyframework/notifications/providers/resend/core/v1/ResendEmailProvider.java) | Defines `sendEmail`, `buildPayload` operations. |
| [`ResendProperties`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/notifications-resend/src/main/java/org/fireflyframework/notifications/providers/resend/properties/v1/ResendProperties.java) | Carries `apiKey`, `defaultFrom`, `baseUrl` data; field constraints and defaults are declared in the linked source. |
| [`ResendAutoConfiguration`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/notifications-resend/src/main/java/org/fireflyframework/notifications/providers/resend/config/v1/ResendAutoConfiguration.java) | Defines `resendRestClient`, `resendEmailProvider` operations. |

## Continue the application

Use the [Notifications Core guide](../../docs/site/modules/notifications-core.md) for the next part of this integration.

## Build and validation

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-notifications-resend -am verify
./mvnw -pl :fireflyframework-notifications-resend -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Configuration details and source references: [module guide](../../docs/site/modules/notifications-resend.md).

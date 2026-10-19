![Jafly. Notifications - SendGrid](../assets/modules/notifications-sendgrid.svg)

# Notifications - SendGrid

Send email through SendGrid while retaining the common notification service contract.

Configure the SendGrid credential and sender information, then use EmailService with EmailRequestDTO. Keep the provider credential in external configuration rather than in the request model.

| Maven artifact | Packaging | Source project |
| --- | --- | --- |
| `org.fireflyframework:fireflyframework-notifications-sendgrid` | `jar` | [modules/notifications-sendgrid](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/notifications-sendgrid) |

Part of [External services](index.md#external-services).

## In the onboarding service

SendGrid can deliver application confirmations through the email provider port. Register the core email service explicitly if the application injects EmailService, and keep the provider key in deployment configuration.

## Add the component

[Installation and version availability](../installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-notifications-sendgrid</artifactId>
    <version>26.10.21-SNAPSHOT</version>
</dependency>
```

## Quick start

The injection examples use Lombok `@RequiredArgsConstructor`. Add its provided dependency and annotation processor as described in [Constructor injection](../maven.md#constructor-injection).

The Java examples in this section are API fragments for application code. Use the stated imports and supply the application-owned collaborators described alongside them.

1. Add the dependencies (core + this adapter):

```xml
<dependencies>
    <dependency>
        <groupId>org.fireflyframework</groupId>
        <artifactId>fireflyframework-notifications-core</artifactId>
    </dependency>
    <dependency>
        <groupId>org.fireflyframework</groupId>
        <artifactId>fireflyframework-notifications-sendgrid</artifactId>
    </dependency>
</dependencies>
```

2. Select SendGrid and provide your API key in `application.yml`:

```yaml
firefly:
  notifications:
    email:
      provider: sendgrid       # selects this adapter
    sendgrid:
      api-key: ${SENDGRID_API_KEY}
```

3. Register the email service, then inject it. The adapter creates the `EmailProvider` bean. Register the core service explicitly in application configuration:

```java
import org.fireflyframework.notifications.core.services.email.v1.EmailServiceImpl;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@Import(EmailServiceImpl.class)
class EmailNotificationsConfiguration {
}
```

Inject `EmailService` after the configuration and provider have been loaded:

```java
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import org.fireflyframework.notifications.core.services.email.v1.EmailService;
import org.fireflyframework.notifications.interfaces.dtos.email.v1.EmailRequestDTO;
import org.fireflyframework.notifications.interfaces.dtos.email.v1.EmailResponseDTO;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class WelcomeMailer {

    private final EmailService emailService;



    public Mono<EmailResponseDTO> sendWelcome(String to) {
        EmailRequestDTO request = new EmailRequestDTO();
        request.setFrom("noreply@example.com");   // a verified SendGrid sender
        request.setTo(to);
        request.setSubject("Welcome to Firefly");
        request.setHtml("<h1>Welcome!</h1><p>Thanks for signing up.</p>");
        return emailService.sendEmail(request);
    }
}
```

The auto-configuration builds the `SendGrid` client and the `SendGridEmailProvider` bean for you. The returned `EmailResponseDTO`
carries `success`/`error` status and, on success, the SendGrid `X-Message-Id`.

You can also depend on the `EmailProvider` SPI directly if you prefer to bypass the `EmailService` facade. The same `SendGridEmailProvider` bean satisfies it.

## Configuration and wiring

The `firefly.notifications.sendgrid` namespace is bound by [SendGridProperties.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/notifications-sendgrid/src/main/java/org/fireflyframework/notifications/providers/sendgrid/properties/v1/SendGridProperties.java). Nested configuration is declared in the linked class. Defaults are the field initializers.

| Setting | Java type | Source default |
| --- | --- | --- |
| `firefly.notifications.sendgrid.api-key` | `String` | `not initialized` |

Property-based wiring conditions found in this module:

- `name = "firefly.notifications.email.provider", havingValue = "sendgrid"`. [SendGridAutoConfiguration.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/notifications-sendgrid/src/main/java/org/fireflyframework/notifications/providers/sendgrid/config/v1/SendGridAutoConfiguration.java).
- `prefix = "firefly.notifications.sendgrid", name = "api-key"`. [SendGridAutoConfiguration.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/notifications-sendgrid/src/main/java/org/fireflyframework/notifications/providers/sendgrid/config/v1/SendGridAutoConfiguration.java).

## Public API and extension points

| Type | Contract and responsibility |
| --- | --- |
| [`SendGridEmailProvider`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/notifications-sendgrid/src/main/java/org/fireflyframework/notifications/providers/sendgrid/core/v1/SendGridEmailProvider.java) | Defines `sendEmail`, `buildMail` operations. |
| [`SendGridProperties`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/notifications-sendgrid/src/main/java/org/fireflyframework/notifications/providers/sendgrid/properties/v1/SendGridProperties.java) | Carries `apiKey` data; field constraints and defaults are declared in the linked source. |
| [`SendGridAutoConfiguration`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/notifications-sendgrid/src/main/java/org/fireflyframework/notifications/providers/sendgrid/config/v1/SendGridAutoConfiguration.java) | Defines `sendGrid`, `sendGridEmailProvider` operations. |

## Related components

| Related component | Why it belongs nearby |
| --- | --- |
| [Notifications Core](notifications-core.md) | Send email, SMS, and push notifications through provider ports, with templates, preferences, and metrics. |

## Build and test

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-notifications-sendgrid -am verify
./mvnw -pl :fireflyframework-notifications-sendgrid -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

This subproject has no authored `src/test/java` suite. Its Maven build still validates the configured compilation/generation and any reactor prerequisites. Use the consuming component tests to validate integration behavior.

## API reference excerpt

Contract excerpt from [SendGridEmailProvider.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/notifications-sendgrid/src/main/java/org/fireflyframework/notifications/providers/sendgrid/core/v1/SendGridEmailProvider.java). This fragment needs the full type and its imports.

```java
public class SendGridEmailProvider implements EmailProvider {
    private final SendGridProperties properties;
    private final SendGrid sendGrid;
    public Mono<EmailResponseDTO> sendEmail(EmailRequestDTO request) {
        return Mono.fromCallable(() -> {
            try {
                Mail mail = buildMail(request);
                Request sendGridRequest = new Request();
                sendGridRequest.setMethod(Method.POST);
                sendGridRequest.setEndpoint("mail/send");
                sendGridRequest.setBody(mail.build());
                Response response = sendGrid.api(sendGridRequest);
                if (response.getStatusCode() >= 200 && response.getStatusCode() < 300) {
                    String messageId = getMessageIdFromHeaders(response.getHeaders());
                    return EmailResponseDTO.success(messageId);
                } else {
                    return EmailResponseDTO.error("SendGrid error: " + response.getBody());
                }
            } catch (IOException e) {
                return EmailResponseDTO.error(e.getMessage());
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }
    private Mail buildMail(EmailRequestDTO request) {
```

## Detailed reference

### Configuration

All properties live under the `firefly.notifications` prefix. This adapter binds a single
`@ConfigurationProperties` class, `SendGridProperties` (prefix `firefly.notifications.sendgrid`).

```yaml
firefly:
  notifications:
    email:
      provider: sendgrid          # required: activates this adapter
    sendgrid:
      api-key: ${SENDGRID_API_KEY} # required: SendGrid v3 API key with Mail Send permission
```

| Property | Description | Default |
| --- | --- | --- |
| `firefly.notifications.email.provider` | Selects the active email adapter. Must equal `sendgrid` for this module's auto-configuration to activate. | _(none)_ |
| `firefly.notifications.sendgrid.api-key` | SendGrid v3 API key used to authenticate the `SendGrid` client. Required for the `SendGrid` bean to be created. Keep it out of source control (use an environment variable or secret manager). | _(none)_ |

Notes:

- The sender (`from`) address and recipients are supplied per message on the `EmailRequestDTO`, not via configuration. there are no global `from-email` / `from-name` properties. Ensure the `from` address is a SendGrid-verified sender or authenticated domain, otherwise SendGrid rejects the send.
- All adapter beans are `@ConditionalOnMissingBean`. To override delivery behaviour, declare your own `SendGrid` or `EmailProvider` bean and the auto-configured one steps aside.

### How It Works

`SendGridAutoConfiguration` activates only when `firefly.notifications.email.provider=sendgrid` and `com.sendgrid.SendGrid` is on the classpath. It then:

1. Creates a `SendGrid` client from `firefly.notifications.sendgrid.api-key` (only when the key is present).
2. Registers `SendGridEmailProvider` as the `EmailProvider`, unless another `EmailProvider` bean already exists.

On each `sendEmail(...)` call, `SendGridEmailProvider` builds a SendGrid `Mail` (mapping subject, HTML/text content, CC, BCC, and Base64-encoded attachments), issues a `POST mail/send`, and maps the result: 2xx -> `EmailResponseDTO.success(X-Message-Id)`, anything else (or an `IOException`) -> `EmailResponseDTO.error(...)`. The whole call runs via `Mono.fromCallable(...).subscribeOn(Schedulers.boundedElastic())` to keep blocking I/O off the reactive event loop.

## Continue the application

Use the [Notifications Core guide](notifications-core.md) for the next part of this integration. The runnable submission/query service is in [Getting started](../getting-started.md).

## Source and further reading

- [Maven project and dependency declarations](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/notifications-sendgrid/pom.xml).
- [Production source](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/notifications-sendgrid/src/main/java).
- [org.springframework.boot.autoconfigure.AutoConfiguration.imports](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/notifications-sendgrid/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports).
- [Module catalog](index.md) and [getting started](../getting-started.md).
- [Maven platform](../maven.md) and [architecture](../architecture.md).

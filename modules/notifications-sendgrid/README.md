![Jafly. Notifications - SendGrid](../../docs/site/assets/modules/notifications-sendgrid.svg)

# Notifications - SendGrid

Send email through SendGrid while retaining the common notification service contract.

Configure the SendGrid credential and sender information, then use EmailService with EmailRequestDTO. Keep the provider credential in external configuration rather than in the request model.

[Module guide](../../docs/site/modules/notifications-sendgrid.md) · [Jafly README](../../README.md) · [Module catalog](../../docs/site/modules/index.md)

## In the onboarding service

SendGrid can deliver application confirmations through the email provider port. Register the core email service explicitly if the application injects EmailService, and keep the provider key in deployment configuration.

## Installation

[Installation and version availability](../../docs/site/installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-notifications-sendgrid</artifactId>
    <version>26.10.21-SNAPSHOT</version>
</dependency>
```

## Quick start

The injection examples use Lombok `@RequiredArgsConstructor`. Add its provided dependency and annotation processor as described in [Constructor injection](../../docs/site/maven.md#constructor-injection).

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

## Configuration

The `firefly.notifications.sendgrid` namespace is bound by [SendGridProperties.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/notifications-sendgrid/src/main/java/org/fireflyframework/notifications/providers/sendgrid/properties/v1/SendGridProperties.java). Nested configuration is declared in the linked class. Defaults are the field initializers.

| Setting | Java type | Source default |
| --- | --- | --- |
| `firefly.notifications.sendgrid.api-key` | `String` | `not initialized` |

## API overview

| Type | Contract and responsibility |
| --- | --- |
| [`SendGridEmailProvider`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/notifications-sendgrid/src/main/java/org/fireflyframework/notifications/providers/sendgrid/core/v1/SendGridEmailProvider.java) | Defines `sendEmail`, `buildMail` operations. |
| [`SendGridProperties`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/notifications-sendgrid/src/main/java/org/fireflyframework/notifications/providers/sendgrid/properties/v1/SendGridProperties.java) | Carries `apiKey` data; field constraints and defaults are declared in the linked source. |
| [`SendGridAutoConfiguration`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/notifications-sendgrid/src/main/java/org/fireflyframework/notifications/providers/sendgrid/config/v1/SendGridAutoConfiguration.java) | Defines `sendGrid`, `sendGridEmailProvider` operations. |

## Continue the application

Use the [Notifications Core guide](../../docs/site/modules/notifications-core.md) for the next part of this integration.

## Build and validation

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-notifications-sendgrid -am verify
./mvnw -pl :fireflyframework-notifications-sendgrid -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Configuration details and source references: [module guide](../../docs/site/modules/notifications-sendgrid.md).

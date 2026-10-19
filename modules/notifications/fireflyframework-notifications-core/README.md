![Jafly. Notifications Core](../../../docs/site/assets/modules/notifications-core.svg)

# Notifications Core

Send email, SMS, and push notifications through provider ports, with templates, preferences, and metrics.

Inject EmailService, SMSService, or PushService and supply the matching provider bean. Template email requires a NotificationTemplateEngine; the in-memory preference implementation is process-local and should be evaluated before using it for durable user preferences.

[Module guide](../../../docs/site/modules/notifications-core.md) · [Jafly README](../../../README.md) · [Module catalog](../../../docs/site/modules/index.md)

## In the onboarding service

The onboarding service can use EmailService to confirm receipt or request missing information. The wiring recipe below registers the facade explicitly and keeps the provider behind EmailProvider.

## Installation

[Installation and version availability](../../../docs/site/installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-notifications-core</artifactId>
    <version>26.10.20-SNAPSHOT</version>
</dependency>
```

## Quick start

The Java examples in this section are API fragments for application code. Use the stated imports and supply the application-owned collaborators described alongside them.

Choose the provider for the channel you need, then register that channel's service implementation explicitly. For example, the [SendGrid adapter](../../../docs/site/modules/notifications-sendgrid.md) auto-configures an `EmailProvider` when selected and configured. It does not register `EmailServiceImpl` in an unrelated application's component-scan package.

For an application outside `org.fireflyframework.notifications`, place this application-owned configuration in a package scanned by Spring Boot:

```java
import org.fireflyframework.notifications.core.services.email.v1.EmailServiceImpl;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@Import(EmailServiceImpl.class)
class EmailNotificationsConfiguration {
}
```

Now inject the `EmailService` interface in application code. Do not scan every channel implementation when only one provider is configured; SMS and push services require their own provider beans.

```java
import org.fireflyframework.notifications.core.services.email.v1.EmailService;
import org.fireflyframework.notifications.interfaces.dtos.email.v1.EmailRequestDTO;
import org.fireflyframework.notifications.interfaces.dtos.email.v1.EmailResponseDTO;
import reactor.core.publisher.Mono;

EmailRequestDTO request = EmailRequestDTO.builder()
        .from("orders@example.com")
        .to("customer@example.com")
        .subject("Your order")
        .text("Your order has been received.")
        .build();
Mono<EmailResponseDTO> result = emailService.sendEmail(request);
```

`emailService` is the injected bean; compose `result` into the application flow. A provider response is not proof of delivery to the recipient mailbox. Check the response status and the selected provider's error semantics.

| Channel | Service implementation to import | Required provider |
| --- | --- | --- |
| Email | `org.fireflyframework.notifications.core.services.email.v1.EmailServiceImpl` | `EmailProvider` |
| SMS | `org.fireflyframework.notifications.core.services.sms.v1.SMSServiceImpl` | `SMSProvider` |
| Push | `org.fireflyframework.notifications.core.services.push.v1.PushServiceImpl` | `PushProvider` |

Template email additionally needs a `NotificationTemplateEngine` bean, for example `new FreemarkerNotificationTemplateEngine()` with templates under `src/main/resources/notification-templates`. A preference service must also be registered and invoked explicitly when the application applies user preferences; the in-memory implementation is process-local.

## Configuration

This module declares no `@ConfigurationProperties` class of its own. Use the constructor/port contracts above and the configuration of the host or related core module.

## API overview

| Type | Contract and responsibility |
| --- | --- |
| [`EmailService`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/notifications/fireflyframework-notifications-core/src/main/java/org/fireflyframework/notifications/core/services/email/v1/EmailService.java) | Sends email requests and template-email requests through the configured email provider. |
| [`SMSService`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/notifications/fireflyframework-notifications-core/src/main/java/org/fireflyframework/notifications/core/services/sms/v1/SMSService.java) | Sends SMS requests through the configured SMS provider. |
| [`PushService`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/notifications/fireflyframework-notifications-core/src/main/java/org/fireflyframework/notifications/core/services/push/v1/PushService.java) | Sends push-notification requests through the configured push provider. |
| [`NotificationTemplateEngine`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/notifications/fireflyframework-notifications-core/src/main/java/org/fireflyframework/notifications/core/services/template/NotificationTemplateEngine.java) | Renders the requested notification template with application-supplied variables. |
| [`NotificationPreferenceService`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/notifications/fireflyframework-notifications-core/src/main/java/org/fireflyframework/notifications/core/services/preferences/NotificationPreferenceService.java) | Reads and updates the notification preferences used by the service layer. |

## Continue the application

Use the [Notifications - SendGrid guide](../../../docs/site/modules/notifications-sendgrid.md) for the next part of this integration.

## Build and validation

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-notifications-core -am verify
./mvnw -pl :fireflyframework-notifications-core -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Configuration details and source references: [module guide](../../../docs/site/modules/notifications-core.md).

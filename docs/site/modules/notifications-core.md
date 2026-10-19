![Jafly. Notifications Core](../assets/modules/notifications-core.svg)

# Notifications Core

Send email, SMS, and push notifications through provider ports, with templates, preferences, and metrics.

Inject EmailService, SMSService, or PushService and supply the matching provider bean. Template email requires a NotificationTemplateEngine; the in-memory preference implementation is process-local and should be evaluated before using it for durable user preferences.

| Maven artifact | Packaging | Source project |
| --- | --- | --- |
| `org.fireflyframework:fireflyframework-notifications-core` | `jar` | [modules/notifications/fireflyframework-notifications-core](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/notifications/fireflyframework-notifications-core) |

Part of [External services](index.md#external-services).

## In the onboarding service

The onboarding service can use EmailService to confirm receipt or request missing information. The wiring recipe below registers the facade explicitly and keeps the provider behind EmailProvider.

## Add the component

[Installation and version availability](../installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-notifications-core</artifactId>
    <version>26.10.21-SNAPSHOT</version>
</dependency>
```

## Quick start

The Java examples in this section are API fragments for application code. Use the stated imports and supply the application-owned collaborators described alongside them.

Choose the provider for the channel you need, then register that channel's service implementation explicitly. For example, the [SendGrid adapter](notifications-sendgrid.md) auto-configures an `EmailProvider` when selected and configured. It does not register `EmailServiceImpl` in an unrelated application's component-scan package.

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

## Configuration and wiring

This module declares no `@ConfigurationProperties` class of its own. Use the constructor/port contracts above and the configuration of the host or related core module.

Property-based wiring conditions found in this module:

- `prefix = "firefly.observability.metrics", name = "enabled", havingValue = "true", matchIfMissing = true`. [NotificationObservabilityAutoConfiguration.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/notifications/fireflyframework-notifications-core/src/main/java/org/fireflyframework/notifications/observability/NotificationObservabilityAutoConfiguration.java).

## Public API and extension points

| Type | Contract and responsibility |
| --- | --- |
| [`EmailService`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/notifications/fireflyframework-notifications-core/src/main/java/org/fireflyframework/notifications/core/services/email/v1/EmailService.java) | Sends email requests and template-email requests through the configured email provider. |
| [`SMSService`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/notifications/fireflyframework-notifications-core/src/main/java/org/fireflyframework/notifications/core/services/sms/v1/SMSService.java) | Sends SMS requests through the configured SMS provider. |
| [`PushService`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/notifications/fireflyframework-notifications-core/src/main/java/org/fireflyframework/notifications/core/services/push/v1/PushService.java) | Sends push-notification requests through the configured push provider. |
| [`NotificationTemplateEngine`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/notifications/fireflyframework-notifications-core/src/main/java/org/fireflyframework/notifications/core/services/template/NotificationTemplateEngine.java) | Renders the requested notification template with application-supplied variables. |
| [`NotificationPreferenceService`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/notifications/fireflyframework-notifications-core/src/main/java/org/fireflyframework/notifications/core/services/preferences/NotificationPreferenceService.java) | Reads and updates the notification preferences used by the service layer. |

## Related components

| Related component | Why it belongs nearby |
| --- | --- |
| [Notifications Library](notifications.md) | Group the notification service contracts and their core implementation. |
| [Starter Core](starter-core.md) | Assemble core infrastructure conventions for WebClient, actuator, logging, configuration, and service discovery. |

## Build and test

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-notifications-core -am verify
./mvnw -pl :fireflyframework-notifications-core -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Representative test sources:

- [EmailServiceImplTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/notifications/fireflyframework-notifications-core/src/test/java/org/fireflyframework/notifications/core/services/email/v1/EmailServiceImplTest.java).
- [PushServiceImplTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/notifications/fireflyframework-notifications-core/src/test/java/org/fireflyframework/notifications/core/services/push/v1/PushServiceImplTest.java).
- [SMSServiceImplTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/notifications/fireflyframework-notifications-core/src/test/java/org/fireflyframework/notifications/core/services/sms/v1/SMSServiceImplTest.java).

Mock, local-container, and application-context tests do not establish acceptance against a live provider account. Validate the intended provider deployment and credentials separately.

## Test fixture

The `sendEmail_returnsSuccess` test demonstrates one concrete behavior. Excerpt from [EmailServiceImplTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/notifications/fireflyframework-notifications-core/src/test/java/org/fireflyframework/notifications/core/services/email/v1/EmailServiceImplTest.java). The test class supplies its fixtures and imports.

```java
@Test
void sendEmail_returnsSuccess() {
    EmailRequestDTO req = EmailRequestDTO.builder()
            .from("noreply@example.com")
            .to("user@example.com")
            .subject("Hello")
            .text("Hi")
            .build();

    EmailResponseDTO resp = emailService.sendEmail(req).block();
    assertThat(resp).isNotNull();
    assertThat(resp.getStatus()).isEqualTo(EmailStatusEnum.SENT);
    assertThat(resp.getMessageId()).isEqualTo("test-message-id");
}
```

## Continue the application

Use the [Notifications - SendGrid guide](notifications-sendgrid.md) for the next part of this integration. The runnable submission/query service is in [Getting started](../getting-started.md).

## Source and further reading

- [Maven project and dependency declarations](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/notifications/fireflyframework-notifications-core/pom.xml).
- [Production source](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/notifications/fireflyframework-notifications-core/src/main/java).
- [org.springframework.boot.autoconfigure.AutoConfiguration.imports](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/notifications/fireflyframework-notifications-core/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports).
- [Module catalog](index.md) and [getting started](../getting-started.md).
- [Maven platform](../maven.md) and [architecture](../architecture.md).

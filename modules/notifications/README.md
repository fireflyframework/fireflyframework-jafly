![Jafly. Notifications Library](../../docs/site/assets/modules/notifications.svg)

# Notifications Library

Group the notification service contracts and their core implementation.

Use notifications-core for the concrete service APIs, then choose email, SMS, or push provider adapters. This aggregator is a POM, so select a child JAR rather than declaring it as an ordinary runtime JAR.

[Module guide](../../docs/site/modules/notifications.md) · [Jafly README](../../README.md) · [Module catalog](../../docs/site/modules/index.md)

## In the onboarding service

After the service accepts an application, it may send a confirmation to the applicant. Choose a delivery provider and explicitly register the channel service before connecting notification work to the handler or event consumer.

## Choose a component

| Related component | Why it belongs nearby |
| --- | --- |
| [Notifications Core](../../docs/site/modules/notifications-core.md) | Send email, SMS, and push notifications through provider ports, with templates, preferences, and metrics. |

## Installation

[Installation and version availability](../../docs/site/installation.md) explains source builds and published releases.

This artifact has `pom` packaging and groups child projects. Select a concrete child JAR for the application classpath.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-notifications-core</artifactId>
    <version>26.10.18</version>
</dependency>
```

## Quick start

The injection examples use Lombok `@RequiredArgsConstructor`. Add its provided dependency and annotation processor as described in [Constructor injection](../../docs/site/maven.md#constructor-injection).

The Java examples in this section are API fragments for application code. Use the stated imports and supply the application-owned collaborators described alongside them.

1. Add a provider adapter (here: SendGrid for email). The adapter brings `fireflyframework-notifications-core` in transitively.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-notifications-sendgrid</artifactId>
</dependency>
```

2. Select and configure the provider in `application.yml`:

```yaml
firefly:
  notifications:
    email:
      provider: sendgrid        # binds the SendGrid EmailProvider adapter
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
import lombok.RequiredArgsConstructor;
import org.fireflyframework.notifications.core.services.email.v1.EmailService;
import org.fireflyframework.notifications.interfaces.dtos.email.v1.EmailRequestDTO;
import org.fireflyframework.notifications.interfaces.dtos.email.v1.EmailResponseDTO;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class OrderNotificationService {

    private final EmailService emailService;



    public Mono<EmailResponseDTO> sendOrderConfirmation(String customerEmail, String orderId) {
        EmailRequestDTO request = EmailRequestDTO.builder()
                .from("orders@example.com")
                .to(customerEmail)
                .subject("Order Confirmation - " + orderId)
                .html("<h1>Thanks!</h1><p>Your order " + orderId + " is confirmed.</p>")
                .build();
        return emailService.sendEmail(request);
    }
}
```

Templated email. Provide a `NotificationTemplateEngine` bean (the FreeMarker engine renders `.ftl` files from `/notification-templates`) and call `sendTemplateEmail`:

```java
@Bean
NotificationTemplateEngine notificationTemplateEngine() {
    return new FreemarkerNotificationTemplateEngine(); // classpath:/notification-templates
}

// ...
EmailTemplateRequestDTO request = EmailTemplateRequestDTO.builder()
        .from("welcome@example.com")
        .to(user.getEmail())
        .subject("Welcome!")
        .templateId("welcome-email")                 // resolves welcome-email.ftl
        .templateVariables(Map.of("firstName", user.getFirstName()))
        .build();
return emailService.sendTemplateEmail(request);
```

Respect user preferences before dispatching:

```java
return preferenceService.isChannelEnabled(userId, "email")
        .filter(Boolean::booleanValue)
        .flatMap(enabled -> emailService.sendEmail(request));
```

SMS and push use `SMSService.sendSMS(SMSRequestDTO)` and `PushService.sendPush(PushNotificationRequest)`. Select their provider adapter and explicitly register the matching channel service; see the core wiring recipe for the service/provider pairs.

## Configuration

This POM has no runtime configuration namespace. Configure the selected child libraries in the application that uses them.

## Continue the application

Use the [Notifications Core guide](../../docs/site/modules/notifications-core.md) for the next part of this integration.

## Build and validation

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-notifications,:fireflyframework-notifications-core -am verify
./mvnw -pl :fireflyframework-notifications,:fireflyframework-notifications-core -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Configuration details and source references: [module guide](../../docs/site/modules/notifications.md).

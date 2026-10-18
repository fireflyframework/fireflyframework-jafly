![Jafly. Notifications - Twilio](../assets/modules/notifications-twilio.svg)

# Notifications - Twilio

Send SMS notifications through Twilio.

Configure the account credential and sender settings, then invoke the shared SMS service. Verify destination formatting and provider delivery semantics with your Twilio account before operating a messaging flow.

| Maven artifact | Packaging | Source project |
| --- | --- | --- |
| `org.fireflyframework:fireflyframework-notifications-twilio` | `jar` | [modules/notifications-twilio](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/notifications-twilio) |

Part of [External services](index.md#external-services).

## In the onboarding service

Use the Twilio SMS provider for an onboarding message when the product has collected a suitable phone number and chosen that channel. The application decides when a notification should be sent.

## Add the component

[Installation and version availability](../installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-notifications-twilio</artifactId>
    <version>26.10.17-SNAPSHOT</version>
</dependency>
```

## Quick start

The Java examples in this section are API fragments for application code. Use the stated imports and supply the application-owned collaborators described alongside them.

If you use a core channel service instead of the provider SPI directly, [register the matching service implementation explicitly](notifications-core.md#quick-start). Provider auto-configuration does not expand the application's component scan.

1. Add the dependency (see [Installation](#add-the-component)).

2. Select Twilio as the SMS provider and supply credentials in `application.yml`:

```yaml
firefly:
  notifications:
    sms:
      provider: twilio          # activates this adapter
    twilio:
      account-sid: ${TWILIO_ACCOUNT_SID}
      auth-token: ${TWILIO_AUTH_TOKEN}
      phone-number: "+1234567890"   # your Twilio sender number (E.164)
```

3. Inject the `SMSProvider` port and send a message. Your code stays vendor-agnostic:

```java
import org.fireflyframework.notifications.interfaces.dtos.sms.v1.SMSRequestDTO;
import org.fireflyframework.notifications.interfaces.dtos.sms.v1.SMSResponseDTO;
import org.fireflyframework.notifications.interfaces.providers.sms.v1.SMSProvider;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class OtpService {

    private final SMSProvider smsProvider;

    public OtpService(SMSProvider smsProvider) {
        this.smsProvider = smsProvider; // TwilioSMSProvider injected by auto-config
    }

    public Mono<SMSResponseDTO> sendOtp(String phoneNumber, String code) {
        SMSRequestDTO request = SMSRequestDTO.builder()
                .phoneNumber(phoneNumber)
                .message("Your verification code is " + code)
                .build();

        return smsProvider.sendSMS(request)
                .doOnNext(response -> {
                    if ("SENT".equals(response.getStatus())) {
                        // response.getMessageId() == Twilio message SID
                    }
                });
    }
}
```

A successful send returns an `SMSResponseDTO` with status `SENT` and the Twilio message SID in `messageId`. Validation failures (null request, blank number, blank message, or missing sender number) return status `FAILED` with a populated `errorMessage`.

## Configuration and wiring

The `firefly.notifications.twilio` namespace is bound by [TwilioProperties.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/notifications-twilio/src/main/java/org/fireflyframework/notifications/providers/twilio/properties/v1/TwilioProperties.java). Nested configuration is declared in the linked class. Defaults are the field initializers.

| Setting | Java type | Source default |
| --- | --- | --- |
| `firefly.notifications.twilio.account-sid` | `String` | `not initialized` |
| `firefly.notifications.twilio.auth-token` | `String` | `not initialized` |
| `firefly.notifications.twilio.phone-number` | `String` | `not initialized` |

Property-based wiring conditions found in this module:

- `name = "firefly.notifications.sms.provider", havingValue = "twilio"`. [TwilioAutoConfiguration.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/notifications-twilio/src/main/java/org/fireflyframework/notifications/providers/twilio/config/v1/TwilioAutoConfiguration.java).

## Public API and extension points

| Type | Contract and responsibility |
| --- | --- |
| [`TwilioSMSProvider`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/notifications-twilio/src/main/java/org/fireflyframework/notifications/providers/twilio/core/v1/TwilioSMSProvider.java) | Defines `TwilioSMSProvider`, `sendSMS`, `PhoneNumber` operations. |
| [`TwilioProperties`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/notifications-twilio/src/main/java/org/fireflyframework/notifications/providers/twilio/properties/v1/TwilioProperties.java) | Carries `accountSid`, `authToken`, `phoneNumber` data; field constraints and defaults are declared in the linked source. |
| [`TwilioAutoConfiguration`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/notifications-twilio/src/main/java/org/fireflyframework/notifications/providers/twilio/config/v1/TwilioAutoConfiguration.java) | Defines `twilioInit`, `twilioSMSProvider` operations. |

## Related components

| Related component | Why it belongs nearby |
| --- | --- |
| [Notifications Core](notifications-core.md) | Send email, SMS, and push notifications through provider ports, with templates, preferences, and metrics. |

## Build and test

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-notifications-twilio -am verify
./mvnw -pl :fireflyframework-notifications-twilio -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

This subproject has no authored `src/test/java` suite. Its Maven build still validates the configured compilation/generation and any reactor prerequisites. Use the consuming component tests to validate integration behavior.

## API reference excerpt

Contract excerpt from [TwilioSMSProvider.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/notifications-twilio/src/main/java/org/fireflyframework/notifications/providers/twilio/core/v1/TwilioSMSProvider.java). This fragment needs the full type and its imports.

```java
public class TwilioSMSProvider implements SMSProvider {
    private final TwilioProperties twilioProperties;
    public TwilioSMSProvider(TwilioProperties twilioProperties) {
        this.twilioProperties = twilioProperties;
    }
    public Mono<SMSResponseDTO> sendSMS(SMSRequestDTO request) {
        if (request == null) {
            return Mono.just(SMSResponseDTO.error("Request cannot be null"));
        }
        if (!StringUtils.hasText(request.getPhoneNumber())) {
            return Mono.just(SMSResponseDTO.error("Phone number cannot be empty"));
        }
        if (!StringUtils.hasText(request.getMessage())) {
            return Mono.just(SMSResponseDTO.error("Message cannot be empty"));
        }
        if (!StringUtils.hasText(twilioProperties.getPhoneNumber())) {
            return Mono.just(SMSResponseDTO.error("Sender phone number not configured"));
        }
        return Mono.fromCallable(() -> {
            Message message = Message.creator(
                    new PhoneNumber(request.getPhoneNumber().trim()),
                    new PhoneNumber(twilioProperties.getPhoneNumber().trim()),
                    request.getMessage().trim()
            ).create();
```

## Detailed reference

### Configuration

The adapter is configured through two property groups: the shared notifications selector and the Twilio-specific credentials (`@ConfigurationProperties(prefix = "firefly.notifications.twilio")`).

```yaml
firefly:
  notifications:
    sms:
      provider: twilio          # required: must equal "twilio" to enable this adapter
    twilio:
      account-sid:              # Twilio Account SID (starts with "AC...")
      auth-token:               # Twilio Auth Token
      phone-number:             # Twilio sender number in E.164 format, e.g. +14155552671
```

| Property | Default | Description |
| --- | --- | --- |
| `firefly.notifications.sms.provider` | _(none)_ | Adapter selector. Must be `twilio` for this module's auto-configuration to activate. |
| `firefly.notifications.twilio.account-sid` | _(none)_ | Twilio Account SID used to authenticate with the Twilio REST API. |
| `firefly.notifications.twilio.auth-token` | _(none)_ | Twilio Auth Token paired with the Account SID. Keep this secret (env var / vault). |
| `firefly.notifications.twilio.phone-number` | _(none)_ | The "from" number messages are sent from. Must be a Twilio-owned or verified number in E.164 format. If unset, sends fail with `Sender phone number not configured`. |

> Tip: source `account-sid` and `auth-token` from environment variables or a secrets manager rather than committing them to `application.yml`.

### How It Works

`TwilioAutoConfiguration` is registered via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` and is guarded by:

- `@ConditionalOnProperty(name = "firefly.notifications.sms.provider", havingValue = "twilio")`. opt-in selection.
- `@ConditionalOnClass(com.twilio.Twilio.class)`. only when the Twilio SDK is present.
- `@EnableConfigurationProperties(TwilioProperties.class)`. binds the `firefly.notifications.twilio.*` keys.

When active, it initializes the static Twilio client with `Twilio.init(accountSid, authToken)` and registers a `TwilioSMSProvider` as the `SMSProvider` bean (both `@ConditionalOnMissingBean`, so you can override either). `TwilioSMSProvider.sendSMS` validates the request, builds a Twilio `Message` (recipient, sender, body), executes the REST call on `Schedulers.boundedElastic()`, and maps the result to an `SMSResponseDTO`.

## Continue the application

Use the [Notifications Core guide](notifications-core.md) for the next part of this integration. The runnable submission/query service is in [Getting started](../getting-started.md).

## Source and further reading

- [Maven project and dependency declarations](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/notifications-twilio/pom.xml).
- [Production source](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/notifications-twilio/src/main/java).
- [org.springframework.boot.autoconfigure.AutoConfiguration.imports](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/notifications-twilio/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports).
- [Module catalog](index.md) and [getting started](../getting-started.md).
- [Maven platform](../maven.md) and [architecture](../architecture.md).

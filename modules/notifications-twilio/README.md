![Jafly. Notifications - Twilio](../../docs/site/assets/modules/notifications-twilio.svg)

# Notifications - Twilio

Send SMS notifications through Twilio.

Configure the account credential and sender settings, then invoke the shared SMS service. Verify destination formatting and provider delivery semantics with your Twilio account before operating a messaging flow.

[Module guide](../../docs/site/modules/notifications-twilio.md) · [Jafly README](../../README.md) · [Module catalog](../../docs/site/modules/index.md)

## In the onboarding service

Use the Twilio SMS provider for an onboarding message when the product has collected a suitable phone number and chosen that channel. The application decides when a notification should be sent.

## Installation

[Installation and version availability](../../docs/site/installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-notifications-twilio</artifactId>
    <version>26.10.22</version>
</dependency>
```

## Quick start

The Java examples in this section are API fragments for application code. Use the stated imports and supply the application-owned collaborators described alongside them.

If you use a core channel service instead of the provider SPI directly, [register the matching service implementation explicitly](../../docs/site/modules/notifications-core.md#quick-start). Provider auto-configuration does not expand the application's component scan.

1. Add the dependency (see [Installation](#installation)).

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

## Configuration

The `firefly.notifications.twilio` namespace is bound by [TwilioProperties.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/notifications-twilio/src/main/java/org/fireflyframework/notifications/providers/twilio/properties/v1/TwilioProperties.java). Nested configuration is declared in the linked class. Defaults are the field initializers.

| Setting | Java type | Source default |
| --- | --- | --- |
| `firefly.notifications.twilio.account-sid` | `String` | `not initialized` |
| `firefly.notifications.twilio.auth-token` | `String` | `not initialized` |
| `firefly.notifications.twilio.phone-number` | `String` | `not initialized` |

## API overview

| Type | Contract and responsibility |
| --- | --- |
| [`TwilioSMSProvider`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/notifications-twilio/src/main/java/org/fireflyframework/notifications/providers/twilio/core/v1/TwilioSMSProvider.java) | Defines `TwilioSMSProvider`, `sendSMS`, `PhoneNumber` operations. |
| [`TwilioProperties`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/notifications-twilio/src/main/java/org/fireflyframework/notifications/providers/twilio/properties/v1/TwilioProperties.java) | Carries `accountSid`, `authToken`, `phoneNumber` data; field constraints and defaults are declared in the linked source. |
| [`TwilioAutoConfiguration`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/notifications-twilio/src/main/java/org/fireflyframework/notifications/providers/twilio/config/v1/TwilioAutoConfiguration.java) | Defines `twilioInit`, `twilioSMSProvider` operations. |

## Continue the application

Use the [Notifications Core guide](../../docs/site/modules/notifications-core.md) for the next part of this integration.

## Build and validation

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-notifications-twilio -am verify
./mvnw -pl :fireflyframework-notifications-twilio -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Configuration details and source references: [module guide](../../docs/site/modules/notifications-twilio.md).

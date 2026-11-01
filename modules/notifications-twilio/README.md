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
        this.smsProvider = smsProvider; // TwilioSMSProviderImpl injected by auto-config
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

`SENT` means that the provider API accepted the outbound message; it does not prove delivery. The exact nonblank provider SID is returned in `messageId`.

| SDK response status | DTO result |
| --- | --- |
| `queued`, `sending`, `sent`, `delivered`, `accepted`, `scheduled`, `read` | `SENT`, representing API acceptance only |
| `failed`, `undelivered`, `canceled`, deprecated `partially_delivered` | `FAILED` with a fixed safe error; retain the SID |
| Inbound-only `receiving` or `received`, missing or unrecognized status | `FAILED` with a fixed safe error; retain a nonblank SID |
| Missing or blank SID | `FAILED`; no invented message ID |

The partial-delivery classification is a conservative adapter policy. These states follow the [Twilio Message resource reference](https://www.twilio.com/docs/messaging/api/message-resource#message-status-values); normalizing `read` also makes no delivery/read-receipt promise. Recipient, sender, and body trimming remain unchanged. Validation failures (null request, blank number, blank message, or missing sender number) still return `FAILED`.

Each configured application uses an instance `TwilioRestClient`. A custom SDK client bean overrides the default client. The original one-argument provider constructor and factory remain available and create an instance client lazily from their properties; the additive constructor accepts an explicit SDK client. The deprecated `twilioInit` compatibility hook is a no-op. Calling `Twilio.init` no longer configures this adapter. Unselected configuration performs no provider IO; an explicit `SMSProvider` override does not require unused Twilio credentials. The selected default client requires account credentials.

SDK exceptions remain reactive errors with the fixed message `Twilio SMS request failed` and no raw SDK cause. The unchanged shared SMS service converts that safe message into `FAILED`. Provider response error text and initialization credentials are not logged by this adapter. Twilio SDK debug logging remains application-controlled.

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
| [`TwilioSMSProviderImpl`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/notifications-twilio/src/main/java/org/fireflyframework/notifications/providers/twilio/core/v1/TwilioSMSProviderImpl.java) | Defines `TwilioSMSProviderImpl`, `sendSMS`, `PhoneNumber` operations. |
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

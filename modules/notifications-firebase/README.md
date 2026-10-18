![Jafly. Notifications - Firebase](../../docs/site/assets/modules/notifications-firebase.svg)

# Notifications - Firebase

Send push notifications through Firebase Cloud Messaging.

Add this adapter beside notifications-core and configure Firebase credentials/settings. Use the shared push service/request contracts rather than binding application code directly to the Firebase implementation.

[Module guide](../../docs/site/modules/notifications-firebase.md) · [Jafly README](../../README.md) · [Module catalog](../../docs/site/modules/index.md)

## In the onboarding service

A mobile applicant portal can receive push notifications through Firebase. The application supplies the device/topic target and decides which application-status changes should trigger a message.

## Installation

[Installation and version availability](../../docs/site/installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-notifications-firebase</artifactId>
    <version>26.10.17</version>
</dependency>
```

## Quick start

The injection examples use Lombok `@RequiredArgsConstructor`. Add its provided dependency and annotation processor as described in [Constructor injection](../../docs/site/maven.md#constructor-injection).

The Java examples in this section are API fragments for application code. Use the stated imports and supply the application-owned collaborators described alongside them.

If you use a core channel service instead of the provider SPI directly, [register the matching service implementation explicitly](../../docs/site/modules/notifications-core.md#quick-start). Provider auto-configuration does not expand the application's component scan.

1. Add the dependencies (core + this adapter):

```xml
<dependencies>
    <dependency>
        <groupId>org.fireflyframework</groupId>
        <artifactId>fireflyframework-notifications-core</artifactId>
    </dependency>
    <dependency>
        <groupId>org.fireflyframework</groupId>
        <artifactId>fireflyframework-notifications-firebase</artifactId>
    </dependency>
</dependencies>
```

2. Select and configure the Firebase adapter in `application.yml`:

```yaml
firefly:
  notifications:
    push:
      provider: firebase          # selects this adapter
    firebase:
      credentials-path: /etc/secrets/firebase-service-account.json
      project-id: my-firebase-project
```

3. Inject and use the `PushProvider`. Your code depends on the core SPI, not on Firebase directly:

```java
import lombok.RequiredArgsConstructor;
import org.fireflyframework.notifications.interfaces.dtos.push.v1.PushNotificationRequest;
import org.fireflyframework.notifications.interfaces.dtos.push.v1.PushNotificationResponse;
import org.fireflyframework.notifications.interfaces.providers.push.v1.PushProvider;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class AlertService {

    private final PushProvider pushProvider; // FcmPushProvider injected by auto-config



    public Mono<PushNotificationResponse> notifyDevice(String deviceToken) {
        PushNotificationRequest request = PushNotificationRequest.builder()
                .token(deviceToken)
                .title("Payment received")
                .body("Your transfer of €250.00 has been completed.")
                .data(Map.of("type", "PAYMENT", "txId", "abc-123"))
                .build();

        return pushProvider.sendPush(request);
    }
}
```

On success the returned `PushNotificationResponse` carries the FCM `messageId` and `success = true`; on failure it returns `success = false` with an `errorMessage`.

## Configuration

The `firefly.notifications.firebase` namespace is bound by [FcmProperties.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/notifications-firebase/src/main/java/org/fireflyframework/notifications/providers/firebase/properties/v1/FcmProperties.java). Nested configuration is declared in the linked class. Defaults are the field initializers.

| Setting | Java type | Source default |
| --- | --- | --- |
| `firefly.notifications.firebase.credentials-path` | `String` | `not initialized` |
| `firefly.notifications.firebase.project-id` | `String` | `not initialized` |

## API overview

| Type | Contract and responsibility |
| --- | --- |
| [`FcmProperties`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/notifications-firebase/src/main/java/org/fireflyframework/notifications/providers/firebase/properties/v1/FcmProperties.java) | Carries `credentialsPath`, `projectId` data; field constraints and defaults are declared in the linked source. |
| [`FcmAutoConfiguration`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/notifications-firebase/src/main/java/org/fireflyframework/notifications/providers/firebase/config/v1/FcmAutoConfiguration.java) | Defines `firebaseApp`, `firebaseMessaging`, `fcmPushProvider` operations. |
| [`FcmPushProvider`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/notifications-firebase/src/main/java/org/fireflyframework/notifications/providers/firebase/core/v1/FcmPushProvider.java) | Defines `FcmPushProvider`, `sendPush` operations. |

## Continue the application

Use the [Notifications Core guide](../../docs/site/modules/notifications-core.md) for the next part of this integration.

## Build and validation

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-notifications-firebase -am verify
./mvnw -pl :fireflyframework-notifications-firebase -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Configuration details and source references: [module guide](../../docs/site/modules/notifications-firebase.md).

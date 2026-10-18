package org.fireflyframework.notifications.core.services.push.v1;

import org.fireflyframework.notifications.interfaces.dtos.push.v1.PushNotificationRequest;
import org.fireflyframework.notifications.interfaces.dtos.push.v1.PushNotificationResponse;
import org.fireflyframework.notifications.interfaces.providers.push.v1.PushProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {PushServiceImpl.class, PushServiceImplTest.TestBeans.class})
class PushServiceImplTest {

    @Configuration
    static class TestBeans {
        @Bean
        PushProvider pushProvider() {
            return request -> Mono.just(PushNotificationResponse.builder()
                    .messageId("push-1")
                    .success(true)
                    .build());
        }
    }

    @Autowired
    private PushService pushService;

    @Test
    void sendPush_returnsSuccess() {
        PushNotificationRequest req = PushNotificationRequest.builder()
                .token("fake-token")
                .title("Hello")
                .body("World")
                .build();

        PushNotificationResponse resp = pushService.sendPush(req).block();
        assertThat(resp).isNotNull();
        assertThat(resp.isSuccess()).isTrue();
        assertThat(resp.getMessageId()).isEqualTo("push-1");
    }
}

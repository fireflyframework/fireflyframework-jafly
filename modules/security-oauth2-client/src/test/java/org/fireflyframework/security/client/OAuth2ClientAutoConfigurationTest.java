/*
 * Copyright 2024-2026 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.fireflyframework.security.client;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.github.tomakehurst.wiremock.WireMockServer;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.security.oauth2.client.autoconfigure.reactive.ReactiveOAuth2ClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.security.oauth2.client.AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServerOAuth2AuthorizedClientExchangeFilterFunction;

class OAuth2ClientAutoConfigurationTest {
    private ReactiveWebApplicationContextRunner runner() {
        return new ReactiveWebApplicationContextRunner().withConfiguration(AutoConfigurations.of(
                ReactiveOAuth2ClientAutoConfiguration.class, OAuth2ClientAutoConfiguration.class));
    }

    private String[] registration(int port) {
        return new String[] {
                "spring.security.oauth2.client.registration.worker.provider=fixture",
                "spring.security.oauth2.client.registration.worker.client-id=synthetic-worker",
                "spring.security.oauth2.client.registration.worker.client-secret=synthetic-private-secret",
                "spring.security.oauth2.client.registration.worker.authorization-grant-type=client_credentials",
                "spring.security.oauth2.client.registration.worker.scope=funds:read",
                "spring.security.oauth2.client.provider.fixture.token-uri=http://127.0.0.1:" + port + "/oauth2/token"};
    }

    @Test
    void nativeBootRegistrationCreatesBackgroundManagerAndActuallyAcquiresToken() {
        var server = new WireMockServer(options().bindAddress("127.0.0.1").dynamicPort());
        server.start();
        try {
            server.stubFor(post(urlEqualTo("/oauth2/token")).willReturn(okJson(
                    "{\"access_token\":\"synthetic-client-token\",\"token_type\":\"Bearer\",\"expires_in\":30}")));
            runner().withPropertyValues(registration(server.port())).run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).hasSingleBean(ReactiveClientRegistrationRepository.class);
                assertThat(context).hasSingleBean(ReactiveOAuth2AuthorizedClientService.class);
                assertThat(context).hasSingleBean(ReactiveOAuth2AuthorizedClientManager.class);
                assertThat(context).hasSingleBean(ServerOAuth2AuthorizedClientExchangeFilterFunction.class);
                var manager = context.getBean(ReactiveOAuth2AuthorizedClientManager.class);
                assertThat(manager).isInstanceOf(AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager.class);
                var request = OAuth2AuthorizeRequest.withClientRegistrationId("worker")
                        .principal("synthetic-tenant-worker").build();
                var authorized = manager.authorize(request).block(Duration.ofSeconds(5));
                assertThat(authorized).isNotNull();
                assertThat(authorized.getAccessToken().getTokenValue()).isEqualTo("synthetic-client-token");
                assertThat(authorized.getPrincipalName()).isEqualTo("synthetic-tenant-worker");
            });
            server.verify(postRequestedFor(urlEqualTo("/oauth2/token"))
                    .withRequestBody(containing("grant_type=client_credentials"))
                    .withRequestBody(containing("scope=funds%3Aread")));
        } finally {
            server.stop();
        }
    }

    @Test
    void noRegistrationDoesNotInventClientIdentityOrManager() {
        runner().run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(ReactiveClientRegistrationRepository.class);
            assertThat(context).doesNotHaveBean(ReactiveOAuth2AuthorizedClientManager.class);
            assertThat(context).doesNotHaveBean(ServerOAuth2AuthorizedClientExchangeFilterFunction.class);
        });
    }

    @Test
    void projectManagerAndClientServiceRemainSelectedWithoutPrimary() {
        var manager = mock(ReactiveOAuth2AuthorizedClientManager.class);
        var service = mock(ReactiveOAuth2AuthorizedClientService.class);
        runner().withPropertyValues(registration(1))
                .withBean(ReactiveOAuth2AuthorizedClientManager.class, () -> manager)
                .withBean(ReactiveOAuth2AuthorizedClientService.class, () -> service)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ReactiveOAuth2AuthorizedClientManager.class);
                    assertThat(context).hasSingleBean(ReactiveOAuth2AuthorizedClientService.class);
                    assertThat(context.getBean(ReactiveOAuth2AuthorizedClientManager.class)).isSameAs(manager);
                    assertThat(context.getBean(ReactiveOAuth2AuthorizedClientService.class)).isSameAs(service);
                    assertThat(context).hasSingleBean(ServerOAuth2AuthorizedClientExchangeFilterFunction.class);
                });
    }
}

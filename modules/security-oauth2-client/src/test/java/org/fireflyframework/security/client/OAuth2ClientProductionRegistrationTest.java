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

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.fireflyframework.security.api.domain.Decision;
import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.fireflyframework.security.rs.JwtToFireflyPrincipalConverterImpl;
import org.fireflyframework.security.rs.ResourceServerAutoConfiguration;
import org.fireflyframework.security.spi.PolicyDecisionPort;
import org.fireflyframework.security.webflux.authentication.FireflyAuthenticationToken;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.client.AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServerOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.server.handler.DefaultWebFilterChain;
import reactor.core.publisher.Mono;

class OAuth2ClientProductionRegistrationTest {
    private static final Duration WAIT = Duration.ofSeconds(5);
    private static final List<String> BOOT_CLIENT_CONFIGURATIONS = List.of(
            "org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration",
            "org.springframework.boot.security.autoconfigure.web.reactive.ReactiveWebSecurityAutoConfiguration",
            "org.springframework.boot.security.oauth2.client.autoconfigure.reactive.ReactiveOAuth2ClientAutoConfiguration",
            "org.springframework.boot.security.oauth2.client.autoconfigure.reactive.ReactiveOAuth2ClientWebSecurityAutoConfiguration");

    private ReactiveWebApplicationContextRunner runner(boolean resourceServer) {
        var configurations = new ArrayList<Class<?>>();
        configurations.add(OAuth2ClientAutoConfiguration.class);
        var loader = getClass().getClassLoader();
        for (var name : ImportCandidates.load(AutoConfiguration.class, loader)) {
            if (BOOT_CLIENT_CONFIGURATIONS.contains(name)) {
                try {
                    configurations.add(Class.forName(name, false, loader));
                } catch (ClassNotFoundException missing) {
                    throw new IllegalStateException(missing);
                }
            }
        }
        if (resourceServer) configurations.add(ResourceServerAutoConfiguration.class);
        return new ReactiveWebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(configurations.toArray(Class<?>[]::new)));
    }

    private String[] registration() {
        return new String[] {
                "spring.security.oauth2.client.registration.worker.provider=fixture",
                "spring.security.oauth2.client.registration.worker.client-id=source-unit-worker",
                "spring.security.oauth2.client.registration.worker.client-secret=source-unit-fixture-value",
                "spring.security.oauth2.client.registration.worker.authorization-grant-type=client_credentials",
                "spring.security.oauth2.client.provider.fixture.token-uri=https://idp.example.invalid/oauth2/token"};
    }

    @Test
    void standardRegistrationFromProductionDependenciesCreatesBackgroundClientBeans() {
        runner(false).withPropertyValues(registration()).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(ReactiveClientRegistrationRepository.class);
            assertThat(context).hasSingleBean(ReactiveOAuth2AuthorizedClientService.class);
            assertThat(context).hasSingleBean(ReactiveOAuth2AuthorizedClientManager.class);
            assertThat(context).hasSingleBean(ServerOAuth2AuthorizedClientExchangeFilterFunction.class);
            var registration = context.getBean(ReactiveClientRegistrationRepository.class)
                    .findByRegistrationId("worker").block(WAIT);
            assertThat(registration).isNotNull();
            assertThat(registration.getAuthorizationGrantType()).isEqualTo(AuthorizationGrantType.CLIENT_CREDENTIALS);
            assertThat(registration.getClientId()).isEqualTo("source-unit-worker");
            assertThat(context.getBean(ReactiveOAuth2AuthorizedClientManager.class))
                    .isInstanceOf(AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager.class);
        });
    }

    @Test
    void noRegistrationCreatesNoClientIdentityOrManager() {
        runner(false).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(ReactiveClientRegistrationRepository.class);
            assertThat(context).doesNotHaveBean(ReactiveOAuth2AuthorizedClientManager.class);
        });
    }

    @Test
    void outboundClientLeavesResourceServerJwtChainAndAnonymousDefaultDenialInPlace() {
        runner(true).withPropertyValues(registration()).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(SecurityWebFilterChain.class);
            assertThat(context).hasBean("fireflySecurityWebFilterChain");
            assertThat(context.getBean(ReactiveJwtDecoder.class)).isInstanceOf(NimbusReactiveJwtDecoder.class);
            assertThat(context).hasSingleBean(JwtToFireflyPrincipalConverterImpl.class);
            var chain = context.getBean(SecurityWebFilterChain.class);
            var filters = chain.getWebFilters().collectList().block(WAIT);
            assertThat(filters).noneMatch(filter -> filter.getClass().getName().contains("OAuth2Login")
                    || filter.getClass().getName().contains("OAuth2AuthorizationRequestRedirect"));
            var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/protected"));
            new DefaultWebFilterChain(e -> e.getResponse().setComplete(), filters).filter(exchange).block(WAIT);
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        });
    }

    @Test
    void outboundClientPreservesCurrentUrlPolicySelectionInTheResourceServerChain() {
        var allowed = new AtomicBoolean(false);
        var calls = new AtomicInteger();
        PolicyDecisionPort policy = (principal, action, resource, data) -> {
            assertThat(principal.subject()).isEqualTo("source-unit-principal");
            assertThat(action).isEqualTo("GET");
            assertThat(resource).isEqualTo("/protected");
            calls.incrementAndGet();
            return Mono.just(allowed.get() ? Decision.permit() : Decision.deny("source-unit-denial"));
        };
        runner(true).withPropertyValues(registration())
                .withPropertyValues("firefly.security.resource-server.strict-policy=true")
                .withBean(PolicyDecisionPort.class, () -> policy).run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(SecurityWebFilterChain.class);
                    var filters = context.getBean(SecurityWebFilterChain.class).getWebFilters().collectList().block(WAIT);
                    // An existing source-unit security context tests policy composition, not JWT authentication.
                    var authentication = new FireflyAuthenticationToken(SecurityPrincipal.builder()
                            .subject("source-unit-principal").build());
                    for (boolean permit : new boolean[] {false, true, false}) {
                        allowed.set(permit);
                        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/protected"));
                        new DefaultWebFilterChain(e -> {
                            e.getResponse().setStatusCode(HttpStatus.NO_CONTENT);
                            return e.getResponse().setComplete();
                        }, filters).filter(exchange)
                                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication))
                                .block(WAIT);
                        assertThat(exchange.getResponse().getStatusCode())
                                .isEqualTo(permit ? HttpStatus.NO_CONTENT : HttpStatus.FORBIDDEN);
                    }
                    assertThat(calls.get()).isEqualTo(3);
                });
    }
}

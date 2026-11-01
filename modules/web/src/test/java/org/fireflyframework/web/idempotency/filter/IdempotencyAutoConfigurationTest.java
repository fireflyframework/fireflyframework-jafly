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


package org.fireflyframework.web.idempotency.filter;

import org.fireflyframework.cache.factory.CacheManagerFactory;
import org.fireflyframework.cache.properties.CacheProperties;
import org.fireflyframework.web.idempotency.config.IdempotencyAutoConfiguration;
import org.fireflyframework.web.idempotency.config.IdempotencyProperties;
import org.fireflyframework.web.idempotency.scope.IdempotencyScopeResolver;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class IdempotencyAutoConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(IdempotencyAutoConfiguration.class))
            .withBean(CacheManagerFactory.class, () -> {
                var properties = new CacheProperties();
                properties.setDefaultCacheType(org.fireflyframework.cache.core.CacheType.CAFFEINE);
                return new CacheManagerFactory(properties, new com.fasterxml.jackson.databind.ObjectMapper(), null, null, null);
            });

    @Test void applicationNamespaceAndCustomTrustedResolverAreUsedByAutoConfiguredFilter() {
        runner.withPropertyValues("spring.application.name=synthetic-auto-service")
                .withBean(IdempotencyScopeResolver.class, () -> exchange -> Mono.just("synthetic-trusted-caller-and-tenant"))
                .run(context -> {
                    assertThat(context).hasSingleBean(IdempotencyWebFilterImpl.class);
                    assertThat(context.getBean(IdempotencyProperties.class).getNamespace()).isEqualTo("synthetic-auto-service");
                    var filter = context.getBean(IdempotencyWebFilterImpl.class);
                    AtomicInteger effects = new AtomicInteger();
                    org.springframework.web.server.WebFilterChain handler = exchange -> {
                        effects.incrementAndGet(); exchange.getResponse().setStatusCode(HttpStatus.NO_CONTENT);
                        return exchange.getResponse().setComplete();
                    };
                    filter.filter(request(), handler).block(); filter.filter(request(), handler).block();
                    assertThat(effects.get()).isEqualTo(1);
                });
    }

    @Test void explicitEnvironmentNamespaceOverridesApplicationName() {
        runner.withPropertyValues("spring.application.name=service", "firefly.web.idempotency.namespace=service:test")
                .run(context -> assertThat(context.getBean(IdempotencyProperties.class).getNamespace()).isEqualTo("service:test"));
    }

    private MockServerWebExchange request() {
        return MockServerWebExchange.from(MockServerHttpRequest.post("/api/test").header("X-Idempotency-Key", "synthetic-key").body("{}"));
    }
}

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

package org.fireflyframework.common.backoffice.controller;

import org.fireflyframework.common.backoffice.context.BackofficeContext;
import org.fireflyframework.common.backoffice.resolver.BackofficeContextResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AbstractBackofficeResourceControllerTest {

    @Mock
    BackofficeContextResolver contextResolver;

    private TestController controller;

    @BeforeEach
    void setUp() {
        controller = new TestController(contextResolver);
    }

    private ServerWebExchange exchange() {
        return MockServerWebExchange.from(MockServerHttpRequest.get("/backoffice/resource"));
    }

    private BackofficeContext context(Set<String> roles, String impersonatedSubject) {
        return BackofficeContext.builder()
                .backofficeUserId(UUID.randomUUID())
                .backofficeRoles(roles)
                .impersonatedSubject(impersonatedSubject)
                .build();
    }

    @Test
    void resolvesBackofficeContext() {
        when(contextResolver.resolveContext(any())).thenReturn(Mono.just(context(Set.of("admin"), null)));
        StepVerifier.create(controller.resolveContext(exchange()))
                .expectNextMatches(ctx -> ctx.hasBackofficeRole("admin") && !ctx.isImpersonating())
                .verifyComplete();
    }

    @Test
    void detectsImpersonation() {
        when(contextResolver.resolveContext(any())).thenReturn(Mono.just(context(Set.of("support"), "user-123")));
        StepVerifier.create(controller.resolveContext(exchange()))
                .expectNextMatches(BackofficeContext::isImpersonating)
                .verifyComplete();
    }

    @Test
    void propagatesResolverError() {
        when(contextResolver.resolveContext(any())).thenReturn(Mono.error(new IllegalStateException("no principal")));
        StepVerifier.create(controller.resolveContext(exchange()))
                .expectError(IllegalStateException.class).verify();
    }

    @Test
    void requireBackofficeRolePassesWhenHeld() {
        when(contextResolver.resolveContext(any())).thenReturn(Mono.just(context(Set.of("admin"), null)));
        StepVerifier.create(controller.requireBackofficeRole(exchange(), "admin")).verifyComplete();
    }

    @Test
    void requireBackofficeRoleFailsWhenMissing() {
        when(contextResolver.resolveContext(any())).thenReturn(Mono.just(context(Set.of("support"), null)));
        StepVerifier.create(controller.requireBackofficeRole(exchange(), "admin"))
                .expectError(SecurityException.class).verify();
    }

    @Test
    void logOperationDoesNotThrow() {
        assertThatCode(() -> controller.logOperation("approve-refund")).doesNotThrowAnyException();
    }

    /** Concrete subclass exposing the protected base methods for testing (same package). */
    static class TestController extends AbstractBackofficeResourceController {
        TestController(BackofficeContextResolver resolver) {
            super(resolver);
        }
    }
}

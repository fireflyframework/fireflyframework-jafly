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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.fireflyframework.common.backoffice.context.BackofficeContext;
import org.fireflyframework.common.backoffice.resolver.BackofficeContextResolver;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Thin, product-agnostic base for backoffice resource controllers. It resolves the
 * {@link BackofficeContext} from the validated security context (via {@link BackofficeContextResolver})
 * and offers generic operator-role/permission guards. It carries <strong>no</strong> contract/product
 * scoping and reads no trusted {@code X-Party-Id}-style identity header.
 */
@Slf4j
@RequiredArgsConstructor
public abstract class AbstractBackofficeResourceController {

    protected final BackofficeContextResolver contextResolver;

    /** Resolve the backoffice context (operator, optional impersonated subject, roles, tenant). */
    protected Mono<BackofficeContext> resolveContext(ServerWebExchange exchange) {
        return contextResolver.resolveContext(exchange)
                .doOnNext(ctx -> log.debug("Resolved backoffice context: operator={}, impersonating={}, tenant={}",
                        ctx.getBackofficeUserId(), ctx.getImpersonatedSubject(), ctx.getTenantId()));
    }

    /** Log a backoffice operation for the audit trail. */
    protected void logOperation(String operation) {
        log.info("Backoffice operation: {}", operation);
    }

    /** Fail-closed guard: require the operator to hold a backoffice role. */
    protected Mono<Void> requireBackofficeRole(ServerWebExchange exchange, String role) {
        return resolveContext(exchange).flatMap(ctx -> ctx.hasBackofficeRole(role)
                ? Mono.<Void>empty()
                : Mono.<Void>error(new SecurityException("Backoffice role required: " + role)));
    }

    /** Fail-closed guard: require the operator to hold a backoffice permission. */
    protected Mono<Void> requireBackofficePermission(ServerWebExchange exchange, String permission) {
        return resolveContext(exchange).flatMap(ctx -> ctx.hasBackofficePermission(permission)
                ? Mono.<Void>empty()
                : Mono.<Void>error(new SecurityException("Backoffice permission required: " + permission)));
    }
}

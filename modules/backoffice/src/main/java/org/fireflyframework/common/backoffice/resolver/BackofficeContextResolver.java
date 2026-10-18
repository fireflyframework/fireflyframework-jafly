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

package org.fireflyframework.common.backoffice.resolver;

import org.fireflyframework.common.backoffice.context.BackofficeContext;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Interface for resolving the {@link BackofficeContext} from incoming requests.
 *
 * <p>Implementations extract and enrich product-agnostic context information from the validated
 * security principal (via the {@code fireflyframework-security} platform):</p>
 * <ul>
 *   <li><strong>backofficeUserId</strong> - the authenticated backoffice operator (from the principal subject)</li>
 *   <li><strong>backofficeRoles / backofficePermissions</strong> - from the principal's authorities/scopes</li>
 *   <li><strong>tenantId</strong> - from the principal's tenant identifier (when a UUID)</li>
 *   <li><strong>impersonatedSubject</strong> - optional generic subject from the {@code X-Impersonate-Subject} header</li>
 * </ul>
 *
 * <p>This is the main entry point for backoffice context resolution. It carries no product domain
 * (no party, contract, or product) and no Security Center / SessionManager dependency.</p>
 *
 * @author Firefly Development Team
 * @since 1.0.0
 */
public interface BackofficeContextResolver {

    /**
     * Resolves the complete backoffice context from the request.
     *
     * @param exchange the server web exchange
     * @return Mono of resolved BackofficeContext
     */
    Mono<BackofficeContext> resolveContext(ServerWebExchange exchange);

    /**
     * Resolves the backoffice operator ID from the request.
     *
     * <p>The operator identity is derived from the validated security principal's subject. When the
     * subject is a UUID it is returned; otherwise the result is empty and the raw subject should be
     * retained as the {@code "backofficeUserSubject"} attribute on the context.</p>
     *
     * @param exchange the server web exchange
     * @return Mono of backoffice operator UUID (may be empty)
     */
    Mono<UUID> resolveBackofficeUserId(ServerWebExchange exchange);

    /**
     * Resolves the generic impersonated subject from the request (optional).
     *
     * <p>Read from the {@code X-Impersonate-Subject} header when present; empty otherwise.</p>
     *
     * @param exchange the server web exchange
     * @return Mono of the impersonated subject (may be empty)
     */
    Mono<String> resolveImpersonatedSubject(ServerWebExchange exchange);

    /**
     * Resolves the tenant ID from the request.
     *
     * <p>Typically derived from the validated security principal's tenant identifier.</p>
     *
     * @param exchange the server web exchange
     * @return Mono of tenant UUID (may be empty)
     */
    Mono<UUID> resolveTenantId(ServerWebExchange exchange);

    /**
     * Resolves the impersonation reason from the request (for audit trail).
     *
     * @param exchange the server web exchange
     * @return Mono of impersonation reason (may be empty)
     */
    Mono<String> resolveImpersonationReason(ServerWebExchange exchange);

    /**
     * Checks if this resolver supports the given request.
     * Allows for multiple resolver implementations with different strategies.
     *
     * @param exchange the server web exchange
     * @return true if this resolver can handle the request
     */
    default boolean supports(ServerWebExchange exchange) {
        return true;
    }

    /**
     * Priority of this resolver (higher values take precedence).
     * Used when multiple resolvers support the same request.
     *
     * @return priority value
     */
    default int getPriority() {
        return 0;
    }
}

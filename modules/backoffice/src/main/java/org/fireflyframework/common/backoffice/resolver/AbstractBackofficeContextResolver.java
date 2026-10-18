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
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Set;
import java.util.UUID;

/**
 * Abstract base implementation of {@link BackofficeContextResolver}.
 *
 * <p>Provides the assembly template for a product-agnostic {@link BackofficeContext}: the backoffice
 * operator identity, roles and permissions default from the validated security principal (resolved by
 * subclasses), an optional generic impersonated subject is read from the request, and the tenant is
 * derived from the principal.</p>
 *
 * <p>Subclasses implement the principal-backed resolution methods. This base wires them together,
 * captures the operator IP for audit, and exposes enrichment hooks for impersonated-subject
 * roles/permissions (informational only).</p>
 *
 * @author Firefly Development Team
 * @since 1.0.0
 */
@Slf4j
public abstract class AbstractBackofficeContextResolver implements BackofficeContextResolver {

    @Override
    public Mono<BackofficeContext> resolveContext(ServerWebExchange exchange) {
        log.debug("Resolving backoffice context from validated security principal");

        return Mono.zip(
                        resolveBackofficeUserId(exchange).map(UUID::toString).defaultIfEmpty(""),
                        resolveImpersonatedSubject(exchange).defaultIfEmpty(""),
                        resolveTenantId(exchange).map(UUID::toString).defaultIfEmpty(""),
                        resolveImpersonationReason(exchange).defaultIfEmpty("")
                )
                .flatMap(tuple -> {
                    String backofficeUserIdRaw = tuple.getT1();
                    String impersonatedSubjectRaw = tuple.getT2();
                    String tenantIdRaw = tuple.getT3();
                    String impersonationReasonRaw = tuple.getT4();

                    UUID backofficeUserId = backofficeUserIdRaw.isEmpty() ? null : UUID.fromString(backofficeUserIdRaw);
                    String impersonatedSubject = impersonatedSubjectRaw.isEmpty() ? null : impersonatedSubjectRaw;
                    UUID tenantId = tenantIdRaw.isEmpty() ? null : UUID.fromString(tenantIdRaw);
                    String impersonationReason = impersonationReasonRaw.isEmpty() ? null : impersonationReasonRaw;

                    BackofficeContext basicContext = BackofficeContext.builder()
                            .backofficeUserId(backofficeUserId)
                            .impersonatedSubject(impersonatedSubject)
                            .tenantId(tenantId)
                            .impersonationReason(impersonationReason)
                            .backofficeUserIpAddress(extractIpAddress(exchange))
                            .build();

                    return enrichContext(basicContext, exchange);
                })
                .doOnSuccess(context -> log.debug(
                        "Resolved backoffice context: operator={}, impersonatedSubject={}, tenant={}",
                        context.getBackofficeUserId(), context.getImpersonatedSubject(), context.getTenantId()))
                .doOnError(error -> log.error("Failed to resolve backoffice context", error));
    }

    /**
     * Enriches the basic context with backoffice roles/permissions (from the principal) and the
     * impersonated subject's informational roles/permissions.
     *
     * @param basicContext the basic context with identities resolved
     * @param exchange     the server web exchange
     * @return Mono of enriched BackofficeContext
     */
    protected Mono<BackofficeContext> enrichContext(BackofficeContext basicContext,
                                                    ServerWebExchange exchange) {
        return Mono.zip(
                        resolveBackofficeRoles(basicContext, exchange),
                        resolveBackofficePermissions(basicContext, exchange),
                        resolveImpersonatedSubjectRoles(basicContext, exchange),
                        resolveImpersonatedSubjectPermissions(basicContext, exchange)
                )
                .map(tuple -> basicContext.toBuilder()
                        .backofficeRoles(tuple.getT1())
                        .backofficePermissions(tuple.getT2())
                        .impersonatedSubjectRoles(tuple.getT3())
                        .impersonatedSubjectPermissions(tuple.getT4())
                        .build())
                .defaultIfEmpty(basicContext);
    }

    /**
     * Resolves roles for the backoffice operator. Defaults to the principal's authorities in the
     * concrete resolver; the base returns an empty set.
     *
     * @param context  the backoffice context
     * @param exchange the server web exchange
     * @return Mono of role set
     */
    protected Mono<Set<String>> resolveBackofficeRoles(BackofficeContext context, ServerWebExchange exchange) {
        log.debug("Resolving backoffice roles for operator: {}", context.getBackofficeUserId());
        return Mono.just(Set.of());
    }

    /**
     * Resolves permissions for the backoffice operator. Defaults to the principal's scopes in the
     * concrete resolver; the base returns an empty set.
     *
     * @param context  the backoffice context
     * @param exchange the server web exchange
     * @return Mono of permission set
     */
    protected Mono<Set<String>> resolveBackofficePermissions(BackofficeContext context, ServerWebExchange exchange) {
        log.debug("Resolving backoffice permissions for operator: {}", context.getBackofficeUserId());
        return Mono.just(Set.of());
    }

    /**
     * Resolves informational roles for the impersonated subject (if any).
     *
     * @param context  the backoffice context
     * @param exchange the server web exchange
     * @return Mono of role set
     */
    protected Mono<Set<String>> resolveImpersonatedSubjectRoles(BackofficeContext context, ServerWebExchange exchange) {
        log.debug("Resolving impersonated subject roles for: {}", context.getImpersonatedSubject());
        return Mono.just(Set.of());
    }

    /**
     * Resolves informational permissions for the impersonated subject (if any).
     *
     * @param context  the backoffice context
     * @param exchange the server web exchange
     * @return Mono of permission set
     */
    protected Mono<Set<String>> resolveImpersonatedSubjectPermissions(BackofficeContext context, ServerWebExchange exchange) {
        log.debug("Resolving impersonated subject permissions for: {}", context.getImpersonatedSubject());
        return Mono.just(Set.of());
    }

    /**
     * Extracts a string value from a request attribute or header.
     *
     * @param exchange      the server web exchange
     * @param attributeName the attribute name
     * @param headerName    the header name
     * @return Mono of String (may be empty)
     */
    protected Mono<String> extractString(ServerWebExchange exchange, String attributeName, String headerName) {
        String fromAttribute = exchange.getAttribute(attributeName);
        if (fromAttribute != null && !fromAttribute.isEmpty()) {
            return Mono.just(fromAttribute);
        }

        String headerValue = exchange.getRequest().getHeaders().getFirst(headerName);
        if (headerValue != null && !headerValue.isEmpty()) {
            return Mono.just(headerValue);
        }

        return Mono.empty();
    }

    /**
     * Extracts the IP address of the backoffice operator from the request.
     *
     * @param exchange the server web exchange
     * @return IP address or "unknown"
     */
    protected String extractIpAddress(ServerWebExchange exchange) {
        String xForwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = exchange.getRequest().getHeaders().getFirst("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }

        if (exchange.getRequest().getRemoteAddress() != null
                && exchange.getRequest().getRemoteAddress().getAddress() != null) {
            return exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
        }

        return "unknown";
    }

    @Override
    public Mono<String> resolveImpersonatedSubject(ServerWebExchange exchange) {
        log.debug("Resolving impersonated subject from X-Impersonate-Subject header");
        return extractString(exchange, "impersonatedSubject", "X-Impersonate-Subject");
    }

    @Override
    public Mono<String> resolveImpersonationReason(ServerWebExchange exchange) {
        log.debug("Resolving impersonation reason from request");
        return extractString(exchange, "impersonationReason", "X-Impersonation-Reason");
    }
}

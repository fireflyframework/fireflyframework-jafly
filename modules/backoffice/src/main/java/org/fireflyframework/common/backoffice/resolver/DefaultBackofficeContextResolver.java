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
import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.fireflyframework.security.spi.SecurityContextPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Default, product-agnostic implementation of {@link BackofficeContextResolver}.
 *
 * <p><strong>Provided by the library - applications don't need to implement anything.</strong></p>
 *
 * <p>This resolver assembles the {@link BackofficeContext} from the validated security principal
 * exposed by the {@code fireflyframework-security} platform ({@link SecurityContextPort}):</p>
 * <ul>
 *   <li><strong>backofficeUserId</strong> - from {@link SecurityPrincipal#subject()} when it is a UUID;
 *       otherwise {@code null}, with the raw subject kept under the {@code "backofficeUserSubject"} attribute</li>
 *   <li><strong>backofficeRoles</strong> - from {@link SecurityPrincipal#authorities()}</li>
 *   <li><strong>backofficePermissions</strong> - from {@link SecurityPrincipal#scopes()}</li>
 *   <li><strong>tenantId</strong> - parsed from {@link SecurityPrincipal#tenantId()} when it is a UUID</li>
 *   <li><strong>impersonatedSubject</strong> - read from the optional {@code X-Impersonate-Subject} header</li>
 * </ul>
 *
 * <p>There is no Security Center, no SessionManager, and no product domain (party/contract/product).</p>
 *
 * @author Firefly Development Team
 * @since 1.0.0
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class DefaultBackofficeContextResolver extends AbstractBackofficeContextResolver {

    /**
     * Optional attribute key under which the raw (non-UUID) principal subject is preserved.
     */
    public static final String ATTR_BACKOFFICE_USER_SUBJECT = "backofficeUserSubject";

    private final SecurityContextPort securityContextPort;

    @Override
    public Mono<BackofficeContext> resolveContext(ServerWebExchange exchange) {
        log.debug("Resolving backoffice context from validated security principal");

        return securityContextPort.currentPrincipal()
                .map(principal -> buildContext(principal, exchange))
                .doOnSuccess(context -> {
                    if (context != null) {
                        log.debug("Resolved backoffice context: operator={}, impersonatedSubject={}, tenant={}",
                                context.getBackofficeUserId(), context.getImpersonatedSubject(), context.getTenantId());
                    }
                })
                .doOnError(error -> log.error("Failed to resolve backoffice context", error));
    }

    private BackofficeContext buildContext(SecurityPrincipal principal, ServerWebExchange exchange) {
        String subject = principal.subject();
        UUID backofficeUserId = parseUuid(subject);

        Map<String, Object> attributes = new HashMap<>();
        if (backofficeUserId == null && subject != null) {
            // Subject is not a UUID — keep it so callers can still identify the operator.
            attributes.put(ATTR_BACKOFFICE_USER_SUBJECT, subject);
        }

        String impersonatedSubject = firstHeader(exchange, "X-Impersonate-Subject");
        String impersonationReason = firstHeader(exchange, "X-Impersonation-Reason");

        Set<String> backofficeRoles = principal.authorities() != null ? principal.authorities() : Set.of();
        Set<String> backofficePermissions = principal.scopes() != null ? principal.scopes() : Set.of();
        UUID tenantId = parseUuid(principal.tenantId());

        return BackofficeContext.builder()
                .backofficeUserId(backofficeUserId)
                .impersonatedSubject(impersonatedSubject)
                .tenantId(tenantId)
                .backofficeRoles(backofficeRoles)
                .backofficePermissions(backofficePermissions)
                .impersonatedSubjectRoles(Set.of())
                .impersonatedSubjectPermissions(Set.of())
                .impersonationReason(impersonationReason)
                .backofficeUserIpAddress(extractIpAddress(exchange))
                .attributes(attributes)
                .build();
    }

    @Override
    public Mono<UUID> resolveBackofficeUserId(ServerWebExchange exchange) {
        log.debug("Resolving backoffice operator ID from validated security principal");
        return securityContextPort.currentPrincipal()
                .mapNotNull(principal -> parseUuid(principal.subject()));
    }

    @Override
    public Mono<UUID> resolveTenantId(ServerWebExchange exchange) {
        log.debug("Resolving tenant ID from validated security principal");
        return securityContextPort.currentPrincipal()
                .mapNotNull(principal -> parseUuid(principal.tenantId()));
    }

    @Override
    protected Mono<Set<String>> resolveBackofficeRoles(BackofficeContext context, ServerWebExchange exchange) {
        return securityContextPort.currentPrincipal()
                .map(principal -> principal.authorities() != null ? principal.authorities() : Set.<String>of())
                .defaultIfEmpty(Set.of());
    }

    @Override
    protected Mono<Set<String>> resolveBackofficePermissions(BackofficeContext context, ServerWebExchange exchange) {
        return securityContextPort.currentPrincipal()
                .map(principal -> principal.scopes() != null ? principal.scopes() : Set.<String>of())
                .defaultIfEmpty(Set.of());
    }

    @Override
    public boolean supports(ServerWebExchange exchange) {
        return true;
    }

    @Override
    public int getPriority() {
        return 0;
    }

    /**
     * Parses the given value to a {@link UUID}, returning {@code null} when it is absent or not a UUID.
     */
    private static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String firstHeader(ServerWebExchange exchange, String headerName) {
        String value = exchange.getRequest().getHeaders().getFirst(headerName);
        return (value != null && !value.isEmpty()) ? value : null;
    }
}

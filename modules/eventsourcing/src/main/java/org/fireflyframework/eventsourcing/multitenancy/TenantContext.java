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

package org.fireflyframework.eventsourcing.multitenancy;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * Context holder for tenant information in reactive streams.
 * Provides tenant isolation for event sourcing operations.
 * 
 * <p>Example usage:
 * <pre>
 * // Set tenant context
 * return eventStore.appendEvents(...)
 *     .contextWrite(TenantContext.withTenantId("tenant-123"));
 * 
 * // Get current tenant
 * String tenantId = TenantContext.getCurrentTenantId()
 *     .block();
 * </pre>
 */
public class TenantContext {

    private static final String TENANT_ID_KEY = "tenantId";
    private static final String DEFAULT_TENANT = "default";

    /**
     * Get the current tenant ID from the reactive context.
     *
     * @return Mono with the tenant ID
     */
    public static Mono<String> getCurrentTenantId() {
        return Mono.deferContextual(ctx -> 
            Mono.just(ctx.getOrDefault(TENANT_ID_KEY, DEFAULT_TENANT))
        );
    }

    /**
     * Get the current tenant ID or return default.
     *
     * @return the tenant ID
     */
    public static String getCurrentTenantIdOrDefault() {
        return getCurrentTenantId().block();
    }

    /**
     * Create a context with the given tenant ID.
     *
     * @param tenantId the tenant ID
     * @return the context function
     */
    public static java.util.function.Function<Context, Context> withTenantId(String tenantId) {
        return ctx -> ctx.put(TENANT_ID_KEY, tenantId);
    }

    /**
     * Check if a tenant ID is set in the current context.
     *
     * @return Mono with true if tenant is set
     */
    public static Mono<Boolean> hasTenantId() {
        return Mono.deferContextual(ctx -> 
            Mono.just(ctx.hasKey(TENANT_ID_KEY))
        );
    }

    /**
     * Clear the tenant context.
     *
     * @return the context function
     */
    public static java.util.function.Function<Context, Context> clear() {
        return ctx -> ctx.delete(TENANT_ID_KEY);
    }

    /**
     * Get the default tenant ID.
     *
     * @return the default tenant ID
     */
    public static String getDefaultTenant() {
        return DEFAULT_TENANT;
    }
}


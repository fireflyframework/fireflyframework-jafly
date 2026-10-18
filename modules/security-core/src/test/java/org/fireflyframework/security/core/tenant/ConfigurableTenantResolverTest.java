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

package org.fireflyframework.security.core.tenant;

import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;

class ConfigurableTenantResolverTest {

    private final ConfigurableTenantResolver resolver = new ConfigurableTenantResolver(List.of("tenant_ids"));

    private static SecurityPrincipal principalWith(Object tenantClaim) {
        return SecurityPrincipal.builder()
                .subject("user-1")
                .claims(tenantClaim == null ? Map.of() : Map.of("tenant_ids", tenantClaim))
                .build();
    }

    @Test
    void emitsTheSingleTenant() {
        StepVerifier.create(resolver.resolveTenant(principalWith(List.of("tenant-a"))))
                .expectNext("tenant-a")
                .verifyComplete();
    }

    @Test
    void emptyWhenNoTenant() {
        StepVerifier.create(resolver.resolveTenant(principalWith(null)))
                .verifyComplete();
    }

    @Test
    void emptyWhenMoreThanOneTenantFailClosed() {
        StepVerifier.create(resolver.resolveTenant(principalWith(List.of("tenant-a", "tenant-b"))))
                .verifyComplete();
    }

    @Test
    void readsNestedClaimPath() {
        ConfigurableTenantResolver nested = new ConfigurableTenantResolver(List.of("org.tenant"));
        SecurityPrincipal principal = SecurityPrincipal.builder()
                .subject("user-1")
                .claims(Map.of("org", Map.of("tenant", "tenant-x")))
                .build();
        StepVerifier.create(nested.resolveTenant(principal))
                .expectNext("tenant-x")
                .verifyComplete();
    }
}

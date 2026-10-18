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
import org.fireflyframework.security.core.authority.ClaimPaths;
import org.fireflyframework.security.spi.TenantResolverPort;
import reactor.core.publisher.Mono;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Default {@link TenantResolverPort}: reads the tenant id from the validated token claims at the
 * configured dot-paths (default {@code tenant_ids}, coalesced across paths).
 *
 * <p>Single-tenant and <strong>fail-closed</strong>: it emits the id only when the token carries
 * exactly one tenant, and {@link Mono#empty()} for 0 or &gt;1 so the caller decides how to fail
 * (the header bridge turns empty into a 403 when a tenant is required). Picking one silently is
 * never an option — that would risk acting on the wrong tenant. The future multi-tenant change
 * (active tenant chosen from the request and validated against the set) is a change to the caller,
 * not to this single-valued port.</p>
 */
public class ConfigurableTenantResolver implements TenantResolverPort {

    private final List<String> claimPaths;

    public ConfigurableTenantResolver(List<String> claimPaths) {
        this.claimPaths = List.copyOf(claimPaths);
    }

    @Override
    public Mono<String> resolveTenant(SecurityPrincipal principal) {
        Set<String> tenants = new LinkedHashSet<>();
        for (String path : claimPaths) {
            tenants.addAll(ClaimPaths.readStringSet(principal.claims(), path));
        }
        return tenants.size() == 1 ? Mono.just(tenants.iterator().next()) : Mono.empty();
    }
}

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

package org.fireflyframework.security.webflux.bridge;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for the principal&rarr;header bridge. Disabled by default so existing consumers are
 * untouched; opting in publishes the trusted tenant/user/roles headers derived from the validated
 * token.
 */
@Data
@ConfigurationProperties(prefix = "firefly.security.webflux.header-bridge")
public class PrincipalHeaderBridgeProperties {

    /** Master switch; {@code false} by default so no existing consumer changes behaviour. */
    private boolean enabled = false;

    /** Header set to the resolved tenant id. */
    private String tenantHeader = "X-Tenant-Id";

    /** Header set to the authenticated subject. */
    private String userHeader = "X-User-Id";

    /** Header set to the comma-joined authorities. */
    private String rolesHeader = "X-User-Roles";

    /** When {@code true}, an unresolved tenant fails the request with 403 (fail-closed). */
    private boolean tenantRequired = true;

    /** Claim dot-paths inspected for the tenant id (passed to the default resolver). */
    private List<String> tenantClaimPaths = new ArrayList<>(List.of("tenant_ids"));
}

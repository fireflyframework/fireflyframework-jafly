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

package org.fireflyframework.data.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * Response DTO for the enrichment preview endpoint.
 *
 * <p>Provides metadata about which provider would be selected for a given
 * enrichment request without actually executing the enrichment. This is useful
 * for cost estimation, debugging provider selection, and verifying configuration.</p>
 *
 * <p><b>Example Response:</b></p>
 * <pre>{@code
 * {
 *   "providerName": "Financial Data Provider",
 *   "enrichmentType": "company-profile",
 *   "providerVersion": "2.1.0",
 *   "priority": 100,
 *   "cached": true,
 *   "tenantId": "550e8400-e29b-41d4-a716-446655440001",
 *   "autoSelected": true,
 *   "description": "Enriches company data with financial information",
 *   "tags": ["production", "gdpr-compliant"]
 * }
 * }</pre>
 */
@Data
@Builder
@Schema(description = "Preview of which enrichment provider would be selected without executing")
public class PreviewResponse {

    @Schema(description = "Name of the provider that would be selected", example = "Financial Data Provider")
    private final String providerName;

    @Schema(description = "The enrichment type requested", example = "company-profile")
    private final String enrichmentType;

    @Schema(description = "Version of the selected provider", example = "2.1.0")
    private final String providerVersion;

    @Schema(description = "Priority of the selected provider", example = "100")
    private final int priority;

    @Schema(description = "Whether caching is active for this provider", example = "true")
    private final boolean cached;

    @Schema(description = "Tenant ID for the request", example = "550e8400-e29b-41d4-a716-446655440001")
    private final UUID tenantId;

    @Schema(description = "Whether the provider was automatically selected by the registry", example = "true")
    private final boolean autoSelected;

    @Schema(description = "Description of the selected provider", example = "Enriches company data with financial information")
    private final String description;

    @Schema(description = "Tags associated with the selected provider", example = "[\"production\", \"gdpr-compliant\"]")
    private final List<String> tags;
}

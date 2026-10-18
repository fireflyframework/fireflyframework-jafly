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

package org.fireflyframework.webhooks.core.services;

import org.fireflyframework.webhooks.core.domain.WebhookMetadata;
import org.springframework.http.server.reactive.ServerHttpRequest;

/**
 * Service for enriching webhook events with additional metadata.
 * <p>
 * Enrichment includes:
 * - User-Agent parsing
 * - High-precision timestamps
 * - Request ID generation
 */
public interface WebhookMetadataEnrichmentService {

    /**
     * Enriches a webhook request with additional metadata.
     *
     * @param request the HTTP request
     * @param sourceIp the source IP address
     * @return enriched metadata
     */
    WebhookMetadata enrich(ServerHttpRequest request, String sourceIp);

    /**
     * Parses User-Agent header.
     *
     * @param userAgent the User-Agent string
     * @return parsed User-Agent information
     */
    WebhookMetadata.UserAgentInfo parseUserAgent(String userAgent);

    /**
     * Generates a unique request ID.
     *
     * @return unique request ID
     */
    String generateRequestId();
}


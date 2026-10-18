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

package org.fireflyframework.webhooks.core.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Enriched metadata for webhook events.
 * <p>
 * Contains additional information extracted from the request for better
 * observability, debugging, and analytics.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookMetadata {

    /**
     * Unique request ID for this webhook
     */
    private String requestId;

    /**
     * Timestamp with nanosecond precision
     */
    private Instant receivedAtNanos;

    /**
     * Source IP address
     */
    private String sourceIp;

    /**
     * User-Agent information
     */
    private UserAgentInfo userAgent;

    /**
     * Request size in bytes
     */
    private Long requestSize;

    /**
     * Response time in milliseconds
     */
    private Long responseTimeMs;

    /**
     * User-Agent parsing information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserAgentInfo {
        private String raw;
        private String browser;
        private String browserVersion;
        private String os;
        private String osVersion;
        private String device;
        private String deviceType;

        @JsonProperty("bot")
        private boolean bot;
    }
}


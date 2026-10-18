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

package org.fireflyframework.webhooks.core.services.impl;

import org.fireflyframework.webhooks.core.domain.WebhookMetadata;
import org.fireflyframework.webhooks.core.services.WebhookMetadataEnrichmentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Implementation of webhook metadata enrichment service.
 */
@Service
@Slf4j
@ConditionalOnProperty(name = "firefly.webhooks.metadata-enrichment.enabled", havingValue = "true", matchIfMissing = true)
public class WebhookMetadataEnrichmentServiceImpl implements WebhookMetadataEnrichmentService {

    // Common bot patterns
    private static final Pattern BOT_PATTERN = Pattern.compile(
            "(bot|crawler|spider|scraper|slurp|bingpreview|facebookexternalhit|twitterbot|linkedinbot)",
            Pattern.CASE_INSENSITIVE
    );

    // User-Agent parsing patterns (simplified)
    private static final Pattern BROWSER_PATTERN = Pattern.compile(
            "(Chrome|Firefox|Safari|Edge|Opera|MSIE|Trident)[\\/\\s]([\\d.]+)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern OS_PATTERN = Pattern.compile(
            "(Windows NT|Mac OS X|Linux|Android|iOS)[\\/\\s]?([\\d._]+)?",
            Pattern.CASE_INSENSITIVE
    );

    @Override
    public WebhookMetadata enrich(ServerHttpRequest request, String sourceIp) {
        String userAgentHeader = request.getHeaders().getFirst("User-Agent");
        String requestId = generateRequestId();

        return WebhookMetadata.builder()
                .requestId(requestId)
                .receivedAtNanos(Instant.now())
                .sourceIp(sourceIp)
                .userAgent(parseUserAgent(userAgentHeader))
                .requestSize(request.getHeaders().getContentLength())
                .build();
    }

    @Override
    public WebhookMetadata.UserAgentInfo parseUserAgent(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return WebhookMetadata.UserAgentInfo.builder()
                    .raw("Unknown")
                    .browser("Unknown")
                    .os("Unknown")
                    .device("Unknown")
                    .deviceType("Unknown")
                    .bot(false)
                    .build();
        }

        boolean isBot = BOT_PATTERN.matcher(userAgent).find();

        // Parse browser
        String browser = "Unknown";
        String browserVersion = "Unknown";
        Matcher browserMatcher = BROWSER_PATTERN.matcher(userAgent);
        if (browserMatcher.find()) {
            browser = browserMatcher.group(1);
            browserVersion = browserMatcher.group(2);
        }

        // Parse OS
        String os = "Unknown";
        String osVersion = "Unknown";
        Matcher osMatcher = OS_PATTERN.matcher(userAgent);
        if (osMatcher.find()) {
            os = osMatcher.group(1);
            if (osMatcher.group(2) != null) {
                osVersion = osMatcher.group(2);
            }
        }

        // Determine device type
        String deviceType = determineDeviceType(userAgent);

        return WebhookMetadata.UserAgentInfo.builder()
                .raw(userAgent)
                .browser(browser)
                .browserVersion(browserVersion)
                .os(os)
                .osVersion(osVersion)
                .device("Unknown") // Would need more sophisticated parsing
                .deviceType(deviceType)
                .bot(isBot)
                .build();
    }

    @Override
    public String generateRequestId() {
        return UUID.randomUUID().toString();
    }

    /**
     * Determines device type from User-Agent.
     */
    private String determineDeviceType(String userAgent) {
        String ua = userAgent.toLowerCase();
        if (ua.contains("mobile") || ua.contains("android") || ua.contains("iphone")) {
            return "Mobile";
        } else if (ua.contains("tablet") || ua.contains("ipad")) {
            return "Tablet";
        } else {
            return "Desktop";
        }
    }
}


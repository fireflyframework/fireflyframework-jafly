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

import com.fasterxml.jackson.databind.JsonNode;
import org.fireflyframework.webhooks.core.domain.events.WebhookRejectedEvent;
import org.springframework.http.server.reactive.ServerHttpRequest;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Helper class for building WebhookRejectedEvent instances.
 */
public class WebhookRejectionHelper {

    /**
     * Builds a WebhookRejectedEvent from a validation failure.
     *
     * @param providerName the provider name
     * @param payload the webhook payload
     * @param request the HTTP request
     * @param rejectionReason the reason for rejection
     * @param category the rejection category
     * @return the rejected event
     */
    public static WebhookRejectedEvent buildRejectedEvent(
            String providerName,
            JsonNode payload,
            ServerHttpRequest request,
            String rejectionReason,
            WebhookRejectedEvent.RejectionCategory category) {
        
        return buildRejectedEvent(
                providerName,
                payload,
                request,
                rejectionReason,
                category,
                null,
                null,
                null
        );
    }

    /**
     * Builds a WebhookRejectedEvent with full details.
     *
     * @param providerName the provider name
     * @param payload the webhook payload
     * @param request the HTTP request
     * @param rejectionReason the reason for rejection
     * @param category the rejection category
     * @param errorDetails error details or stack trace
     * @param retryCount number of retry attempts
     * @param exception the exception that caused rejection
     * @return the rejected event
     */
    public static WebhookRejectedEvent buildRejectedEvent(
            String providerName,
            JsonNode payload,
            ServerHttpRequest request,
            String rejectionReason,
            WebhookRejectedEvent.RejectionCategory category,
            String errorDetails,
            Integer retryCount,
            Throwable exception) {

        // Extract headers
        Map<String, String> headers = request.getHeaders().toSingleValueMap();

        // Extract query params
        Map<String, String> queryParams = request.getQueryParams().entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().isEmpty() ? "" : e.getValue().get(0)
                ));

        // Extract source IP
        String sourceIp = extractSourceIp(request);

        return WebhookRejectedEvent.builder()
                .eventId(UUID.randomUUID())
                .providerName(providerName)
                .payload(payload)
                .headers(headers)
                .queryParams(queryParams)
                .receivedAt(Instant.now())
                .rejectedAt(Instant.now())
                .sourceIp(sourceIp)
                .httpMethod(request.getMethod().name())
                .rejectionReason(rejectionReason)
                .rejectionCategory(category)
                .errorDetails(errorDetails)
                .retryCount(retryCount)
                .exceptionType(exception != null ? exception.getClass().getName() : null)
                .build();
    }

    /**
     * Extracts the source IP from the request.
     *
     * @param request the HTTP request
     * @return the source IP address
     */
    private static String extractSourceIp(ServerHttpRequest request) {
        String forwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isEmpty()) {
            return forwardedFor.split(",")[0].trim();
        }

        if (request.getRemoteAddress() != null) {
            return request.getRemoteAddress().getAddress().getHostAddress();
        }

        return "unknown";
    }
}


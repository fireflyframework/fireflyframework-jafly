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

package org.fireflyframework.webhooks.web.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import org.fireflyframework.webhooks.core.domain.WebhookMetadata;
import org.fireflyframework.webhooks.core.services.WebhookMetadataEnrichmentService;
import org.fireflyframework.webhooks.core.services.WebhookProcessingService;
import org.fireflyframework.webhooks.interfaces.dto.WebhookEventDTO;
import org.fireflyframework.webhooks.interfaces.dto.WebhookResponseDTO;
import org.fireflyframework.webhooks.core.metrics.WebhookMetricsService;
import org.fireflyframework.webhooks.core.validation.WebhookValidator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * REST controller for receiving webhooks from multiple providers.
 * <p>
 * This controller provides a dynamic endpoint that accepts webhooks from any
 * provider without requiring code changes. The provider name is captured from
 * the URL path and the webhook payload is accepted AS-IS.
 * <p>
 * <b>Important:</b> This controller does NOT validate webhook signatures.
 * Signature validation is performed by the worker/processor that consumes
 * events from the message queue. This allows:
 * <ul>
 *   <li>Headers to be stored in the event store for audit trail</li>
 *   <li>Workers to validate signatures when processing</li>
 *   <li>Replay capability with signature re-validation</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/webhook")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Webhooks", description = "Webhook ingestion endpoints for multiple providers")
public class WebhookController {

    private final WebhookProcessingService webhookProcessingService;
    private final WebhookValidator webhookValidator;
    private final WebhookMetricsService metricsService;
    private final org.fireflyframework.webhooks.core.ratelimit.WebhookRateLimitService rateLimitService;
    private final WebhookMetadataEnrichmentService metadataEnrichmentService;

    /**
     * Universal webhook endpoint that accepts webhooks from any provider.
     * <p>
     * The endpoint accepts:
     * - Dynamic provider name in the URL path (e.g., /api/v1/webhook/stripe)
     * - Any JSON payload in the request body
     * - All HTTP headers for signature validation
     * - Query parameters if needed
     * - Optional X-Idempotency-Key header (handled by lib-common-web IdempotencyWebFilter)
     * <p>
     * Examples:
     * - POST /api/v1/webhook/stripe
     * - POST /api/v1/webhook/paypal
     * - POST /api/v1/webhook/twilio
     * - POST /api/v1/webhook/custom-provider
     * <p>
     * Note: HTTP-level idempotency is handled transparently by the IdempotencyWebFilter
     * from lib-common-web. If a request includes an X-Idempotency-Key header, the filter
     * will cache the entire HTTP response and return it for duplicate requests.
     *
     * @param providerName the name of the webhook provider
     * @param payload the raw webhook payload as JSON
     * @param request the HTTP request for extracting headers and metadata
     * @param queryParams query parameters from the request
     * @return a Mono containing the ResponseEntity with webhook response
     */
    @PostMapping(
            value = "/{providerName}",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(
            summary = "Receive webhook from any provider",
            description = "Universal endpoint that accepts webhooks from any provider. " +
                    "The webhook payload is stored AS-IS and published to a message queue for processing."
    )
    @ApiResponse(
            responseCode = "202",
            description = "Webhook accepted and queued for processing",
            content = @Content(schema = @Schema(implementation = WebhookResponseDTO.class))
    )
    @ApiResponse(
            responseCode = "400",
            description = "Invalid webhook payload"
    )
    @ApiResponse(
            responseCode = "500",
            description = "Internal server error"
    )
    public Mono<ResponseEntity<WebhookResponseDTO>> receiveWebhook(
            @Parameter(description = "Name of the webhook provider", example = "stripe", required = true)
            @PathVariable String providerName,
            @Parameter(description = "Raw webhook payload", required = true)
            @RequestBody JsonNode payload,
            ServerHttpRequest request,
            @RequestParam(required = false) MultiValueMap<String, String> queryParams
    ) {
        Instant startTime = Instant.now();
        UUID eventId = UUID.randomUUID();

        log.info("Received webhook from provider: {} eventId: {}", providerName, eventId);

        // Record metrics
        metricsService.recordWebhookReceived(providerName);

        // Apply rate limiting
        String sourceIp = extractSourceIp(request);

        return rateLimitService.executeWithRateLimit(providerName,
                rateLimitService.executeWithIpRateLimit(sourceIp,
                        Mono.defer(() -> {
                            try {
                                // 1. Validate request
                                webhookValidator.validateRequest(providerName, payload, request);

                                // 2. Process webhook
                                // Note: HTTP-level idempotency is handled by IdempotencyWebFilter (lib-common-web)
                                return processWebhook(providerName, payload, request, queryParams,
                                        eventId, startTime);

                            } catch (ResponseStatusException e) {
                                // Validation failed
                                metricsService.recordWebhookRejected(providerName, e.getReason());
                                return Mono.error(e);
                            }
                        })
                )
        )
        .map(response -> ResponseEntity.status(HttpStatus.ACCEPTED).body(response))
        .contextWrite(ctx -> ctx.put("eventId", eventId.toString()).put("provider", providerName));
    }

    /**
     * Processes a webhook.
     */
    private Mono<WebhookResponseDTO> processWebhook(
            String providerName,
            JsonNode payload,
            ServerHttpRequest request,
            MultiValueMap<String, String> queryParams,
            UUID eventId,
            Instant startTime) {

        // Extract headers - these will be stored in event store and passed to workers
        Map<String, String> headers = extractHeaders(request);

        // Record payload size
        long payloadSize = payload.toString().getBytes().length;
        metricsService.recordPayloadSize(providerName, payloadSize);

        // Extract source IP
        String sourceIp = extractSourceIp(request);

        // Enrich metadata with User-Agent parsing, timestamps, etc.
        WebhookMetadata enrichedMetadata = metadataEnrichmentService.enrich(request, sourceIp);

        // Build webhook event DTO
        WebhookEventDTO eventDto = WebhookEventDTO.builder()
                .eventId(eventId)
                .providerName(providerName.toLowerCase())
                .payload(payload)
                .headers(headers)
                .queryParams(extractQueryParams(queryParams))
                .receivedAt(startTime)
                .sourceIp(sourceIp)
                .httpMethod(request.getMethod().name())
                .enrichedMetadata(convertMetadataToMap(enrichedMetadata))
                .build();

        // Process the webhook
        return webhookProcessingService.processWebhook(eventDto)
                .map(response -> {
                    // Calculate response time and add to metadata
                    long responseTimeMs = java.time.Duration.between(startTime, Instant.now()).toMillis();
                    if (response.getMetadata() != null) {
                        response.getMetadata().put("responseTimeMs", responseTimeMs);
                    }
                    return response;
                })
                .doOnSuccess(response -> {
                    log.info("Webhook processed successfully: {} from provider: {}", eventId, providerName);
                    metricsService.recordWebhookPublished(providerName);
                    metricsService.recordProcessingTime(providerName, startTime);
                })
                .doOnError(error -> {
                    log.error("Error processing webhook from provider: {}", providerName, error);
                    String errorType = error.getClass().getSimpleName();
                    metricsService.recordWebhookFailed(providerName, errorType);
                });
    }

    /**
     * Extracts HTTP headers from the request.
     * <p>
     * Headers are stored in the event store and passed to workers for signature validation.
     * This allows workers to validate signatures when processing events from the queue.
     *
     * @param request the HTTP request
     * @return map of header names to values
     */
    private Map<String, String> extractHeaders(ServerHttpRequest request) {
        Map<String, String> headers = new HashMap<>();
        request.getHeaders().forEach((name, values) -> {
            if (!values.isEmpty()) {
                headers.put(name, values.get(0)); // Take first value if multiple
            }
        });
        return headers;
    }

    /**
     * Extracts query parameters from the request.
     *
     * @param queryParams the query parameters
     * @return map of parameter names to values
     */
    private Map<String, String> extractQueryParams(MultiValueMap<String, String> queryParams) {
        if (queryParams == null || queryParams.isEmpty()) {
            return Map.of();
        }

        Map<String, String> params = new HashMap<>();
        queryParams.forEach((name, values) -> {
            if (!values.isEmpty()) {
                params.put(name, values.get(0)); // Take first value if multiple
            }
        });
        return params;
    }

    /**
     * Extracts the source IP address from the request.
     * <p>
     * Handles X-Forwarded-For header for proxied requests.
     *
     * @param request the HTTP request
     * @return the source IP address
     */
    private String extractSourceIp(ServerHttpRequest request) {
        String forwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isEmpty()) {
            return forwardedFor.split(",")[0].trim();
        }

        if (request.getRemoteAddress() != null) {
            return request.getRemoteAddress().getAddress().getHostAddress();
        }

        return "unknown";
    }

    /**
     * Converts WebhookMetadata to Map for DTO.
     *
     * @param metadata the metadata object
     * @return map representation
     */
    private Map<String, Object> convertMetadataToMap(WebhookMetadata metadata) {
        if (metadata == null) {
            return null;
        }

        Map<String, Object> map = new HashMap<>();
        map.put("requestId", metadata.getRequestId());
        map.put("receivedAtNanos", metadata.getReceivedAtNanos());
        map.put("sourceIp", metadata.getSourceIp());
        map.put("requestSize", metadata.getRequestSize());

        // responseTimeMs will be null here (calculated later in the response)
        if (metadata.getResponseTimeMs() != null) {
            map.put("responseTimeMs", metadata.getResponseTimeMs());
        }

        if (metadata.getUserAgent() != null) {
            Map<String, Object> userAgentMap = new HashMap<>();
            userAgentMap.put("raw", metadata.getUserAgent().getRaw());
            userAgentMap.put("browser", metadata.getUserAgent().getBrowser());
            userAgentMap.put("browserVersion", metadata.getUserAgent().getBrowserVersion());
            userAgentMap.put("os", metadata.getUserAgent().getOs());
            userAgentMap.put("osVersion", metadata.getUserAgent().getOsVersion());
            userAgentMap.put("device", metadata.getUserAgent().getDevice());
            userAgentMap.put("deviceType", metadata.getUserAgent().getDeviceType());
            userAgentMap.put("bot", metadata.getUserAgent().isBot());
            map.put("userAgent", userAgentMap);
        }

        return map;
    }
}
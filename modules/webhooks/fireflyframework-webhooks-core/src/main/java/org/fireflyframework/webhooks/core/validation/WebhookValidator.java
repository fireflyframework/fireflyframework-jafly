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

package org.fireflyframework.webhooks.core.validation;

import com.fasterxml.jackson.databind.JsonNode;
import org.fireflyframework.webhooks.core.config.WebhookSecurityProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.net.util.SubnetUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Validator for webhook requests.
 * Validates payload size, provider name, IP whitelist, and other security constraints.
 */
@Component
@Slf4j
public class WebhookValidator {

    private final WebhookSecurityProperties securityProperties;

    private Pattern providerNamePattern;

    public WebhookValidator(WebhookSecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    /**
     * Validates the webhook request.
     *
     * @param providerName the provider name from URL path
     * @param payload the webhook payload
     * @param request the HTTP request
     * @throws ResponseStatusException if validation fails
     */
    public void validateRequest(String providerName, JsonNode payload, ServerHttpRequest request) {
        validateProviderName(providerName);
        validatePayloadSize(payload);
        validateContentType(request);
        validateIpWhitelist(providerName, request);
    }

    /**
     * Validates the provider name format.
     *
     * @param providerName the provider name
     * @throws ResponseStatusException if validation fails
     */
    public void validateProviderName(String providerName) {
        if (!securityProperties.isValidateProviderName()) {
            return;
        }

        if (providerName == null || providerName.isBlank()) {
            log.warn("Provider name is null or blank");
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Provider name is required"
            );
        }

        // Lazy initialize pattern
        if (providerNamePattern == null) {
            providerNamePattern = Pattern.compile(securityProperties.getProviderNamePattern());
        }

        if (!providerNamePattern.matcher(providerName).matches()) {
            log.warn("Invalid provider name format: {}", providerName);
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid provider name format. Must match pattern: " + securityProperties.getProviderNamePattern()
            );
        }
    }

    /**
     * Validates the payload size.
     *
     * @param payload the webhook payload
     * @throws ResponseStatusException if payload is too large
     */
    public void validatePayloadSize(JsonNode payload) {
        if (!securityProperties.isValidatePayloadSize()) {
            return;
        }

        if (payload == null) {
            log.warn("Payload is null");
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Payload is required"
            );
        }

        long payloadSize = payload.toString().getBytes().length;
        long maxSize = securityProperties.getMaxPayloadSize();

        if (payloadSize > maxSize) {
            log.warn("Payload size {} exceeds maximum allowed size {}", payloadSize, maxSize);
            throw new ResponseStatusException(
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    String.format("Payload size %d bytes exceeds maximum allowed size %d bytes", payloadSize, maxSize)
            );
        }

        log.debug("Payload size validation passed: {} bytes", payloadSize);
    }

    /**
     * Validates the Content-Type header.
     *
     * @param request the HTTP request
     * @throws ResponseStatusException if Content-Type is invalid
     */
    public void validateContentType(ServerHttpRequest request) {
        if (!securityProperties.isRequireContentType()) {
            return;
        }

        String contentType = request.getHeaders().getFirst("Content-Type");
        if (contentType == null || contentType.isBlank()) {
            log.warn("Content-Type header is missing");
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Content-Type header is required"
            );
        }

        // Extract base content type (ignore charset and other parameters)
        String baseContentType = contentType.split(";")[0].trim().toLowerCase();

        List<String> allowedTypes = securityProperties.getAllowedContentTypes();
        if (!allowedTypes.contains(baseContentType)) {
            log.warn("Invalid Content-Type: {}. Allowed types: {}", baseContentType, allowedTypes);
            throw new ResponseStatusException(
                    HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Content-Type must be one of: " + String.join(", ", allowedTypes)
            );
        }
    }

    /**
     * Validates the source IP against whitelist.
     *
     * @param providerName the provider name
     * @param request the HTTP request
     * @throws ResponseStatusException if IP is not whitelisted
     */
    public void validateIpWhitelist(String providerName, ServerHttpRequest request) {
        if (!securityProperties.isEnableIpWhitelist()) {
            return;
        }

        List<String> whitelist = securityProperties.getIpWhitelist().get(providerName);
        if (whitelist == null || whitelist.isEmpty()) {
            // No whitelist configured for this provider, allow all
            return;
        }

        String sourceIp = extractSourceIp(request);
        
        // Check if IP is in whitelist (supports CIDR notation)
        boolean isWhitelisted = whitelist.stream()
                .anyMatch(allowedIp -> isIpMatch(sourceIp, allowedIp));

        if (!isWhitelisted) {
            log.warn("IP {} not whitelisted for provider {}", sourceIp, providerName);
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Source IP not authorized for this provider"
            );
        }

        log.debug("IP whitelist validation passed: {} for provider {}", sourceIp, providerName);
    }

    /**
     * Extracts the source IP from the request.
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
     * Checks if an IP matches an allowed IP (supports CIDR notation).
     * Supports both exact IP matching and CIDR notation (e.g., "192.168.1.0/24").
     *
     * @param sourceIp the source IP
     * @param allowedIp the allowed IP (can be CIDR notation)
     * @return true if IP matches
     */
    private boolean isIpMatch(String sourceIp, String allowedIp) {
        try {
            // Check if allowedIp is in CIDR notation
            if (allowedIp.contains("/")) {
                // CIDR notation - use SubnetUtils for matching
                SubnetUtils subnetUtils = new SubnetUtils(allowedIp);
                subnetUtils.setInclusiveHostCount(true); // Include network and broadcast addresses
                return subnetUtils.getInfo().isInRange(sourceIp);
            } else {
                // Exact IP match
                return sourceIp.equals(allowedIp);
            }
        } catch (IllegalArgumentException e) {
            log.warn("Invalid IP or CIDR notation: allowedIp={}, error={}", allowedIp, e.getMessage());
            return false;
        }
    }
}


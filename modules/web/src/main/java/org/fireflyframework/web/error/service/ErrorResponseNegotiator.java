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

package org.fireflyframework.web.error.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.fireflyframework.web.error.models.ErrorResponse;
import org.fireflyframework.web.error.models.ProblemDetail;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;

import java.util.List;

/**
 * Service for negotiating error response format based on client preferences.
 * <p>
 * This service determines the appropriate error response format based on the Accept header
 * and converts ErrorResponse to the requested format (RFC 7807 Problem Details or standard JSON).
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ErrorResponseNegotiator {

    /**
     * RFC 7807 Problem Details media type.
     */
    public static final MediaType APPLICATION_PROBLEM_JSON = MediaType.parseMediaType("application/problem+json");

    private final ObjectMapper objectMapper;

    /**
     * Negotiates the error response format based on the Accept header.
     *
     * @param exchange      the server web exchange
     * @param errorResponse the error response to format
     * @return the formatted error response as a byte array
     */
    public byte[] negotiate(ServerWebExchange exchange, ErrorResponse errorResponse) {
        MediaType preferredMediaType = determinePreferredMediaType(exchange);
        
        if (isProblemJsonPreferred(preferredMediaType)) {
            return formatAsProblemJson(errorResponse);
        } else {
            return formatAsStandardJson(errorResponse);
        }
    }

    /**
     * Determines the content type to use for the error response.
     *
     * @param exchange the server web exchange
     * @return the content type to use
     */
    public MediaType determineContentType(ServerWebExchange exchange) {
        MediaType preferredMediaType = determinePreferredMediaType(exchange);
        
        if (isProblemJsonPreferred(preferredMediaType)) {
            return APPLICATION_PROBLEM_JSON;
        } else {
            return MediaType.APPLICATION_JSON;
        }
    }

    /**
     * Determines the preferred media type from the Accept header.
     *
     * @param exchange the server web exchange
     * @return the preferred media type
     */
    private MediaType determinePreferredMediaType(ServerWebExchange exchange) {
        List<MediaType> acceptedMediaTypes = exchange.getRequest().getHeaders().getAccept();
        
        if (acceptedMediaTypes == null || acceptedMediaTypes.isEmpty()) {
            return MediaType.APPLICATION_JSON;
        }

        // Sort by quality factor (q parameter) - highest quality first
        List<MediaType> sortedMediaTypes = acceptedMediaTypes.stream()
                .sorted((m1, m2) -> {
                    double q1 = m1.getQualityValue();
                    double q2 = m2.getQualityValue();
                    return Double.compare(q2, q1); // Descending order
                })
                .toList();

        // Find the first acceptable media type
        for (MediaType mediaType : sortedMediaTypes) {
            if (isProblemJsonCompatible(mediaType)) {
                return APPLICATION_PROBLEM_JSON;
            } else if (isJsonCompatible(mediaType)) {
                return MediaType.APPLICATION_JSON;
            }
        }

        // Default to application/json
        return MediaType.APPLICATION_JSON;
    }

    /**
     * Checks if the media type is compatible with RFC 7807 Problem Details.
     *
     * @param mediaType the media type to check
     * @return true if compatible, false otherwise
     */
    private boolean isProblemJsonCompatible(MediaType mediaType) {
        return mediaType.isCompatibleWith(APPLICATION_PROBLEM_JSON) ||
               (mediaType.getType().equals("application") && 
                mediaType.getSubtype().contains("problem"));
    }

    /**
     * Checks if the media type is compatible with standard JSON.
     *
     * @param mediaType the media type to check
     * @return true if compatible, false otherwise
     */
    private boolean isJsonCompatible(MediaType mediaType) {
        return mediaType.isCompatibleWith(MediaType.APPLICATION_JSON) ||
               mediaType.getSubtype().endsWith("+json") ||
               mediaType.equals(MediaType.ALL);
    }

    /**
     * Checks if RFC 7807 Problem Details format is preferred.
     *
     * @param mediaType the preferred media type
     * @return true if Problem Details is preferred, false otherwise
     */
    private boolean isProblemJsonPreferred(MediaType mediaType) {
        return mediaType.isCompatibleWith(APPLICATION_PROBLEM_JSON);
    }

    /**
     * Formats the error response as RFC 7807 Problem Details JSON.
     *
     * @param errorResponse the error response to format
     * @return the formatted response as a byte array
     */
    private byte[] formatAsProblemJson(ErrorResponse errorResponse) {
        try {
            ProblemDetail problemDetail = ProblemDetail.fromErrorResponse(errorResponse);
            return objectMapper.writeValueAsBytes(problemDetail);
        } catch (JsonProcessingException e) {
            log.error("Error serializing ProblemDetail", e);
            return formatFallbackError();
        }
    }

    /**
     * Formats the error response as standard JSON.
     *
     * @param errorResponse the error response to format
     * @return the formatted response as a byte array
     */
    private byte[] formatAsStandardJson(ErrorResponse errorResponse) {
        try {
            return objectMapper.writeValueAsBytes(errorResponse);
        } catch (JsonProcessingException e) {
            log.error("Error serializing ErrorResponse", e);
            return formatFallbackError();
        }
    }

    /**
     * Creates a fallback error response when serialization fails.
     *
     * @return a simple error message as a byte array
     */
    private byte[] formatFallbackError() {
        String fallback = "{\"error\":\"Internal Server Error\",\"message\":\"An error occurred while processing the error response\"}";
        return fallback.getBytes();
    }

    /**
     * Checks if the client accepts RFC 7807 Problem Details format.
     *
     * @param exchange the server web exchange
     * @return true if the client accepts Problem Details, false otherwise
     */
    public boolean acceptsProblemJson(ServerWebExchange exchange) {
        List<MediaType> acceptedMediaTypes = exchange.getRequest().getHeaders().getAccept();
        
        if (acceptedMediaTypes == null || acceptedMediaTypes.isEmpty()) {
            return false;
        }

        return acceptedMediaTypes.stream()
                .anyMatch(this::isProblemJsonCompatible);
    }

    /**
     * Checks if the client accepts standard JSON format.
     *
     * @param exchange the server web exchange
     * @return true if the client accepts JSON, false otherwise
     */
    public boolean acceptsJson(ServerWebExchange exchange) {
        List<MediaType> acceptedMediaTypes = exchange.getRequest().getHeaders().getAccept();
        
        if (acceptedMediaTypes == null || acceptedMediaTypes.isEmpty()) {
            return true; // Default to JSON
        }

        return acceptedMediaTypes.stream()
                .anyMatch(this::isJsonCompatible);
    }
}


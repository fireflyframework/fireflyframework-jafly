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
package org.fireflyframework.client.exception;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Parses error response bodies to extract error messages and validation errors.
 * 
 * <p>This class supports multiple error response formats including:
 * <ul>
 *   <li>Spring Boot error responses</li>
 *   <li>RFC 7807 Problem Details</li>
 *   <li>Custom error formats</li>
 * </ul>
 *
 * @author Firefly Software Foundation
 * @since 2.0.0
 */
public class ErrorResponseParser {

    private static final Logger log = LoggerFactory.getLogger(ErrorResponseParser.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Parses an error message from the response body.
     *
     * @param body the response body
     * @return the error message, or null if not found
     */
    public static String parseErrorMessage(String body) {
        if (body == null || body.isEmpty()) {
            return null;
        }

        try {
            JsonNode root = objectMapper.readTree(body);
            
            // Try common error message fields
            String[] messageFields = {"message", "error", "detail", "title", "errorMessage", "error_description"};
            for (String field : messageFields) {
                if (root.has(field)) {
                    JsonNode node = root.get(field);
                    if (node.isTextual()) {
                        return node.asText();
                    }
                }
            }
            
            // If no message field found, return the whole body if it's short
            if (body.length() < 200) {
                return body;
            }
            
        } catch (Exception e) {
            log.debug("Failed to parse error message from response body", e);
            // Return body as-is if it's short and not JSON
            if (body.length() < 200) {
                return body;
            }
        }
        
        return null;
    }

    /**
     * Parses validation errors from the response body.
     *
     * @param body the response body
     * @return list of validation errors, or empty list if none found
     */
    public static List<ValidationError> parseValidationErrors(String body) {
        if (body == null || body.isEmpty()) {
            return Collections.emptyList();
        }

        try {
            JsonNode root = objectMapper.readTree(body);
            List<ValidationError> errors = new ArrayList<>();
            
            // Try Spring Boot validation error format
            if (root.has("errors") && root.get("errors").isArray()) {
                for (JsonNode errorNode : root.get("errors")) {
                    errors.add(parseValidationError(errorNode));
                }
            }
            
            // Try RFC 7807 Problem Details format
            if (root.has("invalid-params") && root.get("invalid-params").isArray()) {
                for (JsonNode paramNode : root.get("invalid-params")) {
                    errors.add(ValidationError.builder()
                        .field(paramNode.has("name") ? paramNode.get("name").asText() : null)
                        .message(paramNode.has("reason") ? paramNode.get("reason").asText() : null)
                        .build());
                }
            }
            
            // Try custom validation error format
            if (root.has("validationErrors") && root.get("validationErrors").isArray()) {
                for (JsonNode errorNode : root.get("validationErrors")) {
                    errors.add(parseValidationError(errorNode));
                }
            }
            
            return errors;
            
        } catch (Exception e) {
            log.debug("Failed to parse validation errors from response body", e);
            return Collections.emptyList();
        }
    }

    /**
     * Parses a single validation error from a JSON node.
     */
    private static ValidationError parseValidationError(JsonNode errorNode) {
        return ValidationError.builder()
            .field(extractField(errorNode, "field", "fieldName", "property", "path"))
            .message(extractField(errorNode, "message", "defaultMessage", "reason", "detail"))
            .code(extractField(errorNode, "code", "errorCode", "type"))
            .rejectedValue(errorNode.has("rejectedValue") ? errorNode.get("rejectedValue").asText() : null)
            .build();
    }

    /**
     * Extracts a field value from a JSON node, trying multiple field names.
     */
    private static String extractField(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            if (node.has(fieldName)) {
                JsonNode fieldNode = node.get(fieldName);
                if (fieldNode.isTextual()) {
                    return fieldNode.asText();
                }
            }
        }
        return null;
    }
}


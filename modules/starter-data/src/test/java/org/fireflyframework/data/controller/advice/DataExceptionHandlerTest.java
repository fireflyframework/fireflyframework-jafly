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

package org.fireflyframework.data.controller.advice;

import org.fireflyframework.data.enrichment.EnrichmentRequestValidator.EnrichmentValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link DataExceptionHandler}.
 */
class DataExceptionHandlerTest {

    private DataExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new DataExceptionHandler();
    }

    @Test
    void handleValidationException_shouldReturn400WithErrorsList() {
        // Given
        List<String> errors = List.of(
                "Required parameter 'companyId' is missing",
                "Source DTO is required but was null"
        );
        EnrichmentValidationException ex = new EnrichmentValidationException(
                "Enrichment request validation failed: " + String.join("; ", errors),
                errors
        );

        // When
        ResponseEntity<Map<String, Object>> response = handler.handleValidationException(ex);

        // Then
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("status")).isEqualTo(400);
        assertThat(response.getBody().get("error")).isEqualTo("Validation Failed");

        @SuppressWarnings("unchecked")
        List<String> returnedErrors = (List<String>) response.getBody().get("errors");
        assertThat(returnedErrors).hasSize(2);
        assertThat(returnedErrors).containsExactlyElementsOf(errors);
    }

    @Test
    void handleValidationException_shouldIncludeTimestamp() {
        // Given
        EnrichmentValidationException ex = new EnrichmentValidationException(
                "Validation failed",
                List.of("Some error")
        );

        // When
        ResponseEntity<Map<String, Object>> response = handler.handleValidationException(ex);

        // Then
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).containsKey("timestamp");
        assertThat(response.getBody().get("timestamp")).isNotNull();
        assertThat(response.getBody().get("timestamp").toString()).isNotEmpty();
    }

    @Test
    void handleValidationException_shouldIncludeOriginalMessage() {
        // Given
        String message = "Enrichment request validation failed: Required parameter 'email' is missing";
        EnrichmentValidationException ex = new EnrichmentValidationException(
                message,
                List.of("Required parameter 'email' is missing")
        );

        // When
        ResponseEntity<Map<String, Object>> response = handler.handleValidationException(ex);

        // Then
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("message")).isEqualTo(message);
    }
}

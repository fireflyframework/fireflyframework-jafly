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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Global exception handler for data controllers.
 *
 * <p>Translates domain-specific exceptions into appropriate HTTP responses,
 * ensuring clients receive meaningful error details instead of generic 500 errors.</p>
 */
@Slf4j
@RestControllerAdvice(basePackages = "org.fireflyframework.data.controller")
public class DataExceptionHandler {

    @ExceptionHandler(EnrichmentValidationException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(EnrichmentValidationException ex) {
        log.warn("Enrichment validation failed: {}", ex.getMessage());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", 400);
        body.put("error", "Validation Failed");
        body.put("message", ex.getMessage());
        body.put("errors", ex.getErrors());
        body.put("timestamp", Instant.now().toString());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }
}

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


package org.fireflyframework.core.logging.examples;

import org.fireflyframework.core.logging.LoggingUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Example class demonstrating the usage of the enhanced logging structure.
 * <p>
 * This class provides examples of different logging patterns using the
 * LoggingUtils class and structured logging.
 */
@Component
@Slf4j
public class LoggingExample {

    /**
     * Demonstrates basic logging.
     */
    public void demonstrateBasicLogging() {
        log.info("This is a basic info log");
        log.debug("This is a basic debug log");
        log.warn("This is a basic warning log");
        
        try {
            throw new RuntimeException("Test exception");
        } catch (Exception e) {
            log.error("This is a basic error log with exception", e);
        }
    }

    /**
     * Demonstrates structured logging using LoggingUtils.
     */
    public void demonstrateStructuredLogging() {
        // Simple structured log with a single additional field
        LoggingUtils.log("Processing user request")
                .with("userId", "user123")
                .info(log);

        // More complex structured log with multiple fields
        LoggingUtils.log("Order processed successfully")
                .with("orderId", "ORD-12345")
                .with("amount", 99.99)
                .with("items", 3)
                .with("shipping", "express")
                .info(log);

        // Structured log with nested data
        Map<String, Object> orderDetails = new HashMap<>();
        orderDetails.put("id", "ORD-12345");
        orderDetails.put("amount", 99.99);
        orderDetails.put("currency", "USD");

        Map<String, Object> customerDetails = new HashMap<>();
        customerDetails.put("id", "CUST-6789");
        customerDetails.put("name", "John Doe");
        customerDetails.put("tier", "premium");

        LoggingUtils.log("Order details")
                .with("order", orderDetails)
                .with("customer", customerDetails)
                .with("processingTime", 235)
                .info(log);

        // Error logging with structured data
        try {
            throw new IllegalArgumentException("Invalid order ID format");
        } catch (Exception e) {
            LoggingUtils.log("Failed to process order")
                    .with("orderId", "ORD-12345")
                    .with("errorCode", "ERR-4321")
                    .with("attemptCount", 3)
                    .error(log, e);
        }
    }

    /**
     * Demonstrates using MDC for context.
     */
    public void demonstrateMdcLogging() {
        // Using MDC for temporary context
        LoggingUtils.withMdc("requestId", "REQ-12345", () -> {
            log.info("Processing request with ID in MDC");
            
            // Nested MDC context
            return LoggingUtils.withMdc("sessionId", "SESSION-6789", () -> {
                log.info("Processing session with both request ID and session ID in MDC");
                return null;
            });
        });

        // Using multiple MDC values
        Map<String, String> context = new HashMap<>();
        context.put("userId", "user123");
        context.put("requestId", "REQ-12345");
        context.put("correlationId", "CORR-6789");

        LoggingUtils.withMdc(context, () -> {
            log.info("This log includes multiple context values");
            
            // Structured logging with MDC
            LoggingUtils.log("Processing user data")
                    .with("dataSize", 1024)
                    .with("format", "JSON")
                    .info(log);
            
            return null;
        });
    }
}

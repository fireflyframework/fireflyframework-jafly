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


package org.fireflyframework.web.error.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for error handling.
 * 
 * <p>This class provides comprehensive configuration options for customizing
 * error handling behavior including:</p>
 * <ul>
 *   <li>Environment-aware error details (dev vs production)</li>
 *   <li>Stack trace inclusion control</li>
 *   <li>Documentation and help URL configuration</li>
 *   <li>Error response customization</li>
 *   <li>Distributed tracing integration</li>
 *   <li>Metrics and observability settings</li>
 * </ul>
 *
 * <p>Example configuration in application.yml:</p>
 * <pre>
 * firefly:
 *   web:
 *     error-handling:
 *     include-stack-trace: false
 *     include-debug-info: false
 *     documentation-base-url: https://docs.example.com/errors
 *     help-base-url: https://support.example.com/help
 *     support-email: support@example.com
 *     instance-id: ${spring.application.name}:${random.uuid}
 *     enable-metrics: true
 *     enable-distributed-tracing: true
 *     mask-sensitive-data: true
 * </pre>
 */
@Data
@Configuration
@Validated
@ConfigurationProperties(prefix = "firefly.web.error-handling")
public class ErrorHandlingProperties {

    /**
     * Whether to include stack traces in error responses.
     * Should be false in production for security reasons.
     * Default: false
     */
    private boolean includeStackTrace = false;

    /**
     * Whether to include debug information in error responses.
     * Debug info may include request parameters, headers, etc.
     * Should be false in production for security reasons.
     * Default: false
     */
    private boolean includeDebugInfo = false;

    /**
     * Whether to include exception cause chain in error responses.
     * Default: false
     */
    private boolean includeExceptionCause = false;

    /**
     * Base URL for error documentation.
     * Error codes will be appended to this URL.
     * Example: https://docs.example.com/errors
     */
    private String documentationBaseUrl;

    /**
     * Base URL for help and support.
     * Example: https://support.example.com/help
     */
    private String helpBaseUrl;

    /**
     * Support email address for error assistance.
     */
    private String supportEmail;

    /**
     * Support phone number for error assistance.
     */
    private String supportPhone;

    /**
     * Instance identifier for this application instance.
     * Useful in distributed systems to identify which instance generated the error.
     * Can use Spring placeholders like ${spring.application.name}:${random.uuid}
     */
    private String instanceId;

    /**
     * Whether to enable error metrics collection.
     * Default: true
     */
    private boolean enableMetrics = true;

    /**
     * Whether to enable distributed tracing integration.
     * Integrates with OpenTelemetry, Zipkin, Jaeger, etc.
     * Default: true
     */
    private boolean enableDistributedTracing = true;

    /**
     * Whether to mask sensitive data in error responses.
     * Uses PII masking service if available.
     * Default: true
     */
    private boolean maskSensitiveData = true;

    /**
     * Whether to include correlation IDs in error responses.
     * Default: true
     */
    private boolean includeCorrelationId = true;

    /**
     * Whether to include request IDs in error responses.
     * Default: true
     */
    private boolean includeRequestId = true;

    /**
     * Whether to include span IDs in error responses.
     * Default: true
     */
    private boolean includeSpanId = true;

    /**
     * Whether to enable RFC 7807 Problem Details format.
     * Default: true
     */
    private boolean enableRfc7807 = true;

    /**
     * Maximum length for error messages.
     * Messages longer than this will be truncated.
     * Default: 500
     */
    private int maxMessageLength = 500;

    /**
     * Maximum length for error details.
     * Details longer than this will be truncated.
     * Default: 2000
     */
    private int maxDetailsLength = 2000;

    /**
     * Maximum number of validation errors to include in response.
     * Default: 100
     */
    private int maxValidationErrors = 100;

    /**
     * Whether to include suggestions in error responses.
     * Default: true
     */
    private boolean includeSuggestions = true;

    /**
     * Whether to include documentation links in error responses.
     * Default: true
     */
    private boolean includeDocumentation = true;

    /**
     * Whether to include help URLs in error responses.
     * Default: true
     */
    private boolean includeHelpUrl = true;

    /**
     * Default error severity for unclassified errors.
     * Default: MEDIUM
     */
    private String defaultSeverity = "MEDIUM";

    /**
     * Default error category for unclassified errors.
     * Default: UNKNOWN
     */
    private String defaultCategory = "UNKNOWN";

    /**
     * Whether to log all errors (in addition to returning them in responses).
     * Default: true
     */
    private boolean logAllErrors = true;

    /**
     * Log level for client errors (4xx).
     * Options: TRACE, DEBUG, INFO, WARN, ERROR
     * Default: WARN
     */
    private String clientErrorLogLevel = "WARN";

    /**
     * Log level for server errors (5xx).
     * Options: TRACE, DEBUG, INFO, WARN, ERROR
     * Default: ERROR
     */
    private String serverErrorLogLevel = "ERROR";

    /**
     * Whether to enable error response caching.
     * Can reduce load during error storms.
     * Default: false
     */
    private boolean enableErrorCaching = false;

    /**
     * Error cache TTL in seconds.
     * Only used if enableErrorCaching is true.
     * Default: 60
     */
    private int errorCacheTtlSeconds = 60;

    /**
     * Maximum number of cached error responses.
     * Only used if enableErrorCaching is true.
     * Default: 1000
     */
    private int errorCacheMaxSize = 1000;

    /**
     * Creates a new ErrorHandlingProperties with default values.
     */
    public ErrorHandlingProperties() {
        // Default constructor
    }
}


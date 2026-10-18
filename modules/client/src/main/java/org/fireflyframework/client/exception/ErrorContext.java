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

import org.fireflyframework.client.ClientType;
import lombok.Builder;
import lombok.Getter;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Rich context information for service client errors.
 * 
 * <p>This class captures comprehensive metadata about an error including
 * service information, request tracking, protocol details, and performance metrics.
 * It enables better debugging, monitoring, and error handling.
 *
 * @author Firefly Software Foundation
 * @since 2.0.0
 */
@Getter
@Builder(toBuilder = true)
public class ErrorContext {
    
    // Service Information
    private final String serviceName;
    private final String endpoint;
    private final String method;
    private final ClientType clientType;
    
    // Request Tracking
    private final String requestId;
    private final String correlationId;
    @Builder.Default
    private final Instant timestamp = Instant.now();
    
    // Protocol Details
    private final Integer httpStatusCode;
    private final String grpcStatusCode;
    private final String responseBody;
    @Builder.Default
    private final Map<String, String> headers = new HashMap<>();
    
    // Performance Metrics
    private final Duration elapsedTime;
    private final Integer retryAttempt;
    
    // Extensibility
    @Builder.Default
    private final Map<String, Object> additionalContext = new HashMap<>();
    
    /**
     * Creates an empty error context with just a timestamp.
     *
     * @return an empty error context
     */
    public static ErrorContext empty() {
        return ErrorContext.builder().build();
    }
    
    /**
     * Gets an unmodifiable view of the headers.
     *
     * @return unmodifiable map of headers
     */
    public Map<String, String> getHeaders() {
        return headers != null ? Collections.unmodifiableMap(headers) : Collections.emptyMap();
    }
    
    /**
     * Gets an unmodifiable view of the additional context.
     *
     * @return unmodifiable map of additional context
     */
    public Map<String, Object> getAdditionalContext() {
        return additionalContext != null ? Collections.unmodifiableMap(additionalContext) : Collections.emptyMap();
    }
    
    /**
     * Checks if this context has HTTP status code information.
     *
     * @return true if HTTP status code is present
     */
    public boolean hasHttpStatusCode() {
        return httpStatusCode != null;
    }
    
    /**
     * Checks if this context has gRPC status code information.
     *
     * @return true if gRPC status code is present
     */
    public boolean hasGrpcStatusCode() {
        return grpcStatusCode != null && !grpcStatusCode.isEmpty();
    }
    
    /**
     * Checks if this context has timing information.
     *
     * @return true if elapsed time is present
     */
    public boolean hasElapsedTime() {
        return elapsedTime != null;
    }
    
    /**
     * Checks if this is a retry attempt.
     *
     * @return true if retry attempt is greater than 0
     */
    public boolean isRetry() {
        return retryAttempt != null && retryAttempt > 0;
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("ErrorContext{");
        
        if (serviceName != null) {
            sb.append("service='").append(serviceName).append("'");
        }
        
        if (endpoint != null) {
            sb.append(", endpoint='").append(endpoint).append("'");
        }
        
        if (method != null) {
            sb.append(", method='").append(method).append("'");
        }
        
        if (clientType != null) {
            sb.append(", clientType=").append(clientType);
        }
        
        if (requestId != null) {
            sb.append(", requestId='").append(requestId).append("'");
        }
        
        if (httpStatusCode != null) {
            sb.append(", httpStatus=").append(httpStatusCode);
        }
        
        if (grpcStatusCode != null) {
            sb.append(", grpcStatus='").append(grpcStatusCode).append("'");
        }
        
        if (elapsedTime != null) {
            sb.append(", duration=").append(elapsedTime.toMillis()).append("ms");
        }
        
        if (retryAttempt != null && retryAttempt > 0) {
            sb.append(", retry=").append(retryAttempt);
        }
        
        sb.append('}');
        return sb.toString();
    }
}


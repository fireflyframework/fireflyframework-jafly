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


package org.fireflyframework.web.error.exceptions;

import org.springframework.http.HttpStatus;

import java.util.HashMap;
import java.util.Map;

/**
 * Exception thrown when a service is temporarily unavailable.
 * Results in a 503 SERVICE UNAVAILABLE response.
 */
public class ServiceUnavailableException extends BusinessException {

    /**
     * The name of the service that is unavailable.
     */
    private final String serviceName;

    /**
     * The retry-after time in seconds, if available.
     */
    private final Integer retryAfterSeconds;

    /**
     * The reason why the service is unavailable.
     */
    private final String reason;

    /**
     * Creates a new ServiceUnavailableException with the given message.
     *
     * @param message the error message
     * @param serviceName the name of the service that is unavailable
     */
    public ServiceUnavailableException(String message, String serviceName) {
        this(message, serviceName, null, null);
    }

    /**
     * Creates a new ServiceUnavailableException with the given message and retry-after time.
     *
     * @param message the error message
     * @param serviceName the name of the service that is unavailable
     * @param retryAfterSeconds the retry-after time in seconds
     */
    public ServiceUnavailableException(String message, String serviceName, Integer retryAfterSeconds) {
        this(message, serviceName, retryAfterSeconds, null);
    }

    /**
     * Creates a new ServiceUnavailableException with the given message, retry-after time, and reason.
     *
     * @param message the error message
     * @param serviceName the name of the service that is unavailable
     * @param retryAfterSeconds the retry-after time in seconds
     * @param reason the reason why the service is unavailable
     */
    public ServiceUnavailableException(String message, String serviceName, Integer retryAfterSeconds, String reason) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE", message);
        this.serviceName = serviceName;
        this.retryAfterSeconds = retryAfterSeconds;
        this.reason = reason;
    }

    @Override
    public ServiceUnavailableException withMetadata(String key, Object value) {
        Map<String, Object> newMetadata = new HashMap<>(getMetadata());
        newMetadata.put(key, value);
        return new ServiceUnavailableException(
            getStatus(),
            getCode(),
            getMessage(),
            getCause(),
            newMetadata,
            serviceName,
            retryAfterSeconds,
            reason
        );
    }

    @Override
    public ServiceUnavailableException withMetadata(Map<String, Object> metadata) {
        Map<String, Object> newMetadata = new HashMap<>(getMetadata());
        newMetadata.putAll(metadata);
        return new ServiceUnavailableException(
            getStatus(),
            getCode(),
            getMessage(),
            getCause(),
            newMetadata,
            serviceName,
            retryAfterSeconds,
            reason
        );
    }

    /**
     * Creates a new ServiceUnavailableException with a code, message, retry-after time, and reason.
     *
     * @param code the error code
     * @param message the error message
     * @param serviceName the name of the service that is unavailable
     * @param retryAfterSeconds the retry-after time in seconds
     * @param reason the reason why the service is unavailable
     */
    public ServiceUnavailableException(String code, String message, String serviceName, Integer retryAfterSeconds, String reason) {
        super(HttpStatus.SERVICE_UNAVAILABLE, code, message);
        this.serviceName = serviceName;
        this.retryAfterSeconds = retryAfterSeconds;
        this.reason = reason;
    }

    /**
     * Creates a new ServiceUnavailableException with all parameters.
     *
     * @param status the HTTP status code
     * @param code the error code
     * @param message the error message
     * @param cause the cause of this exception
     * @param metadata additional metadata about the error
     * @param serviceName the name of the service that is unavailable
     * @param retryAfterSeconds the retry-after time in seconds
     * @param reason the reason why the service is unavailable
     */
    public ServiceUnavailableException(HttpStatus status, String code, String message, Throwable cause,
                                      Map<String, Object> metadata, String serviceName,
                                      Integer retryAfterSeconds, String reason) {
        super(status, code, message, cause, metadata);
        this.serviceName = serviceName;
        this.retryAfterSeconds = retryAfterSeconds;
        this.reason = reason;
    }

    /**
     * Returns the name of the service that is unavailable.
     *
     * @return the service name
     */
    public String getServiceName() {
        return serviceName;
    }

    /**
     * Returns the retry-after time in seconds, if available.
     *
     * @return the retry-after time in seconds, or null if not specified
     */
    public Integer getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    /**
     * Returns the reason why the service is unavailable.
     *
     * @return the reason, or null if not specified
     */
    public String getReason() {
        return reason;
    }

    /**
     * Creates a new ServiceUnavailableException for a service.
     *
     * @param serviceName the name of the service that is unavailable
     * @return a new ServiceUnavailableException
     */
    public static ServiceUnavailableException forService(String serviceName) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("serviceName", serviceName);

        ServiceUnavailableException exception = new ServiceUnavailableException(
                String.format("Service '%s' is currently unavailable", serviceName),
                serviceName
        );

        // Manually copy metadata
        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            exception = (ServiceUnavailableException) exception.withMetadata(entry.getKey(), entry.getValue());
        }

        return exception;
    }

    /**
     * Creates a new ServiceUnavailableException for a service with a retry-after time.
     *
     * @param serviceName the name of the service that is unavailable
     * @param retryAfterSeconds the retry-after time in seconds
     * @return a new ServiceUnavailableException
     */
    public static ServiceUnavailableException forServiceWithRetry(String serviceName, int retryAfterSeconds) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("serviceName", serviceName);
        metadata.put("retryAfterSeconds", retryAfterSeconds);

        ServiceUnavailableException exception = new ServiceUnavailableException(
                String.format("Service '%s' is currently unavailable. Please try again in %d seconds",
                        serviceName, retryAfterSeconds),
                serviceName,
                retryAfterSeconds
        );

        // Manually copy metadata
        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            exception = (ServiceUnavailableException) exception.withMetadata(entry.getKey(), entry.getValue());
        }

        return exception;
    }

    /**
     * Creates a new ServiceUnavailableException for a service that is under maintenance.
     *
     * @param serviceName the name of the service that is unavailable
     * @param retryAfterSeconds the retry-after time in seconds
     * @return a new ServiceUnavailableException
     */
    public static ServiceUnavailableException forMaintenance(String serviceName, int retryAfterSeconds) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("serviceName", serviceName);
        metadata.put("retryAfterSeconds", retryAfterSeconds);
        metadata.put("reason", "maintenance");

        ServiceUnavailableException exception = new ServiceUnavailableException(
                "MAINTENANCE",
                String.format("Service '%s' is currently undergoing maintenance. Please try again in %d seconds",
                        serviceName, retryAfterSeconds),
                serviceName,
                retryAfterSeconds,
                "maintenance"
        );

        // Manually copy metadata
        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            exception = (ServiceUnavailableException) exception.withMetadata(entry.getKey(), entry.getValue());
        }

        return exception;
    }

    /**
     * Creates a new ServiceUnavailableException for a service that is overloaded.
     *
     * @param serviceName the name of the service that is unavailable
     * @param retryAfterSeconds the retry-after time in seconds
     * @return a new ServiceUnavailableException
     */
    public static ServiceUnavailableException forOverload(String serviceName, int retryAfterSeconds) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("serviceName", serviceName);
        metadata.put("retryAfterSeconds", retryAfterSeconds);
        metadata.put("reason", "overload");

        ServiceUnavailableException exception = new ServiceUnavailableException(
                "OVERLOAD",
                String.format("Service '%s' is currently overloaded. Please try again in %d seconds",
                        serviceName, retryAfterSeconds),
                serviceName,
                retryAfterSeconds,
                "overload"
        );

        // Manually copy metadata
        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            exception = (ServiceUnavailableException) exception.withMetadata(entry.getKey(), entry.getValue());
        }

        return exception;
    }
}

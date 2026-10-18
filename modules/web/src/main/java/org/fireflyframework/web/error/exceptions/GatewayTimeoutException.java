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
 * Exception thrown when a request to an upstream server times out.
 * Results in a 504 GATEWAY TIMEOUT response.
 */
public class GatewayTimeoutException extends BusinessException {

    /**
     * The name of the upstream server that timed out.
     */
    private final String serverName;

    /**
     * The URL of the upstream server that timed out.
     */
    private final String serverUrl;

    /**
     * The timeout in milliseconds.
     */
    private final long timeoutMillis;

    /**
     * Creates a new GatewayTimeoutException with the given message.
     *
     * @param message the error message
     * @param serverName the name of the upstream server that timed out
     * @param serverUrl the URL of the upstream server that timed out
     * @param timeoutMillis the timeout in milliseconds
     */
    public GatewayTimeoutException(String message, String serverName, String serverUrl, long timeoutMillis) {
        super(HttpStatus.GATEWAY_TIMEOUT, "GATEWAY_TIMEOUT", message);
        this.serverName = serverName;
        this.serverUrl = serverUrl;
        this.timeoutMillis = timeoutMillis;
    }

    @Override
    public GatewayTimeoutException withMetadata(String key, Object value) {
        Map<String, Object> newMetadata = new HashMap<>(getMetadata());
        newMetadata.put(key, value);
        return new GatewayTimeoutException(
            getStatus(),
            getCode(),
            getMessage(),
            getCause(),
            newMetadata,
            serverName,
            serverUrl,
            timeoutMillis
        );
    }

    @Override
    public GatewayTimeoutException withMetadata(Map<String, Object> metadata) {
        Map<String, Object> newMetadata = new HashMap<>(getMetadata());
        newMetadata.putAll(metadata);
        return new GatewayTimeoutException(
            getStatus(),
            getCode(),
            getMessage(),
            getCause(),
            newMetadata,
            serverName,
            serverUrl,
            timeoutMillis
        );
    }

    /**
     * Creates a new GatewayTimeoutException with a code and message.
     *
     * @param code the error code
     * @param message the error message
     * @param serverName the name of the upstream server that timed out
     * @param serverUrl the URL of the upstream server that timed out
     * @param timeoutMillis the timeout in milliseconds
     */
    public GatewayTimeoutException(String code, String message, String serverName, String serverUrl, long timeoutMillis) {
        super(HttpStatus.GATEWAY_TIMEOUT, code, message);
        this.serverName = serverName;
        this.serverUrl = serverUrl;
        this.timeoutMillis = timeoutMillis;
    }

    /**
     * Creates a new GatewayTimeoutException with all parameters.
     *
     * @param status the HTTP status code
     * @param code the error code
     * @param message the error message
     * @param cause the cause of this exception
     * @param metadata additional metadata about the error
     * @param serverName the name of the upstream server that timed out
     * @param serverUrl the URL of the upstream server that timed out
     * @param timeoutMillis the timeout in milliseconds
     */
    public GatewayTimeoutException(HttpStatus status, String code, String message, Throwable cause,
                                  Map<String, Object> metadata, String serverName, String serverUrl,
                                  long timeoutMillis) {
        super(status, code, message, cause, metadata);
        this.serverName = serverName;
        this.serverUrl = serverUrl;
        this.timeoutMillis = timeoutMillis;
    }

    /**
     * Returns the name of the upstream server that timed out.
     *
     * @return the server name
     */
    public String getServerName() {
        return serverName;
    }

    /**
     * Returns the URL of the upstream server that timed out.
     *
     * @return the server URL
     */
    public String getServerUrl() {
        return serverUrl;
    }

    /**
     * Returns the timeout in milliseconds.
     *
     * @return the timeout in milliseconds
     */
    public long getTimeoutMillis() {
        return timeoutMillis;
    }

    /**
     * Creates a new GatewayTimeoutException for an upstream server.
     *
     * @param serverName the name of the upstream server that timed out
     * @param serverUrl the URL of the upstream server that timed out
     * @param timeoutMillis the timeout in milliseconds
     * @return a new GatewayTimeoutException
     */
    public static GatewayTimeoutException forServer(String serverName, String serverUrl, long timeoutMillis) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("serverName", serverName);
        metadata.put("serverUrl", serverUrl);
        metadata.put("timeoutMillis", timeoutMillis);

        GatewayTimeoutException exception = new GatewayTimeoutException(
                String.format("Request to upstream server '%s' timed out after %d ms",
                        serverName, timeoutMillis),
                serverName,
                serverUrl,
                timeoutMillis
        );

        // Manually copy metadata
        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            exception = (GatewayTimeoutException) exception.withMetadata(entry.getKey(), entry.getValue());
        }

        return exception;
    }

    /**
     * Creates a new GatewayTimeoutException for an upstream server with a specific operation.
     *
     * @param serverName the name of the upstream server that timed out
     * @param serverUrl the URL of the upstream server that timed out
     * @param operation the operation that timed out
     * @param timeoutMillis the timeout in milliseconds
     * @return a new GatewayTimeoutException
     */
    public static GatewayTimeoutException forOperation(String serverName, String serverUrl, String operation, long timeoutMillis) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("serverName", serverName);
        metadata.put("serverUrl", serverUrl);
        metadata.put("operation", operation);
        metadata.put("timeoutMillis", timeoutMillis);

        GatewayTimeoutException exception = new GatewayTimeoutException(
                "OPERATION_TIMEOUT",
                String.format("Operation '%s' on upstream server '%s' timed out after %d ms",
                        operation, serverName, timeoutMillis),
                serverName,
                serverUrl,
                timeoutMillis
        );

        // Manually copy metadata
        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            exception = (GatewayTimeoutException) exception.withMetadata(entry.getKey(), entry.getValue());
        }

        return exception;
    }

    /**
     * Creates a new GatewayTimeoutException for a connection timeout.
     *
     * @param serverName the name of the upstream server that timed out
     * @param serverUrl the URL of the upstream server that timed out
     * @param timeoutMillis the timeout in milliseconds
     * @return a new GatewayTimeoutException
     */
    public static GatewayTimeoutException forConnectionTimeout(String serverName, String serverUrl, long timeoutMillis) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("serverName", serverName);
        metadata.put("serverUrl", serverUrl);
        metadata.put("timeoutMillis", timeoutMillis);
        metadata.put("timeoutType", "connection");

        GatewayTimeoutException exception = new GatewayTimeoutException(
                "CONNECTION_TIMEOUT",
                String.format("Connection to upstream server '%s' timed out after %d ms",
                        serverName, timeoutMillis),
                serverName,
                serverUrl,
                timeoutMillis
        );

        // Manually copy metadata
        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            exception = (GatewayTimeoutException) exception.withMetadata(entry.getKey(), entry.getValue());
        }

        return exception;
    }

    /**
     * Creates a new GatewayTimeoutException for a read timeout.
     *
     * @param serverName the name of the upstream server that timed out
     * @param serverUrl the URL of the upstream server that timed out
     * @param timeoutMillis the timeout in milliseconds
     * @return a new GatewayTimeoutException
     */
    public static GatewayTimeoutException forReadTimeout(String serverName, String serverUrl, long timeoutMillis) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("serverName", serverName);
        metadata.put("serverUrl", serverUrl);
        metadata.put("timeoutMillis", timeoutMillis);
        metadata.put("timeoutType", "read");

        GatewayTimeoutException exception = new GatewayTimeoutException(
                "READ_TIMEOUT",
                String.format("Read from upstream server '%s' timed out after %d ms",
                        serverName, timeoutMillis),
                serverName,
                serverUrl,
                timeoutMillis
        );

        // Manually copy metadata
        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            exception = (GatewayTimeoutException) exception.withMetadata(entry.getKey(), entry.getValue());
        }

        return exception;
    }
}

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
 * Exception thrown when a request to an upstream server fails.
 * Results in a 502 BAD GATEWAY response.
 * This is more specific than ThirdPartyServiceException and provides more detailed information
 * about the upstream server that failed.
 */
public class BadGatewayException extends BusinessException {

    /**
     * The name of the upstream server that failed.
     */
    private final String serverName;

    /**
     * The URL of the upstream server that failed.
     */
    private final String serverUrl;

    /**
     * The error code returned by the upstream server.
     */
    private final String serverErrorCode;

    /**
     * Creates a new BadGatewayException with the given message.
     *
     * @param message the error message
     * @param serverName the name of the upstream server that failed
     * @param serverUrl the URL of the upstream server that failed
     * @param serverErrorCode the error code returned by the upstream server
     */
    public BadGatewayException(String message, String serverName, String serverUrl, String serverErrorCode) {
        super(HttpStatus.BAD_GATEWAY, "BAD_GATEWAY", message);
        this.serverName = serverName;
        this.serverUrl = serverUrl;
        this.serverErrorCode = serverErrorCode;
    }

    @Override
    public BadGatewayException withMetadata(String key, Object value) {
        Map<String, Object> newMetadata = new HashMap<>(getMetadata());
        newMetadata.put(key, value);
        BadGatewayException exception = new BadGatewayException(getCode(), getMessage(), serverName, serverUrl, serverErrorCode);
        // Apply all metadata to the new instance
        for (Map.Entry<String, Object> entry : newMetadata.entrySet()) {
            // Use super's implementation to avoid infinite recursion
            exception = new BadGatewayException(
                exception.getStatus(),
                exception.getCode(),
                exception.getMessage(),
                exception.getCause(),
                newMetadata,
                exception.getServerName(),
                exception.getServerUrl(),
                exception.getServerErrorCode()
            );
            break; // We've added all metadata at once, so break
        }
        return exception;
    }

    @Override
    public BadGatewayException withMetadata(Map<String, Object> metadata) {
        Map<String, Object> newMetadata = new HashMap<>(getMetadata());
        newMetadata.putAll(metadata);
        return new BadGatewayException(
            getStatus(),
            getCode(),
            getMessage(),
            getCause(),
            newMetadata,
            serverName,
            serverUrl,
            serverErrorCode
        );
    }

    /**
     * Creates a new BadGatewayException with a code and message.
     *
     * @param code the error code
     * @param message the error message
     * @param serverName the name of the upstream server that failed
     * @param serverUrl the URL of the upstream server that failed
     * @param serverErrorCode the error code returned by the upstream server
     */
    public BadGatewayException(String code, String message, String serverName, String serverUrl, String serverErrorCode) {
        super(HttpStatus.BAD_GATEWAY, code, message);
        this.serverName = serverName;
        this.serverUrl = serverUrl;
        this.serverErrorCode = serverErrorCode;
    }

    /**
     * Creates a new BadGatewayException with all parameters.
     *
     * @param status the HTTP status code
     * @param code the error code
     * @param message the error message
     * @param cause the cause of this exception
     * @param metadata additional metadata about the error
     * @param serverName the name of the upstream server that failed
     * @param serverUrl the URL of the upstream server that failed
     * @param serverErrorCode the error code returned by the upstream server
     */
    public BadGatewayException(HttpStatus status, String code, String message, Throwable cause,
                              Map<String, Object> metadata, String serverName, String serverUrl,
                              String serverErrorCode) {
        super(status, code, message, cause, metadata);
        this.serverName = serverName;
        this.serverUrl = serverUrl;
        this.serverErrorCode = serverErrorCode;
    }

    /**
     * Returns the name of the upstream server that failed.
     *
     * @return the server name
     */
    public String getServerName() {
        return serverName;
    }

    /**
     * Returns the URL of the upstream server that failed.
     *
     * @return the server URL
     */
    public String getServerUrl() {
        return serverUrl;
    }

    /**
     * Returns the error code returned by the upstream server.
     *
     * @return the server error code
     */
    public String getServerErrorCode() {
        return serverErrorCode;
    }

    /**
     * Creates a new BadGatewayException for an upstream server.
     *
     * @param serverName the name of the upstream server that failed
     * @param serverUrl the URL of the upstream server that failed
     * @param serverErrorCode the error code returned by the upstream server
     * @return a new BadGatewayException
     */
    public static BadGatewayException forServer(String serverName, String serverUrl, String serverErrorCode) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("serverName", serverName);
        metadata.put("serverUrl", serverUrl);
        metadata.put("serverErrorCode", serverErrorCode);

        BadGatewayException exception = new BadGatewayException(
                String.format("Request to upstream server '%s' failed with error code '%s'",
                        serverName, serverErrorCode),
                serverName,
                serverUrl,
                serverErrorCode
        );

        // Manually copy metadata
        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            exception = (BadGatewayException) exception.withMetadata(entry.getKey(), entry.getValue());
        }

        return exception;
    }

    /**
     * Creates a new BadGatewayException for an upstream server with a specific error message.
     *
     * @param serverName the name of the upstream server that failed
     * @param serverUrl the URL of the upstream server that failed
     * @param serverErrorCode the error code returned by the upstream server
     * @param errorMessage the error message returned by the upstream server
     * @return a new BadGatewayException
     */
    public static BadGatewayException forServerWithMessage(String serverName, String serverUrl, String serverErrorCode, String errorMessage) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("serverName", serverName);
        metadata.put("serverUrl", serverUrl);
        metadata.put("serverErrorCode", serverErrorCode);
        metadata.put("errorMessage", errorMessage);

        BadGatewayException exception = new BadGatewayException(
                String.format("Request to upstream server '%s' failed with error code '%s': %s",
                        serverName, serverErrorCode, errorMessage),
                serverName,
                serverUrl,
                serverErrorCode
        );

        // Manually copy metadata
        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            exception = (BadGatewayException) exception.withMetadata(entry.getKey(), entry.getValue());
        }

        return exception;
    }

    /**
     * Creates a new BadGatewayException for an invalid response from an upstream server.
     *
     * @param serverName the name of the upstream server that failed
     * @param serverUrl the URL of the upstream server that failed
     * @param responseStatus the HTTP status code returned by the upstream server
     * @return a new BadGatewayException
     */
    public static BadGatewayException forInvalidResponse(String serverName, String serverUrl, int responseStatus) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("serverName", serverName);
        metadata.put("serverUrl", serverUrl);
        metadata.put("responseStatus", responseStatus);

        BadGatewayException exception = new BadGatewayException(
                "INVALID_UPSTREAM_RESPONSE",
                String.format("Upstream server '%s' returned an invalid response with status code %d",
                        serverName, responseStatus),
                serverName,
                serverUrl,
                String.valueOf(responseStatus)
        );

        // Manually copy metadata
        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            exception = (BadGatewayException) exception.withMetadata(entry.getKey(), entry.getValue());
        }

        return exception;
    }
}

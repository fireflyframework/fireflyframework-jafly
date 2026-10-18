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

/**
 * Exception thrown when WSDL parsing or service initialization fails.
 *
 * <p>This exception indicates problems with WSDL retrieval, parsing,
 * or service port initialization. It typically occurs during client
 * construction rather than during request execution.
 *
 * <p>This error is NOT retryable as it indicates a configuration problem.
 *
 * @author Firefly Software Foundation
 * @since 2.0.0
 */
public class WsdlParsingException extends ServiceClientException {

    /**
     * Creates a new WSDL parsing exception.
     *
     * @param message the error message
     */
    public WsdlParsingException(String message) {
        super(message);
    }

    /**
     * Creates a new WSDL parsing exception with a cause.
     *
     * @param message the error message
     * @param cause the underlying cause
     */
    public WsdlParsingException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates a new WSDL parsing exception from a cause.
     *
     * @param cause the underlying cause
     */
    public WsdlParsingException(Throwable cause) {
        super("Failed to parse WSDL: " + cause.getMessage(), cause);
    }

    /**
     * Creates a new WSDL parsing exception with error context.
     *
     * @param message the error message
     * @param errorContext rich context information about the error
     */
    public WsdlParsingException(String message, ErrorContext errorContext) {
        super(message, errorContext);
    }

    /**
     * Creates a new WSDL parsing exception with error context and cause.
     *
     * @param message the error message
     * @param errorContext rich context information about the error
     * @param cause the underlying cause
     */
    public WsdlParsingException(String message, ErrorContext errorContext, Throwable cause) {
        super(message, errorContext, cause);
    }

    @Override
    public ErrorCategory getErrorCategory() {
        return ErrorCategory.CONFIGURATION_ERROR;
    }
}


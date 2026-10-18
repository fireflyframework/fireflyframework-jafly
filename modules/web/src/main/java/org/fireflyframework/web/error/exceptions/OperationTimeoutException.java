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

/**
 * Exception thrown when an operation times out.
 * Results in a 408 REQUEST TIMEOUT response.
 */
public class OperationTimeoutException extends BusinessException {
    
    /**
     * The operation that timed out.
     */
    private final String operation;
    
    /**
     * The timeout in milliseconds.
     */
    private final long timeoutMillis;
    
    /**
     * Creates a new OperationTimeoutException with the given message.
     *
     * @param message the error message
     * @param operation the operation that timed out
     * @param timeoutMillis the timeout in milliseconds
     */
    public OperationTimeoutException(String message, String operation, long timeoutMillis) {
        super(HttpStatus.REQUEST_TIMEOUT, "OPERATION_TIMEOUT", message);
        this.operation = operation;
        this.timeoutMillis = timeoutMillis;
    }
    
    /**
     * Creates a new OperationTimeoutException with a code and message.
     *
     * @param code the error code
     * @param message the error message
     * @param operation the operation that timed out
     * @param timeoutMillis the timeout in milliseconds
     */
    public OperationTimeoutException(String code, String message, String operation, long timeoutMillis) {
        super(HttpStatus.REQUEST_TIMEOUT, code, message);
        this.operation = operation;
        this.timeoutMillis = timeoutMillis;
    }
    
    /**
     * Returns the operation that timed out.
     *
     * @return the operation
     */
    public String getOperation() {
        return operation;
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
     * Creates a new OperationTimeoutException for a database operation.
     *
     * @param operation the database operation that timed out
     * @param timeoutMillis the timeout in milliseconds
     * @return a new OperationTimeoutException
     */
    public static OperationTimeoutException databaseTimeout(String operation, long timeoutMillis) {
        return new OperationTimeoutException(
                "DATABASE_TIMEOUT",
                String.format("Database operation '%s' timed out after %d ms", operation, timeoutMillis),
                operation,
                timeoutMillis
        );
    }
    
    /**
     * Creates a new OperationTimeoutException for a service call.
     *
     * @param serviceName the name of the service
     * @param operation the operation that timed out
     * @param timeoutMillis the timeout in milliseconds
     * @return a new OperationTimeoutException
     */
    public static OperationTimeoutException serviceCallTimeout(String serviceName, String operation, long timeoutMillis) {
        return new OperationTimeoutException(
                "SERVICE_CALL_TIMEOUT",
                String.format("Call to service '%s' operation '%s' timed out after %d ms", serviceName, operation, timeoutMillis),
                operation,
                timeoutMillis
        );
    }
}

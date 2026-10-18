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
 * Exception thrown when a service operation fails.
 * Results in a 500 INTERNAL SERVER ERROR response.
 */
public class ServiceException extends BusinessException {
    
    /**
     * Creates a new ServiceException with the given message.
     *
     * @param message the error message
     */
    public ServiceException(String message) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, message);
    }
    
    /**
     * Creates a new ServiceException with a code and message.
     *
     * @param code the error code
     * @param message the error message
     */
    public ServiceException(String code, String message) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, code, message);
    }
    
    /**
     * Creates a new ServiceException with a cause.
     *
     * @param message the error message
     * @param cause the cause of the exception
     * @return a new ServiceException
     */
    public static ServiceException withCause(String message, Throwable cause) {
        ServiceException exception = new ServiceException("SERVICE_ERROR", message);
        exception.initCause(cause);
        return exception;
    }
    
    /**
     * Creates a new ServiceException for a dependency failure.
     *
     * @param dependencyName the name of the dependency that failed
     * @param reason the reason for the failure
     * @return a new ServiceException
     */
    public static ServiceException dependencyFailure(String dependencyName, String reason) {
        return new ServiceException(
                "DEPENDENCY_FAILURE",
                String.format("Dependency '%s' failed: %s", dependencyName, reason)
        );
    }
}

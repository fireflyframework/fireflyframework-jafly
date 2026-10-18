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
 * Exception thrown when a third-party service call fails.
 * Results in a 502 BAD GATEWAY response.
 */
public class ThirdPartyServiceException extends BusinessException {
    
    /**
     * The name of the third-party service that failed.
     */
    private final String serviceName;
    
    /**
     * Creates a new ThirdPartyServiceException with the given message.
     *
     * @param message the error message
     * @param serviceName the name of the third-party service
     */
    public ThirdPartyServiceException(String message, String serviceName) {
        super(HttpStatus.BAD_GATEWAY, "THIRD_PARTY_SERVICE_ERROR", message);
        this.serviceName = serviceName;
    }
    
    /**
     * Creates a new ThirdPartyServiceException with a code and message.
     *
     * @param code the error code
     * @param message the error message
     * @param serviceName the name of the third-party service
     */
    public ThirdPartyServiceException(String code, String message, String serviceName) {
        super(HttpStatus.BAD_GATEWAY, code, message);
        this.serviceName = serviceName;
    }
    
    /**
     * Returns the name of the third-party service that failed.
     *
     * @return the service name
     */
    public String getServiceName() {
        return serviceName;
    }
    
    /**
     * Creates a new ThirdPartyServiceException for a service that is unavailable.
     *
     * @param serviceName the name of the third-party service
     * @return a new ThirdPartyServiceException
     */
    public static ThirdPartyServiceException serviceUnavailable(String serviceName) {
        return new ThirdPartyServiceException(
                "SERVICE_UNAVAILABLE",
                String.format("The service '%s' is currently unavailable", serviceName),
                serviceName
        );
    }
    
    /**
     * Creates a new ThirdPartyServiceException for a service that returned an error.
     *
     * @param serviceName the name of the third-party service
     * @param errorCode the error code returned by the service
     * @param errorMessage the error message returned by the service
     * @return a new ThirdPartyServiceException
     */
    public static ThirdPartyServiceException serviceError(String serviceName, String errorCode, String errorMessage) {
        return new ThirdPartyServiceException(
                "SERVICE_ERROR_" + errorCode,
                String.format("The service '%s' returned an error: %s", serviceName, errorMessage),
                serviceName
        );
    }
}

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


package org.fireflyframework.web.error.converter;

import org.fireflyframework.web.error.exceptions.BusinessException;
import org.fireflyframework.web.error.exceptions.OperationTimeoutException;
import org.fireflyframework.web.error.exceptions.ThirdPartyServiceException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

/**
 * Converter for network-related exceptions.
 * Converts network exceptions to appropriate business exceptions.
 */
@Component
@ConditionalOnClass(Exception.class)
public class NetworkExceptionConverter implements ExceptionConverter<Exception> {

    /**
     * Creates a new NetworkExceptionConverter.
     */
    public NetworkExceptionConverter() {
        // Default constructor
    }

    @Override
    public Class<Exception> getExceptionType() {
        return Exception.class;
    }

    @Override
    public boolean canHandle(Throwable exception) {
        return exception instanceof ConnectException ||
               exception instanceof SocketTimeoutException ||
               exception instanceof UnknownHostException;
    }

    @Override
    public BusinessException convert(Exception exception) {
        if (exception instanceof SocketTimeoutException) {
            return OperationTimeoutException.serviceCallTimeout("unknown", "network-call", 0);
        } else if (exception instanceof ConnectException) {
            return ThirdPartyServiceException.serviceUnavailable("unknown");
        } else if (exception instanceof UnknownHostException) {
            return ThirdPartyServiceException.serviceUnavailable("unknown-host");
        }

        // Fallback
        return ThirdPartyServiceException.serviceError("network", "NETWORK_ERROR", exception.getMessage());
    }
}

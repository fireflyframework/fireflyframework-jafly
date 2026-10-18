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
import org.fireflyframework.web.error.exceptions.ServiceException;
import org.fireflyframework.web.error.exceptions.ThirdPartyServiceException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

/**
 * Converter for Spring's HttpServerErrorException.
 * Converts HTTP server error exceptions to the appropriate business exceptions.
 */
@Component
@ConditionalOnClass(HttpServerErrorException.class)
public class HttpServerErrorExceptionConverter implements ExceptionConverter<HttpServerErrorException> {

    /**
     * Creates a new HttpServerErrorExceptionConverter.
     */
    public HttpServerErrorExceptionConverter() {
        // Default constructor
    }

    @Override
    public Class<HttpServerErrorException> getExceptionType() {
        return HttpServerErrorException.class;
    }

    @Override
    public BusinessException convert(HttpServerErrorException exception) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        String responseBody = exception.getResponseBodyAsString();

        switch (status) {
            case GATEWAY_TIMEOUT:
                return new OperationTimeoutException(
                        "GATEWAY_TIMEOUT",
                        "Gateway timeout: " + responseBody,
                        "http-request",
                        0
                );
            case BAD_GATEWAY:
                return new ThirdPartyServiceException(
                        "BAD_GATEWAY",
                        "Bad gateway: " + responseBody,
                        "unknown"
                );
            case SERVICE_UNAVAILABLE:
                return ThirdPartyServiceException.serviceUnavailable("unknown");
            default:
                return new ServiceException(
                        "SERVER_ERROR",
                        "Server error: " + responseBody
                );
        }
    }
}

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
import org.fireflyframework.web.error.exceptions.ServiceException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for converting standard exceptions to business exceptions.
 * This service uses a list of exception converters to convert exceptions
 * to the appropriate business exception.
 */
@Service
public class ExceptionConverterService {

    private final List<ExceptionConverter<?>> converters;

    /**
     * Creates a new ExceptionConverterService with the given converters.
     *
     * @param converters the list of exception converters
     */
    public ExceptionConverterService(List<ExceptionConverter<?>> converters) {
        this.converters = converters;
    }

    /**
     * Converts the given exception to a business exception.
     * If the exception is already a business exception, it is returned as is.
     * If no converter can handle the exception, a generic ServiceException is returned.
     *
     * @param exception the exception to convert
     * @return the converted business exception
     */
    public BusinessException convertException(Throwable exception) {
        // If the exception is already a business exception, return it
        if (exception instanceof BusinessException) {
            return (BusinessException) exception;
        }

        // Try to find a converter that can handle the exception
        for (ExceptionConverter<?> converter : converters) {
            if (converter.canHandle(exception)) {
                return convertWithConverter(converter, exception);
            }
        }

        // If no converter can handle the exception, return a generic service exception
        return new ServiceException(
                "UNEXPECTED_ERROR",
                "An unexpected error occurred: " + exception.getMessage()
        );
    }

    @SuppressWarnings("unchecked")
    private <T extends Throwable> BusinessException convertWithConverter(ExceptionConverter<T> converter, Throwable exception) {
        return converter.convert((T) exception);
    }
}

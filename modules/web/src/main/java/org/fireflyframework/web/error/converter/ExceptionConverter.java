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

/**
 * Interface for converting standard exceptions to business exceptions.
 * Implementations of this interface can convert specific exception types
 * to the appropriate business exception.
 *
 * @param <T> the type of exception that this converter can handle
 */
public interface ExceptionConverter<T extends Throwable> {
    
    /**
     * Returns the exception type that this converter can handle.
     *
     * @return the exception class
     */
    Class<T> getExceptionType();
    
    /**
     * Converts the given exception to a business exception.
     *
     * @param exception the exception to convert
     * @return the converted business exception
     */
    BusinessException convert(T exception);
    
    /**
     * Returns whether this converter can handle the given exception.
     *
     * @param exception the exception to check
     * @return true if this converter can handle the exception, false otherwise
     */
    default boolean canHandle(Throwable exception) {
        return getExceptionType().isInstance(exception);
    }
}

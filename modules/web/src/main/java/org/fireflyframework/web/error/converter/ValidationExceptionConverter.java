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
import org.fireflyframework.web.error.exceptions.ValidationException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.support.WebExchangeBindException;

/**
 * Converter for Spring's validation exceptions.
 * Converts validation exceptions to ValidationException.
 */
@Component
@ConditionalOnClass(Exception.class)
public class ValidationExceptionConverter implements ExceptionConverter<Exception> {

    /**
     * Creates a new ValidationExceptionConverter.
     */
    public ValidationExceptionConverter() {
        // Default constructor
    }

    @Override
    public Class<Exception> getExceptionType() {
        return Exception.class;
    }

    @Override
    public boolean canHandle(Throwable exception) {
        return exception instanceof MethodArgumentNotValidException ||
               exception instanceof BindException ||
               exception instanceof WebExchangeBindException;
    }

    @Override
    public BusinessException convert(Exception exception) {
        BindingResult bindingResult = null;

        if (exception instanceof MethodArgumentNotValidException) {
            bindingResult = ((MethodArgumentNotValidException) exception).getBindingResult();
        } else if (exception instanceof BindException) {
            bindingResult = ((BindException) exception);
        } else if (exception instanceof WebExchangeBindException) {
            bindingResult = ((WebExchangeBindException) exception);
        }

        if (bindingResult != null) {
            ValidationException.Builder builder = new ValidationException.Builder();

            for (FieldError fieldError : bindingResult.getFieldErrors()) {
                builder.addError(fieldError.getField(), fieldError.getDefaultMessage());
            }

            return builder.build();
        }

        // Fallback
        return new ValidationException("Validation failed");
    }
}

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
import org.fireflyframework.web.error.exceptions.DataIntegrityException;
import org.fireflyframework.web.error.exceptions.ResourceNotFoundException;
import org.fireflyframework.web.error.exceptions.ServiceException;
import jakarta.persistence.EntityExistsException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.QueryTimeoutException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;

/**
 * Converter for JPA exceptions.
 * Converts JPA exceptions to appropriate business exceptions.
 */
@Component
@ConditionalOnClass(Exception.class)
public class JpaExceptionConverter implements ExceptionConverter<Exception> {

    /**
     * Creates a new JpaExceptionConverter.
     */
    public JpaExceptionConverter() {
        // Default constructor
    }

    @Override
    public Class<Exception> getExceptionType() {
        return Exception.class;
    }

    @Override
    public boolean canHandle(Throwable exception) {
        return exception instanceof PersistenceException ||
               exception instanceof EntityNotFoundException ||
               exception instanceof NoResultException;
    }

    @Override
    public BusinessException convert(Exception exception) {
        if (exception instanceof EntityNotFoundException || exception instanceof NoResultException) {
            return ResourceNotFoundException.withReason("ENTITY_NOT_FOUND", exception.getMessage());
        } else if (exception instanceof EntityExistsException) {
            return DataIntegrityException.uniqueConstraintViolation("entity", "duplicate");
        } else if (exception instanceof QueryTimeoutException) {
            return org.fireflyframework.web.error.exceptions.OperationTimeoutException.databaseTimeout("jpa-query", 0);
        } else if (exception instanceof PersistenceException) {
            // Try to extract more information from the message
            String message = exception.getMessage();
            if (message != null) {
                if (message.contains("unique constraint") || message.contains("duplicate")) {
                    return DataIntegrityException.uniqueConstraintViolation("unknown", message);
                } else if (message.contains("foreign key constraint")) {
                    return DataIntegrityException.foreignKeyConstraintViolation("unknown", "unknown", "unknown");
                }
            }
            return new ServiceException("DATABASE_ERROR", "Database error: " + exception.getMessage());
        }

        // Fallback
        return new ServiceException("JPA_ERROR", "JPA error: " + exception.getMessage());
    }
}

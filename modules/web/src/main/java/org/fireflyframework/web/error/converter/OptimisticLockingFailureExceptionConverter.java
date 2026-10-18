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
import org.fireflyframework.web.error.exceptions.ConcurrencyException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;

/**
 * Converter for Spring's OptimisticLockingFailureException.
 * Converts optimistic locking failure exceptions to concurrency exceptions.
 */
@Component
@ConditionalOnClass(OptimisticLockingFailureException.class)
public class OptimisticLockingFailureExceptionConverter implements ExceptionConverter<OptimisticLockingFailureException> {

    /**
     * Creates a new OptimisticLockingFailureExceptionConverter.
     */
    public OptimisticLockingFailureExceptionConverter() {
        // Default constructor
    }

    @Override
    public Class<OptimisticLockingFailureException> getExceptionType() {
        return OptimisticLockingFailureException.class;
    }

    @Override
    public BusinessException convert(OptimisticLockingFailureException exception) {
        String message = exception.getMessage();

        // Try to extract entity information from the message
        String resource = "unknown";
        String resourceId = "unknown";

        if (message != null) {
            // Try to parse entity information from the message
            // This is a simple example and might need to be adapted to your specific exception messages
            if (message.contains("entity") && message.contains("with id")) {
                int entityStart = message.indexOf("entity") + 7;
                int entityEnd = message.indexOf("with id") - 1;
                if (entityStart < entityEnd) {
                    resource = message.substring(entityStart, entityEnd).trim();
                }

                int idStart = message.indexOf("with id") + 8;
                int idEnd = message.indexOf("was", idStart);
                if (idStart < idEnd) {
                    resourceId = message.substring(idStart, idEnd).trim();
                    // Remove quotes if present
                    if (resourceId.startsWith("'") && resourceId.endsWith("'")) {
                        resourceId = resourceId.substring(1, resourceId.length() - 1);
                    }
                }
            }
        }

        return ConcurrencyException.optimisticLockingFailure(resource, resourceId);
    }
}

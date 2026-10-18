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


package org.fireflyframework.web.error.examples;

import org.fireflyframework.web.error.converter.ExceptionConverterService;
import org.fireflyframework.web.error.exceptions.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import java.util.concurrent.TimeoutException;

/**
 * Example service that demonstrates how to use the exception handling features.
 * This class shows how to throw business exceptions directly and how to use
 * the exception conversion mechanism.
 */
@Service
@ConditionalOnClass({DataIntegrityViolationException.class, OptimisticLockingFailureException.class})
public class ExceptionHandlingExample {

    private final ExceptionConverterService converterService;

    /**
     * Creates a new ExceptionHandlingExample with the given converter service.
     *
     * @param converterService the exception converter service
     */
    public ExceptionHandlingExample(ExceptionConverterService converterService) {
        this.converterService = converterService;
    }

    /**
     * Example of throwing business exceptions directly.
     * This method demonstrates how to create and throw various business exceptions.
     *
     * @param exceptionType the type of exception to throw
     * @throws BusinessException the business exception
     */
    public void throwBusinessException(String exceptionType) throws BusinessException {
        switch (exceptionType) {
            case "resource-not-found":
                throw ResourceNotFoundException.forResource("User", "123");
            case "invalid-request":
                throw InvalidRequestException.forField("email", "invalid-email", "must be a valid email format");
            case "conflict":
                throw ConflictException.resourceAlreadyExists("User", "john.doe@example.com");
            case "unauthorized":
                throw UnauthorizedException.missingAuthentication();
            case "forbidden":
                throw ForbiddenException.insufficientPermissions("ADMIN");
            case "service-error":
                throw ServiceException.withCause("Failed to process request", new RuntimeException("Database connection failed"));
            case "validation-error":
                ValidationException.Builder validationBuilder = new ValidationException.Builder()
                        .addError("email", "must be a valid email")
                        .addError("password", "must be at least 8 characters");
                throw validationBuilder.build();
            case "third-party-service":
                throw ThirdPartyServiceException.serviceUnavailable("PaymentService");
            case "rate-limit":
                throw RateLimitException.forUser("user123", 60);
            case "data-integrity":
                throw DataIntegrityException.uniqueConstraintViolation("email", "john.doe@example.com");
            case "operation-timeout":
                throw OperationTimeoutException.databaseTimeout("query", 5000);
            case "concurrency":
                throw ConcurrencyException.optimisticLockingFailure("User", "123");
            case "authorization":
                throw AuthorizationException.missingPermission("USER_WRITE");
            default:
                throw new BusinessException("Unknown exception type: " + exceptionType);
        }
    }

    /**
     * Example of manually converting standard exceptions to business exceptions.
     * This method demonstrates how to use the converter service to convert exceptions.
     *
     * @param exceptionType the type of exception to convert
     * @return the converted business exception
     */
    public BusinessException convertStandardException(String exceptionType) {
        Throwable exception;

        switch (exceptionType) {
            case "data-integrity":
                exception = new DataIntegrityViolationException("Duplicate entry 'john.doe@example.com' for key 'email'");
                break;
            case "optimistic-locking":
                exception = new OptimisticLockingFailureException("Failed to update entity User with id '123' - it was modified by another transaction");
                break;
            case "client-error":
                exception = HttpClientErrorException.NotFound.create(org.springframework.http.HttpStatus.NOT_FOUND, "Not Found", null, null, null);
                break;
            case "server-error":
                exception = HttpServerErrorException.ServiceUnavailable.create(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable", null, null, null);
                break;
            case "timeout":
                exception = new TimeoutException("Operation timed out after 5000ms");
                break;
            default:
                exception = new RuntimeException("Unknown exception: " + exceptionType);
                break;
        }

        return converterService.convertException(exception);
    }

    /**
     * Example of automatically converting standard exceptions to business exceptions.
     * Any exceptions thrown by this method will be automatically converted to business exceptions
     * by the GlobalExceptionHandler.
     *
     * @param exceptionType the type of exception to throw
     * @throws BusinessException the converted business exception
     * @throws TimeoutException if a timeout occurs
     */
    public void throwStandardException(String exceptionType) throws BusinessException, TimeoutException {
        switch (exceptionType) {
            case "data-integrity":
                throw new DataIntegrityViolationException("Duplicate entry 'john.doe@example.com' for key 'email'");
            case "optimistic-locking":
                throw new OptimisticLockingFailureException("Failed to update entity User with id '123' - it was modified by another transaction");
            case "client-error":
                throw HttpClientErrorException.NotFound.create(org.springframework.http.HttpStatus.NOT_FOUND, "Not Found", null, null, null);
            case "server-error":
                throw HttpServerErrorException.ServiceUnavailable.create(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable", null, null, null);
            case "timeout":
                throw new TimeoutException("Operation timed out after 5000ms");
            default:
                throw new RuntimeException("Unknown exception: " + exceptionType);
        }
    }
}

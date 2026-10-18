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


package org.fireflyframework.web.error.aspect;

import org.fireflyframework.web.error.exceptions.BusinessException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Aspect that adds metadata to all business exceptions.
 * This aspect intercepts the ExceptionConverterService.convertException method
 * and adds metadata to the returned exception.
 */
@Aspect
@Component
@Order(1)
public class ExceptionMetadataAspect {

    /**
     * Whether Spring's {@code org.springframework.dao} exception hierarchy (shipped by
     * spring-tx, pulled transitively by spring-data) is on the classpath. fireflyframework-web
     * declares spring-data-commons as {@code optional}, so data-less services (e.g. reactive
     * BFFs) may run without it. This aspect runs for EVERY exception, so guarding the
     * {@code instanceof org.springframework.dao.*} checks with this flag prevents a
     * {@code NoClassDefFoundError} when the classes are absent. When present (e.g. core
     * services with a database) the original {@code instanceof} runs unchanged, so the
     * full type hierarchy — including subclasses like {@code DuplicateKeyException} — is
     * matched exactly as before.
     */
    private static final boolean SPRING_DAO_PRESENT = ClassUtils.isPresent(
            "org.springframework.dao.DataIntegrityViolationException",
            ExceptionMetadataAspect.class.getClassLoader());

    /**
     * Adds metadata to all business exceptions returned by the ExceptionConverterService.
     *
     * @param joinPoint the join point
     * @return the business exception with added metadata
     * @throws Throwable if an error occurs
     */
    @Around("execution(* org.fireflyframework.web.error.converter.ExceptionConverterService.convertException(..))")
    public Object addMetadataToExceptions(ProceedingJoinPoint joinPoint) throws Throwable {
        // Get the original exception from the arguments
        Object[] args = joinPoint.getArgs();
        if (args.length == 0 || !(args[0] instanceof Throwable)) {
            // If there are no arguments or the first argument is not a Throwable,
            // just proceed with the original method
            return joinPoint.proceed();
        }

        Throwable originalException = (Throwable) args[0];

        // If the original exception is already a BusinessException, just proceed without adding metadata
        if (originalException instanceof BusinessException) {
            return joinPoint.proceed();
        }

        // Proceed with the original method to get the converted exception
        Object result = joinPoint.proceed();

        // If the result is not a BusinessException, just return it
        if (!(result instanceof BusinessException)) {
            return result;
        }

        // Add metadata to the converted exception
        BusinessException convertedException = (BusinessException) result;
        return addMetadata(convertedException, originalException);
    }

    /**
     * Adds metadata to a business exception.
     *
     * @param convertedException the converted business exception
     * @param originalException the original exception
     * @return the business exception with added metadata
     */
    private BusinessException addMetadata(BusinessException convertedException, Throwable originalException) {
        // Start with existing metadata if available
        Map<String, Object> metadata = new HashMap<>();
        if (convertedException.getMetadata() != null) {
            metadata.putAll(convertedException.getMetadata());
        }

        // Add exception type if not already present
        metadata.putIfAbsent("exceptionType", originalException.getClass().getName());

        // Add unique exception ID for tracking if not already present
        metadata.putIfAbsent("exceptionId", UUID.randomUUID().toString());

        // Add stack trace element information for debugging if not already present
        if (originalException.getStackTrace() != null && originalException.getStackTrace().length > 0) {
            StackTraceElement element = originalException.getStackTrace()[0];
            metadata.putIfAbsent("sourceClass", element.getClassName());
            metadata.putIfAbsent("sourceMethod", element.getMethodName());
            metadata.putIfAbsent("sourceLine", element.getLineNumber());
            metadata.putIfAbsent("sourceFile", element.getFileName());
        }

        // Add cause information if available and not already present
        if (originalException.getCause() != null) {
            metadata.putIfAbsent("causeType", originalException.getCause().getClass().getName());
            metadata.putIfAbsent("causeMessage", originalException.getCause().getMessage());
        }

        // Add exception-specific metadata
        addExceptionSpecificMetadata(metadata, originalException);

        // Return the exception with the added metadata
        return convertedException.withMetadata(metadata);
    }

    /**
     * Adds exception-specific metadata based on the type of the original exception.
     *
     * @param metadata the metadata map to add to
     * @param exception the original exception
     */
    private void addExceptionSpecificMetadata(Map<String, Object> metadata, Throwable exception) {
        // Handle specific exception types.
        // The org.springframework.dao.* checks are guarded by SPRING_DAO_PRESENT so the
        // short-circuit skips the instanceof (and its class resolution) when spring-tx is
        // absent — avoiding a NoClassDefFoundError in data-less services. When present the
        // instanceof runs exactly as before, matching the full subclass hierarchy.
        if (SPRING_DAO_PRESENT && exception instanceof org.springframework.dao.DataIntegrityViolationException) {
            metadata.putIfAbsent("category", "dataIntegrity");
            // Extract constraint name if available
            if (exception.getMessage() != null) {
                if (exception.getMessage().contains("constraint")) {
                    metadata.putIfAbsent("constraintViolation", true);
                }
                if (exception.getMessage().contains("unique") || exception.getMessage().contains("duplicate")) {
                    metadata.putIfAbsent("uniqueViolation", true);
                }
                if (exception.getMessage().contains("foreign key")) {
                    metadata.putIfAbsent("foreignKeyViolation", true);
                }
            }
        } else if (SPRING_DAO_PRESENT && exception instanceof org.springframework.dao.QueryTimeoutException) {
            metadata.putIfAbsent("category", "timeout");
            metadata.putIfAbsent("timeoutType", "database");
        } else if (exception instanceof java.util.concurrent.TimeoutException) {
            metadata.putIfAbsent("category", "timeout");
            metadata.putIfAbsent("timeoutType", "operation");
        } else if (exception instanceof java.net.SocketTimeoutException) {
            metadata.putIfAbsent("category", "timeout");
            metadata.putIfAbsent("timeoutType", "network");
        } else if (exception instanceof org.springframework.web.reactive.function.client.WebClientResponseException) {
            org.springframework.web.reactive.function.client.WebClientResponseException ex =
                (org.springframework.web.reactive.function.client.WebClientResponseException) exception;
            metadata.putIfAbsent("category", "externalService");
            metadata.putIfAbsent("statusCode", ex.getStatusCode().value());
            metadata.putIfAbsent("responseBody", ex.getResponseBodyAsString());
            if (ex.getRequest() != null) {
                metadata.putIfAbsent("requestUrl", ex.getRequest().getURI().toString());
                metadata.putIfAbsent("requestMethod", ex.getRequest().getMethod().name());
            }
        } else if (exception instanceof java.net.ConnectException) {
            metadata.putIfAbsent("category", "network");
            metadata.putIfAbsent("connectionError", true);
        } else if (exception instanceof java.net.UnknownHostException) {
            metadata.putIfAbsent("category", "network");
            metadata.putIfAbsent("unknownHost", true);
            metadata.putIfAbsent("host", exception.getMessage());
        } else if (exception.getClass().getName().equals("io.r2dbc.spi.R2dbcException")) {
            try {
                // Use reflection to avoid direct dependency on r2dbc
                Object sqlState = exception.getClass().getMethod("getSqlState").invoke(exception);
                Object errorCode = exception.getClass().getMethod("getErrorCode").invoke(exception);

                metadata.putIfAbsent("category", "database");
                if (sqlState != null) {
                    metadata.putIfAbsent("sqlState", sqlState.toString());
                }
                if (errorCode != null) {
                    metadata.putIfAbsent("errorCode", errorCode.toString());
                }

                // Add exception message for better context
                if (exception.getMessage() != null) {
                    metadata.putIfAbsent("databaseErrorMessage", exception.getMessage());
                }

                // Handle specific R2DBC exception types
                String exceptionClassName = exception.getClass().getName();
                if (exceptionClassName.equals("io.r2dbc.spi.R2dbcTimeoutException")) {
                    metadata.putIfAbsent("timeoutType", "r2dbc");
                    metadata.putIfAbsent("r2dbcExceptionType", "timeout");
                } else if (exceptionClassName.equals("io.r2dbc.spi.R2dbcDataIntegrityViolationException")) {
                    metadata.putIfAbsent("dataIntegrityViolation", true);
                    metadata.putIfAbsent("r2dbcExceptionType", "dataIntegrity");
                } else if (exceptionClassName.equals("io.r2dbc.spi.R2dbcPermissionDeniedException")) {
                    metadata.putIfAbsent("permissionDenied", true);
                    metadata.putIfAbsent("r2dbcExceptionType", "permissionDenied");
                } else if (exceptionClassName.equals("io.r2dbc.spi.R2dbcRollbackException")) {
                    metadata.putIfAbsent("transactionRollback", true);
                    metadata.putIfAbsent("r2dbcExceptionType", "rollback");
                } else if (exceptionClassName.equals("io.r2dbc.spi.R2dbcTransientException")) {
                    metadata.putIfAbsent("transientError", true);
                    metadata.putIfAbsent("r2dbcExceptionType", "transient");
                } else if (exceptionClassName.equals("io.r2dbc.spi.R2dbcNonTransientException")) {
                    metadata.putIfAbsent("nonTransientError", true);
                    metadata.putIfAbsent("r2dbcExceptionType", "nonTransient");
                } else if (exceptionClassName.equals("io.r2dbc.spi.R2dbcBadGrammarException")) {
                    metadata.putIfAbsent("badGrammar", true);
                    metadata.putIfAbsent("r2dbcExceptionType", "badGrammar");
                } else {
                    metadata.putIfAbsent("r2dbcExceptionType", "generic");
                }
            } catch (Exception e) {
                // Ignore reflection errors
            }
        }

        // Add support for Spring Web exceptions
        try {
            // Use reflection to avoid direct dependency on spring-web
            if (exception.getClass().getName().equals("org.springframework.web.client.HttpClientErrorException")) {
                metadata.putIfAbsent("category", "httpClient");
                // Extract status code using reflection
                Object statusCode = exception.getClass().getMethod("getStatusCode").invoke(exception);
                if (statusCode != null) {
                    metadata.putIfAbsent("statusCode", statusCode.toString());
                }
            } else if (exception.getClass().getName().equals("org.springframework.web.client.HttpServerErrorException")) {
                metadata.putIfAbsent("category", "httpServer");
                // Extract status code using reflection
                Object statusCode = exception.getClass().getMethod("getStatusCode").invoke(exception);
                if (statusCode != null) {
                    metadata.putIfAbsent("statusCode", statusCode.toString());
                }
            }
        } catch (Exception e) {
            // Ignore reflection errors
        }
    }
}

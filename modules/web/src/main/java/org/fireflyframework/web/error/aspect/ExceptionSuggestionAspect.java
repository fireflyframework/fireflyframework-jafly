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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Aspect that adds suggestions to all business exceptions.
 * This aspect intercepts the ExceptionConverterService.convertException method
 * and adds suggestions to the returned exception based on its type and metadata.
 *
 * The suggestions are added to the metadata of the exception with the key "suggestion".
 * They can then be extracted by the GlobalExceptionHandler to include in the error response.
 */
@Aspect
@Component
@Order(2) // Execute after ExceptionMetadataAspect
public class ExceptionSuggestionAspect {

    // Common patterns in exception messages
    private static final Pattern UNIQUE_CONSTRAINT_PATTERN = Pattern.compile("(?:unique|duplicate).*?(?:key|constraint|index).*?['\"](.*?)['\"]", Pattern.CASE_INSENSITIVE);
    private static final Pattern FOREIGN_KEY_PATTERN = Pattern.compile("foreign key.*?['\"](.*?)['\"]", Pattern.CASE_INSENSITIVE);
    private static final Pattern FIELD_PATTERN = Pattern.compile("(?:field|column|property)\\s+['\"](.*?)['\"]", Pattern.CASE_INSENSITIVE);
    private static final Pattern TABLE_PATTERN = Pattern.compile("(?:table|relation)\\s+['\"](.*?)['\"]", Pattern.CASE_INSENSITIVE);

    /**
     * Adds suggestions to all business exceptions returned by the ExceptionConverterService.
     *
     * @param joinPoint the join point
     * @return the business exception with added suggestions
     * @throws Throwable if an error occurs
     */
    @Around("execution(* org.fireflyframework.web.error.converter.ExceptionConverterService.convertException(..))")
    public Object addSuggestionsToExceptions(ProceedingJoinPoint joinPoint) throws Throwable {
        // Get the original exception from the arguments
        Object[] args = joinPoint.getArgs();
        if (args.length == 0 || !(args[0] instanceof Throwable)) {
            // If there are no arguments or the first argument is not a Throwable,
            // just proceed with the original method
            return joinPoint.proceed();
        }

        Throwable originalException = (Throwable) args[0];

        // Proceed with the original method to get the converted exception
        Object result = joinPoint.proceed();

        // If the result is not a BusinessException, just return it
        if (!(result instanceof BusinessException)) {
            return result;
        }

        // Add suggestions to the converted exception
        BusinessException convertedException = (BusinessException) result;
        return addSuggestion(convertedException, originalException);
    }

    /**
     * Adds a suggestion to a business exception based on its type and metadata.
     *
     * @param convertedException the converted business exception
     * @param originalException the original exception
     * @return the business exception with added suggestion
     */
    private BusinessException addSuggestion(BusinessException convertedException, Throwable originalException) {
        // Get existing metadata
        Map<String, Object> metadata = new HashMap<>();
        if (convertedException.getMetadata() != null) {
            metadata.putAll(convertedException.getMetadata());
        }

        // If the metadata already contains a suggestion, don't override it
        if (metadata.containsKey("suggestion")) {
            return convertedException;
        }

        // Add suggestion based on exception type and metadata
        String suggestion = generateSuggestion(convertedException, originalException, metadata);
        if (suggestion != null) {
            metadata.put("suggestion", suggestion);
            return convertedException.withMetadata(metadata);
        }

        return convertedException;
    }

    /**
     * Generates a suggestion based on the exception type and metadata.
     *
     * @param convertedException the converted business exception
     * @param originalException the original exception
     * @param metadata the metadata
     * @return the suggestion
     */
    private String generateSuggestion(BusinessException convertedException, Throwable originalException, Map<String, Object> metadata) {
        // First check if we have a category in metadata that can help us generate a more specific suggestion
        String category = metadata.containsKey("category") ? metadata.get("category").toString() : null;
        if (category != null) {
            if ("dataIntegrity".equals(category)) {
                return generateDataIntegritySuggestion(metadata, convertedException.getMessage());
            } else if ("timeout".equals(category)) {
                return generateTimeoutSuggestion(metadata);
            } else if ("network".equals(category)) {
                return generateNetworkSuggestion(metadata);
            } else if ("externalService".equals(category)) {
                return "There was an issue with an external service. Please try again later or check the service status.";
            } else if ("database".equals(category)) {
                return generateDatabaseSuggestion(metadata, convertedException.getMessage());
            }
        }

        // Check if the original exception was a network-related exception by looking at the exceptionType
        String exceptionType = metadata.containsKey("exceptionType") ? metadata.get("exceptionType").toString() : null;
        if (exceptionType != null) {
            if (exceptionType.contains("ConnectException")) {
                return "Could not connect to the server. Please check your network connection and try again.";
            } else if (exceptionType.contains("UnknownHostException")) {
                Object host = metadata.get("host");
                if (host != null) {
                    return String.format("The host '%s' could not be resolved. Please check the hostname and try again.", host);
                }
                return "The host could not be resolved. Please check the hostname and try again.";
            } else if (exceptionType.contains("SocketTimeoutException")) {
                return "The network request timed out. Please check your connection and try again.";
            }
        }

        // If no category or no specific suggestion for the category, check based on HTTP status code
        HttpStatus status = convertedException.getStatus();
        String code = convertedException.getCode();
        String message = convertedException.getMessage();

        // Generate suggestion based on HTTP status
        if (status != null) {
            // 400 Bad Request
            if (status == HttpStatus.BAD_REQUEST) {
                return generateBadRequestSuggestion(convertedException, originalException, metadata);
            }
            // 401 Unauthorized
            else if (status == HttpStatus.UNAUTHORIZED) {
                return generateUnauthorizedSuggestion(code);
            }
            // 403 Forbidden
            else if (status == HttpStatus.FORBIDDEN) {
                return generateForbiddenSuggestion(code, metadata);
            }
            // 404 Not Found
            else if (status == HttpStatus.NOT_FOUND) {
                return generateNotFoundSuggestion(metadata);
            }
            // 405 Method Not Allowed
            else if (status == HttpStatus.METHOD_NOT_ALLOWED) {
                return "Use one of the allowed HTTP methods for this resource. Check the 'Allow' header in the response for supported methods.";
            }
            // 408 Request Timeout
            else if (status == HttpStatus.REQUEST_TIMEOUT) {
                return generateTimeoutSuggestion(metadata);
            }
            // 409 Conflict
            else if (status == HttpStatus.CONFLICT) {
                return generateConflictSuggestion(code, metadata);
            }
            // 410 Gone
            else if (status == HttpStatus.GONE) {
                return "This resource is no longer available. Please update your application to use the new endpoint or resource.";
            }
            // 412 Precondition Failed
            else if (status == HttpStatus.PRECONDITION_FAILED) {
                return "The resource has been modified since you last retrieved it. Fetch the latest version and try again.";
            }
            // 413 Payload Too Large
            else if (status == HttpStatus.PAYLOAD_TOO_LARGE) {
                return generatePayloadTooLargeSuggestion(metadata);
            }
            // 415 Unsupported Media Type
            else if (status == HttpStatus.UNSUPPORTED_MEDIA_TYPE) {
                return generateUnsupportedMediaTypeSuggestion(metadata);
            }
            // 423 Locked
            else if (status.value() == 423) { // HttpStatus.LOCKED
                return "The resource is currently locked. Please try again later or contact the user who has the lock.";
            }
            // 429 Too Many Requests
            else if (status == HttpStatus.TOO_MANY_REQUESTS) {
                return generateRateLimitSuggestion(metadata);
            }
            // 500 Internal Server Error
            else if (status == HttpStatus.INTERNAL_SERVER_ERROR) {
                return "Please try again later or contact support with the trace ID if the issue persists.";
            }
            // 501 Not Implemented
            else if (status == HttpStatus.NOT_IMPLEMENTED) {
                return "This feature is not currently implemented. Please check the documentation for available features.";
            }
            // 502 Bad Gateway
            else if (status == HttpStatus.BAD_GATEWAY) {
                return "There was an issue communicating with an external service. Please try again later.";
            }
            // 503 Service Unavailable
            else if (status == HttpStatus.SERVICE_UNAVAILABLE) {
                return generateServiceUnavailableSuggestion(metadata);
            }
            // 504 Gateway Timeout
            else if (status == HttpStatus.GATEWAY_TIMEOUT) {
                return "The external service took too long to respond. Please try again later when the service is more responsive.";
            }
        }

        // If we couldn't generate a suggestion based on HTTP status or category, use a default suggestion

        // Default suggestion
        return "Please check your request and try again.";
    }

    /**
     * Generates a suggestion for 400 Bad Request errors.
     */
    private String generateBadRequestSuggestion(BusinessException ex, Throwable originalException, Map<String, Object> metadata) {
        String code = ex.getCode();

        if ("VALIDATION_ERROR".equals(code)) {
            return "Please check the validation errors and correct your request.";
        } else if ("INVALID_FIELD".equals(code)) {
            Object field = metadata.get("field");
            if (field != null) {
                return String.format("Please provide a valid value for the '%s' field.", field);
            }
            return "Please check the field values in your request.";
        } else if ("INVALID_REQUEST".equals(code)) {
            return "Please check your request format and parameters.";
        } else if (code != null && code.contains("CONSTRAINT_VIOLATION")) {
            return generateDataIntegritySuggestion(metadata, ex.getMessage());
        } else if (originalException instanceof org.springframework.dao.DataIntegrityViolationException) {
            return generateDataIntegritySuggestion(metadata, originalException.getMessage());
        }

        return "Please check your request parameters and try again.";
    }

    /**
     * Generates a suggestion for 401 Unauthorized errors.
     */
    private String generateUnauthorizedSuggestion(String code) {
        if ("MISSING_AUTHENTICATION".equals(code)) {
            return "Please include a valid authentication token in your request.";
        } else if ("INVALID_CREDENTIALS".equals(code)) {
            return "The provided credentials are invalid. Please check your username and password.";
        } else if ("TOKEN_EXPIRED".equals(code)) {
            return "Your authentication token has expired. Please log in again to obtain a new token.";
        } else if ("INVALID_TOKEN".equals(code)) {
            return "The provided authentication token is invalid. Please obtain a new token.";
        }

        return "Please authenticate and try again.";
    }

    /**
     * Generates a suggestion for 403 Forbidden errors.
     */
    private String generateForbiddenSuggestion(String code, Map<String, Object> metadata) {
        if ("INSUFFICIENT_PERMISSIONS".equals(code)) {
            Object requiredRole = metadata.get("requiredRole");
            if (requiredRole != null) {
                return String.format("You need the '%s' role to access this resource. Contact your administrator for access.", requiredRole);
            }
            return "You don't have sufficient permissions to access this resource. Contact your administrator for access.";
        } else if ("RESOURCE_ACCESS_DENIED".equals(code)) {
            Object resourceType = metadata.get("resourceType");
            Object resourceId = metadata.get("resourceId");
            if (resourceType != null && resourceId != null) {
                return String.format("You don't have permission to access the %s with ID '%s'.", resourceType, resourceId);
            }
            return "You don't have permission to access this resource.";
        } else if ("MISSING_PERMISSION".equals(code)) {
            Object permission = metadata.get("permission");
            if (permission != null) {
                return String.format("You need the '%s' permission to perform this action. Contact your administrator for access.", permission);
            }
            return "You don't have the required permission to perform this action.";
        }

        return "You don't have permission to access this resource. Contact an administrator if you need access.";
    }

    /**
     * Generates a suggestion for 404 Not Found errors.
     */
    private String generateNotFoundSuggestion(Map<String, Object> metadata) {
        Object resourceType = metadata.get("resourceType");
        Object resourceId = metadata.get("resourceId");

        if (resourceType != null && resourceId != null) {
            return String.format("The %s with ID '%s' could not be found. Please verify the ID is correct.", resourceType, resourceId);
        } else if (resourceType != null) {
            return String.format("The requested %s could not be found. Please verify your request.", resourceType);
        }

        return "The requested resource could not be found. Please check the identifier and ensure it exists.";
    }

    /**
     * Generates a suggestion for timeout errors.
     */
    private String generateTimeoutSuggestion(Map<String, Object> metadata) {
        String timeoutType = metadata.containsKey("timeoutType") ? metadata.get("timeoutType").toString() : null;

        if ("database".equals(timeoutType) || "r2dbc".equals(timeoutType)) {
            return "The database operation took too long to complete. Try simplifying your query or try again later.";
        } else if ("operation".equals(timeoutType)) {
            return "The operation took too long to complete. Please try again later or with a smaller dataset.";
        } else if ("network".equals(timeoutType)) {
            return "The network request timed out. Please check your connection and try again.";
        }

        // Check if the original exception was a SocketTimeoutException
        String exceptionType = metadata.containsKey("exceptionType") ? metadata.get("exceptionType").toString() : null;
        if (exceptionType != null && exceptionType.contains("SocketTimeoutException")) {
            return "The network request timed out. Please check your connection and try again.";
        }

        return "The request took too long to complete. Please try again later or with a smaller dataset.";
    }

    /**
     * Generates a suggestion for conflict errors.
     */
    private String generateConflictSuggestion(String code, Map<String, Object> metadata) {
        if ("RESOURCE_ALREADY_EXISTS".equals(code)) {
            Object resourceType = metadata.get("resourceType");
            Object identifier = metadata.get("identifier");

            if (resourceType != null && identifier != null) {
                return String.format("A %s with the identifier '%s' already exists. Use a different identifier or update the existing resource.", resourceType, identifier);
            } else if (resourceType != null) {
                return String.format("A %s with the provided identifier already exists. Use a different identifier or update the existing resource.", resourceType);
            }

            return "The resource already exists. Use a different identifier or update the existing resource.";
        } else if ("OPTIMISTIC_LOCKING_FAILURE".equals(code)) {
            return "The resource has been modified by another user since you retrieved it. Please refresh and try again.";
        } else if ("CONCURRENT_MODIFICATION".equals(code)) {
            return "The resource is being modified concurrently. Please try again later.";
        }

        return "The request conflicts with the current state of the resource. Refresh and try again.";
    }

    /**
     * Generates a suggestion for payload too large errors.
     */
    private String generatePayloadTooLargeSuggestion(Map<String, Object> metadata) {
        String code = metadata.containsKey("code") ? metadata.get("code").toString() : null;

        if ("FILE_TOO_LARGE".equals(code)) {
            Object fileName = metadata.get("fileName");
            Object maxSize = metadata.get("maxSize");

            if (fileName != null && maxSize != null) {
                return String.format("The file '%s' exceeds the maximum allowed size of %s. Please reduce the file size and try again.", fileName, formatFileSize(maxSize));
            }

            return "The uploaded file exceeds the maximum allowed size. Please reduce the file size and try again.";
        } else if ("MULTIPART_REQUEST_TOO_LARGE".equals(code)) {
            return "The multipart request exceeds the maximum allowed size. Please reduce the size of your uploads and try again.";
        }

        return "The request payload is too large. Please reduce the size of your request and try again.";
    }

    /**
     * Formats a file size in bytes to a human-readable format.
     */
    private String formatFileSize(Object bytes) {
        try {
            long size = Long.parseLong(bytes.toString());
            if (size < 1024) {
                return size + " bytes";
            } else if (size < 1024 * 1024) {
                return String.format("%.2f KB", size / 1024.0);
            } else if (size < 1024 * 1024 * 1024) {
                return String.format("%.2f MB", size / (1024.0 * 1024.0));
            } else {
                return String.format("%.2f GB", size / (1024.0 * 1024.0 * 1024.0));
            }
        } catch (NumberFormatException e) {
            return bytes.toString();
        }
    }

    /**
     * Generates a suggestion for unsupported media type errors.
     */
    private String generateUnsupportedMediaTypeSuggestion(Map<String, Object> metadata) {
        Object unsupportedType = metadata.get("unsupportedType");
        Object supportedTypes = metadata.get("supportedTypes");

        if (unsupportedType != null && supportedTypes != null) {
            return String.format("The media type '%s' is not supported. Please use one of the supported types: %s.", unsupportedType, supportedTypes);
        } else if (supportedTypes != null) {
            return String.format("Please use one of the supported media types: %s.", supportedTypes);
        }

        return "The request format is not supported. Please check the Content-Type header and use a supported format.";
    }

    /**
     * Generates a suggestion for rate limit errors.
     */
    private String generateRateLimitSuggestion(Map<String, Object> metadata) {
        Object retryAfterSeconds = metadata.get("retryAfterSeconds");

        if (retryAfterSeconds != null) {
            int seconds = Integer.parseInt(retryAfterSeconds.toString());
            if (seconds < 60) {
                return String.format("You have exceeded the rate limit. Please try again in %d seconds.", seconds);
            } else if (seconds < 3600) {
                return String.format("You have exceeded the rate limit. Please try again in %d minutes.", seconds / 60);
            } else {
                return String.format("You have exceeded the rate limit. Please try again in %d hours.", seconds / 3600);
            }
        }

        return "You have exceeded the rate limit. Please reduce the frequency of your requests and try again later.";
    }

    /**
     * Generates a suggestion for service unavailable errors.
     */
    private String generateServiceUnavailableSuggestion(Map<String, Object> metadata) {
        Object retryAfterSeconds = metadata.get("retryAfterSeconds");
        Object maintenanceEndTime = metadata.get("maintenanceEndTime");

        if (maintenanceEndTime != null) {
            return String.format("The service is currently undergoing maintenance. Please try again after %s.", maintenanceEndTime);
        } else if (retryAfterSeconds != null) {
            int seconds = Integer.parseInt(retryAfterSeconds.toString());
            if (seconds < 60) {
                return String.format("The service is temporarily unavailable. Please try again in %d seconds.", seconds);
            } else if (seconds < 3600) {
                return String.format("The service is temporarily unavailable. Please try again in %d minutes.", seconds / 60);
            } else {
                return String.format("The service is temporarily unavailable. Please try again in %d hours.", seconds / 3600);
            }
        }

        return "The service is temporarily unavailable. Please try again later.";
    }

    /**
     * Generates a suggestion for data integrity errors.
     */
    private String generateDataIntegritySuggestion(Map<String, Object> metadata, String message) {
        Boolean uniqueViolation = metadata.containsKey("uniqueViolation") ? (Boolean) metadata.get("uniqueViolation") : false;
        Boolean foreignKeyViolation = metadata.containsKey("foreignKeyViolation") ? (Boolean) metadata.get("foreignKeyViolation") : false;
        Boolean constraintViolation = metadata.containsKey("constraintViolation") ? (Boolean) metadata.get("constraintViolation") : false;

        if (Boolean.TRUE.equals(uniqueViolation)) {
            // Try to extract the field name from the message
            String field = extractField(message, metadata);
            if (field != null) {
                return String.format("A record with the same '%s' already exists. Please use a different value.", field);
            }
            return "A record with the same unique identifier already exists. Please use a different value.";
        } else if (Boolean.TRUE.equals(foreignKeyViolation)) {
            // Try to extract the field name from the message
            String field = extractField(message, metadata);
            if (field != null) {
                return String.format("The referenced '%s' does not exist. Please provide a valid reference.", field);
            }
            return "The referenced record does not exist. Please provide a valid reference.";
        } else if (Boolean.TRUE.equals(constraintViolation)) {
            return "The data violates a constraint. Please check your input and ensure it meets all requirements.";
        }

        return "The data violates integrity constraints. Please check your input and try again.";
    }

    /**
     * Generates a suggestion for network errors.
     */
    private String generateNetworkSuggestion(Map<String, Object> metadata) {
        Boolean connectionError = metadata.containsKey("connectionError") ? (Boolean) metadata.get("connectionError") : false;
        Boolean unknownHost = metadata.containsKey("unknownHost") ? (Boolean) metadata.get("unknownHost") : false;

        if (Boolean.TRUE.equals(connectionError)) {
            return "Could not connect to the server. Please check your network connection and try again.";
        } else if (Boolean.TRUE.equals(unknownHost)) {
            Object host = metadata.get("host");
            if (host != null) {
                return String.format("The host '%s' could not be resolved. Please check the hostname and try again.", host);
            }
            return "The host could not be resolved. Please check the hostname and try again.";
        }

        return "A network error occurred. Please check your connection and try again.";
    }

    /**
     * Generates a suggestion for database errors.
     */
    private String generateDatabaseSuggestion(Map<String, Object> metadata, String message) {
        // First check if we have a specific R2DBC exception type
        String r2dbcExceptionType = metadata.containsKey("r2dbcExceptionType") ? metadata.get("r2dbcExceptionType").toString() : null;
        if (r2dbcExceptionType != null) {
            switch (r2dbcExceptionType) {
                case "timeout":
                    return "The database operation took too long to complete. Try simplifying your query or try again later.";
                case "dataIntegrity":
                    return generateDataIntegritySuggestion(metadata, message);
                case "permissionDenied":
                    return "You don't have sufficient permissions to perform this database operation. Contact your database administrator.";
                case "rollback":
                    return "The database transaction was rolled back. This could be due to a conflict with another transaction. Please try again.";
                case "transient":
                    return "A temporary database error occurred. Please try again later.";
                case "nonTransient":
                    return "A database error occurred that requires attention. Please check your input and ensure it meets all requirements.";
                case "badGrammar":
                    return "There was a syntax error in the database query. Please check your input for any invalid characters or formats.";
                case "generic":
                    // Fall through to SQL state handling
                    break;
            }
        }

        // If no specific R2DBC exception type or it's generic, check SQL state
        String sqlState = metadata.containsKey("sqlState") ? metadata.get("sqlState").toString() : null;
        if (sqlState != null) {
            // PostgreSQL error codes
            if ("23505".equals(sqlState)) { // unique_violation
                String field = extractField(message, metadata);
                if (field != null) {
                    return String.format("A record with the same '%s' already exists. Please use a different value.", field);
                }
                return "A record with the same unique identifier already exists. Please use a different value.";
            } else if ("23503".equals(sqlState)) { // foreign_key_violation
                String field = extractField(message, metadata);
                if (field != null) {
                    return String.format("The referenced '%s' does not exist. Please provide a valid reference.", field);
                }
                return "The referenced record does not exist. Please provide a valid reference.";
            } else if ("23502".equals(sqlState)) { // not_null_violation
                String field = extractField(message, metadata);
                if (field != null) {
                    return String.format("The '%s' field cannot be null. Please provide a value.", field);
                }
                return "A required field is missing. Please provide values for all required fields.";
            } else if ("23514".equals(sqlState)) { // check_violation
                return "The data violates a check constraint. Please ensure your data meets all requirements.";
            } else if (sqlState.startsWith("42")) { // syntax_error or similar
                return "There was a syntax error in the database query. Please check your input.";
            } else if (sqlState.startsWith("08")) { // connection_exception
                return "There was an issue connecting to the database. Please try again later.";
            } else if (sqlState.startsWith("57")) { // operator_intervention
                return "The database operation was cancelled by an administrator. Please try again later.";
            } else if (sqlState.startsWith("53")) { // insufficient_resources
                return "The database has insufficient resources to complete the operation. Please try again later or with a smaller dataset.";
            } else if (sqlState.startsWith("40")) { // transaction_rollback
                return "The transaction was rolled back. Please try again.";
            } else if (sqlState.startsWith("22")) { // data_exception
                return "The data provided is invalid. Please check your input and try again.";
            } else if (sqlState.startsWith("28")) { // invalid_authorization_specification
                return "Database authentication failed. Please check your credentials.";
            } else if (sqlState.startsWith("3D")) { // invalid_catalog_name
                return "The specified database does not exist. Please check your database configuration.";
            } else if (sqlState.startsWith("3F")) { // invalid_schema_name
                return "The specified schema does not exist. Please check your database configuration.";
            } else if (sqlState.startsWith("42P01")) { // undefined_table
                return "The specified table does not exist. Please check your query and ensure the table name is correct.";
            } else if (sqlState.startsWith("42P02")) { // undefined_parameter
                return "The query references an undefined parameter. Please check your query parameters.";
            }
        }

        // Check for data integrity violation flag
        Boolean dataIntegrityViolation = metadata.containsKey("dataIntegrityViolation") ? (Boolean) metadata.get("dataIntegrityViolation") : false;
        if (Boolean.TRUE.equals(dataIntegrityViolation)) {
            return generateDataIntegritySuggestion(metadata, message);
        }

        // Extract any useful information from the database error message
        String dbErrorMessage = metadata.containsKey("databaseErrorMessage") ? metadata.get("databaseErrorMessage").toString() : null;
        if (dbErrorMessage != null) {
            if (dbErrorMessage.contains("connection") && dbErrorMessage.contains("refused")) {
                return "Could not connect to the database. Please check that the database server is running and accessible.";
            } else if (dbErrorMessage.contains("authentication") || dbErrorMessage.contains("password")) {
                return "Database authentication failed. Please check your credentials.";
            } else if (dbErrorMessage.contains("timeout")) {
                return "The database operation timed out. Please try again later or optimize your query.";
            } else if (dbErrorMessage.contains("deadlock")) {
                return "A database deadlock was detected. Please try again later.";
            }
        }

        // Default suggestion
        return "A database error occurred. Please check your input and try again.";
    }

    /**
     * Extracts a field name from an error message or metadata.
     */
    private String extractField(String message, Map<String, Object> metadata) {
        // First check if the field is in the metadata
        if (metadata.containsKey("field")) {
            return metadata.get("field").toString();
        }

        // Try to extract from the message using patterns
        if (message != null) {
            // Try to extract from unique constraint message
            Matcher uniqueMatcher = UNIQUE_CONSTRAINT_PATTERN.matcher(message);
            if (uniqueMatcher.find()) {
                return uniqueMatcher.group(1);
            }

            // Try to extract from foreign key message
            Matcher foreignKeyMatcher = FOREIGN_KEY_PATTERN.matcher(message);
            if (foreignKeyMatcher.find()) {
                return foreignKeyMatcher.group(1);
            }

            // Try to extract from field pattern
            Matcher fieldMatcher = FIELD_PATTERN.matcher(message);
            if (fieldMatcher.find()) {
                return fieldMatcher.group(1);
            }
        }

        return null;
    }
}

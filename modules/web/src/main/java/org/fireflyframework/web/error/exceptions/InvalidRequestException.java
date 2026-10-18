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


package org.fireflyframework.web.error.exceptions;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when a request is invalid.
 * Results in a 400 BAD REQUEST response.
 */
public class InvalidRequestException extends BusinessException {

    /**
     * Creates a new InvalidRequestException with the given message.
     *
     * @param message the error message
     */
    public InvalidRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }

    /**
     * Creates a new InvalidRequestException with a code and message.
     *
     * @param code the error code
     * @param message the error message
     */
    public InvalidRequestException(String code, String message) {
        super(HttpStatus.BAD_REQUEST, code, message);
    }

    /**
     * Creates a new InvalidRequestException for a specific field with an invalid value.
     *
     * @param field the field that has an invalid value
     * @param value the invalid value
     * @param reason the reason why the value is invalid
     * @return a new InvalidRequestException
     */
    public static InvalidRequestException forField(String field, String value, String reason) {
        return new InvalidRequestException(
                "INVALID_FIELD",
                String.format("Invalid value '%s' for field '%s': %s", value, field, reason)
        );
    }

    /**
     * Creates a new InvalidRequestException with a specific reason code and message.
     *
     * @param code the error code
     * @param message the error message
     * @return a new InvalidRequestException
     */
    public static InvalidRequestException withReason(String code, String message) {
        return new InvalidRequestException(code, message);
    }
}

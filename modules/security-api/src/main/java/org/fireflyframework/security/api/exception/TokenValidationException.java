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

package org.fireflyframework.security.api.exception;

import org.fireflyframework.kernel.exception.FireflySecurityException;

/**
 * Raised when a presented bearer token cannot be validated — bad signature, untrusted issuer,
 * wrong audience, expired/not-yet-valid, or revoked. Always results in HTTP 401.
 */
public class TokenValidationException extends FireflySecurityException {

    public static final String ERROR_CODE = "SECURITY_TOKEN_INVALID";

    public TokenValidationException(String message) {
        super(message, ERROR_CODE);
    }

    public TokenValidationException(String message, Throwable cause) {
        super(message, ERROR_CODE, cause);
    }
}

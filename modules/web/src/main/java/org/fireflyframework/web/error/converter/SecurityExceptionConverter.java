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

import org.fireflyframework.web.error.exceptions.AuthorizationException;
import org.fireflyframework.web.error.exceptions.BusinessException;
import org.fireflyframework.web.error.exceptions.UnauthorizedException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AccountExpiredException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

/**
 * Converter for Spring Security exceptions.
 * Converts security exceptions to appropriate business exceptions.
 */
@Component
@ConditionalOnClass(Exception.class)
public class SecurityExceptionConverter implements ExceptionConverter<Exception> {

    /**
     * Creates a new SecurityExceptionConverter.
     */
    public SecurityExceptionConverter() {
        // Default constructor
    }

    @Override
    public Class<Exception> getExceptionType() {
        return Exception.class;
    }

    @Override
    public boolean canHandle(Throwable exception) {
        return exception instanceof AuthenticationException ||
               exception instanceof AccessDeniedException;
    }

    @Override
    public BusinessException convert(Exception exception) {
        if (exception instanceof AccessDeniedException) {
            return AuthorizationException.missingPermission("REQUIRED_PERMISSION");
        } else if (exception instanceof BadCredentialsException) {
            return UnauthorizedException.invalidCredentials();
        } else if (exception instanceof InsufficientAuthenticationException) {
            return UnauthorizedException.missingAuthentication();
        } else if (exception instanceof AccountExpiredException) {
            return UnauthorizedException.withReason("ACCOUNT_EXPIRED", "Your account has expired");
        } else if (exception instanceof CredentialsExpiredException) {
            return UnauthorizedException.withReason("CREDENTIALS_EXPIRED", "Your credentials have expired");
        } else if (exception instanceof DisabledException) {
            return UnauthorizedException.withReason("ACCOUNT_DISABLED", "Your account is disabled");
        } else if (exception instanceof LockedException) {
            return UnauthorizedException.withReason("ACCOUNT_LOCKED", "Your account is locked");
        } else if (exception instanceof AuthenticationException) {
            return UnauthorizedException.withReason("AUTHENTICATION_ERROR", exception.getMessage());
        }

        // Fallback
        return UnauthorizedException.withReason("SECURITY_ERROR", "Security error: " + exception.getMessage());
    }
}

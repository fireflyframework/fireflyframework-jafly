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

package org.fireflyframework.security.rs;

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.util.Assert;

/**
 * Access-token claim validators for signature-verifying JWT decoders. Unlike Spring's timestamp
 * validator alone, these profiles require an expiration claim; Spring's default clock skew and
 * present-expiration/not-before checks are retained. These factories do not verify signatures.
 */
public final class AccessTokenValidators {

    private static final OAuth2Error REQUIRED_EXPIRATION =
            new OAuth2Error("invalid_token", "Access token expiration is required", null);

    private AccessTokenValidators() {
    }

    /** Requires expiration and applies Spring's existing default timestamp validation. */
    public static OAuth2TokenValidator<Jwt> timestamps() {
        OAuth2TokenValidator<Jwt> requiredExpiration = jwt -> jwt.getExpiresAt() == null
                ? OAuth2TokenValidatorResult.failure(REQUIRED_EXPIRATION)
                : OAuth2TokenValidatorResult.success();
        return new DelegatingOAuth2TokenValidator<>(new JwtTimestampValidator(), requiredExpiration);
    }

    /**
     * Requires expiration, a case-sensitive exact issuer and membership of the exact expected
     * audience. Configuration values must be nonblank; values are not normalized or trimmed.
     */
    public static OAuth2TokenValidator<Jwt> forIssuerAndAudience(String issuer, String audience) {
        Assert.hasText(issuer, "Issuer must not be blank");
        Assert.hasText(audience, "Audience must not be blank");
        OAuth2TokenValidator<Jwt> expectedAudience = jwt -> jwt.getAudience() != null
                && jwt.getAudience().contains(audience)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(
                        new OAuth2Error("invalid_token", "Required audience is missing", null));
        return new DelegatingOAuth2TokenValidator<>(timestamps(), new JwtIssuerValidator(issuer), expectedAudience);
    }
}

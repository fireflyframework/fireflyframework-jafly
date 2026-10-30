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

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class AccessTokenValidatorsTest {

    private Jwt.Builder token() {
        return Jwt.withTokenValue("synthetic-token").header("alg", "RS256")
                .subject("synthetic-subject").issuedAt(Instant.now().minusSeconds(7200));
    }

    @Test
    void timestampsRequireExpirationWithoutLeakingTokenClaims() {
        var result = AccessTokenValidators.timestamps().validate(token().build());
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).allSatisfy(error -> {
            assertThat(error.getErrorCode()).isEqualTo("invalid_token");
            assertThat(error.getDescription()).doesNotContain("synthetic-subject", "synthetic-token");
        });
    }

    @Test
    void validTimestampsDoNotIntroduceIssuerOrAudienceRequirements() {
        var jwt = token().expiresAt(Instant.now().plusSeconds(300))
                .notBefore(Instant.now().minusSeconds(1)).build();
        assertThat(AccessTokenValidators.timestamps().validate(jwt).hasErrors()).isFalse();
    }

    @Test
    void expiredTokenIsRejected() {
        var jwt = token().expiresAt(Instant.now().minusSeconds(3600)).build();
        assertThat(AccessTokenValidators.timestamps().validate(jwt).hasErrors()).isTrue();
    }

    @Test
    void futureNotBeforeIsRejected() {
        var jwt = token().expiresAt(Instant.now().plusSeconds(7200))
                .notBefore(Instant.now().plusSeconds(3600)).build();
        assertThat(AccessTokenValidators.timestamps().validate(jwt).hasErrors()).isTrue();
    }

    @Test
    void springDefaultClockSkewIsPreservedForExpirationAndNotBefore() {
        assertThat(AccessTokenValidators.timestamps().validate(
                token().expiresAt(Instant.now().minusSeconds(30)).build()).hasErrors()).isFalse();
        assertThat(AccessTokenValidators.timestamps().validate(token().expiresAt(Instant.now().plusSeconds(300))
                .notBefore(Instant.now().plusSeconds(30)).build()).hasErrors()).isFalse();
    }

    @Test
    void exactIssuerAndAudienceMembershipAcceptMultipleAudiences() {
        var jwt = token().expiresAt(Instant.now().plusSeconds(300)).issuer("https://issuer.test")
                .audience(List.of("unrelated-api", "document-api")).build();
        assertThat(AccessTokenValidators.forIssuerAndAudience("https://issuer.test", "document-api")
                .validate(jwt).hasErrors()).isFalse();
    }

    @Test
    void issuerAndAudienceDoNotUseCaseFoldingOrSuffixAliases() {
        for (var jwt : List.of(
                token().expiresAt(Instant.now().plusSeconds(300)).issuer("https://issuer.test/").audience(List.of("document-api")).build(),
                token().expiresAt(Instant.now().plusSeconds(300)).issuer("https://issuer.test").audience(List.of("DOCUMENT-API")).build(),
                token().expiresAt(Instant.now().plusSeconds(300)).issuer("https://issuer.test").build(),
                token().expiresAt(Instant.now().plusSeconds(300)).audience(List.of("document-api")).build())) {
            assertThat(AccessTokenValidators.forIssuerAndAudience("https://issuer.test", "document-api")
                    .validate(jwt).hasErrors()).isTrue();
        }
    }

    @Test
    void issuerAndAudienceCompositionStillRequiresExpiration() {
        var jwt = token().issuer("https://issuer.test").audience(List.of("document-api")).build();
        assertThat(AccessTokenValidators.forIssuerAndAudience("https://issuer.test", "document-api")
                .validate(jwt).hasErrors()).isTrue();
    }

    @Test
    void nullAndBlankConfigurationRejectImmediately() {
        for (String blank : new String[]{null, "", " ", "\t"}) {
            assertThatIllegalArgumentException().isThrownBy(() ->
                    AccessTokenValidators.forIssuerAndAudience(blank, "document-api"));
            assertThatIllegalArgumentException().isThrownBy(() ->
                    AccessTokenValidators.forIssuerAndAudience("https://issuer.test", blank));
        }
    }
}

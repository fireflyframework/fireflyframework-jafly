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

package org.fireflyframework.client.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

class JwtValidatorLoggingTest {
    private Logger logger;
    private Level previousLevel;
    private ListAppender<ILoggingEvent> appender;
    private String signingKey;

    @BeforeEach
    void captureLogs() throws Exception {
        logger = (Logger) LoggerFactory.getLogger(JwtValidator.class);
        previousLevel = logger.getLevel();
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.DEBUG);
        var generator = KeyGenerator.getInstance("HmacSHA256");
        generator.init(256);
        signingKey = HexFormat.of().formatHex(generator.generateKey().getEncoded());
    }

    @AfterEach
    void restoreLogs() {
        logger.detachAppender(appender);
        logger.setLevel(previousLevel);
        appender.stop();
    }

    @Test
    void validatesTheRealSignatureAndReturnsClaimsWithoutLoggingSubjectOrToken() throws Exception {
        String subject = "synthetic-sensitive-subject-Ω";
        String token =
                signedToken(subject, signingKey, Instant.now().plusSeconds(300).getEpochSecond());
        var claims = validator().validate(token);
        assertThat(claims.getSubject()).isEqualTo(subject);
        assertThat(claims.getIssuer()).isEqualTo("https://synthetic-issuer.example");
        assertThat(claims.getAudience()).isEqualTo("synthetic-audience");
        assertThat(appender.list).isNotEmpty();
        for (var event : appender.list) {
            assertThat(event.getFormattedMessage()).doesNotContain(subject, token, signingKey);
            if (event.getArgumentArray() != null) {
                assertThat(Arrays.toString(event.getArgumentArray()))
                        .doesNotContain(subject, token, signingKey);
            }
        }
    }

    @Test
    void rejectsAnotherKeysSignatureWithoutEmittingASuccessLog() throws Exception {
        String token =
                signedToken(
                        "synthetic-untrusted-subject",
                        signingKey + "different",
                        Instant.now().plusSeconds(300).getEpochSecond());
        assertThatThrownBy(() -> validator().validate(token))
                .isInstanceOf(JwtValidator.JwtValidationException.class)
                .hasMessage("Invalid JWT signature");
        assertThat(appender.list).isEmpty();
    }

    @Test
    void rejectsExpiredSignedClaimsWithoutEmittingASuccessLog() throws Exception {
        String token =
                signedToken(
                        "synthetic-expired-subject",
                        signingKey,
                        Instant.now().minusSeconds(300).getEpochSecond());
        assertThatThrownBy(() -> validator().validate(token))
                .isInstanceOf(JwtValidator.JwtValidationException.class)
                .hasMessage("JWT has expired");
        assertThat(appender.list).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("nontextSubjects")
    void rejectsSignedNontextSubjectsWithAConstantDiagnostic(Object subject) throws Exception {
        Map<String, Object> claims = baseClaims();
        claims.put("sub", subject);
        String token = signedClaims(claims, signingKey);
        assertThatThrownBy(() -> validator().validate(token))
                .isInstanceOf(JwtValidator.JwtValidationException.class)
                .hasMessage("Invalid subject claim");
        assertThat(appender.list).isEmpty();
    }

    static Stream<Object> nontextSubjects() {
        return Stream.of(42, true, Map.of("synthetic", "object"), List.of("synthetic-array"));
    }

    @Test
    void preservesAnAbsentOptionalSubject() throws Exception {
        assertThat(validator().validate(signedClaims(baseClaims(), signingKey)).getSubject())
                .isNull();
    }

    @Test
    void preservesAnExplicitNullOptionalSubject() throws Exception {
        Map<String, Object> claims = baseClaims();
        claims.put("sub", null);
        assertThat(validator().validate(signedClaims(claims, signingKey)).getSubject()).isNull();
    }

    @Test
    void preservesTheExistingRequiredClaimPresenceRule() throws Exception {
        var requiringSubject =
                JwtValidator.builder()
                        .secret(signingKey)
                        .requiredClaims(java.util.Set.of("sub"))
                        .build();
        String absent = signedClaims(baseClaims(), signingKey);
        assertThatThrownBy(() -> requiringSubject.validate(absent))
                .isInstanceOf(JwtValidator.JwtValidationException.class)
                .hasMessage("Missing required claim: sub");
        Map<String, Object> explicitNull = baseClaims();
        explicitNull.put("sub", null);
        assertThat(requiringSubject.validate(signedClaims(explicitNull, signingKey)).getSubject())
                .isNull();
    }

    private static Map<String, Object> baseClaims() {
        return new LinkedHashMap<>(
                Map.of(
                        "iss",
                        "https://synthetic-issuer.example",
                        "aud",
                        "synthetic-audience",
                        "exp",
                        Instant.now().plusSeconds(300).getEpochSecond()));
    }

    private JwtValidator validator() {
        return JwtValidator.builder()
                .secret(signingKey)
                .issuer("https://synthetic-issuer.example")
                .audience("synthetic-audience")
                .build();
    }

    private static String signedToken(String subject, String key, long expiration)
            throws Exception {
        Map<String, Object> claims = baseClaims();
        claims.put("sub", subject);
        claims.put("exp", expiration);
        return signedClaims(claims, key);
    }

    private static String signedClaims(Map<String, Object> claims, String key) throws Exception {
        var encoder = Base64.getUrlEncoder().withoutPadding();
        String header =
                encoder.encodeToString(
                        "{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payload = encoder.encodeToString(new ObjectMapper().writeValueAsBytes(claims));
        String signingInput = header + "." + payload;
        var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return signingInput
                + "."
                + encoder.encodeToString(
                        mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8)));
    }
}

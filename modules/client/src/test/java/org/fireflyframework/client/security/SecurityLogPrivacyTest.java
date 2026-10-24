package org.fireflyframework.client.security;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityLogPrivacyTest {
    @Test
    void storingAndReadingASecretRetainsItsValueWithoutLoggingItsNameOrContent() {
        var manager = manager();
        String name = "synthetic-private-credential-name";
        String value = UUID.randomUUID().toString();
        try (var capture = new LogCapture(SecretsEncryptionManager.class)) {
            manager.storeSecret(name, value);
            assertThat(manager.getSecret(name)).isEqualTo(value);
            assertThat(manager.getSecretCount()).isEqualTo(1);
            capture.assertExcludes(name, value);
        }
    }

    @Test
    void removingASecretRetainsTheRemovalOutcomeWithoutLoggingItsName() {
        var manager = manager();
        String name = "synthetic-private-removal-name";
        manager.storeSecret(name, UUID.randomUUID().toString());
        try (var capture = new LogCapture(SecretsEncryptionManager.class)) {
            assertThat(manager.removeSecret(name)).isTrue();
            assertThat(manager.hasSecret(name)).isFalse();
            assertThat(manager.removeSecret(name)).isFalse();
            capture.assertExcludes(name);
        }
    }

    @Test
    void aMissingSecretRetainsTheAbsentResultWithoutLoggingTheLookupName() {
        var manager = manager();
        String name = "synthetic-private-missing-name";
        try (var capture = new LogCapture(SecretsEncryptionManager.class)) {
            assertThat(manager.getSecret(name)).isNull();
            capture.assertExcludes(name);
        }
    }

    @Test
    void successfulSignatureValidationRetainsClaimsWithoutLoggingTheSubjectOrToken() throws Exception {
        String secret = UUID.randomUUID().toString();
        String subject = "synthetic-private-customer-subject";
        String issuer = "https://issuer.example.invalid";
        String audience = "synthetic-client";
        var mapper = new ObjectMapper();
        var encoder = Base64.getUrlEncoder().withoutPadding();
        String header = encoder.encodeToString(mapper.writeValueAsBytes(Map.of("alg", "HS256", "typ", "JWT")));
        String payload = encoder.encodeToString(mapper.writeValueAsBytes(Map.of(
                "sub", subject, "iss", issuer, "aud", audience,
                "exp", Instant.now().plusSeconds(300).getEpochSecond())));
        String unsigned = header + "." + payload;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String token = unsigned + "." + encoder.encodeToString(mac.doFinal(unsigned.getBytes(StandardCharsets.UTF_8)));
        var validator = JwtValidator.builder().secret(secret).issuer(issuer).audience(audience).build();
        try (var capture = new LogCapture(JwtValidator.class)) {
            assertThat(validator.validate(token).getSubject()).isEqualTo(subject);
            capture.assertExcludes(subject, token, secret);
        }
    }

    private static SecretsEncryptionManager manager() {
        return SecretsEncryptionManager.builder()
                .masterKey(UUID.randomUUID().toString().replace("-", ""))
                .build();
    }

    private static final class LogCapture implements AutoCloseable {
        private final Logger logger;
        private final Level previousLevel;
        private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

        private LogCapture(Class<?> type) {
            logger = (Logger) LoggerFactory.getLogger(type);
            previousLevel = logger.getLevel();
            logger.setLevel(Level.DEBUG);
            appender.start();
            logger.addAppender(appender);
        }

        private void assertExcludes(String... values) {
            assertThat(appender.list).isNotEmpty();
            for (var event : appender.list) {
                String diagnostic = event.getFormattedMessage() + Arrays.deepToString(event.getArgumentArray());
                for (String value : values) {
                    assertThat(diagnostic).doesNotContain(value);
                }
            }
        }

        @Override
        public void close() {
            logger.detachAppender(appender);
            logger.setLevel(previousLevel);
            appender.stop();
        }
    }
}

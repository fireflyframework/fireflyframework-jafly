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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;

class SecretsEncryptionManagerSecurityTest {
    private Logger logger;
    private Level previousLevel;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void captureLogs() {
        logger = (Logger) LoggerFactory.getLogger(SecretsEncryptionManager.class);
        previousLevel = logger.getLevel();
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.DEBUG);
    }

    @AfterEach
    void restoreLogs() {
        logger.detachAppender(appender);
        logger.setLevel(previousLevel);
        appender.stop();
    }

    @Test
    void encryptsUnicodeUsingTheExistingIvCiphertextFormat() {
        var manager = manager();
        String plaintext = "Synthetic text Ω \uD83D\uDE80";
        String encrypted = manager.encrypt(plaintext);
        assertThat(manager.decrypt(encrypted)).isEqualTo(plaintext);
        assertThat(Base64.getDecoder().decode(encrypted))
                .hasSize(
                        12
                                + plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                                + 16);
        assertPrivateLogs(plaintext, encrypted);
    }

    @Test
    void rejectsModifiedAuthenticatedCiphertext() {
        var manager = manager();
        byte[] ciphertext = Base64.getDecoder().decode(manager.encrypt("synthetic tamper control"));
        ciphertext[ciphertext.length - 1] ^= 1;
        assertThatThrownBy(() -> manager.decrypt(Base64.getEncoder().encodeToString(ciphertext)))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Decryption failed");
    }

    @Test
    void storesAndRetrievesWithoutLoggingTheCallerSuppliedSecretName() {
        var manager = manager();
        String label = "synthetic-sensitive-storage-label";
        String value = "synthetic-storage-value-Ω";
        manager.storeSecret(label, value);
        assertThat(manager.getSecret(label)).isEqualTo(value);
        assertThat(manager.hasSecret(label)).isTrue();
        assertPrivateLogs(label, value);
    }

    @Test
    void missingSecretDoesNotLogTheCallerSuppliedName() {
        String label = "synthetic-sensitive-missing-label";
        assertThat(manager().getSecret(label)).isNull();
        assertThat(appender.list).anyMatch(event -> event.getLevel() == Level.WARN);
        assertPrivateLogs(label);
    }

    @Test
    void removalDoesNotLogTheCallerSuppliedName() {
        var manager = manager();
        String label = "synthetic-sensitive-removal-label";
        manager.storeSecret(label, "synthetic-removal-value");
        appender.list.clear();
        assertThat(manager.removeSecret(label)).isTrue();
        assertThat(manager.removeSecret(label)).isFalse();
        assertPrivateLogs(label);
    }

    @Test
    void rotationPreservesBothManagersWithoutLoggingSecretNames() {
        var original = manager();
        String label = "synthetic-sensitive-rotation-label";
        String value = "synthetic-rotation-value-Ω";
        original.storeSecret(label, value);
        appender.list.clear();
        var rotated = original.rotateKey(rawAsciiKey());
        assertThat(rotated.getSecret(label)).isEqualTo(value);
        assertThat(original.getSecret(label)).isEqualTo(value);
        assertThat(rotated.getSecretCount()).isEqualTo(1);
        assertPrivateLogs(label, value);
    }

    @Test
    void checkedCombinedLengthPreservesExactNonnegativeBoundariesWithoutAllocating() {
        assertThat(SecretsEncryptionManager.checkedCombinedLength(0, 0)).isZero();
        assertThat(SecretsEncryptionManager.checkedCombinedLength(12, 32)).isEqualTo(44);
        assertThat(SecretsEncryptionManager.checkedCombinedLength(12, Integer.MAX_VALUE - 12))
                .isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void checkedCombinedLengthRejectsOverflowBeforeCombinedAllocation() {
        assertThatThrownBy(
                        () ->
                                SecretsEncryptionManager.checkedCombinedLength(
                                        12, Integer.MAX_VALUE - 11))
                .isInstanceOf(ArithmeticException.class);
        assertThatThrownBy(
                        () ->
                                SecretsEncryptionManager.checkedCombinedLength(
                                        Integer.MAX_VALUE, Integer.MAX_VALUE))
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    void checkedCombinedLengthRejectsNegativeLengths() {
        assertThatThrownBy(() -> SecretsEncryptionManager.checkedCombinedLength(-1, 12))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SecretsEncryptionManager.checkedCombinedLength(12, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void assertPrivateLogs(String... sensitiveValues) {
        assertThat(appender.list).isNotEmpty();
        for (var event : appender.list) {
            assertThat(event.getFormattedMessage()).doesNotContain(sensitiveValues);
            if (event.getArgumentArray() != null) {
                assertThat(Arrays.toString(event.getArgumentArray()))
                        .doesNotContain(sensitiveValues);
            }
        }
    }

    private static SecretsEncryptionManager manager() {
        return SecretsEncryptionManager.builder().masterKey(rawAsciiKey()).build();
    }

    private static String rawAsciiKey() {
        byte[] random = new byte[16];
        new SecureRandom().nextBytes(random);
        return HexFormat.of().formatHex(random);
    }
}

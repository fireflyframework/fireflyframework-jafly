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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;
import java.util.stream.Stream;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

class SecretsEncryptionManagerKeyEncodingTest {
    @Test
    void generatedFactoryKeyEncryptsAndDecryptsUnicode() {
        assertThatCode(() -> roundTrip(SecretsEncryptionManager.create())).doesNotThrowAnyException();
    }

    @Test
    void generatedBase64KeyCanConfigureThePublicBuilder() {
        String generated = SecretsEncryptionManager.generateMasterKey();
        assertThat(Base64.getDecoder().decode(generated)).hasSize(32);
        assertThatCode(() -> roundTrip(manager(generated))).doesNotThrowAnyException();
    }

    @Test
    void rotationAcceptsAGeneratedKeyAndPreservesBothManagers() {
        var original = manager(rawAsciiKey());
        String label = "synthetic-rotation-label";
        String value = "Synthetic rotation value Ω";
        original.storeSecret(label, value);
        assertThatCode(() -> {
            var rotated = original.rotateKey(SecretsEncryptionManager.generateMasterKey());
            assertThat(rotated.getSecret(label)).isEqualTo(value);
            assertThat(original.getSecret(label)).isEqualTo(value);
            assertThat(rotated.getSecretCount()).isEqualTo(1);
        }).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rawRepresentations")
    void preservesExistingThirtyTwoByteUtf8Keys(String description, String key) {
        assertThat(key.getBytes(StandardCharsets.UTF_8)).hasSize(32);
        roundTrip(manager(key));
    }

    static Stream<Arguments> rawRepresentations() {
        var random = new SecureRandom();
        char[] unicode = new char[16];
        for (int i = 0; i < unicode.length; i++) {
            unicode[i] = (char) (0x100 + random.nextInt(0x80));
        }
        return Stream.of(Arguments.of("raw ASCII", rawAsciiKey()),
                Arguments.of("raw multibyte UTF-8", new String(unicode)));
    }

    @Test
    void decryptsTheExistingIvAndCiphertextFrameWithARawKey() throws Exception {
        String key = rawAsciiKey();
        String plaintext = "Synthetic existing frame Ω";
        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES"),
                new GCMParameterSpec(128, iv));
        var frame = new ByteArrayOutputStream();
        frame.writeBytes(iv);
        frame.writeBytes(cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8)));
        assertThat(manager(key).decrypt(Base64.getEncoder().encodeToString(frame.toByteArray())))
                .isEqualTo(plaintext);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidConfigurations")
    void rejectsUnsupportedKeyMaterial(String description, String key) {
        assertThatThrownBy(() -> manager(key).encrypt("synthetic plaintext"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Encryption failed")
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    static Stream<Arguments> invalidConfigurations() {
        byte[] shortMaterial = new byte[16];
        new SecureRandom().nextBytes(shortMaterial);
        return Stream.of(Arguments.of("missing", (String) null),
                Arguments.of("empty", ""),
                Arguments.of("Base64 of non-AES256 material", Base64.getEncoder().encodeToString(shortMaterial)),
                Arguments.of("invalid text", "!" + UUID.randomUUID()));
    }

    private static void roundTrip(SecretsEncryptionManager manager) {
        String value = "Synthetic generated-key value Ω 🚀";
        assertThat(manager.decrypt(manager.encrypt(value))).isEqualTo(value);
    }

    private static SecretsEncryptionManager manager(String key) {
        return SecretsEncryptionManager.builder().masterKey(key).build();
    }

    private static String rawAsciiKey() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}

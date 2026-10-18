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

package org.fireflyframework.security.core.key;

import org.fireflyframework.security.api.domain.SigningKey;
import org.fireflyframework.security.spi.KeyManagementPort;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Development-default {@link KeyManagementPort}: generates an in-process RS256 key pair and serves
 * a JWKS for it. Rotation keeps the previous public key for an overlap window so in-flight tokens
 * remain verifiable. <strong>Not for production</strong> — production deployments bind a Vault or
 * cloud-KMS adapter; the autoconfigure layer fails closed if no key source resolves under prod profiles.
 */
public class InMemoryKeyManagementAdapter implements KeyManagementPort {

    private static final String ALGORITHM = "RS256";
    private static final int OVERLAP_KEYS = 2;

    private final List<SigningKey> keys = new CopyOnWriteArrayList<>();

    public InMemoryKeyManagementAdapter() {
        keys.add(generate());
    }

    @Override
    public Mono<SigningKey> activeSigningKey() {
        return Mono.fromSupplier(() -> keys.get(0));
    }

    @Override
    public Flux<SigningKey> verificationKeys() {
        return Flux.fromIterable(keys);
    }

    @Override
    public Mono<String> jwkSetJson() {
        return Mono.fromSupplier(() -> keys.stream()
                .map(InMemoryKeyManagementAdapter::toJwkJson)
                .collect(Collectors.joining(",", "{\"keys\":[", "]}")));
    }

    @Override
    public Mono<Void> rotate() {
        return Mono.fromRunnable(() -> {
            keys.add(0, generate());
            while (keys.size() > OVERLAP_KEYS) {
                keys.remove(keys.size() - 1);
            }
        });
    }

    private static SigningKey generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();
            return new SigningKey(UUID.randomUUID().toString(), ALGORITHM, pair.getPrivate(), pair.getPublic());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA key generation unavailable", e);
        }
    }

    private static String toJwkJson(SigningKey key) {
        RSAPublicKey publicKey = (RSAPublicKey) key.publicKey();
        String n = base64Url(publicKey.getModulus());
        String e = base64Url(publicKey.getPublicExponent());
        return "{\"kty\":\"RSA\",\"use\":\"sig\",\"alg\":\"" + key.algorithm()
                + "\",\"kid\":\"" + key.kid() + "\",\"n\":\"" + n + "\",\"e\":\"" + e + "\"}";
    }

    private static String base64Url(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            byte[] trimmed = new byte[bytes.length - 1];
            System.arraycopy(bytes, 1, trimmed, 0, trimmed.length);
            bytes = trimmed;
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}

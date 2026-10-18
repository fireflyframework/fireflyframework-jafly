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

package org.fireflyframework.security.idp.internaldb.service;

import dev.samstevens.totp.code.*;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;
import org.fireflyframework.security.idp.internaldb.config.InternalDbProperties;
import org.fireflyframework.security.idp.internaldb.domain.User;
import org.fireflyframework.security.idp.internaldb.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Service for TOTP (Time-based One-Time Password) multi-factor authentication.
 *
 * <p>Supports:
 * <ul>
 *   <li>TOTP secret generation and QR code URI creation</li>
 *   <li>TOTP code verification with configurable time window</li>
 *   <li>Enable/disable MFA per user</li>
 * </ul>
 */
@Slf4j
public class MfaService {

    private final UserRepository userRepository;
    private final InternalDbProperties properties;
    private final SecretGenerator secretGenerator;
    private final CodeVerifier codeVerifier;

    public MfaService(UserRepository userRepository, InternalDbProperties properties) {
        this.userRepository = userRepository;
        this.properties = properties;
        this.secretGenerator = new DefaultSecretGenerator();

        TimeProvider timeProvider = new SystemTimeProvider();
        CodeGenerator codeGenerator = new DefaultCodeGenerator(HashingAlgorithm.SHA1, 6);
        this.codeVerifier = new DefaultCodeVerifier(codeGenerator, timeProvider);
    }

    /**
     * Enables MFA for a user by generating a TOTP secret and returning the QR code URI.
     *
     * @param userId the user ID
     * @return a Mono emitting the otpauth:// URI for QR code generation
     */
    public Mono<String> enableMfa(UUID userId) {
        return userRepository.findById(userId)
                .switchIfEmpty(Mono.error(new RuntimeException("User not found")))
                .flatMap(user -> {
                    user.markAsNotNew();
                    String secret = secretGenerator.generate();
                    user.setMfaSecret(secret);
                    user.setMfaEnabled(true);

                    return userRepository.save(user)
                            .map(saved -> {
                                QrData qrData = new QrData.Builder()
                                        .label(user.getEmail() != null ? user.getEmail() : user.getUsername())
                                        .secret(secret)
                                        .issuer(properties.getJwt().getIssuer())
                                        .algorithm(HashingAlgorithm.SHA1)
                                        .digits(6)
                                        .period(30)
                                        .build();

                                String uri = qrData.getUri();
                                log.info("MFA enabled for user: {}", user.getUsername());
                                return uri;
                            });
                });
    }

    /**
     * Verifies a TOTP code against the user's stored secret.
     *
     * @param userId the user ID
     * @param code the TOTP code to verify
     * @return a Mono emitting true if the code is valid
     */
    public Mono<Boolean> verifyTotp(UUID userId, String code) {
        return userRepository.findById(userId)
                .switchIfEmpty(Mono.error(new RuntimeException("User not found")))
                .map(user -> {
                    if (!user.getMfaEnabled() || user.getMfaSecret() == null) {
                        throw new RuntimeException("MFA is not enabled for this user");
                    }
                    return codeVerifier.isValidCode(user.getMfaSecret(), code);
                });
    }

    /**
     * Verifies a TOTP code against a user looked up by username.
     *
     * @param username the username
     * @param code the TOTP code
     * @return a Mono emitting true if valid
     */
    public Mono<Boolean> verifyTotpByUsername(String username, String code) {
        return userRepository.findByUsername(username)
                .switchIfEmpty(Mono.error(new RuntimeException("User not found")))
                .map(user -> {
                    if (!user.getMfaEnabled() || user.getMfaSecret() == null) {
                        throw new RuntimeException("MFA is not enabled for this user");
                    }
                    return codeVerifier.isValidCode(user.getMfaSecret(), code);
                });
    }

    /**
     * Disables MFA for a user, clearing the stored secret.
     *
     * @param userId the user ID
     * @return a Mono signaling completion
     */
    public Mono<Void> disableMfa(UUID userId) {
        return userRepository.findById(userId)
                .switchIfEmpty(Mono.error(new RuntimeException("User not found")))
                .flatMap(user -> {
                    user.markAsNotNew();
                    user.setMfaSecret(null);
                    user.setMfaEnabled(false);
                    return userRepository.save(user);
                })
                .doOnSuccess(v -> log.info("MFA disabled for user: {}", userId))
                .then();
    }

    /**
     * Checks if a user has MFA enabled.
     *
     * @param username the username
     * @return a Mono emitting true if MFA is enabled
     */
    public Mono<Boolean> isMfaEnabled(String username) {
        return userRepository.findByUsername(username)
                .map(user -> user.getMfaEnabled() != null && user.getMfaEnabled())
                .defaultIfEmpty(false);
    }
}

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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.fireflyframework.security.idp.internaldb.config.InternalDbProperties;
import org.fireflyframework.security.idp.internaldb.domain.PasswordResetToken;
import org.fireflyframework.security.idp.internaldb.repository.PasswordResetTokenRepository;
import org.fireflyframework.security.idp.internaldb.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Service for secure password reset flows using token-based verification.
 * Generates hashed reset tokens stored in the database with configurable expiry and rate limiting.
 *
 * <p>Security measures:</p>
 * <ul>
 *   <li>Tokens are stored as SHA-256 hashes — raw tokens are never persisted</li>
 *   <li>Rate limiting: max 3 reset requests per hour per user</li>
 *   <li>Tokens expire after configurable duration (default: 1 hour)</li>
 *   <li>Tokens are single-use and marked as used after completion</li>
 * </ul>
 */
@RequiredArgsConstructor
@Slf4j
public class PasswordResetService {

    private static final int MAX_RESETS_PER_HOUR = 3;

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final UserManagementService userManagementService;
    private final PasswordEncoder passwordEncoder;
    private final InternalDbProperties properties;

    /**
     * Initiates a password reset by generating a token, storing the hash, and logging the token ID.
     * In production, an email notification module should be wired to send the reset link.
     *
     * @param username the username requesting password reset
     * @return Mono that completes when the reset is initiated
     */
    public Mono<Void> initiateReset(String username) {
        return userRepository.findByUsername(username)
                .switchIfEmpty(Mono.defer(() -> {
                    // Don't reveal whether the user exists — log and return silently
                    log.debug("Password reset requested for non-existent user: {}", username);
                    return Mono.empty();
                }))
                .flatMap(user -> {
                    // Rate limiting: count recent reset requests
                    LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
                    return tokenRepository.countByUserIdAndCreatedAtAfter(user.getId(), oneHourAgo)
                            .flatMap(count -> {
                                if (count >= MAX_RESETS_PER_HOUR) {
                                    log.warn("Rate limit exceeded for password reset: user={}", username);
                                    return Mono.error(new IllegalStateException(
                                            "Too many password reset requests. Please try again later."));
                                }

                                // Generate token
                                String rawToken = UUID.randomUUID().toString();
                                String tokenHash = hashToken(rawToken);
                                Duration tokenExpiry = getTokenExpiry();

                                PasswordResetToken resetToken = PasswordResetToken.builder()
                                        .id(UUID.randomUUID())
                                        .userId(user.getId())
                                        .tokenHash(tokenHash)
                                        .expiresAt(LocalDateTime.now().plus(tokenExpiry))
                                        .used(false)
                                        .createdAt(LocalDateTime.now())
                                        .isNewEntity(true)
                                        .build();

                                return tokenRepository.save(resetToken)
                                        .doOnSuccess(saved -> {
                                            // SECURITY: Log the reset token ID for audit, NEVER the raw token
                                            log.info("Password reset token created: tokenId={}, user={}, expiresAt={}",
                                                    saved.getId(), username, saved.getExpiresAt());
                                            // TODO: Wire email notification module to send reset link
                                            // emailProvider.sendPasswordResetEmail(user.getEmail(), rawToken);
                                        })
                                        .then();
                            });
                })
                .then();
    }

    /**
     * Completes a password reset using the raw token and new password.
     *
     * @param rawToken the raw reset token from the reset link
     * @param newPassword the new password
     * @return Mono that completes when the password is reset
     */
    public Mono<Void> completeReset(String rawToken, String newPassword) {
        String tokenHash = hashToken(rawToken);

        return tokenRepository.findByTokenHash(tokenHash)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Invalid or expired reset token")))
                .flatMap(token -> {
                    if (token.isUsed()) {
                        return Mono.error(new IllegalArgumentException("Reset token has already been used"));
                    }
                    if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
                        return Mono.error(new IllegalArgumentException("Reset token has expired"));
                    }

                    // Mark token as used
                    token.setUsed(true);
                    token.markAsNotNew();

                    return tokenRepository.save(token)
                            .then(userRepository.findById(token.getUserId()))
                            .flatMap(user -> userManagementService.resetPassword(
                                    user.getUsername(), newPassword))
                            .doOnSuccess(v -> log.info("Password reset completed for userId={}",
                                    token.getUserId()));
                });
    }

    private Duration getTokenExpiry() {
        if (properties.getPasswordReset() != null
                && properties.getPasswordReset().getTokenExpiry() != null) {
            return properties.getPasswordReset().getTokenExpiry();
        }
        return Duration.ofHours(1);
    }

    static String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}

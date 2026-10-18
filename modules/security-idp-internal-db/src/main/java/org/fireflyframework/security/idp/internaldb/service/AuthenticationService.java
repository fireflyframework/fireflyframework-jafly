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

import org.fireflyframework.security.idp.dtos.TokenResponse;
import org.fireflyframework.security.idp.internaldb.config.InternalDbProperties;
import org.fireflyframework.security.idp.internaldb.domain.RefreshToken;
import org.fireflyframework.security.idp.internaldb.domain.Session;
import org.fireflyframework.security.idp.internaldb.domain.User;
import org.fireflyframework.security.idp.internaldb.repository.RefreshTokenRepository;
import org.fireflyframework.security.idp.internaldb.repository.SessionRepository;
import org.fireflyframework.security.idp.internaldb.repository.UserRepository;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Service for authentication operations including login, token refresh, and logout.
 */
@RequiredArgsConstructor
@Slf4j
public class AuthenticationService {

    private final UserRepository userRepository;
    private final SessionRepository sessionRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RoleService roleService;
    private final JwtTokenService jwtTokenService;
    private final PasswordEncoder passwordEncoder;
    private final InternalDbProperties properties;

    /**
     * Authenticate a user with username and password.
     *
     * <p>Authentication pipeline:
     * <ol>
     *   <li>Find user by username</li>
     *   <li>Check if account is enabled</li>
     *   <li>Check automatic lockout (lockedUntil) and manual lock (accountNonLocked)</li>
     *   <li>Verify password — on failure, increment failed attempts and lock if threshold reached</li>
     *   <li>On success, reset failed attempts and generate tokens</li>
     * </ol>
     *
     * @param username the username
     * @param password the password
     * @return a Mono emitting the token response
     */
    public Mono<TokenResponse> authenticate(String username, String password) {
        log.debug("Authenticating user: {}", username);

        return userRepository.findByUsername(username)
                .doOnNext(user -> user.markAsNotNew())
                .switchIfEmpty(Mono.error(new RuntimeException("Invalid username or password")))
                .flatMap(user -> {
                    if (!user.getEnabled()) {
                        return Mono.error(new RuntimeException("User account is disabled"));
                    }

                    // Check manual lock
                    if (!user.getAccountNonLocked()) {
                        return Mono.error(new RuntimeException("User account is locked"));
                    }

                    // Check automatic lockout
                    if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
                        log.warn("Login attempt for locked user: {} (locked until {})", username, user.getLockedUntil());
                        return Mono.error(new RuntimeException("Account is temporarily locked. Try again later."));
                    }

                    // Clear expired lockout
                    if (user.getLockedUntil() != null && user.getLockedUntil().isBefore(LocalDateTime.now())) {
                        user.setLockedUntil(null);
                        user.setFailedLoginAttempts(0);
                    }

                    // Verify password
                    if (!passwordEncoder.matches(password, user.getPasswordHash())) {
                        return handleFailedLogin(user);
                    }

                    // Password correct — reset failed attempts
                    user.setFailedLoginAttempts(0);
                    user.setLockedUntil(null);

                    return generateTokens(user);
                })
                .flatMap(tokenResponse ->
                    userRepository.findByUsername(username)
                            .doOnNext(user -> user.markAsNotNew())
                            .flatMap(user -> {
                                user.setLastLoginAt(LocalDateTime.now());
                                return userRepository.save(user);
                            })
                            .thenReturn(tokenResponse)
                );
    }

    /**
     * Handles a failed login attempt by incrementing the counter and locking if threshold is reached.
     */
    private Mono<TokenResponse> handleFailedLogin(User user) {
        int attempts = (user.getFailedLoginAttempts() != null ? user.getFailedLoginAttempts() : 0) + 1;
        user.setFailedLoginAttempts(attempts);

        int maxAttempts = properties.getLockout().getMaxFailedAttempts();
        if (attempts >= maxAttempts) {
            LocalDateTime lockUntil = LocalDateTime.now().plus(properties.getLockout().getLockoutDuration());
            user.setLockedUntil(lockUntil);
            log.warn("Account locked for user: {} after {} failed attempts (locked until {})",
                    user.getUsername(), attempts, lockUntil);
        }

        return userRepository.save(user)
                .then(Mono.error(new RuntimeException("Invalid username or password")));
    }

    /**
     * Refresh access token using a refresh token.
     *
     * @param refreshTokenString the refresh token
     * @return a Mono emitting the new token response
     */
    public Mono<TokenResponse> refreshToken(String refreshTokenString) {
        log.debug("Refreshing access token");

        try {
            Claims claims = jwtTokenService.parseToken(refreshTokenString);
            String jti = claims.getId();
            UUID userId = UUID.fromString(claims.getSubject());

            return refreshTokenRepository.findByTokenJti(jti)
                    .doOnNext(refreshToken -> refreshToken.markAsNotNew())
                    .switchIfEmpty(Mono.error(new RuntimeException("Invalid refresh token")))
                    .flatMap(refreshToken -> {
                        if (refreshToken.getRevoked()) {
                            return Mono.error(new RuntimeException("Refresh token has been revoked"));
                        }
                        if (refreshToken.getExpiresAt().isBefore(LocalDateTime.now())) {
                            return Mono.error(new RuntimeException("Refresh token has expired"));
                        }

                        // Update last used time
                        refreshToken.setLastUsedAt(LocalDateTime.now());
                        return refreshTokenRepository.save(refreshToken)
                                .then(userRepository.findById(userId))
                                .doOnNext(user -> user.markAsNotNew());
                    })
                    .switchIfEmpty(Mono.error(new RuntimeException("User not found")))
                    .flatMap(this::generateTokens);

        } catch (Exception e) {
            log.error("Failed to refresh token", e);
            return Mono.error(new RuntimeException("Invalid refresh token"));
        }
    }

    /**
     * Logout a user by revoking their tokens.
     *
     * @param accessToken the access token
     * @param refreshTokenString the refresh token (optional)
     * @return a Mono signaling completion
     */
    public Mono<Void> logout(String accessToken, String refreshTokenString) {
        log.debug("Logging out user");

        try {
            String jti = jwtTokenService.extractJti(accessToken);
            if (jti == null) {
                return Mono.error(new RuntimeException("Invalid access token"));
            }

            Mono<Void> revokeSession = sessionRepository.findByAccessTokenJti(jti)
                    .doOnNext(session -> session.markAsNotNew())
                    .flatMap(session -> {
                        session.setRevoked(true);
                        session.setRevokedAt(LocalDateTime.now());
                        return sessionRepository.save(session);
                    })
                    .then();

            Mono<Void> revokeRefreshToken = Mono.empty();
            if (refreshTokenString != null && !refreshTokenString.isEmpty()) {
                String refreshJti = jwtTokenService.extractJti(refreshTokenString);
                if (refreshJti != null) {
                    revokeRefreshToken = refreshTokenRepository.findByTokenJti(refreshJti)
                            .doOnNext(refreshToken -> refreshToken.markAsNotNew())
                            .flatMap(refreshToken -> {
                                refreshToken.setRevoked(true);
                                refreshToken.setRevokedAt(LocalDateTime.now());
                                return refreshTokenRepository.save(refreshToken);
                            })
                            .then();
                }
            }

            return revokeSession.then(revokeRefreshToken);

        } catch (Exception e) {
            log.error("Failed to logout", e);
            return Mono.error(new RuntimeException("Logout failed"));
        }
    }

    /**
     * Generate access and refresh tokens for a user.
     *
     * @param user the user
     * @return a Mono emitting the token response
     */
    private Mono<TokenResponse> generateTokens(User user) {
        return roleService.getUserRoles(user.getId())
                .collectList()
                .flatMap(roles -> {
                    // Generate tokens
                    String accessToken = jwtTokenService.generateAccessToken(user, roles);
                    String accessTokenJti = jwtTokenService.extractJti(accessToken);

                    // Create session
                    LocalDateTime now = LocalDateTime.now();
                    Session session = Session.builder()
                            .id(UUID.randomUUID())
                            .userId(user.getId())
                            .accessTokenJti(accessTokenJti)
                            .createdAt(now)
                            .expiresAt(now.plus(
                                    properties.getJwt().getAccessTokenExpiration(),
                                    ChronoUnit.MILLIS))
                            .revoked(false)
                            .build();

                    return sessionRepository.save(session)
                            .doOnNext(saved -> saved.markAsNotNew())
                            .flatMap(savedSession -> {
                                // Generate refresh token
                                String refreshToken = jwtTokenService.generateRefreshToken(user, savedSession.getId());
                                String refreshTokenJti = jwtTokenService.extractJti(refreshToken);

                                // Save refresh token (reuse now from outer scope)
                                RefreshToken refreshTokenEntity = RefreshToken.builder()
                                        .id(UUID.randomUUID())
                                        .userId(user.getId())
                                        .tokenJti(refreshTokenJti)
                                        .tokenHash(passwordEncoder.encode(refreshToken))
                                        .sessionId(savedSession.getId())
                                        .createdAt(now)
                                        .expiresAt(now.plus(
                                                properties.getJwt().getRefreshTokenExpiration(),
                                                ChronoUnit.MILLIS))
                                        .revoked(false)
                                        .build();

                                return refreshTokenRepository.save(refreshTokenEntity)
                                        .doOnNext(saved -> saved.markAsNotNew())
                                        .thenReturn(TokenResponse.builder()
                                                .accessToken(accessToken)
                                                .refreshToken(refreshToken)
                                                .tokenType("Bearer")
                                                .expiresIn(properties.getJwt().getAccessTokenExpiration() / 1000)
                                                .build());
                            });
                });
    }

    /**
     * Validate an access token.
     *
     * @param accessToken the access token
     * @return a Mono emitting true if valid
     */
    public Mono<Boolean> validateAccessToken(String accessToken) {
        try {
            if (!jwtTokenService.validateToken(accessToken)) {
                return Mono.just(false);
            }

            String jti = jwtTokenService.extractJti(accessToken);
            if (jti == null) {
                return Mono.just(false);
            }

            return sessionRepository.findByAccessTokenJti(jti)
                    .map(session -> !session.getRevoked() && session.getExpiresAt().isAfter(LocalDateTime.now()))
                    .defaultIfEmpty(false);

        } catch (Exception e) {
            log.error("Failed to validate access token", e);
            return Mono.just(false);
        }
    }

    /**
     * Revoke a refresh token.
     *
     * @param refreshTokenString the refresh token to revoke
     * @return a Mono signaling completion
     */
    public Mono<Void> revokeRefreshToken(String refreshTokenString) {
        try {
            String jti = jwtTokenService.extractJti(refreshTokenString);
            if (jti == null) {
                return Mono.error(new RuntimeException("Invalid refresh token"));
            }

            return refreshTokenRepository.findByTokenJti(jti)
                    .doOnNext(refreshToken -> refreshToken.markAsNotNew())
                    .switchIfEmpty(Mono.error(new RuntimeException("Refresh token not found")))
                    .flatMap(refreshToken -> {
                        refreshToken.setRevoked(true);
                        refreshToken.setRevokedAt(LocalDateTime.now());
                        return refreshTokenRepository.save(refreshToken);
                    })
                    .then();

        } catch (Exception e) {
            log.error("Failed to revoke refresh token", e);
            return Mono.error(new RuntimeException("Failed to revoke refresh token"));
        }
    }
}

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

package org.fireflyframework.security.idp.internaldb.repository;

import org.fireflyframework.security.idp.internaldb.domain.PasswordResetToken;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Reactive repository for password reset token operations.
 */
@Repository
public interface PasswordResetTokenRepository extends R2dbcRepository<PasswordResetToken, UUID> {

    Mono<PasswordResetToken> findByTokenHash(String tokenHash);

    @Query("SELECT COUNT(*) FROM idp_password_reset_tokens WHERE user_id = :userId AND created_at > :since")
    Mono<Long> countByUserIdAndCreatedAtAfter(UUID userId, LocalDateTime since);

    @Modifying
    @Query("DELETE FROM idp_password_reset_tokens WHERE expires_at < :now")
    Mono<Integer> deleteExpired(LocalDateTime now);
}

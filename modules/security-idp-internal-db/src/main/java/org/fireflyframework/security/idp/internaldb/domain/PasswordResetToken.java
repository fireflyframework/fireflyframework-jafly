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

package org.fireflyframework.security.idp.internaldb.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity representing a password reset token.
 * Tokens are stored as SHA-256 hashes for security.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("idp_password_reset_tokens")
public class PasswordResetToken implements Persistable<UUID> {

    @Id
    @Column("id")
    private UUID id;

    @Column("user_id")
    private UUID userId;

    @Column("token_hash")
    private String tokenHash;

    @Column("expires_at")
    private LocalDateTime expiresAt;

    @Column("used")
    private boolean used;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Transient
    private Boolean isNewEntity;

    @Override
    public boolean isNew() {
        return isNewEntity == null || isNewEntity;
    }

    public void markAsNotNew() {
        this.isNewEntity = false;
    }
}

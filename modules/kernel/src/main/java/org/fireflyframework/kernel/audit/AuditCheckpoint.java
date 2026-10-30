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
package org.fireflyframework.kernel.audit;

/**
 * A checkpoint value, not proof of its custody or signature. Verification callers must obtain
 * non-genesis checkpoints from an independently trusted source, not the range being checked.
 */
public record AuditCheckpoint(AuditStream stream, long lastSequence, String lastHash) {
    private static final String GENESIS_HASH = "0".repeat(64);

    public AuditCheckpoint {
        AuditValidation.required(stream);
        AuditValidation.digest(lastHash);
        if (lastSequence < 0 || (lastSequence == 0 && !GENESIS_HASH.equals(lastHash)))
            throw new IllegalArgumentException("Invalid audit checkpoint sequence");
    }

    public static AuditCheckpoint genesis(AuditStream stream) {
        return new AuditCheckpoint(stream, 0, GENESIS_HASH);
    }
}

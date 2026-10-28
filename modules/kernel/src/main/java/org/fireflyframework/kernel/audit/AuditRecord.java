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

import java.time.Instant;

/** Unverified immutable evidence value; construction does not attest persistence or hash correctness. */
public record AuditRecord(AuditEvent event, long sequence, Instant recordedAt,
                          String previousHash, String recordHash) {
    public AuditRecord {
        AuditValidation.required(event);
        AuditValidation.required(recordedAt);
        AuditValidation.digest(previousHash);
        AuditValidation.digest(recordHash);
        if (sequence < 1) throw new IllegalArgumentException("Invalid audit record sequence");
    }
}

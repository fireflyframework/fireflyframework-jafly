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

import java.util.List;

/** Evidence integrity between trusted checkpoints; never an authorization or durability check. */
public interface AuditEvidenceVerifier {
    int MAX_RANGE_RECORDS = 1000;

    /**
     * Verifies a complete, ordered range of at most {@value #MAX_RANGE_RECORDS} records.
     * Event identity uniqueness outside this range must be enforced by the durable store.
     * @throws IllegalArgumentException if the range is invalid or inconsistent
     */
    void verify(AuditCheckpoint trustedStart, AuditCheckpoint trustedEnd, List<AuditRecord> records);
}

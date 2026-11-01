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
package org.fireflyframework.kernel.audit.impl;

import org.fireflyframework.kernel.audit.AuditCheckpoint;
import org.fireflyframework.kernel.audit.AuditEvidenceVerifier;
import org.fireflyframework.kernel.audit.AuditRecord;
import org.fireflyframework.kernel.audit.AuditRecordHasher;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class Sha256AuditEvidenceVerifierImpl implements AuditEvidenceVerifier {
    @Override
    public void verify(AuditCheckpoint trustedStart, AuditCheckpoint trustedEnd, List<AuditRecord> records) {
        Objects.requireNonNull(trustedStart, "Required audit checkpoint is absent");
        Objects.requireNonNull(trustedEnd, "Required audit checkpoint is absent");
        Objects.requireNonNull(records, "Required audit range is absent");
        long count = trustedEnd.lastSequence() - trustedStart.lastSequence();
        if (!trustedStart.stream().equals(trustedEnd.stream()) || count < 0 || count > MAX_RANGE_RECORDS
                || records.size() != count)
            throw new IllegalArgumentException("Invalid audit range bounds");
        List<AuditRecord> snapshot = List.copyOf(records);
        var identities = new HashSet<UUID>();
        AuditCheckpoint previous = trustedStart;
        for (AuditRecord record : snapshot) {
            AuditRecord expected = AuditRecordHasher.record(previous, record.event(), record.recordedAt());
            if (record.sequence() != expected.sequence() || !record.previousHash().equals(expected.previousHash())
                    || !record.recordHash().equals(expected.recordHash()) || !identities.add(record.event().eventId()))
                throw new IllegalArgumentException("Audit range integrity mismatch");
            previous = new AuditCheckpoint(record.event().stream(), record.sequence(), record.recordHash());
        }
        if (!previous.equals(trustedEnd)) throw new IllegalArgumentException("Audit terminal checkpoint mismatch");
    }
}

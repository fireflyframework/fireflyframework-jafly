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

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Pure canonical SHA-256 evidence construction. Does not reserve sequence numbers, append data,
 * sign a checkpoint, validate actor claims or establish an immutable storage boundary.
 */
public final class AuditRecordHasher {
    private AuditRecordHasher() { }

    public static AuditRecord record(AuditCheckpoint previous, AuditEvent event, Instant recordedAt) {
        AuditValidation.required(previous);
        AuditValidation.required(event);
        AuditValidation.required(recordedAt);
        if (!previous.stream().equals(event.stream()) || previous.lastSequence() == Long.MAX_VALUE)
            throw new IllegalArgumentException("Invalid audit stream or exhausted sequence");
        long sequence = previous.lastSequence() + 1;
        try {
            var bytes = new ByteArrayOutputStream();
            var out = new DataOutputStream(bytes);
            text(out, "firefly.audit.record");
            out.writeInt(1);
            uuid(out, event.stream().resourceTenantId());
            text(out, event.stream().service());
            text(out, event.stream().resourceType());
            uuid(out, event.stream().resourceId());
            out.writeLong(sequence);
            text(out, previous.lastHash());
            instant(out, recordedAt);
            uuid(out, event.eventId());
            text(out, event.actor().issuer());
            text(out, event.actor().subject());
            text(out, event.actor().clientId());
            text(out, event.actor().platformTenantId());
            out.writeBoolean(event.actor().staffId() != null);
            if (event.actor().staffId() != null) {
                text(out, event.actor().staffRealm());
                uuid(out, event.actor().staffId());
            }
            text(out, event.action());
            text(out, event.outcome().name());
            uuid(out, event.processId());
            uuid(out, event.correlationId());
            out.writeBoolean(event.causationId() != null);
            if (event.causationId() != null) uuid(out, event.causationId());
            instant(out, event.occurredAt());
            text(out, event.evidenceDigest());
            text(out, event.policyRevision());
            out.flush();
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
            return new AuditRecord(event, sequence, recordedAt, previous.lastHash(), hash);
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Audit evidence encoding is unavailable", exception);
        }
    }

    private static void text(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private static void uuid(DataOutputStream out, UUID value) throws IOException {
        out.writeLong(value.getMostSignificantBits());
        out.writeLong(value.getLeastSignificantBits());
    }

    private static void instant(DataOutputStream out, Instant value) throws IOException {
        out.writeLong(value.getEpochSecond());
        out.writeInt(value.getNano());
    }
}

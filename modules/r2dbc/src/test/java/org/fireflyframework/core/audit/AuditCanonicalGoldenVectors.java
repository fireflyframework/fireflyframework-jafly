/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package org.fireflyframework.core.audit;

import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.security.MessageDigestSpi;
import java.security.Provider;
import java.security.Security;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.fireflyframework.kernel.audit.AuditActor;
import org.fireflyframework.kernel.audit.AuditCheckpoint;
import org.fireflyframework.kernel.audit.AuditEvent;
import org.fireflyframework.kernel.audit.AuditRecord;
import org.fireflyframework.kernel.audit.AuditRecordHasher;
import org.fireflyframework.kernel.audit.AuditStream;

/** Test-only capture of the unchanged hasher's bytes, also checked without the capture provider. */
public final class AuditCanonicalGoldenVectors {
    private static final String PROVIDER = "FireflyAuditGoldenCapture";
    private static final ThreadLocal<byte[]> CAPTURED = new ThreadLocal<>();
    private static final UUID ZERO = new UUID(0, 0);
    private static final UUID HIGH = new UUID(Long.MIN_VALUE, -1);

    private AuditCanonicalGoldenVectors() { }

    public record Vector(String name, AuditRecord record, byte[] bytes) {
        public Vector { bytes = bytes.clone(); }
        @Override public byte[] bytes() { return bytes.clone(); }
    }

    public static List<Vector> vectors() {
        var result = new ArrayList<Vector>();
        for (boolean staff : List.of(false, true)) {
            for (boolean causation : List.of(false, true)) {
                result.add(vector("nullable-" + staff + "-" + causation, "issuer", staff, causation,
                        Instant.EPOCH, Instant.EPOCH, 1, false, AuditEvent.Outcome.ACCEPTED));
            }
        }
        result.add(vector("zero-uuids", "issuer", true, true, Instant.EPOCH, Instant.EPOCH, 1, true, AuditEvent.Outcome.ACCEPTED));
        result.add(vector("unicode", "é漢𐐀🦋", true, true, Instant.EPOCH, Instant.EPOCH, 1, false, AuditEvent.Outcome.ACCEPTED));
        result.add(vector("composed", "é", false, false, Instant.EPOCH, Instant.EPOCH, 1, false, AuditEvent.Outcome.ACCEPTED));
        result.add(vector("decomposed", "e\u0301", false, false, Instant.EPOCH, Instant.EPOCH, 1, false, AuditEvent.Outcome.ACCEPTED));
        result.add(vector("ascii-byte-limit", "a".repeat(2048), false, false, Instant.EPOCH, Instant.EPOCH, 1, false, AuditEvent.Outcome.ACCEPTED));
        result.add(vector("bmp-byte-limit", "é".repeat(1024), false, false, Instant.EPOCH, Instant.EPOCH, 1, false, AuditEvent.Outcome.ACCEPTED));
        result.add(vector("supplementary-byte-limit", "🦋".repeat(512), true, true, Instant.EPOCH, Instant.EPOCH, 1, false, AuditEvent.Outcome.ACCEPTED));
        for (String text : List.of("\u00a0", "\u2007", "\u202f", "\u2000x")) {
            result.add(vector("nonblank-" + Integer.toHexString(text.codePointAt(0)), text, false, false,
                    Instant.EPOCH, Instant.EPOCH, 1, false, AuditEvent.Outcome.ACCEPTED));
        }
        result.add(vector("negative-epoch", "issuer", true, true, Instant.ofEpochSecond(-1, 999999999),
                Instant.ofEpochSecond(-1234567890, 123456789), 2, false, AuditEvent.Outcome.ACCEPTED));
        result.add(vector("instant-min", "issuer", false, false, Instant.MIN, Instant.MIN, 1, false, AuditEvent.Outcome.ACCEPTED));
        result.add(vector("instant-max", "issuer", false, false, Instant.MAX, Instant.MAX, 1, false, AuditEvent.Outcome.ACCEPTED));
        result.add(vector("nanos-one", "issuer", false, true, Instant.ofEpochSecond(1, 1), Instant.ofEpochSecond(1, 1), 1, false, AuditEvent.Outcome.ACCEPTED));
        result.add(vector("nanos-max", "issuer", true, false, Instant.ofEpochSecond(1, 999999999), Instant.ofEpochSecond(1, 999999999), 1, false, AuditEvent.Outcome.ACCEPTED));
        result.add(vector("sequence-max", "issuer", true, true, Instant.EPOCH, Instant.EPOCH, Long.MAX_VALUE, false, AuditEvent.Outcome.ACCEPTED));
        for (var outcome : AuditEvent.Outcome.values()) {
            result.add(vector("outcome-" + outcome, "issuer", false, false, Instant.EPOCH, Instant.EPOCH, 1, false, outcome));
        }
        return List.copyOf(result);
    }

    private static Vector vector(String name, String issuer, boolean staff, boolean causation, Instant recorded,
                                 Instant occurred, long sequence, boolean zero, AuditEvent.Outcome outcome) {
        UUID tenant = zero ? ZERO : HIGH;
        UUID resource = zero ? ZERO : UUID.fromString("01234567-89ab-cdef-fedc-ba9876543210");
        UUID eventId = zero ? ZERO : UUID.fromString("fedcba98-7654-3210-0123-456789abcdef");
        UUID process = zero ? ZERO : UUID.fromString("12345678-9abc-def0-0fed-cba987654321");
        UUID correlation = zero ? ZERO : UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        UUID staffId = zero ? ZERO : UUID.fromString("deadc0de-0000-4000-8000-000000000001");
        UUID cause = zero ? ZERO : UUID.fromString("11111111-2222-3333-4444-555555555555");
        var stream = new AuditStream(tenant, "ledger.api:v1", "account/main", resource);
        var actor = new AuditActor(issuer, "subject", "client", "opaque:tenant", staff ? "realm-𐐀" : null, staff ? staffId : null);
        var event = new AuditEvent(eventId, stream, actor, "CAPTURE/accepted", outcome, process, correlation, causation ? cause : null,
                occurred, "a".repeat(64), "policy:rev-01");
        var previous = sequence == 1 ? AuditCheckpoint.genesis(stream)
                : new AuditCheckpoint(stream, sequence - 1, "f".repeat(64));
        return capture(name, previous, event, recorded);
    }

    public static synchronized Vector capture(String name, AuditCheckpoint previous, AuditEvent event, Instant recorded) {
        try {
            if (!MessageDigest.getInstance("SHA-256").getProvider().getName().equals("SUN") || Security.getProvider(PROVIDER) != null)
                throw new IllegalStateException("Original pinned SUN SHA-256 provider required");
            AuditRecord original = AuditRecordHasher.record(previous, event, recorded);
            CAPTURED.remove();
            AuditRecord captured;
            try {
                if (Security.insertProviderAt(new CaptureProvider(), 1) != 1) throw new IllegalStateException("Exclusive capture provider required");
                captured = AuditRecordHasher.record(previous, event, recorded);
            } finally { Security.removeProvider(PROVIDER); }
            byte[] bytes = CAPTURED.get();
            CAPTURED.remove();
            if (!original.equals(captured) || bytes == null || !HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256", "SUN").digest(bytes)).equals(original.recordHash()))
                throw new IllegalStateException("Actual kernel preimage and genuine SHA-256 must agree");
            return new Vector(name, original, bytes);
        } catch (java.security.GeneralSecurityException error) { throw new IllegalStateException(error); }
    }

    private static final class CaptureProvider extends Provider {
        private static final long serialVersionUID = 1L;
        CaptureProvider() {
            super(PROVIDER, "1", "Test-only kernel byte capture delegating unchanged SUN SHA-256");
            putService(new Service(this, "MessageDigest", "SHA-256", CaptureDigest.class.getName(), null, null));
        }
    }

    public static final class CaptureDigest extends MessageDigestSpi {
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final MessageDigest delegate;
        public CaptureDigest() throws java.security.GeneralSecurityException { delegate = MessageDigest.getInstance("SHA-256", "SUN"); }
        @Override protected void engineUpdate(byte value) { bytes.write(value); delegate.update(value); }
        @Override protected void engineUpdate(byte[] value, int offset, int length) { bytes.write(value, offset, length); delegate.update(value, offset, length); }
        @Override protected byte[] engineDigest() { CAPTURED.set(bytes.toByteArray()); bytes.reset(); return delegate.digest(); }
        @Override protected void engineReset() { bytes.reset(); delegate.reset(); }
        @Override protected int engineGetDigestLength() { return 32; }
    }
}

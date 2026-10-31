/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package org.fireflyframework.core.audit;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.Security;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.fireflyframework.kernel.audit.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AuditCanonicalGoldenTest {
    private static final List<AuditCanonicalGoldenVectors.Vector> VECTORS = AuditCanonicalGoldenVectors.vectors();

    @Test void actualKernelPreimagesDecodeInExactProtocolOrder() throws Exception {
        assertThat(VECTORS).hasSize(28);
        for (var vector : VECTORS) {
            var record = vector.record(); var event = record.event(); var stream = event.stream(); var actor = event.actor();
            var in = new DataInputStream(new ByteArrayInputStream(vector.bytes()));
            assertThat(text(in)).isEqualTo("firefly.audit.record"); assertThat(in.readInt()).isEqualTo(1);
            assertThat(uuid(in)).isEqualTo(stream.resourceTenantId()); assertThat(text(in)).isEqualTo(stream.service());
            assertThat(text(in)).isEqualTo(stream.resourceType()); assertThat(uuid(in)).isEqualTo(stream.resourceId());
            assertThat(in.readLong()).isEqualTo(record.sequence()); assertThat(text(in)).isEqualTo(record.previousHash());
            assertThat(instant(in)).isEqualTo(record.recordedAt()); assertThat(uuid(in)).isEqualTo(event.eventId());
            assertThat(text(in)).isEqualTo(actor.issuer()); assertThat(text(in)).isEqualTo(actor.subject());
            assertThat(text(in)).isEqualTo(actor.clientId()); assertThat(text(in)).isEqualTo(actor.platformTenantId());
            assertThat(in.readUnsignedByte()).isEqualTo(actor.staffId() == null ? 0 : 1);
            if (actor.staffId() != null) { assertThat(text(in)).isEqualTo(actor.staffRealm()); assertThat(uuid(in)).isEqualTo(actor.staffId()); }
            assertThat(text(in)).isEqualTo(event.action()); assertThat(text(in)).isEqualTo(event.outcome().name());
            assertThat(uuid(in)).isEqualTo(event.processId()); assertThat(uuid(in)).isEqualTo(event.correlationId());
            assertThat(in.readUnsignedByte()).isEqualTo(event.causationId() == null ? 0 : 1);
            if (event.causationId() != null) assertThat(uuid(in)).isEqualTo(event.causationId());
            assertThat(instant(in)).isEqualTo(event.occurredAt()); assertThat(text(in)).isEqualTo(event.evidenceDigest());
            assertThat(text(in)).isEqualTo(event.policyRevision()); assertThat(in.read()).isEqualTo(-1);
            assertThat(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256", "SUN").digest(vector.bytes()))).isEqualTo(record.recordHash());
        }
    }

    @Test void originalGenuineDigestProviderAndCapturedBytesRemainIsolated() throws Exception {
        assertThat(Security.getProvider("FireflyAuditGoldenCapture")).isNull();
        assertThat(MessageDigest.getInstance("SHA-256").getProvider().getName()).isEqualTo("SUN");
        assertThat(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest("abc".getBytes(StandardCharsets.UTF_8))))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        var vector = VECTORS.getFirst(); byte[] copy = vector.bytes(); copy[0] ^= 1;
        assertThat(vector.bytes()).isNotEqualTo(copy);
    }

    @Test void allNullableCombinationsZeroAndDistinctHighBitUuidsAreRepresentable() {
        assertThat(VECTORS.stream().filter(v -> v.name().startsWith("nullable-"))).hasSize(4);
        var zero = named("zero-uuids").record().event(); assertThat(zero.eventId()).isEqualTo(new UUID(0, 0));
        assertThat(zero.stream().resourceTenantId()).isEqualTo(new UUID(0, 0)); assertThat(zero.actor().staffId()).isEqualTo(new UUID(0, 0));
        var event = VECTORS.getFirst().record().event();
        assertThat(event.stream().resourceTenantId().getMostSignificantBits()).isNegative();
        assertThat(List.of(event.eventId(), event.processId(), event.correlationId(), event.stream().resourceId(), event.stream().resourceTenantId())).doesNotHaveDuplicates();
    }

    @Test void unicodeIsNotNormalizedOrTruncatedAtItsUtf8Boundary() {
        assertThat(named("composed").record().recordHash()).isNotEqualTo(named("decomposed").record().recordHash());
        for (String name : List.of("ascii-byte-limit", "bmp-byte-limit", "supplementary-byte-limit"))
            assertThat(named(name).record().event().actor().issuer().getBytes(StandardCharsets.UTF_8)).hasSize(2048);
        assertThat(named("supplementary-byte-limit").record().event().actor().issuer()).hasSize(1024);
    }

    @Test void nativeWhitespacePlanMatchesTheActualJdkCharacterDefinition() {
        var points = new ArrayList<Integer>();
        for (int cp = 0; cp <= Character.MAX_CODE_POINT; cp++) if (Character.isWhitespace(cp)) points.add(cp);
        assertThat(points).containsExactly(9,10,11,12,13,28,29,30,31,32,5760,8192,8193,8194,8195,8196,8197,8198,8200,8201,8202,8232,8233,8287,12288);
        for (String value : List.of("\u00a0", "\u2007", "\u202f")) assertThat(actor(value)).isNotNull();
    }

    @Test void actualKernelRejectsMalformedBlankControlAndOversizeText() {
        var invalid = new ArrayList<String>();
        invalid.add(null); invalid.addAll(List.of("", " ", "\u1680", "\u2000", "\u2028", "\u2029", "x\n", "x\u0085", "x\u007f",
                "\ud800", "\udc00", "a".repeat(2049), "é".repeat(1025), "🦋".repeat(513)));
        for (String value : invalid) assertThatThrownBy(() -> actor(value)).isInstanceOfAny(NullPointerException.class, IllegalArgumentException.class);
    }

    @Test void actualKernelRequiresCompleteStaffIdentity() {
        assertThatThrownBy(() -> new AuditActor("issuer", "subject", "client", "platform", "realm", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AuditActor("issuer", "subject", "client", "platform", null, UUID.randomUUID())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void actualKernelTokenEnumDigestAndSequenceDomainsStayClosed() {
        var record = VECTORS.getFirst().record(); var stream = record.event().stream();
        for (String token : List.of("_leading", "has space", "é", "a".repeat(129)))
            assertThatThrownBy(() -> new AuditStream(stream.resourceTenantId(), token, "type", stream.resourceId())).isInstanceOf(IllegalArgumentException.class);
        assertThat(new AuditStream(stream.resourceTenantId(), "a".repeat(128), "b".repeat(128), stream.resourceId())).isNotNull();
        assertThat(AuditEvent.Outcome.values()).extracting(Enum::name).containsExactly("ATTEMPTED","ACCEPTED","REJECTED","FAILED","CANCELLED","COMPLETED","UNKNOWN");
        for (String digest : List.of("A".repeat(64), "g".repeat(64), "a".repeat(63)))
            assertThatThrownBy(() -> new AuditRecord(record.event(), 1, record.recordedAt(), record.previousHash(), digest)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AuditRecord(record.event(), 0, record.recordedAt(), record.previousHash(), record.recordHash())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AuditRecordHasher.record(new AuditCheckpoint(stream, Long.MAX_VALUE, "f".repeat(64)), record.event(), record.recordedAt())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void nativeInstantAndSequenceBoundsAreTheActualKernelRepresentableBounds() {
        assertThat(Instant.MIN.getEpochSecond()).isEqualTo(-31557014167219200L); assertThat(Instant.MAX.getEpochSecond()).isEqualTo(31556889864403199L);
        assertThat(Instant.MIN.getNano()).isZero(); assertThat(Instant.MAX.getNano()).isEqualTo(999999999);
        assertThatThrownBy(() -> Instant.ofEpochSecond(Instant.MIN.getEpochSecond() - 1)).isInstanceOf(DateTimeException.class);
        assertThatThrownBy(() -> Instant.ofEpochSecond(Instant.MAX.getEpochSecond() + 1)).isInstanceOf(DateTimeException.class);
        assertThat(named("sequence-max").record().sequence()).isEqualTo(Long.MAX_VALUE);
        assertThat(named("negative-epoch").record().recordedAt().getEpochSecond()).isEqualTo(-1);
        assertThat(named("negative-epoch").record().recordedAt().getNano()).isEqualTo(999999999);
    }

    private static AuditActor actor(String issuer) { return new AuditActor(issuer, "subject", "client", "platform", null, null); }
    private static AuditCanonicalGoldenVectors.Vector named(String name) { return VECTORS.stream().filter(v -> v.name().equals(name)).findFirst().orElseThrow(); }
    private static UUID uuid(DataInputStream in) throws Exception { return new UUID(in.readLong(), in.readLong()); }
    private static Instant instant(DataInputStream in) throws Exception { return Instant.ofEpochSecond(in.readLong(), in.readInt()); }
    private static String text(DataInputStream in) throws Exception { int count = in.readInt(); assertThat(count).isBetween(1,2048); byte[] bytes = in.readNBytes(count); assertThat(bytes).hasSize(count); return new String(bytes, StandardCharsets.UTF_8); }
}

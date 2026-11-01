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

import org.fireflyframework.kernel.audit.impl.Sha256AuditEvidenceVerifierImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class AuditEvidenceVerifierTest {
    private static final String ZERO = "0".repeat(64);
    private static final String CONTENT = "1".repeat(64);
    private static final String PLATFORM_TENANT = "sandbox-platform";
    private static final Instant TIME = Instant.parse("2026-10-28T12:00:00.123456789Z");
    private final AuditStream stream = new AuditStream(id(1), "core-payments", "payment", id(2));
    private final AuditActor actor = new AuditActor("https://issuer.example.invalid", "subject", "terminal",
            PLATFORM_TENANT, "employees", id(4));
    private final AuditEvidenceVerifier verifier = new Sha256AuditEvidenceVerifierImpl();

    private static UUID id(long n) { return new UUID(0, n); }

    private AuditEvent event(long id, String action) {
        return new AuditEvent(id(id), stream, actor, action, AuditEvent.Outcome.COMPLETED,
                id(10), id(11), id(12), TIME, CONTENT, "policy-7");
    }

    private AuditCheckpoint genesis() { return AuditCheckpoint.genesis(stream); }
    private AuditCheckpoint after(AuditRecord record) {
        return new AuditCheckpoint(record.event().stream(), record.sequence(), record.recordHash());
    }

    @Test
    void acceptsCompleteRangeBetweenIndependentlySuppliedCheckpoints() {
        var first = AuditRecordHasher.record(genesis(), event(20, "PAYMENT_COMMIT"), TIME.plusNanos(1));
        var second = AuditRecordHasher.record(after(first), event(21, "PAYMENT_RECONCILE"), TIME.plusSeconds(2));
        assertDoesNotThrow(() -> verifier.verify(genesis(), after(second), List.of(first, second)));
    }

    @Test
    void acceptsAnEmptyRangeOnlyWhenBothCheckpointsAreIdentical() {
        assertDoesNotThrow(() -> verifier.verify(genesis(), genesis(), List.of()));
        var first = AuditRecordHasher.record(genesis(), event(20, "PAYMENT_COMMIT"), TIME);
        assertThrows(IllegalArgumentException.class, () -> verifier.verify(genesis(), after(first), List.of()));
    }

    @Test
    void detectsChangedContentAgainstTrustedEndEvenWhenReplacementHashIsValid() {
        var original = AuditRecordHasher.record(genesis(), event(20, "PAYMENT_COMMIT"), TIME);
        var changed = AuditRecordHasher.record(genesis(), event(20, "PAYMENT_CANCEL"), TIME);
        assertThrows(IllegalArgumentException.class,
                () -> verifier.verify(genesis(), after(original), List.of(changed)));
    }

    @Test
    void refusesMissingTail() {
        var first = AuditRecordHasher.record(genesis(), event(20, "PAYMENT_COMMIT"), TIME);
        var second = AuditRecordHasher.record(after(first), event(21, "PAYMENT_RECONCILE"), TIME);
        assertThrows(IllegalArgumentException.class,
                () -> verifier.verify(genesis(), after(second), List.of(first)));
    }

    @Test
    void refusesReorderedRecords() {
        var first = AuditRecordHasher.record(genesis(), event(20, "PAYMENT_COMMIT"), TIME);
        var second = AuditRecordHasher.record(after(first), event(21, "PAYMENT_RECONCILE"), TIME);
        assertThrows(IllegalArgumentException.class,
                () -> verifier.verify(genesis(), after(second), List.of(second, first)));
    }

    @Test
    void refusesDuplicateEventIdentityEvenWithAValidChain() {
        var first = AuditRecordHasher.record(genesis(), event(20, "PAYMENT_COMMIT"), TIME);
        var second = AuditRecordHasher.record(after(first), event(20, "PAYMENT_RECONCILE"), TIME);
        assertThrows(IllegalArgumentException.class,
                () -> verifier.verify(genesis(), after(second), List.of(first, second)));
    }

    @Test
    void refusesAValidHashWithTheWrongPreviousLink() {
        var first = AuditRecordHasher.record(genesis(), event(20, "PAYMENT_COMMIT"), TIME);
        var disconnected = AuditRecordHasher.record(new AuditCheckpoint(stream, 1, CONTENT),
                event(21, "PAYMENT_RECONCILE"), TIME);
        assertThrows(IllegalArgumentException.class,
                () -> verifier.verify(genesis(), after(disconnected), List.of(first, disconnected)));
    }

    @Test
    void refusesCorruptedRecordedHash() {
        var first = AuditRecordHasher.record(genesis(), event(20, "PAYMENT_COMMIT"), TIME);
        var corrupted = new AuditRecord(first.event(), first.sequence(), first.recordedAt(),
                first.previousHash(), CONTENT);
        assertThrows(IllegalArgumentException.class,
                () -> verifier.verify(genesis(), after(corrupted), List.of(corrupted)));
    }

    @Test
    void bindsRecordingNanosecondsAndClientIdentity() {
        var first = AuditRecordHasher.record(genesis(), event(20, "PAYMENT_COMMIT"), TIME);
        assertNotEquals(first.recordHash(),
                AuditRecordHasher.record(genesis(), first.event(), TIME.plusNanos(1)).recordHash());
        var alternate = new AuditActor(actor.issuer(), actor.subject(), "another-client",
                actor.platformTenantId(), actor.staffRealm(), actor.staffId());
        var e = first.event();
        var changed = new AuditEvent(e.eventId(), e.stream(), alternate, e.action(), e.outcome(),
                e.processId(), e.correlationId(), e.causationId(), e.occurredAt(), e.evidenceDigest(), e.policyRevision());
        assertNotEquals(first.recordHash(), AuditRecordHasher.record(genesis(), changed, TIME).recordHash());
    }

    @Test
    void rejectsCrossStreamConstructionAndCheckpoints() {
        var other = new AuditStream(id(90), stream.service(), stream.resourceType(), stream.resourceId());
        assertThrows(IllegalArgumentException.class,
                () -> AuditRecordHasher.record(AuditCheckpoint.genesis(other), event(20, "PAYMENT_COMMIT"), TIME));
        assertThrows(IllegalArgumentException.class,
                () -> verifier.verify(genesis(), AuditCheckpoint.genesis(other), List.of()));
    }

    @Test
    void resourceTenantAndActorTenantNeedNotBeEqual() {
        assertNotEquals(stream.resourceTenantId(), actor.platformTenantId());
        assertDoesNotThrow(() -> AuditRecordHasher.record(genesis(), event(20, "PAYMENT_COMMIT"), TIME));
    }

    @Test
    void supportsExactMachineIdentityWithoutInventingStaffIdentity() {
        assertDoesNotThrow(() -> new AuditActor(actor.issuer(), "worker", "batch", PLATFORM_TENANT, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new AuditActor(actor.issuer(), actor.subject(), actor.clientId(), PLATFORM_TENANT, "employees", null));
    }

    @Test
    void rejectsMalformedUnicodeRatherThanReplacingItDuringHashing() {
        assertThrows(IllegalArgumentException.class,
                () -> new AuditActor(actor.issuer(), "bad\uD800", actor.clientId(), PLATFORM_TENANT, null, null));
        assertDoesNotThrow(() -> new AuditActor(actor.issuer(), "李 é \uD83D\uDE80", actor.clientId(), PLATFORM_TENANT, null, null));
    }

    @Test
    void rejectsUnboundedOrControlBearingMetadataAndInvalidDigests() {
        assertThrows(IllegalArgumentException.class,
                () -> new AuditStream(id(1), "x".repeat(129), "payment", id(2)));
        assertThrows(IllegalArgumentException.class,
                () -> new AuditActor(actor.issuer(), "a\nb", actor.clientId(), PLATFORM_TENANT, null, null));
        assertThrows(IllegalArgumentException.class, () -> new AuditCheckpoint(stream, 1, "ABC"));
        assertThrows(IllegalArgumentException.class, () -> new AuditCheckpoint(stream, -1, ZERO));
        assertThrows(IllegalArgumentException.class, () -> new AuditCheckpoint(stream, 0, CONTENT));
    }

    @Test
    void refusesSequenceOverflowAndRangesAboveTheBound() {
        assertThrows(IllegalArgumentException.class,
                () -> AuditRecordHasher.record(new AuditCheckpoint(stream, Long.MAX_VALUE, CONTENT),
                        event(20, "PAYMENT_COMMIT"), TIME));
        assertThrows(IllegalArgumentException.class,
                () -> verifier.verify(genesis(), new AuditCheckpoint(stream, 1001, CONTENT), List.of()));
    }

    @Test
    void acceptsWindowFromNonGenesisCheckpointWithoutInventingEarlierCompleteness() {
        var start = new AuditCheckpoint(stream, 800, CONTENT);
        var record = AuditRecordHasher.record(start, event(20, "PAYMENT_COMMIT"), TIME);
        assertDoesNotThrow(() -> verifier.verify(start, after(record), List.of(record)));
    }

    @Test
    void failureMessagesDoNotIncludeEventOrActorContents() {
        var record = AuditRecordHasher.record(genesis(), event(20, "PRIVATE_ACTION"), TIME);
        var error = assertThrows(IllegalArgumentException.class,
                () -> verifier.verify(genesis(), new AuditCheckpoint(stream, 1, CONTENT), List.of(record)));
        assertFalse(error.getMessage().contains("PRIVATE_ACTION"));
        assertFalse(error.getMessage().contains(actor.subject()));
    }

    @Test
    void matchesTheIndependentlyEncoded471ByteProtocolVector() {
        assertEquals("123bad017fcf66216bdb640821afaceff7116c9b3458e8173d080aca86e17385",
                AuditRecordHasher.record(genesis(), event(20, "PAYMENT_COMMIT"), TIME).recordHash());
    }

    @Test
    void lengthPrefixesDistinguishAdjacentVariableLengthIdentityFields() {
        var a = new AuditActor(actor.issuer(), "ab", "c", PLATFORM_TENANT, null, null);
        var b = new AuditActor(actor.issuer(), "a", "bc", PLATFORM_TENANT, null, null);
        var e = event(20, "PAYMENT_COMMIT");
        assertNotEquals(hash(AuditEvent.class.cast(replace(e, "actor", a))),
                hash(AuditEvent.class.cast(replace(e, "actor", b))));
    }

    @Test
    void absentCausationAndZeroUuidRemainDistinct() {
        var e = event(20, "PAYMENT_COMMIT");
        assertNotEquals(hash(AuditEvent.class.cast(replace(e, "causationId", null))),
                hash(AuditEvent.class.cast(replace(e, "causationId", id(0)))));
    }

    @Test
    void enforcesUtf8ByteBoundWithoutNormalizingIdentity() {
        assertThrows(IllegalArgumentException.class,
                () -> new AuditActor(actor.issuer(), "李".repeat(683), "client", PLATFORM_TENANT, null, null));
        var composed = new AuditActor(actor.issuer(), "é", "client", PLATFORM_TENANT, null, null);
        var decomposed = new AuditActor(actor.issuer(), "e\u0301", "client", PLATFORM_TENANT, null, null);
        var e = event(20, "PAYMENT_COMMIT");
        assertNotEquals(hash(AuditEvent.class.cast(replace(e, "actor", composed))),
                hash(AuditEvent.class.cast(replace(e, "actor", decomposed))));
    }

    @Test
    void preservesNonUuidAuthenticatedPlatformTenantKeysExactly() {
        String tenant = "External:Tenant A";
        var value = new AuditActor(actor.issuer(), actor.subject(), actor.clientId(), tenant, null, null);
        assertEquals(tenant, value.platformTenantId());
        assertEquals(id(1), stream.resourceTenantId());
    }

    @Test
    void platformTenantKeysRejectBlankControlsMalformedUnicodeAndExcessUtf8() {
        for (String tenant : List.of(" ", "tenant\n", "bad\uD800", "李".repeat(683))) {
            assertThrows(IllegalArgumentException.class,
                    () -> new AuditActor(actor.issuer(), actor.subject(), actor.clientId(), tenant, null, null));
        }
    }

    @Test
    void uuidLookingAuthenticatedTenantKeysAreNotCanonicalized() {
        String upper = "AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE";
        var a = new AuditActor(actor.issuer(), actor.subject(), actor.clientId(), upper, null, null);
        var b = new AuditActor(actor.issuer(), actor.subject(), actor.clientId(), upper.toLowerCase(java.util.Locale.ROOT), null, null);
        assertEquals(upper, a.platformTenantId());
        var e = event(20, "PAYMENT_COMMIT");
        assertNotEquals(hash(AuditEvent.class.cast(replace(e, "actor", a))),
                hash(AuditEvent.class.cast(replace(e, "actor", b))));
    }

    @TestFactory
    Stream<DynamicTest> everyEventComponentAffectsEvidence() {
        var original = event(20, "PAYMENT_COMMIT");
        var alternatives = Map.<String, Object>ofEntries(
                Map.entry("eventId", id(100)),
                Map.entry("stream", new AuditStream(id(100), stream.service(), stream.resourceType(), stream.resourceId())),
                Map.entry("actor", new AuditActor(actor.issuer(), "other", actor.clientId(), PLATFORM_TENANT, "employees", id(4))),
                Map.entry("action", "PAYMENT_CANCEL"), Map.entry("outcome", AuditEvent.Outcome.UNKNOWN),
                Map.entry("processId", id(100)), Map.entry("correlationId", id(100)),
                Map.entry("causationId", id(100)), Map.entry("occurredAt", TIME.plusNanos(1)),
                Map.entry("evidenceDigest", "2".repeat(64)), Map.entry("policyRevision", "policy-8"));
        assertEquals(original.getClass().getRecordComponents().length, alternatives.size());
        return alternatives.entrySet().stream().map(entry -> DynamicTest.dynamicTest(entry.getKey(),
                () -> assertNotEquals(hash(original), hash(AuditEvent.class.cast(replace(original, entry.getKey(), entry.getValue()))))));
    }

    @TestFactory
    Stream<DynamicTest> everyActorComponentAffectsEvidence() {
        var e = event(20, "PAYMENT_COMMIT");
        var alternatives = Map.<String, Object>of("issuer", "https://other.example.invalid", "subject", "other",
                "clientId", "other", "platformTenantId", "another-platform-tenant", "staffRealm", "contractors", "staffId", id(100));
        assertEquals(actor.getClass().getRecordComponents().length, alternatives.size());
        return alternatives.entrySet().stream().map(entry -> DynamicTest.dynamicTest(entry.getKey(), () -> {
            var changed = AuditActor.class.cast(replace(actor, entry.getKey(), entry.getValue()));
            assertNotEquals(hash(e), hash(AuditEvent.class.cast(replace(e, "actor", changed))));
        }));
    }

    @TestFactory
    Stream<DynamicTest> everyStreamComponentAffectsEvidence() {
        var e = event(20, "PAYMENT_COMMIT");
        var alternatives = Map.<String, Object>of("resourceTenantId", id(100), "service", "other-core",
                "resourceType", "other-type", "resourceId", id(100));
        assertEquals(stream.getClass().getRecordComponents().length, alternatives.size());
        return alternatives.entrySet().stream().map(entry -> DynamicTest.dynamicTest(entry.getKey(), () -> {
            var changed = AuditStream.class.cast(replace(stream, entry.getKey(), entry.getValue()));
            assertNotEquals(hash(e), hash(AuditEvent.class.cast(replace(e, "stream", changed))));
        }));
    }

    private String hash(AuditEvent event) {
        return AuditRecordHasher.record(AuditCheckpoint.genesis(event.stream()), event, TIME).recordHash();
    }

    private Object replace(Record original, String name, Object value) {
        try {
            var components = original.getClass().getRecordComponents();
            var types = new Class<?>[components.length];
            var arguments = new Object[components.length];
            boolean found = false;
            for (int i = 0; i < components.length; i++) {
                types[i] = components[i].getType();
                boolean selected = components[i].getName().equals(name);
                arguments[i] = selected ? value : components[i].getAccessor().invoke(original);
                found |= selected;
            }
            assertTrue(found);
            return original.getClass().getDeclaredConstructor(types).newInstance(arguments);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Cannot construct synthetic audit vector", exception);
        }
    }
}

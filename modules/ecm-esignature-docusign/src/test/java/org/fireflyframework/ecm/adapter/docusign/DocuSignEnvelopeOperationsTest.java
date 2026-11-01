package org.fireflyframework.ecm.adapter.docusign;

import com.docusign.esign.client.ApiClient;
import com.docusign.esign.client.JSON;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.fireflyframework.ecm.domain.enums.esignature.*;
import org.fireflyframework.ecm.domain.model.document.Document;
import org.fireflyframework.ecm.domain.model.esignature.*;
import org.fireflyframework.ecm.port.document.DocumentContentPort;
import org.fireflyframework.ecm.port.document.DocumentPort;
import org.fireflyframework.ecm.port.esignature.SignatureEnvelopeReferenceResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.core.publisher.Mono;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DocuSignEnvelopeOperationsTest {
    @TempDir Path temporary;
    private static final UUID ID = UUID.fromString("00000000-0000-4000-8000-000000000020");
    private static final UUID ACTOR = UUID.fromString("00000000-0000-4000-8000-000000000021");
    private ScopedSdkConnector.Scope scope() throws Exception {
        var service = temporary.resolve("builder.service");
        Files.writeString(service, ScopedSdkConnector.Builder.class.getName() + "\n");
        return new ScopedSdkConnector.Scope(service);
    }
    private DocuSignSignatureEnvelopeAdapterImpl adapter(ApiClient client, SignatureEnvelopeReferenceResolver resolver) {
        client.setBasePath("https://owned.invalid/restapi");
        var properties = new DocuSignAdapterProperties(); properties.setAccountId("owned-account");
        var documents = mock(DocumentPort.class); var content = mock(DocumentContentPort.class);
        when(documents.getDocument(DocuSignEnvelopeMappingTest.DOCUMENT)).thenReturn(Mono.just(Document.builder()
                .id(DocuSignEnvelopeMappingTest.DOCUMENT).name("agreement.pdf").mimeType("application/pdf").build()));
        when(content.getContent(DocuSignEnvelopeMappingTest.DOCUMENT)).thenReturn(Mono.just(DocuSignEnvelopeMappingTest.PDF.clone()));
        return new DocuSignSignatureEnvelopeAdapterImpl(client, properties, content, documents, resolver);
    }
    private static ScopedSdkConnector.Reply envelope(String status) {
        return new ScopedSdkConnector.Reply(200, "{\"envelopeId\":\"external-envelope\",\"status\":\"" + status + "\"}");
    }
    private static <T> T await(Mono<T> publisher) { return publisher.block(Duration.ofSeconds(2)); }
    private static void unsupported(Mono<?> publisher) {
        var error = assertThrows(DocuSignAdapterException.class, () -> await(publisher));
        assertEquals(DocuSignAdapterException.Code.UNSUPPORTED, error.getCode());
    }
    private static SignatureEnvelope withFields(SignatureField... fields) {
        var base = DocuSignEnvelopeMappingTest.envelope();
        return base.toBuilder().signatureRequests(List.of(base.getSignatureRequests().getFirst().toBuilder()
                .signatureFields(List.of(fields)).build())).build();
    }
    @Test void uuidOnlyLookupRequiresExplicitReferenceRatherThanAnAdapterMemoryMap() throws Exception {
        try (var transport = scope()) {
            unsupported(adapter(new ApiClient(), null).getEnvelope(ID)); assertTrue(transport.exchanges.isEmpty());
        }
    }
    @Test void resolverPreservesExactCallerIdentityAndProviderScope() throws Exception {
        try (var transport = scope()) {
            transport.responder = wire -> envelope("created");
            var port = adapter(new ApiClient(), (id, provider) -> {
                assertEquals(ID, id); assertEquals(SignatureProvider.DOCUSIGN, provider); return Mono.just("external-envelope");
            });
            var result = await(port.getEnvelope(ID)); assertNotNull(result); assertEquals(ID, result.getId());
            assertEquals("external-envelope", result.getExternalEnvelopeId());
        }
    }
    @Test void missingAuthorizedReferenceIsEmptyWithoutProviderIO() throws Exception {
        try (var transport = scope()) {
            var port = adapter(new ApiClient(), (id, provider) -> Mono.empty());
            assertNull(await(port.getEnvelope(ID))); assertEquals(false, await(port.existsEnvelope(ID)));
            assertTrue(transport.exchanges.isEmpty());
        }
    }
    @Test void invalidResolvedReferenceFailsBeforeProviderIO() throws Exception {
        try (var transport = scope()) {
            var port = adapter(new ApiClient(), (id, provider) -> Mono.just("../untrusted"));
            assertThrows(DocuSignAdapterException.class, () -> await(port.getEnvelope(ID))); assertTrue(transport.exchanges.isEmpty());
        }
    }
    @Test void sendMapsActualStatusAndExactUuidActorWithoutNumericConversion() throws Exception {
        try (var transport = scope()) {
            transport.responder = wire -> wire.method().equals("PUT") ? new ScopedSdkConnector.Reply(200, "{}")
                    : new ScopedSdkConnector.Reply(200, "{\"envelopeId\":\"external-envelope\",\"status\":\"sent\",\"sentDateTime\":\"2026-10-30T12:00:00Z\"}");
            var result = await(adapter(new ApiClient(), (id, provider) -> Mono.just("external-envelope")).sendEnvelope(ID, ACTOR));
            assertNotNull(result); assertEquals(ID, result.getId()); assertEquals(ACTOR, result.getSentByActorId()); assertNull(result.getSentBy());
            assertEquals(Instant.parse("2026-10-30T12:00:00Z"), result.getSentAt()); assertNull(result.getModifiedAt());
            assertEquals("sent", new JSON().getObjectMapper().readTree(transport.exchanges.getFirst().bytes()).get("status").asText());
            assertEquals(2, transport.exchanges.size());
        }
    }
    @Test void nullSendActorIsRejectedBeforeProviderIO() throws Exception {
        try (var transport = scope()) {
            assertThrows(DocuSignAdapterException.class, () -> await(adapter(new ApiClient(), null).sendEnvelope(ID, null)));
            assertTrue(transport.exchanges.isEmpty());
        }
    }
    @Test void voidUsesOnlyExplicitCallerReason() throws Exception {
        try (var transport = scope()) {
            transport.responder = wire -> wire.method().equals("PUT") ? new ScopedSdkConnector.Reply(200, "{}") : envelope("voided");
            var result = await(adapter(new ApiClient(), (id, provider) -> Mono.just("external-envelope")).voidEnvelope(ID, "Caller requested cancellation", ACTOR));
            assertNotNull(result); assertEquals(EnvelopeStatus.VOIDED, result.getStatus());
            var json = new JSON().getObjectMapper().readTree(transport.exchanges.getFirst().bytes());
            assertEquals("voided", json.get("status").asText()); assertEquals("Caller requested cancellation", json.get("voidedReason").asText());
        }
    }
    @Test void unsupportedOperationsNeverReturnInventedSuccessOrPerformProviderIO() throws Exception {
        try (var transport = scope()) {
            var port = adapter(new ApiClient(), null);
            unsupported(port.deleteEnvelope(ID)); unsupported(port.archiveEnvelope(ID)); unsupported(port.resendEnvelope(ID));
            unsupported(port.getSigningUrl(ID, "signer@example.invalid", "Signer", "client-user"));
            unsupported(port.getEnvelopesByCreator(ACTOR, 1).collectList()); unsupported(port.getEnvelopesBySender(ACTOR, 1).collectList());
            unsupported(port.getEnvelopesByProvider(SignatureProvider.DOCUSIGN, 1).collectList());
            unsupported(port.getCompletedEnvelopes(Instant.EPOCH, Instant.EPOCH.plusSeconds(1)).collectList());
            unsupported(port.getExpiringEnvelopes(Instant.EPOCH, Instant.EPOCH.plusSeconds(1)).collectList());
            assertTrue(transport.exchanges.isEmpty());
        }
    }
    @Test void dateScopedQueryUsesExactSuppliedBoundsAndNoSyntheticLocalIds() throws Exception {
        try (var transport = scope()) {
            transport.responder = wire -> new ScopedSdkConnector.Reply(200, "{\"envelopes\":[{\"envelopeId\":\"external-envelope\",\"status\":\"created\"}]}");
            var from = Instant.parse("2026-10-29T00:00:00Z"); var to = Instant.parse("2026-10-30T00:00:00Z");
            var result = await(adapter(new ApiClient(), null).getEnvelopesByStatus(EnvelopeStatus.DRAFT, from, to, 3).collectList());
            assertNotNull(result); assertEquals(1, result.size()); assertNull(result.getFirst().getId());
            String query = URLDecoder.decode(transport.exchanges.getFirst().uri().getRawQuery(), StandardCharsets.UTF_8);
            assertTrue(query.contains("from_date=" + from)); assertTrue(query.contains("to_date=" + to));
            assertTrue(query.contains("count=3")); assertTrue(query.contains("status=created"));
        }
    }
    @Test void invalidDateScopeFailsBeforeProviderIO() throws Exception {
        try (var transport = scope()) {
            var port = adapter(new ApiClient(), null);
            assertThrows(DocuSignAdapterException.class, () -> await(port.getEnvelopesByStatus(EnvelopeStatus.SENT, null, Instant.EPOCH, 1).collectList()));
            assertThrows(DocuSignAdapterException.class, () -> await(port.getEnvelopesByStatus(EnvelopeStatus.SENT, Instant.EPOCH.plusSeconds(1), Instant.EPOCH, 1).collectList()));
            assertThrows(DocuSignAdapterException.class, () -> await(port.getEnvelopesByStatus(EnvelopeStatus.SENT, Instant.EPOCH, Instant.EPOCH, 0).collectList()));
            assertTrue(transport.exchanges.isEmpty());
        }
    }
    @Test void actual404IsEmptyWhileMalformedOrMismatchedProviderDataIsRejected() throws Exception {
        try (var transport = scope()) {
            var port = adapter(new ApiClient(), (id, provider) -> Mono.just("external-envelope"));
            transport.responder = wire -> new ScopedSdkConnector.Reply(404, "{}");
            assertNull(await(port.getEnvelope(ID))); assertEquals(false, await(port.existsEnvelope(ID)));
            for (String body : List.of("{\"envelopeId\":\"different-envelope\",\"status\":\"sent\"}",
                    "{\"envelopeId\":\"external-envelope\",\"status\":\"sent\",\"createdDateTime\":\"not-a-date\"}")) {
                transport.responder = wire -> new ScopedSdkConnector.Reply(200, body);
                var error = assertThrows(DocuSignAdapterException.class, () -> await(port.getEnvelope(ID)));
                assertEquals(DocuSignAdapterException.Code.PROVIDER_RESPONSE, error.getCode());
            }
        }
    }
    @Test void explicitSupportedProviderStatusesRetainTheirActualMeaning() throws Exception {
        try (var transport = scope()) {
            var port = adapter(new ApiClient(), null);
            var expected = Map.of("created", EnvelopeStatus.DRAFT, "sent", EnvelopeStatus.SENT,
                    "delivered", EnvelopeStatus.IN_PROGRESS, "signed", EnvelopeStatus.IN_PROGRESS,
                    "completed", EnvelopeStatus.COMPLETED, "declined", EnvelopeStatus.DECLINED, "voided", EnvelopeStatus.VOIDED);
            for (var entry : expected.entrySet()) {
                transport.responder = wire -> envelope(entry.getKey());
                var result = await(port.getEnvelopeByExternalId("external-envelope", SignatureProvider.DOCUSIGN));
                assertNotNull(result); assertEquals(entry.getValue(), result.getStatus());
            }
        }
    }
    @Test void metadataUpdateUsesExplicitExternalReferenceWithoutCreatingIdentityOrTime() throws Exception {
        try (var transport = scope()) {
            transport.responder = wire -> wire.method().equals("PUT") ? new ScopedSdkConnector.Reply(200, "{}") : envelope("created");
            var result = await(adapter(new ApiClient(), null).updateEnvelope(SignatureEnvelope.builder().id(ID)
                    .externalEnvelopeId("external-envelope").title("Updated subject").description("Updated body").build()));
            assertNotNull(result); assertEquals(ID, result.getId()); assertNull(result.getModifiedAt());
            var json = new JSON().getObjectMapper().readTree(transport.exchanges.getFirst().bytes());
            assertEquals("Updated subject", json.get("emailSubject").asText()); assertFalse(json.has("status"));
            var before = transport.exchanges.size();
            unsupported(adapter(new ApiClient(), null).updateEnvelope(SignatureEnvelope.builder().externalEnvelopeId("external-envelope")
                    .title("Subject").status(EnvelopeStatus.SENT).build()));
            assertThrows(DocuSignAdapterException.class, () -> await(adapter(new ApiClient(), null).updateEnvelope(SignatureEnvelope.builder()
                    .externalEnvelopeId("external-envelope").title("Subject").provider(SignatureProvider.ADOBE_SIGN).build())));
            assertEquals(before, transport.exchanges.size(), "Metadata update cannot silently drop lifecycle or provider changes");
        }
    }
    @Test void suppliedFractionalDimensionsArePreservedWithoutRounding() throws Exception {
        try (var transport = scope()) {
            transport.responder = wire -> new ScopedSdkConnector.Reply(201, "{\"envelopeId\":\"external-envelope\",\"status\":\"created\"}");
            var field = DocuSignEnvelopeMappingTest.envelope().getSignatureRequests().getFirst().getSignatureFields().getFirst()
                    .toBuilder().width(12.5).height(8.25).build();
            assertNotNull(await(adapter(new ApiClient(), null).createEnvelope(withFields(field))));
            var json = new JSON().getObjectMapper().readTree(transport.exchanges.getFirst().bytes());
            var tab = json.get("recipients").get("signers").get(0).get("tabs").get("signHereTabs").get(0);
            assertEquals("12.5", tab.get("width").asText()); assertEquals("8.25", tab.get("height").asText());
        }
    }
    @Test void formFillCannotSilentlyAcquireASignatureRequirement() throws Exception {
        try (var transport = scope()) {
            transport.responder = wire -> new ScopedSdkConnector.Reply(201, "{\"envelopeId\":\"external-envelope\",\"status\":\"created\"}");
            var base = DocuSignEnvelopeMappingTest.envelope();
            var request = base.getSignatureRequests().getFirst().toBuilder().requestType(SignatureRequestType.FORM_FILL).build();
            assertThrows(DocuSignAdapterException.class, () -> await(adapter(new ApiClient(), null).createEnvelope(base.toBuilder().signatureRequests(List.of(request)).build())));
            assertTrue(transport.exchanges.isEmpty());
        }
    }
    @Test void sequentialModeCannotSilentlyCreateAParallelSigningGroup() throws Exception {
        try (var transport = scope()) {
            transport.responder = wire -> new ScopedSdkConnector.Reply(201, "{\"envelopeId\":\"external-envelope\",\"status\":\"created\"}");
            var base = DocuSignEnvelopeMappingTest.envelope();
            var second = base.getSignatureRequests().getFirst().toBuilder().id(UUID.randomUUID()).build();
            assertThrows(DocuSignAdapterException.class, () -> await(adapter(new ApiClient(), null).createEnvelope(base.toBuilder()
                    .signingOrder(SigningOrder.SEQUENTIAL).signatureRequests(List.of(base.getSignatureRequests().getFirst(), second)).build())));
            assertTrue(transport.exchanges.isEmpty());
        }
    }
    @Test void signerMessageAndSupportedLanguageUseActualSdkNotificationFields() throws Exception {
        try (var transport = scope()) {
            transport.responder = wire -> new ScopedSdkConnector.Reply(201, "{\"envelopeId\":\"external-envelope\",\"status\":\"created\"}");
            var base = DocuSignEnvelopeMappingTest.envelope();
            var request = base.getSignatureRequests().getFirst().toBuilder().customMessage("Personal signer message").language("es").build();
            assertNotNull(await(adapter(new ApiClient(), null).createEnvelope(base.toBuilder().signatureRequests(List.of(request)).build())));
            var json = new JSON().getObjectMapper().readTree(transport.exchanges.getFirst().bytes()).get("recipients").get("signers").get(0).get("emailNotification");
            assertEquals("Personal signer message", json.get("emailBody").asText()); assertEquals("es", json.get("supportedLanguage").asText());
        }
    }
    @Test void uuidActorAndLegacyNumericActorRemainDistinctInJson() throws Exception {
        var value = SignatureEnvelope.builder().sentBy(42L).sentByActorId(ACTOR).build();
        var mapper = new JSON().getObjectMapper(); var json = mapper.readTree(mapper.writeValueAsBytes(value));
        assertEquals(42L, json.get("sentBy").asLong()); assertEquals(ACTOR.toString(), json.get("sentByActorId").asText());
        var result = mapper.readValue(mapper.writeValueAsBytes(value), SignatureEnvelope.class);
        assertEquals(42L, result.getSentBy()); assertEquals(ACTOR, result.getSentByActorId());
    }

    @Test void allSupportedTabModelsAndCarbonCopyFieldsUseActualSdkWireNames() throws Exception {
        try (var transport = scope()) {
            transport.responder = wire -> new ScopedSdkConnector.Reply(201, "{\"envelopeId\":\"external-envelope\",\"status\":\"created\"}");
            var base = DocuSignEnvelopeMappingTest.envelope(); var signature = base.getSignatureRequests().getFirst().getSignatureFields().getFirst();
            var initial = signature.toBuilder().fieldType(FieldType.INITIAL).required(false).build();
            var text = signature.toBuilder().fieldType(FieldType.TEXT).name("Reference").value("exact value").required(true)
                    .readOnly(true).width(100.0).height(20.0).validationPattern("[a-z ]+").tooltip("Reference value").tabOrder(3).build();
            var signer = base.getSignatureRequests().getFirst().toBuilder().signatureFields(List.of(signature, initial, text)).signerRole("Customer").build();
            var copy = SignatureRequest.builder().signerName("Controlled Copy").signerEmail("copy@example.invalid")
                    .signerRole("Observer").signingOrder(2).requestType(SignatureRequestType.CARBON_COPY).build();
            assertNotNull(await(adapter(new ApiClient(), null).createEnvelope(base.toBuilder().signatureRequests(List.of(signer, copy)).build())));
            var recipients = new JSON().getObjectMapper().readTree(transport.exchanges.getFirst().bytes()).get("recipients");
            var recipient = recipients.get("signers").get(0); assertEquals("Customer", recipient.get("roleName").asText());
            var tabs = recipient.get("tabs"); assertEquals("true", tabs.get("initialHereTabs").get(0).get("optional").asText());
            var input = tabs.get("textTabs").get(0); assertEquals("exact value", input.get("value").asText());
            assertEquals("true", input.get("locked").asText()); assertEquals("true", input.get("required").asText());
            assertEquals("[a-z ]+", input.get("validationPattern").asText()); assertEquals("3", input.get("tabOrder").asText());
            var cc = recipients.get("carbonCopies").get(0); assertEquals("copy@example.invalid", cc.get("email").asText());
            assertEquals("2", cc.get("routingOrder").asText()); assertEquals("Observer", cc.get("roleName").asText());
        }
    }
    @Test void invalidPointAndDimensionInputsNeverReachTheProvider() throws Exception {
        try (var transport = scope()) {
            var port = adapter(new ApiClient(), null);
            var base = DocuSignEnvelopeMappingTest.envelope().getSignatureRequests().getFirst().getSignatureFields().getFirst();
            for (Double value : List.of(Double.NaN, Double.POSITIVE_INFINITY, -1.0, 0.5, (double) Integer.MAX_VALUE + 1)) {
                assertThrows(DocuSignAdapterException.class, () -> await(port.createEnvelope(withFields(base.toBuilder().xPosition(value).build()))));
            }
            for (Double value : List.of(Double.NaN, Double.NEGATIVE_INFINITY, -1.0, 0.0)) {
                assertThrows(DocuSignAdapterException.class, () -> await(port.createEnvelope(withFields(base.toBuilder().width(value).build()))));
            }
            assertThrows(DocuSignAdapterException.class, () -> await(port.createEnvelope(withFields(base.toBuilder().pageNumber(0).build()))));
            assertTrue(transport.exchanges.isEmpty());
        }
    }
    @Test void advancedSettingsAndUnsupportedTabTypesAreExplicitRefusals() throws Exception {
        try (var transport = scope()) {
            var port = adapter(new ApiClient(), null); var base = DocuSignEnvelopeMappingTest.envelope();
            unsupported(port.createEnvelope(base.toBuilder().callbackUrl("https://callback.example.invalid").build()));
            unsupported(port.createEnvelope(base.toBuilder().reminderSettings(ReminderSettings.builder().enabled(true).build()).build()));
            unsupported(port.createEnvelope(base.toBuilder().authRequirements(AuthenticationRequirements.builder().requireIdVerification(true).build()).build()));
            unsupported(port.createEnvelope(base.toBuilder().requireAllSigners(false).build()));
            var field = base.getSignatureRequests().getFirst().getSignatureFields().getFirst();
            unsupported(port.createEnvelope(withFields(field, field.toBuilder().fieldType(FieldType.DATE).build())));
            unsupported(port.createEnvelope(withFields(field.toBuilder().fontSettings(FontSettings.builder().bold(true).build()).build())));
            assertTrue(transport.exchanges.isEmpty());
        }
    }
    @Test void unknownSignerLanguageIsRefusedRatherThanNormalizedOrDropped() throws Exception {
        try (var transport = scope()) {
            var base = DocuSignEnvelopeMappingTest.envelope(); var request = base.getSignatureRequests().getFirst().toBuilder().language("unknown-language").build();
            unsupported(adapter(new ApiClient(), null).createEnvelope(base.toBuilder().signatureRequests(List.of(request)).build()));
            assertTrue(transport.exchanges.isEmpty());
        }
    }
    @Test void originalAllArgumentsConstructorDescriptorStillExists() throws Exception {
        var constructor = SignatureEnvelope.class.getDeclaredConstructor(UUID.class, String.class, String.class, EnvelopeStatus.class,
                List.class, List.class, UUID.class, Instant.class, Long.class, Instant.class, Instant.class, Instant.class, Instant.class,
                Map.class, String.class, SignatureProvider.class, EnvelopePriority.class, Boolean.class, SigningOrder.class,
                ReminderSettings.class, AuthenticationRequirements.class, String.class, Boolean.class, String.class);
        constructor.setAccessible(true);
        var value = constructor.newInstance(ID, "Legacy subject", null, EnvelopeStatus.DRAFT, null, null, ACTOR, null, 42L,
                null, null, null, null, null, "external-envelope", SignatureProvider.DOCUSIGN, null, null, null, null, null, null, null, null);
        assertEquals(42L, value.getSentBy()); assertNull(value.getSentByActorId()); assertEquals(ID, value.getId());
    }
    @Test void multipleDocumentsHaveStableWireIdsAndPreserveContentOrder() throws Exception {
        try (var transport = scope()) {
            var client = new ApiClient(); client.setBasePath("https://owned.invalid/restapi");
            var properties = new DocuSignAdapterProperties(); properties.setAccountId("owned-account");
            var documents = mock(DocumentPort.class); var content = mock(DocumentContentPort.class);
            UUID second = UUID.fromString("00000000-0000-4000-8000-000000000030");
            var ids = List.of(second, DocuSignEnvelopeMappingTest.DOCUMENT);
            for (UUID id : ids) {
                when(documents.getDocument(id)).thenReturn(Mono.just(Document.builder().id(id).name(id + ".pdf").mimeType("application/pdf").build()));
                when(content.getContent(id)).thenReturn(Mono.just(id.toString().getBytes(StandardCharsets.UTF_8)));
            }
            transport.responder = wire -> new ScopedSdkConnector.Reply(201, "{\"envelopeId\":\"external-envelope\",\"status\":\"created\"}");
            var port = new DocuSignSignatureEnvelopeAdapterImpl(client, properties, content, documents);
            assertNotNull(await(port.createEnvelope(DocuSignEnvelopeMappingTest.envelope().toBuilder().documentIds(ids).build())));
            var json = new JSON().getObjectMapper().readTree(transport.exchanges.getFirst().bytes());
            assertEquals("1", json.get("documents").get(0).get("documentId").asText());
            assertEquals(second.toString(), new String(Base64.getDecoder().decode(json.get("documents").get(0).get("documentBase64").asText()), StandardCharsets.UTF_8));
            assertEquals("2", json.get("recipients").get("signers").get(0).get("tabs").get("signHereTabs").get(0).get("documentId").asText());
        }
    }
    @Test void postWrite404CannotTurnASendIntoEmptySuccess() throws Exception {
        try (var transport = scope()) {
            transport.responder = wire -> wire.method().equals("PUT") ? new ScopedSdkConnector.Reply(200, "{}") : new ScopedSdkConnector.Reply(404, "{}");
            var error = assertThrows(DocuSignAdapterException.class, () -> await(adapter(new ApiClient(), (id, provider) -> Mono.just("external-envelope")).sendEnvelope(ID, ACTOR)));
            assertEquals(DocuSignAdapterException.Code.NOT_FOUND, error.getCode());
        }
    }
    @Test void successHttpWithSdkErrorDetailsIsNotAcceptedAsASuccessfulWrite() throws Exception {
        try (var transport = scope()) {
            transport.responder = wire -> wire.method().equals("PUT") ? new ScopedSdkConnector.Reply(200,
                    "{\"errorDetails\":{\"errorCode\":\"CONTROLLED_FAILURE\",\"message\":\"private-provider-body\"}}") : envelope("sent");
            var error = assertThrows(DocuSignAdapterException.class, () -> await(adapter(new ApiClient(), (id, provider) -> Mono.just("external-envelope")).sendEnvelope(ID, ACTOR)));
            assertEquals(DocuSignAdapterException.Code.PROVIDER_RESPONSE, error.getCode()); assertNull(error.getCause());
            assertEquals(1, transport.exchanges.size());
        }
    }
    @Test void nullListRowsAreTypedProviderResponseFailures() throws Exception {
        try (var transport = scope()) {
            transport.responder = wire -> new ScopedSdkConnector.Reply(200, "{\"envelopes\":[null]}");
            var error = assertThrows(DocuSignAdapterException.class, () -> await(adapter(new ApiClient(), null)
                    .getEnvelopesByStatus(EnvelopeStatus.SENT, Instant.EPOCH, Instant.EPOCH.plusSeconds(1), 1).collectList()));
            assertEquals(DocuSignAdapterException.Code.PROVIDER_RESPONSE, error.getCode());
        }
    }

    @Test void textTabValuesHaveAnExplicitAdmissionBound() throws Exception {
        try (var transport = scope()) {
            transport.responder = wire -> new ScopedSdkConnector.Reply(201, "{\"envelopeId\":\"external-envelope\",\"status\":\"created\"}");
            var signature = DocuSignEnvelopeMappingTest.envelope().getSignatureRequests().getFirst().getSignatureFields().getFirst();
            var text = signature.toBuilder().fieldType(FieldType.TEXT).value("x".repeat(10001)).build();
            assertThrows(DocuSignAdapterException.class, () -> await(adapter(new ApiClient(), null).createEnvelope(withFields(signature, text))));
            assertTrue(transport.exchanges.isEmpty());
        }
    }
}

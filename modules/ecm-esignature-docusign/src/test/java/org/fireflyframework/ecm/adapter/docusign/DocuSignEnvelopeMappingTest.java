package org.fireflyframework.ecm.adapter.docusign;

import com.docusign.esign.client.ApiClient;
import com.docusign.esign.client.JSON;
import com.docusign.esign.model.EnvelopeDefinition;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.fireflyframework.ecm.domain.enums.esignature.*;
import org.fireflyframework.ecm.domain.model.document.Document;
import org.fireflyframework.ecm.domain.model.esignature.*;
import org.fireflyframework.ecm.port.document.DocumentContentPort;
import org.fireflyframework.ecm.port.document.DocumentPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.core.publisher.Mono;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DocuSignEnvelopeMappingTest {
    @TempDir Path temporary;
    static final UUID DOCUMENT = UUID.fromString("00000000-0000-4000-8000-000000000010");
    static final UUID REQUEST = UUID.fromString("00000000-0000-4000-8000-000000000011");
    static final byte[] PDF = "%PDF-1.7\ncontrolled-content".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    private ScopedSdkConnector.Scope scope() throws Exception {
        Path service = temporary.resolve("builder-service");
        Files.writeString(service, ScopedSdkConnector.Builder.class.getName() + "\n");
        return new ScopedSdkConnector.Scope(service);
    }
    private DocuSignSignatureEnvelopeAdapter adapter(ApiClient client, boolean content) {
        client.setBasePath("https://owned.invalid/restapi");
        var props = new DocuSignAdapterProperties(); props.setAccountId("owned-account");
        var documents = mock(DocumentPort.class); var bytes = mock(DocumentContentPort.class);
        when(documents.getDocument(DOCUMENT)).thenReturn(Mono.just(Document.builder().id(DOCUMENT)
                .name("agreement.pdf").extension("pdf").mimeType("application/pdf").size((long) PDF.length).encrypted(true).build()));
        when(bytes.getContent(DOCUMENT)).thenReturn(content ? Mono.just(PDF.clone()) : Mono.empty());
        return new DocuSignSignatureEnvelopeAdapter(client, props, bytes, documents);
    }
    static SignatureEnvelope envelope() {
        return SignatureEnvelope.builder().title("Agreement").description("Review and sign")
                .documentIds(List.of(DOCUMENT)).signatureRequests(List.of(SignatureRequest.builder().id(REQUEST)
                        .signerName("Controlled Signer").signerEmail("signer@example.invalid")
                        .requestType(SignatureRequestType.SIGNATURE).required(true).signingOrder(1)
                        .signatureFields(List.of(SignatureField.builder().fieldType(FieldType.SIGNATURE)
                                .documentId(DOCUMENT).pageNumber(1).xPosition(24.0).yPosition(48.0).build())).build())).build();
    }
    private static ScopedSdkConnector.Reply created(ScopedSdkConnector.Wire wire) {
        return new ScopedSdkConnector.Reply(201, "{\"envelopeId\":\"external-envelope\",\"status\":\"created\"}");
    }
    private static void await(Mono<?> mono) { mono.block(Duration.ofSeconds(2)); }
    @Test void draftUsesActualContentDocumentsRecipientsAndPointCoordinates() throws Exception {
        try (var transport = scope()) {
            var client = new ApiClient(); transport.responder = DocuSignEnvelopeMappingTest::created;
            var result = adapter(client, true).createEnvelope(envelope()).block(Duration.ofSeconds(2));
            assertNotNull(result); assertEquals(EnvelopeStatus.DRAFT, result.getStatus());
            var wire = transport.exchanges.getLast();
            var definition = new JSON().getObjectMapper().readValue(wire.bytes(), EnvelopeDefinition.class);
            assertEquals("created", definition.getStatus()); assertEquals("Agreement", definition.getEmailSubject());
            assertNotNull(definition.getDocuments()); assertEquals(1, definition.getDocuments().size());
            assertArrayEquals(PDF, java.util.Base64.getDecoder().decode(definition.getDocuments().getFirst().getDocumentBase64()));
            assertEquals("1", definition.getDocuments().getFirst().getDocumentId());
            var signer = definition.getRecipients().getSigners().getFirst();
            assertEquals("signer@example.invalid", signer.getEmail()); assertEquals("1", signer.getRoutingOrder());
            var tab = signer.getTabs().getSignHereTabs().getFirst();
            assertEquals("24", tab.getXPosition()); assertEquals("48", tab.getYPosition()); assertEquals("1", tab.getPageNumber());
            assertNull(result.getCreatedAt()); assertNull(result.getModifiedAt());
        }
    }
    @Test void missingContentFailsBeforeProviderIO() throws Exception {
        try (var transport = scope()) {
            var client = new ApiClient(); transport.responder = DocuSignEnvelopeMappingTest::created;
            assertThrows(DocuSignAdapterException.class, () -> await(adapter(client, false).createEnvelope(envelope())));
            assertTrue(transport.exchanges.isEmpty());
        }
    }
    @Test void fractionalCoordinateIsNeverRoundedOrSent() throws Exception {
        try (var transport = scope()) {
            var client = new ApiClient(); transport.responder = DocuSignEnvelopeMappingTest::created;
            var base = envelope(); var request = base.getSignatureRequests().getFirst();
            var field = request.getSignatureFields().getFirst().toBuilder().xPosition(1.5).build();
            var invalid = base.toBuilder().signatureRequests(List.of(request.toBuilder().signatureFields(List.of(field)).build())).build();
            assertThrows(DocuSignAdapterException.class, () -> await(adapter(client, true).createEnvelope(invalid)));
            assertTrue(transport.exchanges.isEmpty());
        }
    }
    @Test void unsupportedEnvelopeMetadataIsNotSilentlyDropped() throws Exception {
        try (var transport = scope()) {
            var client = new ApiClient(); transport.responder = DocuSignEnvelopeMappingTest::created;
            assertThrows(DocuSignAdapterException.class, () -> await(adapter(client, true).createEnvelope(envelope().toBuilder().metadata(Map.of("unknown", "value")).build())));
            assertTrue(transport.exchanges.isEmpty());
        }
    }
    @Test void statelessExternalLookupMapsCreatedWithoutInventingInternalIdentity() throws Exception {
        try (var transport = scope()) {
            var client = new ApiClient(); transport.responder = wire -> new ScopedSdkConnector.Reply(200, "{\"envelopeId\":\"external-envelope\",\"status\":\"created\"}");
            var result = adapter(client, true).getEnvelopeByExternalId("external-envelope", SignatureProvider.DOCUSIGN).block(Duration.ofSeconds(2));
            assertNotNull(result); assertNull(result.getId()); assertEquals(EnvelopeStatus.DRAFT, result.getStatus());
        }
    }
    @Test void deliveredMapsInProgressAndUsesOnlyProviderDates() throws Exception {
        try (var transport = scope()) {
            var client = new ApiClient(); transport.responder = wire -> new ScopedSdkConnector.Reply(200, "{\"envelopeId\":\"external-envelope\",\"status\":\"delivered\",\"createdDateTime\":\"2026-10-30T10:00:00Z\",\"sentDateTime\":\"2026-10-30T10:01:00Z\"}");
            var result = adapter(client, true).getEnvelopeByExternalId("external-envelope", SignatureProvider.DOCUSIGN).block(Duration.ofSeconds(2));
            assertNotNull(result); assertEquals(EnvelopeStatus.IN_PROGRESS, result.getStatus());
            assertEquals(Instant.parse("2026-10-30T10:00:00Z"), result.getCreatedAt()); assertNull(result.getCompletedAt());
        }
    }
    @Test void unknownProviderStatusDoesNotBecomeSuccess() throws Exception {
        try (var transport = scope()) {
            var client = new ApiClient(); transport.responder = wire -> new ScopedSdkConnector.Reply(200, "{\"envelopeId\":\"external-envelope\",\"status\":\"unknown-state\"}");
            assertThrows(DocuSignAdapterException.class, () -> await(adapter(client, true).getEnvelopeByExternalId("external-envelope", SignatureProvider.DOCUSIGN)));
        }
    }
    @Test void invalidExternalReferenceFailsBeforeProviderIO() throws Exception {
        try (var transport = scope()) {
            var client = new ApiClient();
            assertThrows(DocuSignAdapterException.class, () -> await(adapter(client, true).getEnvelopeByExternalId("../foreign", SignatureProvider.DOCUSIGN)));
            assertTrue(transport.exchanges.isEmpty());
        }
    }
    @Test void datelessGlobalListIsExplicitlyUnsupported() throws Exception {
        try (var transport = scope()) {
            var client = new ApiClient(); transport.responder = wire -> new ScopedSdkConnector.Reply(200, "{\"envelopes\":[]}");
            assertThrows(DocuSignAdapterException.class, () -> await(adapter(client, true).getEnvelopesByStatus(EnvelopeStatus.SENT, 10).collectList()));
            assertTrue(transport.exchanges.isEmpty());
        }
    }
    @Test void unsupportedResendDoesNotPretendSuccess() throws Exception {
        try (var transport = scope()) {
            var client = new ApiClient();
            assertThrows(DocuSignAdapterException.class, () -> await(adapter(client, true).resendEnvelope(UUID.randomUUID())));
            assertTrue(transport.exchanges.isEmpty());
        }
    }
    @Test void invalidCreateSummaryCannotFabricateAValidEnvelope() throws Exception {
        try (var transport = scope()) {
            var client = new ApiClient(); transport.responder = wire -> new ScopedSdkConnector.Reply(201, "{\"status\":\"sent\"}");
            assertThrows(DocuSignAdapterException.class, () -> await(adapter(client, true).createEnvelope(envelope())));
        }
    }
    @Test void statelessLookupPropagatesTypedSafeProviderFailure() throws Exception {
        try (var transport = scope()) {
            var client = new ApiClient(); transport.responder = wire -> new ScopedSdkConnector.Reply(403, "{\"message\":\"private-provider-body\"}");
            var error = assertThrows(DocuSignAdapterException.class, () -> await(adapter(client, true).getEnvelopeByExternalId("external-envelope", SignatureProvider.DOCUSIGN)));
            assertEquals(DocuSignAdapterException.Code.AUTHORIZATION, error.getCode()); assertNull(error.getCause());
        }
    }
}

package org.fireflyframework.ecm.adapter.docusign;

import com.docusign.esign.client.ApiClient;
import com.docusign.esign.client.JSON;
import com.docusign.esign.model.EnvelopeDefinition;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.fireflyframework.ecm.domain.model.esignature.SignatureEnvelope;
import org.fireflyframework.ecm.domain.model.esignature.SignatureEnvelopeDocument;
import org.fireflyframework.ecm.port.document.DocumentContentPort;
import org.fireflyframework.ecm.port.document.DocumentPort;
import org.fireflyframework.ecm.port.esignature.SignatureEnvelopePort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.core.publisher.Mono;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DocuSignImmutableContentTest {
    @TempDir Path temporary;
    static final UUID VERSION = UUID.fromString("00000000-0000-4000-8000-000000000031");
    static final UUID SECOND = UUID.fromString("00000000-0000-4000-8000-000000000032");
    static String digest(byte[] content) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)); }
    static SignatureEnvelopeDocument snapshot(UUID id, byte[] content) throws Exception {
        return new SignatureEnvelopeDocument(id, VERSION, "agreement.pdf", "application/pdf", content.length, digest(content), content);
    }
    static <T> T await(Mono<T> value) { return value.block(Duration.ofSeconds(2)); }
    static final class Fixture implements AutoCloseable {
        final ScopedSdkConnector.Scope transport;
        final DocumentPort documents = mock(DocumentPort.class);
        final DocumentContentPort content = mock(DocumentContentPort.class);
        final DocuSignSignatureEnvelopeAdapter port;
        final DocuSignAdapterProperties properties = new DocuSignAdapterProperties();
        Fixture(Path directory) throws Exception {
            var service = directory.resolve("builder.service"); Files.writeString(service, ScopedSdkConnector.Builder.class.getName() + "\n");
            transport = new ScopedSdkConnector.Scope(service);
            var client = new ApiClient(); client.setBasePath("https://owned.invalid/restapi");
            properties.setAccountId("owned-account");
            port = new DocuSignSignatureEnvelopeAdapter(client, properties, content, documents);
            transport.responder = wire -> new ScopedSdkConnector.Reply(201,"{\"envelopeId\":\"external-envelope\",\"status\":\"created\"}");
        }
        EnvelopeDefinition sent() throws Exception {
            assertEquals(1, transport.exchanges.size());
            return new JSON().getObjectMapper().readValue(transport.exchanges.getFirst().bytes(), EnvelopeDefinition.class);
        }
        void noFetch() { verifyNoInteractions(documents, content); }
        void noIo() { noFetch(); assertTrue(transport.exchanges.isEmpty()); }
        @Override public void close() { transport.close(); }
    }
    @Test void exactSnapshotBytesReachTheActualSdkWithoutAnyMetadataOrContentRefetch() throws Exception {
        try (var fixture = new Fixture(temporary)) {
            byte[] approved = DocuSignEnvelopeMappingTest.PDF.clone(); var supplied = snapshot(DocuSignEnvelopeMappingTest.DOCUMENT, approved);
            byte[] expected = approved.clone(); approved[0] = 0; supplied.content()[1] = 0;
            var result = await(fixture.port.createEnvelope(DocuSignEnvelopeMappingTest.envelope(), List.of(supplied)));
            assertNotNull(result); assertEquals("external-envelope", result.getExternalEnvelopeId());
            var sent = fixture.sent(); assertArrayEquals(expected, Base64.getDecoder().decode(sent.getDocuments().getFirst().getDocumentBase64()));
            assertEquals(digest(expected), supplied.sha256()); assertEquals(expected.length, supplied.contentLength());
            assertEquals(VERSION, supplied.documentVersionId()); assertEquals("agreement.pdf", sent.getDocuments().getFirst().getName());
            assertEquals("pdf", sent.getDocuments().getFirst().getFileExtension()); fixture.noFetch();
        }
    }
    @Test void snapshotDefensivelyCopiesBothCallerBytesAndEveryContentAccess() throws Exception {
        var input = DocuSignEnvelopeMappingTest.PDF.clone(); var saved = input.clone(); var value = snapshot(DocuSignEnvelopeMappingTest.DOCUMENT, input);
        input[0] = 0; var first = value.content(); first[1] = 0;
        assertArrayEquals(saved, value.content()); assertNotSame(first, value.content());
    }
    @Test void snapshotRefusesDigestAndLengthThatDoNotDescribeItsActualCopiedBytes() throws Exception {
        byte[] bytes = DocuSignEnvelopeMappingTest.PDF.clone();
        assertThrows(IllegalArgumentException.class, () -> new SignatureEnvelopeDocument(DocuSignEnvelopeMappingTest.DOCUMENT, VERSION,
                "agreement.pdf", "application/pdf", bytes.length, "0".repeat(64), bytes));
        assertThrows(IllegalArgumentException.class, () -> new SignatureEnvelopeDocument(DocuSignEnvelopeMappingTest.DOCUMENT, VERSION,
                "agreement.pdf", "application/pdf", bytes.length + 1L, digest(bytes), bytes));
    }
    @Test void snapshotRequiresActualDocumentAndVersionIdentity() throws Exception {
        byte[] bytes = DocuSignEnvelopeMappingTest.PDF.clone();
        assertThrows(IllegalArgumentException.class, () -> snapshot(null, bytes));
        assertThrows(IllegalArgumentException.class, () -> new SignatureEnvelopeDocument(DocuSignEnvelopeMappingTest.DOCUMENT, null,
                "agreement.pdf", "application/pdf", bytes.length, digest(bytes), bytes));
    }
    @Test void emptyContentAndInvalidSnapshotMetadataAreRefusedLocally() throws Exception {
        byte[] bytes = DocuSignEnvelopeMappingTest.PDF.clone();
        assertThrows(IllegalArgumentException.class, () -> snapshot(DocuSignEnvelopeMappingTest.DOCUMENT, new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> new SignatureEnvelopeDocument(DocuSignEnvelopeMappingTest.DOCUMENT, VERSION,
                "bad\rname.pdf", "application/pdf", bytes.length, digest(bytes), bytes));
        assertThrows(IllegalArgumentException.class, () -> new SignatureEnvelopeDocument(DocuSignEnvelopeMappingTest.DOCUMENT, VERSION,
                "agreement.pdf", null, bytes.length, digest(bytes), bytes));
    }
    @Test void twoDocumentsRetainTheExactDeclaredOrderAndSnapshotMetadata() throws Exception {
        try (var fixture = new Fixture(temporary)) {
            var envelope = DocuSignEnvelopeMappingTest.envelope().toBuilder().documentIds(List.of(DocuSignEnvelopeMappingTest.DOCUMENT, SECOND)).build();
            var first = snapshot(DocuSignEnvelopeMappingTest.DOCUMENT, DocuSignEnvelopeMappingTest.PDF);
            var second = new SignatureEnvelopeDocument(SECOND, VERSION, "second.pdf", "application/pdf", 2, digest(new byte[]{1,2}), new byte[]{1,2});
            assertNotNull(await(fixture.port.createEnvelope(envelope, List.of(first, second)))); var sent = fixture.sent();
            assertEquals(List.of("1", "2"), sent.getDocuments().stream().map(com.docusign.esign.model.Document::getDocumentId).toList());
            assertEquals(List.of("agreement.pdf", "second.pdf"), sent.getDocuments().stream().map(com.docusign.esign.model.Document::getName).toList());
            assertArrayEquals(new byte[]{1,2}, Base64.getDecoder().decode(sent.getDocuments().get(1).getDocumentBase64())); fixture.noFetch();
        }
    }
    @Test void reorderedSnapshotIdentityIsRefusedBeforeSdkIo() throws Exception {
        try (var fixture = new Fixture(temporary)) {
            var envelope = DocuSignEnvelopeMappingTest.envelope().toBuilder().documentIds(List.of(DocuSignEnvelopeMappingTest.DOCUMENT, SECOND)).build();
            assertThrows(DocuSignAdapterException.class, () -> await(fixture.port.createEnvelope(envelope,
                    List.of(snapshot(SECOND, new byte[]{1}), snapshot(DocuSignEnvelopeMappingTest.DOCUMENT, new byte[]{2}))))); fixture.noIo();
        }
    }
    @Test void duplicateDocumentIdentityIsRefusedBeforeSdkIo() throws Exception {
        try (var fixture = new Fixture(temporary)) {
            var envelope = DocuSignEnvelopeMappingTest.envelope().toBuilder().documentIds(List.of(DocuSignEnvelopeMappingTest.DOCUMENT, DocuSignEnvelopeMappingTest.DOCUMENT)).build();
            var value = snapshot(DocuSignEnvelopeMappingTest.DOCUMENT, new byte[]{1});
            assertThrows(DocuSignAdapterException.class, () -> await(fixture.port.createEnvelope(envelope, List.of(value, value)))); fixture.noIo();
        }
    }
    @Test void nullMissingExtraAndNullElementSnapshotsAreRefusedBeforeSdkIo() throws Exception {
        try (var fixture = new Fixture(temporary)) {
            var envelope = DocuSignEnvelopeMappingTest.envelope(); var value = snapshot(DocuSignEnvelopeMappingTest.DOCUMENT, new byte[]{1});
            assertThrows(DocuSignAdapterException.class, () -> await(fixture.port.createEnvelope(envelope, null)));
            assertThrows(DocuSignAdapterException.class, () -> await(fixture.port.createEnvelope(envelope, List.of())));
            assertThrows(DocuSignAdapterException.class, () -> await(fixture.port.createEnvelope(envelope, List.of(value, value))));
            assertThrows(DocuSignAdapterException.class, () -> await(fixture.port.createEnvelope(envelope, java.util.Arrays.asList((SignatureEnvelopeDocument)null)))); fixture.noIo();
        }
    }
    @Test void callerListMutationBeforeSubscriptionIsRefusedBeforeSdkIo() throws Exception {
        try (var fixture = new Fixture(temporary)) {
            var supplied = new ArrayList<>(List.of(snapshot(DocuSignEnvelopeMappingTest.DOCUMENT, new byte[]{1})));
            var pending = fixture.port.createEnvelope(DocuSignEnvelopeMappingTest.envelope(), supplied); supplied.clear();
            assertThrows(DocuSignAdapterException.class, () -> await(pending)); fixture.noIo();
        }
    }
    @Test void envelopeDocumentIdentityMutationBeforeSubscriptionIsRefusedBeforeSdkIo() throws Exception {
        try (var fixture = new Fixture(temporary)) {
            var ids = new ArrayList<>(List.of(DocuSignEnvelopeMappingTest.DOCUMENT));
            var envelope = DocuSignEnvelopeMappingTest.envelope().toBuilder().documentIds(ids).build();
            var pending = fixture.port.createEnvelope(envelope, List.of(snapshot(DocuSignEnvelopeMappingTest.DOCUMENT, new byte[]{1})));
            ids.set(0, SECOND); assertThrows(DocuSignAdapterException.class, () -> await(pending)); fixture.noIo();
        }
    }
    @Test void elevenDocumentsAreRefusedBeforeSdkIo() throws Exception {
        try (var fixture = new Fixture(temporary)) {
            var ids = new ArrayList<UUID>(); var values = new ArrayList<SignatureEnvelopeDocument>();
            for (int i=0; i<11; i++) { var id = new UUID(0,i+1); ids.add(id); values.add(snapshot(id,new byte[]{1})); }
            var envelope = DocuSignEnvelopeMappingTest.envelope().toBuilder().documentIds(ids).build();
            assertThrows(DocuSignAdapterException.class, () -> await(fixture.port.createEnvelope(envelope, values))); fixture.noIo();
        }
    }
    @Test void unsupportedMediaTypeIsRefusedBeforeSdkIo() throws Exception {
        try (var fixture = new Fixture(temporary)) {
            var bytes = new byte[]{1}; var value = new SignatureEnvelopeDocument(DocuSignEnvelopeMappingTest.DOCUMENT, VERSION,
                    "agreement.txt", "text/plain", 1, digest(bytes), bytes);
            assertThrows(DocuSignAdapterException.class, () -> await(fixture.port.createEnvelope(DocuSignEnvelopeMappingTest.envelope(), List.of(value)))); fixture.noIo();
        }
    }
    @Test void otherAdaptersHaveNoInventedImmutableContentSupport() {
        var port = (SignatureEnvelopePort) java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{SignatureEnvelopePort.class},
                (proxy, method, args) -> java.lang.reflect.InvocationHandler.invokeDefault(proxy, method, args));
        assertThrows(UnsupportedOperationException.class, () -> await(port.createEnvelope(DocuSignEnvelopeMappingTest.envelope(), List.of())));
    }
    @Test void mismatchedSnapshotIdentityCannotEnterManagedAuthentication() throws Exception {
        var service = temporary.resolve("managed-builder.service"); Files.writeString(service, ScopedSdkConnector.Builder.class.getName() + "\n");
        try (var transport = new ScopedSdkConnector.Scope(service)) {
            var p = new DocuSignAdapterProperties(); p.setAccountId("00000000-0000-4000-8000-000000000003");
            p.setIntegrationKey("00000000-0000-4000-8000-000000000001"); p.setUserId("00000000-0000-4000-8000-000000000002");
            p.setBaseUrl("https://owned.invalid/restapi"); p.setAuthServer("account.owned.invalid");
            var generator = java.security.KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);
            p.setPrivateKey("-----BEGIN PRIVATE KEY-----\n" + Base64.getMimeEncoder(64, new byte[]{10})
                    .encodeToString(generator.generateKeyPair().getPrivate().getEncoded()) + "\n-----END PRIVATE KEY-----\n");
            var docs = mock(DocumentPort.class); var content = mock(DocumentContentPort.class);
            try (var client = new org.fireflyframework.ecm.adapter.docusign.client.ManagedDocuSignApiClient(
                    org.fireflyframework.ecm.adapter.docusign.client.DocuSignClientSettings.from(p))) {
                var port = new DocuSignSignatureEnvelopeAdapter(client,p,content,docs);
                assertThrows(DocuSignAdapterException.class, () -> await(port.createEnvelope(DocuSignEnvelopeMappingTest.envelope(),
                        List.of(snapshot(SECOND,new byte[]{1})))));
                assertTrue(transport.exchanges.isEmpty()); verifyNoInteractions(docs,content);
            }
            assertTrue(transport.clients.stream().allMatch(client -> client.isClosed()));
        }
    }

    @Test void snapshotSdkPathRetainsTheAcceptedImmutableAccountAfterPropertiesMutation() throws Exception {
        try (var fixture = new Fixture(temporary)) {
            fixture.properties.setAccountId("other-owned-account");
            assertNotNull(await(fixture.port.createEnvelope(DocuSignEnvelopeMappingTest.envelope(),
                    List.of(snapshot(DocuSignEnvelopeMappingTest.DOCUMENT, DocuSignEnvelopeMappingTest.PDF)))));
            assertEquals(1, fixture.transport.exchanges.size());
            assertTrue(fixture.transport.exchanges.getFirst().uri().getPath().contains("/accounts/owned-account/")); fixture.noFetch();
        }
    }

}

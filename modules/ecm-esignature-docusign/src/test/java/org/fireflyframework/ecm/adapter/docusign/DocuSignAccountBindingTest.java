package org.fireflyframework.ecm.adapter.docusign;

import com.docusign.esign.client.ApiClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.fireflyframework.ecm.adapter.docusign.client.DocuSignClientSettings;
import org.fireflyframework.ecm.adapter.docusign.client.ManagedDocuSignApiClient;
import org.fireflyframework.ecm.domain.enums.esignature.EnvelopeStatus;
import org.fireflyframework.ecm.domain.enums.esignature.SignatureProvider;
import org.fireflyframework.ecm.domain.model.document.Document;
import org.fireflyframework.ecm.domain.model.esignature.SignatureEnvelope;
import org.fireflyframework.ecm.port.document.DocumentContentPort;
import org.fireflyframework.ecm.port.document.DocumentPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.core.publisher.Mono;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DocuSignAccountBindingTest {
    @TempDir Path temporary;
    private static final String ACCOUNT = "00000000-0000-4000-8000-000000000003";
    private static final String OTHER = "00000000-0000-4000-8000-000000000004";
    private static final UUID ID = UUID.fromString("00000000-0000-4000-8000-000000000020");
    private DocuSignAdapterProperties properties() {
        var p = new DocuSignAdapterProperties(); p.setAccountId(ACCOUNT); return p;
    }
    private DocuSignAdapterProperties managedProperties() throws Exception {
        var p = properties(); p.setIntegrationKey("00000000-0000-4000-8000-000000000001");
        p.setUserId("00000000-0000-4000-8000-000000000002"); p.setBaseUrl("https://owned.invalid/restapi"); p.setAuthServer("account.owned.invalid");
        var generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);
        p.setPrivateKey("-----BEGIN PRIVATE KEY-----\n" + Base64.getMimeEncoder(64, new byte[]{10})
                .encodeToString(generator.generateKeyPair().getPrivate().getEncoded()) + "\n-----END PRIVATE KEY-----\n");
        return p;
    }
    private ScopedSdkConnector.Scope scope() throws Exception {
        var service = temporary.resolve("builder.service"); Files.writeString(service, ScopedSdkConnector.Builder.class.getName() + "\n");
        return new ScopedSdkConnector.Scope(service);
    }
    private DocuSignSignatureEnvelopeAdapter adapter(ApiClient client, DocuSignAdapterProperties p) {
        var docs = mock(DocumentPort.class); var content = mock(DocumentContentPort.class);
        when(docs.getDocument(DocuSignEnvelopeMappingTest.DOCUMENT)).thenReturn(Mono.just(Document.builder()
                .id(DocuSignEnvelopeMappingTest.DOCUMENT).name("agreement.pdf").mimeType("application/pdf").build()));
        when(content.getContent(DocuSignEnvelopeMappingTest.DOCUMENT)).thenReturn(Mono.just(DocuSignEnvelopeMappingTest.PDF.clone()));
        return new DocuSignSignatureEnvelopeAdapter(client, p, content, docs, (id, provider) -> Mono.just("external-envelope"));
    }
    private static <T> T await(Mono<T> value) { return value.block(Duration.ofSeconds(2)); }
    private static ScopedSdkConnector.Reply reply(ScopedSdkConnector.Wire wire) {
        if (wire.method().equals("POST")) return new ScopedSdkConnector.Reply(201, "{\"envelopeId\":\"external-envelope\",\"status\":\"created\"}");
        if (wire.method().equals("PUT")) return new ScopedSdkConnector.Reply(200, "{}");
        if (wire.uri().getPath().endsWith("/envelopes")) return new ScopedSdkConnector.Reply(200, "{\"envelopes\":[]}");
        return new ScopedSdkConnector.Reply(200, "{\"envelopeId\":\"external-envelope\",\"status\":\"created\"}");
    }
    @Test void everySdkReadAndWriteUsesTheOriginalAccountAfterPropertyMutation() throws Exception {
        try (var transport = scope()) {
            var client = new ApiClient(); client.setBasePath("https://owned.invalid/restapi");
            var p = properties(); var port = adapter(client, p); p.setAccountId(OTHER); transport.responder = DocuSignAccountBindingTest::reply;
            assertNotNull(await(port.createEnvelope(DocuSignEnvelopeMappingTest.envelope())));
            assertNotNull(await(port.getEnvelopeByExternalId("external-envelope", SignatureProvider.DOCUSIGN)));
            assertNotNull(await(port.getEnvelope(ID)));
            assertNotNull(await(port.updateEnvelope(SignatureEnvelope.builder().id(ID).title("Updated").build())));
            assertNotNull(await(port.sendEnvelope(ID, ID))); assertNotNull(await(port.voidEnvelope(ID, "Caller reason", ID)));
            assertEquals(true, await(port.existsEnvelope(ID)));
            assertNotNull(await(port.getEnvelopesByStatus(EnvelopeStatus.SENT, Instant.EPOCH, Instant.EPOCH.plusSeconds(1), 1).collectList()));
            assertEquals(11, transport.exchanges.size());
            for (var wire : transport.exchanges) assertTrue(wire.uri().getPath().contains("/accounts/" + ACCOUNT + "/"), wire.uri().getPath());
        }
    }
    @Test void blankingPropertiesAfterConstructionDoesNotEraseTheBoundAccount() throws Exception {
        try (var transport = scope()) {
            var client = new ApiClient(); client.setBasePath("https://owned.invalid/restapi");
            var p = properties(); var port = adapter(client, p); p.setAccountId(""); transport.responder = DocuSignAccountBindingTest::reply;
            assertNotNull(await(port.getEnvelopeByExternalId("external-envelope", SignatureProvider.DOCUSIGN)));
            assertTrue(transport.exchanges.getFirst().uri().getPath().contains("/accounts/" + ACCOUNT + "/"));
        }
    }
    @Test void legacyConstructorRefusesAnInitiallyBlankAccountBeforeAnyExchange() throws Exception {
        try (var transport = scope()) {
            var p = properties(); p.setAccountId("");
            assertThrows(DocuSignAdapterException.class, () -> adapter(new ApiClient(), p)); assertTrue(transport.exchanges.isEmpty());
        }
    }
    @Test void managedClientCannotBePairedWithPropertiesForAnotherAccount() throws Exception {
        try (var transport = scope()) {
            var p = managedProperties();
            try (var client = new ManagedDocuSignApiClient(DocuSignClientSettings.from(p))) {
                var other = properties(); other.setAccountId(OTHER);
                assertThrows(DocuSignAdapterException.class, () -> adapter(client, other)); assertTrue(transport.exchanges.isEmpty());
            }
            assertTrue(transport.clients.stream().allMatch(client -> client.isClosed()));
        }
    }
    @Test void authenticatedAccountAndEnvelopePathStayTogetherAfterPropertiesChange() throws Exception {
        try (var transport = scope()) {
            var p = managedProperties();
            try (var client = new ManagedDocuSignApiClient(DocuSignClientSettings.from(p))) {
                var port = adapter(client, p); p.setAccountId(OTHER);
                assertNotNull(await(port.getEnvelopeByExternalId("controlled-envelope", SignatureProvider.DOCUSIGN)));
                assertEquals(3, transport.exchanges.size());
                assertTrue(transport.exchanges.getLast().uri().getPath().contains("/accounts/" + ACCOUNT + "/"));
            }
            assertTrue(transport.clients.stream().allMatch(client -> client.isClosed()));
        }
    }
    @Test void endpointDriftCannotMakeFrozenBindingMetadataPointAtAnotherServer() throws Exception {
        try (var transport = scope()) {
            var client = new ApiClient(); client.setBasePath("https://owned.invalid/restapi");
            var port = adapter(client, properties()); client.setBasePath("https://other-owned.invalid/restapi"); transport.responder = DocuSignAccountBindingTest::reply;
            assertThrows(DocuSignAdapterException.class, () -> await(port.getEnvelopeByExternalId("external-envelope", SignatureProvider.DOCUSIGN)));
            assertTrue(transport.exchanges.isEmpty());
        }
    }
    @Test void autoConfiguredClientAndAdapterExposeTheSameBindingWithoutAllocationOrAuthentication() throws Exception {
        try (var transport = scope()) {
            var p = managedProperties();
            try (var client = (ManagedDocuSignApiClient) new DocuSignAdapterAutoConfiguration().docuSignApiClient(p)) {
                var port = adapter(client, p);
                int clients = transport.clients.size();
                var lookup = port.accountBinding();
                assertEquals(clients, transport.clients.size()); assertTrue(transport.exchanges.isEmpty());
                p.setAccountId(OTHER); p.setBaseUrl("https://other-owned.invalid/restapi");
                var binding = await(lookup);
                assertSame(client.accountBinding(), binding); assertSame(binding, await(port.accountBinding()));
                assertEquals(SignatureProvider.DOCUSIGN, binding.provider()); assertEquals(ACCOUNT, binding.accountId());
                assertEquals("https://owned.invalid/restapi", binding.endpoint());
                assertFalse(binding.toString().contains(p.getPrivateKey())); assertFalse(binding.toString().contains(p.getIntegrationKey()));
                assertEquals(clients, transport.clients.size()); assertTrue(transport.exchanges.isEmpty());
            }
            assertTrue(transport.clients.stream().allMatch(client -> client.isClosed()));
        }
    }
    @Test void legacyBindingLookupIsLazyAndVerifiesEndpointOnSubscription() throws Exception {
        try (var transport = scope()) {
            var client = new ApiClient(); client.setBasePath("https://owned.invalid/restapi");
            var p = properties(); var port = adapter(client, p);
            var lookup = port.accountBinding(); int clients = transport.clients.size();
            p.setAccountId(OTHER); assertEquals(ACCOUNT, await(lookup).accountId());
            client.setBasePath("https://other-owned.invalid/restapi");
            assertThrows(DocuSignAdapterException.class, () -> await(lookup));
            assertEquals(clients, transport.clients.size()); assertTrue(transport.exchanges.isEmpty());
        }
    }
    @Test void managedEndpointCannotDriftFromItsSettingsOrExposedBinding() throws Exception {
        try (var transport = scope()) {
            var p = managedProperties();
            try (var client = new ManagedDocuSignApiClient(DocuSignClientSettings.from(p))) {
                assertThrows(DocuSignAdapterException.class, () -> client.setBasePath("https://other-owned.invalid/restapi"));
                assertEquals(client.accountBinding().endpoint(), client.getBasePath()); assertTrue(transport.exchanges.isEmpty());
            }
            assertTrue(transport.clients.stream().allMatch(client -> client.isClosed()));
        }
    }
    @Test void legacySnapshotRefusesInsecureOrCredentialBearingEndpointsLocally() throws Exception {
        try (var transport = scope()) {
            assertThrows(IllegalArgumentException.class, () -> new org.fireflyframework.ecm.domain.model.esignature.SignatureEnvelopeAccountBinding(
                    SignatureProvider.DOCUSIGN, ACCOUNT, "http://owned.invalid/restapi"));
            for (String endpoint : java.util.List.of("https://user@owned.invalid/restapi",
                    "https://owned.invalid/restapi?secret=value", "https://owned.invalid/restapi#fragment")) {
                var client = new ApiClient(); client.setBasePath(endpoint);
                assertThrows(DocuSignAdapterException.class, () -> adapter(client, properties()));
            }
            assertTrue(transport.exchanges.isEmpty());
        }
    }
    @Test void otherAdaptersRetainAnExplicitUnsupportedDefault() {
        var port = (org.fireflyframework.ecm.port.esignature.SignatureEnvelopePort) java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{org.fireflyframework.ecm.port.esignature.SignatureEnvelopePort.class},
                (proxy, method, args) -> java.lang.reflect.InvocationHandler.invokeDefault(proxy, method, args));
        assertThrows(UnsupportedOperationException.class, () -> await(port.accountBinding()));
    }

}

package org.fireflyframework.ecm.adapter.docusign;

import com.docusign.esign.client.JSON;
import com.docusign.esign.model.EnvelopeDefinition;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.fireflyframework.ecm.adapter.docusign.client.DocuSignClientSettings;
import org.fireflyframework.ecm.adapter.docusign.client.ManagedDocuSignApiClientImpl;
import org.fireflyframework.ecm.port.document.DocumentPort;
import org.fireflyframework.ecm.port.document.DocumentContentPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DocuSignImmutableIntentTest {
    @TempDir Path temporary;
    @Test void authenticationWindowMutationCannotChangeTheWireOrReturnedIntentAssociation() throws Exception {
        var service=temporary.resolve("builder.service");Files.writeString(service,ScopedSdkConnector.Builder.class.getName()+"\n");
        var p=new DocuSignAdapterProperties();p.setAccountId("00000000-0000-4000-8000-000000000003");
        p.setIntegrationKey("00000000-0000-4000-8000-000000000001");p.setUserId("00000000-0000-4000-8000-000000000002");
        p.setBaseUrl("https://owned.invalid/restapi");p.setAuthServer("account.owned.invalid");
        var generator=KeyPairGenerator.getInstance("RSA");generator.initialize(2048);
        p.setPrivateKey("-----BEGIN PRIVATE KEY-----\n"+Base64.getMimeEncoder(64,new byte[]{10})
                .encodeToString(generator.generateKeyPair().getPrivate().getEncoded())+"\n-----END PRIVATE KEY-----\n");
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        try(var transport=new ScopedSdkConnector.Scope(service)) {
            try(var client=new ManagedDocuSignApiClientImpl(DocuSignClientSettings.from(p))) {
                var documents=mock(DocumentPort.class);var content=mock(DocumentContentPort.class);
                var port=new DocuSignSignatureEnvelopeAdapterImpl(client,p,content,documents);
                var original=DocuSignEnvelopeMappingTest.envelope();
                var envelopeMetadata=new HashMap<String,Object>();var requestMetadata=new HashMap<String,Object>();var fieldMetadata=new HashMap<String,Object>();
                var fields=new ArrayList<>(List.of(original.getSignatureRequests().getFirst().getSignatureFields().getFirst()
                        .toBuilder().metadata(fieldMetadata).build()));
                var requests=new ArrayList<>(List.of(original.getSignatureRequests().getFirst().toBuilder()
                        .signatureFields(fields).metadata(requestMetadata).build()));
                var ids=new ArrayList<>(original.getDocumentIds());
                var envelope=original.toBuilder().documentIds(ids).signatureRequests(requests).metadata(envelopeMetadata).build();
                transport.responder=wire->{
                    if(wire.uri().getPath().endsWith("/oauth/token")) {
                        entered.countDown();
                        try { if(!release.await(2,TimeUnit.SECONDS))throw new AssertionError("Owned auth release expired"); }
                        catch(InterruptedException error){Thread.currentThread().interrupt();throw new AssertionError(error);}
                    }
                    if(wire.method().equals("POST")&&wire.uri().getPath().endsWith("/envelopes"))
                        return new ScopedSdkConnector.Reply(201,"{\"envelopeId\":\"external-envelope\",\"status\":\"created\"}");
                    return transport.defaultReply(wire);
                };
                var pending=port.createEnvelope(envelope,List.of(DocuSignImmutableContentTest.snapshot(
                        DocuSignEnvelopeMappingTest.DOCUMENT,DocuSignEnvelopeMappingTest.PDF))).toFuture();
                try {
                    assertTrue(entered.await(1,TimeUnit.SECONDS),"Actual token exchange must be reached");
                    requests.clear();fields.clear();ids.clear();
                    envelopeMetadata.put("changed",true);requestMetadata.put("changed",true);fieldMetadata.put("changed",true);
                    release.countDown();var result=pending.get(2,TimeUnit.SECONDS);
                    assertEquals(3,transport.exchanges.size());verifyNoInteractions(documents,content);
                    var wire=transport.exchanges.getLast();
                    var sent=new JSON().getObjectMapper().readValue(wire.bytes(),EnvelopeDefinition.class);
                    assertEquals(1,sent.getRecipients().getSigners().size());
                    assertEquals(1,sent.getRecipients().getSigners().getFirst().getTabs().getSignHereTabs().size());
                    assertArrayEquals(DocuSignEnvelopeMappingTest.PDF,Base64.getDecoder().decode(sent.getDocuments().getFirst().getDocumentBase64()));
                    assertEquals(List.of(DocuSignEnvelopeMappingTest.DOCUMENT),result.getDocumentIds());
                    assertEquals(1,result.getSignatureRequests().size());assertEquals(1,result.getSignatureRequests().getFirst().getSignatureFields().size());
                    assertTrue(result.getMetadata().isEmpty());assertTrue(result.getSignatureRequests().getFirst().getMetadata().isEmpty());
                    assertTrue(result.getSignatureRequests().getFirst().getSignatureFields().getFirst().getMetadata().isEmpty());
                    assertThrows(UnsupportedOperationException.class,()->result.getSignatureRequests().clear());
                    assertThrows(UnsupportedOperationException.class,()->result.getSignatureRequests().getFirst().getSignatureFields().clear());
                } finally {release.countDown();pending.cancel(true);}
            }
            assertTrue(transport.clients.stream().allMatch(client->client.isClosed()));
        }
    }
}

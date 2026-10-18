package org.fireflyframework.ecm.adapter.docusign;

import com.docusign.esign.client.ApiClient;
import org.fireflyframework.ecm.port.esignature.SignatureEnvelopePort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = DocuSignAdapterTest.TestConfig.class)
@TestPropertySource(properties = {
        "firefly.ecm.enabled=true",
        "firefly.ecm.features.esignature=true",
        "firefly.ecm.esignature.provider=docusign",
        "firefly.ecm.adapter.docusign.integration-key=ik",
        "firefly.ecm.adapter.docusign.user-id=uid",
        "firefly.ecm.adapter.docusign.account-id=aid",
        "firefly.ecm.adapter.docusign.private-key=-----BEGIN PRIVATE KEY-----\nMIIBVwIBADANBgkqhkiG9w0BAQEFAASCAT8wggE7AgEAAkEA\n-----END PRIVATE KEY-----",
        "spring.autoconfigure.exclude=org.fireflyframework.ecm.adapter.docusign.DocuSignAdapterAutoConfiguration"
})
class DocuSignAdapterTest {

    @Autowired
    private SignatureEnvelopePort envelopePort;

    @Test
    void contextLoads_andDocuSignAdapterPresent() {
        assertThat(envelopePort).isInstanceOf(DocuSignSignatureEnvelopeAdapter.class);
    }

    @Configuration
    @Import({DocuSignSignatureEnvelopeAdapter.class})
    static class TestConfig {
        @Bean
        ApiClient apiClient() {
            return new ApiClient();
        }
        @Bean
        DocuSignAdapterProperties properties() {
            DocuSignAdapterProperties p = new DocuSignAdapterProperties();
            p.setIntegrationKey("ik");
            p.setUserId("uid");
            p.setAccountId("aid");
            p.setPrivateKey("-----BEGIN PRIVATE KEY-----\nABC\n-----END PRIVATE KEY-----");
            p.setSandboxMode(true);
            return p;
        }
        @Bean
        org.fireflyframework.ecm.port.document.DocumentContentPort documentContentPort() {
            return org.mockito.Mockito.mock(org.fireflyframework.ecm.port.document.DocumentContentPort.class);
        }
        @Bean
        org.fireflyframework.ecm.port.document.DocumentPort documentPort() {
            return org.mockito.Mockito.mock(org.fireflyframework.ecm.port.document.DocumentPort.class);
        }
    }
}

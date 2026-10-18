package org.fireflyframework.ecm.adapter.azureblob;

import com.azure.storage.blob.BlobContainerClient;
import org.fireflyframework.ecm.port.document.DocumentContentPort;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = AzureBlobAdapterTest.TestConfig.class)
@TestPropertySource(properties = {
        "firefly.ecm.enabled=true",
        "firefly.ecm.adapter-type=azure-blob"
})
class AzureBlobAdapterTest {

    @Autowired
    private DocumentContentPort contentPort;

    @Test
    void contextLoads_andAzureDocumentContentAdapterPresent() {
        assertThat(contentPort).isInstanceOf(AzureBlobDocumentContentAdapter.class);
    }

    @Configuration
    @Import(AzureBlobDocumentContentAdapter.class)
    static class TestConfig {
        @Bean
        BlobContainerClient blobContainerClient() {
            return Mockito.mock(BlobContainerClient.class);
        }
        @Bean
        AzureBlobAdapterProperties azureBlobAdapterProperties() {
            AzureBlobAdapterProperties p = new AzureBlobAdapterProperties();
            p.setAccountName("test");
            p.setContainerName("test-container");
            p.setPathPrefix("documents/");
            return p;
        }
        @Bean(name = "azureBlobCircuitBreaker")
        io.github.resilience4j.circuitbreaker.CircuitBreaker circuitBreaker() {
            return io.github.resilience4j.circuitbreaker.CircuitBreaker.ofDefaults("azureBlobStorage");
        }
        @Bean(name = "azureBlobRetry")
        io.github.resilience4j.retry.Retry retry() {
            return io.github.resilience4j.retry.Retry.ofDefaults("azureBlobStorage");
        }
    }
}

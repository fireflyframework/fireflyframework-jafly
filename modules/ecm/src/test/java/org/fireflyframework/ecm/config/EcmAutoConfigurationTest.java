package org.fireflyframework.ecm.config;

import org.fireflyframework.ecm.port.document.DocumentContentPort;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = EcmAutoConfiguration.class)
@TestPropertySource(properties = {
        "firefly.ecm.enabled=true",
        "firefly.ecm.features.document-management=true",
        "firefly.ecm.features.content-storage=true"
})
class EcmAutoConfigurationTest {

    @Autowired
    private DocumentContentPort documentContentPort;

    @Test
    void contextLoads_andDocumentContentPortBeanPresent() {
        assertThat(documentContentPort).isNotNull();
    }
}

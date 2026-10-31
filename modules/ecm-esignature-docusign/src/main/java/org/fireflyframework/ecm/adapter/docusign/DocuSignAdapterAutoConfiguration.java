/*
 * Copyright 2024 Firefly Software Foundation.
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
package org.fireflyframework.ecm.adapter.docusign;

import com.docusign.esign.client.ApiClient;
import org.fireflyframework.ecm.adapter.docusign.client.DocuSignClientSettings;
import org.fireflyframework.ecm.adapter.docusign.client.ManagedDocuSignApiClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for the DocuSign ECM adapter.
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(ApiClient.class)
@ConditionalOnProperty(name = "firefly.ecm.esignature.provider", havingValue = "docusign")
@EnableConfigurationProperties(DocuSignAdapterProperties.class)
public class DocuSignAdapterAutoConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public ApiClient docuSignApiClient(DocuSignAdapterProperties properties) {
        DocuSignClientSettings settings = DocuSignClientSettings.from(properties);
        try {
            ApiClient client = new ManagedDocuSignApiClient(settings);
            log.info("Configured DocuSign client; authentication is deferred until an operation");
            return client;
        } catch (Exception e) {
            throw DocuSignAdapterException.safe(e, DocuSignAdapterException.Operation.CONFIGURATION);
        }
    }
}

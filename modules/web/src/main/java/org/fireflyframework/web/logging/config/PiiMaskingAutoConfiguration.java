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


package org.fireflyframework.web.logging.config;

import org.fireflyframework.web.logging.service.PiiMaskingService;
import org.fireflyframework.web.logging.service.StdoutMaskingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

/**
 * Auto-configuration for PII (Personally Identifiable Information) masking functionality.
 * This configuration is active when pii-masking.enabled=true (which is the default).
 * 
 * The configuration provides:
 * - PiiMaskingProperties: Configuration properties for PII patterns and masking behavior
 * - PiiMaskingService: Service that performs the actual PII detection and masking
 * - StdoutMaskingService: Service that automatically masks PII in System.out.println calls
 * 
 * Usage:
 * The PII masking functionality is automatically enabled by default, including stdout masking.
 * To disable PII masking entirely, set pii-masking.enabled=false.
 * To disable only stdout masking, set pii-masking.enable-stdout-masking=false.
 * 
 * Configuration example:
 * <pre>
 * pii-masking:
 *   enabled: true
 *   enable-stdout-masking: true
 *   mask-character: "*"
 *   preserve-length: true
 *   patterns:
 *     email: "\\b[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}\\b"
 *     phone: "\\b(?:\\+?1[-\\s]?)?(?:\\(?[0-9]{3}\\)?[-\\s]?)?[0-9]{3}[-\\s]?[0-9]{4}\\b"
 *   custom-patterns:
 *     internal-id: "ID-[0-9]{6}"
 * </pre>
 */
@AutoConfiguration
@EnableConfigurationProperties(PiiMaskingProperties.class)
@ConditionalOnProperty(name = "pii-masking.enabled", havingValue = "true", matchIfMissing = true)
public class PiiMaskingAutoConfiguration {

    /**
     * Creates the PII masking service bean.
     * This service will be used throughout the application for masking PII data in logs.
     * 
     * @param properties the PII masking configuration properties
     * @return PiiMaskingService instance configured with the provided properties
     */
    @Bean
    @ConditionalOnMissingBean
    public PiiMaskingService piiMaskingService(PiiMaskingProperties properties) {
        return new PiiMaskingService(properties);
    }

    /**
     * Creates the stdout masking service bean.
     * This service automatically masks PII in System.out.println calls when enabled.
     * 
     * @param piiMaskingService the PII masking service to use for masking
     * @return StdoutMaskingService instance configured with the PII masking service
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "pii-masking.enable-stdout-masking", havingValue = "true", matchIfMissing = true)
    public StdoutMaskingService stdoutMaskingService(PiiMaskingService piiMaskingService) {
        return new StdoutMaskingService(piiMaskingService);
    }

    /**
     * Configuration class for stdout masking functionality that automatically enables
     * stdout masking when the application is ready.
     */
    @Configuration
    @ConditionalOnProperty(name = "pii-masking.enable-stdout-masking", havingValue = "true", matchIfMissing = true)
    public static class StdoutMaskingConfiguration {

        private final StdoutMaskingService stdoutMaskingService;

        public StdoutMaskingConfiguration(@org.springframework.beans.factory.annotation.Autowired(required = false) StdoutMaskingService stdoutMaskingService) {
            this.stdoutMaskingService = stdoutMaskingService;
        }

        @EventListener
        public void onApplicationReady(ApplicationReadyEvent event) {
            if (stdoutMaskingService != null) {
                stdoutMaskingService.enableStdoutMasking();
            }
        }
    }
}
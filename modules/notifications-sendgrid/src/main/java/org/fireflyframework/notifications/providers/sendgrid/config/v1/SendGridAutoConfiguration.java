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


package org.fireflyframework.notifications.providers.sendgrid.config.v1;

import org.fireflyframework.notifications.interfaces.providers.email.v1.EmailProvider;
import org.fireflyframework.notifications.providers.sendgrid.core.v1.SendGridEmailProvider;
import org.fireflyframework.notifications.providers.sendgrid.properties.v1.SendGridProperties;
import com.sendgrid.SendGrid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@Slf4j
@AutoConfiguration
@ConditionalOnProperty(name = "firefly.notifications.email.provider", havingValue = "sendgrid")
@ConditionalOnClass(SendGrid.class)
@EnableConfigurationProperties(SendGridProperties.class)
public class SendGridAutoConfiguration {

    @ConditionalOnMissingBean
    @Bean
    @ConditionalOnProperty(prefix = "firefly.notifications.sendgrid", name = "api-key")
    public SendGrid sendGrid(SendGridProperties properties) {
        log.info("Initializing SendGrid email provider");
        return new SendGrid(properties.getApiKey());
    }

    @Bean
    @ConditionalOnMissingBean(EmailProvider.class)
    public EmailProvider sendGridEmailProvider(SendGridProperties properties, SendGrid sendGrid) {
        return new SendGridEmailProvider(properties, sendGrid);
    }
}
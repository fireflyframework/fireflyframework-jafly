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


package org.fireflyframework.notifications.providers.twilio.config.v1;

import org.fireflyframework.notifications.interfaces.providers.sms.v1.SMSProvider;
import org.fireflyframework.notifications.providers.twilio.core.v1.TwilioSMSProvider;
import org.fireflyframework.notifications.providers.twilio.properties.v1.TwilioProperties;
import com.twilio.Twilio;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@Slf4j
@AutoConfiguration
@ConditionalOnProperty(name = "firefly.notifications.sms.provider", havingValue = "twilio")
@ConditionalOnClass(com.twilio.Twilio.class)
@EnableConfigurationProperties(TwilioProperties.class)
public class TwilioAutoConfiguration {

    @ConditionalOnMissingBean
    @Bean
    public com.twilio.Twilio twilioInit(TwilioProperties twilioProperties) {
        log.info("Initializing Twilio SMS provider with account SID: {}",
                twilioProperties.getAccountSid());
        Twilio.init(
                twilioProperties.getAccountSid(),
                twilioProperties.getAuthToken()
        );
        return null;
    }

    @Bean
    @ConditionalOnMissingBean
    public SMSProvider twilioSMSProvider(TwilioProperties twilioProperties) {
        return new TwilioSMSProvider(twilioProperties);
    }
}
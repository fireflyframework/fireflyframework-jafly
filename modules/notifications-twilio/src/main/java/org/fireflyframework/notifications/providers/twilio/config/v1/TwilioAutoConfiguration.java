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
import org.fireflyframework.notifications.providers.twilio.core.v1.TwilioSMSProviderImpl;
import org.fireflyframework.notifications.providers.twilio.properties.v1.TwilioProperties;
import com.twilio.Twilio;
import com.twilio.http.TwilioRestClient;
import org.springframework.context.annotation.Lazy;
import org.springframework.util.StringUtils;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnProperty(name = "firefly.notifications.sms.provider", havingValue = "twilio")
@ConditionalOnClass(com.twilio.Twilio.class)
@EnableConfigurationProperties(TwilioProperties.class)
public class TwilioAutoConfiguration {

    /**
     * @deprecated Configure an instance client instead; this compatibility hook does not change SDK global state.
     */
    @Deprecated
    @ConditionalOnMissingBean
    @Bean
    public com.twilio.Twilio twilioInit(TwilioProperties twilioProperties) {
        return null;
    }

    @Bean
    @Lazy
    @ConditionalOnMissingBean(TwilioRestClient.class)
    public TwilioRestClient twilioRestClient(TwilioProperties twilioProperties) {
        if (!StringUtils.hasText(twilioProperties.getAccountSid()) || !StringUtils.hasText(twilioProperties.getAuthToken())) {
            throw new IllegalStateException("Twilio SMS credentials not configured");
        }
        return new TwilioRestClient.Builder(twilioProperties.getAccountSid(), twilioProperties.getAuthToken()).build();
    }

    public SMSProvider twilioSMSProvider(TwilioProperties twilioProperties) {
        return new TwilioSMSProviderImpl(twilioProperties);
    }

    @Bean
    @ConditionalOnMissingBean(SMSProvider.class)
    public SMSProvider twilioSMSProvider(TwilioProperties twilioProperties, TwilioRestClient client) {
        return new TwilioSMSProviderImpl(twilioProperties, client);
    }
}

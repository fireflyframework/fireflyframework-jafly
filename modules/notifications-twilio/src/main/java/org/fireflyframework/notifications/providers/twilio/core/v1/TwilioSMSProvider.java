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


package org.fireflyframework.notifications.providers.twilio.core.v1;

import org.fireflyframework.notifications.interfaces.dtos.sms.v1.SMSRequestDTO;
import org.fireflyframework.notifications.interfaces.dtos.sms.v1.SMSResponseDTO;
import org.fireflyframework.notifications.interfaces.providers.sms.v1.SMSProvider;
import org.fireflyframework.notifications.providers.twilio.properties.v1.TwilioProperties;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class TwilioSMSProvider implements SMSProvider {

    private final TwilioProperties twilioProperties;

    public TwilioSMSProvider(TwilioProperties twilioProperties) {
        this.twilioProperties = twilioProperties;
    }

    @Override
    public Mono<SMSResponseDTO> sendSMS(SMSRequestDTO request) {
        if (request == null) {
            return Mono.just(SMSResponseDTO.error("Request cannot be null"));
        }
        if (!StringUtils.hasText(request.getPhoneNumber())) {
            return Mono.just(SMSResponseDTO.error("Phone number cannot be empty"));
        }
        if (!StringUtils.hasText(request.getMessage())) {
            return Mono.just(SMSResponseDTO.error("Message cannot be empty"));
        }
        if (!StringUtils.hasText(twilioProperties.getPhoneNumber())) {
            return Mono.just(SMSResponseDTO.error("Sender phone number not configured"));
        }

        return Mono.fromCallable(() -> {
            Message message = Message.creator(
                    new PhoneNumber(request.getPhoneNumber().trim()),
                    new PhoneNumber(twilioProperties.getPhoneNumber().trim()),
                    request.getMessage().trim()
            ).create();

            return SMSResponseDTO.success(message.getSid());
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
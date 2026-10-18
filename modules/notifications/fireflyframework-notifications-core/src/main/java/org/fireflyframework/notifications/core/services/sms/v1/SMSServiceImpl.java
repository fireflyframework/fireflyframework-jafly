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


package org.fireflyframework.notifications.core.services.sms.v1;

import org.fireflyframework.notifications.interfaces.dtos.sms.v1.SMSRequestDTO;
import org.fireflyframework.notifications.interfaces.dtos.sms.v1.SMSResponseDTO;
import org.fireflyframework.notifications.interfaces.providers.sms.v1.SMSProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class SMSServiceImpl implements SMSService {

    private final SMSProvider smsProvider;

    @Override
    public Mono<SMSResponseDTO> sendSMS(SMSRequestDTO request) {
        return smsProvider.sendSMS(request)
                .onErrorResume(error -> Mono.just(SMSResponseDTO.error(error.getMessage())));
    }
}
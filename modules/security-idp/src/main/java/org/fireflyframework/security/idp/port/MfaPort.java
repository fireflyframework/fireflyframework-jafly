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

package org.fireflyframework.security.idp.port;

import org.fireflyframework.security.idp.dtos.MfaChallengeResponse;
import org.fireflyframework.security.idp.dtos.MfaVerifyRequest;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;

/**
 * Segregated IdP capability port for multi-factor authentication challenge/verify.
 */
public interface MfaPort {

    default Mono<ResponseEntity<MfaChallengeResponse>> mfaChallenge(String username) {
        return Mono.error(new UnsupportedOperationException("mfaChallenge is not supported by this IdP provider"));
    }

    default Mono<Void> mfaVerify(MfaVerifyRequest request) {
        return Mono.error(new UnsupportedOperationException("mfaVerify is not supported by this IdP provider"));
    }
}

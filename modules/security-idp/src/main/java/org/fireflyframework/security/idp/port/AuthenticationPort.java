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

import org.fireflyframework.security.idp.dtos.LoginRequest;
import org.fireflyframework.security.idp.dtos.LogoutRequest;
import org.fireflyframework.security.idp.dtos.RefreshRequest;
import org.fireflyframework.security.idp.dtos.TokenResponse;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;

/**
 * Segregated IdP capability port for authentication / token-lifecycle operations.
 *
 * <p>Each method has a {@code NotSupported} default so an adapter implements only the capabilities
 * its provider actually offers, instead of stubbing the rest. The aggregate {@code IdpAdapter}
 * composes this with the other capability ports.
 */
public interface AuthenticationPort {

    default Mono<ResponseEntity<TokenResponse>> login(LoginRequest request) {
        return Mono.error(new UnsupportedOperationException("login is not supported by this IdP provider"));
    }

    default Mono<ResponseEntity<TokenResponse>> refresh(RefreshRequest request) {
        return Mono.error(new UnsupportedOperationException("refresh is not supported by this IdP provider"));
    }

    default Mono<Void> logout(LogoutRequest request) {
        return Mono.error(new UnsupportedOperationException("logout is not supported by this IdP provider"));
    }
}

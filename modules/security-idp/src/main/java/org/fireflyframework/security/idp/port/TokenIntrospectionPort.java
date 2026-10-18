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

import org.fireflyframework.security.idp.dtos.IntrospectionResponse;
import org.fireflyframework.security.idp.dtos.UserInfoResponse;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;

/**
 * Segregated IdP capability port for token introspection (RFC 7662) and OIDC userinfo. This is the
 * provider-facing source behind the security tier's opaque-token validation path.
 */
public interface TokenIntrospectionPort {

    default Mono<ResponseEntity<IntrospectionResponse>> introspect(String accessToken) {
        return Mono.error(new UnsupportedOperationException("introspect is not supported by this IdP provider"));
    }

    default Mono<ResponseEntity<UserInfoResponse>> getUserInfo(String accessToken) {
        return Mono.error(new UnsupportedOperationException("getUserInfo is not supported by this IdP provider"));
    }
}

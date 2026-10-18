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

import org.fireflyframework.security.idp.dtos.SessionInfo;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Segregated IdP capability port for session and refresh-token lifecycle management.
 */
public interface SessionPort {

    default Mono<Void> revokeRefreshToken(String refreshToken) {
        return Mono.error(new UnsupportedOperationException("revokeRefreshToken is not supported by this IdP provider"));
    }

    default Mono<ResponseEntity<List<SessionInfo>>> listSessions(String userId) {
        return Mono.error(new UnsupportedOperationException("listSessions is not supported by this IdP provider"));
    }

    default Mono<Void> revokeSession(String sessionId) {
        return Mono.error(new UnsupportedOperationException("revokeSession is not supported by this IdP provider"));
    }
}

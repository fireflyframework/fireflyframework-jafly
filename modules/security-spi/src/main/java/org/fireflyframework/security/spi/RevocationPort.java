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

package org.fireflyframework.security.spi;

import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Tracks revoked tokens/sessions so even an unexpired, validly-signed token can be rejected.
 * Backed by a fast store (Redis) with entries expiring at the token's {@code exp}.
 */
public interface RevocationPort {

    Mono<Boolean> isRevoked(String tokenId);

    Mono<Void> revoke(String tokenId, Instant expiresAt);
}

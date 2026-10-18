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

import org.fireflyframework.security.api.domain.SigningKey;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Manages first-party token signing keys and the published JWKS. Backends include an in-memory
 * dev generator, HashiCorp Vault Transit, and cloud KMS. Rotation keeps the previous public key
 * in {@link #verificationKeys()} for an overlap window so in-flight tokens stay verifiable.
 */
public interface KeyManagementPort {

    /** @return the current key used to sign newly issued tokens (has a private key). */
    Mono<SigningKey> activeSigningKey();

    /** @return all currently-valid keys for verification (active + overlapping previous). */
    Flux<SigningKey> verificationKeys();

    /** @return the published JWKS document as JSON (public keys only). */
    Mono<String> jwkSetJson();

    /** Rotate to a fresh signing key, retaining the previous for the overlap window. */
    Mono<Void> rotate();
}

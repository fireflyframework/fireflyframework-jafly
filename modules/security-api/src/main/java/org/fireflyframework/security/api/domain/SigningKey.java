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

package org.fireflyframework.security.api.domain;

import java.security.PrivateKey;
import java.security.PublicKey;

/**
 * A signing key pair identified by a {@code kid}, used by the key-management layer to sign
 * first-party tokens and to publish a JWKS for verification. Uses only JDK crypto types so the
 * SPI stays free of third-party JOSE dependencies.
 *
 * @param kid        the key identifier published in JWS headers and the JWKS
 * @param algorithm  the JWS algorithm (e.g. {@code RS256}, {@code ES256})
 * @param privateKey the private key (present only for the active signing key; {@code null} for verify-only keys)
 * @param publicKey  the public key (always present)
 */
public record SigningKey(String kid, String algorithm, PrivateKey privateKey, PublicKey publicKey) {

    public boolean canSign() {
        return privateKey != null;
    }
}

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

package org.fireflyframework.security.rs;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.*;
import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.fireflyframework.security.spi.TokenValidationPort;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.*;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.security.*;
import java.security.interfaces.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class LiveJwtNativeVerificationTest {
    @Test void genuineNimbusSignatureAndIssuerValidationPrecedeEveryLiveExchange() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);
        var pair = generator.generateKeyPair(); var foreign = generator.generateKeyPair();
        var properties = new LiveActiveJwtDecoderTest().properties();
        var local = NimbusReactiveJwtDecoder.withPublicKey((RSAPublicKey)pair.getPublic()).build();
        local.setJwtValidator(JwtValidators.createDefaultWithIssuer(LiveActiveJwtDecoderTest.ISSUER));
        var received = new AtomicReference<Jwt>(); var calls = new AtomicInteger();
        TokenValidationPort live = new TokenValidationPort() {
            public Mono<SecurityPrincipal> validate(org.fireflyframework.security.api.domain.BearerToken token) { throw new AssertionError(); }
            public Mono<SecurityPrincipal> validateLive(org.fireflyframework.security.api.domain.BearerToken token) {
                calls.incrementAndGet(); var jwt = received.get();
                return Mono.just(SecurityPrincipal.builder().issuer(jwt.getClaimAsString("iss")).subject(jwt.getSubject())
                        .claims(Map.of("iss", jwt.getClaimAsString("iss"), "sub", jwt.getSubject(), "aud", jwt.getAudience(),
                                "exp", jwt.getExpiresAt(), "client_id", "workforce-client", "jti", "opaque-token-id")).build());
            }
        };
        var decoder = new LiveActiveJwtDecoderImpl(raw -> local.decode(raw).doOnNext(received::set), live, properties);
        String valid = sign(pair, LiveActiveJwtDecoderTest.ISSUER, Instant.now().plusSeconds(120));
        assertSame(receivedValue(decoder, valid), received.get()); assertEquals(1, calls.get());
        for (String invalid : List.of(sign(foreign, LiveActiveJwtDecoderTest.ISSUER, Instant.now().plusSeconds(120)),
                sign(pair, "https://other.invalid", Instant.now().plusSeconds(120)),
                sign(pair, LiveActiveJwtDecoderTest.ISSUER, Instant.now().minusSeconds(180)))) {
            StepVerifier.create(decoder.decode(invalid)).expectError(BadJwtException.class).verify();
        }
        assertEquals(1, calls.get());
    }
    Jwt receivedValue(LiveActiveJwtDecoderImpl decoder, String token) { return decoder.decode(token).block(); }
    String sign(KeyPair key, String issuer, Instant expires) throws Exception {
        var claims = new JWTClaimsSet.Builder().issuer(issuer).subject("opaque:staff/subject").audience("resource")
                .expirationTime(Date.from(expires)).claim("azp", "workforce-client").jwtID("opaque-token-id").build();
        var signed = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        signed.sign(new RSASSASigner((RSAPrivateKey)key.getPrivate())); return signed.serialize();
    }
}

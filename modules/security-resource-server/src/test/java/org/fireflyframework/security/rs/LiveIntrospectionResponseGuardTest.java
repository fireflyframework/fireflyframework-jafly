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

import io.netty.buffer.PooledByteBufAllocator;
import org.fireflyframework.security.api.domain.BearerToken;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.*;
import org.springframework.http.*;
import org.springframework.security.oauth2.server.resource.introspection.SpringReactiveOpaqueTokenIntrospector;
import org.springframework.web.reactive.function.client.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class LiveIntrospectionResponseGuardTest {
    static final String VALID = "{\"active\":true,\"iss\":\"https://issuer.invalid\",\"sub\":\"opaque:staff/subject\",\"aud\":[\"resource\"],\"client_id\":\"workforce-client\",\"exp\":1793448120,\"jti\":\"opaque-token-id\"}";
    final NettyDataBufferFactory buffers = new NettyDataBufferFactory(PooledByteBufAllocator.DEFAULT);
    final LiveIntrospectionResponseGuardImpl guard = new LiveIntrospectionResponseGuardImpl();
    final ClientRequest request = ClientRequest.create(HttpMethod.POST, URI.create("https://issuer.invalid/introspect")).build();

    Mono<ClientResponse> guard(byte[] bytes, HttpStatus status, List<NettyDataBuffer> allocated) {
        NettyDataBuffer buffer = buffers.allocateBuffer(bytes.length).write(bytes);
        allocated.add(buffer);
        ClientResponse response = ClientResponse.create(status).header("Content-Type", "application/json")
                .body(Flux.just(buffer)).build();
        return guard.filter(request, unused -> Mono.just(response));
    }
    Mono<ClientResponse> guard(String body, List<NettyDataBuffer> allocated) {
        return guard(body.getBytes(StandardCharsets.UTF_8), HttpStatus.OK, allocated);
    }
    void released(List<NettyDataBuffer> allocated) {
        for (var buffer : allocated) assertEquals(0, buffer.getNativeBuffer().refCnt());
    }
    void refused(String body) {
        var allocated = new ArrayList<NettyDataBuffer>();
        StepVerifier.create(guard(body, allocated)).expectErrorSatisfies(error -> {
            assertNull(error.getCause());
            assertEquals("Live introspection response refused", error.getMessage());
        }).verify();
        released(allocated);
    }

    @Test void validBytesReachTheNativeCodecUnchangedAndOriginalPooledBufferIsReleased() {
        var allocated = new ArrayList<NettyDataBuffer>();
        String body = " \n" + VALID + " \n";
        assertEquals(body, guard(body, allocated).flatMap(response -> response.bodyToMono(String.class)).block());
        released(allocated);
    }
    @Test void duplicateIdentityAndActiveKeysRefuseBeforeNativeLastValueCoercion() {
        refused(VALID.replace("\"active\":true", "\"active\":false,\"active\":true"));
        refused(VALID.replace("\"sub\":", "\"sub\":\"PRIVATE_PROVIDER_BODY\",\"sub\":"));
    }
    @Test void exactRawTypesRefuseStringBooleansNumericClientsAndFractionalTimes() {
        for (String body : List.of(VALID.replace("true", "\"true\""),
                VALID.replace("\"workforce-client\"", "42"),
                VALID.replace("1793448120", "1793448120.5"),
                VALID.replace("1793448120", "\"1793448120\""),
                VALID.replace("[\"resource\"]", "[42]"),
                VALID.replace("[\"resource\"]", "[\"resource\",\"resource\"]"))) refused(body);
    }
    @Test void missingIdentityNullArraysEscapedSurrogatesAndTrailingDocumentsRefuse() {
        for (String body : List.of("[]", "null", "{}", VALID + "{}",
                VALID.replace("\"sub\":\"opaque:staff/subject\",", ""),
                VALID.replace("\"sub\":\"opaque:staff/subject\"", "\"sub\":null"),
                VALID.replace("opaque:staff/subject", "\\uD800"))) refused(body);
    }
    @Test void inactiveMayOmitBindingMetadataButTheGenuineNativeClientRefusesIt() {
        WebClient client = client(() -> ClientResponse.create(HttpStatus.OK).header("Content-Type", "application/json").body("{\"active\":false}").build());
        var nativeClient = new SpringReactiveOpaqueTokenIntrospector("https://issuer.invalid/introspect", client);
        StepVerifier.create(nativeClient.introspect("a.b.c")).expectError().verify();
    }
    @Test void successAndErrorBodiesAreBothBoundedAndReleasePooledBuffers() {
        for (HttpStatus status : List.of(HttpStatus.OK, HttpStatus.BAD_REQUEST, HttpStatus.INTERNAL_SERVER_ERROR)) {
            var allocated = new ArrayList<NettyDataBuffer>();
            StepVerifier.create(guard(new byte[16 * 1024 + 1], status, allocated)).expectError().verify();
            released(allocated);
        }
    }
    @Test void nonSuccessAndMalformedUtf8NeverExposeResponseOrTransportErrors() {
        var allocated = new ArrayList<NettyDataBuffer>();
        StepVerifier.create(guard("PRIVATE_BODY".getBytes(StandardCharsets.UTF_8), HttpStatus.BAD_REQUEST, allocated))
                .expectErrorSatisfies(error -> { assertNull(error.getCause()); assertFalse(error.toString().contains("PRIVATE")); }).verify();
        released(allocated);
        var invalid = new ArrayList<NettyDataBuffer>();
        StepVerifier.create(guard(new byte[]{(byte)0xc3, 0x28}, HttpStatus.OK, invalid)).expectError().verify();
        released(invalid);
        StepVerifier.create(guard.filter(request, ignored -> Mono.error(new IllegalStateException("PRIVATE_TRANSPORT"))))
                .expectErrorSatisfies(error -> { assertNull(error.getCause()); assertFalse(error.toString().contains("PRIVATE")); }).verify();
    }
    @Test void cancellationReleasesAccumulatedBodyAndCancelsUpstream() {
        NettyDataBuffer first = buffers.allocateBuffer().write("{\"active\":".getBytes(StandardCharsets.UTF_8));
        var cancelled = new AtomicBoolean();
        var received = new AtomicBoolean();
        var response = ClientResponse.create(HttpStatus.OK).header("Content-Type", "application/json")
                .body(Flux.concat(Flux.just(first), Flux.<DataBuffer>never()).doOnNext(buffer -> received.set(true)).doOnCancel(() -> cancelled.set(true))).build();
        StepVerifier.create(guard.filter(request, ignored -> Mono.just(response)), 0)
                .thenRequest(1).then(() -> assertTrue(received.get())).thenCancel().verify();
        assertTrue(cancelled.get());
        assertEquals(0, first.getNativeBuffer().refCnt());
    }
    @Test void bodyErrorReleasesPreviouslyReceivedPooledBufferAndDropsRawCause() {
        NettyDataBuffer first = buffers.allocateBuffer().write("{".getBytes(StandardCharsets.UTF_8));
        var response = ClientResponse.create(HttpStatus.OK).body(Flux.concat(Flux.just(first),
                Flux.error(new IllegalStateException("PRIVATE_STREAM_ERROR")))).build();
        StepVerifier.create(guard.filter(request, ignored -> Mono.just(response)))
                .expectErrorSatisfies(error -> { assertNull(error.getCause()); assertFalse(error.toString().contains("PRIVATE")); }).verify();
        assertEquals(0, first.getNativeBuffer().refCnt());
    }
    @Test void nativeSpringIntrospectorAndJackson3CodecRunForEverySubscriptionWithoutAttributeMerge() {
        AtomicInteger exchanges = new AtomicInteger();
        WebClient client = client(() -> {
            exchanges.incrementAndGet();
            return ClientResponse.create(HttpStatus.OK).header("Content-Type", "application/json")
                    .body(VALID.substring(0, VALID.length()-1) + ",\"scope\":\"admin\",\"PRIVATE_EXTRA\":\"PRIVATE_BODY\"}").build();
        });
        var p = new LiveActiveJwtDecoderTest().properties();
        var adapter = new SpringLiveTokenValidationAdapterImpl(new SpringReactiveOpaqueTokenIntrospector("https://issuer.invalid/introspect", client), p,
                Clock.fixed(LiveActiveJwtDecoderTest.NOW, ZoneOffset.UTC));
        for (int i = 0; i < 2; i++) {
            var principal = adapter.validateLive(BearerToken.of("a.b.c")).block();
            assertNotNull(principal);
            assertTrue(principal.authorities().isEmpty()); assertTrue(principal.scopes().isEmpty());
            assertFalse(principal.claims().containsKey("PRIVATE_EXTRA")); assertFalse(principal.claims().containsKey("scope"));
            assertEquals(Instant.ofEpochSecond(1793448120), principal.claims().get("exp"));
        }
        assertEquals(2, exchanges.get());
        adapter.close();
        StepVerifier.create(adapter.validateLive(BearerToken.of("a.b.c"))).expectError().verify();
        assertEquals(2, exchanges.get());
    }

    @Test void exactByteCapAllowsOnlyTheValidBoundedDocumentAndReleasesAllFragments() {
        var allocated = new ArrayList<NettyDataBuffer>();
        String document = VALID + " ".repeat(16 * 1024 - VALID.getBytes(StandardCharsets.UTF_8).length);
        assertEquals(document, guard(document, allocated).flatMap(response -> response.bodyToMono(String.class)).block());
        released(allocated);
    }
    @Test void fragmentedUtf8PreservesItsActualBytesAcrossPooledBuffers() {
        byte[] document = VALID.replace("opaque:staff/subject", "opaque:é/subject").getBytes(StandardCharsets.UTF_8);
        int split = 0;
        while ((document[split] & 0xff) != 0xc3) split++;
        split++;
        var first = buffers.allocateBuffer(split).write(Arrays.copyOfRange(document, 0, split));
        var last = buffers.allocateBuffer(document.length-split).write(Arrays.copyOfRange(document, split, document.length));
        var response = ClientResponse.create(HttpStatus.OK).header("Content-Type", "application/json").body(Flux.just(first,last)).build();
        String observed = guard.filter(request, unused -> Mono.just(response)).flatMap(value -> value.bodyToMono(String.class)).block();
        assertArrayEquals(document, observed.getBytes(StandardCharsets.UTF_8));
        assertEquals(0, first.getNativeBuffer().refCnt()); assertEquals(0, last.getNativeBuffer().refCnt());
    }
    @Test void adapterInactiveEmptyAndProviderFailureCannotProduceAPrincipal() {
        var p = new LiveActiveJwtDecoderTest().properties();
        var cases = List.<org.springframework.security.oauth2.server.resource.introspection.ReactiveOpaqueTokenIntrospector>of(
            token -> Mono.empty(), token -> Mono.error(new IllegalStateException("PRIVATE_PROVIDER_ERROR")),
            token -> Mono.just(new org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal(Map.of("active",false),List.of())));
        for (var nativePort : cases) {
            var adapter = new SpringLiveTokenValidationAdapterImpl(nativePort,p,Clock.fixed(LiveActiveJwtDecoderTest.NOW,ZoneOffset.UTC));
            try { StepVerifier.create(adapter.validateLive(BearerToken.of("a.b.c"))).expectErrorSatisfies(error -> {assertNull(error.getCause());assertFalse(error.toString().contains("PRIVATE"));}).verify(); }
            finally { adapter.close(); }
        }
    }
    WebClient client(java.util.function.Supplier<ClientResponse> response) {
        return WebClient.builder().codecs(codecs -> codecs.defaultCodecs().jacksonJsonDecoder(SpringLiveTokenValidationAdapterImpl.nativeJsonDecoder()))
                .exchangeFunction(unused -> Mono.fromSupplier(response)).filter(guard).build();
    }
}

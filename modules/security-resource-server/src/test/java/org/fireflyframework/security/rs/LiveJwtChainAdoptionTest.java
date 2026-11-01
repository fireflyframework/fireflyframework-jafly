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

import org.fireflyframework.security.api.domain.*;
import org.fireflyframework.security.core.authority.*;
import org.fireflyframework.security.core.context.PrincipalFactory;
import org.fireflyframework.security.spi.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpHeaders;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.server.*;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.assertj.core.api.Assertions.assertThat;

class LiveJwtChainAdoptionTest {
    @Test void existingDefaultRemainsLocalAndOldFactorySignatureWorks() {
        var p = new ResourceServerProperties();
        assertEquals(ResourceServerProperties.JwtAdmissionMode.LOCAL_JWT, p.getAdmissionMode());
        var calls = new AtomicInteger();
        var chain = oldChain(p, raw -> Mono.just(new LiveActiveJwtDecoderTest().jwt()));
        request(chain).get().uri("/private").header(HttpHeaders.AUTHORIZATION, "Bearer a.b.c")
                .exchange().expectStatus().isOk();
        assertEquals(0, calls.get());
    }
    @Test void defaultChainWrapsAnInjectedDecoderAndDeniesRemoteFailureBeforeBusinessHandler() {
        var fixture = new LiveActiveJwtDecoderTest(); var p = fixture.properties(); var effects = new AtomicInteger();
        TokenValidationPort port = new TokenValidationPort() {
            public Mono<SecurityPrincipal> validate(BearerToken token) { throw new AssertionError(); }
            public Mono<SecurityPrincipal> validateLive(BearerToken token) { return Mono.error(new IllegalStateException("PRIVATE_PROVIDER_RESPONSE")); }
        };
        var beans = new StaticListableBeanFactory(); beans.addBean("live", port);
        var chain = new ResourceServerAutoConfiguration().fireflySecurityWebFilterChain(ServerHttpSecurity.http(),
                raw -> Mono.just(currentJwt()), converter(), (principal, action, resource, context) -> Mono.just(Decision.permit()), p,
                beans.getBeanProvider(TokenValidationPort.class));
        request(chain, effects).get().uri("/private").header(HttpHeaders.AUTHORIZATION, "Bearer a.b.c")
                .exchange().expectStatus().isUnauthorized().expectBody().consumeWith(response -> {
                    assertFalse(new String(response.getResponseBody() == null ? new byte[0] : response.getResponseBody(), java.nio.charset.StandardCharsets.UTF_8).contains("PRIVATE"));
                });
        assertEquals(0, effects.get());
    }
    @Test void oldDirectFactoryCannotSilentlyDowngradeRequestedLiveMode() {
        assertThrows(IllegalArgumentException.class, () -> oldChain(new LiveActiveJwtDecoderTest().properties(), raw -> Mono.just(currentJwt())));
    }
    @Test void missingAndAmbiguousPortsFailDefaultChainConstruction() {
        var properties = new LiveActiveJwtDecoderTest().properties();
        var beans = new StaticListableBeanFactory();
        assertThrows(RuntimeException.class, () -> new ResourceServerAutoConfiguration().fireflySecurityWebFilterChain(
                ServerHttpSecurity.http(), raw -> Mono.just(currentJwt()), converter(), (p,a,r,c)->Mono.just(Decision.permit()), properties,
                beans.getBeanProvider(TokenValidationPort.class)));
        beans.addBean("first", (TokenValidationPort)token -> Mono.empty());
        beans.addBean("second", (TokenValidationPort)token -> Mono.empty());
        assertThrows(RuntimeException.class, () -> new ResourceServerAutoConfiguration().fireflySecurityWebFilterChain(
                ServerHttpSecurity.http(), raw -> Mono.just(currentJwt()), converter(), (p,a,r,c)->Mono.just(Decision.permit()), properties,
                beans.getBeanProvider(TokenValidationPort.class)));
    }
    @Test void customChainsMustExplicitlyUseThePublicDecorator() {
        var fixture = new LiveActiveJwtDecoderTest(); var properties = fixture.properties();
        TokenValidationPort legacyOnly = token -> Mono.just(fixture.live(Map.of()));
        var live = new LiveActiveJwtDecoder(raw -> Mono.just(currentJwt()), legacyOnly, properties);
        var chain = oldChain(new ResourceServerProperties(), live);
        request(chain).get().uri("/private").header(HttpHeaders.AUTHORIZATION, "Bearer a.b.c")
                .exchange().expectStatus().isUnauthorized();
        // A fully custom local-only chain remains local; a LIVE property elsewhere cannot mutate it.
        request(oldChain(new ResourceServerProperties(), raw -> Mono.just(currentJwt())))
                .get().uri("/private").header(HttpHeaders.AUTHORIZATION, "Bearer a.b.c").exchange().expectStatus().isOk();
    }
    @Test void propertyBindingUsesProjectExplicitLivePortAndDoesNotCreateAnOwnedNativeClient() {
        new ReactiveWebApplicationContextRunner().withConfiguration(AutoConfigurations.of(ResourceServerAutoConfiguration.class))
                .withUserConfiguration(ProjectPort.class).withPropertyValues(
                        "firefly.security.resource-server.admission-mode=LIVE", "firefly.security.resource-server.issuer=https://issuer.invalid",
                        "firefly.security.resource-server.audiences[0]=resource",
                        "firefly.security.resource-server.live-state.allowed-clients[0]=workforce-client",
                        "firefly.security.resource-server.live-state.token-client-claim=azp")
                .run(context -> { assertThat(context).hasNotFailed().hasSingleBean(TokenValidationPort.class);
                    assertThat(context).doesNotHaveBean(SpringLiveTokenValidationAdapter.class); });
    }
    @Test void missingNativeClientSettingsFailBeforeTransportAllocation() {
        assertThrows(IllegalArgumentException.class, () -> SpringLiveTokenValidationAdapter.create(new LiveActiveJwtDecoderTest().properties()));
    }
    static class ProjectPort {
        @Bean ReactiveJwtDecoder customDecoder() { return raw -> Mono.just(currentJwt()); }
        @Bean TokenValidationPort explicitPort() { return token -> Mono.empty(); }
    }
    static Jwt currentJwt() {
        var now = java.time.Instant.now();
        return Jwt.withTokenValue("a.b.c").header("alg","RS256").issuer(LiveActiveJwtDecoderTest.ISSUER)
                .subject("opaque:staff/subject").audience(List.of("resource")).expiresAt(now.plusSeconds(120)).claim("azp","workforce-client").build();
    }
    static org.springframework.core.convert.converter.Converter<Jwt, Mono<org.springframework.security.authentication.AbstractAuthenticationToken>> converter() {
        return new ResourceServerAutoConfiguration().fireflyJwtAuthenticationConverter(new PrincipalFactory(
                new ConfigurableAuthorityMapper(new AuthorityMappingProperties(List.of(), List.of(), ""))));
    }
    SecurityWebFilterChain oldChain(ResourceServerProperties p, ReactiveJwtDecoder decoder) {
        return new ResourceServerAutoConfiguration().fireflySecurityWebFilterChain(ServerHttpSecurity.http(), decoder,
                converter(), (principal,action,resource,context)->Mono.just(Decision.permit()), p);
    }
    WebTestClient request(SecurityWebFilterChain chain) { return request(chain, new AtomicInteger()); }
    WebTestClient request(SecurityWebFilterChain chain, AtomicInteger effects) {
        return WebTestClient.bindToWebHandler(exchange -> { effects.incrementAndGet(); exchange.getResponse().setStatusCode(org.springframework.http.HttpStatus.OK); return exchange.getResponse().setComplete(); })
                .webFilter(new WebFilterChainProxy(chain)).build();
    }
}

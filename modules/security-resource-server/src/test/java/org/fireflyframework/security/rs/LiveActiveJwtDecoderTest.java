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
import static org.junit.jupiter.api.Assertions.*;
import java.time.*;import java.util.*;import java.util.concurrent.atomic.*;
import org.fireflyframework.security.api.domain.*;import org.fireflyframework.security.spi.TokenValidationPort;
import org.junit.jupiter.api.Test;import org.springframework.security.oauth2.jwt.*;import reactor.core.publisher.Mono;import reactor.test.StepVerifier;
class LiveActiveJwtDecoderTest {
 static final Instant NOW=Instant.parse("2026-10-31T12:00:00Z");static final String ISSUER="https://issuer.invalid";
 final AtomicLong nanos=new AtomicLong();final AtomicInteger liveCalls=new AtomicInteger();
 ResourceServerProperties properties(){var p=new ResourceServerProperties();p.setAdmissionMode(ResourceServerProperties.JwtAdmissionMode.LIVE);p.setIssuer(ISSUER);p.setAudiences(List.of("resource"));p.getLiveState().setAllowedClients(List.of("workforce-client"));p.getLiveState().setTokenClientClaim("azp");return p;}
 Jwt jwt(){return Jwt.withTokenValue("a.b.c").header("alg","RS256").issuer(ISSUER).subject("opaque:staff/subject").audience(List.of("resource")).issuedAt(NOW.minusSeconds(1)).expiresAt(NOW.plusSeconds(120)).claim("azp","workforce-client").claim("jti","opaque-token-id").build();}
 SecurityPrincipal live(Map<String,Object> changed){var c=new HashMap<String,Object>(Map.of("iss",ISSUER,"sub","opaque:staff/subject","aud",List.of("resource"),"exp",NOW.plusSeconds(120),"client_id","workforce-client","jti","opaque-token-id"));c.putAll(changed);return SecurityPrincipal.builder().issuer((String)c.get("iss")).subject((String)c.get("sub")).claims(c).authorities(Set.of()).scopes(Set.of()).attributes(Map.of()).build();}
 TokenValidationPort port(java.util.function.Function<BearerToken,Mono<SecurityPrincipal>> answer){return new TokenValidationPort(){public Mono<SecurityPrincipal> validate(BearerToken token){throw new AssertionError("Legacy validation is not LIVE");}public Mono<SecurityPrincipal> validateLive(BearerToken token){liveCalls.incrementAndGet();assertEquals("a.b.c",token.value());return answer.apply(token);}};}
 LiveActiveJwtDecoder decoder(ReactiveJwtDecoder local,TokenValidationPort port){return new LiveActiveJwtDecoder(local,port,properties(),Clock.fixed(NOW,ZoneOffset.UTC),nanos::get);}
 @Test void returnsTheExactLocallyVerifiedJwtWithoutClaimOrRoleMerge(){var verified=jwt();var d=decoder(token->Mono.just(verified),port(token->Mono.just(live(Map.of()))));assertSame(verified,d.decode("a.b.c").block());assertFalse(verified.getClaims().containsKey("client_id"));assertEquals(1,liveCalls.get());}
 @Test void localFailureNeverInvokesTheLiveProvider(){var d=decoder(token->Mono.error(new BadJwtException("PRIVATE_LOCAL_FAILURE")),port(token->Mono.just(live(Map.of()))));StepVerifier.create(d.decode("a.b.c")).expectErrorSatisfies(e->{assertInstanceOf(BadJwtException.class,e);assertNull(e.getCause());assertFalse(e.toString().contains("PRIVATE"));}).verify();assertEquals(0,liveCalls.get());}
 @Test void emptyLocalOrLivePublisherCannotAuthenticate(){StepVerifier.create(decoder(token->Mono.empty(),port(token->Mono.just(live(Map.of())))).decode("a.b.c")).expectError(BadJwtException.class).verify();assertEquals(0,liveCalls.get());StepVerifier.create(decoder(token->Mono.just(jwt()),port(token->Mono.empty())).decode("a.b.c")).expectError(BadJwtException.class).verify();}
 @Test void everySubscriptionQueriesLiveAndCannotReuseAnEarlierPermit(){var calls=new AtomicInteger();var d=decoder(token->Mono.just(jwt()),port(token->calls.getAndIncrement()==0?Mono.just(live(Map.of())):Mono.error(new IllegalStateException("PRIVATE_PROVIDER_BODY"))));assertNotNull(d.decode("a.b.c").block());StepVerifier.create(d.decode("a.b.c")).expectError(BadJwtException.class).verify();assertEquals(2,liveCalls.get());}
 @Test void boundIssuerSubjectClientAudienceExpiryAndTokenIdMustMatch(){for(var changed:List.<Map<String,Object>>of(Map.of("iss","https://other.invalid"),Map.of("sub","other-subject"),Map.of("client_id","other-client"),Map.of("aud",List.of("other-resource")),Map.of("exp",NOW.plusSeconds(121)),Map.of("jti","other-token"))){StepVerifier.create(decoder(token->Mono.just(jwt()),port(token->Mono.just(live(changed)))).decode("a.b.c")).expectError(BadJwtException.class).verify();}}
 @Test void originalMonotonicWindowCannotBeResetByLateLiveSuccess(){var d=decoder(token->Mono.just(jwt()),port(token->Mono.fromSupplier(()->{nanos.set(Duration.ofSeconds(6).toNanos());return live(Map.of());})));StepVerifier.create(d.decode("a.b.c")).expectError(BadJwtException.class).verify();}
 @Test void unsupportedLegacyPortCannotFallBackToTheLocalJwt(){TokenValidationPort legacy=token->Mono.just(live(Map.of()));StepVerifier.create(decoder(token->Mono.just(jwt()),legacy).decode("a.b.c")).expectError(BadJwtException.class).verify();}
 @Test void missingOrOversizeLiveConfigurationRefuses(){var p=properties();p.getLiveState().setTimeout(Duration.ofSeconds(6));assertThrows(IllegalArgumentException.class,()->new LiveActiveJwtDecoder(token->Mono.just(jwt()),port(token->Mono.just(live(Map.of()))),p));}
 @Test void propertiesAreSnapshottedInsteadOfChangingAnInflightAdmission(){var p=properties();var verified=jwt();var d=new LiveActiveJwtDecoder(token->Mono.just(verified),port(token->Mono.just(live(Map.of()))),p,Clock.fixed(NOW,ZoneOffset.UTC),nanos::get);p.getLiveState().setAllowedClients(List.of("other-client"));p.setIssuer("https://other.invalid");assertSame(verified,d.decode("a.b.c").block());}
 @Test void cancellationCannotPublishALateAuthentication(){var sink=reactor.core.publisher.Sinks.<SecurityPrincipal>one();var subscribed=new AtomicBoolean();var cancelled=new AtomicBoolean();var authenticated=new AtomicInteger();var d=decoder(token->Mono.just(jwt()),port(token->sink.asMono().doOnSubscribe(ignored->subscribed.set(true)).doOnCancel(()->cancelled.set(true))));StepVerifier.create(d.decode("a.b.c").doOnNext(ignored->authenticated.incrementAndGet()),0).thenRequest(1).then(()->{assertTrue(subscribed.get());assertEquals(1,liveCalls.get());}).thenCancel().verify();assertTrue(cancelled.get());sink.tryEmitValue(live(Map.of()));assertEquals(0,authenticated.get());}

 @Test void originalVerifiedExpirationStillBoundsALateLiveReply(){
  var clock=new AtomicReference<>(NOW);Clock advancing=new Clock(){public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return this;}public Instant instant(){return clock.get();}};
  var d=new LiveActiveJwtDecoder(token->Mono.just(jwt()),port(token->Mono.fromSupplier(()->{clock.set(NOW.plusSeconds(121));return live(Map.of());})),properties(),advancing,nanos::get);
  StepVerifier.create(d.decode("a.b.c")).expectError(BadJwtException.class).verify();
 }
 @Test void wrongClaimAliasMissingIssuerAndMalformedOpaqueIdentityCannotBeInferred(){
  for(var change:List.<java.util.function.Consumer<ResourceServerProperties>>of(p->p.getLiveState().setTokenClientClaim("client_id"),p->p.setIssuer(null),p->p.getLiveState().setAllowedClients(List.of()))){var p=properties();change.accept(p);try{var d=new LiveActiveJwtDecoder(token->Mono.just(jwt()),port(token->Mono.just(live(Map.of()))),p,Clock.fixed(NOW,ZoneOffset.UTC),nanos::get);StepVerifier.create(d.decode("a.b.c")).expectError(BadJwtException.class).verify();}catch(IllegalArgumentException expected){assertNotNull(expected.getMessage());}}
  StepVerifier.create(decoder(token->Mono.just(jwt()),port(token->Mono.just(live(Map.of("sub","opaque\nsubject"))))).decode("a.b.c")).expectError(BadJwtException.class).verify();
 }
 @Test void nonCompletingLivePublisherUsesTheOriginalTimeoutAndCancels(){
  var fixture=properties();fixture.getLiveState().setTimeout(Duration.ofSeconds(1));var cancelled=new AtomicBoolean();
  StepVerifier.withVirtualTime(()->new LiveActiveJwtDecoder(token->Mono.just(jwt()),port(token->Mono.<SecurityPrincipal>never().doOnCancel(()->cancelled.set(true))),fixture,Clock.fixed(NOW,ZoneOffset.UTC),nanos::get).decode("a.b.c"))
   .thenAwait(Duration.ofSeconds(2)).expectError(BadJwtException.class).verify();assertTrue(cancelled.get());
 }

 @Test void timeSpentInLocalVerificationDoesNotCreateANewLiveWindow(){
  StepVerifier.withVirtualTime(()->decoder(token->Mono.delay(Duration.ofSeconds(3)).map(ignored->jwt()),port(token->Mono.delay(Duration.ofSeconds(3)).map(ignored->live(Map.of())))).decode("a.b.c"))
   .thenAwait(Duration.ofSeconds(5)).expectError(BadJwtException.class).verify();
 }
}

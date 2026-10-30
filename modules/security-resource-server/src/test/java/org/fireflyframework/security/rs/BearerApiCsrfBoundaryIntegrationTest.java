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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import org.fireflyframework.security.api.domain.Decision;
import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.fireflyframework.security.core.policy.PolicyRule;
import org.fireflyframework.security.spi.KeyManagementPort;
import org.fireflyframework.security.webflux.authentication.FireflyAuthenticationToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.server.context.WebSessionServerSecurityContextRepository;
import org.springframework.security.web.server.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.interfaces.RSAPrivateKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@SpringBootTest(
        classes = BearerApiCsrfBoundaryIntegrationTest.Application.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "server.address=127.0.0.1",
            "firefly.security.resource-server.strict-policy=true",
            "firefly.security.resource-server.permit-matchers=/fixture/**",
            "spring.main.banner-mode=off"
        })
class BearerApiCsrfBoundaryIntegrationTest {
    private static final AtomicInteger EFFECTS = new AtomicInteger();
    private static final AtomicInteger PUBLIC_EFFECTS = new AtomicInteger();
    private static final AtomicBoolean DENY = new AtomicBoolean();
    private static final AtomicReference<String> POLICY_SUBJECT = new AtomicReference<>();
    @LocalServerPort int port;
    @Autowired KeyManagementPort keys;

    @BeforeEach
    void reset() {
        EFFECTS.set(0);
        PUBLIC_EFFECTS.set(0);
        DENY.set(false);
        POLICY_SUBJECT.set(null);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class Application {
        @Bean
        PolicyRule currentPolicy() {
            return (principal, action, resource, context) -> {
                POLICY_SUBJECT.set(principal.subject());
                return Mono.just(
                        DENY.get()
                                ? Decision.deny("Synthetic policy withdrawn")
                                : Decision.permit());
            };
        }

        @Bean
        Controller controller() {
            return new Controller();
        }
    }

    @RestController
    static class Controller {
        @GetMapping("/fixture/session")
        Mono<String> seedAuthenticatedSession(ServerWebExchange exchange) {
            var principal = SecurityPrincipal.builder().subject("synthetic-session-user").build();
            return new WebSessionServerSecurityContextRepository()
                    .save(
                            exchange,
                            new SecurityContextImpl(new FireflyAuthenticationToken(principal)))
                    .thenReturn("Test-owned authenticated session");
        }

        @GetMapping("/fixture/csrf")
        Mono<String> csrf(ServerWebExchange exchange) {
            Mono<CsrfToken> token = exchange.getAttribute(CsrfToken.class.getName());
            assertNotNull(token, "Native CSRF filter must expose the token");
            return token.map(CsrfToken::getToken);
        }

        @GetMapping("/fixture/header-session")
        Mono<String> headerSession(ServerWebExchange exchange) {
            return exchange.getSession()
                    .map(
                            session -> {
                                session.start();
                                return Boolean.toString(
                                        session.getAttributes()
                                                .containsKey("SPRING_SECURITY_CONTEXT"));
                            });
        }

        @GetMapping("/api/read")
        String read() {
            return "Synthetic protected read";
        }

        @RequestMapping(
                value = "/api/write",
                method = {
                    RequestMethod.POST,
                    RequestMethod.PUT,
                    RequestMethod.PATCH,
                    RequestMethod.DELETE
                })
        String write() {
            EFFECTS.incrementAndGet();
            return "Synthetic protected effect";
        }

        @PostMapping("/fixture/browser-write")
        String publicWrite() {
            PUBLIC_EFFECTS.incrementAndGet();
            return "Synthetic public effect";
        }
    }

    @Test
    void signedBearerUnsafeMethodsWorkWithoutCsrfTokens() throws Exception {
        String token = token();
        for (String method : List.of("POST", "PUT", "PATCH", "DELETE")) {
            assertEquals(
                    200,
                    send(method, "/api/write", "", "Authorization", "Bearer " + token)
                            .statusCode());
        }
        assertEquals(4, EFFECTS.get());
    }

    @Test
    void sessionCookieCannotAuthenticateSafeProtectedRead() throws Exception {
        assertEquals(
                401, send("GET", "/api/read", "", "Cookie", authenticatedSession()).statusCode());
        assertEquals(0, EFFECTS.get());
    }

    @Test
    void sessionAndValidCsrfCannotAuthenticateUnsafeWrite() throws Exception {
        var csrf = csrf(authenticatedSession());
        assertEquals(
                401,
                send(
                                "POST",
                                "/api/write",
                                "",
                                "Cookie",
                                csrf.cookie(),
                                "X-CSRF-TOKEN",
                                csrf.token())
                        .statusCode());
        assertEquals(0, EFFECTS.get());
    }

    @Test
    void unsafeNonBearerRequestsRequireNativeCsrf() throws Exception {
        for (String method : List.of("POST", "PUT", "PATCH", "DELETE")) {
            assertEquals(403, send(method, "/api/write", "").statusCode());
        }
        assertEquals(0, EFFECTS.get());
    }

    @Test
    void allowlistedBrowserUnsafeEndpointStillRequiresCsrf() throws Exception {
        assertEquals(403, send("POST", "/fixture/browser-write", "").statusCode());
        assertEquals(0, PUBLIC_EFFECTS.get());
        var csrf = csrf(null);
        assertEquals(
                200,
                send(
                                "POST",
                                "/fixture/browser-write",
                                "",
                                "Cookie",
                                csrf.cookie(),
                                "X-CSRF-TOKEN",
                                csrf.token())
                        .statusCode());
        assertEquals(1, PUBLIC_EFFECTS.get());
    }

    @Test
    void otherTransportsCannotAuthenticateEvenWithValidCsrf() throws Exception {
        var csrf = csrf(null);
        String basic =
                Base64.getEncoder()
                        .encodeToString(
                                (UUID.randomUUID() + ":" + UUID.randomUUID())
                                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertEquals(
                401,
                send(
                                "POST",
                                "/api/write",
                                "",
                                "Cookie",
                                csrf.cookie(),
                                "X-CSRF-TOKEN",
                                csrf.token(),
                                "Authorization",
                                "Basic " + basic)
                        .statusCode());
        String token = token();
        assertEquals(
                401,
                send(
                                "POST",
                                "/api/write?access_token=" + token,
                                "",
                                "Cookie",
                                csrf.cookie(),
                                "X-CSRF-TOKEN",
                                csrf.token())
                        .statusCode());
        assertEquals(
                401,
                send(
                                "POST",
                                "/api/write",
                                "access_token=" + token,
                                "Cookie",
                                csrf.cookie(),
                                "X-CSRF-TOKEN",
                                csrf.token(),
                                "Content-Type",
                                "application/x-www-form-urlencoded")
                        .statusCode());
        assertEquals(
                401,
                send(
                                "POST",
                                "/api/write",
                                "",
                                "Cookie",
                                csrf.cookie() + "; access_token=" + token,
                                "X-CSRF-TOKEN",
                                csrf.token())
                        .statusCode());
        assertEquals(0, EFFECTS.get());
    }

    @Test
    void headerBearerAuthenticationIsNotPersistedIntoSession() throws Exception {
        var response =
                send("GET", "/fixture/header-session", "", "Authorization", "Bearer " + token());
        assertEquals(200, response.statusCode());
        assertEquals("false", response.body());
        assertEquals(
                401,
                send("GET", "/api/read", "", "Cookie", sessionCookie(response, null)).statusCode());
    }

    @Test
    void validBearerTakesAuthorityFromTokenDespiteConflictingSession() throws Exception {
        assertEquals(
                200,
                send(
                                "POST",
                                "/api/write",
                                "",
                                "Cookie",
                                authenticatedSession(),
                                "Authorization",
                                "Bearer " + token())
                        .statusCode());
        assertEquals("synthetic-bearer-user", POLICY_SUBJECT.get());
        assertEquals(1, EFFECTS.get());
    }

    @Test
    void currentPolicyDenialStillBlocksValidBearer() throws Exception {
        DENY.set(true);
        assertEquals(
                403,
                send("POST", "/api/write", "", "Authorization", "Bearer " + token()).statusCode());
        assertEquals(0, EFFECTS.get());
    }

    @Test
    void invalidBearerCannotFallBackToAuthenticatedSession() throws Exception {
        assertEquals(
                401,
                send(
                                "POST",
                                "/api/write",
                                "",
                                "Cookie",
                                authenticatedSession(),
                                "Authorization",
                                "Bearer invalid-synthetic-token")
                        .statusCode());
        assertEquals(0, EFFECTS.get());
    }

    private record CsrfCredential(String cookie, String token) {}

    private CsrfCredential csrf(String cookie) throws Exception {
        var response =
                cookie == null
                        ? send("GET", "/fixture/csrf", "")
                        : send("GET", "/fixture/csrf", "", "Cookie", cookie);
        assertEquals(200, response.statusCode());
        return new CsrfCredential(sessionCookie(response, cookie), response.body());
    }

    private String authenticatedSession() throws Exception {
        var response = send("GET", "/fixture/session", "");
        assertEquals(200, response.statusCode());
        return sessionCookie(response, null);
    }

    private String sessionCookie(HttpResponse<?> response, String fallback) {
        String cookie =
                response.headers().allValues("Set-Cookie").stream()
                        .filter(value -> value.startsWith("SESSION="))
                        .map(value -> value.split(";", 2)[0])
                        .findFirst()
                        .orElse(fallback);
        assertNotNull(cookie, "Test-owned native session cookie must exist");
        return cookie;
    }

    private String token() throws Exception {
        var key = keys.activeSigningKey().block();
        var jwt =
                new SignedJWT(
                        new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.kid()).build(),
                        new JWTClaimsSet.Builder()
                                .subject("synthetic-bearer-user")
                                .expirationTime(Date.from(Instant.now().plusSeconds(120)))
                                .build());
        jwt.sign(new RSASSASigner((RSAPrivateKey) key.privateKey()));
        return jwt.serialize();
    }

    private HttpResponse<String> send(String method, String path, String body, String... headers)
            throws Exception {
        var builder =
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                        .timeout(Duration.ofSeconds(8))
                        .method(
                                method,
                                body.isEmpty()
                                        ? HttpRequest.BodyPublishers.noBody()
                                        : HttpRequest.BodyPublishers.ofString(body));
        for (int index = 0; index < headers.length; index += 2)
            builder.header(headers[index], headers[index + 1]);
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build()
                .send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}

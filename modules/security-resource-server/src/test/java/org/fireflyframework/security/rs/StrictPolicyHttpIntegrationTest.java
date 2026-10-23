package org.fireflyframework.security.rs;

import static org.junit.jupiter.api.Assertions.*;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.*;
import java.net.URI;
import java.net.http.*;
import java.security.interfaces.RSAPrivateKey;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.fireflyframework.security.api.domain.*;
import org.fireflyframework.security.core.policy.PolicyRule;
import org.fireflyframework.security.spi.KeyManagementPort;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@SpringBootTest(classes=StrictPolicyHttpIntegrationTest.Application.class,
        webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties={"server.address=127.0.0.1","firefly.security.resource-server.strict-policy=true"})
class StrictPolicyHttpIntegrationTest {
    static final AtomicInteger EFFECTS=new AtomicInteger();
    @LocalServerPort int port;
    @Autowired KeyManagementPort keys;
    @BeforeEach void reset(){EFFECTS.set(0);}

    @SpringBootConfiguration @EnableAutoConfiguration
    static class Application {
        @Bean PolicyRule currentPolicy() {
            return (p,a,r,c)->switch(r) {
                case "/api/masked" -> Mono.just(Decision.permit(List.of(Obligation.of("mask",Map.of("field","synthetic-field")))));
                case "/api/never" -> Mono.never();
                default -> Mono.just(Decision.permit());
            };
        }
        @RestController static class Controller {
            @GetMapping({"/api/plain","/api/masked","/api/never"}) String get(){EFFECTS.incrementAndGet();return "synthetic-result";}
        }
    }
    @Test void aPlainExplicitPermitStillReachesTheController() throws Exception {
        assertEquals(200,send("/api/plain",true).statusCode());assertEquals(1,EFFECTS.get());
    }
    @Test void missingTokenNeverReachesCurrentPolicyProtectedData() throws Exception {
        assertEquals(401,send("/api/plain",false).statusCode());assertEquals(0,EFFECTS.get());
    }
    @Test void anUnsupportedObligationCannotDisappearAtTheUrlEnforcementBoundary() throws Exception {
        var response=send("/api/masked",true);
        assertEquals(403,response.statusCode(),response.body());assertEquals(0,EFFECTS.get());
    }
    @Test void aNonCompletingPolicyIsBoundedAndCannotReachTheController() {
        var response=assertTimeoutPreemptively(Duration.ofSeconds(8),()->send("/api/never",true));
        assertEquals(403,response.statusCode(),response.body());assertEquals(0,EFFECTS.get());
    }
    HttpResponse<String> send(String path,boolean authenticated) throws Exception {
        var builder=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).GET();
        if(authenticated){
            var key=keys.activeSigningKey().block();
            var claims=new JWTClaimsSet.Builder().subject("synthetic-staff").expirationTime(Date.from(Instant.now().plusSeconds(120))).build();
            var jwt=new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.kid()).build(),claims);
            jwt.sign(new RSASSASigner((RSAPrivateKey)key.privateKey()));builder.header("Authorization","Bearer "+jwt.serialize());
        }
        return HttpClient.newHttpClient().send(builder.build(),HttpResponse.BodyHandlers.ofString());
    }
}

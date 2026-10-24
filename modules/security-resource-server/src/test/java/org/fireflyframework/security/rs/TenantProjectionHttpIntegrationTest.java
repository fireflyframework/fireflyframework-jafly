package org.fireflyframework.security.rs;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.*;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import java.net.URI;
import java.net.http.*;
import java.security.interfaces.RSAPrivateKey;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes=TenantProjectionHttpIntegrationTest.Application.class,
        webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties={"server.address=127.0.0.1","firefly.security.resource-server.strict-policy=true",
                "firefly.security.resource-server.tenant-claim-paths[0]=tenant_ids",
                "firefly.security.resource-server.tenant-required=true",
                "firefly.security.resource-server.issuer=https://issuer.test",
                "firefly.security.resource-server.audiences=tenant-projection-tests"})
class TenantProjectionHttpIntegrationTest {
    static final AtomicBoolean CURRENT_PERMISSION=new AtomicBoolean(true);
    static final AtomicInteger EFFECTS=new AtomicInteger();
    @Autowired KeyManagementPort keys;
    @LocalServerPort int port;
    @BeforeEach void reset(){CURRENT_PERMISSION.set(true);EFFECTS.set(0);}
    @SpringBootConfiguration @EnableAutoConfiguration
    static class Application {
        @Bean PolicyRule currentTenantPolicy(){return (principal,action,resource,context)->Mono.just(CURRENT_PERMISSION.get() && "tenant-a".equals(principal.tenantId())?Decision.permit():Decision.deny("Current tenant permission required"));}
        @RestController static class Controller {
            @GetMapping("/tenant-projection") String read(@AuthenticationPrincipal SecurityPrincipal principal){EFFECTS.incrementAndGet();return principal.tenantId()+":"+principal.claims().get("principal_kind");}
        }
    }
    @Test void signatureValidatedTenantReachesActualPrincipalAndCurrentPolicy() throws Exception {
        var response=send(token(List.of("tenant-a"),"tenant-projection-tests"));assertEquals(200,response.statusCode(),response.body());assertEquals("tenant-a:MACHINE",response.body());assertEquals(1,EFFECTS.get());
    }
    @Test void requiredMissingAmbiguousAndWrongAudienceTokensDoNotReachController() throws Exception {
        assertEquals(401,send(token(null,"tenant-projection-tests")).statusCode());assertEquals(401,send(token(List.of("tenant-a","tenant-b"),"tenant-projection-tests")).statusCode());assertEquals(401,send(token(List.of("tenant-a"),"wrong-audience")).statusCode());assertEquals(0,EFFECTS.get());
    }
    @Test void resolvedTenantDoesNotBypassCurrentPermissionOrOtherTenantPolicy() throws Exception {
        var token=token(List.of("tenant-a"),"tenant-projection-tests");CURRENT_PERMISSION.set(false);assertEquals(403,send(token).statusCode());CURRENT_PERMISSION.set(true);assertEquals(403,send(token(List.of("tenant-b"),"tenant-projection-tests")).statusCode());assertEquals(0,EFFECTS.get());assertEquals(200,send(token).statusCode());assertEquals(1,EFFECTS.get());
    }
    String token(Object tenant,String audience) throws Exception {
        var claims=new JWTClaimsSet.Builder().issuer("https://issuer.test").subject("signed-machine").audience(audience).expirationTime(Date.from(Instant.now().plusSeconds(120))).claim("principal_kind","MACHINE").claim("client_id","integration-client");if(tenant!=null)claims.claim("tenant_ids",tenant);var key=keys.activeSigningKey().block();assertNotNull(key);var token=new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.kid()).build(),claims.build());token.sign(new RSASSASigner((RSAPrivateKey)key.privateKey()));return token.serialize();
    }
    HttpResponse<String> send(String token) throws Exception {return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/tenant-projection")).header("Authorization","Bearer "+token).GET().build(),HttpResponse.BodyHandlers.ofString());}
}

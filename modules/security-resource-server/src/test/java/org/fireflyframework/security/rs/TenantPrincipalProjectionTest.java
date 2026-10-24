package org.fireflyframework.security.rs;

import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.fireflyframework.security.core.authority.*;
import org.fireflyframework.security.core.context.PrincipalFactory;
import org.fireflyframework.security.core.tenant.ConfigurableTenantResolver;
import org.fireflyframework.security.webflux.authentication.FireflyAuthenticationToken;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;
import reactor.core.publisher.Mono;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class TenantPrincipalProjectionTest {
    private final PrincipalFactory factory=new PrincipalFactory(new ConfigurableAuthorityMapper(new AuthorityMappingProperties(List.of("roles"),List.of("scope"),"")));
    private Jwt jwt(Map<String,Object> claims){return Jwt.withTokenValue("prevalidated-unit-value").header("alg","RS256").subject("verified-subject").issuer("https://issuer.test").issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(120)).claims(value->value.putAll(claims)).build();}
    private SecurityPrincipal principal(JwtToFireflyPrincipalConverter converter,Map<String,Object> claims){return ((FireflyAuthenticationToken)converter.convert(jwt(claims)).block()).securityPrincipal();}
    @Test void legacyConstructorRetainsUnconfiguredTenantAndFactorySignature() {
        var props=new ResourceServerProperties();assertThat(props.getTenantClaimPaths()).isEmpty();assertThat(props.isTenantRequired()).isFalse();
        assertThat(principal(new JwtToFireflyPrincipalConverter(factory),Map.of("tenant_id","a")).tenantId()).isNull();assertThat(new ResourceServerAutoConfiguration().fireflyJwtAuthenticationConverter(factory)).isNotNull();
    }
    @Test void explicitProjectionPreservesEveryOtherPrincipalField() {
        var claims=Map.<String,Object>of("org",Map.of("tenant",List.of("a")),"roles",List.of("operator"),"scope","read write","auth_time",Instant.now().minusSeconds(30).getEpochSecond(),"acr","reviewed","amr",List.of("pwd"),"principal_kind","MACHINE","client_id","real-client");
        var converter=new JwtToFireflyPrincipalConverter(factory,new ConfigurableTenantResolver(List.of("org.tenant")),true);var token=jwt(claims);var projected=((FireflyAuthenticationToken)converter.convert(token).block()).securityPrincipal();var original=factory.fromClaims(token.getSubject(),token.getIssuer().toString(),token.getClaims());assertThat(projected).isEqualTo(original.toBuilder().tenantId("a").build());assertThat(projected.claims()).doesNotContainKey("staff_actor_id");
    }
    @Test void optionalMissingOrAmbiguousProjectionClearsTenantWithoutCrossRequestState() {
        var converter=new JwtToFireflyPrincipalConverter(factory,new ConfigurableTenantResolver(List.of("tenant_ids")),false);
        assertThat(principal(converter,Map.of("tenant_ids",List.of("first"))).tenantId()).isEqualTo("first");assertThat(principal(converter,Map.of()).tenantId()).isNull();assertThat(principal(converter,Map.of("tenant_ids",List.of("a","b"))).tenantId()).isNull();assertThat(principal(converter,Map.of("tenant_ids",List.of("last"))).tenantId()).isEqualTo("last");
    }
    @Test void requiredMissingOrAmbiguousProjectionDeniesWithoutClaimDisclosure() {
        var converter=new JwtToFireflyPrincipalConverter(factory,new ConfigurableTenantResolver(List.of("tenant_ids")),true);
        for(var claims:List.of(Map.<String,Object>of(),Map.<String,Object>of("tenant_ids",List.of("private-tenant-a","private-tenant-b"))))assertThatThrownBy(()->principal(converter,claims)).isInstanceOf(OAuth2AuthenticationException.class).hasMessageNotContaining("private-tenant");
    }
    @Test void resolverErrorsDenyEvenOptionalProjectionWithoutCauseDisclosure() {
        var converter=new JwtToFireflyPrincipalConverter(factory,p->Mono.error(new IllegalStateException("private-claim-value")),false);
        assertThatThrownBy(()->principal(converter,Map.of())).isInstanceOf(OAuth2AuthenticationException.class).hasMessageNotContaining("private-claim-value").hasNoCause();
    }
}

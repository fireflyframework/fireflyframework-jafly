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

    @Test void configuredProjectionPreservesOpaqueScalarAndAllOtherPrincipalFields() {
        var converter=new JwtToFireflyPrincipalConverter(factory,new ConfigurableTenantResolver(List.of("tenant_id")),true);
        for(String tenant:List.of("opaque:tenant/α β","opaque:tenant/a,b"," tenant-a ")) {
            var token=jwt(Map.of("tenant_id",tenant,"roles","operator auditor","scope","read,write"));
            var projected=((FireflyAuthenticationToken)converter.convert(token).block()).securityPrincipal();
            var expected=factory.fromClaims(token.getSubject(),token.getIssuer().toString(),token.getClaims()).toBuilder().tenantId(tenant).build();
            assertThat(projected).isEqualTo(expected);assertThat(projected.claims().get("tenant_id")).isEqualTo(tenant);
            assertThat(projected.authorities()).containsExactlyInAnyOrder("operator","auditor");
            assertThat(projected.scopes()).containsExactly("read,write").doesNotContain("read","write");
        }
    }
    @Test void requiredAndOptionalProjectionAgreeOnAnExactSingleCollectionElement() {
        String tenant="opaque tenant,α";
        for(boolean required:List.of(true,false)) {
            var converter=new JwtToFireflyPrincipalConverter(factory,new ConfigurableTenantResolver(List.of("tenant_id")),required);
            assertThat(principal(converter,Map.of("tenant_id",List.of(tenant))).tenantId()).isEqualTo(tenant);
            assertThat(principal(converter,Map.of("tenant_id",tenant)).tenantId()).isEqualTo(tenant);
        }
    }
    @Test void distinctCollectionTenantsStillRejectRequiredAndClearOptionalProjection() {
        var claims=Map.<String,Object>of("tenant_id",List.of("opaque tenant","other,tenant"));
        var required=new JwtToFireflyPrincipalConverter(factory,new ConfigurableTenantResolver(List.of("tenant_id")),true);
        var optional=new JwtToFireflyPrincipalConverter(factory,new ConfigurableTenantResolver(List.of("tenant_id")),false);
        assertThatThrownBy(()->principal(required,claims)).isInstanceOf(OAuth2AuthenticationException.class).hasMessageNotContaining("opaque tenant");
        assertThat(principal(optional,claims).tenantId()).isNull();
    }
    @Test void numericAndMixedCollectionLegacyProjectionKeepsItsPriorSemantics() {
        var required=new JwtToFireflyPrincipalConverter(factory,new ConfigurableTenantResolver(List.of("tenant_id")),true);
        var optional=new JwtToFireflyPrincipalConverter(factory,new ConfigurableTenantResolver(List.of("tenant_id")),false);
        assertThat(principal(required,Map.of("tenant_id",17)).tenantId()).isEqualTo("17");
        assertThat(principal(optional,Map.of("tenant_id",List.of(17))).tenantId()).isEqualTo("17");
        var mixed=Map.<String,Object>of("tenant_id",List.of("tenant-a",17));
        assertThatThrownBy(()->principal(required,mixed)).isInstanceOf(OAuth2AuthenticationException.class);
        assertThat(principal(optional,mixed).tenantId()).isNull();
    }
    @Test void legacyUnconfiguredConverterStillDoesNotInferAnOpaqueTenant() {
        assertThat(principal(new JwtToFireflyPrincipalConverter(factory),Map.of("tenant_id","opaque tenant,α")).tenantId()).isNull();
    }


    @Test void malformedProjectionRejectsRequiredAndClearsOptionalWithoutSelectingAnotherTenant() {
        var required=new JwtToFireflyPrincipalConverter(factory,new ConfigurableTenantResolver(List.of("tenant_id")),true);
        var optional=new JwtToFireflyPrincipalConverter(factory,new ConfigurableTenantResolver(List.of("tenant_id")),false);
        for(String malformed:List.of("tenant\nvalue","tenant"+(char)0+"value","tenant"+(char)0xD800,"tenant"+(char)0xDC00,"\t")) {
            for(Object value:List.of(malformed,List.of(malformed),List.of("tenant-a",malformed))) {
                var claims=Map.<String,Object>of("tenant_id",value);
                assertThatThrownBy(()->principal(required,claims)).isInstanceOf(OAuth2AuthenticationException.class)
                        .hasMessageNotContaining("tenant-a").hasNoCause();
                assertThat(principal(optional,claims).tenantId()).isNull();
            }
        }
    }

}

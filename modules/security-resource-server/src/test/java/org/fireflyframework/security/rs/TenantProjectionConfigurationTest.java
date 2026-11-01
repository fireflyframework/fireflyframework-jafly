package org.fireflyframework.security.rs;

import org.fireflyframework.security.core.context.PrincipalFactory;
import org.fireflyframework.security.webflux.authentication.FireflyAuthenticationToken;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.context.annotation.*;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.*;
import reactor.core.publisher.Mono;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class TenantProjectionConfigurationTest {
    private final ReactiveWebApplicationContextRunner runner=new ReactiveWebApplicationContextRunner().withConfiguration(AutoConfigurations.of(ResourceServerAutoConfiguration.class)).withUserConfiguration(Decoder.class);
    @Test void configuredPropertiesBindToTheSingleNativeConverter() {
        runner.withPropertyValues("firefly.security.resource-server.tenant-claim-paths[0]=tenant_id","firefly.security.resource-server.tenant-required=true").run(context->{
            assertThat(context).hasNotFailed();assertThat(context.getBeansOfType(Converter.class).values().stream().filter(JwtToFireflyPrincipalConverterImpl.class::isInstance)).hasSize(1);var props=context.getBean(ResourceServerProperties.class);assertThat(props.getTenantClaimPaths()).containsExactly("tenant_id");assertThat(props.isTenantRequired()).isTrue();var converter=context.getBean(JwtToFireflyPrincipalConverterImpl.class);var jwt=Jwt.withTokenValue("prevalidated-unit-value").header("alg","RS256").subject("subject").claim("tenant_id","configured-tenant").build();assertThat(((FireflyAuthenticationToken)converter.convert(jwt).block()).securityPrincipal().tenantId()).isEqualTo("configured-tenant");
        });
    }
    @Test void requiredTenantWithoutValidConfiguredPathsFailsStartup() {
        for(var paths:List.of(List.<String>of(),List.of(" "),List.of("org..tenant"),List.of("org.tenant."))) {
            var props=new ResourceServerProperties();props.setTenantRequired(true);props.setTenantClaimPaths(paths);assertThatThrownBy(()->new ResourceServerAutoConfiguration().fireflyJwtAuthenticationConverter(new PrincipalFactory(new org.fireflyframework.security.core.authority.ConfigurableAuthorityMapperImpl(new org.fireflyframework.security.core.authority.AuthorityMappingProperties(List.of(),List.of(),""))),props)).isInstanceOf(IllegalArgumentException.class);
        }
        runner.withPropertyValues("firefly.security.resource-server.tenant-required=true").run(context->assertThat(context).hasFailed());
    }
    @Test void projectConverterStillOverridesAutoConfiguration() {
        runner.withUserConfiguration(CustomConverter.class).withPropertyValues("firefly.security.resource-server.tenant-required=true").run(context->{assertThat(context).hasNotFailed();assertThat(context.getBean("customJwtConverter")).isSameAs(CustomConverter.CONVERTER);assertThat(context).doesNotHaveBean("fireflyJwtAuthenticationConverter");});
    }
    @Configuration(proxyBeanMethods=false) static class Decoder {@Bean ReactiveJwtDecoder syntheticDecoder(){return token->Mono.error(new BadJwtException("No decoder call is expected in binding tests"));}}
    @Configuration(proxyBeanMethods=false) static class CustomConverter {
        static final Converter<Jwt,Mono<AbstractAuthenticationToken>> CONVERTER=jwt->Mono.error(new BadJwtException("Project converter placeholder for bean-override contract only"));
        @Bean Converter<Jwt,Mono<AbstractAuthenticationToken>> customJwtConverter(){return CONVERTER;}
    }
}

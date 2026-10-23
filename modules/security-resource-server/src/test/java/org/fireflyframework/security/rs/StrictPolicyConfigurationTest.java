package org.fireflyframework.security.rs;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.fireflyframework.security.api.domain.*;
import org.fireflyframework.security.core.policy.PolicyRule;
import org.fireflyframework.security.spi.PolicyDecisionPort;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import reactor.core.publisher.Mono;

class StrictPolicyConfigurationTest {
    private final SecurityPrincipal principal = SecurityPrincipal.builder().subject("synthetic-user").build();
    private final ReactiveWebApplicationContextRunner runner = new ReactiveWebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ResourceServerAutoConfiguration.class))
            .withUserConfiguration(Decoder.class);

    @Test void theOriginalDirectFactorySignatureRemainsAvailable() {
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() ->
                ResourceServerAutoConfiguration.class.getMethod("fireflyPolicyDecisionPort",
                        org.springframework.beans.factory.ObjectProvider.class));
    }
    @Test void legacyFallbackStillDefersToRbacByDefault() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(PolicyDecisionPort.class)
                    .authorize(principal, "read", "synthetic-resource", Map.of()).block().granted()).isTrue();
        });
    }
    @Test void explicitStrictPropertyMakesTheDefaultProviderDenyMissingRules() {
        runner.withPropertyValues("firefly.security.resource-server.strict-policy=true").run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(PolicyDecisionPort.class);
            assertThat(context.getBean(PolicyDecisionPort.class)
                    .authorize(principal, "read", "synthetic-resource", Map.of()).block().granted()).isFalse();
        });
    }
    @Test void strictFallbackRetainsPolicyObligationsForTheEnforcingCaller() {
        runner.withPropertyValues("firefly.security.resource-server.strict-policy=true")
                .withUserConfiguration(RequiredMask.class).run(context -> {
                    assertThat(context).hasNotFailed();
                    var decision=context.getBean(PolicyDecisionPort.class)
                            .authorize(principal,"read","synthetic-resource",Map.of()).block();
                    assertThat(decision.granted()).isTrue();
                    assertThat(decision.obligations()).containsExactly(RequiredMask.MASK);
                });
    }
    @Test void aPlainProjectProviderRemainsTheSingleAuthority() {
        for(boolean reverse : new boolean[]{false,true}) {
            var classes=reverse?new Class<?>[]{RequiredMask.class, ProjectProvider.class}
                    : new Class<?>[]{ProjectProvider.class, RequiredMask.class};
            runner.withPropertyValues("firefly.security.resource-server.strict-policy=true")
                    .withUserConfiguration(classes).run(context -> {
                        assertThat(context).hasNotFailed().hasSingleBean(PolicyDecisionPort.class);
                        assertThat(context.getBean(PolicyDecisionPort.class)).isSameAs(ProjectProvider.PROVIDER);
                    });
        }
    }
    @Configuration(proxyBeanMethods=false) static class Decoder {
        @Bean ReactiveJwtDecoder syntheticDecoder() {
            return token -> Mono.error(new BadJwtException("Synthetic decoder is not used by these provider tests"));
        }
    }
    @Configuration(proxyBeanMethods=false) static class RequiredMask {
        static final Obligation MASK=Obligation.of("mask",Map.of("field","synthetic-field"));
        @Bean PolicyRule requireMask() {return (p,a,r,c)->Mono.just(Decision.permit(List.of(MASK)));}
    }
    @Configuration(proxyBeanMethods=false) static class ProjectProvider {
        static final PolicyDecisionPort PROVIDER=(p,a,r,c)->Mono.just(Decision.deny("project-denial"));
        @Bean PolicyDecisionPort projectPolicy() {return PROVIDER;}
    }
}

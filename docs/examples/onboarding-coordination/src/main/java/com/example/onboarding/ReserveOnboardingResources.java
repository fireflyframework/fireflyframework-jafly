package com.example.onboarding;

import lombok.RequiredArgsConstructor;
import org.fireflyframework.orchestration.tcc.builder.TccBuilder;
import org.fireflyframework.orchestration.tcc.registry.TccDefinition;
import org.fireflyframework.orchestration.tcc.registry.TccRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.UUID;

@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
public class ReserveOnboardingResources {
    private final OnboardingProviders providers;

    @Bean
    TccDefinition onboardingReservations(TccRegistry registry) {
        var definition = TccBuilder.tcc("reserve-onboarding", 30_000, false, 0, 0)
                .participant("reviewer").order(1).timeoutMs(10_000)
                    .tryHandler((input, context) -> providers.reserve((UUID) input, "reviewer"))
                    .confirmHandler((result, context) -> providers.confirm((ResourceReservation) result))
                    .cancelHandler((result, context) -> providers.cancel((ResourceReservation) result))
                    .add()
                .participant("documents").order(2).timeoutMs(10_000)
                    .tryHandler((input, context) -> providers.reserve((UUID) input, "documents"))
                    .confirmHandler((result, context) -> providers.confirm((ResourceReservation) result))
                    .cancelHandler((result, context) -> providers.cancel((ResourceReservation) result))
                    .add()
                .build();
        registry.register(definition);
        return definition;
    }
}

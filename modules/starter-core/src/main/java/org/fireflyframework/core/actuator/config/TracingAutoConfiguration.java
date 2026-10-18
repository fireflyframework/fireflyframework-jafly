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


package org.fireflyframework.core.actuator.config;

import brave.baggage.BaggageField;
import brave.baggage.BaggagePropagation;
import brave.baggage.BaggagePropagationConfig;
import brave.baggage.CorrelationScopeConfig;
import brave.context.slf4j.MDCScopeDecorator;
import brave.propagation.B3Propagation;
import brave.propagation.Propagation;
import brave.propagation.ThreadLocalCurrentTraceContext;
import brave.sampler.Sampler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import static org.fireflyframework.core.config.TransactionFilter.TRANSACTION_ID_HEADER;

/**
 * Configuration class for distributed tracing.
 * <p>
 * This class provides configuration for distributed tracing using Micrometer Tracing
 * and Brave. It integrates with the existing transaction ID mechanism to provide
 * consistent tracing across services.
 * <p>
 * The configuration includes:
 * - Baggage propagation for transaction IDs
 * - Correlation of trace IDs with transaction IDs
 * - Sampling configuration
 * - MDC integration for logging
 */
@AutoConfiguration
@ConditionalOnClass(name = {"io.micrometer.tracing.Tracer", "brave.Tracing"})
@ConditionalOnProperty(prefix = "management.tracing", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TracingAutoConfiguration {

    /**
     * Configures the transaction ID field for baggage propagation.
     *
     * @return the transaction ID baggage field
     */
    @Bean
    @ConditionalOnMissingBean
    public BaggageField transactionIdField() {
        return BaggageField.create(TRANSACTION_ID_HEADER);
    }

    /**
     * Configures the propagation factory for distributed tracing.
     * <p>
     * This bean is intentionally named differently from Spring Boot's default
     * {@code propagationFactory} bean to avoid a bean name collision with
     * {@code BravePropagationConfigurations.PropagationWithBaggage#propagationFactory}.
     * Since Spring Boot disables bean definition overriding by default, a duplicate
     * name would prevent the application context from starting.
     * <p>
     * Spring Boot's auto-configuration uses {@code @ConditionalOnMissingBean} on the
     * {@link Propagation.Factory} type, so registering this bean causes the
     * auto-configured one to back off automatically.
     *
     * @param transactionIdField the transaction ID baggage field
     * @return the propagation factory
     */
    @Bean
    @ConditionalOnMissingBean
    public Propagation.Factory fireflyPropagationFactory(BaggageField transactionIdField) {
        return BaggagePropagation.newFactoryBuilder(B3Propagation.FACTORY)
                .add(BaggagePropagationConfig.SingleBaggageField.newBuilder(transactionIdField)
                        .addKeyName(TRANSACTION_ID_HEADER)
                        .build())
                .build();
    }

    /**
     * Configures the current trace context for distributed tracing.
     *
     * @param transactionIdField the transaction ID baggage field
     * @return the current trace context
     */
    @Bean
    @ConditionalOnMissingBean
    public ThreadLocalCurrentTraceContext currentTraceContext(BaggageField transactionIdField) {
        return ThreadLocalCurrentTraceContext.newBuilder()
                .addScopeDecorator(MDCScopeDecorator.newBuilder()
                        .add(CorrelationScopeConfig.SingleCorrelationField.newBuilder(transactionIdField)
                                .flushOnUpdate()
                                .build())
                        .build())
                .build();
    }

    /**
     * Configures the sampler for distributed tracing.
     *
     * @param actuatorProperties the actuator properties
     * @return the sampler
     */
    @Bean
    @ConditionalOnMissingBean
    public Sampler sampler(ActuatorProperties actuatorProperties) {
        return Sampler.create((float)actuatorProperties.getTracing().getSampling().getProbability());
    }

}

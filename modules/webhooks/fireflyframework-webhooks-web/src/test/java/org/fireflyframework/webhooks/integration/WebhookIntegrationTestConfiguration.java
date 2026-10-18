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

package org.fireflyframework.webhooks.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.fireflyframework.eda.consumer.kafka.KafkaEventConsumer;
import org.fireflyframework.eda.listener.EventListenerProcessor;
import org.fireflyframework.webhooks.integration.support.StripeSignatureValidator;
import org.fireflyframework.webhooks.integration.support.TestStripeWebhookListener;
import org.fireflyframework.webhooks.integration.support.TestStripeWebhookProcessor;
import org.fireflyframework.webhooks.processor.port.WebhookIdempotencyService;
import org.fireflyframework.webhooks.processor.port.WebhookSignatureValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Test configuration for webhook integration tests.
 * <p>
 * Provides test-specific beans including:
 * - Test webhook processor that tracks processed events
 * - Test webhook listener that consumes from Kafka
 * - Stripe signature validator for testing
 * <p>
 * Note: WebhookIdempotencyService is auto-configured by WebhookIdempotencyAutoConfiguration
 * and doesn't need to be created here.
 * <p>
 * IMPORTANT: This configuration uses a BeanPostProcessor to manually initialize the
 * EventListenerProcessor after all beans are created but before KafkaEventConsumer
 * subscribes to topics. This ensures @EventListener annotations are discovered.
 */
@TestConfiguration
public class WebhookIntegrationTestConfiguration {

    private static final Logger log = LoggerFactory.getLogger(WebhookIntegrationTestConfiguration.class);
    private static final String STRIPE_SECRET = "whsec_test_secret_key_12345";

    /**
     * BeanPostProcessor that initializes EventListenerProcessor after TestStripeWebhookListener
     * is created but before KafkaEventConsumer starts consuming.
     */
    @Bean
    public static BeanPostProcessor eventListenerInitializer() {
        return new BeanPostProcessor() {
            private boolean initialized = false;

            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                // After TestStripeWebhookListener is created, initialize EventListenerProcessor
                if (!initialized && bean instanceof TestStripeWebhookListener) {
                    log.info("🔧 TestStripeWebhookListener bean created, now initializing EventListenerProcessor");
                    initialized = true;
                }

                // When EventListenerProcessor is created, manually initialize it
                if (bean instanceof EventListenerProcessor processor) {
                    log.info("🔧 Manually initializing EventListenerProcessor for test configuration");
                    processor.initializeEventListeners();
                    log.info("✅ EventListenerProcessor initialized with {} topics",
                            processor.getTopicsForConsumerType("KAFKA").size());
                }

                // After EventListenerProcessor is initialized, refresh KafkaEventConsumer topics
                if (initialized && bean instanceof KafkaEventConsumer consumer) {
                    log.info("🔄 Refreshing KafkaEventConsumer topics after EventListenerProcessor initialization");
                    consumer.refreshTopics();
                }

                return bean;
            }
        };
    }

    @Bean
    public TestStripeWebhookProcessor testStripeWebhookProcessor() {
        return new TestStripeWebhookProcessor();
    }

    @Bean
    public StripeSignatureValidator stripeSignatureValidator() {
        return new StripeSignatureValidator(STRIPE_SECRET);
    }

    /**
     * Test webhook listener that uses auto-configured WebhookIdempotencyService.
     * The idempotency service is automatically created by WebhookIdempotencyAutoConfiguration
     * with its own dedicated cache manager.
     */
    @Bean
    public TestStripeWebhookListener testStripeWebhookListener(
            TestStripeWebhookProcessor processor,
            ObjectMapper objectMapper,
            StripeSignatureValidator signatureValidator,
            WebhookIdempotencyService idempotencyService) {
        return new TestStripeWebhookListener(processor, objectMapper, signatureValidator, idempotencyService);
    }
}


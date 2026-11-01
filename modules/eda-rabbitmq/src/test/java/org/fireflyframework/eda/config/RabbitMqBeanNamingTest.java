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

package org.fireflyframework.eda.config;

import org.fireflyframework.eda.consumer.EventConsumer;
import org.fireflyframework.eda.consumer.rabbitmq.RabbitMqEventConsumerImpl;
import org.fireflyframework.eda.publisher.EventPublisher;
import org.fireflyframework.eda.publisher.rabbitmq.RabbitMqEventPublisherImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.AnnotatedGenericBeanDefinition;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.annotation.AnnotationBeanNameGenerator;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RabbitMqBeanNamingTest {
    private String name(Class<?> type, AnnotationConfigApplicationContext context) {
        return AnnotationBeanNameGenerator.INSTANCE.generateBeanName(new AnnotatedGenericBeanDefinition(type), context);
    }

    @Test
    void originalComponentNamesRemainStable() {
        try (var context = new AnnotationConfigApplicationContext()) {
            assertThat(name(RabbitMqEventConsumerImpl.class, context)).isEqualTo("rabbitMqEventConsumer");
            assertThat(name(RabbitMqEventPublisherImpl.class, context)).isEqualTo("rabbitMqEventPublisher");
        }
    }

    @Test
    void originalQualifiersResolveTheNamedComponentsWithoutBroker() {
        try (var context = new AnnotationConfigApplicationContext()) {
            var consumer = mock(RabbitMqEventConsumerImpl.class);
            var publisher = mock(RabbitMqEventPublisherImpl.class);
            // Supplied SPI instances isolate qualifier resolution from broker-dependent component conditions.
            var consumerDefinition = new RootBeanDefinition(EventConsumer.class);
            consumerDefinition.setInstanceSupplier(() -> consumer);
            var publisherDefinition = new RootBeanDefinition(EventPublisher.class);
            publisherDefinition.setInstanceSupplier(() -> publisher);
            context.registerBeanDefinition(name(RabbitMqEventConsumerImpl.class, context), consumerDefinition);
            context.registerBeanDefinition(name(RabbitMqEventPublisherImpl.class, context), publisherDefinition);
            context.registerBean(RequiredEndpoints.class);
            context.refresh();
            var endpoints = context.getBean(RequiredEndpoints.class);
            assertThat(endpoints.consumer).isSameAs(consumer);
            assertThat(endpoints.publisher).isSameAs(publisher);
        }
    }

    static final class RequiredEndpoints {
        final EventConsumer consumer;
        final EventPublisher publisher;
        RequiredEndpoints(@Qualifier("rabbitMqEventConsumer") EventConsumer consumer,
                          @Qualifier("rabbitMqEventPublisher") EventPublisher publisher) {
            this.consumer = consumer;
            this.publisher = publisher;
        }
    }
}

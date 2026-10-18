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

package org.fireflyframework.callbacks.web.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.fireflyframework.callbacks.core.service.CallbackRouter;
import org.fireflyframework.eda.annotation.PublisherType;
import org.fireflyframework.eda.listener.DynamicEventListenerRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

/**
 * Test configuration for Kafka listeners.
 * Provides a working implementation of DynamicEventListenerRegistry for testing.
 */
@TestConfiguration
@EnableKafka
@Slf4j
public class TestKafkaListenerConfig {

    /**
     * Provides a test implementation of DynamicEventListenerRegistry.
     * This implementation actually creates and starts Kafka listeners.
     */
    @Bean
    @Primary
    public DynamicEventListenerRegistry testDynamicEventListenerRegistry(
            CallbackRouter callbackRouter,
            ObjectMapper objectMapper) {
        return new TestDynamicEventListenerRegistry(callbackRouter, objectMapper);
    }

    /**
     * Test implementation of DynamicEventListenerRegistry that actually works.
     */
    @Slf4j
    public static class TestDynamicEventListenerRegistry implements DynamicEventListenerRegistry {

        private final CallbackRouter callbackRouter;
        private final ObjectMapper objectMapper;
        private final Map<String, KafkaMessageListenerContainer<String, String>> containers = new ConcurrentHashMap<>();
        private String bootstrapServers = "localhost:9092"; // Default, will be updated

        public TestDynamicEventListenerRegistry(CallbackRouter callbackRouter, ObjectMapper objectMapper) {
            this.callbackRouter = callbackRouter;
            this.objectMapper = objectMapper;
        }

        @Override
        public void registerListener(
                String listenerId,
                String topic,
                String[] eventTypePatterns,
                PublisherType publisherType,
                BiFunction<Object, Map<String, Object>, Mono<Void>> handler) {
            
            log.info("Registering test Kafka listener: id={}, topic={}, patterns={}", 
                    listenerId, topic, eventTypePatterns);
            
            try {
                // Create Kafka consumer configuration
                Map<String, Object> props = new HashMap<>();
                props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
                props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-consumer-group-" + listenerId);
                props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
                props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
                props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
                props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
                
                ConsumerFactory<String, String> consumerFactory = new DefaultKafkaConsumerFactory<>(props);
                
                // Create container properties
                ContainerProperties containerProps = new ContainerProperties(topic);
                containerProps.setMessageListener((MessageListener<String, String>) record -> {
                    try {
                        log.info("Received Kafka message: topic={}, key={}, value={}", 
                                record.topic(), record.key(), record.value());
                        
                        // Parse the message
                        JsonNode eventJson = objectMapper.readTree(record.value());
                        
                        // Extract headers
                        Map<String, Object> headers = new HashMap<>();
                        record.headers().forEach(header ->
                                headers.put(header.key(), new String(header.value())));

                        // Generate a transaction ID for this message processing
                        String transactionId = java.util.UUID.randomUUID().toString();

                        // Call the handler with transaction ID in context
                        handler.apply(eventJson, headers)
                                .contextWrite(ctx -> ctx.put("X-Transaction-Id", transactionId))
                                .doOnSuccess(v -> log.info("Event processed successfully"))
                                .doOnError(e -> log.error("Error processing event", e))
                                .subscribe();
                        
                    } catch (Exception e) {
                        log.error("Error handling Kafka message", e);
                    }
                });
                
                // Create and start the container
                KafkaMessageListenerContainer<String, String> container = 
                        new KafkaMessageListenerContainer<>(consumerFactory, containerProps);
                container.start();
                
                containers.put(listenerId, container);
                
                log.info("Test Kafka listener started successfully: id={}, topic={}", listenerId, topic);
                
            } catch (Exception e) {
                log.error("Failed to register test Kafka listener: id={}", listenerId, e);
                throw new RuntimeException("Failed to register listener", e);
            }
        }

        @Override
        public void registerListener(
                String listenerId,
                String topic,
                PublisherType publisherType,
                BiFunction<Object, Map<String, Object>, Mono<Void>> handler) {
            // Delegate to the full method with empty event type patterns
            registerListener(listenerId, topic, new String[0], publisherType, handler);
        }

        @Override
        public void unregisterListener(String listenerId) {
            log.info("Unregistering test Kafka listener: id={}", listenerId);
            KafkaMessageListenerContainer<String, String> container = containers.remove(listenerId);
            if (container != null) {
                container.stop();
                log.info("Test Kafka listener stopped: id={}", listenerId);
            }
        }

        @Override
        public String[] getRegisteredListenerIds() {
            return containers.keySet().toArray(new String[0]);
        }

        @Override
        public boolean isListenerRegistered(String listenerId) {
            return containers.containsKey(listenerId);
        }

        public void setBootstrapServers(String bootstrapServers) {
            this.bootstrapServers = bootstrapServers;
        }
    }
}


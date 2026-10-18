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

package org.fireflyframework.eventsourcing.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.fireflyframework.eventsourcing.domain.Event;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Jackson configuration for Event Sourcing serialization.
 * <p>
 * This configuration provides a properly configured ObjectMapper for event serialization
 * and deserialization with the following features:
 * - Proper handling of Java 8+ time types
 * - Polymorphic serialization support for events
 * - Graceful handling of unknown properties
 * - Consistent date/time formatting
 * - Event type resolution based on @JsonTypeName annotations
 */
@Configuration
@Slf4j
public class EventSourcingJacksonConfiguration {

    /**
     * Creates the primary ObjectMapper bean for the application with event sourcing optimizations.
     * 
     * @return configured ObjectMapper instance
     */
    @Bean
    @Primary
    @ConditionalOnMissingBean
    public ObjectMapper eventSourcingObjectMapper() {
        log.info("Configuring ObjectMapper for event sourcing");
        
        ObjectMapper mapper = new ObjectMapper();
        
        // Time handling
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS);
        mapper.disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE);
        
        // Property handling
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT);
        mapper.enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL);
        
        // Null handling
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        
        // Configure polymorphic type handling for Events
        configurePolymorphicTypeHandling(mapper);
        
        return mapper;
    }
    
    /**
     * Configures polymorphic type handling for Event interface.
     * This enables proper serialization/deserialization of different event types.
     */
    private void configurePolymorphicTypeHandling(ObjectMapper mapper) {
        mapper.addMixIn(Event.class, EventMixin.class);

        log.debug("Configured polymorphic type handling for Event interface");
    }
    
    /**
     * Mixin interface to configure polymorphic type handling for Event.
     * This tells Jackson to use the eventType property for type resolution.
     */
    @JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "eventType",
        visible = true
    )
    public interface EventMixin {
        // This interface is just for type configuration
    }
}
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

package org.fireflyframework.eventsourcing.store.r2dbc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.fireflyframework.eventsourcing.domain.Event;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Generic event implementation used when specific event types cannot be deserialized.
 * This is primarily used for testing and debugging scenarios.
 */
public class GenericEvent implements Event {
    
    private final String eventType;
    private final String rawData;
    private Map<String, Object> parsedData;
    
    public GenericEvent(String eventType, String rawData) {
        this.eventType = eventType;
        this.rawData = rawData;
        this.parsedData = parseRawData(rawData);
    }
    
    @Override
    public String getEventType() {
        return eventType;
    }
    
    @Override
    public UUID getAggregateId() {
        Object aggregateId = parsedData.get("aggregateId");
        if (aggregateId instanceof String) {
            return UUID.fromString((String) aggregateId);
        }
        return null;
    }
    
    @Override
    public Instant getEventTimestamp() {
        Object timestamp = parsedData.get("eventTimestamp");
        if (timestamp instanceof String) {
            return Instant.parse((String) timestamp);
        }
        return Instant.now();
    }
    
    @Override
    public Map<String, Object> getMetadata() {
        Object metadata = parsedData.get("metadata");
        if (metadata instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> metadataMap = (Map<String, Object>) metadata;
            return metadataMap;
        }
        return Map.of();
    }
    
    public String getRawData() {
        return rawData;
    }
    
    public Map<String, Object> getParsedData() {
        return parsedData;
    }
    
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseRawData(String rawData) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(rawData, Map.class);
        } catch (JsonProcessingException e) {
            return Map.of("error", "Failed to parse event data: " + e.getMessage());
        }
    }
    
    @Override
    public String toString() {
        return String.format("GenericEvent{eventType='%s', aggregateId=%s}", 
                            eventType, getAggregateId());
    }
}
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

package org.fireflyframework.eventsourcing.upcasting;

import org.fireflyframework.eventsourcing.domain.Event;
import lombok.extern.slf4j.Slf4j;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing event upcasting.
 * Automatically applies registered upcasters to events when loading from the event store.
 */
@Slf4j
public class EventUpcastingService {

    private final List<EventUpcaster> upcasters;

    public EventUpcastingService(List<EventUpcaster> upcasters) {
        this.upcasters = upcasters.stream()
                .sorted(Comparator.comparingInt(EventUpcaster::getPriority).reversed())
                .toList();
        
        log.info("Initialized EventUpcastingService with {} upcasters", upcasters.size());
        upcasters.forEach(upcaster -> 
                log.debug("Registered upcaster: {} (priority: {})", 
                        upcaster.getClass().getSimpleName(), upcaster.getPriority()));
    }

    /**
     * Upcast an event to its latest version.
     * Applies all applicable upcasters in sequence.
     *
     * @param event the event to upcast
     * @return the upcasted event (or original if no upcasting needed)
     */
    public Event upcast(Event event) {
        Event current = event;
        int eventVersion = event.getEventVersion();
        String eventType = event.getEventType();

        // Keep applying upcasters until no more are applicable
        boolean upcastApplied;
        do {
            upcastApplied = false;
            Optional<EventUpcaster> applicableUpcaster = findUpcaster(eventType, eventVersion);
            
            if (applicableUpcaster.isPresent()) {
                EventUpcaster upcaster = applicableUpcaster.get();
                log.debug("Upcasting event {} from version {} to {} using {}", 
                        eventType, eventVersion, upcaster.getTargetVersion(), 
                        upcaster.getClass().getSimpleName());
                
                current = upcaster.upcast(current);
                eventVersion = upcaster.getTargetVersion();
                upcastApplied = true;
            }
        } while (upcastApplied);

        if (current != event) {
            log.info("Event {} upcasted from version {} to version {}", 
                    eventType, event.getEventVersion(), eventVersion);
        }

        return current;
    }

    /**
     * Upcast a list of events.
     *
     * @param events the events to upcast
     * @return the upcasted events
     */
    public List<Event> upcastAll(List<Event> events) {
        return events.stream()
                .map(this::upcast)
                .toList();
    }

    /**
     * Find an upcaster for the given event type and version.
     *
     * @param eventType the event type
     * @param eventVersion the event version
     * @return the upcaster, if found
     */
    private Optional<EventUpcaster> findUpcaster(String eventType, int eventVersion) {
        return upcasters.stream()
                .filter(upcaster -> upcaster.canUpcast(eventType, eventVersion))
                .findFirst();
    }

    /**
     * Check if upcasting is available for the given event.
     *
     * @param event the event to check
     * @return true if an upcaster is available
     */
    public boolean canUpcast(Event event) {
        return findUpcaster(event.getEventType(), event.getEventVersion()).isPresent();
    }

    /**
     * Get all registered upcasters.
     *
     * @return the list of upcasters
     */
    public List<EventUpcaster> getUpcasters() {
        return List.copyOf(upcasters);
    }
}


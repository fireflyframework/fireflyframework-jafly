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

package org.fireflyframework.callbacks.models.repository;

import org.fireflyframework.callbacks.models.entity.EventSubscription;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

import java.util.UUID;

/**
 * Repository for event subscriptions.
 */
@Repository
public interface EventSubscriptionRepository extends BaseRepository<EventSubscription, UUID> {

    /**
     * Finds all active subscriptions.
     */
    Flux<EventSubscription> findByActiveTrue();

    /**
     * Finds subscriptions by tenant ID.
     */
    Flux<EventSubscription> findByTenantId(String tenantId);

    /**
     * Finds active subscriptions by messaging system type.
     */
    @Query("SELECT * FROM event_subscriptions WHERE active = true AND messaging_system_type = :systemType")
    Flux<EventSubscription> findActiveByMessagingSystemType(String systemType);

    /**
     * Finds subscriptions by topic or queue.
     */
    @Query("SELECT * FROM event_subscriptions WHERE topic_or_queue = :topicOrQueue")
    Flux<EventSubscription> findByTopicOrQueue(String topicOrQueue);
}

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

package org.fireflyframework.data.lineage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thread-safe in-memory implementation of {@link LineageTracker}.
 *
 * <p>Stores lineage records in a {@link ConcurrentHashMap} keyed by entity ID,
 * with each entry backed by a {@link CopyOnWriteArrayList} for safe concurrent reads.
 * This implementation is suitable for development, testing, and single-instance deployments.</p>
 */
public class InMemoryLineageTracker implements LineageTracker {

    private static final Logger log = LoggerFactory.getLogger(InMemoryLineageTracker.class);

    private final ConcurrentHashMap<String, List<LineageRecord>> lineageByEntity = new ConcurrentHashMap<>();

    @Override
    public Mono<Void> record(LineageRecord record) {
        return Mono.fromRunnable(() -> {
            lineageByEntity
                    .computeIfAbsent(record.getEntityId(), key -> new CopyOnWriteArrayList<>())
                    .add(record);

            log.debug("Recorded lineage: entity={}, operator={}, operation={}",
                    record.getEntityId(), record.getOperatorId(), record.getOperation());
        });
    }

    @Override
    public Flux<LineageRecord> getLineage(String entityId) {
        return Flux.defer(() -> {
            List<LineageRecord> records = lineageByEntity.getOrDefault(entityId, List.of());
            return Flux.fromIterable(records);
        });
    }

    @Override
    public Flux<LineageRecord> getLineageByOperator(String operatorId) {
        return Flux.defer(() ->
                Flux.fromIterable(lineageByEntity.values())
                        .flatMapIterable(records -> records)
                        .filter(record -> operatorId.equals(record.getOperatorId()))
        );
    }
}

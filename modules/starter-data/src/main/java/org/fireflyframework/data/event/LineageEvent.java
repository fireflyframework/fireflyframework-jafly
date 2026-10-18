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

package org.fireflyframework.data.event;

import lombok.Data;
import org.fireflyframework.data.lineage.LineageRecord;

import java.time.Instant;

/**
 * Event published when a lineage record is created.
 *
 * <p>This event wraps a {@link LineageRecord} and can be consumed by Spring's event
 * mechanism or EDA components for downstream processing and auditing.</p>
 */
@Data
public class LineageEvent {

    private final LineageRecord record;
    private final Instant timestamp;

    public LineageEvent(LineageRecord record) {
        this.record = record;
        this.timestamp = Instant.now();
    }
}

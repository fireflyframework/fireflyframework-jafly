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
import org.fireflyframework.data.quality.QualityReport;

import java.time.Instant;

/**
 * Event published by the {@link org.fireflyframework.data.quality.DataQualityEngine}
 * after evaluating data quality rules.
 */
@Data
public class DataQualityEvent {

    private final QualityReport report;
    private final Instant timestamp;

    public DataQualityEvent(QualityReport report) {
        this.report = report;
        this.timestamp = Instant.now();
    }
}

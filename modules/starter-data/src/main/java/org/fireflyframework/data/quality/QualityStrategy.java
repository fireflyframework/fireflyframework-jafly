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

package org.fireflyframework.data.quality;

/**
 * Strategy for data quality evaluation.
 *
 * <ul>
 *   <li>{@link #FAIL_FAST} - Stop evaluation on the first CRITICAL failure</li>
 *   <li>{@link #COLLECT_ALL} - Run all rules regardless of failures</li>
 * </ul>
 */
public enum QualityStrategy {

    FAIL_FAST,
    COLLECT_ALL
}

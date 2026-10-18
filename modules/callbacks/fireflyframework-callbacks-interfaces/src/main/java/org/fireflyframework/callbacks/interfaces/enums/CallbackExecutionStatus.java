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

package org.fireflyframework.callbacks.interfaces.enums;

/**
 * Status of a callback execution attempt.
 */
public enum CallbackExecutionStatus {
    /**
     * Callback is pending execution.
     */
    PENDING,

    /**
     * Callback is currently being executed.
     */
    IN_PROGRESS,

    /**
     * Callback was successfully delivered.
     */
    SUCCESS,

    /**
     * Callback failed and will be retried.
     */
    FAILED_RETRYING,

    /**
     * Callback failed after all retry attempts.
     */
    FAILED_PERMANENT,

    /**
     * Callback was skipped due to configuration or filters.
     */
    SKIPPED
}

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

package org.fireflyframework.orchestration.core.exception;

import org.fireflyframework.orchestration.core.context.ExecutionContext;
import org.fireflyframework.orchestration.core.model.ExecutionStatus;

import java.util.Map;

/**
 * A required save was not acknowledged. Participant effects may already exist;
 * this error is not a request to retry execution or automatically compensate.
 */
public final class TccPersistenceException extends OrchestrationException {
    public enum Stage { INITIAL, CHECKPOINT, FINAL }

    private final Stage stage;
    private final ExecutionStatus attemptedStatus;
    private final ExecutionContext executionContext;
    private final boolean possibleParticipantEffects;

    public TccPersistenceException(Stage stage, ExecutionStatus attemptedStatus,
                                   ExecutionContext executionContext, Throwable cause) {
        super("Required TCC persistence acknowledgment failed", "ORCHESTRATION_TCC_PERSISTENCE_ERROR",
                Map.of("correlationId", executionContext.getCorrelationId(), "stage", stage.name(),
                        "attemptedStatus", attemptedStatus.name(),
                        "possibleParticipantEffects", stage != Stage.INITIAL), cause);
        this.stage = stage;
        this.attemptedStatus = attemptedStatus;
        this.executionContext = executionContext;
        this.possibleParticipantEffects = stage != Stage.INITIAL;
    }

    public Stage getStage() { return stage; }
    public ExecutionStatus getAttemptedStatus() { return attemptedStatus; }
    public String getCorrelationId() { return executionContext.getCorrelationId(); }
    public boolean hasPossibleParticipantEffects() { return possibleParticipantEffects; }
    public ExecutionContext getExecutionContext() { return executionContext; }
}

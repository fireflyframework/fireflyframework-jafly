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

package org.fireflyframework.orchestration.tcc.engine;

import lombok.extern.slf4j.Slf4j;
import org.fireflyframework.orchestration.core.context.ExecutionContext;
import org.fireflyframework.orchestration.core.exception.TccPersistenceException;
import org.fireflyframework.orchestration.core.persistence.ExecutionPersistenceProvider;
import org.fireflyframework.orchestration.core.persistence.ExecutionState;
import reactor.core.publisher.Mono;

@Slf4j
final class TccPersistenceSupport {
    private TccPersistenceSupport() {}

    static Mono<Void> save(ExecutionPersistenceProvider provider, TccPersistenceMode mode,
                           ExecutionContext context, ExecutionState state, TccPersistenceException.Stage stage) {
        if (provider == null) return Mono.empty();
        return Mono.defer(() -> provider.save(state))
                .onErrorResume(error -> {
                    if (mode == TccPersistenceMode.REQUIRED) {
                        return Mono.error(new TccPersistenceException(stage, state.status(), context, error));
                    }
                    log.warn("[tcc] Failed to persist {} state: {}", stage, context.getCorrelationId(), error);
                    return Mono.empty();
                });
    }
}

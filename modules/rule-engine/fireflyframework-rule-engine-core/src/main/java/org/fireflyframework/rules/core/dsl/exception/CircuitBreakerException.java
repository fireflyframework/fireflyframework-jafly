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

package org.fireflyframework.rules.core.dsl.exception;

import lombok.Getter;
import org.fireflyframework.kernel.exception.FireflyException;

/**
 * Exception thrown when a circuit breaker is triggered.
 * This is NOT an error - it's a controlled stop of rule execution.
 */
@Getter
public class CircuitBreakerException extends FireflyException {
    
    private final String circuitBreakerMessage;
    private final String errorCode;
    
    public CircuitBreakerException(String message) {
        super(message);
        this.circuitBreakerMessage = message;
        this.errorCode = null;
    }
    
    public CircuitBreakerException(String message, String errorCode) {
        super(message);
        this.circuitBreakerMessage = message;
        this.errorCode = errorCode;
    }
}


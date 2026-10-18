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

package org.fireflyframework.resilience;

/**
 * Enumeration of circuit breaker states.
 * 
 * <p>Circuit breaker states follow the standard pattern:
 * <ul>
 *   <li><strong>CLOSED:</strong> Normal operation, requests are allowed through</li>
 *   <li><strong>OPEN:</strong> Circuit is open, requests are rejected immediately</li>
 *   <li><strong>HALF_OPEN:</strong> Limited requests are allowed to test if the service has recovered</li>
 * </ul>
 *
 * @author Firefly Software Foundation
 * @since 2.0.0
 */
public enum CircuitBreakerState {
    
    /**
     * Circuit is closed - normal operation.
     * All requests are allowed through and failures are monitored.
     */
    CLOSED,
    
    /**
     * Circuit is open - service is considered unavailable.
     * Requests are rejected immediately without calling the service.
     */
    OPEN,
    
    /**
     * Circuit is half-open - testing if service has recovered.
     * Limited number of requests are allowed through to test service health.
     */
    HALF_OPEN
}

/*
 * Copyright 2024-2026 Firefly Software Foundation
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0
 */
package org.fireflyframework.eda.consumer.postgres;

import java.time.Duration;
import org.fireflyframework.eda.consumer.postgres.model.*;
import reactor.core.publisher.Mono;

public interface PostgresDeliveryRepository {
    Mono<ClaimBatch> claim(ClaimRequest request);
    Mono<Long> reapExhausted(ClaimRequest request);
    Mono<TransitionResult> acknowledge(DeliveryClaim claim);
    Mono<TransitionResult> reject(DeliveryClaim claim, FailureKind kind, Duration retryDelay);
    Mono<Void> verifyProtocol();
    default Mono<Void> verifyProtocol(ClaimRequest request) { return verifyProtocol(); }
}

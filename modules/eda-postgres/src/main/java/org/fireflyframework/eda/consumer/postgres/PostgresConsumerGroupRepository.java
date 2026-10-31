/*
 * Copyright 2024-2026 Firefly Software Foundation
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0
 */
package org.fireflyframework.eda.consumer.postgres;
import org.fireflyframework.eda.consumer.postgres.model.*;
import reactor.core.publisher.Mono;
public interface PostgresConsumerGroupRepository extends PostgresDeliveryRepository {
    Mono<GroupRegistrationResult> registerGroup(GroupSubscription subscription, RetainedReplayPolicy replayPolicy);
    Mono<TransitionResult> redrive(DeadLetterRedriveCommand command);
}

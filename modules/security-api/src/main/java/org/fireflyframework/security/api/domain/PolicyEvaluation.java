/*
 * Copyright 2026 Firefly Software Foundation
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0
 */
package org.fireflyframework.security.api.domain;

import java.util.Objects;
import java.util.Optional;

/** A policy decision with optional, same-response engine-reported provenance. */
public record PolicyEvaluation(Decision decision, Optional<PolicyEvaluationProvenance> provenance) {
    public PolicyEvaluation {
        Objects.requireNonNull(decision, "decision");
        Objects.requireNonNull(provenance, "provenance");
    }
}

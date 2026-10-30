/*
 * Copyright 2026 Firefly Software Foundation
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0
 */
package org.fireflyframework.security.spi;

import org.fireflyframework.security.api.domain.PolicyEvaluation;
import org.fireflyframework.security.api.domain.SecurityPrincipal;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Opt-in policy evaluation capability with explicit engine provenance availability.
 * Callers requiring versioned decisions must refuse an empty provenance. This port
 * does not confer native authority or replace a current owner authorization check.
 */
public interface PolicyEvaluationPort {
    Mono<PolicyEvaluation> evaluate(SecurityPrincipal principal, String action, String resource,
                                    Map<String, Object> context);
}

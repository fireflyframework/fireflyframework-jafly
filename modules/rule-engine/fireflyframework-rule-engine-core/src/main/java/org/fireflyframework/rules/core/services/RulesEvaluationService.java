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

package org.fireflyframework.rules.core.services;

import org.fireflyframework.rules.interfaces.dtos.evaluation.PlainYamlEvaluationRequestDTO;
import org.fireflyframework.rules.interfaces.dtos.evaluation.RuleEvaluationByCodeRequestDTO;
import org.fireflyframework.rules.interfaces.dtos.evaluation.RulesEvaluationRequestDTO;
import org.fireflyframework.rules.interfaces.dtos.evaluation.RulesEvaluationResponseDTO;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Service interface for rules evaluation operations.
 * Provides business logic for evaluating YAML DSL rules with audit trail recording.
 */
public interface RulesEvaluationService {

    /**
     * Evaluate a base64-encoded YAML rules definition with audit trail recording.
     *
     * @param request The evaluation request containing base64-encoded YAML and input data
     * @param exchange The web exchange for audit context
     * @return Mono containing the evaluation response
     */
    Mono<RulesEvaluationResponseDTO> evaluateRulesDirectWithAudit(
            RulesEvaluationRequestDTO request, ServerWebExchange exchange);

    /**
     * Evaluate a plain YAML rules definition with audit trail recording.
     *
     * @param request The evaluation request containing plain YAML and input data
     * @param exchange The web exchange for audit context
     * @return Mono containing the evaluation response
     */
    Mono<RulesEvaluationResponseDTO> evaluateRulesPlainWithAudit(
            PlainYamlEvaluationRequestDTO request, ServerWebExchange exchange);

    /**
     * Evaluate a stored rule definition by code with audit trail recording.
     *
     * @param request The evaluation request containing rule code and input data
     * @param exchange The web exchange for audit context
     * @return Mono containing the evaluation response
     */
    Mono<RulesEvaluationResponseDTO> evaluateRuleByCodeWithAudit(
            RuleEvaluationByCodeRequestDTO request, ServerWebExchange exchange);
}

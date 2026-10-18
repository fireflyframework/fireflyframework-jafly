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

package org.fireflyframework.rules.web.controllers;

import org.fireflyframework.rules.core.services.RulesEvaluationService;
import org.fireflyframework.rules.interfaces.dtos.evaluation.PlainYamlEvaluationRequestDTO;
import org.fireflyframework.rules.interfaces.dtos.evaluation.RuleEvaluationByCodeRequestDTO;
import org.fireflyframework.rules.interfaces.dtos.evaluation.RulesEvaluationRequestDTO;
import org.fireflyframework.rules.interfaces.dtos.evaluation.RulesEvaluationResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * REST controller for rules evaluation operations.
 * Provides endpoints for evaluating YAML DSL rules against input data.
 * Supports evaluation by stored rule code or direct YAML definition.
 */
@RestController
@RequestMapping("/api/v1/rules/evaluate")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Rules Evaluation", description = "Operations for evaluating YAML DSL rules against input data")
public class RulesEvaluationController {

    private final RulesEvaluationService rulesEvaluationService;

    /**
     * Evaluate a base64-encoded YAML rules definition directly against provided input data
     *
     * @param request the evaluation request containing base64-encoded rules definition and input data
     * @return the evaluation result
     */
    @PostMapping("/direct")
    @Operation(summary = "Evaluate base64-encoded YAML rules definition",
               description = "Evaluate a base64-encoded YAML DSL rules definition against provided input data. " +
                           "Input variables must use camelCase (creditScore, annualIncome), " +
                           "computed variables will be in snake_case (debt_to_income, is_eligible), " +
                           "and constants use UPPER_CASE (MIN_CREDIT_SCORE).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Rules evaluated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid rules definition, input data, or naming convention violations"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public Mono<ResponseEntity<RulesEvaluationResponseDTO>> evaluateRulesDirect(
            @Parameter(description = "Evaluation request with base64-encoded YAML rules definition and camelCase input data", required = true)
            @Valid @RequestBody RulesEvaluationRequestDTO request,
            ServerWebExchange exchange) {
        
        return rulesEvaluationService.evaluateRulesDirectWithAudit(request, exchange)
                .map(ResponseEntity::ok);
    }

    /**
     * Evaluate a plain YAML rules definition directly against provided input data
     *
     * @param request the evaluation request containing plain YAML rules definition and input data
     * @return the evaluation result
     */
    @PostMapping("/plain")
    @Operation(summary = "Evaluate plain YAML rules definition",
               description = "Evaluate a plain (non-base64) YAML DSL rules definition against provided input data. " +
                           "Input variables must use camelCase (creditScore, annualIncome), " +
                           "computed variables will be in snake_case (debt_to_income, is_eligible), " +
                           "and constants use UPPER_CASE (MIN_CREDIT_SCORE). " +
                           "This endpoint accepts YAML content directly without base64 encoding.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Rules evaluated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid rules definition, input data, or naming convention violations"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public Mono<ResponseEntity<RulesEvaluationResponseDTO>> evaluateRulesPlain(
            @Parameter(description = "Evaluation request with plain YAML rules definition and camelCase input data", required = true)
            @Valid @RequestBody PlainYamlEvaluationRequestDTO request,
            ServerWebExchange exchange) {
        
        return rulesEvaluationService.evaluateRulesPlainWithAudit(request, exchange)
                .map(ResponseEntity::ok);
    }

    /**
     * Evaluate a stored rule definition by code against provided input data
     *
     * @param request the evaluation request containing rule code and input data
     * @return the evaluation result
     */
    @PostMapping("/by-code")
    @Operation(summary = "Evaluate stored rule definition by code",
               description = "Evaluate a stored YAML DSL rule definition by its code against provided input data. " +
                           "Input variables must use camelCase naming convention. " +
                           "Returns computed variables in snake_case format.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Rules evaluated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input data or naming convention violations"),
            @ApiResponse(responseCode = "404", description = "Rule definition not found or inactive"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public Mono<ResponseEntity<RulesEvaluationResponseDTO>> evaluateRuleByCode(
            @Parameter(description = "Evaluation request with rule code and camelCase input data", required = true)
            @Valid @RequestBody RuleEvaluationByCodeRequestDTO request,
            ServerWebExchange exchange) {

        return rulesEvaluationService.evaluateRuleByCodeWithAudit(request, exchange)
                .map(ResponseEntity::ok);
    }
}

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

package org.fireflyframework.rules.core.dsl.evaluation;

import org.fireflyframework.rules.core.dsl.ASTVisitor;
import org.fireflyframework.rules.core.dsl.action.*;
import org.fireflyframework.rules.core.dsl.condition.ComparisonCondition;
import org.fireflyframework.rules.core.dsl.condition.Condition;
import org.fireflyframework.rules.core.dsl.condition.ExpressionCondition;
import org.fireflyframework.rules.core.dsl.condition.LogicalCondition;
import org.fireflyframework.rules.core.dsl.expression.*;
import org.fireflyframework.rules.core.dsl.function.CustomFunctionRegistry;
import org.fireflyframework.rules.core.dsl.model.ASTRulesDSL;
import org.fireflyframework.rules.core.dsl.parser.ASTRulesDSLParser;
import org.fireflyframework.rules.core.dsl.visitor.ActionExecutor;
import org.fireflyframework.rules.core.dsl.visitor.EvaluationContext;
import org.fireflyframework.rules.core.dsl.visitor.ExpressionEvaluator;
import org.fireflyframework.rules.core.observability.RuleEngineMetrics;
import org.fireflyframework.rules.core.services.ConstantService;
import org.fireflyframework.rules.core.services.JsonPathService;
import org.fireflyframework.rules.core.services.RestCallService;
import org.fireflyframework.rules.core.services.impl.JsonPathServiceImpl;
import org.fireflyframework.rules.core.services.impl.RestCallServiceImpl;
import org.fireflyframework.rules.core.utils.JsonLogger;
import org.fireflyframework.rules.interfaces.dtos.crud.ConstantDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Pure AST-based rules evaluation engine with no legacy dependencies.
 * Handles all rule evaluation using AST parsing and visitor patterns.
 */
@Component
@Slf4j
public class ASTRulesEvaluationEngine {

    private final ASTRulesDSLParser parser;
    private final ConstantService constantService;
    private final RestCallService restCallService;
    private final JsonPathService jsonPathService;
    private final CustomFunctionRegistry customFunctions;
    private final RuleEngineMetrics metrics;
    private final org.fireflyframework.rules.core.dsl.function.RuleInvoker ruleInvoker;

    /**
     * Primary constructor for Spring dependency injection.
     * <p>
     * {@code restCallService}, {@code jsonPathService}, {@code customFunctions}, and
     * {@code metrics} are optional. When absent, REST/JSON built-ins fall back to internal
     * default implementations, no user-registered functions are available, and no metrics
     * are recorded.
     */
    @Autowired
    public ASTRulesEvaluationEngine(ASTRulesDSLParser parser,
                                   ConstantService constantService,
                                   @Autowired(required = false) RestCallService restCallService,
                                   @Autowired(required = false) JsonPathService jsonPathService,
                                   @Autowired(required = false) CustomFunctionRegistry customFunctions,
                                   @Autowired(required = false) RuleEngineMetrics metrics,
                                   @Autowired(required = false) org.fireflyframework.rules.core.dsl.function.RuleInvoker ruleInvoker) {
        this.parser = Objects.requireNonNull(parser, "ASTRulesDSLParser cannot be null");
        this.constantService = Objects.requireNonNull(constantService, "ConstantService cannot be null");
        this.restCallService = restCallService != null ? restCallService : new RestCallServiceImpl();
        this.jsonPathService = jsonPathService != null ? jsonPathService : new JsonPathServiceImpl();
        this.customFunctions = customFunctions;
        this.metrics = metrics;
        this.ruleInvoker = ruleInvoker;
    }

    /**
     * Test-friendly constructor with default REST/JSON services and no custom function registry.
     */
    public ASTRulesEvaluationEngine(ASTRulesDSLParser parser, ConstantService constantService) {
        this.parser = Objects.requireNonNull(parser, "ASTRulesDSLParser cannot be null");
        this.constantService = Objects.requireNonNull(constantService, "ConstantService cannot be null");
        this.restCallService = new RestCallServiceImpl();
        this.jsonPathService = new JsonPathServiceImpl();
        this.customFunctions = null;
        this.metrics = null;
        this.ruleInvoker = null;
    }

    /**
     * Test-friendly 4-arg constructor (parser, constantService, restCallService, jsonPathService)
     * preserved for backward compatibility with existing tests. Delegates to the wider form with
     * {@code null} for the optional collaborators.
     */
    public ASTRulesEvaluationEngine(ASTRulesDSLParser parser,
                                    ConstantService constantService,
                                    RestCallService restCallService,
                                    JsonPathService jsonPathService) {
        this(parser, constantService, restCallService, jsonPathService, null, null, null);
    }

    /**
     * Test-friendly 5-arg constructor (parser, constantService, restCallService, jsonPathService,
     * customFunctions) preserved for backward compatibility with existing tests.
     */
    public ASTRulesEvaluationEngine(ASTRulesDSLParser parser,
                                    ConstantService constantService,
                                    RestCallService restCallService,
                                    JsonPathService jsonPathService,
                                    CustomFunctionRegistry customFunctions) {
        this(parser, constantService, restCallService, jsonPathService, customFunctions, null, null);
    }

    /**
     * Test-friendly 6-arg constructor preserved for backward compatibility.
     */
    public ASTRulesEvaluationEngine(ASTRulesDSLParser parser,
                                    ConstantService constantService,
                                    RestCallService restCallService,
                                    JsonPathService jsonPathService,
                                    CustomFunctionRegistry customFunctions,
                                    RuleEngineMetrics metrics) {
        this(parser, constantService, restCallService, jsonPathService, customFunctions, metrics, null);
    }
    
    /**
     * Evaluate rules against the provided input data.
     * <p>
     * The visitor-based evaluator is synchronous and may transitively block (e.g., built-in
     * REST/JSON functions). To avoid stalling the Netty event loop, the evaluation step is
     * scheduled on {@code Schedulers.boundedElastic()} which is designed for blocking work.
     */
    public Mono<ASTRulesEvaluationResult> evaluateRulesReactive(String rulesDefinition, Map<String, Object> inputData) {
        long startTime = System.currentTimeMillis();
        Mono<ASTRulesEvaluationResult> pipeline = parser.parseRulesReactive(rulesDefinition)
                .doOnSuccess(dsl -> { if (metrics != null) metrics.recordCompilation(true); })
                .doOnError(e   -> { if (metrics != null) metrics.recordCompilation(false); })
                .flatMap(rulesDSL -> createEvaluationContextReactive(rulesDSL, inputData)
                        .flatMap(context -> applyTimeout(rulesDSL,
                                        Mono.fromCallable(() -> evaluateRules(rulesDSL, context))
                                                .subscribeOn(Schedulers.boundedElastic())))
                        .doOnSuccess(result -> recordEvaluationOutcome(rulesDSL, result)))
                .onErrorResume(error -> {
                    long executionTime = System.currentTimeMillis() - startTime;
                    JsonLogger.error(log, "Rules evaluation failed", error);
                    String errorMessage = error.getMessage();
                    if (errorMessage == null || errorMessage.trim().isEmpty()) {
                        errorMessage = "Unknown error occurred during rule evaluation: " + error.getClass().getSimpleName();
                    }
                    JsonLogger.info(log, "Creating error result with message: " + errorMessage);
                    return Mono.just(ASTRulesEvaluationResult.builder()
                            .success(false)
                            .error(errorMessage)
                            .executionTimeMs(executionTime)
                            .build());
                });
        return pipeline;
    }

    /**
     * Wrap evaluation in a Reactor timeout if the rule declared one. Timeout failures map
     * to a clean fail-loud result rather than an uncaught TimeoutException.
     */
    private Mono<ASTRulesEvaluationResult> applyTimeout(ASTRulesDSL rulesDSL, Mono<ASTRulesEvaluationResult> mono) {
        if (rulesDSL.getTimeoutMs() == null || rulesDSL.getTimeoutMs() <= 0) return mono;
        java.time.Duration limit = java.time.Duration.ofMillis(rulesDSL.getTimeoutMs());
        return mono.timeout(limit, Mono.just(ASTRulesEvaluationResult.builder()
                .success(false)
                .error("Rule '" + (rulesDSL.getName() != null ? rulesDSL.getName() : "anonymous")
                        + "' exceeded its declared timeout of " + rulesDSL.getTimeoutMs() + "ms")
                .build()));
    }

    /**
     * Record per-rule metrics for a completed evaluation. The rule id is taken from the
     * rule's {@code name} (or {@code "anonymous"} if not declared). No-op if no
     * {@link RuleEngineMetrics} bean is wired in.
     */
    private void recordEvaluationOutcome(ASTRulesDSL rulesDSL, ASTRulesEvaluationResult result) {
        if (metrics == null) return;
        String ruleId = rulesDSL.getName() != null && !rulesDSL.getName().isBlank()
                ? rulesDSL.getName() : "anonymous";
        if (!result.isSuccess()) {
            metrics.recordUnmatched(ruleId);
        } else if (result.isCircuitBreakerTriggered()) {
            metrics.recordUnmatched(ruleId);
        } else if (!result.isConditionResult()) {
            metrics.recordUnmatched(ruleId);
        }
        // Note: matched/error metrics for the success path are recorded by the timer wrapper
        // when the engine is invoked via timedEvaluation. For direct evaluateRulesReactive
        // callers we record only the unmatched case here to avoid double-counting.
    }
    
    /**
     * Evaluate rules against the provided input data (synchronous version)
     */
    public ASTRulesEvaluationResult evaluateRules(String rulesDefinition, Map<String, Object> inputData) {
        return evaluateRulesReactive(rulesDefinition, inputData).block();
    }
    
    /**
     * Evaluate a parsed ASTRulesDSL against the provided input data.
     * Same scheduling guarantees as {@link #evaluateRulesReactive(String, Map)}.
     */
    public Mono<ASTRulesEvaluationResult> evaluateRulesReactive(ASTRulesDSL rulesDSL, Map<String, Object> inputData) {
        long startTime = System.currentTimeMillis();
        return createEvaluationContextReactive(rulesDSL, inputData)
                .flatMap(context -> Mono.fromCallable(() -> evaluateRules(rulesDSL, context))
                        .subscribeOn(Schedulers.boundedElastic()))
                .onErrorResume(error -> {
                    long executionTime = System.currentTimeMillis() - startTime;
                    JsonLogger.error(log, "Rules evaluation failed", error);

                    String errorMessage = error.getMessage();
                    if (errorMessage == null || errorMessage.trim().isEmpty()) {
                        errorMessage = "Unknown error occurred during rule evaluation: " + error.getClass().getSimpleName();
                    }
                    JsonLogger.info(log, "Creating error result with message: " + errorMessage);

                    return Mono.just(ASTRulesEvaluationResult.builder()
                            .success(false)
                            .error(errorMessage)
                            .executionTimeMs(executionTime)
                            .build());
                });
    }
    
    /**
     * Core rule evaluation logic using pure AST
     */
    private ASTRulesEvaluationResult evaluateRules(ASTRulesDSL rulesDSL, EvaluationContext context) {
        String operationId = context.getOperationId();

        // Log input data
        JsonLogger.info(log, operationId, "Input data received: " + context.getInputVariables());

        // Log constants loaded
        if (!context.getSystemConstants().isEmpty()) {
            JsonLogger.info(log, operationId, "Constants loaded from database: " + context.getSystemConstants().keySet());
        }

        boolean conditionResult = false;  // Declare outside try block for circuit breaker catch

        try {

            // DMN-style decision table takes precedence when present.
            if (rulesDSL.isDecisionTable()) {
                conditionResult = evaluateDecisionTable(rulesDSL.getDecisionTable(), context);
            }
            // Handle simplified syntax (when/then/else)
            else if (rulesDSL.isSimpleSyntax()) {
                conditionResult = evaluateConditions(rulesDSL.getWhenConditions(), context);

                if (conditionResult && rulesDSL.getThenActions() != null) {
                    executeActions(rulesDSL.getThenActions(), context);
                } else if (!conditionResult && rulesDSL.getElseActions() != null) {
                    executeActions(rulesDSL.getElseActions(), context);
                }
            }
            // Handle rules with only then actions (no when conditions)
            else if (rulesDSL.getThenActions() != null && !rulesDSL.getThenActions().isEmpty()) {
                conditionResult = true; // Always true for unconditional actions
                executeActions(rulesDSL.getThenActions(), context);
            }
            // Handle multiple rules syntax
            else if (rulesDSL.isMultipleRulesSyntax()) {
                conditionResult = evaluateMultipleRules(rulesDSL.getRules(), context);
            }
            // Handle complex conditions syntax
            else if (rulesDSL.isComplexConditionsSyntax()) {
                conditionResult = evaluateConditionalBlock(rulesDSL.getConditions(), context);
            }
            
            // Generate output
            Map<String, Object> outputData = generateOutput(rulesDSL, context, conditionResult);
            long executionTime = System.currentTimeMillis() - context.getStartTime();

            // Log final output data
            JsonLogger.info(log, operationId, "Rule evaluation output: " + outputData);
            JsonLogger.info(log, operationId, "Computed variables: " + context.getComputedVariables());
            JsonLogger.info(log, operationId, "Condition result: " + conditionResult);
            JsonLogger.info(log, operationId, "Execution time: " + executionTime + "ms");

            JsonLogger.info(log, operationId, "AST-based rule evaluation completed successfully");

            return ASTRulesEvaluationResult.builder()
                    .success(true)
                    .conditionResult(conditionResult)
                    .outputData(outputData)
                    .executionTimeMs(executionTime)
                    .circuitBreakerTriggered(context.isCircuitBreakerTriggered())
                    .circuitBreakerMessage(context.getCircuitBreakerMessage())
                    .build();

        } catch (org.fireflyframework.rules.core.dsl.exception.CircuitBreakerException e) {
            // Circuit breaker is a controlled stop, not an error
            long executionTime = System.currentTimeMillis() - context.getStartTime();
            Map<String, Object> outputData = generateOutput(rulesDSL, context, conditionResult);

            JsonLogger.info(log, operationId, "Rule evaluation stopped by circuit breaker: " + e.getCircuitBreakerMessage());
            JsonLogger.info(log, operationId, "Execution time: " + executionTime + "ms");

            return ASTRulesEvaluationResult.builder()
                    .success(true)  // Circuit breaker is a controlled stop, not an error
                    .conditionResult(conditionResult)
                    .outputData(outputData)
                    .executionTimeMs(executionTime)
                    .circuitBreakerTriggered(true)
                    .circuitBreakerMessage(e.getCircuitBreakerMessage())
                    .build();

        } catch (Exception e) {
            JsonLogger.error(log, operationId, "Error during AST-based rules evaluation", e);
            return ASTRulesEvaluationResult.builder()
                    .success(false)
                    .error(e.getMessage())
                    .build();
        }
    }
    
    /**
     * Evaluate a list of conditions using AST
     */
    private boolean evaluateConditions(List<Condition> conditions, EvaluationContext context) {
        if (conditions == null || conditions.isEmpty()) {
            JsonLogger.debug(log, context.getOperationId(), "No conditions to evaluate - returning true");
            return true;
        }

        JsonLogger.info(log, context.getOperationId(), "Evaluating " + conditions.size() + " condition(s)");

        for (int i = 0; i < conditions.size(); i++) {
            Condition condition = conditions.get(i);
            ExpressionEvaluator evaluator = new ExpressionEvaluator(context, restCallService, jsonPathService, customFunctions, ruleInvoker);
            Object result;
            try {
                result = condition.accept(evaluator);
            } catch (RuntimeException e) {
                // Propagate so the outer evaluateRules() handler reports success=false
                // with the real cause; swallowing here would silently flip rules to the
                // else branch and mask authoring or data bugs.
                throw new RuleEvaluationException(
                        "Failed to evaluate condition " + (i + 1) + " (" + condition.toDebugString() + "): "
                                + e.getMessage(), e);
            }

            boolean boolResult = toBoolean(result);
            JsonLogger.info(log, context.getOperationId(),
                String.format("Condition %d evaluation: %s = %s", i + 1, condition.toDebugString(), boolResult));

            if (!boolResult) {
                JsonLogger.info(log, context.getOperationId(), "Condition failed - short-circuiting evaluation");
                return false;
            }
        }
        JsonLogger.info(log, context.getOperationId(), "All conditions passed");
        return true;
    }
    
    /**
     * Execute a list of actions using AST
     */
    private void executeActions(List<Action> actions, EvaluationContext context) {
        if (actions == null || actions.isEmpty()) {
            JsonLogger.debug(log, context.getOperationId(), "No actions to execute");
            return;
        }

        JsonLogger.info(log, context.getOperationId(), "Executing " + actions.size() + " action(s)");

        for (int i = 0; i < actions.size(); i++) {
            Action action = actions.get(i);
            try {
                JsonLogger.info(log, context.getOperationId(),
                    String.format("Executing action %d: %s", i + 1, action.toDebugString()));

                ActionExecutor executor = new ActionExecutor(context, restCallService, jsonPathService, customFunctions, ruleInvoker);
                action.accept(executor);

                JsonLogger.info(log, context.getOperationId(),
                    String.format("Action %d completed successfully", i + 1));
            } catch (org.fireflyframework.rules.core.dsl.exception.CircuitBreakerException e) {
                JsonLogger.info(log, context.getOperationId(),
                    "Circuit breaker triggered: " + e.getCircuitBreakerMessage() + " - stopping execution");
                throw e;
            } catch (RuntimeException e) {
                // Fail-fast: the previous swallow-and-continue policy let later actions
                // read variables that the failing action never set, masking the real cause.
                // The outer evaluateRules() catch converts this into success=false with the
                // original message preserved.
                throw new RuleEvaluationException(
                        "Failed to execute action " + (i + 1) + " (" + action.toDebugString() + "): "
                                + e.getMessage(), e);
            }
        }
        JsonLogger.info(log, context.getOperationId(), "All actions completed");
    }
    
    /**
     * Evaluate a DMN-style decision table. Walks each row, tests the row's `when` predicates,
     * and -- depending on the hit policy -- collects, picks first, or enforces uniqueness of
     * matching rows' outputs. The resulting outputs are written into the context's computed
     * variables and become part of the rule's output map.
     */
    private boolean evaluateDecisionTable(ASTRulesDSL.ASTDecisionTable table, EvaluationContext context) {
        if (table == null || table.getRows() == null || table.getRows().isEmpty()) {
            return false;
        }
        java.util.List<ASTRulesDSL.ASTDecisionRow> matches = new java.util.ArrayList<>();
        ASTRulesDSL.ASTDecisionRow fallback = null;
        for (ASTRulesDSL.ASTDecisionRow row : table.getRows()) {
            if (row.isOtherwise()) { fallback = row; continue; }
            boolean ok = row.getWhen() == null || row.getWhen().isEmpty()
                    || evaluateConditions(row.getWhen(), context);
            if (ok) matches.add(row);
        }
        if (matches.isEmpty() && fallback != null) matches.add(fallback);
        if (matches.isEmpty()) return false;

        ASTRulesDSL.HitPolicy policy = table.getHitPolicy() != null ? table.getHitPolicy() : ASTRulesDSL.HitPolicy.FIRST;
        ExpressionEvaluator evaluator = new ExpressionEvaluator(context, restCallService, jsonPathService, customFunctions, ruleInvoker);
        switch (policy) {
            case FIRST, ANY -> applyDecisionRow(matches.get(0), evaluator, context);
            case UNIQUE -> {
                if (matches.size() > 1) {
                    throw new RuleEvaluationException(
                            "Decision table hit_policy=UNIQUE expects exactly one matching row but " + matches.size() + " matched");
                }
                applyDecisionRow(matches.get(0), evaluator, context);
            }
            case COLLECT -> {
                java.util.Map<String, java.util.List<Object>> grouped = new java.util.LinkedHashMap<>();
                for (ASTRulesDSL.ASTDecisionRow row : matches) {
                    if (row.getOutputs() == null) continue;
                    for (var e : row.getOutputs().entrySet()) {
                        Object val = evaluateOutputCell(e.getValue(), evaluator);
                        grouped.computeIfAbsent(e.getKey(), k -> new java.util.ArrayList<>()).add(val);
                    }
                }
                grouped.forEach(context::setComputedVariable);
            }
        }
        return true;
    }

    private void applyDecisionRow(ASTRulesDSL.ASTDecisionRow row, ExpressionEvaluator evaluator, EvaluationContext context) {
        if (row.getOutputs() == null) return;
        for (var e : row.getOutputs().entrySet()) {
            context.setComputedVariable(e.getKey(), evaluateOutputCell(e.getValue(), evaluator));
        }
    }

    private Object evaluateOutputCell(Object raw, ExpressionEvaluator evaluator) {
        // Numbers, booleans, lists, maps pass through unchanged.
        // Strings default to literals (DMN-style); prefix with '=' to mark an expression
        // that should be parsed and evaluated against the current context.
        if (!(raw instanceof String s)) return raw;
        String trimmed = s.trim();
        if (trimmed.startsWith("=")) {
            String expr = trimmed.substring(1).trim();
            try {
                return parser.getDslParser().parseExpression(expr).accept(evaluator);
            } catch (RuntimeException ex) {
                throw new RuleEvaluationException(
                        "Decision table: failed to evaluate expression '" + expr + "': " + ex.getMessage(), ex);
            }
        }
        return s;
    }

    /**
     * Evaluate multiple rules using AST
     */
    private boolean evaluateMultipleRules(List<ASTRulesDSL.ASTSubRule> rules, EvaluationContext context) {
        boolean anyConditionMet = false;

        JsonLogger.info(log, context.getOperationId(), "Evaluating " + rules.size() + " sub-rule(s)");

        for (int i = 0; i < rules.size(); i++) {
            ASTRulesDSL.ASTSubRule rule = rules.get(i);
            boolean ruleConditionMet = false;

            String ruleName = rule.getName() != null ? rule.getName() : "Rule " + (i + 1);
            JsonLogger.info(log, context.getOperationId(), "Evaluating sub-rule: " + ruleName);

            // Check if rule has simplified conditions
            if (rule.getWhenConditions() != null && !rule.getWhenConditions().isEmpty()) {
                ruleConditionMet = evaluateConditions(rule.getWhenConditions(), context);

                if (ruleConditionMet && rule.getThenActions() != null) {
                    JsonLogger.info(log, context.getOperationId(), "Sub-rule " + ruleName + " conditions met - executing THEN actions");
                    executeActions(rule.getThenActions(), context);
                } else if (!ruleConditionMet && rule.getElseActions() != null) {
                    JsonLogger.info(log, context.getOperationId(), "Sub-rule " + ruleName + " conditions not met - executing ELSE actions");
                    executeActions(rule.getElseActions(), context);
                }
            }
            // Check if rule has complex conditions block
            else if (rule.getConditions() != null) {
                JsonLogger.info(log, context.getOperationId(), "Sub-rule " + ruleName + " using complex conditions syntax");
                ruleConditionMet = evaluateConditionalBlock(rule.getConditions(), context);
            }
            // Handle rules with only then actions (no when conditions) - execute unconditionally
            else if (rule.getThenActions() != null && !rule.getThenActions().isEmpty()) {
                JsonLogger.info(log, context.getOperationId(), "Sub-rule " + ruleName + " has no conditions - executing THEN actions unconditionally");
                executeActions(rule.getThenActions(), context);
                ruleConditionMet = true; // Always true for unconditional actions
            }

            JsonLogger.info(log, context.getOperationId(), "Sub-rule " + ruleName + " result: " + ruleConditionMet);

            if (ruleConditionMet) {
                anyConditionMet = true;
            }
        }

        JsonLogger.info(log, context.getOperationId(), "Multiple rules evaluation complete - any condition met: " + anyConditionMet);
        return anyConditionMet;
    }
    
    /**
     * Evaluate a conditional block using AST - this handles nested conditions properly
     */
    private boolean evaluateConditionalBlock(ASTRulesDSL.ASTConditionalBlock conditionalBlock, EvaluationContext context) {
        if (conditionalBlock == null || conditionalBlock.getIfCondition() == null) {
            return false;
        }

        ExpressionEvaluator evaluator = new ExpressionEvaluator(context, restCallService, jsonPathService, customFunctions, ruleInvoker);
        Object result;
        try {
            result = conditionalBlock.getIfCondition().accept(evaluator);
        } catch (RuntimeException e) {
            // Propagate so the rule reports the real cause instead of silently falling
            // through to the else branch.
            throw new RuleEvaluationException(
                    "Failed to evaluate conditional block 'if' clause: " + e.getMessage(), e);
        }
        boolean conditionResult = toBoolean(result);

        ASTRulesDSL.ASTActionBlock actionBlock = conditionResult ?
                conditionalBlock.getThenBlock() :
                conditionalBlock.getElseBlock();

        if (actionBlock != null) {
            executeActionBlock(actionBlock, context);
        }

        return conditionResult;
    }
    
    /**
     * Execute an action block with full AST support including nested conditions
     */
    private void executeActionBlock(ASTRulesDSL.ASTActionBlock actionBlock, EvaluationContext context) {
        if (actionBlock == null) {
            return;
        }
        
        String operationId = context.getOperationId();
        JsonLogger.info(log, operationId, "Executing AST action block");
        
        // Execute individual actions
        if (actionBlock.getActions() != null) {
            executeActions(actionBlock.getActions(), context);
        }
        
        if (actionBlock.getNestedConditions() != null) {
            evaluateConditionalBlock(actionBlock.getNestedConditions(), context);
        }
    }
    
    /**
     * Create evaluation context with AST support
     */
    private Mono<EvaluationContext> createEvaluationContextReactive(ASTRulesDSL rulesDSL, Map<String, Object> inputData) {
        JsonLogger.info(log, "createEvaluationContextReactive called for rule: " + rulesDSL.getName());
        String operationId = UUID.randomUUID().toString();
        // Apply declared input defaults for any variable the caller omitted. Caller-provided
        // values always win; this only fills the gaps so rule authors can rely on the
        // declared default appearing in the context.
        Map<String, Object> effectiveInputs = new HashMap<>();
        if (rulesDSL.getInputDefaults() != null) effectiveInputs.putAll(rulesDSL.getInputDefaults());
        if (inputData != null) effectiveInputs.putAll(inputData);
        EvaluationContext context = new EvaluationContext(operationId, effectiveInputs);

        // Log the start of rule evaluation early so it appears even if constants loading fails
        JsonLogger.info(log, operationId, "Starting AST-based rule evaluation: " + rulesDSL.getName());

        // Load system constants and wait for completion
        return loadSystemConstants(rulesDSL)
                .map(constants -> {

                    JsonLogger.info(log, "Setting " + constants.size() + " constants in evaluation context");
                    constants.forEach(context::setConstant);
                    return context;
                })
                .doOnError(error -> JsonLogger.error(log, "Error in loadSystemConstants", error));
    }
    
    /**
     * Load system constants from database using ConstantService
     * Automatically detects constants by scanning for UPPER_CASE variable references
     */
    private Mono<Map<String, Object>> loadSystemConstants(ASTRulesDSL rulesDSL) {

        JsonLogger.info(log, "loadSystemConstants called for rule: " + rulesDSL.getName());
        Map<String, Object> constants = new HashMap<>();

        // Auto-detect constants by scanning the AST for UPPER_CASE variable references
        Set<String> detectedConstants = extractConstantReferences(rulesDSL);

        // Also include explicitly declared constants (for backward compatibility)
        if (rulesDSL.getConstants() != null && !rulesDSL.getConstants().isEmpty()) {
            rulesDSL.getConstants().stream()
                    .map(ASTRulesDSL.ASTConstantDefinition::getCode)
                    .filter(code -> code != null && !code.trim().isEmpty())
                    .forEach(detectedConstants::add);
        }

        if (!detectedConstants.isEmpty()) {
            List<String> constantCodes = new ArrayList<>(detectedConstants);

            JsonLogger.info(log, "Loading constants from database: " + constantCodes);

            JsonLogger.info(log, "About to call constantService.getConstantsByCodes with: " + constantCodes);

            return constantService.getConstantsByCodes(constantCodes)
                    .doOnNext(constantDTO -> JsonLogger.info(log, "Found constant in DB: " + constantDTO.getCode()))
                    .doOnComplete(() -> JsonLogger.info(log, "ConstantService flux completed"))
                    .collectList()
                    .doOnNext(constantDTOs -> JsonLogger.info(log, "ConstantService returned " + constantDTOs.size() + " constants"))
                    .flatMap(constantDTOs -> {
                        Map<String, Object> loadedConstants = new HashMap<>();
                        Set<String> foundConstants = new HashSet<>();

                        // Map database constants to evaluation context
                        for (ConstantDTO constantDTO : constantDTOs) {
                            if (constantDTO.getCode() != null) {
                                loadedConstants.put(constantDTO.getCode(), constantDTO.getCurrentValue());
                                foundConstants.add(constantDTO.getCode());
                                JsonLogger.info(log, "Loaded constant: " + constantDTO.getCode() + " = " + constantDTO.getCurrentValue());
                            }
                        }

                        // Check for missing constants
                        Set<String> missingConstants = new HashSet<>(detectedConstants);
                        missingConstants.removeAll(foundConstants);
                        JsonLogger.info(log, "Detected constants: " + detectedConstants + ", Found constants: " + foundConstants + ", Missing constants: " + missingConstants);

                        // Add default values for constants not found in database (if explicitly declared)
                        if (rulesDSL.getConstants() != null) {
                            for (ASTRulesDSL.ASTConstantDefinition constantDef : rulesDSL.getConstants()) {
                                if (constantDef.getCode() != null &&
                                    !loadedConstants.containsKey(constantDef.getCode()) &&
                                    constantDef.getDefaultValue() != null) {
                                    loadedConstants.put(constantDef.getCode(), constantDef.getDefaultValue());
                                    missingConstants.remove(constantDef.getCode());
                                    JsonLogger.warn(log, "Using default value for constant: " + constantDef.getCode());
                                }
                            }
                        }

                        // If there are still missing constants without default values, fail the evaluation
                        if (!missingConstants.isEmpty()) {
                            String missingConstantsList = String.join(", ", missingConstants);
                            String errorMessage = "Required constants not found in database and no default values provided: " + missingConstantsList;

                            IllegalArgumentException exception = new IllegalArgumentException(errorMessage);
                            JsonLogger.error(log, errorMessage, exception);
                            return Mono.error(exception);
                        }

                        return Mono.just(loadedConstants);
                    });
        }

        return Mono.just(constants);
    }

    /**
     * Extract constant references from the AST by scanning for UPPER_CASE variable names
     */
    private Set<String> extractConstantReferences(ASTRulesDSL rulesDSL) {
        VariableReferenceCollector collector = new VariableReferenceCollector();

        // Collect from simple syntax conditions
        if (rulesDSL.getWhenConditions() != null) {
            rulesDSL.getWhenConditions().forEach(condition -> condition.accept(collector));
        }

        // Collect from simple syntax actions
        if (rulesDSL.getThenActions() != null) {
            rulesDSL.getThenActions().forEach(action -> action.accept(collector));
        }
        if (rulesDSL.getElseActions() != null) {
            rulesDSL.getElseActions().forEach(action -> action.accept(collector));
        }

        // Collect from complex syntax (multiple rules)
        if (rulesDSL.getRules() != null) {
            for (ASTRulesDSL.ASTSubRule subRule : rulesDSL.getRules()) {
                if (subRule.getWhenConditions() != null) {
                    subRule.getWhenConditions().forEach(condition -> condition.accept(collector));
                }
                if (subRule.getThenActions() != null) {
                    subRule.getThenActions().forEach(action -> action.accept(collector));
                }
                if (subRule.getElseActions() != null) {
                    subRule.getElseActions().forEach(action -> action.accept(collector));
                }
                if (subRule.getConditions() != null) {
                    collectFromConditionalBlock(subRule.getConditions(), collector);
                }
            }
        }

        // Collect from complex conditions block
        if (rulesDSL.getConditions() != null) {
            collectFromConditionalBlock(rulesDSL.getConditions(), collector);
        }

        // Debug logging
        Set<String> allVariables = collector.getVariableReferences();
        JsonLogger.info(log, "All variable references found: " + allVariables);

        // Filter for UPPER_CASE constants only
        Set<String> constants = collector.getVariableReferences().stream()
                .filter(this::isConstantName)
                .collect(Collectors.toSet());

        JsonLogger.info(log, "Filtered constants (UPPER_CASE): " + constants);
        return constants;
    }

    /**
     * Collect variable references from a conditional block
     */
    private void collectFromConditionalBlock(ASTRulesDSL.ASTConditionalBlock conditionalBlock, VariableReferenceCollector collector) {
        if (conditionalBlock.getIfCondition() != null) {
            conditionalBlock.getIfCondition().accept(collector);
        }
        if (conditionalBlock.getThenBlock() != null && conditionalBlock.getThenBlock().getActions() != null) {
            conditionalBlock.getThenBlock().getActions().forEach(action -> action.accept(collector));
        }
        if (conditionalBlock.getElseBlock() != null && conditionalBlock.getElseBlock().getActions() != null) {
            conditionalBlock.getElseBlock().getActions().forEach(action -> action.accept(collector));
        }
    }

    /**
     * Check if a variable name follows the UPPER_CASE constant naming convention
     */
    private boolean isConstantName(String name) {
        return name != null && name.matches("^[A-Z][A-Z0-9_]*$");
    }
    
    /**
     * Convert object to boolean
     */
    private boolean toBoolean(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue() != 0.0;
        }
        if (value instanceof String) {
            String str = ((String) value).toLowerCase().trim();
            return "true".equals(str) || "yes".equals(str) || "1".equals(str);
        }
        return false;
    }
    
    /**
     * Generate output data
     */
    private Map<String, Object> generateOutput(ASTRulesDSL rulesDSL, EvaluationContext context, boolean conditionResult) {
        Map<String, Object> outputData = new HashMap<>();
        
        // Add all variables to output
        outputData.putAll(context.getAllVariables());
        
        // Add condition result
        outputData.put("conditionResult", conditionResult);
        
        // Add any specific output variables defined in the rule
        if (rulesDSL.getOutput() != null) {
            rulesDSL.getOutput().forEach((key, type) -> {
                if (context.hasVariable(key)) {
                    outputData.put(key, context.getVariable(key));
                }
            });
        }
        
        return outputData;
    }

    /**
     * Visitor class to collect all variable references from AST nodes
     */
    private static class VariableReferenceCollector implements ASTVisitor<Void> {
        private final Set<String> variableReferences = new HashSet<>();

        public Set<String> getVariableReferences() {
            return variableReferences;
        }

        @Override
        public Void visitVariableExpression(VariableExpression node) {
            variableReferences.add(node.getVariableName());
            return null;
        }

        @Override
        public Void visitComparisonCondition(ComparisonCondition node) {
            node.getLeft().accept(this);
            if (node.getRight() != null) {
                node.getRight().accept(this);
            }
            if (node.getRangeEnd() != null) {
                node.getRangeEnd().accept(this);
            }
            return null;
        }

        @Override
        public Void visitLogicalCondition(LogicalCondition node) {
            if (node.getOperands() != null) {
                node.getOperands().forEach(operand -> operand.accept(this));
            }
            return null;
        }

        @Override
        public Void visitExpressionCondition(ExpressionCondition node) {
            node.getExpression().accept(this);
            return null;
        }

        @Override
        public Void visitCalculateAction(CalculateAction node) {
            node.getExpression().accept(this);
            return null;
        }

        @Override
        public Void visitRunAction(RunAction node) {
            node.getExpression().accept(this);
            return null;
        }

        @Override
        public Void visitSetAction(SetAction node) {
            node.getValue().accept(this);
            return null;
        }

        @Override
        public Void visitFunctionCallAction(FunctionCallAction node) {
            if (node.getArguments() != null) {
                node.getArguments().forEach(arg -> arg.accept(this));
            }
            return null;
        }

        @Override
        public Void visitConditionalAction(ConditionalAction node) {
            node.getCondition().accept(this);
            if (node.getThenActions() != null) {
                node.getThenActions().forEach(action -> action.accept(this));
            }
            if (node.getElseActions() != null) {
                node.getElseActions().forEach(action -> action.accept(this));
            }
            return null;
        }

        @Override
        public Void visitBinaryExpression(BinaryExpression node) {
            node.getLeft().accept(this);
            node.getRight().accept(this);
            return null;
        }

        @Override
        public Void visitUnaryExpression(UnaryExpression node) {
            node.getOperand().accept(this);
            return null;
        }

        @Override
        public Void visitLiteralExpression(LiteralExpression node) {
            // Literals don't contain variable references
            return null;
        }

        @Override
        public Void visitFunctionCallExpression(FunctionCallExpression node) {
            if (node.getArguments() != null) {
                node.getArguments().forEach(arg -> arg.accept(this));
            }
            return null;
        }

        @Override
        public Void visitArithmeticAction(ArithmeticAction node) {
            if (node.getValue() != null) {
                node.getValue().accept(this);
            }
            return null;
        }

        @Override
        public Void visitListAction(ListAction node) {
            if (node.getValue() != null) {
                node.getValue().accept(this);
            }
            return null;
        }

        @Override
        public Void visitCircuitBreakerAction(CircuitBreakerAction node) {
            // Circuit breaker actions don't contain variable references
            return null;
        }

        @Override
        public Void visitForEachAction(ForEachAction node) {
            // Collect variable references from list expression
            if (node.getListExpression() != null) {
                node.getListExpression().accept(this);
            }

            // Collect variable references from body actions
            if (node.getBodyActions() != null) {
                for (Action bodyAction : node.getBodyActions()) {
                    bodyAction.accept(this);
                }
            }

            return null;
        }

        @Override
        public Void visitWhileAction(WhileAction node) {
            // Collect variable references from condition
            if (node.getCondition() != null) {
                node.getCondition().accept(this);
            }

            // Collect variable references from body actions
            if (node.getBodyActions() != null) {
                for (Action bodyAction : node.getBodyActions()) {
                    bodyAction.accept(this);
                }
            }

            return null;
        }

        @Override
        public Void visitDoWhileAction(DoWhileAction node) {
            // Collect variable references from body actions
            if (node.getBodyActions() != null) {
                for (Action bodyAction : node.getBodyActions()) {
                    bodyAction.accept(this);
                }
            }

            // Collect variable references from condition
            if (node.getCondition() != null) {
                node.getCondition().accept(this);
            }

            return null;
        }
    }
}

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

package org.fireflyframework.rules.core.dsl.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.fireflyframework.rules.core.dsl.action.Action;
import org.fireflyframework.rules.core.dsl.condition.Condition;
import org.fireflyframework.rules.core.dsl.exception.ASTException;
import org.fireflyframework.rules.core.dsl.model.ASTRulesDSL;
import org.fireflyframework.rules.core.services.CacheService;
import org.fireflyframework.rules.core.utils.JsonLogger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.ArrayList;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Pure AST-based rules DSL parser that converts YAML directly to AST nodes.
 * This completely replaces the legacy parser with no dependencies on legacy models.
 * Now includes high-performance caching for parsed AST models.
 */
@Component
@Slf4j
public class ASTRulesDSLParser {

    @lombok.Getter
    private final DSLParser dslParser;
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    private final ComplexConditionsParser complexConditionsParser;

    @Autowired(required = false)
    private CacheService cacheService;

    public ASTRulesDSLParser(DSLParser dslParser) {
        this.dslParser = dslParser;
        this.complexConditionsParser = new ComplexConditionsParser(dslParser);
    }

    /**
     * Parse rules definition from YAML string to AST model (reactive)
     * Uses caching to avoid re-parsing identical YAML content.
     */
    public Mono<ASTRulesDSL> parseRulesReactive(String rulesDefinition) {
        long startTime = System.currentTimeMillis();

        // Check cache first if caching is enabled
        if (cacheService != null) {
            String cacheKey = cacheService.generateCacheKey(rulesDefinition);

            return cacheService.getCachedAST(cacheKey)
                    .flatMap(cachedAST -> {
                        if (cachedAST.isPresent()) {
                            JsonLogger.info(log, "AST cache hit for rules definition");
                            return Mono.just(cachedAST.get());
                        } else {
                            // Cache miss - parse and cache the result
                            JsonLogger.info(log, "AST cache miss - parsing rules definition with AST parser");
                            try {
                                ASTRulesDSL parsedAST = parseRulesInternal(rulesDefinition);
                                return cacheService.cacheAST(cacheKey, parsedAST)
                                        .thenReturn(parsedAST);
                            } catch (Exception e) {
                                return Mono.error(new ASTException("Failed to parse rules definition: " + e.getMessage()));
                            }
                        }
                    })
                    .doOnTerminate(() -> {
                        long parseTime = System.currentTimeMillis() - startTime;
                    })
                    .onErrorMap(e -> {
                        if (e instanceof ASTException) {
                            return e;
                        }
                        JsonLogger.error(log, "Error parsing rules definition", e);
                        return new ASTException("Failed to parse rules definition: " + e.getMessage());
                    });
        } else {
            // No caching available - parse directly
            JsonLogger.info(log, "Parsing rules definition with AST parser (no caching)");
            try {
                ASTRulesDSL parsedAST = parseRulesInternal(rulesDefinition);
                return Mono.just(parsedAST);
            } catch (Exception e) {
                JsonLogger.error(log, "Error parsing rules definition", e);
                return Mono.error(new ASTException("Failed to parse rules definition: " + e.getMessage()));
            }
        }
    }

    /**
     * Parse rules definition from YAML to AST -- synchronous convenience wrapper around
     * {@link #parseRulesReactive(String)}. Intended for non-reactive callers (tests,
     * code generation, validation tools); reactive callers should subscribe to the Mono
     * directly. Internally blocks; safe to call from non-event-loop threads.
     */
    public ASTRulesDSL parseRules(String rulesDefinition) {
        return parseRulesReactive(rulesDefinition).block();
    }

    /**
     * Internal method to parse rules definition without caching logic
     */
    private ASTRulesDSL parseRulesInternal(String rulesDefinition) throws Exception {
        // Pre-flight lint: catch the most common authoring trap (unquoted colon in action
        // strings) and surface a clean error pointing to the offending line, instead of
        // letting SnakeYAML throw a confusing parse error about an unexpected map.
        lintYaml(rulesDefinition);

        @SuppressWarnings("unchecked")
        Map<String, Object> yamlMap = yamlMapper.readValue(rulesDefinition, Map.class);

        // Convert to AST model
        return convertToASTModel(yamlMap);
    }

    /**
     * Pre-parse lint pass. Scans for action-list entries containing an unquoted ':'
     * (a string with a colon must be YAML-quoted, otherwise SnakeYAML interprets it as
     * a sub-map). Throws ASTException with a precise line number when found.
     */
    private static final java.util.regex.Pattern ACTION_VERB_LINE = java.util.regex.Pattern.compile(
            "^\\s*-\\s+(set|calculate|run|call|add|subtract|multiply|divide|append|prepend|remove|log|print|invoke_rule|stop)\\b.*");

    private void lintYaml(String yaml) {
        String[] lines = yaml.split("\\r?\\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String raw = lines[i];
            String body = raw;
            // ignore comments
            int hash = body.indexOf('#');
            if (hash >= 0) body = body.substring(0, hash);
            if (!ACTION_VERB_LINE.matcher(body).matches()) continue;
            // Strip enclosing single or double quotes on the value (lazy detection: look for
            // matching quote starting after the verb)
            String trimmed = body.trim();
            // Strip "- " prefix
            String afterDash = trimmed.startsWith("- ") ? trimmed.substring(2).trim() : trimmed;
            if (afterDash.startsWith("'") || afterDash.startsWith("\"")) continue;
            // Look for an unquoted ': ' (colon followed by space) which YAML will interpret as map syntax
            if (containsUnquotedColonSpace(afterDash)) {
                throw new ASTException(
                        "YAML lint: line " + (i + 1) + " contains an unquoted ': ' inside an action -- "
                                + "wrap the action in single quotes. Offending line: '" + raw.trim() + "'");
            }
        }
    }

    private boolean containsUnquotedColonSpace(String s) {
        boolean inSingle = false, inDouble = false;
        int parenDepth = 0, braceDepth = 0, bracketDepth = 0;
        for (int i = 0; i < s.length() - 1; i++) {
            char c = s.charAt(i);
            if (c == '\'' && !inDouble) inSingle = !inSingle;
            else if (c == '"' && !inSingle) inDouble = !inDouble;
            else if (!inSingle && !inDouble) {
                switch (c) {
                    case '(' -> parenDepth++;
                    case ')' -> parenDepth = Math.max(0, parenDepth - 1);
                    case '{' -> braceDepth++;
                    case '}' -> braceDepth = Math.max(0, braceDepth - 1);
                    case '[' -> bracketDepth++;
                    case ']' -> bracketDepth = Math.max(0, bracketDepth - 1);
                    case ':' -> {
                        if (parenDepth == 0 && braceDepth == 0 && bracketDepth == 0
                                && s.charAt(i + 1) == ' ') return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Invalidate cached AST for specific YAML content.
     * Useful when rule definitions are updated.
     */
    public void invalidateCache(String rulesDefinition) {
        if (cacheService != null) {
            String cacheKey = cacheService.generateCacheKey(rulesDefinition);
            cacheService.invalidateAST(cacheKey);
            JsonLogger.info(log, "Invalidated AST cache for rules definition");
        }
    }

    /**
     * Clear all cached AST models.
     * Useful for cache management and testing.
     */
    public void clearCache() {
        if (cacheService != null) {
            cacheService.clearASTCache();
            JsonLogger.info(log, "Cleared all AST cache entries");
        }
    }
    
    /**
     * Convert YAML map to AST model
     */
    @SuppressWarnings("unchecked")
    private ASTRulesDSL convertToASTModel(Map<String, Object> yamlMap) {
        ASTRulesDSL.ASTRulesDSLBuilder builder = ASTRulesDSL.builder();
        
        // Basic metadata
        builder.name((String) yamlMap.get("name"));
        builder.description((String) yamlMap.get("description"));
        builder.version((String) yamlMap.get("version"));

        // Metadata
        if (yamlMap.containsKey("metadata")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> metadata = (Map<String, Object>) yamlMap.get("metadata");
            builder.metadata(metadata);
        }
        
        // Input definitions. Accepts three shapes:
        //   inputs: [creditScore, age]                          -- list of names (type = Object)
        //   inputs: {creditScore: "number", age: "number"}      -- name -> type
        //   inputs: {creditScore: {type: "number", default: 0}} -- name -> {type, default}
        // The richer form lets callers omit the variable; the declared default is then injected.
        Map<String, String> resolvedInputs = new java.util.LinkedHashMap<>();
        Map<String, Object> resolvedDefaults = new java.util.LinkedHashMap<>();
        Object inputsObj = yamlMap.containsKey("input") ? yamlMap.get("input") : yamlMap.get("inputs");
        if (inputsObj instanceof Map<?, ?> rawMap) {
            for (Map.Entry<?, ?> e : rawMap.entrySet()) {
                String name = String.valueOf(e.getKey());
                Object value = e.getValue();
                if (value instanceof Map<?, ?> spec) {
                    Object type = spec.get("type");
                    resolvedInputs.put(name, type == null ? "Object" : type.toString());
                    if (spec.containsKey("default")) {
                        resolvedDefaults.put(name, spec.get("default"));
                    }
                } else {
                    resolvedInputs.put(name, value == null ? "Object" : value.toString());
                }
            }
        } else if (inputsObj instanceof List<?> rawList) {
            for (Object item : rawList) resolvedInputs.put(String.valueOf(item), "Object");
        }
        if (!resolvedInputs.isEmpty()) builder.input(resolvedInputs);
        if (!resolvedDefaults.isEmpty()) builder.inputDefaults(resolvedDefaults);

        // Handle both 'output' and 'outputs' (both can be map format)
        if (yamlMap.containsKey("output")) {
            builder.output((Map<String, String>) yamlMap.get("output"));
        } else if (yamlMap.containsKey("outputs")) {
            Object outputsObj = yamlMap.get("outputs");
            if (outputsObj instanceof Map) {
                // outputs is a map format: outputs: {result: "string", score: "number"}
                builder.output((Map<String, String>) outputsObj);
            } else {
                // outputs is a list format: outputs: [result, score]
                List<String> outputsList = convertToStringList(outputsObj);
                Map<String, String> outputsMap = outputsList.stream()
                        .collect(Collectors.toMap(
                                outputName -> outputName,
                                outputName -> "Object" // Default type since outputs list doesn't specify types
                        ));
                builder.output(outputsMap);
            }
        }
        
        // Constants
        if (yamlMap.containsKey("constants")) {
            List<Map<String, Object>> constantsList = (List<Map<String, Object>>) yamlMap.get("constants");
            List<ASTRulesDSL.ASTConstantDefinition> constants = constantsList.stream()
                    .map(this::convertToConstantDefinition)
                    .collect(Collectors.toList());
            builder.constants(constants);
        }
        
        // Simple syntax (when/then/else)
        if (yamlMap.containsKey("when")) {
            List<String> whenStrings = convertToStringList(yamlMap.get("when"));
            List<Condition> whenConditions = whenStrings.stream()
                    .map(dslParser::parseCondition)
                    .collect(Collectors.toList());
            builder.whenConditions(whenConditions);
        }
        
        if (yamlMap.containsKey("then")) {
            List<Action> thenActions = parseActionList(yamlMap.get("then"));
            builder.thenActions(thenActions);
        }

        if (yamlMap.containsKey("else")) {
            List<Action> elseActions = parseActionList(yamlMap.get("else"));
            builder.elseActions(elseActions);
        }
        
        // Multiple rules syntax. Sub-rules are sorted by descending priority so a
        // higher-`priority:` sub-rule evaluates first (DRL-style salience). Ties preserve
        // YAML declaration order via a stable sort.
        if (yamlMap.containsKey("rules")) {
            List<Map<String, Object>> rulesList = (List<Map<String, Object>>) yamlMap.get("rules");
            List<ASTRulesDSL.ASTSubRule> rules = rulesList.stream()
                    .map(this::convertToSubRule)
                    .collect(Collectors.toList());
            rules.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));
            builder.rules(rules);
        }

        // Complex conditions syntax
        if (yamlMap.containsKey("conditions")) {
            Map<String, Object> conditionsMap = (Map<String, Object>) yamlMap.get("conditions");
            ASTRulesDSL.ASTConditionalBlock conditions = convertToConditionalBlock(conditionsMap);
            builder.conditions(conditions);
        }

        // Decision-table (DMN-style) syntax. Mutually exclusive with the other top-level
        // syntaxes; if both are present the explicit table takes precedence and the others
        // are ignored.
        if (yamlMap.containsKey("decision_table") || yamlMap.containsKey("decisionTable")) {
            Object dt = yamlMap.containsKey("decision_table") ? yamlMap.get("decision_table") : yamlMap.get("decisionTable");
            if (dt instanceof Map<?, ?> dtMap) {
                builder.decisionTable(convertToDecisionTable((Map<String, Object>) dtMap));
            }
        }

        // Per-rule wall-clock timeout: accepts "5s", "500ms", or a raw number of milliseconds.
        if (yamlMap.containsKey("timeout")) {
            Long ms = parseTimeoutMs(yamlMap.get("timeout"));
            if (ms != null && ms > 0) builder.timeoutMs(ms);
        }

        return builder.build();
    }

    private Long parseTimeoutMs(Object raw) {
        if (raw == null) return null;
        if (raw instanceof Number n) return n.longValue();
        String s = raw.toString().trim().toLowerCase();
        try {
            if (s.endsWith("ms")) return Long.parseLong(s.substring(0, s.length() - 2).trim());
            if (s.endsWith("s")) return (long) (Double.parseDouble(s.substring(0, s.length() - 1).trim()) * 1000L);
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            throw new ASTException("Invalid timeout value '" + raw + "'. Expected number, '5s', or '500ms'.");
        }
    }

    @SuppressWarnings("unchecked")
    private ASTRulesDSL.ASTDecisionTable convertToDecisionTable(Map<String, Object> dtMap) {
        ASTRulesDSL.ASTDecisionTable.ASTDecisionTableBuilder b = ASTRulesDSL.ASTDecisionTable.builder();
        if (dtMap.containsKey("inputs")) b.inputs(convertToStringList(dtMap.get("inputs")));
        if (dtMap.containsKey("outputs")) b.outputs(convertToStringList(dtMap.get("outputs")));
        if (dtMap.containsKey("hit_policy") || dtMap.containsKey("hitPolicy")) {
            String hp = String.valueOf(dtMap.containsKey("hit_policy") ? dtMap.get("hit_policy") : dtMap.get("hitPolicy"));
            b.hitPolicy(ASTRulesDSL.HitPolicy.valueOf(hp.toUpperCase()));
        }
        List<ASTRulesDSL.ASTDecisionRow> rows = new java.util.ArrayList<>();
        Object rawRows = dtMap.get("rules");
        if (rawRows == null) rawRows = dtMap.get("rows");
        if (rawRows instanceof List<?> list) {
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> rowMap)) continue;
                ASTRulesDSL.ASTDecisionRow.ASTDecisionRowBuilder rb = ASTRulesDSL.ASTDecisionRow.builder();
                if (rowMap.get("name") instanceof String s) rb.name(s);
                if (rowMap.get("otherwise") instanceof Boolean ow) rb.otherwise(ow);
                Object whenObj = rowMap.get("when");
                if (whenObj != null) {
                    List<String> whenStrings = convertToStringList(whenObj);
                    rb.when(whenStrings.stream().map(dslParser::parseCondition).collect(Collectors.toList()));
                }
                Object outObj = rowMap.get("then");
                if (outObj == null) outObj = rowMap.get("outputs");
                if (outObj instanceof Map<?, ?> outMap) {
                    Map<String, Object> outs = new java.util.LinkedHashMap<>();
                    for (Map.Entry<?, ?> e : outMap.entrySet()) outs.put(String.valueOf(e.getKey()), e.getValue());
                    rb.outputs(outs);
                }
                rows.add(rb.build());
            }
        }
        b.rows(rows);
        return b.build();
    }
    
    /**
     * Convert to constant definition
     */
    private ASTRulesDSL.ASTConstantDefinition convertToConstantDefinition(Map<String, Object> constantMap) {
        return ASTRulesDSL.ASTConstantDefinition.builder()
                .name((String) constantMap.get("name"))
                .code((String) constantMap.get("code"))
                .type((String) constantMap.get("type"))
                .defaultValue(constantMap.get("defaultValue"))
                .build();
    }
    
    /**
     * Convert to sub-rule
     */
    @SuppressWarnings("unchecked")
    private ASTRulesDSL.ASTSubRule convertToSubRule(Map<String, Object> ruleMap) {
        ASTRulesDSL.ASTSubRule.ASTSubRuleBuilder builder = ASTRulesDSL.ASTSubRule.builder();
        
        builder.name((String) ruleMap.get("name"));
        builder.description((String) ruleMap.get("description"));
        if (ruleMap.get("priority") instanceof Number n) builder.priority(n.intValue());

        // Simple syntax
        if (ruleMap.containsKey("when")) {
            List<String> whenStrings = convertToStringList(ruleMap.get("when"));
            List<Condition> whenConditions = whenStrings.stream()
                    .map(dslParser::parseCondition)
                    .collect(Collectors.toList());
            builder.whenConditions(whenConditions);
        }
        
        // Sub-rules use the same action-parsing path as the top-level when/then/else so
        // YAML-collapsed map-syntax actions (e.g. `- forEach x in xs: action`) work in
        // both contexts. Routing through parseActionList preserves Map entries instead of
        // calling toString() on them.
        if (ruleMap.containsKey("then")) {
            builder.thenActions(parseActionList(ruleMap.get("then")));
        }

        if (ruleMap.containsKey("else")) {
            builder.elseActions(parseActionList(ruleMap.get("else")));
        }

        // Complex syntax
        if (ruleMap.containsKey("conditions")) {
            Map<String, Object> conditionsMap = (Map<String, Object>) ruleMap.get("conditions");
            ASTRulesDSL.ASTConditionalBlock conditions = convertToConditionalBlock(conditionsMap);
            builder.conditions(conditions);
        }
        
        return builder.build();
    }
    
    /**
     * Convert to conditional block
     */
    @SuppressWarnings("unchecked")
    private ASTRulesDSL.ASTConditionalBlock convertToConditionalBlock(Map<String, Object> conditionsMap) {
        ASTRulesDSL.ASTConditionalBlock.ASTConditionalBlockBuilder builder =
                ASTRulesDSL.ASTConditionalBlock.builder();

        // Parse if condition using complex conditions parser
        if (conditionsMap.containsKey("if")) {
            Object ifConditionObj = conditionsMap.get("if");
            Condition ifCondition = complexConditionsParser.parseComplexCondition(ifConditionObj);
            builder.ifCondition(ifCondition);
        }
        
        // Parse then block
        if (conditionsMap.containsKey("then")) {
            Map<String, Object> thenMap = (Map<String, Object>) conditionsMap.get("then");
            ASTRulesDSL.ASTActionBlock thenBlock = convertToActionBlock(thenMap);
            builder.thenBlock(thenBlock);
        }
        
        // Parse else block
        if (conditionsMap.containsKey("else")) {
            Map<String, Object> elseMap = (Map<String, Object>) conditionsMap.get("else");
            ASTRulesDSL.ASTActionBlock elseBlock = convertToActionBlock(elseMap);
            builder.elseBlock(elseBlock);
        }
        
        return builder.build();
    }
    
    /**
     * Convert to action block with support for nested conditions
     */
    @SuppressWarnings("unchecked")
    private ASTRulesDSL.ASTActionBlock convertToActionBlock(Map<String, Object> actionMap) {
        ASTRulesDSL.ASTActionBlock.ASTActionBlockBuilder builder = 
                ASTRulesDSL.ASTActionBlock.builder();
        
        // Parse actions
        if (actionMap.containsKey("actions")) {
            Object actionsObj = actionMap.get("actions");
            List<Action> actions = parseActionsList(actionsObj);
            builder.actions(actions);
        }
        
        if (actionMap.containsKey("conditions")) {
            Map<String, Object> nestedConditionsMap = (Map<String, Object>) actionMap.get("conditions");
            ASTRulesDSL.ASTConditionalBlock nestedConditions = convertToConditionalBlock(nestedConditionsMap);
            builder.nestedConditions(nestedConditions);
        }
        
        return builder.build();
    }
    
    /**
     * Parse actions list that can contain either simple syntax strings or complex syntax maps.
     * Throws ASTException on any parse failure rather than silently dropping malformed actions.
     */
    @SuppressWarnings("unchecked")
    private List<Action> parseActionsList(Object actionsObj) {
        if (actionsObj instanceof List) {
            List<Object> actionsList = (List<Object>) actionsObj;
            return actionsList.stream()
                    .map(this::parseActionItem)
                    .collect(Collectors.toList());
        } else if (actionsObj instanceof String) {
            return List.of(dslParser.parseAction((String) actionsObj));
        } else {
            throw new ASTException("Unexpected actions object type: " + actionsObj.getClass().getSimpleName());
        }
    }

    /**
     * Parse a single action item (either string or map).
     * Throws ASTException on unknown types or malformed action structures so that
     * malformed rules surface immediately instead of silently losing actions.
     */
    @SuppressWarnings("unchecked")
    private Action parseActionItem(Object actionObj) {
        if (actionObj == null) {
            throw new ASTException("Action entry cannot be null");
        }
        if (actionObj instanceof String) {
            return dslParser.parseAction((String) actionObj);
        } else if (actionObj instanceof Map) {
            Map<String, Object> actionMap = (Map<String, Object>) actionObj;

            // Single-entry maps like {"forEach item in items": "action"} originate from YAML
            // collapsing simple loop syntax. Try the literal reconstruction first; if that
            // does not match the lexer expectations, fall through to the structured-map path.
            if (actionMap.size() == 1) {
                Map.Entry<String, Object> entry = actionMap.entrySet().iterator().next();
                String key = entry.getKey();
                Object value = entry.getValue();

                if (key.startsWith("forEach ") || key.startsWith("while ") || key.equals("do")) {
                    String actionString = key + ": " + formatActionValue(value);
                    try {
                        return dslParser.parseAction(actionString);
                    } catch (Exception e) {
                        log.debug("Reconstructed loop parse failed for '{}', retrying as structured map", actionString);
                        // Fall through to complex action parsing.
                    }
                }
            }

            return parseComplexAction(actionMap);
        } else {
            throw new ASTException("Unsupported action entry type: " + actionObj.getClass().getName());
        }
    }

    /**
     * Format action value for reconstruction (handles strings, lists, etc.)
     */
    @SuppressWarnings("unchecked")
    private String formatActionValue(Object value) {
        if (value instanceof String) {
            return (String) value;
        } else if (value instanceof List) {
            List<Object> list = (List<Object>) value;
            return list.stream()
                    .map(item -> item instanceof String ? (String) item : item.toString())
                    .collect(Collectors.joining("; "));
        } else {
            return value.toString();
        }
    }

    /**
     * Parse complex map-shaped action and re-emit as the simple-syntax string the lexer expects.
     * Throws ASTException on any parse failure so malformed entries never silently disappear.
     */
    @SuppressWarnings("unchecked")
    private Action parseComplexAction(Map<String, Object> actionMap) {
        if (actionMap.containsKey("set")) {
            Map<String, Object> setMap = (Map<String, Object>) actionMap.get("set");
            String variable = (String) setMap.get("variable");
            Object value = setMap.get("value");
            return parseReconstructed("set " + variable + " to " + formatValue(value), "set", actionMap);
        }
        if (actionMap.containsKey("calculate")) {
            Map<String, Object> calcMap = (Map<String, Object>) actionMap.get("calculate");
            String variable = (String) calcMap.get("variable");
            String expression = (String) calcMap.get("expression");
            return parseReconstructed("calculate " + variable + " as " + expression, "calculate", actionMap);
        }
        if (actionMap.containsKey("run")) {
            Map<String, Object> runMap = (Map<String, Object>) actionMap.get("run");
            String variable = (String) runMap.get("variable");
            String expression = (String) runMap.get("expression");
            return parseReconstructed("run " + variable + " as " + expression, "run", actionMap);
        }
        if (actionMap.containsKey("call")) {
            Map<String, Object> callMap = (Map<String, Object>) actionMap.get("call");
            String function = (String) callMap.get("function");
            List<Object> parameters = (List<Object>) callMap.get("parameters");
            String paramStr = parameters.stream()
                    .map(this::formatValue)
                    .collect(Collectors.joining(", "));
            return parseReconstructed("call " + function + " with [" + paramStr + "]", "call", actionMap);
        }
        if (actionMap.containsKey("forEach")) {
            Map<String, Object> forEachMap = (Map<String, Object>) actionMap.get("forEach");
            String variable = (String) forEachMap.get("variable");
            String index = (String) forEachMap.get("index");
            Object inValue = forEachMap.get("in");
            Object doValue = forEachMap.get("do");

            StringBuilder simpleSyntax = new StringBuilder("forEach ").append(variable);
            if (index != null && !index.trim().isEmpty()) {
                simpleSyntax.append(", ").append(index);
            }
            simpleSyntax.append(" in ").append(formatValue(inValue)).append(": ");
            appendLoopBody(doValue, simpleSyntax);
            return parseReconstructed(simpleSyntax.toString(), "forEach", actionMap);
        }
        if (actionMap.containsKey("while")) {
            Map<String, Object> whileMap = (Map<String, Object>) actionMap.get("while");
            String condition = (String) whileMap.get("condition");
            Object doValue = whileMap.get("do");

            StringBuilder simpleSyntax = new StringBuilder("while ").append(condition).append(": ");
            appendLoopBody(doValue, simpleSyntax);
            return parseReconstructed(simpleSyntax.toString(), "while", actionMap);
        }
        if (actionMap.containsKey("do")) {
            Map<String, Object> doWhileMap = (Map<String, Object>) actionMap.get("do");
            Object doValue = doWhileMap.get("actions");
            String condition = (String) doWhileMap.get("while");

            StringBuilder simpleSyntax = new StringBuilder("do: ");
            appendLoopBody(doValue, simpleSyntax);
            simpleSyntax.append(" while ").append(condition);
            return parseReconstructed(simpleSyntax.toString(), "do-while", actionMap);
        }
        throw new ASTException("Unknown complex action type. Keys present: " + actionMap.keySet());
    }

    /**
     * Reconstruct a loop body from a List of action entries or a single String into the
     * semicolon-joined form the ActionParser consumes.
     */
    @SuppressWarnings("unchecked")
    private void appendLoopBody(Object doValue, StringBuilder out) {
        if (doValue instanceof List) {
            List<Object> doList = (List<Object>) doValue;
            List<String> bodyActionStrings = new ArrayList<>();
            for (Object actionObj : doList) {
                if (actionObj instanceof String) {
                    bodyActionStrings.add((String) actionObj);
                } else if (actionObj instanceof Map) {
                    Action bodyAction = parseComplexAction((Map<String, Object>) actionObj);
                    bodyActionStrings.add(bodyAction.toDebugString());
                } else {
                    throw new ASTException("Unsupported loop body entry type: "
                            + (actionObj == null ? "null" : actionObj.getClass().getName()));
                }
            }
            out.append(String.join("; ", bodyActionStrings));
        } else if (doValue instanceof String) {
            out.append((String) doValue);
        } else if (doValue != null) {
            throw new ASTException("Unsupported loop body type: " + doValue.getClass().getName());
        }
    }

    /**
     * Parse a reconstructed simple-syntax string, wrapping any lexer/parser failure in an
     * ASTException tagged with the original complex-form context for easier diagnostics.
     */
    private Action parseReconstructed(String simpleSyntax, String actionKind, Map<String, Object> originalMap) {
        try {
            return dslParser.parseAction(simpleSyntax);
        } catch (Exception e) {
            throw new ASTException(
                    "Failed to parse complex " + actionKind + " action " + originalMap
                            + " (reconstructed: " + simpleSyntax + "): " + e.getMessage(),
                    e);
        }
    }

    /**
     * Format a value for use in simple syntax
     */
    private String formatValue(Object value) {
        if (value instanceof String) {
            return "\"" + value + "\"";
        } else {
            return String.valueOf(value);
        }
    }

    /**
     * Parse a list of actions, handling both simple string syntax and complex map syntax
     */
    @SuppressWarnings("unchecked")
    private List<Action> parseActionList(Object obj) {
        if (obj instanceof List) {
            List<Object> list = (List<Object>) obj;
            return list.stream()
                    .map(item -> {
                        try {
                            if (item instanceof String) {
                                // Simple syntax: "set x to 5"
                                return dslParser.parseAction((String) item);
                            } else if (item instanceof Map) {
                                Map<String, Object> map = (Map<String, Object>) item;

                                // Check if this is a YAML-parsed simple syntax action
                                // e.g., {forEach item in items: set count to 1}
                                // e.g., {while counter < 10: add 1 to counter}
                                // e.g., {do: add 1 to counter while counter < 10}
                                if (map.size() == 1) {
                                    Map.Entry<String, Object> entry = map.entrySet().iterator().next();
                                    String key = entry.getKey();
                                    Object value = entry.getValue();

                                    // If the key starts with an action keyword, reconstruct the action string
                                    if (key.startsWith("forEach ") || key.startsWith("for ") ||
                                        key.startsWith("while ") || key.equals("do")) {
                                        String actionString = key + ": " + value;
                                        return dslParser.parseAction(actionString);
                                    }
                                }

                                // Complex syntax: {forEach: {variable: x, in: items, do: [...]}}
                                return parseComplexAction(map);
                            } else {
                                // Fallback: try to parse as string
                                return dslParser.parseAction(item.toString());
                            }
                        } catch (Exception e) {
                            log.error("Error parsing action: {}", item, e);
                            throw e;
                        }
                    })
                    .collect(Collectors.toList());
        } else if (obj instanceof String) {
            return List.of(dslParser.parseAction((String) obj));
        } else {
            return List.of(dslParser.parseAction(obj.toString()));
        }
    }

    /**
     * Convert object to string list
     */
    @SuppressWarnings("unchecked")
    private List<String> convertToStringList(Object obj) {
        if (obj instanceof List) {
            return ((List<Object>) obj).stream()
                    .map(Object::toString)
                    .collect(Collectors.toList());
        } else if (obj instanceof String) {
            return List.of((String) obj);
        } else {
            return List.of(obj.toString());
        }
    }

}

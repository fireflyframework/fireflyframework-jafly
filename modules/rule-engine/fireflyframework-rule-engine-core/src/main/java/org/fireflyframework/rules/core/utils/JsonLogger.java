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

package org.fireflyframework.rules.core.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Utility class for structured JSON logging in the rule engine.
 * Provides methods to create consistent JSON log messages with proper structure.
 */
@Slf4j
public class JsonLogger {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    static {
        objectMapper.findAndRegisterModules();
    }

    /**
     * Log an info message with structured JSON format
     */
    public static void info(Logger logger, String message, Object... params) {
        logger.info(createJsonLog("INFO", message, null, params));
    }

    /**
     * Log an info message with operation context
     */
    public static void info(Logger logger, String operationId, String message, Object... params) {
        logger.info(createJsonLog("INFO", message, operationId, params));
    }

    /**
     * Log a debug message with structured JSON format
     */
    public static void debug(Logger logger, String message, Object... params) {
        logger.debug(createJsonLog("DEBUG", message, null, params));
    }

    /**
     * Log a debug message with operation context
     */
    public static void debug(Logger logger, String operationId, String message, Object... params) {
        logger.debug(createJsonLog("DEBUG", message, operationId, params));
    }

    /**
     * Log a warning message with structured JSON format
     */
    public static void warn(Logger logger, String message, Object... params) {
        logger.warn(createJsonLog("WARN", message, null, params));
    }

    /**
     * Log a warning message with operation context
     */
    public static void warn(Logger logger, String operationId, String message, Object... params) {
        logger.warn(createJsonLog("WARN", message, operationId, params));
    }

    /**
     * Log an error message with structured JSON format
     */
    public static void error(Logger logger, String message, Throwable throwable, Object... params) {
        logger.error(createJsonLog("ERROR", message, null, throwable, params));
    }

    /**
     * Log an error message with operation context
     */
    public static void error(Logger logger, String operationId, String message, Throwable throwable, Object... params) {
        logger.error(createJsonLog("ERROR", message, operationId, throwable, params));
    }

    /**
     * Log rule execution start
     */
    public static void logRuleStart(Logger logger, String operationId, String ruleName, Map<String, Object> inputVariables) {
        Map<String, Object> logData = new HashMap<>();
        logData.put("event", "rule_execution_start");
        logData.put("rule_name", ruleName);
        logData.put("input_variables", inputVariables != null ? inputVariables.keySet() : null);
        logData.put("input_count", inputVariables != null ? inputVariables.size() : 0);
        
        logger.info(createStructuredLog("INFO", "Rule execution started", operationId, logData));
    }

    /**
     * Log rule execution completion
     */
    public static void logRuleComplete(Logger logger, String operationId, String ruleName, 
                                     boolean success, Map<String, Object> outputData, long executionTimeMs) {
        Map<String, Object> logData = new HashMap<>();
        logData.put("event", "rule_execution_complete");
        logData.put("rule_name", ruleName);
        logData.put("success", success);
        logData.put("output_data", outputData);
        logData.put("execution_time_ms", executionTimeMs);
        
        logger.info(createStructuredLog("INFO", "Rule execution completed", operationId, logData));
    }

    /**
     * Log condition evaluation
     */
    public static void logConditionEvaluation(Logger logger, String operationId, String condition, boolean result) {
        Map<String, Object> logData = new HashMap<>();
        logData.put("event", "condition_evaluation");
        logData.put("condition", condition);
        logData.put("result", result);
        
        logger.info(createStructuredLog("INFO", "Condition evaluated", operationId, logData));
    }

    /**
     * Log action execution
     */
    public static void logActionExecution(Logger logger, String operationId, String actionType, 
                                        String actionDetails, boolean success) {
        Map<String, Object> logData = new HashMap<>();
        logData.put("event", "action_execution");
        logData.put("action_type", actionType);
        logData.put("action_details", actionDetails);
        logData.put("success", success);
        
        logger.info(createStructuredLog("INFO", "Action executed", operationId, logData));
    }

    /**
     * Log variable assignment
     */
    public static void logVariableAssignment(Logger logger, String operationId, String variableName, 
                                           Object value, String variableType) {
        Map<String, Object> logData = new HashMap<>();
        logData.put("event", "variable_assignment");
        logData.put("variable_name", variableName);
        logData.put("variable_value", value);
        logData.put("variable_type", variableType);
        logData.put("value_class", value != null ? value.getClass().getSimpleName() : "null");
        
        logger.info(createStructuredLog("INFO", "Variable assigned", operationId, logData));
    }

    /**
     * Log function call
     */
    public static void logFunctionCall(Logger logger, String operationId, String functionName, 
                                     Object parameters, boolean success) {
        Map<String, Object> logData = new HashMap<>();
        logData.put("event", "function_call");
        logData.put("function_name", functionName);
        logData.put("parameters", parameters);
        logData.put("success", success);
        
        logger.info(createStructuredLog("INFO", "Function called", operationId, logData));
    }

    /**
     * Log circuit breaker trigger
     */
    public static void logCircuitBreaker(Logger logger, String operationId, String message, String ruleName) {
        Map<String, Object> logData = new HashMap<>();
        logData.put("event", "circuit_breaker_triggered");
        logData.put("rule_name", ruleName);
        logData.put("circuit_breaker_message", message);
        
        logger.warn(createStructuredLog("WARN", "Circuit breaker triggered", operationId, logData));
    }

    /**
     * Create a basic JSON log message
     */
    private static String createJsonLog(String level, String message, String operationId, Object... params) {
        return createStructuredLog(level, message, operationId, null, null, params);
    }

    /**
     * Create a JSON log message with error
     */
    private static String createJsonLog(String level, String message, String operationId, 
                                      Throwable throwable, Object... params) {
        return createStructuredLog(level, message, operationId, null, throwable, params);
    }

    /**
     * Create a structured JSON log message
     */
    private static String createStructuredLog(String level, String message, String operationId, 
                                            Map<String, Object> additionalData) {
        return createStructuredLog(level, message, operationId, additionalData, null);
    }

    /**
     * Create a comprehensive structured JSON log message
     */
    private static String createStructuredLog(String level, String message, String operationId, 
                                            Map<String, Object> additionalData, Throwable throwable, 
                                            Object... params) {
        Map<String, Object> logEntry = new HashMap<>();
        
        // Basic log structure
        logEntry.put("timestamp", Instant.now().toString());
        logEntry.put("level", level);
        logEntry.put("message", message);
        logEntry.put("component", "rule_engine");
        
        // Operation context
        if (operationId != null) {
            logEntry.put("operation_id", operationId);
        } else {
            logEntry.put("operation_id", UUID.randomUUID().toString());
        }
        
        // Parameters
        if (params != null && params.length > 0) {
            logEntry.put("parameters", params);
        }
        
        // Additional structured data
        if (additionalData != null) {
            logEntry.putAll(additionalData);
        }
        
        // Error information
        if (throwable != null) {
            Map<String, Object> errorInfo = new HashMap<>();
            errorInfo.put("exception_class", throwable.getClass().getSimpleName());
            errorInfo.put("exception_message", throwable.getMessage());
            if (throwable.getCause() != null) {
                errorInfo.put("cause", throwable.getCause().getMessage());
            }
            logEntry.put("error", errorInfo);
        }
        
        try {
            return objectMapper.writeValueAsString(logEntry);
        } catch (JsonProcessingException e) {
            // Fallback to simple format if JSON serialization fails
            return String.format("{\"timestamp\":\"%s\",\"level\":\"%s\",\"message\":\"%s\",\"error\":\"JSON_SERIALIZATION_FAILED\"}", 
                    Instant.now().toString(), level, message);
        }
    }
}

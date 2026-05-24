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

import org.fireflyframework.rules.core.dsl.action.Action;
import org.fireflyframework.rules.core.dsl.condition.Condition;
import org.fireflyframework.rules.core.dsl.expression.Expression;
import org.fireflyframework.rules.core.dsl.lexer.Lexer;
import org.fireflyframework.rules.core.dsl.lexer.LexerException;
import org.fireflyframework.rules.core.dsl.lexer.Token;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Main DSL parser that coordinates lexical analysis and parsing.
 * Provides high-level parsing methods for different DSL constructs.
 */
@Component
@Slf4j
public class DSLParser {
    
    /**
     * Parse a condition expression from source text
     */
    public Condition parseCondition(String source) {
        log.debug("Parsing condition: {}", source);
        
        try {
            List<Token> tokens = tokenize(source);
            ConditionParser parser = new ConditionParser(tokens);
            Condition condition = parser.parseCondition();
            
            log.debug("Successfully parsed condition: {}", condition.toDebugString());
            return condition;
            
        } catch (LexerException e) {
            log.error("Lexical error while parsing condition: {}", e.getDetailedMessage());
            throw new ParseException(
                "Lexical error: " + e.getMessage(),
                e.getLocation(),
                "PARSE_LEX_001"
            );
        } catch (ParseException e) {
            log.error("Parse error while parsing condition: {}", e.getDetailedMessage());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error while parsing condition", e);
            throw new ParseException(
                "Unexpected parsing error: " + e.getMessage(),
                null,
                "PARSE_UNEXPECTED"
            );
        }
    }
    
    /**
     * Parse an expression from source text
     */
    public Expression parseExpression(String source) {
        log.debug("Parsing expression: {}", source);
        
        try {
            List<Token> tokens = tokenize(source);
            ExpressionParser parser = new ExpressionParser(tokens);
            Expression expression = parser.parseExpression();
            
            log.debug("Successfully parsed expression: {}", expression.toDebugString());
            return expression;
            
        } catch (LexerException e) {
            log.error("Lexical error while parsing expression: {}", e.getDetailedMessage());
            throw new ParseException(
                "Lexical error: " + e.getMessage(),
                e.getLocation(),
                "PARSE_LEX_002"
            );
        } catch (ParseException e) {
            log.error("Parse error while parsing expression: {}", e.getDetailedMessage());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error while parsing expression", e);
            throw new ParseException(
                "Unexpected parsing error: " + e.getMessage(),
                null,
                "PARSE_UNEXPECTED"
            );
        }
    }
    
    /**
     * Parse an action from source text
     */
    public Action parseAction(String source) {
        log.debug("Parsing action: {}", source);
        
        try {
            List<Token> tokens = tokenize(source);
            ActionParser parser = new ActionParser(tokens);
            Action action = parser.parseAction();
            
            log.debug("Successfully parsed action: {}", action.toDebugString());
            return action;
            
        } catch (LexerException e) {
            log.error("Lexical error while parsing action: {}", e.getDetailedMessage());
            throw new ParseException(
                "Lexical error: " + e.getMessage(),
                e.getLocation(),
                "PARSE_LEX_003"
            );
        } catch (ParseException e) {
            log.error("Parse error while parsing action: {}", e.getDetailedMessage());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error while parsing action", e);
            throw new ParseException(
                "Unexpected parsing error: " + e.getMessage(),
                null,
                "PARSE_UNEXPECTED"
            );
        }
    }
    
    /**
     * Parse a list of actions from source text
     */
    public List<Action> parseActionList(String source) {
        log.debug("Parsing action list: {}", source);
        
        try {
            List<Token> tokens = tokenize(source);
            ActionParser parser = new ActionParser(tokens);
            List<Action> actions = parser.parseActionList();
            
            log.debug("Successfully parsed {} actions", actions.size());
            return actions;
            
        } catch (LexerException e) {
            log.error("Lexical error while parsing action list: {}", e.getDetailedMessage());
            throw new ParseException(
                "Lexical error: " + e.getMessage(),
                e.getLocation(),
                "PARSE_LEX_004"
            );
        } catch (ParseException e) {
            log.error("Parse error while parsing action list: {}", e.getDetailedMessage());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error while parsing action list", e);
            throw new ParseException(
                "Unexpected parsing error: " + e.getMessage(),
                null,
                "PARSE_UNEXPECTED"
            );
        }
    }
    
    /**
     * Tokenize source text into tokens
     */
    private List<Token> tokenize(String source) {
        if (source == null || source.trim().isEmpty()) {
            throw new ParseException(
                "Source text cannot be null or empty",
                null,
                "PARSE_EMPTY_SOURCE"
            );
        }
        
        Lexer lexer = new Lexer(source);
        return lexer.tokenize();
    }
    
}

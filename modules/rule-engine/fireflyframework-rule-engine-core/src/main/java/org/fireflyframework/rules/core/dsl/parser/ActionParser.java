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

import org.fireflyframework.rules.core.dsl.action.*;
import org.fireflyframework.rules.core.dsl.condition.Condition;
import org.fireflyframework.rules.core.dsl.expression.Expression;
import org.fireflyframework.rules.core.dsl.expression.VariableExpression;
import org.fireflyframework.rules.core.dsl.lexer.Token;
import org.fireflyframework.rules.core.dsl.lexer.TokenType;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * Parser for action statements.
 * Handles various action types like assignments, function calls, and conditional actions.
 */
@Slf4j
public class ActionParser extends BaseParser {
    
    private final ExpressionParser expressionParser;
    private final ConditionParser conditionParser;
    
    public ActionParser(List<Token> tokens) {
        super(tokens);
        this.expressionParser = new ExpressionParser(tokens);
        this.conditionParser = new ConditionParser(tokens);
    }
    
    /**
     * Parse an action statement
     *
     * Action grammar:
     * action         → setAction | calculateAction | runAction | callAction | conditionalAction
     * setAction      → "set" IDENTIFIER "to" expression
     * calculateAction → "calculate" IDENTIFIER "as" expression
     * runAction      → "run" IDENTIFIER "as" expression
     * callAction     → "call" IDENTIFIER "with" "[" expressionList? "]"
     * conditionalAction → "if" condition "then" actionList ( "else" actionList )?
     * actionList     → action ( "," action )*
     * expressionList → expression ( "," expression )*
     */
    public Action parseAction() {
        if (match(TokenType.SET)) {
            return parseSetAction();
        }

        if (match(TokenType.CALCULATE)) {
            return parseCalculateAction();
        }

        if (match(TokenType.RUN)) {
            return parseRunAction();
        }

        if (match(TokenType.CALL)) {
            return parseCallAction();
        }

        if (match(TokenType.IF)) {
            return parseConditionalAction();
        }

        // Arithmetic actions
        if (match(TokenType.ADD)) {
            return parseArithmeticAction(ArithmeticAction.ArithmeticOperationType.ADD);
        }

        if (match(TokenType.SUBTRACT)) {
            return parseArithmeticAction(ArithmeticAction.ArithmeticOperationType.SUBTRACT);
        }

        // Check for multiply/divide keywords using identifier matching
        if (check(TokenType.IDENTIFIER)) {
            String keyword = peek().getLexeme();
            if ("multiply".equals(keyword)) {
                advance();
                return parseArithmeticAction(ArithmeticAction.ArithmeticOperationType.MULTIPLY);
            }
            if ("divide".equals(keyword)) {
                advance();
                return parseArithmeticAction(ArithmeticAction.ArithmeticOperationType.DIVIDE);
            }
        }

        // List actions
        if (match(TokenType.APPEND)) {
            return parseListAction(ListAction.ListOperationType.APPEND);
        }

        if (match(TokenType.PREPEND)) {
            return parseListAction(ListAction.ListOperationType.PREPEND);
        }

        if (match(TokenType.REMOVE)) {
            return parseListAction(ListAction.ListOperationType.REMOVE);
        }

        // Circuit breaker action
        if (match(TokenType.CIRCUIT_BREAKER)) {
            return parseCircuitBreakerAction();
        }

        // Loop actions
        if (match(TokenType.FOREACH) || match(TokenType.FOR)) {
            return parseForEachAction();
        }

        if (match(TokenType.WHILE)) {
            return parseWhileAction();
        }

        if (match(TokenType.DO)) {
            return parseDoWhileAction();
        }

        throw error(
            "Expected action statement",
            "PARSE_002",
            List.of("Use 'set', 'calculate', 'run', 'call', 'if', 'forEach', 'while', 'do', arithmetic operations, list operations, or 'circuit_breaker' to start an action")
        );
    }
    
    /**
     * Parse a list of actions
     */
    public List<Action> parseActionList() {
        List<Action> actions = new ArrayList<>();
        
        if (!isAtEnd() && !checkAny(TokenType.ELSE, TokenType.EOF)) {
            do {
                actions.add(parseAction());
            } while (match(TokenType.COMMA) && !isAtEnd());
        }
        
        return actions;
    }
    
    /**
     * Parse set action: "set variable to expression"
     */
    private Action parseSetAction() {
        Token setToken = previous();
        
        if (!check(TokenType.IDENTIFIER)) {
            throw error(
                "Expected variable name after 'set'",
                "PARSE_003",
                List.of("Add a variable name after 'set'")
            );
        }
        
        Token variable = advance();
        
        consume(TokenType.TO, "Expected 'to' after variable name in set action");
        
        // Parse the value expression
        this.expressionParser.setCurrentPosition(this.current);
        Expression value = this.expressionParser.parseExpression();
        this.current = this.expressionParser.getCurrentPosition();
        
        return new SetAction(setToken.getLocation(), variable.getLexeme(), value);
    }
    
    /**
     * Parse calculate action: "calculate variable as expression"
     */
    private Action parseCalculateAction() {
        Token calculateToken = previous();

        if (!check(TokenType.IDENTIFIER)) {
            throw error(
                "Expected variable name after 'calculate'",
                "PARSE_004",
                List.of("Add a variable name after 'calculate'")
            );
        }

        Token variable = advance();

        consume(TokenType.AS, "Expected 'as' after variable name in calculate action");

        // Parse the expression
        this.expressionParser.setCurrentPosition(this.current);
        Expression expression = this.expressionParser.parseExpression();
        this.current = this.expressionParser.getCurrentPosition();

        return new CalculateAction(calculateToken.getLocation(), variable.getLexeme(), expression);
    }

    /**
     * Parse run action: "run variable as expression"
     */
    private Action parseRunAction() {
        Token runToken = previous();

        if (!check(TokenType.IDENTIFIER)) {
            throw error(
                "Expected variable name after 'run'",
                "PARSE_006",
                List.of("Add a variable name after 'run'")
            );
        }

        Token variable = advance();

        consume(TokenType.AS, "Expected 'as' after variable name in run action");

        // Parse the expression
        this.expressionParser.setCurrentPosition(this.current);
        Expression expression = this.expressionParser.parseExpression();
        this.current = this.expressionParser.getCurrentPosition();

        return new RunAction(runToken.getLocation(), variable.getLexeme(), expression);
    }

    /**
     * Parse call action: "call function with [arguments]"
     */
    private Action parseCallAction() {
        Token callToken = previous();
        
        if (!check(TokenType.IDENTIFIER)) {
            throw error(
                "Expected function name after 'call'",
                "PARSE_005",
                List.of("Add a function name after 'call'")
            );
        }
        
        Token functionName = advance();
        
        consume(TokenType.WITH, "Expected 'with' after function name in call action");
        consume(TokenType.LBRACKET, "Expected '[' after 'with' in call action");
        
        // Parse arguments
        List<Expression> arguments = new ArrayList<>();
        if (!check(TokenType.RBRACKET)) {
            do {
                this.expressionParser.setCurrentPosition(this.current);
                arguments.add(this.expressionParser.parseExpression());
                this.current = this.expressionParser.getCurrentPosition();
            } while (match(TokenType.COMMA));
        }
        
        consume(TokenType.RBRACKET, "Expected ']' after function arguments");
        
        return new FunctionCallAction(callToken.getLocation(), functionName.getLexeme(), arguments);
    }
    
    /**
     * Parse conditional action: "if condition then actions else actions"
     */
    private Action parseConditionalAction() {
        Token ifToken = previous();
        
        // Parse condition
        this.conditionParser.setCurrentPosition(this.current);
        Condition condition = this.conditionParser.parseCondition();
        this.current = this.conditionParser.getCurrentPosition();
        
        consume(TokenType.THEN, "Expected 'then' after condition in if statement");
        
        // Parse then actions
        List<Action> thenActions = parseActionList();
        
        // Parse optional else actions
        List<Action> elseActions = null;
        if (match(TokenType.ELSE)) {
            elseActions = parseActionList();
        }
        
        return new ConditionalAction(ifToken.getLocation(), condition, thenActions, elseActions);
    }

    /**
     * Parse an arithmetic action.
     *
     * <p>{@code add} and {@code subtract} use English-natural order:
     * <pre>add VALUE to TARGET</pre>
     * <pre>subtract VALUE from TARGET</pre>
     *
     * <p>{@code multiply} and {@code divide} accept <em>both</em> orderings -- the parser
     * disambiguates by which operand is a bare identifier (the target must be a writable
     * variable name):
     * <pre>multiply 1.5 by risk_factor   -- value then target (canonical)</pre>
     * <pre>multiply risk_factor by 1.5   -- target then value (English-natural)</pre>
     * Both produce the same {@code ArithmeticAction(MULTIPLY, "risk_factor", LiteralExpression(1.5))}.
     */
    private Action parseArithmeticAction(ArithmeticAction.ArithmeticOperationType operation) {
        Token operationToken = previous();

        // Parse the first expression after the action keyword.
        this.expressionParser.setCurrentPosition(this.current);
        Expression first = this.expressionParser.parseExpression();
        this.current = this.expressionParser.getCurrentPosition();

        String expectedPreposition = operation.getPreposition();
        if (!check(TokenType.TO) && !check(TokenType.FROM) && !check(TokenType.BY)) {
            throw error(
                "Expected '" + expectedPreposition + "' after value in " + operation.getKeyword() + " action",
                "PARSE_006",
                List.of("Add '" + expectedPreposition + "' after the value")
            );
        }
        advance(); // consume the preposition

        // Two patterns are accepted:
        //   1) VALUE <prep> IDENTIFIER  -- current canonical order, used by add/subtract/multiply/divide
        //   2) IDENTIFIER <prep> VALUE  -- English-natural order for multiply/divide
        // If the next token is a bare IDENTIFIER, fall into pattern 1.
        // Otherwise (number, paren, etc.) and the first expression was itself a bare variable,
        // fall into pattern 2.
        if (check(TokenType.IDENTIFIER)) {
            Token variable = advance();
            return new ArithmeticAction(operationToken.getLocation(), operation, variable.getLexeme(), first);
        }

        boolean swappedAllowed = operation == ArithmeticAction.ArithmeticOperationType.MULTIPLY
                || operation == ArithmeticAction.ArithmeticOperationType.DIVIDE;
        if (swappedAllowed && first instanceof VariableExpression firstVar && firstVar.isSimpleVariable()) {
            // Pattern 2: first was the target, what follows the preposition is the value expression.
            this.expressionParser.setCurrentPosition(this.current);
            Expression valueExpr = this.expressionParser.parseExpression();
            this.current = this.expressionParser.getCurrentPosition();
            return new ArithmeticAction(operationToken.getLocation(), operation, firstVar.getVariableName(), valueExpr);
        }

        throw error(
            "Expected variable name after '" + expectedPreposition + "'",
            "PARSE_007",
            List.of("Add a variable name after '" + expectedPreposition + "'")
        );
    }

    /**
     * Parse list action: "append value to list", "prepend value to list", "remove value from list"
     */
    private Action parseListAction(ListAction.ListOperationType operation) {
        Token operationToken = previous();

        // Parse the value expression
        this.expressionParser.setCurrentPosition(this.current);
        Expression value = this.expressionParser.parseExpression();
        this.current = this.expressionParser.getCurrentPosition();

        // Expect the preposition (to/from)
        String expectedPreposition = operation.getPreposition();
        if (!check(TokenType.TO) && !check(TokenType.FROM)) {
            throw error(
                "Expected '" + expectedPreposition + "' after value in " + operation.getKeyword() + " action",
                "PARSE_008",
                List.of("Add '" + expectedPreposition + "' after the value")
            );
        }

        advance(); // consume the preposition

        if (!check(TokenType.IDENTIFIER)) {
            throw error(
                "Expected list variable name after '" + expectedPreposition + "'",
                "PARSE_009",
                List.of("Add a list variable name after '" + expectedPreposition + "'")
            );
        }

        Token listVariable = advance();

        return new ListAction(operationToken.getLocation(), operation, value, listVariable.getLexeme());
    }

    /**
     * Parse circuit breaker action: "circuit_breaker message"
     */
    private Action parseCircuitBreakerAction() {
        Token circuitBreakerToken = previous();

        if (!check(TokenType.STRING)) {
            throw error(
                "Expected message string after 'circuit_breaker'",
                "PARSE_010",
                List.of("Add a message string after 'circuit_breaker'")
            );
        }

        Token messageToken = advance();
        String message = messageToken.getLexeme();

        // Remove quotes from string literal
        if (message.startsWith("\"") && message.endsWith("\"")) {
            message = message.substring(1, message.length() - 1);
        }

        return new CircuitBreakerAction(circuitBreakerToken.getLocation(), message);
    }

    /**
     * Parse forEach action: "forEach item in items: action" or "forEach item, index in items: action"
     */
    private Action parseForEachAction() {
        Token forEachToken = previous();

        // Parse iteration variable
        if (!check(TokenType.IDENTIFIER)) {
            throw error(
                "Expected iteration variable name after 'forEach'",
                "PARSE_011",
                List.of("Add a variable name after 'forEach', e.g., 'forEach item in items'")
            );
        }

        Token iterationVarToken = advance();
        String iterationVariable = iterationVarToken.getLexeme();

        // Check for optional index variable (forEach item, index in items)
        String indexVariable = null;
        if (match(TokenType.COMMA)) {
            if (!check(TokenType.IDENTIFIER)) {
                throw error(
                    "Expected index variable name after comma",
                    "PARSE_012",
                    List.of("Add an index variable name after comma, e.g., 'forEach item, index in items'")
                );
            }
            Token indexVarToken = advance();
            indexVariable = indexVarToken.getLexeme();
        }

        // Expect 'in' keyword
        if (!match(TokenType.IN)) {
            throw error(
                "Expected 'in' after iteration variable",
                "PARSE_013",
                List.of("Add 'in' keyword, e.g., 'forEach item in items'")
            );
        }

        // Parse the list expression
        this.expressionParser.setCurrentPosition(this.current);
        Expression listExpression = this.expressionParser.parseExpression();
        this.current = this.expressionParser.getCurrentPosition();

        // Expect ':' or 'do' before body actions
        boolean hasColon = match(TokenType.COLON);
        boolean hasDo = match(TokenType.DO);

        if (!hasColon && !hasDo) {
            throw error(
                "Expected ':' or 'do' before forEach body",
                "PARSE_014",
                List.of("Add ':' or 'do' before the forEach actions, e.g., 'forEach item in items: set total to total + item'")
            );
        }

        // Parse body actions (can be multiple actions separated by semicolons)
        List<Action> bodyActions = new java.util.ArrayList<>();

        // For simple syntax with colon, parse actions until end of line or semicolon
        do {
            // Skip any leading semicolons
            while (match(TokenType.SEMICOLON)) {
                // Skip
            }

            if (isAtEnd()) {
                break;
            }

            // Save position to check if we made progress
            int savedPosition = current;

            // Try to parse an action
            try {
                Action bodyAction = parseAction();
                bodyActions.add(bodyAction);
            } catch (Exception e) {
                // If we couldn't parse an action and haven't made progress, break
                if (current == savedPosition) {
                    break;
                }
                throw e;
            }

            // Check for semicolon separator
            if (!match(TokenType.SEMICOLON)) {
                break;
            }
        } while (!isAtEnd());

        if (bodyActions.isEmpty()) {
            throw error(
                "forEach body cannot be empty",
                "PARSE_015",
                List.of("Add at least one action in the forEach body")
            );
        }

        if (indexVariable != null) {
            return new ForEachAction(forEachToken.getLocation(), iterationVariable, indexVariable, listExpression, bodyActions);
        } else {
            return new ForEachAction(forEachToken.getLocation(), iterationVariable, listExpression, bodyActions);
        }
    }

    /**
     * Parse a while loop action
     * Syntax: while condition: action
     *         while condition: action1; action2
     */
    private Action parseWhileAction() {
        Token whileToken = previous();

        // Parse the condition - synchronize parser positions
        this.conditionParser.setCurrentPosition(this.current);
        Condition condition = conditionParser.parseCondition();
        this.current = this.conditionParser.getCurrentPosition();

        // Expect colon
        if (!match(TokenType.COLON)) {
            throw error(
                "Expected ':' after while condition",
                "PARSE_016",
                List.of("Add ':' after the while condition", "Example: while counter < 10: add 1 to counter")
            );
        }

        // Parse body actions (can be multiple separated by semicolons)
        List<Action> bodyActions = new ArrayList<>();
        do {
            try {
                Action action = parseAction();
                bodyActions.add(action);
            } catch (Exception e) {
                // If we can't parse an action, check if we're at a natural break point
                if (isAtEnd() || check(TokenType.NEWLINE)) {
                    break;
                }
                throw e;
            }

            // Check for semicolon separator
            if (!match(TokenType.SEMICOLON)) {
                break;
            }
        } while (!isAtEnd());

        if (bodyActions.isEmpty()) {
            throw error(
                "while body cannot be empty",
                "PARSE_017",
                List.of("Add at least one action in the while body")
            );
        }

        return new WhileAction(whileToken.getLocation(), condition, bodyActions);
    }

    /**
     * Parse a do-while loop action
     * Syntax: do: action while condition
     *         do: action1; action2 while condition
     */
    private Action parseDoWhileAction() {
        Token doToken = previous();

        // Expect colon
        if (!match(TokenType.COLON)) {
            throw error(
                "Expected ':' after 'do'",
                "PARSE_018",
                List.of("Add ':' after 'do'", "Example: do: add 1 to counter while counter < 10")
            );
        }

        // Parse body actions (can be multiple separated by semicolons)
        List<Action> bodyActions = new ArrayList<>();
        do {
            try {
                Action action = parseAction();
                bodyActions.add(action);
            } catch (Exception e) {
                // Check if we hit the 'while' keyword
                if (check(TokenType.WHILE)) {
                    break;
                }
                throw e;
            }

            // Check for semicolon separator or while keyword
            if (check(TokenType.WHILE)) {
                break;
            }
            if (!match(TokenType.SEMICOLON)) {
                break;
            }
        } while (!isAtEnd());

        if (bodyActions.isEmpty()) {
            throw error(
                "do-while body cannot be empty",
                "PARSE_019",
                List.of("Add at least one action in the do-while body")
            );
        }

        // Expect 'while' keyword
        if (!match(TokenType.WHILE)) {
            throw error(
                "Expected 'while' after do-while body",
                "PARSE_020",
                List.of("Add 'while' keyword after the do-while body", "Example: do: add 1 to counter while counter < 10")
            );
        }

        // Parse the condition - synchronize parser positions
        this.conditionParser.setCurrentPosition(this.current);
        Condition condition = conditionParser.parseCondition();
        this.current = this.conditionParser.getCurrentPosition();

        return new DoWhileAction(doToken.getLocation(), bodyActions, condition);
    }
}

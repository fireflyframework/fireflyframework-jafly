package org.fireflyframework.rules.core.dsl;

import org.fireflyframework.rules.core.dsl.condition.Condition;
import org.fireflyframework.rules.core.dsl.evaluation.ASTRulesEvaluationEngine;
import org.fireflyframework.rules.core.dsl.evaluation.ASTRulesEvaluationResult;
import org.fireflyframework.rules.core.dsl.lexer.Lexer;
import org.fireflyframework.rules.core.dsl.lexer.Token;
import org.fireflyframework.rules.core.dsl.parser.ASTRulesDSLParser;
import org.fireflyframework.rules.core.dsl.parser.DSLParser;
import org.fireflyframework.rules.core.dsl.visitor.EvaluationContext;
import org.fireflyframework.rules.core.dsl.visitor.ExpressionEvaluator;
import org.fireflyframework.rules.core.services.ConstantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import reactor.core.publisher.Flux;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LogicalOperatorDebugTest {

    private ASTRulesEvaluationEngine evaluationEngine;

    @Mock
    private ConstantService constantService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        // Mock empty constants
        org.mockito.Mockito.when(constantService.getConstantsByCodes(org.mockito.Mockito.anyList()))
                .thenReturn(Flux.empty());

        // Create evaluation engine with mocked dependencies
        DSLParser dslParser = new DSLParser();
        ASTRulesDSLParser astParser = new ASTRulesDSLParser(dslParser);
        evaluationEngine = new ASTRulesEvaluationEngine(astParser, constantService, null, null);
    }

    @Test
    void testSimpleOrOperator() {
        String rule = """
            name: "Simple OR Test"
            description: "Test simple OR operator"
            inputs:
              - a
              - b
            when:
              - a equals 1 OR b equals 2
            then:
              - set result to true
            else:
              - set result to false
            output:
              result: boolean
            """;

        Map<String, Object> inputData = Map.of(
            "a", 0,  // false condition
            "b", 2   // true condition
        );

        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(rule, inputData);

        assertTrue(result.isSuccess());
        System.out.println("Result: " + result.getOutputData().get("result"));
        assertEquals(true, result.getOutputData().get("result"));
    }

    @Test
    void testDebugConditionParsing() {
        // Test tokenization first
        String conditionString = "a equals 1 OR b equals 2";
        System.out.println("Tokenizing: " + conditionString);

        try {
            Lexer lexer = new Lexer(conditionString);
            java.util.List<Token> tokens = lexer.tokenize();
            System.out.println("Tokens:");
            for (Token token : tokens) {
                System.out.println("  " + token.getType() + ": '" + token.getLexeme() + "'");
            }
        } catch (Exception e) {
            System.out.println("Tokenization error: " + e.getMessage());
            e.printStackTrace();
        }

        // Test just the condition parsing directly
        DSLParser dslParser = new DSLParser();

        System.out.println("Parsing condition: " + conditionString);

        Condition condition = dslParser.parseCondition(conditionString);
        System.out.println("Parsed condition type: " + condition.getClass().getSimpleName());
        System.out.println("Condition debug string: " + condition.toDebugString());

        // Test evaluation
        EvaluationContext testContext =
            new EvaluationContext("test-op");
        testContext.setVariable("a", 0);
        testContext.setVariable("b", 2);

        ExpressionEvaluator evaluator =
            new ExpressionEvaluator(testContext);

        Object result = condition.accept(evaluator);
        System.out.println("Condition evaluation result: " + result);

        assertTrue(result instanceof Boolean);
        assertEquals(true, result);
    }

    @Test
    void testSimpleAndOperator() {
        String rule = """
            name: "Simple AND Test"
            description: "Test simple AND operator"
            inputs:
              - a
              - b
            when:
              - a equals 1 AND b equals 2
            then:
              - set result to true
            else:
              - set result to false
            output:
              result: boolean
            """;

        Map<String, Object> inputData = Map.of(
            "a", 1,  // true condition
            "b", 2   // true condition
        );
        
        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(rule, inputData);
        
        assertTrue(result.isSuccess());
        assertEquals(true, result.getOutputData().get("result"));
    }

    @Test
    @DisplayName("Debug between operator")
    void testBetweenOperator() {
        // First test tokenization
        String conditionString = "age between 18 and 65";
        System.out.println("Tokenizing: " + conditionString);

        try {
            Lexer lexer = new Lexer(conditionString);
            java.util.List<Token> tokens = lexer.tokenize();
            System.out.println("Tokens:");
            for (Token token : tokens) {
                System.out.println("  " + token.getType() + ": '" + token.getLexeme() + "'");
            }
        } catch (Exception e) {
            System.out.println("Tokenization error: " + e.getMessage());
            e.printStackTrace();
        }

        String yamlRule = """
            when:
              - age between 18 and 65
            then:
              - set result to "in_range"
            else:
              - set result to "out_of_range"
            """;

        Map<String, Object> inputData = Map.of("age", 30);

        System.out.println("Testing between operator with age=30");
        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yamlRule, inputData);

        assertTrue(result.isSuccess());
        System.out.println("Result: " + result.getOutputData().get("result"));
        assertEquals("in_range", result.getOutputData().get("result"));
    }

    @Test
    @DisplayName("Debug matches operator")
    void testMatchesOperator() {
        // First test tokenization
        String conditionString = "phoneNumber matches \"^\\\\+1\"";
        System.out.println("Tokenizing: " + conditionString);

        try {
            Lexer lexer = new Lexer(conditionString);
            java.util.List<Token> tokens = lexer.tokenize();
            System.out.println("Tokens:");
            for (Token token : tokens) {
                System.out.println("  " + token.getType() + ": '" + token.getLexeme() + "'");
            }
        } catch (Exception e) {
            System.out.println("Tokenization error: " + e.getMessage());
            e.printStackTrace();
        }

        String yamlRule = """
            when:
              - phoneNumber matches "^\\\\+1"
            then:
              - set result to "us_number"
            else:
              - set result to "not_us_number"
            """;

        Map<String, Object> inputData = Map.of("phoneNumber", "+1234567890");

        System.out.println("Testing matches operator with phoneNumber=+1234567890 and regex=^\\\\+1");
        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yamlRule, inputData);

        assertTrue(result.isSuccess());
        System.out.println("Result: " + result.getOutputData().get("result"));
        assertEquals("us_number", result.getOutputData().get("result"));
    }
}

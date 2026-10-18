package org.fireflyframework.rules.core.dsl;

import org.fireflyframework.rules.core.dsl.action.Action;
import org.fireflyframework.rules.core.dsl.action.ForEachAction;
import org.fireflyframework.rules.core.dsl.evaluation.ASTRulesEvaluationEngine;
import org.fireflyframework.rules.core.dsl.evaluation.ASTRulesEvaluationResult;
import org.fireflyframework.rules.core.dsl.parser.ASTRulesDSLParser;
import org.fireflyframework.rules.core.dsl.parser.DSLParser;
import org.fireflyframework.rules.core.services.ConstantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
public class ForEachSimpleTest {

    private ASTRulesEvaluationEngine evaluationEngine;

    @Mock
    private ConstantService constantService;

    @BeforeEach
    void setUp() {
        Mockito.lenient().when(constantService.getConstantsByCodes(Mockito.anyList()))
               .thenReturn(Flux.empty());

        DSLParser dslParser = new DSLParser();
        ASTRulesDSLParser parser = new ASTRulesDSLParser(dslParser);
        evaluationEngine = new ASTRulesEvaluationEngine(parser, constantService);
    }

    @Test
    void testSimpleForEach() {
        String yaml = """
            name: Simple forEach Test
            inputs:
              - numbers
            when:
              - "true"
            then:
              - set total to 0
              - forEach num in numbers: calculate total as total + num
            output:
              total: number
            """;

        Map<String, Object> inputData = Map.of(
            "numbers", List.of(10, 20, 30)
        );

        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);

        if (!result.isSuccess()) {
            System.out.println("Error: " + result.getError());
        }

        assertTrue(result.isSuccess(), "Expected success but got: " + result.getError());
        Object totalValue = result.getOutputData().get("total");
        System.out.println("Result total: " + totalValue + " (type: " + totalValue.getClass().getName() + ")");
        // Compare numeric value regardless of type (BigDecimal vs Double)
        assertEquals(60.0, ((Number) totalValue).doubleValue(), 0.001);
    }

    @Test
    void testForEachWithSetAction() {
        String yaml = """
            name: forEach with Set Action
            inputs:
              - items
            when:
              - "true"
            then:
              - set count to 0
              - forEach item in items: set count to 1
            output:
              count: number
            """;

        Map<String, Object> inputData = Map.of(
            "items", List.of("a", "b", "c")
        );

        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);

        assertTrue(result.isSuccess(), "Expected success but got: " + result.getError());
        Object countValue = result.getOutputData().get("count");
        System.out.println("Result count: " + countValue + " (type: " + countValue.getClass().getName() + ")");
        // Should be 1 because we set it to 1 in each iteration
        assertEquals(1.0, ((Number) countValue).doubleValue(), 0.001);
    }
}


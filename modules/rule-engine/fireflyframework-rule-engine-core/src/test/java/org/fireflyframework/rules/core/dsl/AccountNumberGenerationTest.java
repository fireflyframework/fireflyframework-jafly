package org.fireflyframework.rules.core.dsl;

import org.fireflyframework.rules.core.dsl.expression.FunctionCallExpressionImpl;
import org.fireflyframework.rules.core.dsl.visitor.EvaluationContext;
import org.fireflyframework.rules.core.dsl.visitor.ExpressionEvaluatorImpl;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.when;

class AccountNumberGenerationTest {
    @Test
    void usesCryptographicRandomnessAndPreservesLeadingZeroes() {
        try (var generators = mockConstruction(SecureRandom.class,
                (generator, context) -> when(generator.nextInt(10))
                        .thenReturn(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 0, 1))) {
            var result = accountNumber();

            assertEquals(1, generators.constructed().size());
            assertEquals("012345678901", result);
        }
    }

    @Test
    void realGeneratorRetainsTheTwelveDigitStringContract() {
        for (int index = 0; index < 64; index++) {
            var result = assertInstanceOf(String.class, accountNumber());
            assertTrue(result.matches("[0-9]{12}"));
        }
    }

    private static Object accountNumber() {
        var evaluator = new ExpressionEvaluatorImpl(new EvaluationContext());
        return new FunctionCallExpressionImpl(null, "generate_account_number", List.of()).accept(evaluator);
    }
}

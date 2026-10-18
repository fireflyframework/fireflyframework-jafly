package org.fireflyframework.rules.core.dsl;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.fireflyframework.rules.core.dsl.evaluation.ASTRulesEvaluationEngine;
import org.fireflyframework.rules.core.dsl.evaluation.ASTRulesEvaluationResult;
import org.fireflyframework.rules.core.dsl.parser.ASTRulesDSLParser;
import org.fireflyframework.rules.core.dsl.parser.DSLParser;
import org.fireflyframework.rules.core.services.ConstantService;
import org.fireflyframework.rules.interfaces.dtos.crud.ConstantDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Comprehensive integration test for the Constant Service with the Rule Engine.
 * Tests constant resolution, logging, and rule evaluation with database-backed constants.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Constant Service Integration Tests")
class ConstantServiceIntegrationTest {

    @Mock
    private ConstantService constantService;

    private ASTRulesEvaluationEngine evaluationEngine;
    private ListAppender<ILoggingEvent> logAppender;
    private ch.qos.logback.classic.Logger rootLogger;

    @BeforeEach
    void setUp() {
        // Create dependencies
        DSLParser dslParser = new DSLParser();
        ASTRulesDSLParser parser = new ASTRulesDSLParser(dslParser);
        evaluationEngine = new ASTRulesEvaluationEngine(parser, constantService, null, null);

        // Set up log capture
        rootLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        logAppender = new ListAppender<>();
        logAppender.start();
        rootLogger.addAppender(logAppender);
        rootLogger.setLevel(Level.DEBUG);
    }

    @Nested
    @DisplayName("Database Constant Resolution Tests")
    class DatabaseConstantResolutionTests {

        @Test
        @DisplayName("Should resolve constants from database and log successful evaluation")
        void testDatabaseConstantResolution() {
            // Arrange - Mock database constants (these are automatically loaded by the engine)
            ConstantDTO minCreditScore = createConstantDTO("MIN_CREDIT_SCORE", new BigDecimal("700"));
            ConstantDTO maxLoanAmount = createConstantDTO("MAX_LOAN_AMOUNT", new BigDecimal("1000000"));
            ConstantDTO baseInterestRate = createConstantDTO("BASE_INTEREST_RATE", new BigDecimal("3.5"));

            when(constantService.getConstantsByCodes(anyList()))
                    .thenReturn(Flux.just(minCreditScore, maxLoanAmount, baseInterestRate));

            String yamlRule = """
                name: "Loan Approval with Database Constants"
                description: "Demonstrates proper constant usage according to DSL specification"
                version: "1.0"
                
                inputs:
                  - creditScore        # camelCase: from API request
                  - requestedAmount    # camelCase: from API request
                  - riskAdjustment     # camelCase: from API request
                
                when:
                  - creditScore at_least MIN_CREDIT_SCORE      # UPPER_CASE: database constant
                  - requestedAmount less_than MAX_LOAN_AMOUNT  # UPPER_CASE: database constant
                then:
                  - set decision to "APPROVED"                 # snake_case: computed variable
                  - calculate final_rate as BASE_INTEREST_RATE + riskAdjustment  # Using constants in calculations
                  - set message to "Loan approved with database constants"
                else:
                  - set decision to "DECLINED"                 # snake_case: computed variable
                  - set message to "Loan declined - criteria not met"
                
                output:
                  decision: text
                  final_rate: number
                  message: text
                """;

            Map<String, Object> inputData = new HashMap<>();
            inputData.put("creditScore", 750);
            inputData.put("requestedAmount", new BigDecimal("800000"));
            inputData.put("riskAdjustment", new BigDecimal("0.5"));

            // Act
            ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yamlRule, inputData);

            // Assert - Verify result
            assertTrue(result.isSuccess(), "Rule evaluation should succeed");
            assertTrue(result.isConditionMet(), "Conditions should be met");
            assertEquals("APPROVED", result.getOutputValue("decision"));
            assertEquals("Loan approved with database constants", result.getOutputValue("message"));
            assertEquals(new BigDecimal("4.0"), result.getOutputValue("final_rate"));

            // Assert - Verify constant service was called with the constants referenced in the rule
            verify(constantService).getConstantsByCodes(
                    argThat(codes -> codes.containsAll(List.of("MIN_CREDIT_SCORE", "MAX_LOAN_AMOUNT", "BASE_INTEREST_RATE")))
            );

            // Assert - Verify comprehensive logging
            List<ILoggingEvent> logEvents = logAppender.list;
            assertFalse(logEvents.isEmpty(), "Should have log events");

            // Check for rule evaluation start log with rule name
            assertTrue(logEvents.stream().anyMatch(event -> 
                event.getFormattedMessage().contains("Starting AST-based rule evaluation") &&
                event.getFormattedMessage().contains("Loan Approval with Database Constants")
            ), "Should log rule evaluation start with rule name");

            // Check for successful completion log
            assertTrue(logEvents.stream().anyMatch(event -> 
                event.getFormattedMessage().contains("AST-based rule evaluation completed successfully")
            ), "Should log successful completion");

            // Verify execution time is tracked
            assertTrue(result.getExecutionTimeMs() >= 0, "Should track execution time");
        }
    }

    @Nested
    @DisplayName("Constant Service Error Handling Tests")
    class ConstantServiceErrorHandlingTests {

        @Test
        @DisplayName("Should handle database errors gracefully and log appropriate errors")
        void testDatabaseErrorHandling() {
            // Arrange - Mock database error
            when(constantService.getConstantsByCodes(anyList()))
                    .thenReturn(Flux.error(new RuntimeException("Database connection failed")));

            String yamlRule = """
                name: "Loan Approval with Database Error"
                description: "Tests error handling when constant service fails"
                
                inputs:
                  - creditScore        # camelCase: from API request
                  - requestedAmount    # camelCase: from API request
                
                when:
                  - creditScore at_least MIN_CREDIT_SCORE      # UPPER_CASE: database constant
                  - requestedAmount less_than MAX_LOAN_AMOUNT  # UPPER_CASE: database constant
                then:
                  - set decision to "APPROVED"                 # snake_case: computed variable
                  - set source to "database_constants"
                else:
                  - set decision to "DECLINED"                 # snake_case: computed variable
                  - set source to "error_fallback"
                
                output:
                  decision: text
                  source: text
                """;

            Map<String, Object> inputData = new HashMap<>();
            inputData.put("creditScore", 700);
            inputData.put("requestedAmount", new BigDecimal("300000"));

            // Act
            ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yamlRule, inputData);

            // Assert - Verify error handling behavior
            // The engine should handle the database error gracefully
            assertNotNull(result, "Should return a result even when database fails");
            
            // Assert - Verify constant service was called
            verify(constantService).getConstantsByCodes(anyList());

            // Assert - Verify error logging
            List<ILoggingEvent> logEvents = logAppender.list;
            
            // Should log the rule evaluation start
            assertTrue(logEvents.stream().anyMatch(event -> 
                event.getFormattedMessage().contains("Starting AST-based rule evaluation")
            ), "Should log rule evaluation start");

            // Should log error information about database failure
            boolean hasErrorLog = logEvents.stream().anyMatch(event -> 
                event.getLevel() == Level.ERROR &&
                (event.getFormattedMessage().toLowerCase().contains("database") ||
                 event.getFormattedMessage().toLowerCase().contains("constant") ||
                 event.getFormattedMessage().toLowerCase().contains("failed"))
            );
            
            assertTrue(hasErrorLog, "Should log error information about database failure");
        }
    }

    /**
     * Helper method to create ConstantDTO objects for testing
     */
    private ConstantDTO createConstantDTO(String code, Object value) {
        ConstantDTO dto = new ConstantDTO();
        dto.setId(UUID.randomUUID());
        dto.setCode(code);
        dto.setCurrentValue(value);
        dto.setDescription("Test constant: " + code);
        dto.setName("Test " + code);
        dto.setValueType(org.fireflyframework.rules.interfaces.enums.ValueType.NUMBER); // Default to NUMBER
        dto.setRequired(false);
        return dto;
    }
}

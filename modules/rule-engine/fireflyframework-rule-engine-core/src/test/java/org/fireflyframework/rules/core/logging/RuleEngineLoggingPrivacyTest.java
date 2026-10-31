/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package org.fireflyframework.rules.core.logging;

import org.fireflyframework.rules.core.audit.AuditHelper;
import org.fireflyframework.rules.core.dsl.evaluation.*;
import org.fireflyframework.rules.core.dsl.lexer.Lexer;
import org.fireflyframework.rules.core.dsl.parser.*;
import org.fireflyframework.rules.core.dsl.exception.ASTException;
import org.fireflyframework.rules.core.mappers.RuleDefinitionMapper;
import org.fireflyframework.rules.core.services.*;
import org.fireflyframework.rules.core.services.impl.*;
import org.fireflyframework.rules.core.validation.YamlDslValidator;
import org.fireflyframework.rules.interfaces.dtos.audit.AuditTrailFilterDTO;
import org.fireflyframework.rules.interfaces.dtos.evaluation.*;
import org.fireflyframework.rules.models.repositories.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RuleEngineLoggingPrivacyTest {
    private static final String SECRET = "PRIVATE_TEST\r\nforged\u2028";

    @Test void lexerKeepsTokensWithoutLoggingSource() {
        try (var logs = new Capture(Lexer.class)) {
            var tokens = new Lexer("PRIVATE_TEST").tokenize();
            assertTrue(tokens.stream().anyMatch(token -> "PRIVATE_TEST".equals(token.getLexeme())));
            logs.safe(2);
        }
    }

    @ParameterizedTest @ValueSource(strings = {"condition", "expression", "action", "actions"})
    void successfulDslParsingDoesNotLogSourceOrReconstructedAst(String kind) {
        var parser = new DSLParser();
        try (var logs = new Capture(DSLParser.class, Lexer.class)) {
            Object result = switch (kind) {
                case "condition" -> parser.parseCondition("value == \"PRIVATE_TEST\"");
                case "expression" -> parser.parseExpression("\"PRIVATE_TEST\"");
                case "action" -> parser.parseAction("set result to \"PRIVATE_TEST\"");
                default -> parser.parseActionList("set result to \"PRIVATE_TEST\"");
            };
            assertNotNull(result); logs.safe(3);
        }
    }

    @ParameterizedTest @ValueSource(strings = {"condition", "expression", "action", "actions"})
    void malformedDslKeepsParseExceptionWithoutLoggingDetailedSource(String kind) {
        var parser = new DSLParser();
        try (var logs = new Capture(DSLParser.class, Lexer.class)) {
            assertThrows(ParseException.class, () -> {
                switch (kind) {
                    case "condition" -> parser.parseCondition(SECRET + " @");
                    case "expression" -> parser.parseExpression(SECRET + " @");
                    case "action" -> parser.parseAction(SECRET + " @");
                    default -> parser.parseActionList(SECRET + " @");
                }
            });
            logs.safe(2);
        }
    }

    @Test void yamlActionFailureDoesNotLogActionItemOrYamlException() {
        var parser = new ASTRulesDSLParser(new DSLParser());
        try (var logs = new Capture(ASTRulesDSLParser.class, DSLParser.class, Lexer.class)) {
            assertThrows(ASTException.class, () -> parser.parseRulesReactive("name: test\nthen:\n  - PRIVATE_TEST@\n").block());
            logs.safe(3);
        }
    }

    @ParameterizedTest @ValueSource(strings = {"base64", "plain", "code"})
    void evaluationsKeepResultAndInputObjectsWithoutLoggingFacts(String kind) {
        var engine = mock(ASTRulesEvaluationEngine.class); var definitions = mock(RuleDefinitionService.class); var audit = audit();
        var rendered = new java.util.concurrent.atomic.AtomicInteger();
        Object value = new Object() { @Override public String toString() { rendered.incrementAndGet(); return SECRET; } };
        Map<String,Object> input = Map.of("PRIVATE_TEST_KEY", value);
        Map<String,Object> output = Map.of("result", SECRET);
        var result = ASTRulesEvaluationResult.success(true, output, 1);
        when(engine.evaluateRulesReactive(anyString(), same(input))).thenReturn(Mono.just(result));
        when(definitions.evaluateRuleByCode(SECRET, input)).thenReturn(Mono.just(result));
        var service = new RulesEvaluationServiceImpl(engine, definitions, audit);
        try (var logs = new Capture(RulesEvaluationServiceImpl.class)) {
            var response = evaluate(service, kind, input).block();
            assertTrue(response.getSuccess()); assertSame(output, response.getOutputData());
            logs.safe(2); assertEquals(0, rendered.get()); assertTrue(logs.encoded().contains("inputCount=1"));
        }
    }

    @ParameterizedTest @ValueSource(strings = {"base64", "plain", "code"})
    void evaluationFailureKeepsResponseErrorWithoutRawThrowableLogging(String kind) {
        var engine = mock(ASTRulesEvaluationEngine.class); var definitions = mock(RuleDefinitionService.class);
        var error = new IllegalStateException(SECRET, new IllegalArgumentException(SECRET));
        Map<String,Object> input = Map.of("secret", SECRET);
        when(engine.evaluateRulesReactive(anyString(), same(input))).thenReturn(Mono.error(error));
        when(definitions.evaluateRuleByCode(SECRET, input)).thenReturn(Mono.error(error));
        var service = new RulesEvaluationServiceImpl(engine, definitions, audit());
        try (var logs = new Capture(RulesEvaluationServiceImpl.class)) {
            var response = evaluate(service, kind, input).block();
            assertFalse(response.getSuccess()); assertEquals(SECRET, response.getError());
            logs.safe(2); assertTrue(logs.encoded().contains("IllegalStateException"));
        }
    }

    @Test void definitionLookupAndEvaluationKeepOriginalErrorWithoutLoggingCodeOrFacts() {
        var repo = mock(RuleDefinitionRepository.class); var error = new IllegalStateException(SECRET);
        when(repo.findByCode(SECRET)).thenReturn(Mono.error(error));
        var service = new RuleDefinitionServiceImpl(repo, mock(RuleDefinitionMapper.class),
                mock(ASTRulesEvaluationEngine.class), mock(YamlDslValidator.class), audit(), mock(CacheService.class));
        try (var logs = new Capture(RuleDefinitionServiceImpl.class)) {
            assertSame(error, assertThrows(IllegalStateException.class, () -> service.evaluateRuleByCode(SECRET, Map.of("secret", SECRET)).block()));
            logs.safe(2);
        }
    }

    @Test void auditQueryKeepsFilterAndOriginalErrorWithoutLoggingFilterOrException() {
        var repo = mock(AuditTrailRepository.class); var error = new IllegalStateException(SECRET);
        when(repo.findWithFilters(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(Flux.error(error));
        when(repo.countWithFilters(any(), any(), any(), any(), any(), any(), any())).thenReturn(Mono.just(0L));
        var filter = AuditTrailFilterDTO.builder().userId(SECRET).sessionId(SECRET).page(3).size(17).build();
        var service = new AuditTrailServiceImpl(repo, new com.fasterxml.jackson.databind.ObjectMapper());
        try (var logs = new Capture(AuditTrailServiceImpl.class)) {
            assertSame(error, assertThrows(IllegalStateException.class, () -> service.getAuditTrails(filter).block()));
            assertEquals(SECRET, filter.getUserId()); logs.safe(2);
        }
    }

    private static AuditHelper audit() {
        var audit = mock(AuditHelper.class);
        when(audit.recordAuditEventWithMetadata(any(), nullable(UUID.class), nullable(String.class), any(), any(),
                anyInt(), anyBoolean(), anyLong(), anyMap(), nullable(ServerWebExchange.class))).thenReturn(Mono.empty());
        when(audit.recordAuditEventWithError(any(), nullable(UUID.class), nullable(String.class), any(), anyString(),
                anyInt(), anyLong(), nullable(ServerWebExchange.class))).thenReturn(Mono.empty());
        return audit;
    }

    private static Mono<RulesEvaluationResponseDTO> evaluate(RulesEvaluationServiceImpl service, String kind, Map<String,Object> input) {
        return switch (kind) {
            case "base64" -> service.evaluateRulesDirectWithAudit(RulesEvaluationRequestDTO.builder()
                    .rulesDefinitionBase64(java.util.Base64.getEncoder().encodeToString(SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8))).inputData(input).build(), null);
            case "plain" -> service.evaluateRulesPlainWithAudit(PlainYamlEvaluationRequestDTO.builder().yamlContent(SECRET).inputData(input).build(), null);
            default -> service.evaluateRuleByCodeWithAudit(RuleEvaluationByCodeRequestDTO.builder().ruleDefinitionCode(SECRET).inputData(input).build(), null);
        };
    }

    private static final class Capture implements AutoCloseable {
        private final java.util.List<ch.qos.logback.classic.Logger> loggers = new java.util.ArrayList<>();
        private final java.util.List<ch.qos.logback.classic.Level> levels = new java.util.ArrayList<>();
        private final java.util.List<Boolean> additive = new java.util.ArrayList<>();
        private final ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> events = new ch.qos.logback.core.read.ListAppender<>();
        private final java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        private final ch.qos.logback.classic.encoder.PatternLayoutEncoder encoder = new ch.qos.logback.classic.encoder.PatternLayoutEncoder();
        private final ch.qos.logback.core.OutputStreamAppender<ch.qos.logback.classic.spi.ILoggingEvent> output = new ch.qos.logback.core.OutputStreamAppender<>();
        Capture(Class<?>... types) {
            var context = ((ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(types[0])).getLoggerContext();
            encoder.setContext(context); encoder.setPattern("%level %msg %ex%n"); encoder.start();
            output.setContext(context); output.setEncoder(encoder); output.setOutputStream(bytes); output.start();
            events.setContext(context); events.start();
            for (Class<?> type : types) {
                var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(type);
                loggers.add(logger); levels.add(logger.getLevel()); additive.add(logger.isAdditive());
                logger.setLevel(ch.qos.logback.classic.Level.DEBUG); logger.setAdditive(false);
                logger.addAppender(events); logger.addAppender(output);
            }
        }
        String encoded() { return bytes.toString(java.nio.charset.StandardCharsets.UTF_8); }
        void safe(int minimumEvents) {
            assertTrue(events.list.size() >= minimumEvents, "The real call sites must emit diagnostics");
            assertFalse(encoded().contains("PRIVATE_TEST"), "Sensitive payload reached encoded bytes");
            for (var event : events.list) {
                assertNull(event.getThrowableProxy(), "Raw failure/cause must not reach encoder");
                assertFalse(event.getFormattedMessage().contains("PRIVATE_TEST"));
                for (String separator : java.util.List.of("\r", "\n", "\u000b", "\f", "\u0085", "\u2028", "\u2029")) {
                    assertFalse(event.getFormattedMessage().contains(separator), "Log record boundary injection");
                }
                Object[] arguments = event.getArgumentArray();
                if (arguments != null) for (Object argument : arguments) {
                    assertFalse(argument instanceof Throwable || argument instanceof java.util.Map<?, ?>,
                            "Only diagnostic metadata may reach log arguments");
                }
            }
        }
        @Override public void close() {
            for (int i = 0; i < loggers.size(); i++) {
                var logger = loggers.get(i); logger.detachAppender(events); logger.detachAppender(output);
                logger.setLevel(levels.get(i)); logger.setAdditive(additive.get(i));
            }
            output.stop(); events.stop(); encoder.stop();
        }
    }
}

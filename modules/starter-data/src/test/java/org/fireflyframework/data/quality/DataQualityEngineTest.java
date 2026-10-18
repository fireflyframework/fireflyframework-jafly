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

package org.fireflyframework.data.quality;

import org.fireflyframework.data.event.DataQualityEvent;
import org.fireflyframework.data.quality.rules.NotNullRule;
import org.fireflyframework.data.quality.rules.PatternRule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link DataQualityEngine}.
 */
@ExtendWith(MockitoExtension.class)
class DataQualityEngineTest {

    @Mock
    private ApplicationEventPublisher eventPublisher;

    record TestDTO(String name, String email, int age) {}

    @Test
    void evaluate_collectAll_shouldRunAllRulesEvenOnFailure() {
        // Given - two rules that both fail
        DataQualityRule<TestDTO> nameRule = new NotNullRule<>(
                "name", TestDTO::name, QualitySeverity.CRITICAL);
        DataQualityRule<TestDTO> emailRule = new NotNullRule<>(
                "email", TestDTO::email, QualitySeverity.CRITICAL);

        DataQualityEngine engine = new DataQualityEngine(List.of(nameRule, emailRule));
        TestDTO dto = new TestDTO(null, null, 25);

        // When & Then
        StepVerifier.create(engine.evaluate(dto, QualityStrategy.COLLECT_ALL))
                .assertNext(report -> {
                    assertThat(report.isPassed()).isFalse();
                    assertThat(report.getTotalRules()).isEqualTo(2);
                    assertThat(report.getFailedRules()).isEqualTo(2);
                    assertThat(report.getPassedRules()).isEqualTo(0);
                })
                .verifyComplete();
    }

    @Test
    void evaluate_failFast_shouldStopOnFirstCriticalFailure() {
        // Given - first rule is CRITICAL and fails, second should not execute
        DataQualityRule<TestDTO> nameRule = new NotNullRule<>(
                "name", TestDTO::name, QualitySeverity.CRITICAL);
        DataQualityRule<TestDTO> emailRule = new NotNullRule<>(
                "email", TestDTO::email, QualitySeverity.CRITICAL);

        DataQualityEngine engine = new DataQualityEngine(List.of(nameRule, emailRule));
        TestDTO dto = new TestDTO(null, null, 25);

        // When & Then
        StepVerifier.create(engine.evaluate(dto, QualityStrategy.FAIL_FAST))
                .assertNext(report -> {
                    assertThat(report.isPassed()).isFalse();
                    assertThat(report.getTotalRules()).isEqualTo(1);
                    assertThat(report.getFailedRules()).isEqualTo(1);
                })
                .verifyComplete();
    }

    @Test
    void evaluate_shouldPassWhenAllRulesPass() {
        // Given
        DataQualityRule<TestDTO> nameRule = new NotNullRule<>(
                "name", TestDTO::name, QualitySeverity.CRITICAL);
        DataQualityRule<TestDTO> emailRule = new PatternRule<>(
                "email", Pattern.compile(".+@.+\\..+"), TestDTO::email);

        DataQualityEngine engine = new DataQualityEngine(List.of(nameRule, emailRule));
        TestDTO dto = new TestDTO("Alice", "alice@example.com", 30);

        // When & Then
        StepVerifier.create(engine.evaluate(dto, QualityStrategy.COLLECT_ALL))
                .assertNext(report -> {
                    assertThat(report.isPassed()).isTrue();
                    assertThat(report.getTotalRules()).isEqualTo(2);
                    assertThat(report.getPassedRules()).isEqualTo(2);
                    assertThat(report.getFailedRules()).isEqualTo(0);
                    assertThat(report.getFailures()).isEmpty();
                })
                .verifyComplete();
    }

    @Test
    void evaluate_shouldReportWarningsAsPassedButIncludeThem() {
        // Given - WARNING failure should not make report.passed false
        DataQualityRule<TestDTO> emailRule = new PatternRule<>(
                "email", Pattern.compile(".+@.+\\..+"), TestDTO::email, QualitySeverity.WARNING);

        DataQualityEngine engine = new DataQualityEngine(List.of(emailRule));
        TestDTO dto = new TestDTO("Alice", "invalid-email", 30);

        // When & Then
        StepVerifier.create(engine.evaluate(dto, QualityStrategy.COLLECT_ALL))
                .assertNext(report -> {
                    assertThat(report.isPassed()).isTrue();
                    assertThat(report.getFailedRules()).isEqualTo(1);
                    assertThat(report.getFailures()).hasSize(1);
                    assertThat(report.getBySeverity(QualitySeverity.WARNING)).hasSize(1);
                    assertThat(report.getBySeverity(QualitySeverity.CRITICAL)).isEmpty();
                })
                .verifyComplete();
    }

    @Test
    void evaluate_shouldPublishEvent() {
        // Given
        DataQualityRule<TestDTO> nameRule = new NotNullRule<>(
                "name", TestDTO::name, QualitySeverity.CRITICAL);

        DataQualityEngine engine = new DataQualityEngine(List.of(nameRule), eventPublisher);
        TestDTO dto = new TestDTO("Alice", "alice@example.com", 30);

        // When
        StepVerifier.create(engine.evaluate(dto, QualityStrategy.COLLECT_ALL))
                .assertNext(report -> assertThat(report.isPassed()).isTrue())
                .verifyComplete();

        // Then
        ArgumentCaptor<DataQualityEvent> captor = ArgumentCaptor.forClass(DataQualityEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());

        DataQualityEvent event = captor.getValue();
        assertThat(event.getReport()).isNotNull();
        assertThat(event.getReport().isPassed()).isTrue();
        assertThat(event.getTimestamp()).isNotNull();
    }
}

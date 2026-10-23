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
package org.fireflyframework.batch.jdbc;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.repository.support.ResourcelessJobRepository;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class JdbcBatchStepsTest {
    private JdbcBatchSteps steps() {
        return new JdbcBatchSteps(
                new ResourcelessJobRepository(),
                new DataSourceTransactionManager(new DriverManagerDataSource()));
    }

    @Test
    void blankStepNamesAreRejectedBeforeReturningNativeBuilder() {
        assertThatThrownBy(() -> steps().chunk(" ", 3))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> steps().tasklet("", (contribution, context) -> null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nonpositiveChunkIsRejected() {
        assertThatThrownBy(() -> steps().chunk("owned-step", 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> steps().chunk("owned-step", -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullTaskletIsRejected() {
        assertThatThrownBy(() -> steps().tasklet("owned-step", null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

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

package org.fireflyframework.security.api.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DecisionTest {

    @Test
    void permitIsGranted() {
        Decision d = Decision.permit();
        assertThat(d.granted()).isTrue();
        assertThat(d.effect()).isEqualTo(Decision.Effect.PERMIT);
        assertThat(d.obligations()).isEmpty();
    }

    @Test
    void denyAndIndeterminateAreNotGranted() {
        assertThat(Decision.deny("nope").granted()).isFalse();
        assertThat(Decision.deny("nope").reason()).isEqualTo("nope");
        assertThat(Decision.indeterminate("engine error").granted()).isFalse();
        assertThat(Decision.indeterminate("engine error").effect()).isEqualTo(Decision.Effect.INDETERMINATE);
    }

    @Test
    void carriesObligationsOnPermit() {
        Decision d = Decision.permit(java.util.List.of(Obligation.of("mask", java.util.Map.of("field", "ssn"))));
        assertThat(d.granted()).isTrue();
        assertThat(d.obligations()).hasSize(1);
        assertThat(d.obligations().get(0).type()).isEqualTo("mask");
    }
}

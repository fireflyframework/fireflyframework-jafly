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

import java.util.List;

/**
 * The outcome of an authorization evaluation.
 *
 * <p>Authorization is <strong>fail-closed</strong>: only {@link Effect#PERMIT} grants access.
 * {@link Effect#INDETERMINATE} (e.g. a policy engine error) is treated as a denial by callers.
 *
 * @param effect      the decision effect
 * @param obligations obligations the caller must fulfil when permitting (e.g. row filters, masking)
 * @param reason      human-readable explanation (primarily for denials and audit)
 */
public record Decision(Effect effect, List<Obligation> obligations, String reason) {

    public enum Effect {PERMIT, DENY, INDETERMINATE}

    public Decision {
        obligations = obligations == null ? List.of() : List.copyOf(obligations);
    }

    public static Decision permit() {
        return new Decision(Effect.PERMIT, List.of(), null);
    }

    public static Decision permit(List<Obligation> obligations) {
        return new Decision(Effect.PERMIT, obligations, null);
    }

    public static Decision deny(String reason) {
        return new Decision(Effect.DENY, List.of(), reason);
    }

    public static Decision indeterminate(String reason) {
        return new Decision(Effect.INDETERMINATE, List.of(), reason);
    }

    /** @return {@code true} only when the effect is {@link Effect#PERMIT}. */
    public boolean granted() {
        return effect == Effect.PERMIT;
    }
}

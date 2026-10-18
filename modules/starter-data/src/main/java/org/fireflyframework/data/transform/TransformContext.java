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

package org.fireflyframework.data.transform;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Immutable context carried through every step of a {@link TransformationChain}.
 *
 * <p>Contains request-scoped metadata such as the originating request ID,
 * tenant identifier, and an extensible metadata map for transformer-specific state.</p>
 */
@Data
@Builder
public class TransformContext {

    private final String requestId;
    private final UUID tenantId;

    @Builder.Default
    private final Map<String, Object> metadata = new HashMap<>();

    @Builder.Default
    private final Instant startTime = Instant.now();
}

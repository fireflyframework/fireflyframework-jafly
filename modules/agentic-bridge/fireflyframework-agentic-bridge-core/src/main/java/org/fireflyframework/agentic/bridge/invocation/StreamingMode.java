/*
 * Copyright 2026 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package org.fireflyframework.agentic.bridge.invocation;

/**
 * Selects the SSE streaming mode exposed by the agentic REST layer.
 *
 * <ul>
 *     <li>{@link #BUFFERED} maps to {@code POST /agents/{name}/stream} —
 *         chunks/messages, friendly for most use cases.</li>
 *     <li>{@link #INCREMENTAL} maps to {@code POST /agents/{name}/stream/incremental}
 *         — true token-by-token streaming with optional debouncing.</li>
 * </ul>
 */
public enum StreamingMode {
    BUFFERED,
    INCREMENTAL
}

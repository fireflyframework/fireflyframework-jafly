/*
 * Copyright 2026 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package org.fireflyframework.agentic.bridge.exception;

/**
 * Raised when the bridge fails to parse a streaming event from the agentic
 * service (malformed SSE frame, unexpected JSON shape, prematurely closed
 * WebSocket, etc.).
 */
public class AgenticStreamException extends AgenticBridgeException {

    public AgenticStreamException(String message, AgenticErrorContext context, Throwable cause) {
        super(message, context, cause);
    }
}

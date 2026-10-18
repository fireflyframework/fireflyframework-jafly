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
 * Raised when the remote agent ran but reported an unsuccessful outcome
 * ({@code success=false} in the {@code AgentResponse} body, or an HTTP 5xx).
 */
public class AgentInvocationException extends AgenticBridgeException {

    public AgentInvocationException(String message, AgenticErrorContext context) {
        super(message, context);
    }

    public AgentInvocationException(String message, AgenticErrorContext context, Throwable cause) {
        super(message, context, cause);
    }
}

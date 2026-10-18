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
 * Raised when the remote agentic service responds with HTTP 404 because the
 * requested agent name is not registered in its {@code AgentRegistry}.
 */
public class AgentNotFoundException extends AgenticBridgeException {

    public AgentNotFoundException(String agentName) {
        super("Agent '" + agentName + "' not found",
                AgenticErrorContext.builder().agentName(agentName).httpStatus(404).build());
    }

    public AgentNotFoundException(String agentName, AgenticErrorContext context) {
        super("Agent '" + agentName + "' not found", context);
    }
}

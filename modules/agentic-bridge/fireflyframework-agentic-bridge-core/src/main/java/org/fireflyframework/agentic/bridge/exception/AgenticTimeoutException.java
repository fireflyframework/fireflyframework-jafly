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

import java.time.Duration;

/**
 * Raised when the bridge gives up waiting for the agentic service to
 * respond, after the configured response timeout (and any retries) elapses.
 */
public class AgenticTimeoutException extends AgenticBridgeException {

    private final Duration timeout;

    public AgenticTimeoutException(String message, Duration timeout, AgenticErrorContext context, Throwable cause) {
        super(message, context, cause);
        this.timeout = timeout;
    }

    public Duration timeout() {
        return timeout;
    }
}

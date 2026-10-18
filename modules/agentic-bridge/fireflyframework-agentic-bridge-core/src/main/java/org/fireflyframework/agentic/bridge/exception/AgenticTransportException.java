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
 * Raised when the bridge cannot communicate with the agentic service due to
 * a protocol or network failure (DNS, TCP reset, broker unreachable, malformed
 * frame, etc.).
 */
public class AgenticTransportException extends AgenticBridgeException {

    public AgenticTransportException(String message, AgenticErrorContext context, Throwable cause) {
        super(message, context, cause);
    }
}

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
import java.util.Optional;

/**
 * Raised when the remote agentic service rejects the request with HTTP 429.
 *
 * <p>Carries an optional {@link #retryAfter()} hint when the server provides
 * a {@code Retry-After} header.</p>
 */
public class AgenticRateLimitException extends AgenticBridgeException {

    private final Duration retryAfter;

    public AgenticRateLimitException(String message, AgenticErrorContext context, Duration retryAfter) {
        super(message, context);
        this.retryAfter = retryAfter;
    }

    public Optional<Duration> retryAfter() {
        return Optional.ofNullable(retryAfter);
    }
}

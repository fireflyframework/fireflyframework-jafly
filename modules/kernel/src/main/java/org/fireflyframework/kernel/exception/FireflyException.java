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

package org.fireflyframework.kernel.exception;

import java.util.Collections;
import java.util.Map;

/**
 * Base exception for all Firefly Framework errors.
 * <p>
 * Provides a unified exception hierarchy so that all framework-level errors
 * can be caught with a single {@code catch (FireflyException e)} block while
 * still allowing module-specific subclasses.
 * <p>
 * This is backwards-compatible: since {@code FireflyException extends RuntimeException},
 * existing {@code catch (RuntimeException)} blocks continue to work.
 */
public class FireflyException extends RuntimeException {

    private final String errorCode;
    private final Map<String, Object> context;

    public FireflyException(String message) {
        this(message, null, null, null);
    }

    public FireflyException(Throwable cause) {
        this(cause != null ? cause.getMessage() : null, null, null, cause);
    }

    public FireflyException(String message, Throwable cause) {
        this(message, null, null, cause);
    }

    public FireflyException(String message, String errorCode) {
        this(message, errorCode, null, null);
    }

    public FireflyException(String message, String errorCode, Throwable cause) {
        this(message, errorCode, null, cause);
    }

    public FireflyException(String message, String errorCode, Map<String, Object> context, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.context = context != null ? Map.copyOf(context) : Collections.emptyMap();
    }

    public String getErrorCode() {
        return errorCode;
    }

    public Map<String, Object> getContext() {
        return context;
    }
}

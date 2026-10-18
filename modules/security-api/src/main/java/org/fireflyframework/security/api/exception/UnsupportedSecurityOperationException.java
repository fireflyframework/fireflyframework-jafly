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

package org.fireflyframework.security.api.exception;

import org.fireflyframework.kernel.exception.FireflySecurityException;

/**
 * Thrown by a segregated capability port when the underlying provider does not support the
 * requested operation. Adapters return this (as a failed {@code Mono}) from the {@code default}
 * methods of ports they do not implement, instead of faking a result.
 */
public class UnsupportedSecurityOperationException extends FireflySecurityException {

    public static final String ERROR_CODE = "SECURITY_OPERATION_UNSUPPORTED";

    public UnsupportedSecurityOperationException(String operation) {
        super("Security operation not supported by this provider: " + operation, ERROR_CODE);
    }
}

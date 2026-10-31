/*
 * Copyright 2024-2026 Firefly Software Foundation.
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
package org.fireflyframework.ecm.adapter.docusign;

import com.docusign.esign.client.ApiException;
import java.util.concurrent.RejectedExecutionException;

/** Provider failures expose categories and status, never credential-bearing SDK bodies or causes. */
public final class DocuSignAdapterException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public enum Code {
        CONFIGURATION, AUTHENTICATION, AUTHORIZATION, NOT_FOUND, RATE_LIMITED,
        PROVIDER_UNAVAILABLE, INVALID_REQUEST, PROVIDER_RESPONSE, TRANSPORT, BUSY, CLOSED, CLEANUP_FAILURE, UNSUPPORTED
    }
    public enum Operation { CONFIGURATION, AUTHENTICATION, ENVELOPE }

    private final Code code;
    private final Operation operation;
    private final Integer httpStatus;

    public DocuSignAdapterException(Code code, Operation operation) {
        this(code, operation, null);
    }

    public DocuSignAdapterException(Code code, Operation operation, Integer httpStatus) {
        super("DocuSign " + operation + " failed (" + code + ")", null);
        this.code = java.util.Objects.requireNonNull(code);
        this.operation = java.util.Objects.requireNonNull(operation);
        this.httpStatus = httpStatus;
    }

    public Code getCode() { return code; }
    public Operation getOperation() { return operation; }
    public Integer getHttpStatus() { return httpStatus; }

    public static DocuSignAdapterException safe(Throwable error, Operation operation) {
        if (error instanceof DocuSignAdapterException safe) return safe;
        if (error instanceof RejectedExecutionException) return new DocuSignAdapterException(Code.BUSY, operation);
        if (error instanceof ApiException api) {
            int status = api.getCode();
            Code category = switch (status) {
                case 400, 422 -> operation == Operation.AUTHENTICATION ? Code.AUTHENTICATION : Code.INVALID_REQUEST;
                case 401 -> Code.AUTHENTICATION;
                case 403 -> Code.AUTHORIZATION;
                case 404 -> Code.NOT_FOUND;
                case 429 -> Code.RATE_LIMITED;
                default -> status >= 500 ? Code.PROVIDER_UNAVAILABLE : Code.PROVIDER_RESPONSE;
            };
            return new DocuSignAdapterException(category, operation, status >= 100 && status <= 599 ? status : null);
        }
        return new DocuSignAdapterException(Code.TRANSPORT, operation);
    }
}

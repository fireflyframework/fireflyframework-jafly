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

package org.fireflyframework.client.exception;

import lombok.Getter;

import javax.xml.namespace.QName;

/**
 * Exception thrown when a SOAP fault is received from a service.
 * 
 * <p>This exception wraps SOAP fault information including fault code,
 * fault string, fault actor, and detail elements, providing a structured
 * way to handle SOAP errors in a reactive context.
 *
 * @author Firefly Software Foundation
 * @since 2.0.0
 */
@Getter
public class SoapFaultException extends ServiceClientException {

    /**
     * The SOAP fault code (e.g., "Server", "Client", "VersionMismatch").
     */
    private final QName faultCode;

    /**
     * The SOAP fault string providing a human-readable description.
     */
    private final String faultString;

    /**
     * The SOAP fault actor indicating which part of the message path caused the fault.
     */
    private final String faultActor;

    /**
     * The SOAP fault detail containing application-specific error information.
     */
    private final String faultDetail;

    /**
     * Creates a new SOAP fault exception.
     *
     * @param faultCode the fault code
     * @param faultString the fault string
     */
    public SoapFaultException(QName faultCode, String faultString) {
        this(faultCode, faultString, null, null);
    }

    /**
     * Creates a new SOAP fault exception with actor and detail.
     *
     * @param faultCode the fault code
     * @param faultString the fault string
     * @param faultActor the fault actor
     * @param faultDetail the fault detail
     */
    public SoapFaultException(QName faultCode, String faultString, String faultActor, String faultDetail) {
        super(buildMessage(faultCode, faultString, faultActor, faultDetail));
        this.faultCode = faultCode;
        this.faultString = faultString;
        this.faultActor = faultActor;
        this.faultDetail = faultDetail;
    }

    /**
     * Creates a new SOAP fault exception with a cause.
     *
     * @param faultCode the fault code
     * @param faultString the fault string
     * @param cause the underlying cause
     */
    public SoapFaultException(QName faultCode, String faultString, Throwable cause) {
        this(faultCode, faultString, null, null, cause);
    }

    /**
     * Creates a new SOAP fault exception with all details and a cause.
     *
     * @param faultCode the fault code
     * @param faultString the fault string
     * @param faultActor the fault actor
     * @param faultDetail the fault detail
     * @param cause the underlying cause
     */
    public SoapFaultException(QName faultCode, String faultString, String faultActor,
                             String faultDetail, Throwable cause) {
        super(buildMessage(faultCode, faultString, faultActor, faultDetail), cause);
        this.faultCode = faultCode;
        this.faultString = faultString;
        this.faultActor = faultActor;
        this.faultDetail = faultDetail;
    }

    /**
     * Creates a new SOAP fault exception with error context.
     *
     * @param faultCode the fault code
     * @param faultString the fault string
     * @param faultActor the fault actor
     * @param faultDetail the fault detail
     * @param errorContext rich context information about the error
     */
    public SoapFaultException(QName faultCode, String faultString, String faultActor,
                             String faultDetail, ErrorContext errorContext) {
        super(buildMessage(faultCode, faultString, faultActor, faultDetail), errorContext);
        this.faultCode = faultCode;
        this.faultString = faultString;
        this.faultActor = faultActor;
        this.faultDetail = faultDetail;
    }

    /**
     * Creates a new SOAP fault exception with error context and cause.
     *
     * @param faultCode the fault code
     * @param faultString the fault string
     * @param faultActor the fault actor
     * @param faultDetail the fault detail
     * @param errorContext rich context information about the error
     * @param cause the underlying cause
     */
    public SoapFaultException(QName faultCode, String faultString, String faultActor,
                             String faultDetail, ErrorContext errorContext, Throwable cause) {
        super(buildMessage(faultCode, faultString, faultActor, faultDetail), errorContext, cause);
        this.faultCode = faultCode;
        this.faultString = faultString;
        this.faultActor = faultActor;
        this.faultDetail = faultDetail;
    }

    /**
     * Checks if this is a client-side fault.
     *
     * @return true if the fault code indicates a client error
     */
    public boolean isClientFault() {
        return faultCode != null &&
               (faultCode.getLocalPart().equalsIgnoreCase("Client") ||
                faultCode.getLocalPart().equalsIgnoreCase("Sender"));
    }

    /**
     * Checks if this is a server-side fault.
     *
     * @return true if the fault code indicates a server error
     */
    public boolean isServerFault() {
        return faultCode != null &&
               (faultCode.getLocalPart().equalsIgnoreCase("Server") ||
                faultCode.getLocalPart().equalsIgnoreCase("Receiver"));
    }

    /**
     * Checks if this is a version mismatch fault.
     *
     * @return true if the fault code indicates a version mismatch
     */
    public boolean isVersionMismatchFault() {
        return faultCode != null &&
               faultCode.getLocalPart().equalsIgnoreCase("VersionMismatch");
    }

    @Override
    public ErrorCategory getErrorCategory() {
        if (isClientFault()) {
            return ErrorCategory.CLIENT_ERROR;
        } else if (isServerFault()) {
            return ErrorCategory.SERVER_ERROR;
        }
        return ErrorCategory.UNKNOWN_ERROR;
    }

    /**
     * Builds a detailed error message from fault components.
     */
    private static String buildMessage(QName faultCode, String faultString, 
                                      String faultActor, String faultDetail) {
        StringBuilder message = new StringBuilder("SOAP Fault");
        
        if (faultCode != null) {
            message.append(" [").append(faultCode.getLocalPart()).append("]");
        }
        
        if (faultString != null && !faultString.isEmpty()) {
            message.append(": ").append(faultString);
        }
        
        if (faultActor != null && !faultActor.isEmpty()) {
            message.append(" (Actor: ").append(faultActor).append(")");
        }
        
        if (faultDetail != null && !faultDetail.isEmpty()) {
            message.append(" - Detail: ").append(faultDetail);
        }
        
        return message.toString();
    }
}


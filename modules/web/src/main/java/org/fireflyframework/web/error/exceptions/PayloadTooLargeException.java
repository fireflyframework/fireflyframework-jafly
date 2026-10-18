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


package org.fireflyframework.web.error.exceptions;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when a request payload is too large.
 * Results in a 413 PAYLOAD TOO LARGE response.
 */
public class PayloadTooLargeException extends BusinessException {
    
    /**
     * The maximum allowed size in bytes.
     */
    private final long maxSizeBytes;
    
    /**
     * The actual size in bytes.
     */
    private final long actualSizeBytes;
    
    /**
     * Creates a new PayloadTooLargeException with the given message.
     *
     * @param message the error message
     * @param maxSizeBytes the maximum allowed size in bytes
     * @param actualSizeBytes the actual size in bytes
     */
    public PayloadTooLargeException(String message, long maxSizeBytes, long actualSizeBytes) {
        super(HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE", message);
        this.maxSizeBytes = maxSizeBytes;
        this.actualSizeBytes = actualSizeBytes;
    }
    
    /**
     * Creates a new PayloadTooLargeException with a code and message.
     *
     * @param code the error code
     * @param message the error message
     * @param maxSizeBytes the maximum allowed size in bytes
     * @param actualSizeBytes the actual size in bytes
     */
    public PayloadTooLargeException(String code, String message, long maxSizeBytes, long actualSizeBytes) {
        super(HttpStatus.PAYLOAD_TOO_LARGE, code, message);
        this.maxSizeBytes = maxSizeBytes;
        this.actualSizeBytes = actualSizeBytes;
    }
    
    /**
     * Returns the maximum allowed size in bytes.
     *
     * @return the maximum size
     */
    public long getMaxSizeBytes() {
        return maxSizeBytes;
    }
    
    /**
     * Returns the actual size in bytes.
     *
     * @return the actual size
     */
    public long getActualSizeBytes() {
        return actualSizeBytes;
    }
    
    /**
     * Creates a new PayloadTooLargeException.
     *
     * @param maxSizeBytes the maximum allowed size in bytes
     * @param actualSizeBytes the actual size in bytes
     * @return a new PayloadTooLargeException
     */
    public static PayloadTooLargeException forSize(long maxSizeBytes, long actualSizeBytes) {
        return new PayloadTooLargeException(
                String.format("Request payload size of %d bytes exceeds the maximum allowed size of %d bytes", 
                        actualSizeBytes, maxSizeBytes),
                maxSizeBytes,
                actualSizeBytes
        );
    }
    
    /**
     * Creates a new PayloadTooLargeException for a file upload.
     *
     * @param fileName the name of the file
     * @param maxSizeBytes the maximum allowed size in bytes
     * @param actualSizeBytes the actual size in bytes
     * @return a new PayloadTooLargeException
     */
    public static PayloadTooLargeException forFileUpload(String fileName, long maxSizeBytes, long actualSizeBytes) {
        return new PayloadTooLargeException(
                "FILE_UPLOAD_TOO_LARGE",
                String.format("File '%s' size of %d bytes exceeds the maximum allowed size of %d bytes", 
                        fileName, actualSizeBytes, maxSizeBytes),
                maxSizeBytes,
                actualSizeBytes
        );
    }
    
    /**
     * Creates a new PayloadTooLargeException for a multipart request.
     *
     * @param maxSizeBytes the maximum allowed size in bytes
     * @param actualSizeBytes the actual size in bytes
     * @return a new PayloadTooLargeException
     */
    public static PayloadTooLargeException forMultipartRequest(long maxSizeBytes, long actualSizeBytes) {
        return new PayloadTooLargeException(
                "MULTIPART_REQUEST_TOO_LARGE",
                String.format("Multipart request size of %d bytes exceeds the maximum allowed size of %d bytes", 
                        actualSizeBytes, maxSizeBytes),
                maxSizeBytes,
                actualSizeBytes
        );
    }
}

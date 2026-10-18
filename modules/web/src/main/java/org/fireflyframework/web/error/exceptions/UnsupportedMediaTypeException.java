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
import org.springframework.http.MediaType;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Exception thrown when a request uses a media type that is not supported.
 * Results in a 415 UNSUPPORTED MEDIA TYPE response.
 */
public class UnsupportedMediaTypeException extends BusinessException {
    
    /**
     * The media type that was used.
     */
    private final String mediaType;
    
    /**
     * The supported media types.
     */
    private final Set<String> supportedMediaTypes;
    
    /**
     * Creates a new UnsupportedMediaTypeException with the given message.
     *
     * @param message the error message
     * @param mediaType the media type that was used
     * @param supportedMediaTypes the supported media types
     */
    public UnsupportedMediaTypeException(String message, String mediaType, Set<String> supportedMediaTypes) {
        super(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", message);
        this.mediaType = mediaType;
        this.supportedMediaTypes = Collections.unmodifiableSet(new HashSet<>(supportedMediaTypes));
    }
    
    /**
     * Creates a new UnsupportedMediaTypeException with a code and message.
     *
     * @param code the error code
     * @param message the error message
     * @param mediaType the media type that was used
     * @param supportedMediaTypes the supported media types
     */
    public UnsupportedMediaTypeException(String code, String message, String mediaType, Set<String> supportedMediaTypes) {
        super(HttpStatus.UNSUPPORTED_MEDIA_TYPE, code, message);
        this.mediaType = mediaType;
        this.supportedMediaTypes = Collections.unmodifiableSet(new HashSet<>(supportedMediaTypes));
    }
    
    /**
     * Returns the media type that was used.
     *
     * @return the media type
     */
    public String getMediaType() {
        return mediaType;
    }
    
    /**
     * Returns the supported media types.
     *
     * @return the supported media types
     */
    public Set<String> getSupportedMediaTypes() {
        return supportedMediaTypes;
    }
    
    /**
     * Creates a new UnsupportedMediaTypeException.
     *
     * @param mediaType the media type that was used
     * @param supportedMediaTypes the supported media types
     * @return a new UnsupportedMediaTypeException
     */
    public static UnsupportedMediaTypeException forMediaType(String mediaType, String... supportedMediaTypes) {
        Set<String> supported = new HashSet<>(Arrays.asList(supportedMediaTypes));
        return new UnsupportedMediaTypeException(
                String.format("Media type '%s' is not supported. Supported media types: %s", 
                        mediaType, Arrays.toString(supportedMediaTypes)),
                mediaType,
                supported
        );
    }
    
    /**
     * Creates a new UnsupportedMediaTypeException.
     *
     * @param mediaType the media type that was used
     * @param supportedMediaTypes the supported media types
     * @return a new UnsupportedMediaTypeException
     */
    public static UnsupportedMediaTypeException forMediaType(MediaType mediaType, MediaType... supportedMediaTypes) {
        Set<String> supported = new HashSet<>();
        for (MediaType type : supportedMediaTypes) {
            supported.add(type.toString());
        }
        return new UnsupportedMediaTypeException(
                String.format("Media type '%s' is not supported. Supported media types: %s", 
                        mediaType, Arrays.toString(supportedMediaTypes)),
                mediaType.toString(),
                supported
        );
    }
}

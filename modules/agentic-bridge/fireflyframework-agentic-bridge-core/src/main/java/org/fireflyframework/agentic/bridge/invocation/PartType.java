/*
 * Copyright 2026 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package org.fireflyframework.agentic.bridge.invocation;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Discriminator for the multimodal {@link MultiModalPart} types accepted by
 * the agentic REST exposure layer.
 *
 * <p>The string values match exactly the {@code MultiModalPart.type} values
 * defined in {@code fireflyframework_agentic.exposure.rest.schemas} so that
 * the JSON wire format is identical.</p>
 */
public enum PartType {
    TEXT("text"),
    IMAGE_URL("image_url"),
    DOCUMENT_URL("document_url"),
    AUDIO_URL("audio_url"),
    VIDEO_URL("video_url"),
    BINARY("binary");

    private final String wire;

    PartType(String wire) {
        this.wire = wire;
    }

    @JsonValue
    public String wire() {
        return wire;
    }

    @JsonCreator
    public static PartType fromWire(String value) {
        if (value == null) {
            return TEXT;
        }
        for (PartType type : values()) {
            if (type.wire.equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown multimodal part type: " + value);
    }
}

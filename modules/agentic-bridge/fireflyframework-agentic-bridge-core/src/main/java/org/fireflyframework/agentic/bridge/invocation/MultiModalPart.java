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
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Base64;
import java.util.Objects;

/**
 * One element of a multimodal prompt sent to an agent.
 *
 * <p>Mirrors the {@code MultiModalPart} model exposed by the agentic REST
 * layer. Static factory methods cover every supported part type so callers
 * never have to remember string discriminators.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class MultiModalPart {

    private final PartType type;
    private final String content;
    private final String mediaType;

    @JsonCreator
    public MultiModalPart(
            @JsonProperty("type") PartType type,
            @JsonProperty("content") String content,
            @JsonProperty("media_type") String mediaType) {
        this.type = type == null ? PartType.TEXT : type;
        this.content = content == null ? "" : content;
        this.mediaType = mediaType;
    }

    public static MultiModalPart text(String value) {
        return new MultiModalPart(PartType.TEXT, value, null);
    }

    public static MultiModalPart imageUrl(String url) {
        return new MultiModalPart(PartType.IMAGE_URL, url, null);
    }

    public static MultiModalPart documentUrl(String url) {
        return new MultiModalPart(PartType.DOCUMENT_URL, url, null);
    }

    public static MultiModalPart audioUrl(String url) {
        return new MultiModalPart(PartType.AUDIO_URL, url, null);
    }

    public static MultiModalPart videoUrl(String url) {
        return new MultiModalPart(PartType.VIDEO_URL, url, null);
    }

    public static MultiModalPart binary(byte[] bytes, String mediaType) {
        Objects.requireNonNull(bytes, "bytes");
        Objects.requireNonNull(mediaType, "mediaType");
        return new MultiModalPart(PartType.BINARY, Base64.getEncoder().encodeToString(bytes), mediaType);
    }

    @JsonProperty("type")
    public PartType type() {
        return type;
    }

    @JsonProperty("content")
    public String content() {
        return content;
    }

    @JsonProperty("media_type")
    public String mediaType() {
        return mediaType;
    }
}

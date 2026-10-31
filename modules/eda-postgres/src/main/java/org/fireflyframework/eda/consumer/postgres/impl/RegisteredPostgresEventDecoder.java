/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package org.fireflyframework.eda.consumer.postgres.impl;

import java.util.Map;
import java.util.Objects;
import org.fireflyframework.eda.consumer.postgres.PostgresEventDecoder;
import org.fireflyframework.eda.serialization.MessageSerializer;

/** Resolves wire identifiers only against operator-supplied class tokens. */
public final class RegisteredPostgresEventDecoder implements PostgresEventDecoder {
    private final MessageSerializer serializer;
    private final Map<String,Class<?>> allowedTypes;

    public RegisteredPostgresEventDecoder(MessageSerializer serializer,Map<String,Class<?>> allowedTypes) {
        this.serializer=Objects.requireNonNull(serializer);
        if(allowedTypes==null||allowedTypes.isEmpty()||allowedTypes.entrySet().stream().anyMatch(e->e.getKey()==null||e.getKey().isBlank()||e.getKey().indexOf(0)>=0||e.getValue()==null||e.getValue().isPrimitive())) {
            throw new IllegalArgumentException("Explicit PostgreSQL event types are required");
        }
        this.allowedTypes=Map.copyOf(allowedTypes);
    }

    @Override
    public Object decode(byte[] payload,String eventClass,String eventType) {
        if(payload==null||payload.length==0)throw new IllegalArgumentException("PostgreSQL event payload is empty");
        String identifier=eventClass!=null?eventClass:eventType;
        Class<?> target=identifier==null?null:allowedTypes.get(identifier);
        if(target==null)throw new IllegalArgumentException("PostgreSQL event type is not registered");
        Class<?> alias=eventType==null?null:allowedTypes.get(eventType);
        if(alias!=null&&alias!=target)throw new IllegalArgumentException("PostgreSQL event type is ambiguous");
        Object result=serializer.deserialize(payload.clone(),target);
        if(result==null)throw new IllegalArgumentException("PostgreSQL event decoder returned no payload");
        return result;
    }
}

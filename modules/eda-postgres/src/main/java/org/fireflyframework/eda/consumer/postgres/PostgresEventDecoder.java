/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package org.fireflyframework.eda.consumer.postgres;

@FunctionalInterface
public interface PostgresEventDecoder {
    Object decode(byte[] payload, String eventClass, String eventType);
}

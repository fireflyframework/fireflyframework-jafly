/*
 * Copyright 2024-2026 Firefly Software Foundation
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0
 */
package org.fireflyframework.eda.consumer.postgres;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.fireflyframework.eda.consumer.postgres.model.DeliveryBounds;

/** Renders a one-time operator migration; never executes DDL at startup. */
public final class PostgresDeliverySchema {
    public static final String VERSION_MARKER = "firefly.eda.delivery/v1";
    private PostgresDeliverySchema() { }

    public static String quoted(String identifier) {
        DeliveryBounds.text(identifier, 63);
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    public static String qualified(String schema, String table) {
        return quoted(schema) + "." + quoted(table);
    }

    public static String installationSql(String schema, String table) {
        String relation = qualified(schema, table);
        byte[] schemaBytes = schema.getBytes(StandardCharsets.UTF_8);
        byte[] tableBytes = table.getBytes(StandardCharsets.UTF_8);
        byte[] identity = ByteBuffer.allocate(8 + schemaBytes.length + tableBytes.length)
                .putInt(schemaBytes.length).put(schemaBytes).putInt(tableBytes.length).put(tableBytes).array();
        final String suffix;
        try {
            suffix = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(identity)).substring(0, 32);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 unavailable", error);
        }
        try (var resource = PostgresDeliverySchema.class.getResourceAsStream("/org/fireflyframework/eda/postgres/delivery-v1.sql")) {
            if (resource == null) throw new IllegalStateException("Delivery protocol migration resource is missing");
            return new String(resource.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("{{dueIndex}}", quoted("firefly_eda_delivery_v1_due_" + suffix))
                    .replace("{{leaseIndex}}", quoted("firefly_eda_delivery_v1_lease_" + suffix))
                    .replace("{{outbox}}", relation);
        } catch (IOException error) {
            throw new IllegalStateException("Delivery protocol migration could not be read", error);
        }
    }
}

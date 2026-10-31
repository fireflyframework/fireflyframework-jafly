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
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.fireflyframework.eda.consumer.postgres.model.ConsumerGroupTables;

/** Renders the one-time technical group migration; never executes it. */
public final class PostgresConsumerGroupSchema {
    public static final String JOURNAL_GUARD_BODY = "BEGIN\n RAISE EXCEPTION USING ERRCODE='55000', MESSAGE='Consumer group redrive journal is append-only';\nEND;\n";
    private PostgresConsumerGroupSchema() { }

    public static String journalGuardName(ConsumerGroupTables tables) { return name(tables, "guard"); }
    public static String journalMutationTriggerName(ConsumerGroupTables tables) { return name(tables, "mutation"); }
    public static String journalTruncateTriggerName(ConsumerGroupTables tables) { return name(tables, "truncate"); }

    private static String name(ConsumerGroupTables tables, String purpose) {
        Objects.requireNonNull(tables, "tables");
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            for (String value : new String[]{tables.schema(), tables.outbox(), tables.registry(), tables.deliveries(), tables.redrives()}) {
                byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
                digest.update(ByteBuffer.allocate(4).putInt(bytes.length).array());
                digest.update(bytes);
            }
            return "ff_eda_g1_" + purpose + "_" + HexFormat.of().formatHex(digest.digest()).substring(0, 32);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 unavailable", error);
        }
    }

    public static String installationSql(ConsumerGroupTables tables) {
        Objects.requireNonNull(tables, "tables");
        var replacements = Map.ofEntries(
                Map.entry("outbox", PostgresDeliverySchema.qualified(tables.schema(), tables.outbox())),
                Map.entry("registry", PostgresDeliverySchema.qualified(tables.schema(), tables.registry())),
                Map.entry("deliveries", PostgresDeliverySchema.qualified(tables.schema(), tables.deliveries())),
                Map.entry("redrives", PostgresDeliverySchema.qualified(tables.schema(), tables.redrives())),
                Map.entry("dueIndex", PostgresDeliverySchema.quoted(name(tables, "due"))),
                Map.entry("leaseIndex", PostgresDeliverySchema.quoted(name(tables, "lease"))),
                Map.entry("guard", PostgresDeliverySchema.qualified(tables.schema(), journalGuardName(tables))),
                Map.entry("mutationGuard", PostgresDeliverySchema.quoted(journalMutationTriggerName(tables))),
                Map.entry("truncateGuard", PostgresDeliverySchema.quoted(journalTruncateTriggerName(tables))),
                Map.entry("guardBody", JOURNAL_GUARD_BODY));
        try (var resource = PostgresConsumerGroupSchema.class.getResourceAsStream("/org/fireflyframework/eda/postgres/delivery-groups-v1.sql")) {
            if (resource == null) throw new IllegalStateException("Group protocol migration resource is missing");
            String template = new String(resource.readAllBytes(), StandardCharsets.UTF_8);
            return Pattern.compile("\\{\\{([A-Za-z]+)}}").matcher(template).replaceAll(match -> {
                String value = replacements.get(match.group(1));
                if (value == null) throw new IllegalStateException("Unknown group migration placeholder");
                return Matcher.quoteReplacement(value);
            });
        } catch (IOException error) {
            throw new IllegalStateException("Group migration resource could not be read", error);
        }
    }
}

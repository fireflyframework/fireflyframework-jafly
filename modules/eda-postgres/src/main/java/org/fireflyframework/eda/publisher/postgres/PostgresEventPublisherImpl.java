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

package org.fireflyframework.eda.publisher.postgres;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.r2dbc.postgresql.codec.Json;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import lombok.extern.slf4j.Slf4j;
import org.fireflyframework.eda.annotation.PublisherType;
import org.fireflyframework.eda.properties.EdaProperties;
import org.fireflyframework.eda.publisher.ConnectionAwarePublisher;
import org.fireflyframework.eda.publisher.EventPublisher;
import org.fireflyframework.eda.publisher.PublisherHealth;
import org.fireflyframework.eda.publisher.postgres.impl.SpringDataPostgresOutboxWriterImpl;
import org.fireflyframework.eda.publisher.postgres.model.PostgresOutboxAppend;
import org.fireflyframework.eda.serialization.MessageSerializer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * PostgreSQL implementation of {@link EventPublisher} using the transactional
 * outbox pattern combined with {@code LISTEN}/{@code NOTIFY}.
 * <p>
 * Each {@link #publish(Object, String, Map)} call performs a single
 * {@code INSERT} into the configured outbox table. A database trigger fires
 * {@code pg_notify(channel, id)} for every inserted row so any consumer that
 * is {@code LISTEN}ing on the matching channel receives the new event id and
 * can fetch the full payload from the outbox row.
 * <p>
 * Benefits over raw {@code NOTIFY}:
 * <ul>
 *   <li>Payloads beyond PostgreSQL's 8000-byte NOTIFY limit are supported.</li>
 *   <li>Events survive consumer crashes (status remains {@code PENDING}
 *       until processed).</li>
 *   <li>Retry counts and dead-letter status are tracked per row.</li>
 *   <li>The {@code INSERT} participates in an owner transaction bound to the
 *       exact configured connection factory; a separate transport pool does
 *       not join another business connection factory's transaction.</li>
 * </ul>
 */
@Slf4j
public class PostgresEventPublisherImpl implements EventPublisher, ConnectionAwarePublisher {

    private final ObjectProvider<ConnectionFactory> connectionFactoryProvider;
    private final MessageSerializer messageSerializer;
    private final EdaProperties edaProperties;
    private final ObjectMapper objectMapper;
    private String connectionId = "default";
    private volatile WriterBinding writerBinding;

    public PostgresEventPublisherImpl(
            @Qualifier("fireflyEdaPostgresPublisherConnectionFactory")
            ObjectProvider<ConnectionFactory> connectionFactoryProvider,
            MessageSerializer messageSerializer,
            EdaProperties edaProperties,
            ObjectMapper objectMapper) {
        this.connectionFactoryProvider = connectionFactoryProvider;
        this.messageSerializer = messageSerializer;
        this.edaProperties = edaProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> publish(Object event, String destination, Map<String, Object> headers) {
        if (event == null) {
            return Mono.error(new IllegalArgumentException("Event payload cannot be null"));
        }

        ConnectionFactory connectionFactory = connectionFactoryProvider.getIfAvailable();
        if (connectionFactory == null) {
            return Mono.error(new IllegalStateException(
                    "PostgreSQL EDA ConnectionFactory is not available"));
        }

        String effectiveDestination = destination != null ? destination : getDefaultDestination();
        if (effectiveDestination == null || effectiveDestination.isBlank()) {
            return Mono.error(new IllegalArgumentException(
                    "No destination provided and no default destination configured"));
        }

        EdaProperties.Publishers.PostgresConfig config = config();
        String table = qualifiedTable(config);
        String channel = PostgresChannelMapper.toChannel(effectiveDestination);
        Map<String, Object> mergedHeaders = mergeHeaders(headers, event);
        String headersJson = headersToJson(mergedHeaders);
        byte[] payload;
        try {
            payload = messageSerializer.serialize(event);
        } catch (Exception e) {
            return Mono.error(new RuntimeException("Failed to serialize event", e));
        }

        String eventType = eventTypeFrom(headers, event);
        String transactionId = extractStringHeader(headers, "transaction_id");
        String eventClass = event.getClass().getName();

        String schema = schema(config);
        String tableName = tableName(config);
        return Mono.defer(() -> {
                    var row = new PostgresOutboxAppend(null, effectiveDestination, channel, eventType,
                            payload, Json.of(headersJson), transactionId, PublisherType.POSTGRES.name(),
                            connectionId != null ? connectionId : "default", eventClass);
                    return outboxWriter(connectionFactory).append(schema, tableName, row);
                })
                .doOnSuccess(rows -> log.debug(
                        "Inserted outbox row for destination={}, channel={}, rows={}",
                        effectiveDestination, channel, rows))
                .then()
                .onErrorMap(e -> {
                    log.error("Failed to publish event to PostgreSQL outbox table {}", table, e);
                    return new RuntimeException("Failed to publish event to PostgreSQL", e);
                });
    }

    private PostgresOutboxWriter outboxWriter(ConnectionFactory factory) {
        WriterBinding current = writerBinding;
        if (current == null || current.factory() != factory) {
            synchronized (this) {
                current = writerBinding;
                if (current == null || current.factory() != factory) {
                    current = new WriterBinding(factory,
                            new SpringDataPostgresOutboxWriterImpl(new R2dbcEntityTemplate(factory)));
                    writerBinding = current;
                }
            }
        }
        return current.writer();
    }

    private record WriterBinding(ConnectionFactory factory, PostgresOutboxWriter writer) { }

    @Override
    public PublisherType getPublisherType() {
        return PublisherType.POSTGRES;
    }

    @Override
    public boolean isAvailable() {
        return connectionFactoryProvider.getIfAvailable() != null;
    }

    @Override
    public String getDefaultDestination() {
        EdaProperties.Publishers.PostgresConfig config = config();
        return config != null ? config.getDefaultDestination() : "events";
    }

    @Override
    public Mono<PublisherHealth> getHealth() {
        ConnectionFactory factory = connectionFactoryProvider.getIfAvailable();
        PublisherHealth.PublisherHealthBuilder builder = PublisherHealth.builder()
                .publisherType(getPublisherType())
                .connectionId(getConnectionId())
                .lastChecked(Instant.now());

        if (factory == null) {
            return Mono.just(builder
                    .status("DOWN")
                    .available(false)
                    .errorMessage("PostgreSQL EDA ConnectionFactory is not available")
                    .build());
        }

        return Mono.usingWhen(
                        Mono.from(factory.create()),
                        connection -> Mono.from(connection.validate(io.r2dbc.spi.ValidationDepth.REMOTE)),
                        Connection::close)
                .map(valid -> {
                    if (Boolean.TRUE.equals(valid)) {
                        return builder
                                .status("UP")
                                .available(true)
                                .details(Map.of(
                                        "connection_id", getConnectionId(),
                                        "outbox_table", qualifiedTable(config())))
                                .build();
                    }
                    return builder
                            .status("DOWN")
                            .available(false)
                            .errorMessage("PostgreSQL connection failed validation")
                            .build();
                })
                .onErrorResume(e -> {
                    log.warn("PostgreSQL publisher health check failed for connection {}",
                            getConnectionId(), e);
                    return Mono.just(builder
                            .status("DOWN")
                            .available(false)
                            .errorMessage(e.getMessage())
                            .details(Map.of("error_type", e.getClass().getSimpleName()))
                            .build());
                });
    }

    @Override
    public void setConnectionId(String connectionId) {
        this.connectionId = connectionId != null ? connectionId : "default";
    }

    @Override
    public String getConnectionId() {
        return connectionId;
    }

    @Override
    public boolean isConnectionConfigured(String connectionId) {
        return connectionFactoryProvider.getIfAvailable() != null
                && edaProperties.getPublishers().getPostgres()
                .getOrDefault(connectionId != null ? connectionId : "default", null) != null;
    }

    private EdaProperties.Publishers.PostgresConfig config() {
        Object resolved = edaProperties.getPublisherConfig(PublisherType.POSTGRES, connectionId);
        return resolved instanceof EdaProperties.Publishers.PostgresConfig postgres ? postgres : null;
    }

    private String qualifiedTable(EdaProperties.Publishers.PostgresConfig config) {
        return schema(config) + "." + tableName(config);
    }

    private String schema(EdaProperties.Publishers.PostgresConfig config) {
        return config != null && config.getSchema() != null ? config.getSchema() : "public";
    }

    private String tableName(EdaProperties.Publishers.PostgresConfig config) {
        return config != null && config.getOutboxTable() != null ? config.getOutboxTable() : "firefly_eda_outbox";
    }

    private Map<String, Object> mergeHeaders(Map<String, Object> headers, Object event) {
        Map<String, Object> merged = new HashMap<>();
        if (headers != null) {
            headers.forEach((k, v) -> {
                if (v != null) {
                    merged.put(k, v);
                }
            });
        }
        merged.put("publisher_type", PublisherType.POSTGRES.name());
        merged.put("connection_id", connectionId);
        merged.put("event_class", event.getClass().getName());
        merged.put("published_at", Instant.now().toString());
        return merged;
    }

    private String headersToJson(Map<String, Object> headers) {
        try {
            return objectMapper.writeValueAsString(headers);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize headers map; falling back to empty JSON object", e);
            return "{}";
        }
    }

    private String eventTypeFrom(Map<String, Object> headers, Object event) {
        if (headers != null) {
            Object explicit = headers.get("eventType");
            if (explicit == null) {
                explicit = headers.get("event_type");
            }
            if (explicit != null) {
                return explicit.toString();
            }
        }
        return event.getClass().getSimpleName();
    }

    private String extractStringHeader(Map<String, Object> headers, String key) {
        if (headers == null) {
            return null;
        }
        Object value = headers.get(key);
        return value != null ? value.toString() : null;
    }
}

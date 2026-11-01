/*
 * Copyright 2024-2026 Firefly Software Foundation
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0
 */
package org.fireflyframework.eda.serialization;

import org.apache.avro.Schema;
import org.apache.avro.SchemaBuilder;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificRecordBase;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AvroReadBudgetTest {
    public static class ReflectedNode {
        public String name;
        @org.apache.avro.reflect.Nullable public ReflectedNode next;
        static int constructions;
        public ReflectedNode() { constructions++; }
    }

    public static class SpecificNode extends SpecificRecordBase {
        public static final Schema SCHEMA$;
        static {
            SCHEMA$ = Schema.createRecord("SpecificNode", null,
                    "org.fireflyframework.eda.serialization.AvroReadBudgetTest", false);
            SCHEMA$.setFields(List.of(
                    new Schema.Field("name", Schema.create(Schema.Type.STRING)),
                    new Schema.Field("next", Schema.createUnion(Schema.create(Schema.Type.NULL), SCHEMA$))));
        }
        public CharSequence name;
        public SpecificNode next;
        static int constructions;
        public SpecificNode() { constructions++; }
        @Override public Schema getSchema() { return SCHEMA$; }
        @Override public Object get(int index) {
            return switch (index) { case 0 -> name; case 1 -> next; default -> throw new IndexOutOfBoundsException(); };
        }
        @Override public void put(int index, Object value) {
            switch (index) {
                case 0 -> name = (CharSequence) value;
                case 1 -> next = (SpecificNode) value;
                default -> throw new IndexOutOfBoundsException();
            }
        }
    }

    public static class Empty {
        static int constructions;
        public Empty() { constructions++; }
    }
    public static class ReflectedMany { public List<Empty> values; public ReflectedMany() { } }
    public static class SpecificEmpty extends SpecificRecordBase {
        static int constructions;
        public SpecificEmpty() { constructions++; }
        public static final Schema SCHEMA$ = SchemaBuilder.record("SpecificEmpty")
                .namespace("org.fireflyframework.eda.serialization.AvroReadBudgetTest").fields().endRecord();
        @Override public Schema getSchema() { return SCHEMA$; }
        @Override public Object get(int index) { throw new IndexOutOfBoundsException(); }
        @Override public void put(int index, Object value) { throw new IndexOutOfBoundsException(); }
    }
    public static class SpecificMany extends SpecificRecordBase {
        public static final Schema SCHEMA$ = SchemaBuilder.record("SpecificMany")
                .namespace("org.fireflyframework.eda.serialization.AvroReadBudgetTest").fields()
                .name("values").type().array().items(SpecificEmpty.SCHEMA$).noDefault().endRecord();
        public List<SpecificEmpty> values;
        @Override public Schema getSchema() { return SCHEMA$; }
        @Override public Object get(int index) {
            if (index != 0) { throw new IndexOutOfBoundsException(); }
            return values;
        }
        @Override @SuppressWarnings("unchecked") public void put(int index, Object value) {
            if (index != 0) { throw new IndexOutOfBoundsException(); }
            values = (List<SpecificEmpty>) value;
        }
    }

    @Test void shallowIncomingReflectedRecursionRemainsSupported() throws IOException {
        var value = new AvroMessageSerializerImpl().deserialize(nodes(4), ReflectedNode.class);
        assertThat(value.name).isEmpty();
        assertThat(value.next.next.next.next).isNull();
    }

    @Test void shallowIncomingSpecificRecursionRemainsSupported() throws IOException {
        var value = new AvroMessageSerializerImpl().deserialize(nodes(4), SpecificNode.class);
        assertThat(value.name.toString()).isEmpty();
        assertThat(value.next.next.next.next).isNull();
    }

    @Test void deepIncomingReflectedRecursionIsRejectedBeforeConstructionExhaustsTheStack() throws IOException {
        var serializer = new AvroMessageSerializerImpl();
        byte[] data = nodes(20_000);
        ReflectedNode.constructions = 0;
        assertThatThrownBy(() -> serializer.deserialize(data, ReflectedNode.class))
                .isInstanceOf(SerializationException.class)
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage("Avro read traversal exceeds bounds");
        assertThat(ReflectedNode.constructions).isBetween(1, 32);
        assertThat(serializer.deserialize(nodes(2), ReflectedNode.class).next.next).isNull();
    }

    @Test void deepIncomingSpecificRecursionIsRejectedBeforeConstructionExhaustsTheStack() throws IOException {
        var serializer = new AvroMessageSerializerImpl();
        byte[] data = nodes(20_000);
        SpecificNode.constructions = 0;
        assertThatThrownBy(() -> serializer.deserialize(data, SpecificNode.class))
                .isInstanceOf(SerializationException.class)
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage("Avro read traversal exceeds bounds");
        assertThat(SpecificNode.constructions).isBetween(1, 32);
        assertThat(serializer.deserialize(nodes(2), SpecificNode.class).next.next).isNull();
    }

    @Test void incomingReflectedRecordVisitsAreRejectedDuringReading() throws IOException {
        byte[] data = emptyRecords(100_001);
        Empty.constructions = 0;
        assertThatThrownBy(() -> new AvroMessageSerializerImpl().deserialize(data, ReflectedMany.class))
                .isInstanceOf(SerializationException.class)
                .hasRootCauseMessage("Avro read traversal exceeds bounds");
        assertThat(Empty.constructions).isBetween(1, 100_000);
    }

    @Test void incomingSpecificRecordVisitsAreRejectedDuringReading() throws IOException {
        byte[] data = emptyRecords(100_001);
        SpecificEmpty.constructions = 0;
        assertThatThrownBy(() -> new AvroMessageSerializerImpl().deserialize(data, SpecificMany.class))
                .isInstanceOf(SerializationException.class)
                .hasRootCauseMessage("Avro read traversal exceeds bounds");
        assertThat(SpecificEmpty.constructions).isBetween(1, 100_000);
    }

    private static byte[] nodes(int count) throws IOException {
        var output = new ByteArrayOutputStream();
        var encoder = EncoderFactory.get().binaryEncoder(output, null);
        for (int index = 0; index < count; index++) {
            encoder.writeString("");
            encoder.writeIndex(index + 1 < count ? 1 : 0);
        }
        encoder.flush();
        return output.toByteArray();
    }

    private static byte[] emptyRecords(int count) throws IOException {
        var output = new ByteArrayOutputStream();
        var encoder = EncoderFactory.get().binaryEncoder(output, null);
        encoder.writeArrayStart();
        encoder.setItemCount(count);
        encoder.writeArrayEnd();
        encoder.flush();
        return output.toByteArray();
    }
}

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

package org.fireflyframework.eda.serialization;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.*;
import org.apache.avro.reflect.ReflectDatumWriter;
import org.apache.avro.specific.SpecificDatumWriter;
import org.apache.avro.specific.SpecificRecord;
import org.fireflyframework.eda.serialization.avro.DeclaredAvroData;
import org.fireflyframework.eda.serialization.avro.DeclaredAvroReaders;
import org.fireflyframework.eda.serialization.avro.DeclaredAvroTypes;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.util.Objects;

/** Avro serialization with operation-local authority from exact declared class tokens. */
@Component("avroMessageSerializer")
@ConditionalOnClass(Schema.class)
public class AvroMessageSerializerImpl implements MessageSerializer {
    private final AvroTypeBindings bindings;

    public AvroMessageSerializerImpl() {
        this(AvroTypeBindings.strict());
    }

    public AvroMessageSerializerImpl(AvroTypeBindings bindings) {
        this.bindings = Objects.requireNonNull(bindings, "Avro type bindings");
        Class<?> objectRoot = bindings.objectRoot();
        if (objectRoot != null) {
            if (objectRoot == Object.class || objectRoot.isInterface() || Modifier.isAbstract(objectRoot.getModifiers())) {
                throw new IllegalArgumentException("Object binding requires one concrete Avro root");
            }
            new DeclaredAvroTypes(objectRoot, bindings);
        }
    }

    @Override
    public byte[] serialize(Object payload) throws SerializationException {
        if (payload == null) { return new byte[0]; }
        if (payload instanceof byte[] bytes) { return bytes; }
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(output, null);
            if (payload instanceof GenericRecord generic && !(payload instanceof SpecificRecord)) {
                DeclaredAvroTypes.validateGeneric(generic.getSchema());
                DeclaredAvroTypes.validateGenericValue(generic, generic.getSchema());
                new GenericDatumWriter<GenericRecord>(generic.getSchema()).write(generic, encoder);
            } else {
                DeclaredAvroTypes types = new DeclaredAvroTypes(payload.getClass(), bindings);
                Schema schema;
                DatumWriter<Object> writer;
                if (payload instanceof SpecificRecord specific) {
                    var model = DeclaredAvroData.specific(types);
                    schema = snapshotSpecificSchema(payload.getClass(), types);
                    if (!schema.equals(specific.getSchema())) { throw new IllegalArgumentException("Specific record schema differs from its declared class"); }
                    writer = new SpecificDatumWriter<>(schema, model);
                } else {
                    var model = DeclaredAvroData.reflection(types);
                    schema = model.getSchema(payload.getClass());
                    writer = new ReflectDatumWriter<>(schema, model);
                }
                types.bind(schema);
                types.validateValue(payload, schema);
                writer.write(payload, encoder);
            }
            encoder.flush();
            return output.toByteArray();
        } catch (IOException | RuntimeException error) {
            throw new SerializationException("Failed to serialize object with Avro", error);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T deserialize(byte[] data, Class<T> targetType) throws SerializationException {
        if (data == null || data.length == 0) { return null; }
        if (targetType == byte[].class) { return (T) data; }
        try {
            Class<?> contract = targetType == Object.class ? bindings.objectRoot() : targetType;
            if (contract == null) { throw new IllegalArgumentException("Nonempty Object decoding requires one explicit root Class token"); }
            if (GenericRecord.class.isAssignableFrom(contract) && !SpecificRecord.class.isAssignableFrom(contract)) {
                throw new IllegalArgumentException("GenericRecord deserialization requires a separate schema contract");
            }
            DeclaredAvroTypes types = new DeclaredAvroTypes(contract, bindings);
            Schema schema;
            DatumReader<Object> reader;
            if (SpecificRecord.class.isAssignableFrom(contract)) {
                var model = DeclaredAvroData.specific(types);
                schema = snapshotSpecificSchema(contract, types);
                types.bind(schema);
                reader = DeclaredAvroReaders.specific(schema, model, types);
            } else {
                var model = DeclaredAvroData.reflection(types);
                schema = model.getSchema(contract);
                types.bind(schema);
                reader = DeclaredAvroReaders.reflection(schema, model, types);
            }
            Object value = reader.read(null, DecoderFactory.get().binaryDecoder(data, null));
            types.validateValue(value, schema);
            return (T) value;
        } catch (IOException | RuntimeException error) {
            throw new SerializationException("Failed to deserialize Avro data", error);
        }
    }

    private static Schema snapshotSpecificSchema(Class<?> type, DeclaredAvroTypes types) throws IOException {
        Schema declared = specificSchema(type);
        types.bind(declared);
        return new Schema.Parser().parse(declared.toString());
    }

    private static Schema specificSchema(Class<?> type) throws IOException {
        try {
            // Generated records expose SCHEMA$; read it from the exact approved class, never a wire name.
            Schema schema = (Schema) type.getField("SCHEMA$").get(null);
            return schema;
        } catch (ReflectiveOperationException | ClassCastException error) {
            throw new IOException("SpecificRecord requires its declared public SCHEMA$", error);
        }
    }

    @Override public SerializationFormat getFormat() { return SerializationFormat.AVRO; }
    @Override public String getContentType() { return SerializationFormat.AVRO.getContentType(); }
    @Override public int getPriority() { return 80; }

    @Override
    public boolean canSerialize(Class<?> type) {
        if (type == null) { return false; }
        if (type == SpecificRecord.class || GenericRecord.class.isAssignableFrom(type) && !SpecificRecord.class.isAssignableFrom(type)) { return true; }
        if (type.isPrimitive() || type.isEnum() || type.isInterface() || type.getName().startsWith("java.time.")) { return false; }
        try {
            new DeclaredAvroTypes(type, bindings);
            return true;
        } catch (RuntimeException unsupported) {
            return false;
        }
    }
}

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

package org.fireflyframework.eda.serialization.avro;

import org.apache.avro.Schema;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.Decoder;
import org.apache.avro.io.ResolvingDecoder;
import org.apache.avro.reflect.ReflectData;
import org.apache.avro.reflect.ReflectDatumReader;
import org.apache.avro.specific.SpecificData;
import org.apache.avro.specific.SpecificDatumReader;

import java.io.IOException;
import java.nio.ByteBuffer;

/** Override class-loading factories while retaining the SDK's binary decoder and allocation guards. */
public final class DeclaredAvroReaders {
    private DeclaredAvroReaders() { }

    public static <T> DatumReader<T> reflection(Schema schema, ReflectData data, DeclaredAvroTypes types) {
        return new ReflectDatumReader<T>(schema, schema, data) {
            private final ReadBudget budget = new ReadBudget();
            @Override protected Object readWithoutConversion(Object old, Schema expected, ResolvingDecoder decoder) throws IOException {
                budget.enter();
                try { return super.readWithoutConversion(old, expected, decoder); }
                finally { budget.exit(); }
            }

            @Override protected Object newArray(Object old, int size, Schema expected) { return types.newArray(size, expected); }
            @Override protected Object newMap(Object old, int size) { return new java.util.LinkedHashMap<>(); }
            @Override protected Class<?> findStringClass(Schema expected) { types.resolve(expected); return String.class; }
            @Override protected Object readBytes(Object old, Schema expected, Decoder decoder) throws IOException {
                ByteBuffer buffer = decoder.readBytes(null);
                if (types.resolve(expected) == byte[].class) {
                    byte[] bytes = new byte[buffer.remaining()];
                    buffer.get(bytes);
                    return bytes;
                }
                return buffer;
            }
        };
    }

    public static <T> DatumReader<T> specific(Schema schema, SpecificData data, DeclaredAvroTypes types) {
        return new SpecificDatumReader<T>(schema, schema, data) {
            private final ReadBudget budget = new ReadBudget();
            @Override protected Object readWithoutConversion(Object old, Schema expected, ResolvingDecoder decoder) throws IOException {
                budget.enter();
                try { return super.readWithoutConversion(old, expected, decoder); }
                finally { budget.exit(); }
            }

            @Override protected Class<?> findStringClass(Schema expected) {
                types.resolve(expected);
                return String.class;
            }
        };
    }

    private static final class ReadBudget {
        private int depth;
        private int visits;

        void enter() {
            if (depth > 32 || ++visits > 100_000) {
                throw new IllegalArgumentException("Avro read traversal exceeds bounds");
            }
            depth++;
        }

        void exit() { depth--; }
    }

}

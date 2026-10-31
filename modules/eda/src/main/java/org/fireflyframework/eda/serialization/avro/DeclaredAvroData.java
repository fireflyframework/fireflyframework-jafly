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
import org.apache.avro.reflect.ReflectData;
import org.apache.avro.reflect.AvroDoc;
import org.apache.avro.specific.SpecificData;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

/** Per-operation Avro models with exact, prevalidated class resolution. */
public final class DeclaredAvroData {
    private DeclaredAvroData() { }

    public static ReflectData reflection(DeclaredAvroTypes types) { return new Reflection(types); }
    public static SpecificData specific(DeclaredAvroTypes types) { return new Specific(types); }

    private static final class Reflection extends ReflectData {
        private final DeclaredAvroTypes types;
        private final Map<Type, Schema> recursive = new HashMap<>();
        Reflection(DeclaredAvroTypes types) {
            this.types = types;
            addLogicalTypeConversions(this);
            setFastReaderEnabled(false);
        }
        @Override public Class<?> getClass(Schema schema) { return types.resolve(schema); }

        @Override protected Schema createSchema(Type type, Map<String, Schema> names) {
            if (!types.reflectedRecord(type)) { return super.createSchema(type, names); }
            Schema previous = recursive.get(type);
            if (previous != null) { return previous; }
            Class<?> concrete = (Class<?>) type;
            String namespace = concrete.getEnclosingClass() == null ? concrete.getPackageName() : concrete.getEnclosingClass().getName().replace('$', '.');
            // Avro 1.12.1 registers record names after fields; the placeholder permits bounded nullable recursion.
            AvroDoc doc = concrete.getAnnotation(AvroDoc.class);
            Schema placeholder = Schema.createRecord(concrete.getSimpleName(), doc == null ? null : doc.value(), namespace, false);
            recursive.put(type, placeholder);
            Schema generated = super.createSchema(type, names);
            placeholder.setFields(generated.getFields().stream().map(field -> new Schema.Field(field, field.schema())).toList());
            for (String alias : generated.getAliases()) { placeholder.addAlias(alias); }
            generated.getObjectProps().forEach(placeholder::addProp);
            names.put(concrete.getName(), placeholder);
            return placeholder;
        }
    }

    private static final class Specific extends SpecificData {
        private final DeclaredAvroTypes types;
        Specific(DeclaredAvroTypes types) {
            this.types = types;
            addLogicalTypeConversions(this);
            setFastReaderEnabled(false);
        }
        @Override public Class<?> getClass(Schema schema) { return types.resolve(schema); }
        @Override public boolean useCustomCoders() { return false; }
        @Override public Object createFixed(Object old, Schema schema) {
            var conversion = getConversionFor(schema.getLogicalType());
            if (conversion != null && conversion.getConvertedType() == types.resolve(schema)) {
                return new org.apache.avro.generic.GenericData.Fixed(schema);
            }
            return super.createFixed(old, schema);
        }
    }
}

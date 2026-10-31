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
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.generic.GenericFixed;
import org.apache.avro.reflect.*;
import org.apache.avro.specific.*;
import org.fireflyframework.eda.serialization.AvroTypeBindings;

import java.lang.reflect.*;
import java.nio.ByteBuffer;
import java.util.*;

/** Operation-local authority derived from exact caller-supplied class tokens. */
public final class DeclaredAvroTypes {
    private static final int MAX_TYPES = 256;
    private static final int MAX_DEPTH = 32;
    private static final int MAX_FIELDS = 4096;
    private static final int MAX_UNION = 16;
    private static final int MAX_VISITS = 100_000;
    private static final Map<Class<?>, Set<String>> LOGICAL_TYPES = Map.of(
            UUID.class, Set.of("uuid"),
            java.math.BigDecimal.class, Set.of("big-decimal"),
            org.apache.avro.util.TimePeriod.class, Set.of("duration"),
            java.time.LocalDate.class, Set.of("date"),
            java.time.LocalTime.class, Set.of("time-millis", "time-micros"),
            java.time.LocalDateTime.class, Set.of("local-timestamp-millis", "local-timestamp-micros", "local-timestamp-nanos"),
            java.time.Instant.class, Set.of("timestamp-millis", "timestamp-micros", "timestamp-nanos"));
    private final Map<Type, Shape> shapes = new HashMap<>();
    private final Map<String, Class<?>> names = new HashMap<>();
    private final IdentityHashMap<Schema, Shape> schemas = new IdentityHashMap<>();
    private final Class<?> root;
    private int fields;

    private static final class Shape {
        final Class<?> type;
        final List<Shape> arguments = new ArrayList<>();
        final Map<String, Shape> fields = new LinkedHashMap<>();
        Shape(Class<?> type) { this.type = type; }
    }

    public DeclaredAvroTypes(Class<?> root, AvroTypeBindings bindings) {
        this.root = Objects.requireNonNull(root, "Avro root type");
        shape(root, 0);
        for (Class<?> extra : bindings.schemaTypes()) {
            shape(extra, 0);
        }
    }

    public Class<?> root() { return root; }

    private Shape shape(Type declared, int depth) {
        require(depth <= MAX_DEPTH, "Avro declared type depth exceeds 32");
        Shape existing = shapes.get(declared);
        if (existing != null) { return existing; }
        require(shapes.size() < MAX_TYPES, "Avro declared type count exceeds 256");
        Class<?> type;
        Type[] arguments;
        if (declared instanceof Class<?> concrete) {
            type = concrete;
            arguments = new Type[0];
        } else if (declared instanceof ParameterizedType parameterized && parameterized.getRawType() instanceof Class<?> concrete) {
            type = concrete;
            arguments = parameterized.getActualTypeArguments();
        } else {
            throw refused("Unresolved Avro declared type");
        }
        rejectMetadata(type);
        Shape result = new Shape(type);
        shapes.put(declared, result);
        if (scalar(type)) { return result; }
        if (type.isArray()) {
            result.arguments.add(shape(type.getComponentType(), depth + 1));
            return result;
        }
        if (Collection.class.isAssignableFrom(type)) {
            require(container(type), "Unsupported Avro collection factory");
            require(arguments.length == 1, "Avro collections require a declared element type");
            result.arguments.add(shape(arguments[0], depth + 1));
            return result;
        }
        if (Map.class.isAssignableFrom(type)) {
            require(type == Map.class || type == HashMap.class || type == LinkedHashMap.class, "Unsupported Avro map factory");
            require(arguments.length == 2 && arguments[0] == String.class, "Avro maps require declared String keys and typed values");
            result.arguments.add(shape(arguments[1], depth + 1));
            return result;
        }
        require(type != Object.class && !type.isInterface() && !Modifier.isAbstract(type.getModifiers()), "Avro requires a concrete declared type or finite union");
        require(!type.getName().startsWith("java.") && !type.getName().startsWith("javax.") && !type.getName().startsWith("jdk."), "Unsupported platform Avro type");
        register(type);
        if (type.isEnum() || SpecificFixed.class.isAssignableFrom(type)) { return result; }
        require(type.getEnclosingClass() == null || Modifier.isStatic(type.getModifiers()), "Avro nested classes must be static");
        for (Class<?> current = type; current != null && current != Object.class && current != SpecificRecordBase.class; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers()) || field.isAnnotationPresent(AvroIgnore.class)) { continue; }
                require(++fields <= MAX_FIELDS, "Avro field count exceeds 4096");
                rejectMetadata(field);
                Union union = field.getAnnotation(Union.class);
                Shape child;
                if (union != null) {
                    require(union.value().length > 0 && union.value().length <= MAX_UNION, "Avro union branch count exceeds 16");
                    child = new Shape(Object.class);
                    for (Class<?> branch : union.value()) { child.arguments.add(shape(branch, depth + 1)); }
                } else if (SpecificRecord.class.isAssignableFrom(type) && field.getType() == Object.class) {
                    child = new Shape(Object.class);
                } else {
                    child = shape(field.getGenericType(), depth + 1);
                }
                AvroName renamed = field.getAnnotation(AvroName.class);
                require(result.fields.putIfAbsent(renamed == null ? field.getName() : renamed.value(), child) == null, "Duplicate Avro field name");
            }
        }
        return result;
    }

    private void register(Class<?> type) {
        for (String name : List.of(type.getName(), type.getName().replace('$', '.'))) {
            Class<?> previous = names.putIfAbsent(name, type);
            require(previous == null || previous == type, "Ambiguous Avro class identity");
        }
    }

    private static void rejectMetadata(AnnotatedElement element) {
        require(!element.isAnnotationPresent(AvroEncode.class) && !element.isAnnotationPresent(Stringable.class)
                && !element.isAnnotationPresent(AvroSchema.class), "Custom Avro executable/schema metadata is unsupported");
        if (element instanceof Class<?> type) {
            require(!type.isAnnotationPresent(Union.class), "Class-level Avro unions require an explicit field contract");
        }
    }

    public boolean reflectedRecord(Type type) {
        return type instanceof Class<?> concrete && names.containsValue(concrete) && !concrete.isEnum()
                && !SpecificRecord.class.isAssignableFrom(concrete) && !SpecificFixed.class.isAssignableFrom(concrete);
    }

    public void bind(Schema schema) { bind(schema, shapes.get(root), 0); }

    private void bind(Schema schema, Shape expected, int depth) {
        require(depth <= MAX_DEPTH, "Avro schema depth exceeds 32");
        Shape previous = schemas.get(schema);
        if (previous != null) {
            require(previous.type == expected.type, "Conflicting Avro schema bindings");
            return;
        }
        if (schema.getType() == Schema.Type.UNION) {
            require(schema.getTypes().size() <= MAX_UNION, "Avro schema union exceeds 16 branches");
            schemas.put(schema, expected);
            List<Schema> nonNull = schema.getTypes().stream().filter(branch -> branch.getType() != Schema.Type.NULL).toList();
            require(expected.type == Object.class || nonNull.size() == 1, "Undeclared Avro union");
            for (Schema branch : schema.getTypes()) {
                Shape target;
                if (branch.getType() == Schema.Type.NULL) { target = new Shape(void.class); }
                else if (expected.type != Object.class) { target = expected; }
                else if (!expected.arguments.isEmpty()) {
                    target = expected.arguments.stream().filter(candidate -> matchesSchema(candidate.type, branch)).findFirst().orElseThrow(() -> refused("Undeclared Avro union branch"));
                } else { target = declaredBranch(branch); }
                bind(branch, target, depth + 1);
            }
            return;
        }
        require(matchesSchema(expected.type, schema), "Avro schema disagrees with the declared type");
        validateHints(schema, expected.type);
        schemas.put(schema, expected);
        switch (schema.getType()) {
            case RECORD -> {
                require(schema.getFields().size() == expected.fields.size(), "Avro record field shape differs from declaration");
                for (Schema.Field field : schema.getFields()) {
                    Shape child = expected.fields.get(field.name());
                    require(child != null, "Undeclared Avro field");
                    if (child.type == Object.class && child.arguments.isEmpty()) { child = declaredBranch(field.schema()); }
                    bind(field.schema(), child, depth + 1);
                }
            }
            case ARRAY -> bind(schema.getElementType(), expected.arguments.getFirst(), depth + 1);
            case MAP -> bind(schema.getValueType(), expected.arguments.getFirst(), depth + 1);
            case ENUM -> require(schema.getEnumSymbols().equals(Arrays.stream(expected.type.getEnumConstants()).map(value -> ((Enum<?>) value).name()).toList()), "Avro enum symbols differ from the declared class");
            case FIXED -> {
                if (!LOGICAL_TYPES.containsKey(expected.type)) {
                    FixedSize size = expected.type.getAnnotation(FixedSize.class);
                    require(size != null && size.value() == schema.getFixedSize(), "Avro fixed size differs from the declared class");
                }
            }
            default -> { }
        }
    }

    private Shape declaredBranch(Schema schema) {
        if (schema.getType() == Schema.Type.UNION) { return new Shape(Object.class); }
        Class<?> type = switch (schema.getType()) {
            case RECORD, ENUM, FIXED -> names.get(schema.getFullName());
            case STRING -> CharSequence.class;
            case BYTES -> ByteBuffer.class;
            case INT -> int.class;
            case LONG -> long.class;
            case FLOAT -> float.class;
            case DOUBLE -> double.class;
            case BOOLEAN -> boolean.class;
            case NULL -> void.class;
            default -> null;
        };
        require(type != null, "Specific schema branch requires an exact additional Class token");
        Shape result = shapes.get(type);
        return result == null ? shape(type, 0) : result;
    }

    private boolean matchesSchema(Class<?> type, Schema schema) {
        if (LOGICAL_TYPES.containsKey(type)) {
            if (schema.getLogicalType() == null || !LOGICAL_TYPES.get(type).contains(schema.getLogicalType().getName())) { return false; }
            return switch (schema.getLogicalType().getName()) {
                case "uuid" -> schema.getType() == Schema.Type.STRING || schema.getType() == Schema.Type.FIXED && schema.getFixedSize() == 16;
                case "duration" -> schema.getType() == Schema.Type.FIXED && schema.getFixedSize() == 12;
                case "big-decimal" -> schema.getType() == Schema.Type.BYTES;
                case "date", "time-millis" -> schema.getType() == Schema.Type.INT;
                default -> schema.getType() == Schema.Type.LONG;
            };
        }
        return switch (schema.getType()) {
            case RECORD -> names.get(schema.getFullName()) == type && !type.isEnum() && !SpecificFixed.class.isAssignableFrom(type);
            case ENUM -> names.get(schema.getFullName()) == type && type.isEnum();
            case FIXED -> names.get(schema.getFullName()) == type && SpecificFixed.class.isAssignableFrom(type);
            case ARRAY -> type.isArray() && type != byte[].class || Collection.class.isAssignableFrom(type);
            case MAP -> Map.class.isAssignableFrom(type);
            case STRING -> type == String.class || type == CharSequence.class;
            case BYTES -> type == byte[].class || type == ByteBuffer.class;
            case INT -> type == int.class || type == Integer.class || type == byte.class || type == Byte.class || type == short.class || type == Short.class || type == char.class || type == Character.class;
            case LONG -> type == long.class || type == Long.class;
            case FLOAT -> type == float.class || type == Float.class;
            case DOUBLE -> type == double.class || type == Double.class;
            case BOOLEAN -> type == boolean.class || type == Boolean.class;
            case NULL -> type == void.class || type == Void.class;
            case UNION -> false;
        };
    }

    private static void validateHints(Schema schema, Class<?> type) {
        String hint = schema.getProp(SpecificData.CLASS_PROP);
        String expectedHint = type == byte.class ? Byte.class.getName() : type == short.class ? Short.class.getName()
                : type == char.class ? Character.class.getName() : type.getName();
        require(hint == null || hint.equals(expectedHint), "Avro java-class is not the declared type");
        require(schema.getProp(SpecificData.KEY_CLASS_PROP) == null, "Avro key class hints are unsupported");
        String element = schema.getProp(SpecificData.ELEMENT_PROP);
        require(element == null || type.isArray() && element.equals(type.getComponentType().getName()), "Avro element class is not the declared component");
    }

    public Class<?> resolve(Schema schema) {
        Shape shape = schemas.get(schema);
        require(shape != null, "Unbound Avro schema");
        return shape.type;
    }

    public Object newArray(int size, Schema schema) {
        Class<?> type = resolve(schema);
        if (type.isArray()) { return Array.newInstance(type.getComponentType(), size); }
        if (type.isAssignableFrom(ArrayList.class)) { return new ArrayList<>(); }
        if (type == LinkedHashSet.class) { return new LinkedHashSet<>(); }
        if (type.isAssignableFrom(HashSet.class)) { return new HashSet<>(); }
        throw refused("Unsupported Avro collection factory");
    }

    public void validateValue(Object value, Schema schema) {
        validateValue(value, schema, new IdentityHashMap<>(), new int[1], 0);
    }

    private void validateValue(Object value, Schema schema, IdentityHashMap<Object, Boolean> active, int[] visits, int depth) {
        require(depth <= MAX_DEPTH && ++visits[0] <= MAX_VISITS, "Avro value traversal exceeds bounds");
        if (schema.getType() == Schema.Type.UNION) {
            Schema branch = schema.getTypes().stream().filter(candidate -> accepts(value, resolve(candidate))).findFirst().orElseThrow(() -> refused("Value is outside its declared Avro union"));
            validateValue(value, branch, active, visits, depth + 1);
            return;
        }
        Class<?> type = resolve(schema);
        require(accepts(value, type), "Value is not the exact declared Avro type");
        if (value == null || scalar(type) || type.isEnum() || SpecificFixed.class.isAssignableFrom(type)) { return; }
        require(active.put(value, Boolean.TRUE) == null, "Cyclic Avro values are unsupported");
        try {
            switch (schema.getType()) {
                case ARRAY -> {
                    if (value instanceof Collection<?> collection) {
                        for (Object entry : collection) { validateValue(entry, schema.getElementType(), active, visits, depth + 1); }
                    } else {
                        for (int i = 0; i < Array.getLength(value); i++) { validateValue(Array.get(value, i), schema.getElementType(), active, visits, depth + 1); }
                    }
                }
                case MAP -> {
                    for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                        require(entry.getKey() instanceof String, "Avro map keys must be Strings");
                        validateValue(entry.getValue(), schema.getValueType(), active, visits, depth + 1);
                    }
                }
                case RECORD -> {
                    for (Schema.Field field : schema.getFields()) {
                        Object child = value instanceof SpecificRecord specific ? specific.get(field.pos()) : fieldValue(value, field.name());
                        validateValue(child, field.schema(), active, visits, depth + 1);
                    }
                }
                default -> { }
            }
        } finally { active.remove(value); }
    }

    private static Object fieldValue(Object value, String name) {
        for (Class<?> type = value.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers()) || field.isAnnotationPresent(AvroIgnore.class)) { continue; }
                AvroName renamed = field.getAnnotation(AvroName.class);
                if ((renamed == null ? field.getName() : renamed.value()).equals(name)) {
                    require(field.trySetAccessible(), "Avro field is inaccessible");
                    try { return field.get(value); } catch (IllegalAccessException error) { throw new IllegalArgumentException("Avro field is inaccessible", error); }
                }
            }
        }
        throw refused("Undeclared Avro field");
    }

    private static boolean accepts(Object value, Class<?> type) {
        if (value == null) { return type == void.class || type == Void.class; }
        if (type.isPrimitive()) {
            return type == int.class && value instanceof Integer || type == long.class && value instanceof Long
                    || type == boolean.class && value instanceof Boolean || type == float.class && value instanceof Float
                    || type == double.class && value instanceof Double || type == byte.class && value instanceof Byte
                    || type == short.class && value instanceof Short || type == char.class && value instanceof Character;
        }
        if (type == CharSequence.class) { return value instanceof String || value instanceof org.apache.avro.util.Utf8; }
        if (type == ByteBuffer.class) { return value instanceof ByteBuffer; }
        if (Collection.class.isAssignableFrom(type) || Map.class.isAssignableFrom(type)) {
            return type.isInstance(value) && value.getClass().getModule() == List.class.getModule() && value.getClass().getPackageName().equals("java.util");
        }
        return value.getClass() == type;
    }

    private static boolean scalar(Class<?> type) {
        return LOGICAL_TYPES.containsKey(type) || type.isPrimitive() || type == String.class || type == CharSequence.class || type == ByteBuffer.class || type == byte[].class
                || type == Integer.class || type == Long.class || type == Boolean.class || type == Float.class || type == Double.class
                || type == Byte.class || type == Short.class || type == Character.class || type == Void.class;
    }

    private static boolean container(Class<?> type) {
        return type == Collection.class || type == List.class || type == Set.class || type == ArrayList.class || type == HashSet.class || type == LinkedHashSet.class;
    }

    public static void validateGeneric(Schema schema) {
        validateGeneric(schema, Collections.newSetFromMap(new IdentityHashMap<>()), 0, new int[1]);
    }

    private static void validateGeneric(Schema schema, Set<Schema> seen, int depth, int[] nodes) {
        require(depth <= MAX_DEPTH && ++nodes[0] <= MAX_FIELDS, "Generic Avro schema exceeds bounds");
        if (!seen.add(schema)) { return; }
        require(schema.getProp(SpecificData.CLASS_PROP) == null && schema.getProp(SpecificData.KEY_CLASS_PROP) == null && schema.getProp(SpecificData.ELEMENT_PROP) == null, "Generic schemas cannot grant Java class authority");
        switch (schema.getType()) {
            case RECORD -> { for (Schema.Field field : schema.getFields()) { validateGeneric(field.schema(), seen, depth + 1, nodes); } }
            case UNION -> { require(schema.getTypes().size() <= MAX_UNION, "Generic Avro union exceeds bounds");for (Schema branch : schema.getTypes()) { validateGeneric(branch, seen, depth + 1, nodes); } }
            case ARRAY -> validateGeneric(schema.getElementType(), seen, depth + 1, nodes);
            case MAP -> validateGeneric(schema.getValueType(), seen, depth + 1, nodes);
            default -> { }
        }
    }

    public static void validateGenericValue(Object value, Schema schema) {
        validateGenericValue(value, schema, new IdentityHashMap<>(), new int[1], 0);
    }

    private static void validateGenericValue(Object value, Schema schema, IdentityHashMap<Object, Boolean> active, int[] visits, int depth) {
        require(depth <= MAX_DEPTH && ++visits[0] <= MAX_VISITS, "Generic Avro value traversal exceeds bounds");
        if (schema.getType() == Schema.Type.UNION) {
            Schema branch = schema.getTypes().stream().filter(candidate -> genericMatches(value, candidate)).findFirst().orElseThrow(() -> refused("Generic value is outside its union"));
            validateGenericValue(value, branch, active, visits, depth + 1);
            return;
        }
        require(genericMatches(value, schema), "Generic value differs from its schema");
        if (value == null || schema.getType() != Schema.Type.RECORD && schema.getType() != Schema.Type.ARRAY && schema.getType() != Schema.Type.MAP) { return; }
        require(active.put(value, Boolean.TRUE) == null, "Cyclic generic Avro values are unsupported");
        try {
            switch (schema.getType()) {
                case RECORD -> { for (Schema.Field field : schema.getFields()) { validateGenericValue(((GenericRecord) value).get(field.pos()), field.schema(), active, visits, depth + 1); } }
                case ARRAY -> { for (Object entry : (Collection<?>) value) { validateGenericValue(entry, schema.getElementType(), active, visits, depth + 1); } }
                case MAP -> { for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) { require(entry.getKey() instanceof CharSequence, "Generic Avro map keys require strings");validateGenericValue(entry.getValue(), schema.getValueType(), active, visits, depth + 1); } }
                default -> { }
            }
        } finally { active.remove(value); }
    }

    private static boolean genericMatches(Object value, Schema schema) {
        return switch (schema.getType()) {
            case NULL -> value == null;
            case RECORD -> value instanceof GenericRecord record && record.getSchema().equals(schema);
            case ARRAY -> value instanceof Collection<?>;
            case MAP -> value instanceof Map<?, ?>;
            case STRING -> value instanceof CharSequence;
            case BYTES -> value instanceof ByteBuffer;
            case INT -> value instanceof Integer;
            case LONG -> value instanceof Long;
            case FLOAT -> value instanceof Float;
            case DOUBLE -> value instanceof Double;
            case BOOLEAN -> value instanceof Boolean;
            case ENUM -> value instanceof org.apache.avro.generic.GenericEnumSymbol<?> symbol && symbol.getSchema().equals(schema);
            case FIXED -> value instanceof GenericFixed fixed && fixed.getSchema().equals(schema);
            case UNION -> false;
        };
    }

    private static void require(boolean condition, String message) { if (!condition) { throw refused(message); } }
    private static IllegalArgumentException refused(String message) { return new IllegalArgumentException(message); }
}

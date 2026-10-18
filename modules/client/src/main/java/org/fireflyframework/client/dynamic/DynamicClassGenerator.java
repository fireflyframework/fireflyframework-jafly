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

package org.fireflyframework.client.dynamic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Generates dynamic classes at runtime based on JSON schema.
 * 
 * <p>This class uses Java's dynamic proxy mechanism to create objects that behave
 * like DTOs without requiring compile-time class definitions.
 *
 * <p>The generated classes support:
 * <ul>
 *   <li>Getter methods for all fields</li>
 *   <li>Setter methods for all fields</li>
 *   <li>toString(), equals(), and hashCode()</li>
 *   <li>Nested object support</li>
 * </ul>
 *
 * <p><b>Note:</b> This implementation uses dynamic proxies which have some limitations:
 * <ul>
 *   <li>Cannot extend concrete classes (only interfaces)</li>
 *   <li>Reflection-based access is required</li>
 *   <li>Performance overhead compared to real DTOs</li>
 * </ul>
 *
 * <p>For production use with high performance requirements, consider:
 * <ul>
 *   <li>Using real DTOs when the schema is known</li>
 *   <li>Caching generated classes</li>
 *   <li>Using bytecode generation libraries like ByteBuddy or Javassist</li>
 * </ul>
 *
 * @author Firefly Software Foundation
 * @since 1.0.0
 */
public class DynamicClassGenerator {

    private static final Map<String, Class<?>> CLASS_CACHE = new ConcurrentHashMap<>();

    /**
     * Generates a dynamic class instance from a JSON node.
     *
     * @param className the name for the generated class
     * @param jsonNode the JSON node containing the data
     * @param objectMapper the ObjectMapper for conversions
     * @return an instance of the dynamically generated class
     */
    public static Object generateClass(String className, JsonNode jsonNode, ObjectMapper objectMapper) {
        if (!jsonNode.isObject()) {
            throw new IllegalArgumentException("Can only generate classes from JSON objects");
        }

        // Convert JSON to Map for easier access
        @SuppressWarnings("unchecked")
        Map<String, Object> data = objectMapper.convertValue(jsonNode, Map.class);

        // Create a dynamic proxy that implements Map-like access
        return createDynamicProxy(className, data);
    }

    /**
     * Creates a dynamic proxy that behaves like a DTO.
     */
    private static Object createDynamicProxy(String className, Map<String, Object> data) {
        // Create an invocation handler that handles method calls
        InvocationHandler handler = new DynamicObjectHandler(className, data);

        // Create a proxy that implements DynamicDTO interface
        return Proxy.newProxyInstance(
            DynamicClassGenerator.class.getClassLoader(),
            new Class<?>[] { DynamicDTO.class },
            handler
        );
    }

    /**
     * Interface that all dynamically generated classes implement.
     * This allows for type-safe access to common methods.
     */
    public interface DynamicDTO {
        /**
         * Gets a field value by name.
         *
         * @param fieldName the field name
         * @return the field value
         */
        Object get(String fieldName);

        /**
         * Sets a field value by name.
         *
         * @param fieldName the field name
         * @param value the value to set
         */
        void set(String fieldName, Object value);

        /**
         * Gets all field names.
         *
         * @return set of field names
         */
        Set<String> getFieldNames();

        /**
         * Converts to a Map.
         *
         * @return Map representation
         */
        Map<String, Object> toMap();

        /**
         * Gets the class name.
         *
         * @return the class name
         */
        String getClassName();
    }

    /**
     * Invocation handler for dynamic proxy objects.
     */
    private static class DynamicObjectHandler implements InvocationHandler {
        private final String className;
        private final Map<String, Object> data;

        public DynamicObjectHandler(String className, Map<String, Object> data) {
            this.className = className;
            this.data = new HashMap<>(data);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String methodName = method.getName();

            // Handle DynamicDTO interface methods
            switch (methodName) {
                case "get":
                    return data.get(args[0]);
                case "set":
                    data.put((String) args[0], args[1]);
                    return null;
                case "getFieldNames":
                    return data.keySet();
                case "toMap":
                    return new HashMap<>(data);
                case "getClassName":
                    return className;
                case "toString":
                    return className + data.toString();
                case "hashCode":
                    return data.hashCode();
                case "equals":
                    if (args[0] == null) return false;
                    if (args[0] == proxy) return true;
                    if (Proxy.isProxyClass(args[0].getClass())) {
                        InvocationHandler otherHandler = Proxy.getInvocationHandler(args[0]);
                        if (otherHandler instanceof DynamicObjectHandler) {
                            return data.equals(((DynamicObjectHandler) otherHandler).data);
                        }
                    }
                    return false;
            }

            // Handle getter methods (getXxx)
            if (methodName.startsWith("get") && methodName.length() > 3) {
                String fieldName = decapitalize(methodName.substring(3));
                return data.get(fieldName);
            }

            // Handle setter methods (setXxx)
            if (methodName.startsWith("set") && methodName.length() > 3 && args != null && args.length == 1) {
                String fieldName = decapitalize(methodName.substring(3));
                data.put(fieldName, args[0]);
                return null;
            }

            // Handle is methods (isXxx) for boolean fields
            if (methodName.startsWith("is") && methodName.length() > 2) {
                String fieldName = decapitalize(methodName.substring(2));
                return data.get(fieldName);
            }

            throw new UnsupportedOperationException("Method not supported: " + methodName);
        }

        private String decapitalize(String string) {
            if (string == null || string.isEmpty()) {
                return string;
            }
            char[] chars = string.toCharArray();
            chars[0] = Character.toLowerCase(chars[0]);
            return new String(chars);
        }
    }

    /**
     * Clears the class cache.
     * Useful for testing or when you want to regenerate classes.
     */
    public static void clearCache() {
        CLASS_CACHE.clear();
    }
}


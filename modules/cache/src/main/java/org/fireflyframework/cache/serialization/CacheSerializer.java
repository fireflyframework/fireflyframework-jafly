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

package org.fireflyframework.cache.serialization;

/**
 * Interface for serializing and deserializing cache values.
 * <p>
 * This interface provides a contract for converting objects to and from
 * their serialized representation for storage in cache systems.
 */
public interface CacheSerializer {

    /**
     * Serializes an object to its cache representation.
     *
     * @param object the object to serialize
     * @return the serialized representation
     * @throws SerializationException if serialization fails
     */
    Object serialize(Object object) throws SerializationException;

    /**
     * Deserializes a cache value back to an object.
     *
     * @param data the serialized data
     * @param type the target type for deserialization
     * @param <T> the target type
     * @return the deserialized object
     * @throws SerializationException if deserialization fails
     */
    <T> T deserialize(Object data, Class<T> type) throws SerializationException;

    /**
     * Checks if this serializer supports the given object type.
     *
     * @param type the type to check
     * @return true if the type is supported
     */
    boolean supports(Class<?> type);

    /**
     * Gets the serializer type identifier.
     *
     * @return the serializer type
     */
    String getType();
}
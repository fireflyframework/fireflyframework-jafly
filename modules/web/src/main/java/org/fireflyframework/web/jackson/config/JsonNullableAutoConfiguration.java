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
package org.fireflyframework.web.jackson.config;

import org.openapitools.jackson.nullable.JsonNullableModule;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/** Preserves generated DTO presence semantics through Boot's existing Jackson 2 builder. */
@AutoConfiguration(beforeName = "org.springframework.boot.jackson2.autoconfigure.Jackson2AutoConfiguration")
@ConditionalOnClass(name = {
        "com.fasterxml.jackson.databind.ObjectMapper",
        "org.openapitools.jackson.nullable.JsonNullableModule"
})
public class JsonNullableAutoConfiguration {
    /** Leaves application-provided nullable modules and ObjectMapper beans in control. */
    @Bean
    @ConditionalOnMissingBean(value = JsonNullableModule.class, name = "jsonNullableModule")
    public JsonNullableModule jsonNullableModule() {
        return new JsonNullableModule();
    }
}

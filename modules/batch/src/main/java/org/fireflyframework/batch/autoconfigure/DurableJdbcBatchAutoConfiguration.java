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
package org.fireflyframework.batch.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/** Default providers are deferred until ordinary project configurations have been registered. */
@AutoConfiguration(
        after = DurableBatchAutoConfiguration.class,
        beforeName = {
            "org.springframework.boot.batch.autoconfigure.BatchAutoConfiguration",
            "org.springframework.boot.batch.autoconfigure.BatchJobLauncherAutoConfiguration"
        })
@ConditionalOnClass(
        name = "org.springframework.batch.core.configuration.support.JdbcDefaultBatchConfiguration")
@ConditionalOnProperty(prefix = "firefly.batch.durable", name = "enabled", havingValue = "true")
@Import(DurableJdbcBatchAutoConfiguration.DefaultProviders.class)
public class DurableJdbcBatchAutoConfiguration {
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnMissingBean(
            value =
                    org.springframework.batch.core.configuration.support.DefaultBatchConfiguration
                            .class,
            annotation =
                    org.springframework.batch.core.configuration.annotation.EnableBatchProcessing
                            .class)
    @Import(JdbcBatchConfiguration.class)
    static class DefaultProviders {}
}

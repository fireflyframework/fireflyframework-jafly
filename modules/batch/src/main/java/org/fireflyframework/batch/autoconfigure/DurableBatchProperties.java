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

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Names of application-owned resources; no database or executor is created implicitly. */
@ConfigurationProperties("firefly.batch.durable")
public class DurableBatchProperties {
    private boolean enabled;
    private String dataSourceRef;
    private String transactionManagerRef;
    private String taskExecutorRef;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getDataSourceRef() {
        return dataSourceRef;
    }

    public void setDataSourceRef(String dataSourceRef) {
        this.dataSourceRef = dataSourceRef;
    }

    public String getTransactionManagerRef() {
        return transactionManagerRef;
    }

    public void setTransactionManagerRef(String transactionManagerRef) {
        this.transactionManagerRef = transactionManagerRef;
    }

    public String getTaskExecutorRef() {
        return taskExecutorRef;
    }

    public void setTaskExecutorRef(String taskExecutorRef) {
        this.taskExecutorRef = taskExecutorRef;
    }
}

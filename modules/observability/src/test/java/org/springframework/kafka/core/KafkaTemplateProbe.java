package org.springframework.kafka.core;

/**
 * Test double whose name matches the Kafka instrumentation prefix.
 */
public class KafkaTemplateProbe {

    public boolean enabled;

    public void setObservationEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}

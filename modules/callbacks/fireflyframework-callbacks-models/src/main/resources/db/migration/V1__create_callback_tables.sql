-- Authorized Domains Table
CREATE TABLE authorized_domains (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    domain VARCHAR(255) NOT NULL UNIQUE,
    organization VARCHAR(255),
    contact_email VARCHAR(255),
    verified BOOLEAN NOT NULL DEFAULT FALSE,
    verification_method VARCHAR(100),
    verification_token VARCHAR(500),
    verified_at TIMESTAMP,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    allowed_paths TEXT[],
    max_callbacks_per_minute INTEGER,
    ip_whitelist TEXT[],
    require_https BOOLEAN DEFAULT TRUE,
    tenant_id VARCHAR(100),
    notes TEXT,
    metadata TEXT,
    expires_at TIMESTAMP,
    last_callback_at TIMESTAMP,
    total_callbacks BIGINT DEFAULT 0,
    total_failed BIGINT DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    authorized_by VARCHAR(255)
);

CREATE INDEX idx_authorized_domains_domain ON authorized_domains(domain);
CREATE INDEX idx_authorized_domains_tenant_id ON authorized_domains(tenant_id);
CREATE INDEX idx_authorized_domains_active_verified ON authorized_domains(active, verified);

-- Event Subscriptions Table
CREATE TABLE event_subscriptions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    messaging_system_type VARCHAR(50) NOT NULL,
    connection_config TEXT,
    topic_or_queue VARCHAR(500) NOT NULL,
    consumer_group_id VARCHAR(255),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    consumer_properties TEXT,
    event_type_patterns TEXT[],
    max_concurrent_consumers INTEGER DEFAULT 1,
    polling_interval_ms INTEGER DEFAULT 1000,
    tenant_id VARCHAR(100),
    metadata TEXT,
    last_message_at TIMESTAMP,
    total_messages_received BIGINT DEFAULT 0,
    total_messages_failed BIGINT DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255)
);

CREATE INDEX idx_event_subscriptions_active ON event_subscriptions(active);
CREATE INDEX idx_event_subscriptions_tenant_id ON event_subscriptions(tenant_id);
CREATE INDEX idx_event_subscriptions_messaging_system ON event_subscriptions(messaging_system_type);
CREATE INDEX idx_event_subscriptions_topic_queue ON event_subscriptions(topic_or_queue);

-- Callback Configurations Table
CREATE TABLE callback_configurations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    url VARCHAR(2048) NOT NULL,
    http_method VARCHAR(10) NOT NULL DEFAULT 'POST',
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    subscribed_event_types TEXT[] NOT NULL,
    custom_headers TEXT,
    secret VARCHAR(500),
    signature_enabled BOOLEAN DEFAULT FALSE,
    signature_header VARCHAR(100) DEFAULT 'X-Signature',
    max_retries INTEGER DEFAULT 3,
    retry_delay_ms INTEGER DEFAULT 1000,
    retry_backoff_multiplier DOUBLE PRECISION DEFAULT 2.0,
    timeout_ms INTEGER DEFAULT 30000,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    tenant_id VARCHAR(100),
    filter_expression TEXT,
    metadata TEXT,
    failure_threshold INTEGER DEFAULT 10,
    failure_count INTEGER DEFAULT 0,
    last_success_at TIMESTAMP,
    last_failure_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255)
);

CREATE INDEX idx_callback_configs_status ON callback_configurations(status);
CREATE INDEX idx_callback_configs_active ON callback_configurations(active);
CREATE INDEX idx_callback_configs_tenant_id ON callback_configurations(tenant_id);
CREATE INDEX idx_callback_configs_event_types ON callback_configurations USING GIN(subscribed_event_types);

-- Callback Executions Table
CREATE TABLE callback_executions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    configuration_id UUID NOT NULL REFERENCES callback_configurations(id) ON DELETE CASCADE,
    event_type VARCHAR(255) NOT NULL,
    source_event_id UUID,
    status VARCHAR(50) NOT NULL,
    request_payload TEXT,
    response_status_code INTEGER,
    response_body TEXT,
    request_headers TEXT,
    response_headers TEXT,
    attempt_number INTEGER NOT NULL DEFAULT 1,
    max_attempts INTEGER,
    error_message TEXT,
    error_stack_trace TEXT,
    request_duration_ms BIGINT,
    next_retry_at TIMESTAMP,
    executed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    metadata TEXT
);

CREATE INDEX idx_callback_executions_config_id ON callback_executions(configuration_id);
CREATE INDEX idx_callback_executions_status ON callback_executions(status);
CREATE INDEX idx_callback_executions_event_type ON callback_executions(event_type);
CREATE INDEX idx_callback_executions_executed_at ON callback_executions(executed_at DESC);
CREATE INDEX idx_callback_executions_next_retry ON callback_executions(next_retry_at) WHERE status = 'FAILED_RETRYING';

-- Comments for documentation
COMMENT ON TABLE authorized_domains IS 'Whitelisted domains authorized to receive callbacks';
COMMENT ON TABLE event_subscriptions IS 'Dynamic subscriptions to messaging systems (Kafka, RabbitMQ, etc.)';
COMMENT ON TABLE callback_configurations IS 'HTTP callback configurations for webhook dispatch';
COMMENT ON TABLE callback_executions IS 'Audit trail of callback execution attempts';

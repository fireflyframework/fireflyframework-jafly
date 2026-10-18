-- Create audit_trails table for tracking rule engine operations
-- This table stores audit records for all rule definition operations and rule evaluations

CREATE TABLE audit_trails (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    operation_type VARCHAR(50) NOT NULL,
    entity_type VARCHAR(30) NOT NULL,
    entity_id UUID,
    rule_code VARCHAR(255),
    user_id VARCHAR(255),
    ip_address VARCHAR(45),
    user_agent TEXT,
    http_method VARCHAR(10),
    endpoint VARCHAR(500),
    request_data TEXT,
    response_data TEXT,
    status_code INTEGER,
    success BOOLEAN NOT NULL DEFAULT false,
    error_message TEXT,
    execution_time_ms BIGINT,
    metadata TEXT,
    session_id VARCHAR(255),
    correlation_id VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for efficient querying
CREATE INDEX idx_audit_trails_operation_type ON audit_trails(operation_type);
CREATE INDEX idx_audit_trails_entity_type ON audit_trails(entity_type);
CREATE INDEX idx_audit_trails_entity_id ON audit_trails(entity_id);
CREATE INDEX idx_audit_trails_rule_code ON audit_trails(rule_code);
CREATE INDEX idx_audit_trails_user_id ON audit_trails(user_id);
CREATE INDEX idx_audit_trails_success ON audit_trails(success);
CREATE INDEX idx_audit_trails_created_at ON audit_trails(created_at DESC);
CREATE INDEX idx_audit_trails_session_id ON audit_trails(session_id);
CREATE INDEX idx_audit_trails_correlation_id ON audit_trails(correlation_id);

-- Composite indexes for common query patterns
CREATE INDEX idx_audit_trails_operation_created ON audit_trails(operation_type, created_at DESC);
CREATE INDEX idx_audit_trails_user_created ON audit_trails(user_id, created_at DESC);
CREATE INDEX idx_audit_trails_entity_operation ON audit_trails(entity_id, operation_type, created_at DESC);
CREATE INDEX idx_audit_trails_rule_operation ON audit_trails(rule_code, operation_type, created_at DESC);
CREATE INDEX idx_audit_trails_success_created ON audit_trails(success, created_at DESC);

-- Add constraints
ALTER TABLE audit_trails ADD CONSTRAINT chk_audit_trails_operation_type
    CHECK (operation_type IN (
        'RULE_DEFINITION_CREATE', 'RULE_DEFINITION_UPDATE', 'RULE_DEFINITION_DELETE',
        'RULE_DEFINITION_GET', 'RULE_DEFINITION_FILTER', 'RULE_DEFINITION_VALIDATE',
        'RULE_EVALUATION_DIRECT', 'RULE_EVALUATION_PLAIN', 'RULE_EVALUATION_BY_CODE', 'RULE_EVALUATION_BATCH',
        'CONSTANT_CREATE', 'CONSTANT_UPDATE', 'CONSTANT_DELETE',
        'CONSTANT_GET', 'CONSTANT_FILTER',
        'YAML_VALIDATION'
    ));

ALTER TABLE audit_trails ADD CONSTRAINT chk_audit_trails_entity_type 
    CHECK (entity_type IN ('RULE_DEFINITION', 'RULE_EVALUATION', 'CONSTANT', 'VALIDATION'));

ALTER TABLE audit_trails ADD CONSTRAINT chk_audit_trails_status_code 
    CHECK (status_code >= 100 AND status_code < 600);

ALTER TABLE audit_trails ADD CONSTRAINT chk_audit_trails_execution_time 
    CHECK (execution_time_ms >= 0);

-- Add comments for documentation
COMMENT ON TABLE audit_trails IS 'Audit trail records for all rule engine operations including rule definitions and evaluations';
COMMENT ON COLUMN audit_trails.operation_type IS 'Type of operation performed (e.g., RULE_DEFINITION_CREATE, RULE_EVALUATION_DIRECT)';
COMMENT ON COLUMN audit_trails.entity_type IS 'Type of entity being operated on (RULE_DEFINITION, RULE_EVALUATION, CONSTANT, VALIDATION)';
COMMENT ON COLUMN audit_trails.entity_id IS 'ID of the entity being operated on (null for evaluations)';
COMMENT ON COLUMN audit_trails.rule_code IS 'Code of the rule definition (if applicable)';
COMMENT ON COLUMN audit_trails.user_id IS 'User who performed the operation';
COMMENT ON COLUMN audit_trails.ip_address IS 'IP address from which the operation was performed';
COMMENT ON COLUMN audit_trails.user_agent IS 'User agent string from the request';
COMMENT ON COLUMN audit_trails.http_method IS 'HTTP method used (GET, POST, PUT, DELETE)';
COMMENT ON COLUMN audit_trails.endpoint IS 'Request endpoint/path';
COMMENT ON COLUMN audit_trails.request_data IS 'Request data as JSON string';
COMMENT ON COLUMN audit_trails.response_data IS 'Response data as JSON string';
COMMENT ON COLUMN audit_trails.status_code IS 'HTTP status code of the response';
COMMENT ON COLUMN audit_trails.success IS 'Whether the operation was successful';
COMMENT ON COLUMN audit_trails.error_message IS 'Error message if the operation failed';
COMMENT ON COLUMN audit_trails.execution_time_ms IS 'Execution time in milliseconds';
COMMENT ON COLUMN audit_trails.metadata IS 'Additional metadata as JSON string';
COMMENT ON COLUMN audit_trails.session_id IS 'Session ID for tracking related operations';
COMMENT ON COLUMN audit_trails.correlation_id IS 'Correlation ID for distributed tracing';
COMMENT ON COLUMN audit_trails.created_at IS 'Timestamp when the audit record was created';

-- Create a partial index for failed operations (for monitoring and alerting)
CREATE INDEX idx_audit_trails_failures ON audit_trails(created_at DESC, operation_type, error_message) 
    WHERE success = false;

-- Create a regular index for recent operations (optimized for time-based queries)
CREATE INDEX idx_audit_trails_recent ON audit_trails(created_at DESC, operation_type, user_id);

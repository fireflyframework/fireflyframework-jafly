-- Create rule_definitions table for storing YAML DSL rule definitions
CREATE TABLE rule_definitions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    yaml_content TEXT NOT NULL,
    version VARCHAR(20),
    is_active BOOLEAN NOT NULL DEFAULT true,
    tags VARCHAR(500),
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for better query performance
CREATE INDEX idx_rule_definitions_code ON rule_definitions(code);
CREATE INDEX idx_rule_definitions_is_active ON rule_definitions(is_active);
CREATE INDEX idx_rule_definitions_created_by ON rule_definitions(created_by);
CREATE INDEX idx_rule_definitions_version ON rule_definitions(version);
CREATE INDEX idx_rule_definitions_created_at ON rule_definitions(created_at);

-- Add comments for documentation
COMMENT ON TABLE rule_definitions IS 'Stores YAML DSL rule definitions that have been validated and can be referenced by UUID for evaluation';
COMMENT ON COLUMN rule_definitions.id IS 'UUID primary key';
COMMENT ON COLUMN rule_definitions.code IS 'Unique code identifier for the rule definition (alphanumeric with underscores)';
COMMENT ON COLUMN rule_definitions.name IS 'Human-readable name for the rule definition';
COMMENT ON COLUMN rule_definitions.description IS 'Detailed description of what this rule definition does';
COMMENT ON COLUMN rule_definitions.yaml_content IS 'The validated YAML DSL content as a string';
COMMENT ON COLUMN rule_definitions.version IS 'Version of the rule definition for tracking changes (semantic versioning)';
COMMENT ON COLUMN rule_definitions.is_active IS 'Whether this rule definition is currently active and can be used for evaluation';
COMMENT ON COLUMN rule_definitions.tags IS 'Tags for categorizing and searching rule definitions (comma-separated)';
COMMENT ON COLUMN rule_definitions.created_by IS 'User who created this rule definition';
COMMENT ON COLUMN rule_definitions.updated_by IS 'User who last modified this rule definition';
COMMENT ON COLUMN rule_definitions.created_at IS 'Timestamp when the rule definition was created';
COMMENT ON COLUMN rule_definitions.updated_at IS 'Timestamp when the rule definition was last modified';

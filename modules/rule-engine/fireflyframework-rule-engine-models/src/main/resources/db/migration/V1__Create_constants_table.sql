-- Create CONSTANTS table
-- This table stores system constants that can be used in rule expressions
-- Constants are predefined values that don't change during rule execution,
-- acting as a feature store for the rule engine

CREATE TABLE constants (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(500) NOT NULL,
    value_type VARCHAR(50) NOT NULL CHECK (value_type IN ('STRING', 'NUMBER', 'BOOLEAN', 'DATE', 'OBJECT')),
    required BOOLEAN NOT NULL DEFAULT false,
    description TEXT,
    current_value JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create index on code for fast lookups
CREATE INDEX idx_constants_code ON constants(code);

-- Create index on value_type for filtering
CREATE INDEX idx_constants_value_type ON constants(value_type);

-- Create index on required flag for filtering
CREATE INDEX idx_constants_required ON constants(required);

-- Add comments to table
COMMENT ON TABLE constants IS 'Stores system constants that can be used in rule expressions';
COMMENT ON COLUMN constants.id IS 'UUID primary key';
COMMENT ON COLUMN constants.code IS 'Unique constant code (e.g., MINIMUM_CREDIT_SCORE)';
COMMENT ON COLUMN constants.name IS 'Human-readable constant name';
COMMENT ON COLUMN constants.value_type IS 'Data type (STRING, NUMBER, BOOLEAN, DATE, OBJECT)';
COMMENT ON COLUMN constants.required IS 'Whether constant must have value for evaluation';
COMMENT ON COLUMN constants.description IS 'Optional constant description';
COMMENT ON COLUMN constants.current_value IS 'Current value of the constant (stored as JSON)';
COMMENT ON COLUMN constants.created_at IS 'Creation timestamp';
COMMENT ON COLUMN constants.updated_at IS 'Last update timestamp';

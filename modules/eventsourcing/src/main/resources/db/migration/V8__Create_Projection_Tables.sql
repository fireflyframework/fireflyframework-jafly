-- Migration script for Event Sourcing projection tables
-- This creates the necessary tables for projection position tracking and example projections

-- Table for tracking projection positions (checkpoints)
CREATE TABLE IF NOT EXISTS projection_positions (
    projection_name VARCHAR(255) NOT NULL,
    position BIGINT NOT NULL DEFAULT 0,
    last_updated TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_projection_positions PRIMARY KEY (projection_name)
);

-- Index for faster lookups
CREATE INDEX IF NOT EXISTS idx_projection_positions_name ON projection_positions(projection_name);

-- Example table for account balance projections
CREATE TABLE IF NOT EXISTS account_balance_projections (
    id BIGSERIAL NOT NULL,
    account_id UUID NOT NULL,
    balance DECIMAL(19,2) NOT NULL DEFAULT 0.00,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    last_updated TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_account_balance_projections PRIMARY KEY (id),
    CONSTRAINT uk_account_balance_projections_account_id UNIQUE (account_id)
);

-- Indexes for faster queries
CREATE INDEX IF NOT EXISTS idx_account_balance_projections_account_id ON account_balance_projections(account_id);
CREATE INDEX IF NOT EXISTS idx_account_balance_projections_currency ON account_balance_projections(currency);
CREATE INDEX IF NOT EXISTS idx_account_balance_projections_last_updated ON account_balance_projections(last_updated);

-- Comment the tables for documentation
COMMENT ON TABLE projection_positions IS 'Tracks the current position (global sequence) for each projection';
COMMENT ON COLUMN projection_positions.projection_name IS 'Unique name identifying the projection';
COMMENT ON COLUMN projection_positions.position IS 'Last processed global sequence number';
COMMENT ON COLUMN projection_positions.last_updated IS 'Timestamp when the position was last updated';

COMMENT ON TABLE account_balance_projections IS 'Read model for account balances - example projection';
COMMENT ON COLUMN account_balance_projections.account_id IS 'Unique identifier for the account';
COMMENT ON COLUMN account_balance_projections.balance IS 'Current balance amount';
COMMENT ON COLUMN account_balance_projections.currency IS 'ISO currency code';
COMMENT ON COLUMN account_balance_projections.last_updated IS 'When this balance was last updated';
COMMENT ON COLUMN account_balance_projections.version IS 'Version for optimistic concurrency control';
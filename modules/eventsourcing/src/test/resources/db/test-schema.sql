-- Test schema initialization for integration tests
-- This creates the necessary tables for testing projections

-- Table for tracking projection positions (checkpoints)
CREATE TABLE IF NOT EXISTS projection_positions (
    projection_name VARCHAR(255) NOT NULL,
    position BIGINT NOT NULL DEFAULT 0,
    last_updated TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_projection_positions PRIMARY KEY (projection_name)
);

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
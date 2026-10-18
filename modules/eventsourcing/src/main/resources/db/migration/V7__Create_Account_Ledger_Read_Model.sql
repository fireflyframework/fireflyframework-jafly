-- Create account ledger read model table for fast queries
-- This is the read model for the AccountLedger aggregate (event-sourced)
CREATE TABLE account_ledger_read_model (
    account_id UUID PRIMARY KEY,
    account_number VARCHAR(50) NOT NULL UNIQUE,
    account_type VARCHAR(50) NOT NULL,
    customer_id UUID NOT NULL,
    balance DECIMAL(19,4) NOT NULL DEFAULT 0.0000,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    frozen BOOLEAN NOT NULL DEFAULT FALSE,
    closed BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    opened_at TIMESTAMP WITH TIME ZONE NOT NULL,
    closed_at TIMESTAMP WITH TIME ZONE,
    last_transaction_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    last_updated TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT valid_status CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED')),
    CONSTRAINT valid_balance CHECK (balance >= -999999999999999.9999)
);

-- Create indexes for performance
CREATE INDEX idx_account_ledger_account_number ON account_ledger_read_model(account_number);
CREATE INDEX idx_account_ledger_customer_id ON account_ledger_read_model(customer_id);
CREATE INDEX idx_account_ledger_status ON account_ledger_read_model(status);
CREATE INDEX idx_account_ledger_currency ON account_ledger_read_model(currency);
CREATE INDEX idx_account_ledger_balance ON account_ledger_read_model(balance);
CREATE INDEX idx_account_ledger_opened_at ON account_ledger_read_model(opened_at);
CREATE INDEX idx_account_ledger_last_updated ON account_ledger_read_model(last_updated);

-- Composite indexes for common queries
CREATE INDEX idx_account_ledger_customer_status ON account_ledger_read_model(customer_id, status);
CREATE INDEX idx_account_ledger_status_balance ON account_ledger_read_model(status, balance) WHERE status = 'ACTIVE';

-- Add comments for documentation
COMMENT ON TABLE account_ledger_read_model IS 'Read model for Account Ledger - optimized for queries (event-sourced aggregate)';
COMMENT ON COLUMN account_ledger_read_model.account_id IS 'Unique identifier for the account (aggregate ID)';
COMMENT ON COLUMN account_ledger_read_model.account_number IS 'Human-readable account number';
COMMENT ON COLUMN account_ledger_read_model.account_type IS 'Type of account (CHECKING, SAVINGS, etc.)';
COMMENT ON COLUMN account_ledger_read_model.customer_id IS 'Identifier of the customer who owns the account';
COMMENT ON COLUMN account_ledger_read_model.balance IS 'Current balance (calculated from events)';
COMMENT ON COLUMN account_ledger_read_model.currency IS 'ISO currency code';
COMMENT ON COLUMN account_ledger_read_model.frozen IS 'Whether the account is frozen';
COMMENT ON COLUMN account_ledger_read_model.closed IS 'Whether the account is closed';
COMMENT ON COLUMN account_ledger_read_model.status IS 'Current status of the account';
COMMENT ON COLUMN account_ledger_read_model.opened_at IS 'When the account was opened';
COMMENT ON COLUMN account_ledger_read_model.closed_at IS 'When the account was closed (if applicable)';
COMMENT ON COLUMN account_ledger_read_model.last_transaction_at IS 'Timestamp of the last transaction';
COMMENT ON COLUMN account_ledger_read_model.version IS 'Version for optimistic concurrency control';
COMMENT ON COLUMN account_ledger_read_model.last_updated IS 'When this read model was last updated';


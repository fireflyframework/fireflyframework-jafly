-- Create snapshots table for performance optimization
CREATE TABLE snapshots (
    aggregate_id UUID NOT NULL,
    aggregate_type VARCHAR(255) NOT NULL,
    aggregate_version BIGINT NOT NULL,
    snapshot_data TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    PRIMARY KEY (aggregate_id, aggregate_type)
);

-- Create indexes for performance
CREATE INDEX idx_snapshots_version ON snapshots(aggregate_version);
CREATE INDEX idx_snapshots_created_at ON snapshots(created_at);
CREATE INDEX idx_snapshots_type ON snapshots(aggregate_type);

-- Add comments for documentation
COMMENT ON TABLE snapshots IS 'Stores aggregate snapshots for performance optimization';
COMMENT ON COLUMN snapshots.aggregate_id IS 'Identifier of the aggregate';
COMMENT ON COLUMN snapshots.aggregate_type IS 'Type/class name of the aggregate';
COMMENT ON COLUMN snapshots.aggregate_version IS 'Version of the aggregate at the time of snapshot';
COMMENT ON COLUMN snapshots.snapshot_data IS 'JSON representation of the aggregate state';
COMMENT ON COLUMN snapshots.created_at IS 'Timestamp when the snapshot was created';
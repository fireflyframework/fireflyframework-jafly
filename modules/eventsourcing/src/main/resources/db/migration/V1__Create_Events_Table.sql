-- Create events table for Event Sourcing
CREATE TABLE events (
    event_id UUID PRIMARY KEY,
    aggregate_id UUID NOT NULL,
    aggregate_type VARCHAR(255) NOT NULL,
    aggregate_version BIGINT NOT NULL,
    global_sequence BIGSERIAL UNIQUE,
    event_type VARCHAR(255) NOT NULL,
    event_data TEXT NOT NULL,
    metadata TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    CONSTRAINT unique_aggregate_version UNIQUE(aggregate_id, aggregate_version)
);

-- Create indexes for performance
CREATE INDEX idx_events_aggregate ON events(aggregate_id, aggregate_type);
CREATE INDEX idx_events_global_sequence ON events(global_sequence);
CREATE INDEX idx_events_type ON events(event_type);
CREATE INDEX idx_events_created_at ON events(created_at);
CREATE INDEX idx_events_aggregate_version ON events(aggregate_id, aggregate_version);

-- Add comments for documentation
COMMENT ON TABLE events IS 'Stores all domain events for event sourcing';
COMMENT ON COLUMN events.event_id IS 'Unique identifier for each event';
COMMENT ON COLUMN events.aggregate_id IS 'Identifier of the aggregate that generated the event';
COMMENT ON COLUMN events.aggregate_type IS 'Type/class name of the aggregate';
COMMENT ON COLUMN events.aggregate_version IS 'Version of the aggregate when this event was generated';
COMMENT ON COLUMN events.global_sequence IS 'Global ordering sequence for all events';
COMMENT ON COLUMN events.event_type IS 'Type identifier for the event (used for deserialization)';
COMMENT ON COLUMN events.event_data IS 'JSON representation of the event data';
COMMENT ON COLUMN events.metadata IS 'Additional metadata associated with the event';
COMMENT ON COLUMN events.created_at IS 'Timestamp when the event was stored';
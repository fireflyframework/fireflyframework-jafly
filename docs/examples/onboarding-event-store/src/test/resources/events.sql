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
CREATE INDEX idx_events_aggregate ON events(aggregate_id, aggregate_type);

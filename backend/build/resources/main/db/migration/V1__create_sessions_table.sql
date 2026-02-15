-- Media3Watch Sessions Table
-- This table stores aggregated playback session data from the Android SDK

CREATE TABLE IF NOT EXISTS sessions (
    id BIGSERIAL PRIMARY KEY,
    session_id VARCHAR(128) NOT NULL UNIQUE,
    timestamp TIMESTAMPTZ NOT NULL,
    content_id VARCHAR(256),
    stream_type VARCHAR(16),
    player_startup_ms INTEGER,
    rebuffer_time_ms INTEGER,
    rebuffer_count INTEGER,
    error_count INTEGER,
    payload JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Indexes for common query patterns
CREATE INDEX idx_sessions_timestamp ON sessions (timestamp DESC);
CREATE INDEX idx_sessions_content_id ON sessions (content_id) WHERE content_id IS NOT NULL;
CREATE INDEX idx_sessions_stream_type ON sessions (stream_type) WHERE stream_type IS NOT NULL;
CREATE INDEX idx_sessions_player_startup ON sessions (player_startup_ms) WHERE player_startup_ms IS NOT NULL;

-- Partial index for error analysis
CREATE INDEX idx_sessions_errors ON sessions (error_count, timestamp DESC) WHERE error_count > 0;

-- GIN index for JSONB payload queries (optional, for advanced analytics)
CREATE INDEX idx_sessions_payload ON sessions USING GIN (payload);

-- Comment on table and key columns
COMMENT ON TABLE sessions IS 'Stores aggregated playback session summaries from Media3Watch Android SDK';
COMMENT ON COLUMN sessions.session_id IS 'Unique session identifier (UUID from SDK)';
COMMENT ON COLUMN sessions.timestamp IS 'Session end timestamp (milliseconds since epoch)';
COMMENT ON COLUMN sessions.payload IS 'Full JSON payload for flexible querying';


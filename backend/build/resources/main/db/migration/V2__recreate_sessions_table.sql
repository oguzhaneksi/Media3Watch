-- V2: Recreate sessions table to match Android SDK SessionSummary schema
-- This drops all existing data (acceptable in development phase)

DROP TABLE IF EXISTS sessions;

CREATE TABLE sessions (
    id BIGSERIAL PRIMARY KEY,
    session_id VARCHAR(128) NOT NULL UNIQUE,
    timestamp BIGINT NOT NULL,
    session_start_date_iso VARCHAR(64) NOT NULL,
    session_duration_ms BIGINT NOT NULL,
    startup_time_ms BIGINT,
    rebuffer_time_ms BIGINT,
    rebuffer_count INTEGER,
    play_time_ms BIGINT,
    rebuffer_ratio REAL,
    total_dropped_frames BIGINT,
    total_seek_count INTEGER,
    total_seek_time_ms BIGINT,
    mean_video_format_bitrate INTEGER,
    error_count INTEGER,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Indexes for common query patterns
CREATE INDEX idx_sessions_timestamp ON sessions (timestamp DESC);
CREATE INDEX idx_sessions_startup ON sessions (startup_time_ms) WHERE startup_time_ms IS NOT NULL;
CREATE INDEX idx_sessions_errors ON sessions (error_count, timestamp DESC) WHERE error_count > 0;
CREATE INDEX idx_sessions_rebuffer ON sessions (rebuffer_ratio) WHERE rebuffer_ratio IS NOT NULL;

COMMENT ON TABLE sessions IS 'Stores playback session summaries from Media3Watch Android SDK';
COMMENT ON COLUMN sessions.session_id IS 'Unique session identifier (UUID from SDK)';
COMMENT ON COLUMN sessions.timestamp IS 'Epoch milliseconds when session was recorded';

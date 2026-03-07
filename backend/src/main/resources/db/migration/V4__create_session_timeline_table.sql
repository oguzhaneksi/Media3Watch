CREATE TABLE session_timeline (
    id                   BIGSERIAL PRIMARY KEY,
    session_id           VARCHAR(128) NOT NULL,
    timestamp_ms         BIGINT NOT NULL,
    elapsed_ms           BIGINT NOT NULL,
    playback_state       VARCHAR(16),
    current_bitrate      INTEGER,
    network_type         VARCHAR(16),
    total_dropped_frames BIGINT,
    buffered_duration_ms BIGINT,
    rebuffer_count       INTEGER,
    rebuffer_time_ms     BIGINT,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_timeline_session_timestamp ON session_timeline (session_id, timestamp_ms);

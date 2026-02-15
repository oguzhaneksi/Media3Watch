#!/bin/bash
# Checks session data in PostgreSQL database

echo "🔍 Checking data in the sessions table..."
echo ""

docker exec -it m3w-postgres psql -U m3w -d media3watch -c "
SELECT 
    session_id,
    session_start_date_iso,
    session_duration_ms,
    rebuffer_count,
    error_count,
    created_at
FROM sessions
ORDER BY created_at DESC
LIMIT 10;
"

echo ""
echo "📊 Total session count:"
docker exec -it m3w-postgres psql -U m3w -d media3watch -c "SELECT COUNT(*) FROM sessions;"

#!/bin/bash
# Connects directly to PostgreSQL CLI

echo "🔌 Connecting to PostgreSQL database..."
echo "📝 Useful commands:"
echo "   - Last 10 sessions: SELECT * FROM sessions ORDER BY created_at DESC LIMIT 10;"
echo "   - Total records: SELECT COUNT(*) FROM sessions;"
echo "   - Sessions with errors: SELECT * FROM sessions WHERE error_count > 0;"
echo "   - Exit: \\q"
echo ""

docker exec -it m3w-postgres psql -U m3w -d media3watch

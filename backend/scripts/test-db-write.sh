#!/bin/bash
# Sends data to API and verifies in PostgreSQL

set -e

API_KEY="dev-key"
BASE_URL="http://localhost:8080"
TEST_SESSION_ID="test-$(date +%s)-$(uuidgen)"

echo "🚀 Starting test..."
echo "Session ID: $TEST_SESSION_ID"
echo ""

# 1. Health check
echo "1️⃣ Health check..."
curl -s "$BASE_URL/health" | jq .
echo ""

# 2. Send session
echo "2️⃣ Sending session data..."
RESPONSE=$(curl -s -X POST "$BASE_URL/v1/sessions" \
  -H "X-API-Key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d "{
    \"sessionId\": \"$TEST_SESSION_ID\",
    \"timestamp\": $(date +%s)000,
    \"sessionStartDateIso\": \"$(date -u +%Y-%m-%dT%H:%M:%S.000Z)\",
    \"sessionDurationMs\": 45000,
    \"startupTimeMs\": 450,
    \"rebufferTimeMs\": 1200,
    \"rebufferCount\": 2,
    \"playTimeMs\": 42000,
    \"rebufferRatio\": 0.028,
    \"totalDroppedFrames\": 12,
    \"totalSeekCount\": 1,
    \"totalSeekTimeMs\": 300,
    \"meanVideoFormatBitrate\": 2500000,
    \"errorCount\": 0
  }")

echo "$RESPONSE" | jq .
echo ""

# 3. Check in PostgreSQL
echo "3️⃣ Checking data in PostgreSQL..."
sleep 1  # Short wait for DB write

DB_RESULT=$(docker exec m3w-postgres psql -U m3w -d media3watch -t -A -c \
  "SELECT session_id, session_duration_ms, rebuffer_count, created_at 
   FROM sessions 
   WHERE session_id = '$TEST_SESSION_ID';")

if [ -z "$DB_RESULT" ]; then
  echo "❌ ERROR: Data not found in PostgreSQL!"
  exit 1
else
  echo "✅ Data successfully written to PostgreSQL:"
  docker exec m3w-postgres psql -U m3w -d media3watch -c \
    "SELECT * FROM sessions WHERE session_id = '$TEST_SESSION_ID';"
  echo ""
  echo "🎉 Test SUCCESSFUL!"
fi

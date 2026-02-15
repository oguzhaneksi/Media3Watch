#!/bin/bash
# API'ye veri gönderir ve PostgreSQL'de doğrular

set -e

API_KEY="dev-key"
BASE_URL="http://localhost:8080"
TEST_SESSION_ID="test-$(date +%s)-$(uuidgen)"

echo "🚀 Test başlatılıyor..."
echo "Session ID: $TEST_SESSION_ID"
echo ""

# 1. Health check
echo "1️⃣ Health check..."
curl -s "$BASE_URL/health" | jq .
echo ""

# 2. Session gönder
echo "2️⃣ Session verisi gönderiliyor..."
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

# 3. PostgreSQL'de kontrol et
echo "3️⃣ PostgreSQL'de veri kontrol ediliyor..."
sleep 1  # DB'ye yazılması için kısa bir bekleme

DB_RESULT=$(docker exec m3w-postgres psql -U m3w -d media3watch -t -A -c \
  "SELECT session_id, session_duration_ms, rebuffer_count, created_at 
   FROM sessions 
   WHERE session_id = '$TEST_SESSION_ID';")

if [ -z "$DB_RESULT" ]; then
  echo "❌ HATA: Veri PostgreSQL'de bulunamadı!"
  exit 1
else
  echo "✅ Veri başarıyla PostgreSQL'e yazıldı:"
  docker exec m3w-postgres psql -U m3w -d media3watch -c \
    "SELECT * FROM sessions WHERE session_id = '$TEST_SESSION_ID';"
  echo ""
  echo "🎉 Test BAŞARILI!"
fi

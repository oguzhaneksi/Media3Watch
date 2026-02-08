#!/bin/bash

# Media3Watch Backend - Smoke Test Script
# This script performs basic health and functionality checks


# Configuration
BASE_URL="${BASE_URL:-http://localhost:8080}"
API_KEY="${M3W_API_KEY:-dev-key}"

echo "==================================================="
echo "  Media3Watch Backend - Smoke Test"
echo "==================================================="
echo ""
echo "Base URL: $BASE_URL"
echo "API Key: ${API_KEY:0:8}..."
echo ""

# Check if server is reachable
echo "Checking if server is reachable..."
if ! curl -s --connect-timeout 5 -o /dev/null "$BASE_URL/health" 2>/dev/null; then
    echo -e "\033[0;31m✗ ERROR: Cannot connect to $BASE_URL\033[0m"
    echo "Please make sure the backend server is running."
    echo "You can start it with: ./gradlew run"
    exit 1
fi
echo -e "\033[0;32m✓ Server is reachable\033[0m"
echo ""

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Test counter
PASSED=0
FAILED=0

# Function to run a test
run_test() {
    local test_name="$1"
    local expected_status="$2"
    local actual_status="$3"

    if [ "$actual_status" == "$expected_status" ]; then
        ((PASSED++))
        echo -e "  ${GREEN}✓${NC} $test_name (Status: $actual_status)"
    else
        ((FAILED++))
        echo -e "  ${RED}✗${NC} $test_name (Expected: $expected_status, Got: $actual_status)"
    fi
}

# ----------------------------------------
# Test 1: Health Check
# ----------------------------------------
echo "1. Health Check Endpoint"
HEALTH_STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/health")
run_test "GET /health returns 200" "200" "$HEALTH_STATUS"
HEALTH_RESPONSE=$(curl -s "$BASE_URL/health")

if echo "$HEALTH_RESPONSE" | grep -q "healthy"; then
    ((PASSED++))
    echo -e "  ${GREEN}✓${NC} Response contains 'healthy' status"
else
    ((FAILED++))
    echo -e "  ${RED}✗${NC} Response missing 'healthy' status"
fi

echo ""

# ----------------------------------------
# Test 2: Sessions Endpoint - No Auth
# ----------------------------------------
echo "2. Sessions Endpoint - Authentication"
NO_AUTH_STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
    -X POST "$BASE_URL/v1/sessions" \
    -H "Content-Type: application/json" \
    -d '{"sessionId":"test"}')
run_test "POST /v1/sessions without API key returns 401" "401" "$NO_AUTH_STATUS"

echo ""

# ----------------------------------------
# Test 3: Sessions Endpoint - Invalid Key
# ----------------------------------------
echo "3. Sessions Endpoint - Invalid API Key"
INVALID_KEY_STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
    -X POST "$BASE_URL/v1/sessions" \
    -H "Content-Type: application/json" \
    -H "X-API-Key: invalid-key" \
    -d '{"sessionId":"test"}')
run_test "POST /v1/sessions with invalid API key returns 401" "401" "$INVALID_KEY_STATUS"

echo ""

# ----------------------------------------
# Test 4: Sessions Endpoint - Valid Request
# ----------------------------------------
echo "4. Sessions Endpoint - Valid Request"
SESSION_ID="smoke-test-$(date +%s)"
VALID_REQUEST_STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
    -X POST "$BASE_URL/v1/sessions" \
    -H "Content-Type: application/json" \
    -H "X-API-Key: $API_KEY" \
    -d "{
        \"sessionId\": \"$SESSION_ID\",
        \"timestamp\": $(date +%s)000,
        \"schemaVersion\": 1,
        \"contentId\": \"test-content-123\",
        \"streamType\": \"VOD\",
        \"playerStartupMs\": 1500,
        \"rebufferTimeMs\": 200,
        \"rebufferCount\": 1,
        \"errorCount\": 0
    }")
run_test "POST /v1/sessions with valid data returns 200" "200" "$VALID_REQUEST_STATUS"

echo ""

# ----------------------------------------
# Test 5: Sessions Endpoint - Idempotency
# ----------------------------------------
echo "5. Sessions Endpoint - Idempotency (Upsert)"
UPSERT_STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
    -X POST "$BASE_URL/v1/sessions" \
    -H "Content-Type: application/json" \
    -H "X-API-Key: $API_KEY" \
    -d "{
        \"sessionId\": \"$SESSION_ID\",
        \"timestamp\": $(date +%s)000,
        \"schemaVersion\": 1,
        \"contentId\": \"test-content-123\",
        \"streamType\": \"VOD\",
        \"playerStartupMs\": 1600,
        \"rebufferTimeMs\": 100,
        \"rebufferCount\": 0,
        \"errorCount\": 0
    }")
run_test "POST /v1/sessions with same sessionId (upsert) returns 200" "200" "$UPSERT_STATUS"

echo ""

# ----------------------------------------
# Test 6: Sessions Endpoint - Invalid Schema
# ----------------------------------------
echo "6. Sessions Endpoint - Invalid Schema"
INVALID_SCHEMA_STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
    -X POST "$BASE_URL/v1/sessions" \
    -H "Content-Type: application/json" \
    -H "X-API-Key: $API_KEY" \
    -d '{"sessionId":"","timestamp":0}')
run_test "POST /v1/sessions with empty sessionId returns 400" "400" "$INVALID_SCHEMA_STATUS"

echo ""

# ----------------------------------------
# Test 7: Metrics Endpoint
# ----------------------------------------
echo "7. Metrics Endpoint"
METRICS_STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/metrics")
run_test "GET /metrics returns 200" "200" "$METRICS_STATUS"

METRICS_RESPONSE=$(curl -s "$BASE_URL/metrics")
if echo "$METRICS_RESPONSE" | grep -q "sessions_ingested_total"; then
    ((PASSED++))
    echo -e "  ${GREEN}✓${NC} Metrics contains 'sessions_ingested_total' counter"
else
    echo -e "  ${YELLOW}~${NC} Metrics may not contain custom counter yet (check if enabled)"
fi

echo ""

# ----------------------------------------
# Summary
# ----------------------------------------
echo "==================================================="
echo "  Summary"
echo "==================================================="
echo -e "  ${GREEN}Passed:${NC} $PASSED"
echo -e "  ${RED}Failed:${NC} $FAILED"
echo ""

if [ $FAILED -eq 0 ]; then
    echo -e "${GREEN}All smoke tests passed!${NC}"
    exit 0
else
    echo -e "${RED}Some tests failed. Please check the output above.${NC}"
    exit 1
fi


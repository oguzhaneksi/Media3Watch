#!/bin/bash

# Media3Watch Backend - Data Cleanup Script
# Removes sessions older than RETENTION_DAYS

set -e

# Configuration
RETENTION_DAYS="${RETENTION_DAYS:-90}"
DATABASE_URL="${DATABASE_URL:-jdbc:postgresql://localhost:5432/media3watch}"
DATABASE_USER="${DATABASE_USER:-m3w}"
DATABASE_PASSWORD="${DATABASE_PASSWORD:-m3w}"

# Extract host, port, and database from JDBC URL
DB_HOST=$(echo "$DATABASE_URL" | sed -n 's/.*:\/\/\([^:\/]*\).*/\1/p')
DB_PORT=$(echo "$DATABASE_URL" | sed -n 's/.*:\([0-9]*\)\/.*/\1/p')
DB_NAME=$(echo "$DATABASE_URL" | sed -n 's/.*\/\([^?]*\).*/\1/p')

echo "==================================================="
echo "  Media3Watch - Session Cleanup"
echo "==================================================="
echo ""
echo "Database: $DB_NAME@$DB_HOST:$DB_PORT"
echo "Retention: $RETENTION_DAYS days"
echo ""

# Count records to be deleted
echo "Checking records to delete..."
COUNT=$(PGPASSWORD="$DATABASE_PASSWORD" psql -h "$DB_HOST" -p "$DB_PORT" -U "$DATABASE_USER" -d "$DB_NAME" -t -c \
    "SELECT COUNT(*) FROM sessions WHERE to_timestamp(timestamp / 1000.0) < NOW() - INTERVAL '$RETENTION_DAYS days';")

COUNT=$(echo "$COUNT" | tr -d ' ')
echo "Found $COUNT records older than $RETENTION_DAYS days"

if [ "$COUNT" -eq "0" ]; then
    echo "No records to delete. Exiting."
    exit 0
fi

# Confirm deletion (skip if running non-interactively)
if [ -t 0 ]; then
    read -p "Delete these records? (y/N) " confirm
    if [ "$confirm" != "y" ] && [ "$confirm" != "Y" ]; then
        echo "Aborted."
        exit 0
    fi
fi

# Perform deletion
echo "Deleting old records..."
DELETED=$(PGPASSWORD="$DATABASE_PASSWORD" psql -h "$DB_HOST" -p "$DB_PORT" -U "$DATABASE_USER" -d "$DB_NAME" -t -c \
    "DELETE FROM sessions WHERE to_timestamp(timestamp / 1000.0) < NOW() - INTERVAL '$RETENTION_DAYS days' RETURNING 1;" | wc -l)

echo "Deleted $DELETED records"

# Optional: VACUUM to reclaim space
echo "Running VACUUM ANALYZE..."
PGPASSWORD="$DATABASE_PASSWORD" psql -h "$DB_HOST" -p "$DB_PORT" -U "$DATABASE_USER" -d "$DB_NAME" -c "VACUUM ANALYZE sessions;"

echo ""
echo "Cleanup completed successfully!"


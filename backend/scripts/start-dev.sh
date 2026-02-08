#!/bin/bash

# Media3Watch Backend - Development Startup Script
# This script starts the database and backend server for local development

set -e

echo "==================================================="
echo "  Media3Watch Backend - Development Startup"
echo "==================================================="
echo ""

# Check if Docker is running
if ! docker info > /dev/null 2>&1; then
    echo "❌ ERROR: Docker is not running"
    echo ""
    echo "Please start Docker Desktop and try again."
    exit 1
fi

echo "✓ Docker is running"
echo ""

# Start database
echo "Starting PostgreSQL database..."
docker-compose up -d postgres

echo "Waiting for database to be healthy..."
sleep 5

# Check database health
if docker-compose ps postgres | grep -q "healthy"; then
    echo "✓ Database is healthy"
else
    echo "⚠️  Database may still be starting..."
    echo "Waiting a bit longer..."
    sleep 5
fi

echo ""
echo "==================================================="
echo "  Database is ready!"
echo "==================================================="
echo ""
echo "You can now start the backend with:"
echo "  ./gradlew run"
echo ""
echo "Or run the smoke test with:"
echo "  ./scripts/smoke-test.sh"
echo ""
echo "To stop the database:"
echo "  docker-compose down"
echo ""


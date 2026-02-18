#!/bin/bash
# Script to run the integration tests in Docker with Ollama AI validation

set -e

echo "🚀 Starting Mermaid Plugin Integration Tests with Ollama AI"
echo "============================================================"

# Check if Docker is available
if ! command -v docker &> /dev/null; then
    echo "❌ Docker is not installed or not in PATH"
    exit 1
fi

# Check if Docker Compose is available
if ! command -v docker-compose &> /dev/null && ! docker compose version &> /dev/null 2>&1; then
    echo "❌ Docker Compose is not installed or not in PATH"
    exit 1
fi

# Determine which docker compose command to use
if docker compose version &> /dev/null 2>&1; then
    DOCKER_COMPOSE="docker compose"
else
    DOCKER_COMPOSE="docker-compose"
fi

echo "✅ Docker and Docker Compose are available"
echo ""

# Clean up any previous containers
echo "🧹 Cleaning up previous containers..."
$DOCKER_COMPOSE -f docker-compose.test.yml down -v 2>/dev/null || true

# Build and start the services
echo ""
echo "🏗️  Building test environment..."
$DOCKER_COMPOSE -f docker-compose.test.yml build

echo ""
echo "🎯 Starting Ollama and test services..."
$DOCKER_COMPOSE -f docker-compose.test.yml up --abort-on-container-exit

# Get the exit code from the test runner
TEST_EXIT_CODE=$?

# Copy test screenshots from the volume
echo ""
echo "📸 Extracting test screenshots..."
docker cp mermaid-test-runner:/workspace/build/test-screenshots ./build/test-screenshots 2>/dev/null || echo "No screenshots to extract"

# Clean up
echo ""
echo "🧹 Cleaning up containers..."
$DOCKER_COMPOSE -f docker-compose.test.yml down -v

echo ""
if [ $TEST_EXIT_CODE -eq 0 ]; then
    echo "✅ Tests passed successfully!"
    echo "📸 Screenshots are available in: ./build/test-screenshots/"
else
    echo "❌ Tests failed with exit code: $TEST_EXIT_CODE"
fi

exit $TEST_EXIT_CODE

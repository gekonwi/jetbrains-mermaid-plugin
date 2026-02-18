#!/bin/bash
# Script to start Ollama service locally for development and testing

set -e

echo "🚀 Starting Ollama AI Service"
echo "============================="

# Check if Docker is available
if ! command -v docker &> /dev/null; then
    echo "❌ Docker is not installed or not in PATH"
    exit 1
fi

# Check if Ollama container is already running
if docker ps | grep -q mermaid-ollama; then
    echo "ℹ️  Ollama is already running"
    echo "To stop it, run: docker stop mermaid-ollama"
    exit 0
fi

# Remove any existing stopped container
docker rm mermaid-ollama 2>/dev/null || true

echo "🏗️  Starting Ollama container..."
docker run -d \
    --name mermaid-ollama \
    -p 11434:11434 \
    -v ollama-data:/root/.ollama \
    ollama/ollama:latest

# Wait for Ollama to be ready
echo "⏳ Waiting for Ollama to be ready..."
for i in {1..30}; do
    if curl -s http://localhost:11434/api/tags > /dev/null 2>&1; then
        echo "✅ Ollama is ready!"
        break
    fi
    if [ $i -eq 30 ]; then
        echo "❌ Ollama failed to start within 30 seconds"
        docker logs mermaid-ollama
        exit 1
    fi
    sleep 1
done

# Pull the llava model for image analysis
echo ""
echo "📦 Pulling llava model (this may take a few minutes on first run)..."
echo "    Progress will be shown below..."

if curl -X POST http://localhost:11434/api/pull -d '{"name":"llava"}' 2>&1 | tee /tmp/ollama-pull.log; then
    echo "✓ Model pull completed successfully"
else
    echo "❌ Model pull failed. Check logs above."
    exit 1
fi

echo ""
echo "✅ Ollama is ready with llava model!"
echo ""
echo "📍 Ollama API: http://localhost:11434"
echo ""
echo "To stop Ollama, run:"
echo "  docker stop mermaid-ollama"
echo ""
echo "To view logs, run:"
echo "  docker logs -f mermaid-ollama"

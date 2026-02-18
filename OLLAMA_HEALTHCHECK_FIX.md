# Ollama Healthcheck Fix

## Issue
The containerized tests were failing with:
```
dependency failed to start: container mermaid-test-ollama is unhealthy
```

## Root Cause
The healthcheck in `docker-compose.test.yml` was using `curl` to check if Ollama was ready:
```yaml
healthcheck:
  test: ["CMD", "curl", "-f", "http://localhost:11434/api/tags"]
```

However, the `ollama/ollama:latest` Docker image does not include `curl` by default, causing the healthcheck command to fail.

## Solution
Changed the healthcheck to use the `ollama` CLI command which is available in the image:

```yaml
healthcheck:
  test: ["CMD-SHELL", "ollama list || exit 1"]
  interval: 10s
  timeout: 5s
  retries: 5
  start_period: 10s  # Give ollama time to initialize
```

### Key Changes
1. **Command**: Changed from `curl` to `ollama list`
2. **Format**: Changed from `CMD` to `CMD-SHELL` to support shell syntax
3. **Start Period**: Added `start_period: 10s` to give Ollama time to initialize before health checks begin

## Why This Works
- `ollama list` is a built-in command in the ollama image that lists available models
- It requires the ollama server to be running, making it a good healthcheck
- The `|| exit 1` ensures the healthcheck fails if the command fails
- `start_period` prevents false failures during container initialization

## Testing
To test the healthcheck manually:
```bash
docker compose -f docker-compose.test.yml up -d ollama
docker ps  # Should show ollama as healthy after ~10-20 seconds
docker inspect mermaid-test-ollama | grep Health
```

## Alternative Solutions Considered
1. **Install curl**: Would require a custom Dockerfile extending ollama image
2. **Use wget**: Also not available by default in ollama image
3. **Use nc (netcat)**: More complex and less reliable
4. **Remove healthcheck**: Would require manual delays, less robust

## References
- [Docker Compose Healthcheck Documentation](https://docs.docker.com/compose/compose-file/05-services/#healthcheck)
- [Ollama Docker Documentation](https://hub.docker.com/r/ollama/ollama)

## Last Updated
2026-02-18

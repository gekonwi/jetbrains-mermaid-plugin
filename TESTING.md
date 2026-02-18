# Testing Infrastructure with AI Validation

This directory contains the infrastructure for running integration tests with AI-powered validation using Ollama.

## Overview

The test infrastructure includes:
1. **Ollama AI Service**: Local AI service for validating diagram screenshots
2. **Docker Compose Setup**: Containerized test environment
3. **Integration Tests**: Tests that render Mermaid diagrams and validate them with AI

## Quick Start

### Option 1: Run Containerized Tests (Recommended)

Run the complete test suite with Ollama in Docker:

```bash
./run-containerized-tests.sh
```

This will:
- Start Ollama in a container
- Pull the llava vision model
- Run the integration tests
- Validate screenshots with AI
- Clean up containers when done

### Option 2: Run Tests Locally with Ollama

1. Start Ollama:
```bash
./start-ollama.sh
```

2. Run the tests:
```bash
./gradlew test --tests "com.nereid.integration.ArchitectureDiagramRenderingTest"
```

3. Stop Ollama when done:
```bash
docker stop mermaid-ollama
```

## Architecture

### Components

```
┌─────────────────────────────────────────┐
│         Test Execution                  │
│                                         │
│  ┌──────────────────────────────────┐  │
│  │  Integration Test                │  │
│  │  - Load test data                │  │
│  │  - Render diagram                │  │
│  │  - Capture screenshot            │  │
│  │  - Validate with AI              │  │
│  └──────────────┬───────────────────┘  │
│                 │                       │
│                 │ HTTP                  │
│                 ▼                       │
│  ┌──────────────────────────────────┐  │
│  │  Ollama AI Service               │  │
│  │  - llava vision model            │  │
│  │  - Image analysis                │  │
│  │  - Diagram validation            │  │
│  └──────────────────────────────────┘  │
└─────────────────────────────────────────┘
```

### Test Flow

1. **Setup**: Start Ollama and load the llava vision model
2. **Load Test Data**: Read the architecture diagram markdown file
3. **Render**: Create MermaidPreviewPanel and render the diagram
4. **Capture**: Take a screenshot of the rendered preview
5. **Validate**: Send screenshot to Ollama for AI-based validation
6. **Verify**: Check that AI confirms the diagram structure matches expectations

## Docker Setup

### docker-compose.test.yml

Defines two services:
- **ollama**: Runs the Ollama AI service with llava model
- **test-runner**: Runs the integration tests with access to Ollama

### Dockerfile.test

Creates a test execution environment with:
- JDK 17
- Xvfb for headless rendering
- Required UI libraries
- Gradle for test execution

## Integration Test Details

### ArchitectureDiagramRenderingTest

The integration test performs the following steps:

```kotlin
fun testArchitectureDiagramRendering() {
    // 1. Check if JCEF is supported
    if (!JBCefApp.isSupported()) { skip }
    
    // 2. Load test data
    val diagram = loadArchitectureDiagram()
    
    // 3. Render with MermaidPreviewPanel
    val preview = MermaidPreviewPanel()
    preview.renderDiagram(diagram)
    
    // 4. Capture screenshot
    val screenshot = captureScreenshot()
    
    // 5. Validate with Ollama AI
    val result = validateWithAI(screenshot, diagram)
    
    // 6. Verify results
    assertTrue(result.isValid)
}
```

### AI Validation

The test sends the screenshot to Ollama with a prompt that asks:
- Are there multiple groups/layers visible?
- Are there service boxes/nodes?
- Are there connection lines/arrows?
- Does it match a layered architecture pattern?

Ollama's llava model analyzes the image and responds with:
- Number of groups detected
- Number of services detected
- Number of connections detected
- Whether it matches the expected structure
- Observations about the diagram

## Environment Variables

- `OLLAMA_HOST`: Ollama service URL (default: `http://localhost:11434`)
- `DISPLAY`: X11 display for UI rendering (default: `:99`)
- `GRADLE_OPTS`: JVM options for Gradle (default: `-Xmx2048m`)

## Test Data

### architecture-diagram.md

Comprehensive test data file with:
- Multiple groups (Frontend, API Gateway, Backend, Storage, External)
- 11 services with various icons
- 13 directional connections
- Layered architecture pattern

## Troubleshooting

### Ollama not starting

Check Docker logs:
```bash
docker logs mermaid-ollama
```

### Tests failing in headless mode

The tests gracefully skip JCEF rendering in headless environments. To run with UI:
```bash
export DISPLAY=:99
Xvfb :99 -screen 0 1024x768x24 &
./gradlew test --tests "com.nereid.integration.ArchitectureDiagramRenderingTest"
```

### AI validation not working

1. Check if Ollama is running:
```bash
curl http://localhost:11434/api/tags
```

2. Check if llava model is available:
```bash
curl http://localhost:11434/api/tags | grep llava
```

3. Pull llava model manually:
```bash
curl -X POST http://localhost:11434/api/pull -d '{"name":"llava"}'
```

### Docker Compose issues

Reset everything:
```bash
docker-compose -f docker-compose.test.yml down -v
docker system prune -f
./run-containerized-tests.sh
```

## CI/CD Integration

To integrate with CI/CD pipelines:

### GitHub Actions Example

```yaml
name: Integration Tests

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Run containerized tests
        run: ./run-containerized-tests.sh
      - name: Upload screenshots
        if: always()
        uses: actions/upload-artifact@v3
        with:
          name: test-screenshots
          path: build/test-screenshots/
```

## Manual Testing

For quick manual verification without running the full test suite:

```bash
# Start Ollama
./start-ollama.sh

# Open the test HTML file
open test-architecture-diagram.html

# Or serve it with a local server
python3 -m http.server 8000
# Then open http://localhost:8000/test-architecture-diagram.html
```

## Dependencies

- Docker and Docker Compose
- JDK 17
- Kotlin 1.9.21
- Gradle 8.5
- Ollama with llava model

## Output

Test results and artifacts:
- **Test Reports**: `build/test-results/`
- **Screenshots**: `build/test-screenshots/`
- **Logs**: Docker logs and Gradle test output

## Future Enhancements

Potential improvements:
1. Visual regression testing with baseline screenshots
2. Performance benchmarks for different diagram sizes
3. Support for multiple AI models (GPT-4V, Claude, etc.)
4. Automated screenshot comparison
5. Test data generation for all Mermaid diagram types

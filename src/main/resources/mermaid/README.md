# Mermaid.js Library

This directory contains the Mermaid.js library resources used by the plugin.

## Build-Time Download

The `mermaid.min.js` file is **NOT stored in Git**. Instead, it is automatically downloaded during the build process to `build/downloaded-resources/mermaid/` and then copied here during resource processing.

### Version Management

The Mermaid version is configured in `build.gradle.kts`:
```kotlin
val mermaidVersion = "11.12.2"
```

### Download Location

Following Gradle best practices:
- **Download Location**: `build/downloaded-resources/mermaid/mermaid.min.js` (not tracked in Git)
- **Runtime Location**: Copied to `build/resources/main/mermaid/mermaid.min.js` during `processResources`
- **Source Directory**: `src/main/resources/mermaid/` contains only static files (HTML, CSS, JS)

### Download Process

The build system automatically downloads Mermaid.js from:
1. **Primary**: npm - `npm install mermaid@{version}`
2. **Fallback**: jsDelivr CDN - `https://cdn.jsdelivr.net/npm/mermaid@{version}/dist/mermaid.min.js`

### Manual Download

If you need to download Mermaid.js manually:

```bash
# Using Gradle task
./gradlew downloadMermaid

# This downloads to: build/downloaded-resources/mermaid/mermaid.min.js
```

### Why Not in src/main/resources?

The `src/main/resources` directory should only contain **source-controlled files**. Build-time dependencies like `mermaid.min.js` should:
- ✅ Be downloaded to the `build/` directory
- ✅ Be copied during the build process
- ✅ Never be committed to version control

This follows standard Gradle conventions where:
- `src/` = source-controlled files
- `build/` = generated/downloaded build artifacts

### Why Not in Git?

The mermaid.min.js file is:
- **Large**: ~2.7 MB minified
- **Binary**: Not suitable for version control
- **Versioned externally**: Already version-controlled by npm

Downloading at build time:
- ✅ Reduces repository size
- ✅ Makes version updates clearer (just change version number)
- ✅ Follows modern dependency management practices
- ✅ Ensures consistent builds across environments
- ✅ Separates source from build artifacts

### Build Integration

The download happens automatically before:
- `processResources` - Copies to build output
- `processTestResources` - Makes available for tests
- `test` - Ensures file exists for test execution
- `build` - Full build includes download

### Upgrading Mermaid

To upgrade to a new version:
1. Update `mermaidVersion` in `build.gradle.kts`
2. Run `./gradlew clean downloadMermaid`
3. Test the new version
4. Commit only the version change in `build.gradle.kts`

The downloaded file in `build/` is automatically cleaned by `./gradlew clean` and never committed.

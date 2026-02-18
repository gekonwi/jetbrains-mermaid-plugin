# Mermaid.js Library

This directory contains the Mermaid.js library used by the plugin.

## Build-Time Download

The `mermaid.min.js` file is **NOT stored in Git**. Instead, it is automatically downloaded during the build process.

### Version Management

The Mermaid version is configured in `build.gradle.kts`:
```kotlin
val mermaidVersion = "11.12.2"
```

### Download Process

The build system automatically downloads Mermaid.js from:
1. **Primary**: jsDelivr CDN - `https://cdn.jsdelivr.net/npm/mermaid@{version}/dist/mermaid.min.js`
2. **Fallback**: npm registry tarball if CDN fails

### Manual Download

If you need to download Mermaid.js manually:

```bash
# Using Gradle task
./gradlew downloadMermaid

# Or download directly
curl -L -o src/main/resources/mermaid/mermaid.min.js \
  https://cdn.jsdelivr.net/npm/mermaid@11.12.2/dist/mermaid.min.js
```

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

### Build Integration

The download happens automatically before:
- `processResources`
- `processTestResources`
- `test`
- `build`

### Upgrading Mermaid

To upgrade to a new version:
1. Update `mermaidVersion` in `build.gradle.kts`
2. Run `./gradlew clean downloadMermaid`
3. Test the new version
4. Commit only the version change in `build.gradle.kts`

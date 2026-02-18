# Docker Build Fix - Ubuntu 24.04 Package Migration

## Issue
The containerized tests (`./run-containerized-tests.sh`) were failing with:
```
E: Package 'libasound2' has no installation candidate
```

## Root Cause
The `eclipse-temurin:17-jdk` Docker image was updated to use Ubuntu 24.04 (Noble Numbat), which includes the time64 (t64) migration. This migration renamed several 32-bit time-related libraries to include a `t64` suffix.

## Affected Packages
The following packages were renamed in Ubuntu 24.04:
- `libasound2` → `libasound2t64`
- `libgtk-3-0` → `libgtk-3-0t64`
- `libatk1.0-0` → `libatk1.0-0t64`
- `libatk-bridge2.0-0` → `libatk-bridge2.0-0t64`

## Solution
Updated `Dockerfile.test` to use the new t64 package names.

## Testing
```bash
# Build the Docker image
docker build -f Dockerfile.test -t test-build .

# Verify packages are installed
docker run --rm test-build dpkg -l | grep -E "libasound|libgtk|libatk"

# Expected output:
# ii  libasound2t64           1.2.11-1ubuntu0.2
# ii  libatk-bridge2.0-0t64   2.52.0-1build1
# ii  libatk1.0-0t64          2.52.0-1build1
# ii  libgtk-3-0t64           3.24.41-4ubuntu1.3
```

## References
- [Ubuntu Time64 Migration](https://wiki.ubuntu.com/ReleaseNotes/Noble#Time64)
- [Debian Time64 Transition](https://wiki.debian.org/ReleaseGoals/64bit-time)

## Prevention
When using base images, pin to specific versions if stability is critical:
```dockerfile
FROM eclipse-temurin:17-jdk-jammy  # Ubuntu 22.04 (older, no t64)
# OR
FROM eclipse-temurin:17-jdk-noble  # Ubuntu 24.04 (with t64, explicit)
```

## Last Updated
2026-02-18

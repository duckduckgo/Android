#!/usr/bin/env zsh

# Clear Gradle and project build caches for performance testing.
# IMPORTANT: Keeps init script /Users/zsolt/.gradle/init.d/bitrise-build-cache.init.gradle.kts intact.

set -euo pipefail

GRADLE_USER_HOME_DEFAULT="$HOME/.gradle"
PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"

print "Gradle user home: ${GRADLE_USER_HOME_DEFAULT}"
print "Project root: ${PROJECT_ROOT}"

# 1) Clear Gradle-level caches, but preserve init.d scripts
if [[ -d "${GRADLE_USER_HOME_DEFAULT}" ]]; then
  print "\n==> Cleaning Gradle user home caches (keeping init.d)…"

  # Wrapper distributions (downloaded Gradle versions)
  rm -rf "${GRADLE_USER_HOME_DEFAULT}/wrapper/dists" || true

  # General caches
  rm -rf "${GRADLE_USER_HOME_DEFAULT}/caches" || true

  # Build-scan / Develocity caches
  rm -rf "${GRADLE_USER_HOME_DEFAULT}/enterprise" || true

  # Local build-cache storage (if configured under ~/.gradle)
  rm -rf "${GRADLE_USER_HOME_DEFAULT}/build-cache" || true

  # DO NOT touch init.d directory, to keep bitrise-build-cache.init.gradle.kts
  print "Preserved: ${GRADLE_USER_HOME_DEFAULT}/init.d/bitrise-build-cache.init.gradle.kts (and other init scripts)"
fi

# 2) Clear project-level build artifacts and caches
print "\n==> Cleaning project-level build directories…"

# Root project build directory (includes configuration cache, analytics, reports, etc.)
rm -rf "${PROJECT_ROOT}/build" || true

# Known Gradle build-cache directory under project (if used)
rm -rf "${PROJECT_ROOT}/build-cache" || true

# Configuration cache location
rm -rf "${PROJECT_ROOT}/.gradle" || true

# Per-module build directories (top-level immediate children only)
for moduleDir in "${PROJECT_ROOT}"/*; do
  if [[ -d "${moduleDir}/build" ]]; then
    print " - Deleting module build dir: ${moduleDir}/build"
    rm -rf "${moduleDir}/build" || true
  fi

  if [[ -d "${moduleDir}/build-cache" ]]; then
    print " - Deleting module build-cache dir: ${moduleDir}/build-cache"
    rm -rf "${moduleDir}/build-cache" || true
  fi

done

print "\nGradle and project caches cleared (init.d left intact)." 
print "You can now run: ./gradlew assembleDebug --no-configuration-cache --no-daemon"
#!/usr/bin/env bash
# Scores every -api module and writes the scorecard, scores CSV, findings CSV and declarations CSV.
# Usage: score.sh [outDir] [date] [previousScoresCsv]
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="${REPO_ROOT:-$(git -C "$SCRIPT_DIR" rev-parse --show-toplevel)}"
OUT_DIR="${1:-$SCRIPT_DIR/output}"
DATE="${2:-$(date +%F)}"
PREVIOUS="${3:-}"

# Pinned to the Kotlin version of the checkout this script runs from, not the one being scored: Scorer.java is compiled
# against these jars, and a newer parser still reads older code.
SCORER_ROOT="$(git -C "$SCRIPT_DIR" rev-parse --show-toplevel)"
KOTLIN_VERSION="$(grep '^version.kotlin=' "$SCORER_ROOT/versions.properties" | cut -d= -f2)"
COROUTINES_VERSION="$(grep '^version.kotlinx.coroutines=' "$SCORER_ROOT/versions.properties" | cut -d= -f2)"
GRADLE_CACHE="${GRADLE_USER_HOME:-$HOME/.gradle}/caches/modules-2/files-2.1"
# CI runners have no Gradle cache, so missing jars are downloaded from Maven Central into this folder once.
JAR_CACHE="${API_QUALITY_JAR_CACHE:-$HOME/.cache/api-quality-scorer}"
MAVEN_CENTRAL="https://repo1.maven.org/maven2"

jar() {
  local group="$1" artifact="$2" version="$3" found
  found="$(find "$GRADLE_CACHE/$group/$artifact/$version" -name "$artifact-$version.jar" 2>/dev/null | head -1)"
  if [[ -n "$found" ]]; then
    echo "$found"
    return
  fi
  found="$JAR_CACHE/$artifact-$version.jar"
  if [[ ! -s "$found" ]]; then
    mkdir -p "$JAR_CACHE"
    echo "Downloading $group:$artifact:$version" >&2
    curl -fsSL -o "$found.tmp" "$MAVEN_CENTRAL/${group//.//}/$artifact/$version/$artifact-$version.jar"
    mv "$found.tmp" "$found"
  fi
  echo "$found"
}

CLASSPATH="$(jar org.jetbrains.kotlin kotlin-compiler-embeddable "$KOTLIN_VERSION")"
CLASSPATH+=":$(jar org.jetbrains.kotlin kotlin-stdlib "$KOTLIN_VERSION")"
CLASSPATH+=":$(jar org.jetbrains.kotlin kotlin-script-runtime "$KOTLIN_VERSION")"
CLASSPATH+=":$(jar org.jetbrains.kotlin kotlin-reflect "$KOTLIN_VERSION")"
CLASSPATH+=":$(jar org.jetbrains.kotlin kotlin-daemon-embeddable "$KOTLIN_VERSION")"
CLASSPATH+=":$(jar org.jetbrains.intellij.deps trove4j 1.0.20200330)"
CLASSPATH+=":$(jar org.jetbrains.kotlinx kotlinx-coroutines-core-jvm "$COROUTINES_VERSION")"

java -Djava.awt.headless=true -Didea.io.use.nio2=true -cp "$CLASSPATH" "$SCRIPT_DIR/Scorer.java" "$REPO_ROOT" "$OUT_DIR" "$DATE" $PREVIOUS

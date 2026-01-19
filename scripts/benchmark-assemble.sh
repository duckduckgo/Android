#!/usr/bin/env zsh

# Keep this script deliberately simple and robust for benchmarking.
# We do NOT use `set -e` so that a single failing command doesn't
# terminate the whole script with a misleading error.
set -u

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CLEANER="${PROJECT_ROOT}/scripts/clear-gradle-caches.sh"
SAVE_CACHES="${PROJECT_ROOT}/scripts/save-gradle-caches.sh"
LOG_DIR="${PROJECT_ROOT}/benchmark-logs"
RUNS=5

# Pin JAVA_HOME if you want a consistent JDK for all runs
export JAVA_HOME=/Users/zsolt/Library/Java/JavaVirtualMachines/ms-21.0.8/Contents/Home

mkdir -p "${LOG_DIR}"

echo "Project root: ${PROJECT_ROOT}"
echo "Using cleaner: ${CLEANER}"
echo "Using saver:   ${SAVE_CACHES}"
echo "Log dir:      ${LOG_DIR}"
echo "Runs:         ${RUNS}"
echo

if [[ ! -x "${CLEANER}" ]]; then
  echo "ERROR: Cleaner script not found or not executable: ${CLEANER}" >&2
  exit 1
fi

if [[ ! -x "${SAVE_CACHES}" ]]; then
  echo "ERROR: Cache saver script not found or not executable: ${SAVE_CACHES}" >&2
  exit 1
fi

if [[ ! -x "${PROJECT_ROOT}/gradlew" ]]; then
  echo "ERROR: ./gradlew not found or not executable in ${PROJECT_ROOT}" >&2
  exit 1
fi

for i in $(seq 1 ${RUNS}); do
  echo "==> Run #${i}"

  mkdir -p  "${LOG_DIR}"

  LOG_FILE="${LOG_DIR}/assembleDebug-run-${i}.log"
  CLEAR_LOG_FILE="${LOG_DIR}/clear-caches-run-${i}.log"
  SAVE_LOG_FILE="${LOG_DIR}/save-caches-run-${i}.log"

  echo "  - Saving current Gradle caches (log -> ${SAVE_LOG_FILE})..."
  "${SAVE_CACHES}" > "${SAVE_LOG_FILE}" 2>&1 || {
    echo "  - WARNING: cache save failed for run #${i}; continuing"
  }

  echo "  - Clearing caches (log -> ${CLEAR_LOG_FILE})..."
  "${CLEANER}" > "${CLEAR_LOG_FILE}" 2>&1

  # Restore Gradle caches from the just-saved tarball before running Gradle.
  CACHE_ARCHIVE="${PROJECT_ROOT}/benchmark-logs/gradle-caches.tar.gz"
  if [[ -f "${CACHE_ARCHIVE}" ]]; then
    echo "  - Restoring Gradle caches from ${CACHE_ARCHIVE} (log -> ${SAVE_LOG_FILE})..."

    RESTORE_TMP="${PROJECT_ROOT}/benchmark-logs/.gradle-restore-tmp"
    rm -rf "${RESTORE_TMP}"
    mkdir -p "${RESTORE_TMP}"

    {
      echo "    * Extracting archive into temporary directory ${RESTORE_TMP}..."
      (
        cd "${RESTORE_TMP}" && \
        tar -xvzf "${CACHE_ARCHIVE}"
      )

      # Move staged gradle-home contents into GRADLE_USER_HOME (~/.gradle by default)
      TARGET_GRADLE_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}"
      if [[ -d "${RESTORE_TMP}/gradle-home" ]]; then
        echo "    * Restoring gradle-home/ into ${TARGET_GRADLE_HOME}..."
        mkdir -p "${TARGET_GRADLE_HOME}"
        rsync -a "${RESTORE_TMP}/gradle-home/" "${TARGET_GRADLE_HOME}/"
      else
        echo "    * WARNING: gradle-home/ not found in archive; skipping GRADLE_USER_HOME restore"
      fi

      # Move staged project-gradle/.gradle into PROJECT_ROOT/.gradle
      if [[ -d "${RESTORE_TMP}/project-gradle/.gradle" ]]; then
        echo "    * Restoring project-gradle/.gradle into ${PROJECT_ROOT}/.gradle..."
        mkdir -p "${PROJECT_ROOT}/.gradle"
        rsync -a "${RESTORE_TMP}/project-gradle/.gradle/" "${PROJECT_ROOT}/.gradle/"
      else
        echo "    * WARNING: project-gradle/.gradle not found in archive; skipping project .gradle restore"
      fi
    } >> "${SAVE_LOG_FILE}" 2>&1 || {
      echo "  - WARNING: cache restore failed for run #${i}, continuing with empty caches"
    }
  else
    echo "  - WARNING: cache archive not found at ${CACHE_ARCHIVE}; caches will be cold for this run"
  fi

  echo "  - Running ./gradlew clean"
  "${PROJECT_ROOT}/gradlew" clean > /dev/null  2>&1

  echo "  - Running ./gradlew assembleDebug (logs -> ${LOG_FILE})"
  START_EPOCH=$(date +%s)
  # All Gradle output goes into the log file (overwrite per run)
  "${PROJECT_ROOT}/gradlew" assembleDebug --info --no-daemon \
    > "${LOG_FILE}" 2>&1
  EXIT_CODE=$?

  END_EPOCH=$(date +%s)
  ELAPSED=$((END_EPOCH - START_EPOCH))

  if [[ ${EXIT_CODE} -ne 0 ]]; then
    printf "  => Run #%-2d FAILED (exit=%d) after %4ds – see %s\n\n" "${i}" "${EXIT_CODE}" "${ELAPSED}" "${LOG_FILE}"
  else
    printf "  => Run #%-2d duration: %4ds (SUCCESS)\n\n" "${i}" "${ELAPSED}"
  fi
done

echo "All runs complete. Logs are under: ${LOG_DIR}"
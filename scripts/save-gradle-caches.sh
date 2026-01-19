#!/usr/bin/env zsh

# Save selected Gradle cache directories into a tarball for reuse.
# This is intended for benchmarking Gradle's caching effectiveness.

set -u

GRADLE_USER_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}"
PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CACHE_ARCHIVE_DIR="${PROJECT_ROOT}/benchmark-logs"
CACHE_ARCHIVE="${CACHE_ARCHIVE_DIR}/gradle-caches.tar.gz"
PROJECT_GRADLE_DIR="${PROJECT_ROOT}/.gradle"

mkdir -p "${CACHE_ARCHIVE_DIR}"

echo "Gradle user home:     ${GRADLE_USER_HOME}"
echo "Project .gradle dir:  ${PROJECT_GRADLE_DIR}"
echo "Archive dir:          ${CACHE_ARCHIVE_DIR}"
echo "Archive file:         ${CACHE_ARCHIVE}"

tmp_root="${PROJECT_ROOT}/benchmark-logs/.gradle-cache-tmp"
rm -rf "${tmp_root}"
mkdir -p "${tmp_root}/gradle-home" "${tmp_root}/project-gradle"

echo "Staging Gradle caches under: ${tmp_root}"

# Stage selected Gradle user home caches under gradle-home/
if [[ -d "${GRADLE_USER_HOME}" ]]; then
  echo "  - Collecting caches from GRADLE_USER_HOME (selected paths only)..."

  # Dependency JARs
  patterns=(
    'caches/jars-*' \
    # Dependency AARs
    'caches/modules-*/files-*' \
    'caches/modules-*/metadata-*' \
    # Generated JARs for plugins and build scripts
    # The `**` segment matches the version-specific folder, such as `7.6`.
    'caches/**/generated-gradle-jars/*.jar' \
    # Kotlin build script cache
    # The `**` segment matches the version-specific folder, such as `7.6`.
    'caches/**/kotlin-dsl' \
    # Cache of downloaded Gradle binary
    'wrapper' \
    # JDKs downloaded by the toolchain support
    'jdks'
  )

  for pattern in "${patterns[@]}"; do
    # Use noglob to avoid zsh expanding the pattern before we hand it to the shell
    # inside the parameter expansion. We then re-enable globbing and expand via ${~...}.
    setopt localoptions noglob
    expanded_pattern="${GRADLE_USER_HOME}/${pattern}"
    setopt localoptions glob
    matches=(${~expanded_pattern})
    for src in "${matches[@]}"; do
      if [[ -e "${src}" ]]; then
        rel_path="${src#${GRADLE_USER_HOME}/}"
        dest="${tmp_root}/gradle-home/${rel_path}"
        echo "    * ${rel_path} -> gradle-home/${rel_path}"
        if [[ -d "${src}" ]]; then
          mkdir -p "${dest}"
          rsync -a --delete "${src}/" "${dest}/"
        else
          mkdir -p "${dest:h}"
          rsync -a "${src}" "${dest}"
        fi
      fi
    done
  done
else
  echo "WARNING: GRADLE_USER_HOME does not exist: ${GRADLE_USER_HOME}" >&2
fi

# Stage project .gradle under project-gradle/
if [[ -d "${PROJECT_GRADLE_DIR}" ]]; then
  echo "  - Collecting project .gradle into project-gradle/.gradle..."
  rsync -a --delete "${PROJECT_GRADLE_DIR}/" "${tmp_root}/project-gradle/.gradle/"
else
  echo "WARNING: Project .gradle directory not found at ${PROJECT_GRADLE_DIR}; skipping" >&2
fi

if [[ ! -d "${tmp_root}/gradle-home" && ! -d "${tmp_root}/project-gradle" ]]; then
  echo "WARNING: Nothing staged under ${tmp_root}; nothing to archive." >&2
else
  echo "Creating cache archive from staged directories..."
  (
    cd "${tmp_root}" && \
    tar -czvf "${CACHE_ARCHIVE}" gradle-home project-gradle
  ) || {
    echo "ERROR: tar failed when creating cache archive" >&2
    exit 1
  }
fi

echo "Cache archive created at: ${CACHE_ARCHIVE}"
ls -hal "${CACHE_ARCHIVE}"

# Leave staged directory around for inspection; caller may clean it if desired.
echo "Cache archive created at: ${CACHE_ARCHIVE}"
ls -hal "${CACHE_ARCHIVE}"
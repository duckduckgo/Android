#!/usr/bin/env bash
#
# Copyright (c) 2026 DuckDuckGo
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

set -euo pipefail

# Where to put Breakpad symbols
SYMBOLS_ROOT=${1:-"build/symbols"}
# Where to search for .so (override with $2 if you want)
# Prefer unstripped outputs (obj/** or .cxx/**). Adjust as needed for your project.
SEARCH_ROOT=${2:-"app"}

# Common places for unstripped libs (CMake/AGP variants); tweak or add paths if needed.
CANDIDATES=(
  "$SEARCH_ROOT/.cxx/*/obj/**"                               # AGP .cxx (RelWithDebInfo/Debug) unstripped
  "$SEARCH_ROOT/build/intermediates/cxx/*/obj/**"            # Older AGP
  "$SEARCH_ROOT/build/intermediates/merged_native_libs/**/out/lib/**"  # Sometimes unstripped, sometimes not
  "$SEARCH_ROOT/build/intermediates/library_and_local_jars_jni/**"     # AAR JNI merges
)

echo ">> Symbols root: $SYMBOLS_ROOT"
rm -rf "$SYMBOLS_ROOT"
mkdir -p "$SYMBOLS_ROOT"

shopt -s globstar nullglob

found_any=false
for path in "${CANDIDATES[@]}"; do
  for so in $path/*.so; do
    found_any=true
    tmpfile="$(mktemp)"
    if dump_syms "$so" >"$tmpfile" 2>/dev/null; then
      # MODULE <OS> <ARCH> <DEBUG_ID> <DEBUG_FILE>
      # Read DEBUG_ID and DEBUG_FILE from the first line
      read -r _ _ _ DEBUG_ID DEBUG_FILE < <(head -n 1 "$tmpfile")
      if [[ -z "${DEBUG_ID:-}" || -z "${DEBUG_FILE:-}" ]]; then
        echo "!! Couldn't parse MODULE line for $so; skipping"
        rm -f "$tmpfile"
        continue
      fi
      outdir="$SYMBOLS_ROOT/$DEBUG_FILE/$DEBUG_ID"
      mkdir -p "$outdir"
      mv "$tmpfile" "$outdir/$DEBUG_FILE.sym"
      echo "OK  $DEBUG_FILE  ->  $outdir/$DEBUG_FILE.sym"
    else
      echo "!! dump_syms failed for $so (maybe stripped?); skipping"
      rm -f "$tmpfile"
    fi
  done
done

if ! $found_any; then
  echo "!! No .so files found. Point the script at your unstripped outputs, e.g.:"
  echo "   $0 build/symbols app/.cxx/RelWithDebInfo/obj"
  exit 1
fi

echo ">> Done. You can now run:"
echo "   minidump-stackwalk /path/to/crash.dmp $SYMBOLS_ROOT"


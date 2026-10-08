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

ABI_LIST=("arm64-v8a")   # add more if you build them
SYMSTORE="${SYMSTORE:-$PWD/build/symbols}"
DUMP_SYMS="dump_syms"  # path to your dump_syms

mkdir -p "$SYMSTORE"

# Find candidate .so in all modules (CMake/ndk-build output + merged copies)
mapfile -t SO_FILES < <(
  find . -type f \( \
    -path "*/.cxx/*/obj/*/*.so" -o \
    -path "*/build/intermediates/merged_native_libs/*/out/lib/*/*.so" \
  \) | grep -E "$(IFS='|'; echo "${ABI_LIST[*]}")"
)

for so in "${SO_FILES[@]}"; do
  # Breakpad .sym
  sym_tmp="$SYMSTORE/$(basename "$so").sym"
  "$DUMP_SYMS" -a arm64 "$so" > "$sym_tmp"
  modid=$(head -n1 "$sym_tmp" | awk '{print $4}')
  name=$(basename "$so")
  mkdir -p "$SYMSTORE/$name/$modid"
  mv "$sym_tmp" "$SYMSTORE/$name/$modid/$name.sym"
  echo "SYMBOLIZED: $name  =>  $modid"
done


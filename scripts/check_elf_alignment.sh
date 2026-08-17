#!/usr/bin/env bash

set -uo pipefail

PROGRAM_NAME="${0##*/}"

PAGE_SIZE=16384
PAGE_SIZE_KB=16

# Exit codes:
#   0 = all validation checks passed
#   1 = one or more validation checks failed
#   2 = script/tooling/configuration error

# Stable, machine-readable status labels. CI can grep these regardless of colour:
#   PASS | WARN | FAIL | ERROR | RESULT
#
# Colour is enabled only for an interactive terminal and disabled when:
#   - stdout is not a TTY (typical CI behaviour)
#   - NO_COLOR is set
#   - TERM=dumb

RED=""
GREEN=""
YELLOW=""
BOLD=""
RESET=""

if [[ -t 1 && -z "${NO_COLOR:-}" && "${TERM:-}" != "dumb" ]] \
    && command -v tput >/dev/null 2>&1; then
    RED="$(tput setaf 1 2>/dev/null || true)"
    GREEN="$(tput setaf 2 2>/dev/null || true)"
    YELLOW="$(tput setaf 3 2>/dev/null || true)"
    BOLD="$(tput bold 2>/dev/null || true)"
    RESET="$(tput sgr0 2>/dev/null || true)"
fi

TOTAL_INPUTS=0
PASSED_INPUTS=0
FAILED_INPUTS=0
TOTAL_ELFS=0
PASSED_ELFS=0
FAILED_ELFS=0
WARNING_COUNT=0
ERROR_COUNT=0

print_status() {
    local status="$1"
    shift

    case "${status}" in
        PASS)
            printf '%s[PASS]%s %s\n' "${GREEN}" "${RESET}" "$*"
            ;;
        WARN)
            printf '%s[WARN]%s %s\n' "${YELLOW}" "${RESET}" "$*"
            ;;
        FAIL)
            printf '%s[FAIL]%s %s\n' "${RED}" "${RESET}" "$*"
            ;;
        ERROR)
            printf '%s[ERROR]%s %s\n' "${RED}" "${RESET}" "$*" >&2
            ;;
        INFO)
            printf '[INFO] %s\n' "$*"
            ;;
        *)
            printf '[%s] %s\n' "${status}" "$*"
            ;;
    esac
}

print_detail() {
    local level="$1"
    shift

    case "${level}" in
        WARN)
            printf '       %swarning:%s %s\n' "${YELLOW}" "${RESET}" "$*"
            ;;
        ERROR)
            printf '       %serror:%s %s\n' "${RED}" "${RESET}" "$*"
            ;;
        *)
            printf '       %s\n' "$*"
            ;;
    esac
}

usage() {
    cat <<EOF
Validate Android native libraries for 16 KB page-size compatibility.

Usage:
  ${PROGRAM_NAME} <APK|AAB|SO|directory> [...]

Examples:
  ${PROGRAM_NAME} app-release.apk
  ${PROGRAM_NAME} app-release.aab
  ${PROGRAM_NAME} path/to/libfoo.so
  ${PROGRAM_NAME} path/to/extracted/libs

Checks:
  - APK ZIP alignment using: zipalign -c -P 16 -v 4
  - Every PT_LOAD segment has p_align >= 16 KB
  - PT_LOAD file-offset / virtual-address congruence
  - PT_GNU_RELRO layout and its 16 KB permission boundary
  - Multiple PT_GNU_RELRO segments

Relevant packaged ABIs:
  - arm64-v8a
  - x86_64

CI behaviour:
  - Colour is disabled automatically when stdout is not a TTY.
  - Set NO_COLOR=1 to disable colour explicitly.
  - A stable final line is always printed:
      RESULT=PASS|FAIL inputs=N elf_files=N passed=N failed=N warnings=N errors=N
  - Exit 0: all checks passed
  - Exit 1: validation failure
  - Exit 2: tooling or configuration error
EOF
}

canonical_path() {
    python3 - "$1" <<'PY'
import os
import sys

print(os.path.realpath(sys.argv[1]))
PY
}

find_android_sdk() {
    local candidate

    for candidate in \
        "${ANDROID_HOME:-}" \
        "${ANDROID_SDK_ROOT:-}" \
        "${HOME}/Library/Android/sdk" \
        "${HOME}/Android/Sdk"
    do
        if [[ -n "${candidate}" && -d "${candidate}" ]]; then
            canonical_path "${candidate}"
            return 0
        fi
    done

    return 1
}

find_readelf() {
    local candidate
    local sdk_root=""

    if command -v llvm-readelf >/dev/null 2>&1; then
        command -v llvm-readelf
        return 0
    fi

    if command -v readelf >/dev/null 2>&1; then
        command -v readelf
        return 0
    fi

    for candidate in \
        "${ANDROID_NDK_HOME:-}" \
        "${ANDROID_NDK_ROOT:-}"
    do
        if [[ -n "${candidate}" && -d "${candidate}" ]]; then
            candidate="$(
                find "${candidate}" \
                    -type f \
                    -path '*/toolchains/llvm/prebuilt/*/bin/llvm-readelf' \
                    -perm -111 \
                    2>/dev/null |
                sort -V |
                tail -n 1
            )"

            if [[ -n "${candidate}" ]]; then
                printf '%s\n' "${candidate}"
                return 0
            fi
        fi
    done

    sdk_root="$(find_android_sdk 2>/dev/null || true)"

    if [[ -n "${sdk_root}" ]]; then
        candidate="$(
            find "${sdk_root}/ndk" \
                -type f \
                -path '*/toolchains/llvm/prebuilt/*/bin/llvm-readelf' \
                -perm -111 \
                2>/dev/null |
            sort -V |
            tail -n 1
        )"

        if [[ -n "${candidate}" ]]; then
            printf '%s\n' "${candidate}"
            return 0
        fi

        candidate="$(
            find "${sdk_root}/ndk-bundle" \
                -type f \
                -path '*/toolchains/llvm/prebuilt/*/bin/llvm-readelf' \
                -perm -111 \
                2>/dev/null |
            sort -V |
            tail -n 1
        )"

        if [[ -n "${candidate}" ]]; then
            printf '%s\n' "${candidate}"
            return 0
        fi
    fi

    return 1
}

find_zipalign() {
    local candidate
    local sdk_root=""

    if command -v zipalign >/dev/null 2>&1; then
        command -v zipalign
        return 0
    fi

    sdk_root="$(find_android_sdk 2>/dev/null || true)"

    if [[ -n "${sdk_root}" ]]; then
        candidate="$(
            find "${sdk_root}/build-tools" \
                -type f \
                -name zipalign \
                -perm -111 \
                2>/dev/null |
            sort -V |
            tail -n 1
        )"

        if [[ -n "${candidate}" ]]; then
            printf '%s\n' "${candidate}"
            return 0
        fi
    fi

    return 1
}

check_zip_alignment() {
    local apk="$1"
    local zipalign_bin="$2"
    local output
    local status

    output="$(
        "${zipalign_bin}" \
            -c \
            -P "${PAGE_SIZE_KB}" \
            -v \
            4 \
            "${apk}" 2>&1
    )"
    status=$?

    if [[ ${status} -eq 0 ]]; then
        print_status PASS "ZIP  ${apk}"
        return 0
    fi

    print_status FAIL "ZIP  ${apk}"
    ERROR_COUNT=$((ERROR_COUNT + 1))

    while IFS= read -r line; do
        [[ -n "${line}" ]] && print_detail ERROR "${line}"
    done <<< "${output}"

    return 1
}

check_elf_raw() {
    local elf="$1"
    local readelf_bin="$2"

    python3 - \
        "${elf}" \
        "${readelf_bin}" \
        "${PAGE_SIZE}" <<'PY'
from __future__ import print_function

import subprocess
import sys


elf_path = sys.argv[1]
readelf = sys.argv[2]
page_size = int(sys.argv[3])


def emit(kind, message):
    # Tab-separated records are easy for the Bash wrapper and CI tooling to
    # parse. Messages are normalised to a single line.
    message = str(message).replace("\t", " ").replace("\r", " ").replace("\n", " ")
    print("{}\t{}".format(kind, message))


def parse_number(value):
    return int(value, 0)


def align_down(value, alignment):
    return value & ~(alignment - 1)


def align_up(value, alignment):
    return (value + alignment - 1) & ~(alignment - 1)


def raw_end(segment):
    return segment["vaddr"] + segment["memsz"]


def parse_program_headers(output):
    segments = []

    for raw_line in output.splitlines():
        fields = raw_line.split()

        if not fields:
            continue

        segment_type = fields[0]

        if segment_type not in ("LOAD", "GNU_RELRO"):
            continue

        if len(fields) < 8:
            raise ValueError(
                "Could not parse program-header row: {!r}".format(raw_line)
            )

        try:
            offset = parse_number(fields[1])
            vaddr = parse_number(fields[2])
            filesz = parse_number(fields[4])
            memsz = parse_number(fields[5])
            align = parse_number(fields[-1])
        except ValueError:
            raise ValueError(
                "Could not parse numeric fields in row: {!r}".format(raw_line)
            )

        flags = "".join(fields[6:-1])

        segments.append(
            {
                "type": segment_type,
                "offset": offset,
                "vaddr": vaddr,
                "filesz": filesz,
                "memsz": memsz,
                "flags": flags,
                "align": align,
            }
        )

    return segments


def classify_relro_position(load, relro):
    alignment = load["align"]

    if alignment <= 0 or alignment & (alignment - 1):
        return "ERROR"

    segment_start = align_down(load["vaddr"], alignment)
    segment_end = align_up(raw_end(load), alignment)

    relro_start = align_down(relro["vaddr"], alignment)
    relro_end = align_up(raw_end(relro), alignment)

    if relro_end <= segment_start or relro_start >= segment_end:
        return "NONE"

    if relro_start < segment_start or relro_end > segment_end:
        return "ERROR"

    if relro_start == segment_start:
        if relro_end < segment_end:
            return "PREFIX"
        return "ENTIRE"

    if relro_end == segment_end:
        return "SUFFIX"

    return "MIDDLE"


try:
    result = subprocess.run(
        [readelf, "--program-headers", "--wide", elf_path],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        check=False,
    )
except OSError as error:
    emit("TOOL_ERROR", "Could not execute {}: {}".format(readelf, error))
    sys.exit(2)


if result.returncode != 0:
    details = result.stderr.strip() or "readelf returned exit code {}".format(
        result.returncode
    )
    emit("TOOL_ERROR", details)
    sys.exit(2)


try:
    segments = parse_program_headers(result.stdout)
except ValueError as error:
    emit("TOOL_ERROR", error)
    sys.exit(2)


load_segments = [
    segment for segment in segments if segment["type"] == "LOAD"
]
relro_segments = [
    segment for segment in segments if segment["type"] == "GNU_RELRO"
]

errors = []
warnings = []


if not load_segments:
    errors.append("No PT_LOAD segments were found.")

for index, load in enumerate(load_segments):
    alignment = load["align"]
    offset = load["offset"]
    vaddr = load["vaddr"]

    if alignment < page_size:
        errors.append(
            "PT_LOAD[{index}] p_align=0x{actual:x}; expected >=0x{expected:x}.".format(
                index=index,
                actual=alignment,
                expected=page_size,
            )
        )

    if offset % page_size != vaddr % page_size:
        errors.append(
            "PT_LOAD[{index}] is not congruent for 16 KB pages: "
            "p_offset=0x{offset:x}, p_vaddr=0x{vaddr:x}.".format(
                index=index,
                offset=offset,
                vaddr=vaddr,
            )
        )


if len(relro_segments) > 1:
    errors.append(
        "Found {} PT_GNU_RELRO segments; expected at most one.".format(
            len(relro_segments)
        )
    )

elif not relro_segments:
    warnings.append("No PT_GNU_RELRO segment was found.")

else:
    relro = relro_segments[0]
    relro_start = relro["vaddr"]
    relro_end = raw_end(relro)

    matching_loads = []

    for load_index, load in enumerate(load_segments):
        position = classify_relro_position(load, relro)

        if position != "NONE":
            matching_loads.append((load_index, load, position))

    invalid_matches = [
        match for match in matching_loads if match[2] == "ERROR"
    ]
    valid_matches = [
        match
        for match in matching_loads
        if match[2] in ("PREFIX", "MIDDLE", "SUFFIX", "ENTIRE")
    ]

    if invalid_matches and not valid_matches:
        errors.append(
            "PT_GNU_RELRO spans outside aligned PT_LOAD boundaries: "
            "start=0x{start:x}, end=0x{end:x}.".format(
                start=relro_start,
                end=relro_end,
            )
        )

    elif not valid_matches:
        errors.append(
            "PT_GNU_RELRO does not overlap a PT_LOAD segment: "
            "start=0x{start:x}, end=0x{end:x}.".format(
                start=relro_start,
                end=relro_end,
            )
        )

    else:
        writable_matches = [
            match for match in valid_matches if "W" in match[1]["flags"]
        ]

        if writable_matches:
            load_index, _, relro_position = writable_matches[0]
        else:
            load_index, _, relro_position = valid_matches[0]
            errors.append(
                "PT_GNU_RELRO is not associated with a writable PT_LOAD segment."
            )

        if (
            relro_position not in ("SUFFIX", "ENTIRE")
            and relro_end % page_size != 0
        ):
            errors.append(
                "PT_GNU_RELRO is not a suffix and its end is not 16 KB "
                "aligned: start=0x{start:08x}, end=0x{end:08x}, "
                "remainder=0x{remainder:x}, position={position}, "
                "PT_LOAD[{index}].".format(
                    start=relro_start,
                    end=relro_end,
                    remainder=relro_end % page_size,
                    position=relro_position,
                    index=load_index,
                )
            )

        if relro_position == "MIDDLE":
            warnings.append(
                "PT_GNU_RELRO is in the middle of PT_LOAD[{}].".format(
                    load_index
                )
            )


for error in errors:
    emit("ERROR", error)

for warning in warnings:
    emit("WARN", warning)

if errors:
    emit("RESULT", "FAIL")
    sys.exit(1)

emit("RESULT", "PASS")
sys.exit(0)
PY
}

render_elf_result() {
    local elf="$1"
    local readelf_bin="$2"
    local output
    local status
    local kind
    local message
    local result="ERROR"
    local -a detail_kinds=()
    local -a detail_messages=()

    output="$(check_elf_raw "${elf}" "${readelf_bin}")"
    status=$?

    while IFS=$'\t' read -r kind message; do
        case "${kind}" in
            ERROR|TOOL_ERROR)
                detail_kinds+=("ERROR")
                detail_messages+=("${message}")
                ERROR_COUNT=$((ERROR_COUNT + 1))
                ;;
            WARN)
                detail_kinds+=("WARN")
                detail_messages+=("${message}")
                WARNING_COUNT=$((WARNING_COUNT + 1))
                ;;
            RESULT)
                result="${message}"
                ;;
        esac
    done <<< "${output}"

    TOTAL_ELFS=$((TOTAL_ELFS + 1))

    local rc
    if [[ ${status} -eq 0 && "${result}" == "PASS" ]]; then
        PASSED_ELFS=$((PASSED_ELFS + 1))
        print_status PASS "ELF  ${elf}"
        rc=0
    else
        FAILED_ELFS=$((FAILED_ELFS + 1))
        if [[ ${status} -eq 2 ]]; then
            print_status ERROR "ELF  ${elf}"
            rc=2
        else
            print_status FAIL "ELF  ${elf}"
            rc=1
        fi
    fi

    local i
    for (( i = 0; i < ${#detail_kinds[@]}; i++ )); do
        print_detail "${detail_kinds[i]}" "${detail_messages[i]}"
    done

    return "${rc}"
}

is_relevant_abi_path() {
    local path="$1"

    case "${path}" in
        */lib/arm64-v8a/* | */lib/x86_64/*)
            return 0
            ;;
        */lib/armeabi-v7a/* | */lib/x86/*)
            return 1
            ;;
        *)
            return 0
            ;;
    esac
}

extract_apk() {
    local apk="$1"
    local destination="$2"
    local status

    unzip -qq \
        "${apk}" \
        'lib/arm64-v8a/*.so' \
        'lib/x86_64/*.so' \
        -d "${destination}" \
        2>/dev/null

    status=$?

    if [[ ${status} -ne 0 && ${status} -ne 11 ]]; then
        return "${status}"
    fi

    return 0
}

extract_aab() {
    local aab="$1"
    local destination="$2"
    local status

    unzip -qq \
        "${aab}" \
        '*/lib/arm64-v8a/*.so' \
        '*/lib/x86_64/*.so' \
        -d "${destination}" \
        2>/dev/null

    status=$?

    if [[ ${status} -ne 0 && ${status} -ne 11 ]]; then
        return "${status}"
    fi

    return 0
}

scan_elf_files() {
    local root="$1"
    local readelf_bin="$2"
    local found_file=0
    local scan_result=0
    local candidate
    local status

    while IFS= read -r -d '' candidate; do
        if ! is_relevant_abi_path "${candidate}"; then
            continue
        fi

        found_file=1
        render_elf_result "${candidate}" "${readelf_bin}"
        status=$?

        if [[ ${status} -eq 2 ]]; then
            scan_result=2
        elif [[ ${status} -eq 1 && ${scan_result} -eq 0 ]]; then
            scan_result=1
        fi
    done < <(
        if [[ -f "${root}" ]]; then
            printf '%s\0' "${root}"
        else
            find "${root}" -type f -name '*.so' -print0
        fi
    )

    if [[ ${found_file} -eq 0 ]]; then
        print_status WARN "No arm64-v8a or x86_64 shared libraries found under ${root}"
        WARNING_COUNT=$((WARNING_COUNT + 1))
    fi

    return "${scan_result}"
}

process_input() {
    local input="$1"
    local readelf_bin="$2"
    local zipalign_bin="$3"
    local temporary_directory=""
    local scan_root=""
    local result=0
    local status

    TOTAL_INPUTS=$((TOTAL_INPUTS + 1))

    echo
    printf '%s%s%s\n' "${BOLD}" "${input}" "${RESET}"
    printf '%*s\n' "${#input}" '' | tr ' ' '-'

    if [[ ! -e "${input}" ]]; then
        print_status ERROR "Input does not exist: ${input}"
        ERROR_COUNT=$((ERROR_COUNT + 1))
        FAILED_INPUTS=$((FAILED_INPUTS + 1))
        return 2
    fi

    case "${input}" in
        *.apk)
            if [[ -z "${zipalign_bin}" ]]; then
                print_status ERROR \
                    "zipalign was not found; install Android SDK Build-Tools 35.0.0 or newer."
                ERROR_COUNT=$((ERROR_COUNT + 1))
                result=2
            else
                check_zip_alignment "${input}" "${zipalign_bin}" || result=1
            fi

            temporary_directory="$(
                mktemp -d -t check_elf_alignment.XXXXXX
            )" || {
                print_status ERROR "Could not create a temporary directory."
                ERROR_COUNT=$((ERROR_COUNT + 1))
                FAILED_INPUTS=$((FAILED_INPUTS + 1))
                return 2
            }

            if ! extract_apk "${input}" "${temporary_directory}"; then
                print_status ERROR "Could not extract native libraries from ${input}"
                ERROR_COUNT=$((ERROR_COUNT + 1))
                rm -rf "${temporary_directory}"
                FAILED_INPUTS=$((FAILED_INPUTS + 1))
                return 2
            fi

            scan_root="${temporary_directory}"
            ;;

        *.aab)
            print_status WARN \
                "AAB ZIP layout is not the final APK layout; validate a generated APK too."
            WARNING_COUNT=$((WARNING_COUNT + 1))

            temporary_directory="$(
                mktemp -d -t check_elf_alignment.XXXXXX
            )" || {
                print_status ERROR "Could not create a temporary directory."
                ERROR_COUNT=$((ERROR_COUNT + 1))
                FAILED_INPUTS=$((FAILED_INPUTS + 1))
                return 2
            }

            if ! extract_aab "${input}" "${temporary_directory}"; then
                print_status ERROR "Could not extract native libraries from ${input}"
                ERROR_COUNT=$((ERROR_COUNT + 1))
                rm -rf "${temporary_directory}"
                FAILED_INPUTS=$((FAILED_INPUTS + 1))
                return 2
            fi

            scan_root="${temporary_directory}"
            ;;

        *)
            scan_root="${input}"
            ;;
    esac

    scan_elf_files "${scan_root}" "${readelf_bin}"
    status=$?

    if [[ ${status} -eq 2 ]]; then
        result=2
    elif [[ ${status} -eq 1 && ${result} -eq 0 ]]; then
        result=1
    fi

    if [[ -n "${temporary_directory}" ]]; then
        rm -rf "${temporary_directory}"
    fi

    if [[ ${result} -eq 0 ]]; then
        PASSED_INPUTS=$((PASSED_INPUTS + 1))
    else
        FAILED_INPUTS=$((FAILED_INPUTS + 1))
    fi

    return "${result}"
}

print_summary() {
    local result="$1"

    echo
    printf '%sSummary%s\n' "${BOLD}" "${RESET}"
    printf '%s\n' '-------'
    printf 'Inputs:   %d total, %d passed, %d failed\n' \
        "${TOTAL_INPUTS}" "${PASSED_INPUTS}" "${FAILED_INPUTS}"
    printf 'ELFs:     %d total, %d passed, %d failed\n' \
        "${TOTAL_ELFS}" "${PASSED_ELFS}" "${FAILED_ELFS}"
    printf 'Findings: %d warnings, %d errors\n' \
        "${WARNING_COUNT}" "${ERROR_COUNT}"

    if [[ "${result}" == "PASS" ]]; then
        print_status PASS "16 KB verification passed"
    else
        print_status FAIL "16 KB verification failed"
    fi

    # Stable machine-readable line. Do not colour or reformat this.
    printf 'RESULT=%s inputs=%d elf_files=%d passed=%d failed=%d warnings=%d errors=%d\n' \
        "${result}" \
        "${TOTAL_INPUTS}" \
        "${TOTAL_ELFS}" \
        "${PASSED_ELFS}" \
        "${FAILED_ELFS}" \
        "${WARNING_COUNT}" \
        "${ERROR_COUNT}"
}

main() {
    local readelf_bin=""
    local zipalign_bin=""
    local overall_status=0
    local input
    local status

    if [[ $# -eq 0 ]]; then
        usage
        return 2
    fi

    case "${1}" in
        --help | -h)
            usage
            return 0
            ;;
    esac

    if ! command -v python3 >/dev/null 2>&1; then
        print_status ERROR "python3 is required."
        return 2
    fi

    if ! command -v unzip >/dev/null 2>&1; then
        print_status ERROR "unzip is required."
        return 2
    fi

    if ! readelf_bin="$(find_readelf)"; then
        print_status ERROR "Neither llvm-readelf nor readelf was found."
        printf '%s\n' \
            "Set ANDROID_NDK_HOME, ANDROID_NDK_ROOT, ANDROID_HOME, or ANDROID_SDK_ROOT." \
            >&2
        return 2
    fi

    zipalign_bin="$(find_zipalign 2>/dev/null || true)"

    print_status INFO "readelf=${readelf_bin}"

    if [[ -n "${zipalign_bin}" ]]; then
        print_status INFO "zipalign=${zipalign_bin}"
    else
        print_status WARN "zipalign was not found; APK ZIP checks will fail."
        WARNING_COUNT=$((WARNING_COUNT + 1))
    fi

    for input in "$@"; do
        process_input "${input}" "${readelf_bin}" "${zipalign_bin}"
        status=$?

        if [[ ${status} -eq 2 ]]; then
            overall_status=2
        elif [[ ${status} -eq 1 && ${overall_status} -eq 0 ]]; then
            overall_status=1
        fi
    done

    if [[ ${overall_status} -eq 0 ]]; then
        print_summary PASS
    else
        print_summary FAIL
    fi

    return "${overall_status}"
}

main "$@"

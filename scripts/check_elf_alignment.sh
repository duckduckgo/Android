#!/bin/bash
progname="${0##*/}"
progname="${progname%.sh}"
# usage: check_elf_alignment.sh [path to *.so files|path to *.apk]
cleanup_trap() {
  if [ -n "${tmp}" -a -d "${tmp}" ]; then
    rm -rf ${tmp}
  fi
  exit $1
}
usage() {
  echo "Host side script to check the ELF alignment of shared libraries."
  echo "Shared libraries are reported ALIGNED when their ELF regions are"
  echo "16 KB or 64 KB aligned. Otherwise they are reported as UNALIGNED."
  echo "Libraries whose PT_GNU_RELRO segment cannot be protected at a 16 KB"
  echo "page size are additionally reported as RELRO MISALIGNED."
  echo
  echo "Usage: ${progname} [input-path|input-APK|input-APEX]"
}
if [ ${#} -ne 1 ]; then
  usage
  exit
fi
case ${1} in
  --help | -h | -\?)
    usage
    exit
    ;;
  *)
    dir="${1}"
    ;;
esac
if ! [ -f "${dir}" -o -d "${dir}" ]; then
  echo "Invalid file: ${dir}" >&2
  exit 1
fi
if [[ "${dir}" == *.apk ]]; then
  trap 'cleanup_trap' EXIT
  echo
  echo "Recursively analyzing $dir"
  echo
  if { zipalign --help 2>&1 | grep -q "\-P <pagesize_kb>"; }; then
    echo "=== APK zip-alignment ==="
    zipalign -v -c -P 16 4 "${dir}" | egrep 'lib/arm64-v8a|lib/x86_64|Verification'
    echo "========================="
  else
    echo "NOTICE: Zip alignment check requires build-tools version 35.0.0-rc3 or higher."
    echo "  You can install the latest build-tools by running the below command"
    echo "  and updating your \$PATH:"
    echo
    echo "    sdkmanager \"build-tools;35.0.0-rc3\""
  fi
  dir_filename=$(basename "${dir}")
  tmp=$(mktemp -d -t "${dir_filename%.apk}_out_XXXXX")
  unzip "${dir}" lib/* -d "${tmp}" >/dev/null 2>&1
  dir="${tmp}"
fi
if [[ "${dir}" == *.apex ]]; then
  trap 'cleanup_trap' EXIT
  echo
  echo "Recursively analyzing $dir"
  echo
  dir_filename=$(basename "${dir}")
  tmp=$(mktemp -d -t "${dir_filename%.apex}_out_XXXXX")
  deapexer extract "${dir}" "${tmp}" || { echo "Failed to deapex." && exit 1; }
  dir="${tmp}"
fi
RED=$(tput setaf 1)
GREEN=$(tput setaf 2)
ENDCOLOR=$(tput sgr0)
unaligned_libs=()

# >>> DDG local addition: PT_GNU_RELRO 16 KB alignment (not in upstream AOSP) >>>
misaligned_relro_libs=()

# Echoes the RELRO end address when the segment cannot be protected at a 16 KB
# page size, and nothing otherwise.
#
# To protect RELRO the loader must round its end up to a page boundary. That is
# only safe when no writable data lives between the real end and that boundary,
# so the test is whether any writable LOAD segment has bytes inside
# [relro_end, round_up(relro_end, 16 KB)) -- this is what upstream tooling calls
# "RELRO is not a suffix".
#
# Testing relro_end against a writable segment's end for equality is wrong: lld
# pads PT_GNU_RELRO's p_memsz up to max-page-size, so a safe RELRO usually
# overshoots its segment and keeps real writable data in a later LOAD segment on
# a later page. Only the interval test above distinguishes the two cases.
#
# objdump is used rather than llvm-readelf to keep this script free of any NDK
# dependency. It splits each program header over two lines:
#    RELRO off 0x..1e8c20 vaddr 0x..1ecc20 paddr 0x..1ecc20 align 2**0
#          filesz 0x..163e0 memsz 0x..163e0 flags r--
relro_misaligned_end() {
  # not named "rm": bash locals are dynamically scoped and would shadow the command
  local elf="${1}" phdrs rv rmemsz relro_end pad_end v m
  phdrs="$(objdump -p "${elf}" 2>/dev/null | awk '
    /^ *(LOAD|RELRO) +off/ { type = $1; vaddr = $5; next }
    /^ *filesz/ {
      if (type == "RELRO") print "RELRO", vaddr, $4
      else if (type == "LOAD" && $6 ~ /w/) print "LOADW", vaddr, $4
      type = ""
    }')"

  rv="$(echo "${phdrs}" | awk '$1 == "RELRO" { print $2; exit }')"
  rmemsz="$(echo "${phdrs}" | awk '$1 == "RELRO" { print $3; exit }')"
  [ -z "${rv}" ] && return 0  # no PT_GNU_RELRO segment, nothing to check

  rv=$(( rv ))
  relro_end=$(( rv + rmemsz ))
  (( relro_end % 16384 == 0 )) && return 0

  pad_end=$(( (relro_end + 16383) / 16384 * 16384 ))

  # read must be told to split on spaces: the caller sets IFS=$'\n'
  while IFS=' ' read -r _ v m; do
    [ -z "${v}" ] && continue
    v=$(( v )); m=$(( m ))
    # does [v, v+m) overlap the padding range [relro_end, pad_end)?
    if (( v < pad_end && v + m > relro_end )); then
      printf '0x%x\n' "${relro_end}"
      return 0
    fi
  done <<< "$(echo "${phdrs}" | grep '^LOADW')"

  return 0  # nothing writable in the padding range, safe to round up
}
# <<< DDG local addition <<<

echo
echo "=== ELF alignment ==="
matches="$(find "${dir}" -type f)"
IFS=$'\n'
exit_code=0

for match in $matches; do
  [[ "${match}" == *".apk" ]] && echo "WARNING: doesn't recursively inspect .apk file: ${match}"
  [[ "${match}" == *".apex" ]] && echo "WARNING: doesn't recursively inspect .apex file: ${match}"
  [[ $(file "${match}") == *"ELF"* ]] || continue
  res="$(objdump -p "${match}" | grep LOAD | awk '{ print $NF }' | head -1)"
  # >>> DDG local addition >>>
  relro_note=""
  relro_end="$(relro_misaligned_end "${match}")"
  if [ -n "${relro_end}" ]; then
    relro_note=" ${RED}RELRO MISALIGNED${ENDCOLOR} (end=${relro_end})"
    misaligned_relro_libs+=("${match}")
    exit_code=1
  fi
  # <<< DDG local addition <<<
  if [[ $res =~ 2\*\*(1[4-9]|[2-9][0-9]|[1-9][0-9]{2,}) ]]; then
    echo -e "${match}: ${GREEN}ALIGNED${ENDCOLOR} ($res)${relro_note}"
  else
    echo -e "${match}: ${RED}UNALIGNED${ENDCOLOR} ($res)${relro_note}"
    unaligned_libs+=("${match}")
    exit_code=1
  fi
done

if [ ${#unaligned_libs[@]} -gt 0 ]; then
  echo -e "${RED}Found ${#unaligned_libs[@]} unaligned libs (only arm64-v8a/x86_64 libs need to be aligned).${ENDCOLOR}"
fi
# >>> DDG local addition >>>
# Reported separately: a misaligned RELRO end needs the library relinked, so it
# is not fixable by repackaging the way a zip-alignment failure is.
if [ ${#misaligned_relro_libs[@]} -gt 0 ]; then
  echo -e "${RED}Found ${#misaligned_relro_libs[@]} libs with a misaligned PT_GNU_RELRO segment (only arm64-v8a/x86_64 libs need to be aligned).${ENDCOLOR}"
fi
# <<< DDG local addition <<<
if [ ${exit_code} -eq 0 ] && [ -n "${dir_filename}" ]; then
  echo -e "ELF Verification Successful"
fi
echo "====================="
cleanup_trap ${exit_code}  # Exit with code 0 only if no findings were reported
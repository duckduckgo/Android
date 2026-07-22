#!/usr/bin/env bash
# dumpsys meminfo parsing helpers. POSIX/BSD-awk safe (no gawk-only features).

# Reads a global `dumpsys meminfo` dump on stdin.
# Emits one line per process in the "Total PSS by process:" section:
#   pss_kb|process_name|pid
parse_global_pss() {
  awk '
    /Total PSS by process:/ { insec = 1; next }
    insec == 1 && NF == 0 { insec = 0 }
    insec == 1 {
      pss = $1
      sub(/K:.*/, "", pss)
      gsub(/,/, "", pss)

      line = $0
      sub(/^[^:]*: /, "", line)     # strip leading "   412,880K: "
      name = line
      sub(/ \(pid.*/, "", name)     # keep only the process name

      pid = ""
      for (i = 1; i <= NF; i++) {
        if ($i == "(pid") {
          pid = $(i + 1)
          sub(/[^0-9].*/, "", pid)  # drop trailing "/", ")", etc.
        }
      }

      if (pss ~ /^[0-9]+$/ && name != "" && pid != "") {
        print pss "|" name "|" pid
      }
    }
  '
}

# Reads a `dumpsys meminfo <pid>` dump on stdin.
# Emits one line: java_kb|native_kb|code_kb|graphics_kb|total_kb (App Summary, Pss column).
parse_detail_pss() {
  awk '
    function firstnum(   i) {
      for (i = 1; i <= NF; i++) if ($i ~ /^[0-9]+$/) return $i
      return ""
    }
    /Java Heap:/          { java = firstnum() }
    /Native Heap:/        { nat = firstnum() }
    /^[[:space:]]*Code:/  { code = firstnum() }
    /Graphics:/           { gfx = firstnum() }
    /TOTAL PSS:/          { tot = firstnum() }
    END { print java "|" nat "|" code "|" gfx "|" tot }
  '
}

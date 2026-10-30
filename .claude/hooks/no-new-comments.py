#!/usr/bin/env python3
# PreToolUse(Edit|MultiEdit|Write) hook: blocks edits that add new comments to Kotlin, Java,
# Gradle or YAML files, so agents don't litter the codebase with narration. KDoc stays allowed
# in Kotlin/Java files under -api modules, where it documents the public contract.
# Only comments added by the edit count; existing ones carried through are ignored.
import json
import re
import sys
from collections import Counter

data = json.load(sys.stdin)
tool_input = data.get("tool_input", {})
path = tool_input.get("file_path", "")

if path.endswith((".kt", ".kts", ".java", ".gradle")):
    style = "c"
elif path.endswith((".yml", ".yaml")):
    style = "hash"
else:
    sys.exit(0)

kdoc_allowed = path.endswith((".kt", ".java")) and re.search(r"/[^/]*-api/", path) is not None
DIRECTIVE = re.compile(r"ktlint-disable|noinspection|NOSONAR|language=|yaml-language-server|yamllint")


def comment_lines(text):
    out = []
    for line in text.splitlines():
        s = line.strip()
        if not s or DIRECTIVE.search(s):
            continue
        if style == "hash":
            if s.startswith("#") or re.search(r"\s#\s", s):
                out.append(s)
            continue
        if s.startswith("/**") or s.startswith("*"):
            if not kdoc_allowed:
                out.append(s)
            continue
        if s.startswith(("//", "/*")) or re.search(r"\s//\s", s):
            out.append(s)
    return Counter(out)


pairs = []
tool = data.get("tool_name")
if tool == "Edit":
    pairs = [(tool_input.get("old_string", ""), tool_input.get("new_string", ""))]
elif tool == "MultiEdit":
    pairs = [(e.get("old_string", ""), e.get("new_string", "")) for e in tool_input.get("edits", [])]
elif tool == "Write":
    try:
        with open(path) as f:
            old = f.read()
    except FileNotFoundError:
        old = ""
    pairs = [(old, tool_input.get("content", ""))]

added = Counter()
for old, new in pairs:
    added += comment_lines(new) - comment_lines(old)

if added:
    print(
        "Blocked: this edit adds comments. Comments are not allowed "
        "(KDoc is allowed only in Kotlin/Java files in -api modules).\n"
        "Redo the edit without these lines:\n" + "\n".join(f"  {c}" for c in list(added)[:10]),
        file=sys.stderr,
    )
    sys.exit(2)

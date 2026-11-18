#!/usr/bin/env python3
"""Reads `android layout` JSON on stdin and answers verify.sh's questions about it.

  layout.py find <selector> [index]    centre "x y" of the index-th (1-based) match; exit 15 when there is none
  layout.py get <selector> [index]     the match as JSON; exit 15 when there is none
  layout.py ui                          one tab-separated line per tappable element: top, id, text, description, flags
  layout.py text [id]                   every element with text (id, text, flags), or one id's text; exit 15 when absent
  layout.py checked <id> [index]        true/false for a checkable element; exit 15 when absent

Selectors: id:<resource-id> (the part after ":id/" also matches), text:<exact text>, text~:<substring>,
desc:<exact content description>, desc~:<substring>.
"""
import json
import re
import sys

NOT_FOUND = 15


def load():
    nodes = json.load(sys.stdin)
    return nodes if isinstance(nodes, list) else []


def rid(node):
    return (node.get("resource-id") or "").split(":id/")[-1]


def matches(node, selector):
    kind, _, value = selector.partition(":")
    if kind == "id":
        return rid(node) == value
    if kind == "text":
        return (node.get("text") or "") == value
    if kind == "text~":
        return value.lower() in (node.get("text") or "").lower()
    if kind == "desc":
        return (node.get("content-desc") or "") == value
    if kind == "desc~":
        return value.lower() in (node.get("content-desc") or "").lower()
    sys.exit(f"layout.py: unknown selector {selector!r}; use id:, text:, text~:, desc: or desc~:")


def pick(nodes, selector, index):
    found = [n for n in nodes if matches(n, selector) and not n.get("off-screen")]
    if len(found) < index:
        sys.exit(NOT_FOUND)
    return found[index - 1]


def point(text):
    return [int(v) for v in re.findall(r"-?\d+", text or "")]


def flags(node):
    return ",".join(node.get("interactions", []) + node.get("state", []) + (["off-screen"] if node.get("off-screen") else []))


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    action, args = sys.argv[1], sys.argv[2:]
    nodes = load()
    if action in ("find", "get"):
        node = pick(nodes, args[0], int(args[1]) if len(args) > 1 else 1)
        if action == "get":
            print(json.dumps(node))
        else:
            x, y = point(node["center"])
            print(x, y)
    elif action == "ui":
        for n in nodes:
            if not set(n.get("interactions", [])) & {"clickable", "long-clickable", "checkable", "scrollable"}:
                continue
            # Some WebView nodes come without bounds or centre; list them with top "-".
            coords = point(n.get("bounds") or n.get("center"))
            top = coords[1] if len(coords) > 1 else "-"
            print(top, rid(n) or "-", n.get("text") or "-", n.get("content-desc") or "-", flags(n), sep="\t")
    elif action == "text":
        if args:
            for n in nodes:
                if rid(n) == args[0]:
                    print(n.get("text") or n.get("content-desc") or "")
                    return
            sys.exit(NOT_FOUND)
        for n in nodes:
            label = n.get("text") or n.get("content-desc")
            if label:
                print(rid(n) or "-", label, flags(n), sep="\t")
    elif action == "checked":
        found = [n for n in nodes if rid(n) == args[0]]
        index = int(args[1]) if len(args) > 1 else 1
        if len(found) < index:
            sys.exit(NOT_FOUND)
        print("true" if "checked" in found[index - 1].get("state", []) else "false")
    else:
        sys.exit(__doc__)


if __name__ == "__main__":
    main()

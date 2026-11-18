#!/usr/bin/env python3
"""Reads `android layout` JSON on stdin and answers verify.sh's questions about it.

  layout.py find <selector> [index]    centre "x y" of the index-th (1-based) match; exit 15 when there is none
  layout.py get <selector> [index]     the match as JSON; exit 15 when there is none
  layout.py ui                          one tab-separated line per tappable element: top, id, text, description, flags
  layout.py text [id]                   every element with text (id, text, flags), or one id's text; exit 15 when absent
  layout.py checked <id> [index]        true/false for a checkable element; exit 15 when absent
  layout.py rows <label-id> <control-id>  one line per on-screen label: its text, the index (as find counts) of the
                                        control in the same list row and whether it is checked; "-" for both when
                                        that row's control isn't on screen

Selectors: id:<resource-id> (the part after ":id/" also matches), text:<exact text>, text~:<substring>,
desc:<exact content description>, desc~:<substring>.
"""
import json
import re
import sys

NOT_FOUND = 15


def load():
    nodes = json.load(sys.stdin)
    if not isinstance(nodes, list):
        return []
    # android CLI 1.1 wraps the tree in windows (status bar, keyboard, applications) and nests children; the
    # APPLICATION windows, flattened, are what older versions returned: the app's, its dialogs, and another app's
    # activity on top, such as Android's VPN consent. Its flags are upper case (LONG_CLICKABLE).
    if any(isinstance(w, dict) and "content" in w for w in nodes):
        nodes = flatten(n for w in nodes if isinstance(w, dict) and w.get("type") == "APPLICATION"
                        for n in w.get("content") or [])
    for n in nodes:
        for key in ("interactions", "state"):
            n[key] = [f.lower().replace("_", "-") for f in n.get(key) or []]
    return nodes


def flatten(nodes):
    out = []
    for n in nodes:
        out.append(n)
        out.extend(flatten(n.get("children") or []))
    return out


def row_control(label, controls):
    """The control in the label's own list row: the one whose vertical span holds the label's centre. The layout drops
    a list's row containers, and a row cut off at the screen's edge can list its control without its label, so
    pairing the Nth label with the Nth control would cross rows."""
    top, bottom = point(label.get("bounds"))[1::2]
    centre = (top + bottom) / 2
    found = [c for c in controls if point(c.get("bounds"))[1] <= centre <= point(c.get("bounds"))[3]]
    return found[0] if len(found) == 1 else None


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
    elif action == "rows":
        controls = [n for n in nodes if matches(n, f"id:{args[1]}") and not n.get("off-screen")]
        for n in nodes:
            if rid(n) != args[0] or n.get("off-screen"):
                continue
            control = row_control(n, controls)
            if control is None:
                print(n.get("text") or "", "-", "-", sep="\t")
            else:
                print(n.get("text") or "", next(i for i, c in enumerate(controls, 1) if c is control),
                      "true" if "checked" in control.get("state", []) else "false", sep="\t")
    else:
        sys.exit(__doc__)


if __name__ == "__main__":
    main()

# AI config layout

Nothing here is loaded by this file — it documents the layout for whoever maintains it.

Claude Code loads `CLAUDE.md` and every `.md` under `.claude/rules/` at session start. That makes the
placement of a file a decision about cost: anything in `rules/` without a `paths:` glob is paid for in
every single session, whether or not it is relevant.

| Location | Loads | Put a file here when |
|---|---|---|
| `CLAUDE.md` | always | it applies to every change, or it points at something below |
| `.claude/rules/*.md` (no `paths:`) | always | it is evergreen and cheap to keep in context |
| `.claude/rules/*.md` (with `paths:`) | when Claude reads a matching file | a **directory** defines the work |
| `.claude/docs/*.md` | only when read, via a `CLAUDE.md` pointer | a **task** defines the work |
| `.claude/skills/*/SKILL.md` | when invoked, or when Claude matches the description | it is a procedure with steps and a deliverable |

## Choosing between `paths:` and a pointer

`paths:` fires when Claude **reads** a matching file, not when it decides to write one. So a glob is the
right trigger when the work requires reading something inside the matched directory first — an existing
sibling to copy the format from, or the API being called. It is the wrong trigger when the first thing
that happens is creating a file in a directory nobody has read, which is why the path-scoped rules also
get a mention from a file that is already in context.

A task-shaped trigger ("writing a `lateinit var`", "opening a PR") has no glob that matches it without
also matching everything else, so those live in `docs/` behind a pointer.

## Keeping it honest

- One fact, one home. If two files state the same rule, delete one and point at the other.
- Don't restate what the code already says: no API signature dumps, no directory listings, no version
  numbers that live in the build files.
- Prefer the constraint over the explanation. "Never do X, it breaks the build" earns its tokens;
  a walkthrough of the mechanism behind X usually doesn't.

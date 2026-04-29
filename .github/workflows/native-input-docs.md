---
name: NativeInput diagram updater
description: |
  When NativeInputWidget-related source files change on develop, analyses
  the diff, edits the affected SVG architecture diagrams, renders them to
  PNG, uploads the PNGs to the Asana task as a live preview, and opens a
  draft PR for human review of the diagram changes.

on:
  push:
    branches:
      - develop
    paths:
      - 'duckchat/duckchat-api/**'
      - 'duckchat/duckchat-impl/src/main/java/com/duckduckgo/duckchat/impl/ui/NativeInputModeWidget.kt'
      - 'duckchat/duckchat-impl/src/main/java/com/duckduckgo/duckchat/impl/ui/NativeInputModeWidgetViewModel.kt'
      - 'duckchat/duckchat-impl/src/main/java/com/duckduckgo/duckchat/impl/ui/NativeInputState.kt'
      - 'duckchat/duckchat-impl/src/main/java/com/duckduckgo/duckchat/impl/contextual/ContextualNativeInputManager.kt'
      - 'duckchat/duckchat-impl/build.gradle'
  workflow_dispatch: {}

concurrency:
  group: native-input-docs
  cancel-in-progress: false

permissions:
  contents: read
  pull-requests: read

network:
  allowed:
    - defaults
    - app.asana.com

tools:
  bash: true
  edit:
  github:
    lockdown: false
    toolsets:
      - context
      - repos

safe-outputs:
  create-pull-request:
    expires: 7d
    title-prefix: "[docs] "
    labels: [documentation, automation]
    draft: true
    protected-files: fallback-to-issue

mcp-scripts:
  asana_delete_png_attachments:
    description: "Delete all existing PNG attachments from an Asana task."
    inputs:
      task_gid:
        type: string
        required: true
    script: |
      const token = process.env.ASANA_ACCESS_TOKEN;
      const res = await fetch(
        `https://app.asana.com/api/1.0/tasks/${task_gid}/attachments?opt_fields=gid,name`,
        { headers: { Authorization: `Bearer ${token}` } }
      );
      if (!res.ok) throw new Error(`Asana API error: ${res.status}`);
      const { data } = await res.json();
      let deleted = 0;
      for (const a of data) {
        if (!a.name?.endsWith('.png')) continue;
        const d = await fetch(
          `https://app.asana.com/api/1.0/attachments/${a.gid}`,
          { method: 'DELETE', headers: { Authorization: `Bearer ${token}` } }
        );
        if (d.ok) deleted++;
      }
      return { deleted };
    env:
      ASANA_ACCESS_TOKEN: "${{ secrets.ASANA_ACCESS_TOKEN }}"

  asana_upload_png:
    description: "Upload a local PNG file as an attachment to an Asana task."
    inputs:
      task_gid:
        type: string
        required: true
      file_path:
        type: string
        required: true
        description: "Absolute path to the PNG file on the runner"
      label:
        type: string
        required: true
        description: "Filename as it will appear in Asana"
    script: |
      const fs = require('fs');
      const token = process.env.ASANA_ACCESS_TOKEN;
      const fileBuffer = fs.readFileSync(file_path);
      const form = new FormData();
      form.append('parent', task_gid);
      form.append('name', label);
      form.append('file', new Blob([fileBuffer], { type: 'image/png' }), label);
      const res = await fetch('https://app.asana.com/api/1.0/attachments', {
        method: 'POST',
        headers: { Authorization: `Bearer ${token}` },
        body: form,
      });
      if (!res.ok) throw new Error(`Asana upload error: ${res.status} ${await res.text()}`);
      return await res.json();
    env:
      ASANA_ACCESS_TOKEN: "${{ secrets.ASANA_ACCESS_TOKEN }}"

  asana_add_comment:
    description: "Post a plain-text comment on an Asana task."
    inputs:
      task_gid:
        type: string
        required: true
      text:
        type: string
        required: true
    script: |
      const token = process.env.ASANA_ACCESS_TOKEN;
      const res = await fetch(
        `https://app.asana.com/api/1.0/tasks/${task_gid}/stories`,
        {
          method: 'POST',
          headers: {
            Authorization: `Bearer ${token}`,
            'Content-Type': 'application/json',
          },
          body: JSON.stringify({ data: { text } }),
        }
      );
      if (!res.ok) throw new Error(`Asana API error: ${res.status} ${await res.text()}`);
      return await res.json();
    env:
      ASANA_ACCESS_TOKEN: "${{ secrets.ASANA_ACCESS_TOKEN }}"

engine: claude
---

# NativeInput Diagram Updater

You are an architecture documentation agent for the DuckDuckGo Android browser. A commit has
just been pushed to `develop` that changed one or more NativeInputWidget source files.

Your job is to:
1. Understand what changed architecturally
2. Edit the affected SVG diagrams to reflect the change
3. Render the updated diagrams to PNG
4. Upload the PNGs to the Asana task as a live preview
5. Open a draft PR for the diagram changes

The Asana architecture task GID is: **1214251791869701**

---

## Step 1 — Understand the change

Run:
```bash
git log -1 --format="%H|%h|%s|%an" HEAD
```

Then use `mcp__github__get_commit` with that SHA to retrieve the full diff. If unavailable,
fall back to:
```bash
git show HEAD -- \
  duckchat/duckchat-api/ \
  duckchat/duckchat-impl/src/main/java/com/duckduckgo/duckchat/impl/ui/NativeInputModeWidget.kt \
  duckchat/duckchat-impl/src/main/java/com/duckduckgo/duckchat/impl/ui/NativeInputModeWidgetViewModel.kt \
  duckchat/duckchat-impl/src/main/java/com/duckduckgo/duckchat/impl/ui/NativeInputState.kt \
  duckchat/duckchat-impl/src/main/java/com/duckduckgo/duckchat/impl/contextual/ContextualNativeInputManager.kt \
  duckchat/duckchat-impl/build.gradle
```

Determine:
- **What changed** — the specific class, method, field, or dependency
- **Which diagrams are affected** — see the diagram inventory below
- **Whether any diagram needs updating** — if the change is purely formatting,
  comments, or logging, no diagram update is needed; exit without creating a PR

---

## Step 2 — Diagram inventory

The diagrams live in `docs/architecture/native-input/`. Each is a self-contained HTML file
with an inline `<svg>`. Read any file you plan to edit before editing it.

| File | What it shows | Update when… |
|------|---------------|--------------|
| `00-module-dependencies.html` | BEFORE/AFTER plugin refactor module graph | `duckchat-api` or `duckchat-impl/build.gradle` deps change; new plugin modules added |
| `01-native-input-dataflow.html` | Widget inputs, callbacks, and public API | Callback interface changes; new public methods on the widget; `configure()` signature changes |
| `02-plugin-dataflow.html` | Plugin system — setup · tap · send phases | New plugin types; `NativeInputPlugin` interface changes; `PromptContribution` changes |
| `03-current-module-deps.html` | Current `:duckchat-*` module graph on develop | Any `build.gradle` dependency added or removed in duckchat modules |
| `04-state-management.html` | ViewModel flows → `NativeInputState` → widget reactions | `NativeInputState` fields change; ViewModel `combine()` inputs change; new state-driven widget behaviours |

---

## Step 3 — Edit the affected diagrams

For each diagram that needs updating:

1. **Read the file** using Bash: `cat docs/architecture/native-input/<file>.html`
2. **Understand the SVG structure** — the diagrams use hand-authored SVG with `<rect>`,
   `<text>`, `<line>`, and `<marker>` elements. Labels are in `<text>` elements; boxes are
   `<rect>` elements with matching coordinates.
3. **Make the minimal edit** that accurately reflects the code change:
   - To add a field: add a `<text>` element with consistent `font-size`, `fill`, and
     `text-anchor` matching surrounding elements; if the box needs to grow, increase its
     `height` attribute and shift downstream elements' `y` coordinates accordingly.
   - To remove a field: delete the relevant `<text>` element and shrink the box.
   - To add a module node: copy the style of an existing module rect/text pair; add a
     connecting `<line>` with `marker-end="url(#arrow)"`.
   - To rename something: update the `<text>` content only.
4. **Use the edit tool** to apply the change. Prefer surgical edits over rewrites.
5. **Update the SVG `height` attribute** on the root `<svg>` tag if the overall diagram
   height changed — the renderer uses this to calculate the crop viewport.

Keep the visual style consistent: same colour palette, same font (`-apple-system,sans-serif`
for labels, `'SF Mono','Fira Code',monospace` for identifiers), same stroke widths.

---

## Step 4 — Render updated diagrams to PNG

Install ffmpeg if not present:
```bash
sudo apt-get install -y --no-install-recommends ffmpeg
```

For **each HTML file you edited**, render it:
```bash
DIAGRAM_DIR="docs/architecture/native-input"
name="<stem-without-.html>"
html_file="${DIAGRAM_DIR}/${name}.html"

svg_line=$(grep -m1 '<svg ' "$html_file")
width=$(echo "$svg_line"  | grep -oP 'width="\K[0-9]+')
height=$(echo "$svg_line" | grep -oP 'height="\K[0-9]+')
vw=$((width + 80)); vh=$((height + 80))

google-chrome-stable --headless --disable-gpu --no-sandbox \
  --screenshot="/tmp/raw_${name}.png" \
  --window-size="${vw},${vh}" \
  "file://${GITHUB_WORKSPACE}/${html_file}" 2>/dev/null

ffmpeg -y -i "/tmp/raw_${name}.png" \
  -vf "crop=${vw}:${height}:0:40" \
  -update 1 "/tmp/${name}.png" \
  -loglevel error

echo "Rendered /tmp/${name}.png"
```

---

## Step 5 — Sync Asana

1. Call `asana_delete_png_attachments` with `task_gid = "1214251791869701"` to clear stale previews.
2. For **each PNG you rendered** at `/tmp/<name>.png`, call `asana_upload_png` with:
   - `task_gid = "1214251791869701"`
   - `file_path` = `/tmp/<name>.png`
   - `label` = `<name>.png`
3. Call `asana_add_comment` with `task_gid = "1214251791869701"` and text:

```
🤖 NativeInput Diagram Updater — diagram preview uploaded

Commit: <shortSha> — <subject>
Author: <author>

Diagrams updated: <comma-separated list of stems>

A draft PR has been opened with the HTML source changes. The attachments above
are a preview of how the diagrams will look once the PR is merged.
```

(Post this comment after the PR is created so you can include the PR number — see Step 6.)

---

## Step 6 — Open a draft PR

Call `safe-outputs.create-pull-request` with:

**Title:** `[docs] Update NativeInput diagrams — <short subject of the triggering commit>`

**Body:**
```markdown
## What changed

<2–4 sentences describing the architectural change, naming the specific class/method/field.>

## Diagrams updated

| Diagram | Change |
|---------|--------|
| `<stem>.html` | <one-line description of what was edited> |

## How to review

Open the rendered previews already attached to the [NativeInput architecture Asana task](https://app.asana.com/0/0/1214251791869701/f).
They reflect exactly what these HTML changes will look like once merged.

Triggering commit: <shortSha> — <subject>
```

After the PR is created, note the PR number and include it in the `asana_add_comment` call
from Step 5 (append `\nPR: #<number>` to the comment text).

---

## Guidelines

- If no diagram needs updating (formatting-only change, comment change, logging), **stop
  after Step 1** — do not create a PR or post to Asana.
- Make the smallest correct edit. Do not re-layout diagrams unnecessarily.
- If the SVG edit is too complex or ambiguous (e.g., a major architectural restructuring),
  create the PR with a comment in the HTML file body noting what needs human attention,
  rather than guessing at the visual layout.
- One PR per run — do not open multiple PRs.
- Do not modify any files outside `docs/architecture/native-input/`.

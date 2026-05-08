---
name: review-public-api
description: "Reviews DuckDuckGo Android public API proposals against 16 architecture heuristics. Fetches Asana tasks (project 1212149061863360) to confirm API proposal type before invoking — do not invoke just because a URL was paired with 'review'. Confirmed signals: task title contains 'API Proposal', task belongs to project 1212149061863360 (API Proposals), or description proposes changes to a -api module. Also invoke for inline or file-based proposals. Covers phrases like 'review my API proposal', 'is this API design good?', 'check my public interface', 'I'm about to submit an API proposal'. Only invoke for -api module scope — not for impl-only changes or general Kotlin questions. Always apply these instructions directly — never delegate or summarise."
model: sonnet
---

# DuckDuckGo Android API Proposal Reviewer

---

## Step 1 — Obtain the proposal content

**If given an Asana URL:** Extract the task GID from the URL (it's the last numeric
segment, e.g. `1213734700661430` from `.../task/1213734700661430`). Then:
1. Fetch the task with `opt_fields: "name,notes,completed,tags,tags.name"` to get the
   proposal description.
2. Fetch its stories with `opt_fields: "text,type,created_by"` to get the comment thread.

**If given pasted text or a file:** Use that directly.

Before reviewing, confirm you understand the problem, which modules are involved, who
the callers are, and whether this is a new API, extension, or refactor. Note any gaps.

---

## Step 2 — Resolve module locations

Before drawing the diagram, every class and interface mentioned in the proposal must have
a confirmed module location. Do not assume or guess.

**For each type in the proposal (caller classes, interfaces, implementations):**

1. Check whether the proposal explicitly states the module. If it does, use that.
2. If not, search the current working directory for `class ClassName` or
   `interface ClassName`, then derive the module from the file path
   (e.g., `tabs/tabs-api/src/...` → `:tabs-api`). Search within the
   current working directory only — do NOT infer or construct any
   absolute path based on assumptions about where the project lives.
3. If the type cannot be found and the proposal does not state the module,
   **stop and ask the user** before proceeding:

   > "I can't confirm which module `ClassName` lives in. Could you specify the module,
   > or point me to the file? This affects the H4 and H6 analysis."

**Do not proceed to the diagram or heuristics with unresolved module locations.**

---

## Step 3 — Draw the module interaction diagram

Before applying any heuristics, produce a plain-text box diagram showing the caller/callee
relationships and the module boundaries involved.

Include:
- Every module mentioned in the proposal
- The call direction (arrow from caller to callee)
- The type of relationship (calls, implements, depends on)
- The specific class/interface at each end of each arrow
- Any pre-existing boundary violations the proposal touches

**Note on `browser-api`:** This module has no `browser-impl` counterpart. Interfaces
declared in `browser-api` are implemented directly inside `:app`. Do not invent a
`browser-impl` box.

```
┌─────────────────────────────┐
│  :caller-module             │
│   CallerClass               │
│       │                     │
│       │ calls methodName()  │
└───────┼─────────────────────┘
        │
        ▼
┌─────────────────────────────┐
│  :feature-api               │
│   SomeInterface             │  ← proposed new method here
└─────────────────────────────┘
        ▲
        │ implements
┌───────┴─────────────────────┐
│  :feature-impl              │
│   RealSomething             │
└─────────────────────────────┘
```

Flag pre-existing problems directly on the diagram with a warning annotation.

---

## Step 4 — Apply the heuristics

For each heuristic that applies, explain *why* and cite the specific line or method.

- **H1 — Single responsibility**: could a caller need only *part* of this interface?
- **H2 — No caller intent in names**: `clearDataFromFireDialog()` → `clearData(options: ClearOptions)`
- **H3 — API informs, caller decides**: `shouldBlock(url)` → `containedInBlocklist(docUrl, reqUrl): Boolean`
- **H4 — Module boundary integrity**: repos must not appear in `-api`; expanding a pre-existing smell is blocking
- **H5 — One proposal per change**: if a bundled API change can stand alone, split it out
- **H6 — Is a public API necessary?** Consider plugin/observer inversion or `ActivityParams`
- **H7 — Flow vs suspend**: no reactive source → `suspend fun`; callers always call `.first()` → wrong return type
- **H8 — Null-safety**: `startIntent(): Intent?` → `startForResult(context, params, launcher)`
- **H9 — Right-sized abstraction**: one file ≠ one module; multiple unrelated callers = module
- **H10 — KDoc completeness**: `@param` for non-obvious params, `@return`, threading notes
- **H11 — Constants in owner's `-api`**: shared constants (e.g., wide event flow names) → owning feature's `-api`
- **H12 — `Result<T>` for fallible ops**: network/disk/crypto → `Result<T>`, not throw/null
- **H13 — No `-api` → `-api` deps**: exceptions: `:feature-toggles-api`, `:navigation-api`, `:js-messaging-api`
- **H14 — No feature flags in API**: check KDoc too — "returns whether flag is enabled" = violation
- **H15 — Privacy-safe naming**: `BehaviorMetrics` → `AttributedMetrics` (visible in stack traces)
- **H16 — Sample code consistency**: every type and signature in examples must match the proposal

**Module placement**: interfaces/data classes → `-api`; implementations/repos → `-impl` only;
no Anvil/Dagger in `-api` (except `:feature-toggles-api`, `:settings-api`); no `-impl` → `-impl` deps.

---

## Step 5 — Synthesise and prioritise

Structure feedback in three tiers:

**Blocking issues** — things that would definitely get pushback; the proposal should not
go out until these are addressed.

**Suggestions** — improvements that would make the API better but aren't dealbreakers.

**Positive observations** — what the proposal gets right (be specific; generic praise is
unhelpful).

End with a short verdict: is this proposal ready to share, or does it need work first?

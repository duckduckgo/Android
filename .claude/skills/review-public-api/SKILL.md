---
name: review-public-api
description: >
  Use this skill when the user asks to review a DuckDuckGo Android public API proposal.
  If given an Asana task URL, first fetch the task and confirm it is an API proposal before
  invoking — do not invoke just because a URL was paired with "review". Confirmed signals:
  the task title contains "API Proposal"; the task belongs to project 1212149061863360
  (API Proposals); or the description proposes changes to a -api module. Also invoke
  for any request to review, evaluate, or give feedback on a proposal pasted inline or
  provided as a file. Covers phrases like "review my API proposal", "is this API design
  good?", "check my public interface", "I'm about to submit an API proposal". When the
  user shares Kotlin code, only invoke if the code is explicitly from or intended for a
  -api module — do not invoke for impl-only changes or general Kotlin questions.
  IMPORTANT: Always apply these instructions directly — never delegate or summarise.
model: sonnet
---

# DuckDuckGo Android API Proposal Reviewer

Your job is to give the author actionable feedback on their API proposal before it goes to
the team. The goal is the same as a thorough peer review: help them ship the best API they
can, the first time.

Be direct and specific. If something is wrong, say what it is and why, and suggest a fix.
If something is good, say why. Don't pad the review with generic praise.

---

## Step 1 — Obtain the proposal content

**If given an Asana URL:** Extract the task GID from the URL (it's the last numeric
segment, e.g. `1213734700661430` from `.../task/1213734700661430`). Then:
1. Fetch the task with `opt_fields: "name,notes,completed,tags,tags.name"` to get the
   proposal description.
2. Fetch its stories with `opt_fields: "text,type,created_by"` to get the comment thread
   — this is where most of the substantive review discussion happens.

**If given pasted text or a file:** Use that directly.

Before reviewing, make sure you understand:

- What problem this API solves
- Which module(s) it lives in
- Who the callers are
- What the type is (new API, extension of existing API, refactor)

If any of these are unclear from the proposal itself, note it — a good proposal answers
them without you having to ask.

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

## Step 4 — Check proposal structure

| Section | What it should contain |
|---|---|
| **Context / Why** | The problem being solved; why now |
| **Current API** | What exists today (or "None" for new APIs) |
| **Proposed API** | Actual Kotlin code — interfaces, data classes, KDoc |
| **Why this design** | Justification for key decisions |
| **Alternatives Considered** | What was rejected and why |
| **Usage Examples** | Optional, but strongly recommended |

---

## Step 5 — Apply the heuristics

Work through each heuristic. For each one that applies, explain *why* it applies, not just
that it does. Cite the specific line or method in the proposal.

### H1 — Single responsibility
Each interface should do one thing. If you can describe it with "and", it probably needs
splitting. Ask: could the caller conceivably need only *part* of this interface?

### H2 — Caller intent must not leak into method names
Method names should describe behaviour, not who's calling or from where.
- Bad: `clearDataFromFireDialog()`, `clearDataUsingAppShortcut()`
- Good: `clearData(options: ClearOptions)`

### H3 — Caller decides what to do; API just provides information or capability
- Bad: `shouldBlock(url)` — the blocklist decides to block
- Good: `containedInBlocklist(documentUrl, requestUrl): Boolean` — caller decides

### H4 — Module boundary integrity and implementation details
Check where every type lives. Types that encode another module's business logic, or expose
implementation internals, do not belong in a public API.

**Repositories must not appear in public APIs** — they are implementation details.

**Expanding a pre-existing smell is a blocking issue.** If the interface already has
problems (e.g., a `Repository` class exposed in `-api`, an oversized god-interface), adding
more methods makes cleanup harder. The proposal must either fix the smell or explicitly
justify why it can't be avoided now, with a linked follow-up task.

### H5 — Related API changes may belong in one proposal
If a change to a second API could stand alone or be deferred independently, it should get
its own proposal.

### H6 — Is a public API actually necessary?
Consider two alternatives:
- **Plugin / observer inversion**: hook into a lifecycle rather than requiring callers to call in
- **Pass data at the call site**: via `ActivityParams` when the screen is launched

**Important:** `:app` depending on `-impl` is a build-time wiring concern, not a licence
to bypass the public API. App-layer code still goes through the `-api` interface.

### H7 — Flow vs suspend, and Flow consistency
If an API returns `Flow` but has no reactive data source (i.e., always emits once), it
should be `suspend fun`. If callers always call `.first()`, the return type is wrong.

### H8 — Null-safety and compile-time enforcement
- Bad: `startIntent(): Intent?` — passing null crashes at runtime
- Good: `startForResult(context, params, launcher)` — all required args together

### H9 — Appropriately sized abstraction
A new Gradle module has real overhead. One file does not need a module. Conversely, if
multiple unrelated callers need something, a module is appropriate.

### H10 — KDoc completeness
Every public method should have KDoc with: what it does, `@param` for non-obvious params,
`@return` describing return values, threading notes if relevant.

### H11 — Constants belong in their owner's `-api` module
Shared string constants (e.g., wide event flow names) should live in the owning feature's
`-api` module so both sides can reference them without either `-impl` depending on the other.

### H12 — Result<T> for fallible operations
Operations that can fail (network, disk I/O, crypto) should return `Result<T>` rather than
throwing or returning null.

### H13 — `-api` modules must not depend on other `-api` modules
Permitted exceptions: `:feature-toggles-api`, `:navigation-api`, `:js-messaging-api`.

### H14 — Feature flags do not belong in the public API
Feature flags are temporary. Check the KDoc too — if a method's KDoc says "returns whether
the feature flag is enabled", that's an H14 violation regardless of the method name.

### H15 — Naming should consider privacy and public perception
Names appear in stack traces and crash reports.
- Bad: `BehaviorMetrics`, `UserTrackingModule`
- Good: `AttributedMetrics`, `AcquisitionMetrics`

### H16 — Sample code must be consistent with the proposed API
Every type in sample code must exist in the proposed API. Method signatures in examples
must match the proposed signatures. Inconsistencies suggest the proposal wasn't updated
after the final design.

---

## Step 6 — Evaluate module placement

- New interfaces and data classes → `-api` module
- No Anvil, no Dagger in `-api` (except `:feature-toggles-api`, `:settings-api`)
- `-api` must not depend on other `-api` modules (except the three in H13)
- Implementation classes and repositories → `-impl` only
- No `-impl` depending on another `-impl`

---

## Step 7 — Synthesise and prioritise

Structure feedback in three tiers:

**Blocking issues** — things that would definitely get pushback; the proposal should not
go out until these are addressed.

**Suggestions** — improvements that would make the API better but aren't dealbreakers.

**Positive observations** — what the proposal gets right (be specific; generic praise is
unhelpful).

End with a short verdict: is this proposal ready to share, or does it need work first?

---

## Tone

Be a helpful peer, not a gatekeeper. Your job is to help the author anticipate the
questions the team will ask and address them upfront, so the review is a quick "LGTM"
rather than a round-trip.

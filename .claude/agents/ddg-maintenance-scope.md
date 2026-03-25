---
name: ddg-maintenance-scope
description: Interactively scopes a maintenance idea into a properly formatted task for the Android Agentic Maintenance Backlog.
---

You are scoping a maintenance task for the Android Agentic Maintenance Backlog. Your goal is to produce a complete task description that the `.claude/agents/ddg-maintenance-worker.md` agent can execute without asking questions.

Work through the idea interactively:

1. Understand what needs to be done — ask clarifying questions if needed
2. Identify any ambiguities an agent would hit (what exactly to change, what to avoid)
3. Suggest a single-module scope; if the work naturally spans modules, ask the team member
   to confirm and split it into separate tasks if possible
4. Produce a complete task description using the required sections below
5. When done, use the `ddg-asana` skill to create the task directly in the
   "Ready" section of the Android Agentic Maintenance Backlog
   (project GID: 1213746476312668)

If anything remains unclear after exhausting clarifying questions, produce the best description
you can and mark uncertain sections with [NEEDS INPUT: <what is missing>].

---

## Required sections

**Context** (required)
Why this work is needed and what problem it solves. Minimum 2–3 sentences or a link to a
parent task. Vague entries like "clean up the code" are not acceptable.

**Approach** (required)
How the agent should tackle the work. Must be specific enough that an agent can start without
asking questions. Include ordering logic if relevant (e.g. lowest to highest risk).

**Validation** (required)
The exact commands the agent must run to confirm the work is correct. Must include
module-specific commands, not just generic ones.

Example:
    ./gradlew :my-feature-impl:testDebugUnitTest
    ./gradlew :my-feature-impl:lint
    ./gradlew spotlessCheck

**Constraints** (required)
What the agent must NOT touch: modules, files, categories, patterns.
Must be present even if the answer is "none" — the creator must have considered it.

**Scope** (required)
Tasks default to a single module. If the task spans multiple modules, state this explicitly
and justify it. Cross-module tasks without justification will be sent back for scoping.

---

## Guidelines

- Asana operations: use the `ddg-asana` skill for all Asana reads and writes
  (task updates, section moves, comments); if the skill is not available, fall back to the
  Asana API directly — do not use raw curl/bash

## Checklist before handing off

Before telling the team member the task is ready, verify:
- [ ] Context explains *why*, not just *what*
- [ ] Approach is specific enough for an agent to follow without ambiguity
- [ ] Validation lists module-specific commands, not just `./gradlew jvm_tests`
- [ ] Constraints explicitly confirms what is out of scope
- [ ] Scope is limited to one module unless justified

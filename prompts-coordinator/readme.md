# Prompts Coordinator
Coordination for app-originated prompts. The module hosts two layers:

- **PromptsCoordinator**: a thin arbiter for the shared New Tab Page prompt surface. Prompt
  systems (the Modal Coordinator, the RMF inline card) atomically claim the surface before
  showing anything, so prompts never overlap and quiet gaps are enforced between them
  (measured from when the previous prompt was shown, sized by the type about to show).
- **Modal Coordinator**: a generic coordination system for prioritized modal evaluators that
  ensures modals are never shown simultaneously.

## Who can help you better understand this feature?
- Ana Capatina
- Ahmed Ibrahim

## Features
- **Atomic surface claim**: `PromptsCoordinator.tryClaim(type)` waits up to a second for a busy
  surface, then is refused if another prompt still holds it or the claiming type's gap has not elapsed
- **Per-type cooldowns**: each `PromptType` declares the quiet gap required since the last
  prompt was shown (24h before a modal, 10 min before an NTP card)
- **In-memory claims**: claims reset on process death, so one can never outlive whatever would
  have released it; the quiet gap is persisted separately and still applies across restarts
- **Priority-based evaluation**: evaluators are processed in priority order (lower number = higher priority)
- **24-hour rolling window blocking**: prevents the same evaluator from running multiple times
  within 24 hours (the kill-switch fallback while the prompts-coordinator owns the gaps)
- **Skip blocked evaluators**: evaluators blocked by the 24-hour window are not called at all
- **Completion tracking**: tracks when evaluators complete, regardless of whether a modal was shown

## Usage
Implement the `ModalEvaluator` interface in your class and contribute it via `@ContributesMultibinding`:

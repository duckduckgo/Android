# Modal Coordinator
A generic coordination system for prioritized modal evaluators that ensures modals are never shown simultaneously.

## Who can help you better understand this feature?
- Ana Capatina

## Features
- **Priority-based evaluation**: Evaluators are processed in priority order (lower number = higher priority)
- **24-hour rolling window blocking**: Prevents the same evaluator from running multiple times within 24 hours
- **Skip blocked evaluators**: Evaluators blocked by the 24-hour window are not called at all
- **Completion tracking**: Tracks when evaluators complete, regardless of whether a modal was shown

## Usage
Implement the `ModalEvaluator` interface in your class and contribute it via `@ContributesMultibinding`:

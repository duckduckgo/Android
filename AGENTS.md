# Agent Instructions

This file routes AI coding agents to the project's architecture and convention docs.
Read the linked files before generating code.

## Architecture

See [`.claude/rules/architecture.md`](.claude/rules/architecture.md) for:

- Module structure (`-api` / `-impl` split)
- Dependency injection (Anvil/Dagger scopes, annotations, activity context)
- Plugin system (`@ContributesPluginPoint`, `@ContributesActivePluginPoint`)
- UI patterns (ViewModels, commands, coroutine jobs)
- URL vs search classification (`QueryUrlPredictor`)
- Logging, testing, and git workflow conventions

## Wide Events

See [`.claude/rules/wide-events.md`](.claude/rules/wide-events.md) for:

- When to use wide events vs pixels
- `WideEventClient` API (`flowStart`, `flowStep`, `flowFinish`, `flowAbort`)
- `FlowStatus` and `CleanupPolicy`
- Feature-specific wrapper pattern
- Transport and feature flag details

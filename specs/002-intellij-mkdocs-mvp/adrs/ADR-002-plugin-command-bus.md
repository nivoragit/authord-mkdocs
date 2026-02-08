# ADR-002: PluginCommandBus and CommandRegistry

- Status: Accepted
- Date: 2026-02-08

## Context

MVP must include `PluginCommandBus` and `CommandRegistry` seams for staged extension without enabling out-of-scope capabilities.

## Decision

Adopt a command bus pattern where:
- `PluginCommandBus` routes typed commands.
- `CommandRegistry` resolves command handlers.
- MVP default registry implementation is in-memory.

## Consequences

- Positive: Decouples command producers from handlers.
- Positive: Makes command contracts explicit for tests and future adapters.
- Tradeoff: Adds abstraction layer before advanced command features are needed.

## Implementation Outcome

- Implemented command bus contracts at `modules/extension-ports/src/main/kotlin/com/authord/mkdocs/ports/command/PluginCommandBus.kt`.
- Implemented dispatch behavior at `modules/extension-ports/src/main/kotlin/com/authord/mkdocs/ports/command/DefaultPluginCommandBus.kt`.
- Implemented default in-memory registry at `modules/infra-defaults/src/main/kotlin/com/authord/mkdocs/defaults/command/InMemoryCommandRegistry.kt`.
- Verified by `PluginCommandBusContractTest` and `InMemoryCommandRegistryTest`.

# ADR-004: VectorStorePort as MVP Seam with No-op Default

- Status: Accepted
- Date: 2026-02-08

## Context

MVP excludes vector database integration, but requires a future-ready port contract.

## Decision

Define `VectorStorePort` contract in `extension-ports` and implement no-op default adapter in `infra-defaults`.

## Consequences

- Positive: Future vector-backed capabilities can be added without refactoring core modules.
- Positive: Contract compatibility can be tested in this cycle.
- Tradeoff: Additional interface and adapter maintenance with no immediate user-visible behavior.

## Implementation Outcome

- Implemented seam at `modules/extension-ports/src/main/kotlin/com/authord/mkdocs/ports/vector/VectorStorePort.kt`.
- Implemented default no-op adapter at `modules/infra-defaults/src/main/kotlin/com/authord/mkdocs/defaults/vector/NoOpVectorStoreAdapter.kt`.
- Wired in composition root at `modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/PluginCompositionRoot.kt`.
- Verified by `NoOpAdaptersTest` and `TopicTreePortContractTest`.

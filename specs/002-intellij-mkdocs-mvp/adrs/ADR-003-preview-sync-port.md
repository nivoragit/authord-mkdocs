# ADR-003: PreviewSyncPort as MVP Seam with No-op Default

- Status: Accepted
- Date: 2026-02-08

## Context

MVP excludes code-preview synchronization behavior, but requires a seam for future sync features.

## Decision

Define `PreviewSyncPort` with minimal contract methods and ship a no-op default adapter in MVP.

## Consequences

- Positive: Preserves extension point without introducing out-of-scope behavior.
- Positive: Enables contract tests now and behavior implementation later.
- Tradeoff: Extra seam surface in MVP with intentionally limited behavior.

## Implementation Outcome

- Implemented seam at `modules/extension-ports/src/main/kotlin/com/authord/mkdocs/ports/preview/PreviewSyncPort.kt`.
- Implemented default adapter at `modules/infra-defaults/src/main/kotlin/com/authord/mkdocs/defaults/preview/NoOpPreviewSyncAdapter.kt`.
- Wired in composition root at `modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/PluginCompositionRoot.kt`.
- Verified by `NoOpAdaptersTest` and `TopicTreePortContractTest`.

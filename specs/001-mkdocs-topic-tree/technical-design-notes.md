# Technical Design Notes: Phase 2 - MkDocs Topic Tree Management

## 1. Scope and Goals

Phase 2 delivers topic-tree management synced with MkDocs configuration and markdown files while keeping MVP preview behavior backward compatible.

## 2. Architecture Overview

## 2.1 Domain Core (UI/vendor agnostic)

Planned domain entities and invariants:
- `TopicId`
- `TopicNode`
- `TopicTree`
- `TopicOrder`
- `TopicLink`
- `TopicMetadata`

## 2.2 Command-Based Mutation and Validation Layer

Commands:
- `add`
- `move`
- `remove`
- `rename`
- `reparent`
- `reorder`

Validation responsibilities:
- hierarchy integrity
- duplicate/broken path detection
- malformed link detection
- instance-scope enforcement

## 2.3 MkDocsConfigGateway

Responsibilities:
- Parse `mkdocs.yml` (`nav`, `docs_dir`, `not_in_nav`)
- Serialize deterministically
- Preserve canonical nav ordering/hierarchy

## 2.4 DocsFileGateway

Responsibilities:
- Create/delete/rename/move markdown files
- Enforce path normalization and project-boundary safety
- Provide recoverable delete behavior

## 2.5 TreeSyncOrchestrator

Planned transaction lifecycle:
1. Prepare
2. Apply tree/nav changes
3. Apply file mutations
4. Verify post-conditions
5. Commit or rollback/compensate

## 2.6 InstanceRegistry

Responsibilities:
- Discover root default instance
- Register explicit additional instances
- Persist/restore last selected instance
- Scope all operations to active instance

## 2.7 UI Layer Wiring

Responsibilities:
- Tree action handlers for command dispatch
- Drag/drop to command mapping
- Instance switching controls
- Validation/reconciliation issue display

## 2.8 Startup + Watcher Reconciliation

Responsibilities:
- Build tree from nav or deterministic fallback
- Reconcile nav/docs divergence (nav-first, non-destructive)
- Debounced update pipeline for file/config changes

## 2.9 Error Taxonomy and Observability

Planned categories:
- `VALIDATION`
- `CONFIG_PARSE`
- `CONFIG_WRITE`
- `FILE_IO`
- `RECONCILIATION`
- `ORCHESTRATION`
- `INSTANCE_SCOPE`

Log/event requirements:
- include project/instance/command correlation fields
- map user notifications to recoverable vs non-recoverable failures

## 3. Backward Compatibility Notes

- MVP preview start/update workflow remains unchanged.
- Topic-tree additions are additive and feature-flag-aware.

## 4. Public API Versioning and Documentation

- Record version changes for public topic-tree contracts.
- Add/maintain KDoc and usage contracts for every new/changed public service.

## 5. Open Design Decisions

- None blocking planning. Implementation tradeoffs will be tracked in code-level ADR notes if needed.

## 6. Implementation Mapping (to be filled during execution)

| Component | Target Module | Planned Entry Points | Status |
|-----------|---------------|----------------------|--------|
| Domain core entities | `modules/core-domain` | `.../core/topic/*` | Planned |
| Command handlers | `modules/core-domain` + `modules/extension-ports` | `TopicTreeCommand*` | Planned |
| Config gateway | `modules/mkdocs-runtime-adapter` or `modules/infra-defaults` | `MkDocsConfigGateway` | Planned |
| File gateway | `modules/mkdocs-runtime-adapter` or `modules/infra-defaults` | `DocsFileGateway` | Planned |
| Orchestrator | `modules/ui-plugin` + adapter layer | `TreeSyncOrchestrator` | Planned |
| Instance registry | `modules/ui-plugin` | `InstanceRegistry` | Planned |
| UI wiring | `modules/ui-plugin` | tool window/tree action layer | Planned |

## 7. Implementation Status Snapshot (2026-02-15)

1. Scoped coverage-gate design is closed with canonical PASS evidence (`20260215T041517Z`) in:
- `tests/integration/topic-tree/CoverageGateEvidence.md`
- `specs/001-mkdocs-topic-tree/quickstart.md`
- `specs/001-mkdocs-topic-tree/test-plan-traceability.md`

2. Success-criteria evidence artifacts were added for:
- SC-001: `tests/integration/topic-tree/StartupTimingEvidence.md`
- SC-002: `tests/integration/topic-tree/PartialSyncZeroStateEvidence.md`
- SC-003: `tests/integration/topic-tree/SeededValidationEvidence.md`
- SC-004: `tests/integration/topic-tree/CompatibilityRegressionEvidence.md`
- SC-006: `tests/integration/topic-tree/MutationResponsivenessEvidence.md`
- SC-007: `tests/integration/topic-tree/InstanceScaleEvidence.md`

3. Runtime-decoupling guard checks were re-verified with focused test evidence:
- `modules/mkdocs-runtime-adapter/src/test/kotlin/com/authord/mkdocs/runtime/RuntimeDecouplingPolicyTest.kt`
- `tests/integration/topic-tree/RunIdeDecouplingEvidence.md`
- `specs/001-mkdocs-topic-tree/analysis-closure.md`

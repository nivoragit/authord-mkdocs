# Phase 0 Research: IntelliJ MkDocs Plugin MVP

## Scope

Research tasks were derived from:
- MVP architecture goals (extension-ready seams + minimal defaults)
- Runtime lifecycle constraints (single server per project, reliable start/stop/restart, dispose-safe cleanup)
- Contract needs (topic-tree command DTO/results, explorer and preview events)
- Quality gates (SDD artifacts and 100% unit coverage for in-scope code)

All technical-context unknowns are resolved in this document.

## Decisions

### 1) Module Boundary Strategy

- Decision: Use a five-module architecture exactly matching requested boundaries: `core-domain`, `mkdocs-runtime-adapter`, `ui-plugin`, `extension-ports`, `infra-defaults`.
- Rationale: Keeps domain invariants independent from IDE UI and runtime process control, while making extension seams explicit and contract-testable.
- Alternatives considered: Single-module plugin package. Rejected because seam contracts and no-op adapters would be tightly coupled and harder to validate independently.

### 2) Topic Tree Ownership

- Decision: Keep `TopicTree` aggregate, node invariants, and mutation rules exclusively in `core-domain`, exposed outward through `TopicTreePort` commands.
- Rationale: Ensures deterministic mutation behavior and avoids UI/runtime layers bypassing invariants.
- Alternatives considered: UI-owned tree state with ad-hoc validation. Rejected because it increases integrity risk and weakens command contract clarity.

### 3) Route Mapping Policy

- Decision: Implement a deterministic `RouteMappingService` in `core-domain` with explicit rules:
  - `docs/index.md -> /`
  - `docs/<segment>/index.md -> /<segment>/`
  - `docs/<segment>.md -> /<segment>/`
  - Nested paths follow the same pattern recursively.
- Rationale: Keeps routing testable and stable independent of UI selection mechanics.
- Alternatives considered: Mapping logic inside UI coordinator. Rejected because it is harder to unit-test and easier to regress with UI changes.

### 4) Semantic Scroll Policy

- Decision: `ScrollSemanticService` computes scroll delta only from non-comment PSI ranges and returns semantic delta samples for downstream preview synchronization seams.
- Rationale: Satisfies MVP behavior while preserving a clean seam for future bidirectional sync.
- Alternatives considered: Raw editor offset tracking. Rejected because it includes comment-only movement and violates MVP semantics.

### 5) Runtime Bootstrap and Install Path

- Decision: `mkdocs-runtime-adapter` performs first-run runtime provisioning with `uv`, installs `mkdocs`, and records runtime readiness per project.
- Rationale: Meets first-activation zero-manual-setup requirement and isolates external tool execution in one module.
- Alternatives considered: Use project-global Python environment. Rejected due to dependency drift and lower reproducibility.

### 6) Runtime Lifecycle Management

- Decision: Enforce single mkdocs server process per project with a lifecycle state machine (`Uninitialized -> Bootstrapping -> Ready -> Serving -> Stopping/Restarting -> Stopped/Failed -> Disposed`).
- Rationale: Provides predictable behavior for activation, re-activation, and IDE shutdown.
- Alternatives considered: Fire-and-forget process launch per activation action. Rejected due to duplicate-process risk and unreliable cleanup.

### 7) Base URL Detection Contract

- Decision: Parse mkdocs stdout using a strict URL extraction contract and publish `RuntimeBaseUrlDetected` only after validation.
- Rationale: Prevents opening invalid preview sessions and supports deterministic error handling when output is malformed.
- Alternatives considered: Fixed hardcoded URL assumption. Rejected because mkdocs bind/port can vary in practice.

### 8) Command and Event Contracts

- Decision: Model command interactions via an OpenAPI contract (`plugin-control.openapi.yaml`) and event streams via AsyncAPI (`navigation-events.asyncapi.yaml`).
- Rationale: Gives explicit typed DTO/result schemas for topic-tree mutations and navigation events, and supports contract tests against defaults.
- Alternatives considered: Informal Kotlin interfaces only. Rejected because cross-module contract drift is harder to detect.

### 9) Default Adapter Behavior

- Decision: Ship no-op `PreviewSyncPort`, no-op `VectorStorePort`, and in-memory `CommandRegistry` in `infra-defaults`.
- Rationale: Enables extension seams now without enabling out-of-scope features.
- Alternatives considered: Omit adapters until future cycle. Rejected because seam integration and contract testing are MVP requirements.

### 10) Testing and Quality Gate Enforcement

- Decision: Use three test layers:
  - Unit: routing, index handling, URL parsing, semantic scroll offsets, topic invariants
  - Integration: runtime lifecycle, explorer-to-preview updates
  - Contract: default adapters for extension ports
  and enforce CI gate of 100% unit coverage on scoped code.
- Rationale: Directly matches requested quality strategy and isolates failures by concern.
- Alternatives considered: Integration-heavy testing only. Rejected due to weaker localization and insufficient guarantee for coverage gate.

### 11) DocOps Artifacts

- Decision: Produce four ADRs (TopicTreePort, CommandBus, PreviewSyncPort, VectorStorePort), a runtime diagnostics runbook, and a requirements-to-tests traceability matrix as first-class planning artifacts.
- Rationale: Satisfies SDD cycle expectations and improves implementation accountability.
- Alternatives considered: Keep design decisions embedded only in plan text. Rejected because later audits and onboarding become harder.

## Resolved Clarifications

- No unresolved `NEEDS CLARIFICATION` items remain.

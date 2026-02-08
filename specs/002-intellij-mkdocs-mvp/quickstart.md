# Quickstart: IntelliJ MkDocs Plugin MVP Plan

## Purpose

This quickstart validates the planned architecture, contracts, and quality gates before implementation tasks are generated.

## 1) Confirm Inputs

1. Ensure branch is `002-intellij-mkdocs-mvp`.
2. Review `spec.md` for in-scope/out-of-scope boundaries.
3. Review `plan.md`, `research.md`, and `data-model.md` for architecture and invariants.

## 2) Scaffold Planned Module Layout

Create module roots:

- `modules/core-domain`
- `modules/mkdocs-runtime-adapter`
- `modules/ui-plugin`
- `modules/extension-ports`
- `modules/infra-defaults`

Create test roots:

- `tests/unit`
- `tests/integration`
- `tests/contract`

## 3) Implement Core Domain First

1. Implement `TopicTree` aggregate and invariants.
2. Implement `RouteMappingService` with required mapping rules.
3. Implement `ScrollSemanticService` with comment PSI exclusion logic.
4. Add unit tests for each invariant and mapping case.

## 4) Implement Runtime Adapter

1. Implement first-activation runtime bootstrap via `uv`.
2. Implement mkdocs process manager with one server instance per project.
3. Implement stdout base URL parser and validation.
4. Add unit + integration tests for startup, restart, stop, and cleanup.

## 5) Implement UI Module

1. Implement docs explorer surface.
2. Implement preview pane with embedded browser.
3. Implement navigation coordinator subscribing to selection events and applying route updates.
4. Add integration tests for explorer-to-preview flow.

## 6) Implement Ports and Defaults

1. Implement `TopicTreePort`, `PreviewSyncPort`, `PluginCommandBus`, `CommandRegistry`, `VectorStorePort` contracts.
2. Implement defaults:
   - no-op `PreviewSyncPort`
   - in-memory `CommandRegistry`
   - no-op `VectorStorePort`
3. Add contract tests ensuring defaults satisfy port contracts without enabling out-of-scope behavior.

## 7) Validate Contracts

1. Validate OpenAPI schema at `contracts/plugin-control.openapi.yaml`.
2. Validate AsyncAPI schema at `contracts/navigation-events.asyncapi.yaml`.
3. Confirm command DTO/result schemas and event payload schemas match implementation.

## 8) Enforce Quality Gates

1. Run unit tests for scoped code and enforce 100% unit coverage gate.
2. Run integration tests for:
   - runtime lifecycle
   - explorer -> preview updates
3. Run contract tests for default adapters and port compatibility.

## 9) Complete DocOps Deliverables

1. Finalize ADRs in `adrs/`.
2. Update `runtime-diagnostics-runbook.md` with observed startup/restart failure diagnostics.
3. Update `traceability-matrix.md` to map requirements to implemented tests.

## Validation Snapshot (2026-02-08)

- [x] Multi-module project skeleton created.
- [x] MVP runtime activation flow implemented and tested.
- [x] Docs explorer to preview route mapping implemented and tested.
- [x] Extension seams + default adapters implemented and contract-tested.
- [x] Contracts validated against implementation classes.
- [x] SDD artifacts updated (ADRs, runbook, traceability matrix).
- [x] `./gradlew test` passes in local environment.
- [ ] `jacocoTestCoverageVerification` at 100% line coverage pending final confirmation.

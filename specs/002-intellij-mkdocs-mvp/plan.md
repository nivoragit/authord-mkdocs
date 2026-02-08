# Implementation Plan: IntelliJ MkDocs Plugin MVP

**Branch**: `[002-intellij-mkdocs-mvp]` | **Date**: 2026-02-08 | **Spec**: [`/Users/madushika/projects/authord-mkdocs-plugin/specs/002-intellij-mkdocs-mvp/spec.md`](/Users/madushika/projects/authord-mkdocs-plugin/specs/002-intellij-mkdocs-mvp/spec.md)
**Input**: Feature specification from `/specs/002-intellij-mkdocs-mvp/spec.md`

## Summary

Deliver an extension-ready IntelliJ MkDocs MVP with five bounded modules: `core-domain`, `mkdocs-runtime-adapter`, `ui-plugin`, `extension-ports`, and `infra-defaults`. The plan prioritizes first-activation runtime bootstrap, single-instance mkdocs server lifecycle reliability, docs explorer to preview navigation, semantic scroll delta capture excluding comment PSI ranges, and explicit extension seams (command bus, topic tree port, preview sync port, vector store port) with minimal/no-op defaults. Phase outputs include design contracts, data model, quickstart, and DocOps artifacts (ADRs, runtime diagnostics runbook, requirements-to-tests traceability matrix).

## Technical Context

**Language/Version**: Kotlin 1.9+ on JVM 21 (IntelliJ Platform plugin target)  
**Primary Dependencies**: IntelliJ Platform SDK (PSI, ToolWindow, Disposable lifecycle), embedded browser component, `uv` CLI, `mkdocs` CLI  
**Storage**: Local project filesystem + IDE plugin state; no external database in MVP  
**Testing**: JUnit 5 + IntelliJ Platform test framework + contract schema validation + integration tests  
**Target Platform**: IntelliJ-based IDEs on macOS/Linux/Windows  
**Project Type**: Single IntelliJ plugin project with modular package boundaries  
**Performance Goals**: First activation to live preview <= 5 minutes (p90), preview route update <= 2 seconds (p95), base URL auto-detection >= 95% successful startups  
**Constraints**: Single mkdocs server instance per project; start/stop/restart reliability; dispose-safe cleanup; out-of-scope features disabled via flags; 100% unit coverage for in-scope code  
**Scale/Scope**: One active preview session per project; docs trees up to 10k markdown files; single-developer local workflow for MVP

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Gate | Pre-Phase 0 | Post-Phase 1 | Status |
|------|-------------|--------------|--------|
| Constitution enforceability | `.specify/memory/constitution.md` is a placeholder template with no ratified enforceable rules | Unchanged; no additional constitutional constraints discovered | PASS (informational) |
| Spec quality gates captured | SDD artifacts and 100% unit coverage captured in plan scope | Reflected in `quickstart.md` and `traceability-matrix.md` | PASS |
| Scope discipline | In-scope MVP items and explicit out-of-scope exclusions captured | Contracts and defaults preserve extension seams without enabling excluded features | PASS |
| Reliability constraints | Lifecycle reliability requirements captured at planning level | Runtime lifecycle contract + runbook include start/stop/restart and disposal paths | PASS |

## Project Structure

### Documentation (this feature)

```text
specs/002-intellij-mkdocs-mvp/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── runtime-diagnostics-runbook.md
├── traceability-matrix.md
├── adrs/
│   ├── ADR-001-topic-tree-port.md
│   ├── ADR-002-plugin-command-bus.md
│   ├── ADR-003-preview-sync-port.md
│   └── ADR-004-vector-store-port.md
├── contracts/
│   ├── plugin-control.openapi.yaml
│   └── navigation-events.asyncapi.yaml
└── tasks.md
```

### Source Code (repository root)

```text
modules/
├── core-domain/
│   ├── src/main/kotlin/
│   └── src/test/kotlin/
├── mkdocs-runtime-adapter/
│   ├── src/main/kotlin/
│   └── src/test/kotlin/
├── ui-plugin/
│   ├── src/main/kotlin/
│   └── src/test/kotlin/
├── extension-ports/
│   ├── src/main/kotlin/
│   └── src/test/kotlin/
└── infra-defaults/
    ├── src/main/kotlin/
    └── src/test/kotlin/

tests/
├── integration/
│   ├── runtime-lifecycle/
│   └── explorer-preview-navigation/
└── contract/
    ├── topic-tree-command-port/
    ├── preview-sync-defaults/
    └── vector-store-defaults/
```

**Structure Decision**: Use a single-repo, multi-module plugin architecture to keep MVP boundaries explicit and extension seams testable. Domain logic remains isolated from runtime process control and UI concerns, while port contracts and default adapters remain independently testable.

## Complexity Tracking

No constitutional violations require justification in this plan iteration.

# Implementation Plan: IntelliJ Plugin Shell Cycle

**Branch**: `002-intellij-mkdocs-mvp` | **Date**: 2026-02-09 | **Spec**: `/Users/madushika/projects/authord-mkdocs-plugin/specs/002-intellij-mkdocs-mvp/spec.md`
**Input**: Feature specification from `/Users/madushika/projects/authord-mkdocs-plugin/specs/002-intellij-mkdocs-mvp/spec.md` plus cycle planning brief for plugin-shell enablement

## Summary

This cycle converts the existing MVP services into a runnable IntelliJ plugin shell without rewriting core runtime/domain behavior. The plan introduces plugin build/descriptor wiring, a minimal MkDocs tool window factory, and a Start MkDocs Preview action that delegates to existing activation/runtime services while preserving project-scoped single-instance runtime guarantees. Delivery includes plugin-shell smoke verification and complete DocOps outputs required by constitution gates.

## Technical Context

**Language/Version**: Kotlin 1.9+ on JVM 21  
**Primary Dependencies**: IntelliJ Platform SDK, IntelliJ Platform Gradle Plugin, existing project modules (`core-domain`, `mkdocs-runtime-adapter`, `ui-plugin`, `extension-ports`, `infra-defaults`)  
**Storage**: N/A (project-local runtime directory + in-memory adapter state)  
**Testing**: Gradle `test`, JaCoCo coverage verification, unit tests for action/tool window/integration adapter seams, plugin-shell smoke checks via `runIde` workflow  
**Target Platform**: IntelliJ IDEA Community target baseline compatible with current plugin metadata and sandbox run workflow  
**Project Type**: Multi-module IntelliJ plugin project  
**Performance Goals**: Action update/actionPerformed path responds without user-perceived lag and avoids blocking UI interaction during shell operations  
**Constraints**: Preserve single runtime instance per project, no feature-scope expansion beyond shell enablement, maintain 100% scoped unit coverage, maintain mandatory extension seams  
**Scale/Scope**: One plugin shell cycle delivering build/plugin wiring, descriptor registration, tool window shell, action wiring, runtime integration adapter, and verification/docs outputs

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Gate | Description | Pre-Phase 0 | Post-Phase 1 |
|------|-------------|-------------|--------------|
| I. OS-Neutral Execution | Uses `uv`, avoids shell activation scripts and hardcoded Python paths, and uses Path APIs for filesystem operations | PASS | PASS |
| II. Runtime Decoupling | Avoids hardcoded host/port and routes preview using base URL detected from runtime stdout | PASS | PASS |
| III. Delivery Completeness | Plans all required docs: feature spec, technical design notes, operational runbook, test plan + traceability matrix, changelog, migration notes (if needed) | PASS | PASS |
| IV. Test Gate | Enforces 100% unit coverage for scoped code and CI failure below threshold | PASS | PASS |
| V. MVP-First, Extension-Ready | Limits implementation to MVP behavior and defines stable interfaces with default/no-op adapters for future features | PASS | PASS |
| VI. Topic-Tree Parity Runway | Keeps topic-tree domain vendor-independent and defines command-based mutation + validation runway | PASS | PASS |
| VII. Backward Compatibility | Defines interface versioning and confirms MVP workflows remain compatible | PASS | PASS |
| VIII. Code Documentation Standard | Plans KDoc for all public APIs and meaningful comments for non-obvious logic/invariants | PASS | PASS |

## Project Structure

### Documentation (this feature)

```text
/Users/madushika/projects/authord-mkdocs-plugin/specs/002-intellij-mkdocs-mvp/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
└── tasks.md
```

### Source Code (repository root)

```text
/Users/madushika/projects/authord-mkdocs-plugin/
├── build.gradle.kts
├── settings.gradle.kts
├── src/main/resources/
│   └── META-INF/plugin.xml
├── modules/
│   ├── core-domain/
│   ├── mkdocs-runtime-adapter/
│   ├── ui-plugin/
│   ├── extension-ports/
│   └── infra-defaults/
├── tests/
│   ├── contract/
│   └── integration/
├── docs/implementation/
└── .github/workflows/
```

**Structure Decision**: Keep the existing multi-module structure and add plugin-shell entry wiring in descriptor/resources and `ui-plugin` orchestration layer. Runtime/domain logic remains in existing modules and is reused through adapter/facade boundaries.

## Phase 0: Research Plan

1. Confirm IntelliJ Platform Gradle plugin approach compatible with current project (IDE target, metadata, `runIde`).
2. Confirm plugin descriptor registration pattern for tool window and action visibility.
3. Confirm action-system execution patterns for safe `update` and `actionPerformed` behavior.
4. Confirm runtime integration pattern that reuses existing services and preserves single-instance guard semantics.
5. Confirm verification strategy for unit + smoke checks and CI/local runIde documentation.

## Phase 1: Design Plan

1. Define plugin-shell entities and lifecycle transitions in `data-model.md`.
2. Define action/tool-window/runtime-handoff interaction contracts in `/contracts/`.
3. Define implementation quickstart for local and CI-oriented plugin-shell verification.
4. Update agent context for Codex after design artifact refresh.

## Complexity Tracking

No constitution violations are required for this cycle plan.

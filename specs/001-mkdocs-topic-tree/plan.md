# Implementation Plan: Phase 2 - MkDocs Topic Tree Management

**Branch**: `001-mkdocs-topic-tree` | **Date**: 2026-02-13 | **Spec**: `/Users/madushika/projects/authord-mkdocs-plugin/specs/001-mkdocs-topic-tree/spec.md`
**Input**: Feature specification from `/Users/madushika/projects/authord-mkdocs-plugin/specs/001-mkdocs-topic-tree/spec.md`

## Summary

Phase 2 delivers topic-tree management synchronized with `mkdocs.yml` and markdown files while preserving MVP preview behavior. The implementation remains domain-core first, supports command-based mutation workflows, enforces deterministic config serialization, provides non-destructive reconciliation with explicit watcher triggers, and applies strict release gates for compatibility severity and 100% scoped unit coverage.

Scope and constraints in this plan map only to explicit spec requirements (`R-01..R-22`, `NAV-001..NAV-004`, `PI-001..PI-003`, `DOC-001..DOC-006`) plus constitution gates.

## Technical Context

**Language/Version**: Kotlin 1.9.24 on JVM 21 (plugin target compatibility on JVM 17 bytecode) (`R-15`, `R-16`)  
**Primary Dependencies**: IntelliJ Platform SDK, IntelliJ Platform Gradle plugin, existing modules (`core-domain`, `extension-ports`, `infra-defaults`, `mkdocs-runtime-adapter`, `ui-plugin`), `uv` + `mkdocs` runtime path (`R-15`, `R-16`, `R-18`)  
**Storage**: Project-local `mkdocs.yml`/`mkdocs.yaml`, `docs_dir` markdown files, in-memory project service state (`R-01`, `R-02`, `R-08`, `R-09`, `R-12`)  
**Testing**: Unit + integration + smoke + policy tests with requirements-to-tests traceability (`R-15a`, `R-17`, `R-18`, `DOC-004`)  
**Target Platform**: IntelliJ plugin runtime on Windows/macOS/Linux (`R-15`, `R-17`, `R-17a`)  
**Project Type**: Multi-module IntelliJ plugin with UI/vendor-agnostic domain core (`R-04`, `R-13`, `NAV-001`)  
**Performance Goals**: Startup+initial reconciliation <=2s p95 on reference datasets; mutation feedback <=300ms p95 (`R-19`, `R-20`, `SC-001`, `SC-006`)  
**Constraints**: Canonical nav source, deterministic serialization, non-destructive reconciliation trigger policy, compatibility severity blocking, 100% scoped unit coverage (`R-12e`, `R-13`, `R-14`, `R-15a`, `R-18`, `NAV-002`, `NAV-004`)  
**Scale/Scope**: Up to 5 instances and 10,000 markdown files without cross-instance mutation leakage (`R-21`)  

## NFR and Constraint Binding (No Hidden Scope)

| Plan Constraint / NFR | Spec Requirement IDs |
|------------------------|----------------------|
| Startup+reconciliation latency target | `R-19`, `SC-001` |
| Mutation feedback responsiveness target | `R-20`, `SC-006` |
| Supported scale profile | `R-21`, `SC-007` |
| Runtime compatibility and regression blocking | `R-15`, `R-15a`, `SC-004` |
| Watcher trigger exactness | `R-12`, `R-12e`, `NAV-004` |
| OS matrix and path/case behavior | `R-17`, `R-17a` |
| Canonical nav + deterministic serialization | `R-13`, `R-14`, `NAV-001`, `NAV-003` |
| Atomic sync or compensating rollback | `R-11`, `R-11a`, `NAV-002` |
| Scoped 100% unit coverage gate | `R-18`, `SC-005` |

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Gate | Description | Pre-Phase 0 | Post-Phase 1 |
|------|-------------|-------------|--------------|
| I. OS-Neutral Execution | Uses `uv`, avoids shell activation scripts and hardcoded Python paths, and uses Path APIs for filesystem operations | PASS | PASS |
| II. Runtime Decoupling | Avoids hardcoded host/port and routes preview using base URL detected from runtime stdout | PASS | PASS |
| III. Delivery Completeness | Plans all required docs: feature spec, technical design notes, operational runbook, test plan + traceability matrix, changelog, migration notes (if needed) | PASS | PASS |
| IV. Test Gate | Enforces 100% unit coverage for scoped code and CI failure below threshold | PASS | PASS |
| V. MVP-First, Extension-Ready | Limits implementation to in-scope MVP behavior and keeps extension seams stable | PASS | PASS |
| VI. Topic-Tree Parity Runway | Keeps topic-tree domain vendor-independent, canonical nav source, atomic sync/rollback, deterministic serialization, and explicit reconciliation policy | PASS | PASS |
| VII. Backward Compatibility | Defines interface versioning and confirms MVP preview workflows remain compatible | PASS | PASS |
| VIII. Code Documentation Standard | Plans KDoc for all public APIs and meaningful comments for non-obvious logic/invariants | PASS | PASS |

Checklist completion with evidence is a pre-implementation gate artifact.

## Project Structure

### Documentation (this feature)

```text
/Users/madushika/projects/authord-mkdocs-plugin/specs/001-mkdocs-topic-tree/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── technical-design-notes.md
├── operational-runbook.md
├── test-plan-traceability.md
├── contracts/
│   ├── topic-tree-control.openapi.yaml
│   └── topic-tree-events.asyncapi.yaml
├── checklists/
│   ├── requirements.md
│   ├── phase2-gates.md
│   └── phase2-compliance-hard-gates.md
├── analysis-closure.md
└── tasks.md
```

### Source Code (repository root)

```text
/Users/madushika/projects/authord-mkdocs-plugin/
├── modules/
│   ├── core-domain/
│   │   └── src/main/kotlin/com/authord/mkdocs/core/topic/
│   ├── extension-ports/
│   │   └── src/main/kotlin/com/authord/mkdocs/ports/
│   ├── infra-defaults/
│   │   └── src/main/kotlin/com/authord/mkdocs/defaults/
│   ├── mkdocs-runtime-adapter/
│   │   └── src/main/kotlin/com/authord/mkdocs/runtime/
│   └── ui-plugin/
│       ├── src/main/kotlin/com/authord/mkdocs/ui/intellij/
│       └── src/main/resources/META-INF/plugin.xml
├── tests/
│   ├── contract/
│   └── integration/
└── docs/implementation/
```

**Structure Decision**: Preserve current multi-module architecture. Implement domain + ports first, then gateways/orchestration, then UI/watcher wiring. Keep preview path backward-compatible.

## Phase 0: Research Plan

1. Re-validate deterministic YAML parse/serialize profile and diff-churn guardrails (`R-14`, `NAV-003`).
2. Re-validate atomic sync with compensation ordering and rollback idempotency (`R-11`, `R-11a`, `NAV-002`).
3. Confirm exact watcher trigger matrix and external move handling paths (`R-12`, `R-12e`, `NAV-004`).
4. Confirm OS matrix normalization/case handling policy and edge cases (`R-17`, `R-17a`).
5. Confirm compatibility severity rubric classification source and release-blocker usage (`R-15a`, `DOC-004`, `SC-004`).
6. Confirm runtime decoupling invariants are explicitly retained under compatibility strategy (`R-15`, `R-15a`, Constitution Gate II).
7. Confirm NFR measurement approach for startup latency, feedback latency, and scale profile (`R-19`, `R-20`, `R-21`).

## Phase 1: Design Plan

1. Update `data-model.md` for `R-12e`, `R-15a`, `R-17a`, and NFR entities (`R-19..R-21`) as needed.
2. Update contracts in `/contracts/` for watcher-trigger events, compatibility severity reporting, and instance/scale trace fields (`R-12e`, `R-15a`, `R-21`).
3. Update `quickstart.md` with:
- runtime decoupling verification steps (`R-15`, `R-15a`),
- watcher trigger coverage checks (`R-12e`),
- OS matrix validation checks (`R-17a`),
- release severity gate decision checks (`R-15a`).
4. Maintain/expand planning artifact structures:
- `technical-design-notes.md`: architecture decisions, invariants, and rollback strategy (`DOC-002`),
- `operational-runbook.md`: startup/reconciliation/recovery workflows (`DOC-003`),
- `test-plan-traceability.md`: complete requirements-to-tests matrix (`DOC-004`).
5. Run `.specify/scripts/bash/update-agent-context.sh codex` and verify no unsupported hidden scope is introduced.
6. Re-check constitution gates after design artifact alignment.

## Phase 2: Implementation Planning Scope

1. **Domain Core + Canonical Nav**
- Implement/adjust `TopicId`, `TopicNode`, `TopicTree`, `TopicOrder`, `TopicLink`, `TopicMetadata`, and command mutations (`R-04`, `R-04a`, `R-13`, `R-14`, `R-22`).

2. **Gateways + Atomic Orchestration**
- Implement deterministic `MkDocsConfigGateway` and `DocsFileGateway` sync behaviors (`R-08`, `R-09`, `R-09a`, `R-09b`, `R-09c`, `R-11`, `R-11a`).
- Implement `TreeSyncOrchestrator` apply/verify/rollback flow (`R-11`, `NAV-002`).

3. **UI Wiring + Drag/Drop + Independence**
- Wire action and drag/drop command paths with instance scoping and US2 independence contracts (`R-04`, `R-05`, `R-06`, `R-07`, `R-22`).

4. **Startup + Watchers + Reconciliation**
- Implement startup load and exact trigger coverage for file/config changes, including external move handling (`R-01`, `R-02`, `R-03`, `R-12`, `R-12a`, `R-12b`, `R-12c`, `R-12d`, `R-12e`).

5. **Runtime Decoupling and Compatibility Safeguards**
- Preserve no hardcoded host/port behavior and stdout URL detection checks via compatibility validation strategy (`R-15`, `R-15a`).

6. **OS Matrix Verification**
- Verify path normalization and case behavior across Windows/macOS/Linux (`R-17`, `R-17a`).

7. **Severity Rubric and Release Decisions**
- Enforce severity classification source of truth and fail release gate on unresolved high-severity regressions (`R-15a`, `DOC-004`, `SC-004`).

8. **Quality + Delivery Gates**
- Enforce 100% scoped coverage and complete all required DocOps artifacts (`R-18`, `DOC-001..DOC-006`, `SC-005`).
- Maintain MVP-first and backward-compatible evolution constraints explicitly (`R-15`, `R-16`, `PI-001..PI-003`).

## Plan Section -> Spec Requirement Mapping Summary

| Plan Section | Spec Requirement IDs |
|--------------|----------------------|
| Summary | `R-01..R-22`, `NAV-001..NAV-004`, `PI-001..PI-003` |
| Technical Context | `R-15`, `R-15a`, `R-16`, `R-17`, `R-17a`, `R-18`, `R-19`, `R-20`, `R-21` |
| NFR and Constraint Binding | `R-12`, `R-12e`, `R-13`, `R-14`, `R-15a`, `R-18`, `R-19`, `R-20`, `R-21`, `NAV-001`, `NAV-002`, `NAV-003`, `NAV-004` |
| Constitution Check | `DOC-001..DOC-006`, `R-15`, `R-16`, `R-18`, `NAV-001..NAV-004` |
| Phase 0 Research Plan | `R-11`, `R-11a`, `R-12`, `R-12e`, `R-14`, `R-15`, `R-15a`, `R-17`, `R-17a`, `R-19`, `R-20`, `R-21`, `NAV-002`, `NAV-003`, `NAV-004` |
| Phase 1 Design Plan | `R-12e`, `R-15a`, `R-17a`, `R-21`, `DOC-002`, `DOC-003`, `DOC-004` |
| Phase 2 Implementation Scope | `R-01..R-22`, `NAV-001..NAV-004`, `PI-001..PI-003`, `DOC-001..DOC-006` |

Plan-to-Spec Mapping unchanged: no requirement IDs were added, removed, or remapped by this patch.

## Complexity Tracking

No constitution violations are required for this cycle plan.

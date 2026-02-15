<!--
Sync Impact Report
- Version change: 1.0.0 -> 1.1.0
- Modified principles:
  - VI. Topic-Tree Parity Runway (expanded with canonical nav, atomic sync, deterministic serialization, and startup reconciliation obligations)
- Added sections:
  - None
- Removed sections:
  - None
- Templates requiring updates:
  - ✅ Updated: .specify/templates/plan-template.md
  - ✅ Updated: .specify/templates/spec-template.md
  - ✅ Updated: .specify/templates/tasks-template.md
  - ⚠ Pending: .specify/templates/commands/*.md (directory not present in repository)
  - ✅ Reviewed (no update required): docs/implementation/README.md
  - ✅ Reviewed (no update required): docs/implementation/technical-design-notes.md
  - ✅ Reviewed (no update required): docs/implementation/operational-runbook.md
  - ✅ Reviewed (no update required): docs/implementation/test-plan-traceability.md
- Follow-up TODOs:
  - None
-->

# Authord MkDocs Plugin Constitution

## Core Principles

### I. OS-Neutral Execution

- All runtime/bootstrap execution MUST be OS-neutral.
- Shell activation scripts MUST NOT be required.
- Python executable paths MUST NOT be hardcoded.
- `uv` MUST be used for bootstrap and command execution.
- Filesystem operations MUST use Path APIs only.

Rationale: this prevents environment drift and platform-specific failures.

### II. Runtime Decoupling

- Host and port MUST NOT be hardcoded in runtime services.
- `mkdocs serve` MUST be started with default host/port behavior.
- Runtime base URL MUST be detected from stdout and used for preview routing.

Rationale: runtime endpoints can vary by environment and must be discovered, not assumed.

### III. Delivery Completeness Per Cycle

A cycle is complete only when all required documentation artifacts are delivered and current:

- feature spec
- technical design notes
- operational runbook
- test plan with requirements-to-tests traceability matrix
- changelog
- migration notes when compatibility impact exists

Documentation for each new or changed function/service MUST include:

- purpose
- inputs
- outputs
- errors/failure modes
- usage examples when relevant

Public interfaces MUST document usage contracts and constraints explicitly.

Rationale: delivery quality is incomplete without operational and maintenance context.

### IV. Test Gate

- In-scope production code MUST maintain 100% unit test coverage.
- CI MUST fail when scoped modules/files are below threshold.
- Coverage scope MUST be explicit in build configuration.

Rationale: strict coverage is a release gate for this project.

### V. MVP-First, Extension-Ready

- Only current-cycle MVP behavior MUST be implemented.
- Future capabilities MUST be prepared via stable interfaces and default/no-op adapters.
- Out-of-scope behavior MUST remain disabled by default.

Rationale: this preserves iteration speed without sacrificing extension seams.

### VI. Topic-Tree Parity Runway

- Domain model MUST remain UI/vendor independent and include:
  - `TopicId`
  - `TopicNode`
  - `TopicTree`
  - `TopicOrder`
  - `TopicLink`
  - `TopicMetadata`
- Command-based mutations with validation MUST support:
  - `add`
  - `move`
  - `remove`
  - `rename`
  - `reparent`
  - `reorder`
- Storage/provider integration MUST remain pluggable.
- `mkdocs.yml` `nav` MUST be the canonical persisted navigation model.
- Plugin topic tree state MUST be derived from `mkdocs.yml` `nav`, or from a deterministic fallback when `nav` is missing.
- Mutations spanning tree model, markdown files, and `mkdocs.yml` MUST be atomic; if true atomicity is not feasible, a compensating rollback procedure MUST be implemented and documented.
- `mkdocs.yml` writes MUST be deterministic to minimize diff churn for unchanged logical structures.
- Startup and file-change flows MUST reconcile `nav` and `docs_dir` with an explicit, documented conflict handling policy.

Rationale: parity evolution requires a stable, provider-agnostic domain core.

### VII. Backward-Compatible Evolution

- New features MUST NOT break MVP workflows.
- Public service interfaces MUST be versioned and documented.
- Breaking changes MUST include migration notes and compatibility rationale.

Rationale: predictable evolution reduces downstream integration risk.

### VIII. Code Documentation Standard

- KDoc is REQUIRED for all public classes, interfaces, methods, and functions.
- Comments are REQUIRED for non-obvious logic, invariants, edge cases, and design decisions.
- Redundant comments that restate code MUST NOT be added.

Rationale: maintainability requires explicit intent and contract-level documentation.

## Implementation Standards

1. Runtime and filesystem changes MUST satisfy Principles I and II before merge.
2. Public interface updates MUST satisfy Principles III, VII, and VIII in the same cycle.
3. Topic-tree related work MUST satisfy Principle VI, including canonical nav source, atomic sync integrity, deterministic serialization, and startup reconciliation, even when UI parity is deferred.
4. Coverage and CI settings MUST enforce Principle IV continuously.

## Delivery Workflow and Compliance

1. `/speckit.specify` output MUST include MVP scope boundaries and out-of-scope guardrails.
2. `/speckit.plan` MUST complete Constitution Check gates before implementation.
3. `/speckit.tasks` MUST include explicit tasks for:
   - documentation artifacts required by Principle III
   - unit/integration/contract testing and traceability
   - coverage gate enforcement
   - interface versioning and KDoc/comment compliance
4. A cycle MUST remain INCOMPLETE until docs, tests, and coverage gates are all green.

## Governance

- This constitution supersedes conflicting local workflow guidance.
- Amendments require:
  1. a written proposal in pull request description,
  2. explicit impact on templates and workflow artifacts,
  3. an updated Sync Impact Report in this file.
- Versioning policy:
  - MAJOR: incompatible governance changes or principle removals/redefinitions,
  - MINOR: new principle/section or materially expanded obligations,
  - PATCH: clarifications without normative impact.
- Compliance review is REQUIRED on every plan, task list, and release gate review.
- Violations MUST be documented in plan/task artifacts with justification and remediation steps.

**Version**: 1.1.0 | **Ratified**: 2026-02-08 | **Last Amended**: 2026-02-13

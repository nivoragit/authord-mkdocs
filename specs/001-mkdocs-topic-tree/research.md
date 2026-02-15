# Phase 0 Research: Phase 2 - MkDocs Topic Tree Management

## Scope

Research tasks were derived from Phase 2 requirements and architecture directives:
- Canonical `mkdocs.yml` nav as persisted source
- Deterministic fallback and deterministic serialization
- Atomic tree/nav/file mutation integrity with rollback/compensation
- Multi-instance model and reconciliation strategy
- Runtime/UI decoupling, compatibility, and observability requirements

All design-time clarifications required for planning are resolved below.

## Decisions

### 1) Canonical navigation source model

- Decision: Treat `mkdocs.yml` `nav` as the canonical persisted navigation model. The in-memory tree is always derived from canonical nav when present; fallback derivation applies only when nav is missing.
- Rationale: Matches constitution obligations and ensures deterministic, user-visible source of truth.
- Alternatives considered:
  - Filesystem-first canonical model.
  - Rejected because it cannot preserve explicit nav hierarchy/order semantics.

### 2) Fallback tree determinism strategy

- Decision: When nav is absent, derive fallback tree from `docs_dir` markdown files sorted by normalized relative path (`/` separators, case-preserving but case-insensitive compare for ordering).
- Rationale: Produces stable and reproducible tree output independent of filesystem listing order.
- Alternatives considered:
  - Native filesystem iteration order.
  - Rejected due nondeterministic ordering across OS/filesystems.

### 3) Deterministic `mkdocs.yml` serialization

- Decision: Use a deterministic serializer profile: stable map key ordering, normalized indentation, normalized list formatting, and no write if logical document content has not changed.
- Rationale: Minimizes diff churn and supports predictable reviews/merges.
- Alternatives considered:
  - Round-trip preserve-whitespace serializer.
  - Rejected because formatting drift from mixed editor changes remains hard to control.

### 4) Atomic mutation orchestration

- Decision: Implement `TreeSyncOrchestrator` with staged transaction flow: `prepare -> apply nav/tree -> apply file ops -> verify -> commit`, with compensation stack execution on failure.
- Rationale: Ensures no partial destructive state remains after mutation errors.
- Alternatives considered:
  - Best-effort updates without transaction semantics.
  - Rejected because it violates atomic integrity and recovery requirements.

### 5) Delete operation safety policy

- Decision: Default delete performs nav removal plus move-to-recovery location with explicit confirmation; explicit nav-only delete is separately available.
- Rationale: Balances sync consistency and non-destructive safety.
- Alternatives considered:
  - Immediate hard-delete default.
  - Rejected due high data-loss risk and weak recovery guarantees.

### 6) Rename/move link rewrite scope

- Decision: Rewrite relative markdown links in `docs_dir` markdown files only; skip external URLs and non-markdown assets.
- Rationale: Limits side effects while preserving local navigation integrity.
- Alternatives considered:
  - Global text rewrite across all files.
  - Rejected due false positives and high mutation risk.

### 7) Multi-instance registry model

- Decision: Auto-discover only root `mkdocs.yml` as default instance; additional instances require explicit path registration; persist selected instance per project.
- Rationale: Reduces accidental instance selection and keeps operation scope explicit.
- Alternatives considered:
  - Auto-discover all config files recursively.
  - Rejected due ambiguity and accidental cross-instance edits.

### 8) Reconciliation conflict policy

- Decision: Nav-first structure policy on startup and relevant file changes. Missing nav targets become validation issues; extra files become unlinked bucket entries; no automatic destructive deletion.
- Rationale: Preserves canonical ordering while avoiding destructive conflict resolution.
- Alternatives considered:
  - Aggressive auto-fix by deleting unmatched entries/files.
  - Rejected due destructive behavior and low operator trust.

### 9) Watcher and reload strategy

- Decision: Use scoped watchers with an explicit trigger matrix: active config (`mkdocs.yml`/`mkdocs.yaml`) create/update/delete/rename/move and markdown create/update/delete/rename/move under active `docs_dir`; debounce reconciliation to avoid event storms.
- Rationale: Keeps UI responsive and avoids redundant expensive rebuilds.
- Alternatives considered:
  - Full tree rebuild on every file event.
  - Rejected due unnecessary churn and degraded UX on large projects.

### 10) Runtime decoupling compatibility invariant

- Decision: Preserve runtime decoupling invariants as compatibility guardrails: no hardcoded host/port and stdout base-URL detection remain release-gated behaviors.
- Rationale: Phase 2 must not regress MVP preview behavior while topic-tree features are added.
- Alternatives considered:
  - Treat runtime decoupling as out-of-scope and unvalidated.
  - Rejected due constitution Gate II and regression risk.

### 11) Error taxonomy and observability model

- Decision: Introduce typed error categories (`VALIDATION`, `CONFIG_PARSE`, `CONFIG_WRITE`, `FILE_IO`, `RECONCILIATION`, `ORCHESTRATION`, `INSTANCE_SCOPE`) with structured logs and user-notification mapping.
- Rationale: Improves diagnosability and consistent user feedback.
- Alternatives considered:
  - Generic exception-to-string reporting only.
  - Rejected because it weakens supportability and traceability.

### 12) OS matrix and path comparison policy

- Decision: Encode explicit cross-platform behavior: Windows/macOS case-insensitive comparisons (preserve on-disk casing), Linux case-sensitive comparisons, with canonical normalized separators.
- Rationale: Removes ambiguity and makes `R-17` validation deterministic across supported OS.
- Alternatives considered:
  - Uniform case-sensitive handling on all platforms.
  - Rejected because it diverges from expected platform behavior and increases false violations.

### 13) Compatibility severity rubric and release usage

- Decision: Classify compatibility severity in test-plan traceability + compatibility validation report, and treat unresolved high-severity regressions as release blockers.
- Rationale: Ensures objective release decisions and aligns with backward-compatible evolution constraints.
- Alternatives considered:
  - Informal severity tracking in ad-hoc notes.
  - Rejected due inconsistent release decisions.

### 14) NFR scope alignment decision

- Decision: Keep startup latency, mutation feedback latency, and scale profile requirements in scope as explicit requirements (`R-19`, `R-20`, `R-21`), plus US2 independence requirement (`R-22`).
- Rationale: Avoids hidden scope and aligns plan constraints with explicit requirement IDs.
- Alternatives considered:
  - Remove NFR constraints from planning scope.
  - Rejected because performance/scale expectations are part of accepted spec outcomes.

### 15) Coverage and traceability gate enforcement

- Decision: Keep explicit requirements-to-tests matrix for R-01..R-22 and enforce 100% unit coverage gate on in-scope production modules in CI.
- Rationale: Required by constitution and release readiness policy.
- Alternatives considered:
  - Manual coverage checks.
  - Rejected because manual enforcement is non-repeatable.

## Resolved Clarifications

- No unresolved `NEEDS CLARIFICATION` markers remain for Phase 2 design.

# Quickstart: Phase 2 - MkDocs Topic Tree Management

## Purpose

Run a repeatable validation flow for Phase 2 requirements (`R-01..R-22`), including startup tree construction, mutation synchronization, explicit watcher-trigger reconciliation, runtime decoupling compatibility invariants, OS matrix path behavior, and MVP preview compatibility.

## 1) Confirm planning artifacts

1. Verify branch is `001-mkdocs-topic-tree`.
2. Verify feature artifacts exist in `/Users/madushika/projects/authord-mkdocs-plugin/specs/001-mkdocs-topic-tree/`:
- `spec.md`
- `plan.md`
- `research.md`
- `data-model.md`
- `contracts/topic-tree-control.openapi.yaml`
- `contracts/topic-tree-events.asyncapi.yaml`
- `technical-design-notes.md`
- `operational-runbook.md`
- `test-plan-traceability.md`

## 2) Build and quality gate

Run from repository root.

```bash
GRADLE_USER_HOME=$PWD/.gradle-user ./gradlew clean test jacocoTestCoverageVerification --no-daemon
```

Expected: all tests pass and scoped 100% unit coverage gate passes.

### 2.1 Latest canonical coverage-gate evidence (2026-02-15)

Executed commands (same canonical run `20260215T041517Z`, commit `bc04d71e120531c40553f8d41b1be2667c5a97d5`):

```bash
./gradlew scopedCoverageGate --no-daemon --console=plain
GRADLE_USER_HOME=$PWD/.gradle-user ./gradlew clean test jacocoTestCoverageVerification --no-daemon --console=plain
```

Observed terminal result:

```text
BUILD SUCCESSFUL in 32s
37 actionable tasks: 7 executed, 30 up-to-date
...
BUILD SUCCESSFUL in 3m 50s
47 actionable tasks: 47 executed
```

Result: canonical coverage gate evidence is **PASS** for both C1 and SC-005 in run `20260215T041517Z`. Full verbatim command outputs and JaCoCo XML counters are documented in `tests/integration/topic-tree/CoverageGateEvidence.md` with logs `.tmp/gate-runs/20260215T041517Z_cmd1_scopedCoverageGate.log` and `.tmp/gate-runs/20260215T041517Z_cmd2_clean_test_jacoco.log`.

## 3) runIde smoke workflow

```bash
GRADLE_USER_HOME=$PWD/.gradle-user ./gradlew :modules:ui-plugin:runIde --no-daemon
```

In sandbox IDE, validate:
1. Plugin loads and tool window is available.
2. Existing preview workflow still starts and renders (backward compatibility).
3. No plugin exceptions are logged during startup.

### 3.1 Runtime decoupling compatibility checks (`R-15`, `R-15a`)

1. Confirm no hardcoded runtime host/port override is introduced by Phase 2 flows.
2. Confirm preview routing still depends on base URL detected from runtime stdout.
3. If either invariant fails, classify as high-severity compatibility regression and block release.

## 4) Phase 2 startup and fallback scenarios

1. Project A has valid `mkdocs.yml` with nav:
- Open project and verify tree matches nav order/hierarchy.

2. Project B has `mkdocs.yml` without nav:
- Open project and verify deterministic fallback from `docs_dir`.

3. Add extra docs not present in nav:
- Verify they appear in unlinked bucket.

## 5) Mutation and sync scenarios

For active instance, validate each command path:
1. `add` topic under section.
2. `move` topic to new sibling position.
3. `remove` using default delete and explicit nav-only delete.
4. `rename` and verify path/link rewrite scope.
5. `reparent` and `reorder` siblings.

For each mutation verify:
- Tree state updated.
- `mkdocs.yml` updated deterministically.
- File operations applied consistently.
- On forced failure, compensation/rollback outcome is non-destructive.

## 6) Instance model and reconciliation scenarios

1. Confirm root `mkdocs.yml` auto-discovery default instance.
2. Add secondary instance explicitly by config path.
3. Switch active instance and verify operation scoping.
4. Trigger file/config divergence and run reconciliation.
5. Verify nav-first policy, missing-file validation, unlinked bucket updates, and no destructive auto-delete.

### 6.1 Watcher trigger coverage checks (`R-12e`)

1. Verify reconciliation triggers on active config create/update/delete/rename/move.
2. Verify reconciliation triggers on markdown create/update/delete/rename/move inside active `docs_dir`.
3. Verify external move from inside `docs_dir` to outside reports broken-path issue without destructive mutation.
4. Verify external move from outside `docs_dir` to inside creates unlinked bucket entry.

### 6.2 OS matrix verification (`R-17`, `R-17a`)

1. Validate path normalization and comparison behavior on Windows, macOS, and Linux fixtures.
2. Validate case-only rename handling follows platform rules without duplicate-reference false positives.

## 7) Logging and observability checks

1. Verify mutation/reconciliation events include `instanceId`, `commandId`/`transactionId`, and error category where applicable.
2. Verify user-facing notifications map to typed error taxonomy with actionable guidance.

### 7.1 Severity rubric and release decision checks (`R-15a`)

1. Confirm severity classification is recorded in test-plan traceability matrix and compatibility validation report.
2. Confirm unresolved high-severity compatibility regressions fail release gate.

## 8) Release-Gate Evidence Index

| Gate Artifact | Purpose | Status | Evidence Link |
|---------------|---------|--------|---------------|
| `tests/integration/topic-tree/RunIdeDecouplingEvidence.md` | Runtime decoupling compatibility evidence | PENDING | |
| `tests/integration/topic-tree/RunIdePhase2CompatibilityValidation.md` | Compatibility severity decision | PENDING | |
| `tests/integration/topic-tree/StartupTimingEvidence.md` | SC-001 startup timing evidence | PENDING | |
| `tests/integration/topic-tree/PartialSyncZeroStateEvidence.md` | SC-002 partial-sync evidence | PENDING | |
| `tests/integration/topic-tree/SeededValidationEvidence.md` | SC-003 seeded validation evidence | PENDING | |
| `tests/integration/topic-tree/CoverageGateEvidence.md` | SC-005 coverage gate evidence | EXECUTED (PASS - canonical run `20260215T041517Z`) | `tests/integration/topic-tree/CoverageGateEvidence.md` |
| `specs/001-mkdocs-topic-tree/analysis-closure.md` | CR1/CR2/TR1/CR3 closure summary | PENDING | |

## 9) Cycle completion conditions

Phase 2 remains incomplete until all are true:
1. All in-scope tests pass.
2. 100% unit coverage gate passes for scoped production modules.
3. Requirements-to-tests matrix is complete and current.
4. Technical design notes, operational runbook, and test plan artifacts are present and updated.
5. Changelog and migration notes are updated when compatibility impact exists.
6. Compatibility severity rubric has no unresolved high-severity items.

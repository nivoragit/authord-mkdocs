# Operational Runbook: Phase 2 - MkDocs Topic Tree Management

## 1. Purpose

Define operator/developer procedures for running, validating, troubleshooting, and recovering Phase 2 topic-tree synchronization flows.

## 2. Prerequisites

1. JDK 21 and Gradle wrapper available.
2. `uv` available on PATH for runtime bootstrap workflows.
3. MkDocs project with `mkdocs.yml`/`mkdocs.yaml` and `docs_dir` content.

## 3. Standard Commands

### 3.1 Full quality gate

```bash
GRADLE_USER_HOME=$PWD/.gradle-user ./gradlew clean test jacocoTestCoverageVerification --no-daemon
```

### 3.2 Plugin sandbox execution

```bash
GRADLE_USER_HOME=$PWD/.gradle-user ./gradlew :modules:ui-plugin:runIde --no-daemon
```

## 4. Startup and Instance Operations

1. Open project and verify root instance auto-discovery.
2. Add additional instance by explicit config path.
3. Switch instance and verify tree scope changes accordingly.
4. Confirm last selected instance restores after project reopen.

## 5. Mutation Operations (Operational Checks)

For each operation (`add`, `move`, `remove`, `rename`, `reparent`, `reorder`):
1. Confirm action succeeds with expected tree state.
2. Confirm `mkdocs.yml` updates deterministically.
3. Confirm markdown file mutations (where applicable) match nav changes.
4. Confirm rollback/compensation behavior on forced failure.

## 6. Reconciliation Operations

Trigger conditions:
- startup
- relevant file changes
- manual reconcile action

Expected outcomes:
- nav-first hierarchy remains canonical
- missing nav targets reported as issues
- unlinked docs bucket updated
- no automatic destructive deletions

## 7. Error Taxonomy and Operator Response

| Error Code | Meaning | Operator Action |
|------------|---------|-----------------|
| `VALIDATION` | Command/config violates domain rules | Correct input/config, retry |
| `CONFIG_PARSE` | Config cannot be parsed | Fix YAML syntax/structure |
| `CONFIG_WRITE` | Config persistence failed | Check file permissions/locks |
| `FILE_IO` | File mutation failed | Verify path/conflicts and retry |
| `RECONCILIATION` | Reconcile pipeline failed | Run manual reconcile and inspect logs |
| `ORCHESTRATION` | Transaction stage failure | Review rollback status and repair |
| `INSTANCE_SCOPE` | Wrong/missing active instance | Select correct instance and retry |

## 8. Observability

Log/event minimum fields:
- `projectId`
- `instanceId`
- `commandId` or `transactionId`
- error category/severity
- rollback indicator

Primary inspection locations:
- IDE log (`idea.log` in sandbox/system log directory)
- plugin informational notifications in IDE

## 9. Recovery Procedures

1. If mutation fails mid-flight, inspect rollback status.
2. If rollback incomplete, run reconciliation and restore from recovery location where needed.
3. For delete operations, use recovery path to restore nav/file pair.
4. Re-run validation after recovery.

## 10. Cycle Completion Checklist Structure

| Gate | Required Condition | Evidence Artifact | Result |
|------|--------------------|-------------------|--------|
| Docs Current | All required artifacts (`DOC-001..DOC-006`) updated | `analysis-closure.md` | PENDING |
| Tests Green | Full quality gate command passes | CI logs + quickstart record | PENDING |
| Coverage Green | Scoped 100% coverage verification passes | `CoverageGateEvidence.md` | PENDING |
| Compatibility Green | No unresolved high-severity compatibility regressions | `RunIdePhase2CompatibilityValidation.md` | PENDING |
| Checklist Execution | `phase2-gates` and `phase2-compliance-hard-gates` executed with evidence | `analysis-closure.md` | PENDING |

## 11. Completion Gate Rule

Phase 2 cycle remains incomplete until all checklist gates above are PASS.
If compatibility impact exists, changelog and migration notes must also be updated.

## 12. Current Evidence Pointers (2026-02-15)

1. Canonical coverage-gate run (SC-005 / R-18):
- `tests/integration/topic-tree/CoverageGateEvidence.md`
- `specs/001-mkdocs-topic-tree/quickstart.md` section `2.1`
- `specs/001-mkdocs-topic-tree/test-plan-traceability.md` section `4.1`

2. Success-criteria verification artifacts:
- `tests/integration/topic-tree/StartupTimingEvidence.md`
- `tests/integration/topic-tree/PartialSyncZeroStateEvidence.md`
- `tests/integration/topic-tree/SeededValidationEvidence.md`
- `tests/integration/topic-tree/CompatibilityRegressionEvidence.md`
- `tests/integration/topic-tree/MutationResponsivenessEvidence.md`
- `tests/integration/topic-tree/InstanceScaleEvidence.md`

3. Runtime decoupling and rollback/recovery-related evidence:
- `tests/integration/topic-tree/RunIdeDecouplingEvidence.md`
- `specs/001-mkdocs-topic-tree/analysis-closure.md`
- `.tmp/gate-runs/20260215T050001Z_t119_runtime_decoupling.log`

# Test Plan and Requirements-to-Tests Traceability: Phase 2

## 1. Test Objectives

1. Validate functional requirements `R-01..R-22` and constraints `NAV-001..NAV-004`.
2. Preserve backward compatibility for MVP preview workflows (`R-15`, `R-15a`).
3. Enforce 100% scoped unit coverage gate (`R-18`, `SC-005`).
4. Maintain measurable verification for success criteria `SC-001..SC-007`.

## 2. Test Levels

1. Unit tests:
- Domain core entities, command invariants, and path policy.
- YAML/file gateway deterministic behavior and link rewrite scope.
- Atomic orchestration rollback/compensation and validation taxonomy.

2. Integration tests:
- Startup/reconciliation flows and watcher-trigger behavior (`R-12e`).
- Multi-instance scoping and compatibility regression criteria.

3. Smoke tests:
- `runIde` startup/preview compatibility and runtime-decoupling invariants.

## 3. Execution Commands

### 3.1 Full quality gate

```bash
GRADLE_USER_HOME=$PWD/.gradle-user ./gradlew clean test jacocoTestCoverageVerification --no-daemon
```

### 3.2 Focused module runs (example)

```bash
GRADLE_USER_HOME=$PWD/.gradle-user ./gradlew :modules:core-domain:test :modules:ui-plugin:test --no-daemon
```

## 4. Scoped Coverage Gate Definition (`R-18`)

In-scope production modules for this cycle:
- `modules/core-domain`
- `modules/extension-ports`
- `modules/mkdocs-runtime-adapter`
- `modules/ui-plugin`

Gate policy:
1. CI MUST fail when scoped coverage is below 100%.
2. Coverage scope MUST remain explicit in module and root build configuration.
3. Release gate uses full quality command output as source-of-truth evidence.

### 4.1 Scoped Coverage Evidence Pointers (`SC-005`)

- Root aggregated gate: `build.gradle.kts` (`scopedCoverageModules`, `scopedCoverageGate`).
- Module thresholds (100% line coverage): `modules/core-domain/build.gradle.kts`, `modules/extension-ports/build.gradle.kts`, `modules/mkdocs-runtime-adapter/build.gradle.kts`, `modules/ui-plugin/build.gradle.kts`.
- CI fail-on-threshold workflows: `.github/workflows/ci.yml` and `.github/workflows/quality.yml`.
- Policy proof tests: `modules/core-domain/src/test/kotlin/com/authord/mkdocs/core/quality/ScopedCoveragePolicyTest.kt`.
- Execution evidence artifact: `tests/integration/topic-tree/CoverageGateEvidence.md` (updated during `T116`/`T129`).
- Direct command-output evidence pointer: `tests/integration/topic-tree/CoverageGateEvidence.md` ("Command 1: scopedCoverageGate", "Command 2: Full Quality Gate").
- Release/readiness gate execution pointer: `specs/001-mkdocs-topic-tree/quickstart.md` ("2.1 Latest canonical coverage-gate evidence (2026-02-15)").
- Canonical latest run context: `20260215T041517Z` on commit `bc04d71e120531c40553f8d41b1be2667c5a97d5` (C1 = PASS, SC-005 = PASS; logs: `.tmp/gate-runs/20260215T041517Z_cmd1_scopedCoverageGate.log`, `.tmp/gate-runs/20260215T041517Z_cmd2_clean_test_jacoco.log`).

## 5. Compatibility Severity Rubric Source-of-Truth (`R-15a`)

Severity classification sources:
- This file (traceability matrix + rubric section).
- `tests/integration/topic-tree/RunIdePhase2CompatibilityValidation.md`.

High-severity compatibility regression (release blocker) includes at least one:
1. Preview cannot start from existing MVP entrypoints.
2. Preview unreachable/blank due to runtime URL detection failure.
3. Plugin-attributed IDE crash/freeze >5 seconds in preview workflow.
4. Destructive data-loss behavior in compatibility path.

Gate rule:
- Any unresolved high-severity item => release gate FAIL.

## 6. Requirements-to-Tests Traceability Matrix

| Requirement ID | Unit Test Suite(s) | Integration/Smoke Suite(s) | Verification Artifact | Status |
|----------------|--------------------|-----------------------------|-----------------------|--------|
| R-01 | `TopicTreeStartupLoaderTest` | `TopicTreeStartupIntegrationTest` | `StartupReconciliationSmokeValidation.md` | Planned |
| R-02 | `TopicTreeFallbackBuilderTest` | `TopicTreeStartupIntegrationTest` | `StartupReconciliationSmokeValidation.md` | Planned |
| R-03 | `UnlinkedFilesBucketServiceTest` | `TopicTreeStartupIntegrationTest` | `StartupReconciliationSmokeValidation.md` | Planned |
| R-04 | `TopicTreeAggregateMutationTest` | `MutationAndDragDropIntegrationTest` | `MutationAndDragDropSmokeValidation.md` | Planned |
| R-04a | `AddChildTransformPolicyTest` | `MutationAndDragDropIntegrationTest` | `MutationAndDragDropSmokeValidation.md` | Planned |
| R-05 | `AddExistingFileCommandTest` | `MutationAndDragDropIntegrationTest` | `MutationAndDragDropSmokeValidation.md` | Planned |
| R-06 | `ExternalLinkCommandTest` | `MutationAndDragDropIntegrationTest` | `MutationAndDragDropSmokeValidation.md` | Planned |
| R-07 | `InstanceScopeIsolationTest` | `MultiInstanceReconciliationTest` | `MultiInstanceCompatibilitySmokeValidation.md` | Planned |
| R-07a | `InstanceRegistryDiscoveryTest` | `MultiInstanceReconciliationTest` | `MultiInstanceCompatibilitySmokeValidation.md` | Planned |
| R-07b | `InstanceRegistryDiscoveryTest` | `MultiInstanceReconciliationTest` | `MultiInstanceCompatibilitySmokeValidation.md` | Planned |
| R-07c | `InstanceSelectionPersistenceTest` | `MultiInstanceReconciliationTest` | `MultiInstanceCompatibilitySmokeValidation.md` | Planned |
| R-08 | `MkDocsConfigGatewayContractTest` | `TopicMutationIntegrationTest` | `MutationAndDragDropSmokeValidation.md` | Planned |
| R-09 | `DocsFileGatewayContractTest` | `TopicMutationIntegrationTest` | `MutationAndDragDropSmokeValidation.md` | Planned |
| R-09a | `DeleteSemanticsPolicyTest` | `TopicMutationIntegrationTest` | `MutationAndDragDropSmokeValidation.md` | Planned |
| R-09b | `DeleteSemanticsPolicyTest` | `TopicMutationIntegrationTest` | `MutationAndDragDropSmokeValidation.md` | Planned |
| R-09c | `MarkdownLinkRewriteScopeTest` | `TopicMutationIntegrationTest` | `MutationAndDragDropSmokeValidation.md` | Planned |
| R-10 | `TopicTreeValidationServiceTest` | `SeededValidationDatasetIntegrationTest` | `SeededValidationEvidence.md` | Executed (PASS - run `20260215T045617Z`) |
| R-11 | `TreeSyncOrchestratorAtomicityTest` | `PartialSyncZeroStateIntegrationTest` | `PartialSyncZeroStateEvidence.md` | Executed (PASS - run `20260215T045617Z`) |
| R-11a | `DeleteSemanticsPolicyTest` | `TopicMutationIntegrationTest` | `MutationAndDragDropSmokeValidation.md` | Planned |
| R-12 | `StartupReconciliationPolicyTest` | `TopicTreeStartupIntegrationTest` | `StartupReconciliationSmokeValidation.md` | Planned |
| R-12a | `StartupReconciliationPolicyTest` | `TopicTreeStartupIntegrationTest` | `StartupReconciliationSmokeValidation.md` | Planned |
| R-12b | `StartupReconciliationPolicyTest` | `TopicTreeStartupIntegrationTest` | `StartupReconciliationSmokeValidation.md` | Planned |
| R-12c | `UnlinkedFilesBucketServiceTest` | `TopicTreeStartupIntegrationTest` | `StartupReconciliationSmokeValidation.md` | Planned |
| R-12d | `StartupReconciliationPolicyTest` | `TopicTreeStartupIntegrationTest` | `StartupReconciliationSmokeValidation.md` | Planned |
| R-12e | `TopicTreeWatcherTriggerMatrixTest`, `ExternalMoveHandlingTest` | `TopicTreeStartupIntegrationTest` | `StartupReconciliationSmokeValidation.md` | Planned |
| R-13 | `MkDocsConfigGatewayContractTest` | `TopicTreeStartupIntegrationTest` | `StartupReconciliationSmokeValidation.md` | Planned |
| R-14 | `MkDocsYamlGatewayRoundTripTest` | `TopicMutationIntegrationTest` | `MutationAndDragDropSmokeValidation.md` | Planned |
| R-15 | `PreviewCompatibilityCriteriaTest`, `RuntimeDecouplingPolicyTest` | `RunIdePhase2CompatibilityValidation.md` | `RunIdeDecouplingEvidence.md` | Executed (PASS - run `20260215T052141Z`; runIde compatibility decision captured in `T130`) |
| R-15a | `BaseUrlDetectorStdoutTest`, `PreviewCompatibilityCriteriaTest` | `RunIdePhase2CompatibilityValidation.md` | `CompatibilityRegressionEvidence.md` | Executed (PASS - run `20260215T052141Z`; unresolved high-severity regressions = 0) |
| R-16 | `TopicTreeApplicationServiceContractTest`, `TopicTreeUiServiceContractTest` | `RunIdePhase2CompatibilityValidation.md` | `public-api-usage.md` | Executed (PASS - contracts + usage guidance + runIde compatibility validation captured in `T130`) |
| R-17 | `PathNormalizationPolicyTest` | `PathCaseMatrixIntegrationTest` | `PathCaseMatrixIntegrationTest` output | Planned |
| R-17a | `PathNormalizationPolicyTest` | `PathCaseMatrixIntegrationTest` | `PathCaseMatrixIntegrationTest` output | Planned |
| R-18 | `RequirementCoveragePolicyTest`, `ScopedCoveragePolicyTest` | `CI quality workflow` | `CoverageGateEvidence.md` | Executed (PASS - canonical run `20260215T041517Z`: both required gate commands succeeded) |
| R-19 | `StartupReconciliationPerformanceTest` | `StartupTimingEvidence.md` | `StartupTimingEvidence.md` | Executed (PASS - run `20260215T045617Z`) |
| R-20 | `MutationFeedbackPerformanceTest` | `MutationResponsivenessEvidence.md` | `MutationResponsivenessEvidence.md` | Executed (PASS - run `20260215T045617Z`) |
| R-21 | `InstanceScaleProfileIntegrationTest` | `InstanceScaleProfileIntegrationTest` | `InstanceScaleEvidence.md` | Executed (PASS - run `20260215T045617Z`) |
| R-22 | `MutationFlowIndependenceTest` | `MutationAndDragDropIntegrationTest` | `MutationAndDragDropSmokeValidation.md` | Planned |

## 7. Success Criteria Verification Rows (`SC-001..SC-007`)

| Success Criteria ID | Verification Tasks | Verification Artifact | Verification Method | Status |
|---------------------|--------------------|-----------------------|---------------------|--------|
| SC-001 | T111, T035, T112 | `tests/integration/topic-tree/StartupTimingEvidence.md` | p95 startup timing measurement | Executed (PASS - run `20260215T045617Z`) |
| SC-002 | T111, T057, T113 | `tests/integration/topic-tree/PartialSyncZeroStateEvidence.md` | Partial-sync zero-state validation | Executed (PASS - run `20260215T045617Z`) |
| SC-003 | T111, T003, T036, T114 | `tests/integration/topic-tree/SeededValidationEvidence.md` | Seeded dataset detection checks | Executed (PASS - run `20260215T045617Z`) |
| SC-004 | T111, T006, T077, T086, T087, T115, T130 | `tests/integration/topic-tree/CompatibilityRegressionEvidence.md`; `tests/integration/topic-tree/RunIdePhase2CompatibilityValidation.md` | Compatibility severity rubric gate | Executed (PASS - run `20260215T052141Z`; runIde compatibility decision captured) |
| SC-005 | T111, T103, T104, T105, T106, T107, T108, T109, T110, T116, T129 | `tests/integration/topic-tree/CoverageGateEvidence.md`; `specs/001-mkdocs-topic-tree/quickstart.md` | CI/build coverage gate verification (verbatim command output + JaCoCo XML counters + canonical gate logs) | Executed (PASS - canonical run `20260215T041517Z`; pointers synchronized by T111/T116/T129) |
| SC-006 | T058, T117 | `tests/integration/topic-tree/MutationResponsivenessEvidence.md` | p95 mutation feedback timing | Executed (PASS - run `20260215T045617Z`) |
| SC-007 | T081, T118 | `tests/integration/topic-tree/InstanceScaleEvidence.md` | Multi-instance scale profile validation | Executed (PASS - run `20260215T045617Z`) |

### 7.1 T111 Closure Pointers (`SC-001..SC-005`)

- Canonical gate run context and PASS result source: `specs/001-mkdocs-topic-tree/quickstart.md` section "2.1 Latest canonical coverage-gate evidence (2026-02-15)" (run `20260215T041517Z`).
- SC-005 verbatim command-output and JaCoCo counter source: `tests/integration/topic-tree/CoverageGateEvidence.md`.
- Canonical command logs for SC-005: `.tmp/gate-runs/20260215T041517Z_cmd1_scopedCoverageGate.log` and `.tmp/gate-runs/20260215T041517Z_cmd2_clean_test_jacoco.log`.
- SC-001/SC-002/SC-003 evidence artifacts are published and marked executed (run `20260215T045617Z`).
- SC-004 criteria and runIde compatibility gate evidence are published in `tests/integration/topic-tree/CompatibilityRegressionEvidence.md` and `tests/integration/topic-tree/RunIdePhase2CompatibilityValidation.md` (run `20260215T052141Z`).

## 8. Seeded Fixtures

- `tests/fixtures/topic-tree/validation-seeded-cases.yaml`
- `tests/fixtures/topic-tree/performance-datasets.yaml`

## 9. Release Readiness Criteria

Phase 2 remains incomplete until all are true:
1. Requirements and success criteria rows are linked to executed evidence.
2. 100% scoped coverage gate is green in CI.
3. Unresolved high-severity compatibility regressions are zero.
4. Required documentation artifacts are current.

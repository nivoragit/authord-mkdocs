# Tasks: Phase 2 - MkDocs Topic Tree Management

**Input**: Design documents from `/Users/madushika/projects/authord-mkdocs-plugin/specs/001-mkdocs-topic-tree/`
**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/`, `quickstart.md`

**Tests**: Unit, integration, smoke, and policy tests are REQUIRED for this cycle. 100% scoped unit coverage is a hard gate.

**Organization**: Tasks are dependency-ordered and grouped by milestone and user story.
- A) Domain + YAML roundtrip
- B) File gateway + atomic orchestrator
- C) UI actions + drag/drop
- D) Startup rebuild + watchers + reconciliation
- E) Validation + unlinked bucket + instances
- F) Tests, docs, coverage, release gate

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Prepare traceability artifacts, fixtures, and release-gate scaffolding.

- [X] T001 Initialize requirements-to-tests traceability matrix structure in `specs/001-mkdocs-topic-tree/test-plan-traceability.md` (Req: DOC-004)
- [X] T002 Add `SC-001..SC-007` traceability rows with verification columns in `specs/001-mkdocs-topic-tree/test-plan-traceability.md` (Req: SC-001, SC-002, SC-003, SC-004, SC-005, SC-006, SC-007, DOC-004)
- [X] T003 [P] Define seeded validation dataset catalog in `tests/fixtures/topic-tree/validation-seeded-cases.yaml` (Req: R-10, SC-003)
- [X] T004 [P] Define startup/mutation benchmark fixture catalog in `tests/fixtures/topic-tree/performance-datasets.yaml` (Req: R-19, R-20, R-21, SC-001, SC-006, SC-007)
- [X] T005 [P] Create runIde runtime-decoupling evidence template in `tests/integration/topic-tree/RunIdeDecouplingEvidence.md` (Req: R-15, R-15a, DOC-004)
- [X] T006 Define compatibility severity rubric source-of-truth section in `specs/001-mkdocs-topic-tree/test-plan-traceability.md` (Req: R-15a, SC-004, DOC-004)
- [X] T007 [P] Add cycle-completion checklist structure to runbook in `specs/001-mkdocs-topic-tree/operational-runbook.md` (Req: DOC-003, DOC-005)
- [X] T008 Create requirement-coverage policy test scaffold in `modules/core-domain/src/test/kotlin/com/authord/mkdocs/core/quality/RequirementCoveragePolicyTest.kt` (Req: R-18, DOC-004)
- [X] T009 [P] Create release-gate evidence index in `specs/001-mkdocs-topic-tree/quickstart.md` (Req: DOC-004, DOC-005)
- [X] T010 Create analysis-closure checklist skeleton for CR1/CR2/TR1/CR3 in `specs/001-mkdocs-topic-tree/analysis-closure.md` (Req: DOC-004)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Define Phase 2 public contracts and foundational architecture before story implementation.

**⚠️ CRITICAL**: No user story implementation starts before this phase is complete.

### Foundational Contract Tests

- [X] T011 [P] Add `MkDocsConfigGateway` public-contract test in `modules/extension-ports/src/test/kotlin/com/authord/mkdocs/ports/topic/MkDocsConfigGatewayContractTest.kt` (Req: R-08, R-13, R-14, NAV-001, NAV-003, PI-003)
- [X] T012 [P] Add `DocsFileGateway` public-contract test in `modules/extension-ports/src/test/kotlin/com/authord/mkdocs/ports/topic/DocsFileGatewayContractTest.kt` (Req: R-09, R-09a, R-09b, R-09c, PI-003)
- [X] T013 [P] Add `TreeSyncOrchestrator` public-contract test in `modules/extension-ports/src/test/kotlin/com/authord/mkdocs/ports/topic/TreeSyncOrchestratorContractTest.kt` (Req: R-11, R-11a, NAV-002, PI-003)
- [X] T014 [P] Add `InstanceRegistryPort` public-contract test in `modules/extension-ports/src/test/kotlin/com/authord/mkdocs/ports/topic/InstanceRegistryPortContractTest.kt` (Req: R-07, R-07a, R-07b, R-07c, PI-003)
- [X] T015 [P] Add application-service API contract test in `modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/intellij/TopicTreeApplicationServiceContractTest.kt` (Req: R-22, PI-001, PI-003)
- [X] T016 [P] Add UI-facing service API contract test in `modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/intellij/TopicTreeUiServiceContractTest.kt` (Req: R-04, R-05, R-06, PI-001, PI-003)

### Foundational Interfaces and Wiring

- [X] T017 Define `MkDocsConfigGateway` interface in `modules/extension-ports/src/main/kotlin/com/authord/mkdocs/ports/topic/MkDocsConfigGateway.kt` (Req: R-08, R-13, R-14, NAV-001, NAV-003)
- [X] T018 Define `DocsFileGateway` interface in `modules/extension-ports/src/main/kotlin/com/authord/mkdocs/ports/topic/DocsFileGateway.kt` (Req: R-09, R-09a, R-09b, R-09c)
- [X] T019 Define `TreeSyncOrchestrator` interface in `modules/extension-ports/src/main/kotlin/com/authord/mkdocs/ports/topic/TreeSyncOrchestrator.kt` (Req: R-11, R-11a, NAV-002)
- [X] T020 Define `InstanceRegistryPort` interface in `modules/extension-ports/src/main/kotlin/com/authord/mkdocs/ports/topic/InstanceRegistryPort.kt` (Req: R-07, R-07a, R-07b, R-07c)
- [X] T021 Define orchestration/application service API in `modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/intellij/TopicTreeApplicationService.kt` (Req: R-22, PI-001, PI-003)
- [X] T022 Define UI-facing topic-tree service API in `modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/intellij/TopicTreeUiService.kt` (Req: R-04, R-05, R-06, PI-001, PI-003)
- [X] T023 Define sync error taxonomy types in `modules/extension-ports/src/main/kotlin/com/authord/mkdocs/ports/topic/TopicSyncError.kt` (Req: R-10, R-11, R-15a)
- [X] T024 Define sync observability event model in `modules/extension-ports/src/main/kotlin/com/authord/mkdocs/ports/topic/TopicSyncEvent.kt` (Req: R-10, R-11, R-12, R-15a)
- [X] T025 Wire foundational ports/services in `modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/PluginCompositionRoot.kt` (Req: R-15, R-16, PI-002)
- [X] T026 Update public interface version registry in `modules/extension-ports/src/main/kotlin/com/authord/mkdocs/ports/TopicTreePort.kt` (Req: R-16, PI-001, PI-003)

**Checkpoint**: Core contracts and architecture boundaries are in place.

---

## Phase 3: User Story 1 - Reconcile Project Navigation at Startup (Priority: P1) 🎯 MVP

**Milestones**: A + D + E

**Goal**: Build topic tree from canonical `mkdocs.yml` nav (or deterministic fallback), reconcile non-destructively, and track validation/unlinked files.

**Independent Test**: Open nav-present and nav-missing projects, trigger relevant file/config events, verify deterministic tree + unlinked bucket + validation outputs.

### Tests for User Story 1

- [X] T027 [P] [US1] Add startup nav-loading tests in `modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/intellij/TopicTreeStartupLoaderTest.kt` (Req: R-01, R-13, NAV-001)
- [X] T028 [P] [US1] Add deterministic docs_dir fallback tests in `modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/intellij/TopicTreeFallbackBuilderTest.kt` (Req: R-02, R-14, NAV-003)
- [X] T029 [P] [US1] Add unlinked-bucket derivation tests in `modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/intellij/UnlinkedFilesBucketServiceTest.kt` (Req: R-03, R-12c)
- [X] T030 [P] [US1] Add reconciliation policy tests in `modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/intellij/StartupReconciliationPolicyTest.kt` (Req: R-12, R-12a, R-12b, R-12d, NAV-004)
- [X] T031 [P] [US1] Add watcher trigger matrix tests in `modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/intellij/TopicTreeWatcherTriggerMatrixTest.kt` (Req: R-12e, NAV-004)
- [X] T032 [P] [US1] Add external move/rename behavior tests in `modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/intellij/ExternalMoveHandlingTest.kt` (Req: R-12e, R-11)
- [X] T033 [P] [US1] Add validation detection tests for broken/duplicate/malformed entries in `modules/core-domain/src/test/kotlin/com/authord/mkdocs/core/topic/TopicTreeValidationServiceTest.kt` (Req: R-10)
- [X] T034 [P] [US1] Add OS path normalization/case behavior tests in `modules/core-domain/src/test/kotlin/com/authord/mkdocs/core/topic/PathNormalizationPolicyTest.kt` (Req: R-17, R-17a)
- [X] T035 [P] [US1] Add startup latency threshold tests (p95 <=2s) in `modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/intellij/StartupReconciliationPerformanceTest.kt` (Req: R-19, SC-001)
- [X] T036 [P] [US1] Add seeded validation-dataset verification tests in `tests/integration/topic-tree/SeededValidationDatasetIntegrationTest.kt` (Req: R-10, SC-003)

### Implementation for User Story 1

- [X] T037 [US1] Implement nav-first startup tree loader in `modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/intellij/TopicTreeStartupLoader.kt` (Req: R-01, R-13, NAV-001)
- [X] T038 [US1] Implement deterministic fallback tree builder in `modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/intellij/TopicTreeFallbackBuilder.kt` (Req: R-02, R-14, NAV-003)
- [X] T039 [US1] Implement unlinked files bucket service in `modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/intellij/UnlinkedFilesBucketService.kt` (Req: R-03, R-12c)
- [X] T040 [US1] Implement startup reconciliation coordinator with non-destructive policy in `modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/intellij/StartupReconciliationCoordinator.kt` (Req: R-12, R-12a, R-12b, R-12d, NAV-004)
- [X] T041 [US1] Implement watcher trigger coordinator for config/docs create-update-delete-rename-move events in `modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/intellij/TopicTreeWatcherCoordinator.kt` (Req: R-12e, NAV-004)
- [X] T042 [US1] Implement external move handling rules for docs_dir boundary transitions in `modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/intellij/TopicTreeWatcherCoordinator.kt` (Req: R-12e, R-11)
- [X] T043 [US1] Implement validation issue service in `modules/core-domain/src/main/kotlin/com/authord/mkdocs/core/topic/TopicTreeValidationService.kt` (Req: R-10)
- [X] T044 [US1] Implement path normalization and OS-specific case comparison policy in `modules/core-domain/src/main/kotlin/com/authord/mkdocs/core/topic/PathPolicy.kt` (Req: R-17, R-17a)
- [X] T045 [US1] Integrate startup/reconciliation into tool window lifecycle in `modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/intellij/MkdocsToolWindowFactory.kt` (Req: R-01, R-02, R-03, R-12)
- [X] T046 [US1] Emit structured reconciliation observability events in `modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/intellij/TopicTreeObservability.kt` (Req: R-10, R-12, NAV-004)
- [X] T047 [US1] Record startup/reconciliation smoke procedure and evidence checklist in `tests/integration/topic-tree/StartupReconciliationSmokeValidation.md` (Req: R-01, R-02, R-03, R-12, DOC-004)

**Checkpoint**: US1 is independently functional and testable.

---

## Phase 4: User Story 2 - Perform Topic Mutations with MkDocs Sync (Priority: P1)

**Milestones**: A + B + C

**Goal**: Execute command-based topic mutations with deterministic YAML roundtrip, file sync, and atomic rollback/compensation.

**Independent Test**: Execute each mutation type and verify tree/nav/file consistency plus rollback behavior on injected failures.

### Tests for User Story 2

- [X] T048 [P] [US2] Add domain mutation invariant tests for add/move/remove/rename/reparent/reorder in `modules/core-domain/src/test/kotlin/com/authord/mkdocs/core/topic/TopicTreeAggregateMutationTest.kt` (Req: R-04)
- [X] T049 [P] [US2] Add add-child page-to-section transform tests in `modules/core-domain/src/test/kotlin/com/authord/mkdocs/core/topic/AddChildTransformPolicyTest.kt` (Req: R-04a)
- [X] T050 [P] [US2] Add add-existing-file command tests in `modules/core-domain/src/test/kotlin/com/authord/mkdocs/core/topic/AddExistingFileCommandTest.kt` (Req: R-05)
- [X] T051 [P] [US2] Add external-link command tests in `modules/core-domain/src/test/kotlin/com/authord/mkdocs/core/topic/ExternalLinkCommandTest.kt` (Req: R-06)
- [X] T052 [P] [US2] Add deterministic YAML roundtrip tests in `modules/mkdocs-runtime-adapter/src/test/kotlin/com/authord/mkdocs/runtime/MkDocsYamlGatewayRoundTripTest.kt` (Req: R-08, R-13, R-14, NAV-001, NAV-003)
- [X] T053 [P] [US2] Add file mutation sync tests for create/delete/rename/move in `modules/mkdocs-runtime-adapter/src/test/kotlin/com/authord/mkdocs/runtime/DocsFileGatewayAdapterMutationTest.kt` (Req: R-09)
- [X] T054 [P] [US2] Add delete semantics tests (default recovery + nav-only) in `modules/mkdocs-runtime-adapter/src/test/kotlin/com/authord/mkdocs/runtime/DeleteSemanticsPolicyTest.kt` (Req: R-09a, R-09b, R-11a)
- [X] T055 [P] [US2] Add markdown-link rewrite scope tests in `modules/mkdocs-runtime-adapter/src/test/kotlin/com/authord/mkdocs/runtime/MarkdownLinkRewriteScopeTest.kt` (Req: R-09c)
- [X] T056 [P] [US2] Add atomic apply/rollback orchestrator tests in `modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/intellij/TreeSyncOrchestratorAtomicityTest.kt` (Req: R-11, NAV-002)
- [X] T057 [P] [US2] Add partial-sync zero-state regression test in `tests/integration/topic-tree/PartialSyncZeroStateIntegrationTest.kt` (Req: R-11, SC-002)
- [X] T058 [P] [US2] Add mutation feedback latency tests (p95 <=300ms) in `modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/intellij/MutationFeedbackPerformanceTest.kt` (Req: R-20, SC-006)
- [X] T059 [P] [US2] Add US2 independence tests using preloaded tree context in `modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/intellij/MutationFlowIndependenceTest.kt` (Req: R-22)

### Implementation for User Story 2

- [X] T060 [US2] Implement command DTOs for add/move/remove/rename/reparent/reorder/add-child/existing-file/external-link in `modules/extension-ports/src/main/kotlin/com/authord/mkdocs/ports/topic/TopicTreeCommandDtos.kt` (Req: R-04, R-04a, R-05, R-06, PI-003)
- [X] T061 [US2] Implement domain aggregate mutation handlers in `modules/core-domain/src/main/kotlin/com/authord/mkdocs/core/topic/TopicTreeAggregate.kt` (Req: R-04, R-04a)
- [X] T062 [US2] Implement deterministic parse/serialize gateway in `modules/mkdocs-runtime-adapter/src/main/kotlin/com/authord/mkdocs/runtime/MkDocsYamlGateway.kt` (Req: R-08, R-13, R-14, NAV-001, NAV-003)
- [X] T063 [US2] Implement docs file gateway adapter with recoverable delete and nav-only behavior in `modules/mkdocs-runtime-adapter/src/main/kotlin/com/authord/mkdocs/runtime/DocsFileGatewayAdapter.kt` (Req: R-09, R-09a, R-09b, R-11a)
- [X] T064 [US2] Implement scoped markdown-link rewrite service in `modules/mkdocs-runtime-adapter/src/main/kotlin/com/authord/mkdocs/runtime/MarkdownLinkRewriter.kt` (Req: R-09c)
- [X] T065 [US2] Implement tree sync orchestrator apply/verify/commit/rollback flow in `modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/intellij/TopicTreeSyncOrchestratorService.kt` (Req: R-11, NAV-002)
- [X] T066 [US2] Wire mutation orchestration application service in `modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/intellij/TopicTreeApplicationService.kt` (Req: R-08, R-09, R-11, R-22, PI-001)
- [X] T067 [US2] Implement UI-facing command dispatch service in `modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/intellij/TopicTreeUiService.kt` (Req: R-04, R-05, R-06, PI-001)
- [X] T068 [US2] Implement topic tree action controller in `modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/intellij/TopicTreeActionController.kt` (Req: R-04, R-05, R-06)
- [X] T069 [US2] Implement drag/drop reorder and reparent controller in `modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/intellij/TopicTreeDragDropController.kt` (Req: R-04)
- [X] T070 [US2] Integrate action and drag/drop controllers into tool window in `modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/intellij/MkdocsToolWindowFactory.kt` (Req: R-04, R-08, R-09)
- [X] T071 [US2] Implement non-destructive failure recovery messaging in `modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/intellij/TopicTreeFailureRecoveryPresenter.kt` (Req: R-11, R-11a)
- [X] T072 [US2] Record mutation/drag-drop smoke evidence in `tests/integration/topic-tree/MutationAndDragDropSmokeValidation.md` (Req: R-04, R-08, R-09, R-11, DOC-004)

**Checkpoint**: US2 is independently functional and testable.

---

## Phase 5: User Story 3 - Manage Multi-Instance Configurations Safely (Priority: P2)

**Milestones**: E + F

**Goal**: Support multiple MkDocs instances with strict scope isolation and no regressions in MVP preview/runtime behavior.

**Independent Test**: Add/select instances, execute scoped mutations, verify no cross-instance leakage, and validate preview compatibility criteria.

### Tests for User Story 3

- [X] T073 [P] [US3] Add default discovery + explicit add-instance tests in `modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/intellij/InstanceRegistryDiscoveryTest.kt` (Req: R-07a, R-07b)
- [X] T074 [P] [US3] Add instance selection persistence tests in `modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/intellij/InstanceSelectionPersistenceTest.kt` (Req: R-07c)
- [X] T075 [P] [US3] Add instance-scoped mutation isolation tests in `modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/intellij/InstanceScopeIsolationTest.kt` (Req: R-07, R-21)
- [X] T076 [P] [US3] Add multi-instance reconciliation tests in `modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/intellij/MultiInstanceReconciliationTest.kt` (Req: R-12, R-12a, R-12c, R-21)
- [X] T077 [P] [US3] Add preview compatibility regression criteria tests in `modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/intellij/PreviewCompatibilityCriteriaTest.kt` (Req: R-15, R-15a, SC-004)
- [X] T078 [P] [US3] Add runtime decoupling regression tests for host/port hardcoding in `modules/mkdocs-runtime-adapter/src/test/kotlin/com/authord/mkdocs/runtime/RuntimeDecouplingPolicyTest.kt` (Req: R-15)
- [X] T079 [P] [US3] Add runtime base URL detection from stdout regression tests in `modules/mkdocs-runtime-adapter/src/test/kotlin/com/authord/mkdocs/runtime/BaseUrlDetectorStdoutTest.kt` (Req: R-15, R-15a)
- [X] T080 [P] [US3] Add OS-matrix integration tests for case sensitivity behavior in `tests/integration/topic-tree/PathCaseMatrixIntegrationTest.kt` (Req: R-17, R-17a)
- [X] T081 [P] [US3] Add scale-profile tests (5 instances/10k docs synthetic) in `tests/integration/topic-tree/InstanceScaleProfileIntegrationTest.kt` (Req: R-21, SC-007)

### Implementation for User Story 3

- [X] T082 [US3] Implement instance registry service in `modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/intellij/InstanceRegistryService.kt` (Req: R-07, R-07a, R-07b, R-07c)
- [X] T083 [US3] Implement instance scope guard in `modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/intellij/TopicTreeScopeGuard.kt` (Req: R-07, R-21)
- [X] T084 [US3] Implement instance switching coordinator in `modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/intellij/InstanceSwitchCoordinator.kt` (Req: R-07, R-07c)
- [X] T085 [US3] Implement multi-instance reconciliation coordinator in `modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/intellij/MultiInstanceReconciliationCoordinator.kt` (Req: R-12, R-12a, R-12c)
- [X] T086 [US3] Implement compatibility severity classifier service in `modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/intellij/CompatibilitySeverityClassifier.kt` (Req: R-15a, SC-004)
- [X] T087 [US3] Implement release-blocking compatibility gate evaluator in `modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/intellij/CompatibilityReleaseGateService.kt` (Req: R-15a, SC-004)
- [X] T088 [US3] Preserve runtime-decoupling invariants in runtime adapter flow in `modules/mkdocs-runtime-adapter/src/main/kotlin/com/authord/mkdocs/runtime/MkdocsProcessManager.kt` (Req: R-15)
- [X] T089 [US3] Preserve stdout base URL detection flow in `modules/mkdocs-runtime-adapter/src/main/kotlin/com/authord/mkdocs/runtime/BaseUrlDetector.kt` (Req: R-15, R-15a)
- [X] T090 [US3] Wire instance selection UI interactions in `modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/intellij/MkdocsToolWindowFactory.kt` (Req: R-07, R-07c)
- [X] T091 [US3] Verify preview entrypoint compatibility wiring in `modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/intellij/StartMkdocsAction.kt` (Req: R-15, PI-002)
- [X] T092 [US3] Record multi-instance + compatibility smoke evidence in `tests/integration/topic-tree/MultiInstanceCompatibilitySmokeValidation.md` (Req: R-07, R-15, R-21, DOC-004)

**Checkpoint**: US3 is independently functional and testable.

---

## Phase 6: Polish, Compliance, and Release Gates (Milestone F)

**Purpose**: Explicitly close CR1, CR2, TR1, and CR3 with documentation, coverage, and release evidence.

### CR1 Closure: Documentation + KDoc/Comments

- [X] T093 Close CR1 by documenting `MkDocsConfigGateway` API (purpose, inputs, outputs, errors, usage examples) in `modules/extension-ports/src/main/kotlin/com/authord/mkdocs/ports/topic/MkDocsConfigGateway.kt` (Req: DOC-006, R-16, PI-003)
- [X] T094 Close CR1 by documenting `DocsFileGateway` API (purpose, inputs, outputs, errors, usage examples) in `modules/extension-ports/src/main/kotlin/com/authord/mkdocs/ports/topic/DocsFileGateway.kt` (Req: DOC-006, R-16, PI-003)
- [X] T095 Close CR1 by documenting `TreeSyncOrchestrator` API (purpose, inputs, outputs, errors, usage examples) in `modules/extension-ports/src/main/kotlin/com/authord/mkdocs/ports/topic/TreeSyncOrchestrator.kt` (Req: DOC-006, R-16, PI-003)
- [X] T096 Close CR1 by documenting `InstanceRegistryPort` API (purpose, inputs, outputs, errors, usage examples) in `modules/extension-ports/src/main/kotlin/com/authord/mkdocs/ports/topic/InstanceRegistryPort.kt` (Req: DOC-006, R-16, PI-003)
- [X] T097 Close CR1 by documenting orchestration application service API (purpose, inputs, outputs, errors, usage examples) in `modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/intellij/TopicTreeApplicationService.kt` (Req: DOC-006, R-16, PI-001, PI-003)
- [X] T098 Close CR1 by documenting UI-facing service API (purpose, inputs, outputs, errors, usage examples) in `modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/intellij/TopicTreeUiService.kt` (Req: DOC-006, R-16, PI-001, PI-003)
- [X] T099 Add explicit KDoc coverage for all new/changed public classes/interfaces/methods/functions in `modules/core-domain/src/test/kotlin/com/authord/mkdocs/core/quality/KDocCoveragePolicyTest.kt` (Req: DOC-006, R-16)
- [X] T100 Add meaningful comments for non-obvious logic branches in `modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/intellij/TopicTreeSyncOrchestratorService.kt` (Req: DOC-006)
- [X] T101 Add meaningful comments for non-obvious watcher/external-move rules in `modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/intellij/TopicTreeWatcherCoordinator.kt` (Req: DOC-006, R-12e)
- [X] T102 Add meaningful comments for deterministic serialization/rewrite invariants in `modules/mkdocs-runtime-adapter/src/main/kotlin/com/authord/mkdocs/runtime/MkDocsYamlGateway.kt` (Req: DOC-006, R-14)

### CR2 Closure: 100% Scoped Coverage Gates

- [X] T103 Close CR2 by enforcing root aggregated scoped coverage threshold in `build.gradle.kts` (Req: R-18, SC-005)
- [X] T104 Close CR2 by enforcing module coverage threshold in `modules/core-domain/build.gradle.kts` (Req: R-18, SC-005)
- [X] T105 Close CR2 by enforcing module coverage threshold in `modules/extension-ports/build.gradle.kts` (Req: R-18, SC-005)
- [X] T106 Close CR2 by enforcing module coverage threshold in `modules/mkdocs-runtime-adapter/build.gradle.kts` (Req: R-18, SC-005)
- [X] T107 Close CR2 by enforcing module coverage threshold in `modules/ui-plugin/build.gradle.kts` (Req: R-18, SC-005)
- [X] T108 Close CR2 by adding CI fail-on-threshold workflow step in `.github/workflows/quality.yml` (Req: R-18, SC-005)
- [X] T109 Close CR2 by adding scoped-coverage policy test in `modules/core-domain/src/test/kotlin/com/authord/mkdocs/core/quality/ScopedCoveragePolicyTest.kt` (Req: R-18, SC-005)
- [X] T110 Close CR2 by publishing scoped coverage evidence section in `specs/001-mkdocs-topic-tree/test-plan-traceability.md` (Req: R-18, SC-005, DOC-004). Evidence must quote command output verbatim and include JaCoCo XML counters.

> Remediation checkpoint (2026-02-15): Core-domain scoped coverage gaps for `PathPolicy.kt`, `TopicTreeAggregate.kt`, and `TopicTreeValidationService.kt` remain closed via targeted unit tests only; `:modules:core-domain:jacocoTestCoverageVerification` is green and `:modules:extension-ports:jacocoTestCoverageVerification` is up-to-date in `./gradlew scopedCoverageGate --no-daemon --console=plain`. Latest canonical run `20260215T041517Z` records root `scopedCoverageGate` as PASS with no active coverage blocker. Canonical evidence sources: `tests/integration/topic-tree/CoverageGateEvidence.md`, `specs/001-mkdocs-topic-tree/quickstart.md`, and `specs/001-mkdocs-topic-tree/test-plan-traceability.md`.

### TR1 Closure: Success Criteria Traceability and Verification

- [X] T111 Close TR1 by completing SC-001..SC-005 matrix rows with task/test links in `specs/001-mkdocs-topic-tree/test-plan-traceability.md` (Req: SC-001, SC-002, SC-003, SC-004, SC-005, DOC-004)
- [X] T112 Add SC-001 startup timing verification evidence in `tests/integration/topic-tree/StartupTimingEvidence.md` (Req: SC-001, R-19)
- [X] T113 Add SC-002 partial-sync zero-state verification evidence in `tests/integration/topic-tree/PartialSyncZeroStateEvidence.md` (Req: SC-002, R-11)
- [X] T114 Add SC-003 seeded validation-dataset verification evidence in `tests/integration/topic-tree/SeededValidationEvidence.md` (Req: SC-003, R-10)
- [X] T115 Add SC-004 compatibility regression criteria verification evidence in `tests/integration/topic-tree/CompatibilityRegressionEvidence.md` (Req: SC-004, R-15, R-15a)
- [X] T116 Add SC-005 coverage gate verification evidence in `tests/integration/topic-tree/CoverageGateEvidence.md` (Req: SC-005, R-18)
- [X] T117 Add SC-006 mutation responsiveness verification evidence in `tests/integration/topic-tree/MutationResponsivenessEvidence.md` (Req: SC-006, R-20)
- [X] T118 Add SC-007 instance scale-profile verification evidence in `tests/integration/topic-tree/InstanceScaleEvidence.md` (Req: SC-007, R-21)

### CR3 Closure: Runtime Decoupling Regression Guards

- [X] T119 Close CR3 by re-verifying host/port hardcoding regression policy (re-run and confirm existing tests remain green after full integration) in `modules/mkdocs-runtime-adapter/src/test/kotlin/com/authord/mkdocs/runtime/RuntimeDecouplingPolicyTest.kt` (Req: R-15)
- [X] T120 Close CR3 by publishing/updating runIde + checklist-linked runtime decoupling evidence in `specs/001-mkdocs-topic-tree/analysis-closure.md` (Req: R-15, R-15a)
- [X] T121 Close CR3 by collecting runIde smoke evidence for decoupling invariants in `tests/integration/topic-tree/RunIdeDecouplingEvidence.md` (Req: R-15, R-15a, DOC-004)

### Required Delivery Artifacts

- [X] T122 Update feature specification with implemented decisions in `specs/001-mkdocs-topic-tree/spec.md` (Req: DOC-001)
- [X] T123 Update technical design notes with final architecture and invariants in `specs/001-mkdocs-topic-tree/technical-design-notes.md` (Req: DOC-002)
- [X] T124 Update operational runbook with reconciliation/recovery/rollback procedures in `specs/001-mkdocs-topic-tree/operational-runbook.md` (Req: DOC-003)
- [X] T125 Update full requirements-to-tests traceability matrix in `specs/001-mkdocs-topic-tree/test-plan-traceability.md` (Req: DOC-004)
- [X] T126 Update changelog for Phase 2 release in `CHANGELOG.md` (Req: DOC-005)
- [X] T127 Update migration notes when compatibility impact exists in `docs/implementation/migration-notes.md` (Req: DOC-005, PI-002)
- [X] T128 Update public API usage guidance summary in `docs/implementation/public-api-usage.md` (Req: DOC-006, PI-003)

### Final Release Gate

- [X] T129 Execute full quality gate command and capture output in `specs/001-mkdocs-topic-tree/quickstart.md` (Req: R-18, SC-005)

> SC-005 evidence checkpoint (2026-02-15): `T116` and `T129` capture canonical run `20260215T041517Z` with both required gate commands PASS and synchronized SC-005 traceability pointers. Current gate state is PASS. Canonical evidence sources: `tests/integration/topic-tree/CoverageGateEvidence.md`, `specs/001-mkdocs-topic-tree/quickstart.md`, and `specs/001-mkdocs-topic-tree/test-plan-traceability.md`.
- [X] T130 Execute runIde smoke and compatibility gate decision capture in `tests/integration/topic-tree/RunIdePhase2CompatibilityValidation.md` (Req: R-15, R-15a, SC-004)
- [X] T131 Record CR1/CR2/TR1/CR3 closure status in `specs/001-mkdocs-topic-tree/analysis-closure.md` (Req: DOC-004)
- [X] T132 Mark cycle-completion gate results in `specs/001-mkdocs-topic-tree/analysis-closure.md` (Req: DOC-001, DOC-002, DOC-003, DOC-004, DOC-005, DOC-006, R-18)
- [X] T133 Execute compliance checklists and mark PASS/FAIL with evidence links in `specs/001-mkdocs-topic-tree/analysis-closure.md` (Req: DOC-004)

---

## Dependencies & Critical Path

### Phase Dependencies

- Phase 1 -> Phase 2 -> Phase 3/4/5 -> Phase 6
- Phase 2 blocks all user-story execution.
- Phase 6 starts only after all selected user stories are complete.

### User Story Dependencies

- US1 depends only on Phase 2.
- US2 depends on Phase 2; US1 components are optional reuse only (not a hard prerequisite per `R-22`).
- US3 depends on Phase 2 and integrates with US1/US2 outcomes.

### Milestone Dependencies

- Milestone A (`T017`, `T052`, `T062`) must complete before Milestone B (`T063`, `T065`) and Milestone C (`T067`, `T070`) finalize.
- Milestone D (`T040`, `T041`) depends on Milestone A and feeds Milestone E validation/instance outcomes (`T043`, `T073`, `T085`).
- Milestone E (`T073`, `T082`, `T085`) must complete before Milestone F release-gate closure (`T103`, `T111`, `T129`, `T130`, `T131`, `T132`, `T133`).

### Critical Path

`T001 -> T011 -> T017 -> T025 -> T027 -> T037 -> T040 -> T041 -> T048 -> T061 -> T062 -> T065 -> T073 -> T082 -> T086 -> T103 -> T108 -> T111 -> T119 -> T121 -> T129 -> T132 -> T133`

---

## Requirement and Constraint Traceability Matrix (R/NAV/PI/DOC/SC)

| ID | Task IDs |
|----|----------|
| R-01 | T027, T037, T045, T047 |
| R-02 | T028, T038, T045, T047 |
| R-03 | T029, T039, T045, T047 |
| R-04 | T016, T048, T060, T061, T067, T068, T069, T070 |
| R-04a | T049, T060, T061 |
| R-05 | T016, T050, T060, T067, T068 |
| R-06 | T016, T051, T060, T067, T068 |
| R-07 | T014, T020, T075, T082, T083, T090, T092 |
| R-07a | T014, T020, T073, T082 |
| R-07b | T014, T020, T073, T082 |
| R-07c | T014, T020, T074, T082, T084, T090 |
| R-08 | T011, T017, T052, T062, T066, T070 |
| R-09 | T012, T018, T053, T063, T066, T070, T072 |
| R-09a | T012, T054, T063 |
| R-09b | T012, T054, T063 |
| R-09c | T012, T055, T064 |
| R-10 | T023, T033, T036, T043, T046, T114 |
| R-11 | T013, T019, T032, T056, T057, T065, T071, T113 |
| R-11a | T013, T054, T063, T071 |
| R-12 | T030, T040, T045, T076, T085 |
| R-12a | T030, T040, T076, T085 |
| R-12b | T030, T040 |
| R-12c | T029, T039, T076, T085 |
| R-12d | T030, T040 |
| R-12e | T031, T032, T041, T042, T101 |
| R-13 | T011, T017, T027, T052, T062 |
| R-14 | T011, T017, T028, T038, T052, T062, T102 |
| R-15 | T025, T077, T078, T088, T091, T115, T119, T121, T127, T130 |
| R-15a | T006, T023, T024, T077, T079, T086, T087, T089, T115, T120, T121, T130 |
| R-16 | T025, T026, T093, T094, T095, T096, T097, T098, T099 |
| R-17 | T034, T044, T080 |
| R-17a | T034, T044, T080 |
| R-18 | T008, T099, T103, T104, T105, T106, T107, T108, T109, T110, T116, T129, T132 |
| R-19 | T004, T035, T112 |
| R-20 | T004, T058, T117 |
| R-21 | T004, T075, T076, T081, T083, T118 |
| R-22 | T015, T021, T059, T066 |
| NAV-001 | T011, T017, T027, T052, T062 |
| NAV-002 | T013, T019, T056, T065 |
| NAV-003 | T011, T017, T028, T038, T052, T062 |
| NAV-004 | T030, T031, T040, T041, T046 |
| PI-001 | T015, T016, T021, T022, T026, T066, T067, T097, T098 |
| PI-002 | T025, T091, T127 |
| PI-003 | T011, T012, T013, T014, T015, T016, T020, T021, T022, T026, T060, T093, T094, T095, T096, T097, T098, T128 |
| DOC-001 | T122, T132 |
| DOC-002 | T123, T132 |
| DOC-003 | T007, T124, T132 |
| DOC-004 | T001, T002, T005, T006, T008, T009, T010, T047, T072, T092, T110, T111, T121, T125, T131, T132, T133 |
| DOC-005 | T007, T009, T126, T127, T132 |
| DOC-006 | T093, T094, T095, T096, T097, T098, T099, T100, T101, T102, T128, T132 |
| SC-001 | T002, T035, T112 |
| SC-002 | T002, T057, T113 |
| SC-003 | T002, T003, T036, T114 |
| SC-004 | T002, T006, T077, T086, T087, T115, T130 |
| SC-005 | T002, T103, T104, T105, T106, T107, T108, T109, T110, T116, T129 |
| SC-006 | T002, T058, T117 |
| SC-007 | T002, T081, T118 |

---

## Success Criteria Verification Matrix (TR1)

| Success Criteria ID | Verification Tasks | Primary Evidence Artifact |
|---------------------|--------------------|---------------------------|
| SC-001 | T035, T112 | `tests/integration/topic-tree/StartupTimingEvidence.md` |
| SC-002 | T057, T113 | `tests/integration/topic-tree/PartialSyncZeroStateEvidence.md` |
| SC-003 | T003, T036, T114 | `tests/integration/topic-tree/SeededValidationEvidence.md` |
| SC-004 | T006, T077, T086, T087, T115, T130 | `tests/integration/topic-tree/CompatibilityRegressionEvidence.md` |
| SC-005 | T103, T104, T105, T106, T107, T108, T109, T110, T116, T129 | `tests/integration/topic-tree/CoverageGateEvidence.md` |
| SC-006 | T058, T117 | `tests/integration/topic-tree/MutationResponsivenessEvidence.md` |
| SC-007 | T081, T118 | `tests/integration/topic-tree/InstanceScaleEvidence.md` |

---

## Parallel Execution Examples

### US1

- Run `T027`, `T028`, `T029`, `T030`, `T031`, `T032`, `T033`, `T034`, `T035`, `T036` in parallel.
- Then run `T037`, `T038`, `T039` in parallel before `T040` and `T041` integration tasks.

### US2

- Run `T048` through `T059` in parallel as test-first work.
- Run `T062`, `T063`, `T064` in parallel after `T060`/`T061` command model stabilization.

### US3

- Run `T073` through `T081` in parallel.
- Run `T082`, `T083`, `T084`, `T085` in parallel before final compatibility wiring tasks.

### Phase 6

- Run documentation tasks `T093` through `T102` in parallel with coverage config tasks `T103` through `T110`.
- Execute release evidence tasks `T111` through `T121` after story-complete code freeze.

---

## Implementation Strategy

### MVP First

1. Complete Phase 1 and Phase 2.
2. Deliver US1 and validate independent startup/reconciliation behavior.
3. Deliver US2 core mutation synchronization value.

### Incremental Delivery

1. US1: canonical startup + fallback + reconciliation.
2. US2: command mutations + deterministic YAML + file/orchestrator sync.
3. US3: multi-instance + compatibility + runtime decoupling safeguards.
4. Phase 6: CR1/CR2/TR1/CR3 closure, docs, coverage, and release evidence.

### Suggested MVP Scope

- Minimum shippable increment: **US1 + US2** plus release-gate tasks `T103` through `T111` and evidence task `T129`.

---

## Definition of Done Checklist

1. DoD-01: All selected tasks are completed and linked evidence artifacts are updated.
2. DoD-02: Every `R/NAV/PI/DOC/SC` ID in `spec.md` maps to one or more completed tasks.
3. DoD-03: CR1 closure is complete with per-file API docs/KDoc/comments for required public interfaces/services.
4. DoD-04: CR2 closure is complete with 100% scoped coverage gates enforced in root + all touched modules and CI fail-on-threshold.
5. DoD-05: TR1 closure is complete with `SC-001..SC-005` (and remaining SC IDs) mapped to executed verification evidence.
6. DoD-06: CR3 closure is complete with runtime decoupling regression tests and runIde smoke evidence.
7. DoD-07: `runIde` compatibility verification is recorded and release-blocker severity rubric is applied.
8. DoD-08: Feature spec, technical design notes, operational runbook, test plan/traceability, changelog, and migration notes are updated.
9. DoD-09: Cycle remains **INCOMPLETE** until docs are complete, all tests pass, and coverage gate passes.

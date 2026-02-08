# Tasks: IntelliJ MkDocs Plugin MVP

**Input**: Design documents from `/Users/madushika/projects/authord-mkdocs-plugin/specs/002-intellij-mkdocs-mvp/`  
**Prerequisites**: `plan.md` (required), `spec.md` (required), `research.md`, `data-model.md`, `contracts/`

**Tests**: Tests are required by specification (`FR-019`) and this cycle enforces unit, integration, and contract coverage.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no unresolved dependencies)
- **[Story]**: User story label (`[US1]`, `[US2]`, `[US3]`)
- Every implementation task references requirement IDs.

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Initialize module layout, shared build config, and baseline quality scaffolding.

- [X] T001 Create multi-module Gradle settings and module includes in `./settings.gradle.kts` (Req: FR-001, FR-006, FR-013, FR-019)
- [X] T002 Configure root build plugins, dependency versions, and shared test settings in `./build.gradle.kts` (Req: FR-019)
- [X] T003 [P] Add module build scripts for `core-domain`, `mkdocs-runtime-adapter`, `ui-plugin`, `extension-ports`, and `infra-defaults` in `modules/*/build.gradle.kts` (Req: FR-001, FR-006, FR-013, FR-019)
- [X] T004 [P] Create baseline module package structure with placeholder sources in `modules/core-domain/src/main/kotlin/com/authord/mkdocs/core/.gitkeep` (Req: FR-001, FR-012, FR-013)
- [X] T005 [P] Create plugin descriptor and UI module resource scaffold in `modules/ui-plugin/src/main/resources/META-INF/plugin.xml` (Req: FR-005, FR-006)
- [X] T006 [P] Create shared test directory scaffolding in `tests/integration/.gitkeep` and `tests/contract/.gitkeep` (Req: FR-019)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core contracts and infrastructure that block all user stories.

**⚠️ CRITICAL**: No user story implementation starts until this phase is complete.

- [X] T007 Define runtime lifecycle state model in `modules/core-domain/src/main/kotlin/com/authord/mkdocs/core/runtime/RuntimeLifecycleState.kt` (Req: FR-003)
- [X] T008 Define feature-flag policy model and defaults in `modules/core-domain/src/main/kotlin/com/authord/mkdocs/core/flags/FeatureFlagPolicy.kt` (Req: FR-017, FR-020, FR-021, FR-022, FR-023)
- [X] T009 [P] Define extension seam interfaces in `modules/extension-ports/src/main/kotlin/com/authord/mkdocs/ports/TopicTreePort.kt` (Req: FR-013, FR-014, FR-015, FR-016)
- [X] T010 [P] Implement in-memory command registry default adapter in `modules/infra-defaults/src/main/kotlin/com/authord/mkdocs/defaults/command/InMemoryCommandRegistry.kt` (Req: FR-014)
- [X] T011 [P] Implement no-op preview sync default adapter in `modules/infra-defaults/src/main/kotlin/com/authord/mkdocs/defaults/preview/NoOpPreviewSyncAdapter.kt` (Req: FR-016)
- [X] T012 [P] Implement no-op vector store default adapter in `modules/infra-defaults/src/main/kotlin/com/authord/mkdocs/defaults/vector/NoOpVectorStoreAdapter.kt` (Req: FR-015, FR-022)
- [X] T013 Add foundational unit tests for flags and default seam behavior in `modules/core-domain/src/test/kotlin/com/authord/mkdocs/core/flags/FeatureFlagPolicyTest.kt` (Req: FR-017, FR-020, FR-021, FR-022, FR-023)
- [X] T014 [P] Add foundational unit tests for in-memory command registry in `modules/infra-defaults/src/test/kotlin/com/authord/mkdocs/defaults/command/InMemoryCommandRegistryTest.kt` (Req: FR-014)
- [X] T015 [P] Add foundational unit tests for no-op adapters in `modules/infra-defaults/src/test/kotlin/com/authord/mkdocs/defaults/ports/NoOpAdaptersTest.kt` (Req: FR-015, FR-016)

**Checkpoint**: Foundation complete. User stories can proceed in priority order or in parallel if staffed.

---

## Phase 3: User Story 1 - Activate and Preview Docs (Priority: P1) 🎯 MVP

**Goal**: First activation prepares runtime, starts mkdocs, detects URL, and opens side-by-side preview.

**Independent Test**: In a valid docs project with no existing runtime, activate plugin and verify live preview opens automatically.

### Tests for User Story 1

- [X] T016 [P] [US1] Add unit tests for first-run runtime bootstrap and install flow in `modules/mkdocs-runtime-adapter/src/test/kotlin/com/authord/mkdocs/runtime/UvBootstrapServiceTest.kt` (Req: FR-001, FR-002)
- [X] T017 [P] [US1] Add unit tests for single-instance process lifecycle and restart safety in `modules/mkdocs-runtime-adapter/src/test/kotlin/com/authord/mkdocs/runtime/MkdocsProcessManagerTest.kt` (Req: FR-003)
- [X] T018 [P] [US1] Add unit tests for stdout base URL detection parser in `modules/mkdocs-runtime-adapter/src/test/kotlin/com/authord/mkdocs/runtime/BaseUrlDetectorTest.kt` (Req: FR-004)
- [X] T019 [P] [US1] Add unit tests for preview pane opening coordinator in `modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/PreviewPaneCoordinatorTest.kt` (Req: FR-005)
- [X] T020 [US1] Add integration test for activation-to-preview journey in `tests/integration/runtime-lifecycle/ActivationToPreviewIT.kt` (Req: FR-001, FR-002, FR-003, FR-004, FR-005)

### Implementation for User Story 1

- [X] T021 [US1] Implement uv bootstrap and mkdocs install service in `modules/mkdocs-runtime-adapter/src/main/kotlin/com/authord/mkdocs/runtime/UvBootstrapService.kt` (Req: FR-001, FR-002)
- [X] T022 [US1] Implement mkdocs process manager with start/stop/restart and per-project singleton enforcement in `modules/mkdocs-runtime-adapter/src/main/kotlin/com/authord/mkdocs/runtime/MkdocsProcessManager.kt` (Req: FR-003)
- [X] T023 [US1] Implement stdout base URL detector in `modules/mkdocs-runtime-adapter/src/main/kotlin/com/authord/mkdocs/runtime/BaseUrlDetector.kt` (Req: FR-004)
- [X] T024 [US1] Implement side-by-side preview pane coordinator in `modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/PreviewPaneCoordinator.kt` (Req: FR-005)
- [X] T025 [US1] Implement activation orchestrator and runtime start wiring in `modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/PluginActivationService.kt` (Req: FR-001, FR-002, FR-003, FR-004, FR-005, FR-017)
- [X] T026 [US1] Implement activation failure presenter for setup/startup errors in `modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/ActivationErrorPresenter.kt` (Req: FR-001, FR-002, FR-003, FR-004, FR-005)

**Checkpoint**: User Story 1 is independently functional and demoable as MVP.

---

## Phase 4: User Story 2 - Explorer-Based Route Navigation (Priority: P2)

**Goal**: Docs explorer selections deterministically update preview routes.

**Independent Test**: Select docs files and verify route mappings for root, segment index, segment file, and nested paths.

### Tests for User Story 2

- [X] T027 [P] [US2] Add unit tests for docs explorer discovery and markdown filtering in `modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/DocsExplorerServiceTest.kt` (Req: FR-006)
- [X] T028 [P] [US2] Add unit tests for route mapping root/index/file/nested rules in `modules/core-domain/src/test/kotlin/com/authord/mkdocs/core/navigation/RouteMappingServiceTest.kt` (Req: FR-008, FR-009, FR-010, FR-011)
- [X] T029 [P] [US2] Add unit tests for explorer-selection to preview-route coordination in `modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/NavigationCoordinatorTest.kt` (Req: FR-007)
- [X] T030 [US2] Add integration test for explorer-to-preview update path in `tests/integration/explorer-preview-navigation/ExplorerSelectionToPreviewIT.kt` (Req: FR-006, FR-007, FR-008, FR-009, FR-010, FR-011)

### Implementation for User Story 2

- [X] T031 [US2] Implement docs explorer service and tree model in `modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/DocsExplorerService.kt` (Req: FR-006)
- [X] T032 [US2] Implement deterministic route mapping service in `modules/core-domain/src/main/kotlin/com/authord/mkdocs/core/navigation/RouteMappingService.kt` (Req: FR-008, FR-009, FR-010, FR-011)
- [X] T033 [US2] Implement docs file selection event publisher in `modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/DocsFileSelectionPublisher.kt` (Req: FR-007)
- [X] T034 [US2] Implement navigation coordinator to apply mapped routes in preview in `modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/NavigationCoordinator.kt` (Req: FR-007, FR-008, FR-009, FR-010, FR-011)
- [X] T035 [US2] Implement invalid-selection handling and route fallback behavior in `modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/PreviewNavigationFailureHandler.kt` (Req: FR-007, FR-011)

**Checkpoint**: User Story 2 is independently functional and testable alongside US1.

---

## Phase 5: User Story 3 - Scroll Semantics and Extension Seams (Priority: P3)

**Goal**: Deliver semantic scroll delta logic and extension-ready seam contracts with default adapters only.

**Independent Test**: Verify comment-excluding scroll semantics and seam contracts/default adapters without enabling out-of-scope capabilities.

### Tests for User Story 3

- [X] T036 [P] [US3] Add unit tests for topic tree invariants and mutation outcomes in `modules/core-domain/src/test/kotlin/com/authord/mkdocs/core/topic/TopicTreeAggregateTest.kt` (Req: FR-013)
- [X] T037 [P] [US3] Add unit tests for command bus and registry dispatch behavior in `modules/extension-ports/src/test/kotlin/com/authord/mkdocs/ports/PluginCommandBusContractTest.kt` (Req: FR-014)
- [X] T038 [P] [US3] Add unit tests for semantic scroll excluding comment PSI ranges in `modules/core-domain/src/test/kotlin/com/authord/mkdocs/core/scroll/ScrollSemanticServiceTest.kt` (Req: FR-012)
- [X] T039 [P] [US3] Add unit tests for out-of-scope guardrails (no sync, no AI commands, no vector DB behavior, no WriterSide parity mode) in `modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/OutOfScopeGuardrailsTest.kt` (Req: FR-020, FR-021, FR-022, FR-023)
- [X] T040 [US3] Add contract tests for extension seam defaults in `tests/contract/topic-tree-command-port/TopicTreePortContractTest.kt` (Req: FR-013, FR-014, FR-015, FR-016)

### Implementation for User Story 3

- [X] T041 [US3] Implement topic tree aggregate and mutation service in `modules/core-domain/src/main/kotlin/com/authord/mkdocs/core/topic/TopicTreeAggregate.kt` (Req: FR-013)
- [X] T042 [US3] Implement topic-tree command DTO/result models in `modules/extension-ports/src/main/kotlin/com/authord/mkdocs/ports/topic/TopicTreeCommandDtos.kt` (Req: FR-013)
- [X] T043 [US3] Implement plugin command bus and dispatch service in `modules/extension-ports/src/main/kotlin/com/authord/mkdocs/ports/command/DefaultPluginCommandBus.kt` (Req: FR-014)
- [X] T044 [US3] Implement preview sync and vector store port interfaces in `modules/extension-ports/src/main/kotlin/com/authord/mkdocs/ports/preview/PreviewSyncPort.kt` (Req: FR-015, FR-016)
- [X] T045 [US3] Implement semantic scroll service with comment PSI exclusion in `modules/core-domain/src/main/kotlin/com/authord/mkdocs/core/scroll/ScrollSemanticService.kt` (Req: FR-012)
- [X] T046 [US3] Implement feature-flag policy service for staged seam enablement in `modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/FeatureFlagPolicyService.kt` (Req: FR-017, FR-020, FR-021, FR-022, FR-023)
- [X] T047 [US3] Wire no-op adapters and command registry defaults in plugin composition root in `modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/PluginCompositionRoot.kt` (Req: FR-014, FR-015, FR-016, FR-017)

**Checkpoint**: User Story 3 is independently testable and extension seams are present with minimal/default behavior only.

---

## Phase 6: Polish & Cross-Cutting (Testing, Coverage Gate, DocOps)

**Purpose**: Complete mandatory quality gates and artifact deliverables for this cycle.

- [X] T048 [P] Add unit test asserting required SDD artifact presence in `modules/core-domain/src/test/kotlin/com/authord/mkdocs/core/quality/DocumentationArtifactsPolicyTest.kt` (Req: FR-018)
- [X] T049 [P] Add unit test asserting scoped coverage configuration integrity in `modules/core-domain/src/test/kotlin/com/authord/mkdocs/core/quality/CoverageScopePolicyTest.kt` (Req: FR-019)
- [X] T050 [P] Configure CI to enforce 100% scoped unit coverage in `.github/workflows/ci.yml` (Req: FR-019)
- [X] T051 [P] Configure Gradle coverage verification rules in `./build.gradle.kts` (Req: FR-019)
- [X] T052 [P] Finalize requirements-to-tests traceability matrix with actual test classes in `specs/002-intellij-mkdocs-mvp/traceability-matrix.md` (Req: FR-018, FR-019)
- [X] T053 [P] Finalize ADR for TopicTreePort with implementation outcomes in `specs/002-intellij-mkdocs-mvp/adrs/ADR-001-topic-tree-port.md` (Req: FR-018)
- [X] T054 [P] Finalize ADR for CommandBus with implementation outcomes in `specs/002-intellij-mkdocs-mvp/adrs/ADR-002-plugin-command-bus.md` (Req: FR-018)
- [X] T055 [P] Finalize ADR for PreviewSyncPort with implementation outcomes in `specs/002-intellij-mkdocs-mvp/adrs/ADR-003-preview-sync-port.md` (Req: FR-018)
- [X] T056 [P] Finalize ADR for VectorStorePort with implementation outcomes in `specs/002-intellij-mkdocs-mvp/adrs/ADR-004-vector-store-port.md` (Req: FR-018)
- [X] T057 [P] Update runtime diagnostics runbook with validated failure signatures and recovery steps in `specs/002-intellij-mkdocs-mvp/runtime-diagnostics-runbook.md` (Req: FR-018)
- [X] T058 Validate OpenAPI and AsyncAPI contracts against implementation in `specs/002-intellij-mkdocs-mvp/contracts/plugin-control.openapi.yaml` (Req: FR-013, FR-014, FR-015, FR-016)
- [X] T059 Run quickstart validation and annotate completion criteria in `specs/002-intellij-mkdocs-mvp/quickstart.md` (Req: FR-018, FR-019)
- [X] T060 Enforce cycle gate checklist and keep cycle INCOMPLETE until docs, tests, and coverage pass in `specs/002-intellij-mkdocs-mvp/checklists/release-gate.md` (Req: FR-018, FR-019)

---

## Requirement-to-Unit-Test Mapping

- FR-001 -> T016 (`UvBootstrapServiceTest.kt`)
- FR-002 -> T016 (`UvBootstrapServiceTest.kt`)
- FR-003 -> T017 (`MkdocsProcessManagerTest.kt`)
- FR-004 -> T018 (`BaseUrlDetectorTest.kt`)
- FR-005 -> T019 (`PreviewPaneCoordinatorTest.kt`)
- FR-006 -> T027 (`DocsExplorerServiceTest.kt`)
- FR-007 -> T029 (`NavigationCoordinatorTest.kt`)
- FR-008 -> T028 (`RouteMappingServiceTest.kt`)
- FR-009 -> T028 (`RouteMappingServiceTest.kt`)
- FR-010 -> T028 (`RouteMappingServiceTest.kt`)
- FR-011 -> T028 (`RouteMappingServiceTest.kt`)
- FR-012 -> T038 (`ScrollSemanticServiceTest.kt`)
- FR-013 -> T036 (`TopicTreeAggregateTest.kt`)
- FR-014 -> T037 (`PluginCommandBusContractTest.kt`)
- FR-015 -> T015 (`NoOpAdaptersTest.kt`)
- FR-016 -> T015 (`NoOpAdaptersTest.kt`)
- FR-017 -> T013 (`FeatureFlagPolicyTest.kt`)
- FR-018 -> T048 (`DocumentationArtifactsPolicyTest.kt`)
- FR-019 -> T049 (`CoverageScopePolicyTest.kt`)
- FR-020 -> T039 (`OutOfScopeGuardrailsTest.kt`)
- FR-021 -> T039 (`OutOfScopeGuardrailsTest.kt`)
- FR-022 -> T039 (`OutOfScopeGuardrailsTest.kt`)
- FR-023 -> T039 (`OutOfScopeGuardrailsTest.kt`)

---

## Dependencies & Execution Order

### Phase Dependencies

- Phase 1 (Setup): no dependencies.
- Phase 2 (Foundational): depends on Phase 1; blocks all user stories.
- Phase 3 (US1): depends on Phase 2.
- Phase 4 (US2): depends on Phase 2; can run parallel with US1 after foundation.
- Phase 5 (US3): depends on Phase 2; can run parallel with US1/US2 after foundation.
- Phase 6 (Polish): depends on completion of selected user stories and mandatory quality/doc tasks.

### User Story Dependencies

- US1 (P1): independent after foundational phase; target MVP.
- US2 (P2): independent after foundational phase; integrates with US1 preview.
- US3 (P3): independent after foundational phase; must not expand beyond seam/default behavior.

### Completion Gate

- Cycle status remains **INCOMPLETE** until T052, T053, T054, T055, T056, T057, T058, T059, and T060 are complete and CI confirms T050/T051 coverage enforcement.

---

## Parallel Execution Examples

### User Story 1

```bash
# Parallel unit tests
T016, T017, T018, T019

# Then implementation sequence
T021 -> T022 -> T023 -> T024 -> T025 -> T026
```

### User Story 2

```bash
# Parallel unit tests
T027, T028, T029

# Parallel implementation where safe
T031 || T032
# Then
T033 -> T034 -> T035
```

### User Story 3

```bash
# Parallel unit tests
T036, T037, T038, T039

# Parallel seam implementations
T041 || T042 || T043 || T044 || T045
# Then integration wiring
T046 -> T047
```

---

## Implementation Strategy

### MVP First (US1)

1. Complete Phase 1 and Phase 2.
2. Deliver Phase 3 (US1) and validate T020 integration test.
3. Demo MVP activation-to-preview flow.

### Incremental Delivery

1. Add US2 for explorer-to-preview navigation.
2. Add US3 seams and semantic scroll behavior.
3. Finish Phase 6 quality and DocOps gates.

### Parallel Team Strategy

1. Team completes Setup + Foundational together.
2. After Phase 2:
   - Engineer A: US1 runtime and preview tasks.
   - Engineer B: US2 explorer and route tasks.
   - Engineer C: US3 seams and scroll tasks.
3. Merge only after Phase 6 completion gate is green.

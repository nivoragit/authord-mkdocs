# Tasks: IntelliJ Plugin Shell Cycle

**Input**: Design documents from `/Users/madushika/projects/authord-mkdocs-plugin/specs/002-intellij-mkdocs-mvp/`  
**Prerequisites**: `plan.md` (required), `spec.md` (required), `research.md`, `data-model.md`, `contracts/`, `quickstart.md`

**Tests**: Unit tests and coverage-gate tasks are REQUIRED for in-scope production code. Integration smoke validation is REQUIRED for plugin-shell operability.

**Organization**: Tasks are grouped by user story, with explicit workstream alignment:
- A) Build/descriptor enablement
- B) Plugin UI shell (tool window + action)
- C) Runtime integration wiring
- D) Testing + coverage gate
- E) DocOps artifacts

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Parallelizable task (different files, no unresolved dependency)
- **[Story]**: User story label (`[US1]`, `[US2]`, `[US3]`) for story-phase tasks only
- Every implementation task references one or more requirement IDs (`R-01..R-06`)

## Requirement IDs (Cycle Scope)

- **R-01** Build/plugin setup (`runIde`-capable IntelliJ plugin configuration)
- **R-02** Plugin descriptor registration (platform dependency, tool window, start-preview action)
- **R-03** Tool window shell implementation
- **R-04** Start MkDocs Preview action wiring and action-system behavior
- **R-05** Runtime handoff integration to existing services with lifecycle/single-instance preservation
- **R-06** Run-in-IDE workflow validation (plugin loads, tool window visible, action visible/invokable)

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Prepare plugin-shell source/test scaffolding and build workflow entry points.

- [X] T001 [P] Configure IntelliJ plugin build baseline and metadata properties in `build.gradle.kts` and `gradle.properties` (Req: R-01)
- [X] T002 [P] Ensure module build wiring supports IntelliJ shell classes in `modules/ui-plugin/build.gradle.kts` (Req: R-01)
- [X] T003 [P] Create IntelliJ shell package scaffold in `modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/intellij/.gitkeep` (Req: R-03, R-04, R-05)
- [X] T004 [P] Create plugin-shell integration test scaffold in `tests/integration/plugin-shell/.gitkeep` (Req: R-06)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Establish descriptor and shared adapter contracts required by all user stories.

**⚠️ CRITICAL**: User story work starts only after this phase.

- [X] T005 Update platform dependency declaration and core plugin metadata in `modules/ui-plugin/src/main/resources/META-INF/plugin.xml` (Req: R-02)
- [X] T006 Register MkDocs tool window extension metadata in `modules/ui-plugin/src/main/resources/META-INF/plugin.xml` (Req: R-02, R-03)
- [X] T007 Register Start MkDocs Preview action metadata in `modules/ui-plugin/src/main/resources/META-INF/plugin.xml` (Req: R-02, R-04)
- [X] T008 [P] Add shared IntelliJ test fixtures for project/action context mocking in `modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/intellij/IntellijTestFixtures.kt` (Req: R-03, R-04, R-05)
- [X] T009 [P] Align plugin-shell control/event contracts with planned adapter flow in `specs/002-intellij-mkdocs-mvp/contracts/plugin-control.openapi.yaml` and `specs/002-intellij-mkdocs-mvp/contracts/navigation-events.asyncapi.yaml` (Req: R-04, R-05, R-06)

**Checkpoint**: Foundation complete; user stories can proceed.

---

## Phase 3: User Story 1 - A) Build/Descriptor Enablement (Priority: P1) 🎯 MVP

**Goal**: Plugin shell can be built/launched in development IDE with valid descriptor registrations.

**Independent Test**: Build plugin, execute `runIde`, and confirm plugin loads without descriptor/build compatibility errors.

### Tests for User Story 1

- [X] T010 [P] [US1] Add plugin build policy unit test for IntelliJ/runIde configuration in `modules/core-domain/src/test/kotlin/com/authord/mkdocs/core/quality/PluginBuildPolicyTest.kt` (Req: R-01)
- [X] T011 [P] [US1] Add descriptor registration unit test for dependency/tool-window/action entries in `modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/intellij/PluginDescriptorRegistrationTest.kt` (Req: R-02)

### Implementation for User Story 1

- [X] T012 [US1] Implement IntelliJ Platform Gradle plugin and IDE target configuration in `build.gradle.kts` (Req: R-01)
- [X] T013 [US1] Implement runIde-ready plugin metadata and compatibility properties in `gradle.properties` (Req: R-01)
- [X] T014 [US1] Implement descriptor entries for platform/tool-window/action in `modules/ui-plugin/src/main/resources/META-INF/plugin.xml` (Req: R-02)
- [X] T015 [US1] Document local runIde usage and expected plugin-load checks in `specs/002-intellij-mkdocs-mvp/quickstart.md` (Req: R-01, R-06)

**Checkpoint**: Build/descriptor shell path is independently verifiable.

---

## Phase 4: User Story 2 - B) Plugin UI Shell (Tool Window + Action) (Priority: P1)

**Goal**: Minimal MkDocs tool window and start-preview action are visible and callable in IDE shell.

**Independent Test**: In development IDE, verify tool window content creation and action presentation/invocation behavior.

### Tests for User Story 2

- [X] T016 [P] [US2] Add tool-window factory content-path unit tests in `modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/intellij/MkdocsToolWindowFactoryTest.kt` (Req: R-03)
- [X] T017 [P] [US2] Add start-action visibility/enabled update logic unit tests in `modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/intellij/StartMkdocsActionPresentationTest.kt` (Req: R-04)

### Implementation for User Story 2

- [X] T018 [US2] Implement minimal tool-window factory and shell panel in `modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/intellij/MkdocsToolWindowFactory.kt` (Req: R-03)
- [X] T019 [US2] Implement start-preview action `update`/`actionPerformed` shell behavior in `modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/intellij/StartMkdocsAction.kt` (Req: R-04)
- [X] T020 [US2] Wire tool-window/action instantiation path through composition wiring in `modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/PluginCompositionRoot.kt` (Req: R-03, R-04)

**Checkpoint**: UI shell entry points are independently testable.

---

## Phase 5: User Story 3 - C) Runtime Integration Wiring (Priority: P2)

**Goal**: Action/tool-window delegate to existing runtime services while preserving lifecycle and single-instance guarantees.

**Independent Test**: Invoke start-preview through action with mocked and real adapter states; verify delegation contract and single-instance guard behavior.

### Tests for User Story 3

- [X] T021 [P] [US3] Add action-to-service delegation contract unit tests with mocked adapter in `modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/intellij/StartMkdocsActionInvocationTest.kt` (Req: R-04, R-05)
- [X] T022 [P] [US3] Add runtime integration lifecycle guard unit tests in `modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/intellij/PluginRuntimeIntegrationServiceTest.kt` (Req: R-05)

### Implementation for User Story 3

- [X] T023 [US3] Implement project-scoped runtime integration adapter delegating to existing services in `modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/intellij/PluginRuntimeIntegrationService.kt` (Req: R-05)
- [X] T024 [US3] Connect StartMkdocsAction to runtime integration adapter without runtime logic duplication in `modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/intellij/StartMkdocsAction.kt` (Req: R-04, R-05)
- [X] T025 [US3] Preserve activation/runtime single-instance and base-URL handoff behavior through existing service reuse in `modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/PluginActivationService.kt` (Req: R-05)

**Checkpoint**: Runtime handoff wiring is independently testable and extension seams remain intact.

---

## Phase 6: D) Testing + Coverage Gate (Cross-Cutting)

**Purpose**: Validate plugin shell in IDE workflow and enforce 100% scoped unit coverage in CI.

- [X] T026 [P] Add plugin-shell smoke scenario definition for runIde launch/plugin-load/tool-window/action checks in `tests/integration/plugin-shell/RunIdeSmokeValidation.md` (Req: R-06)
- [X] T027 Execute runIde smoke validation and record evidence/outcome in `specs/002-intellij-mkdocs-mvp/checklists/release-gate.md` (Req: R-06)
- [X] T028 [P] Update requirements-to-tests traceability matrix for R-01..R-06 in `specs/002-intellij-mkdocs-mvp/traceability-matrix.md` (Req: R-01, R-02, R-03, R-04, R-05, R-06)
- [X] T029 [P] Enforce CI scoped unit coverage gate execution in `.github/workflows/ci.yml` (Req: R-06)
- [X] T030 [P] Enforce scoped 100% coverage verification rules in `build.gradle.kts` (Req: R-06)
- [X] T031 Run full test + coverage verification and record pass/fail in `specs/002-intellij-mkdocs-mvp/checklists/release-gate.md` (Req: R-06)

---

## Phase 7: E) DocOps Artifacts (Cross-Cutting)

**Purpose**: Complete constitution-required docs package and API usage documentation.

- [X] T032 [P] Update feature spec for plugin-shell cycle scope and R-01..R-06 alignment in `specs/002-intellij-mkdocs-mvp/spec.md` (Req: R-01, R-02, R-03, R-04, R-05, R-06)
- [X] T033 [P] Update technical design notes with plugin entry architecture and function-level usage docs in `docs/implementation/technical-design-notes.md` (Req: R-03, R-04, R-05)
- [X] T034 [P] Update operational runbook for local runIde workflow and troubleshooting in `docs/implementation/operational-runbook.md` (Req: R-01, R-06)
- [X] T035 [P] Update test plan + requirements→tests traceability matrix documentation in `docs/implementation/test-plan-traceability.md` (Req: R-01, R-02, R-03, R-04, R-05, R-06)
- [X] T036 [P] Update changelog and migration notes for plugin-shell cycle changes in `CHANGELOG.md` and `docs/implementation/migration-notes.md` (Req: R-06)
- [X] T037 [P] Add public API usage docs (purpose/inputs/outputs/errors/examples) for new/changed plugin-shell functions in `modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/intellij/*.kt` (Req: R-03, R-04, R-05)
- [X] T038 Mark cycle completion gate as blocked until docs/tests/coverage are green in `specs/002-intellij-mkdocs-mvp/checklists/release-gate.md` (Req: R-06)

---

## Requirement-to-Test Traceability Matrix (R-01..R-06)

| Requirement | Unit Tests | Integration / Smoke Validation |
|-------------|------------|--------------------------------|
| R-01 | T010 (`PluginBuildPolicyTest.kt`) | T027 (`RunIdeSmokeValidation.md` + release-gate evidence) |
| R-02 | T011 (`PluginDescriptorRegistrationTest.kt`) | T027 (`RunIdeSmokeValidation.md` + release-gate evidence) |
| R-03 | T016 (`MkdocsToolWindowFactoryTest.kt`) | T027 (`RunIdeSmokeValidation.md` + release-gate evidence) |
| R-04 | T017 (`StartMkdocsActionPresentationTest.kt`), T021 (`StartMkdocsActionInvocationTest.kt`) | T027 (`RunIdeSmokeValidation.md` + release-gate evidence) |
| R-05 | T022 (`PluginRuntimeIntegrationServiceTest.kt`), T021 (`StartMkdocsActionInvocationTest.kt`) | T027 (`RunIdeSmokeValidation.md` + release-gate evidence) |
| R-06 | T026/T027 smoke workflow checks | T027 + T031 recorded gate run |

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: no dependencies.
- **Phase 2 (Foundational)**: depends on Phase 1 and blocks all user stories.
- **Phase 3 (US1 / A)**: depends on Phase 2.
- **Phase 4 (US2 / B)**: depends on Phase 2; can run in parallel with US1 when staffed.
- **Phase 5 (US3 / C)**: depends on Phase 2 and US2 action shell implementation.
- **Phase 6 (D)**: depends on completion of US1-US3 implementation and tests.
- **Phase 7 (E)**: can begin during implementation but must complete before cycle closure.

### User Story Dependencies

- **US1 (P1)**: Independent after foundational setup.
- **US2 (P1)**: Independent after foundational setup; provides visible shell entry points.
- **US3 (P2)**: Depends on US2 action class being available for delegation.

### Cycle Completion Gate

- Cycle is **COMPLETE** when all DocOps tasks (T032-T038) are done, smoke/tests pass (T027, T031), and coverage gate enforcement is verified (T029, T030, T031).

---

## Parallel Execution Examples

### User Story 1 (A)

```bash
# Parallel tests
T010, T011

# Implementation sequence
T012 -> T013 -> T014 -> T015
```

### User Story 2 (B)

```bash
# Parallel tests
T016, T017

# Implementation sequence
T018 -> T019 -> T020
```

### User Story 3 (C)

```bash
# Parallel tests
T021, T022

# Implementation sequence
T023 -> T024 -> T025
```

---

## Implementation Strategy

### Suggested MVP Scope

1. Complete Phase 1 and Phase 2.
2. Deliver US1 (A) and US2 (B) to obtain runnable plugin shell (`runIde` + visible tool window + callable action).
3. Validate smoke checks before expanding to runtime integration hardening (US3).

### Incremental Delivery

1. Build/descriptor enablement (US1)
2. UI shell (US2)
3. Runtime handoff wiring (US3)
4. Testing/coverage gate hardening (Phase 6)
5. DocOps closure (Phase 7)

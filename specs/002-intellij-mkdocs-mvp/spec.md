# Feature Specification: IntelliJ Plugin Shell Cycle

**Feature Branch**: `[002-intellij-mkdocs-mvp]`  
**Created**: 2026-02-08  
**Status**: Draft  
**Input**: User description: "Cycle goal: make current MVP runnable inside IntelliJ as a real plugin shell."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Build and Launch Plugin Shell (Priority: P1)

A plugin developer can build the plugin and launch a development IDE where the plugin loads with valid metadata.

**Why this priority**: Without a runnable shell in IntelliJ, no UI/runtime entry point can be validated.

**Independent Test**: Run Gradle plugin-shell build checks and launch `runIde`; plugin loads without descriptor compatibility errors.

**Acceptance Scenarios**:

1. **Given** the repository build configuration, **When** the plugin build runs, **Then** IntelliJ Platform plugin configuration and compatibility metadata are valid.
2. **Given** plugin descriptor configuration, **When** the plugin is loaded in development IDE, **Then** platform dependency, tool window registration, and start-preview action registration are recognized.

---

### User Story 2 - Use Plugin UI Shell Entry Points (Priority: P1)

A plugin developer can open the MkDocs tool window and invoke Start MkDocs Preview from the IDE action system.

**Why this priority**: The cycle objective is a real plugin shell with visible and callable UI entry points.

**Independent Test**: Verify tool window content creation path and action update/actionPerformed behavior with unit tests and runIde smoke steps.

**Acceptance Scenarios**:

1. **Given** a project is open in IDE, **When** the tool window is created, **Then** the minimal MkDocs shell panel is visible.
2. **Given** a project context is available, **When** action update runs, **Then** Start MkDocs Preview visibility/enabled state matches runtime readiness.
3. **Given** Start MkDocs Preview is invoked, **When** actionPerformed executes, **Then** runtime handoff is delegated to the project runtime integration service.

---

### User Story 3 - Preserve Runtime Handoff Guarantees (Priority: P2)

A platform engineer can wire action/tool-window entry points to existing runtime/domain services while preserving single-instance lifecycle behavior per project.

**Why this priority**: Plugin-shell enablement must not break existing MVP runtime behavior.

**Independent Test**: Validate lifecycle guard behavior in runtime integration adapter unit tests and smoke validation notes.

**Acceptance Scenarios**:

1. **Given** runtime is not running for a project, **When** plugin entry point triggers start, **Then** runtime start delegates to existing activation/runtime services.
2. **Given** runtime is already running for a project, **When** start is triggered again, **Then** no second process instance is launched.
3. **Given** project service is disposed, **When** plugin cleanup runs, **Then** runtime stop/dispose paths execute safely.

### Edge Cases

- Project context exists but base path is unavailable.
- Start action is invoked while runtime start is not allowed by feature-policy guardrails.
- Tool window start button is clicked while runtime is already active.
- `runIde` environment launches but plugin descriptor registration is invalid.
- Runtime startup output does not contain a detectable URL and activation returns controlled failure.

## Out of Scope *(mandatory)*

- New end-user features beyond plugin-shell enablement.
- Code↔preview scroll synchronization behavior.
- AI chatbot or command-execution features.
- Vector retrieval/database-backed behavior.
- Full WriterSide-like topic-tree UX parity.

## Requirements *(mandatory)*

### Functional Requirements

- **R-01**: System MUST configure IntelliJ Platform Gradle plugin setup, compatible IDE target, and plugin metadata so `runIde` is available.
- **R-02**: System MUST define plugin descriptor entries for platform dependency, MkDocs tool window registration, and Start MkDocs Preview action registration.
- **R-03**: System MUST implement a minimal MkDocs tool window factory and shell content panel.
- **R-04**: System MUST implement Start MkDocs Preview action `update` and `actionPerformed` behavior that follows IntelliJ action-system lifecycle/threading expectations.
- **R-05**: System MUST wire plugin shell entry points to existing runtime/domain services and preserve single runtime instance behavior per project.
- **R-06**: System MUST support run-in-IDE workflow validation where plugin loads, tool window is present, and action is visible/invokable.

### Public Interfaces & Versioning *(mandatory when public interfaces change)*

- **PI-001**: `PluginRuntimeIntegrationService` MUST be versioned as `1.0.0` and documented with usage contract for action/tool-window callers.
- **PI-002**: `StartMkdocsAction` and `MkdocsToolWindowFactory` MUST preserve MVP workflow compatibility and rely on project-scoped runtime integration instead of duplicating runtime logic.
- **PI-003**: Existing seam interfaces (`TopicTreePort`, `PreviewSyncPort`, `PluginCommandBus` + `CommandRegistry`, `VectorStorePort`) MUST remain present and unchanged in this cycle.

### Documentation Deliverables *(mandatory)*

- **DOC-001**: Feature spec is updated for plugin-shell scope and R-01..R-06.
- **DOC-002**: Technical design notes are updated with plugin entry architecture and function-level usage guidance.
- **DOC-003**: Operational runbook is updated for local `runIde` workflow and troubleshooting.
- **DOC-004**: Test plan and requirements-to-tests traceability matrix are updated for R-01..R-06.
- **DOC-005**: Changelog is updated and migration notes are included if compatibility changes are introduced.
- **DOC-006**: New/changed public services/functions include purpose, inputs, outputs, failure modes, and relevant usage examples.

### Key Entities *(include if feature involves data)*

- **PluginShellConfiguration**: Build and plugin metadata values controlling IDE target and descriptor compatibility.
- **PluginDescriptorRegistration**: Descriptor state containing dependency, tool window, and action entries.
- **ToolWindowShellState**: Minimal tool window content status for plugin shell UI.
- **ActionPresentationState**: Visibility/enabled decision for Start MkDocs Preview action.
- **RuntimeHandoffState**: Runtime lifecycle status and delegation outcome for project-scoped preview start.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: `runIde` task resolves and launches development IDE plugin shell workflow in documented local setup.
- **SC-002**: 100% of plugin descriptor policy tests pass for required platform dependency, tool window registration, and action registration.
- **SC-003**: 100% of in-scope unit tests pass for action presentation, action invocation delegation, tool-window content path, and runtime lifecycle guard behavior.
- **SC-004**: Scoped unit coverage remains at 100% and CI gate fails below threshold.
- **SC-005**: Cycle is marked complete only after documentation bundle, tests, and coverage gate are all green.

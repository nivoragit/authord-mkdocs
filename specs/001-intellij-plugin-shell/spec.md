# Feature Specification: IntelliJ Plugin Shell MVP Enablement

**Feature Branch**: `001-intellij-plugin-shell` (spec ID on working branch `002-intellij-mkdocs-mvp`)  
**Created**: 2026-02-09  
**Status**: Draft  
**Input**: User description: "Cycle goal: make current MVP runnable inside IntelliJ as a real plugin shell."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Launch Plugin Shell in Development IDE (Priority: P1)

A plugin developer launches the development IDE and verifies the plugin shell is loaded with a visible MkDocs tool window and a callable start-preview action.

**Why this priority**: The cycle goal is unusable unless the plugin can be launched and interacted with inside IntelliJ.

**Independent Test**: Launch the development IDE from the documented workflow, open a project, confirm the MkDocs tool window is visible, and invoke the start-preview action.

**Acceptance Scenarios**:

1. **Given** a valid development environment, **When** the developer launches the plugin in development IDE mode, **Then** the plugin loads without descriptor errors.
2. **Given** the plugin is loaded, **When** the developer opens a project, **Then** the MkDocs tool window appears with minimal shell content.
3. **Given** the plugin is loaded, **When** the developer searches for and triggers the start-preview action, **Then** the action is visible and callable.

---

### User Story 2 - Reuse Existing Runtime Behavior via Action (Priority: P1)

A documentation author uses the start-preview action and expects runtime startup behavior to match current MVP rules, including single server instance per project and URL-based preview handoff.

**Why this priority**: The plugin shell must expose existing MVP capability, not replace or regress runtime behavior.

**Independent Test**: Invoke start-preview repeatedly in the same project and verify runtime handoff reuses existing lifecycle/base-URL behavior and does not spawn duplicate project runtimes.

**Acceptance Scenarios**:

1. **Given** no active runtime for a project, **When** start-preview is invoked, **Then** existing runtime/bootstrap services are called and preview handoff uses detected base URL.
2. **Given** an active runtime for a project, **When** start-preview is invoked again, **Then** the system preserves single-instance behavior for that project.
3. **Given** startup output does not contain a valid base URL, **When** start-preview is invoked, **Then** the action reports a controlled failure without crashing the plugin shell.

---

### User Story 3 - Preserve Extension Seams and Cycle Quality Gates (Priority: P2)

A platform maintainer ensures plugin-shell enablement does not remove mandatory seams, does not enable future-cycle features, and still passes documentation and coverage gates.

**Why this priority**: The constitution requires MVP-first delivery with extension-ready architecture and complete documentation/test gates.

**Independent Test**: Run the full test and quality gate workflow and verify mandatory seams remain available, out-of-scope features remain disabled, docs are complete, and scoped unit coverage is 100%.

**Acceptance Scenarios**:

1. **Given** the plugin shell changes are merged, **When** quality checks run, **Then** mandatory seams remain present and callable with minimal/default behavior.
2. **Given** this cycle build, **When** out-of-scope feature paths are tested, **Then** they remain disabled.
3. **Given** release readiness review, **When** artifacts are checked, **Then** required docs and traceability artifacts are complete and current.

---

### Edge Cases

- What happens when start-preview is invoked before the tool window shell is initialized for the project?
- How does the plugin shell behave when runtime startup succeeds but base URL detection fails?
- How does repeated start-preview invocation behave when a prior runtime process is still active for the same project?
- What happens when plugin descriptor metadata is incompatible with the launched IDE target?

## Out of Scope *(mandatory)*

- New end-user features beyond plugin shell enablement for this cycle.
- Code-to-preview or preview-to-code synchronization behavior.
- AI command execution or chat workflows.
- Vector retrieval/integration behavior.
- Full WriterSide-like topic tree UX parity.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST provide a development workflow that launches IntelliJ with the plugin shell enabled.
- **FR-002**: System MUST configure plugin metadata and IDE compatibility so the plugin is loadable in the targeted development IDE.
- **FR-003**: System MUST provide a plugin descriptor that declares required platform dependency.
- **FR-004**: System MUST register an MkDocs tool window extension in the plugin descriptor.
- **FR-005**: System MUST register a Start MkDocs Preview action in the plugin descriptor and expose it through the IDE action system.
- **FR-006**: System MUST create minimal MkDocs tool window shell content through registered tool-window initialization.
- **FR-007**: Start-preview action MUST invoke existing activation/runtime services for handoff instead of duplicating runtime behavior.
- **FR-008**: Start-preview action execution MUST follow IDE action safety and lifecycle rules so the interface remains responsive.
- **FR-009**: Runtime handoff MUST preserve single runtime server instance semantics per project.
- **FR-010**: Runtime handoff MUST reuse existing base-URL detection before preview routing/opening.
- **FR-011**: Plugin shell workflow MUST verify run-in-IDE behavior where tool window is visible and action is callable.
- **FR-012**: System MUST NOT introduce new end-user features beyond plugin-shell enablement in this cycle.
- **FR-013**: System MUST keep mandatory seams present with minimal/default behavior: TopicTree command API/service, PreviewSyncPort, PluginCommandBus + CommandRegistry, VectorStorePort, and feature flags.
- **FR-014**: In-scope production code MUST maintain 100% unit test coverage with failing gate below threshold.
- **FR-015**: Cycle documentation bundle MUST be complete: feature spec, technical design notes, operational runbook, test plan + requirements-to-tests traceability matrix, changelog, and migration notes when needed.
- **FR-016**: New/changed public services and functions MUST include usage guidance covering purpose, inputs, outputs, error behavior, and examples where relevant.
- **FR-017**: Public code artifacts changed in scope MUST include required KDoc/docstrings and meaningful non-obvious logic comments per constitution.

### Assumptions & Dependencies

- Development environment supports launching IntelliJ plugin sandbox workflows.
- Existing runtime/domain services from the current MVP remain available and reusable.
- Mandatory seam interfaces and default adapters remain the architectural extension boundary for this cycle.
- Out-of-scope capabilities remain disabled by feature policy and are not introduced by plugin-shell wiring.

### Public Interfaces & Versioning *(mandatory when public interfaces change)*

- **PI-001**: Existing public seam interfaces (TopicTreePort, PreviewSyncPort, PluginCommandBus, CommandRegistry, VectorStorePort) MUST retain their current published API version and remain backward compatible.
- **PI-002**: Any new public plugin-shell service interfaces introduced in this cycle MUST declare version and compatibility expectations before release.
- **PI-003**: Usage contracts for new/changed public services MUST document inputs, outputs, constraints, and failure modes.

### Documentation Deliverables *(mandatory)*

- **DOC-001**: Feature spec is updated for delivered plugin-shell scope and exclusions.
- **DOC-002**: Technical design notes are updated for plugin descriptor, tool window shell, action wiring, and runtime handoff boundaries.
- **DOC-003**: Operational runbook is updated with run-in-IDE usage, action invocation steps, and failure handling.
- **DOC-004**: Test plan and requirements-to-tests traceability matrix are updated for plugin-shell requirements.
- **DOC-005**: Changelog is updated; migration notes are included when compatibility impact exists.
- **DOC-006**: New/changed public services/functions include required function-level usage guidance.

### Key Entities *(include if feature involves data)*

- **Plugin Shell Session**: Lifecycle state of plugin load and project-level tool window/action availability.
- **Tool Window Shell View**: Minimal MkDocs panel surface registered and shown for project context.
- **Start Preview Action Invocation**: Action-system request context that triggers runtime handoff through existing services.
- **Project Runtime Session**: Per-project runtime lifecycle state that must preserve single-instance behavior.
- **Descriptor Registration Contract**: Plugin metadata entries that bind dependencies, tool window extension, and action registration.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: In at least 90% of clean development runs, developers can launch the plugin in development IDE mode and access visible MkDocs tool window plus callable start-preview action within 3 minutes.
- **SC-002**: In 100% of repeated same-project action invocations under test, runtime lifecycle maintains a single active server instance per project.
- **SC-003**: In at least 95% of valid startup-output scenarios in automated tests, base URL is detected and runtime handoff completes without manual URL entry.
- **SC-004**: Scoped in-scope production code maintains 100% unit coverage, and verification fails automatically below threshold.
- **SC-005**: 100% of functional requirements map to one or more tests in the requirements-to-tests traceability matrix.
- **SC-006**: Required cycle documentation artifacts are present and current before cycle completion.

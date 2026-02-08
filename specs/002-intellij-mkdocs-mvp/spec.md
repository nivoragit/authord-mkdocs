# Feature Specification: IntelliJ MkDocs Plugin MVP

**Feature Branch**: `[002-intellij-mkdocs-mvp]`  
**Created**: 2026-02-08  
**Status**: Draft  
**Input**: User description: "Define MVP for IntelliJ MkDocs plugin. MVP IN SCOPE: - On first activation, create plugin-managed venv via uv and install mkdocs. - Dedicated docs explorer. - Activation starts mkdocs serve (dirty mode, default bind/port), detects base URL from stdout, opens side-by-side preview. - Selecting docs markdown updates preview route: - docs/index.md -> / - docs/foo/index.md -> /foo/ - docs/foo.md -> /foo/ - Track editor scroll delta excluding comment PSI ranges. OUT OF SCOPE (future cycles): - code↔preview scroll sync, - AI chatbot/command execution, - vector database integration, - full WriterSide-like topic tree UX parity. MANDATORY SEAMS (implemented now, behavior minimal/default): - TopicTreeService command API (add/move/remove/reorder/validate). - PluginCommandBus + CommandRegistry. - VectorStorePort. - PreviewSyncPort. - Feature flags for staged enablement. QUALITY: - SDD artifacts required this cycle. - 100% unit coverage for in-scope code."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Activate and Preview Docs (Priority: P1)

A documentation author activates the plugin for the first time and expects a working live documentation preview without manual environment preparation.

**Why this priority**: First activation and preview startup are the primary MVP outcome. If this fails, other capabilities do not provide usable value.

**Independent Test**: Enable the plugin in a valid docs project with no existing plugin runtime and verify the preview opens automatically after startup.

**Acceptance Scenarios**:

1. **Given** a valid docs project and no plugin-managed runtime, **When** the author activates the plugin, **Then** the plugin creates its managed runtime and installs required documentation tooling.
2. **Given** activation setup completed, **When** the plugin starts documentation preview serving, **Then** it detects the base URL from startup output and opens side-by-side preview.
3. **Given** setup or startup fails, **When** activation runs, **Then** the author receives a clear failure message and actionable next steps.

---

### User Story 2 - Explorer-Based Route Navigation (Priority: P2)

A documentation author navigates markdown files using a dedicated docs explorer and expects each selected file to load the matching route in preview.

**Why this priority**: Fast and predictable navigation from source docs to rendered preview is the core daily authoring loop after activation.

**Independent Test**: Select files from the docs explorer and verify route conversion and preview updates for each mapping rule.

**Acceptance Scenarios**:

1. **Given** the author selects `docs/index.md`, **When** selection occurs, **Then** the preview route becomes `/`.
2. **Given** the author selects `docs/foo/index.md`, **When** selection occurs, **Then** the preview route becomes `/foo/`.
3. **Given** the author selects `docs/foo.md`, **When** selection occurs, **Then** the preview route becomes `/foo/`.
4. **Given** the author selects nested markdown files under `docs/`, **When** selection occurs, **Then** route conversion follows the same index-or-file mapping pattern at that path depth.

---

### User Story 3 - Scroll Signal and Future Seams (Priority: P3)

A product team needs MVP to capture editor scroll delta (excluding comment PSI ranges) while exposing minimal seam contracts required for future capabilities.

**Why this priority**: This enables future expansion without expanding MVP user-facing scope and avoids architecture rework in later cycles.

**Independent Test**: Verify through unit tests that scroll delta excludes comment ranges and each mandatory seam exists with minimal/default behavior.

**Acceptance Scenarios**:

1. **Given** a docs markdown editor view, **When** the author scrolls content, **Then** scroll delta is captured only from non-comment PSI ranges.
2. **Given** TopicTreeService command API (`add`, `move`, `remove`, `rename`, `reparent`, `reorder`, `validate`), PluginCommandBus + CommandRegistry, VectorStorePort, and PreviewSyncPort seams are present, **When** invoked in MVP mode, **Then** they return minimal/default behavior only.
3. **Given** staged feature flags, **When** plugin features initialize, **Then** only in-scope capabilities are enabled by default.

### Edge Cases

- First activation occurs in a project without valid documentation configuration.
- Base URL cannot be parsed from preview server startup output.
- Preview URL is detected but cannot be loaded in the side-by-side pane.
- User selects a non-markdown file or a file outside `docs/`.
- Route conversion must handle nested paths and unusual segment names consistently.
- Scroll movement happens entirely in comment PSI ranges and must produce no effective delta.
- Scroll movement crosses comment and non-comment ranges and must only include non-comment movement.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST create a plugin-managed runtime on first activation when one does not already exist.
- **FR-002**: System MUST install required documentation tooling into the plugin-managed runtime during first activation.
- **FR-003**: System MUST start documentation preview serving during activation using runtime defaults without hardcoded host/port values.
- **FR-004**: System MUST detect preview base URL from startup output before opening preview.
- **FR-005**: System MUST open a side-by-side preview pane after successful base URL detection.
- **FR-006**: System MUST provide a dedicated docs explorer for markdown content navigation.
- **FR-007**: System MUST update preview route when a docs markdown file is selected.
- **FR-008**: System MUST map `docs/index.md` to `/`.
- **FR-009**: System MUST map `docs/<segment>/index.md` to `/<segment>/`.
- **FR-010**: System MUST map `docs/<segment>.md` to `/<segment>/`.
- **FR-011**: System MUST apply the same mapping behavior to nested paths under `docs/`.
- **FR-012**: System MUST track editor scroll delta for docs content and exclude comment PSI ranges from the captured delta.
- **FR-013**: System MUST implement TopicTreeService command API seam with add, move, remove, rename, reparent, reorder, and validate commands using minimal/default MVP behavior.
- **FR-014**: System MUST implement PluginCommandBus and CommandRegistry seams with minimal/default MVP behavior.
- **FR-015**: System MUST implement VectorStorePort seam with minimal/default MVP behavior.
- **FR-016**: System MUST implement PreviewSyncPort seam with minimal/default MVP behavior.
- **FR-017**: System MUST provide feature flags for staged enablement of in-scope and seam behaviors.
- **FR-018**: System MUST include Software Design Description artifacts for all in-scope functionality in this cycle.
- **FR-019**: System MUST achieve 100% unit test coverage for in-scope code in this cycle.
- **FR-020**: System MUST NOT include code-to-preview or preview-to-code scroll synchronization in this MVP.
- **FR-021**: System MUST NOT include AI chatbot or command execution capability in this MVP.
- **FR-022**: System MUST NOT include vector database integration behavior in this MVP.
- **FR-023**: System MUST NOT include full WriterSide-like topic tree UX parity in this MVP.

### Assumptions & Dependencies

- Plugin users operate in IDE projects that contain a `docs/` tree of markdown content.
- Local environment permissions allow creation and maintenance of plugin-managed runtime resources.
- Documentation configuration is available and valid in projects expected to use live preview.
- Product owners control staged feature flags so future-cycle capabilities remain disabled in MVP.
- Completion of SDD artifacts and 100% unit coverage are release gates for this cycle.

### Key Entities *(include if feature involves data)*

- **Documentation Project**: IDE project containing documentation source files and explorer-visible docs paths.
- **Managed Runtime State**: State tracking whether plugin runtime setup and tooling installation are complete.
- **Preview Session**: Active local preview context including startup state, detected base URL, and current route.
- **Route Mapping Rule**: Path conversion rule set that maps docs markdown files to preview routes.
- **Scroll Delta Sample**: Recorded editor scroll movement excluding comment PSI ranges.
- **Feature Flag**: Toggle that controls staged enablement of behavior.
- **Seam Contract**: Minimal command/port contract exposed for TopicTreeService, command bus/registry, vector store, and preview sync.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: At least 90% of first-time users in valid docs projects can reach an open live preview within 5 minutes of activation.
- **SC-002**: At least 95% of successful preview startup attempts result in automatic base URL detection and preview opening without manual URL entry.
- **SC-003**: Route mapping accuracy for defined patterns (`docs/index.md`, `docs/<segment>/index.md`, `docs/<segment>.md`) is 100% across approved test cases.
- **SC-004**: At least 95% of docs explorer selections load the correct preview route within 2 seconds.
- **SC-005**: Scroll delta exclusion of comment PSI ranges is correct for 100% of unit-test scenarios.
- **SC-006**: Release readiness requires complete SDD artifacts for in-scope behavior and verified 100% unit coverage for in-scope code.

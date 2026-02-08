# Feature Specification: IntelliJ MkDocs Plugin MVP

**Feature Branch**: `[001-intellij-mkdocs-mvp]`  
**Created**: 2026-02-08  
**Status**: Draft  
**Input**: User description: "Define MVP for IntelliJ MkDocs plugin. MVP IN SCOPE: - On first activation, create plugin-managed venv via uv and install mkdocs. - Dedicated docs explorer. - Activation starts mkdocs serve (dirty mode, default bind/port), detects base URL from stdout, opens side-by-side preview. - Selecting docs markdown updates preview route: - docs/index.md -> / - docs/foo/index.md -> /foo/ - docs/foo.md -> /foo/ - Track editor scroll delta excluding comment PSI ranges. OUT OF SCOPE (future cycles): - code↔preview scroll sync, - AI chatbot/command execution, - vector database integration, - full WriterSide-like topic tree UX parity. MANDATORY SEAMS (implemented now, behavior minimal/default): - TopicTreeService command API (add/move/remove/reorder/validate). - PluginCommandBus + CommandRegistry. - VectorStorePort. - PreviewSyncPort. - Feature flags for staged enablement. QUALITY: - SDD artifacts required this cycle. - 100% unit coverage for in-scope code."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - First Activation to Live Preview (Priority: P1)

A documentation author activates the plugin for the first time and gets a working live documentation preview without manual environment setup.

**Why this priority**: First-run activation is the core MVP value. If activation fails or is manual-heavy, the rest of the feature set is blocked.

**Independent Test**: Can be fully tested by enabling the plugin in a valid documentation project and verifying a live preview opens automatically after startup completes.

**Acceptance Scenarios**:

1. **Given** a project with valid documentation configuration and no prior plugin runtime setup, **When** the author activates the plugin, **Then** the plugin prepares an isolated runtime with required documentation tooling and starts a local preview server.
2. **Given** the preview server starts successfully, **When** the plugin detects the server base URL from startup output, **Then** a side-by-side preview opens in the IDE workspace using that detected URL.
3. **Given** runtime setup or server startup fails, **When** activation is attempted, **Then** the author receives a clear error message with next-step guidance and no broken preview pane is opened.

---

### User Story 2 - Navigate Docs and Update Preview Route (Priority: P2)

A documentation author uses a dedicated docs explorer and expects selected markdown files to open the matching preview route automatically.

**Why this priority**: Reliable file-to-route navigation is the core day-to-day authoring workflow after activation.

**Independent Test**: Can be fully tested by selecting files from the docs explorer and verifying each selection updates the preview to the expected route.

**Acceptance Scenarios**:

1. **Given** the docs explorer is visible, **When** the author selects `docs/index.md`, **Then** the preview updates to `/`.
2. **Given** the docs explorer is visible, **When** the author selects `docs/foo/index.md`, **Then** the preview updates to `/foo/`.
3. **Given** the docs explorer is visible, **When** the author selects `docs/foo.md`, **Then** the preview updates to `/foo/`.
4. **Given** nested documentation files under `docs/`, **When** the author selects a file, **Then** the preview route follows the same index-or-file conversion pattern for that path depth.

---

### User Story 3 - Capture Author Scroll Signals and Extensibility Seams (Priority: P3)

A product team needs the MVP to capture scroll movement signals (excluding comment ranges) and include minimal extension seams for future capabilities without enabling those capabilities yet.

**Why this priority**: These seams and scroll signals reduce rewrite risk in future cycles while keeping MVP behavior intentionally limited.

**Independent Test**: Can be fully tested through unit tests that verify scroll delta capture rules, seam API availability, default seam behavior, and feature-flag gating.

**Acceptance Scenarios**:

1. **Given** an open docs markdown file, **When** the author scrolls in the editor, **Then** the plugin records scroll delta changes while excluding comment-only PSI ranges from delta calculations.
2. **Given** seam interfaces for topic commands, command dispatch, vector storage, and preview sync are present, **When** they are invoked in MVP mode, **Then** they respond with defined minimal/default behavior and do not enable out-of-scope features.
3. **Given** staged rollout flags are configured, **When** MVP features initialize, **Then** only in-scope behavior is active by default.

### Edge Cases

- First activation is triggered in a project missing required documentation configuration.
- Server startup output is present but does not include a detectable base URL.
- The detected preview URL becomes unreachable after opening the preview pane.
- The selected file is outside the `docs/` tree or is not a markdown document.
- Route conversion encounters unusual names (spaces, uppercase letters, or deep nesting) and must still produce a valid route path.
- Scroll movement occurs entirely inside comment PSI ranges; no effective delta should be emitted.
- Scroll movement spans both comment and non-comment ranges; only non-comment movement contributes to delta.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST prepare a plugin-managed isolated runtime on first activation for projects that do not already have a valid plugin runtime.
- **FR-002**: System MUST install required documentation serving capability during first activation so authors can run live preview without manual dependency setup.
- **FR-003**: System MUST start a local documentation preview server during activation using default host and port values and an incremental refresh mode suitable for authoring feedback.
- **FR-004**: System MUST detect the preview base URL from server startup output before opening preview.
- **FR-005**: System MUST open a side-by-side preview pane in the IDE once a valid base URL is detected.
- **FR-006**: System MUST provide a dedicated docs explorer for browsing documentation markdown files.
- **FR-007**: System MUST update the active preview route when a markdown file is selected from the docs explorer.
- **FR-008**: System MUST map `docs/index.md` to `/`.
- **FR-009**: System MUST map `docs/<segment>/index.md` to `/<segment>/`.
- **FR-010**: System MUST map `docs/<segment>.md` to `/<segment>/`.
- **FR-011**: System MUST apply the same mapping rules consistently for nested paths under `docs/`.
- **FR-012**: System MUST track editor scroll delta for active docs markdown content while excluding comment PSI ranges from delta calculations.
- **FR-013**: System MUST expose a TopicTreeService command API seam supporting add, move, remove, reorder, and validate operations with defined minimal/default behavior in MVP.
- **FR-014**: System MUST provide a PluginCommandBus and CommandRegistry seam for command registration and dispatch with defined minimal/default behavior in MVP.
- **FR-015**: System MUST provide a VectorStorePort seam with defined minimal/default behavior in MVP.
- **FR-016**: System MUST provide a PreviewSyncPort seam with defined minimal/default behavior in MVP.
- **FR-017**: System MUST provide feature flags that control staged enablement of MVP and seam-related behavior, with default states documented.
- **FR-018**: System MUST include Software Design Description artifacts for all in-scope capabilities in this cycle.
- **FR-019**: System MUST achieve 100% unit test coverage for in-scope code in this cycle.
- **FR-020**: System MUST NOT include bidirectional code-to-preview scroll synchronization in this MVP.
- **FR-021**: System MUST NOT include AI chatbot or command execution assistance in this MVP.
- **FR-022**: System MUST NOT include vector database-backed behavior in this MVP.
- **FR-023**: System MUST NOT attempt full feature-parity topic tree user experience with WriterSide-like behavior in this MVP.

### Assumptions & Dependencies

- Authors use this MVP inside an IDE project containing a `docs/` documentation tree.
- Projects targeted by this MVP have documentation content that can be served locally once runtime setup succeeds.
- Documentation authors have permission to create and maintain a plugin-managed runtime in their local development environment.
- Feature-flag defaults are controlled by product owners to keep out-of-scope functionality disabled during MVP.
- Coverage and SDD completion are release-gating checks for this cycle.

### Key Entities *(include if feature involves data)*

- **Documentation Project**: The IDE project containing source docs, including the `docs/` tree and files selected for preview.
- **Runtime Setup State**: First-run and subsequent-run status indicating whether plugin-managed documentation runtime requirements are satisfied.
- **Preview Session**: The active local preview context containing server state, detected base URL, and currently selected route.
- **Route Mapping Rule**: Deterministic transformation rule from a docs markdown path to a preview route path.
- **Scroll Delta Record**: Captured editor scroll movement amount for docs content, with comment PSI ranges excluded.
- **Feature Flag**: Toggle that controls whether staged behavior is enabled or kept at default/minimal state.
- **Command Seam Contract**: Defined command and port-level contracts for topic tree actions, generic command dispatch, vector storage, and preview sync.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: At least 90% of first-time authors can activate the plugin and reach a visible live preview in under 5 minutes in a valid documentation project.
- **SC-002**: At least 95% of successful preview server startups result in automatic base URL detection and preview opening without manual URL entry.
- **SC-003**: Route conversion accuracy is 100% for the defined mapping patterns (`docs/index.md`, `docs/<segment>/index.md`, `docs/<segment>.md`) across approved test fixtures.
- **SC-004**: At least 95% of docs explorer file selections update the preview to the correct route within 2 seconds.
- **SC-005**: Scroll delta capture excludes comment PSI ranges with 100% correctness across the unit-test fixture suite.
- **SC-006**: Release readiness for this cycle requires completed SDD artifacts for all in-scope requirements and verified 100% unit test coverage for in-scope code.

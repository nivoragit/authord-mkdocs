# Feature Specification: Phase 2 - MkDocs Topic Tree Management

**Feature Branch**: `001-mkdocs-topic-tree`  
**Created**: 2026-02-13  
**Status**: In Progress (implementation and evidence updates applied through `T128`)  
**Input**: User description: "Create/update feature spec for: Phase 2 - MkDocs Topic Tree Management for JetBrains Plugin"

## Clarifications

### Session 2026-02-13

- Q: When adding a child to a page node, what transform policy should apply? → A: Auto-convert the page node into a section, preserve the original page as first child, then add the new child.
- Q: What should be the default behavior when deleting a topic backed by a markdown file? → A: Default to nav removal plus move file to Trash/Recovery with confirmation, while allowing explicit nav-only delete. Rationale: keeps nav/file sync while minimizing irreversible data loss.
- Q: On rename/move of a markdown topic, what link rewrite scope should apply by default? → A: Rewrite relative markdown links within `docs_dir` markdown files that reference the moved/renamed file; do not rewrite external URLs or non-markdown assets. Rationale: preserves authoring integrity with bounded mutation risk.
- Q: What default strategy should govern multi-instance discovery and switching? → A: Auto-discover only root `mkdocs.yml` as default instance; allow explicit add-instance by path for additional configs; persist last selected instance per project. Rationale: deterministic behavior with low false positives.
- Q: What should be the default reconciliation policy when `mkdocs.yml` nav and filesystem diverge? → A: Nav-first for structure; preserve nav ordering/hierarchy, report missing file references as validation issues, place extra files in unlinked bucket, and never perform automatic destructive deletion. Rationale: aligns with canonical-nav governance and non-destructive recovery.
- Q: What exact trigger set defines “relevant file changes” for reconciliation? → A: Trigger reconciliation on mkdocs config file create/update/delete/rename/move and `docs_dir` markdown create/update/delete/rename/move events. External rename/move transitions across `docs_dir` boundaries are handled non-destructively with validation + unlinked-bucket behavior.
- Q: How is OS support and path/case behavior specified for R-17? → A: Supported OS matrix is Windows/macOS/Linux with explicit normalization and case-sensitivity rules per OS.
- Q: How is “high-severity regression” defined and governed for compatibility? → A: Severity is classified in the test-plan traceability matrix and compatibility validation report; unresolved high-severity items are release blockers.
- Q: How is plan/spec NFR mismatch resolved? → A: Keep NFRs in scope and encode explicit requirements + acceptance criteria for startup/reconciliation latency, mutation feedback latency, and supported scale profile.
- Q: How are duplicated canonical-nav requirements handled? → A: Keep R-13/R-14 as canonical functional requirements; convert NAV-001/NAV-003 to cross-references to avoid duplicate normative text.
- Q: Is US2 dependent on US1 scaffolding? → A: US2 is functionally independent when a valid tree context exists; reusing US1 startup/reconciliation scaffolding is implementation convenience only.

### Session 2026-02-15

- Q: Which canonical run is the source of truth for SC-005 coverage-gate evidence? → A: Run `20260215T041517Z` on commit `bc04d71e120531c40553f8d41b1be2667c5a97d5` with both required gate commands PASS.
- Q: How are SC-001/SC-002/SC-003/SC-004/SC-006/SC-007 evidence artifacts tracked after TR1 continuation? → A: Evidence artifacts are published under `tests/integration/topic-tree/` and linked from `specs/001-mkdocs-topic-tree/test-plan-traceability.md`.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Reconcile Project Navigation at Startup (Priority: P1)

As a documentation author, I can open a MkDocs project and immediately see a reliable topic tree sourced from project navigation, so I can understand and manage structure without manual setup.

**Why this priority**: The tree must reliably load before any edit operation can be trusted.

**Independent Test**: Open projects with and without `mkdocs.yml` `nav`, and verify the plugin produces a stable tree view, an unlinked bucket, and validation results without requiring any mutation actions.

**Acceptance Scenarios**:

1. **Given** a valid `mkdocs.yml` with `nav`, **When** the project opens, **Then** the topic tree is built from `nav` and preserves nav order.
2. **Given** `mkdocs.yml` without `nav`, **When** the project opens, **Then** a deterministic fallback tree is built from `docs_dir` markdown files.
3. **Given** markdown files present in `docs_dir` but missing from `nav`, **When** the tree is built, **Then** those files appear under an unlinked files bucket.
4. **Given** broken paths, duplicate references, or malformed links in navigation data, **When** startup reconciliation runs, **Then** validation issues are surfaced without destructive writes.

---

### User Story 2 - Perform Topic Mutations with MkDocs Sync (Priority: P1)

As a documentation author, I can create and reorganize topics in the plugin tree, and the plugin keeps `mkdocs.yml` and markdown files synchronized behind the scenes.

**Why this priority**: This is the core Phase 2 capability and primary user value.

**Independent Test**: Execute each topic mutation operation and verify both tree state and project artifacts (`mkdocs.yml`, markdown files) remain synchronized and consistent.

**Dependency Clarification**: US2 is functionally independent from US1 when a valid preloaded tree context is provided (for example by fixtures or direct command setup). Reusing US1 startup/reconciliation components is implementation convenience only, not a mandatory prerequisite.

**Acceptance Scenarios**:

1. **Given** a selected topic node, **When** I create/add child/reparent/reorder/move/delete/rename, **Then** tree state, `mkdocs.yml` nav, and related file mutations are applied as one consistent change.
2. **Given** an existing markdown file not currently in nav, **When** I add it to navigation, **Then** it appears at the chosen tree location and nav is updated.
3. **Given** an external documentation URL, **When** I add an external link node, **Then** it appears in the tree and nav with link validation feedback.
4. **Given** any partial failure during a mutation, **When** sync execution completes, **Then** no partial destructive state remains and recovery behavior is recorded.

---

### User Story 3 - Manage Multi-Instance Configurations Safely (Priority: P2)

As a documentation maintainer, I can manage multiple MkDocs instances/configs in one workspace, with operations scoped to the chosen instance and without breaking existing MVP preview workflows.

**Why this priority**: Multi-config workflows are important but can build on single-instance mutation support.

**Independent Test**: Configure multiple instances, perform mutations in one instance, and verify no unintended changes occur in others while MVP preview behavior remains functional.

**Acceptance Scenarios**:

1. **Given** multiple registered instances, **When** I select one instance, **Then** tree operations apply only to that instance's configuration and docs scope.
2. **Given** existing MVP preview workflows, **When** Phase 2 features are enabled, **Then** preview start, route updates, and runtime behavior continue to work without regression.
3. **Given** public service/interface changes for topic-tree features, **When** consumers integrate, **Then** versioned contracts and migration guidance are available.

---

### Edge Cases

- `mkdocs.yml` exists but `nav` is empty, partially malformed, or mixed with valid entries.
- `docs_dir` contains deep nesting, duplicate filenames in different folders, or very large file counts.
- Nav references missing files, case-mismatched paths, or non-markdown targets.
- Same markdown file is referenced multiple times in nav.
- External link nodes contain malformed URL formats.
- Mutation target collides with an existing path during rename/move.
- Add-child is requested on a page node that currently has no children.
- Rename/move affects references outside `docs_dir` or non-markdown files.
- Concurrent edits happen from filesystem changes while tree mutations are in progress.
- Instance switching occurs mid-operation.
- Recovery path itself fails after a partially applied mutation.
- Active `mkdocs.yml`/`mkdocs.yaml` is externally renamed or moved.
- Markdown files are externally moved from inside `docs_dir` to outside `docs_dir`.
- Markdown files are externally moved from outside `docs_dir` into `docs_dir`.

## Out of Scope *(mandatory)*

- Replacing or redesigning existing MVP preview runtime behavior.
- AI chat, command execution assistants, or vector retrieval workflows.
- WriterSide-level visual parity beyond required Phase 2 topic-tree operations.
- Cross-project/global topic tree merges across unrelated MkDocs projects.
- New rendering/runtime features unrelated to topic-tree synchronization.

## Requirements *(mandatory)*

### Functional Requirements

- **R-01**: The system MUST build the topic tree from `mkdocs.yml` `nav` at startup when `nav` is present and parseable.
- **R-02**: The system MUST build a deterministic fallback tree from `docs_dir` markdown files when `nav` is missing.
- **R-03**: The system MUST expose an unlinked files bucket for markdown files in `docs_dir` that are not referenced by `nav`.
- **R-04**: The system MUST support topic operations: create, delete, rename, move, reorder, reparent, and add child.
- **R-04a**: When add-child is invoked on a page node, the system MUST auto-convert that page node into a section container, preserve the original page as the first child with its original title/path, and add the new child under the same container.
- **R-05**: The system MUST allow adding an existing markdown file into navigation at a user-selected location.
- **R-06**: The system MUST allow adding external link nodes into navigation.
- **R-07**: The system MUST support adding and selecting instances, and MUST scope all tree operations to the selected instance.
- **R-07a**: On project startup, the system MUST auto-discover root-level `mkdocs.yml` as the default instance.
- **R-07b**: Additional instances MUST be added explicitly by user-provided config path.
- **R-07c**: The last selected instance MUST be persisted and restored per project.
- **R-08**: The system MUST update `mkdocs.yml` navigation for every successful tree mutation.
- **R-09**: The system MUST synchronize file mutations with nav mutations for create, delete, rename, and move operations on markdown topics.
- **R-09a**: The default delete action for a topic linked to a markdown file MUST remove the topic from nav and move the file to a recoverable location (Trash/Recovery), with explicit user confirmation before execution.
- **R-09b**: The system MUST provide an explicit nav-only delete action that removes the nav entry and keeps the markdown file on disk.
- **R-09c**: For rename/move operations, the system MUST rewrite relative markdown links within `docs_dir` markdown files that reference the affected topic, and MUST NOT rewrite external URLs or non-markdown assets.
- **R-10**: The system MUST validate and report broken paths, duplicate references, and malformed links.
- **R-11**: The system MUST perform non-destructive conflict handling and recovery for mutation or reconciliation failures.
- **R-11a**: Recovery behavior for delete operations MUST support restoration of both nav entry and file reference when default delete was used.
- **R-12**: The system MUST reconcile `nav` and `docs_dir` on startup and on defined file-change triggers using an explicit conflict policy.
- **R-12a**: Reconciliation MUST use nav-first structural policy by default, preserving canonical nav hierarchy and ordering.
- **R-12b**: Reconciliation MUST represent nav entries with missing files as validation issues without destructive nav/file removal.
- **R-12c**: Reconciliation MUST place files that exist in `docs_dir` but are absent from nav into the unlinked files bucket.
- **R-12d**: Reconciliation MUST NOT perform automatic destructive deletion of files or nav entries.
- **R-12e**: The defined reconciliation trigger set MUST include: (a) active mkdocs config file (`mkdocs.yml`/`mkdocs.yaml`) create/update/delete/rename/move events, and (b) markdown create/update/delete/rename/move events under active `docs_dir`. External rename/move transitions across `docs_dir` boundaries MUST be handled non-destructively.
- **R-13**: Canonical source requirement: the system MUST treat `mkdocs.yml` `nav` as the canonical persisted navigation model (referenced by `NAV-001`).
- **R-14**: Deterministic serialization requirement: the system MUST serialize `mkdocs.yml` deterministically to minimize diff churn for unchanged logical structures (referenced by `NAV-003`).
- **R-15**: The system MUST preserve existing MVP preview behavior and not break current preview workflows.
- **R-15a**: Compatibility regression severity MUST be classified using a single release rubric, and any unresolved high-severity regression MUST block release.
- **R-16**: Public topic-tree interfaces/services changed in this cycle MUST be versioned and documented with usage contracts.
- **R-17**: The implementation MUST maintain platform-neutral path and execution behavior across supported operating systems: Windows, macOS, and Linux.
- **R-17a**: Path normalization and comparison MUST follow the OS matrix defined in this spec, including canonical separators and explicit case-sensitivity expectations.
- **R-18**: In-scope production modules for this cycle MUST satisfy the 100% unit coverage gate.
- **R-19**: Startup tree load plus initial reconciliation MUST meet the cycle performance target for reference project size.
- **R-20**: Mutation command feedback (success/failure) MUST meet the cycle responsiveness target for local operations.
- **R-21**: The cycle scope MUST support the declared instance/file scale profile without cross-instance mutation leakage.
- **R-22**: Topic mutation workflows (US2 scope) MUST be executable independently of startup/watcher scaffolding when a valid tree context is supplied.

### Acceptance Criteria per Requirement

- **R-01 Acceptance**:
1. Startup tree order matches `mkdocs.yml` nav order exactly.
2. Nested nav structures are reflected in tree hierarchy.

- **R-02 Acceptance**:
1. Same file set always yields same fallback tree ordering.
2. Fallback tree generation is independent of nondeterministic filesystem listing order.

- **R-03 Acceptance**:
1. Files not referenced in nav are listed under unlinked bucket.
2. Linking an unlinked file removes it from the bucket in the next state refresh.

- **R-04 Acceptance**:
1. Each operation produces expected tree structure updates.
2. Operation results are persisted to navigation state.
3. Add-child on a page node converts that node to a section container without losing the original page entry.
4. After conversion, the preserved original page is first child and the newly added node is inserted under the same container.

- **R-05 Acceptance**:
1. User can choose an existing markdown file and insert it at a selected tree position.
2. Inserted file becomes a standard nav node with consistent ordering behavior.

- **R-06 Acceptance**:
1. External link node creation supports title + URL entry.
2. Malformed links are rejected with validation feedback.

- **R-07 Acceptance**:
1. Instance selection clearly scopes operations to one instance.
2. Mutations in one instance do not alter another instance's nav/files.
3. Root-level `mkdocs.yml` is discovered automatically as default on startup.
4. Non-default instances are created only through explicit add-instance actions.
5. Last selected instance is restored when reopening the same project.

- **R-08 Acceptance**:
1. Each successful mutation updates `mkdocs.yml` without manual user edits.
2. No mutation is marked successful if nav update fails.

- **R-09 Acceptance**:
1. Create/rename/move/delete topic operations apply matching markdown file changes.
2. File and nav states stay aligned after operation completion.
3. Default delete removes nav entry and moves the file to a recoverable location after confirmation.
4. Nav-only delete removes only nav entry and leaves file discoverable through reconciliation/unlinked handling.
5. Rename/move updates impacted relative markdown links within `docs_dir` markdown files.
6. External URLs and non-markdown asset references remain unchanged during rewrite.

- **R-10 Acceptance**:
1. Broken paths, duplicate references, and malformed links are each detected.
2. Validation results identify affected nodes/entries clearly.

- **R-11 Acceptance**:
1. Partial mutation failure does not leave destructive partial state.
2. Recovery outcome and next-action guidance are surfaced to users.
3. Delete recovery can restore removed nav entry and recoverable file pairing when restoration is requested.

- **R-12 Acceptance**:
1. Startup reconciliation runs for nav/docs mismatch detection.
2. Only the trigger categories defined in `R-12e` initiate automatic scoped reconciliation checks.
3. Canonical nav ordering/hierarchy remains preserved after reconciliation.
4. Missing file references from nav are reported as validation issues.
5. Files outside nav appear in unlinked bucket after reconciliation.
6. No automatic destructive deletion is performed during reconciliation.

- **R-12e Acceptance**:
1. Config file create/update/delete/rename/move events for active instance config trigger reconciliation.
2. Markdown create/update/delete/rename/move events inside active `docs_dir` trigger reconciliation.
3. External rename/move from inside `docs_dir` to outside `docs_dir` does not auto-rewrite nav; existing nav reference is reported as a broken-path validation issue.
4. External move from outside `docs_dir` into `docs_dir` creates an unlinked bucket entry until explicitly linked.
5. Rename/move events wholly outside project root are logged as external events and do not trigger destructive operations.

- **R-13 Acceptance**:
1. Persisted canonical navigation source is `mkdocs.yml` nav.
2. Tree model is derived from canonical nav or documented fallback mode.

- **R-14 Acceptance**:
1. Equivalent logical nav structures produce equivalent serialized output.
2. Repeated save cycles without logical changes do not create churn-only diffs.

- **R-15 Acceptance**:
1. Existing MVP preview start/update flows remain functional.
2. Topic-tree features do not block legacy preview workflows.

- **R-15a Acceptance**:
1. Severity classification source of truth is recorded in the test plan traceability matrix and compatibility validation report.
2. High-severity regression is defined as at least one of: preview cannot start from existing MVP entrypoints, preview is unreachable/blank due to runtime URL detection failure, plugin-attributed IDE crash/freeze over 5 seconds in preview workflow, or destructive data-loss behavior in compatibility path.
3. Release gate fails when any high-severity compatibility regression remains unresolved.

- **R-16 Acceptance**:
1. Each changed public interface includes version identifier and contract notes.
2. Backward compatibility or migration guidance is documented for interface changes.

- **R-17 Acceptance**:
1. Path handling is validated on Windows, macOS, and Linux.
2. Normalization and case-comparison behavior follow `R-17a` matrix rules.
3. No OS-specific manual workflow is required by feature behavior.

- **R-17a Acceptance**:
1. Internal canonical path keys use `/`, resolve `.` and `..`, and collapse duplicate separators.
2. Windows and macOS path comparisons are case-insensitive while preserving original on-disk casing for writes and display.
3. Linux path comparisons are case-sensitive.
4. Case-only rename handling follows OS rules without introducing duplicate-reference false positives.

- **R-18 Acceptance**:
1. Coverage report for in-scope production modules reaches 100% unit coverage.
2. CI/build gate fails when threshold is not met.

- **R-19 Acceptance**:
1. For reference project datasets up to 1,000 markdown files, startup tree load plus initial reconciliation completes within 2 seconds in at least 95% of runs.
2. If threshold is exceeded, operation still completes non-destructively and records performance telemetry.

- **R-20 Acceptance**:
1. For reference project datasets up to 1,000 markdown files, mutation command success/failure feedback is emitted within 300 ms at p95.
2. The responsiveness target applies to add/move/remove/rename/reparent/reorder flows.

- **R-21 Acceptance**:
1. Up to 5 registered instances in one project are supported with isolated mutation scope.
2. Up to 10,000 markdown files across registered instances can be reconciled without crash or destructive corruption.

- **R-22 Acceptance**:
1. Mutation command tests execute successfully with a valid preloaded tree context without requiring startup watcher initialization.
2. US2 release acceptance can be evaluated independently from US1 startup-loading UI path.

### Assumptions

- Project-level permissions allow creating, renaming, moving, and deleting markdown files within configured docs scope.
- `mkdocs.yml` and docs files are editable by the plugin during operations.
- Conflicts are resolved using non-destructive policies that prefer preserving user data over forced overwrite.
- Existing MVP preview runtime behavior remains active and unchanged unless required for compatibility.
- Supported OS matrix for `R-17`:

| OS | Supported | Path Normalization | Comparison Rule |
|----|-----------|--------------------|-----------------|
| Windows | Yes | Normalize separators to `/`; resolve `.`/`..`; collapse duplicate separators; preserve drive-letter casing for display | Case-insensitive comparison, preserve original on-disk casing for writes/display |
| macOS | Yes | Normalize separators to `/`; resolve `.`/`..`; collapse duplicate separators | Case-insensitive comparison, preserve original on-disk casing for writes/display |
| Linux | Yes | Normalize separators to `/`; resolve `.`/`..`; collapse duplicate separators | Case-sensitive comparison |

### Navigation Sync Constraints *(mandatory when topic-tree/navigation scope exists)*

- **NAV-001**: Canonical nav source is defined by `R-13`; this constraint references that requirement for navigation-scope traceability.
- **NAV-002**: Tree/file/navigation mutations MUST be atomic, or a compensating rollback procedure MUST be defined and documented.
- **NAV-003**: Deterministic serialization is defined by `R-14`; this constraint references that requirement for navigation-scope traceability.
- **NAV-004**: Startup and file-change reconciliation between `nav` and `docs_dir` MUST follow an explicit conflict handling policy.

### Public Interfaces & Versioning *(mandatory when public interfaces change)*

- **PI-001**: Topic-tree command and mutation public interfaces MUST be versioned and published with compatibility notes.
- **PI-002**: Existing MVP preview public behaviors MUST remain backward compatible in Phase 2.
- **PI-003**: Public usage contracts MUST define inputs, outputs, constraints, and failure modes for tree mutation and reconciliation operations.

### Documentation Deliverables *(mandatory)*

- **DOC-001**: Feature spec is updated for Phase 2 delivered scope.
- **DOC-002**: Technical design notes are updated for topic-tree architecture, sync flows, and conflict handling.
- **DOC-003**: Operational runbook is updated for startup reconciliation, mutation failures, and recovery.
- **DOC-004**: Test plan and requirements-to-tests traceability matrix are updated with R-01..R-22 mapping, including compatibility regression severity classification.
- **DOC-005**: Changelog is updated; migration notes are included for any compatibility impact.
- **DOC-006**: New/changed public services include usage guidance (purpose, inputs, outputs, errors, and relevant examples).
- A cycle is incomplete until required documentation artifacts are current and both tests and scoped coverage gates are green.

### Compatibility + Migration Impact Notes

- Existing MVP preview workflows remain supported and unchanged from user perspective.
- Phase 2 introduces additive topic-tree capabilities; no mandatory migration is required for projects already using preview-only MVP workflows.
- Projects without `nav` continue to function through deterministic fallback behavior.
- If interface versions change, migration notes MUST specify compatibility window and upgrade path.
- Compatibility severity source of truth is the test-plan traceability matrix plus runIde compatibility validation report; unresolved high-severity regressions are release blockers.

### Key Entities *(include if feature involves data)*

- **TopicNode**: A navigable item representing a markdown topic, external link, or grouping node with title, location reference, and ordering metadata.
- **TopicTree**: Instance-scoped hierarchical model of topic nodes used for UI rendering and mutation operations.
- **NavigationDocument**: Canonical persisted navigation structure mapped to `mkdocs.yml` nav.
- **InstanceContext**: Configuration scope for one MkDocs instance, including nav source and docs scope.
- **UnlinkedFileEntry**: Markdown file discovered in docs scope but absent from canonical navigation.
- **ValidationIssue**: Structured representation of path, duplicate, or link integrity problems.
- **MutationTransaction**: Logical change unit covering tree, nav, and file mutations with recovery metadata.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: In at least 95% of startup runs on supported project samples (up to 1,000 markdown files), tree initialization plus initial reconciliation completes within 2 seconds.
- **SC-002**: For all in-scope topic mutation operations, zero unresolved partial-sync states are observed in release-gate validation.
- **SC-003**: Validation checks detect 100% of seeded broken-path, duplicate-reference, and malformed-link test cases in acceptance datasets.
- **SC-004**: Existing MVP preview workflows maintain parity, with no unresolved high-severity regressions per `R-15a` rubric.
- **SC-005**: In-scope production modules meet the 100% unit coverage gate in CI for this cycle.
- **SC-006**: Mutation command success/failure feedback meets a p95 threshold of 300 ms for reference datasets up to 1,000 markdown files.
- **SC-007**: Phase 2 supports up to 5 instances and 10,000 markdown files across instances without cross-instance mutation leakage.

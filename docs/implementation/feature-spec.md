# Feature Spec (Implemented): IntelliJ MkDocs Plugin MVP

## 1. Objective

Deliver a bounded MVP that enables documentation authors to activate MkDocs preview quickly, navigate docs-to-preview routes reliably, and keep extension seams in place for future cycles.

## 2. In Scope (Implemented)

1. First activation bootstraps a plugin-managed runtime using `uv` and installs `mkdocs`.
2. Dedicated docs explorer service for markdown file discovery under `docs/`.
3. Activation starts a single MkDocs server instance per project using `mkdocs serve` defaults (no hardcoded host/port).
4. Base URL is detected from startup output and preview state is opened.
5. Selecting docs markdown updates preview route using these rules:
- `docs/index.md` -> `/`
- `docs/foo/index.md` -> `/foo/`
- `docs/foo.md` -> `/foo/`
- Nested paths use the same mapping pattern
6. Semantic scroll delta tracking excludes comment ranges.
7. Mandatory extension seams are implemented with minimal/default behavior:
- `TopicTreePort` command API
- `PluginCommandBus` + `CommandRegistry`
- `PreviewSyncPort`
- `VectorStorePort`
- feature flags for staged enablement
8. Topic-tree runway includes vendor-independent domain primitives and mutation commands:
- `TopicId`, `TopicNode`, `TopicTree`, `TopicOrder`, `TopicLink`, `TopicMetadata`
- mutation command support for `add`, `move`, `remove`, `rename`, `reparent`, `reorder`
9. Software design artifacts are present and maintained in this cycle.
10. Scoped code enforces 100% unit test line coverage.

## 3. Out of Scope (Explicitly Excluded)

1. Code-to-preview and preview-to-code scroll synchronization.
2. AI chatbot or command execution features.
3. Vector database-backed behavior.
4. WriterSide-like full topic tree UX parity.

## 4. Primary Users and Scenarios

## 4.1 Documentation Author: Activation + Preview

Expected outcome:
- Author enables plugin flow and reaches live preview without manual runtime setup.

Acceptance outcome:
- Runtime bootstrap runs once per project path.
- Runtime start returns already-running status if server already exists for the same project.
- Base URL detection is required before preview open success.

## 4.2 Documentation Author: Docs Explorer Navigation

Expected outcome:
- Selecting a markdown file in `docs/` navigates preview to the mapped route.

Acceptance outcome:
- Route mapping behavior is deterministic for root/index/file/nested patterns.
- Unsupported paths produce a controlled non-applied navigation result.

## 4.3 Product/Platform Team: Future-Proof Seams

Expected outcome:
- Extension contracts exist now but do not enable future-cycle features.

Acceptance outcome:
- Ports and bus/registry seams are present and contract-tested.
- Default adapters are no-op/in-memory and guarded by feature policies.

## 5. Implemented Functional Requirements

| ID | Requirement |
|----|-------------|
| FR-001 | Create plugin-managed runtime on first activation |
| FR-002 | Install MkDocs tooling during first activation |
| FR-003 | Start MkDocs serving with runtime defaults (no hardcoded host/port) |
| FR-004 | Detect base URL from startup output |
| FR-005 | Open side-by-side preview state after URL detection |
| FR-006 | Provide dedicated docs explorer capability |
| FR-007 | Update preview route on docs markdown selection |
| FR-008 | Map `docs/index.md` to `/` |
| FR-009 | Map `docs/<segment>/index.md` to `/<segment>/` |
| FR-010 | Map `docs/<segment>.md` to `/<segment>/` |
| FR-011 | Apply same mapping for nested paths |
| FR-012 | Track semantic scroll delta excluding comments |
| FR-013 | Implement TopicTree command seam (`add/move/remove/rename/reparent/reorder/validate`) |
| FR-014 | Implement PluginCommandBus + CommandRegistry seams |
| FR-015 | Implement VectorStorePort seam |
| FR-016 | Implement PreviewSyncPort seam |
| FR-017 | Provide staged feature-flag policy |
| FR-018 | Provide SDD/DocOps artifacts |
| FR-019 | Enforce 100% scoped unit coverage |
| FR-020 | Exclude code-preview/preview-code sync |
| FR-021 | Exclude AI chatbot/command execution |
| FR-022 | Exclude vector database integration behavior |
| FR-023 | Exclude WriterSide-like full parity UX |

## 6. Constraints and Assumptions

1. Project contains a markdown docs tree under `docs/`.
2. Host environment allows `uv` execution and local runtime directory creation.
3. Runtime lifecycle is single-instance per project ID.
4. Feature flags default to MVP-only behavior.
5. Quality gate requires docs + tests + coverage before marking cycle complete.

## 7. Delivered Artifacts

1. Specs and design artifacts in `specs/002-intellij-mkdocs-mvp/`.
2. ADRs for seam boundaries in `specs/002-intellij-mkdocs-mvp/adrs/`.
3. Contract schemas in `specs/002-intellij-mkdocs-mvp/contracts/`.
4. Runbook and traceability matrix in `specs/002-intellij-mkdocs-mvp/`.
5. CI gate in `.github/workflows/ci.yml`.

## 8. Current Status

Implemented and test-verified in repository scope. Runtime and UI flows are implemented as plugin service logic with modular boundaries; full IntelliJ extension-point wiring is intentionally minimal in this cycle.

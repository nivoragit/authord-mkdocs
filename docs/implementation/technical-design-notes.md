# Technical Design Notes: IntelliJ MkDocs MVP

## 1. Architecture Overview

The implementation uses a multi-module architecture to separate domain logic, runtime process control, plugin-facing orchestration, extension contracts, and default adapters.

## 1.1 Modules

1. `modules/core-domain`
- Topic tree domain model and invariants.
- Route mapping logic for docs file to preview route.
- Semantic scroll delta logic excluding comment ranges.
- Feature policy model and runtime lifecycle enum.

2. `modules/mkdocs-runtime-adapter`
- Runtime bootstrap via `uv` and `mkdocs` install.
- MkDocs process lifecycle manager (single instance per project).
- Base URL parsing from startup output.

3. `modules/ui-plugin`
- Activation orchestration.
- Docs explorer service.
- File selection publisher.
- Navigation coordinator and preview pane state coordinator.
- Composition root wiring seam defaults.

4. `modules/extension-ports`
- `TopicTreePort` and typed command/result DTOs.
- `PluginCommandBus` + `CommandRegistry` contracts.
- `PreviewSyncPort` contract.
- `VectorStorePort` contract.

5. `modules/infra-defaults`
- In-memory command registry.
- No-op preview sync adapter.
- No-op vector store adapter.

## 2. Key Design Decisions

1. Keep future extensions behind ports and default adapters now, without enabling out-of-scope behavior.
2. Enforce domain invariants in a central topic tree aggregate.
3. Keep runtime lifecycle single-instance per project ID to avoid duplicate mkdocs servers.
4. Use explicit route mapping service to make file->route behavior deterministic and testable.
5. Use feature-flag policy defaults to allow MVP flow and disallow future-cycle feature paths.

## 3. Core Contracts and Data Structures

## 3.1 Topic Tree Command Boundary

- Command types: `ADD`, `MOVE`, `REMOVE`, `RENAME`, `REPARENT`, `REORDER`, `VALIDATE`.
- Results: `SUCCESS`, `REJECTED`, `FAILED` with version and violations.
- Domain aggregate enforces:
- parent existence
- unique node IDs
- non-negative indices
- root removal protection
- reorder sibling-set exactness
- cycle prevention
- rename title validation and reparent root/cycle protections

Provider-independent topic runway primitives include:
`TopicId`, `TopicNode`, `TopicTree`, `TopicOrder`, `TopicLink`, and `TopicMetadata`.

## 3.2 Runtime Lifecycle

- Lifecycle states include:
`UNINITIALIZED`, `BOOTSTRAPPING`, `READY`, `SERVING`, `RESTARTING`, `STOPPING`, `STOPPED`, `FAILED`, `DISPOSED`.
- Runtime start command shape by default:
`mkdocs serve`
- Host/port are not hardcoded in runtime manager defaults.

## 3.3 Preview Routing Rules

- `docs/index.md` -> `/`
- `docs/foo/index.md` -> `/foo/`
- `docs/foo.md` -> `/foo/`
- nested index/file rules preserved.
- non-docs or non-markdown selections are rejected from navigation.

## 3.4 Scroll Semantics

- `ScrollSemanticService` computes scroll delta while subtracting overlap against comment ranges.
- Pure-comment movement yields no effective semantic delta.

## 4. Main Flows

## 4.1 Activation Flow

1. Validate feature policy allows MVP-only execution.
2. Bootstrap runtime (`uv venv ...`, `uv pip install mkdocs`) unless already bootstrapped for project path.
3. Start or reuse per-project mkdocs runtime process.
4. Detect base URL from startup output.
5. Open preview session with detected base URL.
6. Return activation result with success/failure reason.

## 4.2 Explorer -> Preview Navigation Flow

1. Docs explorer discovers markdown files under `docs/`.
2. Selection is published as event.
3. Navigation coordinator maps path to route.
4. Preview pane session navigates to route.
5. Failure handler provides explicit message when mapping is not applicable.

## 4.3 Extension Seam Wiring

1. Composition root builds default `InMemoryCommandRegistry`.
2. Registers topic command handlers (`ADD/MOVE/REMOVE/RENAME/REPARENT/REORDER/VALIDATE`).
3. Builds `DefaultPluginCommandBus` against registry.
4. Wires no-op `PreviewSyncPort` and no-op `VectorStorePort`.

## 5. Function-Level Usage Guidance

The following usage contracts are the authoritative references for new/changed MVP services.

| Service / Function | Purpose | Inputs | Outputs | Errors / Failure Modes | Usage Example |
|--------------------|---------|--------|---------|------------------------|---------------|
| `UvBootstrapService.bootstrap(projectPath)` | Prepare plugin-managed runtime and install MkDocs once per project path. | `projectPath: String` | `BootstrapResult(success, runtimePath, executedCommands, skipped, errorMessage)` | Returns `success=false` when `uv venv` or `uv pip install mkdocs` exits non-zero; `errorMessage` captures stderr fallback text. | Call during activation before runtime start; abort activation if `success=false`. |
| `MkdocsProcessManager.start(projectId, workingDir, config)` | Start or reuse a single MkDocs process for one project ID. | `projectId`, `workingDir`, optional `RuntimeServerConfig(extraArgs)` | `RuntimeStartResult(started, processId, command, alreadyRunning)` | Reuses existing process when alive; launch failure surfaces via `started=false` and caller-owned launcher behavior. | Call after bootstrap and before URL detection. |
| `MkdocsProcessManager.stop/restart/dispose` | Lifecycle controls with dispose-safe cleanup semantics. | `projectId` | `Boolean` for stop/dispose, `RuntimeStartResult` for restart | `stop`/`dispose` return `false` when no process exists; `restart` falls back to fresh start when prior state is absent. | Call from plugin shutdown and explicit restart flows. |
| `BaseUrlDetector.detectBaseUrl(startupOutput)` | Detect runtime preview base URL from process stdout text. | `startupOutput: String` | URL string or `null` | Returns `null` when no valid URL token exists; caller treats this as activation failure (`BASE_URL_NOT_FOUND`). | Parse captured startup logs before opening preview. |
| `PluginActivationService.activate(...)` | Orchestrate policy guard, bootstrap, runtime start, URL detection, and preview open. | `projectId`, `projectPath`, `startupOutput`, optional `runtimeConfig` | `ActivationResult(success, reason, message, previewUrl)` | Fails with typed reasons: policy disabled, bootstrap failure, runtime failure, URL missing. | Entry point for first-use and repeated activation flows. |
| `DocsExplorerService.discoverMarkdownFiles(projectPath, docsRoot)` | Discover markdown files under docs root using OS-neutral path traversal. | `projectPath`, optional `docsRoot` | `List<String>` project-relative paths | Returns empty list when docs root does not exist or is not a directory. | Populate explorer model before user navigation. |
| `RouteMappingService.mapToRoute(selectedPath, docsRoot)` | Convert docs markdown file path to normalized preview route. | `selectedPath`, optional `docsRoot` | Route string (for example `/guide/`) or `null` | Returns `null` for non-docs or non-markdown paths. | Use before preview navigation to enforce deterministic routing rules. |
| `NavigationCoordinator.onFileSelected(projectId, selectedPath)` | Apply route mapping and update preview pane state for current project. | `projectId`, `selectedPath` | `NavigationResult(applied, route, message)` | Non-applicable mappings produce `applied=false` with failure message from `PreviewNavigationFailureHandler`. | Handle explorer selection events. |
| `ScrollSemanticService.calculateSemanticDelta(...)` | Compute editor scroll delta excluding overlaps with comment ranges. | Previous/current offsets, viewport size, list of `CommentRange` | `Int` effective semantic delta | Returns `0` for non-movement or fully-commented movement window. | Forward computed semantic delta to `PreviewSyncPort` seam. |
| `TopicTreePort.execute(command)` and `PluginCommandBus.dispatch(command)` | Execute command-based topic mutations through registry-resolved handlers. | Typed `TopicTreeCommand` DTOs | `TopicTreeCommandResult(status, message, violations)` | Rejected results include code/message violations (`NODE_MISSING`, `CYCLE`, etc.); unregistered command handlers return typed failure from bus implementation. | Register handlers in composition root; dispatch from mutation entry points. |
| `PreviewSyncPort.onEditorScrollSemanticDelta(...)` | Extension seam for preview sync capabilities while MVP keeps default behavior no-op. | `projectId`, `documentPath`, semantic `delta` | No direct return value | MVP default adapter intentionally performs no action; future adapters must preserve semantic delta contract (comments already excluded). | Invoke after computing semantic scroll delta events. |
| `VectorStorePort.upsert/search` | Extension seam for future vector-backed retrieval with MVP-safe defaults. | Upsert: `documentId`, `content`; Search: `query`, `limit` | Upsert: none; Search: `List<String>` provider-specific hits | MVP default adapter returns empty search results and stores nothing by design. | Leave wired for extension readiness without enabling vector behavior in MVP. |

## 6. Quality and Governance Design

1. Root Gradle config applies Jacoco line coverage rule minimum `1.0` for scoped modules.
2. CI workflow enforces `clean test jacocoTestCoverageVerification`.
3. Policy tests verify required SDD/implementation docs artifacts, coverage scope configuration, runtime neutrality constraints, and KDoc coverage for public declarations.
4. ADRs capture seam decisions for future extension cycles.
5. Public seam/service interfaces are version-tagged in source KDoc (`API Version: 1.0.0`).

## 7. Known Boundaries

1. Plugin metadata exists, but full IntelliJ extension-point UI wiring is intentionally limited in this cycle.
2. Contract schemas (OpenAPI/AsyncAPI) are internal design/verification contracts, not public network APIs.
3. Future-cycle capabilities remain disabled by default feature policy.

## 8. Key Implementation Files

| Area | Primary Files |
|------|---------------|
| Activation flow | `modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/PluginActivationService.kt`, `modules/mkdocs-runtime-adapter/src/main/kotlin/com/authord/mkdocs/runtime/UvBootstrapService.kt`, `modules/mkdocs-runtime-adapter/src/main/kotlin/com/authord/mkdocs/runtime/MkdocsProcessManager.kt`, `modules/mkdocs-runtime-adapter/src/main/kotlin/com/authord/mkdocs/runtime/BaseUrlDetector.kt` |
| Docs navigation flow | `modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/DocsExplorerService.kt`, `modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/NavigationCoordinator.kt`, `modules/core-domain/src/main/kotlin/com/authord/mkdocs/core/navigation/RouteMappingService.kt`, `modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/PreviewPaneCoordinator.kt` |
| Scroll semantics | `modules/core-domain/src/main/kotlin/com/authord/mkdocs/core/scroll/ScrollSemanticService.kt` |
| Topic tree seam + domain | `modules/extension-ports/src/main/kotlin/com/authord/mkdocs/ports/TopicTreePort.kt`, `modules/extension-ports/src/main/kotlin/com/authord/mkdocs/ports/topic/TopicTreeCommandDtos.kt`, `modules/core-domain/src/main/kotlin/com/authord/mkdocs/core/topic/TopicTreeAggregate.kt` |
| Command bus seam | `modules/extension-ports/src/main/kotlin/com/authord/mkdocs/ports/command/PluginCommandBus.kt`, `modules/extension-ports/src/main/kotlin/com/authord/mkdocs/ports/command/DefaultPluginCommandBus.kt`, `modules/infra-defaults/src/main/kotlin/com/authord/mkdocs/defaults/command/InMemoryCommandRegistry.kt` |
| Preview/vector seams | `modules/extension-ports/src/main/kotlin/com/authord/mkdocs/ports/preview/PreviewSyncPort.kt`, `modules/infra-defaults/src/main/kotlin/com/authord/mkdocs/defaults/preview/NoOpPreviewSyncAdapter.kt`, `modules/extension-ports/src/main/kotlin/com/authord/mkdocs/ports/vector/VectorStorePort.kt`, `modules/infra-defaults/src/main/kotlin/com/authord/mkdocs/defaults/vector/NoOpVectorStoreAdapter.kt` |
| Feature policy | `modules/core-domain/src/main/kotlin/com/authord/mkdocs/core/flags/FeatureFlagPolicy.kt`, `modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/FeatureFlagPolicyService.kt` |
| Quality gates | `build.gradle.kts`, `.github/workflows/ci.yml`, `modules/core-domain/src/test/kotlin/com/authord/mkdocs/core/quality/DocumentationArtifactsPolicyTest.kt`, `modules/core-domain/src/test/kotlin/com/authord/mkdocs/core/quality/CoverageScopePolicyTest.kt` |

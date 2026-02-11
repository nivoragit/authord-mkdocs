# Technical Design Notes: IntelliJ Plugin Shell Cycle

## 1. Architecture Overview

This cycle adds plugin entry wiring so the current MVP can run as an IntelliJ plugin shell while keeping existing runtime/domain behavior intact.

### 1.1 Shell Modules and Responsibilities

1. `build.gradle.kts` + `gradle.properties`
- Enables IntelliJ Platform Gradle plugin setup.
- Defines IDE target and plugin compatibility metadata.

2. `modules/ui-plugin/src/main/resources/META-INF/plugin.xml`
- Declares platform dependency.
- Registers tool window factory and Start MkDocs Preview action.
- Registers project-scoped runtime integration service.

3. `modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/intellij/`
- `MkdocsToolWindowFactory`: minimal tool-window shell panel.
- `StartMkdocsAction`: action-system entry point for runtime handoff.
- `PluginRuntimeIntegrationService`: project-scoped adapter that delegates to existing activation/runtime services.

4. Existing runtime/domain modules (reused, not rewritten)
- `UvBootstrapService`
- `MkdocsProcessManager`
- `BaseUrlDetector`
- `PluginActivationService`

## 2. Plugin Entry Architecture

## 2.1 Tool Window Path

1. IntelliJ loads plugin descriptor and registers `MkdocsToolWindowFactory`.
2. Tool window factory creates minimal shell content panel.
3. Tool window content path auto-starts preview through `PluginRuntimeIntegrationService.startPreview(TOOL_WINDOW)`.

## 2.2 Action Path

1. IntelliJ registers `StartMkdocsAction`.
2. `update` computes visibility/enabled state from project context and runtime readiness.
3. `actionPerformed` delegates to `PluginRuntimeIntegrationService.startPreview(ACTION)`.

## 2.3 Runtime Handoff Path

1. Project-scoped runtime integration service collects project path + startup output context.
2. Service delegates to existing `PluginActivationService`.
3. Activation flow reuses existing bootstrap, runtime manager, and base-URL detection.
4. Single-instance runtime behavior remains controlled by `MkdocsProcessManager`.

## 2.4 Session Stabilization Path (2026-02-11)

1. Runtime command path uses `mkdocs serve --livereload --dirty`.
2. Runtime process is launched through a parent-PID guard script so MkDocs exits when IDE exits.
3. Typing in active docs markdown schedules preview refresh after document save.
4. Scroll sync maps editor viewport percentage directly to preview viewport percentage (`0.0..1.0`).
5. Scroll listener handles `VisibleAreaEvent.oldRectangle == null` safely to prevent runIde project-load crash.

## 3. Lifecycle and Invariants

1. One runtime manager instance per IntelliJ project service instance.
2. Runtime can only start when:
- project base path exists,
- MVP feature policy allows execution,
- runtime is not already active for the same project.
3. Dispose path must call runtime stop/dispose safely.
4. No hardcoded runtime host/port values are introduced in plugin shell code.

## 4. Function-Level Usage Guidance

The entries below are the usage contracts for new/changed public plugin-shell services.

| Function/Service | Purpose | Inputs | Outputs | Errors/Failure Modes | Usage Example |
|------------------|---------|--------|---------|----------------------|---------------|
| `PluginRuntimeIntegrationService.canStartPreview()` | Determine if Start action/tool-window trigger should be enabled. | Project-scoped state and feature policy. | `Boolean` readiness. | Returns `false` when project path is missing, policy blocks MVP flow, or runtime is already running. | `if (service.canStartPreview()) { ... }` |
| `PluginRuntimeIntegrationService.startPreview(trigger)` | Delegate preview start to existing activation/runtime orchestration. | `trigger: ACTION or TOOL_WINDOW`; project base path; startup output provider text. | `ActivationResult` with success flag, reason, and message/URL. | `START_FAILED` when path missing; activation failure reasons from existing activation service. | `service.startPreview(PreviewStartTrigger.ACTION)` |
| `PluginRuntimeIntegrationService.stopPreview()` | Stop runtime for current project. | none (project-scoped). | `Boolean` stopped state. | Returns `false` when no active runtime exists. | `service.stopPreview()` |
| `PluginRuntimeIntegrationService.updateFeatureFlags(policy)` | Override active policy for lifecycle/guard behavior. | `FeatureFlagPolicy`. | none. | Can disable action/runtime readiness when MVP flag is false. | `service.updateFeatureFlags(FeatureFlagPolicy(mvpEnabled = false))` |
| `StartMkdocsAction.update(event)` | Apply action visibility/enabled state in action system. | `AnActionEvent` with project context. | UI presentation state update. | Hidden+disabled when project is missing; disabled when runtime cannot start. | Registered action in `plugin.xml`; invoked by IntelliJ action updates. |
| `StartMkdocsAction.actionPerformed(event)` | Trigger runtime handoff from action invocation. | `AnActionEvent` with project context. | Delegates to runtime integration service. | No-op when project missing or runtime start is not allowed. | User runs **Start MkDocs Preview** from Tools menu. |
| `MkdocsToolWindowFactory.createToolWindowContent(project, toolWindow)` | Build/register shell content and auto-start preview for MkDocs tool window. | IntelliJ `Project` + `ToolWindow`. | Tool window content registered; preview start attempted. | If runtime cannot start, failure is reported and tool window stays available. | IntelliJ constructs tool window after plugin load. |
| `MkdocsToolWindowFactory.scheduleTypingRefresh(...)` | Refresh preview route for active docs markdown after typing. | project/runtime/preview context, selected path, optional document. | `Boolean` scheduled-state. | Returns `false` when runtime inactive, path outside docs markdown, or editor is not active file. | Triggered from document listener in tool-window factory. |
| `MkdocsToolWindowFactory.scheduleScrollSync(...)` | Apply editor viewport progress to preview viewport progress. | project/runtime/preview context, selected path, raw delta, document length, visible offsets. | `Boolean` scheduled-state. | Returns `false` when runtime inactive, non-doc path, no active editor match, or negligible progress change. | Triggered from visible-area listener in tool-window factory. |
| `PreviewContent.scrollToProgress(progress)` | Scroll preview pane to normalized vertical progress. | `progress` in `[0.0, 1.0]`. | none. | No-op in non-JCEF fallback preview. | Called by `scheduleScrollSync(...)`. |

## 5. Compatibility and Versioning

1. `PluginRuntimeIntegrationService` API version: `1.0.0`.
2. Existing seam interfaces remain unchanged for this cycle.
3. Plugin shell changes are additive and preserve existing MVP workflows.

## 6. Validation Strategy

1. Policy tests for build/descriptors.
2. Unit tests for tool-window content path, action presentation/invocation, runtime integration lifecycle guard.
3. Unit tests for typing refresh and viewport-percentage scroll sync behavior in tool-window factory.
4. RunIde smoke workflow for plugin load + visible entry points.
5. Coverage gate remains 100% for scoped code.

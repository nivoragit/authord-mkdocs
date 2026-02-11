# Data Model: IntelliJ Plugin Shell Cycle

## Overview

This model defines planning-level entities for plugin-shell enablement while reusing existing runtime/domain modules.

## Entities

### 1) PluginShellConfig

- Purpose: Declares installable plugin-shell build/runtime metadata.
- Fields:
  - `pluginId` (string, immutable)
  - `pluginName` (string)
  - `ideTarget` (string)
  - `sinceBuild` (string)
  - `untilBuild` (string, optional)
  - `runIdeEnabled` (boolean)
- Relationships:
  - Drives descriptor and build-plugin workflow.
- Validation Rules:
  - `pluginId` and `pluginName` must be non-empty.
  - `ideTarget` and compatibility bounds must be coherent.
  - `runIdeEnabled` must be true for cycle acceptance.

### 2) PluginDescriptorRegistration

- Purpose: Represents descriptor-level registrations required for plugin shell.
- Fields:
  - `dependsPlatform` (boolean)
  - `toolWindowId` (string)
  - `toolWindowFactoryClass` (string)
  - `startActionId` (string)
  - `startActionClass` (string)
- Relationships:
  - Bound to `PluginShellConfig` and UI entry components.
- Validation Rules:
  - `dependsPlatform` must be true.
  - Tool-window and action IDs/classes must be present and unique.

### 3) ToolWindowShellState

- Purpose: Captures tool window initialization and content state per project.
- Fields:
  - `projectId` (string)
  - `toolWindowVisible` (boolean)
  - `contentCreated` (boolean)
  - `statusMessage` (string)
- Relationships:
  - Created by `MkdocsToolWindowFactory`.
  - Read by action/runtime integration adapter.
- Validation Rules:
  - `contentCreated` must be true before shell is considered initialized.

### 4) StartPreviewActionState

- Purpose: Defines action presentation and invocation eligibility.
- Fields:
  - `projectId` (string)
  - `visible` (boolean)
  - `enabled` (boolean)
  - `disabledReason` (string, optional)
- Relationships:
  - Produced by action `update` logic.
  - Consumed by user invocation path.
- Validation Rules:
  - If `enabled` is false, `disabledReason` should be provided.

### 5) PluginRuntimeIntegrationRequest

- Purpose: Adapter request from action/tool window to existing runtime entrypoint.
- Fields:
  - `projectId` (string)
  - `projectPath` (string)
  - `triggerSource` (enum: `ACTION`, `TOOL_WINDOW`)
  - `startupOutput` (string)
- Relationships:
  - Routed to existing activation/runtime services.
- Validation Rules:
  - `projectId` and `projectPath` are required.
  - `triggerSource` must be one of supported values.

### 6) PluginRuntimeIntegrationResult

- Purpose: Standard result returned from runtime handoff adapter.
- Fields:
  - `projectId` (string)
  - `started` (boolean)
  - `alreadyRunning` (boolean)
  - `baseUrlDetected` (boolean)
  - `previewUrl` (string, optional)
  - `failureReason` (string, optional)
- Relationships:
  - Mirrors existing runtime/activation outcomes.
  - Drives action feedback and shell status display.
- Validation Rules:
  - If `started` and `baseUrlDetected` are true, `previewUrl` must be present.
  - Failure outcomes must include `failureReason`.

### 7) RuntimeLifecycleGuardState

- Purpose: Ensures single runtime instance semantics per project in integration layer.
- Fields:
  - `projectId` (string)
  - `runtimeActive` (boolean)
  - `processId` (string, optional)
  - `lastTransition` (enum: `START`, `REUSE`, `STOP`, `RESTART`, `FAILED`)
- Relationships:
  - Delegates to existing process manager/source-of-truth state.
- Validation Rules:
  - At most one active process per `projectId`.

### 8) PluginShellSmokeCheck

- Purpose: Captures `runIde` smoke verification status for cycle acceptance.
- Fields:
  - `runIdeLaunchSucceeded` (boolean)
  - `pluginLoaded` (boolean)
  - `toolWindowPresent` (boolean)
  - `actionPresent` (boolean)
  - `actionInvokable` (boolean)
- Relationships:
  - Aggregates shell acceptance outcomes for runbook/test plan.
- Validation Rules:
  - All booleans must be true for smoke acceptance.

### 9) DocOpsArtifactStatus

- Purpose: Tracks required cycle documentation completeness.
- Fields:
  - `featureSpecUpdated` (boolean)
  - `designNotesUpdated` (boolean)
  - `runbookUpdated` (boolean)
  - `testPlanTraceabilityUpdated` (boolean)
  - `changelogUpdated` (boolean)
  - `migrationNotesUpdatedOrN/A` (boolean)
- Relationships:
  - Used by release-gate checks.
- Validation Rules:
  - All required artifacts must be true before cycle completion.

## State Transitions

### Plugin Shell Readiness

1. `CONFIGURED`: plugin build and descriptor metadata are valid.
2. `LOADED`: plugin loads in development IDE sandbox.
3. `TOOL_WINDOW_READY`: tool window content factory created shell panel.
4. `ACTION_READY`: start-preview action visible and invokable.
5. `RUNTIME_HANDOFF_READY`: action delegates to runtime adapter and returns controlled result.
6. `VERIFIED`: smoke and quality/documentation gates pass.

### Runtime Handoff Adapter Flow

1. `REQUEST_RECEIVED`: action/tool-window submits integration request.
2. `DELEGATED`: existing activation/runtime service invoked.
3. `REUSED_OR_STARTED`: runtime single-instance behavior resolved.
4. `URL_RESOLVED_OR_FAILED`: base URL detected or controlled failure returned.
5. `RESULT_PUBLISHED`: adapter result surfaced back to UI shell.

## Invariant Summary

- Plugin shell work must not duplicate runtime/domain business logic.
- Single runtime instance per project remains enforced by existing runtime lifecycle manager.
- Tool window and action registration are mandatory for shell readiness.
- Out-of-scope features remain disabled while mandatory extension seams remain present.

# Session Addendum Specification (2026-02-11): Preview Runtime + Sync Stabilization

## Context

This addendum captures post-cycle stabilization applied on top of R-01..R-06 plugin-shell delivery.
It documents runtime and preview behavior implemented during the current session.

## Session Scope

### In Scope

1. Use `mkdocs serve --livereload --dirty` in plugin runtime command path.
2. Ensure runtime server lifecycle is bound to IDE lifecycle via parent-PID guard.
3. Auto-start preview from tool window initialization path.
4. Refresh preview after typing in active docs markdown files.
5. Synchronize preview scroll using editor viewport percentage mapping.
6. Prevent startup crash in scroll listener when `VisibleAreaEvent.oldRectangle` is null.

### Out of Scope

1. Bidirectional sync from preview back to editor.
2. Cursor-point-based sync heuristics.
3. New user-facing features outside plugin-shell/session stabilization.

## Session Requirements

- **S-01**: Runtime command MUST invoke MkDocs with `--livereload --dirty`.
- **S-02**: Runtime process MUST terminate when parent IDE process is no longer alive.
- **S-03**: Tool-window creation MUST attempt preview start automatically.
- **S-04**: Typing in active docs markdown MUST schedule preview refresh on saved content.
- **S-05**: Scroll sync MUST map editor viewport progress to the same preview viewport progress.
- **S-06**: Scroll listener MUST safely ignore visible-area events that do not provide a previous rectangle.

## Acceptance Scenarios

1. **Given** preview runtime starts from plugin shell, **When** command list is resolved, **Then** `mkdocs serve --livereload --dirty` is present.
2. **Given** IDE parent process exits, **When** guard loop checks parent PID, **Then** MkDocs subprocess exits.
3. **Given** tool window is created, **When** content initializes, **Then** preview start is attempted and result is reported.
4. **Given** author types in active docs file, **When** typing refresh delay elapses, **Then** preview route refresh executes from saved content.
5. **Given** editor scroll position changes, **When** viewport progress changes, **Then** preview scroll target matches the same normalized progress.
6. **Given** editor emits visible-area event with missing old rectangle, **When** listener handles event, **Then** no exception is thrown and plugin remains active.

## Validation Mapping

- `MkdocsToolWindowFactoryTest` covers S-03, S-04, S-05, S-06.
- `PluginActivationServiceTest` covers S-01 and parent-guard setup of S-02.
- `PluginRuntimeIntegrationServiceTest` covers lifecycle aspects of S-02 and runtime state guards.
- `UvBootstrapServiceTest` extends runtime adapter branch coverage for inline theme parsing used by bootstrap package resolution.

## Implementation References

- `modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/PluginActivationService.kt`
- `modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/intellij/MkdocsToolWindowFactory.kt`
- `modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/intellij/PluginRuntimeIntegrationService.kt`
- `modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/intellij/MkdocsToolWindowFactoryTest.kt`
- `modules/mkdocs-runtime-adapter/src/test/kotlin/com/authord/mkdocs/runtime/UvBootstrapServiceTest.kt`

# Feature Spec (Implemented): IntelliJ Plugin Shell Cycle

## 1. Objective

Make the existing MVP runnable inside IntelliJ as a real plugin shell without expanding product scope beyond R-01..R-06.

## 2. In Scope (Implemented)

1. IntelliJ Platform Gradle plugin setup with plugin metadata and IDE compatibility properties.
2. `runIde`-ready project configuration and local workflow documentation.
3. Plugin descriptor registrations for:
- `com.intellij.modules.platform` dependency
- MkDocs tool window extension
- Start MkDocs Preview action
4. Minimal `MkdocsToolWindowFactory` shell panel implementation.
5. `StartMkdocsAction` wiring with action-system update/actionPerformed behavior.
6. Project-scoped `PluginRuntimeIntegrationService` wiring to existing activation/runtime services.
7. Lifecycle/single-instance guard preservation through existing runtime manager semantics.
8. Unit and policy tests for build/descriptor/action/tool-window/runtime-integration paths.
9. RunIde smoke validation checklist artifacts and updated DocOps package.
10. Current-session stabilization updates for preview runtime and sync behavior:
- Runtime command uses `mkdocs serve --livereload --dirty`.
- Runtime is parent-bound so server process terminates with IDE shutdown.
- Tool window auto-starts preview on creation.
- Editor typing refresh and viewport-percentage preview scroll sync in tool window.

## 3. Out of Scope (Explicitly Excluded)

1. New end-user features beyond plugin shell enablement.
2. Bidirectional code↔preview scroll synchronization (preview-to-code remains out of scope).
3. AI chatbot/command execution workflows.
4. Vector retrieval/database-backed behavior.
5. WriterSide-like full topic tree UX parity.

## 4. Requirement Alignment

| Requirement | Delivery Summary |
|-------------|------------------|
| R-01 | IntelliJ Gradle plugin and compatibility metadata configured; `runIde` workflow documented |
| R-02 | `plugin.xml` includes platform dependency, tool-window extension, and Start action registration |
| R-03 | `MkdocsToolWindowFactory` creates minimal plugin shell panel |
| R-04 | `StartMkdocsAction` implements update/actionPerformed and delegates to runtime integration service |
| R-05 | Runtime handoff reuses existing activation/runtime services with single-instance guard preserved |
| R-06 | Run-in-IDE smoke workflow documented and release-gate evidence updated |

## 5. Delivery Constraints Satisfied

1. Existing runtime behavior was preserved; no runtime feature rewrite was introduced.
2. Mandatory seams remain present:
- `TopicTreePort`/service command API
- `PreviewSyncPort`
- `PluginCommandBus` + `CommandRegistry`
- `VectorStorePort`
- feature flags
3. Public plugin-shell services/actions include KDoc and function-level usage guidance in technical notes.
4. CI/build coverage gate remains configured for 100% scoped unit coverage.

## 6. Quality Gates

Cycle remains incomplete until all of the following are true:
1. Unit tests pass for plugin-shell scope.
2. Coverage gate passes at 100% scoped unit coverage.
3. RunIde smoke checklist evidence is recorded.
4. Required DocOps artifacts are updated.

## 7. Session Addendum (2026-02-11)

1. Added crash guard for `VisibleAreaEvent.oldRectangle == null` in scroll listener path.
2. Replaced scroll-delta approximation with direct viewport-percentage mapping (`editor progress == preview progress`).
3. Updated operational guidance for common startup failures (`Address already in use`, missing MkDocs theme package, base-URL detection failures).

# Operational Runbook: IntelliJ Plugin Shell Cycle

## 1. Purpose

Define local and CI operating steps for plugin-shell build, runIde smoke validation, and troubleshooting.

## 2. Prerequisites

1. JDK 21 available for Gradle build/toolchain tasks.
2. `uv` available on PATH for runtime bootstrap commands.
3. IntelliJ Platform artifacts can be downloaded by Gradle on first run.

## 3. Build and Test Commands

Run from repository root.

## 3.1 Full quality gate

```bash
GRADLE_USER_HOME=$PWD/.gradle-user ./gradlew test jacocoTestCoverageVerification --no-daemon
```

## 3.2 UI plugin compile/test focus

```bash
GRADLE_USER_HOME=$PWD/.gradle-user ./gradlew :modules:ui-plugin:test --no-daemon
```

## 3.3 CI-equivalent command

```bash
./gradlew clean test jacocoTestCoverageVerification --no-daemon
```

## 4. runIde Workflow (Local)

1. Resolve plugin-shell dependencies:

```bash
GRADLE_USER_HOME=$PWD/.gradle-user ./gradlew :modules:ui-plugin:buildPlugin --no-daemon
```

2. Launch development IDE with plugin sandbox:

```bash
GRADLE_USER_HOME=$PWD/.gradle-user ./gradlew :modules:ui-plugin:runIde --no-daemon
```

3. In launched IDE, validate smoke criteria:
- Plugin loads without descriptor errors.
- **Authord MkDocs** tool window is visible.
- **Start MkDocs Preview** action appears in **Tools** menu and is invokable.
- Tool window auto-starts preview and resolves preview URL from runtime output.

## 5. Runtime Handoff Diagnostics

When action/tool-window trigger is invoked:
1. Verify `PluginRuntimeIntegrationService.canStartPreview()` state.
2. Confirm project base path exists.
3. Confirm runtime lifecycle state through `isRuntimeRunning()` before/after start.
4. Confirm activation result reason/message when startup output lacks URL.

## 5.1 Runtime Serve Command (Current Session)

Runtime start delegates to an OS-neutral command path based on:

```text
uv run --python <runtime-python> python <project>/.mkdocs-plugin-runtime/serve_with_parent_guard.py --parent-pid <ide-pid> --working-dir <project> -- mkdocs serve --livereload --dirty
```

Key operational behavior:
1. `--livereload --dirty` enables fast preview rebuilds for authoring.
2. Parent-PID guard terminates MkDocs when IDE process exits.
3. No hardcoded host/port is used; preview base URL is detected from startup output.

## 6. Common Failure Signatures and Fixes

## 6.1 `runIde` task missing

Possible causes:
- IntelliJ Gradle plugin not applied.
- UI plugin module not configured with IntelliJ target values.

Actions:
1. Verify root `build.gradle.kts` declares `org.jetbrains.intellij` plugin.
2. Verify `modules/ui-plugin/build.gradle.kts` contains `intellij { ... }` configuration.
3. Verify `gradle.properties` defines `platformType`, `platformVersion`, `sinceBuild`, `untilBuild`.

## 6.2 Plugin descriptor registration failure

Possible causes:
- Missing `com.intellij.modules.platform` dependency.
- Invalid tool-window/action class path.

Actions:
1. Validate `modules/ui-plugin/src/main/resources/META-INF/plugin.xml` registrations.
2. Run unit policy test `PluginDescriptorRegistrationTest`.

## 6.3 Action visible but disabled

Possible causes:
- Project base path missing.
- Runtime already running.
- MVP policy disabled.

Actions:
1. Check `PluginRuntimeIntegrationService.canStartPreview()` inputs.
2. Check feature-flag policy state.
3. Check runtime running state and stop if necessary.

## 6.4 Coverage gate failure

Possible causes:
- New plugin-shell lines missing unit tests.
- Uncovered branches in action/tool-window/integration service logic.

Actions:
1. Add/adjust tests under `modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/intellij/`.
2. Re-run `jacocoTestCoverageVerification`.

## 6.5 `runIde` window opens then auto-closes

Possible causes:
- Plugin exception during tool-window initialization.
- Unhandled scroll listener edge case at editor startup.

Actions:
1. Inspect `modules/ui-plugin/build/idea-sandbox/system/log/idea.log`.
2. Search for `project loading failed` and plugin stack traces.
3. Verify no `VisibleAreaEvent.getOldRectangle()` null dereference exists in tool-window scroll listener path.

## 6.6 Preview start fails with `Address already in use`

Possible causes:
- Existing MkDocs process already bound to default port.

Actions:
1. Stop prior MkDocs process.
2. Restart preview from tool window/action.
3. If repeated, restart IDE sandbox to reinitialize process manager state.

## 6.7 Preview start fails with theme config error

Possible causes:
- `mkdocs.yml` references theme packages not installed in runtime venv.

Actions:
1. Install required theme package in project/runtime environment.
2. Re-run preview start and confirm startup output includes detected base URL.

## 7. Completion Rule

Cycle is complete only when:
1. runIde smoke checklist is documented,
2. tests pass,
3. 100% scoped unit coverage gate passes,
4. required DocOps artifacts are updated.

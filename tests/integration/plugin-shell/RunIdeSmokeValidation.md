# RunIde Smoke Validation

## Purpose

Record run-in-IDE smoke validation evidence for requirement R-06.

## Preconditions

1. Build succeeds (`./gradlew test jacocoTestCoverageVerification`).
2. IntelliJ plugin shell is configured in build and descriptor.

## Smoke Steps

1. Launch sandbox IDE:

```bash
GRADLE_USER_HOME=$PWD/.gradle-user ./gradlew :modules:ui-plugin:runIde --no-daemon
```

2. In sandbox IDE validate:
- Plugin loads without descriptor errors.
- **Authord MkDocs** tool window appears.
- **Start MkDocs Preview** action appears under **Tools**.
- Action is invokable and delegates to runtime integration service.

## Execution Record

- Date: 2026-02-09
- Executor: Codex (automated runIde smoke + log verification)
- Result: PASS
- Notes:
  - Executed: `GRADLE_USER_HOME=/tmp/authord-gradle-user ./gradlew :modules:ui-plugin:runIde --no-daemon`
  - `runIde` reached running IDE state (`IDE STARTED`) and plugin sandbox booted successfully.
  - Evidence in `modules/ui-plugin/build/idea-sandbox/system/log/idea.log`:
    - `IDE STARTED`
    - `Loaded custom plugins: Authord MkDocs Plugin (0.1.0)`
  - No plugin descriptor failure entries detected in startup log.
  - Tool-window/action registration and invocation path are additionally verified by plugin-shell tests:
    - `PluginDescriptorRegistrationTest`
    - `MkdocsToolWindowFactoryTest`
    - `StartMkdocsActionPresentationTest`
    - `StartMkdocsActionInvocationTest`

## Troubleshooting Notes

- If `runIde` is missing, validate IntelliJ Gradle plugin setup in `build.gradle.kts` and `modules/ui-plugin/build.gradle.kts`.
- If plugin fails to load, validate descriptor entries in `modules/ui-plugin/src/main/resources/META-INF/plugin.xml`.

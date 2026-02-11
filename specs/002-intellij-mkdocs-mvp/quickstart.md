# Quickstart: IntelliJ Plugin Shell Cycle

## Purpose

Validate plugin-shell cycle delivery for R-01..R-06: build setup, descriptor wiring, tool window/action shell, runtime handoff, and runIde smoke workflow.

## 1) Confirm planning inputs

1. Confirm branch is `002-intellij-mkdocs-mvp`.
2. Review artifacts in `/Users/madushika/projects/authord-mkdocs-plugin/specs/002-intellij-mkdocs-mvp/`:
- `spec.md`
- `plan.md`
- `research.md`
- `data-model.md`
- `tasks.md`

## 2) Build and plugin setup checks (R-01)

1. Verify `build.gradle.kts` declares IntelliJ Gradle plugin dependency.
2. Verify `modules/ui-plugin/build.gradle.kts` includes IntelliJ target configuration.
3. Verify `gradle.properties` includes:
- `platformType`
- `platformVersion`
- `sinceBuild`
- `untilBuild`

## 3) Descriptor checks (R-02)

Verify `modules/ui-plugin/src/main/resources/META-INF/plugin.xml` contains:
1. `<depends>com.intellij.modules.platform</depends>`
2. tool-window registration for `MkdocsToolWindowFactory`
3. action registration for `StartMkdocsAction`

## 4) Local plugin-shell execution workflow (R-06)

1. Build and test:

```bash
GRADLE_USER_HOME=$PWD/.gradle-user ./gradlew test jacocoTestCoverageVerification --no-daemon
```

2. Launch plugin sandbox IDE:

```bash
GRADLE_USER_HOME=$PWD/.gradle-user ./gradlew :modules:ui-plugin:runIde --no-daemon
```

3. In launched IDE, verify smoke checklist:
- Plugin loads without descriptor/build errors.
- **Authord MkDocs** tool window is visible.
- **Start MkDocs Preview** action is visible in **Tools** menu and callable.

## 5) Runtime handoff checks (R-05)

1. Trigger Start action or tool-window button.
2. Confirm runtime integration service delegates to existing activation/runtime services.
3. Re-trigger start and confirm single-instance lifecycle guard behavior.

## 6) Documentation and release gate closure

Before marking cycle complete, ensure updates exist for:
1. `spec.md`
2. technical design notes
3. operational runbook
4. test plan + traceability matrix
5. changelog (+ migration notes if needed)

Cycle remains incomplete until docs, tests, and coverage gate are all green.

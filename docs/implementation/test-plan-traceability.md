# Test Plan and Requirements Traceability

## 1. Test Objectives

1. Validate plugin-shell cycle requirements R-01..R-06.
2. Ensure action/tool-window/runtime handoff behavior is unit tested.
3. Ensure CI enforces 100% scoped unit coverage.
4. Keep requirements-to-tests traceability explicit and current.

## 2. Test Levels

1. Unit tests
- Build/descriptor policy tests.
- Action presentation and invocation behavior.
- Tool-window content creation path.
- Runtime integration lifecycle and single-instance guard logic.

2. Integration and smoke validation
- `runIde` smoke checklist for plugin load, tool window presence, and action availability.

3. Policy tests
- Coverage gate and doc artifact policy tests.
- Runtime neutrality and KDoc policy checks.

## 3. Execution Commands

## 3.1 Full suite + coverage gate

```bash
GRADLE_USER_HOME=$PWD/.gradle-user ./gradlew test jacocoTestCoverageVerification --no-daemon
```

## 3.2 Plugin-shell focused tests

```bash
GRADLE_USER_HOME=$PWD/.gradle-user ./gradlew :modules:ui-plugin:test --no-daemon
```

## 4. Requirement-to-Test Traceability Matrix

| Requirement | Summary | Unit Tests | Integration / Smoke |
|-------------|---------|------------|---------------------|
| R-01 | IntelliJ Gradle plugin setup + runIde readiness | `PluginBuildPolicyTest` | `tests/integration/plugin-shell/RunIdeSmokeValidation.md` |
| R-02 | Plugin descriptor entries (platform/tool-window/action) | `PluginDescriptorRegistrationTest` | `tests/integration/plugin-shell/RunIdeSmokeValidation.md` |
| R-03 | Minimal tool window shell content path | `MkdocsToolWindowFactoryTest` | `tests/integration/plugin-shell/RunIdeSmokeValidation.md` |
| R-04 | Start action update/actionPerformed behavior | `StartMkdocsActionPresentationTest`, `StartMkdocsActionInvocationTest` | `tests/integration/plugin-shell/RunIdeSmokeValidation.md` |
| R-05 | Runtime handoff integration + single-instance lifecycle | `PluginRuntimeIntegrationServiceTest`, `StartMkdocsActionInvocationTest` | `tests/integration/plugin-shell/RunIdeSmokeValidation.md` |
| R-06 | Run-in-IDE workflow validation | `PluginBuildPolicyTest` (runIde docs/config checks) | `tests/integration/plugin-shell/RunIdeSmokeValidation.md`, release-gate checklist |

## 5. Test Inventory (Plugin Shell)

- `modules/core-domain/src/test/kotlin/com/authord/mkdocs/core/quality/PluginBuildPolicyTest.kt`
- `modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/intellij/PluginDescriptorRegistrationTest.kt`
- `modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/intellij/MkdocsToolWindowFactoryTest.kt`
- `modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/intellij/StartMkdocsActionPresentationTest.kt`
- `modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/intellij/StartMkdocsActionInvocationTest.kt`
- `modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/intellij/PluginRuntimeIntegrationServiceTest.kt`
- `tests/integration/plugin-shell/RunIdeSmokeValidation.md`

## 6. Coverage and Completion Gate

Cycle is incomplete until all are true:
1. Plugin-shell unit tests pass.
2. `jacocoTestCoverageVerification` passes at 100% scoped unit coverage.
3. runIde smoke checklist is executed and documented.
4. Traceability matrix remains aligned with current requirements and tests.

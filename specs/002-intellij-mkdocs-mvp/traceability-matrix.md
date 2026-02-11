# Requirements to Tests Traceability Matrix

## Scope

Maps plugin-shell cycle requirements (R-01..R-06) to unit and smoke validation artifacts.

| Requirement ID | Requirement Summary | Unit Tests | Integration / Smoke Evidence |
|----------------|---------------------|------------|-------------------------------|
| R-01 | IntelliJ build/plugin setup and `runIde` readiness | `PluginBuildPolicyTest` | `tests/integration/plugin-shell/RunIdeSmokeValidation.md` |
| R-02 | Plugin descriptor platform/tool-window/action registration | `PluginDescriptorRegistrationTest` | `tests/integration/plugin-shell/RunIdeSmokeValidation.md` |
| R-03 | Minimal tool-window shell implementation | `MkdocsToolWindowFactoryTest` | `tests/integration/plugin-shell/RunIdeSmokeValidation.md` |
| R-04 | Start action update/actionPerformed behavior and delegation | `StartMkdocsActionPresentationTest`, `StartMkdocsActionInvocationTest` | `tests/integration/plugin-shell/RunIdeSmokeValidation.md` |
| R-05 | Runtime handoff integration preserves single-instance lifecycle | `PluginRuntimeIntegrationServiceTest`, `StartMkdocsActionInvocationTest` | `tests/integration/plugin-shell/RunIdeSmokeValidation.md` |
| R-06 | Run-in-IDE workflow validation (plugin load, tool window, action) | `PluginBuildPolicyTest` (workflow config assertions) | `tests/integration/plugin-shell/RunIdeSmokeValidation.md`, `specs/002-intellij-mkdocs-mvp/checklists/release-gate.md` |

## Coverage Gate

- Unit coverage gate target: 100% for scoped code.
- CI must run `jacocoTestCoverageVerification` and fail below threshold.
- Matrix must be updated whenever requirements or tests change.

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

## Session Addendum Scope (2026-02-11)

Maps session stabilization requirements (S-01..S-06) defined in `session-2026-02-11-preview-sync.md`.

| Session Req ID | Requirement Summary | Unit Tests | Integration / Runtime Evidence |
|----------------|---------------------|------------|--------------------------------|
| S-01 | Runtime serve command uses `--livereload --dirty` | `PluginActivationServiceTest` | `idea.log` startup output + runbook command verification |
| S-02 | Runtime lifecycle bound to parent IDE process | `PluginActivationServiceTest`, `PluginRuntimeIntegrationServiceTest` | parent-guard process behavior checks in runbook |
| S-03 | Tool-window path auto-starts preview | `MkdocsToolWindowFactoryTest` | runIde smoke validation |
| S-04 | Typing refresh updates preview route for active docs markdown | `MkdocsToolWindowFactoryTest` | interactive runIde typing checks |
| S-05 | Scroll sync maps editor viewport percentage to preview percentage | `MkdocsToolWindowFactoryTest` | interactive runIde scroll checks |
| S-06 | Null-safe visible-area listener handling during editor startup | `MkdocsToolWindowFactoryTest` | absence of startup NPE in `idea.log` |

## Coverage Gate

- Unit coverage gate target: 100% for scoped code.
- CI must run `jacocoTestCoverageVerification` and fail below threshold.
- Matrix must be updated whenever requirements or tests change.

Current gate status (2026-02-11):
- `:modules:mkdocs-runtime-adapter:jacocoTestCoverageVerification` passing after inline-theme branch tests.
- Full gate currently blocked by `:modules:ui-plugin:jacocoTestCoverageVerification` (0.7 / 1.0 required).

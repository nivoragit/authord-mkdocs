# Changelog

All notable changes to this project are documented in this file.

## [0.2.2] - 2026-02-15

## Added

1. Phase 2 success-criteria evidence artifacts:
- `tests/integration/topic-tree/StartupTimingEvidence.md`
- `tests/integration/topic-tree/PartialSyncZeroStateEvidence.md`
- `tests/integration/topic-tree/SeededValidationEvidence.md`
- `tests/integration/topic-tree/CompatibilityRegressionEvidence.md`
- `tests/integration/topic-tree/MutationResponsivenessEvidence.md`
- `tests/integration/topic-tree/InstanceScaleEvidence.md`

2. Public API usage guidance summary:
- `docs/implementation/public-api-usage.md`

## Changed

1. Phase 2 release docs synchronized for current execution state:
- `specs/001-mkdocs-topic-tree/spec.md`
- `specs/001-mkdocs-topic-tree/technical-design-notes.md`
- `specs/001-mkdocs-topic-tree/operational-runbook.md`
- `specs/001-mkdocs-topic-tree/test-plan-traceability.md`
- `specs/001-mkdocs-topic-tree/analysis-closure.md`
- `tests/integration/topic-tree/RunIdeDecouplingEvidence.md`

2. Runtime decoupling checkpoint evidence refreshed from focused run:
- `.tmp/gate-runs/20260215T050001Z_t119_runtime_decoupling.log`

## Migration Notes

No breaking migration steps are required for this documentation/evidence update.

## [0.2.1] - 2026-02-11

## Added

1. Session stabilization documentation artifacts:
- `docs/implementation/session-2026-02-11-plugin-preview-stabilization.md`
- `specs/002-intellij-mkdocs-mvp/session-2026-02-11-preview-sync.md`

2. Viewport-percentage preview sync behavior:
- editor viewport progress now maps directly to preview viewport progress.
- tool-window factory tests extended for percentage-based mapping and startup edge cases.
3. Runtime adapter coverage tests for inline MkDocs theme parsing:
- added scalar/map/empty-block/non-material branch tests in `UvBootstrapServiceTest`.

## Changed

1. Runtime serve command path now uses:
- `mkdocs serve --livereload --dirty`
- parent-bound guarded execution to align server lifecycle with IDE lifecycle.

2. Tool window runtime behavior now auto-starts preview on content creation and keeps action path as explicit retry/start entry point.

3. Tool-window scroll listener now handles null visible-area history safely during early editor initialization.

## Fixed

1. `runIde` auto-close/project-load crash caused by null `VisibleAreaEvent.oldRectangle` in preview scroll listener.
2. Preview start re-invocation availability when base URL detection fails (process cleanup on failure path).
3. KDoc policy violations fixed for `MkdocsToolWindowFactory` listener callbacks and preview contract methods.

## Migration Notes

No breaking migration steps are required. This is an additive stabilization release.

## [0.2.0] - 2026-02-09

## Added

1. IntelliJ plugin-shell enablement for MVP runtime integration:
- IntelliJ Gradle plugin configuration and compatibility properties for `runIde`.
- Plugin descriptor updates for platform dependency, tool window registration, and Start action registration.
- Minimal IntelliJ tool window factory (`MkdocsToolWindowFactory`) and Start action (`StartMkdocsAction`).
- Project-scoped runtime integration service (`PluginRuntimeIntegrationService`) delegating to existing activation/runtime services.

2. Plugin-shell unit test coverage:
- `PluginBuildPolicyTest`
- `PluginDescriptorRegistrationTest`
- `MkdocsToolWindowFactoryTest`
- `StartMkdocsActionPresentationTest`
- `StartMkdocsActionInvocationTest`
- `PluginRuntimeIntegrationServiceTest`

3. Plugin-shell DocOps artifacts:
- Updated feature spec for R-01..R-06 scope.
- Updated technical design notes with plugin entry architecture and function-level usage guidance.
- Updated operational runbook with `runIde` workflow and troubleshooting.
- Updated requirements→tests traceability matrix for plugin-shell requirements.
- Added runIde smoke validation checklist at `tests/integration/plugin-shell/RunIdeSmokeValidation.md`.

## Changed

1. `modules/ui-plugin` build now applies IntelliJ plugin tooling and patches plugin compatibility range from `gradle.properties`.
2. `modules/ui-plugin/src/main/resources/META-INF/plugin.xml` now includes required IntelliJ extension registrations for this cycle.

## Migration Notes

No migration steps are required for this additive plugin-shell cycle.

## [0.1.0] - 2026-02-08

## Added

1. Multi-module MVP architecture:
- `core-domain`
- `mkdocs-runtime-adapter`
- `ui-plugin`
- `extension-ports`
- `infra-defaults`

2. Activation/runtime MVP flow:
- first-activation runtime bootstrap via `uv`
- `mkdocs` installation flow
- single-instance runtime process manager per project
- base URL detection from startup output
- preview open/navigate state coordinator

3. Docs navigation MVP:
- docs explorer markdown discovery
- route mapping service (`docs/index.md`, `docs/<segment>/index.md`, `docs/<segment>.md`, nested paths)
- explorer selection to preview navigation orchestration

4. Extension seams with minimal/default behavior:
- `TopicTreePort` command API and DTO/result contracts
- `PluginCommandBus` + `CommandRegistry`
- `PreviewSyncPort`
- `VectorStorePort`
- no-op/in-memory default adapters
- topic mutation runway expanded with `rename` and `reparent` commands
- provider-neutral topic model runway includes `TopicId`, `TopicOrder`, `TopicLink`, `TopicMetadata`, and `TopicTree` alias

5. Guardrails and policy:
- feature policy defaults for MVP-only behavior
- explicit tests covering out-of-scope exclusions
- OS-neutral runtime/file handling via `uv` and Path APIs for filesystem operations
- runtime decoupling defaults use `mkdocs serve` without hardcoded host/port
- policy tests enforce runtime neutrality constraints and required delivery documentation artifacts

## Quality

1. Unit, integration, and contract test suites added.
2. Jacoco coverage verification configured to enforce 100% scoped unit line coverage.
3. CI workflow enforces `clean test jacocoTestCoverageVerification`.
4. Quality policy tests enforce:
- required SDD + implementation docs bundle presence
- function-level usage guidance section in technical design notes
- requirements-to-tests traceability matrix presence
- runtime neutrality constraints (no hardcoded python path / host-port literals in main sources)
- KDoc coverage for public declarations in main Kotlin source files

## Documentation

1. Feature and implementation artifacts under `specs/002-intellij-mkdocs-mvp/`.
2. ADRs for TopicTreePort, CommandBus, PreviewSyncPort, and VectorStorePort.
3. Runtime diagnostics runbook and requirements-to-tests traceability matrix.
4. Consolidated implementation docs under `docs/implementation/`.

## Known Limitations

1. Code<->preview scroll sync is intentionally out of scope.
2. AI chatbot/command execution is intentionally out of scope.
3. Vector database integration behavior is intentionally out of scope.
4. Full WriterSide-like topic tree UX parity is intentionally out of scope.
5. IntelliJ extension-point wiring remains minimal for this cycle.

## Migration Notes

No migration steps are required for this initial baseline.
See `docs/implementation/migration-notes.md`.

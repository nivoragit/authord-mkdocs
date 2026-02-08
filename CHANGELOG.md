# Changelog

All notable changes to this project are documented in this file.

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

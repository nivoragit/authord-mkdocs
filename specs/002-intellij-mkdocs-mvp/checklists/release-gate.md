# Release Gate Checklist: IntelliJ Plugin Shell Cycle

**Purpose**: Enforce cycle completion only when plugin-shell docs, tests, smoke validation, and coverage gates pass.  
**Created**: 2026-02-09

## Gate Status

- [x] Plugin-shell implementation tasks for R-01..R-05 completed
- [x] Unit test suite passes for plugin-shell scope
- [x] Scoped unit coverage gate verified at 100% (`jacocoTestCoverageVerification`)
- [x] `runIde` smoke validation recorded (plugin load + tool window + action visibility/invocation)
- [x] Requirements-to-tests traceability matrix updated for R-01..R-06
- [x] Technical design notes include function-level usage guidance for new/changed public services
- [x] Operational runbook includes local runIde workflow and troubleshooting
- [x] Changelog and migration notes updated for plugin-shell cycle

## Cycle Completion Rule

- [x] Cycle marked COMPLETE

Cycle must remain INCOMPLETE until all unchecked items above are completed.

## Evidence

- Full gate command passed on 2026-02-09: `GRADLE_USER_HOME=/tmp/authord-gradle-user ./gradlew test jacocoTestCoverageVerification --no-daemon -Dkotlin.incremental=false`
- runIde smoke command executed on 2026-02-09: `GRADLE_USER_HOME=/tmp/authord-gradle-user ./gradlew :modules:ui-plugin:runIde --no-daemon`
- Startup evidence recorded in `modules/ui-plugin/build/idea-sandbox/system/log/idea.log`:
  - `IDE STARTED`
  - `Loaded custom plugins: Authord MkDocs Plugin (0.1.0)`
- Detailed smoke record updated in `tests/integration/plugin-shell/RunIdeSmokeValidation.md`.

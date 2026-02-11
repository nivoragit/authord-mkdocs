# Release Gate Checklist: IntelliJ Plugin Shell Cycle

**Purpose**: Enforce cycle completion only when plugin-shell docs, tests, smoke validation, and coverage gates pass.  
**Created**: 2026-02-09

## Gate Status

- [x] Plugin-shell implementation tasks for R-01..R-06 completed
- [x] Unit test suite passes for plugin-shell scope
- [ ] Scoped unit coverage gate verified at 100% (`jacocoTestCoverageVerification`)
- [x] `runIde` smoke validation recorded (plugin load + tool window + action visibility/invocation)
- [x] Requirements-to-tests traceability matrix updated for R-01..R-06
- [x] Technical design notes include function-level usage guidance for new/changed public services
- [x] Operational runbook includes local runIde workflow and troubleshooting
- [x] Changelog and migration notes updated for plugin-shell cycle
- [x] Session addendum spec and implementation docs updated for runtime/preview stabilization (`2026-02-11`)
- [x] Session stabilization tests pass for tool-window typing/scroll sync and runtime command behavior
- [x] Constitution compliance review performed for docs/reporting artifacts

## Cycle Completion Rule

- [ ] Cycle marked COMPLETE

Cycle must remain INCOMPLETE until all unchecked items above are completed.

## Evidence

- Full gate command passed on 2026-02-09: `GRADLE_USER_HOME=/tmp/authord-gradle-user ./gradlew test jacocoTestCoverageVerification --no-daemon -Dkotlin.incremental=false`
- runIde smoke command executed on 2026-02-09: `GRADLE_USER_HOME=/tmp/authord-gradle-user ./gradlew :modules:ui-plugin:runIde --no-daemon`
- Startup evidence recorded in `modules/ui-plugin/build/idea-sandbox/system/log/idea.log`:
  - `IDE STARTED`
  - `Loaded custom plugins: Authord MkDocs Plugin (0.1.0)`
- Detailed smoke record updated in `tests/integration/plugin-shell/RunIdeSmokeValidation.md`.
- Session addendum tests passed on 2026-02-11:
  - `GRADLE_USER_HOME=/tmp/authord-gradle-user ./gradlew :modules:ui-plugin:test --tests 'com.authord.mkdocs.ui.intellij.MkdocsToolWindowFactoryTest' -x :modules:ui-plugin:jacocoTestReport -x :modules:ui-plugin:jacocoTestCoverageVerification --no-daemon -Dkotlin.incremental=false`
- Session addendum runIde verification on 2026-02-11:
  - `./gradlew :modules:ui-plugin:runIde`
- Session KDoc policy fix verification on 2026-02-11:
  - `GRADLE_USER_HOME=/tmp/authord-gradle-user ./gradlew :modules:core-domain:test --tests 'com.authord.mkdocs.core.quality.KDocCoveragePolicyTest' -x :modules:core-domain:jacocoTestReport -x :modules:core-domain:jacocoTestCoverageVerification --no-daemon -Dkotlin.incremental=false`
- Session runtime-adapter coverage verification on 2026-02-11:
  - `GRADLE_USER_HOME=/tmp/authord-gradle-user ./gradlew :modules:mkdocs-runtime-adapter:test :modules:mkdocs-runtime-adapter:jacocoTestCoverageVerification --no-daemon -Dkotlin.incremental=false`
- Current blocking gate result on 2026-02-11:
  - `GRADLE_USER_HOME=/tmp/authord-gradle-user ./gradlew test jacocoTestCoverageVerification --no-daemon -Dkotlin.incremental=false`
  - Failure: `:modules:ui-plugin:jacocoTestCoverageVerification` (line coverage ratio `0.7`, required `1.0`)

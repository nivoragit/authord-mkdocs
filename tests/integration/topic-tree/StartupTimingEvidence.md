# Startup Timing Evidence (SC-001 / R-19)

## Run Context

- Run ID: `20260215T045617Z`
- Timestamp (UTC): `2026-02-15T04:56:17Z`
- Branch: `001-mkdocs-topic-tree`
- Commit: `bc04d71e120531c40553f8d41b1be2667c5a97d5`
- Command log: `.tmp/gate-runs/20260215T045617Z_t112_t118_ui_plugin_focused.log`

## Executed Command

```bash
./gradlew :modules:ui-plugin:test --tests "*StartupReconciliationPerformanceTest" --tests "*PartialSyncZeroStateIntegrationTest" --tests "*SeededValidationDatasetIntegrationTest" --tests "*PreviewCompatibilityCriteriaTest" --tests "*MutationFeedbackPerformanceTest" --tests "*InstanceScaleProfileIntegrationTest" --no-daemon --console=plain
```

## Evidence

- Test suite: `modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/intellij/StartupReconciliationPerformanceTest.kt`
- Threshold assertion: `p95 <= 2000ms` enforced in test logic.
- Result XML: `modules/ui-plugin/build/test-results/test/TEST-com.authord.mkdocs.ui.intellij.StartupReconciliationPerformanceTest.xml`
- Suite result: `tests=1 failures=0 errors=0` (time `2.356s`)

## Disposition

- SC-001: **PASS** (threshold assertion passed in canonical Section-1 evidence run).

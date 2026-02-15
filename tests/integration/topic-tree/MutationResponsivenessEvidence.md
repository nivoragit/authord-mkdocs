# Mutation Responsiveness Evidence (SC-006 / R-20)

## Run Context

- Run ID: `20260215T045617Z`
- Branch: `001-mkdocs-topic-tree`
- Commit: `bc04d71e120531c40553f8d41b1be2667c5a97d5`
- Command log: `.tmp/gate-runs/20260215T045617Z_t112_t118_ui_plugin_focused.log`

## Evidence

- Test suite: `modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/intellij/MutationFeedbackPerformanceTest.kt`
- Threshold assertion: `p95 <= 300ms` enforced in test logic.
- Result XML: `modules/ui-plugin/build/test-results/test/TEST-com.authord.mkdocs.ui.intellij.MutationFeedbackPerformanceTest.xml`
- Suite result: `tests=1 failures=0 errors=0` (time `0.266s`)

## Disposition

- SC-006: **PASS** in run `20260215T045617Z`.

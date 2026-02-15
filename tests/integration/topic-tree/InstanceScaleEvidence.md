# Instance Scale Evidence (SC-007 / R-21)

## Run Context

- Run ID: `20260215T045617Z`
- Branch: `001-mkdocs-topic-tree`
- Commit: `bc04d71e120531c40553f8d41b1be2667c5a97d5`
- Command log: `.tmp/gate-runs/20260215T045617Z_t112_t118_ui_plugin_focused.log`

## Evidence

- Test suite: `tests/integration/topic-tree/InstanceScaleProfileIntegrationTest.kt`
- Verified profile:
- 5 instances reconciled
- 10,000 docs total synthetic profile (2,000 per instance)
- no cross-instance path leakage in reconciliation outputs
- Result XML: `modules/ui-plugin/build/test-results/test/TEST-com.authord.mkdocs.ui.intellij.InstanceScaleProfileIntegrationTest.xml`
- Suite result: `tests=1 failures=0 errors=0` (time `5.078s`)

## Disposition

- SC-007: **PASS** in run `20260215T045617Z`.

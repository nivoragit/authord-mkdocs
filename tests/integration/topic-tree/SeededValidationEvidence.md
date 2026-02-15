# Seeded Validation Evidence (SC-003 / R-10)

## Run Context

- Run ID: `20260215T045617Z`
- Branch: `001-mkdocs-topic-tree`
- Commit: `bc04d71e120531c40553f8d41b1be2667c5a97d5`
- Command log: `.tmp/gate-runs/20260215T045617Z_t112_t118_ui_plugin_focused.log`

## Evidence

- Test suite: `tests/integration/topic-tree/SeededValidationDatasetIntegrationTest.kt`
- Dataset source: `tests/fixtures/topic-tree/validation-seeded-cases.yaml`
- Verified seeded families:
- broken path
- duplicate reference
- malformed link
- Result XML: `modules/ui-plugin/build/test-results/test/TEST-integration.topic.tree.SeededValidationDatasetIntegrationTest.xml`
- Suite result: `tests=2 failures=0 errors=0` (time `0.091s`)

## Disposition

- SC-003: **PASS** in run `20260215T045617Z`.

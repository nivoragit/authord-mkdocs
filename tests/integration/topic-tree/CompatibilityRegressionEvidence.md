# Compatibility Regression Evidence (SC-004 / R-15 / R-15a)

## Run Context

- Run ID: `20260215T045617Z`
- Branch: `001-mkdocs-topic-tree`
- Commit: `bc04d71e120531c40553f8d41b1be2667c5a97d5`
- Command log: `.tmp/gate-runs/20260215T045617Z_t112_t118_ui_plugin_focused.log`

## Evidence

- Severity rubric source: `specs/001-mkdocs-topic-tree/test-plan-traceability.md` section `5. Compatibility Severity Rubric Source-of-Truth`.
- Test suite: `modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/intellij/PreviewCompatibilityCriteriaTest.kt`
- Verified conditions:
- high-severity categories classify as HIGH
- release gate blocks unresolved high-severity regressions
- release gate passes when high-severity findings are resolved
- Result XML: `modules/ui-plugin/build/test-results/test/TEST-com.authord.mkdocs.ui.intellij.PreviewCompatibilityCriteriaTest.xml`
- Suite result: `tests=3 failures=0 errors=0` (time `0.336s`)

## Disposition

- SC-004: **PASS (criteria enforcement)** for run `20260215T045617Z`.
- Note: full runIde smoke gate capture remains tracked by `T130`.

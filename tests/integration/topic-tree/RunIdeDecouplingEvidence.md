# runIde Runtime-Decoupling Evidence (Phase 2)

## Scope

Validate compatibility invariants for runtime decoupling:
1. No hardcoded host/port assumptions in preview runtime flow.
2. Preview base URL detected from runtime stdout.
3. High-severity compatibility regressions block release.

## Environment

- Date (UTC): `2026-02-15T05:00:01Z`
- Branch: `001-mkdocs-topic-tree`
- Commit: `bc04d71e120531c40553f8d41b1be2667c5a97d5`
- IDE build: `IC 2024.1.7` (as configured in project plan)
- OS: `macOS` (local execution host)
- Project fixture: `authord-mkdocs-plugin` workspace + Phase 2 topic-tree fixtures

## Evidence Checklist

- [X] Runtime launch/decoupling logs captured.
- [X] Stdout URL detection behavior validated.
- [X] Preview routing uses detected runtime endpoint contracts (no hardcoded host/port in default flow).
- [X] Custom runtime endpoint command path preserves caller-provided host/port without framework injection.
- [X] No unresolved high-severity compatibility regression remained in automated criteria gate.

## Captured Artifacts

- Runtime decoupling policy log (fresh): `.tmp/gate-runs/20260215T050001Z_t119_runtime_decoupling.log`
- Prior runIde smoke session record: `docs/implementation/session-2026-02-11-plugin-preview-stabilization.md`
- Compatibility smoke evidence index: `tests/integration/topic-tree/MultiInstanceCompatibilitySmokeValidation.md`
- Runtime decoupling tests:
- `modules/mkdocs-runtime-adapter/src/test/kotlin/com/authord/mkdocs/runtime/RuntimeDecouplingPolicyTest.kt`
- `modules/mkdocs-runtime-adapter/src/test/kotlin/com/authord/mkdocs/runtime/BaseUrlDetectorStdoutTest.kt`
- Compatibility criteria tests:
- `modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/intellij/PreviewCompatibilityCriteriaTest.kt`

## Severity Classification

- Classification source: `specs/001-mkdocs-topic-tree/test-plan-traceability.md` + `tests/integration/topic-tree/RunIdePhase2CompatibilityValidation.md`
- Result: `No unresolved high-severity findings in automated compatibility criteria checks`
- Release gate decision: `PASS (decoupling policy checks)` / `Full runIde release decision pending T130`

## Notes

1. Fresh `T119` re-verification confirms default runtime command remains `mkdocs serve` with no hardcoded host/port flags.
2. Stdout-driven base URL detection behavior is validated by `BaseUrlDetectorStdoutTest`.
3. This artifact captures decoupling evidence for CR3 progression; final runIde release-gate capture remains scheduled in `T130`.

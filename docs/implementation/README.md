# IntelliJ MkDocs Implementation Docs

This folder documents the current plugin-shell implementation cycle on branch `002-intellij-mkdocs-mvp`.

## Documents

- `feature-spec.md`: Implemented feature scope (R-01..R-06), requirements, and acceptance outcomes.
- `technical-design-notes.md`: Module architecture, flows, seams, and design constraints.
- `operational-runbook.md`: Build/test/run operations and runtime troubleshooting.
- `test-plan-traceability.md`: Test strategy and requirement-to-test mapping.
- `migration-notes.md`: Upgrade and compatibility notes.
- `session-2026-02-11-plugin-preview-stabilization.md`: Current-session addendum for live preview, runtime lifecycle hardening, and scroll sync behavior.
- `specs/002-intellij-mkdocs-mvp/checklists/release-gate.md`: Current release/compliance gate status (must be green to mark cycle complete).

## Related Source Artifacts

- Canonical planning/spec artifacts: `specs/002-intellij-mkdocs-mvp/`
- Current-session spec addendum: `specs/002-intellij-mkdocs-mvp/session-2026-02-11-preview-sync.md`
- CI coverage enforcement: `.github/workflows/ci.yml`
- Root build and quality gate config: `build.gradle.kts`
- Changelog: `CHANGELOG.md`

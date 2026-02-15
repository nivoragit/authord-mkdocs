# Startup Reconciliation Smoke Validation

## Purpose

Capture repeatable smoke evidence for US1 startup tree loading and reconciliation behavior.

## Preconditions

- Project has `mkdocs.yml` (or `mkdocs.yaml`) and a `docs/` directory.
- Plugin is installed and enabled.
- No manual mutations are running during startup validation.

## Procedure

1. Open a project with `mkdocs.yml` containing `nav`.
2. Open the MkDocs tool window and wait for initial preview startup.
3. Confirm startup reconciliation runs without destructive edits.
4. Verify tree order matches canonical `nav` ordering.
5. Verify missing nav paths are surfaced as validation issues.
6. Verify markdown files under `docs/` absent from nav appear in unlinked bucket output.
7. Repeat with a project where `nav` is missing and confirm deterministic fallback tree.

## Evidence Checklist

- [ ] Runtime start result captured (success/failure + message)
- [ ] Startup tree source captured (`NAV` or `FALLBACK`)
- [ ] Ordered tree snapshot attached
- [ ] Unlinked bucket snapshot attached
- [ ] Validation issue summary attached
- [ ] Confirmation that no destructive automatic deletion occurred

## Evidence Links

- Logs/screenshots:
- Traceability row updates:
- Reviewer/date:

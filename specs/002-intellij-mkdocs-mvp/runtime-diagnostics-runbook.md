# Runtime Diagnostics Runbook: IntelliJ MkDocs Plugin MVP

## Purpose

Operational checks and troubleshooting for mkdocs runtime lifecycle in MVP.

## Lifecycle Checks

### A) First Activation Bootstrap

1. Verify runtime setup entered `BOOTSTRAPPING`.
2. Verify `uv` bootstrap command started.
3. Verify `mkdocs` installation completed.
4. Verify lifecycle transitioned to `READY`.

Failure Signals:
- Runtime path not created.
- `uv` command not found.
- `mkdocs` install failure output.

Actions:
- Confirm `uv` availability.
- Capture bootstrap command stderr/stdout.
- Retry bootstrap after cleanup of partially created runtime directory.

### B) Runtime Start

1. Verify only one start operation is active for project.
2. Verify mkdocs process is spawned.
3. Verify base URL parsed from stdout.
4. Verify lifecycle transitioned to `SERVING`.

Failure Signals:
- Duplicate process instance for same project.
- Base URL not detected within timeout.
- Process exits immediately after launch.

Actions:
- Enforce single-instance lock by project ID.
- Capture full startup output and parser diagnostics.
- Validate docs configuration presence.

### C) Runtime Restart

1. Verify transition `SERVING -> RESTARTING`.
2. Verify previous process termination.
3. Verify new process launch and URL detection.
4. Verify return to `SERVING`.

Failure Signals:
- Old process remains alive.
- New process starts without valid URL.

Actions:
- Force kill orphaned process and re-check PID ownership.
- Re-run URL parser against captured output.

### D) Runtime Stop and Disposal

1. Verify transition `SERVING -> STOPPING -> STOPPED`.
2. Verify process resources released.
3. On IDE/project disposal, verify terminal transition to `DISPOSED`.

Failure Signals:
- Process remains alive after stop/dispose.
- Preview pane still active with stale URL.

Actions:
- Trigger dispose-safe cleanup path.
- Clear preview session and runtime state cache.

## Diagnostics Artifacts to Capture

- Lifecycle state transitions with timestamps.
- Runtime setup and mkdocs startup output.
- Parsed base URL and parser status.
- Process identifiers and termination status.
- Feature flag snapshot for runtime and preview behavior.

## Escalation Criteria

- Repeated startup failure across three attempts in same project.
- Persistent duplicate process instances.
- Dispose cleanup failures that leak process handles.

## Validated Signatures (2026-02-08)

- `BASE_URL_NOT_FOUND`: startup output did not contain URL token; activation returned non-success with explicit guidance.
- `BOOTSTRAP_FAILED`: simulated non-zero exit from `uv` bootstrap command produced actionable error details.
- `SINGLE_INSTANCE_GUARD`: repeated start request for same project returned already-running status and did not launch a second process.
- `RESTART_REPLACED_PROCESS`: restart request stopped prior process handle and started a new handle ID.

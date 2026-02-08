# Operational Runbook: IntelliJ MkDocs MVP

## 1. Purpose

Provide day-to-day run, validate, and troubleshoot procedures for the current MVP implementation.

## 2. Prerequisites

1. JDK 21 available for Gradle builds.
2. `uv` available on PATH for runtime bootstrap flows.
3. Network access available when installing `mkdocs` during first bootstrap.
4. A project path containing markdown docs under `docs/` for navigation scenarios.

## 3. Build and Test Commands

Run from repository root.

## 3.1 Standard validation

```bash
GRADLE_USER_HOME=$PWD/.gradle-user ./gradlew test jacocoTestCoverageVerification --no-daemon
```

## 3.2 Module-specific checks

```bash
GRADLE_USER_HOME=$PWD/.gradle-user ./gradlew :modules:core-domain:test --no-daemon
GRADLE_USER_HOME=$PWD/.gradle-user ./gradlew :modules:mkdocs-runtime-adapter:test --no-daemon
GRADLE_USER_HOME=$PWD/.gradle-user ./gradlew :modules:ui-plugin:test --no-daemon
```

## 3.3 CI-equivalent command

```bash
./gradlew clean test jacocoTestCoverageVerification --no-daemon
```

Matches `.github/workflows/ci.yml`.

## 4. Runtime Lifecycle Expectations

## 4.1 First activation

Expected sequence:
1. Bootstrap runtime directory `.mkdocs-plugin-venv` under project path.
2. Execute `uv venv <runtime-path>`.
3. Execute `uv pip install mkdocs`.
4. Start MkDocs process and parse base URL.
5. Open preview state.

## 4.2 Start/stop/restart

1. Start should reuse the same process if already alive for same project ID.
2. Stop should terminate process and clear tracked running state.
3. Restart should stop old handle and create a new one.
4. Dispose path should be stop-safe.

## 5. Operational Failure Signatures

## 5.1 `BOOTSTRAP_FAILED`

Signals:
- `uv` not installed or runtime creation fails.
- `mkdocs` install command returns non-zero.

Actions:
1. Verify `uv --version` succeeds.
2. Capture stderr from bootstrap commands.
3. Remove partial runtime directory and retry activation.

## 5.2 `BASE_URL_NOT_FOUND`

Signals:
- Startup output does not include URL token.

Actions:
1. Capture full startup stdout.
2. Confirm MkDocs startup completed and emitted URL.
3. Re-run activation with known-good startup output.

## 5.3 `SINGLE_INSTANCE_GUARD`

Signals:
- Repeated starts should report already-running and avoid duplicate process launch.

Actions:
1. Verify process manager state by project ID.
2. Validate no second process ID is created for same project.

## 5.4 `RESTART_REPLACED_PROCESS`

Signals:
- Restart should produce a new process handle after stopping previous one.

Actions:
1. Compare process IDs before and after restart.
2. Verify prior handle is no longer alive.

## 6. Runtime Diagnostics Capture Checklist

1. Lifecycle state transition sequence with timestamps.
2. Bootstrap command outputs (`uv venv`, `uv pip install mkdocs`).
3. MkDocs startup stdout used for URL detection.
4. Process IDs and alive/dead status across start/restart/stop.
5. Active feature flag policy snapshot.

## 7. Escalation Thresholds

Escalate when one of these occurs:
1. Startup fails 3 times consecutively for same project.
2. Duplicate process instances are observed for one project ID.
3. Stop/dispose repeatedly leaves orphaned processes.

## 8. Recovery Playbook

1. Stop runtime for project.
2. Remove stale runtime directory only if bootstrap is corrupt.
3. Re-run activation bootstrap and start flow.
4. Re-run integration tests:
- `ActivationToPreviewIT`
- `ExplorerSelectionToPreviewIT`
5. Re-run full coverage gate command before closing incident.

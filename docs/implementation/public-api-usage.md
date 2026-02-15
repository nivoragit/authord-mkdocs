# Public API Usage Summary (Phase 2 Topic Tree)

## Scope

This summary captures public usage contracts for Phase 2 topic-tree interfaces and UI-facing services.

## Interface Inventory

1. `MkDocsConfigGateway`
- Path: `modules/extension-ports/src/main/kotlin/com/authord/mkdocs/ports/topic/MkDocsConfigGateway.kt`
- Purpose: parse/load/write deterministic `mkdocs.yml` config documents.
- Inputs: `TopicInstanceRef`, `MkDocsConfigDocument`.
- Outputs: `TopicGatewayResult<MkDocsConfigDocument|Unit|String>`.
- Failure modes: `CONFIG_PARSE`, `CONFIG_WRITE`, `VALIDATION`.

2. `DocsFileGateway`
- Path: `modules/extension-ports/src/main/kotlin/com/authord/mkdocs/ports/topic/DocsFileGateway.kt`
- Purpose: create/delete/rename/move markdown files and rewrite relative links.
- Inputs: instance scope + docs-relative paths + operation mode.
- Outputs: typed success paths/counts via `TopicGatewayResult`.
- Failure modes: `FILE_IO`, `VALIDATION`, `INSTANCE_SCOPE`.

3. `TreeSyncOrchestrator`
- Path: `modules/extension-ports/src/main/kotlin/com/authord/mkdocs/ports/topic/TreeSyncOrchestrator.kt`
- Purpose: atomic apply/rollback/compensate for topic-tree mutations.
- Inputs: `TopicSyncTransaction`, `transactionId`, rollback reason.
- Outputs: `TopicGatewayResult<TopicSyncOutcome>`.
- Failure modes: `ORCHESTRATION`, `CONFIG_WRITE`, `FILE_IO`.

4. `InstanceRegistryPort`
- Path: `modules/extension-ports/src/main/kotlin/com/authord/mkdocs/ports/topic/InstanceRegistryPort.kt`
- Purpose: discover/register/list/select active MkDocs instances.
- Inputs: `projectRootPath`, `TopicInstanceRef`, `instanceId`.
- Outputs: typed instance views via `TopicGatewayResult`.
- Failure modes: `INSTANCE_SCOPE`, `VALIDATION`.

5. `TopicTreeApplicationService`
- Path: `modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/intellij/TopicTreeApplicationService.kt`
- Purpose: orchestration boundary for execute/rollback + active instance visibility.
- Inputs: `TopicSyncTransaction`, `transactionId`, reason.
- Outputs: `TopicGatewayResult<TopicSyncOutcome|TopicInstanceRef?>`.
- Failure modes: typed downstream gateway/orchestration errors.

6. `TopicTreeUiService`
- Path: `modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/intellij/TopicTreeUiService.kt`
- Purpose: UI command dispatch, active tree refresh, and instance selection.
- Inputs: `TopicTreeCommand`, `instanceId`.
- Outputs: `TopicGatewayResult<TopicSyncOutcome|TopicInstanceRef>`.
- Failure modes: validation/orchestration/reconciliation/instance-scope errors.

## Usage Rules

1. Keep all operations instance-scoped; do not call file/config APIs without `TopicInstanceRef`.
2. Treat `TopicGatewayResult.Failure` as the primary error contract and branch by typed error code.
3. Use orchestrator rollback/compensation paths for partial mutation failures.
4. Keep runtime endpoint handling decoupled from hardcoded host/port assumptions.
5. Preserve deterministic serialization expectations for `mkdocs.yml` writes.

## Versioning and Compatibility Notes

1. Contracts remain additive for Phase 2; no breaking API migration is required in this release window.
2. Compatibility severity and release blocking behavior are governed by:
- `specs/001-mkdocs-topic-tree/test-plan-traceability.md`
- `tests/integration/topic-tree/CompatibilityRegressionEvidence.md`

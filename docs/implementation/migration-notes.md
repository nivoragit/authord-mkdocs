# Migration Notes

## Release Context

This cycle includes Phase 2 topic-tree evidence and documentation closure updates on top of existing MVP and plugin-shell services.

## Compatibility Impact

No breaking migration steps are required.

## Backward Compatibility Notes

1. Existing MVP runtime/domain modules remain unchanged in behavior.
2. Mandatory seams remain present and compatible:
- `TopicTreePort`
- `PluginCommandBus` + `CommandRegistry`
- `PreviewSyncPort`
- `VectorStorePort`
3. Plugin-shell additions are additive and do not enable future-cycle features by default.
4. Session stabilization changes are behavior-level refinements only:
- runtime serve command now uses `--livereload --dirty`,
- runtime process is tied to IDE parent lifecycle,
- preview sync uses viewport-percentage mapping from editor to preview.
5. Phase 2 evidence artifacts are additive and do not introduce API-breaking behavior:
- `tests/integration/topic-tree/StartupTimingEvidence.md`
- `tests/integration/topic-tree/PartialSyncZeroStateEvidence.md`
- `tests/integration/topic-tree/SeededValidationEvidence.md`
- `tests/integration/topic-tree/CompatibilityRegressionEvidence.md`
- `tests/integration/topic-tree/MutationResponsivenessEvidence.md`
- `tests/integration/topic-tree/InstanceScaleEvidence.md`
- `docs/implementation/public-api-usage.md`

## If Future Breaking Changes Occur

Add a migration section with:
1. interface version changes,
2. required configuration updates,
3. compatibility window and rollback guidance.

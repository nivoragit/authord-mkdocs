# Migration Notes

## Release Context

This cycle adds plugin-shell entry wiring (build/plugin descriptor/tool-window/action/runtime integration) on top of existing MVP services.

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

## If Future Breaking Changes Occur

Add a migration section with:
1. interface version changes,
2. required configuration updates,
3. compatibility window and rollback guidance.

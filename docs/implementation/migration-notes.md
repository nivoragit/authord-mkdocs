# Migration Notes

## Current Release Context

This is the first implementation baseline for the IntelliJ MkDocs MVP in this repository.

## Migration Impact

No data, schema, or runtime migration steps are required.

## Compatibility Notes

1. Extension seams (`TopicTreePort`, `PluginCommandBus`/`CommandRegistry`, `PreviewSyncPort`, `VectorStorePort`) are present with minimal/default behavior.
2. Future-cycle capabilities remain disabled by default feature policy.
3. Existing projects only need standard prerequisites (`JDK 21`, `uv`) to run build/test validation.

## Forward-Looking Migration Guidance

If future cycles enable currently excluded capabilities, add a versioned migration section covering:
1. Behavioral flag defaults and rollout strategy.
2. Contract/schema changes for command/event payloads.
3. Any persisted state migration (if persistence is introduced later).

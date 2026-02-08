# Data Model: IntelliJ MkDocs Plugin MVP

## Overview

This model defines domain entities, contract DTOs, and lifecycle state needed for the MVP and extension seams.

## Entities

### 1) TopicTree

- Purpose: Aggregate root for ordered documentation topics managed by command mutations.
- Fields:
  - `treeId` (string, immutable)
  - `rootNodeId` (string, immutable)
  - `nodes` (map of `TopicNode` keyed by `nodeId`)
  - `version` (integer, monotonic)
- Relationships:
  - Owns many `TopicNode`.
  - Mutated by `TopicTreeCommand`.
- Validation Rules:
  - Exactly one root node.
  - No cycles in parent-child graph.
  - Sibling order must be contiguous and unique.
  - Parent must exist for non-root nodes.
  - `version` increments by 1 per successful mutation.

### 2) TopicNode

- Purpose: Represents a single topic entry in the topic tree.
- Fields:
  - `nodeId` (string, immutable)
  - `parentNodeId` (string, nullable only for root)
  - `title` (string, required, non-empty)
  - `sourcePath` (string, optional in MVP)
  - `orderIndex` (integer, required)
  - `status` (enum: `ACTIVE`, `REMOVED`)
- Relationships:
  - Belongs to one `TopicTree`.
  - Referenced by add/move/remove/reorder/validate commands.
- Validation Rules:
  - `title` non-empty after trim.
  - `orderIndex >= 0`.
  - Root cannot be removed.

### 3) TopicTreeCommand

- Purpose: DTO for mutation requests routed through command bus/registry.
- Fields:
  - `commandId` (string, immutable, unique)
  - `commandType` (enum: `ADD`, `MOVE`, `REMOVE`, `REORDER`, `VALIDATE`)
  - `treeId` (string, required)
  - `payload` (object, shape depends on command type)
  - `requestedAt` (timestamp)
- Relationships:
  - Produces `TopicTreeCommandResult`.
  - Routed by `PluginCommandBus`.
- Validation Rules:
  - `payload` must match command schema for `commandType`.
  - `commandId` must be unique per request.

### 4) TopicTreeCommandResult

- Purpose: Standard result envelope for command execution.
- Fields:
  - `commandId` (string, required)
  - `status` (enum: `SUCCESS`, `REJECTED`, `FAILED`)
  - `treeVersion` (integer, optional)
  - `violations` (array of violation objects)
  - `message` (string)
- Relationships:
  - Returned by `TopicTreePort` commands.
- Validation Rules:
  - `SUCCESS` requires `treeVersion`.
  - `REJECTED` requires at least one violation.

### 5) RouteMappingRequest

- Purpose: Input for path-to-preview route conversion.
- Fields:
  - `selectedPath` (string, required)
  - `docsRoot` (string, required)
- Relationships:
  - Consumed by `RouteMappingService`.
  - Produces `RouteMappingResult`.
- Validation Rules:
  - `selectedPath` must resolve under `docsRoot`.
  - Only markdown file extensions are eligible.

### 6) RouteMappingResult

- Purpose: Deterministic output for preview route changes.
- Fields:
  - `route` (string, required)
  - `normalizedPath` (string, required)
  - `mappingRule` (enum: `ROOT_INDEX`, `SEGMENT_INDEX`, `SEGMENT_FILE`, `NESTED`)
- Relationships:
  - Emitted as part of preview navigation event flow.
- Validation Rules:
  - `route` starts and ends with `/`.
  - Root route is exactly `/`.

### 7) ScrollSemanticSample

- Purpose: Captures semantic scroll delta excluding comment PSI ranges.
- Fields:
  - `sampleId` (string)
  - `documentPath` (string)
  - `rawDelta` (integer)
  - `excludedCommentDelta` (integer)
  - `semanticDelta` (integer)
  - `capturedAt` (timestamp)
- Relationships:
  - Produced by `ScrollSemanticService`.
  - Optionally forwarded to `PreviewSyncPort`.
- Validation Rules:
  - `semanticDelta = rawDelta - excludedCommentDelta`.
  - Excluded ranges must correspond to comment PSI regions only.

### 8) MkDocsRuntimeState

- Purpose: Tracks runtime setup and mkdocs process lifecycle for one project.
- Fields:
  - `projectId` (string)
  - `state` (enum: `UNINITIALIZED`, `BOOTSTRAPPING`, `READY`, `SERVING`, `RESTARTING`, `STOPPING`, `STOPPED`, `FAILED`, `DISPOSED`)
  - `runtimePath` (string, optional)
  - `processId` (string, optional)
  - `baseUrl` (string, optional)
  - `lastError` (string, optional)
- Relationships:
  - Owned by runtime manager per project.
  - Drives preview session creation and teardown.
- Validation Rules:
  - One active `SERVING` process per `projectId`.
  - `baseUrl` required for `SERVING`.
  - `DISPOSED` is terminal.

### 9) PreviewSession

- Purpose: Represents current embedded preview pane state.
- Fields:
  - `sessionId` (string)
  - `projectId` (string)
  - `baseUrl` (string)
  - `currentRoute` (string)
  - `status` (enum: `OPEN`, `CLOSED`, `ERROR`)
- Relationships:
  - Depends on `MkDocsRuntimeState` with valid base URL.
  - Updated by file selection and route mapping.
- Validation Rules:
  - `OPEN` requires reachable `baseUrl`.
  - Route updates require active session status.

### 10) DocsFileSelectionEvent

- Purpose: Event emitted when user selects a docs markdown file in explorer.
- Fields:
  - `eventId` (string)
  - `projectId` (string)
  - `selectedPath` (string)
  - `occurredAt` (timestamp)
- Relationships:
  - Consumed by navigation coordinator.
  - Triggers `RouteMappingRequest`.
- Validation Rules:
  - `selectedPath` must be inside docs root and markdown.

### 11) PreviewNavigationEvent

- Purpose: Event emitted for requested/applied preview route changes.
- Fields:
  - `eventId` (string)
  - `projectId` (string)
  - `route` (string)
  - `phase` (enum: `REQUESTED`, `APPLIED`, `FAILED`)
  - `reason` (string, optional)
  - `occurredAt` (timestamp)
- Relationships:
  - Produced by navigation coordinator.
  - Can be observed by `PreviewSyncPort`.
- Validation Rules:
  - `APPLIED` requires valid route.
  - `FAILED` requires reason.

### 12) FeatureFlagSet

- Purpose: Controls staged enablement of MVP and extension seams.
- Fields:
  - `flags` (map<string, boolean>)
  - `updatedAt` (timestamp)
- Relationships:
  - Read by runtime, UI coordinator, and seam adapters.
- Validation Rules:
  - Out-of-scope capabilities default to disabled.

## State Transitions

### Runtime Lifecycle

1. `UNINITIALIZED -> BOOTSTRAPPING`: first activation starts runtime setup.
2. `BOOTSTRAPPING -> READY`: runtime created and tooling installed.
3. `READY -> SERVING`: mkdocs process launched and base URL detected.
4. `SERVING -> RESTARTING`: explicit restart command.
5. `RESTARTING -> SERVING`: process relaunched and URL revalidated.
6. `SERVING -> STOPPING -> STOPPED`: explicit stop command or IDE deactivation.
7. `ANY_ACTIVE -> FAILED`: setup/startup/process failure.
8. `STOPPED|FAILED -> DISPOSED`: project disposal / IDE shutdown cleanup.

### Topic Tree Mutation Lifecycle

1. `Command Received`: command bus routes typed DTO.
2. `Validation`: invariant checks run by domain service.
3. `Mutation`: apply if valid.
4. `Result`: emit success/rejected/failed result DTO.

## Invariant Summary

- Topic tree is acyclic, rooted, and order-consistent.
- Route mapping rules are deterministic and path-safe.
- Semantic scroll excludes comment PSI ranges by definition.
- Runtime allows only one active mkdocs server per project.
- Extension ports remain available even when adapters are no-op.

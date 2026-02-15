# Data Model: Phase 2 - MkDocs Topic Tree Management

## Overview

This model defines the Phase 2 domain and orchestration entities required to synchronize topic-tree mutations across in-memory tree state, `mkdocs.yml`, and markdown files while preserving MVP preview compatibility.

## Domain Core Entities

### 1) TopicId

- Purpose: Stable identity value for a topic node.
- Fields:
  - `value` (string, immutable, non-blank)
- Validation Rules:
  - Must be unique within an instance tree scope.
  - Must be stable across reorder operations.

### 2) TopicOrder

- Purpose: Canonical sibling ordering marker.
- Fields:
  - `value` (integer, `>= 0`)
- Validation Rules:
  - Must be contiguous inside a sibling set after normalization.

### 3) TopicLink

- Purpose: Logical relationship from one topic to another (`child`, `external`, `instance-ref` as applicable).
- Fields:
  - `from` (`TopicId`)
  - `to` (`TopicId`)
  - `relation` (string enum-like value)
- Validation Rules:
  - `from` and `to` must exist in same instance scope unless relation is external.

### 4) TopicMetadata

- Purpose: Extensible metadata envelope for node attributes.
- Fields:
  - `attributes` (map string->string)
- Validation Rules:
  - Reserved keys must not be overwritten by UI adapters.

### 5) TopicNode

- Purpose: Tree node representing page, section, or external link.
- Fields:
  - `topicId` (`TopicId`)
  - `title` (string)
  - `kind` (`PAGE`, `SECTION`, `EXTERNAL_LINK`)
  - `order` (`TopicOrder`)
  - `parentId` (`TopicId?`)
  - `sourcePath` (project-relative path for markdown-backed nodes)
  - `externalUrl` (string? for external nodes)
  - `metadata` (`TopicMetadata`)
- Validation Rules:
  - `kind=PAGE` requires `sourcePath`.
  - `kind=EXTERNAL_LINK` requires well-formed URL and no local `sourcePath`.
  - Root node has `parentId = null`.

### 6) TopicTree

- Purpose: Instance-scoped hierarchical aggregate for all topic nodes.
- Fields:
  - `treeId` (string)
  - `instanceId` (string)
  - `nodes` (map `TopicId` -> `TopicNode`)
  - `links` (list `TopicLink`)
  - `version` (integer)
- Validation Rules:
  - Exactly one root container.
  - No parent-child cycles.
  - Node order uniqueness per parent.

## Command and Validation Entities

### 7) TopicMutationCommand

- Purpose: Mutation request envelope.
- Fields:
  - `commandId` (string)
  - `instanceId` (string)
  - `commandType` (`ADD`, `MOVE`, `REMOVE`, `RENAME`, `REPARENT`, `REORDER`, `VALIDATE`)
  - `payload` (type-specific command data)
  - `requestedAt` (timestamp)
- Validation Rules:
  - `instanceId` must resolve to active registry entry.
  - Payload must satisfy command-specific preconditions.

### 8) ValidationIssue

- Purpose: Structured validation or reconciliation finding.
- Fields:
  - `issueId` (string)
  - `category` (`BROKEN_PATH`, `DUPLICATE_REFERENCE`, `MALFORMED_LINK`, `MISSING_FILE`, `INSTANCE_SCOPE`, `OTHER`)
  - `severity` (`INFO`, `WARN`, `ERROR`)
  - `message` (string)
  - `nodeRef` (`TopicId?`)
  - `pathRef` (string?)
- Validation Rules:
  - `category`, `severity`, and `message` are mandatory.

### 9) MutationOutcome

- Purpose: Result envelope from mutation command execution.
- Fields:
  - `commandId` (string)
  - `status` (`SUCCESS`, `REJECTED`, `FAILED`)
  - `treeVersion` (integer?)
  - `issues` (list `ValidationIssue`)
  - `message` (string)
- Validation Rules:
  - `SUCCESS` requires updated `treeVersion`.
  - `REJECTED`/`FAILED` should include one or more issues or message details.

## Persistence and Sync Entities

### 10) MkDocsConfigDocument

- Purpose: Parsed canonical configuration representation.
- Fields:
  - `configPath` (absolute path)
  - `docsDir` (relative path)
  - `navEntries` (ordered nav tree)
  - `notInNavEntries` (ordered list of unlinked docs references)
  - `rawOtherKeys` (ordered map for non-nav config)
- Validation Rules:
  - `docsDir` must resolve within project root.
  - `navEntries` logical structure must be serializable deterministically.

### 11) DocsFileMutation

- Purpose: Filesystem operation needed to align docs files with nav changes.
- Fields:
  - `operation` (`CREATE`, `DELETE_TO_RECOVERY`, `RENAME`, `MOVE`, `NO_OP`)
  - `sourcePath` (path?)
  - `targetPath` (path?)
  - `recoveryPath` (path?)
- Validation Rules:
  - `RENAME` and `MOVE` require both source and target paths.
  - `DELETE_TO_RECOVERY` requires recovery path.

### 12) TreeSyncTransaction

- Purpose: Atomic orchestration context across tree/nav/file stages.
- Fields:
  - `transactionId` (string)
  - `instanceId` (string)
  - `command` (`TopicMutationCommand`)
  - `preStateSnapshot` (tree + config summary)
  - `plannedFileMutations` (list `DocsFileMutation`)
  - `compensationStack` (ordered rollback actions)
  - `status` (`PREPARED`, `APPLYING`, `VERIFYING`, `COMMITTED`, `ROLLED_BACK`, `FAILED`)
- Validation Rules:
  - Commit allowed only after verification pass.
  - Rollback path must be idempotent and logged.

### 13) ReconciliationReport

- Purpose: Startup/watch-triggered divergence analysis output.
- Fields:
  - `instanceId` (string)
  - `policy` (`NAV_FIRST_NON_DESTRUCTIVE`)
  - `missingNavTargets` (list paths)
  - `unlinkedFiles` (list paths)
  - `duplicateReferences` (list paths)
  - `malformedLinks` (list strings)
  - `actionsTaken` (list strings)
- Validation Rules:
  - Automatic destructive actions are not allowed.

## Instance Management Entities

### 14) MkDocsInstance

- Purpose: Registered MkDocs configuration scope.
- Fields:
  - `instanceId` (string)
  - `projectRoot` (absolute path)
  - `configPath` (absolute path)
  - `docsDirPath` (absolute path)
  - `label` (string)
  - `isDefault` (boolean)
- Validation Rules:
  - Only one default instance per project.
  - `configPath` and `docsDirPath` must remain inside project boundary unless explicitly allowed by policy.

### 15) InstanceRegistryState

- Purpose: Persisted per-project instance-selection state.
- Fields:
  - `projectId` (string)
  - `instances` (ordered list `MkDocsInstance`)
  - `activeInstanceId` (string)
  - `lastSelectedInstanceId` (string)
- Validation Rules:
  - `activeInstanceId` must exist in `instances`.

## Error and Observability Entities

### 16) SyncError

- Purpose: Typed error taxonomy element for operator visibility.
- Fields:
  - `errorCode` (`VALIDATION`, `CONFIG_PARSE`, `CONFIG_WRITE`, `FILE_IO`, `RECONCILIATION`, `ORCHESTRATION`, `INSTANCE_SCOPE`)
  - `message` (string)
  - `recoverable` (boolean)
  - `context` (map string->string)
- Validation Rules:
  - `errorCode` and `message` required.

### 17) SyncEvent

- Purpose: Structured observability event for logs/notifications.
- Fields:
  - `eventId` (string)
  - `instanceId` (string)
  - `transactionId` (string?)
  - `eventType` (`MUTATION_APPLIED`, `MUTATION_ROLLED_BACK`, `RECONCILIATION_COMPLETED`, `VALIDATION_REPORTED`, `INSTANCE_SWITCHED`)
  - `occurredAt` (timestamp)
  - `details` (map string->string)
- Validation Rules:
  - Must include enough correlation metadata for troubleshooting.

## State Transitions

### A) Mutation Transaction Lifecycle

1. `PREPARED` -> `APPLYING` -> `VERIFYING` -> `COMMITTED`
2. Any failure in `APPLYING` or `VERIFYING` transitions to `ROLLED_BACK` (if compensation succeeds) or `FAILED` (if compensation fails)

### B) Startup/Reconciliation Lifecycle

1. `DISCOVER_INSTANCE` -> `LOAD_CONFIG` -> `BUILD_TREE_OR_FALLBACK` -> `RECONCILE` -> `PUBLISH_ISSUES`
2. Reconciliation never transitions into destructive auto-delete state

### C) Instance Selection Lifecycle

1. `DEFAULT_DISCOVERED` -> `ACTIVE_SELECTED`
2. `EXPLICIT_INSTANCE_ADDED` -> `ACTIVE_SELECTED`
3. `PROJECT_REOPENED` -> `LAST_SELECTED_RESTORED`

## Invariants

- `mkdocs.yml` nav remains canonical persisted navigation source.
- Tree, config, and docs-file mutations are atomic or compensated.
- Deterministic serialization avoids churn-only diffs.
- Operations are scoped to active instance only.
- MVP preview behavior remains backward compatible.

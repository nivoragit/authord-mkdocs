# Test Plan and Requirements Traceability

## 1. Test Objectives

1. Verify all MVP in-scope requirements are covered by executable tests.
2. Prove out-of-scope features remain disabled/unimplemented by default behavior.
3. Enforce 100% scoped unit coverage.
4. Keep documentation and contract artifacts tied to test evidence.

## 2. Test Levels

1. Unit tests
- Domain invariants and route/scroll semantics.
- Runtime adapter behavior and lifecycle edge cases.
- UI orchestration services and guardrails.
- Port and default adapter contracts.

2. Integration tests
- Activation lifecycle from bootstrap through preview open.
- Docs explorer selection to preview route update flow.

3. Contract tests
- Topic tree command seam compatibility.
- Default adapters satisfy seam contracts without enabling future-cycle behavior.

4. Documentation/quality policy tests
- Required SDD artifacts exist.
- Coverage scope and enforcement configuration stay intact.
- Constitution policy checks for runtime neutrality and KDoc compliance stay intact.

## 3. Execution Commands

## 3.1 Full suite + coverage gate

```bash
GRADLE_USER_HOME=$PWD/.gradle-user ./gradlew test jacocoTestCoverageVerification --no-daemon
```

## 3.2 Integration-only smoke

```bash
GRADLE_USER_HOME=$PWD/.gradle-user ./gradlew :modules:ui-plugin:test --tests "integration.runtime.ActivationToPreviewIT" --tests "integration.navigation.ExplorerSelectionToPreviewIT" --no-daemon
```

## 4. Requirement-to-Test Traceability Matrix

| Requirement | Summary | Unit Tests | Integration/Contract/Policy Tests |
|-------------|---------|------------|-----------------------------------|
| FR-001 | Create plugin-managed runtime on first activation | `UvBootstrapServiceTest` | `ActivationToPreviewIT` |
| FR-002 | Install documentation tooling during first activation | `UvBootstrapServiceTest` | `ActivationToPreviewIT` |
| FR-003 | Start preview serving with runtime defaults and no hardcoded host/port | `MkdocsProcessManagerTest` | `ActivationToPreviewIT` |
| FR-004 | Detect base URL from startup output | `BaseUrlDetectorTest` | `ActivationToPreviewIT` |
| FR-005 | Open side-by-side preview after URL detection | `PreviewPaneCoordinatorTest`, `PluginActivationServiceTest` | `ActivationToPreviewIT` |
| FR-006 | Provide dedicated docs explorer | `DocsExplorerServiceTest` | `ExplorerSelectionToPreviewIT` |
| FR-007 | Update preview route on docs file selection | `NavigationCoordinatorTest` | `ExplorerSelectionToPreviewIT` |
| FR-008 | Map `docs/index.md` to `/` | `RouteMappingServiceTest` | - |
| FR-009 | Map `docs/<segment>/index.md` to `/<segment>/` | `RouteMappingServiceTest` | - |
| FR-010 | Map `docs/<segment>.md` to `/<segment>/` | `RouteMappingServiceTest` | - |
| FR-011 | Apply route mapping rules to nested paths | `RouteMappingServiceTest` | - |
| FR-012 | Track semantic scroll delta excluding comments | `ScrollSemanticServiceTest` | - |
| FR-013 | TopicTree command seam with add/move/remove/rename/reparent/reorder/validate | `TopicTreeAggregateTest` | `TopicTreePortContractTest` |
| FR-014 | PluginCommandBus + CommandRegistry seams | `PluginCommandBusContractTest`, `InMemoryCommandRegistryTest` | `TopicTreePortContractTest` |
| FR-015 | VectorStorePort seam (minimal/default) | `NoOpAdaptersTest` | `TopicTreePortContractTest` |
| FR-016 | PreviewSyncPort seam (minimal/default) | `NoOpAdaptersTest` | `TopicTreePortContractTest` |
| FR-017 | Feature flags for staged enablement | `FeatureFlagPolicyTest`, `FeatureFlagPolicyServiceTest` | `OutOfScopeGuardrailsTest` |
| FR-018 | SDD artifacts required | `DocumentationArtifactsPolicyTest` | ADRs/runbook/traceability review |
| FR-019 | 100% scoped unit coverage | `CoverageScopePolicyTest` | CI `jacocoTestCoverageVerification` |
| FR-020 | No code<->preview scroll sync in MVP | `OutOfScopeGuardrailsTest`, `FeatureFlagPolicyTest` | - |
| FR-021 | No AI chatbot/command execution in MVP | `OutOfScopeGuardrailsTest`, `FeatureFlagPolicyTest` | - |
| FR-022 | No vector database behavior in MVP | `OutOfScopeGuardrailsTest`, `NoOpAdaptersTest` | `TopicTreePortContractTest` |
| FR-023 | No WriterSide-like full parity UX in MVP | `OutOfScopeGuardrailsTest`, `FeatureFlagPolicyTest` | - |

## 5. Test Inventory (Paths)

1. Unit tests: `modules/*/src/test/kotlin/...`
2. Integration tests:
- `tests/integration/runtime-lifecycle/ActivationToPreviewIT.kt`
- `tests/integration/explorer-preview-navigation/ExplorerSelectionToPreviewIT.kt`
3. Contract tests:
- `tests/contract/topic-tree-command-port/TopicTreePortContractTest.kt`

## 5.1 Test Class to File Reference

| Test Class | File |
|------------|------|
| `UvBootstrapServiceTest` | `modules/mkdocs-runtime-adapter/src/test/kotlin/com/authord/mkdocs/runtime/UvBootstrapServiceTest.kt` |
| `MkdocsProcessManagerTest` | `modules/mkdocs-runtime-adapter/src/test/kotlin/com/authord/mkdocs/runtime/MkdocsProcessManagerTest.kt` |
| `BaseUrlDetectorTest` | `modules/mkdocs-runtime-adapter/src/test/kotlin/com/authord/mkdocs/runtime/BaseUrlDetectorTest.kt` |
| `PluginActivationServiceTest` | `modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/PluginActivationServiceTest.kt` |
| `PreviewPaneCoordinatorTest` | `modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/PreviewPaneCoordinatorTest.kt` |
| `DocsExplorerServiceTest` | `modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/DocsExplorerServiceTest.kt` |
| `NavigationCoordinatorTest` | `modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/NavigationCoordinatorTest.kt` |
| `RouteMappingServiceTest` | `modules/core-domain/src/test/kotlin/com/authord/mkdocs/core/navigation/RouteMappingServiceTest.kt` |
| `ScrollSemanticServiceTest` | `modules/core-domain/src/test/kotlin/com/authord/mkdocs/core/scroll/ScrollSemanticServiceTest.kt` |
| `TopicTreeAggregateTest` | `modules/core-domain/src/test/kotlin/com/authord/mkdocs/core/topic/TopicTreeAggregateTest.kt` |
| `TopicDomainModelTest` | `modules/core-domain/src/test/kotlin/com/authord/mkdocs/core/topic/TopicDomainModelTest.kt` |
| `PluginCommandBusContractTest` | `modules/extension-ports/src/test/kotlin/com/authord/mkdocs/ports/PluginCommandBusContractTest.kt` |
| `InMemoryCommandRegistryTest` | `modules/infra-defaults/src/test/kotlin/com/authord/mkdocs/defaults/command/InMemoryCommandRegistryTest.kt` |
| `NoOpAdaptersTest` | `modules/infra-defaults/src/test/kotlin/com/authord/mkdocs/defaults/ports/NoOpAdaptersTest.kt` |
| `FeatureFlagPolicyTest` | `modules/core-domain/src/test/kotlin/com/authord/mkdocs/core/flags/FeatureFlagPolicyTest.kt` |
| `FeatureFlagPolicyServiceTest` | `modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/FeatureFlagPolicyServiceTest.kt` |
| `OutOfScopeGuardrailsTest` | `modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/OutOfScopeGuardrailsTest.kt` |
| `DocumentationArtifactsPolicyTest` | `modules/core-domain/src/test/kotlin/com/authord/mkdocs/core/quality/DocumentationArtifactsPolicyTest.kt` |
| `CoverageScopePolicyTest` | `modules/core-domain/src/test/kotlin/com/authord/mkdocs/core/quality/CoverageScopePolicyTest.kt` |
| `RuntimeNeutralityPolicyTest` | `modules/core-domain/src/test/kotlin/com/authord/mkdocs/core/quality/RuntimeNeutralityPolicyTest.kt` |
| `KDocCoveragePolicyTest` | `modules/core-domain/src/test/kotlin/com/authord/mkdocs/core/quality/KDocCoveragePolicyTest.kt` |
| `ActivationToPreviewIT` | `tests/integration/runtime-lifecycle/ActivationToPreviewIT.kt` |
| `ExplorerSelectionToPreviewIT` | `tests/integration/explorer-preview-navigation/ExplorerSelectionToPreviewIT.kt` |
| `TopicTreePortContractTest` | `tests/contract/topic-tree-command-port/TopicTreePortContractTest.kt` |

## 6. Completion Gate

Cycle is complete only when all are true:
1. Unit, integration, and contract tests pass.
2. Coverage gate passes at 100% scoped unit line coverage.
3. Traceability matrix remains aligned with current requirements and tests.
4. Required design and runbook artifacts are present and current.

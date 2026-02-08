# Requirements to Tests Traceability Matrix

## Scope

Maps MVP functional requirements from `spec.md` to implemented unit, integration, and contract tests.

| Requirement ID | Requirement Summary | Test Type | Implemented Test Class(es) |
|----------------|---------------------|-----------|-----------------------------|
| FR-001 | Create plugin-managed runtime on first activation | Unit + Integration | `UvBootstrapServiceTest`, `ActivationToPreviewIT` |
| FR-002 | Install documentation tooling during first activation | Unit + Integration | `UvBootstrapServiceTest`, `ActivationToPreviewIT` |
| FR-003 | Start preview serving with runtime defaults and no hardcoded host/port | Unit + Integration | `MkdocsProcessManagerTest`, `ActivationToPreviewIT` |
| FR-004 | Detect base URL from startup output | Unit + Integration | `BaseUrlDetectorTest`, `ActivationToPreviewIT` |
| FR-005 | Open side-by-side preview after URL detection | Unit + Integration | `PreviewPaneCoordinatorTest`, `ActivationToPreviewIT` |
| FR-006 | Provide dedicated docs explorer | Unit + Integration | `DocsExplorerServiceTest`, `ExplorerSelectionToPreviewIT` |
| FR-007 | Update preview route on docs selection | Unit + Integration | `NavigationCoordinatorTest`, `ExplorerSelectionToPreviewIT` |
| FR-008 | Map `docs/index.md` to `/` | Unit | `RouteMappingServiceTest` |
| FR-009 | Map `docs/<segment>/index.md` to `/<segment>/` | Unit | `RouteMappingServiceTest` |
| FR-010 | Map `docs/<segment>.md` to `/<segment>/` | Unit | `RouteMappingServiceTest` |
| FR-011 | Apply route mapping to nested paths | Unit | `RouteMappingServiceTest` |
| FR-012 | Track scroll delta excluding comment PSI ranges | Unit | `ScrollSemanticServiceTest` |
| FR-013 | Implement TopicTreeService command API seam (`add/move/remove/rename/reparent/reorder/validate`) | Unit + Contract | `TopicTreeAggregateTest`, `TopicTreePortContractTest` |
| FR-014 | Implement PluginCommandBus + CommandRegistry seams | Unit + Contract | `InMemoryCommandRegistryTest`, `PluginCommandBusContractTest`, `TopicTreePortContractTest` |
| FR-015 | Implement VectorStorePort seam | Unit + Contract | `NoOpAdaptersTest`, `TopicTreePortContractTest` |
| FR-016 | Implement PreviewSyncPort seam | Unit + Contract | `NoOpAdaptersTest`, `TopicTreePortContractTest` |
| FR-017 | Provide feature flags for staged enablement | Unit | `FeatureFlagPolicyTest`, `OutOfScopeGuardrailsTest` |
| FR-018 | Include SDD artifacts for in-scope functionality | Unit + Documentation QA | `DocumentationArtifactsPolicyTest`; ADRs + runbook + matrix |
| FR-019 | Achieve 100% unit coverage for scoped code | CI + Unit Policy | `CoverageScopePolicyTest`; CI workflow + Jacoco verification |
| FR-020 | Exclude code-preview/preview-code sync from MVP | Unit | `OutOfScopeGuardrailsTest`, `FeatureFlagPolicyTest` |
| FR-021 | Exclude AI chatbot/command execution capability | Unit | `OutOfScopeGuardrailsTest`, `FeatureFlagPolicyTest` |
| FR-022 | Exclude vector database integration behavior | Unit + Contract | `OutOfScopeGuardrailsTest`, `NoOpAdaptersTest`, `TopicTreePortContractTest` |
| FR-023 | Exclude full WriterSide-like topic tree parity | Unit | `OutOfScopeGuardrailsTest`, `FeatureFlagPolicyTest` |

## Coverage Gate

- Unit coverage gate target: 100% for in-scope code.
- Matrix updates are required when requirements or tests change.

# Multi-Instance and Compatibility Smoke Validation (US3)

## Scope
- Requirement IDs: `R-07`, `R-07a`, `R-07b`, `R-07c`, `R-12`, `R-12a`, `R-12c`, `R-15`, `R-15a`, `R-17`, `R-17a`, `R-21`
- Task ID: `T092`
- Objective: capture evidence that multi-instance isolation and compatibility release-gate behavior are wired and testable.

## Automated Evidence
| Evidence Item | Command / Source | Result |
|---|---|---|
| Default discovery + explicit add-instance behavior | `InstanceRegistryDiscoveryTest` | PASS |
| Last selected instance persistence and restore | `InstanceSelectionPersistenceTest` | PASS |
| Active-instance scope guard blocks cross-instance mutations | `InstanceScopeIsolationTest` | PASS |
| Multi-instance reconciliation isolation and nav-first policy | `MultiInstanceReconciliationTest` | PASS |
| Compatibility severity rubric + release-blocking gate | `PreviewCompatibilityCriteriaTest` | PASS |
| Runtime decoupling host/port invariant | `RuntimeDecouplingPolicyTest` | PASS |
| Runtime base URL detection from stdout | `BaseUrlDetectorStdoutTest` | PASS |
| OS matrix path behavior and case handling | `PathCaseMatrixIntegrationTest` | PASS |
| Scale profile (5 instances / 10k docs synthetic) | `InstanceScaleProfileIntegrationTest` | PASS |

## Run Commands
```bash
./gradlew :modules:ui-plugin:test --tests "*InstanceRegistryDiscoveryTest" --tests "*InstanceSelectionPersistenceTest" --tests "*InstanceScopeIsolationTest" --tests "*MultiInstanceReconciliationTest" --tests "*PreviewCompatibilityCriteriaTest" --tests "*PathCaseMatrixIntegrationTest" --tests "*InstanceScaleProfileIntegrationTest" --tests "*MkdocsToolWindowFactoryTest" --tests "*StartMkdocsActionInvocationTest"
./gradlew :modules:mkdocs-runtime-adapter:test --tests "*RuntimeDecouplingPolicyTest" --tests "*BaseUrlDetectorStdoutTest" --tests "*MkdocsProcessManagerTest" --tests "*BaseUrlDetectorTest"
```

## Notes
- Release gate behavior blocks preview start when unresolved high-severity compatibility findings exist.
- Runtime endpoint handling stays decoupled: no hardcoded host/port injection and base URL comes from startup output parsing.

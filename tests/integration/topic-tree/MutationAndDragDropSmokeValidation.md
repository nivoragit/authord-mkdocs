# Mutation and Drag/Drop Smoke Validation (US2)

## Scope
- Requirement IDs: `R-04`, `R-05`, `R-06`, `R-08`, `R-09`, `R-11`
- Task ID: `T072`
- Objective: capture evidence that topic action and drag/drop command paths are wired and fail non-destructively.

## Automated Evidence
| Evidence Item | Command / Source | Result |
|---|---|---|
| Action controller command dispatch + path normalization | `TopicTreeActionControllerTest` | PASS |
| Drag/drop command dispatch + failure recovery guidance | `TopicTreeDragDropControllerTest` | PASS |
| Failure recovery presenter guidance mapping | `TopicTreeFailureRecoveryPresenterTest` | PASS |
| Tool window wiring of action + drag/drop controllers | `MkdocsToolWindowFactoryTest` (`create tool window content wires topic tree action and drag drop controllers`) | PASS |
| Atomic orchestration rollback compensation | `TreeSyncOrchestratorAtomicityTest` | PASS |

## Run Commands
```bash
./gradlew :modules:ui-plugin:test --tests "*TopicTreeActionControllerTest" --tests "*TopicTreeDragDropControllerTest" --tests "*TopicTreeFailureRecoveryPresenterTest" --tests "*MkdocsToolWindowFactoryTest" --tests "*TreeSyncOrchestratorAtomicityTest"
```

## Notes
- Failure paths return non-destructive guidance and preserve rollback/compensation semantics.
- Command dispatch contracts remain instance-scoped through `TopicTreeUiService`.

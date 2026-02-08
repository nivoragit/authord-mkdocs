# ADR-001: TopicTreePort Contract Boundary

- Status: Accepted
- Date: 2026-02-08

## Context

MVP requires TopicTree command API seams now, while full WriterSide-like UX parity remains out of scope.

## Decision

Define `TopicTreePort` as the exclusive mutation boundary for add/move/remove/reorder/validate operations using typed command DTOs and typed result envelopes.

## Consequences

- Positive: Domain invariants remain centralized and contract-testable.
- Positive: Future adapters can replace defaults without changing domain semantics.
- Tradeoff: More upfront DTO/result modeling work in MVP.

## Implementation Outcome

- Implemented `TopicTreePort` interface at `modules/extension-ports/src/main/kotlin/com/authord/mkdocs/ports/TopicTreePort.kt`.
- Implemented typed command DTO/result models at `modules/extension-ports/src/main/kotlin/com/authord/mkdocs/ports/topic/TopicTreeCommandDtos.kt`.
- Implemented domain-backed mutation service at `modules/core-domain/src/main/kotlin/com/authord/mkdocs/core/topic/TopicTreeAggregate.kt`.
- Verified by `TopicTreeAggregateTest` and `TopicTreePortContractTest`.

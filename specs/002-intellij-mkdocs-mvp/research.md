# Phase 0 Research: IntelliJ Plugin Shell Cycle

## Scope

Research tasks were derived from plugin-shell workstreams:
- Build/plugin setup and `runIde` availability
- Plugin descriptor registration for tool window and action
- UI action wiring to existing runtime/domain services
- Runtime integration preserving lifecycle and single-instance behavior
- Verification and DocOps quality gates

All technical-context clarifications required for planning are resolved below.

## Decisions

### 1) IntelliJ plugin build strategy

- Decision: Use IntelliJ Platform Gradle plugin configuration in the root build so plugin metadata, IDE target, and sandbox run tasks (including `runIde`) are explicit and repeatable.
- Rationale: This provides a standard plugin-shell development workflow and aligns local and CI behavior.
- Alternatives considered:
  - Keep only generic Kotlin/JVM build tasks and skip plugin-specific configuration.
  - Rejected because plugin shell cannot be launched reliably without explicit plugin tooling/task configuration.

### 2) IDE target and compatibility baseline

- Decision: Keep the IDE target baseline aligned with the current project’s Kotlin/JVM and plugin metadata constraints, and document it in runbook/quickstart.
- Rationale: The cycle goal is shell operability, so compatibility must be explicit and testable.
- Alternatives considered:
  - Leave IDE target implicit.
  - Rejected because incompatible target drift can cause plugin load failures in `runIde`.

### 3) Plugin descriptor registration model

- Decision: Register three descriptor concerns in `META-INF/plugin.xml`: platform dependency, MkDocs tool window, and Start MkDocs Preview action.
- Rationale: This is the minimal shell contract needed for visible UI entry points without introducing new feature scope.
- Alternatives considered:
  - Programmatic registration only.
  - Rejected because declarative descriptor registration is the canonical plugin-shell entry model and improves load-time transparency.

### 4) Tool window shell design

- Decision: Implement a minimal `ToolWindowFactory` that creates a lightweight MkDocs panel with shell status content only.
- Rationale: Satisfies cycle objective (visible plugin shell) while keeping feature scope bounded.
- Alternatives considered:
  - Implement full preview UI parity now.
  - Rejected as out-of-scope for this cycle.

### 5) Action wiring contract

- Decision: Implement `StartMkdocsAction` with strict `update` and `actionPerformed` responsibilities and delegate business behavior to existing services.
- Rationale: Preserves separation of concerns and avoids duplicate runtime logic.
- Alternatives considered:
  - Embed runtime orchestration logic directly in action class.
  - Rejected because it couples IDE action plumbing to runtime domain behavior and increases regression risk.

### 6) Runtime integration adapter pattern

- Decision: Introduce/extend a project-scoped adapter/facade in `ui-plugin` that bridges action/tool window to existing activation/runtime services.
- Rationale: Provides plugin-service access that preserves current lifecycle behavior and single-instance guarantees.
- Alternatives considered:
  - Reimplement lifecycle logic in plugin shell classes.
  - Rejected because it risks violating single-instance and runtime-decoupling guarantees.

### 7) Lifecycle/single-instance guard preservation

- Decision: Treat existing process manager semantics as source of truth and validate them through adapter-focused unit tests.
- Rationale: The cycle explicitly requires runtime handoff reuse with no feature rewrite.
- Alternatives considered:
  - Add second layer of process tracking in action class.
  - Rejected because duplicated state risks divergence and duplicate runtime instances.

### 8) Verification strategy

- Decision: Add unit tests for action visibility/enabled state, action->service delegation, tool window factory content creation, and lifecycle guard adapter behavior; add smoke verification steps for `runIde`, plugin load, tool window presence, and action invocability.
- Rationale: Covers both deterministic logic and minimum real-shell operability.
- Alternatives considered:
  - Rely on manual IDE checks only.
  - Rejected because it weakens repeatability and traceability.

### 9) DocOps package for this cycle

- Decision: Deliver and maintain: feature spec, technical design notes, operational runbook, test plan + requirements traceability matrix, changelog, and migration notes (if compatibility impact is introduced).
- Rationale: Required by constitution and cycle completion gates.
- Alternatives considered:
  - Partial docs update.
  - Rejected because cycle completeness would fail constitution gates.

## Resolved Clarifications

- No unresolved `NEEDS CLARIFICATION` items remain for Phase 1 design.

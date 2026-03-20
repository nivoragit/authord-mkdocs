# Kotlin Codebase File Reference

Generated on 2026-03-20 from `206` Kotlin source files.

Each section documents one `.kt` file with its role, dependencies, responsibilities, and key implemented behavior.

## FeatureFlagPolicy.kt

**File Path**
`modules/core-domain/src/main/kotlin/com/authord/mkdocs/core/flags/FeatureFlagPolicy.kt`

**What it does**
Implements `FeatureFlagPolicy` as a policy/classification component in the `core-domain` module.

**Why it matters**
It codifies decision rules that shape runtime behavior and error handling.

**Dependencies**
- Internal: core-domain local package types
- External: Kotlin/Java standard library only.

**Responsibilities**
- Provide feature-specific implementation logic for this module area.
- Integrate with neighboring services/components to deliver end behavior.
- Preserve deterministic domain rules independent from platform adapters.

**Key Functionalities**
- Primary declarations: `FeatureFlagPolicy`.
- Implements key methods/functions including `allowsMvpFlow`, `disallowsFutureCycleFeatures`.
- Applies domain-centric rules for topic trees, routing, lifecycle, or flags.

## RouteMappingService.kt

**File Path**
`modules/core-domain/src/main/kotlin/com/authord/mkdocs/core/navigation/RouteMappingService.kt`

**What it does**
Implements `RouteMappingService` as a service in the `core-domain` module.

**Why it matters**
It concentrates reusable business logic so multiple callers can share consistent behavior.

**Dependencies**
- Internal: core-domain local package types
- External: Kotlin/Java standard library only.

**Responsibilities**
- Coordinate workflow steps and state transitions for its feature area.
- Encapsulate reusable logic so UI/actions can call a single abstraction.
- Preserve deterministic domain rules independent from platform adapters.

**Key Functionalities**
- Primary declarations: `RouteMappingService`.
- Implements key methods/functions including `mapToRoute`.
- Applies domain-centric rules for topic trees, routing, lifecycle, or flags.

## RuntimeLifecycleState.kt

**File Path**
`modules/core-domain/src/main/kotlin/com/authord/mkdocs/core/runtime/RuntimeLifecycleState.kt`

**What it does**
Implements `RuntimeLifecycleState` as a domain model definition in the `core-domain` module.

**Why it matters**
It defines shared data semantics that must remain stable across module boundaries.

**Dependencies**
- Internal: core-domain local package types
- External: Kotlin/Java standard library only.

**Responsibilities**
- Define typed data structures exchanged across services and modules.
- Keep semantic meaning explicit for serialization, validation, or routing.
- Preserve deterministic domain rules independent from platform adapters.

**Key Functionalities**
- Primary declarations: `RuntimeLifecycleState`.
- Focuses on type/contract definitions more than executable method logic.
- Applies domain-centric rules for topic trees, routing, lifecycle, or flags.

## ScrollSemanticService.kt

**File Path**
`modules/core-domain/src/main/kotlin/com/authord/mkdocs/core/scroll/ScrollSemanticService.kt`

**What it does**
Implements `ScrollSemanticService` as a service in the `core-domain` module.

**Why it matters**
It concentrates reusable business logic so multiple callers can share consistent behavior.

**Dependencies**
- Internal: core-domain local package types
- External: Kotlin/Java standard library only.

**Responsibilities**
- Coordinate workflow steps and state transitions for its feature area.
- Encapsulate reusable logic so UI/actions can call a single abstraction.
- Preserve deterministic domain rules independent from platform adapters.

**Key Functionalities**
- Primary declarations: `CommentRange`, `ScrollSemanticService`.
- Implements key methods/functions including `calculateSemanticDelta`.
- Applies domain-centric rules for topic trees, routing, lifecycle, or flags.

## PathPolicy.kt

**File Path**
`modules/core-domain/src/main/kotlin/com/authord/mkdocs/core/topic/PathPolicy.kt`

**What it does**
Implements `PathPolicy` as a policy/classification component in the `core-domain` module.

**Why it matters**
It codifies decision rules that shape runtime behavior and error handling.

**Dependencies**
- Internal: core-domain local package types
- External: java.util.Locale

**Responsibilities**
- Provide feature-specific implementation logic for this module area.
- Integrate with neighboring services/components to deliver end behavior.
- Preserve deterministic domain rules independent from platform adapters.

**Key Functionalities**
- Primary declarations: `PathPolicy`.
- Implements key methods/functions including `normalize`, `comparisonKey`, `equivalent`, `forCurrentOs`, `forOsName`.
- Applies domain-centric rules for topic trees, routing, lifecycle, or flags.

## TopicDomainModel.kt

**File Path**
`modules/core-domain/src/main/kotlin/com/authord/mkdocs/core/topic/TopicDomainModel.kt`

**What it does**
Implements `TopicDomainModel` as a domain model definition in the `core-domain` module.

**Why it matters**
It defines shared data semantics that must remain stable across module boundaries.

**Dependencies**
- Internal: core-domain local package types
- External: Kotlin/Java standard library only.

**Responsibilities**
- Define typed data structures exchanged across services and modules.
- Keep semantic meaning explicit for serialization, validation, or routing.
- Preserve deterministic domain rules independent from platform adapters.

**Key Functionalities**
- Primary declarations: `TopicLink`, `TopicMetadata`.
- Focuses on type/contract definitions more than executable method logic.
- Applies domain-centric rules for topic trees, routing, lifecycle, or flags.

## TopicTreeAggregate.kt

**File Path**
`modules/core-domain/src/main/kotlin/com/authord/mkdocs/core/topic/TopicTreeAggregate.kt`

**What it does**
Implements `TopicTreeAggregate` as a component in the `core-domain` module.

**Why it matters**
This file encodes domain behavior that other modules consume, so correctness here directly affects plugin state and business rules.

**Dependencies**
- Internal: extension-ports module APIs, com.authord.mkdocs.ports.TopicTreePort, com.authord.mkdocs.ports.topic.AddChildTopicNodeCommand, com.authord.mkdocs.ports.topic.AddExistingFileTopicNodeCommand, com.authord.mkdocs.ports.topic.AddExternalLinkTopicNodeCommand
- External: Kotlin/Java standard library only.

**Responsibilities**
- Provide feature-specific implementation logic for this module area.
- Integrate with neighboring services/components to deliver end behavior.
- Preserve deterministic domain rules independent from platform adapters.

**Key Functionalities**
- Primary declarations: `TopicNodeStatus`, `TopicNodeKind`, `TopicNode`, `TopicTreeAggregate`, plus `1` more.
- Implements key methods/functions including `snapshot`, `bootstrapFromNav`, `visit`, `apply`.
- Applies domain-centric rules for topic trees, routing, lifecycle, or flags.

## TopicTreeValidationService.kt

**File Path**
`modules/core-domain/src/main/kotlin/com/authord/mkdocs/core/topic/TopicTreeValidationService.kt`

**What it does**
Implements `TopicTreeValidationService` as a service in the `core-domain` module.

**Why it matters**
It concentrates reusable business logic so multiple callers can share consistent behavior.

**Dependencies**
- Internal: extension-ports module APIs, com.authord.mkdocs.ports.topic.TopicNavNode
- External: java.net.URI

**Responsibilities**
- Coordinate workflow steps and state transitions for its feature area.
- Encapsulate reusable logic so UI/actions can call a single abstraction.
- Preserve deterministic domain rules independent from platform adapters.

**Key Functionalities**
- Primary declarations: `TopicTreeValidationIssueType`, `TopicTreeValidationIssue`, `TopicTreeValidationService`.
- Implements key methods/functions including `validate`, `visit`.
- Applies domain-centric rules for topic trees, routing, lifecycle, or flags.

## FeatureFlagPolicyTest.kt

**File Path**
`modules/core-domain/src/test/kotlin/com/authord/mkdocs/core/flags/FeatureFlagPolicyTest.kt`

**What it does**
Defines a unit test suite for `FeatureFlag` by exercising expected behavior and edge cases.

**Why it matters**
It provides fast regression coverage for local logic so changes can be made safely.

**Dependencies**
- Internal: core-domain local package types
- External: Kotlin Test

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Detect regressions in business logic, edge cases, and guard conditions.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `default policy allows MVP and disables future-cycle features`.
- Test case: `future-cycle flags invalidate policy`.

## RouteMappingServiceTest.kt

**File Path**
`modules/core-domain/src/test/kotlin/com/authord/mkdocs/core/navigation/RouteMappingServiceTest.kt`

**What it does**
Defines a unit test suite for `RouteMapping` by exercising expected behavior and edge cases.

**Why it matters**
It provides fast regression coverage for local logic so changes can be made safely.

**Dependencies**
- Internal: core-domain local package types
- External: Kotlin Test

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Detect regressions in business logic, edge cases, and guard conditions.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `maps docs index to root`.
- Test case: `maps segment index to trailing slash route`.
- Test case: `maps segment markdown file to trailing slash route`.
- Test case: `maps uppercase markdown extension to route`.
- Test case: `maps markdown extension variant to route`.
- Additional `8` test cases expand scenario coverage.

## CoverageScopePolicyTest.kt

**File Path**
`modules/core-domain/src/test/kotlin/com/authord/mkdocs/core/quality/CoverageScopePolicyTest.kt`

**What it does**
Defines a unit test suite for `CoverageScope` by exercising expected behavior and edge cases.

**Why it matters**
It provides fast regression coverage for local logic so changes can be made safely.

**Dependencies**
- Internal: core-domain local package types
- External: Kotlin Test, Java NIO, java.nio.file.Files, java.nio.file.Path

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Detect regressions in business logic, edge cases, and guard conditions.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `coverage verification requires 100 percent line coverage`.

## DocumentationArtifactsPolicyTest.kt

**File Path**
`modules/core-domain/src/test/kotlin/com/authord/mkdocs/core/quality/DocumentationArtifactsPolicyTest.kt`

**What it does**
Defines a unit test suite for `DocumentationArtifacts` by exercising expected behavior and edge cases.

**Why it matters**
It provides fast regression coverage for local logic so changes can be made safely.

**Dependencies**
- Internal: core-domain local package types
- External: Kotlin Test, Java NIO, java.nio.file.Files, java.nio.file.Path

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Detect regressions in business logic, edge cases, and guard conditions.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `required SDD artifacts exist for this feature`.
- Test case: `documentation includes function usage guidance and traceability matrix`.

## KDocCoveragePolicyTest.kt

**File Path**
`modules/core-domain/src/test/kotlin/com/authord/mkdocs/core/quality/KDocCoveragePolicyTest.kt`

**What it does**
Defines a unit test suite for `KDocCoverage` by exercising expected behavior and edge cases.

**Why it matters**
It provides fast regression coverage for local logic so changes can be made safely.

**Dependencies**
- Internal: core-domain local package types
- External: Kotlin Test, Java NIO, java.nio.file.Files, java.nio.file.Path

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Detect regressions in business logic, edge cases, and guard conditions.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `phase2 public api surface declarations include KDoc`.

## PluginBuildPolicyTest.kt

**File Path**
`modules/core-domain/src/test/kotlin/com/authord/mkdocs/core/quality/PluginBuildPolicyTest.kt`

**What it does**
Defines a unit test suite for `PluginBuild` by exercising expected behavior and edge cases.

**Why it matters**
It provides fast regression coverage for local logic so changes can be made safely.

**Dependencies**
- Internal: core-domain local package types
- External: Kotlin Test, Java NIO, java.nio.file.Files, java.nio.file.Path

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Detect regressions in business logic, edge cases, and guard conditions.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `build configuration enables intellij platform plugin and runIde readiness`.

## RequirementCoveragePolicyTest.kt

**File Path**
`modules/core-domain/src/test/kotlin/com/authord/mkdocs/core/quality/RequirementCoveragePolicyTest.kt`

**What it does**
Defines a unit test suite for `RequirementCoverage` by exercising expected behavior and edge cases.

**Why it matters**
It provides fast regression coverage for local logic so changes can be made safely.

**Dependencies**
- Internal: core-domain local package types
- External: Kotlin Test, Java NIO, java.nio.file.Files, java.nio.file.Path

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Detect regressions in business logic, edge cases, and guard conditions.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `phase2 traceability matrix lists all required requirement families`.
- Test case: `quality gate references jacoco coverage verification`.

## RuntimeNeutralityPolicyTest.kt

**File Path**
`modules/core-domain/src/test/kotlin/com/authord/mkdocs/core/quality/RuntimeNeutralityPolicyTest.kt`

**What it does**
Defines a unit test suite for `RuntimeNeutrality` by exercising expected behavior and edge cases.

**Why it matters**
It provides fast regression coverage for local logic so changes can be made safely.

**Dependencies**
- Internal: core-domain local package types
- External: Kotlin Test, Java NIO, java.nio.file.Files, java.nio.file.Path

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Detect regressions in business logic, edge cases, and guard conditions.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `main sources avoid shell activation and hardcoded python paths`.
- Test case: `main sources avoid hardcoded runtime host and port defaults`.

## ScopedCoveragePolicyTest.kt

**File Path**
`modules/core-domain/src/test/kotlin/com/authord/mkdocs/core/quality/ScopedCoveragePolicyTest.kt`

**What it does**
Defines a unit test suite for `ScopedCoverage` by exercising expected behavior and edge cases.

**Why it matters**
It provides fast regression coverage for local logic so changes can be made safely.

**Dependencies**
- Internal: core-domain local package types
- External: Kotlin Test, Java NIO, java.nio.file.Files, java.nio.file.Path

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Detect regressions in business logic, edge cases, and guard conditions.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `root build defines scoped coverage gate over required modules`.
- Test case: `module build files enforce 100 percent coverage minimum`.
- Test case: `ci workflows run fail on threshold coverage gates`.

## RuntimeLifecycleStateTest.kt

**File Path**
`modules/core-domain/src/test/kotlin/com/authord/mkdocs/core/runtime/RuntimeLifecycleStateTest.kt`

**What it does**
Defines a unit test suite for `RuntimeLifecycle` by exercising expected behavior and edge cases.

**Why it matters**
It provides fast regression coverage for local logic so changes can be made safely.

**Dependencies**
- Internal: core-domain local package types
- External: Kotlin Test

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Detect regressions in business logic, edge cases, and guard conditions.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `runtime lifecycle enum exposes all expected states`.

## ScrollSemanticServiceTest.kt

**File Path**
`modules/core-domain/src/test/kotlin/com/authord/mkdocs/core/scroll/ScrollSemanticServiceTest.kt`

**What it does**
Defines a unit test suite for `ScrollSemantic` by exercising expected behavior and edge cases.

**Why it matters**
It provides fast regression coverage for local logic so changes can be made safely.

**Dependencies**
- Internal: core-domain local package types
- External: Kotlin Test

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Detect regressions in business logic, edge cases, and guard conditions.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `returns zero when visible region is fully comments`.
- Test case: `returns reduced delta when visible region partly overlaps comments`.
- Test case: `returns raw delta when no comment overlap exists`.
- Test case: `returns zero when raw delta is zero`.
- Test case: `applies exclusion ratio for negative deltas`.

## AddChildTransformPolicyTest.kt

**File Path**
`modules/core-domain/src/test/kotlin/com/authord/mkdocs/core/topic/AddChildTransformPolicyTest.kt`

**What it does**
Defines a unit test suite for `AddChildTransform` by exercising expected behavior and edge cases.

**Why it matters**
It provides fast regression coverage for local logic so changes can be made safely.

**Dependencies**
- Internal: extension-ports module APIs, com.authord.mkdocs.ports.topic.AddChildTopicNodeCommand, com.authord.mkdocs.ports.topic.AddExternalLinkTopicNodeCommand, com.authord.mkdocs.ports.topic.AddTopicNodeCommand, com.authord.mkdocs.ports.topic.RemoveTopicNodeCommand
- External: Kotlin Test

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Detect regressions in business logic, edge cases, and guard conditions.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `add-child on page auto-converts parent into section and preserves original page as first child`.
- Test case: `add-child rejects external link target and missing target nodes`.
- Test case: `add-child rejects removed target duplicate child ids and invalid child input`.
- Test case: `add-child section branch preserves deterministic sibling ordering by order then node id`.
- Test case: `add-child page transform reindexes pre-existing children and handles preserved id collision`.

## AddExistingFileCommandTest.kt

**File Path**
`modules/core-domain/src/test/kotlin/com/authord/mkdocs/core/topic/AddExistingFileCommandTest.kt`

**What it does**
Defines a unit test suite for `AddExistingFileCommand` by exercising expected behavior and edge cases.

**Why it matters**
It provides fast regression coverage for local logic so changes can be made safely.

**Dependencies**
- Internal: extension-ports module APIs, com.authord.mkdocs.ports.topic.AddExistingFileTopicNodeCommand, com.authord.mkdocs.ports.topic.TopicTreeCommandStatus
- External: Kotlin Test

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Detect regressions in business logic, edge cases, and guard conditions.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `add-existing-file inserts markdown-backed page node under selected parent`.
- Test case: `add-existing-file rejects missing parent, invalid paths, and duplicate node ids`.

## ExternalLinkCommandTest.kt

**File Path**
`modules/core-domain/src/test/kotlin/com/authord/mkdocs/core/topic/ExternalLinkCommandTest.kt`

**What it does**
Defines a unit test suite for `ExternalLinkCommand` by exercising expected behavior and edge cases.

**Why it matters**
It provides fast regression coverage for local logic so changes can be made safely.

**Dependencies**
- Internal: extension-ports module APIs, com.authord.mkdocs.ports.topic.AddExternalLinkTopicNodeCommand, com.authord.mkdocs.ports.topic.TopicTreeCommandStatus
- External: Kotlin Test

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Detect regressions in business logic, edge cases, and guard conditions.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `add-external-link inserts external-link node with url`.
- Test case: `add-external-link rejects malformed urls and missing parent`.

## PathNormalizationPolicyTest.kt

**File Path**
`modules/core-domain/src/test/kotlin/com/authord/mkdocs/core/topic/PathNormalizationPolicyTest.kt`

**What it does**
Defines a unit test suite for `PathNormalization` by exercising expected behavior and edge cases.

**Why it matters**
It provides fast regression coverage for local logic so changes can be made safely.

**Dependencies**
- Internal: core-domain local package types
- External: Kotlin Test

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Detect regressions in business logic, edge cases, and guard conditions.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `normalize handles empty and whitespace-only input`.
- Test case: `normalize handles windows drive and dot segments`.
- Test case: `normalize handles leading slash and relative parent traversal`.
- Test case: `normalize treats non-letter drive prefix as regular path`.
- Test case: `comparison key follows case sensitivity policy`.
- Additional `3` test cases expand scenario coverage.

## TopicDomainModelTest.kt

**File Path**
`modules/core-domain/src/test/kotlin/com/authord/mkdocs/core/topic/TopicDomainModelTest.kt`

**What it does**
Defines a unit test suite for `TopicDomainModel` by exercising expected behavior and edge cases.

**Why it matters**
It provides fast regression coverage for local logic so changes can be made safely.

**Dependencies**
- Internal: core-domain local package types
- External: Kotlin Test

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Detect regressions in business logic, edge cases, and guard conditions.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `topic id requires non-blank values`.
- Test case: `topic order requires non-negative values`.
- Test case: `topic value objects expose boxed getters`.
- Test case: `topic link and metadata expose fields`.
- Test case: `topic metadata defaults to empty attributes`.
- Additional `1` test cases expand scenario coverage.

## TopicTreeAggregateMutationTest.kt

**File Path**
`modules/core-domain/src/test/kotlin/com/authord/mkdocs/core/topic/TopicTreeAggregateMutationTest.kt`

**What it does**
Defines a unit test suite for `TopicTreeAggregate` by exercising expected behavior and edge cases.

**Why it matters**
It provides fast regression coverage for local logic so changes can be made safely.

**Dependencies**
- Internal: extension-ports module APIs, com.authord.mkdocs.ports.topic.AddTopicNodeCommand, com.authord.mkdocs.ports.topic.MoveTopicNodeCommand, com.authord.mkdocs.ports.topic.RemoveTopicNodeCommand, com.authord.mkdocs.ports.topic.RenameTopicNodeCommand
- External: Kotlin Test

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Detect regressions in business logic, edge cases, and guard conditions.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `add enforces unique node ids and valid parent`.
- Test case: `move enforces cycle safety and non-negative ordering`.
- Test case: `remove marks node as removed and rejects root removal`.
- Test case: `rename requires existing node and non-blank title`.
- Test case: `reparent rejects cycles and missing parents`.
- Additional `1` test cases expand scenario coverage.

## TopicTreeAggregateTest.kt

**File Path**
`modules/core-domain/src/test/kotlin/com/authord/mkdocs/core/topic/TopicTreeAggregateTest.kt`

**What it does**
Defines a unit test suite for `TopicTreeAggregate` by exercising expected behavior and edge cases.

**Why it matters**
It provides fast regression coverage for local logic so changes can be made safely.

**Dependencies**
- Internal: extension-ports module APIs, com.authord.mkdocs.ports.topic.AddTopicNodeCommand, com.authord.mkdocs.ports.topic.MoveTopicNodeCommand, com.authord.mkdocs.ports.topic.RemoveTopicNodeCommand, com.authord.mkdocs.ports.topic.RenameTopicNodeCommand
- External: Kotlin Test

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Detect regressions in business logic, edge cases, and guard conditions.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `add node mutation succeeds and exposes snapshot fields`.
- Test case: `add node rejects missing parent`.
- Test case: `add node rejects duplicate id`.
- Test case: `add node rejects invalid title or order`.
- Test case: `root node removal is rejected`.
- Additional `25` test cases expand scenario coverage.

## TopicTreeValidationServiceTest.kt

**File Path**
`modules/core-domain/src/test/kotlin/com/authord/mkdocs/core/topic/TopicTreeValidationServiceTest.kt`

**What it does**
Defines a unit test suite for `TopicTreeValidation` by exercising expected behavior and edge cases.

**Why it matters**
It provides fast regression coverage for local logic so changes can be made safely.

**Dependencies**
- Internal: extension-ports module APIs, com.authord.mkdocs.ports.topic.TopicNavNode
- External: Kotlin Test

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Detect regressions in business logic, edge cases, and guard conditions.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `detects broken path references in nav`.
- Test case: `detects duplicate nav references`.
- Test case: `detects malformed external links`.
- Test case: `validate traverses nested children and reports missing child paths`.
- Test case: `validate deduplicates duplicates under case-insensitive policy`.
- Additional `1` test cases expand scenario coverage.

## TopicTreePort.kt

**File Path**
`modules/extension-ports/src/main/kotlin/com/authord/mkdocs/ports/TopicTreePort.kt`

**What it does**
Implements `TopicTreePort` as a port contract in the `extension-ports` module.

**Why it matters**
It establishes a clean boundary between core logic and concrete implementations, enabling modularity and testability.

**Dependencies**
- Internal: extension-ports module APIs, com.authord.mkdocs.ports.topic.TopicTreeCommand, com.authord.mkdocs.ports.topic.TopicTreeCommandResult
- External: Kotlin/Java standard library only.

**Responsibilities**
- Define a stable contract for collaborators across module boundaries.
- Document capability shape, inputs, and outputs expected by callers.

**Key Functionalities**
- Primary declarations: `TopicTreeApiVersionRegistry`, `TopicTreePort`.
- Implements key methods/functions including `execute`.

## DefaultPluginCommandBus.kt

**File Path**
`modules/extension-ports/src/main/kotlin/com/authord/mkdocs/ports/command/DefaultPluginCommandBus.kt`

**What it does**
Implements `DefaultPluginCommandBus` as a component in the `extension-ports` module.

**Why it matters**
This file defines cross-module contracts that keep the architecture decoupled and testable.

**Dependencies**
- Internal: extension-ports module APIs, com.authord.mkdocs.ports.topic.TopicTreeCommand, com.authord.mkdocs.ports.topic.TopicTreeCommandResult, com.authord.mkdocs.ports.topic.TopicTreeCommandStatus, com.authord.mkdocs.ports.topic.TopicTreeViolation
- External: Kotlin/Java standard library only.

**Responsibilities**
- Provide feature-specific implementation logic for this module area.
- Integrate with neighboring services/components to deliver end behavior.

**Key Functionalities**
- Primary declarations: `DefaultPluginCommandBus`.
- Focuses on type/contract definitions more than executable method logic.

## PluginCommandBus.kt

**File Path**
`modules/extension-ports/src/main/kotlin/com/authord/mkdocs/ports/command/PluginCommandBus.kt`

**What it does**
Implements `PluginCommandBus` as a component in the `extension-ports` module.

**Why it matters**
This file defines cross-module contracts that keep the architecture decoupled and testable.

**Dependencies**
- Internal: extension-ports module APIs, com.authord.mkdocs.ports.topic.TopicTreeCommand, com.authord.mkdocs.ports.topic.TopicTreeCommandResult
- External: Kotlin/Java standard library only.

**Responsibilities**
- Provide feature-specific implementation logic for this module area.
- Integrate with neighboring services/components to deliver end behavior.

**Key Functionalities**
- Primary declarations: `CommandRegistry`, `PluginCommandBus`.
- Implements key methods/functions including `handle`, `register`, `resolve`, `dispatch`.

## PreviewSyncPort.kt

**File Path**
`modules/extension-ports/src/main/kotlin/com/authord/mkdocs/ports/preview/PreviewSyncPort.kt`

**What it does**
Implements `PreviewSyncPort` as a port contract in the `extension-ports` module.

**Why it matters**
It establishes a clean boundary between core logic and concrete implementations, enabling modularity and testability.

**Dependencies**
- Internal: extension-ports local package types
- External: Kotlin/Java standard library only.

**Responsibilities**
- Define a stable contract for collaborators across module boundaries.
- Document capability shape, inputs, and outputs expected by callers.

**Key Functionalities**
- Primary declarations: `PreviewSyncPort`.
- Implements key methods/functions including `onEditorScrollSemanticDelta`.

## DocsFileGateway.kt

**File Path**
`modules/extension-ports/src/main/kotlin/com/authord/mkdocs/ports/topic/DocsFileGateway.kt`

**What it does**
Implements `DocsFileGateway` as a adapter layer component in the `extension-ports` module.

**Why it matters**
It connects abstract project workflows to concrete runtime or filesystem operations required in production.

**Dependencies**
- Internal: extension-ports local package types
- External: Kotlin/Java standard library only.

**Responsibilities**
- Translate domain-level requests into concrete runtime/filesystem operations.
- Normalize external behavior into plugin-friendly results and errors.

**Key Functionalities**
- Primary declarations: `DocsFileGateway`.
- Implements key methods/functions including `createMarkdownFile`, `deleteMarkdownFile`, `renameMarkdownFile`, `moveMarkdownFile`, `rewriteRelativeMarkdownLinks`, `upsertMarkdownTitleHeading`.

## InstanceRegistryPort.kt

**File Path**
`modules/extension-ports/src/main/kotlin/com/authord/mkdocs/ports/topic/InstanceRegistryPort.kt`

**What it does**
Implements `InstanceRegistryPort` as a port contract in the `extension-ports` module.

**Why it matters**
It establishes a clean boundary between core logic and concrete implementations, enabling modularity and testability.

**Dependencies**
- Internal: extension-ports local package types
- External: Kotlin/Java standard library only.

**Responsibilities**
- Define a stable contract for collaborators across module boundaries.
- Document capability shape, inputs, and outputs expected by callers.

**Key Functionalities**
- Primary declarations: `InstanceRegistryPort`.
- Implements key methods/functions including `discoverDefaultInstance`, `registerInstance`, `listInstances`, `selectActiveInstance`, `activeInstance`.

## MkDocsConfigGateway.kt

**File Path**
`modules/extension-ports/src/main/kotlin/com/authord/mkdocs/ports/topic/MkDocsConfigGateway.kt`

**What it does**
Implements `MkDocsConfigGateway` as a adapter layer component in the `extension-ports` module.

**Why it matters**
It connects abstract project workflows to concrete runtime or filesystem operations required in production.

**Dependencies**
- Internal: extension-ports local package types
- External: Kotlin/Java standard library only.

**Responsibilities**
- Translate domain-level requests into concrete runtime/filesystem operations.
- Normalize external behavior into plugin-friendly results and errors.

**Key Functionalities**
- Primary declarations: `MkDocsConfigGateway`.
- Implements key methods/functions including `loadConfig`, `writeConfig`, `serializeDeterministically`.

## TopicSyncError.kt

**File Path**
`modules/extension-ports/src/main/kotlin/com/authord/mkdocs/ports/topic/TopicSyncError.kt`

**What it does**
Implements `TopicSyncError` as a domain model definition in the `extension-ports` module.

**Why it matters**
It defines shared data semantics that must remain stable across module boundaries.

**Dependencies**
- Internal: extension-ports local package types
- External: Kotlin/Java standard library only.

**Responsibilities**
- Define typed data structures exchanged across services and modules.
- Keep semantic meaning explicit for serialization, validation, or routing.

**Key Functionalities**
- Primary declarations: `TopicSyncErrorCode`, `TopicSyncError`, `DefaultTopicSyncError`, `TopicGatewayResult`, plus `2` more.
- Focuses on type/contract definitions more than executable method logic.

## TopicSyncEvent.kt

**File Path**
`modules/extension-ports/src/main/kotlin/com/authord/mkdocs/ports/topic/TopicSyncEvent.kt`

**What it does**
Implements `TopicSyncEvent` as a domain model definition in the `extension-ports` module.

**Why it matters**
It defines shared data semantics that must remain stable across module boundaries.

**Dependencies**
- Internal: extension-ports local package types
- External: Kotlin/Java standard library only.

**Responsibilities**
- Define typed data structures exchanged across services and modules.
- Keep semantic meaning explicit for serialization, validation, or routing.

**Key Functionalities**
- Primary declarations: `TopicSyncEventType`, `TopicSyncEvent`.
- Focuses on type/contract definitions more than executable method logic.

## TopicSyncModels.kt

**File Path**
`modules/extension-ports/src/main/kotlin/com/authord/mkdocs/ports/topic/TopicSyncModels.kt`

**What it does**
Implements `TopicSyncModels` as a domain model definition in the `extension-ports` module.

**Why it matters**
It defines shared data semantics that must remain stable across module boundaries.

**Dependencies**
- Internal: extension-ports local package types
- External: Kotlin/Java standard library only.

**Responsibilities**
- Define typed data structures exchanged across services and modules.
- Keep semantic meaning explicit for serialization, validation, or routing.

**Key Functionalities**
- Primary declarations: `TopicInstanceRef`, `TopicNavNode`, `MkDocsConfigDocument`, `TopicFileOperationKind`, plus `4` more.
- Focuses on type/contract definitions more than executable method logic.

## TopicTreeAggregateBootstrapPort.kt

**File Path**
`modules/extension-ports/src/main/kotlin/com/authord/mkdocs/ports/topic/TopicTreeAggregateBootstrapPort.kt`

**What it does**
Implements `TopicTreeAggregateBootstrapPort` as a port contract in the `extension-ports` module.

**Why it matters**
It establishes a clean boundary between core logic and concrete implementations, enabling modularity and testability.

**Dependencies**
- Internal: extension-ports local package types
- External: Kotlin/Java standard library only.

**Responsibilities**
- Define a stable contract for collaborators across module boundaries.
- Document capability shape, inputs, and outputs expected by callers.

**Key Functionalities**
- Primary declarations: `TopicTreeAggregateBootstrapPort`, `TopicTreeAggregateRefreshPort`.
- Implements key methods/functions including `bootstrapTreeFromNav`, `refreshTreeFromNav`.

## TopicTreeCommandDtos.kt

**File Path**
`modules/extension-ports/src/main/kotlin/com/authord/mkdocs/ports/topic/TopicTreeCommandDtos.kt`

**What it does**
Implements `TopicTreeCommandDtos` as a domain model definition in the `extension-ports` module.

**Why it matters**
It defines shared data semantics that must remain stable across module boundaries.

**Dependencies**
- Internal: extension-ports local package types
- External: Kotlin/Java standard library only.

**Responsibilities**
- Define typed data structures exchanged across services and modules.
- Keep semantic meaning explicit for serialization, validation, or routing.

**Key Functionalities**
- Primary declarations: `TopicTreeCommandType`, `TopicTreeCommand`, `AddTopicNodeCommand`, `AddChildTopicNodeCommand`, plus `11` more.
- Focuses on type/contract definitions more than executable method logic.

## TreeSyncOrchestrator.kt

**File Path**
`modules/extension-ports/src/main/kotlin/com/authord/mkdocs/ports/topic/TreeSyncOrchestrator.kt`

**What it does**
Implements `TreeSyncOrchestrator` as a workflow coordinator in the `extension-ports` module.

**Why it matters**
It centralizes cross-service sequencing so lifecycle transitions remain predictable and recoverable.

**Dependencies**
- Internal: extension-ports local package types
- External: Kotlin/Java standard library only.

**Responsibilities**
- Coordinate workflow steps and state transitions for its feature area.
- Encapsulate reusable logic so UI/actions can call a single abstraction.

**Key Functionalities**
- Primary declarations: `TreeSyncOrchestrator`.
- Implements key methods/functions including `apply`, `rollback`, `compensate`.

## VectorStorePort.kt

**File Path**
`modules/extension-ports/src/main/kotlin/com/authord/mkdocs/ports/vector/VectorStorePort.kt`

**What it does**
Implements `VectorStorePort` as a port contract in the `extension-ports` module.

**Why it matters**
It establishes a clean boundary between core logic and concrete implementations, enabling modularity and testability.

**Dependencies**
- Internal: extension-ports local package types
- External: Kotlin/Java standard library only.

**Responsibilities**
- Define a stable contract for collaborators across module boundaries.
- Document capability shape, inputs, and outputs expected by callers.

**Key Functionalities**
- Primary declarations: `VectorStorePort`.
- Implements key methods/functions including `upsert`, `search`.

## ExtensionPortsCoverageRemediationTest.kt

**File Path**
`modules/extension-ports/src/test/kotlin/com/authord/mkdocs/ports/ExtensionPortsCoverageRemediationTest.kt`

**What it does**
Defines a unit test suite for `ExtensionPorts` by exercising expected behavior and edge cases.

**Why it matters**
It provides fast regression coverage for local logic so changes can be made safely.

**Dependencies**
- Internal: extension-ports module APIs, com.authord.mkdocs.ports.topic.AddChildTopicNodeCommand, com.authord.mkdocs.ports.topic.AddExistingFileTopicNodeCommand, com.authord.mkdocs.ports.topic.AddExternalLinkTopicNodeCommand, com.authord.mkdocs.ports.topic.AddTopicNodeCommand
- External: Kotlin Test

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Detect regressions in business logic, edge cases, and guard conditions.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `covers topic sync events and errors`.
- Test case: `covers topic sync model defaults and explicit values`.
- Test case: `covers additional command dto variants`.
- Test case: `covers topic tree api version registry defaults`.

## PluginCommandBusContractTest.kt

**File Path**
`modules/extension-ports/src/test/kotlin/com/authord/mkdocs/ports/PluginCommandBusContractTest.kt`

**What it does**
Defines a contract test suite for `PluginCommandBus` by exercising expected behavior and edge cases.

**Why it matters**
It protects API/port compatibility so independent implementations can evolve without breaking callers.

**Dependencies**
- Internal: extension-ports module APIs, infra-defaults adapters, com.authord.mkdocs.defaults.command.InMemoryCommandRegistry, com.authord.mkdocs.ports.command.DefaultPluginCommandBus, com.authord.mkdocs.ports.topic.TopicTreeCommandResult, com.authord.mkdocs.ports.topic.TopicTreeCommandStatus
- External: Kotlin Test

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Enforce interface/port invariants shared by multiple implementations.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `dispatches command to registered handler`.
- Test case: `returns rejected result when no handler exists`.

## TopicTreeCommandDtosCoverageTest.kt

**File Path**
`modules/extension-ports/src/test/kotlin/com/authord/mkdocs/ports/TopicTreeCommandDtosCoverageTest.kt`

**What it does**
Defines a unit test suite for `TopicTreeCommandDtos` by exercising expected behavior and edge cases.

**Why it matters**
It provides fast regression coverage for local logic so changes can be made safely.

**Dependencies**
- Internal: extension-ports module APIs, com.authord.mkdocs.ports.topic.AddTopicNodeCommand, com.authord.mkdocs.ports.topic.MoveTopicNodeCommand, com.authord.mkdocs.ports.topic.RemoveTopicNodeCommand, com.authord.mkdocs.ports.topic.RenameTopicNodeCommand
- External: Kotlin Test

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Detect regressions in business logic, edge cases, and guard conditions.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `command dtos expose expected fields and command types`.
- Test case: `result and violation dtos expose fields`.

## DocsFileGatewayContractTest.kt

**File Path**
`modules/extension-ports/src/test/kotlin/com/authord/mkdocs/ports/topic/DocsFileGatewayContractTest.kt`

**What it does**
Defines a contract test suite for `DocsFileGateway` by exercising expected behavior and edge cases.

**Why it matters**
It protects API/port compatibility so independent implementations can evolve without breaking callers.

**Dependencies**
- Internal: extension-ports local package types
- External: Kotlin Test

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Enforce interface/port invariants shared by multiple implementations.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `defines markdown create delete rename move rewrite and heading sync operations`.
- Test case: `delete mode supports recoverable and nav only variants`.
- Test case: `default heading sync operation returns success with same relative path`.

## InstanceRegistryPortContractTest.kt

**File Path**
`modules/extension-ports/src/test/kotlin/com/authord/mkdocs/ports/topic/InstanceRegistryPortContractTest.kt`

**What it does**
Defines a contract test suite for `InstanceRegistryPort` by exercising expected behavior and edge cases.

**Why it matters**
It protects API/port compatibility so independent implementations can evolve without breaking callers.

**Dependencies**
- Internal: extension-ports local package types
- External: Kotlin Test

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Enforce interface/port invariants shared by multiple implementations.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `defines default discovery registration listing selection and active instance lookup`.
- Test case: `instance reference includes id config and docs paths`.

## MkDocsConfigGatewayContractTest.kt

**File Path**
`modules/extension-ports/src/test/kotlin/com/authord/mkdocs/ports/topic/MkDocsConfigGatewayContractTest.kt`

**What it does**
Defines a contract test suite for `MkDocsConfigGateway` by exercising expected behavior and edge cases.

**Why it matters**
It protects API/port compatibility so independent implementations can evolve without breaking callers.

**Dependencies**
- Internal: extension-ports local package types
- External: Kotlin Test

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Enforce interface/port invariants shared by multiple implementations.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `defines deterministic config load write and serialize contract`.
- Test case: `config document keeps nav and not in nav collections`.

## TreeSyncOrchestratorContractTest.kt

**File Path**
`modules/extension-ports/src/test/kotlin/com/authord/mkdocs/ports/topic/TreeSyncOrchestratorContractTest.kt`

**What it does**
Defines a contract test suite for `TreeSyncOrchestrator` by exercising expected behavior and edge cases.

**Why it matters**
It protects API/port compatibility so independent implementations can evolve without breaking callers.

**Dependencies**
- Internal: extension-ports local package types
- External: Kotlin Test

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Enforce interface/port invariants shared by multiple implementations.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `defines apply rollback and compensate contract`.
- Test case: `sync transaction requires command and instance context`.

## InMemoryCommandRegistry.kt

**File Path**
`modules/infra-defaults/src/main/kotlin/com/authord/mkdocs/defaults/command/InMemoryCommandRegistry.kt`

**What it does**
Implements `InMemoryCommandRegistry` as a component in the `infra-defaults` module.

**Why it matters**
This file provides default infrastructure behavior used when richer adapters are not configured.

**Dependencies**
- Internal: extension-ports module APIs, com.authord.mkdocs.ports.command.CommandRegistry, com.authord.mkdocs.ports.command.TopicTreeCommandHandler
- External: java.util.concurrent.ConcurrentHashMap

**Responsibilities**
- Provide feature-specific implementation logic for this module area.
- Integrate with neighboring services/components to deliver end behavior.

**Key Functionalities**
- Primary declarations: `InMemoryCommandRegistry`.
- Focuses on type/contract definitions more than executable method logic.

## NoOpPreviewSyncAdapter.kt

**File Path**
`modules/infra-defaults/src/main/kotlin/com/authord/mkdocs/defaults/preview/NoOpPreviewSyncAdapter.kt`

**What it does**
Implements `NoOpPreviewSyncAdapter` as a adapter layer component in the `infra-defaults` module.

**Why it matters**
It connects abstract project workflows to concrete runtime or filesystem operations required in production.

**Dependencies**
- Internal: extension-ports module APIs, com.authord.mkdocs.ports.preview.PreviewSyncPort
- External: Kotlin/Java standard library only.

**Responsibilities**
- Translate domain-level requests into concrete runtime/filesystem operations.
- Normalize external behavior into plugin-friendly results and errors.

**Key Functionalities**
- Primary declarations: `NoOpPreviewSyncAdapter`.
- Focuses on type/contract definitions more than executable method logic.

## NoOpVectorStoreAdapter.kt

**File Path**
`modules/infra-defaults/src/main/kotlin/com/authord/mkdocs/defaults/vector/NoOpVectorStoreAdapter.kt`

**What it does**
Implements `NoOpVectorStoreAdapter` as a adapter layer component in the `infra-defaults` module.

**Why it matters**
It connects abstract project workflows to concrete runtime or filesystem operations required in production.

**Dependencies**
- Internal: extension-ports module APIs, com.authord.mkdocs.ports.vector.VectorStorePort
- External: Kotlin/Java standard library only.

**Responsibilities**
- Translate domain-level requests into concrete runtime/filesystem operations.
- Normalize external behavior into plugin-friendly results and errors.

**Key Functionalities**
- Primary declarations: `NoOpVectorStoreAdapter`.
- Focuses on type/contract definitions more than executable method logic.

## InMemoryCommandRegistryTest.kt

**File Path**
`modules/infra-defaults/src/test/kotlin/com/authord/mkdocs/defaults/command/InMemoryCommandRegistryTest.kt`

**What it does**
Defines a unit test suite for `InMemoryCommand` by exercising expected behavior and edge cases.

**Why it matters**
It provides fast regression coverage for local logic so changes can be made safely.

**Dependencies**
- Internal: extension-ports module APIs, com.authord.mkdocs.ports.command.TopicTreeCommandHandler, com.authord.mkdocs.ports.topic.ValidateTopicTreeCommand, com.authord.mkdocs.ports.topic.TopicTreeCommandResult, com.authord.mkdocs.ports.topic.TopicTreeCommandStatus
- External: Kotlin Test

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Detect regressions in business logic, edge cases, and guard conditions.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `register and resolve handler by command type`.

## NoOpAdaptersTest.kt

**File Path**
`modules/infra-defaults/src/test/kotlin/com/authord/mkdocs/defaults/ports/NoOpAdaptersTest.kt`

**What it does**
Defines a unit test suite for `NoOpAdapters` by exercising expected behavior and edge cases.

**Why it matters**
It provides fast regression coverage for local logic so changes can be made safely.

**Dependencies**
- Internal: infra-defaults adapters, com.authord.mkdocs.defaults.preview.NoOpPreviewSyncAdapter, com.authord.mkdocs.defaults.vector.NoOpVectorStoreAdapter
- External: Kotlin Test

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Detect regressions in business logic, edge cases, and guard conditions.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `preview sync adapter captures deltas without side effects`.
- Test case: `vector store adapter returns empty results`.

## BaseUrlDetector.kt

**File Path**
`modules/mkdocs-runtime-adapter/src/main/kotlin/com/authord/mkdocs/runtime/BaseUrlDetector.kt`

**What it does**
Implements `BaseUrlDetector` as a component in the `mkdocs-runtime-adapter` module.

**Why it matters**
This file bridges plugin logic with the MkDocs/uv runtime and filesystem behaviors.

**Dependencies**
- Internal: mkdocs-runtime-adapter local package types
- External: Kotlin/Java standard library only.

**Responsibilities**
- Provide feature-specific implementation logic for this module area.
- Integrate with neighboring services/components to deliver end behavior.
- Keep runtime interactions deterministic and safe for project-local execution.

**Key Functionalities**
- Contains lightweight configuration/constants or extension declarations.
- Handles MkDocs/uv process or filesystem integration points.

## DocsFileGatewayAdapter.kt

**File Path**
`modules/mkdocs-runtime-adapter/src/main/kotlin/com/authord/mkdocs/runtime/DocsFileGatewayAdapter.kt`

**What it does**
Implements `DocsFileGatewayAdapter` as a adapter layer component in the `mkdocs-runtime-adapter` module.

**Why it matters**
It connects abstract project workflows to concrete runtime or filesystem operations required in production.

**Dependencies**
- Internal: extension-ports module APIs, com.authord.mkdocs.ports.topic.DefaultTopicSyncError, com.authord.mkdocs.ports.topic.DocsFileGateway, com.authord.mkdocs.ports.topic.TopicDeleteMode, com.authord.mkdocs.ports.topic.TopicGatewayResult
- External: Java NIO, java.awt.Desktop, java.nio.file.Files, java.nio.file.Path, java.nio.file.Paths

**Responsibilities**
- Translate domain-level requests into concrete runtime/filesystem operations.
- Normalize external behavior into plugin-friendly results and errors.
- Keep runtime interactions deterministic and safe for project-local execution.

**Key Functionalities**
- Primary declarations: `DocsFileGatewayAdapter`.
- Focuses on type/contract definitions more than executable method logic.
- Handles MkDocs/uv process or filesystem integration points.

## MarkdownHeadingSupport.kt

**File Path**
`modules/mkdocs-runtime-adapter/src/main/kotlin/com/authord/mkdocs/runtime/MarkdownHeadingSupport.kt`

**What it does**
Implements `MarkdownHeadingSupport` as a port contract in the `mkdocs-runtime-adapter` module.

**Why it matters**
It establishes a clean boundary between core logic and concrete implementations, enabling modularity and testability.

**Dependencies**
- Internal: mkdocs-runtime-adapter local package types
- External: Kotlin/Java standard library only.

**Responsibilities**
- Define a stable contract for collaborators across module boundaries.
- Document capability shape, inputs, and outputs expected by callers.
- Keep runtime interactions deterministic and safe for project-local execution.

**Key Functionalities**
- Primary declarations: `MarkdownHeadingSupport`.
- Implements key methods/functions including `extractFirstH1`, `upsertFirstH1`, `defaultTopicContent`.
- Handles MkDocs/uv process or filesystem integration points.

## MarkdownLinkRewriter.kt

**File Path**
`modules/mkdocs-runtime-adapter/src/main/kotlin/com/authord/mkdocs/runtime/MarkdownLinkRewriter.kt`

**What it does**
Implements `MarkdownLinkRewriter` as a component in the `mkdocs-runtime-adapter` module.

**Why it matters**
This file bridges plugin logic with the MkDocs/uv runtime and filesystem behaviors.

**Dependencies**
- Internal: extension-ports module APIs, com.authord.mkdocs.ports.topic.DefaultTopicSyncError, com.authord.mkdocs.ports.topic.TopicGatewayResult, com.authord.mkdocs.ports.topic.TopicInstanceRef, com.authord.mkdocs.ports.topic.TopicSyncErrorCode
- External: Java NIO, java.nio.file.Files, java.nio.file.Path, java.nio.file.Paths

**Responsibilities**
- Provide feature-specific implementation logic for this module area.
- Integrate with neighboring services/components to deliver end behavior.
- Keep runtime interactions deterministic and safe for project-local execution.

**Key Functionalities**
- Primary declarations: `MarkdownLinkRewriter`.
- Implements key methods/functions including `rewrite`.
- Handles MkDocs/uv process or filesystem integration points.

## MkDocsYamlGateway.kt

**File Path**
`modules/mkdocs-runtime-adapter/src/main/kotlin/com/authord/mkdocs/runtime/MkDocsYamlGateway.kt`

**What it does**
Implements `MkDocsYamlGateway` as a adapter layer component in the `mkdocs-runtime-adapter` module.

**Why it matters**
It connects abstract project workflows to concrete runtime or filesystem operations required in production.

**Dependencies**
- Internal: extension-ports module APIs, com.authord.mkdocs.ports.topic.DefaultTopicSyncError, com.authord.mkdocs.ports.topic.MkDocsConfigDocument, com.authord.mkdocs.ports.topic.MkDocsConfigGateway, com.authord.mkdocs.ports.topic.TopicGatewayResult
- External: SnakeYAML, Java NIO, org.yaml.snakeyaml.DumperOptions, org.yaml.snakeyaml.LoaderOptions, org.yaml.snakeyaml.Yaml, org.yaml.snakeyaml.constructor.SafeConstructor

**Responsibilities**
- Translate domain-level requests into concrete runtime/filesystem operations.
- Normalize external behavior into plugin-friendly results and errors.
- Keep runtime interactions deterministic and safe for project-local execution.

**Key Functionalities**
- Primary declarations: `MkDocsYamlGateway`.
- Focuses on type/contract definitions more than executable method logic.
- Handles MkDocs/uv process or filesystem integration points.

## MkdocsProcessManager.kt

**File Path**
`modules/mkdocs-runtime-adapter/src/main/kotlin/com/authord/mkdocs/runtime/MkdocsProcessManager.kt`

**What it does**
Implements `MkdocsProcessManager` as a workflow coordinator in the `mkdocs-runtime-adapter` module.

**Why it matters**
It centralizes cross-service sequencing so lifecycle transitions remain predictable and recoverable.

**Dependencies**
- Internal: mkdocs-runtime-adapter local package types
- External: java.util.concurrent.ConcurrentHashMap, java.util.concurrent.locks.ReentrantLock

**Responsibilities**
- Coordinate workflow steps and state transitions for its feature area.
- Encapsulate reusable logic so UI/actions can call a single abstraction.
- Keep runtime interactions deterministic and safe for project-local execution.

**Key Functionalities**
- Primary declarations: `RuntimeServerConfig`, `ManagedProcessHandle`, `RuntimeStartResult`, `RuntimeProcessDiagnostics`, plus `2` more.
- Implements key methods/functions including `stop`, `isAlive`, `startupOutput`, `stdoutOutput`, `stderrOutput`, `exitCodeOrNull`, plus `6` additional members.
- Handles MkDocs/uv process or filesystem integration points.

## UvBootstrapService.kt

**File Path**
`modules/mkdocs-runtime-adapter/src/main/kotlin/com/authord/mkdocs/runtime/UvBootstrapService.kt`

**What it does**
Implements `UvBootstrapService` as a service in the `mkdocs-runtime-adapter` module.

**Why it matters**
It concentrates reusable business logic so multiple callers can share consistent behavior.

**Dependencies**
- Internal: mkdocs-runtime-adapter local package types
- External: Java NIO, java.nio.file.Files, java.nio.file.Path, java.security.MessageDigest, java.util.concurrent.ConcurrentHashMap

**Responsibilities**
- Coordinate workflow steps and state transitions for its feature area.
- Encapsulate reusable logic so UI/actions can call a single abstraction.
- Keep runtime interactions deterministic and safe for project-local execution.

**Key Functionalities**
- Primary declarations: `CommandResult`, `BootstrapResult`.
- Implements key methods/functions including `run`.
- Handles MkDocs/uv process or filesystem integration points.

## UvExecutableProvider.kt

**File Path**
`modules/mkdocs-runtime-adapter/src/main/kotlin/com/authord/mkdocs/runtime/UvExecutableProvider.kt`

**What it does**
Implements `UvExecutableProvider` as a provider/factory component in the `mkdocs-runtime-adapter` module.

**Why it matters**
It encapsulates resource discovery/creation logic and keeps callers independent from setup details.

**Dependencies**
- Internal: mkdocs-runtime-adapter local package types
- External: Java NIO, Java IO, java.io.File, java.net.URI, java.net.http.HttpClient, java.net.http.HttpRequest

**Responsibilities**
- Provide feature-specific implementation logic for this module area.
- Integrate with neighboring services/components to deliver end behavior.
- Keep runtime interactions deterministic and safe for project-local execution.

**Key Functionalities**
- Primary declarations: `UvExecutableResult`, `StaticUvExecutableProvider`, `UvArchiveDownloadResult`, `HttpUvArchiveDownloader`, plus `2` more.
- Implements key methods/functions including `resolve`, `download`, `extract`.
- Handles MkDocs/uv process or filesystem integration points.

## BaseUrlDetectorStdoutTest.kt

**File Path**
`modules/mkdocs-runtime-adapter/src/test/kotlin/com/authord/mkdocs/runtime/BaseUrlDetectorStdoutTest.kt`

**What it does**
Defines a unit test suite for `BaseUrlDetectorStdout` by exercising expected behavior and edge cases.

**Why it matters**
It provides fast regression coverage for local logic so changes can be made safely.

**Dependencies**
- Internal: mkdocs-runtime-adapter local package types
- External: Kotlin Test

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Detect regressions in business logic, edge cases, and guard conditions.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `detects runtime base url from multiline stdout`.
- Test case: `trims trailing punctuation from detected url`.
- Test case: `returns null when stdout has no http or https url`.
- Test case: `ignores unrelated warning urls`.

## BaseUrlDetectorTest.kt

**File Path**
`modules/mkdocs-runtime-adapter/src/test/kotlin/com/authord/mkdocs/runtime/BaseUrlDetectorTest.kt`

**What it does**
Defines a unit test suite for `BaseUrlDetector` by exercising expected behavior and edge cases.

**Why it matters**
It provides fast regression coverage for local logic so changes can be made safely.

**Dependencies**
- Internal: mkdocs-runtime-adapter local package types
- External: Kotlin Test

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Detect regressions in business logic, edge cases, and guard conditions.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `detects base url from startup output`.
- Test case: `returns null when url is absent`.

## DeleteSemanticsPolicyTest.kt

**File Path**
`modules/mkdocs-runtime-adapter/src/test/kotlin/com/authord/mkdocs/runtime/DeleteSemanticsPolicyTest.kt`

**What it does**
Defines a unit test suite for `DeleteSemantics` by exercising expected behavior and edge cases.

**Why it matters**
It provides fast regression coverage for local logic so changes can be made safely.

**Dependencies**
- Internal: extension-ports module APIs, com.authord.mkdocs.ports.topic.TopicDeleteMode, com.authord.mkdocs.ports.topic.TopicGatewayResult, com.authord.mkdocs.ports.topic.TopicInstanceRef
- External: Kotlin Test, Java NIO, java.nio.file.Files

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Detect regressions in business logic, edge cases, and guard conditions.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `recoverable delete with default trash strategy removes markdown safely`.
- Test case: `recoverable delete uses system trash path when available`.
- Test case: `recoverable delete falls back to recovery location when system trash unavailable`.
- Test case: `nav-only delete leaves underlying markdown file untouched`.

## DocsFileGatewayAdapterMutationTest.kt

**File Path**
`modules/mkdocs-runtime-adapter/src/test/kotlin/com/authord/mkdocs/runtime/DocsFileGatewayAdapterMutationTest.kt`

**What it does**
Defines a unit test suite for `DocsFileGatewayAdapter` by exercising expected behavior and edge cases.

**Why it matters**
It provides fast regression coverage for local logic so changes can be made safely.

**Dependencies**
- Internal: extension-ports module APIs, com.authord.mkdocs.ports.topic.TopicDeleteMode, com.authord.mkdocs.ports.topic.TopicGatewayResult, com.authord.mkdocs.ports.topic.TopicInstanceRef
- External: Kotlin Test, Java NIO, java.nio.file.Files

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Detect regressions in business logic, edge cases, and guard conditions.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `gateway synchronizes create rename move and recoverable delete operations`.
- Test case: `gateway upserts markdown title heading`.

## MarkdownHeadingSupportTest.kt

**File Path**
`modules/mkdocs-runtime-adapter/src/test/kotlin/com/authord/mkdocs/runtime/MarkdownHeadingSupportTest.kt`

**What it does**
Defines a unit test suite for `MarkdownHeadingSupport` by exercising expected behavior and edge cases.

**Why it matters**
It provides fast regression coverage for local logic so changes can be made safely.

**Dependencies**
- Internal: mkdocs-runtime-adapter local package types
- External: Kotlin Test

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Detect regressions in business logic, edge cases, and guard conditions.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `extract first h1 skips code fences and returns first top-level heading`.
- Test case: `extract first h1 handles yaml front matter`.
- Test case: `extract first h1 returns null when heading is missing`.
- Test case: `upsert first h1 replaces existing heading`.
- Test case: `upsert first h1 inserts heading after front matter when absent`.

## MarkdownLinkRewriteScopeTest.kt

**File Path**
`modules/mkdocs-runtime-adapter/src/test/kotlin/com/authord/mkdocs/runtime/MarkdownLinkRewriteScopeTest.kt`

**What it does**
Defines a unit test suite for `MarkdownLinkRewrite` by exercising expected behavior and edge cases.

**Why it matters**
It provides fast regression coverage for local logic so changes can be made safely.

**Dependencies**
- Internal: extension-ports module APIs, com.authord.mkdocs.ports.topic.TopicGatewayResult, com.authord.mkdocs.ports.topic.TopicInstanceRef
- External: Kotlin Test, Java NIO, java.nio.file.Files

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Detect regressions in business logic, edge cases, and guard conditions.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `rewrite updates only markdown relative links and preserves external or non-markdown targets`.

## MkDocsYamlGatewayRoundTripTest.kt

**File Path**
`modules/mkdocs-runtime-adapter/src/test/kotlin/com/authord/mkdocs/runtime/MkDocsYamlGatewayRoundTripTest.kt`

**What it does**
Defines a unit test suite for `MkDocsYamlGateway` by exercising expected behavior and edge cases.

**Why it matters**
It provides fast regression coverage for local logic so changes can be made safely.

**Dependencies**
- Internal: extension-ports module APIs, com.authord.mkdocs.ports.topic.MkDocsConfigDocument, com.authord.mkdocs.ports.topic.TopicGatewayResult, com.authord.mkdocs.ports.topic.TopicInstanceRef, com.authord.mkdocs.ports.topic.TopicNavNode
- External: Kotlin Test, Java NIO, java.nio.file.Files

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Detect regressions in business logic, edge cases, and guard conditions.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `roundtrip parse and deterministic serialization keeps site_name docs_dir nav and not_in_nav`.
- Test case: `load and serialize without nav preserves no-nav mode`.
- Test case: `writeConfig preserves deterministic output for unchanged logical structure`.
- Test case: `writeConfig preserves unknown keys and custom docs_dir when nav is present`.
- Test case: `serializeDeterministically uses document docs_dir when no raw yaml is present`.

## MkdocsProcessManagerTest.kt

**File Path**
`modules/mkdocs-runtime-adapter/src/test/kotlin/com/authord/mkdocs/runtime/MkdocsProcessManagerTest.kt`

**What it does**
Defines a unit test suite for `MkdocsProcess` by exercising expected behavior and edge cases.

**Why it matters**
It provides fast regression coverage for local logic so changes can be made safely.

**Dependencies**
- Internal: mkdocs-runtime-adapter local package types
- External: Kotlin Test

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Detect regressions in business logic, edge cases, and guard conditions.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `managed process handle default startup output is empty`.
- Test case: `runtime start result default startup output is empty`.
- Test case: `ensures single server instance per project`.
- Test case: `restart stops previous process and launches a new one`.
- Test case: `stop returns false when no process exists`.
- Additional `4` test cases expand scenario coverage.

## ProjectManagedUvExecutableProviderTest.kt

**File Path**
`modules/mkdocs-runtime-adapter/src/test/kotlin/com/authord/mkdocs/runtime/ProjectManagedUvExecutableProviderTest.kt`

**What it does**
Defines a unit test suite for `ProjectManagedUvExecutable` by exercising expected behavior and edge cases.

**Why it matters**
It provides fast regression coverage for local logic so changes can be made safely.

**Dependencies**
- Internal: mkdocs-runtime-adapter local package types
- External: Kotlin Test, Java NIO, java.nio.file.Files, java.nio.file.Path, java.nio.file.attribute.PosixFilePermission

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Detect regressions in business logic, edge cases, and guard conditions.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `reuses existing project managed uv executable`.
- Test case: `uses explicit configured uv path directly without copying`.
- Test case: `downloads uv into project managed tools path when discovery fails`.
- Test case: `returns actionable failure when uv cannot be discovered or downloaded`.

## RuntimeAdapterCoverageEdgeCasesTest.kt

**File Path**
`modules/mkdocs-runtime-adapter/src/test/kotlin/com/authord/mkdocs/runtime/RuntimeAdapterCoverageEdgeCasesTest.kt`

**What it does**
Defines a unit test suite for `RuntimeAdapter` by exercising expected behavior and edge cases.

**Why it matters**
It provides fast regression coverage for local logic so changes can be made safely.

**Dependencies**
- Internal: extension-ports module APIs, com.authord.mkdocs.ports.topic.MkDocsConfigDocument, com.authord.mkdocs.ports.topic.TopicDeleteMode, com.authord.mkdocs.ports.topic.TopicGatewayResult, com.authord.mkdocs.ports.topic.TopicInstanceRef
- External: Kotlin Test, Java NIO, java.nio.file.Files

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Detect regressions in business logic, edge cases, and guard conditions.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `mkdocs yaml gateway covers parse and write failure branches`.
- Test case: `docs file gateway adapter covers validation and io failure branches`.
- Test case: `markdown link rewriter covers instance scope io and non-target markdown branches`.
- Test case: `uv bootstrap service covers two-phase install with get-deps`.
- Test case: `uv bootstrap detects mkdocs yaml file variant`.
- Additional `4` test cases expand scenario coverage.

## RuntimeDecouplingPolicyTest.kt

**File Path**
`modules/mkdocs-runtime-adapter/src/test/kotlin/com/authord/mkdocs/runtime/RuntimeDecouplingPolicyTest.kt`

**What it does**
Defines a unit test suite for `RuntimeDecoupling` by exercising expected behavior and edge cases.

**Why it matters**
It provides fast regression coverage for local logic so changes can be made safely.

**Dependencies**
- Internal: mkdocs-runtime-adapter local package types
- External: Kotlin Test

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Detect regressions in business logic, edge cases, and guard conditions.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `default runtime command does not hardcode host or port`.
- Test case: `manager preserves caller-defined command without injecting runtime endpoint`.

## UvBootstrapServiceTest.kt

**File Path**
`modules/mkdocs-runtime-adapter/src/test/kotlin/com/authord/mkdocs/runtime/UvBootstrapServiceTest.kt`

**What it does**
Defines a unit test suite for `UvBootstrap` by exercising expected behavior and edge cases.

**Why it matters**
It provides fast regression coverage for local logic so changes can be made safely.

**Dependencies**
- Internal: mkdocs-runtime-adapter local package types
- External: Kotlin Test, Java NIO, java.nio.file.Path

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Detect regressions in business logic, edge cases, and guard conditions.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `command result exposes stdout and stderr properties`.
- Test case: `runs bootstrap and install commands on first activation`.
- Test case: `uses resolved uv executable path for all commands`.
- Test case: `fails bootstrap when uv executable cannot be resolved`.
- Test case: `reuses existing runtime directory and skips uv venv command`.
- Additional `12` test cases expand scenario coverage.

## UvExecutableProviderCoverageTest.kt

**File Path**
`modules/mkdocs-runtime-adapter/src/test/kotlin/com/authord/mkdocs/runtime/UvExecutableProviderCoverageTest.kt`

**What it does**
Defines a unit test suite for `UvExecutableProvider` by exercising expected behavior and edge cases.

**Why it matters**
It provides fast regression coverage for local logic so changes can be made safely.

**Dependencies**
- Internal: mkdocs-runtime-adapter local package types
- External: Kotlin Test, Java NIO, Java IO, com.sun.net.httpserver.HttpServer, java.io.File, java.net.InetSocketAddress, java.net.ServerSocket

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Detect regressions in business logic, edge cases, and guard conditions.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `static provider returns configured executable`.
- Test case: `http archive downloader covers success http error and connection failure`.
- Test case: `default archive extractor covers zip and tar branches plus failures`.
- Test case: `provider covers path candidate and common fallback discovery`.
- Test case: `provider covers managed windows executable and download failure branches`.
- Additional `2` test cases expand scenario coverage.

## ActivationErrorPresenter.kt

**File Path**
`modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/ActivationErrorPresenter.kt`

**What it does**
Implements `ActivationErrorPresenter` as a domain model definition in the `ui-plugin` module.

**Why it matters**
It defines shared data semantics that must remain stable across module boundaries.

**Dependencies**
- Internal: ui-plugin local package types
- External: Kotlin/Java standard library only.

**Responsibilities**
- Define typed data structures exchanged across services and modules.
- Keep semantic meaning explicit for serialization, validation, or routing.
- Respect IntelliJ lifecycle/disposable boundaries when managing UI state.

**Key Functionalities**
- Primary declarations: `ActivationFailureReason`.
- Focuses on type/contract definitions more than executable method logic.
- Interacts with IntelliJ services/actions/tool windows for plugin UX flows.

## DocsExplorerService.kt

**File Path**
`modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/DocsExplorerService.kt`

**What it does**
Implements `DocsExplorerService` as a service in the `ui-plugin` module.

**Why it matters**
It concentrates reusable business logic so multiple callers can share consistent behavior.

**Dependencies**
- Internal: ui-plugin local package types
- External: Java NIO, java.nio.file.Files, java.nio.file.Path

**Responsibilities**
- Coordinate workflow steps and state transitions for its feature area.
- Encapsulate reusable logic so UI/actions can call a single abstraction.
- Respect IntelliJ lifecycle/disposable boundaries when managing UI state.

**Key Functionalities**
- Primary declarations: `DocsExplorerService`.
- Implements key methods/functions including `discoverMarkdownFiles`.
- Interacts with IntelliJ services/actions/tool windows for plugin UX flows.

## DocsFileSelectionPublisher.kt

**File Path**
`modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/DocsFileSelectionPublisher.kt`

**What it does**
Implements `DocsFileSelectionPublisher` as a component in the `ui-plugin` module.

**Why it matters**
This file drives IntelliJ plugin user experience, lifecycle orchestration, or UI integration.

**Dependencies**
- Internal: ui-plugin local package types
- External: Kotlin/Java standard library only.

**Responsibilities**
- Provide feature-specific implementation logic for this module area.
- Integrate with neighboring services/components to deliver end behavior.
- Respect IntelliJ lifecycle/disposable boundaries when managing UI state.

**Key Functionalities**
- Primary declarations: `DocsFileSelectedEvent`, `DocsFileSelectionPublisher`.
- Implements key methods/functions including `subscribe`, `publish`.
- Interacts with IntelliJ services/actions/tool windows for plugin UX flows.

## FeatureFlagPolicyService.kt

**File Path**
`modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/FeatureFlagPolicyService.kt`

**What it does**
Implements `FeatureFlagPolicyService` as a service in the `ui-plugin` module.

**Why it matters**
It concentrates reusable business logic so multiple callers can share consistent behavior.

**Dependencies**
- Internal: core-domain module APIs, com.authord.mkdocs.core.flags.FeatureFlagPolicy
- External: Kotlin/Java standard library only.

**Responsibilities**
- Coordinate workflow steps and state transitions for its feature area.
- Encapsulate reusable logic so UI/actions can call a single abstraction.
- Respect IntelliJ lifecycle/disposable boundaries when managing UI state.

**Key Functionalities**
- Primary declarations: `FeatureFlagPolicyService`.
- Implements key methods/functions including `current`, `update`, `allowsMvpOnlyExecution`.
- Interacts with IntelliJ services/actions/tool windows for plugin UX flows.

## NavigationCoordinator.kt

**File Path**
`modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/NavigationCoordinator.kt`

**What it does**
Implements `NavigationCoordinator` as a workflow coordinator in the `ui-plugin` module.

**Why it matters**
It centralizes cross-service sequencing so lifecycle transitions remain predictable and recoverable.

**Dependencies**
- Internal: core-domain module APIs, com.authord.mkdocs.core.navigation.RouteMappingService
- External: Kotlin/Java standard library only.

**Responsibilities**
- Coordinate workflow steps and state transitions for its feature area.
- Encapsulate reusable logic so UI/actions can call a single abstraction.
- Respect IntelliJ lifecycle/disposable boundaries when managing UI state.

**Key Functionalities**
- Primary declarations: `NavigationResult`, `NavigationCoordinator`.
- Implements key methods/functions including `onFileSelected`.
- Interacts with IntelliJ services/actions/tool windows for plugin UX flows.

## PluginActivationService.kt

**File Path**
`modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/PluginActivationService.kt`

**What it does**
Implements `PluginActivationService` as a service in the `ui-plugin` module.

**Why it matters**
It concentrates reusable business logic so multiple callers can share consistent behavior.

**Dependencies**
- Internal: core-domain module APIs, mkdocs-runtime-adapter APIs, ui-plugin APIs, com.authord.mkdocs.core.flags.FeatureFlagPolicy, com.authord.mkdocs.runtime.BaseUrlDetector, com.authord.mkdocs.runtime.MkdocsProcessManager, com.authord.mkdocs.runtime.RuntimeProcessDiagnostics
- External: IntelliJ Platform SDK, Java NIO, com.intellij.openapi.application.PathManager, com.intellij.openapi.diagnostic.Logger, java.net.HttpURLConnection, java.net.InetAddress

**Responsibilities**
- Coordinate workflow steps and state transitions for its feature area.
- Encapsulate reusable logic so UI/actions can call a single abstraction.
- Respect IntelliJ lifecycle/disposable boundaries when managing UI state.

**Key Functionalities**
- Primary declarations: `ActivationResult`, `PluginActivationService`, `StartupAttemptFailure`, `StartupReadiness`, plus `3` more.
- Implements key methods/functions including `isReady`, `activate`, `disposeProjectResources`.
- Interacts with IntelliJ services/actions/tool windows for plugin UX flows.

## PluginCompositionRoot.kt

**File Path**
`modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/PluginCompositionRoot.kt`

**What it does**
Implements `PluginCompositionRoot` as a component in the `ui-plugin` module.

**Why it matters**
This file drives IntelliJ plugin user experience, lifecycle orchestration, or UI integration.

**Dependencies**
- Internal: extension-ports module APIs, infra-defaults adapters, ui-plugin APIs, com.authord.mkdocs.defaults.command.InMemoryCommandRegistry, com.authord.mkdocs.defaults.preview.NoOpPreviewSyncAdapter, com.authord.mkdocs.defaults.vector.NoOpVectorStoreAdapter, com.authord.mkdocs.ports.TopicTreePort
- External: IntelliJ Platform SDK, com.intellij.openapi.components.service, com.intellij.openapi.project.Project

**Responsibilities**
- Provide feature-specific implementation logic for this module area.
- Integrate with neighboring services/components to deliver end behavior.
- Respect IntelliJ lifecycle/disposable boundaries when managing UI state.

**Key Functionalities**
- Primary declarations: `PluginCompositionRoot`, `Wiring`, `NoOpMkDocsConfigGateway`, `NoOpDocsFileGateway`, plus `3` more.
- Implements key methods/functions including `create`, `runtimeIntegration`.
- Interacts with IntelliJ services/actions/tool windows for plugin UX flows.

## PreviewNavigationFailureHandler.kt

**File Path**
`modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/PreviewNavigationFailureHandler.kt`

**What it does**
Implements `PreviewNavigationFailureHandler` as a component in the `ui-plugin` module.

**Why it matters**
This file drives IntelliJ plugin user experience, lifecycle orchestration, or UI integration.

**Dependencies**
- Internal: ui-plugin local package types
- External: Kotlin/Java standard library only.

**Responsibilities**
- Provide feature-specific implementation logic for this module area.
- Integrate with neighboring services/components to deliver end behavior.
- Respect IntelliJ lifecycle/disposable boundaries when managing UI state.

**Key Functionalities**
- Primary declarations: `PreviewNavigationFailureHandler`.
- Implements key methods/functions including `messageFor`.
- Interacts with IntelliJ services/actions/tool windows for plugin UX flows.

## PreviewPaneCoordinator.kt

**File Path**
`modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/PreviewPaneCoordinator.kt`

**What it does**
Implements `PreviewPaneCoordinator` as a workflow coordinator in the `ui-plugin` module.

**Why it matters**
It centralizes cross-service sequencing so lifecycle transitions remain predictable and recoverable.

**Dependencies**
- Internal: ui-plugin local package types
- External: java.util.concurrent.ConcurrentHashMap

**Responsibilities**
- Coordinate workflow steps and state transitions for its feature area.
- Encapsulate reusable logic so UI/actions can call a single abstraction.
- Respect IntelliJ lifecycle/disposable boundaries when managing UI state.

**Key Functionalities**
- Primary declarations: `PreviewPaneState`.
- Implements key methods/functions including `navigate`, `currentUrl`, `currentState`.
- Interacts with IntelliJ services/actions/tool windows for plugin UX flows.

## AuthordBackgroundTaskRunner.kt

**File Path**
`modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/intellij/AuthordBackgroundTaskRunner.kt`

**What it does**
Implements `AuthordBackgroundTaskRunner` as a component in the `ui-plugin` module.

**Why it matters**
This file drives IntelliJ plugin user experience, lifecycle orchestration, or UI integration.

**Dependencies**
- Internal: ui-plugin local package types
- External: IntelliJ Platform SDK, com.intellij.openapi.progress.ProgressIndicator, com.intellij.openapi.progress.ProgressManager, com.intellij.openapi.progress.Task, com.intellij.openapi.project.Project

**Responsibilities**
- Provide feature-specific implementation logic for this module area.
- Integrate with neighboring services/components to deliver end behavior.
- Respect IntelliJ lifecycle/disposable boundaries when managing UI state.

**Key Functionalities**
- Contains lightweight configuration/constants or extension declarations.
- Interacts with IntelliJ services/actions/tool windows for plugin UX flows.

## AuthordMarkdownOpenPreviewListener.kt

**File Path**
`modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/intellij/AuthordMarkdownOpenPreviewListener.kt`

**What it does**
Implements `AuthordMarkdownOpenPreviewListener` as a component in the `ui-plugin` module.

**Why it matters**
This file drives IntelliJ plugin user experience, lifecycle orchestration, or UI integration.

**Dependencies**
- Internal: ui-plugin APIs, com.authord.mkdocs.ui.PluginCompositionRoot
- External: IntelliJ Platform SDK, com.intellij.openapi.fileEditor.FileEditorManager, com.intellij.openapi.fileEditor.FileEditorManagerListener, com.intellij.openapi.project.DumbAware, com.intellij.openapi.project.Project

**Responsibilities**
- Handle IDE events/actions and delegate work to application services.
- Keep interaction-side concerns separate from core business logic.
- Respect IntelliJ lifecycle/disposable boundaries when managing UI state.

**Key Functionalities**
- Primary declarations: `AuthordMarkdownOpenPreviewListener`.
- Focuses on type/contract definitions more than executable method logic.
- Interacts with IntelliJ services/actions/tool windows for plugin UX flows.

## AuthordMarkdownSplitEditorProvider.kt

**File Path**
`modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/intellij/AuthordMarkdownSplitEditorProvider.kt`

**What it does**
Implements `AuthordMarkdownSplitEditorProvider` as a provider/factory component in the `ui-plugin` module.

**Why it matters**
It encapsulates resource discovery/creation logic and keeps callers independent from setup details.

**Dependencies**
- Internal: ui-plugin APIs, com.authord.mkdocs.ui.PluginCompositionRoot
- External: IntelliJ Platform SDK, com.intellij.icons.AllIcons, com.intellij.openapi.application.ApplicationManager, com.intellij.openapi.application.ModalityState, com.intellij.openapi.actionSystem.ActionGroup

**Responsibilities**
- Provide feature-specific implementation logic for this module area.
- Integrate with neighboring services/components to deliver end behavior.
- Respect IntelliJ lifecycle/disposable boundaries when managing UI state.

**Key Functionalities**
- Primary declarations: `AuthordMarkdownSplitEditorProvider`, `AuthordMarkdownPreviewFileEditorProvider`, `AuthordMarkdownEditorWithPreview`, `AuthordMarkdownPreviewFileEditor`.
- Focuses on type/contract definitions more than executable method logic.
- Interacts with IntelliJ services/actions/tool windows for plugin UX flows.

## AuthordPreviewSettingsService.kt

**File Path**
`modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/intellij/AuthordPreviewSettingsService.kt`

**What it does**
Implements `AuthordPreviewSettingsService` as a service in the `ui-plugin` module.

**Why it matters**
It concentrates reusable business logic so multiple callers can share consistent behavior.

**Dependencies**
- Internal: ui-plugin local package types
- External: IntelliJ Platform SDK, com.intellij.openapi.components.PersistentStateComponent, com.intellij.openapi.components.Service, com.intellij.openapi.components.State, com.intellij.openapi.components.Storage

**Responsibilities**
- Coordinate workflow steps and state transitions for its feature area.
- Encapsulate reusable logic so UI/actions can call a single abstraction.
- Respect IntelliJ lifecycle/disposable boundaries when managing UI state.

**Key Functionalities**
- Primary declarations: `AuthordPreviewSettingsState`, `AuthordPreviewSettingsService`.
- Focuses on type/contract definitions more than executable method logic.
- Interacts with IntelliJ services/actions/tool windows for plugin UX flows.

## AuthordSplitEditorLayoutStateService.kt

**File Path**
`modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/intellij/AuthordSplitEditorLayoutStateService.kt`

**What it does**
Implements `AuthordSplitEditorLayoutStateService` as a service in the `ui-plugin` module.

**Why it matters**
It concentrates reusable business logic so multiple callers can share consistent behavior.

**Dependencies**
- Internal: ui-plugin local package types
- External: IntelliJ Platform SDK, com.intellij.openapi.components.PersistentStateComponent, com.intellij.openapi.components.Service, com.intellij.openapi.components.State, com.intellij.openapi.components.Storage

**Responsibilities**
- Coordinate workflow steps and state transitions for its feature area.
- Encapsulate reusable logic so UI/actions can call a single abstraction.
- Respect IntelliJ lifecycle/disposable boundaries when managing UI state.

**Key Functionalities**
- Primary declarations: `AuthordSplitEditorLayoutState`, `AuthordSplitEditorLayoutStateService`.
- Implements key methods/functions including `preferredLayout`, `setPreferredLayout`.
- Interacts with IntelliJ services/actions/tool windows for plugin UX flows.

## AuthordUiBundle.kt

**File Path**
`modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/intellij/AuthordUiBundle.kt`

**What it does**
Implements `AuthordUiBundle` as a component in the `ui-plugin` module.

**Why it matters**
This file drives IntelliJ plugin user experience, lifecycle orchestration, or UI integration.

**Dependencies**
- Internal: ui-plugin local package types
- External: IntelliJ Platform SDK, com.intellij.DynamicBundle, org.jetbrains.annotations.PropertyKey

**Responsibilities**
- Provide feature-specific implementation logic for this module area.
- Integrate with neighboring services/components to deliver end behavior.
- Respect IntelliJ lifecycle/disposable boundaries when managing UI state.

**Key Functionalities**
- Primary declarations: `AuthordUiBundle`.
- Implements key methods/functions including `message`.
- Interacts with IntelliJ services/actions/tool windows for plugin UX flows.

## CompatibilityReleaseGateService.kt

**File Path**
`modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/intellij/CompatibilityReleaseGateService.kt`

**What it does**
Implements `CompatibilityReleaseGateService` as a service in the `ui-plugin` module.

**Why it matters**
It concentrates reusable business logic so multiple callers can share consistent behavior.

**Dependencies**
- Internal: ui-plugin local package types
- External: Kotlin/Java standard library only.

**Responsibilities**
- Coordinate workflow steps and state transitions for its feature area.
- Encapsulate reusable logic so UI/actions can call a single abstraction.
- Respect IntelliJ lifecycle/disposable boundaries when managing UI state.

**Key Functionalities**
- Primary declarations: `CompatibilityReleaseGateDecision`, `CompatibilityReleaseGateService`.
- Implements key methods/functions including `evaluate`.
- Interacts with IntelliJ services/actions/tool windows for plugin UX flows.

## CompatibilitySeverityClassifier.kt

**File Path**
`modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/intellij/CompatibilitySeverityClassifier.kt`

**What it does**
Implements `CompatibilitySeverityClassifier` as a policy/classification component in the `ui-plugin` module.

**Why it matters**
It codifies decision rules that shape runtime behavior and error handling.

**Dependencies**
- Internal: ui-plugin local package types
- External: Kotlin/Java standard library only.

**Responsibilities**
- Provide feature-specific implementation logic for this module area.
- Integrate with neighboring services/components to deliver end behavior.
- Respect IntelliJ lifecycle/disposable boundaries when managing UI state.

**Key Functionalities**
- Primary declarations: `CompatibilityRegressionKind`, `CompatibilitySeverity`, `CompatibilityRegression`, `CompatibilitySeverityClassifier`.
- Implements key methods/functions including `classify`.
- Interacts with IntelliJ services/actions/tool windows for plugin UX flows.

## DocsScopeResolver.kt

**File Path**
`modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/intellij/DocsScopeResolver.kt`

**What it does**
Implements `DocsScopeResolver` as a component in the `ui-plugin` module.

**Why it matters**
This file drives IntelliJ plugin user experience, lifecycle orchestration, or UI integration.

**Dependencies**
- Internal: ui-plugin local package types
- External: IntelliJ Platform SDK, Java NIO, com.intellij.openapi.project.Project, java.nio.file.Files, java.nio.file.Path, java.util.Locale

**Responsibilities**
- Provide feature-specific implementation logic for this module area.
- Integrate with neighboring services/components to deliver end behavior.
- Respect IntelliJ lifecycle/disposable boundaries when managing UI state.

**Key Functionalities**
- Primary declarations: `DocsScopeStatus`, `DocsScopeResult`, `DocsScopeResolver`, `ConfigScope`.
- Implements key methods/functions including `resolve`.
- Interacts with IntelliJ services/actions/tool windows for plugin UX flows.

## FailureBypassStore.kt

**File Path**
`modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/intellij/FailureBypassStore.kt`

**What it does**
Implements `FailureBypassStore` as a component in the `ui-plugin` module.

**Why it matters**
This file drives IntelliJ plugin user experience, lifecycle orchestration, or UI integration.

**Dependencies**
- Internal: ui-plugin local package types
- External: IntelliJ Platform SDK, com.intellij.openapi.components.Service, com.intellij.openapi.util.SystemInfoRt, java.util.Locale, java.util.concurrent.ConcurrentHashMap

**Responsibilities**
- Provide feature-specific implementation logic for this module area.
- Integrate with neighboring services/components to deliver end behavior.
- Respect IntelliJ lifecycle/disposable boundaries when managing UI state.

**Key Functionalities**
- Primary declarations: `FailureBypassStore`.
- Implements key methods/functions including `markBypass`, `isBypassed`, `clearBypass`, `clearAll`.
- Interacts with IntelliJ services/actions/tool windows for plugin UX flows.

## InstanceRegistryService.kt

**File Path**
`modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/intellij/InstanceRegistryService.kt`

**What it does**
Implements `InstanceRegistryService` as a service in the `ui-plugin` module.

**Why it matters**
It concentrates reusable business logic so multiple callers can share consistent behavior.

**Dependencies**
- Internal: core-domain module APIs, extension-ports module APIs, com.authord.mkdocs.core.topic.PathPolicy, com.authord.mkdocs.ports.topic.DefaultTopicSyncError, com.authord.mkdocs.ports.topic.InstanceRegistryPort, com.authord.mkdocs.ports.topic.TopicGatewayResult
- External: Java NIO, java.nio.file.Files, java.nio.file.Path, java.util.concurrent.ConcurrentHashMap

**Responsibilities**
- Coordinate workflow steps and state transitions for its feature area.
- Encapsulate reusable logic so UI/actions can call a single abstraction.
- Respect IntelliJ lifecycle/disposable boundaries when managing UI state.

**Key Functionalities**
- Primary declarations: `PersistedInstanceRegistryState`, `InstanceRegistryService`, `InstanceRegistryStateStore`, `InMemoryInstanceRegistryStateStore`.
- Implements key methods/functions including `load`, `save`.
- Interacts with IntelliJ services/actions/tool windows for plugin UX flows.

## InstanceSwitchCoordinator.kt

**File Path**
`modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/intellij/InstanceSwitchCoordinator.kt`

**What it does**
Implements `InstanceSwitchCoordinator` as a workflow coordinator in the `ui-plugin` module.

**Why it matters**
It centralizes cross-service sequencing so lifecycle transitions remain predictable and recoverable.

**Dependencies**
- Internal: extension-ports module APIs, com.authord.mkdocs.ports.topic.DefaultTopicSyncError, com.authord.mkdocs.ports.topic.TopicGatewayResult, com.authord.mkdocs.ports.topic.TopicInstanceRef, com.authord.mkdocs.ports.topic.TopicSyncErrorCode
- External: Kotlin/Java standard library only.

**Responsibilities**
- Coordinate workflow steps and state transitions for its feature area.
- Encapsulate reusable logic so UI/actions can call a single abstraction.
- Respect IntelliJ lifecycle/disposable boundaries when managing UI state.

**Key Functionalities**
- Primary declarations: `InstanceSwitchOutcome`, `InstanceSwitchCoordinator`.
- Implements key methods/functions including `switchActiveInstance`.
- Interacts with IntelliJ services/actions/tool windows for plugin UX flows.

## MkDocsPreviewBrowserService.kt

**File Path**
`modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/intellij/MkDocsPreviewBrowserService.kt`

**What it does**
Implements `MkDocsPreviewBrowserService` as a service in the `ui-plugin` module.

**Why it matters**
It concentrates reusable business logic so multiple callers can share consistent behavior.

**Dependencies**
- Internal: ui-plugin local package types
- External: IntelliJ Platform SDK, com.intellij.openapi.Disposable, com.intellij.openapi.components.Service, com.intellij.openapi.diagnostic.Logger, com.intellij.openapi.project.Project

**Responsibilities**
- Coordinate workflow steps and state transitions for its feature area.
- Encapsulate reusable logic so UI/actions can call a single abstraction.
- Respect IntelliJ lifecycle/disposable boundaries when managing UI state.

**Key Functionalities**
- Primary declarations: `PreviewOwnerKind`, `MkDocsPreviewBrowserService`, `OwnerRegistryEntry`, `NoOpPreviewContent`.
- Implements key methods/functions including `ensurePreviewContent`, `previewContentOrNull`, `hasInitializedPreviewContent`, `markMarkdownActivated`, `markdownPreviewActivated`, `loadUrl`, plus `6` additional members.
- Interacts with IntelliJ services/actions/tool windows for plugin UX flows.

## MkDocsPreviewService.kt

**File Path**
`modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/intellij/MkDocsPreviewService.kt`

**What it does**
Implements `MkDocsPreviewService` as a service in the `ui-plugin` module.

**Why it matters**
It concentrates reusable business logic so multiple callers can share consistent behavior.

**Dependencies**
- Internal: mkdocs-runtime-adapter APIs, com.authord.mkdocs.runtime.MkdocsProcessManager
- External: IntelliJ Platform SDK, com.intellij.openapi.Disposable, com.intellij.openapi.diagnostic.Logger

**Responsibilities**
- Coordinate workflow steps and state transitions for its feature area.
- Encapsulate reusable logic so UI/actions can call a single abstraction.
- Respect IntelliJ lifecycle/disposable boundaries when managing UI state.

**Key Functionalities**
- Primary declarations: `MkDocsPreviewService`.
- Implements key methods/functions including `stopServer`, `isServerRunning`.
- Interacts with IntelliJ services/actions/tool windows for plugin UX flows.

## MkDocsProjectCreator.kt

**File Path**
`modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/intellij/MkDocsProjectCreator.kt`

**What it does**
Implements `MkDocsProjectCreator` as a component in the `ui-plugin` module.

**Why it matters**
This file drives IntelliJ plugin user experience, lifecycle orchestration, or UI integration.

**Dependencies**
- Internal: extension-ports module APIs, mkdocs-runtime-adapter APIs, com.authord.mkdocs.ports.topic.TopicGatewayResult, com.authord.mkdocs.ports.topic.TopicInstanceRef, com.authord.mkdocs.runtime.CommandResult, com.authord.mkdocs.runtime.CommandRunner
- External: Java NIO, java.nio.file.Files, java.nio.file.Path

**Responsibilities**
- Provide feature-specific implementation logic for this module area.
- Integrate with neighboring services/components to deliver end behavior.
- Respect IntelliJ lifecycle/disposable boundaries when managing UI state.

**Key Functionalities**
- Primary declarations: `MkDocsProjectCreationResult`, `MkDocsProjectCreator`.
- Implements key methods/functions including `createProject`.
- Interacts with IntelliJ services/actions/tool windows for plugin UX flows.

## MkdocsConfigLocator.kt

**File Path**
`modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/intellij/MkdocsConfigLocator.kt`

**What it does**
Implements `MkdocsConfigLocator` as a provider/factory component in the `ui-plugin` module.

**Why it matters**
It encapsulates resource discovery/creation logic and keeps callers independent from setup details.

**Dependencies**
- Internal: ui-plugin local package types
- External: Java NIO, java.nio.file.FileVisitResult, java.nio.file.Files, java.nio.file.Path, java.nio.file.SimpleFileVisitor

**Responsibilities**
- Provide feature-specific implementation logic for this module area.
- Integrate with neighboring services/components to deliver end behavior.
- Respect IntelliJ lifecycle/disposable boundaries when managing UI state.

**Key Functionalities**
- Primary declarations: `MkdocsConfigLocator`.
- Implements key methods/functions including `findMkdocsConfig`, `findAllMkdocsConfigs`, `invalidate`, `clear`.
- Interacts with IntelliJ services/actions/tool windows for plugin UX flows.

## MkdocsScrollSyncEngine.kt

**File Path**
`modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/intellij/MkdocsScrollSyncEngine.kt`

**What it does**
Implements `MkdocsScrollSyncEngine` as a component in the `ui-plugin` module.

**Why it matters**
This file drives IntelliJ plugin user experience, lifecycle orchestration, or UI integration.

**Dependencies**
- Internal: ui-plugin local package types
- External: IntelliJ Platform SDK, com.intellij.openapi.editor.Document, com.intellij.openapi.editor.event.DocumentEvent, java.text.Normalizer, java.util.concurrent.atomic.AtomicBoolean

**Responsibilities**
- Provide feature-specific implementation logic for this module area.
- Integrate with neighboring services/components to deliver end behavior.
- Respect IntelliJ lifecycle/disposable boundaries when managing UI state.

**Key Functionalities**
- Primary declarations: `AnchorType`, `SourceAnchor`, `DomAnchor`, `Knot`, plus `13` more.
- Implements key methods/functions including `focusRatio`, `resolveSourceState`, `currentState`, `invalidateAnchors`, `resetSyncState`, `recordDocumentChange`, plus `2` additional members.
- Interacts with IntelliJ services/actions/tool windows for plugin UX flows.

## MkdocsToolWindowFactory.kt

**File Path**
`modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/intellij/MkdocsToolWindowFactory.kt`

**What it does**
Implements `MkdocsToolWindowFactory` as a provider/factory component in the `ui-plugin` module.

**Why it matters**
It encapsulates resource discovery/creation logic and keeps callers independent from setup details.

**Dependencies**
- Internal: core-domain module APIs, extension-ports module APIs, mkdocs-runtime-adapter APIs, ui-plugin APIs, com.authord.mkdocs.core.topic.TopicTreeMutationService, com.authord.mkdocs.ports.topic.MkDocsConfigDocument, com.authord.mkdocs.ports.topic.InstanceRegistryPort, com.authord.mkdocs.ports.topic.TopicGatewayResult
- External: IntelliJ Platform SDK, Java NIO, com.intellij.icons.AllIcons, com.intellij.ide.BrowserUtil, com.intellij.openapi.actionSystem.ActionManager, com.intellij.openapi.actionSystem.AnAction

**Responsibilities**
- Provide feature-specific implementation logic for this module area.
- Integrate with neighboring services/components to deliver end behavior.
- Respect IntelliJ lifecycle/disposable boundaries when managing UI state.

**Key Functionalities**
- Primary declarations: `ShellLayoutMode`, `MkdocsToolWindowFactory`, `TopicTreeControllers`, `PreviewScrollMetrics`, plus `9` more.
- Implements key methods/functions including `loadUrl`, `loadSetupPage`, `scrollToProgress`, `scrollToY`, `requestScrollMetrics`, `requestDomSnapshot`, plus `6` additional members.
- Interacts with IntelliJ services/actions/tool windows for plugin UX flows.

## MkdocsVenvDirectoryExcludePolicy.kt

**File Path**
`modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/intellij/MkdocsVenvDirectoryExcludePolicy.kt`

**What it does**
Implements `MkdocsVenvDirectoryExcludePolicy` as a policy/classification component in the `ui-plugin` module.

**Why it matters**
It codifies decision rules that shape runtime behavior and error handling.

**Dependencies**
- Internal: ui-plugin local package types
- External: IntelliJ Platform SDK, Java NIO, com.intellij.openapi.project.ProjectManager, com.intellij.openapi.roots.impl.DirectoryIndexExcludePolicy, com.intellij.openapi.vfs.VfsUtilCore, java.nio.file.Path

**Responsibilities**
- Provide feature-specific implementation logic for this module area.
- Integrate with neighboring services/components to deliver end behavior.
- Respect IntelliJ lifecycle/disposable boundaries when managing UI state.

**Key Functionalities**
- Primary declarations: `MkdocsVenvDirectoryExcludePolicy`.
- Focuses on type/contract definitions more than executable method logic.
- Interacts with IntelliJ services/actions/tool windows for plugin UX flows.

## MultiInstanceReconciliationCoordinator.kt

**File Path**
`modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/intellij/MultiInstanceReconciliationCoordinator.kt`

**What it does**
Implements `MultiInstanceReconciliationCoordinator` as a workflow coordinator in the `ui-plugin` module.

**Why it matters**
It centralizes cross-service sequencing so lifecycle transitions remain predictable and recoverable.

**Dependencies**
- Internal: extension-ports module APIs, com.authord.mkdocs.ports.topic.MkDocsConfigDocument, com.authord.mkdocs.ports.topic.TopicInstanceRef
- External: Kotlin/Java standard library only.

**Responsibilities**
- Coordinate workflow steps and state transitions for its feature area.
- Encapsulate reusable logic so UI/actions can call a single abstraction.
- Respect IntelliJ lifecycle/disposable boundaries when managing UI state.

**Key Functionalities**
- Primary declarations: `InstanceReconciliationInput`, `MultiInstanceReconciliationResult`, `MultiInstanceReconciliationCoordinator`.
- Implements key methods/functions including `reconcile`.
- Interacts with IntelliJ services/actions/tool windows for plugin UX flows.

## PluginRuntimeIntegrationService.kt

**File Path**
`modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/intellij/PluginRuntimeIntegrationService.kt`

**What it does**
Implements `PluginRuntimeIntegrationService` as a service in the `ui-plugin` module.

**Why it matters**
It concentrates reusable business logic so multiple callers can share consistent behavior.

**Dependencies**
- Internal: core-domain module APIs, mkdocs-runtime-adapter APIs, ui-plugin APIs, com.authord.mkdocs.core.flags.FeatureFlagPolicy, com.authord.mkdocs.runtime.CommandResult, com.authord.mkdocs.runtime.CommandRunner, com.authord.mkdocs.runtime.ManagedProcessHandle
- External: IntelliJ Platform SDK, Java NIO, com.intellij.ui.JBColor, com.intellij.openapi.application.ApplicationManager, com.intellij.openapi.application.ModalityState, com.intellij.openapi.Disposable

**Responsibilities**
- Coordinate workflow steps and state transitions for its feature area.
- Encapsulate reusable logic so UI/actions can call a single abstraction.
- Respect IntelliJ lifecycle/disposable boundaries when managing UI state.

**Key Functionalities**
- Primary declarations: `PreviewStartTrigger`, `PreviewRouteIntentSource`, `PreviewRouteIntent`, `RuntimeIntegrationDependencies`, plus `9` more.
- Implements key methods/functions including `startupOutput`, `createDefault`, `canStartPreview`, `startPreview`, `startPreviewAsync`, `startPreviewWithProgress`, plus `19` additional members.
- Interacts with IntelliJ services/actions/tool windows for plugin UX flows.

## PreviewErrorExcerptFormatter.kt

**File Path**
`modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/intellij/PreviewErrorExcerptFormatter.kt`

**What it does**
Implements `PreviewErrorExcerptFormatter` as a domain model definition in the `ui-plugin` module.

**Why it matters**
It defines shared data semantics that must remain stable across module boundaries.

**Dependencies**
- Internal: ui-plugin local package types
- External: java.util.Locale

**Responsibilities**
- Define typed data structures exchanged across services and modules.
- Keep semantic meaning explicit for serialization, validation, or routing.
- Respect IntelliJ lifecycle/disposable boundaries when managing UI state.

**Key Functionalities**
- Contains lightweight configuration/constants or extension declarations.
- Interacts with IntelliJ services/actions/tool windows for plugin UX flows.

## PreviewFailureNotifier.kt

**File Path**
`modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/intellij/PreviewFailureNotifier.kt`

**What it does**
Implements `PreviewFailureNotifier` as a component in the `ui-plugin` module.

**Why it matters**
This file drives IntelliJ plugin user experience, lifecycle orchestration, or UI integration.

**Dependencies**
- Internal: ui-plugin local package types
- External: IntelliJ Platform SDK, Java NIO, com.intellij.notification.NotificationAction, com.intellij.notification.NotificationGroupManager, com.intellij.notification.NotificationType, com.intellij.openapi.application.PathManager

**Responsibilities**
- Provide feature-specific implementation logic for this module area.
- Integrate with neighboring services/components to deliver end behavior.
- Respect IntelliJ lifecycle/disposable boundaries when managing UI state.

**Key Functionalities**
- Primary declarations: `PreviewFailureNotifier`, `PreviewFailureNotificationPayload`.
- Implements key methods/functions including `notifyStartupFailure`, `clearDedupeStateForTests`.
- Interacts with IntelliJ services/actions/tool windows for plugin UX flows.

## PreviewResultReporter.kt

**File Path**
`modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/intellij/PreviewResultReporter.kt`

**What it does**
Implements `PreviewResultReporter` as a port contract in the `ui-plugin` module.

**Why it matters**
It establishes a clean boundary between core logic and concrete implementations, enabling modularity and testability.

**Dependencies**
- Internal: ui-plugin APIs, com.authord.mkdocs.ui.ActivationResult
- External: IntelliJ Platform SDK, com.intellij.notification.NotificationGroupManager, com.intellij.notification.NotificationType, com.intellij.openapi.project.Project

**Responsibilities**
- Define a stable contract for collaborators across module boundaries.
- Document capability shape, inputs, and outputs expected by callers.
- Respect IntelliJ lifecycle/disposable boundaries when managing UI state.

**Key Functionalities**
- Contains lightweight configuration/constants or extension declarations.
- Interacts with IntelliJ services/actions/tool windows for plugin UX flows.

## PreviewRouteLoadCoordinator.kt

**File Path**
`modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/intellij/PreviewRouteLoadCoordinator.kt`

**What it does**
Implements `PreviewRouteLoadCoordinator` as a workflow coordinator in the `ui-plugin` module.

**Why it matters**
It centralizes cross-service sequencing so lifecycle transitions remain predictable and recoverable.

**Dependencies**
- Internal: ui-plugin local package types
- External: IntelliJ Platform SDK, com.intellij.notification.NotificationType, com.intellij.openapi.application.ApplicationManager, com.intellij.openapi.application.ModalityState, com.intellij.openapi.project.Project

**Responsibilities**
- Coordinate workflow steps and state transitions for its feature area.
- Encapsulate reusable logic so UI/actions can call a single abstraction.
- Respect IntelliJ lifecycle/disposable boundaries when managing UI state.

**Key Functionalities**
- Primary declarations: `PreviewRouteFlowState`, `RouteReadinessOutcome`, `Cancelled`, `Unavailable`, plus `1` more.
- Focuses on type/contract definitions more than executable method logic.
- Interacts with IntelliJ services/actions/tool windows for plugin UX flows.

## PreviewRouterService.kt

**File Path**
`modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/intellij/PreviewRouterService.kt`

**What it does**
Implements `PreviewRouterService` as a service in the `ui-plugin` module.

**Why it matters**
It concentrates reusable business logic so multiple callers can share consistent behavior.

**Dependencies**
- Internal: ui-plugin local package types
- External: IntelliJ Platform SDK, com.intellij.openapi.project.Project

**Responsibilities**
- Coordinate workflow steps and state transitions for its feature area.
- Encapsulate reusable logic so UI/actions can call a single abstraction.
- Respect IntelliJ lifecycle/disposable boundaries when managing UI state.

**Key Functionalities**
- Primary declarations: `PreviewRouteTarget`, `PreviewRouteReason`, `PreviewDecision`, `PreviewRouterService`.
- Implements key methods/functions including `decide`.
- Interacts with IntelliJ services/actions/tool windows for plugin UX flows.

## PreviewStartupFailureClassifier.kt

**File Path**
`modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/intellij/PreviewStartupFailureClassifier.kt`

**What it does**
Implements `PreviewStartupFailureClassifier` as a policy/classification component in the `ui-plugin` module.

**Why it matters**
It codifies decision rules that shape runtime behavior and error handling.

**Dependencies**
- Internal: ui-plugin local package types
- External: Kotlin/Java standard library only.

**Responsibilities**
- Provide feature-specific implementation logic for this module area.
- Integrate with neighboring services/components to deliver end behavior.
- Respect IntelliJ lifecycle/disposable boundaries when managing UI state.

**Key Functionalities**
- Primary declarations: `PreviewStartupFailureCategory`, `PreviewStartupFailure`, `PreviewStartupFailureClassifier`.
- Implements key methods/functions including `classify`.
- Interacts with IntelliJ services/actions/tool windows for plugin UX flows.

## PreviewStartupFailureHandler.kt

**File Path**
`modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/intellij/PreviewStartupFailureHandler.kt`

**What it does**
Implements `PreviewStartupFailureHandler` as a component in the `ui-plugin` module.

**Why it matters**
This file drives IntelliJ plugin user experience, lifecycle orchestration, or UI integration.

**Dependencies**
- Internal: ui-plugin APIs, com.authord.mkdocs.ui.ActivationResult
- External: IntelliJ Platform SDK, com.intellij.openapi.application.ApplicationManager, com.intellij.openapi.application.ModalityState, com.intellij.openapi.fileEditor.FileEditorManager, com.intellij.openapi.project.Project

**Responsibilities**
- Provide feature-specific implementation logic for this module area.
- Integrate with neighboring services/components to deliver end behavior.
- Respect IntelliJ lifecycle/disposable boundaries when managing UI state.

**Key Functionalities**
- Primary declarations: `PreviewStartupFailureHandler`.
- Implements key methods/functions including `handleFailure`.
- Interacts with IntelliJ services/actions/tool windows for plugin UX flows.

## ReloadCoalescingGate.kt

**File Path**
`modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/intellij/ReloadCoalescingGate.kt`

**What it does**
Implements `ReloadCoalescingGate` as a component in the `ui-plugin` module.

**Why it matters**
This file drives IntelliJ plugin user experience, lifecycle orchestration, or UI integration.

**Dependencies**
- Internal: ui-plugin local package types
- External: java.util.concurrent.atomic.AtomicLong

**Responsibilities**
- Provide feature-specific implementation logic for this module area.
- Integrate with neighboring services/components to deliver end behavior.
- Respect IntelliJ lifecycle/disposable boundaries when managing UI state.

**Key Functionalities**
- Primary declarations: `ReloadCoalescingGate`.
- Implements key methods/functions including `shouldSuppressReload`, `recordReload`.
- Interacts with IntelliJ services/actions/tool windows for plugin UX flows.

## SetupPageRenderer.kt

**File Path**
`modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/intellij/SetupPageRenderer.kt`

**What it does**
Implements `SetupPageRenderer` as a UI component in the `ui-plugin` module.

**Why it matters**
It defines user-visible interface behavior and therefore impacts usability and interaction flow.

**Dependencies**
- Internal: ui-plugin local package types
- External: Kotlin/Java standard library only.

**Responsibilities**
- Provide feature-specific implementation logic for this module area.
- Integrate with neighboring services/components to deliver end behavior.
- Respect IntelliJ lifecycle/disposable boundaries when managing UI state.

**Key Functionalities**
- Primary declarations: `SetupPageRenderer`.
- Implements key methods/functions including `render`.
- Interacts with IntelliJ services/actions/tool windows for plugin UX flows.

## SetupPanel.kt

**File Path**
`modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/intellij/SetupPanel.kt`

**What it does**
Implements `SetupPanel` as a UI component in the `ui-plugin` module.

**Why it matters**
It defines user-visible interface behavior and therefore impacts usability and interaction flow.

**Dependencies**
- Internal: ui-plugin local package types
- External: IntelliJ Platform SDK, com.intellij.ide.BrowserUtil, com.intellij.openapi.application.ApplicationManager, com.intellij.openapi.ui.Messages, com.intellij.ui.JBColor

**Responsibilities**
- Provide feature-specific implementation logic for this module area.
- Integrate with neighboring services/components to deliver end behavior.
- Respect IntelliJ lifecycle/disposable boundaries when managing UI state.

**Key Functionalities**
- Primary declarations: `SetupPanel`, `ChevronDownIcon`, `QuestionCircleIcon`.
- Implements key methods/functions including `setLoading`, `requestProjectNameFromUser`.
- Interacts with IntelliJ services/actions/tool windows for plugin UX flows.

## SiteContextResolver.kt

**File Path**
`modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/intellij/SiteContextResolver.kt`

**What it does**
Implements `SiteContextResolver` as a component in the `ui-plugin` module.

**Why it matters**
This file drives IntelliJ plugin user experience, lifecycle orchestration, or UI integration.

**Dependencies**
- Internal: ui-plugin local package types
- External: Java NIO, java.nio.file.Files, java.nio.file.Path, java.util.Locale

**Responsibilities**
- Provide feature-specific implementation logic for this module area.
- Integrate with neighboring services/components to deliver end behavior.
- Respect IntelliJ lifecycle/disposable boundaries when managing UI state.

**Key Functionalities**
- Primary declarations: `SiteContext`, `SiteContextResolver`.
- Implements key methods/functions including `resolveProjectDefault`, `resolveFromRuntimeCommand`.
- Interacts with IntelliJ services/actions/tool windows for plugin UX flows.

## StartMkdocsAction.kt

**File Path**
`modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/intellij/StartMkdocsAction.kt`

**What it does**
Implements `StartMkdocsAction` as an IntelliJ action handler in the `ui-plugin` module.

**Why it matters**
It exposes plugin capabilities through IDE actions, directly affecting user-triggered behavior.

**Dependencies**
- Internal: ui-plugin APIs, com.authord.mkdocs.ui.PluginCompositionRoot
- External: IntelliJ Platform SDK, com.intellij.openapi.actionSystem.ActionUpdateThread, com.intellij.openapi.actionSystem.AnAction, com.intellij.openapi.actionSystem.AnActionEvent, com.intellij.openapi.application.ApplicationManager

**Responsibilities**
- Handle IDE events/actions and delegate work to application services.
- Keep interaction-side concerns separate from core business logic.
- Respect IntelliJ lifecycle/disposable boundaries when managing UI state.

**Key Functionalities**
- Primary declarations: `StartMkdocsAction`, `ActionPresentationState`.
- Focuses on type/contract definitions more than executable method logic.
- Interacts with IntelliJ services/actions/tool windows for plugin UX flows.

## StartMkdocsMarkdownToolbarAction.kt

**File Path**
`modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/intellij/StartMkdocsMarkdownToolbarAction.kt`

**What it does**
Implements `StartMkdocsMarkdownToolbarAction` as an IntelliJ action handler in the `ui-plugin` module.

**Why it matters**
It exposes plugin capabilities through IDE actions, directly affecting user-triggered behavior.

**Dependencies**
- Internal: ui-plugin local package types
- External: IntelliJ Platform SDK, com.intellij.openapi.actionSystem.ActionUpdateThread, com.intellij.openapi.actionSystem.AnAction, com.intellij.openapi.actionSystem.AnActionEvent, com.intellij.openapi.actionSystem.CommonDataKeys

**Responsibilities**
- Handle IDE events/actions and delegate work to application services.
- Keep interaction-side concerns separate from core business logic.
- Respect IntelliJ lifecycle/disposable boundaries when managing UI state.

**Key Functionalities**
- Primary declarations: `StartMkdocsMarkdownToolbarAction`.
- Focuses on type/contract definitions more than executable method logic.
- Interacts with IntelliJ services/actions/tool windows for plugin UX flows.

## StartupReconciliationCoordinator.kt

**File Path**
`modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/intellij/StartupReconciliationCoordinator.kt`

**What it does**
Implements `StartupReconciliationCoordinator` as a workflow coordinator in the `ui-plugin` module.

**Why it matters**
It centralizes cross-service sequencing so lifecycle transitions remain predictable and recoverable.

**Dependencies**
- Internal: extension-ports module APIs, com.authord.mkdocs.ports.topic.MkDocsConfigDocument
- External: Kotlin/Java standard library only.

**Responsibilities**
- Coordinate workflow steps and state transitions for its feature area.
- Encapsulate reusable logic so UI/actions can call a single abstraction.
- Respect IntelliJ lifecycle/disposable boundaries when managing UI state.

**Key Functionalities**
- Primary declarations: `StartupReconciliationCoordinator`.
- Implements key methods/functions including `reconcile`.
- Interacts with IntelliJ services/actions/tool windows for plugin UX flows.

## SystemRuntimeAdapters.kt

**File Path**
`modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/intellij/SystemRuntimeAdapters.kt`

**What it does**
Implements `SystemRuntimeAdapters` as a adapter layer component in the `ui-plugin` module.

**Why it matters**
It connects abstract project workflows to concrete runtime or filesystem operations required in production.

**Dependencies**
- Internal: mkdocs-runtime-adapter APIs, com.authord.mkdocs.runtime.CommandResult, com.authord.mkdocs.runtime.CommandRunner, com.authord.mkdocs.runtime.ManagedProcessHandle, com.authord.mkdocs.runtime.ProcessLauncher
- External: Java IO, java.io.File, java.io.OutputStream, java.util.ArrayDeque, java.util.concurrent.TimeUnit

**Responsibilities**
- Translate domain-level requests into concrete runtime/filesystem operations.
- Normalize external behavior into plugin-friendly results and errors.
- Respect IntelliJ lifecycle/disposable boundaries when managing UI state.

**Key Functionalities**
- Primary declarations: `ShutdownHookRegistrar`, `RuntimeShutdownHookRegistrar`, `ProcessBuilderSystemProcessFactory`, `ParentPipeGuard`, plus `5` more.
- Implements key methods/functions including `register`, `unregister`, `start`, `stop`, `launch`, `appendLine`, plus `1` additional members.
- Interacts with IntelliJ services/actions/tool windows for plugin UX flows.

## ToolWindowVisibility.kt

**File Path**
`modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/intellij/ToolWindowVisibility.kt`

**What it does**
Implements `ToolWindowVisibility` as a UI component in the `ui-plugin` module.

**Why it matters**
It defines user-visible interface behavior and therefore impacts usability and interaction flow.

**Dependencies**
- Internal: ui-plugin local package types
- External: IntelliJ Platform SDK, com.intellij.openapi.project.Project, com.intellij.openapi.wm.ToolWindowManager

**Responsibilities**
- Provide feature-specific implementation logic for this module area.
- Integrate with neighboring services/components to deliver end behavior.
- Respect IntelliJ lifecycle/disposable boundaries when managing UI state.

**Key Functionalities**
- Contains lightweight configuration/constants or extension declarations.
- Interacts with IntelliJ services/actions/tool windows for plugin UX flows.

## TopicTreeActionController.kt

**File Path**
`modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/intellij/TopicTreeActionController.kt`

**What it does**
Implements `TopicTreeActionController` as an IntelliJ action handler in the `ui-plugin` module.

**Why it matters**
It exposes plugin capabilities through IDE actions, directly affecting user-triggered behavior.

**Dependencies**
- Internal: core-domain module APIs, extension-ports module APIs, com.authord.mkdocs.core.topic.PathPolicy, com.authord.mkdocs.ports.topic.AddChildTopicNodeCommand, com.authord.mkdocs.ports.topic.AddExistingFileTopicNodeCommand, com.authord.mkdocs.ports.topic.AddExternalLinkTopicNodeCommand
- External: java.util.UUID

**Responsibilities**
- Handle IDE events/actions and delegate work to application services.
- Keep interaction-side concerns separate from core business logic.
- Respect IntelliJ lifecycle/disposable boundaries when managing UI state.

**Key Functionalities**
- Primary declarations: `TopicTreeActionController`.
- Implements key methods/functions including `createTopic`, `addChildTopic`, `addExistingFile`, `addExternalLink`, `renameTopic`, `removeTopic`.
- Interacts with IntelliJ services/actions/tool windows for plugin UX flows.

## TopicTreeApplicationService.kt

**File Path**
`modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/intellij/TopicTreeApplicationService.kt`

**What it does**
Implements `TopicTreeApplicationService` as a service in the `ui-plugin` module.

**Why it matters**
It concentrates reusable business logic so multiple callers can share consistent behavior.

**Dependencies**
- Internal: extension-ports module APIs, com.authord.mkdocs.ports.topic.TopicGatewayResult, com.authord.mkdocs.ports.topic.TopicInstanceRef, com.authord.mkdocs.ports.topic.MkDocsConfigDocument, com.authord.mkdocs.ports.topic.TopicSyncOutcome
- External: Kotlin/Java standard library only.

**Responsibilities**
- Coordinate workflow steps and state transitions for its feature area.
- Encapsulate reusable logic so UI/actions can call a single abstraction.
- Respect IntelliJ lifecycle/disposable boundaries when managing UI state.

**Key Functionalities**
- Primary declarations: `TopicTreeApplicationService`.
- Implements key methods/functions including `execute`, `rollback`, `activeInstance`, `hydrateTreeFromConfig`.
- Interacts with IntelliJ services/actions/tool windows for plugin UX flows.

## TopicTreeApplicationServiceImpl.kt

**File Path**
`modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/intellij/TopicTreeApplicationServiceImpl.kt`

**What it does**
Implements `TopicTreeApplicationServiceImpl` as a service in the `ui-plugin` module.

**Why it matters**
It concentrates reusable business logic so multiple callers can share consistent behavior.

**Dependencies**
- Internal: extension-ports module APIs, com.authord.mkdocs.ports.topic.InstanceRegistryPort, com.authord.mkdocs.ports.topic.MkDocsConfigDocument, com.authord.mkdocs.ports.topic.TopicGatewayResult, com.authord.mkdocs.ports.topic.TopicInstanceRef
- External: Kotlin/Java standard library only.

**Responsibilities**
- Coordinate workflow steps and state transitions for its feature area.
- Encapsulate reusable logic so UI/actions can call a single abstraction.
- Respect IntelliJ lifecycle/disposable boundaries when managing UI state.

**Key Functionalities**
- Primary declarations: `TopicTreeApplicationServiceImpl`.
- Focuses on type/contract definitions more than executable method logic.
- Interacts with IntelliJ services/actions/tool windows for plugin UX flows.

## TopicTreeDragDropController.kt

**File Path**
`modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/intellij/TopicTreeDragDropController.kt`

**What it does**
Implements `TopicTreeDragDropController` as a component in the `ui-plugin` module.

**Why it matters**
This file drives IntelliJ plugin user experience, lifecycle orchestration, or UI integration.

**Dependencies**
- Internal: extension-ports module APIs, com.authord.mkdocs.ports.topic.MoveTopicNodeCommand, com.authord.mkdocs.ports.topic.ReorderTopicNodesCommand, com.authord.mkdocs.ports.topic.ReparentTopicNodeCommand, com.authord.mkdocs.ports.topic.TopicGatewayResult
- External: java.util.UUID

**Responsibilities**
- Provide feature-specific implementation logic for this module area.
- Integrate with neighboring services/components to deliver end behavior.
- Respect IntelliJ lifecycle/disposable boundaries when managing UI state.

**Key Functionalities**
- Primary declarations: `TopicTreeDragDropController`.
- Implements key methods/functions including `moveTopic`, `reparentTopic`, `reorderTopics`.
- Interacts with IntelliJ services/actions/tool windows for plugin UX flows.

## TopicTreeFailureRecoveryPresenter.kt

**File Path**
`modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/intellij/TopicTreeFailureRecoveryPresenter.kt`

**What it does**
Implements `TopicTreeFailureRecoveryPresenter` as a component in the `ui-plugin` module.

**Why it matters**
This file drives IntelliJ plugin user experience, lifecycle orchestration, or UI integration.

**Dependencies**
- Internal: extension-ports module APIs, com.authord.mkdocs.ports.topic.TopicGatewayResult, com.authord.mkdocs.ports.topic.TopicSyncError, com.authord.mkdocs.ports.topic.TopicSyncErrorCode, com.authord.mkdocs.ports.topic.TopicSyncOutcome
- External: Kotlin/Java standard library only.

**Responsibilities**
- Provide feature-specific implementation logic for this module area.
- Integrate with neighboring services/components to deliver end behavior.
- Respect IntelliJ lifecycle/disposable boundaries when managing UI state.

**Key Functionalities**
- Primary declarations: `TopicTreeRecoveryMessage`, `TopicMutationDispatchResult`, `TopicTreeFailureRecoveryPresenter`.
- Implements key methods/functions including `present`.
- Interacts with IntelliJ services/actions/tool windows for plugin UX flows.

## TopicTreeFallbackBuilder.kt

**File Path**
`modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/intellij/TopicTreeFallbackBuilder.kt`

**What it does**
Implements `TopicTreeFallbackBuilder` as a component in the `ui-plugin` module.

**Why it matters**
This file drives IntelliJ plugin user experience, lifecycle orchestration, or UI integration.

**Dependencies**
- Internal: core-domain module APIs, extension-ports module APIs, com.authord.mkdocs.core.topic.PathPolicy, com.authord.mkdocs.ports.topic.TopicNavNode
- External: Kotlin/Java standard library only.

**Responsibilities**
- Provide feature-specific implementation logic for this module area.
- Integrate with neighboring services/components to deliver end behavior.
- Respect IntelliJ lifecycle/disposable boundaries when managing UI state.

**Key Functionalities**
- Primary declarations: `TopicTreeFallbackBuilder`, `MutableItem`, `MutablePage`, `MutableSection`.
- Implements key methods/functions including `build`, `toNavNode`.
- Interacts with IntelliJ services/actions/tool windows for plugin UX flows.

## TopicTreeObservability.kt

**File Path**
`modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/intellij/TopicTreeObservability.kt`

**What it does**
Implements `TopicTreeObservability` as a component in the `ui-plugin` module.

**Why it matters**
This file drives IntelliJ plugin user experience, lifecycle orchestration, or UI integration.

**Dependencies**
- Internal: extension-ports module APIs, com.authord.mkdocs.ports.topic.TopicSyncEvent, com.authord.mkdocs.ports.topic.TopicSyncEventType
- External: Kotlin/Java standard library only.

**Responsibilities**
- Provide feature-specific implementation logic for this module area.
- Integrate with neighboring services/components to deliver end behavior.
- Respect IntelliJ lifecycle/disposable boundaries when managing UI state.

**Key Functionalities**
- Primary declarations: `TopicTreeObservability`.
- Implements key methods/functions including `emitStartupReconciliation`, `emitWatcherTrigger`, `emitValidationReport`, `emitCompatibilityGate`.
- Interacts with IntelliJ services/actions/tool windows for plugin UX flows.

## TopicTreeScopeGuard.kt

**File Path**
`modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/intellij/TopicTreeScopeGuard.kt`

**What it does**
Implements `TopicTreeScopeGuard` as a policy/classification component in the `ui-plugin` module.

**Why it matters**
It codifies decision rules that shape runtime behavior and error handling.

**Dependencies**
- Internal: extension-ports module APIs, com.authord.mkdocs.ports.topic.DefaultTopicSyncError, com.authord.mkdocs.ports.topic.InstanceRegistryPort, com.authord.mkdocs.ports.topic.TopicGatewayResult, com.authord.mkdocs.ports.topic.TopicInstanceRef
- External: Kotlin/Java standard library only.

**Responsibilities**
- Provide feature-specific implementation logic for this module area.
- Integrate with neighboring services/components to deliver end behavior.
- Respect IntelliJ lifecycle/disposable boundaries when managing UI state.

**Key Functionalities**
- Primary declarations: `TopicTreeScopeGuard`.
- Implements key methods/functions including `ensureCommandScope`.
- Interacts with IntelliJ services/actions/tool windows for plugin UX flows.

## TopicTreeStartupLoader.kt

**File Path**
`modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/intellij/TopicTreeStartupLoader.kt`

**What it does**
Implements `TopicTreeStartupLoader` as a component in the `ui-plugin` module.

**Why it matters**
This file drives IntelliJ plugin user experience, lifecycle orchestration, or UI integration.

**Dependencies**
- Internal: core-domain module APIs, extension-ports module APIs, mkdocs-runtime-adapter APIs, com.authord.mkdocs.core.topic.PathPolicy, com.authord.mkdocs.core.topic.TopicTreeValidationIssue, com.authord.mkdocs.core.topic.TopicTreeValidationService, com.authord.mkdocs.ports.topic.MkDocsConfigDocument
- External: Java NIO, java.nio.file.Files, java.nio.file.Path

**Responsibilities**
- Provide feature-specific implementation logic for this module area.
- Integrate with neighboring services/components to deliver end behavior.
- Respect IntelliJ lifecycle/disposable boundaries when managing UI state.

**Key Functionalities**
- Primary declarations: `StartupTreeSource`, `StartupTreeState`, `TopicTreeStartupLoader`.
- Implements key methods/functions including `load`, `visit`, `resolveNode`.
- Interacts with IntelliJ services/actions/tool windows for plugin UX flows.

## TopicTreeSyncOrchestratorService.kt

**File Path**
`modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/intellij/TopicTreeSyncOrchestratorService.kt`

**What it does**
Implements `TopicTreeSyncOrchestratorService` as a service in the `ui-plugin` module.

**Why it matters**
It concentrates reusable business logic so multiple callers can share consistent behavior.

**Dependencies**
- Internal: extension-ports module APIs, mkdocs-runtime-adapter APIs, com.authord.mkdocs.ports.TopicTreePort, com.authord.mkdocs.ports.topic.AddChildTopicNodeCommand, com.authord.mkdocs.ports.topic.AddExistingFileTopicNodeCommand, com.authord.mkdocs.ports.topic.AddExternalLinkTopicNodeCommand
- External: Java NIO, java.nio.file.Files, java.nio.file.Path, java.util.ArrayDeque, java.util.concurrent.ConcurrentHashMap

**Responsibilities**
- Coordinate workflow steps and state transitions for its feature area.
- Encapsulate reusable logic so UI/actions can call a single abstraction.
- Respect IntelliJ lifecycle/disposable boundaries when managing UI state.

**Key Functionalities**
- Primary declarations: `TopicTreeSyncOrchestratorService`, `ConfigMutationResult`, `NavNodeContext`.
- Implements key methods/functions including `collect`, `rebind`, `visit`.
- Interacts with IntelliJ services/actions/tool windows for plugin UX flows.

## TopicTreeUiService.kt

**File Path**
`modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/intellij/TopicTreeUiService.kt`

**What it does**
Implements `TopicTreeUiService` as a service in the `ui-plugin` module.

**Why it matters**
It concentrates reusable business logic so multiple callers can share consistent behavior.

**Dependencies**
- Internal: extension-ports module APIs, com.authord.mkdocs.ports.topic.TopicGatewayResult, com.authord.mkdocs.ports.topic.TopicInstanceRef, com.authord.mkdocs.ports.topic.TopicSyncOutcome, com.authord.mkdocs.ports.topic.TopicTreeCommand
- External: Kotlin/Java standard library only.

**Responsibilities**
- Coordinate workflow steps and state transitions for its feature area.
- Encapsulate reusable logic so UI/actions can call a single abstraction.
- Respect IntelliJ lifecycle/disposable boundaries when managing UI state.

**Key Functionalities**
- Primary declarations: `TopicTreeUiService`.
- Implements key methods/functions including `dispatch`, `refreshActiveTree`, `selectInstance`.
- Interacts with IntelliJ services/actions/tool windows for plugin UX flows.

## TopicTreeUiServiceImpl.kt

**File Path**
`modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/intellij/TopicTreeUiServiceImpl.kt`

**What it does**
Implements `TopicTreeUiServiceImpl` as a service in the `ui-plugin` module.

**Why it matters**
It concentrates reusable business logic so multiple callers can share consistent behavior.

**Dependencies**
- Internal: extension-ports module APIs, com.authord.mkdocs.ports.topic.DefaultTopicSyncError, com.authord.mkdocs.ports.topic.InstanceRegistryPort, com.authord.mkdocs.ports.topic.MkDocsConfigDocument, com.authord.mkdocs.ports.topic.TopicGatewayResult
- External: Kotlin/Java standard library only.

**Responsibilities**
- Coordinate workflow steps and state transitions for its feature area.
- Encapsulate reusable logic so UI/actions can call a single abstraction.
- Respect IntelliJ lifecycle/disposable boundaries when managing UI state.

**Key Functionalities**
- Primary declarations: `TopicTreeUiServiceImpl`.
- Focuses on type/contract definitions more than executable method logic.
- Interacts with IntelliJ services/actions/tool windows for plugin UX flows.

## TopicTreeWatcherCoordinator.kt

**File Path**
`modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/intellij/TopicTreeWatcherCoordinator.kt`

**What it does**
Implements `TopicTreeWatcherCoordinator` as a workflow coordinator in the `ui-plugin` module.

**Why it matters**
It centralizes cross-service sequencing so lifecycle transitions remain predictable and recoverable.

**Dependencies**
- Internal: core-domain module APIs, com.authord.mkdocs.core.topic.PathPolicy
- External: Kotlin/Java standard library only.

**Responsibilities**
- Coordinate workflow steps and state transitions for its feature area.
- Encapsulate reusable logic so UI/actions can call a single abstraction.
- Respect IntelliJ lifecycle/disposable boundaries when managing UI state.

**Key Functionalities**
- Primary declarations: `WatcherEventKind`, `TopicTreeFileChange`, `WatcherFollowUp`, `WatcherDecision`, plus `1` more.
- Implements key methods/functions including `evaluate`.
- Interacts with IntelliJ services/actions/tool windows for plugin UX flows.

## TopicTreeWorkspacePanel.kt

**File Path**
`modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/intellij/TopicTreeWorkspacePanel.kt`

**What it does**
Implements `TopicTreeWorkspacePanel` as a UI component in the `ui-plugin` module.

**Why it matters**
It defines user-visible interface behavior and therefore impacts usability and interaction flow.

**Dependencies**
- Internal: extension-ports module APIs, ui-plugin APIs, com.authord.mkdocs.ports.topic.TopicGatewayResult, com.authord.mkdocs.ports.topic.TopicNavNode, com.authord.mkdocs.ports.topic.TopicSyncErrorCode, com.authord.mkdocs.ui.PluginCompositionRoot
- External: IntelliJ Platform SDK, Java NIO, com.intellij.icons.AllIcons, com.intellij.notification.NotificationType, com.intellij.openapi.actionSystem.ActionManager, com.intellij.openapi.actionSystem.AnActionEvent

**Responsibilities**
- Provide feature-specific implementation logic for this module area.
- Integrate with neighboring services/components to deliver end behavior.
- Respect IntelliJ lifecycle/disposable boundaries when managing UI state.

**Key Functionalities**
- Primary declarations: `TopicTreeNodeKind`, `TopicTreeNodeView`, `TopicTreeNodeTransferable`, `TopicTreeWorkspacePanel`, plus `3` more.
- Implements key methods/functions including `root`, `render`, `reconcileFromDisk`, `visit`.
- Interacts with IntelliJ services/actions/tool windows for plugin UX flows.

## UnlinkedFilesBucketService.kt

**File Path**
`modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/intellij/UnlinkedFilesBucketService.kt`

**What it does**
Implements `UnlinkedFilesBucketService` as a service in the `ui-plugin` module.

**Why it matters**
It concentrates reusable business logic so multiple callers can share consistent behavior.

**Dependencies**
- Internal: core-domain module APIs, extension-ports module APIs, com.authord.mkdocs.core.topic.PathPolicy, com.authord.mkdocs.ports.topic.TopicNavNode
- External: Kotlin/Java standard library only.

**Responsibilities**
- Coordinate workflow steps and state transitions for its feature area.
- Encapsulate reusable logic so UI/actions can call a single abstraction.
- Respect IntelliJ lifecycle/disposable boundaries when managing UI state.

**Key Functionalities**
- Primary declarations: `UnlinkedFilesBucketService`.
- Implements key methods/functions including `derive`, `visit`.
- Interacts with IntelliJ services/actions/tool windows for plugin UX flows.

## ActivationErrorPresenterTest.kt

**File Path**
`modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/ActivationErrorPresenterTest.kt`

**What it does**
Defines a unit test suite for `ActivationError` by exercising expected behavior and edge cases.

**Why it matters**
It provides fast regression coverage for local logic so changes can be made safely.

**Dependencies**
- Internal: ui-plugin local package types
- External: Kotlin Test

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Detect regressions in business logic, edge cases, and guard conditions.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `renders message per failure reason`.
- Test case: `includes details when provided`.

## DocsExplorerServiceTest.kt

**File Path**
`modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/DocsExplorerServiceTest.kt`

**What it does**
Defines a unit test suite for `DocsExplorer` by exercising expected behavior and edge cases.

**Why it matters**
It provides fast regression coverage for local logic so changes can be made safely.

**Dependencies**
- Internal: ui-plugin local package types
- External: Kotlin Test, Java NIO, java.nio.file.Files

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Detect regressions in business logic, edge cases, and guard conditions.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `discovers markdown files under docs root only`.
- Test case: `returns empty list when docs directory does not exist`.

## DocsFileSelectionPublisherTest.kt

**File Path**
`modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/DocsFileSelectionPublisherTest.kt`

**What it does**
Defines a unit test suite for `DocsFileSelection` by exercising expected behavior and edge cases.

**Why it matters**
It provides fast regression coverage for local logic so changes can be made safely.

**Dependencies**
- Internal: ui-plugin local package types
- External: Kotlin Test

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Detect regressions in business logic, edge cases, and guard conditions.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `publishes selected file events to subscribers`.

## FeatureFlagPolicyServiceTest.kt

**File Path**
`modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/FeatureFlagPolicyServiceTest.kt`

**What it does**
Defines a unit test suite for `FeatureFlagPolicy` by exercising expected behavior and edge cases.

**Why it matters**
It provides fast regression coverage for local logic so changes can be made safely.

**Dependencies**
- Internal: core-domain module APIs, com.authord.mkdocs.core.flags.FeatureFlagPolicy
- External: Kotlin Test

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Detect regressions in business logic, edge cases, and guard conditions.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `current and update expose active policy`.

## NavigationCoordinatorTest.kt

**File Path**
`modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/NavigationCoordinatorTest.kt`

**What it does**
Defines a unit test suite for `Navigation` by exercising expected behavior and edge cases.

**Why it matters**
It provides fast regression coverage for local logic so changes can be made safely.

**Dependencies**
- Internal: core-domain module APIs, com.authord.mkdocs.core.navigation.RouteMappingService
- External: Kotlin Test

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Detect regressions in business logic, edge cases, and guard conditions.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `applies mapped route to preview`.
- Test case: `returns failure when mapping cannot be resolved`.

## OutOfScopeGuardrailsTest.kt

**File Path**
`modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/OutOfScopeGuardrailsTest.kt`

**What it does**
Defines a unit test suite for `OutOfScope` by exercising expected behavior and edge cases.

**Why it matters**
It provides fast regression coverage for local logic so changes can be made safely.

**Dependencies**
- Internal: core-domain module APIs, com.authord.mkdocs.core.flags.FeatureFlagPolicy
- External: Kotlin Test

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Detect regressions in business logic, edge cases, and guard conditions.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `default policy enforces mvp-only behavior`.
- Test case: `future-cycle flags disable mvp-only guardrail`.

## PluginActivationServiceErrorTest.kt

**File Path**
`modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/PluginActivationServiceErrorTest.kt`

**What it does**
Defines a unit test suite for `PluginActivationServiceError` by exercising expected behavior and edge cases.

**Why it matters**
It provides fast regression coverage for local logic so changes can be made safely.

**Dependencies**
- Internal: core-domain module APIs, mkdocs-runtime-adapter APIs, ui-plugin APIs, com.authord.mkdocs.core.flags.FeatureFlagPolicy, com.authord.mkdocs.runtime.BaseUrlDetector, com.authord.mkdocs.runtime.ManagedProcessHandle, com.authord.mkdocs.runtime.MkdocsProcessManager
- External: JUnit 5, Java NIO, org.junit.jupiter.api.Assertions.assertEquals, org.junit.jupiter.api.Test, java.nio.file.Path

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Detect regressions in business logic, edge cases, and guard conditions.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `test startFailureDetails returns git error message`.
- Test case: `test startFailureDetails returns git command error message`.
- Test case: `test startFailureDetails returns module missing message`.

## PluginActivationServiceTest.kt

**File Path**
`modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/PluginActivationServiceTest.kt`

**What it does**
Defines a unit test suite for `PluginActivation` by exercising expected behavior and edge cases.

**Why it matters**
It provides fast regression coverage for local logic so changes can be made safely.

**Dependencies**
- Internal: core-domain module APIs, mkdocs-runtime-adapter APIs, com.authord.mkdocs.core.flags.FeatureFlagPolicy, com.authord.mkdocs.runtime.BaseUrlDetector, com.authord.mkdocs.runtime.CommandResult, com.authord.mkdocs.runtime.ManagedProcessHandle
- External: Kotlin Test, Java NIO, java.nio.file.Files, java.nio.file.Path

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Detect regressions in business logic, edge cases, and guard conditions.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `fails when mvp flow is disabled`.
- Test case: `fails when bootstrap fails`.
- Test case: `fails when process start returns not started`.
- Test case: `includes startup output details when process start fails`.
- Test case: `fails fast with mkdocs config hint when config is missing`.
- Additional `11` test cases expand scenario coverage.

## PluginCompositionRootCoverageTest.kt

**File Path**
`modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/PluginCompositionRootCoverageTest.kt`

**What it does**
Defines a unit test suite for `PluginCompositionRoot` by exercising expected behavior and edge cases.

**Why it matters**
It provides fast regression coverage for local logic so changes can be made safely.

**Dependencies**
- Internal: core-domain module APIs, extension-ports module APIs, ui-plugin APIs, com.authord.mkdocs.core.topic.TopicTreeMutationService, com.authord.mkdocs.ports.topic.DefaultTopicSyncError, com.authord.mkdocs.ports.topic.InstanceRegistryPort, com.authord.mkdocs.ports.topic.TopicDeleteMode
- External: Kotlin Test, Java NIO, java.nio.file.Files, java.nio.file.Path

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Detect regressions in business logic, edge cases, and guard conditions.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `default no-op gateways and orchestrator return unsupported failures`.
- Test case: `default application service delegates execute rollback and active-instance lookup`.
- Test case: `instance registry service covers discovery registration and selection branches`.
- Test case: `default ui service dispatch refresh and instance-selection branches are covered`.

## PluginCompositionRootTest.kt

**File Path**
`modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/PluginCompositionRootTest.kt`

**What it does**
Defines a unit test suite for `PluginCompositionRoot` by exercising expected behavior and edge cases.

**Why it matters**
It provides fast regression coverage for local logic so changes can be made safely.

**Dependencies**
- Internal: core-domain module APIs, extension-ports module APIs, ui-plugin APIs, com.authord.mkdocs.core.topic.TopicTreeMutationService, com.authord.mkdocs.ports.topic.AddTopicNodeCommand, com.authord.mkdocs.ui.intellij.IntellijTestFixtures, com.authord.mkdocs.ui.intellij.PluginRuntimeIntegrationService
- External: Kotlin Test

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Detect regressions in business logic, edge cases, and guard conditions.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `wires command bus and default adapters`.
- Test case: `resolves project runtime integration service through composition root`.

## PreviewPaneCoordinatorTest.kt

**File Path**
`modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/PreviewPaneCoordinatorTest.kt`

**What it does**
Defines a unit test suite for `PreviewPane` by exercising expected behavior and edge cases.

**Why it matters**
It provides fast regression coverage for local logic so changes can be made safely.

**Dependencies**
- Internal: ui-plugin local package types
- External: Kotlin Test

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Detect regressions in business logic, edge cases, and guard conditions.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `opens side-by-side preview and tracks active url`.
- Test case: `returns null for missing session and keeps root url stable`.
- Test case: `normalizes route without leading or trailing slash`.
- Test case: `preserves html route without appending trailing slash`.
- Test case: `handles edge cases in url normalization`.

## AuthordMarkdownOpenPreviewListenerTest.kt

**File Path**
`modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/intellij/AuthordMarkdownOpenPreviewListenerTest.kt`

**What it does**
Defines a unit test suite for `AuthordMarkdownOpenPreview` by exercising expected behavior and edge cases.

**Why it matters**
It provides fast regression coverage for local logic so changes can be made safely.

**Dependencies**
- Internal: core-domain module APIs, com.authord.mkdocs.core.flags.FeatureFlagPolicy
- External: IntelliJ Platform SDK, Kotlin Test, Java NIO, com.intellij.openapi.project.Project, java.nio.file.Files

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Detect regressions in business logic, edge cases, and guard conditions.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `docs markdown open activates browser service and starts runtime`.
- Test case: `non-docs markdown open is ignored`.
- Test case: `listener respects auto open preview setting`.
- Test case: `docs startup failure applies bypass and notifies`.

## AuthordMarkdownSplitEditorProviderTest.kt

**File Path**
`modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/intellij/AuthordMarkdownSplitEditorProviderTest.kt`

**What it does**
Defines a unit test suite for `AuthordMarkdownSplitEditor` by exercising expected behavior and edge cases.

**Why it matters**
It provides fast regression coverage for local logic so changes can be made safely.

**Dependencies**
- Internal: ui-plugin local package types
- External: IntelliJ Platform SDK, Kotlin Test, Java NIO, com.intellij.openapi.fileEditor.TextEditorWithPreview, com.intellij.testFramework.LightVirtualFile, java.nio.file.Files, javax.swing.JPanel

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Detect regressions in business logic, edge cases, and guard conditions.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `split preview eligibility requires mkdocs config`.
- Test case: `split preview eligibility rejects nested config that does not scope selected markdown`.
- Test case: `split preview eligibility rejects markdown outside the project root`.
- Test case: `split preview eligibility accepts docs scoped markdown only`.
- Test case: `refresh suppresses missing config notification when auto start is disabled`.
- Additional `6` test cases expand scenario coverage.

## AuthordPreviewSettingsServiceTest.kt

**File Path**
`modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/intellij/AuthordPreviewSettingsServiceTest.kt`

**What it does**
Defines a unit test suite for `AuthordPreviewSettings` by exercising expected behavior and edge cases.

**Why it matters**
It provides fast regression coverage for local logic so changes can be made safely.

**Dependencies**
- Internal: ui-plugin local package types
- External: Kotlin Test

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Detect regressions in business logic, edge cases, and guard conditions.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `auto-open setting defaults to true`.
- Test case: `auto-open setting can be disabled through state load`.

## AuthordSplitEditorLayoutStateServiceTest.kt

**File Path**
`modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/intellij/AuthordSplitEditorLayoutStateServiceTest.kt`

**What it does**
Defines a unit test suite for `AuthordSplitEditorLayoutState` by exercising expected behavior and edge cases.

**Why it matters**
It provides fast regression coverage for local logic so changes can be made safely.

**Dependencies**
- Internal: ui-plugin local package types
- External: IntelliJ Platform SDK, Kotlin Test, com.intellij.openapi.fileEditor.TextEditorWithPreview

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Detect regressions in business logic, edge cases, and guard conditions.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `service defaults to editor and preview layout`.
- Test case: `service stores and resolves preferred layout`.
- Test case: `service falls back to default for invalid persisted layout`.

## DocsScopeResolverTest.kt

**File Path**
`modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/intellij/DocsScopeResolverTest.kt`

**What it does**
Defines a unit test suite for `DocsScopeResolver` by exercising expected behavior and edge cases.

**Why it matters**
It provides fast regression coverage for local logic so changes can be made safely.

**Dependencies**
- Internal: ui-plugin local package types
- External: Kotlin Test, Java NIO, java.nio.file.Files

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Detect regressions in business logic, edge cases, and guard conditions.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `docs scope resolver marks docs markdown as docs scoped`.
- Test case: `docs scope resolver marks non docs markdown as non docs`.
- Test case: `docs scope resolver marks tied docs roots as ambiguous`.
- Test case: `docs scope resolver marks invalid docs dir as misconfigured`.

## ExternalMoveHandlingTest.kt

**File Path**
`modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/intellij/ExternalMoveHandlingTest.kt`

**What it does**
Defines a unit test suite for `ExternalMoveHandling` by exercising expected behavior and edge cases.

**Why it matters**
It provides fast regression coverage for local logic so changes can be made safely.

**Dependencies**
- Internal: ui-plugin local package types
- External: Kotlin Test

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Detect regressions in business logic, edge cases, and guard conditions.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `inside docs_dir moved outside triggers broken reference validation without destructive action`.
- Test case: `outside docs_dir moved inside creates unlinked file follow-up`.
- Test case: `rename or move wholly outside project root is ignored`.

## InstanceRegistryDiscoveryTest.kt

**File Path**
`modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/intellij/InstanceRegistryDiscoveryTest.kt`

**What it does**
Defines a unit test suite for `InstanceRegistry` by exercising expected behavior and edge cases.

**Why it matters**
It provides fast regression coverage for local logic so changes can be made safely.

**Dependencies**
- Internal: extension-ports module APIs, com.authord.mkdocs.ports.topic.TopicGatewayResult
- External: Kotlin Test, Java NIO, java.nio.file.Path

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Detect regressions in business logic, edge cases, and guard conditions.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `discovers root mkdocs yml as default instance`.
- Test case: `registers additional instances only through explicit add instance flow`.

## InstanceScopeIsolationTest.kt

**File Path**
`modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/intellij/InstanceScopeIsolationTest.kt`

**What it does**
Defines a unit test suite for `InstanceScopeIsolation` by exercising expected behavior and edge cases.

**Why it matters**
It provides fast regression coverage for local logic so changes can be made safely.

**Dependencies**
- Internal: extension-ports module APIs, com.authord.mkdocs.ports.topic.AddTopicNodeCommand, com.authord.mkdocs.ports.topic.DefaultTopicSyncError, com.authord.mkdocs.ports.topic.InstanceRegistryPort, com.authord.mkdocs.ports.topic.TopicGatewayResult
- External: Kotlin Test

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Detect regressions in business logic, edge cases, and guard conditions.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `scope guard allows mutation only for active instance`.

## InstanceSelectionPersistenceTest.kt

**File Path**
`modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/intellij/InstanceSelectionPersistenceTest.kt`

**What it does**
Defines a unit test suite for `InstanceSelectionPersistence` by exercising expected behavior and edge cases.

**Why it matters**
It provides fast regression coverage for local logic so changes can be made safely.

**Dependencies**
- Internal: extension-ports module APIs, com.authord.mkdocs.ports.topic.TopicGatewayResult, com.authord.mkdocs.ports.topic.TopicInstanceRef
- External: Kotlin Test

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Detect regressions in business logic, edge cases, and guard conditions.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `last selected instance is restored when reopening same project`.

## IntellijTestFixtures.kt

**File Path**
`modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/intellij/IntellijTestFixtures.kt`

**What it does**
Defines a unit test suite for `IntellijTestFixtures` by exercising expected behavior and edge cases.

**Why it matters**
It provides fast regression coverage for local logic so changes can be made safely.

**Dependencies**
- Internal: ui-plugin local package types
- External: IntelliJ Platform SDK, Java NIO, com.intellij.openapi.actionSystem.ActionPlaces, com.intellij.openapi.actionSystem.ActionManager, com.intellij.openapi.actionSystem.ActionPopupMenu, com.intellij.openapi.actionSystem.ActionToolbar

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Detect regressions in business logic, edge cases, and guard conditions.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `actionEvent`.
- Test case: `project`.
- Test case: `toolWindowFixture`.

## MkDocsPreviewBrowserServiceTest.kt

**File Path**
`modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/intellij/MkDocsPreviewBrowserServiceTest.kt`

**What it does**
Defines a unit test suite for `MkDocsPreviewBrowser` by exercising expected behavior and edge cases.

**Why it matters**
It provides fast regression coverage for local logic so changes can be made safely.

**Dependencies**
- Internal: ui-plugin local package types
- External: Kotlin Test, javax.swing.JPanel

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Detect regressions in business logic, edge cases, and guard conditions.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `ensurePreviewContent returns a single instance per project service`.
- Test case: `markMarkdownActivated toggles activation and initializes preview surface`.
- Test case: `split owner takes preview component ownership and tool window regains it after split deactivation`.
- Test case: `shared browser deduplicates same route across owners but still navigates changed routes`.
- Test case: `force reload bypasses shared last url dedupe`.
- Additional `3` test cases expand scenario coverage.

## MkDocsProjectCreatorTest.kt

**File Path**
`modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/intellij/MkDocsProjectCreatorTest.kt`

**What it does**
Defines a unit test suite for `MkDocsProjectCreator` by exercising expected behavior and edge cases.

**Why it matters**
It provides fast regression coverage for local logic so changes can be made safely.

**Dependencies**
- Internal: extension-ports module APIs, mkdocs-runtime-adapter APIs, com.authord.mkdocs.ports.topic.TopicGatewayResult, com.authord.mkdocs.ports.topic.TopicInstanceRef, com.authord.mkdocs.runtime.CommandResult, com.authord.mkdocs.runtime.CommandRunner
- External: Kotlin Test, Java NIO, java.nio.file.Files

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Detect regressions in business logic, edge cases, and guard conditions.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `createProject resolves uv writes authord scaffold and nav in project root`.
- Test case: `createProject fails when project name is blank`.
- Test case: `createProject fails when uv resolution fails`.

## MkdocsConfigLocatorTest.kt

**File Path**
`modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/intellij/MkdocsConfigLocatorTest.kt`

**What it does**
Defines a unit test suite for `MkdocsConfigLocator` by exercising expected behavior and edge cases.

**Why it matters**
It provides fast regression coverage for local logic so changes can be made safely.

**Dependencies**
- Internal: ui-plugin local package types
- External: Kotlin Test, Java NIO, java.nio.file.Files

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Detect regressions in business logic, edge cases, and guard conditions.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `clearCacheBeforeEach`.
- Test case: `clearCacheAfterEach`.
- Test case: `findMkdocsConfig resolves project root config and prefers mkdocs yml over yaml`.
- Test case: `findMkdocsConfig discovers nested config when root config is absent`.
- Test case: `findMkdocsConfig supports underscore variants and prefers shallower config`.
- Additional `2` test cases expand scenario coverage.

## MkdocsScrollSyncEngineTest.kt

**File Path**
`modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/intellij/MkdocsScrollSyncEngineTest.kt`

**What it does**
Defines a unit test suite for `MkdocsScrollSyncEngine` by exercising expected behavior and edge cases.

**Why it matters**
It provides fast regression coverage for local logic so changes can be made safely.

**Dependencies**
- Internal: ui-plugin local package types
- External: IntelliJ Platform SDK, Kotlin Test, com.intellij.openapi.editor.event.DocumentEvent, com.intellij.openapi.editor.impl.DocumentImpl

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Detect regressions in business logic, edge cases, and guard conditions.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `source extraction omits markdown comments while preserving fenced code context`.
- Test case: `hard heading matching prefers stable heading ids with occurrences`.
- Test case: `global non crossing hard matching avoids A B A vs B A top to bottom jump`.
- Test case: `post dp acceptance guard drops ambiguous neighborhood matches`.
- Test case: `onEditorScroll requests dom snapshot once and reuses cached map for following scrolls`.
- Additional `5` test cases expand scenario coverage.

## MkdocsToolWindowFactoryTest.kt

**File Path**
`modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/intellij/MkdocsToolWindowFactoryTest.kt`

**What it does**
Defines a unit test suite for `MkdocsToolWindow` by exercising expected behavior and edge cases.

**Why it matters**
It provides fast regression coverage for local logic so changes can be made safely.

**Dependencies**
- Internal: mkdocs-runtime-adapter APIs, com.authord.mkdocs.runtime.CommandResult, com.authord.mkdocs.runtime.UvExecutableResult
- External: IntelliJ Platform SDK, Kotlin Test, Java NIO, com.intellij.openapi.Disposable, com.intellij.ui.OnePixelSplitter, com.intellij.openapi.editor.impl.DocumentImpl, com.intellij.openapi.vfs.VirtualFile

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Detect regressions in business logic, edge cases, and guard conditions.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `loadedUrls`.
- Test case: `scrolledYValues`.
- Test case: `scrollSyncTokens`.
- Test case: `setupPageLoadCount`.
- Test case: `triggerSetupProjectCreate`.
- Additional `40` test cases expand scenario coverage.

## MultiInstanceReconciliationTest.kt

**File Path**
`modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/intellij/MultiInstanceReconciliationTest.kt`

**What it does**
Defines a unit test suite for `MultiInstanceReconciliation` by exercising expected behavior and edge cases.

**Why it matters**
It provides fast regression coverage for local logic so changes can be made safely.

**Dependencies**
- Internal: extension-ports module APIs, com.authord.mkdocs.ports.topic.MkDocsConfigDocument, com.authord.mkdocs.ports.topic.TopicInstanceRef, com.authord.mkdocs.ports.topic.TopicNavNode
- External: Kotlin Test

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Detect regressions in business logic, edge cases, and guard conditions.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `reconciliation runs per instance with nav-first isolation`.
- Test case: `missing nav file remains validation issue without destructive changes`.

## MutationFeedbackPerformanceTest.kt

**File Path**
`modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/intellij/MutationFeedbackPerformanceTest.kt`

**What it does**
Defines a performance-oriented test suite for `MutationFeedback` by exercising expected behavior and edge cases.

**Why it matters**
It guards operational quality constraints and prevents performance regressions in user-facing flows.

**Dependencies**
- Internal: extension-ports module APIs, com.authord.mkdocs.ports.TopicTreePort, com.authord.mkdocs.ports.topic.DefaultTopicSyncError, com.authord.mkdocs.ports.topic.DocsFileGateway, com.authord.mkdocs.ports.topic.MkDocsConfigDocument
- External: Kotlin Test

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Guard response-time or throughput expectations for critical flows.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `mutation feedback stays within 300ms p95 on local orchestration`.

## MutationFlowIndependenceTest.kt

**File Path**
`modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/intellij/MutationFlowIndependenceTest.kt`

**What it does**
Defines a unit test suite for `MutationFlowIndependence` by exercising expected behavior and edge cases.

**Why it matters**
It provides fast regression coverage for local logic so changes can be made safely.

**Dependencies**
- Internal: extension-ports module APIs, com.authord.mkdocs.ports.topic.DefaultTopicSyncError, com.authord.mkdocs.ports.topic.InstanceRegistryPort, com.authord.mkdocs.ports.topic.TopicGatewayResult, com.authord.mkdocs.ports.topic.TopicInstanceRef
- External: Kotlin Test

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Detect regressions in business logic, edge cases, and guard conditions.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `ui mutation dispatch works with preloaded tree context without startup watcher scaffolding`.
- Test case: `dispatch rejects when no active instance is available`.

## OrchestratorLogicTest.kt

**File Path**
`modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/intellij/OrchestratorLogicTest.kt`

**What it does**
Defines a unit test suite for `OrchestratorLogic` by exercising expected behavior and edge cases.

**Why it matters**
It provides fast regression coverage for local logic so changes can be made safely.

**Dependencies**
- Internal: extension-ports module APIs, com.authord.mkdocs.ports.TopicTreePort, com.authord.mkdocs.ports.topic.*
- External: Kotlin Test

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Detect regressions in business logic, edge cases, and guard conditions.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `apply generates file creation ops for AddChildTopicNodeCommand`.
- Test case: `apply generates file creation ops for AddTopicNodeCommand with null sourcePath (Derivation)`.

## PluginDescriptorRegistrationTest.kt

**File Path**
`modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/intellij/PluginDescriptorRegistrationTest.kt`

**What it does**
Defines a unit test suite for `PluginDescriptorRegistration` by exercising expected behavior and edge cases.

**Why it matters**
It provides fast regression coverage for local logic so changes can be made safely.

**Dependencies**
- Internal: ui-plugin local package types
- External: Kotlin Test, Java NIO, java.nio.file.Files, java.nio.file.Path

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Detect regressions in business logic, edge cases, and guard conditions.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `plugin descriptor registers platform dependency tool window and start action`.
- Test case: `markdown toolbar descriptor registers authord preview action in markdown toolbar`.

## PluginRuntimeIntegrationServiceTest.kt

**File Path**
`modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/intellij/PluginRuntimeIntegrationServiceTest.kt`

**What it does**
Defines a unit test suite for `PluginRuntimeIntegration` by exercising expected behavior and edge cases.

**Why it matters**
It provides fast regression coverage for local logic so changes can be made safely.

**Dependencies**
- Internal: core-domain module APIs, mkdocs-runtime-adapter APIs, ui-plugin APIs, com.authord.mkdocs.runtime.BaseUrlDetector, com.authord.mkdocs.runtime.CommandResult, com.authord.mkdocs.runtime.CommandRunner, com.authord.mkdocs.runtime.ManagedProcessHandle
- External: Kotlin Test, Java NIO, java.nio.file.Files, java.nio.file.attribute.FileTime, java.util.concurrent.TimeUnit, java.util.concurrent.atomic.AtomicInteger

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Detect regressions in business logic, edge cases, and guard conditions.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `startPreview fails when project path is unavailable`.
- Test case: `runtime integration preserves single-instance lifecycle through process manager`.
- Test case: `startPreview does not restart running runtime before first config fingerprint is known`.
- Test case: `startPreview reloads mkdocs config changes by restarting runtime`.
- Test case: `restartPreview restarts runtime process and re-runs activation flow`.
- Additional `23` test cases expand scenario coverage.

## PreviewCompatibilityCriteriaTest.kt

**File Path**
`modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/intellij/PreviewCompatibilityCriteriaTest.kt`

**What it does**
Defines a unit test suite for `PreviewCompatibilityCriteria` by exercising expected behavior and edge cases.

**Why it matters**
It provides fast regression coverage for local logic so changes can be made safely.

**Dependencies**
- Internal: ui-plugin local package types
- External: Kotlin Test

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Detect regressions in business logic, edge cases, and guard conditions.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `classifier maps high-severity rubric categories to high`.
- Test case: `release gate blocks unresolved high-severity compatibility regressions`.
- Test case: `release gate passes when high-severity findings are resolved`.

## PreviewErrorExcerptFormatterTest.kt

**File Path**
`modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/intellij/PreviewErrorExcerptFormatterTest.kt`

**What it does**
Defines a unit test suite for `PreviewErrorExcerpt` by exercising expected behavior and edge cases.

**Why it matters**
It provides fast regression coverage for local logic so changes can be made safely.

**Dependencies**
- Internal: ui-plugin local package types
- External: Kotlin Test

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Detect regressions in business logic, edge cases, and guard conditions.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `extracts stderr tail when available`.
- Test case: `falls back to details section when tails are absent`.
- Test case: `truncates long excerpts`.

## PreviewFailureNotifierTest.kt

**File Path**
`modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/intellij/PreviewFailureNotifierTest.kt`

**What it does**
Defines a unit test suite for `PreviewFailureNotifier` by exercising expected behavior and edge cases.

**Why it matters**
It provides fast regression coverage for local logic so changes can be made safely.

**Dependencies**
- Internal: ui-plugin local package types
- External: Kotlin Test

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Detect regressions in business logic, edge cases, and guard conditions.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `clearDedupeState`.
- Test case: `notification dedupe suppresses repeated identical failures`.

## PreviewNavigationPolicyTest.kt

**File Path**
`modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/intellij/PreviewNavigationPolicyTest.kt`

**What it does**
Defines a unit test suite for `PreviewNavigation` by exercising expected behavior and edge cases.

**Why it matters**
It provides fast regression coverage for local logic so changes can be made safely.

**Dependencies**
- Internal: ui-plugin local package types
- External: Kotlin Test

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Detect regressions in business logic, edge cases, and guard conditions.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `allows local preview urls in main frame`.
- Test case: `blocks non-local main-frame navigation and opens system browser`.
- Test case: `blocks non-local navigation without user gesture and keeps browser in-pane`.
- Test case: `allows non-main-frame navigation to avoid iframe breakage`.
- Test case: `allows browser internal schemes`.

## PreviewResultReporterTest.kt

**File Path**
`modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/intellij/PreviewResultReporterTest.kt`

**What it does**
Defines a unit test suite for `PreviewResultReporter` by exercising expected behavior and edge cases.

**Why it matters**
It provides fast regression coverage for local logic so changes can be made safely.

**Dependencies**
- Internal: ui-plugin APIs, com.authord.mkdocs.ui.ActivationFailureReason, com.authord.mkdocs.ui.ActivationResult
- External: Kotlin Test

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Detect regressions in business logic, edge cases, and guard conditions.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `formats started message for successful activation`.
- Test case: `formats already-running message for successful reuse`.
- Test case: `formats failure message from activation details`.
- Test case: `formats failure message with truncated exact error excerpt`.
- Test case: `formats fallback failure message when activation message is blank`.
- Additional `3` test cases expand scenario coverage.

## PreviewRouteLoadCoordinatorTest.kt

**File Path**
`modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/intellij/PreviewRouteLoadCoordinatorTest.kt`

**What it does**
Defines a unit test suite for `PreviewRouteLoad` by exercising expected behavior and edge cases.

**Why it matters**
It provides fast regression coverage for local logic so changes can be made safely.

**Dependencies**
- Internal: ui-plugin local package types
- External: Kotlin Test

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Detect regressions in business logic, edge cases, and guard conditions.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `loads once when route is immediately ready`.
- Test case: `emits ordered flow states when route resolves successfully`.
- Test case: `loads once after transient 404 window when route eventually becomes ready`.
- Test case: `warns once when route never becomes ready`.
- Test case: `loads html fallback route when directory style route stays unavailable`.
- Additional `3` test cases expand scenario coverage.

## PreviewRouterServiceTest.kt

**File Path**
`modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/intellij/PreviewRouterServiceTest.kt`

**What it does**
Defines a unit test suite for `PreviewRouter` by exercising expected behavior and edge cases.

**Why it matters**
It provides fast regression coverage for local logic so changes can be made safely.

**Dependencies**
- Internal: ui-plugin local package types
- External: Kotlin Test, Java NIO, java.nio.file.Files

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Detect regressions in business logic, edge cases, and guard conditions.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `router routes docs scoped markdown to authord`.
- Test case: `router routes non docs markdown to intellij`.
- Test case: `router routes ambiguous scope markdown to intellij`.
- Test case: `router routes misconfigured scope markdown to intellij`.
- Test case: `router honors failure bypass for docs scoped markdown`.

## PreviewStartupFailureClassifierTest.kt

**File Path**
`modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/intellij/PreviewStartupFailureClassifierTest.kt`

**What it does**
Defines a unit test suite for `PreviewStartupFailure` by exercising expected behavior and edge cases.

**Why it matters**
It provides fast regression coverage for local logic so changes can be made safely.

**Dependencies**
- Internal: ui-plugin local package types
- External: Kotlin Test

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Detect regressions in business logic, edge cases, and guard conditions.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `classifies missing material dependency`.
- Test case: `classifies mkdocs config parse errors`.
- Test case: `classifies early process exit`.
- Test case: `classifies readiness timeout`.
- Test case: `classifies port bind failures`.
- Additional `1` test cases expand scenario coverage.

## PreviewStartupFailureHandlerTest.kt

**File Path**
`modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/intellij/PreviewStartupFailureHandlerTest.kt`

**What it does**
Defines a unit test suite for `PreviewStartupFailureHandler` by exercising expected behavior and edge cases.

**Why it matters**
It provides fast regression coverage for local logic so changes can be made safely.

**Dependencies**
- Internal: ui-plugin APIs, com.authord.mkdocs.ui.ActivationFailureReason, com.authord.mkdocs.ui.ActivationResult
- External: Kotlin Test, Java NIO, java.nio.file.Files

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Detect regressions in business logic, edge cases, and guard conditions.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `startup failure applies bypass and triggers fallback for docs scoped markdown`.
- Test case: `retry action clears bypass before retrying`.

## ReloadCoalescingGateTest.kt

**File Path**
`modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/intellij/ReloadCoalescingGateTest.kt`

**What it does**
Defines a unit test suite for `ReloadCoalescingGate` by exercising expected behavior and edge cases.

**Why it matters**
It provides fast regression coverage for local logic so changes can be made safely.

**Dependencies**
- Internal: ui-plugin local package types
- External: Kotlin Test

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Detect regressions in business logic, edge cases, and guard conditions.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `first reload is never suppressed and subsequent reload inside window is suppressed`.
- Test case: `reload is allowed once coalescing window elapses`.
- Test case: `non-positive coalescing window disables suppression`.

## SetupPageRendererTest.kt

**File Path**
`modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/intellij/SetupPageRendererTest.kt`

**What it does**
Defines a unit test suite for `SetupPage` by exercising expected behavior and edge cases.

**Why it matters**
It provides fast regression coverage for local logic so changes can be made safely.

**Dependencies**
- Internal: ui-plugin local package types
- External: Kotlin Test

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Detect regressions in business logic, edge cases, and guard conditions.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `render contains setup empty state actions and bridge injection`.

## StartMkdocsActionInvocationTest.kt

**File Path**
`modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/intellij/StartMkdocsActionInvocationTest.kt`

**What it does**
Defines a unit test suite for `StartMkdocsAction` by exercising expected behavior and edge cases.

**Why it matters**
It provides fast regression coverage for local logic so changes can be made safely.

**Dependencies**
- Internal: core-domain module APIs, com.authord.mkdocs.core.flags.FeatureFlagPolicy
- External: Kotlin Test

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Detect regressions in business logic, edge cases, and guard conditions.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `actionPerformed is no-op when project context is missing`.
- Test case: `actionPerformed delegates start request to runtime integration service`.
- Test case: `actionPerformed remains invokable while runtime is already running`.
- Test case: `actionPerformed does not start runtime when action is not startable`.
- Test case: `actionPerformed blocks start when compatibility release gate is blocked`.
- Additional `2` test cases expand scenario coverage.

## StartMkdocsActionPresentationTest.kt

**File Path**
`modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/intellij/StartMkdocsActionPresentationTest.kt`

**What it does**
Defines a unit test suite for `StartMkdocsAction` by exercising expected behavior and edge cases.

**Why it matters**
It provides fast regression coverage for local logic so changes can be made safely.

**Dependencies**
- Internal: core-domain module APIs, com.authord.mkdocs.core.flags.FeatureFlagPolicy
- External: IntelliJ Platform SDK, Kotlin Test, com.intellij.openapi.actionSystem.ActionUpdateThread

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Detect regressions in business logic, edge cases, and guard conditions.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `action update thread is background thread`.
- Test case: `update hides action when no project is available`.
- Test case: `update keeps action visible and enabled when runtime can start`.
- Test case: `update keeps action enabled while preview runtime is already running`.
- Test case: `update disables action when feature policy blocks mvp execution`.
- Additional `2` test cases expand scenario coverage.

## StartMkdocsMarkdownToolbarActionTest.kt

**File Path**
`modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/intellij/StartMkdocsMarkdownToolbarActionTest.kt`

**What it does**
Defines a unit test suite for `StartMkdocsMarkdownToolbar` by exercising expected behavior and edge cases.

**Why it matters**
It provides fast regression coverage for local logic so changes can be made safely.

**Dependencies**
- Internal: ui-plugin local package types
- External: IntelliJ Platform SDK, Kotlin Test, Java NIO, com.intellij.openapi.actionSystem.ActionUpdateThread, java.nio.file.Files

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Detect regressions in business logic, edge cases, and guard conditions.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `toolbar action update thread is background thread`.
- Test case: `update is visible and enabled in markdown context when preview can start`.
- Test case: `update is hidden and disabled in non-markdown context`.
- Test case: `actionPerformed delegates to existing start pipeline for markdown context`.
- Test case: `actionPerformed is no-op outside markdown context`.
- Additional `1` test cases expand scenario coverage.

## StartupReconciliationPerformanceTest.kt

**File Path**
`modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/intellij/StartupReconciliationPerformanceTest.kt`

**What it does**
Defines a performance-oriented test suite for `StartupReconciliation` by exercising expected behavior and edge cases.

**Why it matters**
It guards operational quality constraints and prevents performance regressions in user-facing flows.

**Dependencies**
- Internal: extension-ports module APIs, com.authord.mkdocs.ports.topic.MkDocsConfigDocument, com.authord.mkdocs.ports.topic.TopicNavNode
- External: Kotlin Test

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Guard response-time or throughput expectations for critical flows.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `startup reconciliation meets p95 threshold for reference dataset`.

## StartupReconciliationPolicyTest.kt

**File Path**
`modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/intellij/StartupReconciliationPolicyTest.kt`

**What it does**
Defines a unit test suite for `StartupReconciliation` by exercising expected behavior and edge cases.

**Why it matters**
It provides fast regression coverage for local logic so changes can be made safely.

**Dependencies**
- Internal: core-domain module APIs, extension-ports module APIs, com.authord.mkdocs.core.topic.TopicTreeValidationIssueType, com.authord.mkdocs.ports.topic.MkDocsConfigDocument, com.authord.mkdocs.ports.topic.TopicNavNode
- External: Kotlin Test

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Detect regressions in business logic, edge cases, and guard conditions.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `applies nav-first reconciliation with non-destructive policy`.
- Test case: `keeps fallback mode deterministic when nav is missing`.

## SystemRuntimeAdaptersTest.kt

**File Path**
`modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/intellij/SystemRuntimeAdaptersTest.kt`

**What it does**
Defines a unit test suite for `SystemRuntimeAdapters` by exercising expected behavior and edge cases.

**Why it matters**
It provides fast regression coverage for local logic so changes can be made safely.

**Dependencies**
- Internal: ui-plugin local package types
- External: Kotlin Test, Java IO, java.io.ByteArrayInputStream, java.io.ByteArrayOutputStream, java.io.InputStream, java.io.OutputStream

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Detect regressions in business logic, edge cases, and guard conditions.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `command runner executes command and captures merged output`.
- Test case: `command runner returns failure envelope when process start throws`.
- Test case: `command runner times out long-running command and terminates process`.
- Test case: `process launcher captures startup output from process stream`.
- Test case: `process launcher returns empty startup output when none is emitted`.
- Additional `3` test cases expand scenario coverage.

## TopicTreeActionControllerTest.kt

**File Path**
`modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/intellij/TopicTreeActionControllerTest.kt`

**What it does**
Defines a unit test suite for `TopicTreeAction` by exercising expected behavior and edge cases.

**Why it matters**
It provides fast regression coverage for local logic so changes can be made safely.

**Dependencies**
- Internal: extension-ports module APIs, com.authord.mkdocs.ports.topic.AddExistingFileTopicNodeCommand, com.authord.mkdocs.ports.topic.AddTopicNodeCommand, com.authord.mkdocs.ports.topic.DefaultTopicSyncError, com.authord.mkdocs.ports.topic.TopicGatewayResult
- External: Kotlin Test

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Detect regressions in business logic, edge cases, and guard conditions.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `create topic dispatches add command with normalized path`.
- Test case: `add existing file normalizes relative markdown path`.
- Test case: `failed remove returns non-destructive recovery guidance`.

## TopicTreeAggregateHydrationStartupTest.kt

**File Path**
`modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/intellij/TopicTreeAggregateHydrationStartupTest.kt`

**What it does**
Defines a unit test suite for `TopicTreeAggregateHydrationStartup` by exercising expected behavior and edge cases.

**Why it matters**
It provides fast regression coverage for local logic so changes can be made safely.

**Dependencies**
- Internal: core-domain module APIs, extension-ports module APIs, com.authord.mkdocs.core.topic.TopicTreeAggregate, com.authord.mkdocs.core.topic.TopicTreeMutationService, com.authord.mkdocs.ports.TopicTreePort, com.authord.mkdocs.ports.topic.DefaultTopicSyncError
- External: Kotlin Test, Java NIO, java.nio.file.Files, javax.swing.JComponent, javax.swing.JPanel

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Detect regressions in business logic, edge cases, and guard conditions.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `startup reconciliation hydrates aggregate from loaded config nav`.
- Test case: `startup reconciliation hydrates aggregate from fallback nodes when nav is absent`.
- Test case: `orchestrator hydrates aggregate before first rename command`.
- Test case: `orchestrator synthesizes fallback nav from docs when config nav is missing for rename`.
- Test case: `orchestrator synthesizes fallback nav from docs when config nav is missing for add child`.
- Additional `2` test cases expand scenario coverage.

## TopicTreeApplicationServiceContractTest.kt

**File Path**
`modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/intellij/TopicTreeApplicationServiceContractTest.kt`

**What it does**
Defines a contract test suite for `TopicTreeApplicationService` by exercising expected behavior and edge cases.

**Why it matters**
It protects API/port compatibility so independent implementations can evolve without breaking callers.

**Dependencies**
- Internal: extension-ports module APIs, com.authord.mkdocs.ports.TOPIC_TREE_APP_SERVICE_API_VERSION
- External: Kotlin Test

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Enforce interface/port invariants shared by multiple implementations.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `application service exposes execute rollback and active instance methods`.
- Test case: `application service api version follows semantic format`.

## TopicTreeDragDropControllerTest.kt

**File Path**
`modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/intellij/TopicTreeDragDropControllerTest.kt`

**What it does**
Defines a unit test suite for `TopicTreeDragDrop` by exercising expected behavior and edge cases.

**Why it matters**
It provides fast regression coverage for local logic so changes can be made safely.

**Dependencies**
- Internal: extension-ports module APIs, com.authord.mkdocs.ports.topic.DefaultTopicSyncError, com.authord.mkdocs.ports.topic.MoveTopicNodeCommand, com.authord.mkdocs.ports.topic.ReorderTopicNodesCommand, com.authord.mkdocs.ports.topic.TopicGatewayResult
- External: Kotlin Test

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Detect regressions in business logic, edge cases, and guard conditions.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `move topic dispatches move command`.
- Test case: `reorder failure returns recovery guidance`.

## TopicTreeFailureRecoveryPresenterTest.kt

**File Path**
`modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/intellij/TopicTreeFailureRecoveryPresenterTest.kt`

**What it does**
Defines a unit test suite for `TopicTreeFailureRecovery` by exercising expected behavior and edge cases.

**Why it matters**
It provides fast regression coverage for local logic so changes can be made safely.

**Dependencies**
- Internal: extension-ports module APIs, com.authord.mkdocs.ports.topic.DefaultTopicSyncError, com.authord.mkdocs.ports.topic.TopicSyncErrorCode
- External: Kotlin Test

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Detect regressions in business logic, edge cases, and guard conditions.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `validation failure guidance states no changes applied`.
- Test case: `orchestration failure guidance states rollback compensation`.

## TopicTreeFallbackBuilderTest.kt

**File Path**
`modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/intellij/TopicTreeFallbackBuilderTest.kt`

**What it does**
Defines a unit test suite for `TopicTreeFallbackBuilder` by exercising expected behavior and edge cases.

**Why it matters**
It provides fast regression coverage for local logic so changes can be made safely.

**Dependencies**
- Internal: ui-plugin local package types
- External: Kotlin Test

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Detect regressions in business logic, edge cases, and guard conditions.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `builds deterministic fallback tree from docs dir markdown paths`.
- Test case: `creates section hierarchy when fallback paths contain directories`.
- Test case: `supports absolute docs dir with absolute markdown paths`.

## TopicTreeFileMutationDerivationTest.kt

**File Path**
`modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/intellij/TopicTreeFileMutationDerivationTest.kt`

**What it does**
Defines a unit test suite for `TopicTreeFileMutationDerivation` by exercising expected behavior and edge cases.

**Why it matters**
It provides fast regression coverage for local logic so changes can be made safely.

**Dependencies**
- Internal: extension-ports module APIs, com.authord.mkdocs.ports.TopicTreePort, com.authord.mkdocs.ports.topic.AddChildTopicNodeCommand, com.authord.mkdocs.ports.topic.AddTopicNodeCommand, com.authord.mkdocs.ports.topic.DocsFileGateway
- External: Kotlin Test

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Detect regressions in business logic, edge cases, and guard conditions.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `add topic without source path derives markdown path and creates file`.
- Test case: `add child derives create path when parent is section without direct path`.
- Test case: `no-nav child then child-of-child creates folder hierarchy without config writes`.
- Test case: `no-nav child-of-child after fallback-style rehydrate preserves folder hierarchy without config writes`.
- Test case: `no-nav rename section alias rewrites folder markdown paths without config writes`.
- Additional `10` test cases expand scenario coverage.

## TopicTreeStartupLoaderTest.kt

**File Path**
`modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/intellij/TopicTreeStartupLoaderTest.kt`

**What it does**
Defines a unit test suite for `TopicTreeStartup` by exercising expected behavior and edge cases.

**Why it matters**
It provides fast regression coverage for local logic so changes can be made safely.

**Dependencies**
- Internal: extension-ports module APIs, com.authord.mkdocs.ports.topic.MkDocsConfigDocument, com.authord.mkdocs.ports.topic.TopicNavNode
- External: Kotlin Test, Java NIO, java.nio.file.Files

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Detect regressions in business logic, edge cases, and guard conditions.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `loads startup tree from canonical nav when nav exists`.
- Test case: `preserves nav ordering for startup tree`.
- Test case: `loads fallback tree when docs dir is absolute path`.
- Test case: `keeps canonical nav mode when nav key is present but empty`.
- Test case: `resolves nav display title from markdown h1 when present`.
- Additional `1` test cases expand scenario coverage.

## TopicTreeSyncOrchestratorCacheFreshnessTest.kt

**File Path**
`modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/intellij/TopicTreeSyncOrchestratorCacheFreshnessTest.kt`

**What it does**
Defines a unit test suite for `TopicTreeSyncOrchestratorCacheFreshness` by exercising expected behavior and edge cases.

**Why it matters**
It provides fast regression coverage for local logic so changes can be made safely.

**Dependencies**
- Internal: extension-ports module APIs, mkdocs-runtime-adapter APIs, com.authord.mkdocs.ports.TopicTreePort, com.authord.mkdocs.ports.topic.AddChildTopicNodeCommand, com.authord.mkdocs.ports.topic.DocsFileGateway, com.authord.mkdocs.ports.topic.TopicDeleteMode
- External: Kotlin Test, Java NIO, java.nio.file.Files

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Detect regressions in business logic, edge cases, and guard conditions.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `nav-present cache reloads after external config file change`.

## TopicTreeSyncOrchestratorPersistenceTest.kt

**File Path**
`modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/intellij/TopicTreeSyncOrchestratorPersistenceTest.kt`

**What it does**
Defines a unit test suite for `TopicTreeSyncOrchestratorPersistence` by exercising expected behavior and edge cases.

**Why it matters**
It provides fast regression coverage for local logic so changes can be made safely.

**Dependencies**
- Internal: extension-ports module APIs, com.authord.mkdocs.ports.TopicTreePort, com.authord.mkdocs.ports.topic.AddChildTopicNodeCommand, com.authord.mkdocs.ports.topic.DefaultTopicSyncError, com.authord.mkdocs.ports.topic.DocsFileGateway
- External: Kotlin Test

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Detect regressions in business logic, edge cases, and guard conditions.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `add child mutation persists nav and creates markdown file`.

## TopicTreeUiServiceContractTest.kt

**File Path**
`modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/intellij/TopicTreeUiServiceContractTest.kt`

**What it does**
Defines a contract test suite for `TopicTreeUiService` by exercising expected behavior and edge cases.

**Why it matters**
It protects API/port compatibility so independent implementations can evolve without breaking callers.

**Dependencies**
- Internal: extension-ports module APIs, com.authord.mkdocs.ports.TOPIC_TREE_UI_SERVICE_API_VERSION
- External: Kotlin Test

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Enforce interface/port invariants shared by multiple implementations.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `ui service exposes dispatch refresh and instance selection methods`.
- Test case: `ui service api version follows semantic format`.

## TopicTreeWatcherTriggerMatrixTest.kt

**File Path**
`modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/intellij/TopicTreeWatcherTriggerMatrixTest.kt`

**What it does**
Defines a unit test suite for `TopicTreeWatcherTriggerMatrix` by exercising expected behavior and edge cases.

**Why it matters**
It provides fast regression coverage for local logic so changes can be made safely.

**Dependencies**
- Internal: ui-plugin local package types
- External: Kotlin Test

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Detect regressions in business logic, edge cases, and guard conditions.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `triggers reconciliation on mkdocs config file lifecycle changes`.
- Test case: `triggers reconciliation on markdown changes within docs_dir only`.

## TopicTreeWorkspacePanelUiContractTest.kt

**File Path**
`modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/intellij/TopicTreeWorkspacePanelUiContractTest.kt`

**What it does**
Defines a contract test suite for `TopicTreeWorkspacePanelUi` by exercising expected behavior and edge cases.

**Why it matters**
It protects API/port compatibility so independent implementations can evolve without breaking callers.

**Dependencies**
- Internal: extension-ports module APIs, com.authord.mkdocs.ports.topic.DefaultTopicSyncError, com.authord.mkdocs.ports.topic.InstanceRegistryPort, com.authord.mkdocs.ports.topic.AddChildTopicNodeCommand, com.authord.mkdocs.ports.topic.AddTopicNodeCommand
- External: Kotlin Test

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Enforce interface/port invariants shared by multiple implementations.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `tooltips use exact contract strings`.
- Test case: `header actions follow required toc order`.
- Test case: `menu structure matches contract order`.
- Test case: `enablement follows capability and selection rules`.
- Test case: `expand all header action publishes expanded status`.
- Additional `19` test cases expand scenario coverage.

## TreeSyncOrchestratorAtomicityTest.kt

**File Path**
`modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/intellij/TreeSyncOrchestratorAtomicityTest.kt`

**What it does**
Defines a unit test suite for `TreeSyncOrchestratorAtomicity` by exercising expected behavior and edge cases.

**Why it matters**
It provides fast regression coverage for local logic so changes can be made safely.

**Dependencies**
- Internal: extension-ports module APIs, com.authord.mkdocs.ports.TopicTreePort, com.authord.mkdocs.ports.topic.DefaultTopicSyncError, com.authord.mkdocs.ports.topic.DocsFileGateway, com.authord.mkdocs.ports.topic.MkDocsConfigDocument
- External: Kotlin Test

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Detect regressions in business logic, edge cases, and guard conditions.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `apply succeeds when command config and file operations all succeed`.
- Test case: `apply rolls back with compensation when a later file operation fails`.
- Test case: `failed remove mutation keeps cached nav state consistent for retry`.

## UnlinkedFilesBucketServiceTest.kt

**File Path**
`modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/intellij/UnlinkedFilesBucketServiceTest.kt`

**What it does**
Defines a unit test suite for `UnlinkedFilesBucket` by exercising expected behavior and edge cases.

**Why it matters**
It provides fast regression coverage for local logic so changes can be made safely.

**Dependencies**
- Internal: extension-ports module APIs, com.authord.mkdocs.ports.topic.TopicNavNode
- External: Kotlin Test

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Detect regressions in business logic, edge cases, and guard conditions.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `derives docs_dir markdown files not present in nav`.
- Test case: `normalizes docs paths when nav paths use relative separators`.

## TopicTreePortContractTest.kt

**File Path**
`tests/contract/topic-tree-command-port/TopicTreePortContractTest.kt`

**What it does**
Defines a contract test suite for `TopicTreePort` by exercising expected behavior and edge cases.

**Why it matters**
It protects API/port compatibility so independent implementations can evolve without breaking callers.

**Dependencies**
- Internal: core-domain module APIs, extension-ports module APIs, infra-defaults adapters, com.authord.mkdocs.core.topic.TopicTreeMutationService, com.authord.mkdocs.defaults.command.InMemoryCommandRegistry, com.authord.mkdocs.defaults.preview.NoOpPreviewSyncAdapter, com.authord.mkdocs.defaults.vector.NoOpVectorStoreAdapter
- External: Kotlin Test

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Enforce interface/port invariants shared by multiple implementations.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `command bus and topic-tree port work with default adapters`.

## ExplorerSelectionToPreviewIT.kt

**File Path**
`tests/integration/explorer-preview-navigation/ExplorerSelectionToPreviewIT.kt`

**What it does**
Defines an integration test suite for `ExplorerSelectionToPreview` by exercising expected behavior and edge cases.

**Why it matters**
It validates end-to-end coordination across modules, reducing risk that component changes break runtime workflows.

**Dependencies**
- Internal: core-domain module APIs, ui-plugin APIs, com.authord.mkdocs.core.navigation.RouteMappingService, com.authord.mkdocs.ui.NavigationCoordinator, com.authord.mkdocs.ui.PreviewNavigationFailureHandler, com.authord.mkdocs.ui.PreviewPaneCoordinator
- External: Kotlin Test

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Verify cross-component coordination under realistic project/runtime states.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `file selection updates preview route`.

## ActivationToPreviewIT.kt

**File Path**
`tests/integration/runtime-lifecycle/ActivationToPreviewIT.kt`

**What it does**
Defines an integration test suite for `ActivationToPreview` by exercising expected behavior and edge cases.

**Why it matters**
It validates end-to-end coordination across modules, reducing risk that component changes break runtime workflows.

**Dependencies**
- Internal: core-domain module APIs, mkdocs-runtime-adapter APIs, ui-plugin APIs, com.authord.mkdocs.core.flags.FeatureFlagPolicy, com.authord.mkdocs.runtime.BaseUrlDetector, com.authord.mkdocs.runtime.CommandResult, com.authord.mkdocs.runtime.ManagedProcessHandle
- External: Kotlin Test

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Verify cross-component coordination under realistic project/runtime states.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `activation starts runtime and opens preview`.

## InstanceScaleProfileIntegrationTest.kt

**File Path**
`tests/integration/topic-tree/InstanceScaleProfileIntegrationTest.kt`

**What it does**
Defines an integration test suite for `InstanceScaleProfile` by exercising expected behavior and edge cases.

**Why it matters**
It validates end-to-end coordination across modules, reducing risk that component changes break runtime workflows.

**Dependencies**
- Internal: extension-ports module APIs, com.authord.mkdocs.ports.topic.MkDocsConfigDocument, com.authord.mkdocs.ports.topic.TopicInstanceRef, com.authord.mkdocs.ports.topic.TopicNavNode
- External: Kotlin Test

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Verify cross-component coordination under realistic project/runtime states.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `reconciles five instances and ten thousand docs without cross-instance leakage`.

## PartialSyncZeroStateIntegrationTest.kt

**File Path**
`tests/integration/topic-tree/PartialSyncZeroStateIntegrationTest.kt`

**What it does**
Defines an integration test suite for `PartialSyncZeroState` by exercising expected behavior and edge cases.

**Why it matters**
It validates end-to-end coordination across modules, reducing risk that component changes break runtime workflows.

**Dependencies**
- Internal: extension-ports module APIs, mkdocs-runtime-adapter APIs, com.authord.mkdocs.ports.TopicTreePort, com.authord.mkdocs.ports.topic.TopicFileOperation, com.authord.mkdocs.ports.topic.TopicFileOperationKind, com.authord.mkdocs.ports.topic.TopicGatewayResult
- External: Kotlin Test, Java NIO, java.nio.file.Files

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Verify cross-component coordination under realistic project/runtime states.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `failed file operation triggers compensation and leaves no unresolved partial state`.

## PathCaseMatrixIntegrationTest.kt

**File Path**
`tests/integration/topic-tree/PathCaseMatrixIntegrationTest.kt`

**What it does**
Defines an integration test suite for `PathCaseMatrix` by exercising expected behavior and edge cases.

**Why it matters**
It validates end-to-end coordination across modules, reducing risk that component changes break runtime workflows.

**Dependencies**
- Internal: core-domain module APIs, extension-ports module APIs, com.authord.mkdocs.core.topic.PathPolicy, com.authord.mkdocs.core.topic.TopicTreeValidationIssueType, com.authord.mkdocs.core.topic.TopicTreeValidationService, com.authord.mkdocs.ports.topic.TopicNavNode
- External: Kotlin Test

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Verify cross-component coordination under realistic project/runtime states.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `normalization uses canonical separators and resolves dot segments`.
- Test case: `comparison follows OS matrix case rules`.
- Test case: `case-only path differences follow validation behavior per OS`.

## SeededValidationDatasetIntegrationTest.kt

**File Path**
`tests/integration/topic-tree/SeededValidationDatasetIntegrationTest.kt`

**What it does**
Defines an integration test suite for `SeededValidationDataset` by exercising expected behavior and edge cases.

**Why it matters**
It validates end-to-end coordination across modules, reducing risk that component changes break runtime workflows.

**Dependencies**
- Internal: core-domain module APIs, extension-ports module APIs, com.authord.mkdocs.core.topic.PathPolicy, com.authord.mkdocs.core.topic.TopicTreeValidationIssueType, com.authord.mkdocs.core.topic.TopicTreeValidationService, com.authord.mkdocs.ports.topic.TopicNavNode
- External: Kotlin Test, Java NIO, java.nio.file.Files, java.nio.file.Path

**Responsibilities**
- Specify expected behavior through focused assertions and scenario setup.
- Verify cross-component coordination under realistic project/runtime states.
- Cover representative command paths and failure handling branches.

**Key Functionalities**
- Test case: `seeded validation dataset declares required case families`.
- Test case: `validator detects each seeded validation family`.

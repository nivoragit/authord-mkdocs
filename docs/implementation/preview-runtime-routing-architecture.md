# Authord Preview Runtime And Routing Architecture

Proposed file: `docs/implementation/preview-runtime-routing-architecture.md`

## Goal

Unify startup, routing, and readiness into one project-scoped runtime model so the same site context drives:
- runtime launch
- docs scope resolution
- route mapping
- route readiness and browser load

This removes divergent behavior between startup and routing and prevents stale-route and rebuild-loop failures.

## Implementation Alignment (Current)

Implemented in this branch:
- Shared `SiteContextResolver` is now used by both startup (`PluginActivationService`) and runtime routing (`PluginRuntimeIntegrationService`) to resolve nested config, `docs_dir`, and `use_directory_urls`.
- Startup now always launches MkDocs with explicit `-f <resolved-config>` (or fallback inherited config when theme override is used), removing root-only startup assumptions.
- Topic-mutation preview dispatch no longer bypasses route readiness with direct cached URL loads; cached routes are routed through readiness guard.
- Route mapping now accepts case-insensitive markdown extensions and respects `use_directory_urls=false` by producing `.html` routes.
- Preview pane route normalization now preserves `.html` routes without forcing trailing slash.
- Startup readiness now checks process liveness before and after readiness probe success to avoid false-ready races.

## Design Principles

- Single source of truth for active MkDocs site context.
- No direct browser load without route readiness for local previews.
- Background command queue for runtime operations; no blocking UI on process lifecycle.
- Capability-based route generation from MkDocs config, not fixed trailing-slash assumptions.

## Target Components

## 1. SiteContextResolver (shared startup + routing)

Responsibilities:
- Discover all candidate MkDocs configs in project scope.
- Resolve one active site context for runtime and routing:
  - `projectRoot`
  - `configPath`
  - `docsDirPath`
  - `useDirectoryUrls` (from config, default true)
  - optional `siteUrl`
- Prefer in order:
  1. active runtime `-f` config (when running)
  2. persisted user-selected site config
  3. nearest valid config for selected file
  4. deterministic project default (root-most valid config)

Fixes:
- Root-only startup mismatch (Issue 1).
- Ambiguous routing context drift.

## 2. RuntimeCommandQueue (project actor)

Responsibilities:
- Execute `Start`, `Restart`, `Stop`, `ReconcileMutation` commands sequentially on pooled thread.
- Expose async API returning callbacks/futures to UI callers.
- Replace broad `@Synchronized` lifecycle blocking with explicit queued commands.

Fixes:
- UI stalls and contention around lifecycle calls (including mutation-triggered restart paths).

## 3. RuntimeEndpointResolver

Responsibilities:
- Resolve actual runtime base URL from process output (primary), with readiness probe confirmation.
- Launch MkDocs with `--dev-addr 127.0.0.1:0` (or equivalent dynamic bind) and parse actual bound URL from stdout.
- Verify process alive before and after readiness probe success.

Fixes:
- Loopback port race false positives (Issue 4).

## 4. RoutePlanService

Responsibilities:
- Convert docs-relative markdown path to a route plan using site capabilities:
  - canonical route
  - candidate URLs to probe (`/x/`, `/x/index.html`, `/x.html`) based on `useDirectoryUrls`
- Normalize markdown extensions case-insensitively (`.md`, `.MD`, `.markdown`).
- Produce `RoutePlan` independent of current base URL; compose absolute URL at dispatch time.

Fixes:
- Uppercase extension mismatch (Issue 3).
- Cached stale absolute URL assumptions (Issue 2).

## 5. RouteReadinessCoordinator

Responsibilities:
- Always run local route loads through readiness probing, including cached or mutation paths.
- Keep bounded dirty-livereload nudges per probe cycle (never unbounded config touches).
- Retry with replay windows and cancellation tokens for stale intents.

Fixes:
- Stale 404 direct loads from cached mutation routes (Issue 2).
- Config-touch rebuild thrash.

## 6. PreviewRouteCache (metadata cache, not URL cache)

Responsibilities:
- Cache by normalized file key:
  - route
  - docs-relative path
  - plan capabilities version
- Do not cache absolute target URL.
- Invalidate on:
  - base URL change
  - config fingerprint change
  - docs_dir change
  - `use_directory_urls` change

Fixes:
- Base URL drift and stale absolute route loads (Issue 2).

## Data Contracts

## SiteContext

```kotlin
data class SiteContext(
    val projectRoot: Path,
    val configPath: Path,
    val docsDirPath: Path,
    val useDirectoryUrls: Boolean,
)
```

## RoutePlan

```kotlin
data class RoutePlan(
    val route: String,
    val candidateRelativeUrls: List<String>,
)
```

## RuntimeCommand

```kotlin
sealed interface RuntimeCommand {
    data class Start(val trigger: PreviewStartTrigger) : RuntimeCommand
    data class Restart(val trigger: PreviewStartTrigger) : RuntimeCommand
    object Stop : RuntimeCommand
    data class ReconcileMutation(val navPresent: Boolean) : RuntimeCommand
}
```

## Migration Plan

1. Introduce `SiteContextResolver` and wire both activation and routing to it.
2. Introduce `RoutePlanService`; switch mutation and selection dispatch to `RoutePlan` + readiness path.
3. Replace direct `onTopicMutationCommitted` callers with queue-backed async command.
4. Move startup endpoint resolution to `RuntimeEndpointResolver` with dynamic bind and parsed URL.
5. Remove legacy root-only config lookups and absolute URL cache fields.

## Test Strategy

- Contract tests for `SiteContextResolver` across nested monorepo configs.
- Route plan tests for `use_directory_urls: true/false` and mixed-case markdown extensions.
- Integration tests for mutation dispatch proving readiness guard is always applied.
- Lifecycle tests validating no EDT-blocking path for mutation-triggered restart.
- Startup tests verifying detected runtime URL belongs to launched process lifecycle.

## Documentation Tree Placement

- Add under implementation docs:
  - `docs/implementation/preview-runtime-routing-architecture.md`
- Link from:
  - `docs/implementation/technical-design-notes.md`
  - `docs/implementation/operational-runbook.md`

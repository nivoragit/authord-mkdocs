# authord-mkdocs-plugin Development Guidelines

Auto-generated from all feature plans. Last updated: 2026-02-08

## Active Technologies
- Kotlin 1.9+ on JVM 21 + IntelliJ Platform SDK, IntelliJ Platform Gradle Plugin, existing project modules (`core-domain`, `mkdocs-runtime-adapter`, `ui-plugin`, `extension-ports`, `infra-defaults`) (002-intellij-mkdocs-mvp)
- N/A (project-local runtime directory + in-memory adapter state) (002-intellij-mkdocs-mvp)
- Kotlin 1.9.24 on JVM 21 (IntelliJ plugin modules compiled for JVM 17 target compatibility) + IntelliJ Platform SDK (`IC` 2024.1.7), IntelliJ Platform Gradle plugin, existing modules (`core-domain`, `extension-ports`, `infra-defaults`, `mkdocs-runtime-adapter`, `ui-plugin`), `uv` + `mkdocs` runtime pipeline, deterministic YAML parser/serializer layer for `mkdocs.yml` (001-mkdocs-topic-tree)
- Project-local `mkdocs.yml`/`mkdocs.yaml`, `docs_dir` markdown files, in-memory project service state for active instance/tree/session (001-mkdocs-topic-tree)
- Kotlin 1.9.24 on JVM 21 (plugin target compatibility on JVM 17 bytecode) (`R-15`, `R-16`) + IntelliJ Platform SDK, IntelliJ Platform Gradle plugin, existing modules (`core-domain`, `extension-ports`, `infra-defaults`, `mkdocs-runtime-adapter`, `ui-plugin`), `uv` + `mkdocs` runtime path (`R-15`, `R-16`, `R-18`) (001-mkdocs-topic-tree)
- Project-local `mkdocs.yml`/`mkdocs.yaml`, `docs_dir` markdown files, in-memory project service state (`R-01`, `R-02`, `R-08`, `R-09`, `R-12`) (001-mkdocs-topic-tree)

- Kotlin 1.9+ on JVM 21 (IntelliJ Platform plugin target) + IntelliJ Platform SDK (PSI, ToolWindow, Disposable lifecycle), embedded browser component, `uv` CLI, `mkdocs` CLI (002-intellij-mkdocs-mvp)

## Project Structure

```text
src/
tests/
```

## Commands

# Add commands for Kotlin 1.9+ on JVM 21 (IntelliJ Platform plugin target)

## Code Style

Kotlin 1.9+ on JVM 21 (IntelliJ Platform plugin target): Follow standard conventions

## Recent Changes
- 001-mkdocs-topic-tree: Added Kotlin 1.9.24 on JVM 21 (plugin target compatibility on JVM 17 bytecode) (`R-15`, `R-16`) + IntelliJ Platform SDK, IntelliJ Platform Gradle plugin, existing modules (`core-domain`, `extension-ports`, `infra-defaults`, `mkdocs-runtime-adapter`, `ui-plugin`), `uv` + `mkdocs` runtime path (`R-15`, `R-16`, `R-18`)
- 001-mkdocs-topic-tree: Added Kotlin 1.9.24 on JVM 21 (IntelliJ plugin modules compiled for JVM 17 target compatibility) + IntelliJ Platform SDK (`IC` 2024.1.7), IntelliJ Platform Gradle plugin, existing modules (`core-domain`, `extension-ports`, `infra-defaults`, `mkdocs-runtime-adapter`, `ui-plugin`), `uv` + `mkdocs` runtime pipeline, deterministic YAML parser/serializer layer for `mkdocs.yml`
- 002-intellij-mkdocs-mvp: Added Kotlin 1.9+ on JVM 21 + IntelliJ Platform SDK, IntelliJ Platform Gradle Plugin, existing project modules (`core-domain`, `mkdocs-runtime-adapter`, `ui-plugin`, `extension-ports`, `infra-defaults`)


<!-- MANUAL ADDITIONS START -->
<!-- MANUAL ADDITIONS END -->

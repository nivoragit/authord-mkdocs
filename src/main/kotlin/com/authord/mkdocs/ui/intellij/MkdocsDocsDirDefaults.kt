package com.authord.mkdocs.ui.intellij

import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale

/**
 * Resolves implicit MkDocs docs directory when `docs_dir` is omitted.
 *
 * MkDocs defaults to `<config_dir>/docs`, but when a config itself lives inside
 * a `docs/` directory and no nested `docs/docs` exists, we treat that directory
 * as the effective docs scope to support common nested-config layouts.
 */
internal fun resolveImplicitDocsDirPath(configPath: Path): Path {
    val normalizedConfig = runCatching { configPath.toAbsolutePath().normalize() }.getOrNull() ?: configPath
    val configDirectory = normalizedConfig.parent?.toAbsolutePath()?.normalize()
        ?: return normalizedConfig.resolve("docs").toAbsolutePath().normalize()

    val conventionalDocsDir = configDirectory.resolve("docs").toAbsolutePath().normalize()
    val directoryName = configDirectory.fileName?.toString()?.lowercase(Locale.ROOT).orEmpty()
    if (directoryName == "docs" && !Files.exists(conventionalDocsDir)) {
        return configDirectory
    }

    return conventionalDocsDir
}

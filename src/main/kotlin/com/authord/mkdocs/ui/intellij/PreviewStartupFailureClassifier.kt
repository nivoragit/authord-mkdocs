package com.authord.mkdocs.ui.intellij

internal enum class PreviewStartupFailureCategory {
    MISSING_DEPENDENCY,
    GIT_REPOSITORY_REQUIRED,
    CONFIG_PARSE_ERROR,
    PROCESS_EXITED_EARLY,
    READINESS_TIMEOUT,
    PORT_BIND_ERROR,
    UNKNOWN,
}

internal data class PreviewStartupFailure(
    val category: PreviewStartupFailureCategory,
    val reason: String,
    val nextStep: String,
    val installPackage: String? = null,
    val suggestedCommand: String? = null,
    val primaryLine: String = "",
)

internal object PreviewStartupFailureClassifier {
    private val moduleMissingRegex = Regex("""No module named ['"]([^'"]+)['"]""", RegexOption.IGNORE_CASE)
    private val themeUnrecognizedRegex = Regex("""Unrecogni[sz]ed theme name:\s*['"]([^'"]+)['"]""", RegexOption.IGNORE_CASE)
    private val themeNotInstalledRegex = Regex("""Theme ['"]([^'"]+)['"] is not installed""", RegexOption.IGNORE_CASE)
    private val themeConfiguredMissingRegex = Regex(
        """theme\s+[`'"]?([^`'"]+)[`'"]?\s+is configured but not installed""",
        RegexOption.IGNORE_CASE,
    )
    private val pluginMissingRegex = Regex(
        """(?:Config value ['"]plugins['"]:\s*)?(?:The\s+)?['"]([^'"]+)['"]\s+plugin\s+is\s+not\s+installed""",
        RegexOption.IGNORE_CASE,
    )
    private val suggestedCommandRegex = Regex("""^\s*Suggested command:\s*(.+?)\s*$""", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
    private val gitRevisionPluginRegex = Regex(
        """git(?:[_-]revision[_-]date[_-]localized)(?:[_-]plugin)?""",
        RegexOption.IGNORE_CASE,
    )
    private val builtInThemeIds = setOf("mkdocs", "readthedocs")

    fun classify(rawMessage: String): PreviewStartupFailure {
        val normalized = rawMessage.trim()
        val primaryLine = normalized
            .lineSequence()
            .map(String::trim)
            .firstOrNull { it.isNotEmpty() }
            .orEmpty()
        val suggestedCommand = suggestedCommandRegex.find(normalized)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }

        if (normalized.isBlank()) {
            return PreviewStartupFailure(
                category = PreviewStartupFailureCategory.UNKNOWN,
                reason = "Unknown startup error.",
                nextStep = "Open runtime logs and retry.",
                suggestedCommand = suggestedCommand,
                primaryLine = primaryLine,
            )
        }

        val unrecognizedTheme = themeUnrecognizedRegex.find(normalized)?.groupValues?.getOrNull(1)?.trim()
        if (!unrecognizedTheme.isNullOrBlank()) {
            val installPackage = inferThemePackage(unrecognizedTheme)
            if (installPackage != null || suggestedCommand != null) {
                return PreviewStartupFailure(
                    category = PreviewStartupFailureCategory.MISSING_DEPENDENCY,
                    reason = "MkDocs theme `$unrecognizedTheme` is configured but not installed in the preview runtime.",
                    nextStep = if (!suggestedCommand.isNullOrBlank()) {
                        "Run the suggested command in the same runtime, then retry."
                    } else {
                        "Install `${installPackage.orEmpty()}` in the preview runtime and retry."
                    },
                    installPackage = installPackage,
                    suggestedCommand = suggestedCommand,
                    primaryLine = primaryLine,
                )
            }
        }

        val missingTheme = themeNotInstalledRegex.find(normalized)?.groupValues?.getOrNull(1)?.trim()
        if (!missingTheme.isNullOrBlank()) {
            val installPackage = inferThemePackage(missingTheme)
            if (installPackage != null || suggestedCommand != null) {
                return PreviewStartupFailure(
                    category = PreviewStartupFailureCategory.MISSING_DEPENDENCY,
                    reason = "MkDocs theme `$missingTheme` is configured but not installed in the preview runtime.",
                    nextStep = if (!suggestedCommand.isNullOrBlank()) {
                        "Run the suggested command in the same runtime, then retry."
                    } else {
                        "Install `${installPackage.orEmpty()}` in the preview runtime and retry."
                    },
                    installPackage = installPackage,
                    suggestedCommand = suggestedCommand,
                    primaryLine = primaryLine,
                )
            }
        }

        val configuredMissingTheme = themeConfiguredMissingRegex.find(normalized)?.groupValues?.getOrNull(1)?.trim()
        if (!configuredMissingTheme.isNullOrBlank()) {
            val installPackage = inferThemePackage(configuredMissingTheme)
            if (installPackage != null || suggestedCommand != null) {
                return PreviewStartupFailure(
                    category = PreviewStartupFailureCategory.MISSING_DEPENDENCY,
                    reason = "MkDocs theme `$configuredMissingTheme` is configured but not installed in the preview runtime.",
                    nextStep = if (!suggestedCommand.isNullOrBlank()) {
                        "Run the suggested command in the same runtime, then retry."
                    } else {
                        "Install `${installPackage.orEmpty()}` in the preview runtime and retry."
                    },
                    installPackage = installPackage,
                    suggestedCommand = suggestedCommand,
                    primaryLine = primaryLine,
                )
            }
        }

        if (normalized.contains("No module named 'material'", ignoreCase = true) ||
            normalized.contains("material.extensions.emoji", ignoreCase = true)
        ) {
            return PreviewStartupFailure(
                category = PreviewStartupFailureCategory.MISSING_DEPENDENCY,
                reason = "Missing Python dependency: mkdocs-material.",
                nextStep = "Install `mkdocs-material` in the preview runtime and retry.",
                installPackage = "mkdocs-material",
                suggestedCommand = suggestedCommand,
                primaryLine = primaryLine,
            )
        }

        if (normalized.contains("No module named 'pymdownx'", ignoreCase = true)) {
            return PreviewStartupFailure(
                category = PreviewStartupFailureCategory.MISSING_DEPENDENCY,
                reason = "Missing Python dependency: pymdown-extensions.",
                nextStep = "Install `pymdown-extensions` in the preview runtime and retry.",
                installPackage = "pymdown-extensions",
                suggestedCommand = suggestedCommand,
                primaryLine = primaryLine,
            )
        }

        val moduleMatch = moduleMissingRegex.find(normalized)?.groupValues?.getOrNull(1)
        if (!moduleMatch.isNullOrBlank()) {
            return PreviewStartupFailure(
                category = PreviewStartupFailureCategory.MISSING_DEPENDENCY,
                reason = "Missing Python dependency module: $moduleMatch.",
                nextStep = "Install the missing module in the preview runtime and retry.",
                installPackage = inferModulePackage(moduleMatch),
                suggestedCommand = suggestedCommand,
                primaryLine = primaryLine,
            )
        }

        val pluginMatch = pluginMissingRegex.find(normalized)?.groupValues?.getOrNull(1)?.trim()
        if (!pluginMatch.isNullOrBlank()) {
            val normalizedPluginId = pluginMatch.lowercase().replace('_', '-')
            val inferredPackage = "mkdocs-$normalizedPluginId"
            val nextStep = "Install the matching plugin package, often `$inferredPackage`, in the preview runtime and retry."

            return PreviewStartupFailure(
                category = PreviewStartupFailureCategory.MISSING_DEPENDENCY,
                reason = "MkDocs plugin `$normalizedPluginId` is declared in `mkdocs.yml` but is not installed in the preview runtime.",
                nextStep = nextStep,
                installPackage = inferredPackage,
                suggestedCommand = suggestedCommand,
                primaryLine = primaryLine,
            )
        }

        val referencesGitRevisionPlugin = gitRevisionPluginRegex.containsMatchIn(normalized) ||
            normalized.contains("fallback_to_build_date", ignoreCase = true)
        val indicatesMissingGitRepo = normalized.contains("InvalidGitRepositoryError", ignoreCase = true) ||
            normalized.contains("Unable to find a git directory", ignoreCase = true) ||
            normalized.contains("requires this project to be a git repository", ignoreCase = true)
        if (referencesGitRevisionPlugin && indicatesMissingGitRepo) {
            return PreviewStartupFailure(
                category = PreviewStartupFailureCategory.GIT_REPOSITORY_REQUIRED,
                reason = "MkDocs plugin `git-revision-date-localized` requires the site to be in a Git repository.",
                nextStep = "Open a Git checkout, run `git init`, or set `fallback_to_build_date: true` for that plugin, then retry.",
                suggestedCommand = suggestedCommand,
                primaryLine = primaryLine,
            )
        }

        if (normalized.contains("MkDocs encountered an error parsing the configuration file", ignoreCase = true) ||
            normalized.contains("yaml.scanner.ScannerError", ignoreCase = true) ||
            normalized.contains("ConfigurationError", ignoreCase = true)
        ) {
            return PreviewStartupFailure(
                category = PreviewStartupFailureCategory.CONFIG_PARSE_ERROR,
                reason = "MkDocs configuration parse error.",
                nextStep = "Fix `mkdocs.yml` and retry preview startup.",
                suggestedCommand = suggestedCommand,
                primaryLine = primaryLine,
            )
        }

        if (normalized.contains("process exited before readiness probe succeeded", ignoreCase = true)) {
            return PreviewStartupFailure(
                category = PreviewStartupFailureCategory.PROCESS_EXITED_EARLY,
                reason = "Preview process exited before readiness succeeded.",
                nextStep = "Open runtime logs for stderr details, then retry.",
                suggestedCommand = suggestedCommand,
                primaryLine = primaryLine,
            )
        }

        if (normalized.contains("readiness probe timed out", ignoreCase = true)) {
            return PreviewStartupFailure(
                category = PreviewStartupFailureCategory.READINESS_TIMEOUT,
                reason = "Preview readiness probe timed out.",
                nextStep = "Verify environment/dependencies and retry.",
                suggestedCommand = suggestedCommand,
                primaryLine = primaryLine,
            )
        }

        if (normalized.contains("Address already in use", ignoreCase = true) ||
            normalized.contains("Errno 98", ignoreCase = true) ||
            normalized.contains("Errno 48", ignoreCase = true)
        ) {
            return PreviewStartupFailure(
                category = PreviewStartupFailureCategory.PORT_BIND_ERROR,
                reason = "Preview port bind failed (address already in use).",
                nextStep = "Stop the conflicting process or restart preview on a free port.",
                suggestedCommand = suggestedCommand,
                primaryLine = primaryLine,
            )
        }

        val compact = normalized
            .lineSequence()
            .map(String::trim)
            .filter { it.isNotEmpty() }
            .filterNot { it.startsWith("Suggested command:", ignoreCase = true) }
            .filterNot { it.startsWith("After install, retry Start Authord Preview", ignoreCase = true) }
            .take(3)
            .joinToString(" | ")
        val clipped = if (compact.length <= 220) compact else "${compact.take(220)}..."
        return PreviewStartupFailure(
            category = PreviewStartupFailureCategory.UNKNOWN,
            reason = clipped.ifBlank { "Unknown startup error." },
            nextStep = "Open runtime logs and retry.",
            suggestedCommand = suggestedCommand,
            primaryLine = primaryLine,
        )
    }

    private fun inferThemePackage(themeId: String): String? {
        val normalized = themeId.trim().lowercase().replace('_', '-')
        if (normalized.isBlank() || normalized in builtInThemeIds) {
            return null
        }
        if (normalized == "material") {
            return "mkdocs-material"
        }
        return if (normalized.startsWith("mkdocs-")) normalized else "mkdocs-$normalized"
    }

    private fun inferModulePackage(moduleName: String): String? {
        val normalized = moduleName.trim().lowercase()
        if (normalized.isBlank()) {
            return null
        }
        val root = normalized.substringBefore('.')
        return when (root) {
            "material" -> "mkdocs-material"
            "pymdownx" -> "pymdown-extensions"
            else -> root.replace('_', '-')
        }
    }
}

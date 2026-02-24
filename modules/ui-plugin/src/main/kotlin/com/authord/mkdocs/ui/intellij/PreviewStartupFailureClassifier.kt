package com.authord.mkdocs.ui.intellij

internal enum class PreviewStartupFailureCategory {
    MISSING_DEPENDENCY,
    CONFIG_PARSE_ERROR,
    PROCESS_EXITED_EARLY,
    READINESS_TIMEOUT,
    PORT_BIND_ERROR,
    UNKNOWN,
}

internal enum class PreviewFailureConfidence {
    HIGH,
    MEDIUM,
    LOW,
}

internal data class PreviewStartupFailure(
    val category: PreviewStartupFailureCategory,
    val reason: String,
    val nextStep: String,
    val installPackage: String? = null,
    val primaryLine: String = "",
    val missingModule: String? = null,
    val confidence: PreviewFailureConfidence = PreviewFailureConfidence.LOW,
    val installCommand: String? = null,
    val persistenceGuidance: String = "",
    val configContext: String? = null,
    val suggestedActions: List<String> = emptyList(),
    val dependencyDeclarationHint: String? = null,
)

internal data class PreviewStartupFailureContext(
    val pythonExecutable: String? = null,
    val dependencyDeclarationHint: String? = null,
)

internal object MkdocsErrorClassifier {
    private val moduleMissingRegex = Regex("""No module named ['"]([^'"]+)['"]""", RegexOption.IGNORE_CASE)
    private val configLineColumnRegex = Regex("""line\s+(\d+)\s*,\s*column\s+(\d+)""", RegexOption.IGNORE_CASE)

    fun classify(
        rawMessage: String,
        context: PreviewStartupFailureContext = PreviewStartupFailureContext(),
    ): PreviewStartupFailure {
        val normalized = rawMessage.trim()
        val primaryLine = normalized
            .lineSequence()
            .map(String::trim)
            .firstOrNull { it.isNotEmpty() }
            .orEmpty()

        if (normalized.isBlank()) {
            return PreviewStartupFailure(
                category = PreviewStartupFailureCategory.UNKNOWN,
                reason = "Unknown startup error.",
                nextStep = "Retry preview startup and open runtime logs if the issue persists.",
                primaryLine = primaryLine,
                confidence = PreviewFailureConfidence.LOW,
                suggestedActions = listOf("Retry", "Open Runtime Logs"),
            )
        }

        if (
            normalized.contains("No module named 'material'", ignoreCase = true) ||
            normalized.contains("cannot find module 'material.extensions.emoji'", ignoreCase = true) ||
            normalized.contains("material.extensions.emoji", ignoreCase = true)
        ) {
            return dependencyFailure(
                primaryLine = primaryLine,
                missingModule = "material.extensions.emoji",
                packageName = "mkdocs-material",
                reason = "MkDocs config references Material extensions, but `mkdocs-material` is not installed in the preview Python environment.",
                context = context,
                confidence = PreviewFailureConfidence.HIGH,
            )
        }

        if (normalized.contains("No module named 'pymdownx'", ignoreCase = true)) {
            return dependencyFailure(
                primaryLine = primaryLine,
                missingModule = "pymdownx",
                packageName = "pymdown-extensions",
                reason = "MkDocs config references pymdown extensions, but `pymdown-extensions` is not installed in the preview Python environment.",
                context = context,
                confidence = PreviewFailureConfidence.HIGH,
            )
        }

        val moduleMatch = moduleMissingRegex.find(normalized)?.groupValues?.getOrNull(1)
        if (!moduleMatch.isNullOrBlank()) {
            val inferredPackage = inferPackageFromModule(moduleMatch)
            return dependencyFailure(
                primaryLine = primaryLine,
                missingModule = moduleMatch,
                packageName = inferredPackage,
                reason = "Missing Python module `$moduleMatch` in the preview runtime.",
                context = context,
                confidence = PreviewFailureConfidence.LOW,
            )
        }

        if (
            normalized.contains("MkDocs encountered an error parsing the configuration file", ignoreCase = true) ||
            normalized.contains("yaml.scanner.ScannerError", ignoreCase = true) ||
            normalized.contains("ConfigurationError", ignoreCase = true)
        ) {
            val configContext = extractConfigContext(normalized)
            val reason = if (configContext != null) {
                "MkDocs configuration parse error near $configContext."
            } else {
                "MkDocs configuration parse error."
            }
            return PreviewStartupFailure(
                category = PreviewStartupFailureCategory.CONFIG_PARSE_ERROR,
                reason = reason,
                nextStep = "Fix `mkdocs.yml` syntax and retry preview startup.",
                primaryLine = primaryLine,
                confidence = PreviewFailureConfidence.HIGH,
                configContext = configContext,
                suggestedActions = listOf("Retry", "Open mkdocs.yml"),
            )
        }

        if (normalized.contains("process exited before readiness probe succeeded", ignoreCase = true)) {
            return PreviewStartupFailure(
                category = PreviewStartupFailureCategory.PROCESS_EXITED_EARLY,
                reason = "Preview process exited before readiness succeeded.",
                nextStep = "Retry startup and inspect stderr tail for dependency or config failures.",
                primaryLine = primaryLine,
                confidence = PreviewFailureConfidence.MEDIUM,
                suggestedActions = listOf("Retry", "Open Runtime Logs"),
            )
        }

        if (normalized.contains("readiness probe timed out", ignoreCase = true)) {
            return PreviewStartupFailure(
                category = PreviewStartupFailureCategory.READINESS_TIMEOUT,
                reason = "Preview readiness probe timed out.",
                nextStep = "Retry startup. If it fails again, run dependency setup and inspect runtime logs.",
                primaryLine = primaryLine,
                confidence = PreviewFailureConfidence.MEDIUM,
                suggestedActions = listOf("Retry", "Open Runtime Logs"),
            )
        }

        if (
            normalized.contains("Address already in use", ignoreCase = true) ||
            normalized.contains("Errno 98", ignoreCase = true) ||
            normalized.contains("Errno 48", ignoreCase = true)
        ) {
            return PreviewStartupFailure(
                category = PreviewStartupFailureCategory.PORT_BIND_ERROR,
                reason = "Preview port bind failed (address already in use).",
                nextStep = "Stop the conflicting process or retry preview startup.",
                primaryLine = primaryLine,
                confidence = PreviewFailureConfidence.HIGH,
                suggestedActions = listOf("Retry", "Open Runtime Logs"),
            )
        }

        val compact = normalized
            .lineSequence()
            .map(String::trim)
            .filter { it.isNotEmpty() }
            .take(3)
            .joinToString(" | ")
        val clipped = if (compact.length <= 220) compact else "${compact.take(220)}..."
        return PreviewStartupFailure(
            category = PreviewStartupFailureCategory.UNKNOWN,
            reason = clipped.ifBlank { "Unknown startup error." },
            nextStep = "Retry preview startup and inspect runtime logs.",
            primaryLine = primaryLine,
            confidence = PreviewFailureConfidence.LOW,
            suggestedActions = listOf("Retry", "Open Runtime Logs"),
        )
    }

    private fun dependencyFailure(
        primaryLine: String,
        missingModule: String,
        packageName: String?,
        reason: String,
        context: PreviewStartupFailureContext,
        confidence: PreviewFailureConfidence,
    ): PreviewStartupFailure {
        val installCommand = packageName?.let { buildInstallCommand(context, it) }
        val persistenceGuidance = packageName?.let { buildPersistenceGuidance(it) }.orEmpty()
        val declarationHint = context.dependencyDeclarationHint?.takeIf { it.isNotBlank() }
        val nextStep = buildDependencyNextStep(
            packageName = packageName,
            installCommand = installCommand,
            persistenceGuidance = persistenceGuidance,
            dependencyHint = declarationHint,
        )
        return PreviewStartupFailure(
            category = PreviewStartupFailureCategory.MISSING_DEPENDENCY,
            reason = reason,
            nextStep = nextStep,
            installPackage = packageName,
            primaryLine = primaryLine,
            missingModule = missingModule,
            confidence = confidence,
            installCommand = installCommand,
            persistenceGuidance = persistenceGuidance,
            suggestedActions = listOf("Retry dependency setup", "Start Anyway", "Open Terminal"),
            dependencyDeclarationHint = declarationHint,
        )
    }

    private fun buildInstallCommand(context: PreviewStartupFailureContext, packageName: String): String {
        val pythonExecutable = context.pythonExecutable
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: "python"
        val executable = shellQuote(pythonExecutable)
        return "$executable -m pip install $packageName"
    }

    private fun buildPersistenceGuidance(packageName: String): String {
        return "Add `$packageName` to requirements.txt / requirements-docs.txt / pyproject.toml docs dependencies."
    }

    private fun buildDependencyNextStep(
        packageName: String?,
        installCommand: String?,
        persistenceGuidance: String,
        dependencyHint: String?,
    ): String {
        val quickFix = if (!installCommand.isNullOrBlank()) {
            "Fix now: `$installCommand`."
        } else if (!packageName.isNullOrBlank()) {
            "Fix now: install `$packageName` in the preview runtime."
        } else {
            "Fix now: install the missing Python module in the preview runtime."
        }

        return buildString {
            append(quickFix)
            if (persistenceGuidance.isNotBlank()) {
                append(" Make permanent: ")
                append(persistenceGuidance)
            }
            append(" Then restart preview or click Retry dependency setup.")
            if (!dependencyHint.isNullOrBlank()) {
                append(" ")
                append(dependencyHint)
            }
        }
    }

    private fun inferPackageFromModule(moduleName: String): String {
        val topLevel = moduleName.substringBefore('.').trim()
        return topLevel.replace('_', '-')
    }

    private fun extractConfigContext(rawMessage: String): String? {
        val match = configLineColumnRegex.find(rawMessage) ?: return null
        val line = match.groupValues.getOrNull(1)?.ifBlank { return null } ?: return null
        val column = match.groupValues.getOrNull(2)?.ifBlank { return null } ?: return null
        return "line $line, column $column"
    }

    private fun shellQuote(token: String): String {
        return if (token.any { it.isWhitespace() }) {
            "\"${token.replace("\"", "\\\"")}\""
        } else {
            token
        }
    }
}

internal object PreviewStartupFailureClassifier {
    fun classify(
        rawMessage: String,
        context: PreviewStartupFailureContext = PreviewStartupFailureContext(),
    ): PreviewStartupFailure {
        return MkdocsErrorClassifier.classify(rawMessage, context)
    }
}

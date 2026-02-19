package com.authord.mkdocs.runtime

/**
 * Detects runtime base URL from startup stdout text.
 */
open class BaseUrlDetector {
    private val servingLineUrlRegex = Regex(
        pattern = """(?i)\bserv(?:e|ing)\b.*?\b(?:on|at)\b\s+(https?://[^\s"'<>]+)""",
    )

    /**
     * Extracts a URL only from MkDocs-like serving lines.
     *
     * @return normalized URL or null if none found.
     */
    open fun detectBaseUrl(startupOutput: String): String? {
        val servingLine = startupOutput
            .lineSequence()
            .map(String::trim)
            .firstOrNull { line -> line.contains("serv", ignoreCase = true) }
            ?: return null

        val match = servingLineUrlRegex.find(servingLine) ?: return null
        return normalizeDetectedUrl(match.groupValues[1])
    }

    private fun normalizeDetectedUrl(rawUrl: String): String {
        return rawUrl.trim().trimEnd('.', ',', ';', ')', ']')
    }
}

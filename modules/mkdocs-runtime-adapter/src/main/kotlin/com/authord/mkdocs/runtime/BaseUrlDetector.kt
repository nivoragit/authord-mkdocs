package com.authord.mkdocs.runtime

/**
 * Detects runtime base URL from startup stdout text.
 */
class BaseUrlDetector {
    private val urlRegex = Regex("""https?://[^\s"'<>]+""")

    /**
     * Extracts the first URL token from startup output.
     *
     * @return normalized URL or null if none found.
     */
    fun detectBaseUrl(startupOutput: String): String? {
        val match = urlRegex.find(startupOutput) ?: return null
        return normalizeDetectedUrl(match.value)
    }

    private fun normalizeDetectedUrl(rawUrl: String): String {
        return rawUrl.trim().trimEnd('.', ',', ';', ')', ']')
    }
}

package com.authord.mkdocs.runtime

class BaseUrlDetector {
    private val urlRegex = Regex("(https?://[^\\s]+)")

    fun detectBaseUrl(startupOutput: String): String? {
        val match = urlRegex.find(startupOutput) ?: return null
        return match.groupValues[1].trimEnd('.', ',', ';')
    }
}

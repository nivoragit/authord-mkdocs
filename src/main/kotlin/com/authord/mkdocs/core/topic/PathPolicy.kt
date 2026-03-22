package com.authord.mkdocs.core.topic

import java.util.Locale

/**
 * OS-aware path normalization and comparison policy for topic-tree operations.
 */
data class PathPolicy(
    val caseSensitiveComparison: Boolean,
) {
    /**
     * Normalizes path separators and collapses `.`/`..` segments.
     */
    fun normalize(path: String): String {
        val replaced = path.replace('\\', '/')
        val trimmed = replaced.trim()
        if (trimmed.isEmpty()) {
            return ""
        }

        val drivePrefix = drivePrefix(trimmed)
        val hasLeadingSlash = trimmed.startsWith('/')
        val body = when {
            drivePrefix != null -> trimmed.removePrefix(drivePrefix).removePrefix("/")
            hasLeadingSlash -> trimmed.removePrefix("/")
            else -> trimmed
        }

        val parts = mutableListOf<String>()
        body.split('/')
            .filter { it.isNotEmpty() }
            .forEach { segment ->
                when (segment) {
                    "." -> Unit
                    ".." -> {
                        if (parts.isNotEmpty() && parts.last() != "..") {
                            parts.removeAt(parts.lastIndex)
                        } else if (!hasLeadingSlash && drivePrefix == null) {
                            parts += segment
                        }
                    }

                    else -> parts += segment
                }
            }

        val normalizedBody = parts.joinToString("/")
        return when {
            drivePrefix != null && normalizedBody.isEmpty() -> "$drivePrefix/"
            drivePrefix != null -> "$drivePrefix/$normalizedBody"
            hasLeadingSlash && normalizedBody.isEmpty() -> "/"
            hasLeadingSlash -> "/$normalizedBody"
            else -> normalizedBody
        }
    }

    /**
     * Produces a comparison key according to OS case-sensitivity rules.
     */
    fun comparisonKey(path: String): String {
        val normalized = normalize(path)
        return if (caseSensitiveComparison) {
            normalized
        } else {
            normalized.lowercase(Locale.ROOT)
        }
    }

    /**
     * Returns true when two paths are equivalent under this policy.
     */
    fun equivalent(left: String, right: String): Boolean {
        return comparisonKey(left) == comparisonKey(right)
    }

    private fun drivePrefix(path: String): String? {
        return if (path.length >= 2 && path[1] == ':' && path[0].isLetter()) {
            path.substring(0, 2)
        } else {
            null
        }
    }

    companion object {
        /**
         * Returns policy for the current host OS.
         */
        fun forCurrentOs(): PathPolicy = forOsName(System.getProperty("os.name").orEmpty())

        /**
         * Returns policy for a specific OS name string.
         */
        fun forOsName(osName: String): PathPolicy {
            val normalized = osName.lowercase(Locale.ROOT)
            val caseSensitive = !(normalized.contains("win") || normalized.contains("mac") || normalized.contains("darwin"))
            return PathPolicy(caseSensitiveComparison = caseSensitive)
        }
    }
}

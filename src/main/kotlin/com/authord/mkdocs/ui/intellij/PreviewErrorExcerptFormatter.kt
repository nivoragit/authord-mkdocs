package com.authord.mkdocs.ui.intellij

import java.util.Locale

private const val EXACT_ERROR_MAX_LINES = 10
private const val EXACT_ERROR_MAX_CHARS = 1_200

/**
 * Extracts a compact raw diagnostics excerpt suitable for user-facing notifications.
 *
 * Preference order:
 * 1) `stderr tail:`
 * 2) `stdout tail:`
 * 3) `Details:`
 * 4) full message fallback
 */
internal fun extractExactErrorExcerpt(
    rawMessage: String,
    maxLines: Int = EXACT_ERROR_MAX_LINES,
    maxChars: Int = EXACT_ERROR_MAX_CHARS,
): String {
    if (rawMessage.isBlank()) {
        return ""
    }
    val normalized = rawMessage
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .trim()
    if (normalized.isBlank()) {
        return ""
    }
    val selectedSection = extractPreferredSection(normalized)
    return truncateExcerpt(
        text = selectedSection,
        maxLines = maxLines.coerceAtLeast(1),
        maxChars = maxChars.coerceAtLeast(80),
    )
}

private fun extractPreferredSection(message: String): String {
    sectionAfterLabel(message, "stderr tail:").takeIf { it.isNotBlank() }?.let { return it }
    sectionAfterLabel(
        message = message,
        label = "stdout tail:",
        stopLabels = listOf("stderr tail:"),
    ).takeIf { it.isNotBlank() }?.let { return it }
    sectionAfterLabel(
        message = message,
        label = "details:",
        stopLabels = listOf("stdout tail:", "stderr tail:"),
    ).takeIf { it.isNotBlank() }?.let { return it }
    return message.trim()
}

private fun sectionAfterLabel(
    message: String,
    label: String,
    stopLabels: List<String> = emptyList(),
): String {
    val lowered = message.lowercase(Locale.ROOT)
    val labelIndex = lowered.indexOf(label)
    if (labelIndex < 0) {
        return ""
    }
    val startIndex = (labelIndex + label.length).coerceAtMost(message.length)
    var section = message.substring(startIndex).trimStart()
    if (section.isBlank() || stopLabels.isEmpty()) {
        return section.trim()
    }
    val sectionLower = section.lowercase(Locale.ROOT)
    val stopIndex = stopLabels
        .mapNotNull { stopLabel -> sectionLower.indexOf(stopLabel).takeIf { it >= 0 } }
        .minOrNull()
    if (stopIndex != null) {
        section = section.substring(0, stopIndex)
    }
    return section.trim()
}

private fun truncateExcerpt(text: String, maxLines: Int, maxChars: Int): String {
    val nonBlankLines = text.lineSequence()
        .map { it.trimEnd() }
        .filter { it.isNotBlank() }
        .toList()
    if (nonBlankLines.isEmpty()) {
        return ""
    }

    val limitedLines = nonBlankLines.take(maxLines)
    var excerpt = limitedLines.joinToString("\n")
    var truncated = nonBlankLines.size > maxLines
    if (excerpt.length > maxChars) {
        excerpt = excerpt.take(maxChars).trimEnd()
        truncated = true
    }
    if (truncated) {
        excerpt += "\n..."
    }
    return excerpt
}

package com.authord.mkdocs.runtime

/**
 * Utilities for extracting and updating first-level Markdown headings.
 */
object MarkdownHeadingSupport {
    private val atxH1Regex = Regex("""^\s{0,3}#(?!#)\s+(.+?)\s*#*\s*$""")
    private val fenceRegex = Regex("""^\s*(```+|~~~+).*$""")

    /**
     * Returns the first Markdown H1 heading outside code fences, if present.
     */
    fun extractFirstH1(markdown: String): String? {
        val lines = markdown.lines()
        val bodyStartIndex = frontMatterEndExclusive(lines)
        var inFence = false

        for (index in bodyStartIndex until lines.size) {
            val line = lines[index]
            if (fenceRegex.matches(line)) {
                inFence = !inFence
                continue
            }
            if (inFence) {
                continue
            }

            val match = atxH1Regex.matchEntire(line) ?: continue
            val title = match.groupValues[1].trim()
            if (title.isNotBlank()) {
                return title
            }
        }

        return null
    }

    /**
     * Returns markdown where the first H1 heading is inserted or replaced with [title].
     */
    fun upsertFirstH1(markdown: String, title: String): String {
        val normalizedTitle = title.trim().ifBlank { "Untitled" }
        val lines = markdown.lines().toMutableList()
        val bodyStartIndex = frontMatterEndExclusive(lines)
        var inFence = false
        var headingIndex = -1

        for (index in bodyStartIndex until lines.size) {
            val line = lines[index]
            if (fenceRegex.matches(line)) {
                inFence = !inFence
                continue
            }
            if (inFence) {
                continue
            }
            if (atxH1Regex.matches(line)) {
                headingIndex = index
                break
            }
        }

        if (headingIndex >= 0) {
            lines[headingIndex] = "# $normalizedTitle"
        } else {
            lines.add(bodyStartIndex, "# $normalizedTitle")
            if (bodyStartIndex + 1 < lines.size && lines[bodyStartIndex + 1].isNotBlank()) {
                lines.add(bodyStartIndex + 1, "")
            }
        }

        val rebuilt = lines.joinToString("\n")
        return if (markdown.endsWith('\n')) "$rebuilt\n" else rebuilt
    }

    /**
     * Returns canonical starter content for a new markdown topic file.
     */
    fun defaultTopicContent(title: String): String {
        val normalizedTitle = title.trim().ifBlank { "Untitled" }
        return "# $normalizedTitle\n"
    }

    private fun frontMatterEndExclusive(lines: List<String>): Int {
        if (lines.isEmpty()) {
            return 0
        }
        if (lines.first().trim() != "---") {
            return 0
        }

        for (index in 1 until lines.size) {
            if (lines[index].trim() == "---") {
                val nextIndex = index + 1
                return if (nextIndex < lines.size && lines[nextIndex].isBlank()) {
                    nextIndex + 1
                } else {
                    nextIndex
                }
            }
        }

        return 0
    }
}

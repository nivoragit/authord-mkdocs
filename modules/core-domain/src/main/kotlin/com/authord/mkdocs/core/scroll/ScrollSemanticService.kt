package com.authord.mkdocs.core.scroll

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Comment range in document offsets.
 */
data class CommentRange(
    val startOffset: Int,
    val endOffset: Int,
)

/**
 * Calculates semantic scroll delta by excluding comment-covered ranges.
 */
class ScrollSemanticService {
    /**
     * Computes effective delta after removing the portion overlapping comment ranges.
     *
     * @param rawDelta incoming scroll delta.
     * @param visibleStartOffset first visible document offset.
     * @param visibleEndOffset last visible document offset.
     * @param commentRanges comment spans within visible range.
     */
    fun calculateSemanticDelta(
        rawDelta: Int,
        visibleStartOffset: Int,
        visibleEndOffset: Int,
        commentRanges: List<CommentRange>,
    ): Int {
        if (rawDelta == 0) return 0

        val total = (visibleEndOffset - visibleStartOffset).coerceAtLeast(1)
        val excluded = commentRanges.sumOf { overlap(visibleStartOffset, visibleEndOffset, it.startOffset, it.endOffset) }
            .coerceAtMost(total)

        if (excluded == 0) return rawDelta
        if (excluded == total) return 0

        val ratio = excluded.toDouble() / total.toDouble()
        val excludedDelta = (abs(rawDelta) * ratio).roundToInt()
        return if (rawDelta > 0) rawDelta - excludedDelta else rawDelta + excludedDelta
    }

    /**
     * Returns overlap length between two ranges.
     */
    private fun overlap(startA: Int, endA: Int, startB: Int, endB: Int): Int {
        val start = maxOf(startA, startB)
        val end = minOf(endA, endB)
        return (end - start).coerceAtLeast(0)
    }
}

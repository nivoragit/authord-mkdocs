package com.authord.mkdocs.core.scroll

import kotlin.math.abs
import kotlin.math.roundToInt

data class CommentRange(
    val startOffset: Int,
    val endOffset: Int,
)

class ScrollSemanticService {
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

    private fun overlap(startA: Int, endA: Int, startB: Int, endB: Int): Int {
        val start = maxOf(startA, startB)
        val end = minOf(endA, endB)
        return (end - start).coerceAtLeast(0)
    }
}

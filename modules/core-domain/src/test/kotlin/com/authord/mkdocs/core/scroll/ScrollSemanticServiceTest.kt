package com.authord.mkdocs.core.scroll

import kotlin.test.Test
import kotlin.test.assertEquals

class ScrollSemanticServiceTest {
    private val service = ScrollSemanticService()

    @Test
    fun `returns zero when visible region is fully comments`() {
        val delta = service.calculateSemanticDelta(
            rawDelta = 40,
            visibleStartOffset = 0,
            visibleEndOffset = 100,
            commentRanges = listOf(CommentRange(0, 100)),
        )

        assertEquals(0, delta)
    }

    @Test
    fun `returns reduced delta when visible region partly overlaps comments`() {
        val delta = service.calculateSemanticDelta(
            rawDelta = 100,
            visibleStartOffset = 0,
            visibleEndOffset = 100,
            commentRanges = listOf(CommentRange(0, 20)),
        )

        assertEquals(80, delta)
    }

    @Test
    fun `returns raw delta when no comment overlap exists`() {
        val delta = service.calculateSemanticDelta(
            rawDelta = -25,
            visibleStartOffset = 100,
            visibleEndOffset = 200,
            commentRanges = listOf(CommentRange(0, 20)),
        )

        assertEquals(-25, delta)
    }

    @Test
    fun `returns zero when raw delta is zero`() {
        val delta = service.calculateSemanticDelta(
            rawDelta = 0,
            visibleStartOffset = 0,
            visibleEndOffset = 100,
            commentRanges = listOf(CommentRange(0, 50)),
        )

        assertEquals(0, delta)
    }

    @Test
    fun `applies exclusion ratio for negative deltas`() {
        val delta = service.calculateSemanticDelta(
            rawDelta = -50,
            visibleStartOffset = 0,
            visibleEndOffset = 100,
            commentRanges = listOf(CommentRange(0, 10)),
        )

        assertEquals(-45, delta)
    }
}

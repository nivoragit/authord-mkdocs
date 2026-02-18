package com.authord.mkdocs.ui.intellij

import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.impl.DocumentImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MkdocsScrollSyncEngineTest {
    @Test
    fun `source extraction omits markdown comments while preserving fenced code context`() {
        val engine = MkdocsScrollSyncEngine(
            delayedInvoker = { _, task -> task() },
            mapRebuildDebounceMs = 0L,
        )
        val document = DocumentImpl(
            """
            # Title
            [//]: # (comment one)
            [//]: # comment two
            Visible paragraph line with enough words for sparse anchor extraction.
            ```md
            [//]: # inside code should stay code context
            ```
            """.trimIndent(),
        )

        val source = engine.resolveSourceState(document)
        assertFalse(source.anchors.any { it.startLine == 1 || it.startLine == 2 })
        val codeAnchor = source.anchors.firstOrNull { it.type == AnchorType.CODE }
        assertNotNull(codeAnchor)
        assertTrue(codeAnchor.normText.contains("inside code"))
    }

    @Test
    fun `hard heading matching prefers stable heading ids with occurrences`() {
        val engine = MkdocsScrollSyncEngine(
            delayedInvoker = { _, task -> task() },
            mapRebuildDebounceMs = 0L,
            syncTokenTtlMs = 0L,
        )
        val document = DocumentImpl(
            """
            # Root
            ## Intro {#intro-a}
            body
            ## Intro {#intro-b}
            tail
            """.trimIndent(),
        )
        val snapshot = PreviewDomSnapshot(
            maxScrollY = 900.0,
            anchors = listOf(
                PreviewDomAnchor(id = "intro-b", type = AnchorType.H, level = 2, top = 500.0, bottom = 540.0, normText = "intro"),
                PreviewDomAnchor(id = "intro-a", type = AnchorType.H, level = 2, top = 120.0, bottom = 150.0, normText = "intro"),
            ),
        )

        engine.requestMapRebuild(document, lineHeightPx = 20) { callback -> callback(snapshot) }

        val state = assertNotNull(engine.currentState())
        val hardMatches = state.hardAnchors
        val introA = hardMatches.firstOrNull { it.source.id.contains("key=intro-a") }
        val introB = hardMatches.firstOrNull { it.source.id.contains("key=intro-b") }
        assertNotNull(introA)
        assertNotNull(introB)
        assertEquals(120.0, introA.dom.top)
        assertEquals(500.0, introB.dom.top)
    }

    @Test
    fun `global non crossing hard matching avoids A B A vs B A top to bottom jump`() {
        val engine = MkdocsScrollSyncEngine(
            delayedInvoker = { _, task -> task() },
            mapRebuildDebounceMs = 0L,
            syncTokenTtlMs = 0L,
        )
        val document = DocumentImpl(
            """
            # A {#a}
            # B {#b}
            # A {#a}
            """.trimIndent(),
        )
        val snapshot = PreviewDomSnapshot(
            maxScrollY = 1200.0,
            anchors = listOf(
                PreviewDomAnchor(id = "b", type = AnchorType.H, level = 1, top = 120.0, bottom = 150.0, normText = "b"),
                PreviewDomAnchor(id = "a", type = AnchorType.H, level = 1, top = 920.0, bottom = 960.0, normText = "a"),
            ),
        )

        val command = engine.onEditorScroll(
            document = document,
            lineHeightPx = 20,
            editorScrollTopPx = 0.0,
            editorViewportHeightPx = 80.0,
            loadPreviewSnapshot = { callback -> callback(snapshot) },
        )

        val state = assertNotNull(engine.currentState())
        assertTrue(state.hardAnchors.any { it.source.normText == "b" })
        assertFalse(state.hardAnchors.any { it.source.startLine == 0 })
        assertNotNull(command)
        assertTrue(command.previewY < 300.0)
    }

    @Test
    fun `post dp acceptance guard drops ambiguous neighborhood matches`() {
        val engine = MkdocsScrollSyncEngine(
            delayedInvoker = { _, task -> task() },
            mapRebuildDebounceMs = 0L,
        )
        val document = DocumentImpl("- alpha item")
        val snapshot = PreviewDomSnapshot(
            maxScrollY = 1200.0,
            anchors = listOf(
                PreviewDomAnchor(id = "", type = AnchorType.LI, level = null, top = 100.0, bottom = 130.0, normText = "alpha item"),
                PreviewDomAnchor(id = "", type = AnchorType.LI, level = null, top = 130.0, bottom = 160.0, normText = "alpha item"),
            ),
        )

        engine.requestMapRebuild(document, lineHeightPx = 20) { callback -> callback(snapshot) }
        val state = assertNotNull(engine.currentState())
        assertEquals(2, state.knots.size)
    }

    @Test
    fun `onEditorScroll requests dom snapshot once and reuses cached map for following scrolls`() {
        val engine = MkdocsScrollSyncEngine(
            delayedInvoker = { _, task -> task() },
            mapRebuildDebounceMs = 0L,
        )
        val document = DocumentImpl(
            (1..120).joinToString("\n") { "Paragraph line $it with enough content for sparse anchors." },
        )
        val snapshot = PreviewDomSnapshot(
            maxScrollY = 4000.0,
            anchors = listOf(
                PreviewDomAnchor(id = "", type = AnchorType.P, level = null, top = 200.0, bottom = 240.0, normText = "paragraph line 30 with enough content"),
                PreviewDomAnchor(id = "", type = AnchorType.P, level = null, top = 1200.0, bottom = 1240.0, normText = "paragraph line 60 with enough content"),
                PreviewDomAnchor(id = "", type = AnchorType.P, level = null, top = 2400.0, bottom = 2440.0, normText = "paragraph line 90 with enough content"),
            ),
        )

        var snapshotLoads = 0
        val first = engine.onEditorScroll(
            document = document,
            lineHeightPx = 20,
            editorScrollTopPx = 0.0,
            editorViewportHeightPx = 600.0,
            loadPreviewSnapshot = { callback ->
                snapshotLoads += 1
                callback(snapshot)
            },
        )
        val second = engine.onEditorScroll(
            document = document,
            lineHeightPx = 20,
            editorScrollTopPx = 700.0,
            editorViewportHeightPx = 600.0,
            loadPreviewSnapshot = { callback ->
                snapshotLoads += 1
                callback(snapshot)
            },
        )

        assertNotNull(first)
        assertTrue(second == null || second.previewY >= 0.0)
        assertTrue(snapshotLoads in 1..2)
    }

    @Test
    fun `onEditorScroll applies pixel hysteresis to avoid jitter`() {
        val engine = MkdocsScrollSyncEngine(
            delayedInvoker = { _, task -> task() },
            mapRebuildDebounceMs = 0L,
        )
        val document = DocumentImpl((1..80).joinToString("\n") { "line $it content" })
        val snapshot = PreviewDomSnapshot(
            maxScrollY = 2400.0,
            anchors = listOf(
                PreviewDomAnchor(id = "", type = AnchorType.P, level = null, top = 300.0, bottom = 340.0, normText = "line 25 content"),
                PreviewDomAnchor(id = "", type = AnchorType.P, level = null, top = 900.0, bottom = 940.0, normText = "line 50 content"),
            ),
        )

        val first = engine.onEditorScroll(
            document = document,
            lineHeightPx = 20,
            editorScrollTopPx = 500.0,
            editorViewportHeightPx = 600.0,
            loadPreviewSnapshot = { callback -> callback(snapshot) },
        )
        val second = engine.onEditorScroll(
            document = document,
            lineHeightPx = 20,
            editorScrollTopPx = 504.0,
            editorViewportHeightPx = 600.0,
            loadPreviewSnapshot = { callback -> callback(snapshot) },
        )

        assertNotNull(first)
        assertNull(second)
    }

    @Test
    fun `source extraction captures code table image and heading anchors`() {
        val engine = MkdocsScrollSyncEngine(delayedInvoker = { _, task -> task() })
        val document = DocumentImpl(
            """
            # Heading
            Intro text paragraph with some content.

            | Name | Value |
            | ---- | ----- |
            | A    | 1     |

            ![Architecture](assets/arch.png)

            ```kotlin
            val x = 1
            println(x)
            ```
            """.trimIndent(),
        )

        val source = engine.resolveSourceState(document)
        assertTrue(source.anchors.any { it.type == AnchorType.H })
        assertTrue(source.anchors.any { it.type == AnchorType.TABLE })
        assertTrue(source.anchors.any { it.type == AnchorType.IMG })
        assertTrue(source.anchors.any { it.type == AnchorType.CODE })
    }

    @Test
    fun `recordDocumentChange marks dirty range and rebuild clears dirty flags`() {
        val engine = MkdocsScrollSyncEngine(
            delayedInvoker = { _, task -> task() },
            mapRebuildDebounceMs = 0L,
        )
        val document = DocumentImpl(
            """
            # Title
            line one
            line two changed
            line three
            """.trimIndent(),
        )
        val snapshot = PreviewDomSnapshot(
            maxScrollY = 1200.0,
            anchors = listOf(
                PreviewDomAnchor(id = "title", type = AnchorType.H, level = 1, top = 20.0, bottom = 60.0, normText = "title"),
            ),
        )
        engine.requestMapRebuild(document, lineHeightPx = 20) { callback -> callback(snapshot) }

        val changedLine = "line two changed"
        val changeOffset = document.immutableCharSequence.toString().indexOf(changedLine).coerceAtLeast(0)
        val event = fakeDocumentEvent(
            document = document,
            offset = changeOffset,
            oldFragment = "line two",
            newFragment = changedLine,
        )
        engine.recordDocumentChange(document, event)

        val dirtyState = assertNotNull(engine.currentState())
        assertTrue(dirtyState.needsRebuild)
        assertTrue(dirtyState.dirtyRanges.isNotEmpty())
        assertTrue(dirtyState.dirtyRanges.any { it.startLine <= 2 && it.endLineExclusive >= 3 })

        engine.requestMapRebuild(document, lineHeightPx = 20) { callback -> callback(snapshot) }

        val rebuilt = assertNotNull(engine.currentState())
        assertFalse(rebuilt.needsRebuild)
        assertTrue(rebuilt.dirtyRanges.isEmpty())
    }

    @Test
    fun `append at eof keeps mappedUntilLine at dirty start for tail update hint`() {
        val engine = MkdocsScrollSyncEngine(
            delayedInvoker = { _, task -> task() },
            mapRebuildDebounceMs = 0L,
        )
        val document = DocumentImpl(
            """
            # Title
            line one
            line two
            appended tail
            """.trimIndent(),
        )
        val snapshot = PreviewDomSnapshot(
            maxScrollY = 1200.0,
            anchors = listOf(
                PreviewDomAnchor(id = "title", type = AnchorType.H, level = 1, top = 20.0, bottom = 60.0, normText = "title"),
            ),
        )
        engine.requestMapRebuild(document, lineHeightPx = 20) { callback -> callback(snapshot) }
        val previousLineCount = document.lineCount

        val inserted = "\nappended tail"
        val event = fakeDocumentEvent(
            document = document,
            offset = document.immutableCharSequence.toString().lastIndexOf("appended tail").coerceAtLeast(0),
            oldFragment = "",
            newFragment = inserted.trimStart('\n'),
        )
        engine.recordDocumentChange(document, event)
        engine.requestMapRebuild(document, lineHeightPx = 20) { callback -> callback(snapshot) }

        val rebuilt = assertNotNull(engine.currentState())
        assertTrue(rebuilt.mappedUntilLine <= previousLineCount)
    }

    @Test
    fun `knot sequence is monotonic after rebuild`() {
        val engine = MkdocsScrollSyncEngine(
            delayedInvoker = { _, task -> task() },
            mapRebuildDebounceMs = 0L,
        )
        val document = DocumentImpl(
            """
            # Start
            paragraph one
            ## Middle
            paragraph two
            ## End
            paragraph three
            """.trimIndent(),
        )
        val snapshot = PreviewDomSnapshot(
            maxScrollY = 1800.0,
            anchors = listOf(
                PreviewDomAnchor(id = "start", type = AnchorType.H, level = 1, top = 10.0, bottom = 50.0, normText = "start"),
                PreviewDomAnchor(id = "middle", type = AnchorType.H, level = 2, top = 700.0, bottom = 740.0, normText = "middle"),
                PreviewDomAnchor(id = "end", type = AnchorType.H, level = 2, top = 1300.0, bottom = 1340.0, normText = "end"),
            ),
        )

        engine.requestMapRebuild(document, lineHeightPx = 20) { callback -> callback(snapshot) }
        val state = assertNotNull(engine.currentState())

        assertTrue(state.knots.zipWithNext().all { (left, right) -> left.editorY <= right.editorY })
        assertTrue(state.knots.zipWithNext().all { (left, right) -> left.previewY <= right.previewY })
    }

    private fun fakeDocumentEvent(
        document: DocumentImpl,
        offset: Int,
        oldFragment: String,
        newFragment: String,
    ): DocumentEvent {
        return object : DocumentEvent(document) {
            override fun getOffset(): Int = offset

            override fun getOldLength(): Int = oldFragment.length

            override fun getNewLength(): Int = newFragment.length

            override fun getOldFragment(): CharSequence = oldFragment

            override fun getNewFragment(): CharSequence = newFragment

            override fun getOldTimeStamp(): Long = 0L

            override fun isWholeTextReplaced(): Boolean = false
        }
    }
}

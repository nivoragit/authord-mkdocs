package com.authord.mkdocs.ui.intellij

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.DocumentEvent
import java.text.Normalizer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs

enum class AnchorType {
    H,
    CODE,
    TABLE,
    IMG,
    BQ,
    LI,
    P,
    HR,
}

internal data class SourceAnchor(
    val id: String,
    val type: AnchorType,
    val level: Int?,
    val startLine: Int,
    val endLine: Int,
    val normText: String,
    val occ: Int,
    val strength: Double,
    val segmentKey: String,
)

internal data class DomAnchor(
    val id: String,
    val type: AnchorType,
    val level: Int?,
    val top: Double,
    val bottom: Double,
    val normText: String,
    val occ: Int,
    val strength: Double,
    val segmentKey: String?,
)

internal data class Knot(
    val editorY: Double,
    val previewY: Double,
    val hard: Boolean,
    val confidence: Double,
)

internal data class LineRange(
    val startLine: Int,
    val endLineExclusive: Int,
)

internal data class HardAnchorMatch(
    val source: SourceAnchor,
    val dom: DomAnchor,
    val confidence: Double,
)

internal data class MapState(
    val docVersion: Long,
    val knots: List<Knot>,
    val hardAnchors: List<HardAnchorMatch>,
    val dirtyRanges: List<LineRange>,
    val mappedUntilLine: Int,
    val maxPreviewScrollY: Double,
    val lastSentPreviewY: Double,
    val lastSyncToken: Long,
    val lineHeightPx: Int,
    val needsRebuild: Boolean,
)

internal data class PreviewScrollCommand(
    val previewY: Double,
    val syncToken: Long,
)

internal data class SourceDocumentState(
    val documentIdentity: Int,
    val modificationStamp: Long,
    val lineCount: Int,
    val lines: List<String>,
    val anchors: List<SourceAnchor>,
)

private data class MutableAnchorDraft(
    val type: AnchorType,
    val level: Int?,
    val startLine: Int,
    val endLine: Int,
    val normText: String,
    val strength: Double,
    val segmentKey: String,
    val key: String,
)

private data class SourceAnchorSegment(
    val sourceStartLine: Int,
    val sourceEndLineExclusive: Int,
    val previewStartY: Double,
    val previewEndY: Double,
)

private data class AnchorMatch(
    val source: SourceAnchor,
    val dom: DomAnchor,
    val confidence: Double,
    val hard: Boolean,
)

private data class CandidateEdge(
    val sourceIndex: Int,
    val domIndex: Int,
    val preliminaryScore: Double,
    val finalScore: Double,
)

private data class FenceRegion(
    val startLine: Int,
    val endLineExclusive: Int,
    val language: String,
    val firstMeaningfulLine: String,
)

internal class MkdocsScrollSyncEngine(
    private val delayedInvoker: (delayMillis: Long, task: () -> Unit) -> Unit,
    private val mapRebuildDebounceMs: Long = 120L,
    private val editorFocusRatio: Double = 0.35,
    private val pixelHysteresisPx: Double = 16.0,
    private val syncTokenTtlMs: Long = 120L,
    private val minAnchorConfidence: Double = 0.82,
    private val confidenceMargin: Double = 0.10,
    private val softParagraphStrideLines: Int = 32,
) {
    private val fenceRegex = Regex("""^(?<fence>`{3,}|~{3,})[ \t]*([A-Za-z0-9_+-]+)?[^\n]*$""")
    private val setextRegex = Regex("""^(=+|-+)[ \t]*$""")
    private val atxRegex = Regex("""^(#{1,6})[ \t]+(.+?)\s*$""")
    private val headingIdRegex = Regex("""\{\s*#([A-Za-z0-9_-]+)\s*\}\s*$|\{:\s*#([A-Za-z0-9_-]+)\s*\}\s*$""")
    private val markdownCommentRegex = Regex("""^[ \t]{0,3}\[//\]:\s*#(?:\s*\(.*\)\s*|.*)?$""")
    private val combinedLineRegex = Regex(
        """^(?:(#{1,6}[ \t]+.+?)\s*$|([ \t]{0,3}>[ \t]?.+)$|([ \t]{0,3}(?:[-*+]|\d+\.)[ \t]+.+)$|(\|.+\|[ \t]*)$|([ \t]{0,3}(?:-{3,}|\*{3,}|_{3,})[ \t]*))""",
    )
    private val imageInlineRegex = Regex("""!\[(.*?)\]\((.*?)\)""")
    private val candidateTopK: Int = 4
    private val hardAnchorPreliminaryFloor: Double = 0.60
    private val mediumAnchorPreliminaryFloor: Double = 0.72

    @Volatile
    private var sourceCache: SourceDocumentState? = null

    @Volatile
    private var mapState: MapState? = null

    @Volatile
    private var pendingDirtyRanges: List<LineRange> = emptyList()

    @Volatile
    private var appendOnlyDirtyHint: Boolean = false

    @Volatile
    private var suppressUntilMs: Long = 0L

    private val rebuildSequence = AtomicLong(0)
    private val rebuildInFlight = AtomicBoolean(false)

    fun focusRatio(): Double = editorFocusRatio

    fun resolveSourceState(document: Document): SourceDocumentState {
        val documentIdentity = System.identityHashCode(document)
        val modificationStamp = document.modificationStamp
        val lineCount = document.lineCount
        val cached = sourceCache

        if (
            cached != null &&
            cached.documentIdentity == documentIdentity &&
            cached.modificationStamp == modificationStamp &&
            cached.lineCount == lineCount
        ) {
            return cached
        }

        val lines = extractLines(document)
        val anchors = extractSourceAnchors(lines)
        val rebuilt = SourceDocumentState(
            documentIdentity = documentIdentity,
            modificationStamp = modificationStamp,
            lineCount = lineCount,
            lines = lines,
            anchors = anchors,
        )
        sourceCache = rebuilt
        return rebuilt
    }

    fun currentState(): MapState? = mapState

    fun invalidateAnchors() {
        val current = mapState
        mapState = if (current == null) {
            null
        } else {
            current.copy(needsRebuild = true)
        }
    }

    fun resetSyncState() {
        val current = mapState
        mapState = if (current == null) {
            null
        } else {
            current.copy(lastSentPreviewY = Double.NaN)
        }
        suppressUntilMs = 0L
    }

    fun recordDocumentChange(document: Document, event: DocumentEvent) {
        val previousLineCount = sourceCache?.lineCount ?: document.lineCount
        sourceCache = null

        val startOffset = event.offset.coerceIn(0, document.textLength)
        val startLine = document.getLineNumber(startOffset)
        val oldLineBreaks = countLineBreaks(event.oldFragment)
        val newLineBreaks = countLineBreaks(event.newFragment)
        val oldEndLine = startLine + oldLineBreaks
        val newEndOffset = (event.offset + event.newLength).coerceIn(0, document.textLength)
        val newEndLine = document.getLineNumber(newEndOffset)

        val source = resolveSourceState(document)
        val expandedRange = expandDirtyRange(
            lines = source.lines,
            startLine = startLine,
            endLineExclusive = (maxOf(oldEndLine, newEndLine) + 1),
        )
        pendingDirtyRanges = mergeRanges(pendingDirtyRanges + expandedRange)

        val appendOnly = startLine >= (previousLineCount - 2).coerceAtLeast(0) && newLineBreaks >= oldLineBreaks
        appendOnlyDirtyHint = appendOnlyDirtyHint || appendOnly

        val current = mapState
        if (current != null) {
            mapState = current.copy(
                needsRebuild = true,
                dirtyRanges = mergeRanges(current.dirtyRanges + expandedRange),
            )
        }
    }

    fun requestMapRebuild(
        document: Document,
        lineHeightPx: Int,
        loadPreviewSnapshot: ((PreviewDomSnapshot?) -> Unit) -> Unit,
    ) {
        val sequence = rebuildSequence.incrementAndGet()
        delayedInvoker(mapRebuildDebounceMs) {
            if (sequence != rebuildSequence.get()) {
                return@delayedInvoker
            }

            if (!rebuildInFlight.compareAndSet(false, true)) {
                return@delayedInvoker
            }

            val expectedDocVersion = document.modificationStamp
            loadPreviewSnapshot { snapshot ->
                try {
                    if (document.modificationStamp != expectedDocVersion) {
                        return@loadPreviewSnapshot
                    }

                    val source = resolveSourceState(document)
                    val rebuilt = buildMapState(
                        source = source,
                        lineHeightPx = lineHeightPx.coerceAtLeast(1),
                        previewSnapshot = snapshot,
                        previousState = mapState,
                        dirtyRanges = pendingDirtyRanges,
                        appendOnlyHint = appendOnlyDirtyHint,
                    )
                    mapState = rebuilt
                    pendingDirtyRanges = emptyList()
                    appendOnlyDirtyHint = false
                } finally {
                    rebuildInFlight.set(false)
                    if (sequence != rebuildSequence.get()) {
                        requestMapRebuild(document, lineHeightPx, loadPreviewSnapshot)
                    }
                }
            }
        }
    }

    fun onEditorScroll(
        document: Document,
        lineHeightPx: Int,
        editorScrollTopPx: Double,
        editorViewportHeightPx: Double,
        loadPreviewSnapshot: (((PreviewDomSnapshot?) -> Unit) -> Unit)? = null,
    ): PreviewScrollCommand? {
        if (isSuppressed()) {
            return null
        }

        val source = resolveSourceState(document)
        val state = mapState
        val needsRebuild = state == null ||
            state.docVersion != source.modificationStamp ||
            state.needsRebuild ||
            state.lineHeightPx != lineHeightPx

        if (needsRebuild && loadPreviewSnapshot != null) {
            requestMapRebuild(document, lineHeightPx, loadPreviewSnapshot)
        }

        val activeState = mapState
        if (activeState == null || activeState.knots.size < 2) {
            return coarseFallback(source, editorScrollTopPx, editorViewportHeightPx, lineHeightPx)
        }

        val editorFocusY = editorScrollTopPx + editorViewportHeightPx * editorFocusRatio
        val mappedPreviewY = interpolate(activeState.knots, editorFocusY)
            .coerceIn(0.0, activeState.maxPreviewScrollY.coerceAtLeast(0.0))

        val lastSent = activeState.lastSentPreviewY
        if (lastSent.isFinite() && abs(mappedPreviewY - lastSent) < pixelHysteresisPx) {
            return null
        }

        val nextToken = activeState.lastSyncToken + 1L
        mapState = activeState.copy(
            lastSentPreviewY = mappedPreviewY,
            lastSyncToken = nextToken,
        )
        suppressUntilMs = System.currentTimeMillis() + syncTokenTtlMs
        return PreviewScrollCommand(
            previewY = mappedPreviewY,
            syncToken = nextToken,
        )
    }

    private fun coarseFallback(
        source: SourceDocumentState,
        editorScrollTopPx: Double,
        editorViewportHeightPx: Double,
        lineHeightPx: Int,
    ): PreviewScrollCommand? {
        val current = mapState ?: return null
        if (current.maxPreviewScrollY <= 0.0) {
            return null
        }
        val totalEditorHeight = (source.lineCount.coerceAtLeast(1) * lineHeightPx.coerceAtLeast(1)).toDouble()
        if (totalEditorHeight <= 0.0) {
            return null
        }

        val editorFocusY = editorScrollTopPx + editorViewportHeightPx * editorFocusRatio
        val ratio = (editorFocusY / totalEditorHeight).coerceIn(0.0, 1.0)
        val mappedPreviewY = (ratio * current.maxPreviewScrollY).coerceIn(0.0, current.maxPreviewScrollY.coerceAtLeast(0.0))
        val lastSent = current.lastSentPreviewY
        if (lastSent.isFinite() && abs(mappedPreviewY - lastSent) < pixelHysteresisPx) {
            return null
        }

        val nextToken = current.lastSyncToken + 1L
        mapState = current.copy(
            lastSentPreviewY = mappedPreviewY,
            lastSyncToken = nextToken,
        )
        suppressUntilMs = System.currentTimeMillis() + syncTokenTtlMs
        return PreviewScrollCommand(mappedPreviewY, nextToken)
    }

    private fun buildMapState(
        source: SourceDocumentState,
        lineHeightPx: Int,
        previewSnapshot: PreviewDomSnapshot?,
        previousState: MapState?,
        dirtyRanges: List<LineRange>,
        appendOnlyHint: Boolean,
    ): MapState {
        val maxPreviewScrollY = previewSnapshot?.maxScrollY?.coerceAtLeast(0.0) ?: previousState?.maxPreviewScrollY ?: 0.0
        val domAnchors = normalizeDomAnchors(previewSnapshot)
        val hardMatches = matchHardAnchors(
            sourceAnchors = source.anchors,
            domAnchors = domAnchors,
        )
        val allMatches = densifyAnchors(
            sourceAnchors = source.anchors,
            domAnchors = domAnchors,
            hardMatches = hardMatches,
            sourceLineCount = source.lineCount,
            maxPreviewScrollY = maxPreviewScrollY,
        )
        val knots = buildKnots(
            sourceLineCount = source.lineCount,
            lineHeightPx = lineHeightPx,
            maxPreviewScrollY = maxPreviewScrollY,
            matches = allMatches,
        )

        val previousToken = previousState?.lastSyncToken ?: 0L
        val previousSent = previousState?.lastSentPreviewY ?: Double.NaN
        val mappedUntilLine = if (appendOnlyHint && dirtyRanges.isNotEmpty()) {
            dirtyRanges.minOf { it.startLine }.coerceAtLeast(0)
        } else {
            source.lineCount
        }

        return MapState(
            docVersion = source.modificationStamp,
            knots = knots,
            hardAnchors = hardMatches,
            dirtyRanges = emptyList(),
            mappedUntilLine = mappedUntilLine,
            maxPreviewScrollY = maxPreviewScrollY,
            lastSentPreviewY = if (previousSent.isFinite()) {
                previousSent.coerceIn(0.0, maxPreviewScrollY)
            } else {
                Double.NaN
            },
            lastSyncToken = previousToken,
            lineHeightPx = lineHeightPx,
            needsRebuild = false,
        )
    }

    private fun normalizeDomAnchors(snapshot: PreviewDomSnapshot?): List<DomAnchor> {
        val anchors = snapshot?.anchors.orEmpty()
            .asSequence()
            .filter { it.bottom > it.top }
            .sortedBy { it.top }
            .toList()

        if (anchors.isEmpty()) {
            return emptyList()
        }

        val occurrences = mutableMapOf<String, Int>()
        var segmentKey = "root"
        return anchors.map { raw ->
            val canonicalKey = canonicalKeyForDom(raw)
            if (raw.type == AnchorType.H) {
                segmentKey = canonicalKey.ifBlank { segmentKey }
            }
            val occurrenceKey = occurrenceKey(raw.type, raw.level, canonicalKey)
            val occurrence = occurrences[occurrenceKey] ?: 0
            occurrences[occurrenceKey] = occurrence + 1
            val id = buildAnchorId(raw.type, raw.level, canonicalKey, occurrence)
            DomAnchor(
                id = id,
                type = raw.type,
                level = raw.level,
                top = raw.top.coerceAtLeast(0.0),
                bottom = raw.bottom.coerceAtLeast(raw.top + 1.0),
                normText = raw.normText,
                occ = occurrence,
                strength = strengthForType(raw.type),
                segmentKey = segmentKey,
            )
        }
    }

    private fun canonicalKeyForDom(anchor: PreviewDomAnchor): String {
        if (anchor.type == AnchorType.H) {
            val explicit = anchor.id?.trim()?.takeIf { it.isNotBlank() }
            if (explicit != null) {
                return normalizeHeadingId(explicit)
            }
        }
        return slugify(anchor.normText)
    }

    private fun extractSourceAnchors(lines: List<String>): List<SourceAnchor> {
        val safeLines = if (lines.isEmpty()) listOf("") else lines
        val codeRegions = detectFenceRegions(safeLines)
        val inFence = BooleanArray(safeLines.size)
        val codeRegionsByStart = mutableMapOf<Int, FenceRegion>()
        codeRegions.forEach { region ->
            codeRegionsByStart[region.startLine] = region
            for (line in region.startLine until region.endLineExclusive.coerceAtMost(inFence.size)) {
                inFence[line] = true
            }
        }

        val drafts = mutableListOf<MutableAnchorDraft>()
        var currentSegmentKey = "root"
        var lastParagraphAnchorLine = -softParagraphStrideLines

        var index = 0
        while (index < safeLines.size) {
            val codeRegion = codeRegionsByStart[index]
            if (codeRegion != null) {
                val keyBase = listOf(codeRegion.language, codeRegion.firstMeaningfulLine)
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
                    .ifBlank { "code" }
                val normalized = normalizeText(keyBase)
                val key = slugify(normalized)
                drafts += MutableAnchorDraft(
                    type = AnchorType.CODE,
                    level = null,
                    startLine = codeRegion.startLine,
                    endLine = codeRegion.endLineExclusive,
                    normText = normalized,
                    strength = strengthForType(AnchorType.CODE),
                    segmentKey = currentSegmentKey,
                    key = key,
                )
                index = codeRegion.endLineExclusive
                continue
            }

            if (inFence[index]) {
                index += 1
                continue
            }

            val line = safeLines[index]
            val trimmed = line.trim()
            if (isMarkdownCommentLine(line)) {
                index += 1
                continue
            }

            if (isSetextHeadingStart(safeLines, index, inFence)) {
                val next = safeLines[index + 1].trim()
                val level = if (next.startsWith('=')) 1 else 2
                val normalized = normalizeText(trimmed)
                val key = slugify(normalized)
                drafts += MutableAnchorDraft(
                    type = AnchorType.H,
                    level = level,
                    startLine = index,
                    endLine = (index + 2).coerceAtMost(safeLines.size),
                    normText = normalized,
                    strength = strengthForType(AnchorType.H),
                    segmentKey = currentSegmentKey,
                    key = key,
                )
                currentSegmentKey = key.ifBlank { currentSegmentKey }
                index += 2
                continue
            }

            val match = combinedLineRegex.matchEntire(line)
            if (match != null) {
                val atxCandidate = match.groupValues.getOrNull(1).orEmpty()
                val blockquoteCandidate = match.groupValues.getOrNull(2).orEmpty()
                val listCandidate = match.groupValues.getOrNull(3).orEmpty()
                val tableCandidate = match.groupValues.getOrNull(4).orEmpty()
                val hrCandidate = match.groupValues.getOrNull(5).orEmpty()

                if (atxCandidate.isNotBlank()) {
                    parseAtxHeading(atxCandidate)?.let { parsed ->
                        val explicitId = parseTrailingHeadingId(parsed.text)
                        val cleanedText = explicitId.second
                        val normalized = normalizeText(cleanedText)
                        val headingKey = explicitId.first?.let(::normalizeHeadingId)
                            ?.ifBlank { null }
                            ?: slugify(normalized)
                        drafts += MutableAnchorDraft(
                            type = AnchorType.H,
                            level = parsed.level,
                            startLine = index,
                            endLine = (index + 1).coerceAtMost(safeLines.size),
                            normText = normalized,
                            strength = strengthForType(AnchorType.H),
                            segmentKey = currentSegmentKey,
                            key = headingKey,
                        )
                        currentSegmentKey = headingKey.ifBlank { currentSegmentKey }
                    }
                } else if (tableCandidate.isNotBlank() && isTableHeaderRow(safeLines, index, inFence)) {
                    val normalized = normalizeText(normalizeTableHeader(tableCandidate))
                    val key = slugify(normalized)
                    drafts += MutableAnchorDraft(
                        type = AnchorType.TABLE,
                        level = null,
                        startLine = index,
                        endLine = (index + 2).coerceAtMost(safeLines.size),
                        normText = normalized,
                        strength = strengthForType(AnchorType.TABLE),
                        segmentKey = currentSegmentKey,
                        key = key,
                    )
                } else if (blockquoteCandidate.isNotBlank()) {
                    val normalized = normalizeText(fingerprintText(blockquoteCandidate.removePrefix(">").trim()))
                    val key = slugify(normalized)
                    if (key.isNotBlank()) {
                        drafts += MutableAnchorDraft(
                            type = AnchorType.BQ,
                            level = null,
                            startLine = index,
                            endLine = (index + 1).coerceAtMost(safeLines.size),
                            normText = normalized,
                            strength = strengthForType(AnchorType.BQ),
                            segmentKey = currentSegmentKey,
                            key = key,
                        )
                    }
                } else if (listCandidate.isNotBlank()) {
                    val cleaned = listCandidate.replace(Regex("""^[ \t]{0,3}(?:[-*+]|\d+\.)[ \t]+"""), "")
                    val normalized = normalizeText(fingerprintText(cleaned))
                    val key = slugify(normalized)
                    if (key.isNotBlank()) {
                        drafts += MutableAnchorDraft(
                            type = AnchorType.LI,
                            level = null,
                            startLine = index,
                            endLine = (index + 1).coerceAtMost(safeLines.size),
                            normText = normalized,
                            strength = strengthForType(AnchorType.LI),
                            segmentKey = currentSegmentKey,
                            key = key,
                        )
                    }
                } else if (hrCandidate.isNotBlank()) {
                    drafts += MutableAnchorDraft(
                        type = AnchorType.HR,
                        level = null,
                        startLine = index,
                        endLine = (index + 1).coerceAtMost(safeLines.size),
                        normText = "hr",
                        strength = strengthForType(AnchorType.HR),
                        segmentKey = currentSegmentKey,
                        key = "hr",
                    )
                }
            }

            if (trimmed.isNotEmpty()) {
                imageInlineRegex.findAll(line).forEachIndexed { imageIndex, imageMatch ->
                    val alt = imageMatch.groupValues.getOrElse(1) { "" }
                    val src = imageMatch.groupValues.getOrElse(2) { "" }
                    val imageSignature = "$alt ${basename(src)}".trim()
                    val normalized = normalizeText(imageSignature)
                    val key = slugify("$normalized-$imageIndex")
                    if (key.isNotBlank()) {
                        drafts += MutableAnchorDraft(
                            type = AnchorType.IMG,
                            level = null,
                            startLine = index,
                            endLine = (index + 1).coerceAtMost(safeLines.size),
                            normText = normalized,
                            strength = strengthForType(AnchorType.IMG),
                            segmentKey = currentSegmentKey,
                            key = key,
                        )
                    }
                }
            }

            if (
                index - lastParagraphAnchorLine >= softParagraphStrideLines &&
                isParagraphCandidate(line)
            ) {
                val normalized = normalizeText(fingerprintText(line))
                val key = slugify(normalized)
                if (key.isNotBlank()) {
                    drafts += MutableAnchorDraft(
                        type = AnchorType.P,
                        level = null,
                        startLine = index,
                        endLine = (index + 1).coerceAtMost(safeLines.size),
                        normText = normalized,
                        strength = strengthForType(AnchorType.P),
                        segmentKey = currentSegmentKey,
                        key = key,
                    )
                    lastParagraphAnchorLine = index
                }
            }

            index += 1
        }

        return materializeSourceAnchors(drafts)
    }

    private fun detectFenceRegions(lines: List<String>): List<FenceRegion> {
        if (lines.isEmpty()) {
            return emptyList()
        }

        data class OpenFence(
            val startLine: Int,
            val marker: Char,
            val length: Int,
            val language: String,
            var firstMeaningfulLine: String,
        )

        val regions = mutableListOf<FenceRegion>()
        var openFence: OpenFence? = null

        lines.forEachIndexed { index, line ->
            val trimmedLine = line.trimEnd()
            val fenceMatch = fenceRegex.matchEntire(trimmedLine)
            if (openFence == null) {
                if (fenceMatch != null) {
                    val markerToken = fenceMatch.groups["fence"]?.value.orEmpty()
                    if (markerToken.isNotBlank()) {
                        openFence = OpenFence(
                            startLine = index,
                            marker = markerToken.first(),
                            length = markerToken.length,
                            language = fenceMatch.groupValues.getOrElse(2) { "" }.trim(),
                            firstMeaningfulLine = "",
                        )
                    }
                }
                return@forEachIndexed
            }

            val currentOpen = openFence ?: return@forEachIndexed
            if (fenceMatch != null) {
                val markerToken = fenceMatch.groups["fence"]?.value.orEmpty()
                if (
                    markerToken.isNotBlank() &&
                    markerToken.first() == currentOpen.marker &&
                    markerToken.length >= currentOpen.length
                ) {
                    regions += FenceRegion(
                        startLine = currentOpen.startLine,
                        endLineExclusive = index + 1,
                        language = currentOpen.language,
                        firstMeaningfulLine = currentOpen.firstMeaningfulLine,
                    )
                    openFence = null
                    return@forEachIndexed
                }
            }

            if (currentOpen.firstMeaningfulLine.isBlank()) {
                val normalized = normalizeText(line)
                if (normalized.isNotBlank()) {
                    currentOpen.firstMeaningfulLine = normalized
                }
            }
        }

        val trailing = openFence
        if (trailing != null) {
            regions += FenceRegion(
                startLine = trailing.startLine,
                endLineExclusive = lines.size,
                language = trailing.language,
                firstMeaningfulLine = trailing.firstMeaningfulLine,
            )
        }

        return regions
    }

    private fun materializeSourceAnchors(drafts: List<MutableAnchorDraft>): List<SourceAnchor> {
        if (drafts.isEmpty()) {
            return emptyList()
        }

        val orderedDrafts = drafts
            .sortedWith(compareBy<MutableAnchorDraft> { it.startLine }.thenBy { it.type.ordinal })
        val occurrences = mutableMapOf<String, Int>()

        return orderedDrafts.map { draft ->
            val occurrenceKey = occurrenceKey(draft.type, draft.level, draft.key)
            val occurrence = occurrences[occurrenceKey] ?: 0
            occurrences[occurrenceKey] = occurrence + 1
            SourceAnchor(
                id = buildAnchorId(draft.type, draft.level, draft.key, occurrence),
                type = draft.type,
                level = draft.level,
                startLine = draft.startLine,
                endLine = draft.endLine.coerceAtLeast(draft.startLine + 1),
                normText = draft.normText,
                occ = occurrence,
                strength = draft.strength,
                segmentKey = draft.segmentKey,
            )
        }
    }

    private fun matchHardAnchors(
        sourceAnchors: List<SourceAnchor>,
        domAnchors: List<DomAnchor>,
    ): List<HardAnchorMatch> {
        val sourceHeadings = sourceAnchors
            .filter { it.type == AnchorType.H }
            .sortedBy { it.startLine }
        val domHeadings = domAnchors
            .filter { it.type == AnchorType.H }
            .sortedBy { it.top }
        if (sourceHeadings.isEmpty() || domHeadings.isEmpty()) {
            return emptyList()
        }

        val segment = SourceAnchorSegment(
            sourceStartLine = sourceHeadings.first().startLine.coerceAtLeast(0),
            sourceEndLineExclusive = (sourceHeadings.last().endLine + 1).coerceAtLeast(sourceHeadings.first().startLine + 1),
            previewStartY = domHeadings.first().top.coerceAtLeast(0.0),
            previewEndY = domHeadings.last().bottom.coerceAtLeast(domHeadings.first().top + 1.0),
        )
        val aligned = alignAnchorsWithDp(
            sourceAnchors = sourceHeadings,
            domAnchors = domHeadings,
            segment = segment,
            hard = true,
        )
        return aligned
            .sortedBy { it.source.startLine }
            .map { match ->
                HardAnchorMatch(
                    source = match.source,
                    dom = match.dom,
                    confidence = match.confidence,
                )
            }
    }

    private fun densifyAnchors(
        sourceAnchors: List<SourceAnchor>,
        domAnchors: List<DomAnchor>,
        hardMatches: List<HardAnchorMatch>,
        sourceLineCount: Int,
        maxPreviewScrollY: Double,
    ): List<AnchorMatch> {
        val hard = hardMatches.map {
            AnchorMatch(source = it.source, dom = it.dom, confidence = it.confidence, hard = true)
        }
        if (sourceAnchors.isEmpty() || domAnchors.isEmpty()) {
            return hard
        }

        val usedDomIds = hard.mapTo(mutableSetOf()) { it.dom.id }
        val segments = buildSegments(hardMatches, sourceLineCount, maxPreviewScrollY)
        val densified = mutableListOf<AnchorMatch>()

        segmentLoop@ for (segment in segments) {
            val sourceInSegment = sourceAnchors.filter {
                it.type != AnchorType.H &&
                    it.startLine >= segment.sourceStartLine &&
                    it.startLine < segment.sourceEndLineExclusive
            }.sortedBy { it.startLine }
            val domInSegment = domAnchors.filter {
                it.id !in usedDomIds &&
                    it.top >= segment.previewStartY &&
                    it.top <= segment.previewEndY
            }.sortedBy { it.top }
            if (sourceInSegment.isEmpty() || domInSegment.isEmpty()) {
                continue@segmentLoop
            }

            val aligned = alignAnchorsWithDp(
                sourceAnchors = sourceInSegment,
                domAnchors = domInSegment,
                segment = segment,
                hard = false,
            )
            aligned.forEach { match ->
                if (usedDomIds.add(match.dom.id)) {
                    densified += match
                }
            }
        }

        return (hard + densified)
            .sortedWith(compareBy<AnchorMatch> { it.source.startLine }.thenBy { it.dom.top })
    }

    private fun alignAnchorsWithDp(
        sourceAnchors: List<SourceAnchor>,
        domAnchors: List<DomAnchor>,
        segment: SourceAnchorSegment,
        hard: Boolean,
    ): List<AnchorMatch> {
        if (sourceAnchors.isEmpty() || domAnchors.isEmpty()) {
            return emptyList()
        }

        val candidatesBySource = mutableMapOf<Int, List<CandidateEdge>>()
        val edgeByPair = mutableMapOf<Pair<Int, Int>, CandidateEdge>()
        sourceAnchors.forEachIndexed { sourceIndex, sourceAnchor ->
            val floor = preliminaryFloorFor(sourceAnchor.type)
            val filtered = domAnchors.mapIndexedNotNull { domIndex, domAnchor ->
                val score = preliminaryScore(
                    source = sourceAnchor,
                    dom = domAnchor,
                    segment = segment,
                ) ?: return@mapIndexedNotNull null
                if (score < floor) {
                    return@mapIndexedNotNull null
                }
                CandidateEdge(
                    sourceIndex = sourceIndex,
                    domIndex = domIndex,
                    preliminaryScore = score,
                    finalScore = score,
                )
            }
                .sortedByDescending { it.preliminaryScore }
                .take(candidateTopK)
            if (filtered.isNotEmpty()) {
                candidatesBySource[sourceIndex] = filtered
                filtered.forEach { edge ->
                    edgeByPair[sourceIndex to edge.domIndex] = edge
                }
            }
        }

        if (edgeByPair.isEmpty()) {
            return emptyList()
        }

        val sourceCount = sourceAnchors.size
        val domCount = domAnchors.size
        val dp = Array(sourceCount + 1) { DoubleArray(domCount + 1) { Double.NEGATIVE_INFINITY } }
        val action = Array(sourceCount + 1) { IntArray(domCount + 1) }
        dp[0][0] = 0.0

        for (sourceIndex in 1..sourceCount) {
            dp[sourceIndex][0] = dp[sourceIndex - 1][0] - skipSourcePenalty(sourceAnchors[sourceIndex - 1])
            action[sourceIndex][0] = 1
        }
        for (domIndex in 1..domCount) {
            dp[0][domIndex] = dp[0][domIndex - 1] - skipDomPenalty(domAnchors[domIndex - 1])
            action[0][domIndex] = 2
        }

        for (sourceIndex in 1..sourceCount) {
            for (domIndex in 1..domCount) {
                var bestScore = dp[sourceIndex - 1][domIndex] - skipSourcePenalty(sourceAnchors[sourceIndex - 1])
                var bestAction = 1

                val skipDomScore = dp[sourceIndex][domIndex - 1] - skipDomPenalty(domAnchors[domIndex - 1])
                if (skipDomScore > bestScore) {
                    bestScore = skipDomScore
                    bestAction = 2
                }

                val edge = edgeByPair[(sourceIndex - 1) to (domIndex - 1)]
                if (edge != null) {
                    val matchScore = dp[sourceIndex - 1][domIndex - 1] + edge.finalScore
                    if (matchScore > bestScore || (abs(matchScore - bestScore) <= 1e-9 && bestAction != 3)) {
                        bestScore = matchScore
                        bestAction = 3
                    }
                }

                dp[sourceIndex][domIndex] = bestScore
                action[sourceIndex][domIndex] = bestAction
            }
        }

        var sourceCursor = sourceCount
        var domCursor = domCount
        val matchedEdges = mutableListOf<CandidateEdge>()
        while (sourceCursor > 0 || domCursor > 0) {
            val step = action[sourceCursor][domCursor]
            when {
                sourceCursor > 0 && domCursor > 0 && step == 3 -> {
                    edgeByPair[(sourceCursor - 1) to (domCursor - 1)]?.let(matchedEdges::add)
                    sourceCursor -= 1
                    domCursor -= 1
                }

                sourceCursor > 0 && (domCursor == 0 || step == 1) -> {
                    sourceCursor -= 1
                }

                domCursor > 0 -> {
                    domCursor -= 1
                }

                else -> break
            }
        }

        val accepted = applyPostDpAcceptanceGuard(
            rawMatches = matchedEdges.asReversed(),
            candidatesBySource = candidatesBySource,
        )
        return accepted.map { edge ->
            AnchorMatch(
                source = sourceAnchors[edge.sourceIndex],
                dom = domAnchors[edge.domIndex],
                confidence = edge.finalScore,
                hard = hard,
            )
        }
    }

    private fun applyPostDpAcceptanceGuard(
        rawMatches: List<CandidateEdge>,
        candidatesBySource: Map<Int, List<CandidateEdge>>,
    ): List<CandidateEdge> {
        if (rawMatches.isEmpty()) {
            return emptyList()
        }
        return rawMatches.filter { edge ->
            if (edge.finalScore < minAnchorConfidence) {
                return@filter false
            }

            val alternatives = candidatesBySource[edge.sourceIndex]
                .orEmpty()
                .asSequence()
                .filter { it.domIndex != edge.domIndex }
                .sortedByDescending { it.finalScore }
                .toList()
            val bestAlternative = alternatives.firstOrNull()
            val ambiguousNeighborhood = bestAlternative != null &&
                abs(bestAlternative.domIndex - edge.domIndex) <= 2
            if (!ambiguousNeighborhood) {
                return@filter true
            }
            val alternative = bestAlternative ?: return@filter true

            val bestAlternativeGap = edge.finalScore - alternative.finalScore
            bestAlternativeGap >= confidenceMargin
        }
    }

    private fun preliminaryFloorFor(type: AnchorType): Double {
        return when (type) {
            AnchorType.H,
            AnchorType.CODE,
            AnchorType.TABLE,
            AnchorType.IMG,
            -> hardAnchorPreliminaryFloor

            AnchorType.BQ,
            AnchorType.LI,
            AnchorType.P,
            -> mediumAnchorPreliminaryFloor

            AnchorType.HR -> minAnchorConfidence
        }
    }

    private fun skipSourcePenalty(source: SourceAnchor): Double {
        return when (source.type) {
            AnchorType.H,
            AnchorType.CODE,
            AnchorType.TABLE,
            AnchorType.IMG,
            -> 0.06

            AnchorType.BQ,
            AnchorType.LI,
            AnchorType.P,
            -> 0.10

            AnchorType.HR -> 0.12
        }
    }

    private fun skipDomPenalty(dom: DomAnchor): Double {
        return when (dom.type) {
            AnchorType.H,
            AnchorType.CODE,
            AnchorType.TABLE,
            AnchorType.IMG,
            -> 0.06

            AnchorType.BQ,
            AnchorType.LI,
            AnchorType.P,
            -> 0.10

            AnchorType.HR -> 0.12
        }
    }

    private fun preliminaryScore(
        source: SourceAnchor,
        dom: DomAnchor,
        segment: SourceAnchorSegment,
    ): Double? {
        if (!isCompatible(source, dom)) {
            return null
        }
        return scoreMatch(source = source, dom = dom, segment = segment)
    }

    private fun isCompatible(source: SourceAnchor, dom: DomAnchor): Boolean {
        if (source.type != dom.type) {
            return false
        }
        if (source.type == AnchorType.H) {
            if (source.level != dom.level) {
                return false
            }
            val sourceKey = anchorIdKey(source.id)
            val domKey = anchorIdKey(dom.id)
            if (sourceKey.isBlank() || domKey.isBlank()) {
                return source.normText == dom.normText
            }
            return sourceKey == domKey
        }
        return true
    }

    private fun buildSegments(
        hardMatches: List<HardAnchorMatch>,
        sourceLineCount: Int,
        maxPreviewScrollY: Double,
    ): List<SourceAnchorSegment> {
        if (hardMatches.isEmpty()) {
            return listOf(
                SourceAnchorSegment(
                    sourceStartLine = 0,
                    sourceEndLineExclusive = sourceLineCount,
                    previewStartY = 0.0,
                    previewEndY = maxPreviewScrollY,
                ),
            )
        }

        val sorted = hardMatches.sortedBy { it.source.startLine }
        val segments = mutableListOf<SourceAnchorSegment>()

        if (sorted.first().source.startLine > 0) {
            segments += SourceAnchorSegment(
                sourceStartLine = 0,
                sourceEndLineExclusive = sorted.first().source.startLine,
                previewStartY = 0.0,
                previewEndY = sorted.first().dom.top,
            )
        }

        sorted.forEachIndexed { index, match ->
            val next = sorted.getOrNull(index + 1)
            val sourceEnd = next?.source?.startLine ?: sourceLineCount
            val previewEnd = next?.dom?.top ?: maxPreviewScrollY
            segments += SourceAnchorSegment(
                sourceStartLine = match.source.startLine,
                sourceEndLineExclusive = sourceEnd.coerceAtLeast(match.source.startLine + 1),
                previewStartY = match.dom.top,
                previewEndY = previewEnd.coerceAtLeast(match.dom.top + 1.0),
            )
        }

        return segments
    }

    private fun scoreMatch(
        source: SourceAnchor,
        dom: DomAnchor,
        segment: SourceAnchorSegment,
    ): Double {
        val textScore = textSimilarity(source.normText, dom.normText)
        val typeScore = if (source.type == dom.type) 1.0 else 0.0

        val sourceSpan = (segment.sourceEndLineExclusive - segment.sourceStartLine).coerceAtLeast(1)
        val domSpan = (segment.previewEndY - segment.previewStartY).coerceAtLeast(1.0)
        val sourceRel = (source.startLine - segment.sourceStartLine).toDouble() / sourceSpan.toDouble()
        val domRel = (dom.top - segment.previewStartY) / domSpan
        val relScore = (1.0 - abs(sourceRel - domRel)).coerceIn(0.0, 1.0)

        if (source.type == AnchorType.H) {
            val sourceKey = anchorIdKey(source.id)
            val domKey = anchorIdKey(dom.id)
            val idScore = if (sourceKey == domKey) 1.0 else textScore
            val occurrenceScore = if (source.occ == dom.occ) 1.0 else 0.0
            return (0.50 * idScore) + (0.20 * occurrenceScore) + (0.20 * typeScore) + (0.10 * relScore)
        }

        return (0.60 * textScore) + (0.25 * typeScore) + (0.15 * relScore)
    }

    private fun textSimilarity(left: String, right: String): Double {
        if (left.isBlank() || right.isBlank()) {
            return 0.0
        }
        if (left == right) {
            return 1.0
        }

        val leftTokens = left.split(' ').filter { it.isNotBlank() }.toSet()
        val rightTokens = right.split(' ').filter { it.isNotBlank() }.toSet()
        if (leftTokens.isEmpty() || rightTokens.isEmpty()) {
            return 0.0
        }

        val intersection = leftTokens.intersect(rightTokens).size.toDouble()
        val union = leftTokens.union(rightTokens).size.toDouble().coerceAtLeast(1.0)
        return (intersection / union).coerceIn(0.0, 1.0)
    }

    private fun buildKnots(
        sourceLineCount: Int,
        lineHeightPx: Int,
        maxPreviewScrollY: Double,
        matches: List<AnchorMatch>,
    ): List<Knot> {
        val safeLineHeight = lineHeightPx.coerceAtLeast(1)
        val documentBottomEditorY = sourceLineCount.coerceAtLeast(1).toDouble() * safeLineHeight

        val knots = mutableListOf(
            Knot(editorY = 0.0, previewY = 0.0, hard = true, confidence = 1.0),
            Knot(editorY = documentBottomEditorY, previewY = maxPreviewScrollY, hard = true, confidence = 1.0),
        )

        matches.forEach { match ->
            val startEditorY = match.source.startLine.toDouble() * safeLineHeight
            val endEditorY = match.source.endLine.coerceAtLeast(match.source.startLine + 1).toDouble() * safeLineHeight
            val topPreviewY = match.dom.top.coerceAtLeast(0.0)
            val bottomPreviewY = match.dom.bottom.coerceAtLeast(topPreviewY + 1.0)

            knots += Knot(startEditorY, topPreviewY, hard = match.hard, confidence = match.confidence)
            knots += Knot(endEditorY, bottomPreviewY, hard = match.hard, confidence = match.confidence)
        }

        val sorted = knots.sortedBy { it.editorY }
        val deduplicated = mutableListOf<Knot>()
        sorted.forEach { knot ->
            val last = deduplicated.lastOrNull()
            if (last == null) {
                deduplicated += knot
                return@forEach
            }

            if (abs(last.editorY - knot.editorY) <= 0.5) {
                if (knot.confidence > last.confidence || (knot.hard && !last.hard)) {
                    deduplicated[deduplicated.lastIndex] = knot.copy(
                        previewY = maxOf(last.previewY, knot.previewY),
                    )
                }
                return@forEach
            }

            deduplicated += knot
        }

        val monotonic = mutableListOf<Knot>()
        var maxSeenY = 0.0
        deduplicated.forEach { knot ->
            maxSeenY = maxOf(maxSeenY, knot.previewY)
            monotonic += knot.copy(previewY = maxSeenY.coerceIn(0.0, maxPreviewScrollY))
        }

        val compacted = mutableListOf<Knot>()
        monotonic.forEachIndexed { index, knot ->
            if (index == 0 || index == monotonic.lastIndex || knot.hard) {
                compacted += knot
                return@forEachIndexed
            }

            val prev = compacted.last()
            val next = monotonic[index + 1]
            val editorDelta = abs(knot.editorY - prev.editorY)
            val previewDelta = abs(knot.previewY - prev.previewY)
            val nextEditorDelta = abs(next.editorY - knot.editorY)
            val nextPreviewDelta = abs(next.previewY - knot.previewY)
            if (editorDelta < 2.0 && previewDelta < 2.0 && nextEditorDelta < 2.0 && nextPreviewDelta < 2.0) {
                return@forEachIndexed
            }

            compacted += knot
        }

        return compacted
    }

    private fun interpolate(knots: List<Knot>, editorY: Double): Double {
        if (knots.isEmpty()) {
            return 0.0
        }
        if (editorY <= knots.first().editorY) {
            return knots.first().previewY
        }
        if (editorY >= knots.last().editorY) {
            return knots.last().previewY
        }

        var low = 0
        var high = knots.lastIndex
        while (low <= high) {
            val mid = (low + high) ushr 1
            val value = knots[mid].editorY
            when {
                value < editorY -> low = mid + 1
                value > editorY -> high = mid - 1
                else -> return knots[mid].previewY
            }
        }

        val upperIndex = low.coerceIn(1, knots.lastIndex)
        val lowerIndex = (upperIndex - 1).coerceAtLeast(0)
        val lower = knots[lowerIndex]
        val upper = knots[upperIndex]
        val span = (upper.editorY - lower.editorY).coerceAtLeast(1.0)
        val ratio = ((editorY - lower.editorY) / span).coerceIn(0.0, 1.0)
        return lower.previewY + (upper.previewY - lower.previewY) * ratio
    }

    private fun isSuppressed(): Boolean = System.currentTimeMillis() < suppressUntilMs

    private fun extractLines(document: Document): List<String> {
        val text = document.immutableCharSequence.toString()
        val lines = text.split('\n')
        return if (lines.isEmpty()) listOf("") else lines
    }

    private fun countLineBreaks(fragment: CharSequence): Int {
        if (fragment.isEmpty()) {
            return 0
        }
        var count = 0
        fragment.forEach { char ->
            if (char == '\n') {
                count += 1
            }
        }
        return count
    }

    private fun expandDirtyRange(
        lines: List<String>,
        startLine: Int,
        endLineExclusive: Int,
    ): LineRange {
        if (lines.isEmpty()) {
            return LineRange(0, 1)
        }

        var start = startLine.coerceIn(0, lines.lastIndex)
        var end = endLineExclusive.coerceIn(start + 1, lines.size)

        var cursor = start
        while (cursor > 0 && !isHeadingBoundary(lines, cursor)) {
            cursor -= 1
        }
        start = cursor.coerceAtLeast(0)

        cursor = (end - 1).coerceIn(0, lines.lastIndex)
        while (cursor < lines.lastIndex && !isHeadingBoundary(lines, cursor + 1)) {
            cursor += 1
        }
        end = (cursor + 1).coerceAtMost(lines.size)

        start = (start - 1).coerceAtLeast(0)
        end = (end + 1).coerceAtMost(lines.size)

        while (start > 0 && isBlockContinuation(lines[start - 1])) {
            start -= 1
        }
        while (end < lines.size && isBlockContinuation(lines[end])) {
            end += 1
        }

        return LineRange(startLine = start, endLineExclusive = end.coerceAtLeast(start + 1))
    }

    private fun isHeadingBoundary(lines: List<String>, index: Int): Boolean {
        val line = lines[index]
        if (atxRegex.matches(line.trim())) {
            return true
        }
        if (index + 1 >= lines.size) {
            return false
        }
        return line.trim().isNotEmpty() && setextRegex.matches(lines[index + 1].trim())
    }

    private fun isBlockContinuation(line: String): Boolean {
        val trimmed = line.trimStart()
        return trimmed.startsWith(">") ||
            trimmed.startsWith("|") ||
            trimmed.startsWith("-") ||
            trimmed.startsWith("*") ||
            trimmed.startsWith("+") ||
            trimmed.matches(Regex("""\d+\..*"""))
    }

    private fun mergeRanges(ranges: List<LineRange>): List<LineRange> {
        if (ranges.isEmpty()) {
            return emptyList()
        }
        val sorted = ranges.sortedBy { it.startLine }
        val merged = mutableListOf(sorted.first())
        sorted.drop(1).forEach { range ->
            val last = merged.last()
            if (range.startLine <= last.endLineExclusive + 1) {
                merged[merged.lastIndex] = LineRange(
                    startLine = minOf(last.startLine, range.startLine),
                    endLineExclusive = maxOf(last.endLineExclusive, range.endLineExclusive),
                )
            } else {
                merged += range
            }
        }
        return merged
    }

    private fun occurrenceKey(type: AnchorType, level: Int?, key: String): String {
        return "${type.name}|${level ?: 0}|$key"
    }

    private fun buildAnchorId(type: AnchorType, level: Int?, key: String, occurrence: Int): String {
        val safeKey = key.ifBlank { "empty" }
        return "${type.name}|lvl=${level ?: 0}|key=$safeKey|occ=$occurrence"
    }

    private fun anchorIdKey(id: String): String {
        val marker = "|key="
        val start = id.indexOf(marker)
        if (start == -1) {
            return id
        }
        val from = start + marker.length
        val end = id.indexOf("|occ=", from).takeIf { it > from } ?: id.length
        return id.substring(from, end)
    }

    private fun parseAtxHeading(candidate: String): ParsedAtxHeading? {
        val match = atxRegex.matchEntire(candidate.trim()) ?: return null
        val hashes = match.groupValues[1]
        val text = match.groupValues[2]
            .trim()
            .trimEnd('#')
            .trim()
        if (text.isBlank()) {
            return null
        }
        return ParsedAtxHeading(level = hashes.length, text = text)
    }

    private data class ParsedAtxHeading(
        val level: Int,
        val text: String,
    )

    private fun parseTrailingHeadingId(text: String): Pair<String?, String> {
        val match = headingIdRegex.find(text.trim()) ?: return null to text.trim()
        val explicit = match.groupValues.drop(1).firstOrNull { it.isNotBlank() }
        val cleaned = text.removeRange(match.range).trim()
        return explicit to cleaned
    }

    private fun isSetextHeadingStart(lines: List<String>, index: Int, inFence: BooleanArray): Boolean {
        if (index + 1 >= lines.size) {
            return false
        }
        if (inFence[index] || inFence[index + 1]) {
            return false
        }
        val current = lines[index].trim()
        if (current.isEmpty()) {
            return false
        }
        return setextRegex.matches(lines[index + 1].trim())
    }

    private fun isTableHeaderRow(lines: List<String>, index: Int, inFence: BooleanArray): Boolean {
        if (index + 1 >= lines.size) {
            return false
        }
        if (inFence[index] || inFence[index + 1]) {
            return false
        }
        val delimiter = lines[index + 1].trim()
        return delimiter.matches(Regex("""^\|?\s*:?-{3,}:?(?:\s*\|\s*:?-{3,}:?)*\s*\|?$"""))
    }

    private fun normalizeTableHeader(line: String): String {
        return line
            .trim()
            .trim('|')
            .split('|')
            .map { cell -> normalizeText(cell) }
            .filter { it.isNotBlank() }
            .joinToString(" ")
    }

    private fun normalizeHeadingId(id: String): String {
        return id.trim().trimStart('#').lowercase()
            .replace(Regex("""[^a-z0-9_-]+"""), "-")
            .trim('-')
    }

    private fun slugify(value: String): String {
        return normalizeText(value)
            .replace(Regex("""[^a-z0-9]+"""), "-")
            .trim('-')
    }

    private fun fingerprintText(text: String): String {
        val normalized = normalizeText(text)
        if (normalized.isBlank()) {
            return ""
        }
        val stopwords = setOf("the", "a", "an", "and", "or", "to", "of", "in", "on", "for", "is", "are")
        val tokens = normalized.split(' ')
            .filter { token -> token.isNotBlank() && token !in stopwords }
        val selectedTokens = tokens.take(5)
        val prefix = tokens.take(6).joinToString(" ")
        return (selectedTokens + prefix).joinToString(" ").trim()
    }

    private fun normalizeText(text: String): String {
        val nfkc = Normalizer.normalize(text, Normalizer.Form.NFKC)
        val lowered = nfkc.lowercase().trim()
        if (lowered.isBlank()) {
            return ""
        }

        val withoutMarkdownPunctuation = lowered.replace(
            Regex("""[`*_>#~!\[\](){}:;.,'"\\|+=-]"""),
            " ",
        )

        return withoutMarkdownPunctuation
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun basename(path: String): String {
        val normalized = path.trim().substringBefore('?').substringBefore('#')
        if (normalized.isBlank()) {
            return ""
        }
        return normalized.substringAfterLast('/').substringAfterLast('\\')
    }

    private fun strengthForType(type: AnchorType): Double {
        return when (type) {
            AnchorType.H -> 1.0
            AnchorType.CODE -> 0.95
            AnchorType.TABLE -> 0.92
            AnchorType.IMG -> 0.90
            AnchorType.BQ -> 0.65
            AnchorType.LI -> 0.62
            AnchorType.P -> 0.45
            AnchorType.HR -> 0.30
        }
    }

    private fun isParagraphCandidate(line: String): Boolean {
        val trimmed = line.trim()
        if (isMarkdownCommentLine(line)) {
            return false
        }
        if (trimmed.length < 12) {
            return false
        }
        if (trimmed.startsWith("#") || trimmed.startsWith(">") || trimmed.startsWith("|")) {
            return false
        }
        if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
            return false
        }
        if (trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ")) {
            return false
        }
        if (trimmed.matches(Regex("""\d+\.\s+.*"""))) {
            return false
        }
        return true
    }

    private fun isMarkdownCommentLine(line: String): Boolean = markdownCommentRegex.matches(line)
}

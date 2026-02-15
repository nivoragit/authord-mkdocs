package com.authord.mkdocs.ui.intellij

import com.intellij.openapi.editor.Document
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

internal enum class SyncState {
    IDLE,
    RATIO_SYNC_ACTIVE,
    PRECISE_ADJUST_ACTIVE,
}

internal data class EffectiveUnitsResult(
    val units: IntArray,
    val prefix: IntArray,
    val total: Int,
)

internal data class Anchor(
    val sourceLine: Int,
    val previewY: Double,
)

internal data class EffectiveUnitsCache(
    val documentIdentity: Int,
    val modificationStamp: Long,
    val lineCount: Int,
    val lines: List<String>,
    val units: EffectiveUnitsResult,
)

internal class MkdocsScrollSyncEngine(
    private val delayedInvoker: (delayMillis: Long, task: () -> Unit) -> Unit,
    private val ignoredCommentPrefix: String = "[//]: #",
    private val ratioFlushDelayMs: Long = 16L,
    private val preciseAdjustIdleDebounceMs: Long = 120L,
    private val preciseAdjustPixelEpsilon: Double = 2.0,
    private val preciseAdjustLineEpsilon: Int = 1,
) {
    private var effectiveUnitsCache: EffectiveUnitsCache? = null
    private val suppressEditorEvents = AtomicBoolean(false)
    private val suppressPreviewEvents = AtomicBoolean(false)
    private val coordinator = SyncCoordinator()

    private enum class SyncDirection {
        EDITOR_TO_PREVIEW,
        PREVIEW_TO_EDITOR,
    }

    private data class EditorScrollSnapshot(
        val selectedPath: String,
        val topLine: Int,
        val lines: List<String>,
        val units: EffectiveUnitsResult,
    )

    private data class PreviewScrollSnapshot(
        val selectedPath: String,
        val metrics: PreviewScrollMetrics,
        val currentTopLine: Int,
        val lines: List<String>,
        val units: EffectiveUnitsResult,
    )

    fun pixelEpsilon(): Double = preciseAdjustPixelEpsilon

    fun isEditorEventSuppressed(): Boolean = suppressEditorEvents.get()

    fun isPreviewEventSuppressed(): Boolean = suppressPreviewEvents.get()

    fun suppressEditorEvents() {
        suppressEditorEvents.set(true)
        delayedInvoker(ratioFlushDelayMs) {
            suppressEditorEvents.set(false)
        }
    }

    fun suppressPreviewEvents() {
        suppressPreviewEvents.set(true)
        delayedInvoker(ratioFlushDelayMs) {
            suppressPreviewEvents.set(false)
        }
    }

    fun resolveEffectiveUnits(document: Document): EffectiveUnitsCache {
        val lineCount = document.lineCount
        val currentStamp = document.modificationStamp
        val documentIdentity = System.identityHashCode(document)
        val cached = effectiveUnitsCache

        if (
            cached != null &&
            cached.documentIdentity == documentIdentity &&
            cached.modificationStamp == currentStamp &&
            cached.lineCount == lineCount
        ) {
            return cached
        }

        val lines = extractLines(document)
        val computed = computeEffectiveUnits(lines)
        val cache = EffectiveUnitsCache(
            documentIdentity = documentIdentity,
            modificationStamp = currentStamp,
            lineCount = lineCount,
            lines = lines,
            units = computed,
        )
        effectiveUnitsCache = cache
        return cache
    }

    fun onEditorScroll(
        topLine: Int,
        selectedPath: String,
        lines: List<String>,
        units: EffectiveUnitsResult,
        applyRatio: (Double) -> Unit,
        applyPrecise: (Double) -> Unit,
        loadPreviewSnapshot: ((PreviewDomSnapshot?) -> Unit) -> Unit,
        loadPreviewMetrics: ((PreviewScrollMetrics?) -> Unit) -> Unit,
    ) {
        coordinator.onEditorScroll(
            topLine = topLine,
            selectedPath = selectedPath,
            lines = lines,
            units = units,
            applyRatio = applyRatio,
            applyPrecise = applyPrecise,
            loadPreviewSnapshot = loadPreviewSnapshot,
            loadPreviewMetrics = loadPreviewMetrics,
        )
    }

    fun onPreviewScroll(
        scrollY: Double,
        maxScrollY: Double,
        selectedPath: String,
        currentTopLine: Int,
        lines: List<String>,
        units: EffectiveUnitsResult,
        applyRatio: (Int) -> Unit,
        applyPrecise: (Int) -> Unit,
        loadPreviewSnapshot: ((PreviewDomSnapshot?) -> Unit) -> Unit,
        loadPreviewMetrics: ((PreviewScrollMetrics?) -> Unit) -> Unit,
    ) {
        coordinator.onPreviewScroll(
            scrollY = scrollY,
            maxScrollY = maxScrollY,
            selectedPath = selectedPath,
            currentTopLine = currentTopLine,
            lines = lines,
            units = units,
            applyRatio = applyRatio,
            applyPrecise = applyPrecise,
            loadPreviewSnapshot = loadPreviewSnapshot,
            loadPreviewMetrics = loadPreviewMetrics,
        )
    }

    fun computeEffectiveUnits(lines: List<String>): EffectiveUnitsResult {
        val normalizedLines = if (lines.isEmpty()) listOf("") else lines
        val units = IntArray(normalizedLines.size)
        val prefix = IntArray(normalizedLines.size + 1)

        normalizedLines.forEachIndexed { index, line ->
            val unit = if (line.startsWith(ignoredCommentPrefix) || line.trim().isEmpty()) 0 else 1
            units[index] = unit
            prefix[index + 1] = prefix[index] + unit
        }

        return EffectiveUnitsResult(
            units = units,
            prefix = prefix,
            total = prefix.last(),
        )
    }

    fun ratioSyncFromEditor(topLine: Int, units: EffectiveUnitsResult): Double {
        if (units.total <= 0) {
            return 0.0
        }

        val safeLine = topLine.coerceIn(0, units.units.size)
        val effectivePos = units.prefix[safeLine].toDouble()
        return (effectivePos / units.total.toDouble()).coerceIn(0.0, 1.0)
    }

    fun ratioSyncFromPreview(
        scrollY: Double,
        maxScrollY: Double,
        units: EffectiveUnitsResult,
    ): Int {
        if (units.total <= 0) {
            return 0
        }

        val normalizedMax = maxScrollY.coerceAtLeast(1.0)
        val ratio = (scrollY / normalizedMax).coerceIn(0.0, 1.0)
        val targetUnits = ratio * units.total.toDouble()
        return lineForTargetUnits(units, targetUnits)
    }

    fun buildAnchorMap(
        sourceDoc: List<String>,
        previewDom: PreviewDomSnapshot?,
    ): List<Anchor> {
        val normalizedSource = if (sourceDoc.isEmpty()) listOf("") else sourceDoc
        val lastLine = (normalizedSource.size - 1).coerceAtLeast(0)
        val maxY = previewDom?.maxScrollY?.coerceAtLeast(0.0) ?: 0.0
        val anchors = mutableListOf(
            Anchor(sourceLine = 0, previewY = 0.0),
            Anchor(sourceLine = lastLine, previewY = maxY),
        )

        val previewHeadings = previewDom?.headings.orEmpty()
        if (previewHeadings.isEmpty()) {
            return anchors.sortedBy { it.sourceLine }
        }

        val sourceHeadingOccurrences = mutableMapOf<String, Int>()
        val sourceHeadingLinesByKey = mutableMapOf<String, Int>()
        normalizedSource.forEachIndexed { index, line ->
            val headingText = parseHeadingText(line) ?: return@forEachIndexed
            val normalized = normalizeHeadingText(headingText)
            if (normalized.isBlank()) {
                return@forEachIndexed
            }

            val occurrence = sourceHeadingOccurrences[normalized] ?: 0
            sourceHeadingOccurrences[normalized] = occurrence + 1
            sourceHeadingLinesByKey["$normalized#$occurrence"] = index
        }

        val previewHeadingOccurrences = mutableMapOf<String, Int>()
        previewHeadings.forEach { heading ->
            val normalized = normalizeHeadingText(heading.text)
            if (normalized.isBlank()) {
                return@forEach
            }
            val occurrence = previewHeadingOccurrences[normalized] ?: 0
            previewHeadingOccurrences[normalized] = occurrence + 1
            val key = "$normalized#$occurrence"
            val sourceLine = sourceHeadingLinesByKey[key] ?: return@forEach
            anchors += Anchor(
                sourceLine = sourceLine,
                previewY = heading.y.coerceAtLeast(0.0),
            )
        }

        return anchors
            .sortedBy { it.sourceLine }
            .distinctBy { it.sourceLine }
    }

    fun preciseAdjustFromEditor(
        topLine: Int,
        units: EffectiveUnitsResult,
        anchors: List<Anchor>,
    ): Double? {
        if (anchors.size < 2 || units.total <= 0) {
            return null
        }

        val sorted = anchors.sortedBy { it.sourceLine }
        val safeLine = topLine.coerceIn(0, units.units.lastIndex.coerceAtLeast(0))
        val index = sorted.binarySearch { anchor -> anchor.sourceLine.compareTo(safeLine) }
        val upperIndex = when {
            index >= 0 -> index
            else -> (-index - 1).coerceIn(1, sorted.lastIndex)
        }
        val lowerIndex = (upperIndex - 1).coerceAtLeast(0)

        val lower = sorted[lowerIndex]
        val upper = sorted[upperIndex]
        if (lower.sourceLine == upper.sourceLine) {
            return lower.previewY
        }

        val lowerUnits = effectivePosForLine(units, lower.sourceLine)
        val upperUnits = effectivePosForLine(units, upper.sourceLine)
        val currentUnits = effectivePosForLine(units, safeLine)
        val denominator = (upperUnits - lowerUnits).coerceAtLeast(1.0)
        val p = ((currentUnits - lowerUnits) / denominator).coerceIn(0.0, 1.0)

        return lower.previewY + (upper.previewY - lower.previewY) * p
    }

    fun preciseAdjustFromPreview(
        scrollY: Double,
        maxScrollY: Double,
        units: EffectiveUnitsResult,
        anchors: List<Anchor>,
    ): Int? {
        if (anchors.size < 2 || units.total <= 0) {
            return null
        }

        val clampedY = scrollY.coerceIn(0.0, maxScrollY.coerceAtLeast(0.0))
        val sortedByY = anchors.sortedBy { it.previewY }
        val index = sortedByY.binarySearch { anchor -> anchor.previewY.compareTo(clampedY) }
        val upperIndex = when {
            index >= 0 -> index
            else -> (-index - 1).coerceIn(1, sortedByY.lastIndex)
        }
        val lowerIndex = (upperIndex - 1).coerceAtLeast(0)
        val lower = sortedByY[lowerIndex]
        val upper = sortedByY[upperIndex]
        if (kotlin.math.abs(upper.previewY - lower.previewY) < 0.0001) {
            return lower.sourceLine
        }

        val p = ((clampedY - lower.previewY) / (upper.previewY - lower.previewY)).coerceIn(0.0, 1.0)
        val lowerUnits = effectivePosForLine(units, lower.sourceLine)
        val upperUnits = effectivePosForLine(units, upper.sourceLine)
        val targetUnits = lowerUnits + (upperUnits - lowerUnits) * p
        return lineForTargetUnits(units, targetUnits)
    }

    private fun extractLines(document: Document): List<String> {
        val text = document.immutableCharSequence.toString()
        val lines = text.split('\n')
        return if (lines.isEmpty()) listOf("") else lines
    }

    private fun parseHeadingText(line: String): String? {
        val trimmed = line.trimStart()
        val hashPrefix = trimmed.takeWhile { it == '#' }
        if (hashPrefix.length !in 1..6) {
            return null
        }
        if (!trimmed.startsWith("${hashPrefix} ")) {
            return null
        }
        return trimmed.removePrefix("${hashPrefix} ").trim()
    }

    private fun normalizeHeadingText(text: String): String {
        return text.trim().lowercase().replace(Regex("\\s+"), " ")
    }

    private fun effectivePosForLine(units: EffectiveUnitsResult, line: Int): Double {
        val safe = line.coerceIn(0, units.units.size)
        return units.prefix[safe].toDouble()
    }

    private fun lineForTargetUnits(units: EffectiveUnitsResult, targetUnits: Double): Int {
        if (units.prefix.isEmpty()) {
            return 0
        }

        val clamped = targetUnits.coerceIn(0.0, units.total.toDouble())
        var low = 0
        var high = units.prefix.lastIndex
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (units.prefix[mid].toDouble() <= clamped) {
                low = mid + 1
            } else {
                high = mid - 1
            }
        }

        val resolvedPrefixIndex = high.coerceAtLeast(0)
        return resolvedPrefixIndex.coerceIn(0, units.units.lastIndex.coerceAtLeast(0))
    }

    private inner class SyncCoordinator {
        @Volatile
        var state: SyncState = SyncState.IDLE

        val ratioSeq: AtomicInteger = AtomicInteger(0)
        private val idleDebounceTimer: AtomicInteger = AtomicInteger(0)
        private val preciseAbortSequence: AtomicInteger = AtomicInteger(0)
        private val ratioFlushScheduled: AtomicBoolean = AtomicBoolean(false)
        private var pendingEditorSnapshot: EditorScrollSnapshot? = null
        private var pendingPreviewSnapshot: PreviewScrollSnapshot? = null
        private var lastEditorSnapshot: EditorScrollSnapshot? = null
        private var lastPreviewMetrics: PreviewScrollMetrics? = null

        fun onEditorScroll(
            topLine: Int,
            selectedPath: String,
            lines: List<String>,
            units: EffectiveUnitsResult,
            applyRatio: (Double) -> Unit,
            applyPrecise: (Double) -> Unit,
            loadPreviewSnapshot: ((PreviewDomSnapshot?) -> Unit) -> Unit,
            loadPreviewMetrics: ((PreviewScrollMetrics?) -> Unit) -> Unit,
        ) {
            pendingEditorSnapshot = EditorScrollSnapshot(
                selectedPath = selectedPath,
                topLine = topLine,
                lines = lines,
                units = units,
            )

            if (!ratioFlushScheduled.compareAndSet(false, true)) {
                return
            }

            delayedInvoker(ratioFlushDelayMs) {
                ratioFlushScheduled.set(false)
                val snapshot = pendingEditorSnapshot ?: return@delayedInvoker
                pendingEditorSnapshot = null

                val seq = ratioSeq.incrementAndGet()
                cancelPreciseAdjust()
                state = SyncState.RATIO_SYNC_ACTIVE
                lastEditorSnapshot = snapshot

                val ratio = ratioSyncFromEditor(snapshot.topLine, snapshot.units)
                applyRatio(ratio)

                state = SyncState.IDLE
                schedulePreciseAdjust(
                    direction = SyncDirection.EDITOR_TO_PREVIEW,
                    ratioSequence = seq,
                    applyPreciseY = applyPrecise,
                    applyPreciseLine = {},
                    loadPreviewSnapshot = loadPreviewSnapshot,
                    loadPreviewMetrics = loadPreviewMetrics,
                )
            }
        }

        fun onPreviewScroll(
            scrollY: Double,
            maxScrollY: Double,
            selectedPath: String,
            currentTopLine: Int,
            lines: List<String>,
            units: EffectiveUnitsResult,
            applyRatio: (Int) -> Unit,
            applyPrecise: (Int) -> Unit,
            loadPreviewSnapshot: ((PreviewDomSnapshot?) -> Unit) -> Unit,
            loadPreviewMetrics: ((PreviewScrollMetrics?) -> Unit) -> Unit,
        ) {
            pendingPreviewSnapshot = PreviewScrollSnapshot(
                selectedPath = selectedPath,
                metrics = PreviewScrollMetrics(scrollY = scrollY, maxScrollY = maxScrollY),
                currentTopLine = currentTopLine,
                lines = lines,
                units = units,
            )

            if (!ratioFlushScheduled.compareAndSet(false, true)) {
                return
            }

            delayedInvoker(ratioFlushDelayMs) {
                ratioFlushScheduled.set(false)
                val snapshot = pendingPreviewSnapshot ?: return@delayedInvoker
                pendingPreviewSnapshot = null

                val seq = ratioSeq.incrementAndGet()
                cancelPreciseAdjust()
                state = SyncState.RATIO_SYNC_ACTIVE
                lastPreviewMetrics = snapshot.metrics
                lastEditorSnapshot = EditorScrollSnapshot(
                    selectedPath = snapshot.selectedPath,
                    topLine = snapshot.currentTopLine,
                    lines = snapshot.lines,
                    units = snapshot.units,
                )

                val targetLine = ratioSyncFromPreview(
                    scrollY = snapshot.metrics.scrollY,
                    maxScrollY = snapshot.metrics.maxScrollY,
                    units = snapshot.units,
                )

                val previousTopLine = lastEditorSnapshot?.topLine
                if (previousTopLine == null || kotlin.math.abs(previousTopLine - targetLine) > preciseAdjustLineEpsilon) {
                    applyRatio(targetLine)
                }
                lastEditorSnapshot = lastEditorSnapshot?.copy(topLine = targetLine)

                state = SyncState.IDLE
                schedulePreciseAdjust(
                    direction = SyncDirection.PREVIEW_TO_EDITOR,
                    ratioSequence = seq,
                    applyPreciseY = {},
                    applyPreciseLine = applyPrecise,
                    loadPreviewSnapshot = loadPreviewSnapshot,
                    loadPreviewMetrics = loadPreviewMetrics,
                )
            }
        }

        fun schedulePreciseAdjust(
            direction: SyncDirection,
            ratioSequence: Int,
            applyPreciseY: (Double) -> Unit,
            applyPreciseLine: (Int) -> Unit,
            loadPreviewSnapshot: ((PreviewDomSnapshot?) -> Unit) -> Unit,
            loadPreviewMetrics: ((PreviewScrollMetrics?) -> Unit) -> Unit,
        ) {
            val timerToken = idleDebounceTimer.incrementAndGet()
            val abortToken = preciseAbortSequence.get()

            delayedInvoker(preciseAdjustIdleDebounceMs) {
                if (timerToken != idleDebounceTimer.get()) {
                    return@delayedInvoker
                }
                if (ratioSequence != ratioSeq.get() || abortToken != preciseAbortSequence.get()) {
                    return@delayedInvoker
                }

                state = SyncState.PRECISE_ADJUST_ACTIVE

                when (direction) {
                    SyncDirection.EDITOR_TO_PREVIEW -> {
                        val editorSnapshot = lastEditorSnapshot ?: run {
                            state = SyncState.IDLE
                            return@delayedInvoker
                        }

                        loadPreviewSnapshot { previewSnapshot ->
                            if (ratioSequence != ratioSeq.get() || abortToken != preciseAbortSequence.get()) {
                                state = SyncState.IDLE
                                return@loadPreviewSnapshot
                            }

                            val anchors = buildAnchorMap(editorSnapshot.lines, previewSnapshot)
                            val targetY = preciseAdjustFromEditor(
                                topLine = editorSnapshot.topLine,
                                units = editorSnapshot.units,
                                anchors = anchors,
                            ) ?: run {
                                state = SyncState.IDLE
                                return@loadPreviewSnapshot
                            }

                            loadPreviewMetrics { metrics ->
                                if (ratioSequence != ratioSeq.get() || abortToken != preciseAbortSequence.get()) {
                                    state = SyncState.IDLE
                                    return@loadPreviewMetrics
                                }

                                val delta = if (metrics == null) {
                                    preciseAdjustPixelEpsilon + 1.0
                                } else {
                                    kotlin.math.abs(targetY - metrics.scrollY)
                                }

                                if (delta > preciseAdjustPixelEpsilon) {
                                    applyPreciseY(targetY)
                                }

                                state = SyncState.IDLE
                            }
                        }
                    }

                    SyncDirection.PREVIEW_TO_EDITOR -> {
                        val editorSnapshot = lastEditorSnapshot ?: run {
                            state = SyncState.IDLE
                            return@delayedInvoker
                        }
                        loadPreviewMetrics { refreshedMetrics ->
                            if (ratioSequence != ratioSeq.get() || abortToken != preciseAbortSequence.get()) {
                                state = SyncState.IDLE
                                return@loadPreviewMetrics
                            }

                            val previewMetrics = refreshedMetrics ?: lastPreviewMetrics ?: run {
                                state = SyncState.IDLE
                                return@loadPreviewMetrics
                            }

                            loadPreviewSnapshot { previewSnapshot ->
                                if (ratioSequence != ratioSeq.get() || abortToken != preciseAbortSequence.get()) {
                                    state = SyncState.IDLE
                                    return@loadPreviewSnapshot
                                }

                                val anchors = buildAnchorMap(editorSnapshot.lines, previewSnapshot)
                                val targetLine = preciseAdjustFromPreview(
                                    scrollY = previewMetrics.scrollY,
                                    maxScrollY = previewMetrics.maxScrollY,
                                    units = editorSnapshot.units,
                                    anchors = anchors,
                                )

                                if (targetLine != null && kotlin.math.abs(targetLine - editorSnapshot.topLine) > preciseAdjustLineEpsilon) {
                                    applyPreciseLine(targetLine)
                                }

                                state = SyncState.IDLE
                            }
                        }
                    }
                }
            }
        }

        fun cancelPreciseAdjust() {
            preciseAbortSequence.incrementAndGet()
            idleDebounceTimer.incrementAndGet()
            if (state == SyncState.PRECISE_ADJUST_ACTIVE) {
                state = SyncState.IDLE
            }
        }
    }
}
